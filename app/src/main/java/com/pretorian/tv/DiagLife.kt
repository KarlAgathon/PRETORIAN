package com.pretorian.tv

import android.content.Context
import android.os.Build

/**
 * 3.0.0.7 Diagnostics II — the moments of the app's life: this session's version (section 68), an update and what it kept
 * (section 66), how the previous run ended (section 65), firmware changes (section 64) and exports (section 69).
 * Only local facts; no network lookup of any kind. Runs once per start on a low-priority background thread.
 */
object DiagLife {
    /** aggregate counts that must survive an update; never the content itself */
    fun counts(ctx: Context): Map<String, Int> {
        val m = LinkedHashMap<String, Int>()
        fun put(k: String, f: () -> Int) { m[k] = try { f() } catch (_: Throwable) { -1 } }
        put("playlists") { Store.playlists(ctx).size }  // i18n-ignore
        put("favourite channels") { Favs.all(ctx).size }  // i18n-ignore
        put("recently watched channels") { Recent.list().size }  // i18n-ignore
        put("progress entries") { Watch.stats(ctx).first }  // i18n-ignore
        put("watched (finished) entries") { Watch.stats(ctx).second }  // i18n-ignore
        put("settings stored") { Prefs.allKeys().size }  // i18n-ignore
        return m
    }
    private fun enc(m: Map<String, Int>) = m.entries.joinToString(";") { "${it.key}=${it.value}" }  // i18n-ignore
    private fun dec(s: String): Map<String, Int> = s.split(';').mapNotNull { p -> p.split('=').takeIf { it.size == 2 }?.let { it[0] to (it[1].toIntOrNull() ?: -1) } }.toMap()

    fun start(ctx: Context) {
        Thread({ try { Thread.sleep(8_000); run(ctx.applicationContext) } catch (_: Throwable) {} }, "pretorian-diag2").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()  // i18n-ignore
    }

    private fun run(ctx: Context) {
        DiagII.appCtx = ctx
        // previous session (for the exit records), then this one
        val prevSession = Prefs.str("diag2_last_session"); val prevVersion = Prefs.str("diag2_last_version")  // i18n-ignore
        DiagCollect.guard("life") {  // i18n-ignore
            PerfLog.rowII("sessions", listOf(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, BuildConfig.GIT_COMMIT, BuildConfig.BUILD_TYPE, Build.VERSION.SDK_INT,  // i18n-ignore
                DeviceIdentity.short(), if (Monitor.startMs > 0) Monitor.startMs else ""))  // i18n-ignore
            Prefs.put("diag2_last_session", PerfLog.session); Prefs.put("diag2_last_version", BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")")  // i18n-ignore
        }
        DiagCollect.guard("exit2") { exits(ctx, prevSession, prevVersion) }  // i18n-ignore
        DiagCollect.guard("firmware") {  // i18n-ignore
            val fp = Build.FINGERPRINT ?: ""; val was = Prefs.str("diag2_fw")  // i18n-ignore
            if (was.isNotBlank() && was != fp) { DiagEvents.add("app", "Android firmware changed since the last start: ${was.take(120)} → ${fp.take(120)}"); Prefs.put("diag2_fw_changed_at", System.currentTimeMillis()) }  // i18n-ignore
            Prefs.put("diag2_fw", fp)  // i18n-ignore
        }
        DiagCollect.guard("update") {  // i18n-ignore
            val now = counts(ctx)
            val lastCode = Prefs.long("diag2_code", -1); val lastName = Prefs.str("diag2_name")  // i18n-ignore
            val idWas = Prefs.str("diag2_devid"); val id = DeviceIdentity.id()  // i18n-ignore
            if (lastCode >= 0 && lastCode != BuildConfig.VERSION_CODE.toLong()) {
                val before = dec(Prefs.str("diag2_counts"))  // i18n-ignore
                val lost = before.filter { (k, v) -> v > 0 && (now[k] ?: -1) in 0 until v && k != "recently watched channels" }  // i18n-ignore
                val verdict = when { before.isEmpty() -> "not verified (no counts from the previous version)"; lost.isEmpty() -> "preserved (no count went down)"  // i18n-ignore
                    else -> "check: " + lost.entries.joinToString(", ") { "${it.key} ${it.value}→${now[it.key]}" } }  // i18n-ignore
                PerfLog.rowII("updates", listOf(lastName, lastCode, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, BuildConfig.GIT_COMMIT, if (Monitor.startMs > 0) Monitor.startMs else "",  // i18n-ignore
                    if (idWas.isBlank()) "unknown" else (idWas == id).toString(), enc(before), enc(now), verdict))  // i18n-ignore
                DiagEvents.add("update", "updated $lastName → ${BuildConfig.VERSION_NAME} · first screen ${if (Monitor.startMs > 0) "${Monitor.startMs} ms" else "not yet measured"} · device id ${if (idWas.isBlank()) "not recorded before" else if (idWas == id) "kept" else "CHANGED"} · data: $verdict")  // i18n-ignore
            }
            Prefs.put("diag2_code", BuildConfig.VERSION_CODE.toLong()); Prefs.put("diag2_name", BuildConfig.VERSION_NAME); Prefs.put("diag2_devid", id); Prefs.put("diag2_counts", enc(now))  // i18n-ignore
        }
        // the first-screen time arrives a little later on slow boxes: one more look
        DiagCollect.guard("life") { if (Monitor.startMs > 0) PerfLog.task("app start", "first screen", Monitor.startMs) }  // i18n-ignore
    }

    /** section 65: every exit Android kept, once, classified only as far as the evidence goes */
    private fun exits(ctx: Context, prevSession: String, prevVersion: String) {
        if (Build.VERSION.SDK_INT < 30) return
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val seen = Prefs.long("diag2_exit_seen", 0)  // i18n-ignore
        val list = am.getHistoricalProcessExitReasons(ctx.packageName, 0, 16).filter { it.timestamp > seen }.sortedBy { it.timestamp }
        val bootAt = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime()
        list.forEachIndexed { i, e ->
            val cls = classify(e.reason, e.description, e.timestamp, bootAt)
            // only the newest exit can belong to the run right before this one
            val ps = if (i == list.lastIndex) prevSession else ""; val pv = if (i == list.lastIndex) prevVersion else ""  // i18n-ignore
            PerfLog.rowII("exits", listOf(DiagEvents.stamp(e.timestamp), reasonName(e.reason), e.status, e.importance, e.pss / 1024, e.rss / 1024,  // i18n-ignore
                Redact.text((e.description ?: "").take(100)), ps, pv, cls))  // i18n-ignore
            DiagCollect.inc("previous runs ended: $cls")  // i18n-ignore
        }
        list.maxOfOrNull { it.timestamp }?.let { Prefs.put("diag2_exit_seen", it) }  // i18n-ignore
    }
    fun reasonName(r: Int): String = when (r) {
        0 -> "unknown"; 1 -> "exit self"; 2 -> "signaled"; 3 -> "low memory"; 4 -> "crash (Java)"; 5 -> "crash (native)"; 6 -> "ANR"; 7 -> "initialization failure"  // i18n-ignore
        8 -> "permission change"; 9 -> "excessive resource usage"; 10 -> "user requested"; 11 -> "user stopped"; 12 -> "dependency died"; 13 -> "other"  // i18n-ignore
        14 -> "freezer"; 15 -> "package state change"; 16 -> "package updated"; else -> "reason $r"  // i18n-ignore
    }
    fun classify(r: Int, desc: String?, at: Long, bootAt: Long): String {
        val d = (desc ?: "").lowercase()  // i18n-ignore
        return when (r) {
            1 -> "normal exit"; 10 -> if (d.contains("force") || d.contains("stop")) "force stop (user/system)" else "user-requested termination"  // i18n-ignore
            11 -> "user stopped (force stop)"; 3 -> "low-memory kill"; 4 -> "Java crash"; 5 -> "native crash"; 6 -> "ANR"; 9 -> "excessive resource usage"  // i18n-ignore
            12 -> "dependency termination"; 16 -> "update-related termination"; 7 -> "initialization failure"; 14 -> "frozen by Android"  // i18n-ignore
            else -> when {
                d.contains("installpackage") || d.contains("update") -> "update-related termination"  // i18n-ignore
                at < bootAt -> "ambiguous (ended before the last device start; possibly a device restart)"  // i18n-ignore
                else -> "ambiguous (" + reasonName(r) + ")"  // i18n-ignore
            }
        }
    }

    // ------------------------------------------------------------------ section 69: exports
    fun export(kind: String, ms: Long, f: java.io.File?, outcome: String) = DiagCollect.guard("export") {  // i18n-ignore
        val sha = if (f != null && f.exists() && outcome == "ok") try {  // i18n-ignore
            val md = java.security.MessageDigest.getInstance("SHA-256"); f.inputStream().use { i -> val b = ByteArray(64 * 1024); while (true) { val n = i.read(b); if (n < 0) break; md.update(b, 0, n) } }  // i18n-ignore
            md.digest().take(8).joinToString("") { "%02x".format(it) }  // i18n-ignore
        } catch (_: Throwable) { "" } else ""  // i18n-ignore
        PerfLog.rowII("exports", listOf(kind, ms, f?.length() ?: 0, outcome, sha))  // i18n-ignore
        DiagCollect.inc("diagnostic exports: $kind $outcome")  // i18n-ignore
    }
    fun transfer(what: String) = DiagCollect.inc("diagnostic transfer: $what")  // i18n-ignore
}
