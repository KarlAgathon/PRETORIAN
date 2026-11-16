package com.pretorian.tv

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* ============================================================== shared bits of the film and series pages */

private val LampGreen = Color(0xFF3DDC84)
private val LampYellow = Color(0xFFFFC53D)
private val LampRed = Color(0xFFFF5A5F)
private val LampBlue = Color(0xFF4AA3FF)
private val LampGrey = Color(0xFF8A8F9E)
private val LampAmber = Color(0xFFFFB02E)

/** Which series page should open on which episode (from Home, a note, or after the player). */
object SeriesJump { var id = ""; var season = -1; var num = -1 }

/** "the western wind" → "The Western Wind"; titles with our letters (č, ć, š…) stay as they are. */
fun titleCase(s: String): String {
    if (s.any { it.code > 127 }) return s
    return s.split(' ').joinToString(" ") { w -> if (w.isEmpty()) w else w[0].uppercaseChar() + w.substring(1) }
}

/** "S2 · E5" */
fun epCode(s: Int, e: Int) = listOf("S", s, " · E", e).joinToString("")

/** 118 → "1 h 58 min", 52 → "52 min" */
fun fmtMin(min: Int): String = if (min <= 0) "" else if (min >= 60) "${min / 60} h ${"%02d".format(min % 60)} min" else "$min min"  // i18n-ignore

/** "01:58:00", "118 min", "118" → minutes */
fun parseMin(s: String?): Int {
    if (s.isNullOrBlank()) return 0
    val p = s.split(':').mapNotNull { it.trim().toIntOrNull() }
    if (p.size == 3) return p[0] * 60 + p[1]
    return Regex("\\d+").find(s)?.value?.toIntOrNull() ?: 0
}

@Composable
private fun Lamp(c: Color, size: Dp = 10.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(c))
}

/** Small red-and-black play mark for the Trailer button. */
@Composable
private fun TubeMark() {
    Box(Modifier.width(22.dp).height(15.dp).clip(Rnd(4.dp)).background(Color(0xFFFF0033)), contentAlignment = Alignment.Center) {
        Ico(R.drawable.lc_play, 9.dp, Color.White)
    }
}

/** Pill button with any leading mark. */
@Composable
private fun DBtn(text: String, primary: Boolean = false, fr: FocusRequester? = null, lead: (@Composable (Color) -> Unit)? = null, onClick: () -> Unit) {
    Focusable(onClick = onClick, focusRequester = fr, shape = Pill, normal = if (primary) C.Text else Color.White.copy(alpha = 0.08f),
        focused = if (primary) C.Text else C.Red, zoom = 1.05f,
        modifier = Modifier.height(40.dp).then(if (primary) Modifier else Modifier.border(1.dp, C.Line, Pill))) { f ->
        Row(Modifier.align(Alignment.Center).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val fg = if (primary) C.Bg else if (f) C.OnRed else C.Text
            lead?.invoke(fg)
            T(text, 14.sp, fg, FontWeight.Bold)
        }
    }
}

@Composable
private fun DBadge(t: String, bg: Color = Color(0xB3000000), fg: Color = Color.White) {
    Box(Modifier.clip(Rnd(6.dp)).background(bg).padding(horizontal = 6.dp, vertical = 2.dp)) { T(t, 10.sp, fg, FontWeight.Bold) }
}

/** Poster with the rating in the corner and the quality marks. */
@Composable
private fun BigPoster(url: String?, key: String, rating: String?, marks: List<String>, w: Dp) {
    Box(Modifier.width(w).aspectRatio(2f / 3f).clip(Rnd(16.dp)).border(1.dp, C.Line, Rnd(16.dp))) {
        OnyxImage(url, key, Modifier.fillMaxSize(), maxW = 420)
        if (marks.isNotEmpty()) Row(Modifier.align(Alignment.TopStart).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) { marks.forEach { DBadge(it) } }
        rating?.let { Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) { DBadge("★ $it", fg = Color(0xFFFFC941)) } }
    }
}

@Composable
private fun Cast(people: List<Person>) {
    val cast = people.filter { it.role != "Režija" && it.role != "Autor" }.take(10)
    if (cast.isEmpty()) return
    Kicker("Glumci", C.Soft)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(cast) { p ->
            Column(Modifier.width(74.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(C.CardHi).border(1.dp, C.Line, CircleShape), contentAlignment = Alignment.Center) {
                    if (p.photo != null) AsyncImage(model = p.photo, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else T(p.name.split(' ').mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString(""), 14.sp, C.Soft, FontWeight.Bold)
                }
                T(p.name, 11.sp, C.Text, modifier = Modifier.padding(top = 4.dp))
                T(p.role, 9.sp, C.Muted)
            }
        }
    }
}

/** Audio, subtitle and "find a subtitle" at the bottom of the film page; each shows where it comes from. */
@Composable
private fun TrackBar(search: String, season: Int?, episode: Int?, url: String, choice: TrackChoice, onChange: (TrackChoice) -> Unit) {
    var showSubs by remember { mutableStateOf(false) }
    var showFind by remember { mutableStateOf(false) }
    var showAudio by remember { mutableStateOf(false) }
    val audioText = choice.audio?.title?.substringBefore(" · ")
        ?: if (Prefs.bool("audio_default")) tr("Od providera") else Prefs.audioLangs.split(',').firstOrNull { it.isNotBlank() }?.let { Langs.label(it) } ?: tr("Od providera")
    val (subText, src) = when {
        choice.subOff -> tr("Bez titla") to null
        choice.sub != null -> choice.sub.title.substringBefore(" · ") to ("Provider" to LampGreen)
        choice.subFile != null -> {
            val l = choice.subFile.nameWithoutExtension.substringAfterLast('_')
            if (l == "xx") tr("Titl s diska") to ("Disk" to LampAmber) else Langs.label(l) to ("Online" to LampBlue)
        }
        Prefs.autoSubs -> (Prefs.subLangs.split(',').firstOrNull { it.isNotBlank() }?.let { Langs.label(it) } ?: tr("Automatski")) to ("Provider" to LampGreen)
        else -> tr("Isključen") to null
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BarBox("ZVUK", audioText, R.drawable.lc_volume, null, Modifier.weight(1f)) { showAudio = true }
        BarBox("TITL", subText, R.drawable.lc_captions, src, Modifier.weight(1f)) { showSubs = true }
        BarBox("ONLINE · DISK", tr("Traži titl"), R.drawable.lc_search, null, Modifier.weight(1f)) { showFind = true }
    }
    // "Traži titl": only two ways — search all subtitle sites online, or load a file from the disk / USB
    var findMode by remember { mutableStateOf<String?>(null) }
    if (showFind && findMode == null) Popup("Traži titl", { showFind = false }, width = 560.dp) {
        OptionRow("Traži online", R.drawable.lc_search, sub = "Pretraži sve stranice s titlovima i izaberi") { findMode = "results" }
        OptionRow("Učitaj s diska / USB-a", R.drawable.lc_folder, sub = ".srt · .ass · .vtt · .sub") { findMode = "disk" }
    }
    fun picked(f: java.io.File?) {
        onChange(if (f == null) choice.copy(subFile = null, sub = null, subOff = true) else choice.copy(subFile = f, sub = null, subOff = false))
        showSubs = false; showFind = false; findMode = null
    }
    findMode?.let { fm -> key(fm) { SubtitlePicker(search, season, episode, probeUrl = url, startMode = fm, onDismiss = { findMode = null }) { f -> picked(f) } } }
    if (showSubs) ProviderSubsPicker(url, choice.sub?.title, choice.subOff, onDismiss = { showSubs = false },
        onOff = { onChange(choice.copy(sub = null, subFile = null, subOff = true)); showSubs = false }) { t ->
        onChange(choice.copy(sub = t, subFile = null, subOff = false)); showSubs = false
    }
    if (showAudio) AudioPicker(url, choice.audio?.title, onDismiss = { showAudio = false }) { t -> onChange(choice.copy(audio = t)); showAudio = false }
}

@Composable
private fun BarBox(label: String, value: String, icon: Int, src: Pair<String, Color?>?, modifier: Modifier, onClick: () -> Unit) {
    Focusable(onClick = onClick, shape = Rnd(16.dp), normal = Color.Black.copy(alpha = 0.28f), focused = Color.White.copy(alpha = 0.16f), zoom = 1.02f,
        modifier = modifier.height(58.dp).border(1.dp, C.Line, Rnd(16.dp))) { f ->
        Row(Modifier.fillMaxSize().then(if (f) Modifier.border(2.dp, Color.White, Rnd(16.dp)) else Modifier).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(34.dp).clip(Rnd(10.dp)).background(Color.White.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { Ico(icon, 17.dp, C.Text) }
            Column(Modifier.weight(1f)) {
                Kicker(label, C.Muted, size = 9.sp)
                T(value, 14.sp, C.Text, FontWeight.Bold)
            }
            if (src != null) Row(Modifier.clip(Pill).background((src.second ?: C.Text).copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (src.second != null) Lamp(src.second!!, 7.dp)
                T(src.first, 10.sp, src.second ?: C.Soft, FontWeight.Bold)
            }
            T("▾", 12.sp, C.Muted)
        }
    }
}

fun platformOf(id: String, m: Meta?) = Platforms.norm(VodMemory.catOf[id]) ?: m?.studio

/** The streaming platform as a coloured chip: from the provider category name, else what TMDB says; nothing when unknown. */
@Composable
private fun PlatformChip(id: String, m: Meta?, size: androidx.compose.ui.unit.TextUnit, name: String = "", tv: Boolean = false) {
    // TMDB does not know it: ask the AI once (when a key is set); nothing is shown when it is not sure either
    val ai by load(id, m?.studio, name) { if (platformOf(id, m) == null && m != null && name.isNotBlank()) SportsAI.platform(cleanTitle(name).first, m.year, tv) else null }
    val label = platformOf(id, m) ?: (ai as? Load.Ok)?.value ?: return
    Box(Modifier.clip(Rnd(5.dp)).background(Color(Platforms.color(label))).padding(horizontal = 7.dp, vertical = 2.dp)) {
        T(label.uppercase(), size, Color.White, FontWeight.ExtraBold)  // i18n-ignore
    }
}

/* ============================================================== film page */

/** Film page: big poster with the rating, title, "year – length – genre – studio", story, buttons, cast; audio and subtitle at the bottom. */
@Composable
fun OnyxMovieDetail(v: Vod, nav: Nav) {
    val ctx = LocalContext.current
    val p = remember { Store.active(ctx) } ?: return
    val info by load(v.id) { Repo.movieInfo(p, v.id) }
    val meta by load(v.name, Prefs.tmdbKey) { Tmdb.find(v.name, tv = false) }
    var choice by remember { mutableStateOf(TrackChoice()) }
    var ext by remember { mutableStateOf(false) }
    val playFr = remember { FocusRequester() }
    LaunchedEffect(v.id) { NewContent.seen(p.id, "vod", v.id) }
    val i = (info as? Load.Ok)?.value
    val m = (meta as? Load.Ok)?.value
    // neither TMDB nor the provider has one: a free source (Wikipedia)
    val needFree = m?.overview == null && i?.plot == null && meta !is Load.Loading && info !is Load.Loading
    // no TMDB: cast and director from the free card too (also when the provider has a story)
    val needCard = m == null && meta !is Load.Loading && info !is Load.Loading && (needFree || i?.cast == null)
    val free by load(v.name, needCard) { if (needCard) FreeMeta.movieFull(v.name) else null }
    val fr = (free as? Load.Ok)?.value
    val freeRow = fr
    val title = titleCase(m?.title ?: cleanTitle(v.name).first)
    val pr = remember(Watch.rev) { Watch.get(ctx, v.url) }
    fun play(fromStart: Boolean = false) {
        if (fromStart) Watch.remove(ctx, v.url)
        if (ExtPlayer.always) openExternalOrAsk(ctx, v.url, title, if (fromStart) 0 else pr?.pos ?: 0) { ext = true }
        else nav.push(Screen.Player(listOf(choice.apply(PlayItem(title, v.url, v.poster, null, null, v.name))), 0, live = false))
    }
    val mins = m?.runtimeMin?.takeIf { it > 0 } ?: parseMin(i?.duration)
    val rating = m?.imdbRating ?: (i?.rating ?: v.rating)?.toDoubleOrNull()?.takeIf { it > 0 }?.let { String.format("%.1f", it) }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val pw = ((maxHeight - 110.dp) * (2f / 3f)).coerceIn(150.dp, 280.dp)
        Column(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 6.dp, bottom = 12.dp)) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                // picture and sound marks: 4K, HDR, 5.1, Dolby Atmos (from the provider's file info, else from the name)
                val marks = (i?.marks ?: emptyList()).ifEmpty { listOfNotNull(qualityOf(v.name)) }
                BigPoster(m?.poster ?: v.poster, v.id, rating, marks.filter { it in listOf("4K", "FHD", "HD", "HDR") }, pw)  // i18n-ignore
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Kicker("Film", C.Soft, size = 11.sp)
                    T(title, 32.sp, C.Text, FontWeight.ExtraBold, maxLines = 2)
                    // year – length – genre – studio
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val parts = listOfNotNull((m?.year ?: i?.year ?: freeRow?.year)?.take(4), fmtMin(mins).ifBlank { freeRow?.runtime }, translated(m?.genres ?: i?.genre ?: freeRow?.genre)?.take(40))
                        val plat = platformOf(v.id, m)
                        T(parts.joinToString(" – ") + if (plat != null) " –" else "", 14.sp, C.Soft, FontWeight.SemiBold)
                        PlatformChip(v.id, m, 11.sp, v.name)
                    }
                    val sound = (i?.marks ?: emptyList()).filter { it !in listOf("4K", "FHD", "HD") }  // i18n-ignore
                    if (sound.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        sound.forEach { DBadge(it, bg = Color.White.copy(alpha = 0.12f)) }
                    }
                    val plot = translated(m?.overview ?: i?.plot ?: fr?.plot)
                    T(plot ?: if (meta is Load.Loading || info is Load.Loading || free is Load.Loading) "Učitavam opis…" else tr("Opis nije dostupan."), 14.sp, C.Soft, maxLines = 7)
                    val dir = m?.people?.firstOrNull { it.role == "Režija" }?.name ?: i?.director ?: fr?.director
                    dir?.let { T(tr("Režija") + ": " + it, 12.sp, C.Muted) }
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val started = pr != null && pr.started
                        val left = if (started && pr!!.dur > 0) ((pr.dur - pr.pos) / 60_000).toInt().coerceAtLeast(1) else 0
                        DBtn(if (started) trf("Nastavi · još %s min", left) else tr("Pokreni"), primary = true, fr = playFr, lead = { c -> Ico(R.drawable.lc_play, 14.dp, c) }) { play() }
                        if (started) DBtn(tr("Od početka"), lead = { c -> Ico(R.drawable.lc_rotate_ccw, 14.dp, c) }) { play(fromStart = true) }
                        DBtn(tr("Eksterni plejer"), lead = { c -> Ico(R.drawable.lc_external_link, 14.dp, c) }) { openExternalOrAsk(ctx, v.url, title, pr?.pos ?: 0) { ext = true } }
                        DBtn(tr("Trailer"), lead = { _ -> TubeMark() }) { openTrailer(ctx, v.name, m?.trailerKey) }
                        val on = remember(MyList.rev) { MyList.has("vod", v.id) }
                        DBtn(if (on) tr("✓ Ukloni s liste") else tr("+ Moja lista")) { MyList.toggle("vod", v.id, v.name, v.poster, v.url, p.id) }
                    }
                    if (pr != null && pr.started) ThinBar(pr.fraction, C.Text, Modifier.width(320.dp), 3.dp)
                    m?.people?.let { Cast(it) } ?: (i?.cast ?: fr?.cast)?.let { T(tr("Glumci") + ": " + it, 12.sp, C.Muted, maxLines = 2) }
                }
            }
            Spacer(Modifier.height(10.dp))
            TrackBar(v.name, null, null, v.url, choice) { choice = it }
        }
    }
    if (ext) ExternalPicker(v.url, title, pr?.pos ?: 0, onDismiss = { ext = false })
    LaunchedEffect(Unit) { delay(60); playFr.tryFocus() }
}

/* ============================================================== series page */

/** One line of the episode list: an episode the provider has, or one TMDB knows that is not here (yet). */
private data class EpRow(val e: Episode?, val t: TmdbEp?, val season: Int, val num: Int)

/** Series page: facts on top, seasons as pills, episodes as rows with progress, length and a coloured lamp. */
@Composable
fun OnyxSeriesDetail(s: SeriesItem, nav: Nav) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val p = remember { Store.active(ctx) } ?: return
    val eps by load(s.id) { Repo.episodes(p, s.id) }
    val meta by load(s.name, Prefs.tmdbKey) { Tmdb.find(s.name, tv = true) }
    val m = (meta as? Load.Ok)?.value
    val all = (eps as? Load.Ok)?.value ?: emptyList()
    val tmdbSeasons by load(m?.tmdbId ?: 0) { Tmdb.seasons(m?.tmdbId ?: 0) }
    val jump = remember { if (SeriesJump.id == s.id) SeriesJump.season to SeriesJump.num else null }
    var season by remember { mutableIntStateOf(jump?.first ?: -1) }
    var choice by remember { mutableStateOf(TrackChoice()) }
    var epDlg by remember { mutableStateOf<EpRow?>(null) }
    var seasonMenu by remember { mutableIntStateOf(-1) }
    var ext by remember { mutableStateOf<Episode?>(null) }
    var lastFocusNum by remember { mutableIntStateOf(-1) }
    var followRev by remember { mutableIntStateOf(0) }
    val playFr = remember { FocusRequester() }
    val jumpFr = remember { FocusRequester() }
    val listState = rememberLazyListState()
    LaunchedEffect(s.id) { NewContent.seen(p.id, "series", s.id); Follow.seen(s.id) }
    val title = titleCase(m?.title ?: cleanTitle(s.name).first)
    val seasons = remember(all, tmdbSeasons) {
        (all.map { it.season } + ((tmdbSeasons as? Load.Ok)?.value ?: emptyList())).distinct().sorted()
    }
    // where to continue: the started episode, else the first not watched
    val next = remember(all, Watch.rev) { all.firstOrNull { Watch.get(ctx, it.url)?.started == true } ?: all.firstOrNull { Watch.get(ctx, it.url)?.watched != true } ?: all.firstOrNull() }
    val cur = if (season in seasons) season else next?.season ?: seasons.firstOrNull() ?: 1
    val tmdbEps by load(m?.tmdbId ?: 0, cur) { Tmdb.season(m?.tmdbId ?: 0, cur) }
    val tEps = (tmdbEps as? Load.Ok)?.value ?: emptyList()
    val rows = remember(all, tEps, cur) {
        val have = all.filter { it.season == cur }
        val extra = tEps.filter { t -> have.none { it.num == t.num } }
        (have.map { e -> EpRow(e, tEps.firstOrNull { it.num == e.num }, cur, e.num) } + extra.map { EpRow(null, it, cur, it.num) }).sortedBy { it.num }
    }
    fun epTitle(e: Episode) = "$title · S${e.season}E${e.num}"
    fun itemsOf(list: List<Episode>) = list.map {
        choice.apply(PlayItem(epTitle(it), it.url, s.cover, episodeTitle(it.title, it.num), null, s.name, it.season, it.num))
    }
    fun playEp(e: Episode, fromStart: Boolean = false) {
        if (fromStart) Watch.remove(ctx, e.url)
        if (!Follow.isOn(s.id)) Follow.toggle(p.id, s, all.size)   // a series you watch is followed for new episodes
        SeriesJump.id = s.id; SeriesJump.season = e.season; SeriesJump.num = e.num   // Back from the player lands on this episode
        val list = all.filter { it.season == e.season }
        val pr = Watch.get(ctx, e.url)
        if (ExtPlayer.always) openExternalOrAsk(ctx, e.url, epTitle(e), if (fromStart) 0 else pr?.pos ?: 0) { ext = e }
        else nav.push(Screen.Player(itemsOf(list), list.indexOf(e).coerceAtLeast(0), live = false))
    }
    fun markSeason(n: Int, on: Boolean, upTo: Int = Int.MAX_VALUE) {
        all.filter { it.season == n }.forEach { x ->
            Watch.setWatched(ctx, x.url, epTitle(x), s.cover, on && x.num <= upTo)
        }
    }
    val today = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()) }  // i18n-ignore
    fun fmtDate(d: String?) = d?.let { try { java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.US).format(java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(it)!!) } catch (e: Exception) { it } } ?: ""  // i18n-ignore

    Column(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 4.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // ---- the facts
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            val rating = m?.imdbRating ?: s.rating?.toDoubleOrNull()?.takeIf { it > 0 }?.let { String.format("%.1f", it) }
            BigPoster(m?.poster ?: s.cover, s.id, rating, emptyList(), 112.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Kicker("Serija", C.Soft, size = 11.sp)
                T(title, 28.sp, C.Text, FontWeight.ExtraBold)
                // no TMDB: story, year, genre, cast and director from the free card (Cinemeta / TVmaze)
                val needFree = m == null && meta !is Load.Loading
                val free by load(s.name, needFree) { if (needFree) FreeMeta.seriesFull(s.name) else null }
                val fr = (free as? Load.Ok)?.value
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val parts = listOfNotNull((m?.year ?: fr?.year)?.take(4), if (seasons.isNotEmpty()) trf("%s sezone", seasons.size) + " · " + trf("%s epizoda", all.size) else null,
                        translated(m?.genres ?: fr?.genre)?.take(40))
                    val plat = platformOf(s.id, m)
                    T(parts.joinToString(" – ") + if (plat != null) " –" else "", 13.sp, C.Soft, FontWeight.SemiBold)
                    PlatformChip(s.id, m, 10.sp, s.name, tv = true)
                }
                val story = listOfNotNull(m?.overview ?: s.plot?.ifBlank { null } ?: fr?.plot).firstOrNull()
                T(translated(story) ?: if (free is Load.Loading) tr("Učitavam opis…") else " ", 12.sp, C.Soft, maxLines = 3)
                if (m == null) listOfNotNull(fr?.director?.let { tr("Režija") + ": " + it }, fr?.cast?.let { tr("Glumci") + ": " + it }).joinToString("  ·  ").ifBlank { null }?.let {  // i18n-ignore
                    T(it, 11.sp, C.Muted, maxLines = 1) }
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val np = next?.let { Watch.get(ctx, it.url) }
                    val left = if (np?.started == true && np.dur > 0) ((np.dur - np.pos) / 60_000).toInt().coerceAtLeast(1) else 0
                    val label = when {
                        next == null -> tr("Pokreni")
                        np?.started == true -> trf("Nastavi S%s E%s", next.season, next.num) + " · " + trf("još %s min", left)
                        else -> trf("Gledaj S%s E%s", next.season, next.num)
                    }
                    DBtn(label, primary = true, fr = playFr, lead = { c -> Ico(R.drawable.lc_play, 14.dp, c) }) { next?.let { playEp(it) } }
                    DBtn(tr("Trailer"), lead = { _ -> TubeMark() }) { openTrailer(ctx, s.name, m?.trailerKey) }
                    val on = remember(MyList.rev) { MyList.has("series", s.id) }
                    DBtn(if (on) tr("✓ Ukloni s liste") else tr("+ Moja lista")) { MyList.toggle("series", s.id, s.name, s.cover, null, p.id) }
                    key(followRev) {
                        val f = Follow.isOn(s.id)
                        DBtn(if (f) tr("Podsjeti me na nove epizode · uključeno") else tr("Podsjeti me na nove epizode"), lead = { c -> Ico(R.drawable.lc_bell, 14.dp, if (f) LampYellow else c) }) {
                            Follow.toggle(p.id, s, all.size); followRev++
                        }
                    }
                }
            }
        }
        when (val st = eps) {
            is Load.Loading -> Loading("Učitavam epizode…")
            is Load.Err -> Message("Epizode se nisu učitale", st.message)
            is Load.Ok -> if (all.isEmpty()) Message("Nema epizoda") else {
                // ---- seasons: the open one is white; long OK marks the whole season
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(seasons) { n ->
                            val count = all.count { it.season == n }
                            val seen = all.count { it.season == n && Watch.get(ctx, it.url)?.watched == true }
                            val done = count > 0 && seen == count
                            val open = n == cur
                            Focusable(onClick = { season = n }, onMenu = { if (count > 0) seasonMenu = n }, shape = Pill,
                                normal = if (open) Color.White else Color.White.copy(alpha = 0.07f), focused = if (open) Color.White else C.Red, zoom = 1.05f,
                                modifier = Modifier.height(36.dp).border(1.dp, if (open) Color.White else C.Line, Pill)) { f ->
                                Row(Modifier.align(Alignment.Center).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val fg = if (open) Color(0xFF1A1210) else if (f) C.OnRed else C.Soft
                                    T(trf("Sezona %s", n), 14.sp, fg, FontWeight.Bold)
                                    T(if (count > 0) listOf("· ", seen, "/", count, if (done) " ✓" else "").joinToString("") else "· " + tr("uskoro"), 12.sp,
                                        if (done) (if (open) Color(0xFF1E9E5A) else LampGreen) else fg.copy(alpha = 0.75f), FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                // ---- episodes
                val blurOn = Prefs.bool("blur_eps", true)
                key(Watch.rev, cur) {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(5.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                        itemsIndexed(rows, key = { _, r -> "${r.season}_${r.num}_${r.e?.id}" }) { _, r ->
                            val e = r.e
                            val pr = e?.let { Watch.get(ctx, it.url) }
                            val future = e == null && (r.t?.air == null || r.t.air > today)
                            val (lamp, label) = when {
                                e == null && future -> LampGrey to (tr("Još nije izašla") + (r.t?.air?.let { " · " + fmtDate(it) } ?: ""))
                                e == null -> LampBlue to (tr("Izašla") + " " + fmtDate(r.t?.air) + " · " + tr("čeka se provider"))
                                pr?.watched == true -> LampGreen to tr("Odgledano")
                                pr?.started == true -> LampYellow to trf("još %s min", ((pr.dur - pr.pos) / 60_000).toInt().coerceAtLeast(1))
                                else -> LampRed to tr("Nije gledano")
                            }
                            val mins = e?.durSec?.takeIf { it > 0 }?.let { it / 60 } ?: r.t?.runtime?.takeIf { it > 0 } ?: pr?.dur?.takeIf { it > 0 }?.let { (it / 60_000).toInt() } ?: m?.runtimeMin ?: 0
                            val name = e?.let { episodeTitle(it.title, it.num) } ?: r.t?.name ?: trf("Epizoda %s", r.num)
                            val isJump = jump != null && jump.first == r.season && jump.second == r.num
                            Focusable(onClick = { if (e != null) playEp(e) else Toast.makeText(ctx, label, Toast.LENGTH_SHORT).show() },
                                onMenu = { epDlg = r }, onFocus = { lastFocusNum = r.num }, focusRequester = if (isJump) jumpFr else null,
                                shape = Rnd(12.dp), normal = if (e == null) Color.White.copy(alpha = 0.03f) else Color.Black.copy(alpha = 0.25f),
                                focused = Color.White.copy(alpha = 0.14f), zoom = 1.01f,
                                modifier = Modifier.fillMaxWidth().height(40.dp).border(1.dp, C.Line, Rnd(12.dp))) { f ->
                                Row(Modifier.fillMaxSize().then(if (f) Modifier.border(2.dp, Color.White, Rnd(12.dp)) else Modifier).padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    // picture: blurred until watched (no spoilers), a calendar for the ones not out yet
                                    Box(Modifier.height(28.dp).aspectRatio(16f / 9f).clip(Rnd(6.dp)).background(C.CardHi), contentAlignment = Alignment.Center) {
                                        val img = e?.image ?: r.t?.still
                                        if (e == null) Ico(R.drawable.lc_calendar, 16.dp, LampGrey)
                                        else if (img != null) {
                                            val hide = blurOn && pr?.watched != true && pr?.started != true
                                            OnyxImage(img, e.id, Modifier.fillMaxSize().then(if (hide) Modifier.blur(8.dp) else Modifier), maxW = if (hide) 24 else 200)
                                        }
                                        if (f && e != null) Ico(R.drawable.lc_play, 14.dp, Color.White)
                                    }
                                    T(epCode(r.season, r.num), 12.sp, if (e == null) C.Muted else C.Soft, FontWeight.ExtraBold, modifier = Modifier.width(62.dp))  // i18n-ignore
                                    T(name, 14.sp, if (e == null) C.Muted else C.Text, FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Column(Modifier.width(230.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        ThinBar(if (pr?.watched == true) 1f else pr?.fraction ?: 0f, lamp, Modifier.fillMaxWidth(), 3.dp)
                                        T(label, 11.sp, if (lamp == LampRed || lamp == LampGreen) C.Soft else lamp, FontWeight.SemiBold)
                                    }
                                    T(if (mins > 0) fmtMin(mins) else "—", 13.sp, C.Soft, FontWeight.SemiBold, modifier = Modifier.width(56.dp), align = androidx.compose.ui.text.style.TextAlign.End)  // i18n-ignore
                                    if (e == null && future && Follow.isOn(s.id)) Ico(R.drawable.lc_bell, 14.dp, LampYellow) else Lamp(lamp, 11.dp)
                                }
                            }
                        }
                    }
                }
                // audio, subtitle and "Traži titl" for the episode you continue with
                TrackBar(s.name, next?.season ?: cur, next?.num, next?.url ?: "", choice) { choice = it }
            }
        }
    }

    // ---- long OK on a season
    if (seasonMenu > 0) {
        val n = seasonMenu
        val list = all.filter { it.season == n }
        val watched = list.count { Watch.get(ctx, it.url)?.watched == true }
        Popup(trf("Sezona %s", n) + " · " + trf("%s epizoda", list.size) + " · " + trf("%s odgledano", watched), { seasonMenu = -1 }, width = 520.dp) {
            OptionRow("Označi sezonu kao odgledanu", R.drawable.lc_circle_check, color = LampGreen) { markSeason(n, true); seasonMenu = -1 }
            OptionRow("Označi sezonu kao negledanu", R.drawable.lc_circle_slash, color = LampRed) { markSeason(n, false); seasonMenu = -1 }
            if (lastFocusNum > 1 && n == cur) OptionRow(trf("Odgledano do E%s, ostalo negledano", lastFocusNum), R.drawable.lc_eye) {
                markSeason(n, true, upTo = lastFocusNum); seasonMenu = -1
            }
        }
    }
    // ---- long OK on an episode: picture, story and the marks
    epDlg?.let { r -> EpisodeDialog(r, title, s, m, ::fmtDate, onDismiss = { epDlg = null },
        onPlay = { e, fromStart -> epDlg = null; playEp(e, fromStart) },
        onMark = { e, on -> Watch.setWatched(ctx, e.url, epTitle(e), s.cover, on); epDlg = null },
        onExt = { e -> epDlg = null; ext = e }) }
    ext?.let { e -> ExternalPicker(e.url, epTitle(e), Watch.get(ctx, e.url)?.pos ?: 0, onDismiss = { ext = null }) }
    LaunchedEffect(eps is Load.Ok, rows.size) {
        if (eps !is Load.Ok) return@LaunchedEffect
        if (jump != null && jump.first == cur) {
            val idx = rows.indexOfFirst { it.num == jump.second }
            if (idx >= 0) { listState.scrollToItem(idx); delay(80); if (jumpFr.tryFocus()) return@LaunchedEffect }
        }
        playFr.tryFocus()
    }
}

@Composable
private fun EpisodeDialog(r: EpRow, title: String, s: SeriesItem, m: Meta?, fmtDate: (String?) -> String, onDismiss: () -> Unit,
                          onPlay: (Episode, Boolean) -> Unit, onMark: (Episode, Boolean) -> Unit, onExt: (Episode) -> Unit) {
    val ctx = LocalContext.current
    val e = r.e
    val pr = e?.let { Watch.get(ctx, it.url) }
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val armed = remember { booleanArrayOf(false) }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).onPreviewKeyEvent { ev ->
            val ok = ev.key == Key.DirectionCenter || ev.key == Key.Enter || ev.key == Key.NumPadEnter
            if (!ok || armed[0]) return@onPreviewKeyEvent false
            if (ev.type == KeyEventType.KeyDown && ev.nativeKeyEvent.repeatCount == 0) { armed[0] = true; false } else true
        }, contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(0.8f).clip(Rnd(22.dp)).background(C.Overlay).border(1.dp, C.Line, Rnd(22.dp))) {
                Box(Modifier.fillMaxWidth().height(200.dp)) {
                    val img = e?.image ?: r.t?.still ?: m?.backdrop ?: s.cover
                    OnyxImage(img, e?.id ?: "${s.id}_${r.num}", Modifier.fillMaxSize(), maxW = 640)
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, C.Overlay))))
                    val state = when {
                        e == null -> tr("Još nije izašla")
                        pr?.watched == true -> tr("Odgledano")
                        pr?.started == true -> trf("u toku · još %s min", ((pr.dur - pr.pos) / 60_000).toInt().coerceAtLeast(1))
                        else -> tr("Nije gledano")
                    }
                    Box(Modifier.align(Alignment.TopEnd).padding(12.dp)) { DBadge(state, fg = if (pr?.started == true) LampYellow else Color.White) }
                    Kicker(trf("Sezona %s", r.season) + " · " + trf("Epizoda %s", r.num), C.Soft, size = 11.sp, modifier = Modifier.align(Alignment.BottomStart).padding(start = 22.dp, bottom = 8.dp))
                }
                Column(Modifier.padding(start = 22.dp, end = 22.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    T(e?.let { episodeTitle(it.title, it.num) } ?: r.t?.name ?: title, 26.sp, C.Text, FontWeight.ExtraBold, maxLines = 2)
                    val mins = e?.durSec?.takeIf { it > 0 }?.let { it / 60 } ?: r.t?.runtime ?: 0
                    val air = e?.air ?: r.t?.air
                    T(listOfNotNull(if (mins > 0) fmtMin(mins) else null, air?.let { tr("Emitovano") + " " + fmtDate(it) }).joinToString(" · ").ifBlank { title }, 13.sp, C.Muted)  // i18n-ignore
                    val needFree = e?.plot.isNullOrBlank() && r.t?.overview == null
                    val free by load(s.name, r.season, r.num, needFree) { if (needFree) FreeMeta.episode(s.name, r.season, r.num) else null }
                    T(translated(e?.plot?.ifBlank { null } ?: r.t?.overview ?: (free as? Load.Ok)?.value) ?: tr(if (free is Load.Loading) "Učitavam opis…" else "Opis epizode nije dostupan."), 14.sp, C.Soft, maxLines = 5)
                    if (pr != null && pr.started) ThinBar(pr.fraction, LampYellow, Modifier.fillMaxWidth(), 3.dp)
                    if (e != null) Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val started = pr?.started == true
                        DBtn(if (started) trf("Nastavi · još %s min", ((pr!!.dur - pr.pos) / 60_000).toInt().coerceAtLeast(1)) else tr("Pokreni"), primary = true, fr = first,
                            lead = { c -> Ico(R.drawable.lc_play, 14.dp, c) }) { onPlay(e, false) }
                        if (started || pr?.watched == true) DBtn(tr("Od početka"), lead = { c -> Ico(R.drawable.lc_rotate_ccw, 14.dp, c) }) { onPlay(e, true) }
                        if (pr?.watched != true) DBtn(tr("Označi kao odgledano"), lead = { _ -> Lamp(LampGreen) }) { onMark(e, true) }
                        if (pr != null) DBtn(tr("Označi kao negledano"), lead = { _ -> Lamp(LampRed) }) { onMark(e, false) }
                        DBtn(tr("Eksterni plejer"), lead = { c -> Ico(R.drawable.lc_external_link, 14.dp, c) }) { onExt(e) }
                    } else DBtn(tr("Zatvori"), primary = true, fr = first) { onDismiss() }
                }
            }
        }
        LaunchedEffect(Unit) { delay(60); first.tryFocus() }
    }
}
