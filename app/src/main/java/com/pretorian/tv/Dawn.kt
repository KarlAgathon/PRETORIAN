package com.pretorian.tv

import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.mutableIntStateOf
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
    /** extra part of the path in the top bar (the TV group) */
    var crumb by mutableStateOf("")
    /** "3 / 124" in the top bar while channels are shown */
    var pos by mutableStateOf("")
    /** remote hints at the bottom, set by the TV screen; empty = the shell's own */
    var hints by mutableStateOf<List<Pair<String, String>>>(emptyList())
    /** the focus pill: light grey with dark text, the same in every New Dawn list */
    val pill get() = Silver.Focus
    val onPill get() = Silver.OnFocus
}

/** Charcoal panel used everywhere (Monochrome Silver). */
fun Modifier.dawnGlass(r: Dp = 18.dp): Modifier = this.clip(Rnd(r)).background(Silver.Panel).border(1.dp, Silver.Border, Rnd(r))

/** Glass card of the redesign: rgba(20,24,28,.55) with a 1px hairline. */
fun Modifier.glassCard(r: Dp = 16.dp): Modifier = this.clip(Rnd(r)).background(Color(0x8C14181C)).border(1.dp, Color(0x1FFFFFFF), Rnd(r))


/** PRETORIAN PURE GRAPHITE: the one and only background of the whole app (every screen goes through DawnShell),
 *  a static 16:9 image, centre-cropped, never stretched. Fullscreen video is the only exception. */
@Composable
fun DawnBackground() {
    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.pretorian_graphite), null, Modifier.fillMaxSize(),
        contentScale = androidx.compose.ui.layout.ContentScale.Crop)
    // Izgled › Tema: "Tamnija" darkens the same picture (no new background)
    if (LookOpts.darker) Box(Modifier.fillMaxSize().background(Color(0x66000000)))
}

private data class DawnItem(val screen: Screen, val icon: Int, val label: String, val badge: Int = 0, val kind: String? = null, val go: (() -> Unit)? = null)

/** The whole app: on Home only the left menu (nothing else); every section replaces it and fills the screen. */
@Composable
fun DawnShell(top: Screen, nav: Nav, content: @Composable () -> Unit) {
    val menu = top == Screen.Home
    Box(Modifier.fillMaxSize().background(Silver.Background)) {
        DawnBackground()
        // Home v2.0: its own layout (navigation column + status bar), nothing else on the screen
        if (menu) { HomeV2(nav); OfflineNote(Modifier.align(Alignment.BottomEnd).padding(end = 34.dp, bottom = 10.dp)); return@Box }
        // the single movie / series view fills the whole screen with its picture (spec 03 / 04)
        if (top is Screen.MovieDetail || top is Screen.SeriesDetail) { content(); return@Box }
        Column(Modifier.fillMaxSize()) {
            DawnTopBar(top, menu, nav)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (menu) DawnMenu(nav) else content()
                OfflineNote(Modifier.align(Alignment.BottomEnd).padding(end = 34.dp, bottom = 6.dp))
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

/** No internet: one small calm line in the lower right corner. Never a dialog, never steals focus, never stops a stream that still plays. */
@Composable
fun OfflineNote(modifier: Modifier) {
    val ctx = LocalContext.current
    var online by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            online = try {
                val cm = ctx.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                val caps = cm.getNetworkCapabilities(cm.activeNetwork)
                caps != null && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } catch (_: Exception) { true }
            delay(5_000)
        }
    }
    if (online) return
    Row(modifier.dawnGlass(10.dp).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(7.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Silver.Warning))
        T("Nema interneta", 13.sp, C.Soft)
    }
}

@Composable
fun DawnHintBar(hints: List<Pair<String, String>>) {
    Row(Modifier.fillMaxWidth().height(46.dp).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(30.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically) {
        hints.forEach { (key, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(Modifier.clip(Rnd(6.dp)).border(1.dp, Silver.Border, Rnd(6.dp)).padding(horizontal = 7.dp, vertical = 1.dp)) {
                    // the Back key is drawn as an arrow, so "Back  Back" never shows twice in English
                    T(if (key == "Back") "↩" else key, 13.sp, C.Text, FontWeight.SemiBold)  // i18n-ignore
                }
                T(label, 15.sp, C.Soft)
            }
        }
    }
}

/** Home: the six sections, nothing else. A small number beside Movies / Series = new items; long OK there = Clear notifications. */
@Composable
private fun DawnMenu(nav: Nav) {
    @Suppress("UNUSED_VARIABLE") val r = NewContent.rev + Follow.rev + Watch.rev + Favs.rev + Recent.rev
    val ctx = LocalContext.current
    // shortcuts on top (approved): only when they have something in them
    val cont = remember(Watch.rev) { Watch.continueList(ctx).filter { !ContinueHidden.hidden(it.url, it.ts) } }
    val recent = remember(Recent.rev) { Recent.list().isNotEmpty() }
    val favs = remember(Favs.rev) { Favs.all(ctx).isNotEmpty() }
    val shortcuts = buildList {
        if (cont.isNotEmpty()) {
            val series = cont.none { it.series == null }
            add(DawnItem(if (series) Screen.Series else Screen.Movies, R.drawable.lc_play, "Nastavi gledati", go = { openContinue(series); nav.root(if (series) Screen.Series else Screen.Movies) }))
        }
        if (recent) add(DawnItem(Screen.Tv, R.drawable.lc_history, "Nedavno gledano", go = {
            TvMemory.group = GroupRef(null, Category(RECENT_GROUP, tr("Nedavno gledano"))); TvMemory.channel = 0; nav.root(Screen.Tv) }))
        if (favs) add(DawnItem(Screen.Tv, R.drawable.lc_star, "Favoriti", go = {
            TvMemory.group = GroupRef(null, Category(FAV_PREFIX + Favs.mainId(ctx), tr("Favoriti"))); TvMemory.channel = 0; nav.root(Screen.Tv) }))
    }
    val items = shortcuts + listOf(
        DawnItem(Screen.Tv, R.drawable.lc_tv, "TV"),
        DawnItem(Screen.Movies, R.drawable.lc_film, "Filmovi", NewContent.count("vod"), "vod"),
        DawnItem(Screen.Series, R.drawable.lc_layers, "Serije", NewContent.count("series") + Follow.fresh().size, "series"),
        DawnItem(Screen.Sports, R.drawable.lc_trophy, "Sport"),
        DawnItem(Screen.Catchup, R.drawable.lc_history, "Catch-up"),
        DawnItem(Screen.Search, R.drawable.lc_search, "Pretraga"),
        DawnItem(Screen.Settings, R.drawable.lc_settings, "Postavke", if (Updater.available != null) 1 else 0, "system"),
    )
    val frs = remember(items.size) { items.map { FocusRequester() } }
    var clear by remember { mutableStateOf<DawnItem?>(null) }
    DisposableEffect(Unit) { onDispose { Chrome.inRail = false } }
    // back from a section: the focus waits on that section's item (first start: TV)
    LaunchedEffect(Unit) {
        val i = items.indexOfFirst { it.go == null && it.screen == (Dawn.last ?: Screen.Tv) }.coerceAtLeast(0)
        delay(120); frs[i].tryFocus()
    }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
    // every entry always fits (Postavke never falls off the bottom): the rows shrink to the space there is
    val rowH = ((maxHeight - 6.dp - 8.dp - 24.dp - 4.dp * (items.size - 1)) / items.size).coerceIn(30.dp, 58.dp)
    val big = rowH >= 48.dp
    Column(Modifier.padding(start = 34.dp, top = 6.dp, bottom = 8.dp).width(330.dp)
        .dawnGlass().onFocusChanged { Chrome.inRail = it.hasFocus }.padding(horizontal = 10.dp, vertical = 12.dp)
        .then(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEachIndexed { i, it ->
            Focusable(
                modifier = Modifier.fillMaxWidth().height(rowH),
                onClick = {
                    Chrome.inRail = false
                    if (it.go != null) it.go.invoke()
                    else { Dawn.last = it.screen; nav.root(it.screen) }
                },
                onMenu = if (it.badge > 0 && it.kind != null && it.kind != "system") ({ clear = it }) else null,
                focusRequester = frs[i],
                shape = Rnd(12.dp),
                normal = Color.Transparent,
                focused = Dawn.pill,
                focusBorderColor = Color.Transparent,
                zoom = 1.0f,
            ) { f ->
                val c = if (f) Dawn.onPill else C.Text
                Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Ico(it.icon, if (big) 27.dp else 23.dp, c)
                    T(it.label, if (big) 19.sp else 17.sp, c, if (f) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                    if (it.badge > 0) Box(Modifier.clip(Pill).background(if (f) Dawn.onPill else C.Text).padding(horizontal = 8.dp, vertical = 1.dp)) {
                        T(if (it.badge > 99) "99+" else it.badge.toString(), 13.sp, if (f) Dawn.pill else Silver.OnFocus, FontWeight.Bold)
                    }
                }
            }
        }
    }
    }
    clear?.let { item ->
        Popup(item.label, onDismiss = { clear = null }) {
            OptionRow("Obriši obavještenja", R.drawable.lc_trash_2) {
                item.kind?.let { Notes.clearKind(it) }   // FIX 2: only the notices; "Nedavno dodano" keeps its titles
                clear = null
            }
        }
    }
}

@Composable
private fun DawnTopBar(top: Screen, menu: Boolean, nav: Nav) {
    val section = if (menu) "" else when (top) {
        Screen.Tv -> "TV"; Screen.Movies, is Screen.MovieDetail -> "Filmovi"; Screen.Series, is Screen.SeriesDetail -> "Serije"
        Screen.Catchup -> "Catch-up"; Screen.Sports -> "Sport"; Screen.Search -> "Pretraga"
        Screen.Settings -> "Postavke"
        // first-time setup has no section title
        Screen.AddPlaylist -> if (Store.playlists(LocalContext.current).isEmpty()) "" else "Postavke"
        else -> ""
    }
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(15_000) } }
    val sep: @Composable () -> Unit = { Box(Modifier.width(1.dp).height(20.dp).background(Silver.Border)) }
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 38.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (top != Screen.AddPlaylist) Store.actives(ctx).firstOrNull()?.let { PlaylistChip(it, now.time) }
        if (section.isNotEmpty()) {
            if (Dawn.crumb.isEmpty()) T(section, 18.sp, C.Text, FontWeight.SemiBold)
            else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                T(section, 17.sp, C.Soft)
                T("›", 17.sp, C.Soft)
                T(Dawn.crumb, 17.sp, C.Text, FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.weight(1f))
        if (top == Screen.Tv && Dawn.pos.isNotEmpty()) { T(Dawn.pos, 15.sp, C.Soft); sep() }
        if (top != Screen.AddPlaylist) { NoteBell(nav); sep() }
        // right side (approved): HH:mm · dd.MM.yyyy
        if (LookOpts.clockOn) T(LookOpts.time(LocalContext.current, now), 17.sp, C.Text, FontWeight.Bold)
        if (LookOpts.clockOn && LookOpts.dateOn) T("·", 15.sp, C.Soft)
        if (LookOpts.dateOn) T(SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(now), 15.sp, C.Soft)
    }
}

/** The bell with a red number of unread notifications; OK opens the notification center. */
@Composable
fun NoteBell(nav: Nav) {
    LaunchedEffect(Unit) { Notes.load() }
    val unread = Notes.unread()
    var open by remember { mutableStateOf(false) }
    // BUG 06C: the round button clipped the badge (and the bell's top); the badge now sits in an outer box that never clips
    Box(Modifier.size(48.dp)) {
        Focusable(onClick = { open = true }, shape = androidx.compose.foundation.shape.CircleShape, normal = Color.Transparent, focused = Dawn.pill,
            zoom = 1f, modifier = Modifier.size(40.dp).align(Alignment.Center)) { f ->
            Box(Modifier.align(Alignment.Center)) { Ico(R.drawable.lc_bell, 20.dp, if (f) Color.White else C.Text) }
        }
        if (unread > 0) Box(Modifier.align(Alignment.TopEnd).size(16.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xFFE5484D)),
            contentAlignment = Alignment.Center) { T(if (unread > 9) "9+" else unread.toString(), 9.sp, Color.White, FontWeight.Bold) }
    }
    if (open) NoteCenter(nav) { open = false }
}

/** Notification center (approved): newest first, OK marks read and goes straight there, long OK removes one,
 *  "Clear notifications" last, with a confirmation. */
@Composable
private fun NoteCenter(nav: Nav, onDismiss: () -> Unit) {
    var ask by remember { mutableStateOf(false) }
    val items = Notes.list.sortedByDescending { it.time }
    if (ask) { Popup("Obriši obavještenja", { ask = false }) {
        T("Obrisati sva obavještenja?", 14.sp, C.Soft)
        OptionRow("Obriši", R.drawable.lc_trash_2) { Notes.clear(); ask = false; onDismiss() }
        OptionRow("Odustani", R.drawable.lc_x) { ask = false }
    }; return }
    Popup("Obavještenja", onDismiss, width = 680.dp) {
        if (items.isEmpty()) T("Nema obavještenja.", 15.sp, C.Soft)
        items.forEach { n ->
            val icon = when (n.kind) { "update" -> R.drawable.lc_download; "reminder" -> R.drawable.lc_bell; "sport" -> R.drawable.lc_trophy
                "series" -> R.drawable.lc_layers; "vod" -> R.drawable.lc_film; else -> R.drawable.lc_info }
            OptionRow(n.text, icon, sub = SimpleDateFormat("dd.MM.yyyy · HH:mm", Locale.getDefault()).format(Date(n.time)),
                color = if (n.read) null else C.Text, onLong = { Notes.remove(n.id) }) { onDismiss(); openNote(nav, n) }
        }
        if (items.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(1.dp).background(Color(0x33FFFFFF)))
            OptionRow("Obriši obavještenja", R.drawable.lc_trash_2) { ask = true }
        }
    }
}

/** The active playlist in every status bar (one shared piece): NAME [N DAYS]. Only the day count has a thin outline:
 *  green ≥ 7, amber 3–6, red 0–2 or expired, neutral grey when the provider gives no date (never an invented number).
 *  Name and expiry always come from the same active playlist; a long name is shortened, the day count never. */
@Composable
fun PlaylistStatus(p: Playlist, now: Long, modifier: Modifier = Modifier) {
    val sec = p.exp.toLongOrNull()?.takeIf { it > 0 }
    // whole calendar days to the expiry day (midnight to midnight), so the number changes at midnight, not at an odd hour
    val days = sec?.let { val day = 86_400_000L; val tz = java.util.TimeZone.getDefault()
        ((it * 1000 + tz.getOffset(it * 1000)) / day - (now + tz.getOffset(now)) / day).toInt() }
    val (label, line) = when {
        days == null -> "—" to Color(0x80B0B8C4)  // i18n-ignore
        days < 0 -> tr("Istekla").uppercase() to Color(0xFFE5484D)
        else -> daysLabel(days) to when { days <= 2 -> Color(0xFFE5484D); days <= 6 -> Color(0xFFE5B54A); else -> Color(0xFF46A758) }
    }
    Row(modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        T(p.name, 14.sp, Color(0xFFDDE1E6), FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f, fill = false).widthIn(max = 220.dp))
        Box(Modifier.border(1.dp, line, Rnd(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) { T(label, 13.sp, Color.White, FontWeight.SemiBold, maxLines = 1) }
    }
}

/** "59 DANA", "21 DAN", "1 DAY", "59 DAYS" — singular / plural by the app language */
fun daysLabel(n: Int): String {
    val slav = Look.lang in setOf("bs", "hr", "sr", "ru", "bg")
    val one = if (slav) n % 10 == 1 && n % 100 != 11 else n == 1
    return (if (one) trf("%s DAN", n) else trf("%s DANA", n))
}

@Composable
private fun PlaylistChip(p: Playlist, now: Long) = PlaylistStatus(p, now)

/** Preview area: the picture after the first OK, before that a calm box with a play mark. */
@Composable
fun DawnPreview(modifier: Modifier) {
    val playing = PreviewSel.ch
    Box(modifier.aspectRatio(16f / 9f).dawnGlass(14.dp), contentAlignment = Alignment.Center) {
        if (playing != null) PreviewBox(playing, Modifier.fillMaxSize())
        else Ico(R.drawable.lc_play, 54.dp, Color(0x99DADADE))
    }
}

/** Now and next of a channel under the preview (approved TV browser): big name, NOW with a thin bar, NEXT — no box. */
@Composable
fun DawnNowNext(ch: Channel?, modifier: Modifier) {
    val ctx = LocalContext.current
    Column(modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (ch == null) { T("Stani na kanal da vidiš program", 14.sp, C.Muted); return@Column }
        val st by load(ch.url, EpgShift.rev) { Store.byId(ctx, ch.pid)?.let { Repo.epg(it, ch, 3) } ?: emptyList() }
        val list = (st as? Load.Ok)?.value.orEmpty()
        val t = System.currentTimeMillis()
        val cur = list.firstOrNull { it.start <= t && it.end > t }
        val next = list.firstOrNull { it.start > t }
        T(ch.name.uppercase(), 22.sp, Color.White, FontWeight.Bold)
        if (cur != null) {
            T(tr("Sada").uppercase() + " — " + cur.times() + " • " + cur.title, 15.sp, Color.White, FontWeight.SemiBold)  // i18n-ignore
            Box(Modifier.fillMaxWidth().height(3.dp).clip(Rnd(2.dp)).background(Color(0x33FFFFFF))) {
                Box(Modifier.fillMaxWidth(cur.progress()).height(3.dp).background(Color(0xFFE6E8EB)))
            }
        } else if (st is Load.Loading) T("Učitavam program…", 14.sp, C.Soft)
        if (next != null) T(tr("Zatim").uppercase() + " — " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(next.start)) + " • " + next.title, 14.sp, Color(0xFFB8BEC6))  // i18n-ignore
    }
}

/** Bottom options of the channel screen, exactly six icons in this order (approved): Pause · Favorite · EPG · Format · Archive · More. */
@Composable
fun DawnOptions(ch: Channel?, nav: Nav, modifier: Modifier) {
    val ctx = LocalContext.current
    var epg by remember { mutableStateOf<Channel?>(null) }
    var fmt by remember { mutableStateOf<Channel?>(null) }
    var more by remember { mutableStateOf(false) }
    var sub by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val fr = Favs.rev
    var paused by remember { mutableStateOf(false) }
    fun toast(t: String) = android.widget.Toast.makeText(ctx, tr(t), android.widget.Toast.LENGTH_SHORT).show()
    // actions that need a picture work on the channel in the preview (after the first OK)
    fun live(): Boolean = (PreviewSel.ch != null && Preview.player != null).also { if (!it) toast("Prvo pritisni OK na kanalu za pregled.") }
    // the way back to the channel list is explicit: ◀ on the first icon (or Back) goes to the channel in the preview
    DisposableEffect(Unit) { onDispose { TvFocus.inDock = false; TvFocus.dockFirst = false } }
    // the keys themselves are taken in MainActivity (TvFocus.onKey) before Compose can turn Back into "leave the group"
    Row(modifier.dawnGlass(14.dp).padding(5.dp).onFocusChanged { TvFocus.inDock = it.hasFocus; TvFocus.log("dock focus=${it.hasFocus}") }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        @Composable fun Opt(icon: Int, tint: Color? = null, first: Boolean = false, onClick: () -> Unit) {
            Focusable(modifier = Modifier.weight(1f).fillMaxHeight(), onFocus = { TvFocus.dockFirst = first }, onClick = onClick, shape = Rnd(10.dp), normal = Color.Transparent,
                focused = Dawn.pill, focusBorderColor = Color.Transparent, zoom = 1.0f) { f ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Ico(icon, 26.dp, if (f && tint == null) Dawn.onPill else tint ?: C.Text) }
            }
        }
        Opt(if (paused) R.drawable.lc_play else R.drawable.lc_pause, first = true) {
            if (live()) Preview.player?.let { p -> if (p.isPlaying) { p.pause(); paused = true } else { p.play(); paused = false } }
        }
        val on = ch != null && Favs.inAny(ctx, ch)
        Opt(R.drawable.lc_star, if (on) C.Amber else null) { ch?.let { c -> toast(if (Favs.flip(ctx, c)) "Dodano u favorite" else "Uklonjeno iz favorita") } }
        Opt(R.drawable.lc_calendar) { ch?.let { epg = it } }
        Opt(R.drawable.lc_maximize) { ch?.let { fmt = it } }
        Opt(R.drawable.lc_history) {
            val c = ch ?: return@Opt
            val p = Store.byId(ctx, c.pid)
            if (p != null && Catchups.has(p, c)) { Preview.stop(); PreviewSel.ch = null; CatchupOpen.ch = c; nav.push(Screen.Catchup) } else toast("Ovaj kanal nema arhivu")
        }
        Opt(R.drawable.lc_settings) { more = true }
    }
    // sleep timer set here also ends the preview and goes back to the menu
    LaunchedEffect(Sleep.until) {
        val u = Sleep.until; if (u <= 0) return@LaunchedEffect
        delay((u - System.currentTimeMillis()).coerceAtLeast(0))
        if (Sleep.until == u) { Sleep.until = 0; Preview.stop(); PreviewSel.ch = null; nav.root(Screen.Home) }
    }
    epg?.let { c -> Popup(c.name, { epg = null }, width = 720.dp) { EpgList(c, 16, Modifier.fillMaxWidth().height(440.dp)) } }
    fmt?.let { c -> Popup("Format", { fmt = null }) {
        val cur = Prefs.long("fmt_v2_" + c.url.hashCode(), 0).toInt()
        VOD_FORMATS.forEach { (i, label) -> OptionRow(tr(label), R.drawable.lc_maximize, selected = cur == i) { Prefs.put("fmt_v2_" + c.url.hashCode(), i.toLong()); PreviewFmt.rev++; fmt = null } }
    } }
    // Settings (gear, approved): Channel info · Audio · Subtitles · Sleep timer · External player · EPG settings — only what works now
    if (more) Popup(tr("Postavke"), { more = false }) {
        val pl = Preview.player
        val playingHere = pl != null && PreviewSel.ch != null
        val texts = if (playingHere) pl!!.currentTracks.groups.filter { it.type == androidx.media3.common.C.TRACK_TYPE_TEXT }.sumOf { it.length } else 0
        val auds = if (playingHere) pl!!.currentTracks.groups.filter { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO }.sumOf { it.length } else 0
        if (playingHere) OptionRow("Informacije o kanalu", R.drawable.lc_info) { more = false; sub = 4 }
        if (auds > 1) OptionRow("Audio", R.drawable.lc_volume) { more = false; sub = 2 }
        if (texts > 0) OptionRow("Titlovi", R.drawable.lc_captions) { more = false; sub = 1 }
        OptionRow(if (Sleep.until > 0) trf("Tajmer: %s min", ((Sleep.until - System.currentTimeMillis()) / 60_000 + 1).coerceAtLeast(1)) else tr("Tajmer za spavanje"), R.drawable.lc_moon) { more = false; sub = 3 }
        if (ch != null) OptionRow("Vanjski plejer", R.drawable.lc_external_link) { more = false; sub = 5 }
        if (ch != null) OptionRow("EPG postavke", R.drawable.lc_calendar_clock, value = EpgShift.label(EpgShift.chMin(ch.url, ch.pid))) { more = false; sub = 6 }
    }
    when (sub) {
        1 -> DawnTracks(androidx.media3.common.C.TRACK_TYPE_TEXT) { sub = 0 }
        2 -> DawnTracks(androidx.media3.common.C.TRACK_TYPE_AUDIO) { sub = 0 }
        3 -> Popup("Tajmer za spavanje", { sub = 0 }) {
            OptionRow("Isključen", if (Sleep.until <= 0) R.drawable.lc_check else R.drawable.lc_moon) { Sleep.until = 0; sub = 0 }
            listOf(15, 30, 60, 90).forEach { m -> OptionRow(trf("%s min", m), R.drawable.lc_moon) { Sleep.until = System.currentTimeMillis() + m * 60_000L; sub = 0 } }
        }
        4 -> Popup("Informacije o streamu", { sub = 0 }) {
            val f = Preview.player?.videoFormat
            if (f == null) T("Podaci još nisu dostupni.", 14.sp, C.Soft)
            else {
                OptionRow("Rezolucija", R.drawable.lc_info, value = "${f.width}×${f.height}") {}
                if (f.frameRate > 0) OptionRow("FPS", R.drawable.lc_info, value = kotlin.math.round(f.frameRate).toInt().toString()) {}
                f.sampleMimeType?.let { OptionRow("Kodek", R.drawable.lc_info, value = it.substringAfter('/')) {} }
                if (f.bitrate > 0) OptionRow("Bitrate", R.drawable.lc_info, value = trf("%s kb/s", f.bitrate / 1000)) {}
            }
        }
        5 -> ch?.let { c -> ExternalPicker(c.url, c.name, 0L, onDismiss = { sub = 0 }) }
        6 -> ch?.let { c -> EpgShiftPopup(c) { sub = 0 } }
    }
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
            // only the language; several of the same language get (V1), (V2)
            val all = groups.flatMap { g -> (0 until g.length).map { g to it } }
            val langs = all.map { (g, i) -> iso2(g.getTrackFormat(i).language)?.let { Langs.label(it) } ?: tr("Nepoznat jezik") }
            val seen = HashMap<String, Int>()
            val names = langs.map { l -> if (langs.count { it == l } > 1) { val n = (seen[l] ?: 0) + 1; seen[l] = n; "$l (V$n)" } else l }
            var k = 0
            groups.forEach { g ->
                for (i in 0 until g.length) {
                    val name = names[k++]
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
    query: String, tv: List<Hit>, movies: List<Hit>, shows: List<Hit>, sport: List<SportEvent>, settings: List<SettingHit>, first: FocusRequester,
    onHit: (Hit) -> Unit, onMenu: (Hit) -> Unit, onSport: (SportEvent) -> Unit, onSetting: (SettingHit) -> Unit, modifier: Modifier,
    only: String? = null,
) {
    // one category at a time, never a mixed "all" list (approved decision): the last one is remembered
    var filt by remember { mutableStateOf(only ?: SearchMem.open?.takeIf { it in listOf("tv", "vod", "series", "sport", "settings") } ?: "tv") }
    LaunchedEffect(filt) { SearchMem.open = filt }
    // the chosen category has nothing for this word: show the first one that has results (the list on the left still shows all counts)
    val counts = listOf("tv" to tv.size, "vod" to movies.size, "series" to shows.size, "sport" to sport.size, "settings" to settings.size)
    LaunchedEffect(query, counts) { if (only == null && counts.first { it.first == filt }.second == 0) counts.firstOrNull { it.second > 0 }?.let { filt = it.first } }
    val filters = listOf(
        Triple("tv", "TV", tv.size), Triple("vod", "Filmovi", movies.size),
        Triple("series", "Serije", shows.size), Triple("sport", "Sport", sport.size), Triple("settings", "Postavke", settings.size),
    )
    val icons = mapOf("tv" to R.drawable.lc_tv, "vod" to R.drawable.lc_film, "series" to R.drawable.lc_layers, "sport" to R.drawable.lc_trophy, "settings" to R.drawable.lc_settings)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (only == null) Column(Modifier.width(270.dp).fillMaxHeight().dawnGlass().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            filters.forEach { (id, label, n) ->
                Focusable(onClick = { filt = id }, onFocus = { filt = id }, shape = Rnd(12.dp), normal = if (filt == id) Color(0x26FFFFFF) else Color.Transparent,
                    focused = Dawn.pill, focusBorderColor = Color.Transparent, zoom = 1f, modifier = Modifier.fillMaxWidth().height(50.dp)) { f ->
                    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Ico(icons[id] ?: R.drawable.lc_search, 22.dp, if (f) Dawn.onPill else C.Text)
                        T(label, 16.sp, if (f) Dawn.onPill else C.Text, if (f || filt == id) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                        T("($n)", 14.sp, if (f) Dawn.onPill else C.Muted)
                    }
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            T(trf("Rezultati za: „%s“", query), 16.sp, C.Soft, FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
            val rows: List<Any> = when (filt) {
                "vod" -> movies; "series" -> shows; "sport" -> sport; "settings" -> settings
                else -> tv
            }
            if (rows.isEmpty()) { T("Ništa nije pronađeno", 15.sp, C.Soft); return@Column }
            androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(rows.size) { i ->
                    val r = rows[i]
                    Focusable(onClick = { if (r is Hit) onHit(r) else if (r is SportEvent) onSport(r) else if (r is SettingHit) onSetting(r) }, onMenu = { if (r is Hit) onMenu(r) },
                        focusRequester = if (i == 0) first else null, shape = Rnd(12.dp), normal = Color(0x59141416), focused = Dawn.pill,
                        focusBorderColor = Color.Transparent, zoom = 1f, modifier = Modifier.fillMaxWidth().height(76.dp)) { f ->
                        val main = if (f) Dawn.onPill else Color.White
                        val sub = if (f) Dawn.onPill.copy(alpha = 0.7f) else C.Soft
                        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            val (title, line, type) = when (r) {
                                is Hit.Show -> Triple(cleanTitle(r.s.name).first, tr("Serija"), "Serija")
                                is Hit.Movie -> Triple(cleanTitle(r.v.name).first, tr("Film"), "Film")
                                is Hit.Ch -> Triple(r.ch.name, nowOf(r.ch, 600)?.title ?: (tr("Kanal") + " ${r.ch.num}"), "TV")
                                is Hit.Prog -> Triple(r.e.title, r.ch.name + " · " + r.e.times(), "TV")
                                is SportEvent -> Triple(if (r.home != null && r.away != null) r.home.name + " – " + r.away.name else r.title, r.league, "Sport")
                                is SettingHit -> Triple(r.title, r.path, tr("Postavke"))
                                else -> Triple("", "", "")
                            }
                            val img = when (r) { is Hit.Show -> r.s.cover; is Hit.Movie -> r.v.poster; else -> null }
                            Box(Modifier.width(104.dp).height(60.dp).clip(Rnd(8.dp)).background(Color(0x33000000)), contentAlignment = Alignment.Center) {
                                when {
                                    img != null -> OnyxImage(img, title, Modifier.fillMaxSize(), maxW = 220)
                                    r is Hit.Ch -> ChannelLogo(r.ch, 40.dp)
                                    r is Hit.Prog -> ChannelLogo(r.ch, 40.dp)
                                    r is SettingHit -> Ico(R.drawable.lc_settings, 26.dp, main)
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
