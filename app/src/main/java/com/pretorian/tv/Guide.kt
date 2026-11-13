package com.pretorian.tv

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
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
private fun floorHalf(t: Long) = t - (t % HALF)

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
            GroupColumn(rows, st.group, st.focusKey, st.focusFr, st.wrap, gState, 250.dp, "TV", null,
                onFocusGroup = { focusedGroup = it; Chrome.rail = 3 },
                onOpen = { openGroup(ctx, st, it); st.enter++ },
                onMenu = { st.focusKey = "g:" + it.key; st.groupMenu = it },
                onHeader = { p -> Store.open(ctx, p.id); st.focusKey = "h:" + p.id; st.rev++; st.refocus++ },
                onNewFav = { st.newFav = true }, onNewGroup = { st.newGroupFor = it },
                onFocusRow = { st.focusKey = it; Chrome.rail = 3 },
                extra = {
                    Focusable(modifier = Modifier.padding(start = 4.dp, bottom = 8.dp).height(36.dp), onClick = onList, normal = C.Card, shape = Pill, zoom = 1.03f) { _ ->
                        Row(Modifier.align(Alignment.Center).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Ico(R.drawable.lc_list, 16.dp, C.RedText); T("Lista kanala", 13.sp, C.Text, FontWeight.SemiBold)
                        }
                    }
                })
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

    // guide of the rows around the focused one
    LaunchedEffect(row, list) {
        for (i in (row - 2).coerceAtLeast(0)..(row + 9).coerceAtMost(list.lastIndex)) {
            val ch = list[i]
            if (epg.containsKey(ch.url)) continue
            val p = Store.byId(ctx, ch.pid) ?: continue
            val short = try { Repo.epg(p, ch, 40) } catch (e: Exception) { emptyList() }
            epg[ch.url] = short.ifEmpty { try { ProgIndex.forChannel(ctx, ch) } catch (e: Exception) { emptyList() } }
        }
    }
    LaunchedEffect(row) {
        if (list.isEmpty()) return@LaunchedEffect
        val vis = listState.layoutInfo.visibleItemsInfo
        if (vis.none { it.index == row } || vis.lastOrNull()?.index == row) listState.animateScrollToItem((row - 2).coerceAtLeast(0))
    }

    fun progsOf(ch: Channel?) = ch?.let { epg[it.url] } ?: emptyList()
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
            if (start < viewStart) viewStart = floorHalf(start).coerceAtLeast(floorHalf(now))
            else if (start > viewEnd - HALF) viewStart = floorHalf(start) - HALF
        }

        Column(Modifier.fillMaxSize().padding(top = 16.dp, start = 12.dp, end = 4.dp)) {
            // ---------------------------------------------------------------- picture and show
            val cur = current()
            Row(Modifier.fillMaxWidth().height(176.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                when (Look.previewMode) {
                    2 -> PreviewBox(cur?.first, Modifier.height(170.dp))
                    1 -> PreviewSel.ch?.let { PreviewBox(it, Modifier.height(170.dp)) }
                }
                Column(Modifier.weight(1f).padding(top = 4.dp, end = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val pr = cur?.second
                    T(pr?.title ?: cur?.first?.name ?: "", 22.sp, C.Text, FontWeight.Bold, maxLines = 2)
                    if (pr != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        T(pr.times(), 14.sp, C.Soft, FontWeight.SemiBold)
                        if (pr.start <= now && pr.end > now) {
                            Box(Modifier.width(70.dp).height(4.dp).clip(Rnd(2.dp)).background(C.CardHi)) {
                                Box(Modifier.fillMaxWidth(pr.progress(now)).height(4.dp).background(C.Red))
                            }
                            T(trf("još %s min", (pr.end - now) / MIN), 13.sp, C.Muted)
                        }
                        if (Reminders.has(pr.title, pr.start)) Ico(R.drawable.lc_bell, 15.dp, C.Amber)
                    }
                    cur?.first?.let { T(it.name, 13.sp, C.RedText, FontWeight.SemiBold) }
                    cleanDesc(pr?.desc)?.let { T(it, 13.sp, C.Soft, maxLines = 4) }
                }
            }
            // ---------------------------------------------------------------- time ruler
            Row(Modifier.fillMaxWidth().height(28.dp).padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                T(SimpleDateFormat("EEE, d. MMM, HH:mm", Locale(Look.lang)).format(Date(now)), 14.sp, C.RedText, FontWeight.SemiBold,
                    modifier = Modifier.width(nameW))
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
                            detail = ch to progsOf(ch).firstOrNull { it.start <= focusTime && it.end > focusTime }
                            return@onPreviewKeyEvent true
                        }
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val ch = list.getOrNull(row)
                        val progs = progsOf(ch)
                        val curP = progs.firstOrNull { it.start <= focusTime && it.end > focusTime }
                        when (e.key) {
                            Key.DirectionUp -> { row = if (row == 0) list.lastIndex else row - 1; true }
                            Key.DirectionDown -> { row = if (row == list.lastIndex) 0 else row + 1; true }
                            Key.DirectionRight -> {
                                val next = progs.firstOrNull { it.start >= (curP?.end ?: (focusTime + 1)) }
                                focusTime = next?.start?.plus(1) ?: (focusTime + HALF)
                                keepVisible(next?.start ?: focusTime); true
                            }
                            Key.DirectionLeft -> {
                                val startNow = curP?.start ?: focusTime
                                if (startNow <= now || focusTime - HALF < now) { focusTime = now; viewStart = floorHalf(now) - HALF; onLeftEdge(); true }
                                else {
                                    val prev = progs.lastOrNull { it.end <= (curP?.start ?: focusTime) }
                                    focusTime = (prev?.start?.plus(1) ?: (focusTime - HALF)).coerceAtLeast(now)
                                    keepVisible(prev?.start ?: focusTime); true
                                }
                            }
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                if (e.nativeKeyEvent.repeatCount > 0 && !longFired && ch != null) { longFired = true; chMenu = ch }
                                true
                            }
                            else -> false
                        }
                    }.focusable(interactionSource = src)) {
                    itemsIndexed(list, key = { i, c -> "$i${c.url}" }) { i, ch ->
                        val sel = i == row && focused
                        Row(Modifier.fillMaxWidth().height(46.dp).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.width(nameW).fillMaxHeight().clip(Rnd(10.dp)).background(if (i == row) C.CardFocus else Color.Transparent)
                                .padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                T("${ch.num}", 12.sp, if (i == row) C.RedText else C.Muted, modifier = Modifier.width(28.dp))
                                ChannelLogo(ch.logo, ch.name)
                                T(ch.name, 13.sp, if (i == row) C.Text else C.Soft, if (i == row) FontWeight.SemiBold else FontWeight.Normal, maxLines = 2, modifier = Modifier.weight(1f))
                            }
                            Box(Modifier.weight(1f).fillMaxHeight().clip(Rnd(4.dp))) {
                                val progs = progsOf(ch).filter { it.end > viewStart && it.start < viewEnd }
                                if (progs.isEmpty()) {
                                    Box(Modifier.fillMaxSize().padding(end = 2.dp).clip(Rnd(8.dp))
                                        .background(if (sel) C.Red.copy(alpha = 0.35f) else C.Card)
                                        .then(if (sel) Modifier.border(2.dp, C.Red, Rnd(8.dp)) else Modifier).padding(horizontal = 10.dp),
                                        contentAlignment = Alignment.CenterStart) {
                                        T(if (epg.containsKey(ch.url)) "Bez programa" else "Učitavam…", 13.sp, C.Muted)
                                    }
                                } else progs.forEach { pr ->
                                    val x = ((pr.start.coerceAtLeast(viewStart) - viewStart) / MIN) * ppm
                                    val w = ((pr.end.coerceAtMost(viewEnd) - pr.start.coerceAtLeast(viewStart)) / MIN) * ppm
                                    val isFocus = sel && pr.start <= focusTime && pr.end > focusTime
                                    val isNow = pr.start <= now && pr.end > now
                                    Box(Modifier.offset(x = x.dp).width((w - 3).coerceAtLeast(2f).dp).fillMaxHeight().clip(Rnd(8.dp))
                                        .background(when { isFocus -> C.Red.copy(alpha = 0.45f); isNow -> C.CardHi; else -> C.Card })
                                        .then(if (isFocus) Modifier.border(2.dp, C.Red, Rnd(8.dp)) else Modifier).padding(horizontal = 8.dp),
                                        contentAlignment = Alignment.CenterStart) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                            if (Reminders.has(pr.title, pr.start)) Ico(R.drawable.lc_bell, 12.dp, C.Amber)
                                            T(pr.title, 13.sp, if (isFocus) C.Text else C.Soft, if (isNow) FontWeight.SemiBold else FontWeight.Normal)
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
                        if (p != null) { Preview.stop(); nav.push(Screen.Player(listOf(PlayItem(pr.title, Xtream(p).timeshiftUrl(ch.id, pr), ch.logo, ch.name, null)), 0, live = false)) }
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
