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
 * Phase A0 – diagnostics foundation.
 * The last 200 relevant events (player errors, playlist and EPG failures, crashes), kept in memory only.
 * Every line is passed through [Redact] on the way in, so the buffer never holds a password or a key.
 */
object DiagEvents {
    const val MAX = 200
    private val list = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)  // i18n-ignore

    fun add(kind: String, msg: String?) {
        try {
            val line = fmt.format(Date()) + "  " + kind + "  " + Redact.text((msg ?: "").replace('\n', ' ').take(400))
            synchronized(list) { list.addLast(line); while (list.size > MAX) list.removeFirst() }
        } catch (_: Throwable) {}
    }
    fun lines(): List<String> = synchronized(list) { list.toList() }
    fun clearForTest() = synchronized(list) { list.clear() }
}

/** Writing diagnostics stops below this much free space, so a full box never crashes because of a log. */
object DiagSpace {
    const val MIN_FREE = 20L * 1024 * 1024
    fun ok(dir: File?): Boolean = try { dir == null || (dir.usableSpace <= 0L) || dir.usableSpace >= MIN_FREE } catch (_: Throwable) { false }
}

/**
 * The last line of defence before a report leaves the box: the real secret values stored on this box
 * (playlist logins, links, MAC and device ids, API keys, PIN hash) are searched for and replaced.
 * If the values cannot be read, or one is still found afterwards, the report is refused.
 */
object SecretScan {
    class Refused(msg: String) : Exception(msg)

    /** every secret value this box knows; values shorter than 4 characters are too short to search for reliably */
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
        return out.map { it.trim() }.filter { it.length >= 4 }.distinct().sortedByDescending { it.length }
    }

    /** returns the cleaned text, or throws [Refused] when the report cannot be confirmed safe */
    fun clean(text: String, secrets: List<String>): String {
        var t = Redact.text(text)
        for (s in secrets) {
            t = t.replace(s, "***")
            // the same value URL-encoded (a login inside a link)
            val enc = try { java.net.URLEncoder.encode(s, "UTF-8") } catch (_: Exception) { s }
            if (enc != s) t = t.replace(enc, "***")
        }
        if (secrets.any { it in t }) throw Refused("secret still present")  // i18n-ignore
        return t
    }
}

/** The TXT report: device and app facts, the latest crash and freeze reports, recent events. Never the stream content. */
object DiagReport {
    private const val PER_FILE = 12_000

    fun build(ctx: Context): String {
        val b = StringBuilder()
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())  // i18n-ignore
        b.append("Media Center ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(", ")
            .append(BuildConfig.BUILD_TYPE).append(")\n")  // i18n-ignore
        b.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append(" · Android ").append(Build.VERSION.RELEASE)
            .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")  // i18n-ignore
        b.append("Report time: ").append(now).append(" · screen: ").append(CrashLog.screen).append('\n')  // i18n-ignore
        val rt = Runtime.getRuntime()
        b.append("App heap: ").append((rt.totalMemory() - rt.freeMemory()) / 1_048_576).append(" / ").append(rt.maxMemory() / 1_048_576)
            .append(" MB · free storage: ").append(ctx.filesDir.usableSpace / 1_048_576).append(" MB\n")  // i18n-ignore
        val pls = Store.playlists(ctx)
        b.append("Playlists: ").append(pls.size).append(" · active: ")
            .append(pls.firstOrNull { it.active }?.type?.name ?: "none").append('\n')  // i18n-ignore
        b.append("Crashes in the last 10 min: ").append(Prefs.str("crash_times").split(',').count { it.isNotBlank() }).append("\n\n")  // i18n-ignore

        b.append("== Recent events (").append(DiagEvents.lines().size).append(") ==\n")  // i18n-ignore
        DiagEvents.lines().forEach { b.append(it).append('\n') }

        val files = CrashLog.dir(ctx)?.listFiles()?.sortedByDescending { it.lastModified() }.orEmpty()
        b.append("\n== Saved reports (").append(files.size).append(", newest first) ==\n")  // i18n-ignore
        files.take(4).forEach { f ->
            b.append("\n--- ").append(f.name).append(" ---\n")
            b.append(try { f.readText().take(PER_FILE) } catch (_: Throwable) { "(unreadable)" })  // i18n-ignore
            b.append('\n')
        }
        return b.toString()
    }

    /** the finished, cleaned report, or [SecretScan.Refused] */
    fun safe(ctx: Context): String {
        val secrets = try { SecretScan.values(ctx) } catch (e: Throwable) { throw SecretScan.Refused("secrets unreadable") }  // i18n-ignore
        return SecretScan.clean(build(ctx), secrets)
    }

    fun fileName(): String = "MediaCenter-report-" + SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date()) + ".txt"  // i18n-ignore
}

/**
 * Settings › Diagnostics › Izvezi izvještaj: the cleaned report as a one-time download for a phone on the same home network.
 * The temporary server lives only while this window is open (Back closes it), for 5 minutes at most and for one download.
 */
@Composable
fun DiagExportDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf<String?>(null) }
    var refused by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }
    var folder by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val r = withContext(Dispatchers.IO) { try { DiagReport.safe(ctx) } catch (e: Throwable) { null } }
        if (r == null) { refused = true; return@LaunchedEffect }
        text = r
        url = PhoneLink.startDownload(DiagReport.fileName(), r.toByteArray(Charsets.UTF_8)) { done = true }
    }
    DisposableEffect(Unit) { onDispose { PhoneLink.stop() } }
    val qr = remember(url) { url?.let { PhoneLink.qr(it) }?.asImageBitmap() }
    Popup("Izvezi izvještaj", { PhoneLink.stop(); onDismiss() }, width = 640.dp) {
        when {
            refused -> T("Izvještaj nije napravljen: njegova sigurnost se ne može potvrditi.", 15.sp, C.Danger, maxLines = 3)
            text == null -> T("Pripremam izvještaj…", 15.sp, C.Muted)
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (qr != null && !done) Image(qr, null, Modifier.size(200.dp).clip(Rnd(12.dp)).background(Color.White).padding(6.dp))
                    Column(Modifier.width(360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when {
                            done -> T("✓ Izvještaj je preuzet na telefon. Veza je zatvorena.", 15.sp, C.Green, FontWeight.SemiBold, maxLines = 3)
                            url == null -> T("Preuzimanje preko telefona nije dostupno bez kućne mreže. Spremi izvještaj u folder.", 14.sp, C.Muted, maxLines = 3)
                            else -> {
                                T("Skeniraj telefonom (ista kućna mreža) i preuzmi TXT.", 15.sp, C.Text, FontWeight.SemiBold, maxLines = 3)
                                T("Link važi 5 minuta i za jedno preuzimanje. Back zatvara vezu.", 13.sp, C.Muted, maxLines = 2)
                            }
                        }
                        T("Izvještaj sadrži tehničke podatke o uređaju i mreži, bez lozinki i ključeva.", 12.sp, C.Muted, maxLines = 3)
                    }
                }
                OptionRow("Spremi u folder", R.drawable.lc_download) { folder = true }
            }
        }
        OptionRow("Zatvori", R.drawable.lc_x) { PhoneLink.stop(); onDismiss() }
    }
    if (folder) FileBrowser(tr("Izaberi folder"), emptySet(), pickDir = true, onDismiss = { folder = false }) { dir ->
        try {
            if (!DiagSpace.ok(dir)) throw java.io.IOException("space")  // i18n-ignore
            val f = File(dir, DiagReport.fileName()); f.writeText(text ?: "")
            Toast.makeText(ctx, trf("Spremljeno: %s", f.path), Toast.LENGTH_LONG).show()
        } catch (e: Exception) { Toast.makeText(ctx, tr("U taj folder se ne može pisati. Izaberi drugi ili folder aplikacije."), Toast.LENGTH_LONG).show() }
        folder = false
    }
}
