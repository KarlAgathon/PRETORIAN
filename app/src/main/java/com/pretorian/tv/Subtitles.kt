package com.pretorian.tv

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import kotlinx.coroutines.launch
import java.io.File

/**
 * Pick a subtitle: none, search online (OpenSubtitles, SubDL), from a link, or from a file on the box/USB.
 * [onPicked] gets the .srt file, or null for "no subtitles".
 */
@Composable
fun SubtitlePicker(search: String, season: Int?, episode: Int?, embedded: List<String> = emptyList(),
                   onEmbedded: ((Int) -> Unit)? = null, onDismiss: () -> Unit, onPicked: (File?) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf("menu") }
    var results by remember { mutableStateOf<List<SubResult>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) try {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw Exception("prazno")
            onPicked(Subs.fromBytes(ctx, bytes))
        } catch (e: Exception) { Toast.makeText(ctx, "Fajl se ne može pročitati", Toast.LENGTH_LONG).show() }
    }

    fun runSearch() {
        mode = "results"; busy = true; error = null
        scope.launch {
            try { results = Subs.search(search, season, episode); if (results.isEmpty()) error = "Nema titlova za \"${cleanTitle(search).first}\"" }
            catch (e: Exception) { error = e.message }
            finally { busy = false }
        }
    }

    when (mode) {
        "url" -> TextDialog("Titl s linka", "Link do .srt ili .zip titla", onDismiss = { mode = "menu" }) { url ->
            scope.launch {
                try { onPicked(Subs.fromUrl(ctx, url)) } catch (e: Exception) { Toast.makeText(ctx, "Titl se nije skinuo: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
        "results" -> Popup("Titlovi online", onDismiss = { mode = "menu" }, width = 760.dp) {
            T(cleanTitle(search).first + (season?.let { " · S$it" } ?: "") + (episode?.let { "E$it" } ?: ""), 14.sp, C.Muted)
            if (busy) androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(120.dp)) { Loading("Tražim…") }
            error?.let { T(it, 14.sp, C.Danger, maxLines = 4) }
            results.take(40).forEach { r ->
                OptionRow(r.release.ifBlank { "Titl" }, R.drawable.lc_captions,
                    sub = "${Subs.langName(r.lang)} · ${r.provider}" + if (r.downloads > 0) " · ↓ ${r.downloads}" else "") {
                    busy = true
                    scope.launch {
                        try { onPicked(Subs.download(ctx, r)) }
                        catch (e: Exception) { error = "Preuzimanje nije uspjelo: ${e.message}" }
                        finally { busy = false }
                    }
                }
            }
        }
        else -> Popup("Titlovi", onDismiss) {
            OptionRow("Bez titla", R.drawable.lc_x) { onPicked(null) }
            embedded.forEachIndexed { i, label -> OptionRow(label, R.drawable.lc_captions, sub = "Ugrađen u video") { onEmbedded?.invoke(i) } }
            val prov = Subs.providers()
            OptionRow("Traži online", R.drawable.lc_search,
                sub = if (prov.isEmpty()) "Prvo upiši API ključ: Postavke › Filmovi i titlovi" else prov.joinToString(" · ") + " · " + Prefs.subLangs.uppercase()) {
                if (prov.isEmpty()) Toast.makeText(ctx, "Postavke › Filmovi i titlovi › OpenSubtitles ili SubDL ključ", Toast.LENGTH_LONG).show() else runSearch()
            }
            OptionRow("S linka (internet)", R.drawable.lc_link, sub = "Zalijepi link do .srt ili .zip") { mode = "url" }
            OptionRow("Sa diska ili USB-a", R.drawable.lc_folder, sub = ".srt fajl") {
                try { fileLauncher.launch("*/*") } catch (e: Exception) { Toast.makeText(ctx, "Na ovom uređaju nema upravitelja fajlova", Toast.LENGTH_LONG).show() }
            }
        }
    }
    LaunchedEffect(Unit) { }
}

/** Audio language choice before playing (the real track list appears in the player). */
@Composable
fun AudioPicker(current: String?, tracks: List<String> = emptyList(), onTrack: ((Int) -> Unit)? = null, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    Popup("Audio", onDismiss) {
        if (tracks.isNotEmpty()) {
            tracks.forEachIndexed { i, t -> OptionRow(t, R.drawable.lc_volume) { onTrack?.invoke(i) } }
        } else {
            T("Staze zvuka vide se tek kad film krene. Ovdje biraš koji jezik da uzme ako postoji.", 13.sp, C.Muted, maxLines = 3)
            OptionRow("Automatski", R.drawable.lc_volume, sub = "Prema postavkama: ${Prefs.audioLangs.uppercase()}", selected = current == null) { onPick(null) }
            listOf("en", "bs", "hr", "sr", "de", "tr", "it", "fr", "es").forEach { l ->
                OptionRow(Subs.langName(l), R.drawable.lc_volume, selected = current == l) { onPick(l) }
            }
        }
    }
}
