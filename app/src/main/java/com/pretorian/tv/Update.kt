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

data class UpdateInfo(val code: Long, val name: String, val apk: String, val notes: String)

/** Checks GitHub for a newer PRETORIAN and installs it with one click (same way as LEAD Launcher). */
object Updater {
    private const val JSON_URL = "https://github.com/KarlAgathon/PRETORIAN/releases/latest/download/update.json"
    /** only APKs from this repository's releases are accepted */
    private const val APK_PREFIX = "https://github.com/KarlAgathon/PRETORIAN/releases/"

    var available by mutableStateOf<UpdateInfo?>(null)
    var progress by mutableStateOf(-1)   // -1 idle, 0..100 downloading, 101 installing
    var status by mutableStateOf<String?>(null)

    fun installedCode(ctx: Context): Long {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
    }

    suspend fun check(ctx: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val o = JSONObject(String(Http.bytes(JSON_URL, 100_000)))
            val info = UpdateInfo(o.optLong("versionCode"), o.str("versionName"), o.str("apk"), o.str("notes"))
            Prefs.put("upd_last", System.currentTimeMillis())
            val newer = if (info.code > installedCode(ctx) && info.apk.startsWith(APK_PREFIX)) info else null
            available = newer
            status = if (newer == null) "Imaš najnoviju verziju" else null
            newer
        } catch (e: Exception) {
            status = trf("Provjera nije uspjela: %s", e.message ?: "")
            null
        }
    }

    suspend fun install(ctx: Context, info: UpdateInfo) {
        if (progress >= 0) return
        status = null
        try {
            val file = withContext(Dispatchers.IO) { download(ctx, info.apk) }
            progress = 101
            withContext(Dispatchers.IO) { commit(ctx, file) }
        } catch (e: Exception) {
            progress = -1
            status = trf("Ažuriranje nije uspjelo: %s", e.message ?: "")
        }
    }

    private fun download(ctx: Context, url: String): File {
        val f = File(ctx.cacheDir, "update.apk")
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
                progress = 0
                c.inputStream.use { input ->
                    f.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf); if (n < 0) break
                            out.write(buf, 0, n); done += n
                            if (total > 0) progress = ((done * 100) / total).toInt().coerceIn(0, 100)
                        }
                    }
                }
                return f
            } finally { c.disconnect() }
        }
        throw IOException("Previše preusmjeravanja")
    }

    private fun commit(ctx: Context, apk: File) {
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
                Updater.progress = -1
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "otkazano"
                Updater.status = trf("Instalacija nije završena: %s", msg)
                Toast.makeText(ctx, tr("Ažuriranje nije završeno"), Toast.LENGTH_LONG).show()
            }
        }
    }
}
