package com.pretorian.tv

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/** keeps the last crash so it can be shown at the next start (no adb needed) */
object CrashLog {
    private fun file(c: Context) = File(c.filesDir, "last_crash.txt")
    /** every crash and freeze is also kept here (last 10), readable from a laptop: adb pull [ADB_DIR] */
    fun dir(c: Context): File? = c.getExternalFilesDir(null)?.let { File(it, "crash") }
    const val ADB_DIR = "/sdcard/Android/data/com.pretorian.tv/files/crash"  // i18n-ignore
    private fun header(c: Context): String {
        val rt = Runtime.getRuntime()
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo(); am.getMemoryInfo(mi)
        return "Media Center ${BuildInfo.VERSION} · ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE}\n" +  // i18n-ignore
            "time ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())} · screen ${screen}\n" +  // i18n-ignore
            "app heap ${(rt.totalMemory() - rt.freeMemory()) / 1_048_576} / ${rt.maxMemory() / 1_048_576} MB · box free ${mi.availMem / 1_048_576} MB · low ${mi.lowMemory}\n"  // i18n-ignore
    }
    /** the screen open right now (written into the report) */
    @Volatile var screen = ""
    fun archive(c: Context, kind: String, text: String) {
        try {
            val d = dir(c) ?: return; d.mkdirs()
            File(d, kind + "_" + java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date()) + ".txt").writeText(Redact.text(text))  // i18n-ignore
            d.listFiles()?.sortedByDescending { it.lastModified() }?.drop(10)?.forEach { it.delete() }
        } catch (_: Throwable) {}
    }
    /** after a crash: the app's own log from just before, next to the report */
    fun saveLogAfterCrash(c: Context) {
        Thread {
            try {
                val log = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "800")).inputStream.bufferedReader().readText()  // i18n-ignore
                archive(c, "log", log)
            } catch (_: Throwable) {}
        }.start()
    }

    fun install(c: Context) {
        val app = c.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        if (prev is Handler) return
        Thread.setDefaultUncaughtExceptionHandler(Handler(app, prev))
        if (file(app).exists()) saveLogAfterCrash(app)
    }

    private class Handler(val app: Context, val prev: Thread.UncaughtExceptionHandler?) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(t: Thread, e: Throwable) {
            try {
                val sw = java.io.StringWriter(); e.printStackTrace(java.io.PrintWriter(sw))
                val txt = header(app) + sw.toString().take(12000)
                file(app).writeText(Redact.text(txt))
                archive(app, "crash", txt)
            } catch (_: Throwable) {}
            try { CrashGuard.record() } catch (_: Throwable) {}
            prev?.uncaughtException(t, e)
        }
    }

    /** Freeze watch: if the screen stops answering for 6 s, the place where it hangs is written down and shown next time. */
    @Volatile private var tick = 0L
    private var watching = false
    fun watch(c: Context) {
        if (watching) return; watching = true
        val app = c.applicationContext
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        Thread({
            var reported = false
            while (true) {
                val sent = System.currentTimeMillis()
                main.post { tick = sent }
                try { Thread.sleep(6_000) } catch (_: InterruptedException) { return@Thread }
                if (tick != sent) {
                    if (!reported) try {
                        val st = android.os.Looper.getMainLooper().thread.stackTrace.joinToString("\n") { "  at $it" }
                        val txt = header(app) + "ZAMRZNUTO (ANR) > 6 s\n" + st.take(8000)  // i18n-ignore
                        file(app).writeText(Redact.text(txt)); archive(app, "freeze", txt)
                        reported = true
                    } catch (_: Throwable) {}
                } else reported = false
            }
        }, "pretorian-watchdog").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
    }

    fun take(c: Context): String? = try { file(c).takeIf { it.exists() }?.let { val s = it.readText(); it.delete(); s } } catch (e: Exception) { null }
}

@Composable
fun CrashDialog() {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf(CrashLog.take(ctx)) }
    val t = text ?: return
    Popup("Aplikacija se prošli put srušila", { text = null }, width = 900.dp) {
        T("Slikaj ovaj ekran i pošalji ga, da se greška može popraviti.", 14.sp, C.Soft)
        Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
            T(t, 11.sp, C.Muted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button("Zatvori", R.drawable.lc_x, primary = true) { text = null }
        }
    }
}
