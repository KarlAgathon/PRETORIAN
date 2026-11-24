package com.pretorian.tv

import android.content.Context
import android.os.Build

/**
 * 3.0.0.7 FULL DIAGNOSTIC REPORT — sections 51–69 and the summary (section 70, printed at the top of the report).
 * Reads only what the collectors of [DiagCollect], [DiagII] and [DiagLife] already kept. Every value carries its label:
 * [measured] [device reported] [configured] [cached] [estimated] [suspected] [not observed] [not supported] [unavailable]
 * [insufficient data]. Missing is never zero, configured is never actual, suspected is never confirmed.
 */
internal object DiagReportII {
    private const val M = "[measured]"; private const val D = "[device reported]"; private const val CF = "[configured]"  // i18n-ignore
    private const val NO = "[not observed]"; private const val NS = "[not supported]"; private const val UA = "[unavailable]"; private const val ID = "[insufficient data]"  // i18n-ignore
    private const val SU = "[suspected]"  // i18n-ignore

    fun rows(file: String): List<List<String>> { val l = ArrayList<List<String>>(); PerfLog.each(file) { l += it.split(',') }; return l }
    private fun L(r: List<String>, i: Int) = r.getOrNull(i)?.toLongOrNull()
    private fun S(r: List<String>, i: Int) = r.getOrNull(i) ?: ""  // i18n-ignore
    private fun pct(l: List<Long>, p: Double) = DiagII.pct(l, p)
    private fun dist(l: List<Long>, unit: String = "ms") = DiagII.dist(l, unit)  // i18n-ignore
    private fun share(a: Long, b: Long): String = if (b <= 0) "n/a (denominator 0)" else String.format(java.util.Locale.US, "%.1f%%", a * 100.0 / b)  // i18n-ignore
    private fun f1(d: Double) = String.format(java.util.Locale.US, "%.1f", d)  // i18n-ignore

    /** session → "version (code)", and session → code; sessions written before 3.0.0.7 are not in the table */
    class Versions {
        val name = HashMap<String, String>(); val code = HashMap<String, Long>(); val commit = HashMap<String, String>()
        init {
            rows("sessions").forEach { r -> val s = S(r, 1); name[s] = S(r, 2) + " (" + S(r, 3) + ")"; L(r, 3)?.let { code[s] = it }; commit[s] = S(r, 4) }  // i18n-ignore
            name.putIfAbsent(PerfLog.session, BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")"); code.putIfAbsent(PerfLog.session, BuildConfig.VERSION_CODE.toLong())  // i18n-ignore
        }
        fun of(session: String) = name[session] ?: "version unknown (recorded before 3.0.0.7)"  // i18n-ignore
        fun codeOf(session: String) = code[session]
        val cur = BuildConfig.VERSION_CODE.toLong()
        /** the newest earlier version that has any session on this box */
        fun previous(): Long? = code.values.filter { it < cur }.maxOrNull()
        fun label(c: Long) = name.entries.firstOrNull { code[it.key] == c }?.value ?: "versionCode $c"  // i18n-ignore
    }
    class Scope(val label: String, val ok: (String) -> Boolean)
    private fun scopes(v: Versions) = listOf(Scope("this session") { it == PerfLog.session }, Scope("this version " + BuildConfig.VERSION_NAME) { v.codeOf(it) == v.cur },  // i18n-ignore
        Scope("all retained history (all versions)") { true })  // i18n-ignore

    private fun counters(o: DiagReport.Out, vararg prefixes: String, none: String = "$NO this session") {  // i18n-ignore
        val m = DiagCollect.counters.filterKeys { k -> prefixes.any { k.startsWith(it) } }
        if (m.isEmpty()) o.line(none) else m.toSortedMap().forEach { (k, v) -> o.line("$M $k: ${v.get()} (this session)") }  // i18n-ignore
    }
    private fun ringLines(o: DiagReport.Out, prefix: String, unit: String = "ms", none: String? = null) {  // i18n-ignore
        val m = DiagII.rings(prefix)
        if (m.isEmpty()) { if (none != null) o.line(none) } else m.forEach { (k, r) -> o.line("$M $k: " + dist(r.list(), unit) + " (this session)") }  // i18n-ignore
    }

    // =============================================================== section 70: the summary at the top
    private class Finding(val level: String, val area: String, val text: String, val section: Int, val cause: String = "", val confidence: String = "")  // i18n-ignore
    fun summary(ctx: Context, o: DiagReport.Out) {
        o.line(""); o.line("=".repeat(72)); o.line("70. INTELLIGENT DIAGNOSTIC SUMMARY (local rules, no external AI; detail in the referenced sections)"); o.line("=".repeat(72))  // i18n-ignore
        val v = Versions()
        val f = ArrayList<Finding>()
        val areas = LinkedHashMap<String, String>()
        try {
            // application stability
            val inc = DiagStore.incidents()
            val recent = inc.filter { System.currentTimeMillis() - it.time < 7 * 86_400_000L }
            val crashes = recent.count { it.kind == "crash" || it.kind == "native" }; val freezes = recent.count { it.kind == "freeze" || it.kind == "anr" }  // i18n-ignore
            areas["application stability"] = when { crashes > 0 -> "CRITICAL"; freezes > 0 -> "WARNING"; else -> "NORMAL" }  // i18n-ignore
            if (crashes > 0) f += Finding("CRITICAL", "application stability", "$crashes crash(es) in the last 7 days · latest ${DiagEvents.stamp(recent.filter { it.kind == "crash" || it.kind == "native" }.maxOf { it.time })}", 27, "see the stack trace", "high")  // i18n-ignore
            if (freezes > 0) f += Finding("WARNING", "application stability", "$freezes freeze/ANR incident(s) in the last 7 days", 28, "main thread blocked", "medium")  // i18n-ignore
            val ex = rows("exits").filter { S(it, 11).contains("crash") || S(it, 11) == "ANR" || S(it, 11).contains("low-memory") }  // i18n-ignore
            if (ex.isNotEmpty()) f += Finding("WARNING", "application stability", "${ex.size} abnormal previous-run end(s) reported by Android (latest ${S(ex.last(), 2)}: ${S(ex.last(), 11)})", 65, "", "high")  // i18n-ignore
            // playback
            val q = rows("qoe").filter { v.codeOf(S(it, 1)) == v.cur }  // i18n-ignore
            val starts = q.count { S(it, 4).startsWith("ok") || S(it, 4) == "ended after error" }; val failed = q.count { S(it, 4) == "failed start" }  // i18n-ignore
            val playH = q.sumOf { L(it, 6) ?: 0L } / 3_600_000.0
            val rebufN = q.sumOf { L(it, 8) ?: 0L }
            areas["playback stability"] = when {  // i18n-ignore
                starts + failed < 5 -> "INSUFFICIENT DATA"  // i18n-ignore
                failed * 100 > (starts + failed) * 10 -> "CRITICAL"  // i18n-ignore
                failed > 0 || (playH >= 1 && rebufN / playH > 6) -> "WARNING"  // i18n-ignore
                else -> "NORMAL" }  // i18n-ignore
            if (failed > 0) f += Finding(if (failed * 100 > (starts + failed) * 10) "CRITICAL" else "WARNING", "playback", "$failed of ${starts + failed} stream starts failed in this version (${share(failed.toLong(), (starts + failed).toLong())})", 54, "see the error classes in section 51/54", "high")  // i18n-ignore
            if (playH >= 1 && rebufN / playH > 6) f += Finding("WARNING", "playback", "rebuffering ${f1(rebufN / playH)} times per playback hour in this version (${f1(playH)} h observed)", 54, "suspected causes in section 55", "medium")  // i18n-ignore
            // provider protection and stream lifecycle
            val r458 = DiagII.cnt("HTTP 458 restriction events") + DiagII.cnt("HTTP 429 restriction events") + DiagII.cnt("HTTP 509 restriction events")  // i18n-ignore
            val ov = rows("overlap")  // i18n-ignore
            areas["provider protection"] = if (r458 > 0) "WARNING" else if (ov.isNotEmpty()) "WARNING" else "NORMAL"  // i18n-ignore
            if (r458 > 0) f += Finding("WARNING", "provider protection", "$r458 provider restriction answer(s) (HTTP 458/429/509) this session", 51, "provider connection limit reached — another device on the account, or an earlier connection not yet closed on the provider side", "medium")  // i18n-ignore
            if (ov.isNotEmpty()) f += Finding("WARNING", "stream lifecycle", "${ov.size} moment(s) where two players were loading at the same time (longest ${ov.mapNotNull { L(it, 2) }.maxOrNull() ?: 0L} ms) in retained history", 51, "player hand-over overlap; may count as two connections at the provider", "medium")  // i18n-ignore
            areas["stream lifecycle"] = if (ov.isEmpty()) "NORMAL" else "WARNING"  // i18n-ignore
            // network / VPN
            val netErr = DiagCollect.counters.filterKeys { it.startsWith("stream request errors") }.values.sumOf { it.get() }  // i18n-ignore
            val netTr = DiagII.cnt("network transitions during playback")  // i18n-ignore
            areas["network / VPN"] = when { netTr > 0 || netErr > 20 -> "WARNING"; DiagII.cnt("stream requests completed (tv)") + DiagII.cnt("stream requests completed (movie)") + DiagII.cnt("stream requests completed (series)") == 0L -> "NOT OBSERVED"; else -> "NORMAL" }  // i18n-ignore
            if (netTr > 0) f += Finding("WARNING", "network / VPN", "$netTr network transition(s) during playback this session (now ${DiagII.netCfg()})", 53, "possible network interruption; check rebuffering right after it", "medium")  // i18n-ignore
            // memory
            val mem = rows("mem").filter { S(it, 1) == PerfLog.session }  // i18n-ignore
            val susp = memSuspects(mem)
            areas["memory"] = when { mem.size < 3 -> "INSUFFICIENT DATA"; susp.isNotEmpty() -> "WARNING"; else -> "NORMAL" }  // i18n-ignore
            susp.forEach { f += Finding("WARNING", "memory", it, 61, "growth in equivalent idle state; could also be a cache filling up", "low") }  // i18n-ignore
            val low = DiagCollect.counters.filterKeys { it.startsWith("Android memory trim requests") }.values.sumOf { it.get() }  // i18n-ignore
            if (low > 0) f += Finding("WARNING", "memory", "Android asked the app to free memory $low time(s) this session", 61, "low RAM on the device", "medium")  // i18n-ignore
            // CPU
            val cpu = rows("samples").filter { S(it, 1) == PerfLog.session && S(it, 2) == "idle" }.mapNotNull { L(it, 3) }  // i18n-ignore
            areas["CPU"] = when { cpu.size < 5 -> "INSUFFICIENT DATA"; pct(cpu, .5) > 30 -> "WARNING"; else -> "NORMAL" }  // i18n-ignore
            if (cpu.size >= 5 && pct(cpu, .5) > 30) f += Finding("WARNING", "CPU", "median CPU ${pct(cpu, .5)}% of one core while idle this session", 61, "background work or a stuck animation", "low")  // i18n-ignore
            // decoders / audio / subtitles
            val dec = rows("decfail").filter { v.codeOf(S(it, 1)) == v.cur }  // i18n-ignore
            areas["video decoder"] = if (dec.isNotEmpty()) "WARNING" else if (DiagCollect.counters.keys.any { it.startsWith("video decoder used") }) "NORMAL" else "NOT OBSERVED"  // i18n-ignore
            if (dec.isNotEmpty()) f += Finding("WARNING", "video decoder", "${dec.size} decoder failure(s) in this version (latest: ${S(dec.last(), 4)} ${S(dec.last(), 6)} on ${S(dec.last(), 9)})", 56, "codec/profile not handled by this device's decoder", "medium")  // i18n-ignore
            val aerr = DiagII.cnt("audio sink errors") + DiagCollect.counters.filterKeys { it.startsWith("audio decoder error") }.values.sumOf { it.get() }  // i18n-ignore
            areas["audio"] = if (aerr > 0) "WARNING" else if (DiagCollect.counters.keys.any { it.startsWith("audio decoder used") }) "NORMAL" else "NOT OBSERVED"  // i18n-ignore
            if (aerr > 0) f += Finding("WARNING", "audio", "$aerr audio output/decoder error(s) this session", 57, "", "medium")  // i18n-ignore
            val sfail = DiagCollect.counters.filterKeys { it.startsWith("subtitle downloads") && it.contains("failed") }.values.sumOf { it.get() } + DiagII.cnt("subtitle loading failures (embedded/side-loaded track)")  // i18n-ignore
            areas["subtitles"] = if (sfail > 0) "WARNING" else if (DiagCollect.counters.keys.any { it.startsWith("subtitle") }) "NORMAL" else "NOT OBSERVED"  // i18n-ignore
            // database / storage
            val dbErr = DiagCollect.counters.filterKeys { it.startsWith("database errors") }.values.sumOf { it.get() }  // i18n-ignore
            val corrupt = DiagCollect.counters.keys.any { it.startsWith("database errors: corrupt") }  // i18n-ignore
            areas["database"] = when { corrupt -> "CRITICAL"; dbErr > 0 -> "WARNING"; else -> "NORMAL" }  // i18n-ignore
            if (dbErr > 0) f += Finding(if (corrupt) "CRITICAL" else "WARNING", "database", "$dbErr database error(s) this session", 62, if (corrupt) "database corruption reported by SQLite" else "lock/busy/other", "high")  // i18n-ignore
            val free = ctx.filesDir.usableSpace / 1_048_576
            areas["storage"] = when { free < 200 -> "CRITICAL"; free < 1024 -> "WARNING"; else -> "NORMAL" }  // i18n-ignore
            if (free < 1024) f += Finding(if (free < 200) "CRITICAL" else "WARNING", "storage", "only $free MB free on the device", 62, "", "high")  // i18n-ignore
            // navigation / UI
            val focusMiss = DiagCollect.counters.filterKeys { it.startsWith("focus target missing") }.values.sumOf { it.get() }  // i18n-ignore
            areas["navigation"] = if (focusMiss > 3) "WARNING" else "NORMAL"  // i18n-ignore
            if (focusMiss > 3) f += Finding("WARNING", "navigation", "focus could not be placed $focusMiss time(s) this session", 60, "focus target missing from the screen", "medium")  // i18n-ignore
            val jk = rows("jank").filter { S(it, 1) == PerfLog.session }  // i18n-ignore
            val frames = jk.sumOf { L(it, 3) ?: 0L }; val janky = jk.sumOf { L(it, 5) ?: 0L }; val frozen = jk.sumOf { L(it, 6) ?: 0L }
            areas["UI rendering"] = when { Build.VERSION.SDK_INT < 24 -> "NOT SUPPORTED"; frames < 500 -> "INSUFFICIENT DATA"; frozen > 5 || janky * 100 > frames * 10 -> "WARNING"; else -> "NORMAL" }  // i18n-ignore
            if (frames >= 500 && (frozen > 5 || janky * 100 > frames * 10)) f += Finding("WARNING", "UI rendering", "${share(janky, frames)} janky frames and $frozen frozen frames of $frames this session", 59, "heavy screen or weak device", "medium")  // i18n-ignore
            // update / migration
            val up = rows("updates").lastOrNull()  // i18n-ignore
            areas["update / migration"] = when { up == null -> "NOT OBSERVED"; S(up, 11).startsWith("preserved") && S(up, 8) != "false" -> "NORMAL"; S(up, 11).startsWith("not verified") -> "UNAVAILABLE"; else -> "WARNING" }  // i18n-ignore
            if (up != null && (S(up, 11).startsWith("check") || S(up, 8) == "false")) f += Finding("WARNING", "update / migration", "after ${S(up, 2)} → ${S(up, 4)}: data ${S(up, 11)} · device id kept: ${S(up, 8)}", 66, "", "high")  // i18n-ignore
            // diagnostics themselves
            val off = DiagCollect.disabled()
            areas["diagnostics"] = if (off.isNotEmpty() || PerfLog.dropped.get() > 50) "WARNING" else "NORMAL"  // i18n-ignore
            if (off.isNotEmpty()) f += Finding("WARNING", "diagnostics", "collectors switched off after an internal error: " + off.joinToString(", "), 49, "", "high")  // i18n-ignore
        } catch (e: DiagReport.Full) { throw e } catch (e: Throwable) { o.line("[summary incomplete: " + e.javaClass.simpleName + "]") }  // i18n-ignore
        o.line("Statuses: NORMAL · WARNING · CRITICAL · INSUFFICIENT DATA · NOT OBSERVED · NOT SUPPORTED · UNAVAILABLE. NORMAL is given only where data exists; there is no overall score.")  // i18n-ignore
        areas.forEach { (k, s) -> o.line("  " + s.padEnd(18) + " " + k) }  // i18n-ignore
        o.line("Integrity of user data after updates is " + (if (areas["update / migration"] == "NORMAL") "verified by aggregate counts (section 66)" else "NOT verified in this report (section 66)") + ".")  // i18n-ignore
        if (f.isEmpty()) o.line("Findings: none above the warning rules.") else {  // i18n-ignore
            o.line("Findings (correlation is not causation; 'possible cause' is a lead to check, not a conclusion):")  // i18n-ignore
            f.sortedBy { if (it.level == "CRITICAL") 0 else 1 }.take(12).forEach { x ->  // i18n-ignore
                o.line("- ${x.level} · ${x.area}: ${x.text} · see section ${x.section}" + (if (x.cause.isNotBlank()) " · possible cause: ${x.cause}" else "") + (if (x.confidence.isNotBlank()) " · confidence ${x.confidence}" else "") + " · further investigation required")  // i18n-ignore
            }
        }
    }
    /** suspicious growth in the same (idle) state within one process: heap, native, PSS, threads, file descriptors */
    private fun memSuspects(mem: List<List<String>>): List<String> {
        val idle = mem.filter { S(it, 2) == "idle" }  // i18n-ignore
        if (idle.size < 6) return emptyList()
        val early = idle.filter { (L(it, 17) ?: 0L) <= 60 }; val late = idle.filter { (L(it, 17) ?: 0L) >= (idle.mapNotNull { r -> L(r, 17) }.maxOrNull() ?: 0L) - 60 }
        if (early.size < 2 || late.size < 2 || (late.mapNotNull { L(it, 17) }.maxOrNull() ?: 0L) < 240) return emptyList()
        fun med(l: List<List<String>>, i: Int) = pct(l.mapNotNull { L(it, i) }, .5)
        val out = ArrayList<String>()
        val cacheGrow = med(late, 18) - med(early, 18)
        val heapGrow = med(late, 3) - med(early, 3)
        if (heapGrow > 64 && heapGrow > cacheGrow + 32) out += "$SU Java heap in idle state grew ${med(early, 3)} → ${med(late, 3)} MB over the session (image cache grew $cacheGrow MB)"  // i18n-ignore
        if (med(late, 6) - med(early, 6) > 80) out += "$SU native heap in idle state grew ${med(early, 6)} → ${med(late, 6)} MB"  // i18n-ignore
        if (med(late, 7) - med(early, 7) > 150 && med(late, 7) - med(early, 7) > cacheGrow + 100) out += "$SU PSS in idle state grew ${med(early, 7)} → ${med(late, 7)} MB"  // i18n-ignore
        if (med(late, 10) - med(early, 10) > 25) out += "$SU thread count grew ${med(early, 10)} → ${med(late, 10)}"  // i18n-ignore
        if (med(late, 11) - med(early, 11) > 60) out += "$SU open file descriptors grew ${med(early, 11)} → ${med(late, 11)}"  // i18n-ignore
        return out
    }

    // =============================================================== sections 51–69
    fun sections(ctx: Context, o: DiagReport.Out) {
        fun sec(n: Int, t: String, fill: () -> Unit) {
            o.line(""); o.line("=".repeat(72)); o.line("$n. $t"); o.line("=".repeat(72))  // i18n-ignore
            try { fill() } catch (e: DiagReport.Full) { throw e } catch (e: Throwable) { o.line("[section unavailable: " + e.javaClass.simpleName + "]") }  // i18n-ignore
        }
        val v = Versions()
        val sc = scopes(v)

        sec(51, "STREAM CONNECTION INTELLIGENCE") {  // i18n-ignore
            o.line("Five different things, never equal: player instances · logical streams (one channel/title opening) · HTTP requests (an HLS stream makes many, one per segment, legitimately overlapping inside ONE stream) · local socket connections · connections the provider counts. Releasing a player or finishing a request does not prove the provider closed its side.")  // i18n-ignore
            o.line("$M player instances: created ${(DiagII.cnt("players created (preview)") + DiagII.cnt("players created (fullscreen)"))} · released ${(DiagII.cnt("players released (preview)") + DiagII.cnt("players released (fullscreen)"))} · alive now ${DiagCollect.playersLive.get()} · peak at once ${DiagCollect.playersPeak.get()} (section 40)")  // i18n-ignore
            counters(o, "logical streams opened", none = "$NO no stream opened this session")  // i18n-ignore
            counters(o, "stream requests", "stream redirects", "stream request retries", none = "$NO no stream request this session")  // i18n-ignore
            o.line("$M stream requests active now: ${DiagII.peakLoads.let { "peak at once ${it.get()}" }} (all players together; segments of one stream included)")  // i18n-ignore
            ringLines(o, "request ms playback ")  // i18n-ignore
            counters(o, "overlapping stream requests", "stream requests still open at player release", none = "$M overlapping loads between two players: 0 this session")  // i18n-ignore
            ringLines(o, "overlap duration"); ringLines(o, "gap close→next open"); ringLines(o, "transition "); ringLines(o, "fullscreen first picture after open")  // i18n-ignore
            val ov = rows("overlap"); o.line("$M overlaps in retained history: ${ov.size}" + if (ov.isNotEmpty()) " · durations " + dist(ov.mapNotNull { L(it, 2) }) else "")  // i18n-ignore
            o.line("Provider protection (this device only):"); o.block(ProbeGuard.report())  // i18n-ignore
            counters(o, "provider cooldown", "requests prevented", "HTTP 458", "HTTP 429", "HTTP 509", none = "$NO no provider restriction or cooldown this session")  // i18n-ignore
            val st = rows("streams")  // i18n-ignore
            sc.forEach { s -> val r = st.filter { s.ok(S(it, 1)) }; if (r.isNotEmpty()) {
                o.line("$M ${s.label}: ${r.size} logical streams · requests ${r.sumOf { L(it, 4) ?: 0L }} (completed ${r.sumOf { L(it, 5) ?: 0L }}, cancelled ${r.sumOf { L(it, 6) ?: 0L }}, failed ${r.sumOf { L(it, 7) ?: 0L }}) · redirects ${r.sumOf { L(it, 12) ?: 0L }}")  // i18n-ignore
                val codes = HashMap<String, Long>(); r.forEach { x -> S(x, 13).split(' ').filter { it.contains('x') }.forEach { c -> codes.merge(c.substringBefore('x'), c.substringAfter('x').toLongOrNull() ?: 0L, Long::plus) } }
                if (codes.isNotEmpty()) o.line("   HTTP error codes: " + codes.entries.joinToString(", ") { "${it.key} ×${it.value}" })  // i18n-ignore
            } }
            o.line("$UA socket-level connection lifecycle (Media3's standard HTTP connection does not expose it) · $UA live provider connection count (it would need an extra request; never asked) — the provider's own snapshot at login is in section 32")  // i18n-ignore
        }

        sec(52, "ADVANCED NETWORK INTELLIGENCE") {  // i18n-ignore
            o.line("$D now: " + NetState.now() + " · configuration " + DiagII.netCfg()); o.line("Wi-Fi: " + DiagII.wifiInfo(ctx)); o.line("IP: " + DiagII.ipFamilies(ctx))  // i18n-ignore
            counters(o, "network lost", "network changes", none = "$NO no network change this session"); o.line("Interruption and recovery times: events 'back after … ms' in section 38.")  // i18n-ignore
            o.line("-- playback (Media3 standard HTTP connection): request duration and throughput below; DNS / TCP / TLS / time-to-first-byte: $UA (not exposed by this stack; not replaced for measuring)")  // i18n-ignore
            ringLines(o, "throughput kbit/s playback ", "kbit/s"); counters(o, "bytes received playback")  // i18n-ignore
            o.line("-- app requests (playlist/provider API, EPG, updates, metadata, subtitles, weather, sports) via HttpURLConnection: total duration and time to first response (includes connection setup); separate DNS/TCP/TLS: $UA for this stack")  // i18n-ignore
            ringLines(o, "http total ms "); ringLines(o, "http ttfb ms "); ringLines(o, "http kbit/s ", "kbit/s")  // i18n-ignore
            counters(o, "http ", none = "$NO no app request this session")  // i18n-ignore
            o.line("-- images (posters, backdrops, logos; OkHttp inside the image loader): connection phases measured")  // i18n-ignore
            ringLines(o, "images ", none = "$NO no image downloaded from the network this session (cache only)"); counters(o, "images connections over", "images requests on a reused")  // i18n-ignore
            val err = DiagCollect.counters.filterKeys { it.contains("error: ") && (it.startsWith("http ") || it.startsWith("stream request errors")) }  // i18n-ignore
            val classes = listOf("read timeout", "connection timeout", "connection reset", "connection refused", "DNS resolution failure", "TLS / certificate failure")  // i18n-ignore
            o.line("$M failure classes this session: " + classes.joinToString(" · ") { c -> "$c ${err.filterKeys { it.endsWith(c) }.values.sumOf { it.get() }}" })  // i18n-ignore
            val net = rows("net")  // i18n-ignore
            if (net.isEmpty()) o.line("$NO no app request rows in history") else net.groupBy { S(it, 2) }.toSortedMap().forEach { (c, r) ->  // i18n-ignore
                o.line("$M history $c: " + dist(r.mapNotNull { L(it, 4) }) + " · failures ${r.count { S(it, 7).isNotBlank() }} · statuses " + r.groupBy { S(it, 3).ifBlank { "none" } }.entries.joinToString(" ") { "${it.key}×${it.value.size}" })  // i18n-ignore
            }
            o.line("HTTP response time is not ICMP latency; jitter is not measured.")  // i18n-ignore
        }

        sec(53, "EXTENDED VPN DIAGNOSTICS") {  // i18n-ignore
            o.line("$D now: " + DiagII.netCfg() + " · VPN detection means Android reports a VPN transport; it does not prove that all of this app's traffic goes through it.")  // i18n-ignore
            DiagII.netTimeNow().toSortedMap().forEach { (k, ms) -> o.line("$M time with $k: ${ms / 60_000} min (this session)") }  // i18n-ignore
            counters(o, "VPN ", "network transitions during playback", "errors after a VPN", none = "$NO no VPN change this session")  // i18n-ignore
            val q = rows("qoe"); val st = rows("streams"); val rb = rows("rebuf")  // i18n-ignore
            listOf("Ethernet without VPN", "Ethernet with VPN", "Wi-Fi without VPN", "Wi-Fi with VPN", "unknown").forEach { n ->  // i18n-ignore
                val r = q.filter { S(it, 3) == n }
                if (r.isEmpty()) { o.line("$NO $n: no playback recorded"); return@forEach }  // i18n-ignore
                val started = r.count { S(it, 4).startsWith("ok") || S(it, 4) == "ended after error" }; val failed = r.count { S(it, 4) == "failed start" }  // i18n-ignore
                val playH = r.sumOf { L(it, 6) ?: 0L } / 3_600_000.0
                o.line("$M $n (all history): ${r.size} streams · start failures ${share(failed.toLong(), (started + failed).toLong())} · startup median ${pct(r.mapNotNull { L(it, 5) }, .5)} ms · rebuffering ${if (playH > 0.05) f1(r.sumOf { L(it, 8) ?: 0L } / playH) + "/h" else "$ID"} · errors ${r.sumOf { L(it, 14) ?: 0L }} · recoveries ${r.sumOf { L(it, 16) ?: 0L }}/${r.sumOf { L(it, 15) ?: 0L }} · throughput median ${pct(st.filter { S(it, 3) == n }.mapNotNull { L(it, 11) }, .5)} kbit/s")  // i18n-ignore
            }
            val after = rb.filter { S(it, 5).startsWith("network transition") }  // i18n-ignore
            o.line("$M rebuffering right after a network transition (all history): ${after.size} · " + dist(after.mapNotNull { L(it, 4) }))  // i18n-ignore
            o.line("$UA VPN provider, protocol, server location, kill switch, split tunnelling, encrypted tunnel throughput and full routing (not exposed to apps; no public-IP lookup is made).")  // i18n-ignore
        }

        sec(54, "PLAYBACK QUALITY OF EXPERIENCE (QoE)") {  // i18n-ignore
            o.line("Definitions: a 'stream' is one opening of a channel/title. Started = reached READY. Start success rate = started / (started + failed before READY). Rebuffering = buffering after READY while playing, not within the buffering that follows a seek. Rebuffering ratio = rebuffering time / (playing time + rebuffering time). Dropped-frame ratio = dropped / (dropped + rendered). Recovery rate = recovered streams / recovery attempts.")  // i18n-ignore
            val q = rows("qoe"); val rb = rows("rebuf")  // i18n-ignore
            listOf("tv", "preview", "movie", "series", "catchup", "track probe", "trailer").forEach { t ->  // i18n-ignore
                o.line("-- $t" + if (t == "trailer") " (trailers open in YouTube or the browser, outside the app's player)" else "")  // i18n-ignore
                sc.forEach { s ->
                    val r = q.filter { S(it, 2) == t && s.ok(S(it, 1)) }
                    if (r.isEmpty()) { o.line("   $NO ${s.label}"); return@forEach }  // i18n-ignore
                    val started = r.count { S(it, 4).startsWith("ok") || S(it, 4) == "ended after error" }; val failed = r.count { S(it, 4) == "failed start" }  // i18n-ignore
                    val play = r.sumOf { L(it, 6) ?: 0L }; val reb = r.sumOf { L(it, 9) ?: 0L }; val rebN = r.sumOf { L(it, 8) ?: 0L }
                    val drop = r.sumOf { L(it, 12) ?: 0L }; val rend = r.sumOf { L(it, 13) ?: 0L }
                    val att = r.sumOf { L(it, 15) ?: 0L }; val ok = r.sumOf { L(it, 16) ?: 0L }
                    val rbl = rb.filter { S(it, 2) == t && s.ok(S(it, 1)) }.mapNotNull { L(it, 4) }
                    o.line("   $M ${s.label}: streams ${r.size} · started $started · failed starts $failed · success ${share(started.toLong(), (started + failed).toLong())} · closed before start ${r.count { S(it, 4) == "closed before start" }}")  // i18n-ignore
                    o.line("      playing ${f1(play / 3_600_000.0)} h · paused ${f1(r.sumOf { L(it, 7) ?: 0L } / 3_600_000.0)} h · initial buffering " + dist(r.mapNotNull { L(it, 5) }))  // i18n-ignore
                    o.line("      rebuffering: $rebN events · ${if (play > 180_000) f1(rebN / (play / 3_600_000.0)) + " per playback hour" else "per hour $ID"} · ratio ${share(reb, play + reb)} · longest ${r.mapNotNull { L(it, 10) }.maxOrNull() ?: 0L} ms · mean ${if (rebN > 0) reb / rebN else 0} ms · events " + dist(rbl) + " · after seeks (excluded) ${r.sumOf { L(it, 11) ?: 0L }}")  // i18n-ignore
                    o.line("      frames dropped $drop · rendered ${if (rend > 0) rend.toString() else "unavailable"} · drop ratio ${if (rend > 0) share(drop, drop + rend) else UA} · errors ${r.sumOf { L(it, 14) ?: 0L }} · ended after an error ${r.count { S(it, 4) == "ended after error" }} · recovery $ok of $att (${share(ok, att)}) · recovery time " + dist(r.mapNotNull { L(it, 17) }))  // i18n-ignore
                }
            }
        }

        sec(55, "BUFFER INTELLIGENCE") {  // i18n-ignore
            o.line("$CF preview: 2 s min / 15 s max / 0.5 s to start / 1 s after rebuffer · target ${if (Perf.lite) 6 else 12} MB")  // i18n-ignore
            val mb = Buffer.mb
            o.line("$CF full screen: profile '" + Buffer.OPTIONS.getOrElse(Buffer.choice) { "?" } + "' → $mb MB target · " + when { mb <= 32 -> "15 s min / 20 s max / 1.5 s to start / 3 s after rebuffer"; mb >= 96 -> "30 s min / 120 s max / 3 s to start / 6 s after rebuffer"; else -> "Media3 defaults (50 s min / 50 s max / 1 s / 2 s)" } + " · size has priority over time")  // i18n-ignore
            o.line("Buffered media duration is seconds of video ready to play, not memory. Samples are taken only on existing events (request completed, buffering start/end, READY), at most every 2 s.")  // i18n-ignore
            ringLines(o, "buffer ms ")  // i18n-ignore
            counters(o, "buffer at failure", "rebuffering suspected cause", none = "$NO no playback failure or rebuffering this session")  // i18n-ignore
            val b = rows("buffer")  // i18n-ignore
            b.groupBy { S(it, 2) }.toSortedMap().forEach { (t, r) ->
                val ratio = r.mapNotNull { x -> val br = L(x, 6) ?: 0L; val th = L(x, 7) ?: 0L; if (br > 0 && th > 0) th * 100 / br else null }
                o.line("$M history $t: ${r.size} streams · lowest ${r.mapNotNull { L(it, 3) }.minOrNull() ?: 0L} ms · highest ${r.mapNotNull { L(it, 4) }.maxOrNull() ?: 0L} ms · time-weighted mean per stream " + dist(r.mapNotNull { L(it, 5) }.filter { it >= 0 }) + " · throughput/bitrate " + (if (ratio.isEmpty()) "$UA (bitrate not in the stream)" else "median ${pct(ratio, .5)}%"))  // i18n-ignore
            }
            o.line("Causes are $SU only: 'network transition shortly before', 'request errors shortly before (incoming data)', 'decoder error shortly before', 'throughput close to or below the stream bitrate', 'buffer depleted (cause not identified)', 'not identified'.")  // i18n-ignore
        }

        sec(56, "ADVANCED VIDEO DECODER DIAGNOSTICS") {  // i18n-ignore
            counters(o, "video decoder used", "decoder fallback", "decoder change", "video decoder re", "video decoder errors", "video resolution", "video frame-rate", "video bitrate", "video codec", "video HDR", "streams with HDR", none = "$NO no video decoded this session")  // i18n-ignore
            ringLines(o, "video decoder init ms"); counters(o, "max dropped frames in one report", "dropped video frames")  // i18n-ignore
            o.line("Advertised codecs are in section 33; 'used' above is what actually decoded. HDR metadata in the source does not prove HDR output on the TV.")  // i18n-ignore
            val df = rows("decfail")  // i18n-ignore
            if (df.isEmpty()) o.line("$M decoder failures in retained history: 0") else {  // i18n-ignore
                o.line("$M decoder failures in retained history: ${df.size} — grouped by codec · profile/level · resolution · fps · HDR · decoder:")  // i18n-ignore
                df.groupBy { listOf(S(it, 4), S(it, 5), S(it, 6), S(it, 7), S(it, 8), S(it, 9) + " " + S(it, 10)).joinToString(" · ") }.entries.sortedByDescending { it.value.size }.take(20).forEach { (k, r) ->  // i18n-ignore
                    o.line("   ${r.size}× $k · ${S(r.last(), 3)} · versions " + r.map { v.of(S(it, 1)) }.distinct().joinToString(", ") + " · device ${S(r.last(), 11)} API ${S(r.last(), 12)} · firmware ${S(r.last(), 13)}")  // i18n-ignore
                }
            }
        }

        sec(57, "ADVANCED AUDIO DIAGNOSTICS") {  // i18n-ignore
            counters(o, "audio decoder used", "audio source format", "audio output configured", "audio track", "audio language", "audio focus", "audio output became", "audio playback suppression", "audio underruns", "audio sink errors", "audio decoder error", none = "$NO no audio this session")  // i18n-ignore
            ringLines(o, "audio decoder init ms"); ringLines(o, "audio underrun")  // i18n-ignore
            o.line("$CF Dolby/DTS passthrough: " + (if (Prefs.bool("audio_passthrough", false)) "allowed" else "off (the box decodes to PCM)") + " · sound delay feature: " + (if (Tech.audioDelay) "on, current ${AudioDelay.us / 1000} ms" else "off") + " · FFmpeg audio decoder: " + if (Tech.ffmpeg) "on (only when the box has none)" else "off")  // i18n-ignore
            try {
                val am = ctx.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).forEach { d ->
                    o.line("$D output ${DiagCollect.devType(d.type)}: encodings " + (d.encodings.takeIf { it.isNotEmpty() }?.joinToString(",") ?: "any/unspecified") + " · channels " + (d.channelCounts.takeIf { it.isNotEmpty() }?.joinToString(",") ?: "unspecified"))  // i18n-ignore
                }
            } catch (_: Throwable) { o.line("$UA audio outputs") }  // i18n-ignore
            o.line("'audio output configured' is what the app handed to Android (bitstream = passthrough configured); whether the TV/receiver really decodes it is not observable. HDMI does not tell what device is behind it. A/V sync offset: $UA (not reliably measurable).")  // i18n-ignore
        }

        sec(58, "SUBTITLE DIAGNOSTICS") {  // i18n-ignore
            o.line("$CF automatic subtitles " + (if (Prefs.autoSubs) "on" else "off") + " · languages " + Prefs.subLangs + " · per-title SYNC offsets kept: " + Prefs.allKeys().count { it.startsWith("subfix_") && !it.endsWith("_at") })  // i18n-ignore
            counters(o, "subtitle", none = "$NO no subtitle activity this session"); ringLines(o, "subtitle")  // i18n-ignore
            o.line("No subtitle text, credentials or full links are recorded. Parsing errors inside Media3: $UA separately (they surface as playback errors, section 54). Unsupported formats: offered formats above vs. formats Media3 renders (SRT, SSA/ASS, WebVTT, TTML, CEA-608/708, DVB, PGS).")  // i18n-ignore
        }

        sec(59, "UI RENDERING AND JANK DETECTION") {  // i18n-ignore
            if (Build.VERSION.SDK_INT < 24) { o.line("$NS frame timing needs Android 7"); return@sec }  // i18n-ignore
            o.line("$D display refresh ${f1(DiagII.refresh())} Hz → frame deadline ${f1(1000.0 / DiagII.refresh())} ms. Slow = over the deadline, janky = over 2× the deadline, frozen = over 700 ms. Aggregated per screen; no frame is stored on its own.")  // i18n-ignore
            val jk = rows("jank")  // i18n-ignore
            sc.forEach { s -> val r = jk.filter { s.ok(S(it, 1)) }; if (r.isEmpty()) { o.line("$NO ${s.label}"); return@forEach }  // i18n-ignore
                o.line("-- ${s.label}")  // i18n-ignore
                r.groupBy { S(it, 2) }.toSortedMap().forEach { (scr, x) -> val fr = x.sumOf { L(it, 3) ?: 0L }
                    o.line("   $M $scr: $fr frames · slow ${share(x.sumOf { L(it, 4) ?: 0L }, fr)} · janky ${share(x.sumOf { L(it, 5) ?: 0L }, fr)} · frozen ${x.sumOf { L(it, 6) ?: 0L }} · p50 ${pct(x.mapNotNull { L(it, 7) }, .5)} ms · p95 (visit) ${pct(x.mapNotNull { L(it, 8) }, .5)} ms · worst ${x.mapNotNull { L(it, 9) }.maxOrNull() ?: 0L} ms") }  // i18n-ignore
            }
            ringLines(o, "first drawn frame after navigation ms ", none = "$NO first drawn frame after navigation (needs Android 8)"); ringLines(o, "remote key to next completed frame ms")  // i18n-ignore
            o.line("Time to first composition is in section 45 (not a drawn frame). Main-thread blocking: freezes/ANRs in section 28. Scrolling is included in the screen totals. Remote key → next frame is a lower bound of the response time.")  // i18n-ignore
        }

        sec(60, "REMOTE CONTROL AND NAVIGATION DIAGNOSTICS") {  // i18n-ignore
            counters(o, "focus ", "navigation request answered", "remembered row", none = "$M focus failures this session: 0")  // i18n-ignore
            o.line("Request → first composition per screen: section 45. Key sequences, PIN entry and search text are never recorded. Unexpected BACK transitions and scroll-restoration failures: $UA (no dedicated hook in this version; reported as focus failures where they lose the focus).")  // i18n-ignore
        }

        sec(61, "ADVANCED MEMORY LEAK DETECTION") {  // i18n-ignore
            val rt = Runtime.getRuntime()
            o.line("$M now: Java heap ${(rt.totalMemory() - rt.freeMemory()) / 1_048_576} MB used · ${rt.totalMemory() / 1_048_576} MB allocated · ${rt.maxMemory() / 1_048_576} MB max · native ${android.os.Debug.getNativeHeapAllocatedSize() / 1_048_576} MB · players ${DiagCollect.playersLive.get()}")  // i18n-ignore
            counters(o, "Android memory trim", none = "$M Android memory trim requests: 0 this session")  // i18n-ignore
            ringLines(o, "heap KB ", "KB"); ringLines(o, "native KB ", "KB")  // i18n-ignore
            val mem = rows("mem")  // i18n-ignore
            o.line("Samples every 5 minutes while the app runs (no wake-ups, no service, no forced garbage collection). Graphics memory every 30 minutes where Android gives it.")  // i18n-ignore
            mem.filter { S(it, 1) == PerfLog.session }.let { r ->
                if (r.isEmpty()) o.line("$ID this session: no memory row yet (first after ~1–5 min with 'Praćenje dugog rada' on)") else {  // i18n-ignore
                    val last = r.last()
                    o.line("$M latest (uptime ${S(last, 17)} min, ${S(last, 2)}): heap ${S(last, 3)}/${S(last, 4)}/${S(last, 5)} MB · native ${S(last, 6)} MB · PSS ${S(last, 7)} MB · RSS ${S(last, 8).ifBlank { "n/a" }} MB · graphics ${S(last, 9).ifBlank { "n/a" }} MB · threads ${S(last, 10)} · open files ${S(last, 11)} · GC ${S(last, 12)} runs / ${S(last, 13)} ms · free RAM ${S(last, 14)} MB · low RAM ${S(last, 15)} · image cache ${S(last, 18)} MB")  // i18n-ignore
                    listOf(Pair(60L, "1 h"), Pair(360L, "6 h"), Pair(720L, "12 h"), Pair(1440L, "24 h"), Pair(4320L, "72 h"), Pair(10080L, "7 days")).forEach { (w, lbl) ->  // i18n-ignore
                        val idle = r.filter { S(it, 2) == "idle" }  // i18n-ignore
                        val base = idle.filter { (L(it, 17) ?: 0L) <= 30 }; val at = idle.filter { val u = L(it, 17) ?: 0L; u >= w * 8 / 10 && u <= w * 12 / 10 }
                        if (base.isEmpty() || at.isEmpty()) o.line("   $ID idle comparison start → $lbl of uptime") else  // i18n-ignore
                            o.line("   $M idle start → $lbl: heap ${pct(base.mapNotNull { L(it, 3) }, .5)}→${pct(at.mapNotNull { L(it, 3) }, .5)} MB · native ${pct(base.mapNotNull { L(it, 6) }, .5)}→${pct(at.mapNotNull { L(it, 6) }, .5)} · PSS ${pct(base.mapNotNull { L(it, 7) }, .5)}→${pct(at.mapNotNull { L(it, 7) }, .5)} · threads ${pct(base.mapNotNull { L(it, 10) }, .5)}→${pct(at.mapNotNull { L(it, 10) }, .5)} · files ${pct(base.mapNotNull { L(it, 11) }, .5)}→${pct(at.mapNotNull { L(it, 11) }, .5)} · cache ${pct(base.mapNotNull { L(it, 18) }, .5)}→${pct(at.mapNotNull { L(it, 18) }, .5)} MB")  // i18n-ignore
                    }
                    val s = memSuspects(r); if (s.isEmpty()) o.line("No suspicious growth found by the rules (growth alone is never called a leak).") else s.forEach { o.line(it) }  // i18n-ignore
                }
            }
            o.line("$M longest single process in retained history: ${mem.mapNotNull { L(it, 17) }.maxOrNull() ?: 0L} min · rows ${mem.size} (bounded; older rows are dropped first)")  // i18n-ignore
        }

        sec(62, "DATABASE INTEGRITY DIAGNOSTICS") {  // i18n-ignore
            val f = ctx.getDatabasePath("catalog.db")  // i18n-ignore
            fun kb(x: java.io.File) = if (x.exists()) "${x.length() / 1024} KB" else "absent"  // i18n-ignore
            o.line("$M catalog.db ${kb(f)} · WAL ${kb(java.io.File(f.path + "-wal"))} · SHM ${kb(java.io.File(f.path + "-shm"))} · other databases: " + ctx.databaseList().filter { !it.startsWith("catalog") }.joinToString(", ").ifBlank { "none" })  // i18n-ignore
            if (DiagCollect.playState() != "not playing") o.line("Record counts: deferred (playback is active; no extra database work during playback)") else try {  // i18n-ignore
                CatalogDb.db()?.let { d ->
                    d.rawQuery("SELECT kind, COUNT(*) FROM items GROUP BY kind", null).use { c -> while (c.moveToNext()) o.line("$M records ${c.getString(0)}: ${c.getLong(1)}") }  // i18n-ignore
                    d.rawQuery("SELECT COUNT(*) FROM progs", null).use { c -> if (c.moveToFirst()) o.line("$M guide programmes: ${c.getLong(0)}") }  // i18n-ignore
                }
            } catch (e: Throwable) { o.line("record counts failed: " + DiagII.dbError("report counts", e)) }  // i18n-ignore
            counters(o, "database", "slow database", none = "$M database errors this session: 0")  // i18n-ignore
            val db = rows("db")  // i18n-ignore
            if (db.isEmpty()) o.line("$NO no database operation rows in history") else db.groupBy { S(it, 2) }.toSortedMap().forEach { (op, r) ->  // i18n-ignore
                o.line("$M $op: " + dist(r.mapNotNull { L(it, 3) }) + " · failures ${r.count { S(it, 5) != "ok" }}" + (r.lastOrNull { S(it, 5) != "ok" }?.let { " (last: ${S(it, 5)} at ${S(it, 0)})" } ?: ""))  // i18n-ignore
            }
            o.line("No integrity check (PRAGMA integrity_check) runs automatically; nothing is deleted, rebuilt or repaired by diagnostics. Weekly VACUUM is the app's existing maintenance.")  // i18n-ignore
        }

        sec(63, "PLAYLIST QUALITY INTELLIGENCE") {  // i18n-ignore
            o.line("Counted during the app's own imports only (no extra request, no stream opened). Duplicate = same provider id twice in one list (M3U: same link). Invalid = no id, name or link. Missing image / category / EPG id are optional data, never 'invalid'. Episodes are not counted (would need a request per series).")  // i18n-ignore
            val pls = Store.playlists(ctx); o.line("$M playlists ${pls.size} · active ${pls.count { it.active }} · types " + pls.groupBy { it.type.name }.entries.joinToString(", ") { "${it.key}×${it.value.size}" })  // i18n-ignore
            val r = rows("playlist")  // i18n-ignore
            if (r.isEmpty()) o.line("$NO no import recorded since 3.0.0.7") else {  // i18n-ignore
                r.groupBy { S(it, 2) + " · " + S(it, 3) }.toSortedMap().forEach { (k, x) -> val l = x.last()  // i18n-ignore
                    o.line("$M latest $k (${S(l, 0)}): items ${S(l, 4)} · categories ${S(l, 5)} · duplicates ${S(l, 6)} · invalid ${S(l, 11)} · missing name ${S(l, 7)} / category ${S(l, 8)} / image ${S(l, 9)}" + (if (S(l, 10).isNotBlank()) " / EPG id ${S(l, 10)}" else "") + " · ${S(l, 12)} ms · previous count ${S(l, 13).ifBlank { "n/a" }} · ${S(l, 14)} · imports kept ${x.size}")  // i18n-ignore
                }
            }
            o.line("Added/updated/removed per item: $UA (the catalog is replaced as a whole; only the count before and after is known). Unsupported formats and parse errors appear as import failures above.")  // i18n-ignore
        }

        sec(64, "DEVICE AND FIRMWARE INTELLIGENCE") {  // i18n-ignore
            o.line("$D ${Build.MANUFACTURER} / brand ${Build.BRAND} / model ${Build.MODEL} / product ${Build.PRODUCT} / device ${Build.DEVICE} / board ${Build.BOARD} / hardware ${Build.HARDWARE}")  // i18n-ignore
            o.line("$D Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · security patch ${if (Build.VERSION.SDK_INT >= 23) Build.VERSION.SECURITY_PATCH else "unavailable"} · firmware ${Build.DISPLAY} · build ${Build.ID} ${Build.VERSION.INCREMENTAL} · fingerprint ${Build.FINGERPRINT}")  // i18n-ignore
            o.line("$M firmware change seen: " + Prefs.long("diag2_fw_changed_at", 0).let { if (it > 0) DiagEvents.stamp(it) else "none since 3.0.0.7" })  // i18n-ignore
            o.line("$D SoC: " + if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else "$NS (Android 12+)")  // i18n-ignore
            o.line("$D device ABIs ${Build.SUPPORTED_ABIS.joinToString(", ")} · this process ${if (android.os.Process.is64Bit()) "64-bit" else "32-bit"} · cores ${Runtime.getRuntime().availableProcessors()}")  // i18n-ignore
            try { val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager; val mi = android.app.ActivityManager.MemoryInfo(); am.getMemoryInfo(mi)
                o.line("$D RAM ${mi.totalMem / 1_048_576} MB · Android low-RAM device: ${am.isLowRamDevice} · app memory class ${am.memoryClass} MB (large ${am.largeMemoryClass} MB) · OpenGL ES ${am.deviceConfigurationInfo.glEsVersion}") } catch (_: Throwable) { o.line("$UA RAM") }  // i18n-ignore
            val pm = ctx.packageManager
            o.line("$D Vulkan hardware: " + (if (Build.VERSION.SDK_INT >= 24 && pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)) "yes" else "not reported") + " · GPU renderer/vendor: $UA (would need a graphics context; not created for diagnostics)")  // i18n-ignore
            o.line("$D Media3 ${try { androidx.media3.common.MediaLibraryInfo.VERSION } catch (_: Throwable) { "?" }} · app performance mode ${if (Perf.lite) "Lite" else "Full"} (score ${Perf.score}) · display " + DiagCollect.mode(ctx))  // i18n-ignore
            try { val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager; val d = dm.getDisplay(0)
                o.line("$D display modes: " + d.supportedModes.joinToString(", ") { "${it.physicalWidth}x${it.physicalHeight}@${f1(it.refreshRate.toDouble())}" }.take(600))  // i18n-ignore
                @Suppress("DEPRECATION") o.line("$D HDR types the display reports: " + (d.hdrCapabilities?.supportedHdrTypes?.joinToString(",") { when (it) { 1 -> "Dolby Vision"; 2 -> "HDR10"; 3 -> "HLG"; 4 -> "HDR10+"; else -> "type $it" } }?.ifBlank { "none" } ?: "none"))  // i18n-ignore
            } catch (_: Throwable) { o.line("$UA display capabilities") }  // i18n-ignore
            o.line("$M storage free ${ctx.filesDir.usableSpace / 1_048_576} MB of ${ctx.filesDir.totalSpace / 1_048_576} MB. No serial number, IMEI, MAC or advertising ID is read; the installation ID (section 2) stays the only identifier.")  // i18n-ignore
        }

        sec(65, "APPLICATION EXIT INTELLIGENCE") {  // i18n-ignore
            if (Build.VERSION.SDK_INT < 30) o.line("$NS Android gives the reason of earlier exits only from Android 11; crashes and freezes recorded by the app itself are in sections 27–28.")  // i18n-ignore
            val ex = rows("exits").distinctBy { S(it, 2) + S(it, 3) }  // i18n-ignore
            if (ex.isEmpty()) o.line("$NO no earlier exit recorded since 3.0.0.7") else {  // i18n-ignore
                o.line("$D Android's record of how earlier runs ended (deduplicated, oldest first). A termination is not automatically a crash; ambiguous reasons stay ambiguous:")  // i18n-ignore
                ex.takeLast(40).forEach { o.line("   ${S(it, 2)} · ${S(it, 11)} · reason ${S(it, 3)} · status ${S(it, 4)} · importance ${S(it, 5)} · PSS ${S(it, 6)} MB · RSS ${S(it, 7)} MB" + (if (S(it, 10).isNotBlank()) " · run of ${S(it, 10)} (session ${S(it, 9)})" else "") + (if (S(it, 8).isNotBlank()) " · ${S(it, 8)}" else "")) }  // i18n-ignore
                o.line("$M totals: " + ex.groupBy { S(it, 11) }.entries.joinToString(" · ") { "${it.key} ${it.value.size}" })  // i18n-ignore
            }
        }

        sec(66, "UPDATE AND MIGRATION DIAGNOSTICS") {  // i18n-ignore
            o.line("$M now ${BuildConfig.VERSION_NAME} (versionCode ${BuildConfig.VERSION_CODE}) · build ${BuildConfig.BUILD_TYPE} · commit ${BuildConfig.GIT_COMMIT} (written in at build time) · previous version ${Prefs.str("diag_prev_version").ifBlank { "unknown" }}")  // i18n-ignore
            try { val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0); o.line("$D installed ${DiagEvents.stamp(pi.firstInstallTime)} · last update ${DiagEvents.stamp(pi.lastUpdateTime)}") } catch (_: Throwable) {}  // i18n-ignore
            val up = rows("updates")  // i18n-ignore
            if (up.isEmpty()) o.line("$NO no update recorded since 3.0.0.7 (the first update after this version will be)") else up.takeLast(15).forEach {  // i18n-ignore
                o.line("$M ${S(it, 0)}: ${S(it, 2)} (${S(it, 3)}) → ${S(it, 4)} (${S(it, 5)}) · commit ${S(it, 6).take(12)} · first screen ${S(it, 7).ifBlank { "not measured" }} ms · device ID kept ${S(it, 8)} · data ${S(it, 11)}")  // i18n-ignore
                o.line("      counts before: ${S(it, 9).replace(';', ' ')} · after: ${S(it, 10).replace(';', ' ')}")  // i18n-ignore
            }
            o.line("Preserved data is verified by aggregate counts only (playlists, favourites, recent channels, progress, finished titles, settings) — never by exporting the content. Database migrations: section 62 ('migration' rows). Preference migration: $UA separately. Diagnostics migration: schema ${DiagII.SCHEMA}, new tables added next to the old ones, nothing reset.")  // i18n-ignore
            o.line("APK integrity: not verified by the app (Android checks the signature at install; the app does not repeat or claim it).")  // i18n-ignore
        }

        sec(67, "TECHNICAL SETTINGS SNAPSHOT") {  // i18n-ignore
            o.line("Explicit allow-list only; logins, links, keys, tokens, PINs and private headers are never listed. [configured] = the setting, [measured]/[device reported] = what actually happened.")  // i18n-ignore
            o.line("$CF decoder choice ${Tech.decoder} (0 automatic) · FFmpeg audio ${Tech.ffmpeg} · auto frame rate ${Tech.afr} · audio passthrough ${Prefs.bool("audio_passthrough", false)} · sound delay feature ${Tech.audioDelay} · live pause ${Tech.livePause} · rewind ${Tech.rewind}")  // i18n-ignore
            o.line("$CF buffer profile '${Buffer.OPTIONS.getOrElse(Buffer.choice) { "?" }}' (actual target ${Buffer.mb} MB) · automatic recovery ${Recover.on} (wait ${Recover.waitMs} ms) · backup stream search ${Tech.backup} · connect/read timeout ${PlayOpts.connectMs}/${PlayOpts.readMs} ms")  // i18n-ignore
            o.line("$CF channel health: " + (if (ChannelHealth.probeOn) "active checks ON (owner opt-in)" else "passive (default)") + " · provider protection: ${ProbeGuard.PER_HOUR} checks/hour, ${ProbeGuard.GAP_MS / 1000} s apart, cooldown on 458/429")  // i18n-ignore
            o.line("$CF long-run monitoring ${Tech.monitor} · database cleanup ${Tech.dbClean} · performance mode " + (if (Perf.mode == 0) "automatic" else if (Perf.mode == 1) "Lite" else "Full") + " (actual ${if (Perf.lite) "Lite" else "Full"}) · screensaver ${Saver.minutes} min, mode ${Saver.mode}")  // i18n-ignore
            o.line("$CF subtitles auto ${Prefs.autoSubs} · subtitle languages ${Prefs.subLangs} · audio languages ${Prefs.audioLangs} · udpxy proxy " + (if (Prefs.str("net_udpxy").isBlank()) "not set" else "set") + " · custom user agent " + if (Prefs.str("net_ua").isBlank()) "no" else "yes")  // i18n-ignore
            o.line("$D VPN now ${if (DiagII.vpnOn()) "detected" else "not detected"} · decoder actually used: section 56 · display mode actually set: section 37.")  // i18n-ignore
        }

        sec(68, "VERSION COMPARISON INTELLIGENCE") { compare(o, v) }  // i18n-ignore

        sec(69, "DIAGNOSTIC EXPORT AND QR SECURITY") {  // i18n-ignore
            o.line("Audit of the existing export (unchanged workflow): the report is written line by line to a temporary file in the app cache; every line passes the central redaction (logins in links and Xtream paths, user:pass@ links, Authorization/Bearer, cookies, URL-encoded logins, MAC addresses, JSON secrets, Google keys, bare IP servers in links) and then a search for every real secret stored on this box — if one would remain, the export is refused.")  // i18n-ignore
            o.line("Transfer: a QR code with a one-off link on the local network: own socket per export, random 128-bit key compared in constant time, link valid 5 minutes, at most 3 tries, one file only, the temporary file is deleted when the window closes. The transfer is plain HTTP inside the home network (not encrypted) — the app says so on the phone page. No remote upload exists.")  // i18n-ignore
            counters(o, "diagnostic", none = "$NO no export this session before this one")  // i18n-ignore
            val ex = rows("exports")  // i18n-ignore
            if (ex.isNotEmpty()) { o.line("$M exports in history: ${ex.size} · outcomes " + ex.groupBy { S(it, 5) }.entries.joinToString(" · ") { "${it.key} ${it.value.size}" } + " · generation time " + dist(ex.mapNotNull { L(it, 3) }))  // i18n-ignore
                ex.takeLast(5).forEach { o.line("   ${S(it, 0)} · ${S(it, 2)} · ${S(it, 3)} ms · ${(L(it, 4) ?: 0L) / 1024} KB · ${S(it, 5)} · SHA-256 ${S(it, 6).ifBlank { "n/a" }}") } }  // i18n-ignore
            o.line("This report: schema ${DiagII.SCHEMA} (sections 1–70). Its own duration, size, integrity check (non-empty, valid UTF-8) and SHA-256 prefix are recorded after it is finished and appear in the next report. Whether the phone really received it is seen only as 'file served to the phone'.")  // i18n-ignore
        }
    }

    // =============================================================== section 68
    private class Metric(val name: String, val unit: String, val values: (Long) -> List<Long>, val higherIsWorse: Boolean = true, val minN: Int = 10)
    private fun compare(o: DiagReport.Out, v: Versions) {
        o.line("Each new sample carries its session; the session carries version, versionCode and build commit. Samples recorded before 3.0.0.7 are 'version unknown' and are never assigned to a version afterwards.")  // i18n-ignore
        val byCode = v.code.entries.groupBy({ it.value }, { it.key })
        byCode.keys.sortedDescending().forEach { c -> o.line("$M version ${v.label(c)}: ${byCode[c]?.size ?: 0L} session(s) · commit ${byCode[c]?.firstOrNull()?.let { v.commit[it] }?.take(12)?.ifBlank { "unknown" } ?: "unknown"}") }  // i18n-ignore
        val unknown = HashSet<String>(); listOf("samples", "playback", "tasks").forEach { f -> PerfLog.each(f) { l -> val s = l.split(',').getOrNull(1) ?: ""; if (v.codeOf(s) == null) unknown += s } }  // i18n-ignore
        o.line("$M sessions with version unknown (before 3.0.0.7): ${unknown.size}")  // i18n-ignore
        val prev = v.previous()
        if (prev == null) { o.line("$ID no earlier version with recorded sessions on this box yet — the comparison starts with the next update."); return }  // i18n-ignore
        o.line("Comparing ${BuildConfig.VERSION_NAME} with ${v.label(prev)} on this same device. A change % is shown only when both sides have enough samples of the same kind, state and network; single sessions are never proof.")  // i18n-ignore
        val samples = rows("samples"); val play = rows("playback"); val tasks = rows("tasks"); val qoe = rows("qoe"); val mem = rows("mem"); val jank = rows("jank"); val img = rows("imgagg"); val db = rows("db"); val dec = rows("decfail")  // i18n-ignore
        fun sesOf(code: Long) = byCode[code]?.toSet() ?: emptySet()
        val net = qoe.groupBy { S(it, 3) }.maxByOrNull { it.value.size }?.key ?: "unknown"  // i18n-ignore
        val metrics = listOf(
            Metric("app start → first screen", "ms", { c -> tasks.filter { S(it, 1) in sesOf(c) && S(it, 2) == "app start" }.mapNotNull { L(it, 4) } }, minN = 3),  // i18n-ignore
            Metric("CPU while idle (% of one core)", "%", { c -> samples.filter { S(it, 1) in sesOf(c) && S(it, 2) == "idle" }.mapNotNull { L(it, 3) } }),  // i18n-ignore
            Metric("CPU during playback (% of one core)", "%", { c -> samples.filter { S(it, 1) in sesOf(c) && S(it, 2) == "playback" }.mapNotNull { L(it, 3) } }),  // i18n-ignore
            Metric("Java heap idle", "MB", { c -> samples.filter { S(it, 1) in sesOf(c) && S(it, 2) == "idle" }.mapNotNull { L(it, 4) } }),  // i18n-ignore
            Metric("native heap idle", "MB", { c -> samples.filter { S(it, 1) in sesOf(c) && S(it, 2) == "idle" }.mapNotNull { L(it, 6) } }),  // i18n-ignore
            Metric("PSS idle", "MB", { c -> samples.filter { S(it, 1) in sesOf(c) && S(it, 2) == "idle" }.mapNotNull { L(it, 5) } }),  // i18n-ignore
            Metric("live TV first picture (channel switch)", "ms", { c -> play.filter { S(it, 1) in sesOf(c) && S(it, 2) == "tv" && S(it, 3) == "first_frame" }.mapNotNull { L(it, 4) } }),  // i18n-ignore
            Metric("movie first picture", "ms", { c -> play.filter { S(it, 1) in sesOf(c) && S(it, 2) == "movie" && S(it, 3) == "first_frame" }.mapNotNull { L(it, 4) } }),  // i18n-ignore
            Metric("series first picture", "ms", { c -> play.filter { S(it, 1) in sesOf(c) && S(it, 2) == "series" && S(it, 3) == "first_frame" }.mapNotNull { L(it, 4) } }),  // i18n-ignore
            Metric("live TV startup to READY ($net)", "ms", { c -> qoe.filter { S(it, 1) in sesOf(c) && S(it, 2) == "tv" && S(it, 3) == net }.mapNotNull { L(it, 5) } }),  // i18n-ignore
            Metric("rebuffering event duration (live TV, $net)", "ms", { c -> rows("rebuf").filter { S(it, 1) in sesOf(c) && S(it, 2) == "tv" && S(it, 3) == net }.mapNotNull { L(it, 4) } }),  // i18n-ignore
            Metric("navigation to first composition", "ms", { c -> tasks.filter { S(it, 1) in sesOf(c) && S(it, 2).startsWith("nav ") }.mapNotNull { L(it, 4) } }),  // i18n-ignore
            Metric("UI frame p95 per screen visit", "ms", { c -> jank.filter { S(it, 1) in sesOf(c) }.mapNotNull { L(it, 8) } }),  // i18n-ignore
            Metric("artwork load (median per 30 min)", "ms", { c -> img.filter { S(it, 1) in sesOf(c) && S(it, 2) == "artwork" }.mapNotNull { L(it, 4) } }, minN = 3),  // i18n-ignore
            Metric("database search", "ms", { c -> db.filter { S(it, 1) in sesOf(c) && S(it, 2) == "search" }.mapNotNull { L(it, 3) } }),  // i18n-ignore
            Metric("diagnostics storage", "KB", { c -> mem.filter { S(it, 1) in sesOf(c) }.mapNotNull { L(it, 19) } }, minN = 3),  // i18n-ignore
        )
        metrics.forEach { m ->
            val a = m.values(v.cur); val b = m.values(prev)
            val line = "${m.name}: ${BuildConfig.VERSION_NAME} " + (if (a.isEmpty()) "no samples" else "median ${pct(a, .5)} ${m.unit} (n=${a.size})") + " · ${v.label(prev)} " + (if (b.isEmpty()) "no samples" else "median ${pct(b, .5)} ${m.unit} (n=${b.size})")  // i18n-ignore
            if (a.size < m.minN || b.size < m.minN || pct(b, .5) == 0L) o.line("$ID $line") else {  // i18n-ignore
                val ch = (pct(a, .5) - pct(b, .5)) * 100.0 / pct(b, .5)
                o.line("$M $line · change ${if (ch >= 0) "+" else ""}${f1(ch)}% (${if (kotlin.math.abs(ch) < 5) "about the same" else if ((ch > 0) == m.higherIsWorse) "worse" else "better"})")  // i18n-ignore
            }
        }
        // rates: per playback hour, comparable only with enough hours on both sides
        fun rate(c: Long, idx: Int, type: String): Pair<Double, Double> { val r = qoe.filter { S(it, 1) in sesOf(c) && S(it, 2) == type }; return r.sumOf { L(it, idx) ?: 0L }.toDouble() to r.sumOf { L(it, 6) ?: 0L } / 3_600_000.0 }
        listOf(Pair(8, "rebuffering events"), Pair(14, "playback errors")).forEach { (idx, what) -> listOf("tv", "movie", "series").forEach { t ->  // i18n-ignore
            val (an, ah) = rate(v.cur, idx, t); val (bn, bh) = rate(prev, idx, t)
            if (ah < 2 || bh < 2) o.line("$ID $what per playback hour ($t): ${f1(ah)} h vs ${f1(bh)} h observed (needs 2 h on both sides)")  // i18n-ignore
            else o.line("$M $what per playback hour ($t): ${f1(an / ah)} over ${f1(ah)} h vs ${f1(bn / bh)} over ${f1(bh)} h")  // i18n-ignore
        } }
        listOf(v.cur, prev).forEach { c -> val r = qoe.filter { S(it, 1) in sesOf(c) }; val att = r.sumOf { L(it, 15) ?: 0L }
            o.line((if (att >= 5) M else ID) + " recovery ${v.label(c)}: ${r.sumOf { L(it, 16) ?: 0L }} of $att attempts succeeded · decoder failures ${dec.count { S(it, 1) in sesOf(c) }} over ${f1(r.sumOf { L(it, 6) ?: 0L } / 3_600_000.0)} playback hours") }  // i18n-ignore
    }
}
