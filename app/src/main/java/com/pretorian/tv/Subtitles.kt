package com.pretorian.tv

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
                   onDismiss: () -> Unit, onPicked: (File?) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf("menu") }
    var results by remember { mutableStateOf<List<SubResult>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var probed by remember { mutableStateOf<List<StreamTrack>?>(null) }


    fun runSearch() {
        mode = "results"; busy = true; error = null
        scope.launch {
            try { results = Subs.search(search, season, episode); if (results.isEmpty()) error = tr("Nema titlova za") + " \"${cleanTitle(search).first}\"" }
            catch (e: Exception) { error = e.message }
            finally { busy = false }
        }
    }

    when (mode) {
        "url" -> TextDialog("Titl s linka", "Link do .srt ili .zip titla", onDismiss = { mode = "menu" }) { url ->
            scope.launch {
                try { onPicked(Subs.fromUrl(ctx, url)) } catch (e: Exception) { Toast.makeText(ctx, tr("Titl se nije skinuo") + ": ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
        "disk" -> FilePicker("Titl sa diska ili USB-a", setOf("srt", "sub", "ass", "ssa", "vtt", "zip"), onDismiss = { mode = "menu" }) { bytes, _ ->
            try { onPicked(Subs.fromBytes(ctx, bytes)) } catch (e: Exception) { Toast.makeText(ctx, tr("Fajl se ne može pročitati"), Toast.LENGTH_LONG).show(); mode = "menu" }
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
        "results" -> Popup("Titlovi online", onDismiss = { mode = "menu" }, width = 760.dp) {
            T(cleanTitle(search).first + (season?.let { " · S$it" } ?: "") + (episode?.let { "E$it" } ?: "") + " · " + langList(Prefs.subLangs), 14.sp, C.Muted)
            if (busy) Box(Modifier.fillMaxWidth().height(120.dp)) { Loading("Tražim…") }
            error?.let { T(it, 14.sp, C.Danger, maxLines = 4) }
            results.take(40).forEach { r ->
                OptionRow(r.release.ifBlank { "Titl" }, R.drawable.lc_captions,
                    sub = "${Langs.label(r.lang)} · ${r.provider}" + if (r.downloads > 0) " · ↓ ${r.downloads}" else "") {
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
            OptionRow("Traži online", R.drawable.lc_search,
                sub = if (prov.isEmpty()) "Prvo upiši API ključ: Postavke › Filmovi i titlovi" else prov.joinToString(" · ") + " · " + langList(Prefs.subLangs)) {
                if (prov.isEmpty()) Toast.makeText(ctx, tr("Postavke › Filmovi i titlovi › OpenSubtitles ili SubDL ključ"), Toast.LENGTH_LONG).show() else runSearch()
            }
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
