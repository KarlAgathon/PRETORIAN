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
    /** Beta: test versions, published as the release "beta" (only for whoever chose the Beta channel) */
    private const val BETA_URL = "https://github.com/KarlAgathon/PRETORIAN/releases/download/beta/update.json"
    /** "stable" or "beta" */
    val beta get() = Prefs.str("upd_channel", if (BuildConfig.APPLICATION_ID.endsWith(".beta")) "beta" else "stable") == "beta"  // i18n-ignore
    /** the APK's signature against the installed PRETORIAN: true ok, false foreign or broken, null not checked yet */
    var signature by mutableStateOf<Boolean?>(null)
    /** download paused by the user: the part already loaded stays and goes on from there */
    var paused by mutableStateOf(false)
    @Volatile private var pauseReq = false
    /** "Install" clicked while the background download runs: install as soon as it is done */
    @Volatile var installWhenDone = false
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
    /** the one install session that counts: answers from older sessions are ignored (they used to start a second install) */
    @Volatile var session = -1
    /** when Android's confirm screen was opened (0 = not opened) */
    @Volatile var confirmAt = 0L
    /** Android's "install this update?" screen, opened by the visible activity */
    var confirm by androidx.compose.runtime.mutableStateOf<Intent?>(null)
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
        try {
            withContext(Dispatchers.IO) { download(ctx, info) }; Prefs.put("upd_file", info.name); progress = -1
            if (installWhenDone) { installWhenDone = false; install(ctx, info) }
        } catch (e: Exception) { progress = -1; if (!paused) installWhenDone = false }
    }

    /** The list of published versions (newest first), straight from GitHub. */
    suspend fun releases(): List<Release> = withContext(Dispatchers.IO) {
        try {
            val a = org.json.JSONArray(String(Http.bytes("https://api.github.com/repos/KarlAgathon/PRETORIAN/releases?per_page=8", 400_000)))  // i18n-ignore
            (0 until a.length()).mapNotNull { i ->
                val o = a.getJSONObject(i)
                if (o.optBoolean("prerelease") || o.str("tag_name") == "beta") return@mapNotNull null
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
        PublicFiles.save(ctx, "backup-$tag.json", Backup.export(ctx, Backup.pass.ifBlank { null }).toByteArray()); true
    } catch (e: Exception) { false }

    fun installedCode(ctx: Context): Long {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
    }

    /** the newest Stable version (for the gold STABLE shield) */
    var stableName by mutableStateOf(Prefs.str("upd_stable_name"))
    /** Settings › Update › long OK on the version: the author's own GitHub key (only on his box) */
    val canPublish get() = Prefs.str("gh_token").isNotBlank()

    /** Makes the Beta version on this box the Stable one for everybody: main moves to the beta commit, GitHub builds the release. */
    suspend fun promote(): String? = withContext(Dispatchers.IO) {
        val tok = Prefs.str("gh_token").trim(); if (tok.isBlank()) return@withContext tr("GitHub ključ nije upisan")
        fun call(method: String, path: String, body: String? = null): Pair<Int, String> {
            val c = URL("https://api.github.com/repos/KarlAgathon/PRETORIAN$path").openConnection() as HttpURLConnection  // i18n-ignore
            c.requestMethod = method   // Android's HttpURLConnection knows PATCH
            c.setRequestProperty("Authorization", "Bearer $tok"); c.setRequestProperty("Accept", "application/vnd.github+json")  // i18n-ignore
            c.connectTimeout = 15_000; c.readTimeout = 20_000
            if (body != null) { c.doOutput = true; c.setRequestProperty("Content-Type", "application/json"); c.outputStream.use { it.write(body.toByteArray()) } }  // i18n-ignore
            val code = c.responseCode
            val text = try { (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: "" } catch (e: Exception) { "" }
            c.disconnect(); return code to text
        }
        try {
            val (c1, t1) = call("GET", "/git/ref/heads/beta")
            if (c1 != 200) return@withContext trf("GitHub: %s", c1)
            val sha = JSONObject(t1).getJSONObject("object").getString("sha")
            val (c2, t2) = call("PATCH", "/git/refs/heads/main", JSONObject().put("sha", sha).put("force", false).toString())
            if (c2 !in 200..299) return@withContext trf("GitHub: %s", c2) + " " + (try { JSONObject(t2).optString("message") } catch (e: Exception) { "" })
            UpdLog.add(trf("Označeno kao Stable: %s · GitHub pravi izdanje", BuildInfo.VERSION))
            null
        } catch (e: Exception) { e.message ?: tr("nije uspjelo") }
    }

    suspend fun check(ctx: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        Prefs.put("upd_checked_at", System.currentTimeMillis())
        try {
            fun read(u: String) = JSONObject(String(Http.bytes(u, 100_000))).let { o -> UpdateInfo(o.optLong("versionCode"), o.str("versionName"), o.str("apk"), o.str("notes")) }
            val stable = read(JSON_URL)
            stableName = stable.name; Prefs.put("upd_stable_name", stable.name)
            // Beta gets the test version when it is newer than the stable one
            val info = if (beta) (try { read(BETA_URL) } catch (e: Exception) { null })?.takeIf { it.code > stable.code } ?: stable else stable
            Prefs.put("upd_last", System.currentTimeMillis())
            val newer = if (info.code > installedCode(ctx) && info.apk.startsWith(APK_PREFIX) && Prefs.str("upd_skip") != info.name) info else null
            available = newer
            status = if (newer == null) "Imaš najnoviju verziju" else null
            if (newer != null && Prefs.str("upd_log_seen") != newer.name) { Prefs.put("upd_log_seen", newer.name); UpdLog.add(trf("Provjera: nova verzija %s nađena (imaš %s)", newer.name, BuildInfo.VERSION)) }
            newer
        } catch (e: Exception) {
            UpdLog.add(trf("Provjera nije uspjela: %s", e.message ?: ""))
            status = trf("Provjera nije uspjela: %s", e.message ?: "")
            null
        }
    }

    suspend fun install(ctx: Context, info: UpdateInfo) {
        // a download or an install that is still running blocks a new click: a second click used to throw away the running install
        if (progress in 0..100) {
            // the background download is still running: install as soon as it finishes (the click is never lost)
            installWhenDone = true; status = tr("Preuzimam… instalacija kreće čim završi"); UpdLog.add(tr("Instaliraj kliknuto dok traje preuzimanje: instaliram čim završi")); return
        }
        if (progress == 101 && System.currentTimeMillis() - installStarted < 60_000L) return
        // leftovers of earlier tries are thrown away quietly: their answers no longer count
        session = -1; confirmAt = 0L
        withContext(Dispatchers.IO) { abandonOld(ctx) }
        progress = -1
        status = null
        cancel = false
        paused = false; pauseReq = false
        try {
            if (Prefs.str("upd_pre", "").split(',').contains("backup")) withContext(Dispatchers.IO) { backupBefore(ctx, "prije-${info.name}") }  // i18n-ignore
            UpdLog.add(trf("Instalacija %s pokrenuta", info.name))
            val wasReady = ready(ctx, info)
            val file = if (wasReady) readyFile(ctx) else withContext(Dispatchers.IO) { download(ctx, info) }
            UpdLog.add(if (wasReady) trf("Fajl već preuzet · %s MB", file.length() / 1_048_576) else trf("Preuzimanje gotovo · %s MB", file.length() / 1_048_576))
            // the file must carry the same signature as the installed PRETORIAN
            val sig = withContext(Dispatchers.IO) { signatureOk(ctx, file) }
            signature = sig
            if (sig == false) {
                file.delete(); Prefs.put("upd_file", ""); progress = -1
                UpdLog.add(tr("Potpis ne odgovara: instalacija zaustavljena, fajl obrisan"))
                status = tr("Potpis ne odgovara – fajl nije od PRETORIAN-a ili je oštećen"); return
            }
            UpdLog.add(if (sig == true) tr("Potpis provjeren ✓") else tr("Potpis se ne može provjeriti na ovom Androidu"))
            Prefs.put("upd_file", info.name)
            progress = 101
            installStarted = System.currentTimeMillis()
            fellBack = false
            withContext(Dispatchers.IO) { commit(ctx, file) }
        } catch (e: Exception) {
            progress = -1
            if (paused) { UpdLog.add(trf("Preuzimanje pauzirano · %s MB", doneBytes / 1_048_576)); status = tr("Pauzirano"); return }
            UpdLog.add(trf("Greška: %s", e.message ?: e.javaClass.simpleName))
            status = trf("Ažuriranje nije uspjelo: %s", e.message ?: "")
        }
    }

    fun pause() { pauseReq = true }

    /** same signing certificate as the installed app (Android 9+); null when it cannot be read */
    fun signatureOk(ctx: Context, apk: File): Boolean? {
        if (Build.VERSION.SDK_INT < 28) return null
        return try {
            val pm = ctx.packageManager
            val a = pm.getPackageArchiveInfo(apk.path, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES) ?: return false
            if (a.packageName != ctx.packageName) return false
            val si = a.signingInfo ?: return null
            val mine = pm.getPackageInfo(ctx.packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES).signingInfo ?: return null
            fun certs(x: android.content.pm.SigningInfo) = (if (x.hasMultipleSigners()) x.apkContentsSigners else x.signingCertificateHistory)
                .map { java.security.MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } }.toSet()
            certs(si).intersect(certs(mine)).isNotEmpty()
        } catch (e: Exception) { null }
    }

    /**
     * Downloads the APK piece by piece into "update.apk.part": a stall (20 s without a byte) or a broken connection
     * goes on by itself from where it stopped (up to 3 times), Pause keeps the part, Cancel throws it away.
     */
    private fun download(ctx: Context, info: UpdateInfo, f: File = File(ctx.cacheDir, "update.apk")): File {
        val part = File(f.path + ".part")
        if (Prefs.str("upd_part") != info.name) { part.delete(); Prefs.put("upd_part", info.name) }
        var tries = 0
        while (true) {
            try { fetch(info.apk, part); break }
            catch (e: IOException) {
                if (paused || cancel) { if (cancel) { cancel = false; part.delete(); progress = -1 }; throw e }
                tries++
                if (tries > 3) throw e
                UpdLog.add(trf("Preuzimanje zastalo (%s), nastavljam od %s MB · pokušaj %s/3", e.message ?: "", part.length() / 1_048_576, tries))
                Thread.sleep(2000L * tries)
            }
        }
        f.delete(); if (!part.renameTo(f)) { part.copyTo(f, true); part.delete() }
        Prefs.put("upd_part", "")
        return f
    }

    private fun fetch(url: String, part: File) {
        var cur = url
        repeat(6) {
            val have = part.length()
            val c = URL(cur).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000; c.readTimeout = 20_000; c.instanceFollowRedirects = false
            if (have > 0) c.setRequestProperty("Range", "bytes=$have-")
            try {
                val code = c.responseCode
                if (code in 300..399) { cur = URL(URL(cur), c.getHeaderField("Location") ?: throw IOException("redirect")).toString(); return@repeat }
                if (code == 416) return   // already complete
                if (code !in 200..299) throw IOException("HTTP $code")
                val append = code == 206 && have > 0
                val start = if (append) have else 0L
                val total = start + c.contentLengthLong.coerceAtLeast(0)
                var done = start
                progress = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else 0
                totalBytes = total; doneBytes = done; speed = 0; paused = false
                val t0 = System.currentTimeMillis()
                c.inputStream.use { input ->
                    java.io.FileOutputStream(part, append).use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            if (cancel) throw IOException(tr("otkazano"))
                            if (pauseReq) { pauseReq = false; paused = true; throw IOException(tr("pauzirano")) }
                            val n = input.read(buf); if (n < 0) break
                            out.write(buf, 0, n); done += n
                            doneBytes = done
                            val ms = (System.currentTimeMillis() - t0).coerceAtLeast(1)
                            speed = (done - start) * 1000 / ms
                            if (total > 0) progress = ((done * 100) / total).toInt().coerceIn(0, 100)
                        }
                    }
                }
                if (total > 0 && done < total) throw IOException(tr("veza prekinuta"))
                return
            } finally { c.disconnect() }
        }
        throw IOException("Previše preusmjeravanja")
    }

    /** forget install sessions that were never answered (confirm screen closed with Back) */
    fun abandonOld(ctx: Context) {
        val pi = ctx.packageManager.packageInstaller
        try { pi.mySessions.forEach { s -> try { pi.abandonSession(s.sessionId) } catch (_: Exception) {} } } catch (_: Exception) {}
    }

    /** back in the app while "installing" */
    fun onResume(ctx: Context) {
        val now = System.currentTimeMillis()
        // the confirm screen closed by itself without any answer from Android: one try with Android's normal install screen
        if (progress == 101 && session != -1 && confirmAt > 0 && now - confirmAt < 15_000L && !fellBack) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (progress == 101 && session != -1 && !fellBack) systemFallback(ctx, tr("Ekran za potvrdu se zatvorio bez odgovora"))
            }, 4000)
            return
        }
        if (progress == 101 && now - installStarted > 120_000L) { progress = -1; session = -1; status = tr("Instalacija nije završena: otkazano") }
    }

    /** the only second way: the running session is dropped first (its answer is ignored), then Android's install screen opens */
    fun systemFallback(ctx: Context, why: String) {
        fellBack = true
        val old = session; session = -1
        if (old != -1) try { ctx.packageManager.packageInstaller.abandonSession(old) } catch (_: Exception) {}
        UpdLog.add(why)
        if (installWithSystemScreen(ctx)) { UpdLog.add(tr("Otvaram Androidov ekran za instalaciju")); status = tr("Potvrdi instalaciju na ekranu Androida") }
        else { progress = -1; status = tr("Ažuriranje nije završeno") }
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
        session = id
        pi.openSession(id).use { s ->
            apk.inputStream().use { input -> s.openWrite("pretorian.apk", 0, apk.length()).use { out -> input.copyTo(out); s.fsync(out) } }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val cb = PendingIntent.getBroadcast(ctx, id, Intent(ctx, UpdateResultReceiver::class.java), flags)
            s.commit(cb.intentSender)
        }
        UpdLog.add(trf("Predato Androidu za instalaciju (sesija %s)", id))
    }
}

class UpdateResultReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        // only the install started last counts: an old, thrown-away session must not touch the running one
        val sid = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        if (sid != Updater.session) return
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                // Android 14 blocks a screen opened from a broadcast in the background, and the box then throws the install away:
                // the open PRETORIAN screen opens the confirm screen itself
                Updater.confirm = confirm
                UpdLog.add(tr("Android traži potvrdu → otvaram ekran za potvrdu"))
            }
            PackageInstaller.STATUS_SUCCESS -> { Updater.progress = -1; Updater.session = -1; UpdLog.add(tr("Instalirano ✓")) }
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "otkazano"
                val code = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, 0)
                UpdLog.add(trf("Android odbio (%s): %s", code, msg))
                // the box closed its own confirm screen at once (no time to answer): Android's normal install screen, once
                val quick = Updater.confirmAt > 0 && System.currentTimeMillis() - Updater.confirmAt < 4000L
                if (code == PackageInstaller.STATUS_FAILURE_ABORTED && quick && !Updater.fellBack) { Updater.systemFallback(ctx, tr("Ekran za potvrdu se odmah zatvorio")); return }
                Updater.progress = -1; Updater.installStarted = 0L; Updater.session = -1
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
            PublicFiles.save(ctx, "backup-za-${r.name}.json", Backup.export(ctx, Backup.pass.ifBlank { null }).toByteArray())
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
    /** when this start of the app began: a crash within 15 s counts as "right at start" */
    @Volatile var startedAt = System.currentTimeMillis()
    fun record() {
        val now = System.currentTimeMillis()
        val list = Prefs.str("crash_times").split(',').mapNotNull { it.toLongOrNull() }.filter { now - it < 10 * 60_000L } + now
        Prefs.putNow("crash_times", list.joinToString(","))
        // crashes right at start, one after another: after 5 the next start goes straight to the way back
        val quick = if (now - startedAt < 15_000L) Prefs.long("crash_quick") + 1 else 0L
        Prefs.putNow("crash_quick", quick.toString())
    }
    fun quick() = (Prefs.str("crash_quick").toLongOrNull() ?: 0L) >= 5
    fun clearQuick() = Prefs.put("crash_quick", "0")
    fun tooMany() = Prefs.str("crash_times").split(',').mapNotNull { it.toLongOrNull() }.count { System.currentTimeMillis() - it < 10 * 60_000L } >= 3
    fun clear() = Prefs.put("crash_times", "")
}


/** Every step of an update with its time, for Settings › Diagnostics › Update log (the last 50). */
object UpdLog {
    var rev by androidx.compose.runtime.mutableIntStateOf(0)
    fun add(text: String) {
        val t = java.text.SimpleDateFormat("dd.MM. HH:mm:ss", java.util.Locale.US).format(java.util.Date())  // i18n-ignore
        val lines = (Prefs.str("upd_log").split('\n').filter { it.isNotBlank() } + (t + "  " + text)).takeLast(50)
        Prefs.putNow("upd_log", lines.joinToString("\n")); rev++
    }
    fun lines() = Prefs.str("upd_log").split('\n').filter { it.isNotBlank() }
    fun clear() { Prefs.put("upd_log", ""); rev++ }
}
