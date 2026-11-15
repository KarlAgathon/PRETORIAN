package com.pretorian.tv

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/* ============================================================== small pieces of the new look */

/** Glass panel: a see-through box with a thin line around it. */
fun Modifier.glass(r: Dp = 18.dp): Modifier = this.clip(Rnd(r))
    .background(if (C.IsLight) Color(0x0F000000) else Color(0x14FFFFFF))
    .border(1.dp, C.Line, Rnd(r))

/** Small spaced capitals above a block ("MOJE", "KATEGORIJE PROVIDERA"). */
@Composable
fun Kicker(t: String, color: Color = C.Muted, size: TextUnit = 10.sp, modifier: Modifier = Modifier) {
    Text(tr(t).uppercase(), style = TextStyle(fontFamily = Inter, fontSize = size, color = color, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp),
        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = modifier)
}

/** Colour of a channel's progress line, the same for the same channel every time. */
fun hueOf(name: String): Color {
    val hues = listOf(0xFF3B82F6, 0xFFEF4444, 0xFF14B8A6, 0xFFF97316, 0xFF8B5CF6, 0xFF22C55E, 0xFFEC4899, 0xFFEAB308)
    return Color(hues[(name.hashCode() and 0x7fffffff) % hues.size])
}

/** What is on now, per channel, kept until the show ends (asked only for the rows you see, three at a time). */
object NowCache {
    private val m = HashMap<String, EpgItem>()
    private val none = HashMap<String, Long>()
    val gate = Semaphore(3)
    fun get(url: String): EpgItem? = synchronized(m) { m[url]?.takeIf { it.end > System.currentTimeMillis() } }
    fun put(url: String, e: EpgItem?) = synchronized(m) { if (e != null) m[url] = e else none[url] = System.currentTimeMillis() }
    fun empty(url: String) = synchronized(m) { (none[url] ?: 0L) > System.currentTimeMillis() - 10 * 60_000L }
}

@Composable
fun nowOf(ch: Channel, wait: Long = 400): EpgItem? {
    val ctx = LocalContext.current
    val v by produceState(NowCache.get(ch.url), ch.url) {
        if (value != null || NowCache.empty(ch.url)) return@produceState
        delay(wait)   // while you scroll fast nothing is asked
        val p = Store.byId(ctx, ch.pid) ?: return@produceState
        val now = System.currentTimeMillis()
        val e = NowCache.gate.withPermit { try { Repo.epg(p, ch, 3) } catch (e: Exception) { emptyList() } }.firstOrNull { it.start <= now && it.end > now }
        NowCache.put(ch.url, e); value = e
    }
    return v
}

@Composable
fun ThinBar(f: Float, color: Color, modifier: Modifier = Modifier, h: Dp = 2.dp) {
    Box(modifier.height(h).clip(Pill).background(C.Line)) { Box(Modifier.fillMaxWidth(f.coerceIn(0.02f, 1f)).fillMaxHeight().background(color)) }
}

/* ============================================================== TV */

private sealed class TRow {
    data class Title(val t: String) : TRow()
    data class Grp(val ref: GroupRef) : TRow()
    data class Pl(val p: Playlist) : TRow()
    object NewFav : TRow()
    val id get() = when (this) { is Title -> "t:$t"; is Grp -> "g:" + ref.key; is Pl -> "p:" + p.id; NewFav -> "nf" }
}

private suspend fun tvRows(ctx: android.content.Context): List<TRow> {
    val out = ArrayList<TRow>()
    out.add(TRow.Title("Moje"))
    Favs.lists(ctx).forEach { out.add(TRow.Grp(GroupRef(null, Category(FAV_PREFIX + it.id, it.name)))) }
    if (Recent.list().isNotEmpty()) out.add(TRow.Grp(GroupRef(null, Category(RECENT_GROUP, tr("Zadnje gledano")))))
    if (!Lock.simple) out.add(TRow.NewFav)
    val pls = Store.actives(ctx)
    pls.firstOrNull()?.let { p ->
        out.add(TRow.Title("Kategorije providera"))
        out.add(TRow.Grp(GroupRef(p.id, Category(ALL_GROUP, tr("Svi kanali")))))
        Ovr(ctx, p.id).groups("live", Repo.liveGroups(p)).forEach { out.add(TRow.Grp(GroupRef(p.id, it))) }
    }
    if (pls.size > 1) { out.add(TRow.Title("Druge playliste")); pls.drop(1).forEach { out.add(TRow.Pl(it)) } }
    return out
}

/** TV in the new look: groups · channels with what is on · picture and programme, last watched at the bottom. */
@Composable
fun OnyxTv(nav: Nav, onGuide: () -> Unit) {
    val ctx = LocalContext.current
    val st = remember { TvState() }
    val rows by load(st.rev, Favs.rev, Recent.rev, keep = true) { tvRows(ctx) }
    // how many channels each group has (one list of all channels, read in the background)
    val counts by produceState(emptyMap<String, Int>(), st.rev) {
        delay(1500)
        val p = Store.active(ctx) ?: return@produceState
        value = try { val all = Repo.allChannels(p); all.groupingBy { it.group }.eachCount() + (ALL_GROUP to all.size) } catch (e: Exception) { emptyMap() }
    }
    val gState = rememberLazyListState()
    val coming = remember { TvMemory.restore && TvMemory.group != null && TvMemory.channel >= 0 }
    var inChannels by remember { mutableStateOf(coming) }
    var focusedCh by remember { mutableStateOf<Channel?>(null) }
    var focusedGroup by remember { mutableStateOf<GroupRef?>(null) }
    var enterTick by remember { mutableIntStateOf(0) }
    val gFr = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (!coming) { TvMemory.restore = false; st.refocus++ } }
    // coming back from a channel: the group list shows that channel's group too
    LaunchedEffect(rows is Load.Ok) {
        val list = (rows as? Load.Ok)?.value ?: return@LaunchedEffect
        if (!coming) return@LaunchedEffect
        val idx = list.indexOfFirst { it.id == "g:" + (st.group?.key ?: "") }
        if (idx >= 0) { st.focusKey = list[idx].id; gState.scrollToItem((idx - 3).coerceAtLeast(0)) }
    }
    fun backToGroups() { st.focusKey = "g:" + (st.group?.key ?: ""); inChannels = false; st.refocus++ }
    BackHandler(enabled = inChannels && st.groupMenu == null) { backToGroups() }
    // a group opens only with OK; moving over the list changes nothing on the right
    var preview by remember { mutableStateOf(TvMemory.preview) }
    LaunchedEffect(st.refocus, rows is Load.Ok) {
        val list = (rows as? Load.Ok)?.value ?: return@LaunchedEffect
        if (st.refocus == 0) return@LaunchedEffect
        if (st.focusKey.isEmpty() || list.none { it.id == st.focusKey })
            st.focusKey = st.group?.let { "g:" + it.key }?.takeIf { k -> list.any { it.id == k } } ?: list.firstOrNull { it is TRow.Grp }?.id ?: ""
        val idx = list.indexOfFirst { it.id == st.focusKey }.coerceAtLeast(0)
        if (gState.layoutInfo.visibleItemsInfo.none { it.index == idx }) gState.scrollToItem((idx - 3).coerceAtLeast(0))
        gFr.tryFocus()
    }
    LaunchedEffect(rows) { if (st.group == null) (rows as? Load.Ok)?.value?.firstOrNull { it is TRow.Grp && (it.ref.pid != null) }?.let { st.group = (it as TRow.Grp).ref } }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // ---------------------------------------------------------- groups
            Column(Modifier.width(196.dp).fillMaxHeight().glass().padding(vertical = 8.dp)) {
                when (val r = rows) {
                    is Load.Loading -> Loading("")
                    is Load.Err -> Message("Greška", r.message)
                    is Load.Ok -> LazyColumn(state = gState, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(r.value, key = { it.id }) { row ->
                            when (row) {
                                is TRow.Title -> Kicker(row.t, modifier = Modifier.padding(start = 10.dp, top = 8.dp, bottom = 4.dp))
                                is TRow.Grp -> {
                                    val g = row.ref
                                    val sel = st.group?.key == g.key
                                    val locked = g.pid?.let { Ovr(ctx, it).isLocked("live", g.cat.id) } ?: false
                                    val n = when {
                                        g.cat.id == RECENT_GROUP -> Recent.list().size
                                        g.pid == null -> Favs.channels(favListId(g.cat.id)).size
                                        else -> counts[g.cat.id]
                                    }
                                    Focusable(modifier = Modifier.fillMaxWidth().height(34.dp), shape = Rnd(10.dp),
                                        onClick = { openGroup(ctx, st, g); inChannels = true; enterTick++ },
                                        onFocus = { st.focusKey = row.id; focusedGroup = g; inChannels = false },
                                        onMenu = if (Lock.simple || g.cat.id == ALL_GROUP || g.cat.id == RECENT_GROUP) null else ({ st.focusKey = row.id; st.groupMenu = g }),
                                        focusRequester = if (row.id == st.focusKey) gFr else null,
                                        normal = if (sel) C.CardHi else Color.Transparent, focused = C.Red, zoom = 1f) { f ->
                                        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            T(g.cat.name, 13.sp, if (f) C.OnRed else if (sel) C.Text else C.Soft, if (sel || f) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                                            if (locked) Ico(R.drawable.lc_lock, 12.dp, if (f) C.OnRed else C.Muted)
                                            if (n != null) T(fmtNum(n), 11.sp, if (f) C.OnRed.copy(alpha = 0.7f) else C.Muted)
                                        }
                                    }
                                }
                                is TRow.Pl -> Focusable(modifier = Modifier.fillMaxWidth().height(34.dp), shape = Rnd(10.dp), onClick = {
                                    Store.open(ctx, row.p.id); st.group = null; st.rev++; st.refocus++
                                }, onFocus = { st.focusKey = row.id }, focusRequester = if (row.id == st.focusKey) gFr else null, normal = Color.Transparent, focused = C.Red, zoom = 1f) { f ->
                                    Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Ico(R.drawable.lc_list, 12.dp, if (f) C.OnRed else C.Muted)
                                        T(row.p.name, 13.sp, if (f) C.OnRed else C.Soft, modifier = Modifier.weight(1f))
                                    }
                                }
                                TRow.NewFav -> Focusable(modifier = Modifier.fillMaxWidth().height(30.dp), shape = Rnd(10.dp), onClick = { st.newFav = true },
                                    onFocus = { st.focusKey = row.id }, focusRequester = if (row.id == st.focusKey) gFr else null, normal = Color.Transparent, focused = C.Red, zoom = 1f) { f ->
                                    Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Ico(R.drawable.lc_plus, 12.dp, if (f) C.OnRed else C.Muted); T("Nova lista favorita", 12.sp, if (f) C.OnRed else C.Muted)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // ---------------------------------------------------------- channels
            val g = st.group
            Box(Modifier.weight(1f).fillMaxHeight().glass().onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft) { backToGroups(); true } else false
            }) {
                if (g == null) Message("Izaberi grupu")
                else key(g.key, st.rev) { OnyxChannels(g, nav, st, rows, enterTick, preview, { preview = it; TvMemory.preview = it }) { focusedCh = it; inChannels = true } }
            }
            // ---------------------------------------------------------- picture and programme
            NowPanel(preview, Modifier.width(272.dp).fillMaxHeight())
        }
        RecentStrip(nav)
    }
    TvDialogsRows(st, rows)
}

/** 1240 → "1.240" */
fun fmtNum(n: Int): String = if (n < 1000) "$n" else "%d.%03d".format(n / 1000, n % 1000)

@Composable
private fun TvDialogsRows(st: TvState, rows: Load<List<TRow>>) {
    val grows: Load<List<GRow>> = when (rows) {
        is Load.Ok -> Load.Ok(rows.value.mapNotNull { (it as? TRow.Grp)?.let { g -> GRow.Group(g.ref) } })
        is Load.Err -> rows
        else -> Load.Loading
    }
    TvDialogs(st, grows)
}

@Composable
private fun OnyxChannels(g: GroupRef, nav: Nav, st: TvState, rows: Load<List<TRow>>, enterTick: Int, preview: Channel?, onPreview: (Channel) -> Unit, onFocusCh: (Channel) -> Unit) {
    val ctx = LocalContext.current
    var listRev by remember { mutableIntStateOf(0) }
    val isFav = g.pid == null
    val state by load(g.key, listRev, Favs.rev, keep = true) { channelsOf(ctx, g) }
    var menu by remember { mutableStateOf<Channel?>(null) }
    val cWrap = remember { Wrap() }
    val cState = rememberLazyListState()
    WrapEffect(cWrap, cState)
    Column(Modifier.fillMaxSize().padding(top = 12.dp, start = 12.dp, end = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, bottom = 6.dp)) {
            Kicker(g.cat.name, C.Soft, modifier = Modifier.weight(1f))
            (state as? Load.Ok)?.value?.size?.let { Kicker(trf("%s kanala", fmtNum(it)), C.Muted) }
        }
        when (val s = state) {
            is Load.Loading -> Loading("Učitavam kanale…")
            is Load.Err -> Message("Kanali se nisu učitali", s.message)
            is Load.Ok -> {
                val list = s.value
                if (list.isEmpty()) Message(if (isFav) "Nema favorita" else "Grupa je prazna", if (isFav) "Na kanalu drži OK › Dodaj u favorite" else null)
                else LazyColumn(state = cState, verticalArrangement = Arrangement.spacedBy(3.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    itemsIndexed(list, key = { i, ch -> "$i${ch.url}" }) { i, ch ->
                        val ovr = remember(ch.pid, g.pid) { Ovr(ctx, ch.pid.ifBlank { g.pid ?: "" }) }
                        val locked = ovr.isChLocked(ch)
                        Focusable(modifier = Modifier.fillMaxWidth().height(50.dp).wrapKeys(i, list.size, cWrap), shape = Rnd(12.dp),
                            onClick = {
                                st.gate.run("c:${ch.url}", locked) {
                                    // first OK: this channel plays in the small picture; OK again on the same channel: full screen
                                    TvMemory.channel = i; TvMemory.group = g
                                    if (preview?.url == ch.url) { ovr.countWatch(ch); TvMemory.restore = true; playChannels(nav, list, i) }
                                    else onPreview(ch)
                                }
                            },
                            onMenu = { menu = ch }, onFocus = { onFocusCh(ch) }, focusRequester = cWrap.requester(i),
                            normal = Color.Transparent, focused = C.Red, zoom = 1.01f) { f ->
                            val now = nowOf(ch)
                            Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                T("${ch.num}", 11.sp, if (f) C.OnRed.copy(alpha = 0.6f) else C.Muted, modifier = Modifier.width(26.dp), align = androidx.compose.ui.text.style.TextAlign.End)
                                ChannelLogo(ch, 34.dp, onLight = f)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    T(ovr.chName(ch), 13.sp, if (f) C.OnRed else C.Text, FontWeight.SemiBold)
                                    T(now?.title ?: " ", 11.sp, if (f) C.OnRed.copy(alpha = 0.65f) else C.Muted)
                                    if (now != null) ThinBar(now.progress(), if (f) C.OnRed else hueOf(ch.name), Modifier.fillMaxWidth())
                                }
                                if (preview?.url == ch.url) Ico(R.drawable.lc_play, 13.dp, if (f) C.OnRed else C.Green)
                                if (locked) Ico(R.drawable.lc_lock, 13.dp, if (f) C.OnRed else C.Muted)
                                if (!isFav && Favs.inAny(ctx, ch)) T("★", 13.sp, Color(0xFFFF5A64))
                            }
                        }
                    }
                }
                LaunchedEffect(list) { if (TvMemory.restore && TvMemory.channel >= 0) { TvMemory.restore = false; cWrap.target = TvMemory.channel.coerceAtMost(list.lastIndex) } }
                val enter0 = remember { enterTick }
                LaunchedEffect(enterTick, list.isNotEmpty()) { if (enterTick != enter0 && list.isNotEmpty()) cWrap.target = TvMemory.channel.coerceIn(0, list.lastIndex) }
                menu?.let { ch ->
                    val groups = (rows as? Load.Ok)?.value?.mapNotNull { (it as? TRow.Grp)?.ref }
                        ?.filter { it.pid != null && it.pid == ch.pid.ifBlank { g.pid } && it.cat.id != ALL_GROUP }?.map { it.cat } ?: emptyList()
                    ChannelOptions(g, ch, list, groups, onDismiss = { menu = null }) { reload, close ->
                        listRev++; if (close) menu = null; if (reload) st.rev++
                    }
                }
            }
        }
    }
}

/** Right side of TV: the live picture, what is on now with its progress, and the next two shows. */
@Composable
private fun NowPanel(ch: Channel?, modifier: Modifier) {
    val ctx = LocalContext.current
    val c = ch
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).glass(16.dp)) {
            val cc = c
            if (cc != null && Look.previewMode == 2) PreviewBox(cc, Modifier.fillMaxSize())
            else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (cc != null) ChannelLogo(cc, 56.dp) else Ico(R.drawable.lc_tv, 30.dp, C.Muted)
            }
            if (cc != null) {
                Row(Modifier.align(Alignment.TopStart).padding(8.dp).clip(Rnd(7.dp)).background(Color(0xB3000000)).padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFFF4D55))); T("UŽIVO", 9.sp, Color.White, FontWeight.Bold)
                }
                qualityOf(cc.name)?.let { q ->
                    Box(Modifier.align(Alignment.TopEnd).padding(8.dp).clip(Rnd(7.dp)).background(Color(0xB3000000)).padding(horizontal = 7.dp, vertical = 3.dp)) { T(q, 9.sp, Color.White, FontWeight.Bold) }
                }
            }
        }
        val cc = c ?: run { T("OK na kanalu: slika ovdje · još jednom OK: cijeli ekran", 12.sp, C.Muted, maxLines = 2); return@Column }
        val epg by load(cc.url) { Store.byId(ctx, cc.pid)?.let { Repo.epg(it, cc, 4) } ?: emptyList() }
        val list = (epg as? Load.Ok)?.value ?: emptyList()
        val nowT = System.currentTimeMillis()
        val cur = list.firstOrNull { it.start <= nowT && it.end > nowT }
        Kicker("${cc.num} · ${cc.name}" + (cur?.let { " · " + it.times() } ?: ""), C.Soft)
        T(cur?.title ?: (if (epg is Load.Loading) "Učitavam program…" else cc.name), 19.sp, C.Text, FontWeight.ExtraBold, maxLines = 2)
        cleanDesc(cur?.desc)?.let { T(it, 11.sp, C.Soft, maxLines = 3) }
        if (cur != null) ThinBar(cur.progress(), C.Text, Modifier.fillMaxWidth(), 3.dp)
        val next = list.filter { it.start >= (cur?.end ?: nowT) - 60_000 }.take(2)
        if (next.isNotEmpty()) Column(Modifier.fillMaxWidth().glass(12.dp).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            next.forEach { e ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    T(java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(e.start)), 12.sp, C.Text, FontWeight.Bold)  // i18n-ignore
                    T(e.title, 12.sp, C.Soft, modifier = Modifier.weight(1f))
                }
            }
        } else if (epg is Load.Ok && cur == null) T(if (!EpgSources.any) "Nema programa. Dodaj EPG izvor: Postavke › Playliste › EPG izvori" else "Nema programa za ovaj kanal.", 11.sp, C.Muted, maxLines = 2)
    }
}

/* ============================================================== long OK on a film or series */

/** Card with the poster on the left and big choices on the right (long OK on a poster). */
@Composable
fun PosterSheet(kicker: String, title: String, image: String?, key: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        val armed = remember { booleanArrayOf(false) }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).onPreviewKeyEvent { e ->
            val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
            if (!ok || armed[0]) return@onPreviewKeyEvent false
            if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) { armed[0] = true; false } else true
        }, contentAlignment = Alignment.Center) {
            Row(Modifier.widthIn(max = 640.dp).fillMaxWidth(0.82f).clip(Rnd(24.dp)).background(C.Overlay).border(1.dp, C.Line, Rnd(24.dp)).padding(22.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                Box(Modifier.width(150.dp).aspectRatio(2f / 3f).clip(Rnd(14.dp))) { OnyxImage(image, key, Modifier.fillMaxSize()) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Kicker(kicker, C.Soft)
                    T(title, 22.sp, C.Text, FontWeight.ExtraBold, maxLines = 2)
                    Spacer(Modifier.height(4.dp))
                    content()
                }
            }
        }
    }
}

@Composable
fun SheetRow(label: String, sub: String?, icon: Int, onClick: () -> Unit) {
    Focusable(onClick = onClick, shape = Rnd(14.dp), normal = Color.Transparent, focused = C.Red, zoom = 1.01f, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { f ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Ico(icon, 18.dp, if (f) C.OnRed else C.Text)
            Column(Modifier.weight(1f)) {
                T(label, 14.sp, if (f) C.OnRed else C.Text, FontWeight.Bold)
                if (sub != null) T(sub, 11.sp, if (f) C.OnRed.copy(alpha = 0.65f) else C.Muted)
            }
        }
    }
}

/* ============================================================== search */

@Composable
fun OnyxSearch(nav: Nav) {
    val ctx = LocalContext.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    var q by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var pick by remember { mutableStateOf<Hit?>(null) }
    val fr = remember { FocusRequester() }
    val firstFr = remember { FocusRequester() }
    var jump by remember { mutableIntStateOf(0) }
    LaunchedEffect(q) { delay(600); query = q.trim() }
    val voice = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { q = it; query = it.trim(); SearchHistory.add(it); jump++ }
    }
    // what you searched for stays in "Recent searches" (also when you leave with a result open)
    val lastQuery = androidx.compose.runtime.rememberUpdatedState(query)
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { SearchHistory.add(lastQuery.value) } }
    val chIdx by load(Unit) { SearchIndex.channels(ctx) }
    val vodIdx by load(Unit) { SearchIndex.movies(ctx) }
    val serIdx by load(Unit) { SearchIndex.shows(ctx) }
    val wantProgs = query.length >= 3
    val progIdx by load(wantProgs) { if (wantProgs) SearchIndex.progs(ctx) else emptyList() }
    @Composable
    fun hitsOf(idx: Load<List<SearchIndex.Item>>, max: Int): State<List<Hit>> = produceState(emptyList<Hit>(), query, idx) {
        value = if (query.length < 2) emptyList() else (idx as? Load.Ok)?.value?.let { SearchIndex.find(it, query, max) } ?: emptyList()
    }
    val chHits by hitsOf(chIdx, 30)
    val vodHits by hitsOf(vodIdx, 30)
    val serHits by hitsOf(serIdx, 30)
    val progHits by hitsOf(progIdx, 30)
    val progs = remember(progHits) { progHits.sortedBy { (it as Hit.Prog).e.start } }
    val vs = remember(vodHits, serHits) { vodHits + serHits }
    val busy = chIdx is Load.Loading || vodIdx is Load.Loading || serIdx is Load.Loading || (wantProgs && progIdx is Load.Loading)
    LaunchedEffect(jump, chHits.isNotEmpty() || vs.isNotEmpty() || progs.isNotEmpty()) { if (jump > 0) firstFr.tryFocus() }

    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // the big search field, with the microphone
        Row(Modifier.fillMaxWidth().padding(top = 6.dp).height(52.dp).clip(Pill).background(if (C.IsLight) Color.White else Color(0xF2F5F5F7)),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.padding(start = 18.dp)) { Ico(R.drawable.lc_search, 18.dp, Color(0xFF0A0A0A)) }
            androidx.compose.foundation.text.BasicTextField(q, { q = it }, singleLine = true,
                textStyle = TextStyle(fontFamily = Inter, fontSize = 18.sp, color = Color(0xFF0A0A0A), fontWeight = FontWeight.SemiBold),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFF0A0A0A)),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search, autoCorrectEnabled = false),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { query = q.trim(); SearchHistory.add(q); keyboard?.hide(); jump++ }),
                modifier = Modifier.weight(1f).padding(horizontal = 14.dp).focusRequesterSafe(fr),
                decorationBox = { inner -> Box { if (q.isEmpty()) T("Kanal, film, serija, emisija ili broj", 16.sp, Color(0xFF6B7280)); inner() } })
            // clear the search
            if (q.isNotEmpty()) Focusable(onClick = { SearchHistory.add(q); q = ""; query = ""; try { fr.requestFocus() } catch (_: Exception) {} }, shape = Pill, normal = Color.Transparent, focused = Color(0x1F000000),
                zoom = 1f, modifier = Modifier.height(40.dp)) { _ ->
                Row(Modifier.align(Alignment.Center).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Ico(R.drawable.lc_x, 16.dp, Color(0xFF374151)); T("Obriši", 13.sp, Color(0xFF374151))
                }
            }
            Focusable(onClick = {
                try { voice.launch(android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, Look.lang)) }
                catch (e: Exception) { Toast.makeText(ctx, tr("Glasovna pretraga nije dostupna na ovom uređaju"), Toast.LENGTH_LONG).show() }
            }, shape = Pill, normal = Color.Transparent, focused = Color(0x1F000000), zoom = 1f, modifier = Modifier.padding(end = 8.dp).height(40.dp)) { _ ->
                Row(Modifier.align(Alignment.Center).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Ico(R.drawable.lc_mic, 16.dp, Color(0xFF374151)); T("glasom", 13.sp, Color(0xFF374151))
                }
            }
        }
        if (query.length < 2) {
            T("Upiši bar 2 slova ili klikni „glasom”", 13.sp, C.Muted)
            SearchRecent { h -> q = h; query = h; jump++ }
            return@Column
        }
        if (!busy && chHits.isEmpty() && vs.isEmpty() && progs.isEmpty()) { T("Ništa nije pronađeno", 15.sp, C.Soft); return@Column }
        var first = true
        fun takeFirst(): FocusRequester? = if (first) { first = false; firstFr } else null
        if (chHits.isNotEmpty()) {
            SearchHead("Kanali", chHits.size)
            val f0 = takeFirst()
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(chHits) { i, h ->
                    val ch = (h as Hit.Ch).ch
                    Focusable(onClick = { playChannels(nav, listOf(ch), 0) }, onMenu = { pick = h }, focusRequester = if (i == 0) f0 else null,
                        shape = Rnd(14.dp), normal = Color.Transparent, focused = C.Red, zoom = 1.03f, modifier = Modifier.width(210.dp).height(56.dp).glass(14.dp)) { f ->
                        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ChannelLogo(ch, 34.dp, onLight = f)
                            Column(Modifier.weight(1f)) {
                                T(ch.name, 13.sp, if (f) C.OnRed else C.Text, FontWeight.Bold)
                                T(nowOf(ch, 600)?.title ?: (tr("Kanal") + " ${ch.num}"), 11.sp, if (f) C.OnRed.copy(alpha = 0.65f) else C.Muted)
                            }
                        }
                    }
                }
            }
        }
        if (progs.isNotEmpty()) {
            SearchHead("Emisije u programu", progs.size)
            val f0 = takeFirst()
            progs.take(8).forEachIndexed { i, h ->
                val p = h as Hit.Prog
                val live = p.e.start <= System.currentTimeMillis() && p.e.end > System.currentTimeMillis()
                Focusable(onClick = { pick = h }, focusRequester = if (i == 0) f0 else null, shape = Rnd(14.dp), normal = Color.Transparent, focused = C.Red, zoom = 1.01f,
                    modifier = Modifier.fillMaxWidth(0.72f).height(42.dp).glass(14.dp)) { f ->
                    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        T(if (live) tr("UŽIVO") else java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(p.e.start)), 12.sp,  // i18n-ignore
                            if (f) C.OnRed else C.Text, FontWeight.ExtraBold, modifier = Modifier.width(52.dp))
                        T(p.e.title, 13.sp, if (f) C.OnRed else C.Text, modifier = Modifier.weight(1f))
                        T(p.ch.name, 11.sp, if (f) C.OnRed.copy(alpha = 0.65f) else C.Muted)
                    }
                }
            }
        }
        if (vs.isNotEmpty()) {
            SearchHead("Filmovi i serije", vs.size)
            val f0 = takeFirst()
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                itemsIndexed(vs) { i, h ->
                    val (name, img, id) = when (h) { is Hit.Movie -> Triple(h.v.name, h.v.poster, h.v.id); is Hit.Show -> Triple(h.s.name, h.s.cover, h.s.id); else -> Triple("", null, "") }
                    Box(Modifier.width(96.dp)) {
                        PosterCard(cleanTitle(name).first, img, id, null, Modifier.fillMaxWidth(), fr = if (i == 0) f0 else null) {
                            when (h) { is Hit.Movie -> nav.push(Screen.MovieDetail(h.v)); is Hit.Show -> nav.push(Screen.SeriesDetail(h.s)); else -> {} }
                        }
                    }
                }
            }
        }
        if (busy) T(tr("učitavam…"), 11.sp, C.Muted)
    }
    pick?.let { h -> HitActions(h, nav) { pick = null } }
    LaunchedEffect(Unit) { fr.tryFocus() }
}

@Composable
private fun SearchHead(t: String, n: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) { Kicker(t, C.Soft, modifier = Modifier.weight(1f)); Kicker("$n", C.Muted) }
}

/** focusRequester that does not crash when the item is not on screen yet. */
fun Modifier.focusRequesterSafe(fr: FocusRequester): Modifier = this.focusRequester(fr)

/* ============================================================== film and series pages */

@Composable
fun PillBtn(text: String, icon: Int? = null, primary: Boolean = false, fr: FocusRequester? = null, onClick: () -> Unit) {
    Focusable(onClick = onClick, focusRequester = fr, shape = Pill, normal = if (primary) C.Text else Color.Transparent, focused = if (primary) C.Text else C.Red,
        zoom = 1.05f, modifier = Modifier.height(36.dp).then(if (primary) Modifier else Modifier.border(1.dp, C.Line, Pill))) { f ->
        Row(Modifier.align(Alignment.Center).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            val fg = if (primary) C.Bg else if (f) C.OnRed else C.Text
            if (icon != null) Ico(icon, 14.dp, fg)
            T(text, 13.sp, fg, FontWeight.Bold)
        }
    }
}

@Composable
private fun MetaChip(t: String, star: Boolean = false) {
    Box(Modifier.clip(Pill).background(if (star) Color(0xFFFBBF24) else C.CardHi).padding(horizontal = 10.dp, vertical = 3.dp)) {
        T(t, 11.sp, if (star) Color(0xFF111111) else C.Text, FontWeight.SemiBold)
    }
}

/* ============================================================== first start */

object Welcome { var tab by mutableIntStateOf(-1) }

/** First start: pick how to add the playlist (Xtream, M3U link, file from USB), the language on top, restore from a backup below. */
@Composable
fun WelcomeScreen(nav: Nav) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var langMenu by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf<String?>(null) }
    var rev by remember { mutableIntStateOf(0) }
    val first = remember { FocusRequester() }
    key(rev, Look.lang) {
        Box(Modifier.fillMaxSize()) {
            val cur = Langs.APP.firstOrNull { it.code == Look.lang } ?: Langs.APP[0]
            Focusable(onClick = { langMenu = true }, shape = Pill, normal = Color.Transparent, focused = C.Red, zoom = 1.04f,
                modifier = Modifier.align(Alignment.TopEnd).padding(20.dp).height(36.dp).border(1.dp, C.Line, Pill)) { f ->
                Row(Modifier.align(Alignment.Center).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Ico(R.drawable.lc_globe, 15.dp, if (f) C.OnRed else C.Text); T(cur.native + " ▾", 13.sp, if (f) C.OnRed else C.Text, FontWeight.SemiBold)
                }
            }
            Column(Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 60.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.logo_mark), null, Modifier.size(64.dp))
                T(trf("Dobrodošli u %s", "PRETORIAN"), 30.sp, C.Text, FontWeight.ExtraBold)
                T("Dodaj svoju playlistu i za minut gledaš.", 15.sp, C.Soft)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    WelcomeCard("Xtream Codes", "Server, korisničko ime, lozinka", R.drawable.lc_server, first) { Welcome.tab = 0 }
                    WelcomeCard("M3U playlista", "Link ili fajl s diska / USB-a", R.drawable.lc_link, null) { pick = "m3uask" }
                    WelcomeCard("Stalker portal", "Portal i MAC adresa", R.drawable.lc_tv, null) { Welcome.tab = 2 }
                }
                Spacer(Modifier.height(8.dp))
                // a separate box: bring everything back from a backup
                Focusable(onClick = { pick = "json" }, shape = Rnd(18.dp), normal = Color.Transparent, focused = C.Red, zoom = 1.02f,
                    modifier = Modifier.width(722.dp).height(68.dp).glass(18.dp)) { f ->
                    Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.size(38.dp).clip(Rnd(11.dp)).background(if (f) Color(0x1A000000) else C.CardHi), contentAlignment = Alignment.Center) { Ico(R.drawable.lc_archive, 18.dp, if (f) C.OnRed else C.Text) }
                        Column(Modifier.weight(1f)) {
                            T("VRATI POSTAVKE", 15.sp, if (f) C.OnRed else C.Text, FontWeight.Bold)
                            T("Imaš backup? Playliste, favoriti, profili i postavke s USB-a ili diska", 11.sp, if (f) C.OnRed.copy(alpha = 0.7f) else C.Muted)
                        }
                        T("›", 20.sp, if (f) C.OnRed else C.Muted)  // i18n-ignore
                    }
                }
            }
        }
    }
    if (langMenu) Popup("Jezik", { langMenu = false }) {
        Langs.APP.forEach { l -> OptionRow(l.native, if (l.code == Look.lang) R.drawable.lc_check else null) { setAppLang(ctx, l.code); langMenu = false; rev++ } }
    }
    if (pick == "m3uask") Popup("M3U playlista", { pick = null }) {
        OptionRow("Link", R.drawable.lc_link, sub = "zalijepi ili ukucaj adresu") { pick = null; Welcome.tab = 1 }
        OptionRow("Fajl s diska / USB-a", R.drawable.lc_usb, sub = ".m3u ili .m3u8") { pick = "m3u" }
    }
    when (pick) {
        "m3u" -> FilePicker("Izaberi playlistu", setOf("m3u", "m3u8"), onDismiss = { pick = null }) { bytes, name ->
            pick = null
            scope.launch {
                try {
                    val id = System.currentTimeMillis().toString()
                    val f = java.io.File(ctx.filesDir, "playlist_$id.m3u"); f.writeBytes(bytes)
                    val pl = Playlist(id = id, name = name.substringBeforeLast('.').ifBlank { "M3U" }, type = SourceType.M3U, url = "file://" + f.path, active = true)
                    Repo.test(pl)
                    Store.savePlaylists(ctx, Store.playlists(ctx) + pl); nav.playlistsChanged(); nav.root(Screen.Home)
                } catch (e: Exception) { Toast.makeText(ctx, trf("Nije uspjelo: %s", e.message ?: ""), Toast.LENGTH_LONG).show() }
            }
        }
        "json" -> FilePicker("Izaberi backup", setOf("json"), onDismiss = { pick = null }) { bytes, _ ->
            pick = null
            try { Backup.restore(ctx, String(bytes), null); Toast.makeText(ctx, tr("Sve je vraćeno. PRETORIAN se ponovo pokreće."), Toast.LENGTH_LONG).show()
                (ctx as? android.app.Activity)?.recreate() }
            catch (e: Exception) { Toast.makeText(ctx, e.message ?: tr("Vraćanje nije uspjelo"), Toast.LENGTH_LONG).show() }
        }
    }
    LaunchedEffect(Unit) { first.tryFocus() }
}

@Composable
private fun WelcomeCard(title: String, sub: String, icon: Int, fr: FocusRequester?, onClick: () -> Unit) {
    Focusable(onClick = onClick, focusRequester = fr, shape = Rnd(20.dp), normal = Color.Transparent, focused = C.Red, zoom = 1.04f,
        modifier = Modifier.width(230.dp).height(132.dp).glass(20.dp)) { f ->
        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(38.dp).clip(Rnd(11.dp)).background(if (f) Color(0x1A000000) else C.CardHi), contentAlignment = Alignment.Center) { Ico(icon, 18.dp, if (f) C.OnRed else C.Text) }
            Spacer(Modifier.weight(1f))
            T(title, 16.sp, if (f) C.OnRed else C.Text, FontWeight.Bold)
            T(sub, 11.sp, if (f) C.OnRed.copy(alpha = 0.7f) else C.Muted)
        }
    }
}

/* ============================================================== groups and channels, several at once */

@Composable
private fun ToolBtn(text: String, icon: Int, enabled: Boolean = true, onClick: () -> Unit) {
    Focusable(onClick = { if (enabled) onClick() }, shape = Pill, normal = C.CardHi, focused = C.Red, zoom = 1.05f, modifier = Modifier.height(32.dp)) { f ->
        Row(Modifier.align(Alignment.Center).padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            val c = if (!enabled) C.Muted else if (f) C.OnRed else C.Text
            Ico(icon, 13.dp, c); T(text, 12.sp, c, FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CheckBox(on: Boolean, focused: Boolean) {
    Box(Modifier.size(18.dp).clip(Rnd(5.dp)).background(if (on) Color(0xFF2563EB) else Color.Transparent)
        .border(1.5.dp, if (on) Color(0xFF60A5FA) else if (focused) C.OnRed.copy(alpha = 0.6f) else C.Muted, Rnd(5.dp)), contentAlignment = Alignment.Center) {
        if (on) Ico(R.drawable.lc_check, 12.dp, Color.White)
    }
}

/** Settings › Playlists › Groups and channels: tick several, then move, hide, show or lock them together. */
@Composable
fun BulkEditor(nav: Nav) {
    val ctx = LocalContext.current
    val p = remember { Store.active(ctx) } ?: run { NoPlaylist(nav); return }
    val ovr = remember(p.id) { Ovr(ctx, p.id) }
    var tab by remember { mutableIntStateOf(0) }
    var rev by remember { mutableIntStateOf(0) }
    val changed = remember { booleanArrayOf(false) }
    DisposableEffect(Unit) { onDispose { if (changed[0]) nav.playlistsChanged() } }
    val kind = listOf("live", "vod", "series")[tab]
    val st by load(p.id, kind, rev, keep = true) {
        ovr.groups(kind, when (kind) { "live" -> Repo.liveGroups(p); "vod" -> Repo.vodGroups(p); else -> Repo.seriesGroups(p) }, all = true)
    }
    val groups = (st as? Load.Ok)?.value ?: emptyList()
    var cur by remember { mutableStateOf<Category?>(null) }
    var focusG by remember { mutableStateOf<Category?>(null) }
    LaunchedEffect(focusG) { val g = focusG ?: return@LaunchedEffect; delay(250); if (kind == "live") cur = g }
    val chSt by load(p.id, cur?.id, rev, keep = true) {
        val g = cur ?: return@load emptyList<Channel>()
        if (g.id.startsWith("my_")) ovr.channels(g.id, emptyList(), all = true) else ovr.channels(g.id, Repo.channels(p, g.id), all = true)
    }
    val chans = if (kind == "live") (chSt as? Load.Ok)?.value ?: emptyList() else emptyList()
    val selG = remember { mutableStateListOf<String>() }
    val selC = remember { mutableStateListOf<String>() }
    var pinSetup by remember { mutableStateOf(false) }
    var moveTo by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf<Channel?>(null) }
    var menu by remember { mutableStateOf<Category?>(null) }
    LaunchedEffect(tab) { selG.clear(); selC.clear(); cur = null }
    fun done() { rev++; changed[0] = true }
    fun reorder(ids: List<String>, sel: List<String>, how: String): List<String> {
        val l = ids.toMutableList()
        when (how) {
            "up" -> for (i in 1 until l.size) if (l[i] in sel && l[i - 1] !in sel) { val t = l[i]; l[i] = l[i - 1]; l[i - 1] = t }
            "down" -> for (i in l.size - 2 downTo 0) if (l[i] in sel && l[i + 1] !in sel) { val t = l[i]; l[i] = l[i + 1]; l[i + 1] = t }
            "top" -> return l.filter { it in sel } + l.filter { it !in sel }
            "bottom" -> return l.filter { it !in sel } + l.filter { it in sel }
        }
        return l
    }
    fun act(how: String) {
        if (selC.isNotEmpty() && cur != null) {
            val chosen = chans.filter { it.url in selC }
            when (how) {
                "hide" -> chosen.forEach { if (!ovr.isChHidden(it)) ovr.toggleChHidden(it) }
                "show" -> chosen.forEach { if (ovr.isChHidden(it)) ovr.toggleChHidden(it) }
                "pin" -> { if (!Lock.hasPin()) { pinSetup = true; return }; val lock = chosen.any { !ovr.isChLocked(it) }; chosen.forEach { if (ovr.isChLocked(it) != lock) ovr.toggleChLocked(it) } }
                else -> ovr.setChannelOrder(cur!!.id, reorder(chans.map { it.url }, selC.toList(), how))
            }
        } else if (selG.isNotEmpty()) {
            when (how) {
                "hide" -> ovr.setHidden(kind, selG.toList(), true)
                "show" -> ovr.setHidden(kind, selG.toList(), false)
                "pin" -> { if (!Lock.hasPin()) { pinSetup = true; return }; val lock = selG.any { !ovr.isLocked(kind, it) }; selG.forEach { if (ovr.isLocked(kind, it) != lock) ovr.toggleLocked(kind, it) } }
                else -> ovr.setGroupOrder(kind, reorder(groups.map { it.id }, selG.toList(), how))
            }
        }
        done()
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T(tr("Playliste") + " › " + p.name, 10.sp, C.Muted)
                T("Uređivanje grupa i kanala", 19.sp, C.Text, FontWeight.Bold)
            }
            Segmented(listOf("TV", "Filmovi", "Serije"), tab) { tab = it }
        }
        // what you can do with the ticked rows
        val n = if (selC.isNotEmpty()) selC.size else selG.size
        Row(Modifier.fillMaxWidth().clip(Rnd(14.dp)).background(Color(0x332563EB)).border(1.dp, Color(0x552563EB), Rnd(14.dp)).padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            T(if (n == 0) tr("OK označava · onda biraš radnju") else if (selC.isNotEmpty()) trf("%s kanala označeno", n) else trf("%s grupe označene", n), 12.sp, C.Text, FontWeight.Bold,
                modifier = Modifier.widthIn(max = 150.dp))
            androidx.compose.foundation.lazy.LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { ToolBtn("Gore", R.drawable.lc_arrow_up, n > 0) { act("up") } }
                item { ToolBtn("Dole", R.drawable.lc_arrow_down, n > 0) { act("down") } }
                item { ToolBtn("Na vrh", R.drawable.lc_chevrons_up, n > 0) { act("top") } }
                item { ToolBtn("Na dno", R.drawable.lc_chevrons_down, n > 0) { act("bottom") } }
                item { ToolBtn("Sakrij", R.drawable.lc_eye_off, n > 0) { act("hide") } }
                item { ToolBtn("Prikaži", R.drawable.lc_eye, n > 0) { act("show") } }
                item { ToolBtn("PIN", R.drawable.lc_lock, n > 0) { act("pin") } }
                if (selC.isNotEmpty()) {
                    item { ToolBtn("Premjesti u grupu", R.drawable.lc_layers) { moveTo = true } }
                    if (selC.size == 1) item { ToolBtn("Preimenuj", R.drawable.lc_pencil) { rename = chans.firstOrNull { it.url == selC.first() } } }
                }
                item { ToolBtn("Poništi", R.drawable.lc_x, n > 0) { selG.clear(); selC.clear() } }
            }
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // groups
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    T(trf("Grupe · %s", groups.size), 13.sp, C.Text, FontWeight.Bold, modifier = Modifier.weight(1f))
                    ToolBtn("Sakrij sve", R.drawable.lc_eye_off) { ovr.setHidden(kind, groups.map { it.id }, true); done() }
                    ToolBtn("Prikaži sve", R.drawable.lc_eye) { ovr.setHidden(kind, groups.map { it.id }, false); done() }
                }
                if (st is Load.Loading && groups.isEmpty()) Loading("") else LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                    itemsIndexed(groups, key = { _, g -> g.id }) { i, g ->
                        val on = g.id in selG
                        val hidden = ovr.isHidden(kind, g.id)
                        Focusable(onClick = { if (on) selG.remove(g.id) else { selC.clear(); selG.add(g.id) } }, onFocus = { focusG = g }, onMenu = { menu = g },
                            shape = Rnd(12.dp), normal = if (on) Color(0x332563EB) else if (cur?.id == g.id) C.CardHi else C.Rail, focused = C.Red, zoom = 1.01f,
                            modifier = Modifier.fillMaxWidth().height(40.dp)) { f ->
                            Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CheckBox(on, f)
                                T("${i + 1}", 11.sp, if (f) C.OnRed else C.Muted, modifier = Modifier.width(22.dp))
                                T(g.name, 13.sp, if (f) C.OnRed else if (hidden) C.Muted else C.Text, FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                if (hidden) Ico(R.drawable.lc_eye_off, 13.dp, if (f) C.OnRed else C.Muted)
                                if (ovr.isLocked(kind, g.id)) Ico(R.drawable.lc_lock, 13.dp, if (f) C.OnRed else C.Amber)
                            }
                        }
                    }
                }
            }
            // channels of the group you stand on (TV only)
            if (kind == "live") Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    T((cur?.name ?: tr("Stani na grupu")) + " · " + tr("kanali"), 13.sp, C.Text, FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (cur != null) {
                        ToolBtn("Sakrij sve", R.drawable.lc_eye_off) { chans.forEach { if (!ovr.isChHidden(it)) ovr.toggleChHidden(it) }; done() }
                        ToolBtn("Prikaži sve", R.drawable.lc_eye) { chans.forEach { if (ovr.isChHidden(it)) ovr.toggleChHidden(it) }; done() }
                    }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                    itemsIndexed(chans, key = { i, c -> "$i${c.url}" }) { i, c ->
                        val on = c.url in selC
                        val hidden = ovr.isChHidden(c)
                        Focusable(onClick = { if (on) selC.remove(c.url) else { selG.clear(); selC.add(c.url) } }, shape = Rnd(12.dp),
                            normal = if (on) Color(0x332563EB) else C.Rail, focused = C.Red, zoom = 1.01f, modifier = Modifier.fillMaxWidth().height(40.dp)) { f ->
                            Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CheckBox(on, f)
                                T("${i + 1}", 11.sp, if (f) C.OnRed else C.Muted, modifier = Modifier.width(22.dp))
                                T(ovr.chName(c), 13.sp, if (f) C.OnRed else if (hidden) C.Muted else C.Text, FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                if (hidden) Ico(R.drawable.lc_eye_off, 13.dp, if (f) C.OnRed else C.Muted)
                                if (ovr.isChLocked(c)) Ico(R.drawable.lc_lock, 13.dp, if (f) C.OnRed else C.Amber)
                            }
                        }
                    }
                }
            }
        }
    }
    if (pinSetup) PinSetupDialog(onDismiss = { pinSetup = false }) { pinSetup = false }
    if (moveTo) Popup("Premjesti u grupu", { moveTo = false }) {
        groups.filter { it.id != cur?.id }.forEach { tg ->
            OptionRow(tg.name, R.drawable.lc_layers) {
                chans.filter { it.url in selC }.forEach { ovr.moveChannelTo(it, tg.id) }
                Toast.makeText(ctx, trf("Premješteno u %s", tg.name), Toast.LENGTH_SHORT).show()
                selC.clear(); moveTo = false; done()
            }
        }
    }
    rename?.let { c -> TextDialog("Preimenuj kanal", "Novi naziv", ovr.chName(c), onDismiss = { rename = null }) { v -> if (v.isNotBlank()) ovr.renameChannel(c, v); rename = null; selC.clear(); done() } }
    menu?.let { g -> GroupOptions(kind, g, ovr, groups, onDismiss = { menu = null }) { close -> done(); if (close) menu = null } }
}
