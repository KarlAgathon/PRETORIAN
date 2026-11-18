package com.pretorian.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * New Dawn layout (Settings › Appearance › TV layout): one level on the screen at a time, over a misty night
 * background with a street lamp. Menu › groups › channels (preview, now/next, options) › full screen.
 * OK goes one level in and the previous level disappears; Back (and ◀ in TV) goes exactly one level out.
 */
object Dawn {
    /** the section last opened from the menu: Back to the menu lands on it */
    var last: Screen? = null
    /** Home opened from the menu (its rows full screen); Back closes it and shows the menu again */
    var homeOpen by mutableStateOf(false)
    /** extra part of the path in the top bar (the TV group) */
    var crumb by mutableStateOf("")
    /** "3 / 124" in the top bar while channels are shown */
    var pos by mutableStateOf("")
    /** remote hints at the bottom, set by the TV screen; empty = the shell's own */
    var hints by mutableStateOf<List<Pair<String, String>>>(emptyList())
    /** the focus pill: light grey with dark text, the same in every New Dawn list */
    val pill = Color(0xFFD5DBE3)
    val onPill = Color(0xFF111A26)
    /** New Dawn starts in its misty night colours; black and white and OLED stay if you already use them */
    fun pickTheme() { if (Look.theme != "ponoc" && Look.theme != "bw" && Look.theme != "oled") Look.theme = "ponoc" }
}

/** Frosted grey-blue panel used everywhere in New Dawn. */
fun Modifier.dawnGlass(r: Dp = 18.dp): Modifier = this.clip(Rnd(r)).background(Color(0x8C0E1724)).border(1.dp, Color(0x1FFFFFFF), Rnd(r))

/** Black and white theme: the same misty night without colour. */
private fun dc(c: Color): Color {
    if (Look.theme != "bw") return c
    val l = c.red * 0.3f + c.green * 0.59f + c.blue * 0.11f
    return Color(l, l, l, c.alpha)
}

/** Misty night: blue-grey fog, a house corner on the right and a glowing street lamp. Drawn, not a picture: sharp on every screen. */
@Composable
fun DawnBackground() {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        drawRect(Brush.verticalGradient(listOf(dc(Color(0xFF16243A)), dc(Color(0xFF24364C)), dc(Color(0xFF0D1622))), startY = 0f, endY = h))
        val lx = w * 0.64f; val ly = h * 0.36f
        drawRect(Brush.radialGradient(listOf(dc(Color(0x38A5B9D0)), dc(Color(0x00A5B9D0))), center = Offset(lx, ly), radius = w * 0.42f))
        // house: roof slab and wall in the fog
        drawPath(Path().apply { moveTo(w * 0.78f, h * 0.38f); lineTo(w, h * 0.02f); lineTo(w, h * 0.10f); lineTo(w * 0.83f, h * 0.40f); close() }, dc(Color(0x8C0C1420)))
        drawPath(Path().apply { moveTo(w * 0.83f, h * 0.40f); lineTo(w, h * 0.10f); lineTo(w, h * 0.86f); lineTo(w * 0.83f, h * 0.86f); close() },
            Brush.verticalGradient(listOf(dc(Color(0x8C141E2C)), dc(Color(0x40141E2C))), startY = h * 0.1f, endY = h * 0.86f))
        // lamp post
        val u = h / 720f
        drawRect(Brush.verticalGradient(listOf(dc(Color(0xF2121820)), dc(Color(0x26121820))), startY = ly, endY = h * 0.95f),
            topLeft = Offset(lx - 3 * u, ly + 28 * u), size = Size(6 * u, h * 0.6f))
        drawRect(dc(Color(0x99121820)), topLeft = Offset(lx - 7 * u, h * 0.70f), size = Size(14 * u, 4 * u))
        // warm halo
        drawRect(Brush.radialGradient(0f to dc(Color(0x8CFFD696)), 0.35f to dc(Color(0x2EFFC882)), 1f to dc(Color(0x00FFC882)), center = Offset(lx, ly), radius = w * 0.10f))
        // lantern: glass, roof, base
        drawPath(Path().apply { moveTo(lx - 14 * u, ly - 16 * u); lineTo(lx + 14 * u, ly - 16 * u); lineTo(lx + 10 * u, ly + 22 * u); lineTo(lx - 10 * u, ly + 22 * u); close() }, dc(Color(0xFFFFE2B0)))
        drawPath(Path().apply { moveTo(lx - 20 * u, ly - 16 * u); lineTo(lx, ly - 30 * u); lineTo(lx + 20 * u, ly - 16 * u); close() }, dc(Color(0xFF1A222D)))
        drawRect(dc(Color(0xFF1A222D)), topLeft = Offset(lx - 12 * u, ly + 22 * u), size = Size(24 * u, 6 * u))
        drawRect(dc(Color(0xFF1A222D)), topLeft = Offset(lx - 1.5f * u, ly - 16 * u), size = Size(3 * u, 38 * u))
        drawCircle(Brush.radialGradient(listOf(dc(Color(0xE6FFF0D2)), dc(Color(0x00FFF0D2))), center = Offset(lx, ly + 4 * u), radius = 22 * u), radius = 22 * u, center = Offset(lx, ly + 4 * u))
        // ground fog
        drawRect(Brush.verticalGradient(0f to dc(Color(0x00788799)), 0.5f to dc(Color(0x1A788799)), 1f to dc(Color(0x80080C14)), startY = h * 0.6f, endY = h),
            topLeft = Offset(0f, h * 0.6f), size = Size(w, h * 0.4f))
    }
}

private data class DawnItem(val screen: Screen, val icon: Int, val label: String, val badge: Int = 0)

/** The whole app in New Dawn: the menu alone on the start screen; every section fills the screen under the top bar. */
@Composable
fun DawnShell(top: Screen, nav: Nav, content: @Composable () -> Unit) {
    val menu = top == Screen.Home && !Dawn.homeOpen
    BackHandler(enabled = top == Screen.Home && Dawn.homeOpen) { Dawn.homeOpen = false }
    Box(Modifier.fillMaxSize().background(Color(0xFF16243A))) {
        DawnBackground()
        Column(Modifier.fillMaxSize()) {
            DawnTopBar(top, menu)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (menu) DawnMenu(nav) else content()
            }
            val hints = when {
                top == Screen.Tv && Dawn.hints.isNotEmpty() -> Dawn.hints
                menu -> listOf("▲▼" to "Navigacija", "OK" to "Odaberi")
                else -> listOf("▲▼" to "Navigacija", "OK" to "Odaberi", "Back" to "Nazad")
            }
            DawnHintBar(hints)
        }
    }
}

@Composable
fun DawnHintBar(hints: List<Pair<String, String>>) {
    Row(Modifier.fillMaxWidth().height(46.dp).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(30.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically) {
        hints.forEach { (key, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(Modifier.clip(Rnd(6.dp)).border(1.dp, Color(0x99FFFFFF), Rnd(6.dp)).padding(horizontal = 7.dp, vertical = 1.dp)) {
                    T(key, 13.sp, Color.White, FontWeight.SemiBold)
                }
                T(label, 15.sp, Color(0xFFD5DBE3))
            }
        }
    }
}

@Composable
private fun DawnMenu(nav: Nav) {
    val items = listOf(
        DawnItem(Screen.Home, R.drawable.lc_house, "Početna"),
        DawnItem(Screen.Tv, R.drawable.lc_tv, "TV", Notes.unread("reminder")),
        DawnItem(Screen.Movies, R.drawable.lc_film, "Filmovi", NewContent.count("vod")),
        DawnItem(Screen.Series, R.drawable.lc_layers, "Serije", NewContent.count("series")),
        DawnItem(Screen.Sports, R.drawable.lc_trophy, "Sport"),
        DawnItem(Screen.Catchup, R.drawable.lc_history, "Catch-up"),
        DawnItem(Screen.Search, R.drawable.lc_search, "Pretraga"),
        DawnItem(Screen.Settings, R.drawable.lc_settings, "Postavke", if (Updater.available != null) 1 else 0),
    )
    val frs = remember { items.map { FocusRequester() } }
    DisposableEffect(Unit) { onDispose { Chrome.inRail = false } }
    // back from a section: the focus waits on that section's item (first start: TV)
    LaunchedEffect(Unit) {
        val i = items.indexOfFirst { it.screen == (Dawn.last ?: Screen.Tv) }.coerceAtLeast(0)
        delay(120); frs[i].tryFocus()
    }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
    // every item always fits on the screen, Settings included: rows get smaller on small screens or big text
    val rowH = ((maxHeight - 6.dp - 8.dp - 24.dp) / items.size - 4.dp).coerceIn(36.dp, 54.dp)
    val big = rowH >= 48.dp
    Column(Modifier.padding(start = 34.dp, top = 6.dp, bottom = 8.dp).width(330.dp)
        .dawnGlass().onFocusChanged { Chrome.inRail = it.hasFocus }.padding(horizontal = 10.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEachIndexed { i, it ->
            Box {
                Focusable(
                    modifier = Modifier.fillMaxWidth().height(rowH),
                    onClick = {
                        Chrome.inRail = false
                        Dawn.last = it.screen
                        if (it.screen == Screen.Home) Dawn.homeOpen = true else nav.root(it.screen)
                    },
                    focusRequester = frs[i],
                    shape = Rnd(12.dp),
                    normal = Color.Transparent,
                    focused = Dawn.pill,
                    focusBorderColor = Color.Transparent,
                    zoom = 1.0f,
                ) { f ->
                    val c = if (f) Dawn.onPill else Color(0xFFEFF2F6)
                    Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Ico(it.icon, if (big) 27.dp else 23.dp, c)
                        T(it.label, if (big) 19.sp else 17.sp, c, if (f) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                    }
                }
                if (it.badge > 0) Box(Modifier.align(Alignment.TopEnd).offset(x = (-10).dp, y = 8.dp)) { Bell(it.badge) }
            }
        }
    }
    DawnPlaylists(Modifier.align(Alignment.BottomEnd).padding(end = 38.dp, bottom = 10.dp))
    }
}

/** Active playlists and how many days they still have, small and calm (amber under 7 days, red when expired). */
@Composable
private fun DawnPlaylists(modifier: Modifier) {
    val ctx = LocalContext.current
    val pls = remember { Store.actives(ctx) }
    if (pls.isEmpty()) return
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        pls.take(4).forEach { p ->
            val ex = expiryText(p.exp)
            val left = dawnDaysLeft(p.exp)
            val col = when { ex?.second == 2 -> Color(0xFFF87171); ex?.second == 1 -> Color(0xFFFBBF24); else -> Color(0xFFD5DBE3) }
            Row(Modifier.dawnGlass(12.dp).padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xFF4ADE80)))
                T(p.name, 14.sp, Color.White, FontWeight.SemiBold)
                if (left != null) { T("·", 14.sp, Color(0xFF9AA6B5)); T(left, 14.sp, col) }
            }
        }
    }
}

/** "23 dana", "Bez isteka" or "Istekla" for a playlist's expiry (unix seconds or "0"). */
private fun dawnDaysLeft(exp: String): String? {
    if (exp.isBlank()) return null
    if (exp == "0") return tr("Bez isteka")
    val sec = exp.toLongOrNull() ?: return null
    val days = ((sec * 1000 - System.currentTimeMillis()) / 86_400_000L).toInt()
    return if (days < 0) tr("Istekla") else trf("još %s dana", days)
}

@Composable
private fun DawnTopBar(top: Screen, menu: Boolean) {
    val section = if (menu) "" else when (top) {
        Screen.Home -> "Početna"; Screen.Tv -> "TV"; Screen.Movies, is Screen.MovieDetail -> "Filmovi"; Screen.Series, is Screen.SeriesDetail -> "Serije"
        Screen.Catchup -> "Catch-up"; Screen.Sports -> "Sport"; Screen.Search -> "Pretraga"
        Screen.Settings, Screen.AddPlaylist -> "Postavke"; else -> ""
    }
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(15_000) } }
    val sep: @Composable () -> Unit = { Box(Modifier.width(1.dp).height(20.dp).background(Color(0x4DFFFFFF))) }
    Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 38.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Ico(R.drawable.lc_play, 30.dp, Color.White)
        T("PRETORIAN TV", 21.sp, Color.White, FontWeight.Bold)
        if (section.isNotEmpty()) {
            sep()
            if (Dawn.crumb.isEmpty()) T(section, 18.sp, Color.White, FontWeight.SemiBold)
            else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                T(section, 17.sp, Color(0xFFD5DBE3))
                T("›", 17.sp, Color(0xFFD5DBE3))
                T(Dawn.crumb, 17.sp, Color.White, FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.weight(1f))
        if (top == Screen.Tv && Dawn.pos.isNotEmpty()) { T(Dawn.pos, 15.sp, Color(0xFFD5DBE3)); sep() }
        T(SimpleDateFormat("EEE, d MMM", Locale(Look.lang)).format(now), 15.sp, Color(0xFFD5DBE3))
        sep()
        T(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), 17.sp, Color.White, FontWeight.Bold)
    }
}

/** Preview area: the picture after the first OK, before that a calm box with a play mark. */
@Composable
fun DawnPreview(modifier: Modifier) {
    val playing = PreviewSel.ch
    Box(modifier.aspectRatio(16f / 9f).dawnGlass(14.dp), contentAlignment = Alignment.Center) {
        if (playing != null) PreviewBox(playing, Modifier.fillMaxSize())
        else Ico(R.drawable.lc_play, 54.dp, Color(0x99D5DBE3))
    }
}

/** Now and next of a channel, with a progress line, under the preview. */
@Composable
fun DawnNowNext(ch: Channel?, modifier: Modifier) {
    val ctx = LocalContext.current
    Column(modifier.dawnGlass(14.dp)) {
        if (ch == null) { Box(Modifier.fillMaxWidth().padding(16.dp)) { T("Stani na kanal da vidiš program", 14.sp, C.Muted) }; return@Column }
        val st by load(ch.url, EpgShift.rev) { Store.byId(ctx, ch.pid)?.let { Repo.epg(it, ch, 3) } ?: emptyList() }
        val list = (st as? Load.Ok)?.value.orEmpty()
        val t = System.currentTimeMillis()
        val cur = list.firstOrNull { it.start <= t && it.end > t }
        val next = list.firstOrNull { it.start > t }
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            T(ch.name, 18.sp, Color.White, FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(cur?.title ?: if (st is Load.Loading) tr("Učitavam program…") else tr("Nema EPG vodiča"), 14.sp, Color(0xFFC3CCD8), modifier = Modifier.weight(1f))
                if (cur != null) T(cur.times(), 14.sp, Color(0xFFC3CCD8))
            }
            if (cur != null) Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(4.dp).clip(Rnd(2.dp)).background(Color(0x33FFFFFF))) {
                Box(Modifier.fillMaxWidth(cur.progress()).height(4.dp).background(Color(0xFFB9D0F0)))
            }
        }
        if (next != null) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x1FFFFFFF)))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                T("Slijedi:", 14.sp, Color(0xFFC3CCD8))
                T(next.title, 14.sp, Color.White, FontWeight.Medium, modifier = Modifier.weight(1f))
                T(next.times(), 14.sp, Color(0xFFC3CCD8))
            }
        }
    }
}

/** Bottom options of the channels level: Favourite, EPG, Audio, Subtitles, Options. */
@Composable
fun DawnOptions(ch: Channel?, onOptions: () -> Unit, modifier: Modifier) {
    var fav by remember { mutableStateOf<Channel?>(null) }
    var epg by remember { mutableStateOf<Channel?>(null) }
    var tracks by remember { mutableStateOf(0) }
    Row(modifier.dawnGlass(14.dp).padding(5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        @Composable fun Opt(icon: Int, label: String, onClick: () -> Unit) {
            Focusable(modifier = Modifier.weight(1f).fillMaxHeight(), onClick = onClick, shape = Rnd(10.dp), normal = Color.Transparent,
                focused = Dawn.pill, focusBorderColor = Color.Transparent, zoom = 1.0f) { f ->
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically)) {
                    Ico(icon, 24.dp, if (f) Dawn.onPill else Color(0xFFEFF2F6))
                    T(label, 12.sp, if (f) Dawn.onPill else Color(0xFFD5DBE3), FontWeight.Medium)
                }
            }
        }
        Opt(R.drawable.lc_star, "Favorit") { ch?.let { fav = it } }
        Opt(R.drawable.lc_calendar, "EPG") { ch?.let { epg = it } }
        Opt(R.drawable.lc_volume, "Audio") { tracks = androidx.media3.common.C.TRACK_TYPE_AUDIO }
        Opt(R.drawable.lc_captions, "Titlovi") { tracks = androidx.media3.common.C.TRACK_TYPE_TEXT }
        Opt(R.drawable.lc_settings, "Opcije") { onOptions() }
    }
    fav?.let { c -> FavPicker(c) { fav = null } }
    epg?.let { c -> Popup(c.name, { epg = null }, width = 720.dp) { EpgList(c, 16, Modifier.fillMaxWidth().height(440.dp)) } }
    if (tracks != 0) DawnTracks(tracks) { tracks = 0 }
}

/** Audio or subtitle tracks of the channel in the preview. */
@Composable
private fun DawnTracks(type: Int, onDismiss: () -> Unit) {
    val p = Preview.player
    val text = type == androidx.media3.common.C.TRACK_TYPE_TEXT
    val groups = remember(p) { p?.currentTracks?.groups?.filter { it.type == type }.orEmpty() }
    Popup(if (text) "Titlovi" else "Audio", onDismiss) {
        if (p == null || PreviewSel.ch == null) T("Prvo pritisni OK na kanalu za pregled.", 14.sp, C.Soft, maxLines = 2)
        else if (groups.isEmpty()) T(if (text) "Ovaj kanal nema titlova." else "Ovaj kanal ima samo jedan zvuk.", 14.sp, C.Soft, maxLines = 2)
        else {
            if (text) OptionRow("Isključeno", R.drawable.lc_x) {
                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(type, true).build(); onDismiss()
            }
            groups.forEach { g ->
                for (i in 0 until g.length) {
                    val f = g.getTrackFormat(i)
                    val name = listOfNotNull(f.label, f.language?.uppercase()).distinct().joinToString(" · ").ifBlank { "#${i + 1}" }
                    OptionRow(name, if (g.isTrackSelected(i)) R.drawable.lc_check else if (text) R.drawable.lc_captions else R.drawable.lc_volume,
                        color = if (g.isTrackSelected(i)) C.Green else null) {
                        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(type, false)
                            .setOverrideForType(androidx.media3.common.TrackSelectionOverride(g.mediaTrackGroup, i)).build()
                        onDismiss()
                    }
                }
            }
        }
    }
}

/** New Dawn search results: filters with counts on the left (All, TV channels, Movies, Series, Sport), one clear list on the right. */
@Composable
fun DawnSearchResults(
    query: String, tv: List<Hit>, movies: List<Hit>, shows: List<Hit>, sport: List<SportEvent>, first: FocusRequester,
    onHit: (Hit) -> Unit, onMenu: (Hit) -> Unit, onSport: (SportEvent) -> Unit, modifier: Modifier,
) {
    var filt by remember { mutableStateOf(SearchMem.open ?: "all") }
    LaunchedEffect(filt) { SearchMem.open = filt }
    val filters = listOf(
        Triple("all", "Svi rezultati", tv.size + movies.size + shows.size + sport.size),
        Triple("tv", "TV kanali", tv.size), Triple("vod", "Filmovi", movies.size),
        Triple("series", "Serije", shows.size), Triple("sport", "Sport", sport.size),
    )
    val icons = mapOf("all" to R.drawable.lc_search, "tv" to R.drawable.lc_tv, "vod" to R.drawable.lc_film, "series" to R.drawable.lc_layers, "sport" to R.drawable.lc_trophy)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.width(270.dp).fillMaxHeight().dawnGlass().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            filters.forEach { (id, label, n) ->
                Focusable(onClick = { filt = id }, onFocus = { filt = id }, shape = Rnd(12.dp), normal = if (filt == id) Color(0x26FFFFFF) else Color.Transparent,
                    focused = Dawn.pill, focusBorderColor = Color.Transparent, zoom = 1f, modifier = Modifier.fillMaxWidth().height(50.dp)) { f ->
                    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Ico(icons[id] ?: R.drawable.lc_search, 22.dp, if (f) Dawn.onPill else Color(0xFFEFF2F6))
                        T(label, 16.sp, if (f) Dawn.onPill else Color(0xFFEFF2F6), if (f || filt == id) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                        T("($n)", 14.sp, if (f) Dawn.onPill else Color(0xFF9AA6B5))
                    }
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            T(trf("Rezultati za: „%s“", query), 16.sp, Color(0xFFD5DBE3), FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
            val rows: List<Any> = when (filt) {
                "tv" -> tv; "vod" -> movies; "series" -> shows; "sport" -> sport
                else -> buildList { addAll(shows.take(20)); addAll(movies.take(20)); addAll(tv.take(20)); addAll(sport.take(10)) }
            }
            if (rows.isEmpty()) { T("Ništa nije pronađeno", 15.sp, C.Soft); return@Column }
            androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(rows.size) { i ->
                    val r = rows[i]
                    Focusable(onClick = { if (r is Hit) onHit(r) else if (r is SportEvent) onSport(r) }, onMenu = { if (r is Hit) onMenu(r) },
                        focusRequester = if (i == 0) first else null, shape = Rnd(12.dp), normal = Color(0x590E1724), focused = Dawn.pill,
                        focusBorderColor = Color.Transparent, zoom = 1f, modifier = Modifier.fillMaxWidth().height(76.dp)) { f ->
                        val main = if (f) Dawn.onPill else Color.White
                        val sub = if (f) Dawn.onPill.copy(alpha = 0.7f) else Color(0xFFC3CCD8)
                        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            val (title, line, type) = when (r) {
                                is Hit.Show -> Triple(cleanTitle(r.s.name).first, tr("Serija"), "Serija")
                                is Hit.Movie -> Triple(cleanTitle(r.v.name).first, tr("Film"), "Film")
                                is Hit.Ch -> Triple(r.ch.name, nowOf(r.ch, 600)?.title ?: (tr("Kanal") + " ${r.ch.num}"), "TV")
                                is Hit.Prog -> Triple(r.e.title, r.ch.name + " · " + r.e.times(), "TV")
                                is SportEvent -> Triple(if (r.home != null && r.away != null) r.home.name + " – " + r.away.name else r.title, r.league, "Sport")
                                else -> Triple("", "", "")
                            }
                            val img = when (r) { is Hit.Show -> r.s.cover; is Hit.Movie -> r.v.poster; else -> null }
                            Box(Modifier.width(104.dp).height(60.dp).clip(Rnd(8.dp)).background(Color(0x33000000)), contentAlignment = Alignment.Center) {
                                when {
                                    img != null -> OnyxImage(img, title, Modifier.fillMaxSize(), maxW = 220)
                                    r is Hit.Ch -> ChannelLogo(r.ch, 40.dp)
                                    r is Hit.Prog -> ChannelLogo(r.ch, 40.dp)
                                    else -> Ico(R.drawable.lc_trophy, 26.dp, main)
                                }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                T(title, 16.sp, main, FontWeight.SemiBold)
                                T(line, 13.sp, sub)
                            }
                            Box(Modifier.clip(Rnd(10.dp)).border(1.dp, if (f) Dawn.onPill.copy(alpha = 0.5f) else Color(0x4DFFFFFF), Rnd(10.dp)).padding(horizontal = 12.dp, vertical = 5.dp)) {
                                T(type, 12.sp, main, FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
}
