package com.pretorian.tv

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
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
    /** every language set in Settings › Subtitles, in that order (English and Croatian by default) */
    fun langs(): List<String> = Prefs.subLangs.split(',').map { it.trim().lowercase() }.filter { it.length == 2 }.distinct().ifEmpty { listOf(Look.lang) }
    private fun key(search: String, season: Int?, episode: Int?) = cleanTitle(search).first.lowercase() + "|" + (season ?: -1) + "|" + (episode ?: -1) + "|" + langs().joinToString(",")

    fun state(search: String, season: Int?, episode: Int?): State? = cache[key(search, season, episode)]

    /** starts the search once per title (on the film page already, so the player has it ready); never blocks playback */
    suspend fun prepare(search: String, season: Int?, episode: Int?): State {
        val k = key(search, season, episode)
        cache[k]?.let { if (it !is State.Searching) return it }
        cache[k] = State.Searching; rev++
        val ls = langs()
        val st = try {
            val all = Subs.search(search, season, episode).filter { it.lang in ls }
            // the same release from two sources counts once; the languages keep the order they were set in
            val uniq = all.distinctBy { it.lang + "|" + Sports.norm(it.release.substringBeforeLast('.')) }.sortedBy { ls.indexOf(it.lang) }
            if (uniq.isEmpty()) State.None else State.Found(uniq)
        } catch (e: Exception) { State.None }
        cache[k] = st; rev++
        return st
    }
    fun forget() { cache.clear(); rev++ }

    /** downloaded files, so a version is fetched only once (also the V1 fetched in the background on the film page) */
    private val files = java.util.concurrent.ConcurrentHashMap<String, String>()
    fun fileOf(r: SubResult): java.io.File? = files[r.provider + "|" + r.ref]?.let { java.io.File(it) }?.takeIf { it.exists() }

    /** downloads one version and names it neatly: "Title (V1).en.srt" — never the release or file name of the source */
    suspend fun get(ctx: android.content.Context, r: SubResult, title: String, version: Int): java.io.File {
        fileOf(r)?.let { return it }
        val f = Subs.download(ctx, r)
        val clean = cleanTitle(title).first.replace(Regex("[^\\p{L}\\p{N} ]"), "").trim().take(40).ifBlank { "Subtitle" }  // i18n-ignore
        val nice = java.io.File(f.parentFile, "$clean (V$version).${r.lang}.srt")
        val out = if (nice.exists() || f.renameTo(nice)) nice else f
        files[r.provider + "|" + r.ref] = out.path
        return out
    }

    /** film page: finds the online subtitles and downloads V1 in the background, so the player has it at once */
    suspend fun prefetch(ctx: android.content.Context, search: String, season: Int?, episode: Int?) {
        val st = prepare(search, season, episode)
        if (st is State.Found) try { get(ctx, st.list.first(), search, 1) } catch (_: Exception) {}
    }
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
/** Provider subtitles show only the language; several of the same language become "English (V1)", "English (V2)". */
fun subTrackNames(tracks: List<TrackItem>): List<String> {
    val langs = tracks.map { t ->
        val f = try { t.group.getTrackFormat(t.index) } catch (_: Exception) { null }
        iso2(f?.language)?.let { Langs.label(it) } ?: tr("Nepoznat jezik")
    }
    val seen = HashMap<String, Int>()
    return langs.map { l -> if (langs.count { it == l } > 1) { val n = (seen[l] ?: 0) + 1; seen[l] = n; "$l (V$n)" } else l }
}

@Composable
fun SubsPanel(item: PlayItem, tracks: List<TrackItem>, current: String?, online: Boolean = true, onDismiss: () -> Unit, onOff: () -> Unit, onTrack: (TrackItem) -> Unit, onFile: (String) -> Unit) {
    val search = item.search ?: item.title
    val chosen = remember { mutableStateOf(Prefs.str("subpick_" + item.url.hashCode())) }
    val names = subTrackNames(tracks)
    SubsMenu(search, item.season, item.episode, online, names, { i -> current == null && tracks[i].selected },
        offOn = current == null && tracks.none { it.selected }, onOff = onOff, onProvider = { i -> onTrack(tracks[i]) },
        onlineOn = { id -> current != null && chosen.value == id }, onDismiss = onDismiss) { id, f ->
        Prefs.put("subpick_" + item.url.hashCode(), id); chosen.value = id; onFile(f.path)
    }
}

/**
 * The one Subtitles menu (redesign spec 03), the same on the movie / series view and in the player:
 * a language selector top right (default English, remembered), PROVIDER (Off + the stream's real tracks),
 * then ONLINE · language with "Eng ver.1", "Eng ver.2" … A ✓ only after the file really loaded; the film never restarts.
 */
@Composable
fun SubsMenu(search: String, season: Int?, episode: Int?, online: Boolean, provider: List<String>, providerOn: (Int) -> Boolean,
             offOn: Boolean, onOff: () -> Unit, onProvider: (Int) -> Unit, onlineOn: (String) -> Boolean, onDismiss: () -> Unit,
             onOnline: (String, java.io.File) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    @Suppress("UNUSED_VARIABLE") val r = SmartSubs.rev
    LaunchedEffect(search) { if (online && SmartSubs.state(search, season, episode) == null) SmartSubs.prepare(search, season, episode) }
    val langs = SmartSubs.langs()
    var lang by remember { mutableStateOf(Prefs.str("sub_menu_lang", "en").let { if (it in langs) it else if ("en" in langs) "en" else langs.first() }) }
    var shown by remember { mutableIntStateOf(3) }
    var busy by remember { mutableStateOf<String?>(null) }
    Popup("", onDismiss, width = 460.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            T("Titlovi", 16.sp, Color.White, FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 6.dp))
            // language selector: OK switches to the next of your subtitle languages
            if (online && langs.size > 1) Focusable(onClick = {
                lang = langs[(langs.indexOf(lang) + 1) % langs.size]; Prefs.put("sub_menu_lang", lang); shown = 3
            }, shape = Rnd(16.dp), normal = Color(0x1AFFFFFF), focused = Dawn.pill, zoom = 1f, modifier = Modifier.height(34.dp)) { _ ->
                Row(Modifier.align(Alignment.Center).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    T(Langs.label(lang), 13.sp, Color.White, FontWeight.SemiBold)
                    Ico(R.drawable.lc_chevron_down, 14.dp, Color.White)
                }
            }
        }
        MenuHead(tr("Provider").uppercase())
        MenuRow(tr("Isključeno"), on = offOn) { onOff() }
        provider.forEachIndexed { i, n -> MenuRow(n, on = providerOn(i)) { onProvider(i) } }
        if (online) {
            MenuHead(tr("Online").uppercase() + " · " + Langs.label(lang))
            when (val st = SmartSubs.state(search, season, episode)) {
                null, SmartSubs.State.Searching -> MenuRow(tr("Tražim titlove…")) {}
                SmartSubs.State.None -> MenuRow(tr("Nije dostupno")) {}
                is SmartSubs.State.Found -> {
                    val rs = st.list.filter { it.lang == lang }
                    if (rs.isEmpty()) MenuRow(tr("Nije dostupno")) {}
                    rs.take(shown).forEachIndexed { k, res ->
                        val id = res.provider + "|" + res.ref
                        MenuRow(subShort(lang) + " ver." + (k + 1), on = onlineOn(id), sub = if (busy == id) tr("Preuzimam…") else null) {  // i18n-ignore
                            if (busy != null) return@MenuRow
                            busy = id
                            scope.launch {
                                try { val f = SmartSubs.get(ctx, res, search, k + 1); onOnline(id, f) }
                                catch (e: Exception) { Toast.makeText(ctx, tr("Titl se nije mogao preuzeti, probaj drugu verziju"), Toast.LENGTH_SHORT).show() }
                                busy = null
                            }
                        }
                    }
                    if (rs.size > shown) MenuRow(tr("Pronađi još titlova")) { shown += 3 }
                }
            }
        }
    }
}

/**
 * Player settings (approved): Subtitles (the one online language) · Audio · Subtitle font · Subtitle size ·
 * Skip forward · Skip back · Display format; series also: Autoplay next episode, Skip intro, Skip recap.
 * Every choice applies at once; one list at a time, Back goes one step out.
 */
@Composable
fun PlayerSettings(series: Boolean, audio: String?, resize: Int, onDismiss: () -> Unit, onAudio: () -> Unit, onFormat: () -> Unit,
                   onSubDelay: (() -> Unit)? = null, onAudioDelay: (() -> Unit)? = null, audioDelayLabel: String? = null) {
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
            onSubDelay?.let { f -> OptionRow("Pomak titla", R.drawable.lc_clock, sub = "ako titl kasni ili žuri") { f() } }
            onAudioDelay?.let { f -> OptionRow("Pomak zvuka", R.drawable.lc_volume, value = audioDelayLabel) { f() } }
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
