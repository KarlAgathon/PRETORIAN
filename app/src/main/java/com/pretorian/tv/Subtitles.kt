package com.pretorian.tv

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Format
import kotlinx.coroutines.launch
import java.io.File

/** A built-in track of the stream, as found before playing. */
data class StreamTrack(val lang: String?, val label: String?, val title: String)

/**
 * Pick a subtitle: none, built into the video, search online (OpenSubtitles, SubDL), from a link,
 * from a file on the box/USB, and the subtitle languages.
 * [embedded] are the built-in tracks when already known (in the player); with [probeUrl] they are read on demand.
 * [onPicked] gets the .srt file, or null for "no subtitles".
 */
@Composable
fun SubtitlePicker(search: String, season: Int?, episode: Int?, embedded: List<String> = emptyList(),
                   onEmbedded: ((Int) -> Unit)? = null, probeUrl: String? = null, onProbed: ((StreamTrack) -> Unit)? = null,
                   startMode: String = "menu", onDismiss: () -> Unit, onPicked: (File?) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(startMode) }
    // opened straight on the search or the disk: Back closes it (one step), not into the full menu
    val stepBack = { if (startMode == "menu") mode = "menu" else onDismiss() }
    var results by remember { mutableStateOf<List<SubResult>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var probed by remember { mutableStateOf<List<StreamTrack>?>(null) }
    var aiPick by remember { mutableStateOf(false) }


    fun runSearch() {
        mode = "results"; busy = true; error = null
        scope.launch {
            try {
                results = Subs.search(search, season, episode); if (results.isEmpty()) error = tr("Nema titlova za") + " \"${cleanTitle(search).first}\""
                // AI picks the release that fits this exact film and puts it on top
                if (results.size > 1 && SportsAI.key.isNotBlank()) {
                    val (t, y) = cleanTitle(search)
                    SportsAI.bestSub(t + (if (season != null) " S${season}E${episode ?: 1}" else ""), y, 0, results.map { "${it.release} [${it.lang}]" })?.let { i ->  // i18n-ignore
                        val pick = results[i]; results = listOf(pick) + results.filter { it !== pick }; aiPick = true
                    }
                }
            }
            catch (e: Exception) { error = e.message }
            finally { busy = false }
        }
    }

    LaunchedEffect(Unit) { if (startMode == "results") runSearch() }

    when (mode) {
        "url" -> TextDialog("Titl s linka", "Link do .srt ili .zip titla", onDismiss = { mode = "menu" }) { url ->
            scope.launch {
                try { onPicked(Subs.fromUrl(ctx, url)) } catch (e: Exception) { Toast.makeText(ctx, tr("Titl se nije skinuo") + ": ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
        "disk" -> FilePicker("Titl sa diska ili USB-a", setOf("srt", "sub", "ass", "ssa", "vtt", "zip"), onDismiss = { stepBack() }) { bytes, _ ->
            try { onPicked(Subs.fromBytes(ctx, bytes)) } catch (e: Exception) { Toast.makeText(ctx, tr("Fajl se ne može pročitati"), Toast.LENGTH_LONG).show(); stepBack() }
        }
        "langs" -> LangOrderPicker("Jezici titla", Prefs.subLangs.split(',').filter { it.isNotBlank() }, onDismiss = { mode = "menu" }) {
            if (it.isNotEmpty()) Prefs.subLangs = it.joinToString(",")
            Toast.makeText(ctx, tr("Spremljeno"), Toast.LENGTH_SHORT).show()
            mode = "menu"
        }
        "embedded" -> Popup("Ugrađeni titlovi", onDismiss = { mode = "menu" }) {
            val list = probed
            when {
                list == null -> Box(Modifier.fillMaxWidth().height(110.dp)) { Loading("Tražim…") }
                list.isEmpty() -> T("Ovaj video nema ugrađenih titlova.", 14.sp, C.Muted, maxLines = 2)
                else -> list.forEach { t -> OptionRow(t.title, R.drawable.lc_captions, sub = "Ugrađen u video") { onProbed?.invoke(t) } }
            }
        }
        "results" -> Popup("Titlovi online", onDismiss = { stepBack() }, width = 760.dp) {
            T(cleanTitle(search).first + (season?.let { " · S$it" } ?: "") + (episode?.let { "E$it" } ?: "") + " · " + langList(Prefs.subLangs), 14.sp, C.Muted)
            if (busy) Box(Modifier.fillMaxWidth().height(120.dp)) { Loading("Tražim…") }
            error?.let { T(it, 14.sp, C.Danger, maxLines = 4) }
            results.take(40).forEachIndexed { i, r ->
                OptionRow(r.release.ifBlank { "Titl" }, if (i == 0) R.drawable.lc_check else R.drawable.lc_captions,
                    sub = (if (i == 0) tr(if (aiPick) "✓ AI preporuka" else "✓ najbolje odgovara") + " · " else "") + "${Langs.label(r.lang)} · ${r.provider}" + if (r.downloads > 0) " · ↓ ${r.downloads}" else "",
                    color = if (i == 0) C.Green else null) {
                    busy = true
                    scope.launch {
                        try { onPicked(Subs.download(ctx, r)) }
                        catch (e: Exception) { error = tr("Preuzimanje nije uspjelo") + ": ${e.message}" }
                        finally { busy = false }
                    }
                }
            }
        }
        else -> Popup("Titlovi", onDismiss) {
            OptionRow("Bez titla", R.drawable.lc_x, sub = "Nijedan titl, ni ugrađeni") { onPicked(null) }
            embedded.forEachIndexed { i, label -> OptionRow(label, R.drawable.lc_captions, sub = "Ugrađen u video") { onEmbedded?.invoke(i) } }
            if (probeUrl != null && onProbed != null) OptionRow("Ugrađeni titlovi", R.drawable.lc_captions, sub = "Titlovi koji dolaze s filmom") {
                mode = "embedded"
                if (probed == null) scope.launch {
                    probed = probeTracks(ctx, probeUrl)?.second?.map { StreamTrack(iso2(it.language), it.label, trackLabel(it, false)) } ?: emptyList()
                }
            }
            val prov = Subs.providers()
            OptionRow("Traži online", R.drawable.lc_search, sub = prov.joinToString(" · ") + " · " + langList(Prefs.subLangs)) { runSearch() }
            OptionRow("S linka (internet)", R.drawable.lc_link, sub = "Zalijepi link do .srt ili .zip") { mode = "url" }
            OptionRow("Sa diska ili USB-a", R.drawable.lc_folder, sub = ".srt, .ass, .sub, .zip") { mode = "disk" }
            OptionRow("Jezik titla", R.drawable.lc_languages, value = langList(Prefs.subLangs)) { mode = "langs" }
        }
    }
}

/**
 * Audio choice before playing: the stream is opened for a moment to read its real audio tracks.
 * With only one track there is nothing to choose, and the picker says so.
 */
@Composable
fun AudioPicker(url: String, current: String?, onDismiss: () -> Unit, onPick: (StreamTrack?) -> Unit) {
    val ctx = LocalContext.current
    var tracks by remember { mutableStateOf<List<Format>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(url) {
        val r = probeTracks(ctx, url)
        if (r == null) failed = true
        tracks = r?.first ?: emptyList()
    }
    Popup("Audio staze", onDismiss) {
        val list = tracks
        when {
            list == null -> Box(Modifier.fillMaxWidth().height(110.dp)) { Loading("Provjeravam audio staze…") }
            failed -> T("Staze se nisu mogle pročitati. Izaberi audio u plejeru: strelica dole › Audio.", 14.sp, C.Muted, maxLines = 3)
            list.size <= 1 -> {
                T("Film ima samo jednu audio stazu", 15.sp, C.Text)
                list.firstOrNull()?.let { OptionRow(trackLabel(it, true), R.drawable.lc_volume, selected = true) { onDismiss() } }
            }
            else -> {
                OptionRow("Automatski", R.drawable.lc_volume, sub = langList(Prefs.audioLangs), selected = current == null) { onPick(null) }
                list.forEach { f ->
                    val t = StreamTrack(iso2(f.language), f.label, trackLabel(f, true))
                    OptionRow(t.title, R.drawable.lc_volume, selected = current == t.title) { onPick(t) }
                }
            }
        }
    }
}

/** The "Subtitle" box: only the subtitles the provider's stream has, plus Off. Online and disk are in "Traži titl". */
@Composable
fun ProviderSubsPicker(url: String, current: String?, off: Boolean, onDismiss: () -> Unit, onOff: () -> Unit, onPick: (StreamTrack) -> Unit) {
    val ctx = LocalContext.current
    var tracks by remember { mutableStateOf<List<StreamTrack>?>(null) }
    LaunchedEffect(url) {
        tracks = probeTracks(ctx, url)?.second?.map { StreamTrack(iso2(it.language), it.label, trackLabel(it, false)) } ?: emptyList()
    }
    Popup("Titl", onDismiss) {
        val list = tracks
        when {
            list == null -> Box(Modifier.fillMaxWidth().height(110.dp)) { Loading("Provjeravam titlove…") }
            list.isEmpty() -> {
                T("Provider nema titlova za ovaj naslov. Za online ili s diska koristi „Traži titl“.", 14.sp, C.Muted, maxLines = 3)
                OptionRow("U redu", R.drawable.lc_check) { onDismiss() }
            }
            else -> {
                OptionRow("Isključeno", R.drawable.lc_x, selected = off) { onOff() }
                list.forEach { t -> OptionRow(t.title, R.drawable.lc_captions, sub = "Provider", selected = !off && current == t.title) { onPick(t) } }
            }
        }
    }
}

/**
 * Subtitle timing fix: shifts every time in an .srt / .vtt file. SYNC writes the whole file again and keeps it for this film,
 * a second SYNC later in the film stretches the times between the two points, so a subtitle that drifts stays right to the end.
 */
object SubSync {
    private val TIME = Regex("(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{3})")
    private fun ms(m: MatchResult) = m.groupValues.let { (it[1].toLong() * 3600 + it[2].toLong() * 60 + it[3].toLong()) * 1000 + it[4].toLong() }
    private fun fmt(t: Long, comma: Boolean): String {
        val v = t.coerceAtLeast(0)
        return String.format(java.util.Locale.US, "%02d:%02d:%02d%s%03d", v / 3_600_000, v / 60_000 % 60, v / 1000 % 60, if (comma) "," else ".", v % 1000)  // i18n-ignore
    }
    /** writes [src] shifted by [map] (old time → new time) into a new file next to it */
    private fun rewrite(src: File, map: (Long) -> Long): File {
        val text = src.readText()
        val out = text.lines().joinToString("\n") { line ->
            if (!line.contains("-->")) line else TIME.replace(line) { m -> fmt(map(ms(m)), m.value.contains(',')) }
        }
        val f = File(src.parentFile, src.nameWithoutExtension.substringBefore("_sync") + "_sync" + System.currentTimeMillis() % 100000 + "." + src.extension)
        f.writeText(out); return f
    }
    private fun key(url: String) = "subfix_" + url.hashCode()
    /** a fixed subtitle kept for this film or episode, or null */
    fun kept(url: String): String? = Prefs.str(key(url)).takeIf { it.isNotBlank() && File(it).exists() }
    /** shift by [offsetMs] (positive: the subtitle came too late, so it moves earlier) */
    fun shift(path: String, offsetMs: Long): File = rewrite(File(path)) { it - offsetMs }
    /** SYNC: keeps the fixed file for this film; the second SYNC at another point fixes the drift between the two */
    fun sync(url: String, path: String, atMs: Long, offsetMs: Long): File {
        val prevAt = Prefs.long(key(url) + "_at", -1L)
        val f = if (prevAt >= 0 && kotlin.math.abs(atMs - prevAt) > 5 * 60_000L) {
            // stretch: no change at the first point, the full correction at this point, in between and after in proportion
            rewrite(File(path)) { t -> t - offsetMs * (t - prevAt) / (atMs - prevAt) }
        } else rewrite(File(path)) { it - offsetMs }
        Prefs.put(key(url), f.path); Prefs.put(key(url) + "_at", atMs)
        return f
    }
    fun forget(url: String) { Prefs.remove(key(url), key(url) + "_at") }
}

/** The bar in the player to move the subtitle earlier or later, with SYNC for the whole file. */
@Composable
fun SubSyncDialog(name: String, onDismiss: () -> Unit, onPreview: (Long) -> Unit, onSync: (Long) -> Unit, onReset: () -> Unit) {
    var off by remember { mutableLongStateOf(0L) }
    LaunchedEffect(off) { kotlinx.coroutines.delay(900); onPreview(off) }   // shows the change after a short pause, not on every press
    Popup(tr("Podesi vrijeme titla") + " · " + name, onDismiss, width = 760.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(-1000L to "−1 s", -100L to "−0,1 s").forEach { (d, l) -> Button(l, null) { off += d } }  // i18n-ignore
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                T((if (off > 0) "+" else "") + String.format(java.util.Locale.US, "%.1f s", off / 1000.0).replace('.', ','), 30.sp, C.Text, FontWeight.ExtraBold)  // i18n-ignore
                T(if (off > 0) trf("titl je kasnio %s s", String.format(java.util.Locale.US, "%.1f", off / 1000.0)) else if (off < 0) trf("titl je žurio %s s", String.format(java.util.Locale.US, "%.1f", -off / 1000.0)) else tr("bez pomaka"), 12.sp, C.Muted)
            }
            listOf(100L to "+0,1 s", 1000L to "+1 s").forEach { (d, l) -> Button(l, null) { off += d } }  // i18n-ignore
        }
        T("SYNC prepravi cijeli fajl titla i snimi ga uz ovaj film. Ako pred kraj opet kasni, podesi ponovo i klikni SYNC – titl se ispravi kroz cijeli film.", 12.sp, C.Soft, maxLines = 3)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button("✓ SYNC", R.drawable.lc_check, primary = true) { onSync(off) }  // i18n-ignore
            Button("Poništi", R.drawable.lc_rotate_ccw) { off = 0; onReset() }
        }
    }
}
