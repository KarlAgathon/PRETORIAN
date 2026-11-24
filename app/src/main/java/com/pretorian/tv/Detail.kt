package com.pretorian.tv

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
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
import androidx.compose.ui.graphics.graphicsLayer
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
    var pick by remember { mutableStateOf<String?>(null) }   // "audio" / "subs" / "online"
    var choice by remember { mutableStateOf(TrackChoice()) }
    val playFr = remember { FocusRequester() }
    LaunchedEffect(v.id) { NewContent.seen(p.id, "vod", v.id) }
    val i = (info as? Load.Ok)?.value
    val m = (meta as? Load.Ok)?.value
    val title = titleCase(m?.title ?: cleanTitle(v.name).first)
    val pr = remember(Watch.rev) { Watch.get(ctx, v.url) }
    val started = pr != null && pr.started
    fun play(from: Long? = null) {
        VodMemory.returning = true
        val at = from ?: if (started) pr!!.pos else 0L
        if (ExtPlayer.always) openExternalOrAsk(ctx, v.url, title, at) { ext = true }
        else nav.push(Screen.Player(listOf(choice.apply(PlayItem(title, v.url, v.poster, null, null, v.name, startAt = at))), 0, live = false))
    }
    fun startOver() = play(0L)
    val mins = m?.runtimeMin?.takeIf { it > 0 } ?: parseMin(i?.duration)
    var menu by remember { mutableStateOf<String?>(null) }
    // redesign spec 03: full picture, gradient from the left, breadcrumb, Watch/Resume · Trailer · My List,
    // progress only when started, then exactly four icons whose menus open upward
    Box(Modifier.fillMaxSize()) {
        DetailBackdrop(m?.backdrop ?: i?.backdrop?.takeIf { it.isNotBlank() }, v.poster, "bd:" + v.id, ready = meta !is Load.Loading && info !is Load.Loading)
        Column(Modifier.fillMaxSize().padding(start = 56.dp, end = 48.dp, top = 34.dp, bottom = 34.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            T(listOfNotNull(tr("Filmovi"), vodCrumb(false) ?: VodMemory.catOf[v.id], title).joinToString("  /  ").uppercase(), 13.sp, Silver.TextSoft, FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            T(title, 40.sp, Color.White, FontWeight.Bold, maxLines = 2, modifier = Modifier.widthIn(max = 760.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                (i?.marks ?: emptyList()).filter { it == "4K" || it == "HDR" }.forEach {  // i18n-ignore
                    Box(Modifier.border(1.dp, Color.White, Rnd(5.dp)).padding(horizontal = 6.dp, vertical = 1.dp)) { T(it, 13.sp, Color.White, FontWeight.Bold) }
                }
                val r = (m?.tmdbRating ?: v.rating?.replace(',', '.')?.toDoubleOrNull())?.takeIf { it > 0 }
                if (r != null) T("★ " + String.format(java.util.Locale.US, "%.1f", r), 15.sp, Color(0xFFF5C518), FontWeight.SemiBold)  // i18n-ignore
                val parts = listOfNotNull((m?.year ?: i?.year)?.take(4), fmtMin(mins).ifBlank { null }, translated(m?.genres ?: i?.genre)?.split(", ")?.take(3)?.joinToString(" · "))
                T(parts.joinToString("  •  "), 15.sp, Color(0xFFDDE1E6))  // i18n-ignore
            }
            val needFree = m?.overview == null && i?.plot.isNullOrBlank() && meta !is Load.Loading && info !is Load.Loading
            val free by load(v.name, needFree) { if (needFree) FreeMeta.movieFull(v.name) else null }
            val plot = translated(m?.overview ?: i?.plot?.ifBlank { null } ?: (free as? Load.Ok)?.value?.plot)
            if (plot != null) T(plot, 16.sp, Color(0xFFDDE1E6), maxLines = 3, modifier = Modifier.widthIn(max = 640.dp))
            val dir = (m?.people?.firstOrNull { it.role == "Režija" }?.name ?: i?.director)?.trim()?.ifBlank { null }
            val cast = (m?.people?.filter { it.role != "Režija" && it.role != "Autor" }?.take(4)?.joinToString(", ") { it.name }
                ?: i?.cast?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }?.take(4)?.joinToString(", "))?.ifBlank { null }
            if (cast != null) T(tr("Glumci") + ": " + cast, 14.sp, Silver.TextSoft, maxLines = 1, modifier = Modifier.widthIn(max = 700.dp))
            if (dir != null) T(tr("Režija") + ": " + dir, 14.sp, Silver.TextSoft, maxLines = 1)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                DetBtn(if (started) tr("Nastavi") else tr("Gledaj"), R.drawable.lc_play, primary = true, fr = playFr) { play() }
                DetBtn(tr("Trailer"), R.drawable.ic_trailer_red, colored = true) { openTrailer(ctx, v.name, m?.trailerKey) }
                val on = remember(MyList.rev) { MyList.has("vod", v.id) }
                DetBtn(tr("Moja lista"), if (on) R.drawable.lc_check else R.drawable.lc_plus, on = on) {
                    myListToggle(ctx, VItem(v.id, v.name, v.poster, v.rating, v.url, false, p.id))
                }
            }
            // watched / remaining: only when the film was started
            if (started) Row(Modifier.widthIn(max = 520.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f).height(4.dp).clip(Rnd(2.dp)).background(Color(0x40FFFFFF))) {
                    Box(Modifier.fillMaxWidth(pr!!.fraction).fillMaxHeight().background(Color.White))
                }
                T(trf("još %s min", (((pr!!.dur - pr.pos) / 60_000).toInt()).coerceAtLeast(1)), 13.sp, Silver.TextSoft)
            }
            Spacer(Modifier.height(8.dp))
            MediaIconsRow(menu, { menu = it }, subsOn = choice.subFile != null || choice.sub != null, audioOn = choice.audio != null,
                playOn = choice.speed != 1f || Sleep.until > 0, picOn = choice.fmt > 0) { k ->
                when (k) {
                    MI_SUBS -> FilmSubsPicker(v.name, (probe as? Load.Ok)?.value?.second ?: emptyList(), choice, onDismiss = { menu = null }, onPick = { c -> choice = c; menu = null })
                    MI_AUDIO -> AudioPicker(v.url, choice.audio?.title, onDismiss = { menu = null }) { t -> choice = choice.copy(audio = t); menu = null }
                    MI_PLAYBACK -> PlaybackMenu(choice.speed, { choice = choice.copy(speed = it); menu = null },
                        onStartOver = if (started) ({ menu = null; startOver() }) else null, onDismiss = { menu = null })
                    MI_PICTURE -> PictureMenu(if (choice.fmt < 0) Prefs.long("vod_fmt", 0).toInt() else choice.fmt, { choice = choice.copy(fmt = it); menu = null }) { menu = null }
                }
            }
        }
    }
    if (ext) ExternalPicker(v.url, title, pr?.pos ?: 0, onDismiss = { ext = false })
    LaunchedEffect(Unit) { delay(60); playFr.tryFocus() }
    @Suppress("UNUSED_VARIABLE") val unusedSim = similar
    @Suppress("UNUSED_VARIABLE") val unusedPick = pick
}

/** The four icons of the movie / series view: Subtitles · Audio · Playback · Picture; a white dot = something chosen. */
@Composable
fun MediaIconsRow(open: String?, setOpen: (String?) -> Unit, subsOn: Boolean, audioOn: Boolean, playOn: Boolean, picOn: Boolean,
                  menuOf: @Composable (String) -> Unit) {
    val keys = listOf(MI_SUBS, MI_AUDIO, MI_PLAYBACK, MI_PICTURE)
    val frs = remember { keys.map { FocusRequester() } }
    var last by remember { mutableStateOf<String?>(null) }
    // Back (or a choice) closes the menu and the focus goes back to its icon
    LaunchedEffect(open) { if (open != null) last = open else last?.let { delay(60); frs[keys.indexOf(it)].tryFocus() } }
    // 3.0.0.3: the labels sit above the bottom safe margin, so they are never cut off
    Row(Modifier.padding(bottom = 40.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        listOf(Triple(MI_SUBS, R.drawable.lc_captions, "Titlovi") to subsOn, Triple(MI_AUDIO, R.drawable.lc_volume, "Audio") to audioOn,
            Triple(MI_PLAYBACK, R.drawable.lc_gauge2, "Reprodukcija") to playOn, Triple(MI_PICTURE, R.drawable.lc_rectangle_horizontal, "Slika") to picOn)
            .forEachIndexed { i, (t, on) ->
                MediaIcon(t.second, t.third, open == t.first, fr = frs[i], active = on, onClick = { setOpen(t.first) }) { menuOf(t.first) }
            }
    }
}

/** Film page › Subtitles: one list, one press (approved) — Provider tracks, a line, then online versions per chosen language. */
@Composable
fun FilmSubsPicker(search: String, provider: List<androidx.media3.common.Format>, choice: TrackChoice, onDismiss: () -> Unit, onPick: (TrackChoice) -> Unit,
                   season: Int? = null, episode: Int? = null) {
    val tracks = provider.map { StreamTrack(iso2(it.language), it.label, trackLabel(it, false)) }
    val names = run {
        val seen = HashMap<String, Int>()
        tracks.map { t ->
            val lang = t.lang?.let { Langs.label(it) } ?: tr("Nepoznat jezik")
            val n = (seen[lang] ?: 0) + 1; seen[lang] = n
            if (tracks.count { (it.lang?.let { c -> Langs.label(c) } ?: tr("Nepoznat jezik")) == lang } > 1) "$lang (V$n)" else lang
        }
    }
    SubsMenu(search, season, episode, true, names, { i -> choice.sub?.title == tracks[i].title && choice.subFile == null },
        offOn = choice.subOff, onOff = { onPick(choice.copy(sub = null, subFile = null, subOff = true)) },
        onProvider = { i -> onPick(choice.copy(sub = tracks[i], subFile = null, subOff = false)) },
        onlineOn = { id -> choice.subFile != null && Prefs.str("subpick_film_" + search.hashCode()) == id }, onDismiss = onDismiss) { id, f ->
        Prefs.put("subpick_film_" + search.hashCode(), id)
        onPick(choice.copy(subFile = f, sub = null, subOff = false))
    }
}

/** The three buttons of a film or series page: white = main action, outline = the others; a filled bookmark when on My List. */
@Composable
fun DetBtn(text: String, icon: Int, primary: Boolean = false, on: Boolean = false, fr: FocusRequester? = null, colored: Boolean = false, onClick: () -> Unit) {
    // redesign: Watch is solid white; the others glass with a hairline; focus = translucent pill with a white border
    Focusable(onClick = onClick, focusRequester = fr, shape = Rnd(26.dp), normal = if (primary) Color(0xF2FFFFFF) else Color(0x8C14181C),
        focused = if (primary) Color.White else Dawn.pill, focusBorderColor = Color.White, borderColor = if (primary) Color.Transparent else Color(0x33FFFFFF),
        zoom = 1.04f, modifier = Modifier.height(50.dp)) { _ ->
        @Suppress("UNUSED_VARIABLE") val o = on
        val fg = if (primary) Color(0xFF111113) else Color.White
        Row(Modifier.align(Alignment.Center).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (colored) androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(icon), null, Modifier.size(24.dp)) else Ico(icon, 20.dp, fg)
            T(text, 17.sp, fg, FontWeight.SemiBold)
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
        // the whole series in order: Auto Next goes on into the next season (never back to the first episode)
        val list = all.sortedWith(compareBy({ it.season }, { it.num }))
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

    // redesign spec 04: one view — picture, breadcrumb, Watch/Resume SxxEyy · Trailer · My List,
    // SEASON N ▾ on the left and the four icons on the right right above ONE horizontal episode row
    var epMenu by remember { mutableStateOf<EpRow?>(null) }
    var menu by remember { mutableStateOf<String?>(null) }
    val weekAgo = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(System.currentTimeMillis() - 7 * 86_400_000L)) }  // i18n-ignore
    fun isNew(air: String?) = air != null && air >= weekAgo && air <= today
    val wrap = remember(rows.size) { RowWrap(rows.size) }
    Box(Modifier.fillMaxSize()) {
        DetailBackdrop(m?.backdrop, s.cover, "bd:" + s.id, ready = meta !is Load.Loading)
        Column(Modifier.fillMaxSize().padding(start = 56.dp, end = 48.dp, top = 30.dp, bottom = 26.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            T(listOfNotNull(tr("Serije"), vodCrumb(true) ?: VodMemory.catOf[s.id], title).joinToString("  /  ").uppercase(), 13.sp, Silver.TextSoft, FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            T(title, if (title.length > 26) 32.sp else 40.sp, Color.White, FontWeight.Bold, maxLines = 1, modifier = Modifier.widthIn(max = 760.dp))
            val needFree = m == null && meta !is Load.Loading
            val free by load(s.name, needFree) { if (needFree) FreeMeta.seriesFull(s.name) else null }
            val fr = (free as? Load.Ok)?.value
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val r = (m?.tmdbRating ?: s.rating?.replace(',', '.')?.toDoubleOrNull())?.takeIf { it > 0 }
                if (r != null) T("★ " + String.format(java.util.Locale.US, "%.1f", r), 15.sp, Color(0xFFF5C518), FontWeight.SemiBold)  // i18n-ignore
                T(listOfNotNull((m?.year ?: fr?.year)?.take(4), if (seasons.size == 1) tr("1 sezona") else if (seasons.isNotEmpty()) trf("%s sezone", seasons.size) else null,
                    translated(m?.genres ?: fr?.genre)?.split(", ")?.take(3)?.joinToString(" · ")).joinToString("  •  "), 15.sp, Color(0xFFDDE1E6))  // i18n-ignore
            }
            val story = listOfNotNull(m?.overview ?: s.plot?.ifBlank { null } ?: fr?.plot).firstOrNull()
            T(translated(story) ?: if (free is Load.Loading) tr("Učitavam opis…") else " ", 15.sp, Color(0xFFDDE1E6), maxLines = 2, modifier = Modifier.widthIn(max = 660.dp))
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                val np = next?.let { Watch.get(ctx, it.url) }
                val code = next?.let { "S%02dE%02d".format(it.season, it.num) } ?: ""  // i18n-ignore
                val label = when { next == null -> tr("Gledaj"); np?.started == true -> tr("Nastavi") + " " + code; else -> tr("Gledaj") + " " + code }
                DetBtn(label, R.drawable.lc_play, primary = true, fr = playFr) { next?.let { playEp(it) } }
                DetBtn(tr("Trailer"), R.drawable.ic_trailer_red, colored = true) { openTrailer(ctx, s.name, m?.trailerKey) }
                val on = remember(MyList.rev) { MyList.has("series", s.id) }
                DetBtn(tr("Moja lista"), if (on) R.drawable.lc_check else R.drawable.lc_plus, on = on) {
                    myListToggle(ctx, VItem(s.id, s.name, s.cover, s.rating, "", true, p.id))
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                // SEASON N ▾ : the seasons and "mark season" in one menu that opens upward
                Column {
                    Focusable(onClick = { menu = "season" }, shape = Rnd(22.dp), normal = Color(0x8C14181C), focused = Dawn.pill, borderColor = Color(0x33FFFFFF),
                        zoom = 1f, modifier = Modifier.height(44.dp)) { _ ->
                        Row(Modifier.align(Alignment.Center).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            T(trf("Sezona %s", cur).uppercase(), 15.sp, Color.White, FontWeight.SemiBold)
                            Ico(R.drawable.lc_chevron_down, 16.dp, Color.White)
                        }
                    }
                    if (menu == "season") androidx.compose.runtime.CompositionLocalProvider(LocalUpward provides true) {
                        Popup("Sezone", { menu = null }) {
                            seasons.forEach { n ->
                                val count = all.count { it.season == n }
                                val seen = all.count { it.season == n && Watch.get(ctx, it.url)?.watched == true }
                                MenuRow(trf("Sezona %s", n), on = n == cur, sub = if (count > 0) trf("%s epizoda", count) + (if (seen == count) " · ✓" else "") else tr("uskoro")) {  // i18n-ignore
                                    if (count > 0) { season = n; SeriesJump.id = ""; menu = null }
                                }
                            }
                            val list = all.filter { it.season == cur }
                            if (list.isNotEmpty()) {
                                MenuHead(trf("Sezona %s", cur))
                                val allSeen = list.all { Watch.get(ctx, it.url)?.watched == true }
                                if (allSeen) MenuRow(tr("Označi sezonu kao negledanu")) { markSeason(cur, false); menu = null }
                                else MenuRow(tr("Označi sezonu kao odgledanu")) { markSeason(cur, true); menu = null }
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                MediaIconsRow(menu?.takeIf { it != "season" }, { menu = it }, subsOn = choice.subFile != null || choice.sub != null || choice.subOff, audioOn = choice.audio != null,
                    playOn = choice.speed != 1f || Sleep.until > 0, picOn = choice.fmt > 0) { k ->
                    val first = next ?: all.firstOrNull()
                    when (k) {
                        MI_SUBS -> FilmSubsPicker(s.name, emptyList(), choice, onDismiss = { menu = null }, onPick = { c -> choice = c; menu = null },
                            season = first?.season, episode = first?.num)
                        MI_AUDIO -> AudioPicker(first?.url ?: "", choice.audio?.title, onDismiss = { menu = null }) { t -> choice = choice.copy(audio = t); menu = null }
                        MI_PLAYBACK -> PlaybackMenu(choice.speed, { choice = choice.copy(speed = it); menu = null },
                            onStartOver = next?.let { e -> ({ menu = null; playEp(e, true) }) }, startOverLabel = "Pusti epizodu od početka", series = true, onDismiss = { menu = null })
                        MI_PICTURE -> PictureMenu(if (choice.fmt < 0) Prefs.long("vod_fmt", 0).toInt() else choice.fmt, { choice = choice.copy(fmt = it); menu = null }) { menu = null }
                    }
                }
            }
            when (val st = eps) {
                is Load.Loading -> T("Učitavam epizode…", 15.sp, C.Soft)
                is Load.Err -> T(tr("Epizode se nisu učitale") + ": " + st.message, 15.sp, C.Soft, maxLines = 2)
                is Load.Ok -> if (all.isEmpty()) T("Nema epizoda", 15.sp, C.Soft) else androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val gap = 16.dp
                    val w = (maxWidth - gap * 4) / 5
                    key(Watch.rev, cur) {
                        // the focused card grows 6 % and has a 1.5 dp border: the row gets that room on every side
                        // (content padding) and is widened by the same amount to both sides, so the cards still line up
                        // with SEASON ▾ and the icons and nothing is clipped on the first frame
                        val room = w * 0.04f + 4.dp
                        LazyRow(state = wrap.state, horizontalArrangement = Arrangement.spacedBy(gap), contentPadding = PaddingValues(horizontal = room, vertical = 10.dp),
                            modifier = Modifier.layout { m, c ->
                                val px = room.roundToPx()
                                val pl = m.measure(c.copy(minWidth = c.maxWidth + 2 * px, maxWidth = c.maxWidth + 2 * px))
                                layout(c.maxWidth, pl.height) { pl.place(-px, 0) }
                            }) {
                            itemsIndexed(rows, key = { _, r -> "${r.season}_${r.num}_${r.e?.id}" }) { i, r ->
                                val isJump = jump != null && jump.first == r.season && jump.second == r.num
                                val target = isJump || (jump == null && r == rows.firstOrNull { it.e != null && Watch.get(ctx, it.e.url)?.watched != true })
                                EpisodeCard(r, s, w, if (target) jumpFr else wrap.fr(i), wrap.keys(i, scope), today, ::isNew, ::fmtDate,
                                    onFocus = { lastFocusNum = r.num },
                                    onPlay = { e -> playEp(e) }, onLong = { epDlg = r })
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
    LaunchedEffect(eps is Load.Ok, rows.size) {
        if (eps !is Load.Ok) return@LaunchedEffect
        // back from the player: the focus waits on that episode; else on Watch
        if (jump != null && jump.first == cur) {
            val idx = rows.indexOfFirst { it.num == jump.second }
            if (idx >= 0) { wrap.state.scrollToItem(idx); delay(80); if (jumpFr.tryFocus()) return@LaunchedEffect }
        }
        delay(60); playFr.tryFocus()
    }
    @Suppress("UNUSED_VARIABLE") val unusedEm = epMenu
}

/** One episode of the row: 16:9 picture (a clean numbered plate when there is none — never an empty blurred box),
 *  "E01 — title", the state (✓ Watched / % watched / Not watched) and a thin white bar. Short OK plays, long OK = overlay. */
@Composable
private fun EpisodeCard(r: EpRow, s: SeriesItem, w: Dp, fr: FocusRequester?, keys: Modifier, today: String, isNew: (String?) -> Boolean, fmtDate: (String?) -> String,
                        onFocus: () -> Unit, onPlay: (Episode) -> Unit, onLong: () -> Unit) {
    val ctx = LocalContext.current
    val e = r.e
    val pr = e?.let { Watch.get(ctx, it.url) }
    val future = e == null && (r.t?.air == null || r.t.air > today)
    val generic = Regex("^\\s*(episode|epizoda|ep\\.?|folge|e)\\s*\\d+\\s*$", RegexOption.IGNORE_CASE)
    val own = e?.let { episodeTitle(it.title, it.num) }
    val name = own?.takeUnless { generic.matches(it) } ?: r.t?.name ?: own ?: trf("Epizoda %s", r.num)
    val img = e?.image ?: r.t?.still
    Column(Modifier.width(w), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Focusable(onClick = { if (e != null) onPlay(e) else Toast.makeText(ctx, if (future) tr("Još nije izašla") else tr("Čeka se provider"), Toast.LENGTH_SHORT).show() },
            onMenu = onLong, onFocus = onFocus, focusRequester = fr, shape = Rnd(12.dp), normal = Color(0xFF1B1C1E), focused = Color(0xFF1B1C1E),
            focusBorderColor = Color.White, borderColor = Color(0x1FFFFFFF), zoom = 1.06f, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).then(keys)) { _ ->
            if (img != null) OnyxImage(img, e?.id ?: "${s.id}_${r.season}_${r.num}", Modifier.fillMaxSize(), maxW = 400)
            else Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF2A2C2F), Color(0xFF17181A)))), contentAlignment = Alignment.Center) {
                T("E%02d".format(r.num), 30.sp, Color(0x99FFFFFF), FontWeight.Bold)  // i18n-ignore
            }
            if (e == null) Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) { Ico(R.drawable.lc_lock, 22.dp, Color.White) }
            if (pr?.watched == true) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(Pill).background(Color.White), contentAlignment = Alignment.Center) {
                Ico(R.drawable.lc_check, 14.dp, Color(0xFF111113)) }
            else if (e != null && isNew(e.air)) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).clip(Rnd(6.dp)).background(Color.White).padding(horizontal = 6.dp, vertical = 1.dp)) {
                T("NOVO", 11.sp, Color(0xFF111113), FontWeight.Bold) }
            if (pr != null && pr.started) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color(0x40FFFFFF))) {
                Box(Modifier.fillMaxWidth(pr.fraction).fillMaxHeight().background(Color.White))
            }
        }
        T("E%02d — ".format(r.num) + name, 14.sp, if (e == null) Silver.TextSoft else Color.White, FontWeight.SemiBold, maxLines = 1)  // i18n-ignore
        val state = when {
            e == null && future -> r.t?.air?.let { trf("Izlazi %s", fmtDate(it)) } ?: tr("Još nije izašla")
            e == null -> tr("Čeka se provider")
            pr?.watched == true -> "✓ " + tr("Odgledano")  // i18n-ignore
            pr?.started == true -> trf("%s %% odgledano", (pr.fraction * 100).toInt())
            else -> tr("Nije gledano")
        }
        T(state, 12.sp, Silver.TextSoft, maxLines = 1)
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
                    // compact overlay (spec 04): WATCH / RESUME and only the relevant one of Mark watched / unwatched
                    if (e != null) Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val started = pr?.started == true
                        DetBtn(tr(if (started) "Nastavi" else "Gledaj"), R.drawable.lc_play, primary = true, fr = first) { onPlay(e, false) }
                        if (pr?.watched == true) DetBtn(tr("Označi kao negledano"), R.drawable.lc_circle_slash) { onMark(e, false) }
                        else DetBtn(tr("Označi kao odgledano"), R.drawable.lc_circle_check) { onMark(e, true) }
                        @Suppress("UNUSED_VARIABLE") val x = onExt
                    } else DBtn(tr("Zatvori"), primary = true, fr = first) { onDismiss() }
                }
            }
        }
        LaunchedEffect(Unit) { delay(60); first.tryFocus() }
    }
}

/**
 * Background of the movie / series view (BUG 01B / 02A / 03C): the landscape backdrop keeps its own 16:9 shape
 * (no zoom, no stretching, nothing cut off) and sits on the right, vertically centred, so its subject is right of centre;
 * its edges fade into the Pure Graphite base, and the left text zone stays dark. A portrait poster is never used
 * as a wide background: then the whole poster stands on the right in its own shape. Nothing is drawn while the
 * picture is still being looked up, so no wrong picture flashes in.
 */
@Composable
fun DetailBackdrop(backdrop: String?, poster: String?, key: String, ready: Boolean) {
    // Spec 14: one cinematic backdrop for every movie and series, whatever category it came from.
    // The landscape picture fills the screen (Crop, never stretched), kept to the right and a little above centre;
    // a portrait "backdrop" or a failed load falls back to the poster on the right (whole, not stretched) or plain graphite.
    val base = GRAPHITE
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember(backdrop) { mutableStateOf(if (backdrop == null) 2 else 0) }   // 0 loading · 1 shown · 2 no backdrop
    var bright by remember(backdrop) { mutableStateOf(0f) }
    val alpha by androidx.compose.animation.core.animateFloatAsState(if (state == 1) 1f else 0f, androidx.compose.animation.core.tween(280), label = "bd")
    Box(Modifier.fillMaxSize().background(base)) {
        if (backdrop != null && state != 2) {
            val w = if (Perf.lite) 1280 else 1920
            val req = remember(backdrop, w) {
                coil.request.ImageRequest.Builder(ctx).data(backdrop).size(w, w * 9 / 16).allowHardware(false).memoryCacheKey("bdfull:$backdrop").build()
            }
            AsyncImage(model = req, contentDescription = null, contentScale = ContentScale.Crop,
                alignment = androidx.compose.ui.BiasAlignment(1f, -0.3f),
                modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha },
                onSuccess = { r ->
                    val d = r.result.drawable
                    val iw = d.intrinsicWidth; val ih = d.intrinsicHeight
                    if (iw <= 0 || ih <= 0 || iw.toFloat() / ih < 1.25f) state = 2
                    else {
                        state = 1
                        val bmp = (d as? android.graphics.drawable.BitmapDrawable)?.bitmap
                        if (bmp != null) scope.launch(kotlinx.coroutines.Dispatchers.Default) { bright = leftLuma(bmp) }
                    }
                },
                onError = { state = 2 })
        }
        if (state == 2 && ready && poster != null) Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().aspectRatio(2f / 3f)) {
            OnyxImage(poster, key + ":p", Modifier.fillMaxSize(), maxW = 780, scale = ContentScale.Fit, highQuality = true)
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to base, 0.45f to base.copy(alpha = 0.35f), 1f to Color.Transparent)))
        }
        // left text zone: darkest at the edge, fading out before the middle; brighter pictures get a deeper shade
        val k = ((bright - 0.30f) / 0.35f).coerceIn(0f, 1f)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            0f to base.copy(alpha = 0.96f),
            0.30f to base.copy(alpha = 0.84f + 0.10f * k),
            0.48f to base.copy(alpha = 0.45f + 0.25f * k),
            0.66f to base.copy(alpha = 0.08f * k),
            0.80f to Color.Transparent)))
        // top: a light shade for the breadcrumb and status; bottom: buttons, icons and episodes stay readable
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to base.copy(alpha = 0.55f), 0.16f to Color.Transparent,
            0.55f to Color.Transparent, 1f to base.copy(alpha = 0.90f))))
    }
}

/** Average brightness (0..1) of the left 45 % of a picture, from a tiny copy: one cheap measurement per title. */
private fun leftLuma(b: android.graphics.Bitmap): Float = try {
    val s = android.graphics.Bitmap.createScaledBitmap(b, 48, 27, true)
    var sum = 0f; var n = 0
    for (y in 0 until 27) for (x in 0 until 22) {
        val c = s.getPixel(x, y)
        sum += (0.2126f * ((c shr 16) and 255) + 0.7152f * ((c shr 8) and 255) + 0.0722f * (c and 255)) / 255f; n++
    }
    if (s !== b) s.recycle()
    sum / n
} catch (e: Exception) { 0f }
