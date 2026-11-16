package com.pretorian.tv

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MIN = 60_000L
private const val HALF = 30 * MIN
private const val DAY = 86_400_000L
/** a later show: remind me / don't — nothing else to ask */
private fun remindToggle(ctx: android.content.Context, ch: Channel, pr: EpgItem) {
    val on = Reminders.toggle(Reminder("r${pr.start}${pr.title.hashCode()}", pr.title, pr.start, ch.url, ch.name, ch.logo, ch.pid, "tv"))
    Toast.makeText(ctx, tr(if (on) "Podsjetnik postavljen" else "Podsjetnik uklonjen"), Toast.LENGTH_SHORT).show()
}
private fun floorHalf(t: Long) = t - (t % HALF)
/** local midnight of the day of [t] */
private fun dayStart(t: Long): Long = java.util.Calendar.getInstance().apply {
    timeInMillis = t; set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
}.timeInMillis
/** days from today: -1 yesterday, 0 today, 1 tomorrow */
private fun dayIdx(t: Long, now: Long) = Math.round((dayStart(t) - dayStart(now)).toDouble() / DAY).toInt()
private fun dayName(d: Int, t: Long): String = when (d) {
    -1 -> tr("Jučer"); 0 -> tr("Danas"); 1 -> tr("Sutra")
    else -> SimpleDateFormat("EEE d.", Locale(Look.lang)).format(Date(t))
}
private val PICK = Color(0xFFFBBF24)

/**
 * TV guide as a grid: channels down, time across, the picture and the show's details on top.
 * Back or Left (from the show that is on now) opens the groups and the icon menu; Left again opens the full menu.
 */
@Composable
fun TvGuide(nav: Nav, onList: () -> Unit) {
    val ctx = LocalContext.current
    val st = remember { TvState() }
    val rows by load(st.rev, Favs.rev, keep = true) { groupRows(ctx) }
    val gState = rememberLazyListState()
    WrapEffect(st.wrap, gState)
    val gridFr = remember { FocusRequester() }
    var focusedGroup by remember { mutableStateOf<GroupRef?>(null) }

    DisposableEffect(Unit) {
        Chrome.driven = true; Chrome.rail = if (Chrome.inRail) 3 else 0
        onDispose { Chrome.driven = false; Chrome.rail = 3 }
    }
    val groupsOpen = Chrome.rail >= 1

    // first group: the last one used, else the first one of the list
    LaunchedEffect(rows is Load.Ok) {
        val list = (rows as? Load.Ok)?.value ?: return@LaunchedEffect
        if (st.group == null || list.none { it.id == "g:" + st.group!!.key }) {
            st.group = list.firstNotNullOfOrNull { (it as? GRow.Group)?.ref?.takeIf { r -> r.pid != null } } ?: list.firstNotNullOfOrNull { (it as? GRow.Group)?.ref }
        }
        gridFr.tryFocus()
    }
    // browsing the groups shows that group's channels on the right; focus stays on the groups
    LaunchedEffect(focusedGroup) {
        val f = focusedGroup ?: return@LaunchedEffect
        delay(250)
        val locked = f.pid?.let { Ovr(ctx, it).isLocked("live", f.cat.id) } ?: false
        if (!locked && st.group?.key != f.key) { TvMemory.channel = -1; st.group = f; TvMemory.group = f }
    }
    fun showGroups() { Chrome.rail = 3; st.focusKey = "g:" + (st.group?.key ?: ""); st.refocus++ }
    BackHandler(enabled = Chrome.rail == 0 && st.groupMenu == null) { showGroups() }

    Row(Modifier.fillMaxSize()) {
        if (groupsOpen) {
            GroupColumn(rows, st.group, st.focusKey, st.focusFr, st.wrap, gState, 250.dp, "", null,
                onFocusGroup = { focusedGroup = it; Chrome.rail = 3 },
                onOpen = { openGroup(ctx, st, it); st.enter++ },
                onMenu = { st.focusKey = "g:" + it.key; st.groupMenu = it },
                onHeader = { p -> TvFold.toggle(p.id); if (p.id in TvFold.open) Store.open(ctx, p.id); st.focusKey = "h:" + p.id; st.rev++; st.refocus++ },
                onNewFav = { st.newFav = true }, onNewGroup = { st.newGroupFor = it },
                onFocusRow = { st.focusKey = it; Chrome.rail = 3 })
            Box(Modifier.width(1.dp).fillMaxHeight().background(C.Line))
            LaunchedEffect(st.refocus, rows is Load.Ok) {
                val list = (rows as? Load.Ok)?.value ?: return@LaunchedEffect
                if (st.refocus == 0) return@LaunchedEffect
                if (list.none { it.id == st.focusKey }) st.focusKey = st.group?.let { "g:" + it.key } ?: list.firstOrNull()?.id ?: ""
                val idx = list.indexOfFirst { it.id == st.focusKey }.coerceAtLeast(0)
                if (gState.layoutInfo.visibleItemsInfo.none { it.index == idx }) gState.scrollToItem((idx - 3).coerceAtLeast(0))
                st.focusFr.tryFocus()
            }
        }
        val g = st.group
        Box(Modifier.weight(1f).fillMaxHeight()) {
            if (g == null) Loading("Učitavam grupe…")
            else androidx.compose.runtime.key(g.key, st.rev) {
                val groups = (rows as? Load.Ok)?.value?.mapNotNull { (it as? GRow.Group)?.ref }?.filter { it.pid != null && it.pid == g.pid && it.cat.id != ALL_GROUP }?.map { it.cat } ?: emptyList()
                GuideGrid(g, nav, st.gate, gridFr, groups, st.enter, onLeftEdge = { showGroups() }, onReload = { st.rev++ })
            }
        }
    }
    TvDialogs(st, rows)
}

@Composable
private fun GuideGrid(g: GroupRef, nav: Nav, gate: PinGate, fr: FocusRequester, groups: List<Category>, enter: Int,
                      onLeftEdge: () -> Unit, onReload: () -> Unit) {
    val ctx = LocalContext.current
    val chans by load(g.key, Favs.rev) { channelsOf(ctx, g) }
    val list = (chans as? Load.Ok)?.value ?: emptyList()
    val epg = remember { mutableStateMapOf<String, List<EpgItem>>() }
    val epgLim = remember { mutableStateMapOf<String, Int>() }
    // past shows of channels with an archive (catch-up), loaded when the grid goes back in time
    val past = remember { mutableStateMapOf<String, List<EpgItem>>() }
    var limit by remember { mutableIntStateOf(40) }
    val barFr = remember { FocusRequester() }
    var numBuf by remember { mutableStateOf("") }
    var pickedHour by remember { mutableIntStateOf(-2) }
    var row by remember { mutableIntStateOf(if (TvMemory.group?.key == g.key) TvMemory.channel.coerceAtLeast(0) else 0) }
    val now0 = remember { System.currentTimeMillis() }
    var focusTime by remember { mutableLongStateOf(now0) }
    var viewStart by remember { mutableLongStateOf(floorHalf(now0)) }
    var detail by remember { mutableStateOf<Pair<Channel, EpgItem?>?>(null) }
    var chMenu by remember { mutableStateOf<Channel?>(null) }
    var longFired by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(now0) }
    val listState = rememberLazyListState()
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    // in the grid the groups slide away; Left at the start of the grid brings them back
    LaunchedEffect(focused) { if (focused && Chrome.driven) Chrome.rail = 0 }

    /** how far back this channel can go: its archive days, else now */
    fun minOf(ch: Channel?): Long = if (ch != null && ch.archive > 0) dayStart(now - ch.archive.coerceIn(1, 7) * DAY) else now
    val anyArchive = list.any { it.archive > 0 }
    val wantPast = focusTime < now
    // guide of the rows around the focused one (more of it when a later day is picked; the archive when going back)
    LaunchedEffect(row, list, limit, wantPast) {
        for (i in (row - 2).coerceAtLeast(0)..(row + 12).coerceAtMost(list.lastIndex)) {
            val ch = list[i]
            val p = Store.byId(ctx, ch.pid) ?: continue
            if ((epgLim[ch.url] ?: 0) < limit) {
                val short = try { Repo.epg(p, ch, limit) } catch (e: Exception) { emptyList() }
                epg[ch.url] = short.ifEmpty { epg[ch.url]?.ifEmpty { null } ?: try { ProgIndex.forChannel(ctx, ch) } catch (e: Exception) { emptyList() } }
                epgLim[ch.url] = limit
            }
            if (wantPast && ch.archive > 0 && !past.containsKey(ch.url) && p.type == SourceType.XTREAM) {
                past[ch.url] = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val x = Xtream(p); val t = System.currentTimeMillis()
                    val a = try { x.archive(ch.id) } catch (e: Exception) { emptyList() }
                    (if (a.any { it.end <= t }) a else x.archiveFromGuide(ch.epg, ch.archive.coerceIn(1, 7))).filter { it.end <= t }
                }
            }
        }
    }
    LaunchedEffect(row) {
        if (list.isEmpty()) return@LaunchedEffect
        val vis = listState.layoutInfo.visibleItemsInfo
        if (vis.none { it.index == row } || vis.lastOrNull()?.index == row) listState.animateScrollToItem((row - 2).coerceAtLeast(0))
    }

    fun progsOf(ch: Channel?): List<EpgItem> {
        ch ?: return emptyList()
        val fut = epg[ch.url] ?: emptyList()
        val old = past[ch.url] ?: return fut
        val first = fut.minOfOrNull { it.start } ?: Long.MAX_VALUE
        return (old.filter { it.start < first } + fut).sortedBy { it.start }
    }
    fun current(): Pair<Channel, EpgItem?>? {
        val ch = list.getOrNull(row) ?: return null
        return ch to progsOf(ch).firstOrNull { it.start <= focusTime && it.end > focusTime }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val nameW = 300.dp
        val ppm = 4.5f  // dp per minute
        val gridW = maxWidth - nameW - 16.dp
        val spanMin = (gridW.value / ppm).toLong()
        val viewEnd = viewStart + spanMin * MIN

        fun keepVisible(start: Long) {
            if (start < viewStart) viewStart = floorHalf(start)
            else if (start > viewEnd - HALF) viewStart = floorHalf(start) - HALF
        }
        /** jump the grid to [t] (a day or an hour picked above the grid) */
        fun goTo(t0: Long) {
            val ch = list.getOrNull(row)
            val t = t0.coerceAtLeast(minOf(ch)).coerceAtMost(dayStart(now) + 6 * DAY)
            focusTime = if (t <= now && t >= now - MIN) now else t + 1
            viewStart = floorHalf(t) - HALF
            if (t > now + 18 * 3600_000L) limit = maxOf(limit, 150)
        }

        Column(Modifier.fillMaxSize().padding(top = 16.dp, start = 12.dp, end = 4.dp)) {
            // ---------------------------------------------------------------- picture and show
            val cur = current()
            val tmPic = ListStyle.tivimate
            val noPic = !tmPic && (ListStyle.tivimate || (Perf.lite && !Prefs.bool("lite_preview")) || (Look.previewMode == 1 && PreviewSel.ch == null) || Look.previewMode == 0)
            Row(Modifier.fillMaxWidth().heightIn(min = if (tmPic) 124.dp else 60.dp, max = if (noPic) 104.dp else if (tmPic) 128.dp else 150.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                // TiviMate mode: no small picture, the show fills the top
                when (if (ListStyle.tivimate || (Perf.lite && !Prefs.bool("lite_preview"))) 0 else Look.previewMode) {
                    2 -> PreviewBox(cur?.first, Modifier.height(146.dp))
                    1 -> PreviewSel.ch?.let { PreviewBox(it, Modifier.height(146.dp)) }
                }
                Column(Modifier.weight(1f).padding(top = 4.dp, end = 12.dp), verticalArrangement = Arrangement.spacedBy(if (tmPic) 4.dp else 6.dp)) {
                    val pr = cur?.second
                    T(pr?.title ?: cur?.first?.name ?: "", if (noPic) 19.sp else 22.sp, C.Text, FontWeight.Bold, maxLines = if (noPic) 1 else 2)
                    if (pr != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        T((if (dayIdx(pr.start, now) != 0) dayName(dayIdx(pr.start, now), pr.start) + " · " else "") + pr.times(), 14.sp, C.Soft, FontWeight.SemiBold)  // i18n-ignore
                        if (pr.start <= now && pr.end > now) {
                            Box(Modifier.width(70.dp).height(4.dp).clip(Rnd(2.dp)).background(C.CardHi)) {
                                Box(Modifier.fillMaxWidth(pr.progress(now)).height(4.dp).background(C.Red))
                            }
                            T(trf("još %s min", (pr.end - now) / MIN), 13.sp, C.Muted)
                        }
                        if (Reminders.has(pr.title, pr.start)) Ico(R.drawable.lc_bell, 15.dp, C.Amber)
                    }
                    cur?.first?.let { T(it.name, 13.sp, C.RedText, FontWeight.SemiBold) }
                    cleanDesc(pr?.desc)?.let { T(it, 13.sp, C.Soft, maxLines = if (noPic) 2 else 3) }
                }
                // TiviMate style: the small picture sits in the top right corner
                if (tmPic) {
                    val pc = if (Look.previewMode == 2 && !(Perf.lite && !Prefs.bool("lite_preview"))) cur?.first else PreviewSel.ch
                    if (pc != null) PreviewBox(pc, Modifier.height(124.dp).padding(end = 8.dp))
                    else Box(Modifier.height(124.dp).aspectRatio(16f / 9f).padding(end = 8.dp).clip(Rnd(12.dp)).background(C.Card), contentAlignment = Alignment.Center) {
                        T("OK = slika ovdje · OK još jednom = cijeli ekran", 11.sp, C.Muted, maxLines = 2, modifier = Modifier.padding(10.dp))
                    }
                }
            }
            // ---------------------------------------------------------------- group · day · time
            val fDay = dayIdx(focusTime, now)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp).onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown) { try { fr.requestFocus() } catch (_: Exception) {}; true } else false
            }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                @Composable fun chip(text: String, on: Boolean, red: Boolean = false, f: FocusRequester? = null, click: () -> Unit) =
                    Focusable(modifier = Modifier.height(30.dp), onClick = click, focusRequester = f, shape = Pill, zoom = 1.04f,
                        normal = if (on) PICK else if (red) C.Red.copy(alpha = 0.18f) else C.Card) { foc ->
                        T(text, 12.sp, if (on && !foc) Color(0xFF1A1300) else if (red && !foc) C.RedText else C.Text, if (on || red) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1, modifier = Modifier.align(Alignment.Center).padding(horizontal = 10.dp))
                    }
                chip("☰", false, f = barFr) { onLeftEdge() }  // i18n-ignore
                Box(Modifier.width(1.dp).height(20.dp).background(C.Line))
                T("DAN", 10.sp, C.Muted, FontWeight.Bold)
                for (d in (if (anyArchive) -1 else 0)..3) {
                    val t = dayStart(now) + d * DAY
                    chip(dayName(d, t), d == fDay) {
                        pickedHour = -2
                        goTo(if (d == 0) maxOf(now, t + (focusTime - dayStart(focusTime))) else t + (focusTime - dayStart(focusTime)))
                    }
                }
                Box(Modifier.width(1.dp).height(20.dp).background(C.Line))
                T("VRIJEME", 10.sp, C.Muted, FontWeight.Bold)
                chip("● " + tr("Sada"), false, red = true) { pickedHour = -1; goTo(now) }  // i18n-ignore
                for ((h, label) in listOf(8 to tr("Jutro"), 12 to tr("Podne"), 18 to "18:00", 20 to "20:00", 22 to "22:00")) {  // i18n-ignore
                    chip(label, pickedHour == h && dayIdx(focusTime, now) == fDay) { pickedHour = h; goTo(dayStart(focusTime) + h * 3600_000L) }
                }
            }
            // ---------------------------------------------------------------- time ruler
            Row(Modifier.fillMaxWidth().height(38.dp).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(nameW)) {
                    T(g.cat.name, 11.sp, C.Muted, FontWeight.Bold, maxLines = 1)
                    T(dayName(dayIdx(viewStart + HALF, now), viewStart + HALF) + ", " + SimpleDateFormat("d. MMM", Locale(Look.lang)).format(Date(viewStart + HALF)),  // i18n-ignore
                        13.sp, if (dayIdx(viewStart + HALF, now) == 0) C.RedText else PICK, FontWeight.SemiBold, maxLines = 1)
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    var t = floorHalf(viewStart)
                    while (t < viewEnd) {
                        if (t >= viewStart) T(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t)), 13.sp, C.Muted,
                            modifier = Modifier.offset(x = (((t - viewStart) / MIN) * ppm).dp))
                        t += HALF
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
            // ---------------------------------------------------------------- grid
            when (val s = chans) {
                is Load.Loading -> Loading("Učitavam kanale…")
                is Load.Err -> Message("Kanali se nisu učitali", s.message)
                is Load.Ok -> if (list.isEmpty()) Message("Grupa je prazna") else LazyColumn(state = listState, userScrollEnabled = false,
                    modifier = Modifier.fillMaxSize().focusRequester(fr).onPreviewKeyEvent { e ->
                        val okKey = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                        if (okKey && e.type == KeyEventType.KeyUp) {
                            // short OK: menu of the show; long OK (handled while held): channel options
                            if (longFired) { longFired = false; return@onPreviewKeyEvent true }
                            val ch = list.getOrNull(row) ?: return@onPreviewKeyEvent true
                            val pr = progsOf(ch).firstOrNull { it.start <= focusTime && it.end > focusTime }
                            val t = System.currentTimeMillis()
                            // on what is on now: straight to full screen, no question; past or coming shows: their menu
                            if (pr != null && pr.end <= t && ch.archive > 0) {
                                val p = Store.byId(ctx, ch.pid)
                                val u = Catchups.url(p, ch, pr)
                                if (p != null && u != null) gate.run("c:${ch.url}", Ovr(ctx, ch.pid).isChLocked(ch)) {
                                    Preview.stop(); nav.push(Screen.Player(listOf(PlayItem(pr.title, u, ch.logo, ch.name, null)), 0, live = false))
                                }
                            } else if (ListStyle.tivimate && (pr == null || (pr.start <= t && pr.end > t)) && PreviewSel.ch?.url != ch.url && !(Look.previewMode == 2 && !(Perf.lite && !Prefs.bool("lite_preview")))) {
                                // TiviMate style: first OK starts the small picture in the corner, the next OK goes full screen
                                gate.run("c:${ch.url}", Ovr(ctx, ch.pid).isChLocked(ch)) { PreviewSel.ch = ch }
                            } else if (pr == null || (pr.start <= t && pr.end > t)) {
                                val i = list.indexOfFirst { it.url == ch.url }.coerceAtLeast(0)
                                gate.run("c:${ch.url}", Ovr(ctx, ch.pid).isChLocked(ch)) { TvMemory.channel = i; TvMemory.group = g; TvMemory.restore = true; playChannels(nav, list, i) }
                            } else remindToggle(ctx, ch, pr)
                            return@onPreviewKeyEvent true
                        }
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val ch = list.getOrNull(row)
                        val progs = progsOf(ch)
                        val curP = progs.firstOrNull { it.start <= focusTime && it.end > focusTime }
                        val kc = e.nativeKeyEvent.keyCode
                        // digits: type a channel number
                        val digit = when (kc) { in 7..16 -> kc - 7; in 144..153 -> kc - 144; else -> -1 }
                        if (digit >= 0) { if (numBuf.length < 4) numBuf += digit; return@onPreviewKeyEvent true }
                        // a row that can't go back (no archive) shows now, not the past
                        fun fixRow(r: Int) { val c = list.getOrNull(r); if (focusTime < minOf(c)) focusTime = now }
                        val page = listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(5) - 1
                        when (kc) {
                            android.view.KeyEvent.KEYCODE_CHANNEL_UP, android.view.KeyEvent.KEYCODE_PAGE_UP -> { row = (row - page).coerceAtLeast(0); fixRow(row); return@onPreviewKeyEvent true }
                            android.view.KeyEvent.KEYCODE_CHANNEL_DOWN, android.view.KeyEvent.KEYCODE_PAGE_DOWN -> { row = (row + page).coerceAtMost(list.lastIndex); fixRow(row); return@onPreviewKeyEvent true }
                        }
                        when (e.key) {
                            Key.DirectionUp -> { if (row == 0) { try { barFr.requestFocus() } catch (_: Exception) {} } else { row -= 1; fixRow(row) }; true }
                            Key.DirectionDown -> { row = if (row == list.lastIndex) 0 else row + 1; fixRow(row); true }
                            Key.DirectionRight -> {
                                pickedHour = -2
                                val next = progs.firstOrNull { it.start >= (curP?.end ?: (focusTime + 1)) }
                                focusTime = next?.start?.plus(1) ?: (focusTime + HALF)
                                keepVisible(next?.start ?: focusTime); true
                            }
                            Key.DirectionLeft -> {
                                pickedHour = -2
                                // holding Left: straight back to now
                                if (e.nativeKeyEvent.repeatCount > 0) { if (focusTime != now) { focusTime = now; viewStart = floorHalf(now) - HALF }; return@onPreviewKeyEvent true }
                                val min = minOf(ch)
                                val startNow = curP?.start ?: focusTime
                                val prev = progs.lastOrNull { it.end <= startNow }
                                if (startNow <= min || (prev == null && focusTime - HALF < min)) {
                                    // the start of what this channel can show: the groups slide in
                                    focusTime = now; viewStart = floorHalf(now) - HALF; onLeftEdge(); true
                                } else {
                                    focusTime = (prev?.start?.plus(1) ?: (focusTime - HALF)).coerceAtLeast(min)
                                    keepVisible(prev?.start ?: focusTime); true
                                }
                            }
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                // long OK: on a later show only the reminder, never the channel menu; on what is on now the channel menu
                                if (e.nativeKeyEvent.repeatCount > 0 && !longFired && ch != null) {
                                    longFired = true
                                    val pr = progsOf(ch).firstOrNull { it.start <= focusTime && it.end > focusTime }
                                    if (pr != null && pr.start > System.currentTimeMillis()) remindToggle(ctx, ch, pr) else chMenu = ch
                                }
                                true
                            }
                            else -> false
                        }
                    }.focusable(interactionSource = src)) {
                    itemsIndexed(list, key = { i, c -> "$i${c.url}" }) { i, ch ->
                        val sel = i == row && focused
                        Row(Modifier.fillMaxWidth().height(if (ListStyle.tivimate) 38.dp else 46.dp).padding(vertical = if (ListStyle.tivimate) 1.5.dp else 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.width(nameW).fillMaxHeight().clip(Rnd(10.dp)).background(if (i == row) C.CardFocus else Color.Transparent)
                                .padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                T("${ch.num}", 12.sp, if (i == row) C.RedText else C.Muted, modifier = Modifier.width(28.dp))
                                ChannelLogo(ch.logo, ch.name)
                                T(ch.name, 13.sp, if (i == row) C.Text else C.Soft, if (i == row) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, modifier = Modifier.weight(1f))
                            }
                            Box(Modifier.weight(1f).fillMaxHeight().clip(Rnd(4.dp))) {
                                val progs = progsOf(ch).filter { it.end > viewStart && it.start < viewEnd }
                                if (progs.isEmpty()) {
                                    Box(Modifier.fillMaxSize().padding(end = 2.dp).clip(Rnd(8.dp))
                                        .background(if (sel) C.Red.copy(alpha = ListStyle.strength * 0.85f) else C.Card)
                                        .then(if (sel) Modifier.border(2.dp, C.Red, Rnd(8.dp)) else Modifier).padding(horizontal = 10.dp),
                                        contentAlignment = Alignment.CenterStart) {
                                        T(if (epg.containsKey(ch.url)) "Bez programa" else "Učitavam…", 13.sp, C.Muted)
                                    }
                                } else progs.forEach { pr ->
                                    val x = ((pr.start.coerceAtLeast(viewStart) - viewStart) / MIN) * ppm
                                    val w = ((pr.end.coerceAtMost(viewEnd) - pr.start.coerceAtLeast(viewStart)) / MIN) * ppm
                                    val isFocus = sel && pr.start <= focusTime && pr.end > focusTime
                                    val isNow = pr.start <= now && pr.end > now
                                    val isPast = pr.end <= now
                                    Box(Modifier.offset(x = x.dp).width((w - 3).coerceAtLeast(2f).dp).fillMaxHeight().clip(Rnd(8.dp))
                                        .background(when { isFocus -> C.Red.copy(alpha = ListStyle.strength); isNow -> C.CardHi; isPast -> C.Card.copy(alpha = 0.55f); else -> C.Card })
                                        .then(if (isFocus) Modifier.border(2.dp, C.Red, Rnd(8.dp)) else Modifier).padding(horizontal = 8.dp),
                                        contentAlignment = Alignment.CenterStart) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                            if (isPast && ch.archive > 0) Ico(R.drawable.lc_history, 12.dp, if (isFocus) C.Text else C.Muted)
                                            if (Reminders.has(pr.title, pr.start)) Ico(R.drawable.lc_bell, 12.dp, C.Amber)
                                            T(pr.title, 13.sp, if (isFocus) C.Text else if (isPast) C.Muted else C.Soft, if (isNow) FontWeight.SemiBold else FontWeight.Normal)
                                        }
                                    }
                                }
                                // "now" line
                                if (now in viewStart until viewEnd) Box(Modifier.offset(x = (((now - viewStart) / MIN) * ppm).dp).width(2.dp).fillMaxHeight().background(C.Red))
                            }
                        }
                    }
                }
            }
        }
    }

    // typed channel number: jump after a short pause
    LaunchedEffect(numBuf) {
        if (numBuf.isEmpty()) return@LaunchedEffect
        delay(1300)
        val n = numBuf.toIntOrNull(); numBuf = ""
        val i = list.indexOfFirst { it.num == n }
        if (i >= 0) { row = i; if (focusTime < minOf(list[i])) focusTime = now; listState.scrollToItem((i - 2).coerceAtLeast(0)) }
        else Toast.makeText(ctx, trf("Nema kanala %s u ovoj grupi", n ?: 0), Toast.LENGTH_SHORT).show()
    }
    if (numBuf.isNotEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.clip(Rnd(16.dp)).background(C.Bg.copy(alpha = 0.95f)).border(2.dp, PICK, Rnd(16.dp)).padding(horizontal = 26.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            T("Broj kanala", 11.sp, C.Muted, FontWeight.SemiBold)
            T(numBuf.toList().joinToString(" ") + " _", 34.sp, PICK, FontWeight.ExtraBold)  // i18n-ignore
        }
    }

    fun play(ch: Channel) {
        val i = list.indexOfFirst { it.url == ch.url }.coerceAtLeast(0)
        gate.run("c:${ch.url}", Ovr(ctx, ch.pid).isChLocked(ch)) {
            TvMemory.channel = i; TvMemory.group = g; TvMemory.restore = true
            playChannels(nav, list, i)
        }
    }
    detail?.let { (ch, pr) ->
        val nowT = System.currentTimeMillis()
        val isNow = pr == null || (pr.start <= nowT && pr.end > nowT)
        val past = pr != null && pr.end <= nowT
        Popup(pr?.title ?: ch.name, onDismiss = { detail = null }) {
            T((pr?.times()?.plus(" · ") ?: "") + ch.name, 14.sp, C.Soft, maxLines = 1)
            cleanDesc(pr?.desc)?.let { T(it, 13.sp, C.Muted, maxLines = 6) }
            when {
                isNow -> {
                    OptionRow("Gledaj preko cijelog ekrana", R.drawable.lc_play, color = C.Green) { detail = null; play(ch) }
                    if (Look.previewMode != 2) OptionRow("Pregled u malom prozoru", R.drawable.lc_tv) { PreviewSel.ch = ch; detail = null }
                }
                past -> {
                    if (ch.archive > 0 && pr != null) OptionRow("Gledaj od početka (arhiva)", R.drawable.lc_history, color = C.Green) {
                        detail = null
                        val p = Store.byId(ctx, ch.pid)
                        val u = Catchups.url(p, ch, pr)
                        if (p != null && u != null) { Preview.stop(); nav.push(Screen.Player(listOf(PlayItem(pr.title, u, ch.logo, ch.name, null)), 0, live = false)) }
                    }
                    OptionRow("Gledaj kanal sada", R.drawable.lc_play) { detail = null; play(ch) }
                }
                else -> {
                    val has = Reminders.has(pr!!.title, pr.start)
                    OptionRow(if (has) "Ne podsjećaj me" else "Podsjeti me", R.drawable.lc_bell, color = if (has) null else C.Amber) {
                        val on = Reminders.toggle(Reminder("r${pr.start}${pr.title.hashCode()}", pr.title, pr.start, ch.url, ch.name, ch.logo, ch.pid, "tv"))
                        Toast.makeText(ctx, tr(if (on) "Podsjetnik postavljen" else "Podsjetnik uklonjen"), Toast.LENGTH_SHORT).show()
                        detail = null
                    }
                    OptionRow("Gledaj kanal sada", R.drawable.lc_play) { detail = null; play(ch) }
                }
            }
            OptionRow("Opcije kanala", R.drawable.lc_settings, sub = "Naziv, grupa, sakrij, PIN, favoriti") { detail = null; chMenu = ch }
        }
    }
    chMenu?.let { ch ->
        ChannelOptions(g, ch, list, groups, onDismiss = { chMenu = null; try { fr.requestFocus() } catch (_: Exception) {} }) { reload, close ->
            if (close) chMenu = null
            if (reload) onReload()
        }
    }
    val enter0 = remember { enter }
    // the grid takes focus only at start or after OK on a group, never while the groups are being browsed
    LaunchedEffect(list.isNotEmpty(), enter) { if (list.isNotEmpty() && (Chrome.rail == 0 || enter != enter0)) fr.tryFocus() }
}
