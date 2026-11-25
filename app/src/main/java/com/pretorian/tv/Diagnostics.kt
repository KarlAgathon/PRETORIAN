package com.pretorian.tv

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnostic events (2.8.0): the last 300 are kept in memory for quick use, and every event also goes to the persistent
 * journal of [DiagStore] (10,000 events / 90 days on the box). Every line passes through [Redact] on the way in.
 * Line format: "yyyy-MM-dd HH:mm:ss.SSS|level|kind|message"; level C = critical (written at once), E error, W warning, I info.
 */
object DiagEvents {
    const val MAX = 300
    private val list = ArrayDeque<String>()
    private val fmt = object : ThreadLocal<SimpleDateFormat>() { override fun initialValue() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US) }  // i18n-ignore
    private val CRITICAL = setOf("crash", "freeze", "anr", "native", "fatal")
    private val INFO = setOf("app", "update", "start", "screen", "health")
    // 3.0.0.8 (audit): before, every kind not listed above was stored as "E", so screen-saver, display-mode, decoder-ready
    // and session lines filled the critical/error history (section 29). Now the level comes from the kind and the message.
    private val ERROR_KINDS = setOf("player", "neterr")  // i18n-ignore
    private val ERR_WORDS = listOf("error", "failed", "failure", "exception", "could not", "not supported", "corrupt", "crash")  // i18n-ignore
    private val WARN_WORDS = listOf("warn", "slow", "timeout", "timed out", "refused", "limit", "cooldown", "restriction", "http 4", "http 5", "dropped", "ended early", "stall", "gave up", "low memory", "trim")  // i18n-ignore
    fun level(kind: String, msg: String?): String {
        val m = (msg ?: "").lowercase()
        return when {
            kind in CRITICAL -> "C"
            kind in ERROR_KINDS -> "E"
            kind.endsWith("warn") -> "W"
            ERR_WORDS.any { m.contains(it) } -> "E"
            WARN_WORDS.any { m.contains(it) } || kind == "stall" -> "W"  // i18n-ignore
            else -> "I"
        }
    }
    /** the level of a stored line, worked out again with today's rules (lines written before 3.0.0.8 carry the old "E") */
    fun levelOf(line: String): String {
        val p = line.split('|', limit = 4)
        return if (p.size < 4) "I" else if (p[1] == "C") "C" else level(p[2], p[3])
    }
    fun stamp(t: Long = System.currentTimeMillis()): String = fmt.get()!!.format(Date(t))
    fun parseTime(line: String): Long = try { fmt.get()!!.parse(line.substring(0, 23))?.time ?: 0L } catch (_: Throwable) { 0L }

    fun add(kind: String, msg: String?) {
        try {
            val lvl = if (kind in INFO && kind !in CRITICAL) "I" else level(kind, msg)
            val line = stamp() + "|" + lvl + "|" + kind + "|" + Redact.text((msg ?: "").replace('\n', ' ').replace('|', '/').take(600))
            synchronized(list) { list.addLast(line); while (list.size > MAX) list.removeFirst() }
            DiagStore.enqueue(line, lvl == "C")
        } catch (_: Throwable) {}
    }
    fun lines(): List<String> = synchronized(list) { list.toList() }
    fun clearForTest() = synchronized(list) { list.clear() }
}

/** Diagnostics write nothing below the free-space floor of [DiagStore] (100 MB), so a full box never crashes because of a log. */
object DiagSpace {
    const val MIN_FREE = DiagStore.MIN_FREE
    fun ok(dir: File?): Boolean = try { dir == null || (dir.usableSpace <= 0L) || dir.usableSpace >= MIN_FREE } catch (_: Throwable) { false }
}

/**
 * The last line of defence before a report leaves the box: the real secret values stored on this box
 * (playlist logins, links, MAC and device ids, API keys, PIN hash) are searched for and replaced.
 * If the values cannot be read, or one is still found afterwards, the report is refused.
 */
object SecretScan {
    class Refused(msg: String) : Exception(msg)

    /** every secret value this box knows (long ones), plus the short ones (1–3 characters) handled only where they stand as a login part */
    fun values(ctx: Context): List<String> {
        val out = ArrayList<String>()
        Store.playlists(ctx).forEach { p ->
            out += listOf(p.user, p.pass, p.mac, p.serial, p.deviceId, p.deviceId2)
            // an M3U link usually carries the login itself; the whole link is replaced
            if (p.type == SourceType.M3U) out += p.url
        }
        try { EpgSources.list().forEach { s -> if (Regex("(?i)(user|pass|token|key)=").containsMatchIn(s.url)) out += s.url } } catch (_: Throwable) {}
        val all = Prefs.allStrings()
        all.filter { (k, _) -> k.endsWith("_key") || k.startsWith("ai_key") || k == "gemini_key" || k == "pin_hash" || k == "pin_salt" }  // i18n-ignore
            .forEach { (_, v) -> out += Secret.open(v); out += v }
        return out.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sortedByDescending { it.length }
    }

    /** returns the cleaned text, or throws [Refused] when the report cannot be confirmed safe */
    fun clean(text: String, secrets: List<String>): String {
        var t = Redact.text(text)
        for (s in secrets) {
            if (s.length < 4) {
                // too short to replace everywhere: only where it stands as a login part (/x/, =x&, :x@)
                t = t.replace(Regex("(?<=[/=:])" + Regex.escape(s) + "(?=[/&@\\s]|$)"), "***")
                continue
            }
            t = t.replace(s, "***")
            // the same value URL-encoded (a login inside a link)
            val enc = try { java.net.URLEncoder.encode(s, "UTF-8") } catch (_: Exception) { s }
            if (enc != s) t = t.replace(enc, "***")
        }
        if (secrets.any { it.length >= 4 && it in t }) throw Refused("secret still present")  // i18n-ignore
        return t
    }
}

/**
 * The three exports (2.8.0): the latest crash report (TXT), the complete report (TXT) and the complete archive (ZIP).
 * Each is written to a temporary file in the app cache, line by line through [SecretScan] (never whole in memory),
 * and served once to the phone. Crash and freeze reports are never shortened by the export; what is left out
 * (oldest events past the size limit) is said in the file.
 */
object DiagReport {
    enum class Kind { LATEST, FULL }
    /** the complete TXT stays readable on a phone: past this the oldest events are left out (and counted) */
    /** 3.0.0.1: the full report is one TXT of at most 40 MB (the diagnostics ceiling of 50 MB includes exports) */
    private const val FULL_TXT_MAX = 40L * 1024 * 1024

    private fun tsName() = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())  // i18n-ignore
    /** "PTR-8F2A-91C4-7D03_Living-Room_DIAGNOSTICS_2026-11-24_174218.txt" — only letters, digits, - and _; the real name stays inside */
    fun fileName(k: Kind = Kind.FULL): String = DeviceIdentity.short() + "_" + DeviceIdentity.fileSafe() + "_" +  // i18n-ignore
        (if (k == Kind.LATEST) "CRASH" else "DIAGNOSTICS") + "_" + SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date()) + ".txt"  // i18n-ignore

    /** facts about the app, the box and the network; "nicht verfügbar" is written as "unavailable", never guessed */
    fun header(ctx: Context): String {
        val b = StringBuilder()
        val tz = java.util.TimeZone.getDefault()
        b.append("Media Center ").append(BuildConfig.VERSION_NAME).append(" (versionCode ").append(BuildConfig.VERSION_CODE).append(", ")
            .append(BuildConfig.BUILD_TYPE).append(")\n")  // i18n-ignore
        b.append("Device ID: ").append(DeviceIdentity.id()).append(" (").append(DeviceIdentity.short()).append(")\n")  // i18n-ignore
        b.append("Device name: ").append(DeviceIdentity.name().ifBlank { "not set" }).append('\n')  // i18n-ignore
        b.append("Release channel: ").append(if (BuildConfig.BUILD_TYPE == "beta") "Beta" else "Stable").append(" · session ").append(PerfLog.session).append('\n')  // i18n-ignore
        try { val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            b.append("Installed: ").append(DiagEvents.stamp(pi.firstInstallTime)).append(" · last update: ").append(DiagEvents.stamp(pi.lastUpdateTime)).append('\n') } catch (_: Throwable) { b.append("Installed: unavailable\n") }  // i18n-ignore
        b.append("Session started: ").append(DiagEvents.stamp(DiagCollect.sessionStartWall)).append(" · report schema ").append(DiagII.SCHEMA).append(" (event journal schema ").append(DiagCollect.SCHEMA).append(")").append(" · build commit: ").append(BuildConfig.GIT_COMMIT).append('\n')  // i18n-ignore
        b.append("Previous version: ").append(Prefs.str("diag_prev_version").ifBlank { "unknown" }).append('\n')  // i18n-ignore
        b.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append(" · Android ").append(Build.VERSION.RELEASE)
            .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")  // i18n-ignore
        b.append("Report time: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())).append(" (").append(tz.id).append(")\n")  // i18n-ignore
        b.append("Screen: ").append(CrashLog.screen.ifBlank { "unknown" }).append('\n')  // i18n-ignore
        val up = android.os.SystemClock.elapsedRealtime() - android.os.Process.getStartElapsedRealtime()
        b.append("App uptime: ").append(up / 60_000).append(" min\n")  // i18n-ignore
        try {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val mi = android.app.ActivityManager.MemoryInfo(); am.getMemoryInfo(mi)
            b.append("RAM: ").append(mi.availMem / 1_048_576).append(" MB free of ").append(mi.totalMem / 1_048_576).append(" MB · low memory: ").append(mi.lowMemory).append('\n')  // i18n-ignore
        } catch (_: Throwable) { b.append("RAM: unavailable\n") }  // i18n-ignore
        val rt = Runtime.getRuntime()
        b.append("App heap: ").append((rt.totalMemory() - rt.freeMemory()) / 1_048_576).append(" / ").append(rt.maxMemory() / 1_048_576).append(" MB\n")  // i18n-ignore
        b.append("Free storage: ").append(ctx.filesDir.usableSpace / 1_048_576).append(" MB · diagnostics on the box: ")
            .append(DiagStore.totalBytes() / 1024).append(" KB, ").append(DiagStore.incidents().size).append(" incidents, ")
            .append(DiagStore.eventCount()).append(" events\n")  // i18n-ignore
        b.append("Media3: ").append(try { androidx.media3.common.MediaLibraryInfo.VERSION } catch (_: Throwable) { "unavailable" }).append('\n')  // i18n-ignore
        b.append("Video decoders: ").append(decoders()).append('\n')  // i18n-ignore
        b.append("Network: ").append(network(ctx)).append('\n')  // i18n-ignore
        b.append("Speed (cached, measured in Diagnostics): internet ").append(Prefs.str("speed_norm").ifBlank { "not measured" })
            .append(Prefs.str("speed_norm_at").let { if (it.isBlank()) "" else " at $it" })
            .append(" · VPN ").append(Prefs.str("speed_vpn").ifBlank { "not measured" }).append(Prefs.str("speed_vpn_at").let { if (it.isBlank()) "" else " at $it" }).append('\n')  // i18n-ignore
        b.append("Channel health: ").append(ChannelHealth.stats()).append('\n')  // i18n-ignore
        val pls = Store.playlists(ctx)
        b.append("Playlists: ").append(pls.size).append(" · active: ").append(pls.firstOrNull { it.active }?.type?.name ?: "none").append('\n')  // i18n-ignore
        return b.toString()
    }
    private fun decoders(): String = try {
        val l = android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder && it.supportedTypes.any { t -> t.startsWith("video/") } }
        l.joinToString(", ") { c -> c.name + (if (Build.VERSION.SDK_INT >= 29) (if (c.isHardwareAccelerated) " (hw)" else " (sw)") else "") }.take(1500)  // i18n-ignore
    } catch (_: Throwable) { "unavailable" }  // i18n-ignore
    private fun network(ctx: Context): String = try {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val c = cm.getNetworkCapabilities(cm.activeNetwork)
        if (c == null) "offline" else listOfNotNull(  // i18n-ignore
            if (c.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)) "ethernet" else null,
            if (c.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) "wifi" else null,
            if (c.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)) "VPN on" else "VPN off",  // i18n-ignore
            if (c.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)) "internet ok" else "internet not confirmed").joinToString(" · ")  // i18n-ignore
    } catch (_: Throwable) { "unavailable" }  // i18n-ignore

    internal class Out(val w: java.io.Writer, val secrets: List<String>, val budget: LongArray? = null, val alive: () -> Boolean = { true }) {
        var bytes = 0L
        private var n = 0
        fun line(s: String) {
            if (budget != null) { if (budget[0] <= 0) throw Full(); if (++n % 500 == 0 && !alive()) throw java.io.InterruptedIOException("cancelled") }  // i18n-ignore
            val c = SecretScan.clean(s, secrets); w.write(c); w.write("\n"); bytes += c.length + 1; if (budget != null) budget[0] -= c.length + 1
        }
        fun block(s: String) = s.lineSequence().forEach { line(it) }
    }

    private fun incidentText(i: DiagStore.Incident) = DiagStore.read(i.f)

    /** builds the export into [dest]; throws [SecretScan.Refused] when it cannot be made safe */
    /** the report reached its size ceiling: the rest of that section is left out (listed in section 31) */
    internal class Full : Exception()

    fun build(ctx: Context, kind: Kind, dest: File, alive: () -> Boolean = { true }) {
        val secrets = try { SecretScan.values(ctx) } catch (e: Throwable) { throw SecretScan.Refused("secrets unreadable") }  // i18n-ignore
        DiagStore.flushNow()
        if (dest.exists()) dest.delete()
        when (kind) {
            Kind.LATEST -> dest.bufferedWriter().use { w -> val o = Out(w, secrets); latest(ctx, o) }
            Kind.FULL -> dest.bufferedWriter(Charsets.UTF_8).use { w -> fullTxt(ctx, Out(w, secrets, longArrayOf(FULL_TXT_MAX), alive)) }
        }
    }

    private fun pct(l: List<Long>, p: Double): Long = if (l.isEmpty()) 0 else l.sorted()[((l.size - 1) * p).toInt()]

    /**
     * 3.0.0.1 Full Diagnostic Report: one UTF-8 TXT, 31 numbered sections plus the event journal, streamed line by line
     * (never whole in memory), every line through the secret scan. Values are marked "unavailable" when not measured;
     * nothing is estimated. Incidents come before the journal, so the size ceiling cuts ordinary events, never crashes.
     */
    private fun fullTxt(ctx: Context, o: Out) {
        val omitted = ArrayList<String>()
        fun sec(n: Int, title: String, fill: () -> Unit) {
            o.line(""); o.line("=".repeat(72)); o.line("$n. $title"); o.line("=".repeat(72))  // i18n-ignore
            try { fill() } catch (_: Full) { omitted += "$n. $title (cut at the size ceiling)"; throw Full() }  // i18n-ignore
        }
        val inc = DiagStore.incidents()
        val play = ArrayList<List<String>>(); PerfLog.each("playback") { play += it.split(',') }
        val samples = ArrayList<List<String>>(); PerfLog.each("samples") { samples += it.split(',') }
        val tasks = ArrayList<List<String>>(); PerfLog.each("tasks") { tasks += it.split(',') }
        fun ms(rows: List<List<String>>) = rows.mapNotNull { it.getOrNull(4)?.toLongOrNull() }
        fun stat(label: String, v: List<Long>, unit: String) = o.line(if (v.isEmpty()) "$label: unavailable (no samples)" else "$label: ${v.size} samples · min ${v.min()} · median ${pct(v, .5)} · p95 ${pct(v, .95)} · max ${v.max()} $unit")  // i18n-ignore
        fun events(filter: (String) -> Boolean) { var n = 0; DiagStore.eachEvent { if (filter(it)) { o.line(it); n++ } }; if (n == 0) o.line("no events recorded") }  // i18n-ignore
        fun playKind(k: String) {
            val r = play.filter { it.getOrNull(2) == k }
            stat("time to READY (ms)", ms(r.filter { it.getOrNull(3) == "ready" }), "ms")  // i18n-ignore
            stat("time to first picture (ms)", ms(r.filter { it.getOrNull(3) == "first_frame" }), "ms")  // i18n-ignore
            stat("buffering at start (ms)", ms(r.filter { it.getOrNull(3) == "buffer_startup" }), "ms")  // i18n-ignore
            stat("buffering during playback (ms)", ms(r.filter { it.getOrNull(3) == "buffer_mid" }), "ms")  // i18n-ignore
            o.line("recovery attempts: " + r.count { it.getOrNull(3) == "recovery" })  // i18n-ignore
        }
        try {
            o.line("MEDIA CENTER – FULL DIAGNOSTIC REPORT · report schema " + DiagII.SCHEMA + " (event journal schema " + DiagCollect.SCHEMA + ") · report id " + java.util.UUID.randomUUID().toString().take(13))  // i18n-ignore
            o.line("Status labels: [measured] recorded here · [device] reported by Android · [provider] reported by the IPTV server · [cached] stored earlier · [suspected] indication, not proof · [unavailable] / [not observed] nothing recorded. Zero is a real value, never a stand-in for missing data.")  // i18n-ignore
            o.line("Legend: measured = recorded on this box · unavailable = not recorded or not supported here · nothing is estimated.")  // i18n-ignore
            o.line("Build commit " + BuildConfig.GIT_COMMIT + " · Diagnostics II schema " + DiagII.SCHEMA + " · sections 1–70 · the summary (70) comes first, the evidence follows.")  // i18n-ignore
            // 3.0.0.7: section 70 at the top, built only from what is recorded below
            try { DiagReportII.summary(ctx, o) } catch (e: Full) { throw e } catch (e: Throwable) { o.line("[summary unavailable: " + e.javaClass.simpleName + "]") }  // i18n-ignore
            sec(1, "REPORT SUMMARY") {  // i18n-ignore
                o.line("Created: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date()) + " (" + java.util.TimeZone.getDefault().id + ")")  // i18n-ignore
                o.line("Device: " + DeviceIdentity.short() + " · " + DeviceIdentity.name().ifBlank { "name not set" } + " · Media Center " + BuildConfig.VERSION_NAME)  // i18n-ignore
                o.line("Incidents kept: " + inc.size + " (crash " + inc.count { it.kind == "crash" || it.kind == "native" } + " · freeze " + inc.count { it.kind == "freeze" } + " · ANR " + inc.count { it.kind == "anr" } + ")")  // i18n-ignore
                o.line("Events kept: " + DiagStore.eventCount() + " · diagnostics on the box: " + DiagStore.totalBytes() / 1024 + " KB of 50 MB")  // i18n-ignore
                o.line("Provider protection: " + if (ChannelHealth.probeOn) "active checks on (owner opt-in)" else "passive (default)")  // i18n-ignore
            }
            sec(2, "DEVICE IDENTITY") {  // i18n-ignore
                o.line("Device ID: " + DeviceIdentity.id()); o.line("Short ID: " + DeviceIdentity.short())  // i18n-ignore
                o.line("Device name: " + DeviceIdentity.name().ifBlank { "not set" })  // i18n-ignore
                o.line("The ID is random (not from MAC, serial or IMEI) and belongs to this installation only.")  // i18n-ignore
            }
            sec(3, "APPLICATION AND BUILD") {  // i18n-ignore
                o.line("Version: " + BuildConfig.VERSION_NAME + " (versionCode " + BuildConfig.VERSION_CODE + ") · channel " + (if (BuildConfig.BUILD_TYPE == "beta") "Beta" else "Stable"))  // i18n-ignore
                o.line("Previous version: " + Prefs.str("diag_prev_version").ifBlank { "unknown" })  // i18n-ignore
                o.line("Media3: " + try { androidx.media3.common.MediaLibraryInfo.VERSION } catch (_: Throwable) { "unavailable" })  // i18n-ignore
            }
            sec(4, "DEVICE HARDWARE AND ANDROID") {  // i18n-ignore
                o.line("Model: " + Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")")  // i18n-ignore
                o.line("CPU cores: " + Runtime.getRuntime().availableProcessors() + " · ABI: " + Build.SUPPORTED_ABIS.joinToString(", "))  // i18n-ignore
                try { val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager; val mi = android.app.ActivityManager.MemoryInfo(); am.getMemoryInfo(mi)
                    o.line("RAM: " + mi.totalMem / 1_048_576 + " MB total · " + mi.availMem / 1_048_576 + " MB free now · low memory: " + mi.lowMemory) } catch (_: Throwable) { o.line("RAM: unavailable") }  // i18n-ignore
                try { val m = ctx.resources.displayMetrics; val d = (ctx.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager).getDisplay(0)
                    o.line("Screen: " + m.widthPixels + "×" + m.heightPixels + " · " + (d?.refreshRate?.let { String.format(Locale.US, "%.0f Hz", it) } ?: "refresh unavailable")) } catch (_: Throwable) { o.line("Screen: unavailable") }  // i18n-ignore
                o.line("Thermal status: " + try { if (Build.VERSION.SDK_INT >= 29) (ctx.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).currentThermalStatus.toString() else "unsupported (Android < 10)" } catch (_: Throwable) { "unavailable" })  // i18n-ignore
                o.line("Video decoders: " + decoders())  // i18n-ignore
            }
            sec(5, "APPLICATION SESSION") {  // i18n-ignore
                o.line("Session: " + PerfLog.session + " · uptime " + (android.os.SystemClock.elapsedRealtime() - android.os.Process.getStartElapsedRealtime()) / 60_000 + " min · state now " + Work.state().name.lowercase())  // i18n-ignore
                o.line("Current screen: " + CrashLog.screen.ifBlank { "unknown" })  // i18n-ignore
                PerfLog.counts.toSortedMap().forEach { (k, v) -> o.line("$k: $v") }
            }
            sec(6, "STARTUP PERFORMANCE") {  // i18n-ignore
                o.line("Start to first screen (this session): " + if (Monitor.startMs > 0) "${Monitor.startMs} ms (measured)" else "unavailable")  // i18n-ignore
                events { it.contains("|app|") && (it.contains("start") || it.contains("exit")) }
            }
            sec(7, "NAVIGATION PERFORMANCE") {  // i18n-ignore
                // 3.0.0.4: real per-screen compose times (App.kt logs one "nav <screen> composed" task on every screen change)
                val navRows = tasks.filter { (it.getOrNull(2) ?: "").startsWith("nav ") && it.getOrNull(3) == "composed" }  // i18n-ignore
                if (navRows.isEmpty()) o.line("Per-screen timings: unavailable (no screen change recorded yet this run)")  // i18n-ignore
                else {
                    stat("every screen, time to compose (ms)", navRows.mapNotNull { it.getOrNull(4)?.toLongOrNull() }, "ms")  // i18n-ignore
                    navRows.groupBy { (it.getOrNull(2) ?: "").removePrefix("nav ") }.toSortedMap()
                        .forEach { (screen, r) -> stat("  $screen (ms)", r.mapNotNull { it.getOrNull(4)?.toLongOrNull() }, "ms") }
                }
            }
            sec(8, "CPU STATISTICS") {  // i18n-ignore
                o.line("Method: app process ticks from /proc/self/stat each minute, in % of ONE core (not the whole device).")  // i18n-ignore
                stat("CPU (%)", samples.mapNotNull { it.getOrNull(3)?.toLongOrNull() }, "%")  // i18n-ignore
                samples.groupBy { it.getOrNull(2) ?: "" }.toSortedMap().forEach { (st, r) -> stat("CPU while $st (%)", r.mapNotNull { it.getOrNull(3)?.toLongOrNull() }, "%") }  // i18n-ignore
            }
            sec(9, "RAM STATISTICS AND TRENDS") {  // i18n-ignore
                Monitor.summary().forEach { (k, v) -> o.line("$k: $v") }
                stat("Java heap (MB)", samples.mapNotNull { it.getOrNull(4)?.toLongOrNull() }, "MB")  // i18n-ignore
                stat("PSS (MB, every 5 min)", samples.mapNotNull { it.getOrNull(5)?.toLongOrNull() }, "MB")  // i18n-ignore
                o.line("Growth between the first and the last samples is a hint, not proof of a leak.")  // i18n-ignore
            }
            sec(10, "STORAGE AND CACHE") {  // i18n-ignore
                fun size(f: File?): String = try { if (f == null || !f.exists()) "unavailable" else (f.walkTopDown().maxDepth(4).filter { it.isFile }.sumOf { it.length() } / 1024).toString() + " KB" } catch (_: Throwable) { "unavailable" }  // i18n-ignore
                o.line("Free storage: " + ctx.filesDir.usableSpace / 1_048_576 + " MB")  // i18n-ignore
                o.line("Pictures cache: " + size(File(ctx.cacheDir, "images")) + " · cache total: " + size(ctx.cacheDir) + " · app files: " + size(ctx.filesDir))  // i18n-ignore
                o.line("Diagnostics: " + DiagStore.totalBytes() / 1024 + " KB")  // i18n-ignore
            }
            sec(11, "NETWORK") {  // i18n-ignore
                o.line("Now: " + network(ctx)); o.line("HTTP response times are not ICMP ping.")  // i18n-ignore
                events { it.contains("|net") }
            }
            sec(12, "VPN STATUS") {  // i18n-ignore
                o.line("VPN active now: " + try { NetInfo.vpn(ctx) } catch (_: Throwable) { "unavailable" })  // i18n-ignore
                o.line("Cached speeds (measured earlier in Diagnostics, no new test for this report): internet " + Prefs.str("speed_norm").ifBlank { "not measured" } + " · VPN " + Prefs.str("speed_vpn").ifBlank { "not measured" })  // i18n-ignore
            }
            sec(13, "TV PLAYBACK") { playKind("tv"); o.line("-- preview (small picture) --"); playKind("preview") }  // i18n-ignore
            sec(14, "CHANNEL SWITCHING") {  // i18n-ignore
                o.line("Each preview start is one switch: " + play.count { it.getOrNull(2) == "preview" && it.getOrNull(3) == "first_frame" } + " switches with a picture")  // i18n-ignore
                stat("switch to picture (ms)", ms(play.filter { it.getOrNull(2) == "preview" && it.getOrNull(3) == "first_frame" }), "ms")  // i18n-ignore
            }
            sec(15, "TIME TO FIRST RENDERED FRAME") {  // i18n-ignore
                listOf("tv", "preview", "movie", "series").forEach { k -> stat("$k (ms)", ms(play.filter { it.getOrNull(2) == k && it.getOrNull(3) == "first_frame" }), "ms") }  // i18n-ignore
                stat("after recovery (ms)", ms(play.filter { it.getOrNull(3) == "first_frame_recovery" }), "ms")  // i18n-ignore
            }
            sec(16, "BUFFERING") {  // i18n-ignore
                stat("at start (ms)", ms(play.filter { it.getOrNull(3) == "buffer_startup" }), "ms")  // i18n-ignore
                stat("during playback (ms)", ms(play.filter { it.getOrNull(3) == "buffer_mid" }), "ms")  // i18n-ignore
            }
            sec(17, "PLAYER LIFECYCLE AND CONNECTIONS") {  // i18n-ignore
                o.line("One stream at a time: the preview stops (and now releases) before the full player opens; channel checks close their connection when a stream opens.")  // i18n-ignore
                // 3.0.0.4: real counts (DiagCollect.playerCreated/playerReleased, one call per ExoPlayer instance)
                val createdPreview = DiagCollect.counters["players created (preview)"]?.get() ?: 0L
                val createdFull = DiagCollect.counters["players created (fullscreen)"]?.get() ?: 0L
                val releasedPreview = DiagCollect.counters["players released (preview)"]?.get() ?: 0L
                val releasedFull = DiagCollect.counters["players released (fullscreen)"]?.get() ?: 0L
                o.line("Created this session: $createdPreview preview · $createdFull fullscreen. Released: $releasedPreview preview · $releasedFull fullscreen.")  // i18n-ignore
                o.line("Alive right now: " + DiagCollect.playersLive.get() + " · peak at once this session: " + DiagCollect.playersPeak.get() + " (more than 1 at once is logged below as a 'players>1' event)")  // i18n-ignore
                events { it.contains("player instances alive at once") }
            }
            sec(18, "STREAM RECOVERY") { o.line("recovery attempts: " + play.count { it.getOrNull(3) == "recovery" }); events { it.contains("|recover|") || it.contains("|stream") } }  // i18n-ignore
            sec(19, "MOVIES PLAYBACK AND RESUME") { playKind("movie"); events { it.contains("|resume|") } }  // i18n-ignore
            sec(20, "SERIES PLAYBACK AND EPISODE PROGRESS") { playKind("series") }  // i18n-ignore
            sec(21, "CHANNEL HEALTH") { o.line(ChannelHealth.stats()); events { it.contains("|health|") } }  // i18n-ignore
            sec(22, "IPTV PROVIDER PROTECTION") { o.block(ProbeGuard.report()) }  // i18n-ignore
            sec(23, "EPG PERFORMANCE") {  // i18n-ignore
                tasks.filter { (it.getOrNull(2) ?: "").contains("guide", true) || (it.getOrNull(2) ?: "").contains("epg", true) }.forEach { o.line(it.joinToString(",")) }  // i18n-ignore
                events { it.contains("|epg") || it.contains("guide") }  // i18n-ignore
            }
            sec(24, "CHANNEL LOGOS") { o.line(LogoResolver.stats(ctx)); PerfLog.counts.filterKeys { it.startsWith("logo") }.toSortedMap().forEach { (k, v) -> o.line("$k: $v") } }  // i18n-ignore
            sec(25, "MOVIE AND SERIES ARTWORK") {  // i18n-ignore
                // 3.0.0.4: real timings from Coil's own load events (DiagCollect.imageListener); picture cache size is in section 10
                val ms = DiagCollect.imgMs["artwork"]?.let { synchronized(it) { it.toList() } } ?: emptyList()
                stat("Artwork load time (ms)", ms, "ms")
                val attempts = DiagCollect.counters.filterKeys { it.startsWith("images artwork") }
                if (attempts.isEmpty()) o.line("No artwork load attempts recorded yet this session.")  // i18n-ignore
                else attempts.toSortedMap().forEach { (k, v) -> o.line("$k: ${v.get()}") }
            }
            sec(26, "BACKGROUND TASK COORDINATION") {  // i18n-ignore
                tasks.groupBy { it.getOrNull(2) ?: "" }.toSortedMap().forEach { (t, r) -> o.line(t + ": " + r.groupingBy { it.getOrNull(3) ?: "" }.eachCount().entries.joinToString(", ") { it.key + " " + it.value }); stat("  $t duration (ms)", r.filter { it.getOrNull(3) == "done" }.mapNotNull { it.getOrNull(4)?.toLongOrNull() }, "ms") }  // i18n-ignore
                events { it.contains("|work|") }
            }
            fun incidents(title: Int, name: String, kinds: Set<String>) = sec(title, name) {
                val l = inc.filter { it.kind in kinds }
                if (l.isEmpty()) o.line("none recorded")  // i18n-ignore
                l.forEach { i ->
                    o.line(""); o.line("--- " + i.kind + " · " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(i.time)) + " · " + i.base + " ---")  // i18n-ignore
                    o.block(incidentText(i))
                    DiagStore.logOf(i)?.let { o.line("--- app log for this incident ---"); o.block(DiagStore.read(it)) }  // i18n-ignore
                }
            }
            incidents(27, "CRASH HISTORY", setOf("crash", "native"))  // i18n-ignore
            incidents(28, "FREEZE AND ANR HISTORY", setOf("freeze", "anr", "anr_suspected"))  // i18n-ignore
            sec(29, "CRITICAL ERROR HISTORY") {  // i18n-ignore
                inc.filter { it.kind !in setOf("crash", "native", "freeze", "anr", "anr_suspected") }.forEach { i -> o.line("--- " + i.kind + " · " + i.base + " ---"); o.block(incidentText(i)) }  // i18n-ignore
                // 3.0.0.8: levels are worked out again here, so older journal lines are judged by the same rules
                events { val l = DiagEvents.levelOf(it); l == "C" || l == "E" }
            }
            sec(30, "DIAGNOSTIC SYSTEM HEALTH") {  // i18n-ignore
                o.line("Limits: 50 MB total · 100 incidents · 10,000 events · 90 days · 100 MB free kept")  // i18n-ignore
                o.line("Now: " + DiagStore.totalBytes() / 1024 + " KB · " + inc.size + " incidents · " + DiagStore.eventCount() + " events · free " + ctx.filesDir.usableSpace / 1_048_576 + " MB")  // i18n-ignore
                o.line("Metric samples dropped (queue, space or write errors): " + PerfLog.dropped.get())  // i18n-ignore
                o.line("Local recording of crashes and freezes: active · nothing is sent anywhere by the app")  // i18n-ignore
            }
        } catch (_: Full) { /* the rest is listed below */ }
        // 31 and the journal are written even when a section above was cut (small, and they explain what is missing)
        val room = longArrayOf(o.budget?.get(0) ?: 0)
        o.budget?.set(0, maxOf(room[0], 64L * 1024))
        o.line(""); o.line("=".repeat(72)); o.line("31. REPORT LIMITATIONS, OMISSIONS AND UNAVAILABLE MEASUREMENTS"); o.line("=".repeat(72))  // i18n-ignore
        omitted.forEach { o.line("omitted: $it") }  // i18n-ignore
        o.line("3.0.0.4: per-screen navigation times, player creation/reuse counts and artwork load times are now measured (sections 7, 17, 25); numbers are empty only until that kind of event happens in this session.")  // i18n-ignore
        o.line("Samples exist only while the app ran with 'Praćenje dugog rada' on; playback timings from 3.0 on.")  // i18n-ignore
        o.line("Counts are this device only; other devices on the same account and provider-side counts are not visible.")  // i18n-ignore
        try { extended(ctx, o, play, samples, tasks) } catch (_: Full) { o.budget?.set(0, maxOf(o.budget?.get(0) ?: 0, 32L * 1024)); o.line("[extended sections cut at the size limit]") }  // i18n-ignore
        // 3.0.0.7 Diagnostics II: sections 51–69 (section 70 is at the top)
        try { DiagReportII.sections(ctx, o) } catch (_: Full) { o.budget?.set(0, maxOf(o.budget?.get(0) ?: 0, 32L * 1024)); o.line("[sections 51–69 cut at the size limit]") }  // i18n-ignore
        val total = DiagStore.eventCount()
        val left = ((o.budget?.get(0) ?: 0) - 1024) / 160
        val skip = (total - left).coerceAtLeast(0)
        if (skip > 0) o.line("event journal: the oldest $skip of $total events are left out to stay within the size limit")  // i18n-ignore
        try {
            o.line(""); o.line("=".repeat(72)); o.line("APPENDIX – EVENT JOURNAL (oldest first)"); o.line("=".repeat(72))  // i18n-ignore
            var n = 0L
            DiagStore.eachEvent { l -> if (n++ >= skip) o.line(l) }
            o.line(""); o.line("END OF REPORT")  // i18n-ignore
        } catch (_: Full) { o.budget?.set(0, 1024); o.line("[journal cut at the size limit]") }  // i18n-ignore
    }

    /** 3.0.0.2 sections 32–50: passive observations only (see DiagCollect) */
    private fun extended(ctx: Context, o: Out, play: List<List<String>>, samples: List<List<String>>, tasks: List<List<String>>) {
        fun sec(n: Int, t: String, fill: () -> Unit) { o.line(""); o.line("=".repeat(72)); o.line("$n. $t"); o.line("=".repeat(72)); try { fill() } catch (e: Full) { throw e } catch (e: Throwable) { o.line("[unavailable: " + e.javaClass.simpleName + "]") } }  // i18n-ignore
        fun ev(kinds: List<String>, max: Int = 400) { val l = ArrayList<String>(); DiagStore.eachEvent { e -> if (kinds.any { e.contains("|$it|") }) { l += e; if (l.size > max) l.removeAt(0) } }; if (l.isEmpty()) o.line("[not observed] no events yet") else { o.line("last ${l.size} events:"); l.forEach { o.line(it) } } }  // i18n-ignore
        fun cnt(prefix: String) { val m = DiagCollect.counters.filterKeys { it.startsWith(prefix) }; if (m.isEmpty()) o.line("[not observed] this session") else m.toSortedMap().forEach { (k, v) -> o.line("[measured] $k: ${v.get()} (this session)") } }  // i18n-ignore
        val tv = ctx.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
        sec(32, "IPTV ACCOUNT AND CONNECTION LIMITS") {  // i18n-ignore
            val acc = DiagCollect.accounts()
            if (acc.isEmpty()) o.line("[not observed] no Xtream login answer recorded since 3.0.0.2 (M3U and Stalker do not report these values)")  // i18n-ignore
            acc.forEach { (id, j) ->
                val age = (System.currentTimeMillis() - j.optLong("at")) / 60_000
                o.line("[provider · cached, ${age} min old] playlist ref $id: status ${j.optString("status").ifBlank { "not reported" }} · max connections ${j.optString("max").ifBlank { "not reported" }} · active connections ${j.optString("active").ifBlank { "not reported" }} · expiry ${j.optString("exp").ifBlank { "not reported" }.let { e -> e.toLongOrNull()?.let { DiagEvents.stamp(it * 1000).take(10) } ?: e }} · fields supplied: ${j.optString("fields").ifBlank { "none" }}")  // i18n-ignore
            }
            o.line("[measured] this app now: players alive ${DiagCollect.playersLive.get()} · " + DiagCollect.playState())  // i18n-ignore
            o.line("Provider 'active connections' counts every device on the account at the moment of that answer; it is not this box's count and not live.")  // i18n-ignore
        }
        sec(33, "VIDEO CODEC CAPABILITIES") {  // i18n-ignore
            val mimes = listOf("video/avc" to "H.264", "video/hevc" to "H.265/HEVC", "video/av01" to "AV1", "video/x-vnd.on2.vp8" to "VP8", "video/x-vnd.on2.vp9" to "VP9", "video/mpeg2" to "MPEG-2", "video/mp4v-es" to "MPEG-4", "video/dolby-vision" to "Dolby Vision")  // i18n-ignore
            val list = android.media.MediaCodecList(android.media.MediaCodecList.ALL_CODECS).codecInfos.filter { !it.isEncoder }
            mimes.forEach { (m, label) ->
                val ds = list.filter { c -> c.supportedTypes.any { it.equals(m, true) } }
                if (ds.isEmpty()) o.line("[device] $label: no decoder") else ds.forEach { c ->  // i18n-ignore
                    val cap = try { c.getCapabilitiesForType(m) } catch (_: Throwable) { null }
                    val v = cap?.videoCapabilities
                    o.line("[device] $label: ${c.name} · " + (if (Build.VERSION.SDK_INT >= 29) (if (c.isHardwareAccelerated) "hardware" else if (c.isSoftwareOnly) "software" else "unknown") + " (Android flag)" else "hw/sw unknown (Android < 10)") +  // i18n-ignore
                        (v?.let { " · up to ${it.supportedWidths.upper}×${it.supportedHeights.upper} · ${it.supportedFrameRates.upper.toInt()} fps max" } ?: "") + " · ${cap?.profileLevels?.size ?: 0} profile/levels")  // i18n-ignore
                }
            }
        }
        sec(34, "ACTUAL VIDEO FORMAT AND DECODER SELECTION") { ev(listOf("codec", "format")) }  // i18n-ignore
        sec(35, "HDR AND COLOR INFORMATION") {  // i18n-ignore
            val d = tv.getDisplay(0)
            val types = if (Build.VERSION.SDK_INT >= 24) d?.hdrCapabilities?.supportedHdrTypes?.map { when (it) { 1 -> "Dolby Vision"; 2 -> "HDR10"; 3 -> "HLG"; 4 -> "HDR10+"; else -> "type $it" } } else null  // i18n-ignore
            o.line("[device] display reports HDR: " + (types?.ifEmpty { listOf("none") }?.joinToString(", ") ?: "unavailable (Android < 7)"))  // i18n-ignore
            o.line("Source color info per stream is in section 34 (transfer 6 = PQ/HDR10, 7 = HLG). Metadata in the source does not prove HDR output.")  // i18n-ignore
        }
        sec(36, "AUDIO CAPABILITIES AND ROUTING") {  // i18n-ignore
            if (Build.VERSION.SDK_INT >= 23) {
                val am = ctx.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).forEach { a ->
                    val enc = a.encodings.map { when (it) { 2 -> "PCM16"; 4 -> "PCM float"; 5 -> "AC3"; 6 -> "E-AC3"; 7 -> "DTS"; 8 -> "DTS-HD"; 13 -> "Dolby TrueHD"; 18 -> "E-AC3 JOC"; else -> "enc $it" } }  // i18n-ignore
                    o.line("[device] output " + DiagCollect.devType(a.type) + " · encodings " + (enc.ifEmpty { listOf("any (not listed)") }.joinToString(", ")) + " · channels " + (a.channelCounts.toList().ifEmpty { listOf("any") }.joinToString("/")) + " · rates " + (a.sampleRates.toList().ifEmpty { listOf("any") }.joinToString("/")))  // i18n-ignore
                }
            } else o.line("[unavailable] Android < 6")  // i18n-ignore
            o.line("A generic HDMI output does not tell whether a soundbar or receiver is behind it. Passthrough state is not observable here.")  // i18n-ignore
            cnt("audio"); ev(listOf("audio"))
        }
        sec(37, "DISPLAY MODES AND FRAME RATE MATCHING") {  // i18n-ignore
            o.line("[device] current mode: " + DiagCollect.mode(ctx))  // i18n-ignore
            if (Build.VERSION.SDK_INT >= 23) tv.getDisplay(0)?.supportedModes?.forEach { m -> o.line("[device] supported: ${m.physicalWidth}×${m.physicalHeight} @ " + String.format(Locale.US, "%.3f Hz", m.refreshRate)) }  // i18n-ignore
            o.line("Auto frame rate in the app: " + if (Tech.afr) "on" else "off")  // i18n-ignore
            ev(listOf("display"))
        }
        sec(38, "NETWORK AND VPN TRANSITIONS") { o.line("[device] now: " + NetState.now()); cnt("network"); cnt("player error"); cnt("preview error"); ev(listOf("net", "netwarn", "neterr")) }  // i18n-ignore
        sec(39, "DROPPED FRAMES AND SUSPECTED VIDEO FREEZES") {  // i18n-ignore
            cnt("dropped"); cnt("video decoder"); cnt("audio decoder"); cnt("audio underruns")
            o.line("Position stagnation found by the existing stall watch is [suspected] freeze (it cannot see the picture itself); confirmed ANRs are in section 28.")  // i18n-ignore
            ev(listOf("frames", "stall", "codec"))
        }
        sec(40, "PLAYER LIFECYCLE AND RESOURCE RELEASE") {  // i18n-ignore
            cnt("players"); cnt("preview"); cnt("fullscreen")
            o.line("[measured] player instances alive now: ${DiagCollect.playersLive.get()} · peak this session: ${DiagCollect.playersPeak.get()}")  // i18n-ignore
            o.line("Player instances are not provider connections; the exact socket owner of each stream is not observable from the app.")  // i18n-ignore
            ev(listOf("life"), 200)
        }
        sec(41, "STANDBY AND POWER LIFECYCLE") { cnt("screen"); o.line("HDMI-CEC itself is not visible to apps; standby is seen as 'screen off', wake as 'screen on'."); ev(listOf("power")) }  // i18n-ignore
        sec(42, "DATABASE AND STORAGE GROWTH") {  // i18n-ignore
            o.line(PerfLog.FILES["storage"]!!.replace("time,session,", "time,session,"))
            var n = 0; PerfLog.each("storage") { o.line(it); n++ }; if (n == 0) o.line("[not observed] first snapshot comes after ~6 h of idle running")  // i18n-ignore
        }
        sec(43, "PLAYLIST IMPORT PERFORMANCE") {  // i18n-ignore
            val r = tasks.filter { it.getOrNull(2) == "playlist refresh" }
            if (r.isEmpty()) o.line("[not observed] no refresh since 3.0.0.2") else r.forEach { o.line("[measured] " + it.joinToString(",")) }  // i18n-ignore
            val pls = Store.playlists(ctx); o.line("[measured] playlists ${pls.size} · active ${pls.count { it.active }}")  // i18n-ignore
        }
        sec(44, "EXTENDED EPG DIAGNOSTICS") {  // i18n-ignore
            val tz = java.util.TimeZone.getDefault()
            o.line("[device] time zone ${tz.id} · UTC offset now " + (tz.getOffset(System.currentTimeMillis()) / 60_000) + " min · daylight saving now " + tz.inDaylightTime(Date()))  // i18n-ignore
            o.line("[measured] EPG sources: " + EpgSources.list().size)  // i18n-ignore
            tasks.filter { (it.getOrNull(2) ?: "").contains("guide", true) || (it.getOrNull(2) ?: "").contains("epg", true) }.takeLast(50).forEach { o.line("[measured] " + it.joinToString(",")) }  // i18n-ignore
        }
        sec(45, "NAVIGATION PERFORMANCE") {  // i18n-ignore
            o.line("Measured from the navigation request to the first composition of the destination screen (not a drawn frame).")  // i18n-ignore
            tasks.filter { (it.getOrNull(2) ?: "").startsWith("nav ") }.groupBy { it[2] }.toSortedMap().forEach { (k, r) -> val v = r.mapNotNull { it.getOrNull(4)?.toLongOrNull() }; if (v.isNotEmpty()) o.line("[measured] ${k.removePrefix("nav ")}: ${v.size} samples · median ${pct(v, .5)} ms · p95 ${pct(v, .95)} ms · max ${v.max()} ms") }  // i18n-ignore
        }
        sec(46, "ARTWORK AND LOGO PERFORMANCE") {  // i18n-ignore
            cnt("images")
            DiagCollect.imgMs.forEach { (c, q) -> val v = synchronized(q) { q.toList() }; if (v.isNotEmpty()) o.line("[measured] $c load time: ${v.size} samples · median ${pct(v, .5)} ms · p95 ${pct(v, .95)} ms · max ${v.max()} ms") }  // i18n-ignore
        }
        sec(47, "EXTENDED BACKGROUND TASK COORDINATION") {  // i18n-ignore
            o.line("Heavy jobs run one at a time (Work.heavy); the rows show waiting time before start and duration.")  // i18n-ignore
            tasks.takeLast(200).forEach { o.line(it.joinToString(",")) }
        }
        sec(48, "INCIDENT CORRELATION AND RECOVERY TIMELINE") {  // i18n-ignore
            o.line("Each incident in sections 27–29 now starts with its context: play state, network, app state, last sample and the last 40 events before it.")  // i18n-ignore
            ev(listOf("recover", "stall", "player", "neterr", "resume"), 300)
        }
        sec(49, "DIAGNOSTIC RESOURCE USAGE AND HEALTH") {  // i18n-ignore
            o.line("[measured] diagnostics on the box: ${DiagStore.totalBytes() / 1024} KB of 50 MB · events ${DiagStore.eventCount()} of 10,000 · incidents ${DiagStore.incidents().size} of 100 · retention 90 days · free-space floor 100 MB")  // i18n-ignore
            cnt("diag"); cnt("suppressed"); cnt("collector")
            o.line("collectors switched off after an internal error: " + DiagCollect.disabled().ifEmpty { setOf("none") }.joinToString(", "))  // i18n-ignore
            o.line("metric samples dropped: ${PerfLog.dropped.get()} · exact CPU cost of diagnostics: unavailable (not separable)")  // i18n-ignore
        }
        sec(50, "EXTENDED MEASUREMENT LIMITATIONS") {  // i18n-ignore
            listOf("HDMI-CEC events are not exposed to apps; standby is inferred from screen on/off.",
                "Audio passthrough state and the device behind HDMI are not observable.",
                "Provider connection counts are the provider's own snapshot at login, not live and not per device.",
                "Freeze detection uses playback position only ([suspected]); the picture itself is never inspected.",
                "Navigation times end at first composition, not at a drawn frame.",
                "Build commit id: built into the app since 3.0.0.7 (header and section 66).",
                "Counters marked 'this session' reset when the app restarts; journal events cover up to 90 days.").forEach { o.line("- $it") }  // i18n-ignore
        }
    }

    private fun latest(ctx: Context, o: Out) {
        o.block(header(ctx)); o.line("")
        val i = DiagStore.incidents().firstOrNull { it.kind in setOf("crash", "freeze", "anr", "native") }
        if (i == null) { o.line("No crash or freeze is recorded on this box."); o.line(""); o.line("== Recent events =="); DiagEvents.lines().forEach { o.line(it) }; return }  // i18n-ignore
        o.line("== Latest incident: " + i.kind + " · " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(i.time)) + " ==")  // i18n-ignore
        o.block(incidentText(i))
        DiagStore.logOf(i)?.let { o.line(""); o.line("== App log saved after this incident (may not cover the whole time before it) =="); o.block(DiagStore.read(it)) }  // i18n-ignore
        o.line(""); o.line("== Events in the 30 minutes before and 5 minutes after ==")  // i18n-ignore
        DiagStore.eachEvent { l -> val t = DiagEvents.parseTime(l); if (t in (i.time - 30 * 60_000L)..(i.time + 5 * 60_000L)) o.line(l) }
    }
}

/**
 * Settings › Diagnostics › Izvezi izvještaj, and the crash dialog: choose one of three exports; it is built, cleaned
 * and offered as a one-time download for a phone on the same home network (QR). The temporary server lives only while
 * this window is open (Back closes it), for 5 minutes at most and for one finished download.
 */
@Composable
fun DiagExportDialog(start: DiagReport.Kind? = null, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var kind by remember { mutableStateOf(start) }
    var file by remember { mutableStateOf<File?>(null) }
    var refused by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }
    var folder by remember { mutableStateOf(false) }
    var clear by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf(0L) }
    var why by remember { mutableStateOf<String?>(null) }
    var until by remember { mutableStateOf(0L) }
    LaunchedEffect(kind) {
        val k = kind ?: return@LaunchedEffect
        busy = true; refused = false; url = null; done = false; why = null
        // 3.0.0.1: never on a box that is short of memory or space; the request is refused with a plain reason
        if (Work.state() == Work.State.LOW_MEMORY) { busy = false; refused = true; why = tr("Uređaj trenutno nema dovoljno memorije. Pokušaj ponovo za nekoliko minuta."); return@LaunchedEffect }
        val dir = File(ctx.cacheDir, "diag_export").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val f = File(dir, DiagReport.fileName(k))
        val t0 = android.os.SystemClock.elapsedRealtime()
        val ok = withContext(Dispatchers.IO) {
            try { if (!DiagSpace.ok(dir)) throw java.io.IOException("space"); DiagReport.build(ctx, k, f) { coroutineContext[kotlinx.coroutines.Job]?.isActive != false }; true }  // i18n-ignore
            catch (e: Throwable) {
                DiagEvents.add("diag", "export failed: " + e.javaClass.simpleName)  // i18n-ignore
                DiagLife.export(k.name, android.os.SystemClock.elapsedRealtime() - t0, null, when (e) { is SecretScan.Refused -> "refused (sanitization not assured)"; is java.io.InterruptedIOException -> "cancelled"; else -> "failed (" + e.javaClass.simpleName + ")" })  // i18n-ignore
                f.delete(); false }
        }
        busy = false
        // the finished file is checked before any QR appears: present, not empty, readable UTF-8
        val valid = ok && f.exists() && f.length() > 64 && try { f.inputStream().use { i -> val b = ByteArray(4096); val n = i.read(b); java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(b, 0, maxOf(0, n - 4))); true } } catch (_: Throwable) { false }
        if (!valid) { if (ok) DiagLife.export(k.name, android.os.SystemClock.elapsedRealtime() - t0, f, "failed integrity check (empty or not UTF-8)"); f.delete(); refused = true; return@LaunchedEffect }  // i18n-ignore
        withContext(Dispatchers.IO) { DiagLife.export(k.name, android.os.SystemClock.elapsedRealtime() - t0, f, "ok") }  // i18n-ignore
        file = f
        until = System.currentTimeMillis() + 5 * 60_000L
        val r = PhoneLink.startDownload(f.name, f, "text/plain; charset=utf-8") { done = true; DiagLife.transfer("file served to the phone (download completed)") }  // i18n-ignore
        url = r?.first; session = r?.second ?: 0L
        DiagLife.transfer(if (r == null) "QR/link could not be started (no local network address)" else "QR shown")  // i18n-ignore
    }
    fun close() { PhoneLink.stop(session); File(ctx.cacheDir, "diag_export").listFiles()?.forEach { it.delete() }; onDismiss() }
    DisposableEffect(Unit) { onDispose { PhoneLink.stop(session) } }
    val qr = remember(url) { url?.let { PhoneLink.qr(it) }?.asImageBitmap() }
    Popup(if (kind == DiagReport.Kind.FULL) "Kompletan dijagnostički izvještaj" else "Izvezi izvještaj", { close() }, width = 720.dp) {
        val k = kind
        when {
            k == null -> {
                T("Izaberi šta želiš poslati:", 15.sp, C.Text, FontWeight.SemiBold)
                OptionRow("Zadnji izvještaj o padu (TXT)", R.drawable.lc_file_text, sub = "Zadnji pad ili zamrzavanje s događajima oko njega") { kind = DiagReport.Kind.LATEST }
                OptionRow("Kreiraj kompletan izvještaj", R.drawable.lc_file_text, sub = "Napravi kompletan dijagnostički izvještaj") { kind = DiagReport.Kind.FULL }
                OptionRow("Obriši sve izvještaje", R.drawable.lc_trash_2, color = C.Danger) { clear = true }
            }
            refused -> T(why ?: tr("Izvještaj nije napravljen: njegova sigurnost se ne može potvrditi ili nema dovoljno prostora."), 15.sp, C.Danger, maxLines = 3)
            busy || file == null -> T("Pripremam izvještaj… Back prekida.", 15.sp, C.Muted)
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (qr != null && !done) Image(qr, null, Modifier.size(200.dp).clip(Rnd(12.dp)).background(Color.White).padding(6.dp))
                    Column(Modifier.width(380.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when {
                            done -> T("✓ Izvještaj je preuzet na telefon. Veza je zatvorena.", 15.sp, C.Green, FontWeight.SemiBold, maxLines = 3)
                            url == null -> T("Preuzimanje preko telefona nije dostupno bez kućne mreže. Spremi izvještaj u folder.", 14.sp, C.Muted, maxLines = 3)
                            else -> {
                                T("✓ Izvještaj je uspješno napravljen.", 14.sp, C.Green, FontWeight.SemiBold, maxLines = 2)
                                T("Skeniraj telefonom", 17.sp, C.Text, FontWeight.Bold)
                                T("Preuzmi kompletan dijagnostički izvještaj (TXT). Telefon mora biti u istoj kućnoj mreži.", 13.sp, C.Soft, maxLines = 3)
                                T(trf("Link važi do %s. Back zatvara vezu.", SimpleDateFormat("HH:mm", Locale.US).format(Date(until))), 13.sp, C.Muted, maxLines = 2)  // i18n-ignore
                                T("Prenos u kućnoj mreži nije šifrovan (HTTP).", 12.sp, C.Muted, maxLines = 2)
                            }
                        }
                        file?.let { f -> T(f.name + " · " + (if (f.length() >= 1_048_576) String.format(Locale.US, "%.1f MB", f.length() / 1_048_576.0) else "${maxOf(1, f.length() / 1024)} KB"), 12.sp, C.Soft, maxLines = 2) }  // i18n-ignore
                        T("Nakon preuzimanja izvještaj možeš poslati e-mailom, WhatsAppom ili bilo kojom drugom aplikacijom.", 12.sp, C.Muted, maxLines = 3)
                        T("Izvještaj sadrži tehničke podatke o uređaju i mreži, bez lozinki i ključeva.", 12.sp, C.Muted, maxLines = 3)
                    }
                }
                OptionRow("Spremi u folder", R.drawable.lc_download) { folder = true }
            }
        }
        OptionRow("Zatvori", R.drawable.lc_x) { close() }
    }
    if (clear) SetConfirm(tr("Obrisati sve izvještaje?"), tr("Padovi, zamrzavanja i događaji na ovom uređaju bit će obrisani."), tr("Obriši"), danger = true,
        onCancel = { clear = false }) { DiagStore.clearAll(); Prefs.put("diag_pending", ""); clear = false
        Toast.makeText(ctx, tr("Izvještaji obrisani"), Toast.LENGTH_SHORT).show() }
    if (folder) FileBrowser(tr("Izaberi folder"), emptySet(), pickDir = true, onDismiss = { folder = false }) { dir ->
        try {
            val src = file ?: throw java.io.IOException("none")  // i18n-ignore
            if (!DiagSpace.ok(dir) || dir.usableSpace in 1 until src.length() * 2) throw java.io.IOException("space")  // i18n-ignore
            val f = File(dir, src.name); src.copyTo(f, overwrite = true)
            Toast.makeText(ctx, trf("Spremljeno: %s", f.path), Toast.LENGTH_LONG).show()
        } catch (e: Exception) { Toast.makeText(ctx, tr("U taj folder se ne može pisati. Izaberi drugi ili folder aplikacije."), Toast.LENGTH_LONG).show() }
        folder = false
    }
}
