package com.pretorian.tv

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val code: Long, val name: String, val apk: String, val notes: String) {
    /** the 2–4 short lines of "what's new" shown on the yellow card */
    val lines get() = notes.lines().map { it.trim().removePrefix("-").removePrefix("•").trim() }.filter { it.isNotBlank() }.take(4)
}

/** One published version, for the history list and for going back. */
data class Release(val tag: String, val name: String, val notes: String, val apk: String, val date: String)

/** Checks GitHub for a newer PRETORIAN and installs it with one click (same way as LEAD Launcher). */
object Updater {
    private const val JSON_URL = "https://github.com/KarlAgathon/PRETORIAN/releases/latest/download/update.json"
    /** only APKs from this repository's releases are accepted */
    private const val APK_PREFIX = "https://github.com/KarlAgathon/PRETORIAN/releases/"

    var available by mutableStateOf<UpdateInfo?>(null)
    var progress by mutableStateOf(-1)   // -1 idle, 0..100 downloading, 101 installing
    var status by mutableStateOf<String?>(null)
    var doneBytes by mutableStateOf(0L)
    var totalBytes by mutableStateOf(0L)
    var speed by mutableStateOf(0L)       // bytes per second
    @Volatile var cancel = false
    @Volatile var installStarted = 0L
    /** the system install screen was already tried for this install */
    @Volatile var fellBack = false
    /** shown once per version when you come back from the player */
    var askAfterPlayer by mutableStateOf(false)

    /** "Later" and "Skip" decide whether the update may ask again */
    fun mayAsk(info: UpdateInfo) = Prefs.str("upd_skip") != info.name && System.currentTimeMillis() > Prefs.long("upd_snooze")
    fun snooze(hours: Int) { Prefs.put("upd_snooze", System.currentTimeMillis() + hours * 3600_000L) }
    fun skip(info: UpdateInfo) { Prefs.put("upd_skip", info.name); available = null; Notes.list.removeAll { it.kind == "update" } }
    fun notify(kind: String) = Prefs.str("upd_notify", "bell,badge,player").split(',').contains(kind)

    private fun readyFile(ctx: Context) = File(ctx.cacheDir, "update.apk")
    /** downloaded earlier in the background and still the newest one */
    fun ready(ctx: Context, info: UpdateInfo) = Prefs.str("upd_file") == info.name && readyFile(ctx).length() > 100_000

    /** "Download now in the background": fetched quietly, installed when you say so */
    suspend fun predownload(ctx: Context, info: UpdateInfo) {
        if (ready(ctx, info) || progress in 0..101) return
        try { withContext(Dispatchers.IO) { download(ctx, info.apk) }; Prefs.put("upd_file", info.name); progress = -1 }
        catch (e: Exception) { progress = -1 }
    }

    /** The list of published versions (newest first), straight from GitHub. */
    suspend fun releases(): List<Release> = withContext(Dispatchers.IO) {
        try {
            val a = org.json.JSONArray(String(Http.bytes("https://api.github.com/repos/KarlAgathon/PRETORIAN/releases?per_page=8", 400_000)))  // i18n-ignore
            (0 until a.length()).mapNotNull { i ->
                val o = a.getJSONObject(i)
                val assets = o.optJSONArray("assets") ?: return@mapNotNull null
                val apk = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.str("name").endsWith(".apk") }?.str("browser_download_url") ?: return@mapNotNull null
                if (!apk.startsWith(APK_PREFIX)) return@mapNotNull null
                Release(o.str("tag_name"), o.str("tag_name").removePrefix("v"), o.str("body"), apk, o.str("published_at").take(10))
            }
        } catch (e: Exception) { emptyList() }
    }

    /** An APK picked from a USB stick. */
    suspend fun installBytes(ctx: Context, bytes: ByteArray) {
        val f = File(ctx.cacheDir, "usb.apk"); withContext(Dispatchers.IO) { f.writeBytes(bytes) }
        progress = 101; installStarted = System.currentTimeMillis()
        try { withContext(Dispatchers.IO) { commit(ctx, f) } } catch (e: Exception) { progress = -1; status = trf("Ažuriranje nije uspjelo: %s", e.message ?: "") }
    }

    /** Settings into Download/PRETORIAN before the new version goes in (when chosen). */
    fun backupBefore(ctx: Context, tag: String): Boolean = try {
        PublicFiles.save(ctx, "backup-$tag.json", Backup.export(ctx, null).toByteArray()); true
    } catch (e: Exception) { false }

    fun installedCode(ctx: Context): Long {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
    }

    suspend fun check(ctx: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        Prefs.put("upd_checked_at", System.currentTimeMillis())
        try {
            val o = JSONObject(String(Http.bytes(JSON_URL, 100_000)))
            val info = UpdateInfo(o.optLong("versionCode"), o.str("versionName"), o.str("apk"), o.str("notes"))
            Prefs.put("upd_last", System.currentTimeMillis())
            val newer = if (info.code > installedCode(ctx) && info.apk.startsWith(APK_PREFIX) && Prefs.str("upd_skip") != info.name) info else null
            available = newer
            status = if (newer == null) "Imaš najnoviju verziju" else null
            newer
        } catch (e: Exception) {
            status = trf("Provjera nije uspjela: %s", e.message ?: "")
            null
        }
    }

    suspend fun install(ctx: Context, info: UpdateInfo) {
        // a download or an install that is still running blocks a new click: a second click used to throw away the running install
        if (progress in 0..100) return
        if (progress == 101 && System.currentTimeMillis() - installStarted < 180_000L) return
        // only installs older than 3 minutes (a confirm screen closed with Back) are thrown away
        if (System.currentTimeMillis() - installStarted > 180_000L) withContext(Dispatchers.IO) { abandonOld(ctx) }
        progress = -1
        status = null
        cancel = false
        try {
            if (Prefs.str("upd_pre", "backup").split(',').contains("backup")) withContext(Dispatchers.IO) { backupBefore(ctx, "prije-${info.name}") }  // i18n-ignore
            val file = if (ready(ctx, info)) readyFile(ctx) else withContext(Dispatchers.IO) { download(ctx, info.apk) }
            Prefs.put("upd_file", info.name)
            progress = 101
            installStarted = System.currentTimeMillis()
            fellBack = false
            withContext(Dispatchers.IO) { commit(ctx, file) }
        } catch (e: Exception) {
            progress = -1
            status = trf("Ažuriranje nije uspjelo: %s", e.message ?: "")
        }
    }

    private fun download(ctx: Context, url: String, f: File = File(ctx.cacheDir, "update.apk")): File {
        var cur = url
        repeat(6) {
            val c = URL(cur).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000; c.readTimeout = 60_000; c.instanceFollowRedirects = false
            try {
                val code = c.responseCode
                if (code in 300..399) { cur = URL(URL(cur), c.getHeaderField("Location") ?: throw IOException("redirect")).toString(); return@repeat }
                if (code !in 200..299) throw IOException("HTTP $code")
                val total = c.contentLengthLong
                var done = 0L
                progress = 0; totalBytes = total; doneBytes = 0; speed = 0
                val t0 = System.currentTimeMillis()
                c.inputStream.use { input ->
                    f.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            if (cancel) { cancel = false; progress = -1; throw IOException(tr("otkazano")) }
                            val n = input.read(buf); if (n < 0) break
                            out.write(buf, 0, n); done += n
                            doneBytes = done
                            val ms = (System.currentTimeMillis() - t0).coerceAtLeast(1)
                            speed = done * 1000 / ms
                            if (total > 0) progress = ((done * 100) / total).toInt().coerceIn(0, 100)
                        }
                    }
                }
                return f
            } finally { c.disconnect() }
        }
        throw IOException("Previše preusmjeravanja")
    }

    /** forget install sessions that were never answered (confirm screen closed with Back) */
    fun abandonOld(ctx: Context) {
        val pi = ctx.packageManager.packageInstaller
        try { pi.mySessions.forEach { s -> try { pi.abandonSession(s.sessionId) } catch (_: Exception) {} } } catch (_: Exception) {}
    }

    /** back in the app while "installing": the confirm screen was closed, so the button works again */
    fun onResume() {
        // Android may still be installing in the background: the button comes back only after 3 minutes without an answer
        if (progress == 101 && System.currentTimeMillis() - installStarted > 180_000L) { progress = -1; status = tr("Instalacija nije završena: otkazano") }
    }

    /** the last APK handed to Android, for the fallback below */
    @Volatile var lastApk: File? = null

    /** Fallback when the quiet install is thrown away by the box (seen on Google TV): Android's own install screen. */
    fun installWithSystemScreen(ctx: Context): Boolean {
        val f = lastApk?.takeIf { it.exists() } ?: return false
        return try {
            val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
            @Suppress("DEPRECATION")
            val i = Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            ctx.startActivity(i); true
        } catch (e: Exception) { false }
    }

    fun commit(ctx: Context, apk: File) {
        lastApk = apk
        val pi = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(ctx.packageName)
        // Android 12+: no confirm screen once PRETORIAN itself installed the current version
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        val id = pi.createSession(params)
        pi.openSession(id).use { s ->
            apk.inputStream().use { input -> s.openWrite("pretorian.apk", 0, apk.length()).use { out -> input.copyTo(out); s.fsync(out) } }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val cb = PendingIntent.getBroadcast(ctx, id, Intent(ctx, UpdateResultReceiver::class.java), flags)
            s.commit(cb.intentSender)
        }
    }
}

class UpdateResultReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                ctx.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> { Updater.progress = -1 }
            else -> {
                Updater.progress = -1; Updater.installStarted = 0L
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "otkazano"
                // the box threw the session away by itself: open Android's normal install screen with the same file
                val code = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, 0)
                if ((code == PackageInstaller.STATUS_FAILURE_ABORTED || msg.contains("abandon", true)) && !Updater.fellBack) {
                    Updater.fellBack = true
                    if (Updater.installWithSystemScreen(ctx)) { Updater.status = tr("Potvrdi instalaciju na ekranu Androida"); return }
                }
                Updater.status = trf("Instalacija nije završena: %s", msg)
                Toast.makeText(ctx, tr("Ažuriranje nije završeno"), Toast.LENGTH_LONG).show()
            }
        }
    }
}


/** Files in the public Download/PRETORIAN folder: they stay there even when PRETORIAN is uninstalled. */
object PublicFiles {
    fun save(ctx: Context, name: String, bytes: ByteArray) {
        if (Build.VERSION.SDK_INT >= 29) {
            val cv = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/PRETORIAN")
            }
            val uri = ctx.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: throw IOException("Download")
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: throw IOException("Download")
        } else {
            @Suppress("DEPRECATION")
            val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "PRETORIAN")
            dir.mkdirs(); File(dir, name).writeBytes(bytes)
        }
    }
}

/** Going back to an older version: Android can't do it directly, so settings and the old APK go to Download/PRETORIAN, then PRETORIAN is uninstalled. */
object Rollback {
    var busy by mutableStateOf(false)
    suspend fun prepare(ctx: Context, r: Release): String? = withContext(Dispatchers.IO) {
        try {
            busy = true
            PublicFiles.save(ctx, "backup-za-${r.name}.json", Backup.export(ctx, null).toByteArray())
            val f = File(ctx.cacheDir, "old.apk")
            val c = URL(r.apk).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = true; c.connectTimeout = 15_000; c.readTimeout = 60_000
            c.inputStream.use { ins -> f.outputStream().use { ins.copyTo(it) } }
            PublicFiles.save(ctx, "Pretorian-${r.name}.apk", f.readBytes())
            PublicFiles.save(ctx, "vrati.json", JSONObject().put("version", r.name).put("apk", "Pretorian-${r.name}.apk").put("backup", "backup-za-${r.name}.json").toString().toByteArray())
            f.delete()
            null
        } catch (e: Exception) { e.message ?: tr("nije uspjelo") } finally { busy = false }
    }
    fun uninstall(ctx: Context) {
        try { ctx.startActivity(Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
    }
}

/** A new version that keeps crashing: after 3 crashes in 10 minutes the next start offers a way out. */
object CrashGuard {
    fun record() {
        val now = System.currentTimeMillis()
        val list = Prefs.str("crash_times").split(',').mapNotNull { it.toLongOrNull() }.filter { now - it < 10 * 60_000L } + now
        Prefs.putNow("crash_times", list.joinToString(","))
    }
    fun tooMany() = Prefs.str("crash_times").split(',').mapNotNull { it.toLongOrNull() }.count { System.currentTimeMillis() - it < 10 * 60_000L } >= 3
    fun clear() = Prefs.put("crash_times", "")
}
