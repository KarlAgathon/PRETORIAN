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

/**
 * Crashes and freezes (2.8.0). Each one becomes an incident in [DiagStore] (up to 100 kept on the box, older than
 * 3 days compressed). The crash handler writes it synchronously before the process ends and remembers it as
 * "pending", so the next start shows the crash dialog with a direct export (once per incident).
 * At start, Android's own record of how the last processes ended (API 30+) adds ANRs and native crashes that the
 * Java crash handler cannot see. The app's own log is saved once per incident after the restart; Android only lets an
 * app read its own log lines, and lines from before the crash may already be gone.
 */
object CrashLog {
    /** the incidents, readable from a laptop: adb pull [ADB_DIR] */
    fun dir(c: Context): File? = File(DiagStore.dir(c), "incidents")
    val ADB_DIR get() = "/sdcard/Android/data/" + BuildConfig.APPLICATION_ID + "/files/diag"  // i18n-ignore
    private fun header(c: Context): String {
        val rt = Runtime.getRuntime()
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo(); am.getMemoryInfo(mi)
        return "Media Center ${BuildInfo.VERSION} (${BuildConfig.VERSION_CODE}) · ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE}\n" +  // i18n-ignore
            "time ${DiagEvents.stamp()} · screen ${screen}\n" +  // i18n-ignore
            "app heap ${(rt.totalMemory() - rt.freeMemory()) / 1_048_576} / ${rt.maxMemory() / 1_048_576} MB · box free ${mi.availMem / 1_048_576} MB · low ${mi.lowMemory}\n"  // i18n-ignore
    }
    /** the screen open right now (written into the report) */
    @Volatile var screen = ""

    /** an incident (crash, freeze, import …); returns its name */
    fun archive(c: Context, kind: String, text: String): String? = try { DiagStore.incident(kind.lowercase().filter { it in 'a'..'z' }.ifBlank { "other" }, text) } catch (_: Throwable) { null }  // i18n-ignore

    /** remembered for the dialog at the next start: the incident's name and a short summary (kept small, already redacted) */
    private fun pending(base: String?, summary: String) {
        if (base == null) { Prefs.put("diag_pending_sum", Redact.text(summary).take(3000)); Prefs.put("diag_pending", "nofile"); return }
        Prefs.put("diag_pending", base); Prefs.put("diag_pending_sum", Redact.text(summary).take(3000))
    }

    fun install(c: Context) {
        val app = c.applicationContext
        DiagStore.init(app)
        File(app.filesDir, "last_crash.txt").delete()   // the old single-file report is replaced by the store
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        if (prev is Handler) return
        Thread.setDefaultUncaughtExceptionHandler(Handler(app, prev))
        Thread({ startupChecks(app) }, "pretorian-diag-start")  // i18n-ignore
           .apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
    }

    private fun startupChecks(app: Context) {
        try {
            // version: first start after an update is written down
            val was = Prefs.str("diag_cur_version")
            if (was != BuildConfig.VERSION_NAME) { if (was.isNotBlank()) Prefs.put("diag_prev_version", was); Prefs.put("diag_cur_version", BuildConfig.VERSION_NAME) }
            DiagEvents.add("app", "start " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")" + (if (was.isNotBlank() && was != BuildConfig.VERSION_NAME) " · updated from $was" else ""))  // i18n-ignore
            exitReasons(app)
            // the app log for the incident of the last run: once
            val p = Prefs.str("diag_pending")
            if (p.isNotBlank() && p != "nofile" && Prefs.str("diag_log_for") != p) {  // i18n-ignore
                Prefs.put("diag_log_for", p)
                val log = try { Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "1500", "-v", "threadtime")).inputStream.bufferedReader().use { r -> r.readText().takeLast(400_000) } } catch (_: Throwable) { null }  // i18n-ignore
                if (log != null) DiagStore.attachLog(p, log)
            }
        } catch (_: Throwable) {}
    }

    /** Android's record of how earlier runs ended (Android 11+): ANRs, native crashes, kills for low memory */
    private fun exitReasons(app: Context) {
        if (android.os.Build.VERSION.SDK_INT < 30) return
        try {
            val am = app.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val seen = Prefs.long("diag_exit_seen", 0)
            val list = am.getHistoricalProcessExitReasons(app.packageName, 0, 10).filter { it.timestamp > seen }.sortedBy { it.timestamp }
            list.forEach { e ->
                val why = when (e.reason) {
                    android.app.ApplicationExitInfo.REASON_ANR -> "anr"; android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> "native"
                    android.app.ApplicationExitInfo.REASON_CRASH -> "crash"; android.app.ApplicationExitInfo.REASON_LOW_MEMORY -> "lowmem"
                    android.app.ApplicationExitInfo.REASON_EXIT_SELF -> "exit"; android.app.ApplicationExitInfo.REASON_USER_REQUESTED -> "user"
                    android.app.ApplicationExitInfo.REASON_SIGNALED -> "signal"; else -> "other" }  // i18n-ignore
                // 2.9.1: an update or a normal close is information, not an error
                val normal = why == "exit" || why == "user" || (e.description ?: "").contains("installPackage")
                DiagEvents.add(if (why == "anr" || why == "native") why else if (normal) "app" else "exitinfo", "previous run ended: $why · ${e.description ?: ""} · pss ${e.pss / 1024} MB")  // i18n-ignore
                // 3.0.0.1: killed for memory while on screen = an out-of-memory incident (in the background it is normal Android)
                if (why == "lowmem" && e.importance <= android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    archive(app, "oom", "Out of memory: Android ended the app while it was on screen at ${DiagEvents.stamp(e.timestamp)} · pss ${e.pss / 1024} MB · rss ${e.rss / 1024} MB\n${e.description ?: ""}")  // i18n-ignore
                }
                if (why == "anr" || why == "native") {
                    val trace = try { e.traceInputStream?.bufferedReader()?.use { r -> val cb = CharArray(200_000); val n = r.read(cb); if (n > 0) String(cb, 0, n) else "" } } catch (_: Throwable) { null }
                    val txt = "Media Center ${BuildInfo.VERSION} · ${android.os.Build.MODEL}\n" +  // i18n-ignore
                        "${if (why == "anr") "ANR (the app stopped answering)" else "Native crash"} at ${DiagEvents.stamp(e.timestamp)}\n${e.description ?: ""}\n\n" +  // i18n-ignore
                        (trace ?: "(Android did not keep a trace for this one)")  // i18n-ignore
                    val base = archive(app, why, txt)
                    pending(base, txt.take(1500))
                }
            }
            list.maxOfOrNull { it.timestamp }?.let { Prefs.put("diag_exit_seen", it) }
        } catch (_: Throwable) {}
    }

    private class Handler(val app: Context, val prev: Thread.UncaughtExceptionHandler?) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(t: Thread, e: Throwable) {
            try {
                val sw = java.io.StringWriter(); e.printStackTrace(java.io.PrintWriter(sw))   // the whole trace with every "Caused by"
                DiagEvents.add("crash", e.javaClass.name + ": " + e.message + " · thread " + t.name)  // i18n-ignore
                DiagStore.flushNow()
                val txt = header(app) + "thread ${t.name}\n\n" + sw.toString() + "\n\n== Recent events ==\n" + DiagEvents.lines().takeLast(200).joinToString("\n")  // i18n-ignore
                val base = archive(app, "crash", txt)
                pending(base, header(app) + sw.toString().take(2000))
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
                val sent = android.os.SystemClock.uptimeMillis()
                main.post { tick = sent }
                try { Thread.sleep(6_000) } catch (_: InterruptedException) { return@Thread }
                if (tick != sent) {
                    if (!reported) try {
                        val st = android.os.Looper.getMainLooper().thread.stackTrace.joinToString("\n") { "  at $it" }
                        val txt = header(app) + "FREEZE: the screen did not answer for more than 6 s\n" + st  // i18n-ignore
                        DiagEvents.add("freeze", "main thread blocked > 6 s on " + screen)  // i18n-ignore
                        // the storage floor applies here too (DiagStore.incident checks it)
                        val base = archive(app, "freeze", txt)
                        pending(base, txt.take(2000))
                        reported = true
                    } catch (_: Throwable) {}
                } else reported = false
            }
        }, "pretorian-watchdog").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
    }
}

/**
 * After a crash or a freeze: what happened in short, and a direct export of the report (no Settings needed).
 * Shown once per incident; Close / Back only acknowledge it — the incident stays on the box.
 */
@Composable
fun CrashDialog() {
    val pend = remember { Prefs.str("diag_pending").takeIf { it.isNotBlank() && it != Prefs.str("diag_ack") } }
    var open by remember { mutableStateOf(pend != null) }
    var export by remember { mutableStateOf(false) }
    if (pend == null) return
    fun ack() { Prefs.put("diag_ack", pend); open = false }
    if (open) Popup("Aplikacija je imala problem", { ack() }, width = 820.dp) {
        T("Prošli put se aplikacija srušila ili zamrznula. Izvještaj je sačuvan na uređaju.", 14.sp, C.Soft, maxLines = 3)
        val lines = Prefs.str("diag_pending_sum").lines()
        val key = (lines.take(2) +
            lines.filter { it.contains("Exception") || it.contains("Error") || it.startsWith("Caused by") || it.startsWith("FREEZE") || it.startsWith("ANR") }.take(3) +
            lines.filter { it.contains("com.pretorian") }.take(3)).distinct()  // i18n-ignore
        Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
            key.forEach { T(it.trim(), 13.sp, C.Text, maxLines = 2) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button("Izvezi dijagnostički izvještaj", R.drawable.lc_download, primary = true) { ack(); export = true }
            Button("Zatvori", R.drawable.lc_x) { ack() }
        }
    }
    if (export) DiagExportDialog(DiagReport.Kind.LATEST) { export = false }
}
