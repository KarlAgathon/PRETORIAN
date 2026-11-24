package com.pretorian.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * Spec 15: ONE Catch-Up engine, two entry points (the floating panel in the TV player and the TV › Catch Up browser).
 * Both read the archive through [cuArchive], decide what is playable with [cuItems] and show it with the same
 * day tabs and programme rows; playing goes through the existing archive links (Catchups.url).
 */

/** Archive lists kept for ten minutes, so moving through channels and coming back costs no new request. */
object CuCache {
    private val m = HashMap<String, Pair<Long, List<EpgItem>>>()
    @Synchronized fun peek(url: String): List<EpgItem>? = m[url]?.takeIf { System.currentTimeMillis() - it.first < 600_000 }?.second
    @Synchronized fun put(url: String, l: List<EpgItem>) { m[url] = System.currentTimeMillis() to l; if (m.size > 80) m.keys.firstOrNull()?.let { m.remove(it) } }
}

internal suspend fun cuArchive(p: Playlist, ch: Channel): List<EpgItem> = CuCache.peek(ch.url) ?: archiveOf(p, ch).also { CuCache.put(ch.url, it) }

/** One programme of the archive as both screens show it. [current] = airing now, offered from its start (Start Over). */
data class CuItem2(val e: EpgItem, val current: Boolean)

/** Only what can really be played: archived, finished, inside the channel's archive days; plus the show on air now
 *  (Xtream only, where the same archive link starts it from the beginning). Newest first. */
internal fun cuItems(p: Playlist?, ch: Channel, list: List<EpgItem>, now: Long): List<CuItem2> {
    val past = list.filter { it.archived && it.end <= now && !expired(ch, it.start) }.map { CuItem2(it, false) }
    val cur = if (p?.type == SourceType.XTREAM) list.firstOrNull { it.start <= now && now < it.end }?.let { CuItem2(it, true) } else null
    return (listOfNotNull(cur) + past).distinctBy { it.e.start }.sortedByDescending { it.e.start }
}

/** Day offsets (0 = today, 1 = yesterday …) that really hold playable shows. */
internal fun cuDays(items: List<CuItem2>): List<Int> =
    items.map { dayOffset(it.e.start) }.distinct().sorted()

internal fun dayOffset(t: Long): Int { var d = 0; while (d < 30 && t < Sports.dayStart(-d)) d++; return d }

private fun hm(t: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t))  // i18n-ignore

internal fun durLabel(ms: Long): String {
    val m = (ms / 60_000).toInt().coerceAtLeast(1)
    return if (m >= 60) trf("%s h %s min", m / 60, m % 60) else trf("%s min", m)
}

/** The date tabs: [visible] at once, the chosen one with a thin silver frame; moving onto a tab chooses it. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun CuDayTabs(days: List<Int>, sel: Int, visible: Int, onSel: (Int) -> Unit) {
    val frs = remember(days) { days.associateWith { FocusRequester() } }
    val st = rememberLazyListState()
    LaunchedEffect(days) { runCatching { st.scrollToItem((days.indexOf(sel) - visible + 1).coerceAtLeast(0)) } }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 8.dp
        val w = (maxWidth - gap * (visible - 1)) / visible
        LazyRow(Modifier.fillMaxWidth().focusProperties { enter = { frs[sel] ?: FocusRequester.Default } }, state = st,
            horizontalArrangement = Arrangement.spacedBy(gap)) {
            itemsIndexed(days, key = { _, d -> d }) { _, d ->
                val on = d == sel
                val t = Sports.dayStart(-d)
                val name = when (d) { 0 -> tr("Danas"); 1 -> tr("Jučer"); else -> SimpleDateFormat("EEE d. MMM", appLocale()).format(Date(t)) }  // i18n-ignore
                Focusable(onClick = { onSel(d) }, onFocus = { onSel(d) }, focusRequester = frs[d], shape = Rnd(10.dp),
                    normal = if (on) Color(0x33FFFFFF) else Color(0x14FFFFFF), focused = Color(0x40FFFFFF),
                    borderColor = if (on) Color(0xB3FFFFFF) else Color(0x1FFFFFFF), focusBorderColor = Color(0xE6FFFFFF), zoom = 1f,
                    modifier = Modifier.width(w).height(52.dp)) { f ->
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        T(name, 15.sp, if (f || on) Color.White else Color(0xFFC9CED6), if (on) FontWeight.SemiBold else FontWeight.Normal)
                        if (d <= 1) T(SimpleDateFormat("EEE d. MMM", appLocale()).format(Date(t)), 12.sp, Color(0xFF9AA3AF))  // i18n-ignore
                    }
                }
            }
        }
    }
}

/** One programme row: from–to, title, a short line, length, and ▶ (only rows that can play are ever listed). */
@Composable
internal fun CuProgRow(it: CuItem2, progress: Progress?, fr: FocusRequester?, height: Dp, onLong: (() -> Unit)?, onClick: () -> Unit) {
    val e = it.e
    Focusable(onClick = onClick, onMenu = onLong, focusRequester = fr, shape = Rnd(12.dp), normal = Color(0x1AFFFFFF), focused = Color(0x40FFFFFF),
        borderColor = Color(0x14FFFFFF), focusBorderColor = Color(0xE6FFFFFF), zoom = 1f, modifier = Modifier.fillMaxWidth().height(height)) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            T(hm(e.start) + " – " + hm(e.end), 16.sp, if (f) Color.White else Color(0xFFC9CED6), modifier = Modifier.width(120.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                T(e.title, 17.sp, Color.White, FontWeight.SemiBold, maxLines = 1)
                val sub = if (it.current) tr("U toku · gledaj od početka") else e.desc?.trim()?.ifBlank { null }
                if (sub != null) T(sub, 13.sp, Color(0xFF9AA3AF), maxLines = 1)
                if (progress != null && progress.started && !progress.watched) Box(Modifier.fillMaxWidth().height(3.dp).background(Color(0x33FFFFFF))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(progress.fraction.coerceIn(0.02f, 1f)).background(Color.White))
                }
            }
            T(if (progress?.watched == true) "✓" else durLabel(e.end - e.start), 14.sp, Color(0xFFB0B8C4))
            Box(Modifier.size(34.dp).border(1.dp, if (f) Color.White else Color(0x80FFFFFF), Rnd(8.dp)), contentAlignment = Alignment.Center) {
                Ico(R.drawable.lc_play, 16.dp, Color.White)
            }
        }
    }
}

/* ------------------------------------------------------------ TV › Catch Up: channels → archive */

/** The channel level: channels with archive on the left, the chosen one on the right with its last shows. */
@Composable
internal fun CuChannelBrowser(chans: List<Channel>, mem: HashMap<String, Int>, onContinue: () -> Unit, onOpen: (Channel) -> Unit) {
    val ctx = LocalContext.current
    val rv = CatchupHist.rev + Watch.rev
    val open = remember(rv) { CatchupHist.open(ctx).size }
    val byId = remember(chans) { chans.associateBy { "c:" + it.url } }
    val rows = remember(chans, open) {
        buildList {
            if (open > 0) add(BrowseRow("cont", tr("Nastavi gledati"), R.drawable.lc_play, count = open.toString(), open = onContinue))
            chans.forEachIndexed { i, c ->
                add(BrowseRow("c:" + c.url, c.name, ch = c, badge = qualityTag(c.name), gapBefore = open > 0 && i == 0) { onOpen(c) })
            }
        }
    }
    TwoColBrowser(rows, mem, "chans", tr("Pregledaj arhivu"), R.drawable.lc_play, panel = { r ->
        val c = byId[r.id]
        if (c == null) BrowsePanelText(tr("NASTAVI GLEDATI"), tr("Nastavi gledati"), trf("Započetih emisija: %s", open),
            tr("Emisije iz arhive koje nisi dogledao, od mjesta gdje si stao."))
        else CuChannelPanel(c)
    })
}

@Composable
private fun CuChannelPanel(c: Channel) {
    val ctx = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        ChannelLogo(c.logo, c.name)
        T(tr("IZABRANI KANAL"), 13.sp, Color(0xFF9AA3AF), FontWeight.SemiBold)
    }
    Spacer(Modifier.height(8.dp))
    T(c.name, 32.sp, Color.White, FontWeight.Bold, maxLines = 2)
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Ico(R.drawable.lc_history, 16.dp, Color(0xFFC9CED6))
        T(trf("Arhiva dostupna · %s dana", c.archive.coerceIn(1, 14)), 16.sp, Color(0xFFC9CED6))
    }
    Spacer(Modifier.height(16.dp))
    // the last shows load only once focus rests on a channel; a late answer for another channel is never shown (keyed by url)
    val recent by produceState<List<CuItem2>?>(CuCache.peek(c.url)?.let { cuItems(Store.byId(ctx, c.pid), c, it, System.currentTimeMillis()) }, c.url) {
        if (value != null) return@produceState
        delay(350)
        val p = Store.byId(ctx, c.pid)
        value = try { if (p == null) emptyList() else cuItems(p, c, cuArchive(p, c), System.currentTimeMillis()) } catch (e: Exception) { emptyList() }
    }
    Column(Modifier.fillMaxWidth().background(Color(0x14FFFFFF), Rnd(14.dp)).border(1.dp, Color(0x1FFFFFFF), Rnd(14.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val l = recent
        when {
            l == null -> T(tr("Učitavam arhivu…"), 15.sp, Color(0xFF9AA3AF))
            l.isEmpty() -> T(tr("Lista emisija trenutno nije dostupna."), 15.sp, Color(0xFF9AA3AF))
            else -> {
                T(dayLabel(l.first().e.start), 17.sp, Color.White, FontWeight.SemiBold)
                l.take(3).forEach { T(hm(it.e.start) + " — " + it.e.title, 16.sp, Color(0xFFD9DDE3)) }
            }
        }
    }
}

/** A channel's archive: name and days on top, the shows of the chosen day below (OK plays or resumes, long OK = more). */
@Composable
internal fun CuArchiveView(ch: Channel, mem: HashMap<String, Int>, key: String, nav: Nav) {
    val ctx = LocalContext.current
    val p = Store.byId(ctx, ch.pid)
    val list by load(ch.url) { if (p == null) emptyList() else cuArchive(p, ch) }
    @Suppress("UNUSED_VARIABLE") val wr = Watch.rev
    var pick by remember { mutableStateOf<EpgItem?>(null) }
    Column(Modifier.fillMaxSize().padding(top = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ChannelLogo(ch.logo, ch.name)
            T(ch.name, 26.sp, Color.White, FontWeight.Bold, modifier = Modifier.weight(1f))
            T(trf("Arhiva: %s dana", ch.archive.coerceIn(1, 14)), 15.sp, Color(0xFF9AA3AF))
        }
        when (val s = list) {
            is Load.Loading -> Loading("Učitavam arhivu…")
            is Load.Err -> Message("Arhiva nije dostupna", s.message)
            is Load.Ok -> {
                val items = remember(s.value) { cuItems(p, ch, s.value, System.currentTimeMillis()) }
                val days = remember(items) { cuDays(items) }
                if (days.isEmpty()) { Message("Nema emisija u arhivi", null); return@Column }
                var day by remember { mutableIntStateOf(mem[key + ":day"]?.takeIf { it in days } ?: days.first()) }
                CuDayTabs(days, day, 6) { day = it; mem[key + ":day"] = it }
                val shown = items.filter { dayOffset(it.e.start) == day }
                val first = remember(day) { FocusRequester() }
                LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    itemsIndexed(shown, key = { _, it -> it.e.start }) { i, it ->
                        val url = Catchups.url(p, ch, it.e)
                        val pr = url?.let { u -> Watch.get(ctx, u) }
                        CuProgRow(it, pr, if (i == 0) first else null, 66.dp, onLong = { pick = it.e }) {
                            cuPlay(ctx, nav, ch, it.e, if (pr != null && pr.started && !pr.watched) pr.pos else 0L)
                        }
                    }
                }
                LaunchedEffect(Unit) { first.tryFocus() }
            }
        }
    }
    pick?.let { e -> CuActions(ch, e, nav) { pick = null } }
}

/* ------------------------------------------------------------ the floating panel in the TV player */

/** Catch Up above the dock: the channel that is on, its archive days, three shows at a time. */
@Composable
fun LiveCatchupPanel(ch: Channel?, p: Playlist?, chName: String, list: Load<List<EpgItem>>, onPlay: (EpgItem) -> Unit, first: FocusRequester) {
    val ctx = LocalContext.current
    val days0 = ch?.archive?.takeIf { it > 0 }?.coerceAtMost(14)
    val title = tr("Catch Up") + " · " + chName + (days0?.let { " · " + trf("%s dana", it) } ?: "")
    LivePanel(title, R.drawable.lc_history, true, first) {
        when (list) {
            is Load.Loading -> LiveRow(tr("Učitavam arhivu…"), fr = first) {}
            is Load.Err -> LiveRow(tr("Catch-Up nije dostupan za ovaj kanal"), sub = list.message, fr = first) {}
            is Load.Ok -> {
                val items = remember(list.value) { if (ch == null) emptyList() else cuItems(p, ch, list.value, System.currentTimeMillis()) }
                val days = remember(items) { cuDays(items) }
                if (ch == null || days.isEmpty()) { LiveRow(tr("Catch-Up nije dostupan za ovaj kanal"), fr = first) {}; return@LivePanel }
                var day by remember { mutableIntStateOf(days.first()) }
                CuDayTabs(days, day, 4) { day = it }
                val shown = items.filter { dayOffset(it.e.start) == day }
                val rowH = 62.dp
                LazyColumn(Modifier.fillMaxWidth().height(rowH * 3 + 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(shown, key = { _, it -> it.e.start }) { i, it ->
                        val pr = Catchups.url(p, ch, it.e)?.let { u -> Watch.get(ctx, u) }
                        CuProgRow(it, pr, if (i == 0) first else null, rowH, onLong = null) { onPlay(it.e) }
                    }
                }
                // the loading row that held the focus is gone now: give it to the newest show
                LaunchedEffect(Unit) { delay(60); first.tryFocus() }
            }
        }
    }
}
