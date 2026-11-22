package com.pretorian.tv

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.layout.ContentScale
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
private val LampBlue = Silver.TextSoft
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
        OnyxImage(url, key, Modifier.fillMaxSize(), maxW = 900, scale = androidx.compose.ui.layout.ContentScale.Fit, highQuality = true)
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

/** Language name of a track in the app language ("Engleski"), null when the stream does not say. */
fun trackLang(f: androidx.media3.common.Format): String? = iso2(f.language)?.let { c ->
    java.util.Locale(c).getDisplayLanguage(java.util.Locale(Look.lang)).replaceFirstChar { it.uppercase() }.ifBlank { null } }

/** "Engleski (5.1)", "Njemački (Stereo)"; unknown language: "Audio providera". */
fun audioText(l: List<androidx.media3.common.Format>): String {
    if (l.isEmpty()) return tr("Audio providera")
    return l.take(2).joinToString(", ") { f ->
        val ch = when { f.channelCount >= 8 -> "7.1"; f.channelCount >= 6 -> "5.1"; f.channelCount == 2 -> "Stereo"; else -> null }  // i18n-ignore
        (trackLang(f) ?: tr("Audio providera")) + (ch?.let { " ($it)" } ?: "")
    } + if (l.size > 2) " +${l.size - 2}" else ""  // i18n-ignore
}

/** Provider subtitles: none / one language / two–three / "Više". */
fun providerSubsText(l: List<androidx.media3.common.Format>): String {
    val langs = l.map { trackLang(it) ?: tr("Nepoznat") }.distinct()
    return when { langs.isEmpty() -> tr("Nema"); langs.size <= 3 -> langs.joinToString(", "); else -> tr("Više") }
}

@Composable
private fun InfoCell(icon: Int, label: String, value: String, modifier: Modifier) {
    Row(modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Ico(icon, 22.dp, C.Text)
        Column { T(label, 14.sp, C.Text, FontWeight.Bold); T(value, 14.sp, C.Soft, maxLines = 2) }
    }
}

/**
 * Film page (approved): big picture, poster, title, 4K/HDR · year · length · genres, story, exactly three actions
 * (Watch · Trailer · My List), a row Direction · Cast · Audio · Subtitles (provider) · Subtitles (online), and Similar films
 * that are in your playlist. Online subtitles are already searched while you read.
 */
@Composable
fun OnyxMovieDetail(v: Vod, nav: Nav) {
    val ctx = LocalContext.current
    val p = remember { Store.active(ctx) } ?: return
    val info by load(v.id) { Repo.movieInfo(p, v.id) }
    val meta by load(v.name, Prefs.tmdbKey) { Tmdb.find(v.name, tv = false) }
    val probe by load(v.url) { probeTracks(ctx, v.url) }
    val similar by load(v.name, Prefs.tmdbKey) { VodMatch.match(p, false, "sim:" + v.name, Tmdb.similar(v.name)).filter { it.id != v.id }.take(14) }
    @Suppress("UNUSED_VARIABLE") val sr = SmartSubs.rev
    LaunchedEffect(v.name, SmartSubs.lang()) { SmartSubs.prefetch(ctx, v.name, null, null) }
    var ext by remember { mutableStateOf(false) }
    val playFr = remember { FocusRequester() }
    LaunchedEffect(v.id) { NewContent.seen(p.id, "vod", v.id) }
    val i = (info as? Load.Ok)?.value
    val m = (meta as? Load.Ok)?.value
    val title = titleCase(m?.title ?: cleanTitle(v.name).first)
    val pr = remember(Watch.rev) { Watch.get(ctx, v.url) }
    val started = pr != null && pr.started
    fun play() {
        VodMemory.returning = true
        if (ExtPlayer.always) openExternalOrAsk(ctx, v.url, title, if (started) pr!!.pos else 0) { ext = true }
        else nav.push(Screen.Player(listOf(PlayItem(title, v.url, v.poster, null, null, v.name, startAt = if (started) pr!!.pos else 0L)), 0, live = false))
    }
    val mins = m?.runtimeMin?.takeIf { it > 0 } ?: parseMin(i?.duration)
    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B0C))) {
        Box(Modifier.fillMaxHeight(0.78f).fillMaxWidth(0.72f).align(Alignment.TopEnd)) {
            OnyxImage(m?.backdrop ?: v.poster, "bd:" + v.id, Modifier.fillMaxSize(), maxW = 1280, scale = ContentScale.Crop, highQuality = true)
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFF0B0B0C), Color(0x800B0B0C), Color(0x000B0B0C)))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.6f to Color(0x000B0B0C), 1f to Color(0xFF0B0B0C))))
        }
        Column(Modifier.fillMaxSize().padding(start = 40.dp, end = 32.dp, top = 8.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Box(Modifier.width(190.dp).aspectRatio(2f / 3f).clip(Rnd(14.dp)).border(2.dp, Color(0xE6FFFFFF), Rnd(14.dp))) {
                    OnyxImage(m?.poster ?: v.poster, "p:" + v.id, Modifier.fillMaxSize(), maxW = 400)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    T(title, 38.sp, C.Text, FontWeight.Bold, maxLines = 2)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // 4K / HDR only when the file really says so
                        (i?.marks ?: emptyList()).filter { it == "4K" || it == "HDR" }.forEach {  // i18n-ignore
                            Box(Modifier.border(1.5.dp, C.Text, Rnd(6.dp)).padding(horizontal = 7.dp, vertical = 2.dp)) { T(it, 14.sp, C.Text, FontWeight.Bold) }
                        }
                        val parts = listOfNotNull((m?.year ?: i?.year)?.take(4), fmtMin(mins).ifBlank { null }, translated(m?.genres ?: i?.genre)?.replace(", ", " • ")?.take(48))
                        T(parts.joinToString("   |   "), 16.sp, C.Text)
                    }
                    val plot = translated(m?.overview ?: i?.plot)
                    T(plot ?: if (meta is Load.Loading || info is Load.Loading) "Učitavam opis…" else tr("Opis nije dostupan."), 16.sp, C.Soft, maxLines = 4,
                        modifier = Modifier.widthIn(max = 620.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 4.dp)) {
                        DetBtn(if (started) trf("Nastavi · još %s min", (((pr!!.dur - pr.pos) / 60_000).toInt()).coerceAtLeast(1)) else tr("Gledaj"), R.drawable.lc_play, primary = true, fr = playFr) { play() }
                        DetBtn(tr("Trailer"), R.drawable.lc_film) { openTrailer(ctx, v.name, m?.trailerKey) }
                        val on = remember(MyList.rev) { MyList.has("vod", v.id) }
                        DetBtn(tr(if (on) "U mojoj listi" else "Dodaj na moju listu"), if (on) R.drawable.lc_bookmark_filled else R.drawable.lc_bookmark, on = on) {
                            myListToggle(ctx, VItem(v.id, v.name, v.poster, v.rating, v.url, false, p.id))
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x33FFFFFF)))
            Row(Modifier.fillMaxWidth()) {
                val dir = m?.people?.firstOrNull { it.role == "Režija" }?.name ?: i?.director
                val cast = m?.people?.filter { it.role != "Režija" && it.role != "Autor" }?.take(3)?.joinToString(", ") { it.name } ?: i?.cast?.split(',')?.take(3)?.joinToString(",")?.trim()
                val pv = (probe as? Load.Ok)?.value
                InfoCell(R.drawable.lc_film, tr("Režija"), dir ?: "—", Modifier.weight(0.9f))
                InfoCell(R.drawable.lc_users, tr("Glumci"), cast ?: "—", Modifier.weight(1.4f))
                InfoCell(R.drawable.lc_headphones, "Audio", if (probe is Load.Loading) tr("Provjeravam…") else audioText(pv?.first ?: emptyList()), Modifier.weight(1f))
                InfoCell(R.drawable.lc_message_square, tr("Titlovi (provider)"), if (probe is Load.Loading) tr("Provjeravam…") else providerSubsText(pv?.second ?: emptyList()), Modifier.weight(1f))
                InfoCell(R.drawable.lc_cloud, tr("Titlovi (online)"), when (SmartSubs.state(v.name, null, null)) {
                    is SmartSubs.State.Found -> Langs.label(SmartSubs.lang()); SmartSubs.State.None -> tr("Nije pronađeno"); else -> tr("Tražim…") }, Modifier.weight(1f))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x33FFFFFF)))
            val sim = (similar as? Load.Ok)?.value.orEmpty()
            if (sim.isNotEmpty()) {
                T("Slični filmovi", 20.sp, C.Text, FontWeight.Bold)
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)) {
                    items(sim.size) { k ->
                        val it = sim[k]
                        Focusable(onClick = { it.open(nav) }, shape = Rnd(12.dp), normal = Color.Transparent, focused = Color.Transparent, focusBorderColor = Color.White,
                            zoom = 1.04f, modifier = Modifier.width(220.dp).height(124.dp)) { _ ->
                            OnyxImage(it.image, "p:" + it.id, Modifier.fillMaxSize().clip(Rnd(12.dp)), maxW = 440)
                        }
                    }
                }
            }
        }
    }
    if (ext) ExternalPicker(v.url, title, pr?.pos ?: 0, onDismiss = { ext = false })
    LaunchedEffect(Unit) { delay(60); playFr.tryFocus() }
}

/** The three buttons of a film or series page: white = main action, outline = the others; a filled bookmark when on My List. */
@Composable
fun DetBtn(text: String, icon: Int, primary: Boolean = false, on: Boolean = false, fr: FocusRequester? = null, onClick: () -> Unit) {
    Focusable(onClick = onClick, focusRequester = fr, shape = Rnd(12.dp), normal = if (primary || on) Color(0xE6F2F2F4) else Color(0x14FFFFFF), focused = Dawn.pill,
        focusBorderColor = Color.Transparent, zoom = 1f, modifier = Modifier.height(52.dp).border(1.dp, if (primary || on) Color.Transparent else Color(0xB3FFFFFF), Rnd(12.dp))) { f ->
        val fg = if (f || primary || on) Dawn.onPill else Color.White
        Row(Modifier.align(Alignment.Center).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Ico(icon, 20.dp, fg); T(text, 17.sp, fg, FontWeight.SemiBold)
        }
    }
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

    // one level at a time: the series page (picture, actions, seasons) › the episodes of one season
    var epsOpen by remember { mutableStateOf(jump != null) }
    var epMenu by remember { mutableStateOf<EpRow?>(null) }
    val seasonFr = remember { FocusRequester() }
    androidx.activity.compose.BackHandler(enabled = epsOpen) { epsOpen = false }
    val weekAgo = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(System.currentTimeMillis() - 7 * 86_400_000L)) }  // i18n-ignore
    fun isNew(air: String?) = air != null && air >= weekAgo && air <= today
    val seenAll = remember(all, Watch.rev) { all.count { Watch.get(ctx, it.url)?.watched == true } }
    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B0C))) {
        Box(Modifier.fillMaxHeight(if (epsOpen) 1f else 0.8f).fillMaxWidth(if (epsOpen) 0.42f else 0.72f).align(if (epsOpen) Alignment.TopStart else Alignment.TopEnd)) {
            OnyxImage(m?.backdrop ?: s.cover, "bd:" + s.id, Modifier.fillMaxSize(), maxW = 1280, scale = ContentScale.Crop, highQuality = true)
            Box(Modifier.fillMaxSize().background(if (epsOpen) Brush.horizontalGradient(listOf(Color(0x330B0B0C), Color(0xFF0B0B0C)))
                else Brush.horizontalGradient(listOf(Color(0xFF0B0B0C), Color(0x800B0B0C), Color(0x000B0B0C)))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color(0x000B0B0C), 1f to Color(0xFF0B0B0C))))
        }
        if (!epsOpen) Column(Modifier.fillMaxSize().padding(start = 44.dp, end = 32.dp, top = 6.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            T(title, 40.sp, C.Text, FontWeight.Bold, maxLines = 2, modifier = Modifier.widthIn(max = 640.dp))
            val needFree = m == null && meta !is Load.Loading
            val free by load(s.name, needFree) { if (needFree) FreeMeta.seriesFull(s.name) else null }
            val fr = (free as? Load.Ok)?.value
            T(listOfNotNull(tr("Serija"), translated(m?.genres ?: fr?.genre)?.replace(", ", " • ")?.take(40), (m?.year ?: fr?.year)?.take(4)).joinToString("  •  "), 16.sp, C.Text)
            T(listOfNotNull(if (seasons.isNotEmpty()) trf("%s sezone", seasons.size) else null, if (all.isNotEmpty()) trf("%s epizoda", all.size) else null,
                if (all.isNotEmpty()) trf("%s %% odgledano", seenAll * 100 / all.size) else null).joinToString("   "), 15.sp, C.Soft)
            val story = listOfNotNull(m?.overview ?: s.plot?.ifBlank { null } ?: fr?.plot).firstOrNull()
            T(translated(story) ?: if (free is Load.Loading) tr("Učitavam opis…") else " ", 15.sp, C.Soft, maxLines = 3, modifier = Modifier.widthIn(max = 620.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 4.dp)) {
                val np = next?.let { Watch.get(ctx, it.url) }
                val label = when {
                    next == null -> tr("Gledaj")
                    np?.started == true -> trf("Nastavi S%s E%s", next.season, next.num)
                    else -> trf("Gledaj S%s E%s", next.season, next.num)
                }
                DetBtn(label, R.drawable.lc_play, primary = true, fr = playFr) { next?.let { playEp(it) } }
                DetBtn(tr("Trailer"), R.drawable.lc_film) { openTrailer(ctx, s.name, m?.trailerKey) }
                val on = remember(MyList.rev) { MyList.has("series", s.id) }
                DetBtn(tr(if (on) "U mojoj listi" else "Dodaj na moju listu"), if (on) R.drawable.lc_bookmark_filled else R.drawable.lc_bookmark, on = on) {
                    myListToggle(ctx, VItem(s.id, s.name, s.cover, s.rating, "", true, p.id))
                }
            }
            Spacer(Modifier.weight(1f))
            when (val st = eps) {
                is Load.Loading -> T("Učitavam epizode…", 15.sp, C.Soft)
                is Load.Err -> T(tr("Epizode se nisu učitale") + ": " + st.message, 15.sp, C.Soft, maxLines = 2)
                is Load.Ok -> if (all.isEmpty()) T("Nema epizoda", 15.sp, C.Soft) else {
                    T("Sezone", 20.sp, C.Text, FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(4.dp)) {
                        items(seasons) { n ->
                            val count = all.count { it.season == n }
                            val seen = all.count { it.season == n && Watch.get(ctx, it.url)?.watched == true }
                            val pct = if (count > 0) seen * 100 / count else 0
                            val fresh = all.any { it.season == n && isNew(it.air) }
                            Focusable(onClick = { season = n; epsOpen = true }, onMenu = { if (count > 0) seasonMenu = n },
                                focusRequester = if (n == cur) seasonFr else null, shape = Rnd(16.dp), normal = Color(0xCC161618), focused = Color(0xCC161618),
                                focusBorderColor = Color.White, zoom = 1.03f, modifier = Modifier.width(300.dp).height(130.dp).border(1.dp, Color(0x33FFFFFF), Rnd(16.dp))) { _ ->
                                Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        T(trf("Sezona %s", n), 22.sp, C.Text, FontWeight.Bold, modifier = Modifier.weight(1f))
                                        if (count > 0 && seen == count) Ico(R.drawable.lc_circle_check, 22.dp, C.Text)
                                        else if (fresh) Box(Modifier.clip(Rnd(8.dp)).background(Color(0xF2F2F2F4)).padding(horizontal = 8.dp, vertical = 2.dp)) { T("NOVO", 12.sp, Dawn.onPill, FontWeight.Bold) }
                                    }
                                    T(if (count > 0) trf("%s epizoda", count) else tr("uskoro"), 15.sp, C.Soft)
                                    Spacer(Modifier.weight(1f))
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        ThinBar(pct / 100f, Color.White, Modifier.weight(1f), 4.dp)
                                        T("$pct%", 14.sp, C.Text)  // i18n-ignore
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        else Row(Modifier.fillMaxSize().padding(start = 40.dp, end = 32.dp, top = 6.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(Modifier.weight(0.36f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                T(title, 34.sp, C.Text, FontWeight.Bold, maxLines = 3)
                T(trf("Sezona %s", cur) + "  •  " + trf("%s epizoda", all.count { it.season == cur }), 18.sp, C.Text)
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Ico(R.drawable.lc_info, 16.dp, C.Soft); T("Drži OK za opcije", 14.sp, C.Soft)
                }
            }
            Column(Modifier.weight(0.64f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                T("Sezona", 15.sp, C.Soft)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(seasons) { n ->
                        val count = all.count { it.season == n }
                        val done = count > 0 && all.count { it.season == n && Watch.get(ctx, it.url)?.watched == true } == count
                        val open = n == cur
                        Focusable(onClick = { if (count > 0) season = n }, shape = Pill, normal = if (open) Color(0xF2F2F2F4) else Color(0x1FFFFFFF), focused = Dawn.pill,
                            focusBorderColor = Color.Transparent, zoom = 1f, modifier = Modifier.height(46.dp).width(104.dp)) { f ->
                            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val fg = if (f || open) Dawn.onPill else if (count == 0) C.Muted else C.Text
                                T("S$n", 17.sp, fg, FontWeight.Bold)  // i18n-ignore
                                if (done) Ico(R.drawable.lc_check, 15.dp, fg) else if (count == 0) Ico(R.drawable.lc_lock, 14.dp, fg)
                            }
                        }
                    }
                }
                key(Watch.rev, cur) {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                        itemsIndexed(rows, key = { _, r -> "${r.season}_${r.num}_${r.e?.id}" }) { _, r ->
                            val e = r.e
                            val pr = e?.let { Watch.get(ctx, it.url) }
                            val air = e?.air ?: r.t?.air
                            val future = e == null && (r.t?.air == null || r.t.air > today)
                            val name = (e?.let { episodeTitle(it.title, it.num) } ?: r.t?.name ?: trf("Epizoda %s", r.num))
                            val isJump = jump != null && jump.first == r.season && jump.second == r.num
                            Focusable(onClick = { if (e != null) playEp(e) else Toast.makeText(ctx, if (future) tr("Još nije izašla") else tr("Čeka se provider"), Toast.LENGTH_SHORT).show() },
                                onMenu = { if (e != null) epMenu = r }, onFocus = { lastFocusNum = r.num }, focusRequester = if (isJump || (jump == null && r == rows.firstOrNull { it.e != null && Watch.get(ctx, it.e.url)?.watched != true })) jumpFr else null,
                                shape = Rnd(14.dp), normal = Color(0x99161618), focused = Color(0x99161618), focusBorderColor = Color.White, zoom = 1f,
                                modifier = Modifier.fillMaxWidth().height(84.dp)) { _ ->
                                Row(Modifier.fillMaxSize().padding(end = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                    Box(Modifier.fillMaxHeight().aspectRatio(16f / 9f).clip(Rnd(14.dp)).background(C.CardHi), contentAlignment = Alignment.Center) {
                                        val img = e?.image ?: r.t?.still
                                        if (img != null) OnyxImage(img, e?.id ?: "${s.id}_${r.season}_${r.num}", Modifier.fillMaxSize().then(if (e == null) Modifier.blur(6.dp) else Modifier), maxW = 260)
                                        if (e == null) Ico(R.drawable.lc_lock, 22.dp, Color.White)
                                        else if (pr?.watched == true) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(Rnd(11.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                                            Ico(R.drawable.lc_check, 14.dp, Dawn.onPill) }
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        T("${r.num}. $name", 19.sp, if (e == null) C.Soft else C.Text, FontWeight.SemiBold, maxLines = 1)
                                        if (pr?.started == true) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            ThinBar(pr.fraction, Color.White, Modifier.width(240.dp), 4.dp)
                                            T(trf("još %s min", ((pr.dur - pr.pos) / 60_000).toInt().coerceAtLeast(1)) + " (${(pr.fraction * 100).toInt()}%)", 14.sp, C.Soft)  // i18n-ignore
                                        }
                                    }
                                    when {
                                        e == null && future -> T(air?.let { trf("Izlazi %s", fmtDate(it)) } ?: tr("Još nije izašla"), 15.sp, C.Soft)
                                        e == null -> T("Čeka se provider", 15.sp, C.Soft)
                                        pr?.watched == true -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Ico(R.drawable.lc_check, 16.dp, C.Text); T("Odgledano", 15.sp, C.Text) }
                                        isNew(air) -> Box(Modifier.clip(Rnd(8.dp)).background(Color(0xF2F2F2F4)).padding(horizontal = 10.dp, vertical = 3.dp)) { T("NOVO", 13.sp, Dawn.onPill, FontWeight.Bold) }
                                        else -> {}
                                    }
                                }
                            }
                        }
                    }
                }
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
    // ---- long OK on an episode (approved): Episode details · Mark as watched / unwatched · Remove from Continue watching
    epMenu?.let { r -> val e = r.e!!; val pr = Watch.get(ctx, e.url)
        Popup(epCode(r.season, r.num) + " · " + episodeTitle(e.title, e.num), { epMenu = null }) {
            OptionRow("Detalji epizode", R.drawable.lc_info) { epMenu = null; epDlg = r }
            if (pr?.watched == true) OptionRow("Označi kao negledano", R.drawable.lc_circle_slash) { Watch.setWatched(ctx, e.url, epTitle(e), s.cover, false); epMenu = null }
            else OptionRow("Označi kao odgledano", R.drawable.lc_circle_check) { Watch.setWatched(ctx, e.url, epTitle(e), s.cover, true); epMenu = null }
            if (pr?.started == true) OptionRow("Ukloni iz „Nastavi gledati“", R.drawable.lc_trash_2) { ContinueHidden.hide(e.url); epMenu = null
                Toast.makeText(ctx, tr("Uklonjeno iz „Nastavi gledati“"), Toast.LENGTH_SHORT).show() }
        }
    }
    epDlg?.let { r -> EpisodeDialog(r, title, s, m, ::fmtDate, onDismiss = { epDlg = null },
        onPlay = { e, fromStart -> epDlg = null; playEp(e, fromStart) },
        onMark = { e, on -> Watch.setWatched(ctx, e.url, epTitle(e), s.cover, on); epDlg = null },
        onExt = { e -> epDlg = null; ext = e }) }
    ext?.let { e -> ExternalPicker(e.url, epTitle(e), Watch.get(ctx, e.url)?.pos ?: 0, onDismiss = { ext = null }) }
    var wasEps by remember { mutableStateOf(false) }
    LaunchedEffect(eps is Load.Ok, rows.size, epsOpen, cur) {
        if (eps !is Load.Ok) return@LaunchedEffect
        // back from the episodes: the focus goes to that season's card
        if (!epsOpen && wasEps) { wasEps = false; delay(60); seasonFr.tryFocus(); return@LaunchedEffect }
        wasEps = epsOpen
        if (epsOpen) {
            val idx = if (jump != null && jump.first == cur) rows.indexOfFirst { it.num == jump.second } else rows.indexOfFirst { it.e != null && Watch.get(ctx, it.e.url)?.watched != true }
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
