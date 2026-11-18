package com.pretorian.tv

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
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
 * New Dawn layout (Settings › Appearance › TV layout): one level on the screen at a time.
 * Menu › groups › channels with a big preview, EPG and options › full screen. OK goes one level in and the
 * previous level disappears; Back (and ◀ in TV) goes exactly one level out, with the focus on where you came from.
 */
object Dawn {
    /** the section last opened from the menu: Back to the menu lands on it */
    var last: Screen? = null
    /** extra part of the path in the top bar (the TV group) */
    var crumb by mutableStateOf("")
    /** New Dawn starts in midnight blue; black and white and OLED stay if you already use them */
    fun pickTheme() { if (Look.theme != "ponoc" && Look.theme != "bw" && Look.theme != "oled") Look.theme = "ponoc" }
}

/** Frosted panel used everywhere in New Dawn. */
fun Modifier.dawnGlass(r: Dp = 22.dp): Modifier = this.clip(Rnd(r)).background(C.Rail).border(1.dp, C.Line, Rnd(r))

/** Calm background: theme colour, a soft glow and two misty mountain lines. Lite: only the colour. */
@Composable
fun DawnBackground() {
    if (Perf.lite) return
    val glow = lerp(C.Bg, C.Red, 0.22f)
    val far = lerp(C.Bg, C.Text, 0.10f)
    val near = lerp(C.Bg, C.Text, 0.05f)
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        drawRect(Brush.radialGradient(listOf(glow.copy(alpha = 0.55f), Color.Transparent), center = Offset(w * 0.72f, h * 0.26f), radius = w * 0.55f))
        fun ridge(points: List<Pair<Float, Float>>, color: Color) {
            val p = Path()
            p.moveTo(0f, h * points.first().second)
            for (i in 1 until points.size) {
                val (x0, y0) = points[i - 1]; val (x1, y1) = points[i]
                p.quadraticBezierTo(w * x0 + (w * (x1 - x0)) / 2f, h * minOf(y0, y1) - h * 0.04f, w * x1, h * y1)
            }
            p.lineTo(w, h); p.lineTo(0f, h); p.close()
            drawPath(p, Brush.verticalGradient(listOf(color, C.Bg), startY = h * 0.45f, endY = h))
        }
        ridge(listOf(0f to 0.72f, 0.18f to 0.55f, 0.37f to 0.62f, 0.55f to 0.48f, 0.74f to 0.6f, 0.88f to 0.5f, 1f to 0.6f), far)
        ridge(listOf(0f to 0.84f, 0.22f to 0.72f, 0.45f to 0.8f, 0.66f to 0.7f, 0.86f to 0.8f, 1f to 0.74f), near)
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.035f), Color.Transparent), startY = h * 0.55f, endY = h * 0.75f))
    }
}

private data class DawnItem(val screen: Screen, val icon: Int, val label: String, val badge: Int = 0)

/** The whole app in New Dawn: the menu only on Home; every other screen fills the screen under a slim top bar. */
@Composable
fun DawnShell(top: Screen, nav: Nav, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(C.BgBrush)) {
        DawnBackground()
        if (top == Screen.Home) Row(Modifier.fillMaxSize()) {
            DawnMenu(nav)
            Box(Modifier.weight(1f).fillMaxHeight()) { content() }
        } else Column(Modifier.fillMaxSize()) {
            DawnTopBar(top)
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
    }
}

@Composable
private fun DawnMenu(nav: Nav) {
    val items = listOf(
        DawnItem(Screen.Home, R.drawable.lc_house, "Početna"),
        DawnItem(Screen.Tv, R.drawable.lc_tv, "TV", Notes.unread("reminder")),
        DawnItem(Screen.Movies, R.drawable.lc_film, "Filmovi", NewContent.count("vod")),
        DawnItem(Screen.Series, R.drawable.lc_monitor, "Serije", NewContent.count("series")),
        DawnItem(Screen.Catchup, R.drawable.lc_history, "Catch-up"),
        DawnItem(Screen.Sports, R.drawable.lc_trophy, "Sport"),
        DawnItem(Screen.Search, R.drawable.lc_search, "Pretraga"),
        DawnItem(Screen.Settings, R.drawable.lc_settings, "Postavke", if (Updater.available != null) 1 else 0),
    )
    val frs = remember { items.map { FocusRequester() } }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    DisposableEffect(Unit) { onDispose { Chrome.inRail = false } }
    // back from a section: the focus waits on that section's item
    LaunchedEffect(Unit) {
        val i = items.indexOfFirst { it.screen == Dawn.last }.coerceAtLeast(0)
        delay(120); frs[i].tryFocus()
    }
    Column(Modifier.width(320.dp).fillMaxHeight().padding(start = 28.dp, top = 24.dp, bottom = 24.dp)
        .dawnGlass(26.dp).onFocusChanged { Chrome.inRail = it.hasFocus }.padding(horizontal = 14.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LogoTile(34.dp)
            T("PRETORIAN", 18.sp, C.Text, FontWeight.Bold, modifier = Modifier.weight(1f))
            ProfileButton(34.dp)
        }
        items.forEachIndexed { i, it ->
            if (it.screen == Screen.Settings) Spacer(Modifier.weight(1f))
            Box {
                Focusable(
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    onClick = {
                        Chrome.inRail = false
                        Dawn.last = it.screen
                        if (it.screen == Screen.Home) focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Right) else nav.root(it.screen)
                    },
                    focusRequester = frs[i],
                    shape = Rnd(16.dp),
                    normal = Color.Transparent,
                    focused = C.Red,
                    zoom = 1.02f,
                ) { f ->
                    val c = if (f) C.OnRed else C.Soft
                    Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Ico(it.icon, 28.dp, c)
                        T(it.label, 20.sp, c, if (f) FontWeight.SemiBold else FontWeight.Medium, modifier = Modifier.weight(1f))
                    }
                }
                if (it.badge > 0) Box(Modifier.align(Alignment.TopEnd).offset(x = (-8).dp, y = 6.dp)) { Bell(it.badge) }
            }
        }
    }
}

@Composable
private fun DawnTopBar(top: Screen) {
    val section = when (top) {
        Screen.Tv -> "TV"; Screen.Movies, is Screen.MovieDetail -> "Filmovi"; Screen.Series, is Screen.SeriesDetail -> "Serije"
        Screen.Catchup -> "Catch-up"; Screen.Sports -> "Sport"; Screen.Search -> "Pretraga"
        Screen.Settings, Screen.AddPlaylist -> "Postavke"; else -> ""
    }
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(15_000) } }
    Row(Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 34.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        LogoTile(28.dp)
        T("PRETORIAN", 16.sp, C.Text, FontWeight.Bold)
        if (section.isNotEmpty()) {
            Box(Modifier.width(1.dp).height(22.dp).background(C.Line))
            T(section, 16.sp, if (Dawn.crumb.isEmpty()) C.Text else C.Muted, FontWeight.SemiBold)
            if (Dawn.crumb.isNotEmpty()) {
                Ico(R.drawable.lc_chevron_right, 16.dp, C.Muted)
                T(Dawn.crumb, 16.sp, C.Text, FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
            }
        }
        Spacer(Modifier.weight(1f))
        T(SimpleDateFormat("EEE, d. MMM", Locale(Look.lang)).format(now), 15.sp, C.Muted)
        T(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), 18.sp, C.Text, FontWeight.SemiBold)
    }
}

/** Big text at the bottom right of the groups level: which group you stand on. */
@Composable
fun DawnHero(title: String, sub: String?, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.End) {
        T(title, 46.sp, C.Text, FontWeight.Light)
        if (sub != null) T(sub, 16.sp, C.Muted)
    }
}

/** Preview area: the picture after the first OK, before that the channel's logo and "OK to watch". */
@Composable
fun DawnPreview(focused: Channel?, modifier: Modifier) {
    val playing = PreviewSel.ch
    Box(modifier, contentAlignment = Alignment.Center) {
        if (playing != null) PreviewBox(playing, Modifier.fillMaxHeight())
        else Box(Modifier.fillMaxHeight().aspectRatio(16f / 9f).dawnGlass(18.dp), contentAlignment = Alignment.Center) {
            if (focused != null) Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) { ChannelLogo(focused.logo, focused.name) }
                T(focused.name, 20.sp, C.Text, FontWeight.SemiBold)
                Row(Modifier.clip(Rnd(12.dp)).background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Ico(R.drawable.lc_play, 18.dp, C.Text)
                    T("OK = pregled · OK još jednom = cijeli ekran", 14.sp, C.Text)
                }
            } else T("Izaberi kanal", 15.sp, C.Muted)
        }
    }
}

/** Now and next of a channel, with a progress line. */
@Composable
fun DawnNowNext(ch: Channel?, modifier: Modifier) {
    val ctx = LocalContext.current
    Row(modifier.dawnGlass(18.dp).padding(horizontal = 22.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        if (ch == null) { T("Stani na kanal da vidiš program", 14.sp, C.Muted); return@Row }
        val st by load(ch.url, EpgShift.rev) { Store.byId(ctx, ch.pid)?.let { Repo.epg(it, ch, 3) } ?: emptyList() }
        val list = (st as? Load.Ok)?.value.orEmpty()
        val t = System.currentTimeMillis()
        val cur = list.firstOrNull { it.start <= t && it.end > t }
        val next = list.firstOrNull { it.start > t }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            T(cur?.title ?: ch.name, 19.sp, C.Text, FontWeight.SemiBold)
            T(if (cur != null) ch.name + " · " + cur.times() else if (st is Load.Loading) tr("Učitavam program…") else tr("Nema EPG vodiča"), 13.sp, C.Muted)
            if (cur != null) Box(Modifier.width(260.dp).height(4.dp).clip(Rnd(2.dp)).background(C.CardHi)) {
                Box(Modifier.fillMaxWidth(cur.progress()).height(4.dp).background(C.Red))
            }
        }
        if (next != null) {
            Box(Modifier.width(1.dp).height(40.dp).background(C.Line))
            Column(horizontalAlignment = Alignment.End) {
                T("Slijedi", 12.sp, C.Muted)
                T(next.title, 15.sp, C.Text, FontWeight.Medium)
                T(next.times(), 12.sp, C.Muted)
            }
        }
        qualityOf(ch.name)?.let { Chip(it, C.Card, C.Soft) }
    }
}

/** Bottom options of the channels level: big, clear icons. */
@Composable
fun DawnOptions(ch: Channel?, onFull: () -> Unit, onGroups: () -> Unit, modifier: Modifier) {
    var fav by remember { mutableStateOf<Channel?>(null) }
    var epg by remember { mutableStateOf<Channel?>(null) }
    Row(modifier.dawnGlass(18.dp).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        @Composable fun Opt(icon: Int, label: String, onClick: () -> Unit) {
            Focusable(modifier = Modifier.weight(1f).fillMaxHeight(), onClick = onClick, shape = Rnd(14.dp), normal = Color.Transparent,
                focused = C.Red, zoom = 1.03f) { f ->
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically)) {
                    Ico(icon, 26.dp, if (f) C.OnRed else C.Soft)
                    T(label, 13.sp, if (f) C.OnRed else C.Soft, FontWeight.Medium)
                }
            }
        }
        Opt(R.drawable.lc_star, "Favoriti") { ch?.let { fav = it } }
        Opt(R.drawable.lc_calendar, "EPG") { ch?.let { epg = it } }
        Opt(R.drawable.lc_maximize, "Cijeli ekran") { onFull() }
        Opt(R.drawable.lc_folder, "Grupe") { onGroups() }
    }
    fav?.let { c -> FavPicker(c) { fav = null } }
    epg?.let { c -> Popup(c.name, { epg = null }, width = 720.dp) { EpgList(c, 16, Modifier.fillMaxWidth().height(440.dp)) } }
}
