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
    fun stamp(t: Long = System.currentTimeMillis()): String = fmt.get()!!.format(Date(t))
    fun parseTime(line: String): Long = try { fmt.get()!!.parse(line.substring(0, 23))?.time ?: 0L } catch (_: Throwable) { 0L }

    fun add(kind: String, msg: String?) {
        try {
            val lvl = when { kind in CRITICAL -> "C"; kind in INFO -> "I"; kind.endsWith("warn") -> "W"; else -> "E" }
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
            .forEach { (_, v) -> out += v }
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
    enum class Kind { LATEST, FULL, ZIP }
    /** the complete TXT stays readable on a phone: past this the oldest events are left out (and counted) */
    private const val FULL_TXT_MAX = 20L * 1024 * 1024

    private fun tsName() = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())  // i18n-ignore
    fun fileName(k: Kind = Kind.FULL): String = "MediaCenter-" + BuildConfig.VERSION_NAME + "-" + when (k) {  // i18n-ignore
        Kind.LATEST -> "crash"; Kind.FULL -> "report"; Kind.ZIP -> "diagnostics" } + "-" + tsName() + if (k == Kind.ZIP) ".zip" else ".txt"  // i18n-ignore

    /** facts about the app, the box and the network; "nicht verfügbar" is written as "unavailable", never guessed */
    fun header(ctx: Context): String {
        val b = StringBuilder()
        val tz = java.util.TimeZone.getDefault()
        b.append("Media Center ").append(BuildConfig.VERSION_NAME).append(" (versionCode ").append(BuildConfig.VERSION_CODE).append(", ")
            .append(BuildConfig.BUILD_TYPE).append(")\n")  // i18n-ignore
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

    private class Out(val w: java.io.Writer, val secrets: List<String>) {
        var bytes = 0L
        fun line(s: String) { val c = SecretScan.clean(s, secrets); w.write(c); w.write("\n"); bytes += c.length + 1 }
        fun block(s: String) = s.lineSequence().forEach { line(it) }
    }

    private fun incidentText(i: DiagStore.Incident) = DiagStore.read(i.f)

    /** builds the export into [dest]; throws [SecretScan.Refused] when it cannot be made safe */
    fun build(ctx: Context, kind: Kind, dest: File) {
        val secrets = try { SecretScan.values(ctx) } catch (e: Throwable) { throw SecretScan.Refused("secrets unreadable") }  // i18n-ignore
        DiagStore.flushNow()
        if (dest.exists()) dest.delete()
        when (kind) {
            Kind.LATEST -> dest.bufferedWriter().use { w -> val o = Out(w, secrets); latest(ctx, o) }
            Kind.FULL -> dest.bufferedWriter().use { w -> val o = Out(w, secrets); full(ctx, o) }
            Kind.ZIP -> java.util.zip.ZipOutputStream(dest.outputStream().buffered()).use { z ->
                fun entry(name: String, fill: (Out) -> Unit) {
                    z.putNextEntry(java.util.zip.ZipEntry(name))
                    val w = java.io.OutputStreamWriter(object : java.io.FilterOutputStream(z) { override fun close() { flush() } }, Charsets.UTF_8)
                    fill(Out(w, secrets)); w.flush(); z.closeEntry()
                }
                entry("report.txt") { o -> o.block(header(ctx)); o.line(""); o.line("== Incidents ==")  // i18n-ignore
                    DiagStore.incidents().forEach { o.line(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(it.time)) + "  " + it.kind + "  " + it.base) } }  // i18n-ignore
                DiagStore.incidents().forEach { i ->
                    entry("incidents/" + i.base + ".txt") { o -> o.block(incidentText(i)) }  // i18n-ignore
                    DiagStore.logOf(i)?.let { lf -> entry("incidents/" + i.base + ".log.txt") { o -> o.block(DiagStore.read(lf)) } }  // i18n-ignore
                }
                entry("events.txt") { o -> DiagStore.eachEvent { o.line(it) } }  // i18n-ignore
            }
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

    private fun full(ctx: Context, o: Out) {
        o.block(header(ctx)); o.line("")
        val inc = DiagStore.incidents()
        o.line("== Incidents: " + inc.size + " (newest first, each complete) ==")  // i18n-ignore
        inc.forEach { i ->
            o.line(""); o.line("--- " + i.kind + " · " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(i.time)) + " · " + i.base + " ---")  // i18n-ignore
            o.block(incidentText(i))
            DiagStore.logOf(i)?.let { o.line("--- app log for this incident ---"); o.block(DiagStore.read(it)) }  // i18n-ignore
        }
        // events: newest are the most useful; when the file would pass the limit, the OLDEST are left out and counted
        val total = DiagStore.eventCount()
        val room = ((FULL_TXT_MAX - o.bytes) / 160).coerceAtLeast(0)   // ~160 bytes per event line
        val skip = (total - room).coerceAtLeast(0)
        o.line(""); o.line("== Events: " + total + " saved (oldest first) ==")  // i18n-ignore
        if (skip > 0) o.line("[" + skip + " oldest events left out to keep this file readable — they are in the ZIP archive]")  // i18n-ignore
        var n = 0L
        DiagStore.eachEvent { l -> if (n++ >= skip) o.line(l) }
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
    LaunchedEffect(kind) {
        val k = kind ?: return@LaunchedEffect
        busy = true; refused = false; url = null; done = false
        val dir = File(ctx.cacheDir, "diag_export").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val f = File(dir, DiagReport.fileName(k))
        val ok = withContext(Dispatchers.IO) {
            try { if (!DiagSpace.ok(dir)) throw java.io.IOException("space"); DiagReport.build(ctx, k, f); true }  // i18n-ignore
            catch (e: Throwable) { DiagEvents.add("diag", "export failed: " + e.javaClass.simpleName); f.delete(); false }  // i18n-ignore
        }
        busy = false
        if (!ok) { refused = true; return@LaunchedEffect }
        file = f
        val r = PhoneLink.startDownload(f.name, f, if (k == DiagReport.Kind.ZIP) "application/zip" else "text/plain; charset=utf-8") { done = true }  // i18n-ignore
        url = r?.first; session = r?.second ?: 0L
    }
    fun close() { PhoneLink.stop(session); File(ctx.cacheDir, "diag_export").listFiles()?.forEach { it.delete() }; onDismiss() }
    DisposableEffect(Unit) { onDispose { PhoneLink.stop(session) } }
    val qr = remember(url) { url?.let { PhoneLink.qr(it) }?.asImageBitmap() }
    Popup("Izvezi izvještaj", { close() }, width = 680.dp) {
        val k = kind
        when {
            k == null -> {
                T("Izaberi šta želiš poslati:", 15.sp, C.Text, FontWeight.SemiBold)
                OptionRow("Zadnji izvještaj o padu (TXT)", R.drawable.lc_file_text, sub = "Zadnji pad ili zamrzavanje s događajima oko njega") { kind = DiagReport.Kind.LATEST }
                OptionRow("Kompletan izvještaj (TXT)", R.drawable.lc_file_text, sub = "Svi sačuvani padovi i događaji") { kind = DiagReport.Kind.FULL }
                OptionRow("Kompletna arhiva (ZIP)", R.drawable.lc_download, sub = "Sve datoteke dijagnostike u jednom fajlu") { kind = DiagReport.Kind.ZIP }
                OptionRow("Obriši sve izvještaje", R.drawable.lc_trash_2, color = C.Danger) { clear = true }
            }
            refused -> T("Izvještaj nije napravljen: njegova sigurnost se ne može potvrditi ili nema dovoljno prostora.", 15.sp, C.Danger, maxLines = 3)
            busy || file == null -> T("Pripremam izvještaj…", 15.sp, C.Muted)
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (qr != null && !done) Image(qr, null, Modifier.size(200.dp).clip(Rnd(12.dp)).background(Color.White).padding(6.dp))
                    Column(Modifier.width(380.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when {
                            done -> T("✓ Izvještaj je preuzet na telefon. Veza je zatvorena.", 15.sp, C.Green, FontWeight.SemiBold, maxLines = 3)
                            url == null -> T("Preuzimanje preko telefona nije dostupno bez kućne mreže. Spremi izvještaj u folder.", 14.sp, C.Muted, maxLines = 3)
                            else -> {
                                T("Skeniraj telefonom (ista kućna mreža) i preuzmi fajl.", 15.sp, C.Text, FontWeight.SemiBold, maxLines = 3)
                                T("Link važi 5 minuta i za jedno preuzimanje. Back zatvara vezu.", 13.sp, C.Muted, maxLines = 2)
                                T("Prenos u kućnoj mreži nije šifrovan (HTTP).", 12.sp, C.Muted, maxLines = 2)
                            }
                        }
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
