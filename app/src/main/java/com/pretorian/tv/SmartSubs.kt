package com.pretorian.tv

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import kotlinx.coroutines.launch

/* ============================================================== Smart subtitles: one language, versions V1 · V2 · V3 */

/**
 * Online subtitles in exactly one language (Player settings › Subtitles). The sources are searched together,
 * a failing source never stops the others; the same file from two sources is shown once; the best fit is V1.
 * Results are kept per title for the session, so the menu opens at once and the order never jumps while you look.
 */
object SmartSubs {
    /** the one language online search uses */
    fun lang(): String = Prefs.subLangs.split(',').map { it.trim() }.firstOrNull { it.length == 2 } ?: Look.lang
    fun setLang(code: String) { Prefs.subLangs = code; SubStyle.rev++ }

    sealed class State { object Searching : State(); data class Found(val list: List<SubResult>) : State(); object None : State() }
    private val cache = java.util.concurrent.ConcurrentHashMap<String, State>()
    var rev by mutableIntStateOf(0)
    private fun key(search: String, season: Int?, episode: Int?) = cleanTitle(search).first.lowercase() + "|" + (season ?: -1) + "|" + (episode ?: -1) + "|" + lang()

    fun state(search: String, season: Int?, episode: Int?): State? = cache[key(search, season, episode)]

    /** starts the search once per title (on the film page already, so the player has it ready); never blocks playback */
    suspend fun prepare(search: String, season: Int?, episode: Int?): State {
        val k = key(search, season, episode)
        cache[k]?.let { if (it !is State.Searching) return it }
        cache[k] = State.Searching; rev++
        val l = lang()
        val st = try {
            val all = Subs.search(search, season, episode).filter { it.lang == l }
            // the same release from two sources counts once
            val uniq = all.distinctBy { Sports.norm(it.release.substringBeforeLast('.')) }
            if (uniq.isEmpty()) State.None else State.Found(uniq)
        } catch (e: Exception) { State.None }
        cache[k] = st; rev++
        return st
    }
    fun forget() { cache.clear(); rev++ }
}

/** Subtitle look: size Small/Medium/Large and one of six fonts the box has. */
object SubStyle {
    var rev by mutableIntStateOf(0)
    val FONTS = listOf("Sans" to "sans-serif", "Sans Medium" to "sans-serif-medium", "Condensed" to "sans-serif-condensed",
        "Serif" to "serif", "Monospace" to "monospace", "Casual" to "casual")
    val SIZES = listOf("Mala", "Srednja", "Velika")
    fun font() = Prefs.long("sub_font", 0).toInt().coerceIn(0, FONTS.lastIndex)
    fun size() = (Prefs.str("sub_size", "1").toIntOrNull() ?: 1).coerceIn(0, 2)
}

/** Applies size, font and outline to the player's subtitle view (works for text subtitles; picture subtitles keep their look). */
fun applySubStyle(sv: androidx.media3.ui.SubtitleView) {
    val size = listOf(0.8f, 1f, 1.25f)[SubStyle.size()]
    sv.setFractionalTextSize(androidx.media3.ui.SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * size)
    sv.setApplyEmbeddedStyles(false)
    val tf = android.graphics.Typeface.create(SubStyle.FONTS[SubStyle.font()].second, android.graphics.Typeface.NORMAL)
    sv.setStyle(androidx.media3.ui.CaptionStyleCompat(android.graphics.Color.WHITE, android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT,
        androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE, android.graphics.Color.BLACK, tf))
}

/**
 * The subtitle menu of films and series (approved): PROVIDER (the tracks of the stream, every language) and ONLINE
 * (versions in the one chosen language: Bosanski (V1), (V2), (V3)), then "Find more subtitles". OK applies at once.
 */
@Composable
fun SubsPanel(item: PlayItem, tracks: List<TrackItem>, current: String?, onDismiss: () -> Unit, onOff: () -> Unit, onTrack: (TrackItem) -> Unit, onFile: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val search = item.search ?: item.title
    @Suppress("UNUSED_VARIABLE") val r = SmartSubs.rev
    LaunchedEffect(search) { if (SmartSubs.state(search, item.season, item.episode) == null) SmartSubs.prepare(search, item.season, item.episode) }
    var shown by remember { mutableIntStateOf(3) }
    var busy by remember { mutableStateOf<String?>(null) }
    val chosen = remember { mutableStateOf(Prefs.str("subpick_" + item.url.hashCode())) }
    Popup("", onDismiss, width = 620.dp) {
        T("Provider", 22.sp, C.Text, FontWeight.Bold)
        if (tracks.isEmpty()) T("Nije dostupno", 15.sp, C.Soft, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
        tracks.forEach { t -> OptionRow(t.label, R.drawable.lc_message_square, selected = current == null && t.selected) { onTrack(t) } }
        Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(1.dp).background(Color(0x33FFFFFF)))
        T("Online", 22.sp, C.Text, FontWeight.Bold)
        val langName = Langs.label(SmartSubs.lang())
        when (val st = SmartSubs.state(search, item.season, item.episode)) {
            null, SmartSubs.State.Searching -> T("Tražim titlove…", 15.sp, C.Soft, modifier = Modifier.padding(start = 8.dp))
            SmartSubs.State.None -> T("Nije dostupno", 15.sp, C.Soft, modifier = Modifier.padding(start = 8.dp))
            is SmartSubs.State.Found -> {
                st.list.take(shown).forEachIndexed { i, res ->
                    val id = res.provider + "|" + res.ref
                    OptionRow(trf("%s (V%s)", langName, i + 1), R.drawable.lc_cloud, selected = current != null && chosen.value == id,
                        value = if (busy == id) tr("Preuzimam…") else null) {
                        if (busy != null) return@OptionRow
                        busy = id
                        scope.launch {
                            try {
                                val f = Subs.download(ctx, res)
                                Prefs.put("subpick_" + item.url.hashCode(), id); chosen.value = id
                                onFile(f.path)
                            } catch (e: Exception) { Toast.makeText(ctx, tr("Titl se nije mogao preuzeti, probaj drugu verziju"), Toast.LENGTH_SHORT).show() }
                            busy = null
                        }
                    }
                }
                if (st.list.size > shown) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(1.dp).background(Color(0x33FFFFFF)))
                    OptionRow("Pronađi još titlova", R.drawable.lc_search) { shown += 3 }
                }
            }
        }
        if (current != null || tracks.any { it.selected }) {
            Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(1.dp).background(Color(0x33FFFFFF)))
            OptionRow("Bez titlova", R.drawable.lc_x) { onOff() }
        }
    }
}

/**
 * Player settings (approved): Subtitles (the one online language) · Audio · Subtitle font · Subtitle size ·
 * Skip forward · Skip back · Display format; series also: Autoplay next episode, Skip intro, Skip recap.
 * Every choice applies at once; one list at a time, Back goes one step out.
 */
@Composable
fun PlayerSettings(series: Boolean, audio: String?, resize: Int, onDismiss: () -> Unit, onAudio: () -> Unit, onFormat: () -> Unit) {
    var sub by remember { mutableStateOf("") }
    var rev by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val rr = rev
    when (sub) {
        "" -> Popup("", onDismiss, width = 760.dp) {
            OptionRow("Titlovi", R.drawable.lc_message_square, value = Langs.label(SmartSubs.lang())) { sub = "lang" }
            OptionRow("Audio", R.drawable.lc_headphones, value = audio ?: "—") { onAudio() }
            OptionRow("Font titlova", R.drawable.lc_type, value = SubStyle.FONTS[SubStyle.font()].first) { sub = "font" }
            OptionRow("Veličina titlova", R.drawable.lc_type, value = tr(SubStyle.SIZES[SubStyle.size()])) { sub = "size" }
            if (series) {
                OptionRow("Automatski sljedeća epizoda", R.drawable.lc_play, value = tr(if (Prefs.bool("next_ep", true)) "Uključeno" else "Isključeno")) {
                    Prefs.put("next_ep", !Prefs.bool("next_ep", true)); rev++ }
                val intro = Prefs.long("skip_intro", 0).toInt() != 2
                OptionRow("Preskoči uvod (Intro)", R.drawable.lc_rotate_cw, value = tr(if (intro) "Uključeno" else "Isključeno")) {
                    Prefs.put("skip_intro", if (intro) 2L else 1L); rev++ }
                val recap = Prefs.long("skip_recap", 0).toInt() != 2
                OptionRow("Preskoči sažetak (Recap)", R.drawable.lc_rotate_cw, value = tr(if (recap) "Uključeno" else "Isključeno")) {
                    Prefs.put("skip_recap", if (recap) 2L else 1L); rev++ }
            }
            OptionRow("Preskakanje naprijed", R.drawable.lc_rotate_cw, value = trf("+%s s", Prefs.long("skip_fwd", 10))) { sub = "fwd" }
            OptionRow("Preskakanje nazad", R.drawable.lc_rotate_ccw, value = trf("−%s s", Prefs.long("skip_back", 10))) { sub = "back" }
            OptionRow("Format prikaza", R.drawable.lc_rectangle_horizontal, value = tr(VOD_FORMATS.firstOrNull { it.first == resize }?.second ?: "Automatski")) { onFormat() }
        }
        "lang" -> Popup("Titlovi", { sub = "" }) {
            T("Jezik u kojem se titlovi traže online. Titlovi providera ostaju svi dostupni.", 13.sp, C.Soft, maxLines = 3)
            Langs.APP.forEach { l -> OptionRow(l.native, null, selected = SmartSubs.lang() == l.code) { SmartSubs.setLang(l.code); sub = "" } }
        }
        "font" -> Popup("Font titlova", { sub = "" }) {
            SubStyle.FONTS.forEachIndexed { i, (n, _) -> OptionRow(n, R.drawable.lc_type, selected = SubStyle.font() == i) { Prefs.put("sub_font", i.toLong()); SubStyle.rev++; sub = "" } }
        }
        "size" -> Popup("Veličina titlova", { sub = "" }) {
            SubStyle.SIZES.forEachIndexed { i, n -> OptionRow(n, R.drawable.lc_type, selected = SubStyle.size() == i) { Prefs.put("sub_size", i.toString()); SubStyle.rev++; sub = "" } }
        }
        "fwd", "back" -> Popup(if (sub == "fwd") "Preskakanje naprijed" else "Preskakanje nazad", { sub = "" }) {
            val k = if (sub == "fwd") "skip_fwd" else "skip_back"
            listOf(5L, 10L, 15L, 30L).forEach { n -> OptionRow(trf("%s s", n), null, selected = Prefs.long(k, 10) == n) { Prefs.put(k, n); sub = "" } }
        }
    }
}
