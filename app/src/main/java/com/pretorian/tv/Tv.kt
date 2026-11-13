package com.pretorian.tv

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A group in the TV list: a favourites list (pid null) or a group of one playlist. */
data class GroupRef(val pid: String?, val cat: Category) {
    val key get() = (pid ?: "fav") + "|" + cat.id
}

/** Last TV position, so Back from the player returns to the same group and channel. */
object TvMemory {
    var group: GroupRef? = null
    var channel = -1
    /** true while coming back from the player: focus goes back to the channel that was playing */
    var restore = false
}

/** The small live preview: stopped before the real player starts, so the account never has two connections. */
object Preview {
    var player: ExoPlayer? = null
    fun stop() { try { player?.stop(); player?.clearMediaItems() } catch (_: Exception) {} }
}

/** Row heights from Settings › Appearance › Row size. */
object Rows {
    val channel get() = when (Look.rowSize) { 0 -> 46.dp; 2 -> 60.dp; else -> 52.dp }
    val group get() = when (Look.rowSize) { 0 -> 36.dp; 2 -> 44.dp; else -> 40.dp }
    val font get() = when (Look.rowSize) { 0 -> 14.sp; 2 -> 16.sp; else -> 15.sp }
}

/** What the app rail shows: the guide hides it and opens it step by step. */
object Chrome {
    /** 0 hidden, 1 icons only, 2 icons with names, 3 normal */
    var rail by mutableIntStateOf(3)
    /** true while a screen drives the rail (TV guide) */
    var driven by mutableStateOf(false)
}

/** Runs [action] right away, or after the PIN when [locked] and not yet unlocked this session. */
class PinGate {
    var pending by mutableStateOf<Pair<String, () -> Unit>?>(null)
    fun run(key: String, locked: Boolean, action: () -> Unit) {
        if (!locked || key in Lock.unlocked || !Lock.hasPin()) action() else pending = key to action
    }
}

@Composable
fun PinGateDialog(gate: PinGate) {
    val p = gate.pending ?: return
    PinDialog("Zaključano", "Unesi roditeljski PIN", onDismiss = { gate.pending = null }) { pin ->
        val err = Lock.check(pin)
        if (err == null) { Lock.unlocked.add(p.first); gate.pending = null; p.second() }
        err
    }
}

/** Two steps: new PIN, then the same PIN again. */
@Composable
fun PinSetupDialog(onDismiss: () -> Unit, onDone: () -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    key(first) {
        if (first == null) {
            PinDialog("Novi PIN", "Izaberi 4 cifre. Trebaće za napredni mod i zaključane kanale.", onDismiss) { pin -> first = pin; null }
        } else {
            PinDialog("Ponovi PIN", "Unesi isti PIN još jednom", onDismiss) { pin ->
                if (pin == first) { Lock.setPin(pin); onDone(); null } else { first = null; "PIN-ovi se ne poklapaju" }
            }
        }
    }
}

const val ALL_GROUP = "__all__"

/** Channels of a group: favourites, all channels, or a provider group with your changes. */
suspend fun channelsOf(ctx: android.content.Context, ref: GroupRef): List<Channel> {
    if (ref.pid == null) return Favs.channels(favListId(ref.cat.id))
    val p = Store.byId(ctx, ref.pid) ?: return emptyList()
    val ovr = Ovr(ctx, p.id)
    return when {
        ref.cat.id == ALL_GROUP -> ovr.channels(ALL_GROUP, Repo.allChannels(p))
        ref.cat.id.startsWith("my_") -> ovr.channels(ref.cat.id, emptyList())
        else -> ovr.channels(ref.cat.id, Repo.channels(p, ref.cat.id))
    }
}

/** Rows of the group column: favourites lists, then every turned-on playlist (only the opened one unfolded). */
sealed class GRow {
    data class Group(val ref: GroupRef) : GRow()
    data class Header(val p: Playlist, val open: Boolean) : GRow()
    data class NewFav(val dummy: Int = 0) : GRow()
    data class NewGroup(val pid: String) : GRow()
    val id get() = when (this) { is Group -> "g:" + ref.key; is Header -> "h:" + p.id; is NewFav -> "nf"; is NewGroup -> "ng:$pid" }
}

suspend fun groupRows(ctx: android.content.Context): List<GRow> {
    val out = ArrayList<GRow>()
    Favs.lists(ctx).forEach { out.add(GRow.Group(GroupRef(null, Category(FAV_PREFIX + it.id, "★ " + it.name)))) }
    if (!Lock.simple) out.add(GRow.NewFav())
    val pls = Store.actives(ctx)
    pls.forEachIndexed { i, p ->
        val open = i == 0
        if (pls.size > 1) out.add(GRow.Header(p, open))
        if (open) {
            val ovr = Ovr(ctx, p.id)
            out.add(GRow.Group(GroupRef(p.id, Category(ALL_GROUP, tr("Svi kanali")))))
            ovr.groups("live", Repo.liveGroups(p)).forEach { out.add(GRow.Group(GroupRef(p.id, it))) }
            if (!Lock.simple) out.add(GRow.NewGroup(p.id))
        }
    }
    return out
}

@Composable
fun TvScreen(nav: Nav) {
    val ctx = LocalContext.current
    if (Store.playlists(ctx).isEmpty()) { NoPlaylist(nav); return }
    var guide by remember { mutableStateOf(Prefs.str("tv_view", "list") == "guide") }
    if (guide) TvGuide(nav) { guide = false; Prefs.put("tv_view", "list") }
    else TvList(nav) { guide = true; Prefs.put("tv_view", "guide") }
}

/** The group column, shared by the list and the guide. */
@Composable
fun GroupColumn(
    rows: Load<List<GRow>>, current: GroupRef?, focusKey: String, focusFr: FocusRequester, wrap: Wrap,
    state: androidx.compose.foundation.lazy.LazyListState, width: androidx.compose.ui.unit.Dp,
    title: String, sub: String?, onFocusGroup: (GroupRef) -> Unit, onOpen: (GroupRef) -> Unit, onMenu: (GroupRef) -> Unit,
    onHeader: (Playlist) -> Unit, onNewFav: () -> Unit, onNewGroup: (String) -> Unit, onFocusRow: (String) -> Unit, extra: (@Composable () -> Unit)? = null,
) {
    val ctx = LocalContext.current
    Column(Modifier.width(width).fillMaxHeight().padding(start = 14.dp, end = 10.dp, top = 22.dp)) {
        T(title, 26.sp, C.Text, FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, bottom = 2.dp))
        if (sub != null) T(sub, 12.sp, C.Muted, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
        extra?.invoke()
        when (rows) {
            is Load.Loading -> Loading("Učitavam grupe…")
            is Load.Err -> Message("Greška", rows.message)
            is Load.Ok -> {
                val list = rows.value
                LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 24.dp, top = 2.dp)) {
                    itemsIndexed(list, key = { _, r -> r.id }) { i, r ->
                        val m = Modifier.fillMaxWidth().height(Rows.group).wrapKeys(i, list.size, wrap)
                        val fr = wrap.requester(i, if (r.id == focusKey) focusFr else null)
                        when (r) {
                            is GRow.Group -> {
                                val g = r.ref
                                val selected = current?.key == g.key
                                val locked = g.pid?.let { Ovr(ctx, it).isLocked("live", g.cat.id) } ?: false
                                val shape = Rnd(12.dp)
                                Focusable(
                                    modifier = m.then(if (Look.groupFrame) Modifier.border(if (selected) 1.5.dp else 1.dp,
                                        C.Red.copy(alpha = if (selected) 0.9f else 0.28f), shape) else Modifier),
                                    shape = shape,
                                    onClick = { onOpen(g) },
                                    onFocus = { onFocusRow(r.id); onFocusGroup(g) },
                                    onMenu = if (Lock.simple || g.cat.id == ALL_GROUP) null else ({ onMenu(g) }),
                                    focusRequester = fr,
                                    normal = if (selected) C.Red.copy(alpha = 0.28f) else Color.Transparent,
                                    zoom = 1.0f,
                                ) { _ ->
                                    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (selected) Dot(C.Red, 7.dp)
                                        T(g.cat.name, Rows.font, if (selected) C.Text else C.Soft, if (selected) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.weight(1f))
                                        if (locked) Ico(R.drawable.lc_lock, 15.dp, C.Muted)
                                    }
                                }
                            }
                            is GRow.Header -> Focusable(modifier = m.height(Rows.group + 6.dp), onClick = { onHeader(r.p) },
                                onFocus = { onFocusRow(r.id) }, focusRequester = fr, normal = C.Card, zoom = 1f) { _ ->
                                Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Ico(if (r.open) R.drawable.lc_chevron_down else R.drawable.lc_chevron_right, 16.dp, C.RedText)
                                    T(r.p.name, 15.sp, C.Text, FontWeight.Bold, modifier = Modifier.weight(1f))
                                    expiryText(r.p.exp)?.let { T(it.first, 11.sp, if (it.second > 0) C.Amber else C.Muted) }
                                }
                            }
                            is GRow.NewFav -> Focusable(modifier = m, onClick = onNewFav, onFocus = { onFocusRow(r.id) }, focusRequester = fr,
                                normal = Color.Transparent, zoom = 1f) { _ ->
                                Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Ico(R.drawable.lc_plus, 15.dp, C.Muted); T("Nova lista favorita", 13.sp, C.Muted)
                                }
                            }
                            is GRow.NewGroup -> Focusable(modifier = m, onClick = { onNewGroup(r.pid) }, onFocus = { onFocusRow(r.id) }, focusRequester = fr,
                                normal = Color.Transparent, zoom = 1f) { _ ->
                                Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Ico(R.drawable.lc_plus, 15.dp, C.Muted); T("Nova grupa", 13.sp, C.Muted)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** State and dialogs the list and the guide share. */
class TvState {
    var rev by mutableIntStateOf(0)
    var group by mutableStateOf(TvMemory.group)
    var focusKey by mutableStateOf("")
    var refocus by mutableIntStateOf(0)
    var groupMenu by mutableStateOf<GroupRef?>(null)
    var newGroupFor by mutableStateOf<String?>(null)
    var newFav by mutableStateOf(false)
    val gate = PinGate()
    val focusFr = FocusRequester()
    val wrap = Wrap()
}

@Composable
fun TvDialogs(st: TvState, rows: Load<List<GRow>>) {
    val ctx = LocalContext.current
    st.groupMenu?.let { g ->
        if (g.pid == null) FavListOptions(g, onDismiss = { st.groupMenu = null; st.refocus++ }) { close -> st.rev++; if (close) { st.groupMenu = null; st.refocus++ } }
        else {
            val ovr = remember(g.pid) { Ovr(ctx, g.pid) }
            val current = (rows as? Load.Ok)?.value?.mapNotNull { (it as? GRow.Group)?.ref }?.filter { it.pid == g.pid && it.cat.id != ALL_GROUP }?.map { it.cat } ?: emptyList()
            GroupOptions("live", g.cat, ovr, current, onDismiss = { st.groupMenu = null; st.refocus++ }) { close ->
                st.rev++
                if (close) { st.groupMenu = null; st.refocus++ }
            }
        }
    }
    st.newGroupFor?.let { pid ->
        TextDialog("Nova grupa", "Naziv (npr. Dokumentarni, Sport, Bosna)", onDismiss = { st.newGroupFor = null; st.refocus++ }) { name ->
            if (name.isNotBlank()) Ovr(ctx, pid).addGroup(name)
            st.newGroupFor = null; st.rev++; st.refocus++
        }
    }
    if (st.newFav) TextDialog("Nova lista favorita", "Naziv (npr. Moji, Supruga, Djeca, Sport)", onDismiss = { st.newFav = false; st.refocus++ }) { name ->
        if (name.isNotBlank()) Favs.addList(ctx, name)
        st.newFav = false; st.rev++; st.refocus++
    }
    PinGateDialog(st.gate)
}

/** Opens a group (after the PIN when locked). */
fun openGroup(ctx: android.content.Context, st: TvState, g: GroupRef) {
    val locked = g.pid?.let { Ovr(ctx, it).isLocked("live", g.cat.id) } ?: false
    st.gate.run("g:${g.key}", locked) { if (st.group?.key != g.key) TvMemory.channel = -1; st.group = g; TvMemory.group = g }
}

@Composable
private fun TvList(nav: Nav, onGuide: () -> Unit) {
    val ctx = LocalContext.current
    val st = remember { TvState() }
    val rows by load(st.rev, Favs.rev, keep = true) { groupRows(ctx) }
    val gState = rememberLazyListState()
    WrapEffect(st.wrap, gState)
    val coming = remember { TvMemory.restore && TvMemory.group != null && TvMemory.channel >= 0 }
    var inChannels by remember { mutableStateOf(coming) }
    var focusedCh by remember { mutableStateOf<Channel?>(null) }
    var focusedGroup by remember { mutableStateOf<GroupRef?>(null) }
    LaunchedEffect(Unit) { if (!coming) { TvMemory.restore = false; st.refocus++ } }

    fun backToGroups() { st.focusKey = "g:" + (st.group?.key ?: ""); inChannels = false; st.refocus++ }
    BackHandler(enabled = inChannels && st.groupMenu == null) { backToGroups() }

    LaunchedEffect(focusedGroup) {
        val g = focusedGroup ?: return@LaunchedEffect
        delay(350)
        val locked = g.pid?.let { Ovr(ctx, it).isLocked("live", g.cat.id) } ?: false
        if (!locked || "g:${g.key}" in Lock.unlocked) { if (st.group?.key != g.key) TvMemory.channel = -1; st.group = g; TvMemory.group = g }
    }

    Row(Modifier.fillMaxSize()) {
        if (!inChannels) {
            GroupColumn(rows, st.group, st.focusKey, st.focusFr, st.wrap, gState, 290.dp, "TV", null,
                onFocusGroup = { focusedGroup = it },
                onOpen = { openGroup(ctx, st, it) },
                onMenu = { st.focusKey = "g:" + it.key; st.groupMenu = it },
                onHeader = { p -> Store.open(ctx, p.id); st.focusKey = "h:" + p.id; st.rev++; st.refocus++ },
                onNewFav = { st.newFav = true }, onNewGroup = { st.newGroupFor = it },
                onFocusRow = { st.focusKey = it },
                extra = {
                    Focusable(modifier = Modifier.padding(start = 4.dp, bottom = 8.dp).height(36.dp), onClick = onGuide, normal = C.Card, shape = Pill, zoom = 1.03f) { _ ->
                        Row(Modifier.align(Alignment.Center).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Ico(R.drawable.lc_calendar_clock, 16.dp, C.RedText); T("TV vodič", 13.sp, C.Text, FontWeight.SemiBold)
                        }
                    }
                })
            Box(Modifier.width(1.dp).fillMaxHeight().background(C.Line))
            // focus the group we came from (start, Left/Back from channels, closed menu)
            LaunchedEffect(st.refocus, rows is Load.Ok) {
                val list = (rows as? Load.Ok)?.value ?: return@LaunchedEffect
                if (st.refocus == 0) return@LaunchedEffect
                if (st.focusKey.isEmpty() || list.none { it.id == st.focusKey }) {
                    st.focusKey = st.group?.let { g -> "g:" + g.key }?.takeIf { k -> list.any { it.id == k } } ?: list.firstOrNull()?.id ?: ""
                }
                val idx = list.indexOfFirst { it.id == st.focusKey }.coerceAtLeast(0)
                if (gState.layoutInfo.visibleItemsInfo.none { it.index == idx }) gState.scrollToItem((idx - 3).coerceAtLeast(0))
                st.focusFr.tryFocus()
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            val g = st.group
            if (g == null) Message("Izaberi grupu", "Drži OK na grupi za opcije")
            else Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight().onPreviewKeyEvent { e ->
                    // Left from the channel list always goes back to the groups
                    if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft) { backToGroups(); true } else false
                }) {
                    key(g.key, st.rev) {
                        ChannelList(g, nav, st.gate, rows, onFocusCh = { focusedCh = it; inChannels = true }) { st.rev++ }
                    }
                }
                if (inChannels) EpgPanel(focusedCh, Modifier.width(340.dp))
            }
        }
    }
    TvDialogs(st, rows)
}

/** Rename, move, hide, lock or delete a group. Used by TV, movies, series and the group manager.
 *  [onChanged] gets true when the menu should close; moving keeps it open so you can move again. */
@Composable
fun GroupOptions(kind: String, g: Category, ovr: Ovr, current: List<Category>, onDismiss: () -> Unit, onChanged: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    var rename by remember { mutableStateOf(false) }
    var setup by remember { mutableStateOf(false) }
    val custom = g.id.startsWith("my_")
    if (rename) { TextDialog("Preimenuj grupu", "Novi naziv", g.name, onDismiss = { rename = false }) { n -> if (n.isNotBlank()) ovr.renameGroup(kind, g.id, n); rename = false; onChanged(true) }; return }
    if (setup) { PinSetupDialog(onDismiss = { setup = false }) { ovr.toggleLocked(kind, g.id); setup = false; onChanged(true) }; return }
    val pos = current.indexOfFirst { it.id == g.id }
    Popup(g.name, onDismiss) {
        if (pos >= 0) T("${pos + 1} / ${current.size}", 13.sp, C.Muted)
        OptionRow("Pomjeri gore", R.drawable.lc_arrow_up) {
            if (pos <= 0) Toast.makeText(ctx, tr("Grupa je već prva"), Toast.LENGTH_SHORT).show()
            else { ovr.moveGroup(kind, current, g.id, -1); onChanged(false) }
        }
        OptionRow("Pomjeri dolje", R.drawable.lc_arrow_down) {
            if (pos < 0 || pos >= current.lastIndex) Toast.makeText(ctx, tr("Grupa je već zadnja"), Toast.LENGTH_SHORT).show()
            else { ovr.moveGroup(kind, current, g.id, 1); onChanged(false) }
        }
        OptionRow("Preimenuj", R.drawable.lc_pencil) { rename = true }
        OptionRow(if (ovr.isHidden(kind, g.id)) "Prikaži grupu" else if (custom) "Sakrij grupu" else "Sakrij / izbriši grupu", R.drawable.lc_eye_off) { ovr.toggleHidden(kind, g.id); onChanged(true) }
        OptionRow(if (ovr.isLocked(kind, g.id)) "Otključaj (ukloni PIN)" else "Zaključaj PIN-om (4 broja)", R.drawable.lc_lock,
            sub = "Za kanale koje djeca ne smiju gledati") {
            if (!Lock.hasPin()) setup = true else { ovr.toggleLocked(kind, g.id); onChanged(true) }
        }
        if (custom) OptionRow("Obriši grupu", R.drawable.lc_trash_2, color = C.Danger) { ovr.deleteGroup(g.id); onChanged(true) }
    }
}

/** Options of a favourites list. */
@Composable
private fun FavListOptions(g: GroupRef, onDismiss: () -> Unit, onChanged: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val id = favListId(g.cat.id)
    var rename by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf(false) }
    val lists = Favs.lists(ctx)
    val pos = lists.indexOfFirst { it.id == id }
    if (rename) { TextDialog("Preimenuj listu", "Novi naziv", lists.getOrNull(pos)?.name ?: "", onDismiss = { rename = false }) { n -> if (n.isNotBlank()) Favs.renameList(ctx, id, n); rename = false; onChanged(true) }; return }
    if (del) {
        Popup("Obrisati listu?", onDismiss = { del = false }) {
            T("Kanali ostaju u playlisti, briše se samo ova lista favorita.", 14.sp, C.Soft, maxLines = 3)
            OptionRow("Da, obriši listu", R.drawable.lc_trash_2, color = C.Danger) { Favs.deleteList(ctx, id); del = false; onChanged(true) }
            OptionRow("Odustani", R.drawable.lc_x) { del = false }
        }
        return
    }
    Popup(g.cat.name, onDismiss) {
        if (pos >= 0) T("${pos + 1} / ${lists.size}", 13.sp, C.Muted)
        OptionRow("Pomjeri gore", R.drawable.lc_arrow_up) { if (pos > 0) { Favs.moveList(ctx, id, -1); onChanged(false) } }
        OptionRow("Pomjeri dolje", R.drawable.lc_arrow_down) { if (pos in 0 until lists.lastIndex) { Favs.moveList(ctx, id, 1); onChanged(false) } }
        OptionRow("Preimenuj", R.drawable.lc_pencil) { rename = true }
        if (lists.size > 1) OptionRow("Obriši listu", R.drawable.lc_trash_2, color = C.Danger) { del = true }
    }
}

/** Play [list] from [index], with the preview stopped first (one connection at a time). */
fun playChannels(nav: Nav, list: List<Channel>, index: Int) {
    Preview.stop()
    nav.push(Screen.Player(list.map { PlayItem(it.name, it.url, it.logo, "${it.num}", it) }, index, live = true))
}

@Composable
private fun ChannelList(g: GroupRef, nav: Nav, gate: PinGate, rows: Load<List<GRow>>, onFocusCh: (Channel) -> Unit = {}, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    var listRev by remember { mutableIntStateOf(0) }
    val isFav = g.pid == null
    val state by load(g.key, listRev, Favs.rev, keep = true) { channelsOf(ctx, g) }
    var menu by remember { mutableStateOf<Channel?>(null) }
    var menuUrl by remember { mutableStateOf<String?>(null) }
    val cWrap = remember { Wrap() }
    val cState = rememberLazyListState()
    val chFr = remember { FocusRequester() }
    WrapEffect(cWrap, cState)

    Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 22.dp)) {
        T(g.cat.name, 20.sp, C.Text, FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())
        T("OK gledaj · drži OK = opcije · ◀ grupe", 12.sp, C.Muted, modifier = Modifier.padding(bottom = 10.dp))
        when (val s = state) {
            is Load.Loading -> Loading("Učitavam kanale…")
            is Load.Err -> Message("Kanali se nisu učitali", s.message)
            is Load.Ok -> {
                val list = s.value
                if (list.isEmpty()) {
                    Message(if (isFav) "Nema favorita" else "Grupa je prazna",
                        if (isFav) "Na kanalu drži OK › Dodaj u favorite" else "Kanale možeš prebaciti ovamo preko opcija kanala")
                } else {
                    LazyColumn(state = cState, verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 30.dp)) {
                        itemsIndexed(list, key = { i, ch -> "$i${ch.url}" }) { i, ch ->
                            val ovr = remember(ch.pid, g.pid) { Ovr(ctx, ch.pid.ifBlank { g.pid ?: "" }) }
                            val locked = ovr.isChLocked(ch)
                            Focusable(
                                modifier = Modifier.fillMaxWidth().height(Rows.channel).wrapKeys(i, list.size, cWrap),
                                onClick = {
                                    gate.run("c:${ch.url}", locked) {
                                        ovr.countWatch(ch)
                                        TvMemory.channel = i; TvMemory.group = g; TvMemory.restore = true
                                        playChannels(nav, list, i)
                                    }
                                },
                                onMenu = { menu = ch; menuUrl = ch.url },
                                onFocus = { onFocusCh(ch) },
                                focusRequester = cWrap.requester(i, if (ch.url == menuUrl) chFr else null),
                                normal = C.Card,
                                zoom = 1.01f,
                            ) { f ->
                                Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    T("${ch.num}", 13.sp, C.Muted, modifier = Modifier.width(34.dp))
                                    ChannelLogo(ch.logo, ch.name)
                                    T(ch.name, Rows.font, if (f) C.Text else C.Soft, if (f) FontWeight.SemiBold else FontWeight.Normal,
                                        modifier = Modifier.weight(1f))
                                    qualityOf(ch.name)?.let { Chip(it, C.Card, C.Soft) }
                                    if (locked) Ico(R.drawable.lc_lock, 16.dp, C.Muted)
                                    if (!isFav && Favs.inAny(ctx, ch)) T("★", 17.sp, C.Amber)
                                }
                            }
                        }
                    }
                }
                LaunchedEffect(list) {
                    // back from the player: focus the channel that was playing (once)
                    if (TvMemory.restore && TvMemory.channel >= 0) { TvMemory.restore = false; cWrap.target = TvMemory.channel.coerceAtMost(list.lastIndex) }
                }
                menu?.let { ch ->
                    val groups = (rows as? Load.Ok)?.value?.mapNotNull { (it as? GRow.Group)?.ref }
                        ?.filter { it.pid != null && it.pid == ch.pid.ifBlank { g.pid } && it.cat.id != ALL_GROUP }?.map { it.cat } ?: emptyList()
                    ChannelOptions(g, ch, list, groups, onDismiss = { menu = null }) { reload, close ->
                        listRev++
                        if (close) menu = null
                        if (reload) onChanged()
                    }
                }
                // after the options close, the focus stays on that channel
                LaunchedEffect(menu) { if (menu == null && menuUrl != null) chFr.tryFocus() }
            }
        }
    }
}

/** Pick the favourites lists a channel is in. */
@Composable
fun FavPicker(ch: Channel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var rev by remember { mutableIntStateOf(0) }
    var newList by remember { mutableStateOf(false) }
    if (newList) { TextDialog("Nova lista favorita", "Naziv (npr. Moji, Supruga, Djeca, Sport)", onDismiss = { newList = false }) { n ->
        if (n.isNotBlank()) { val l = Favs.addList(ctx, n); Favs.toggle(l.id, ch) }; newList = false; rev++ }; return }
    key(rev) {
        Popup(tr("Favoriti") + " · " + ch.name, onDismiss) {
            Favs.lists(ctx).forEach { l ->
                val inIt = Favs.contains(l.id, ch)
                OptionRow("★ " + l.name, R.drawable.lc_star, selected = inIt, color = if (inIt) C.Amber else null) {
                    Favs.toggle(l.id, ch)
                    Toast.makeText(ctx, trf(if (inIt) "Uklonjeno iz liste %s" else "Dodano u listu %s", l.name), Toast.LENGTH_SHORT).show()
                    rev++
                }
            }
            OptionRow("Nova lista favorita", R.drawable.lc_plus) { newList = true }
            OptionRow("Gotovo", R.drawable.lc_check) { onDismiss() }
        }
    }
}

/** Options of one channel: favourites, order, group, name, hide, lock. */
@Composable
fun ChannelOptions(g: GroupRef, ch: Channel, list: List<Channel>, groups: List<Category>,
                   onDismiss: () -> Unit, onChanged: (reload: Boolean, close: Boolean) -> Unit) {
    val ctx = LocalContext.current
    val favId = if (g.pid == null) favListId(g.cat.id) else null
    val ovr = remember(ch.pid, g.pid) { Ovr(ctx, ch.pid.ifBlank { g.pid ?: "" }) }
    var mode by remember { mutableStateOf("menu") }
    when (mode) {
        "fav" -> { FavPicker(ch) { mode = "menu"; onChanged(false, false) }; return }
        "rename" -> { TextDialog("Preimenuj kanal", "Novi naziv", ch.name, onDismiss = { mode = "menu" }) { n ->
            if (n.isNotBlank()) { if (favId != null) Favs.rename(favId, ch, n) else ovr.renameChannel(ch, n) }; onChanged(true, true) }; return }
        "info" -> {
            Popup("Informacije o kanalu", onDismiss = { mode = "menu" }) {
                OptionRow("Naziv", R.drawable.lc_tv, value = ch.name) {}
                OptionRow("Grupa", R.drawable.lc_layers, value = g.cat.name) {}
                OptionRow("Playlista", R.drawable.lc_list, value = Store.byId(ctx, ch.pid.ifBlank { g.pid })?.name ?: "—") {}
                OptionRow("Broj", R.drawable.lc_list, value = "${ch.num}") {}
                OptionRow("EPG ID", R.drawable.lc_clock, value = ch.epg ?: tr("Nema")) {}
                OptionRow("Vrsta strima", R.drawable.lc_file_video, value = when {
                    ch.url.contains(".m3u8") -> "HLS (.m3u8)"; ch.url.contains(".ts") -> "MPEG-TS (.ts)"; else -> tr("Direktni strim") }) {}
                OptionRow("Kvalitet po nazivu", R.drawable.lc_monitor, value = qualityOf(ch.name) ?: tr("Nije naveden")) {}
                OptionRow("Server", R.drawable.lc_globe, value = try { java.net.URL(ch.url).host } catch (e: Exception) { "—" }) {}
                OptionRow("Zaključan", R.drawable.lc_lock, value = tr(if (ovr.isChLocked(ch)) "Da" else "Ne")) {}
            }
            return
        }
        "delete" -> {
            Popup("Izbrisati kanal?", onDismiss = { mode = "menu" }) {
                T(trf("Kanal \"%s\" nestaje iz liste. Vraćaš ga u Postavke › Grupe i kanali › Vrati sakrivene kanale.", ch.name), 14.sp, C.Soft, maxLines = 3)
                OptionRow("Da, izbriši iz liste", R.drawable.lc_trash_2, color = C.Danger) { if (!ovr.isChHidden(ch)) ovr.toggleChHidden(ch); onChanged(true, true) }
                OptionRow("Odustani", R.drawable.lc_x) { mode = "menu" }
            }
            return
        }
        "pin" -> { PinSetupDialog(onDismiss = { mode = "menu" }) { ovr.toggleChLocked(ch); onChanged(false, true) }; return }
        "newgroup" -> { TextDialog("Nova grupa", "Naziv nove grupe", onDismiss = { mode = "move" }) { n ->
            if (n.isNotBlank()) { val ng = ovr.addGroup(n); ovr.moveChannelTo(ch, ng.id) }; onChanged(true, true) }; return }
        "move" -> {
            Popup("Premjesti u grupu", onDismiss = { mode = "menu" }) {
                OptionRow("Nova grupa…", R.drawable.lc_plus) { mode = "newgroup" }
                groups.filter { it.id != g.cat.id }.forEach { target ->
                    OptionRow(target.name, R.drawable.lc_layers) {
                        ovr.moveChannelTo(ch, target.id)
                        Toast.makeText(ctx, trf("Premješteno u %s", target.name), Toast.LENGTH_SHORT).show()
                        onChanged(true, true)
                    }
                }
            }
            return
        }
    }
    Popup(ch.name, onDismiss) {
        if (favId != null) {
            OptionRow("Ukloni iz ove liste", R.drawable.lc_star) {
                Favs.toggle(favId, ch); Toast.makeText(ctx, tr("Uklonjeno iz favorita"), Toast.LENGTH_SHORT).show(); onChanged(false, true)
            }
            OptionRow("Liste favorita…", R.drawable.lc_list) { mode = "fav" }
        } else OptionRow(if (Favs.inAny(ctx, ch)) "Liste favorita…" else "Dodaj u favorite", R.drawable.lc_star, color = C.Amber) {
            val lists = Favs.lists(ctx)
            if (lists.size == 1 && !Favs.inAny(ctx, ch)) {
                Favs.toggle(lists[0].id, ch); Toast.makeText(ctx, tr("Dodano u favorite"), Toast.LENGTH_SHORT).show(); onChanged(false, true)
            } else mode = "fav"
        }
        if (Lock.simple) return@Popup
        if (g.cat.id != ALL_GROUP) {
            OptionRow("Pomjeri gore", R.drawable.lc_arrow_up) {
                if (favId != null) Favs.move(favId, ch, -1) else ovr.moveChannel(g.cat.id, list, ch, -1); onChanged(false, false)
            }
            OptionRow("Pomjeri dolje", R.drawable.lc_arrow_down) {
                if (favId != null) Favs.move(favId, ch, 1) else ovr.moveChannel(g.cat.id, list, ch, 1); onChanged(false, false)
            }
        }
        OptionRow("Preimenuj kanal", R.drawable.lc_pencil) { mode = "rename" }
        if (favId == null) {
            OptionRow("Premjesti u drugu grupu", R.drawable.lc_layers) { mode = "move" }
            OptionRow(if (ovr.isChHidden(ch)) "Prikaži kanal" else "Sakrij kanal", R.drawable.lc_eye_off) { ovr.toggleChHidden(ch); onChanged(true, true) }
            OptionRow(if (ovr.isChLocked(ch)) "Otključaj kanal" else "Zaključaj PIN-om (4 broja)", R.drawable.lc_lock) {
                if (!Lock.hasPin()) mode = "pin" else { ovr.toggleChLocked(ch); onChanged(false, true) }
            }
            OptionRow("Izbriši kanal", R.drawable.lc_trash_2, color = C.Danger) { mode = "delete" }
        }
        OptionRow("Informacije o kanalu", R.drawable.lc_info) { mode = "info" }
    }
}

@Composable
fun ChannelLogo(url: String?, name: String) {
    val (w, h) = when (Look.rowSize) { 0 -> 50.dp to 30.dp; 2 -> 62.dp to 38.dp; else -> 56.dp to 34.dp }
    var failed by remember(url) { mutableStateOf(false) }
    val clean = name.substringAfterLast('|').substringAfterLast(':').trim().ifBlank { name }
    val hue = (clean.hashCode() and 0x7fffffff) % 360
    Box(Modifier.width(w).height(h).clip(Rnd(8.dp))
        .background(if (url != null && !failed) (if (C.IsLight) Color(0xFFE6E8EC) else Color(0xFF1C2230))
                    else Color.hsv(hue.toFloat(), 0.45f, if (C.IsLight) 0.85f else 0.38f)),
        contentAlignment = Alignment.Center) {
        if (url != null && !failed) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Fit, onError = { failed = true },
                modifier = Modifier.fillMaxSize().padding(4.dp))
        } else {
            val words = clean.split(' ', '-', '.').filter { it.isNotBlank() && it.first().isLetterOrDigit() }
            val initials = (if (words.size >= 2) words.take(3).joinToString("") { it.first().toString() } else clean.filter { it.isLetterOrDigit() }.take(3)).uppercase()
            T(initials, 12.sp, Color.White, FontWeight.Bold)
        }
    }
}

@Composable
fun NoPlaylist(nav: Nav) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Message("Nema aktivne playliste", "Dodaj Xtream Codes ili M3U playlistu")
        Button("Dodaj playlistu", R.drawable.lc_plus, primary = true) { nav.push(Screen.AddPlaylist) }
    }
}

/** Settings › Groups and channels: order, names, hidden and locked groups for TV, movies and series. */
@Composable
fun GroupManager(nav: Nav) {
    val ctx = LocalContext.current
    val p = remember { Store.active(ctx) } ?: run { NoPlaylist(nav); return }
    val ovr = remember(p.id) { Ovr(ctx, p.id) }
    var tab by remember { mutableIntStateOf(0) }
    var rev by remember { mutableIntStateOf(0) }
    val kind = listOf("live", "vod", "series")[tab]
    val st by load(p.id, kind, rev, keep = true) {
        val raw = when (kind) { "live" -> Repo.liveGroups(p); "vod" -> Repo.vodGroups(p); else -> Repo.seriesGroups(p) }
        ovr.groups(kind, raw, all = true)
    }
    var menu by remember { mutableStateOf<Category?>(null) }
    var lastId by remember { mutableStateOf<String?>(null) }
    var refocus by remember { mutableIntStateOf(0) }
    val rowFr = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var newGroup by remember { mutableStateOf(false) }
    var reset by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        T(tr("Grupe i kanali") + " · ${p.name}", 22.sp, C.Text, FontWeight.Bold)
        T("OK na grupi otvara opcije: redoslijed, naziv, sakrivanje, PIN. Kanale uređuješ u TV-u: drži OK na kanalu.", 13.sp, C.Muted, maxLines = 2)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Segmented(listOf("TV", "Filmovi", "Serije"), tab) { tab = it }
            if (tab == 0) Button("Nova grupa", R.drawable.lc_plus) { newGroup = true }
            if (tab == 0) Button("Vrati sakrivene kanale", R.drawable.lc_eye) { ovr.unhideAllChannels(); rev++; nav.playlistsChanged() }
            Button("Vrati sve izmjene", R.drawable.lc_rotate_ccw) { reset = true }
        }
        when (val s = st) {
            is Load.Loading -> Loading()
            is Load.Err -> Message("Greška", s.message)
            is Load.Ok -> LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(5.dp), contentPadding = PaddingValues(bottom = 30.dp)) {
                itemsIndexed(s.value, key = { _, g -> g.id }) { i, g ->
                    val hidden = ovr.isHidden(kind, g.id); val locked = ovr.isLocked(kind, g.id)
                    Focusable(modifier = Modifier.fillMaxWidth().height(Rows.channel - 4.dp), onClick = { menu = g; lastId = g.id }, onMenu = { menu = g; lastId = g.id },
                        focusRequester = if (g.id == lastId) rowFr else null,
                        normal = C.Card, zoom = 1.01f) { _ ->
                        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            T("${i + 1}", 12.sp, C.Muted, modifier = Modifier.width(30.dp))
                            T(g.name, 15.sp, if (hidden) C.Muted else C.Text, FontWeight.Medium, modifier = Modifier.weight(1f))
                            if (g.id.startsWith("my_")) Chip("MOJA", C.CardFocus, C.RedText)
                            if (hidden) Ico(R.drawable.lc_eye_off, 16.dp, C.Muted)
                            if (locked) Ico(R.drawable.lc_lock, 16.dp, C.Muted)
                        }
                    }
                }
            }
        }
    }
    menu?.let { g ->
        // moving keeps the menu open; the list behind follows, so you see where the group is
        GroupOptions(kind, g, ovr, (st as? Load.Ok)?.value ?: emptyList(), onDismiss = { menu = null; refocus++ }) { close ->
            rev++
            if (close) { menu = null; refocus++ }
            else {
                val idx = (st as? Load.Ok)?.value?.indexOfFirst { it.id == g.id } ?: -1
                if (idx >= 0) scope.launch { listState.animateScrollToItem((idx - 3).coerceAtLeast(0)) }
            }
        }
    }
    LaunchedEffect(refocus) {
        if (refocus == 0 || lastId == null) return@LaunchedEffect
        delay(120)
        val idx = (st as? Load.Ok)?.value?.indexOfFirst { it.id == lastId } ?: -1
        if (idx >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == idx }) listState.scrollToItem((idx - 3).coerceAtLeast(0))
        rowFr.tryFocus()
    }
    if (newGroup) TextDialog("Nova grupa", "Naziv", onDismiss = { newGroup = false }) { n -> if (n.isNotBlank()) ovr.addGroup(n); newGroup = false; rev++ }
    if (reset) Popup("Vratiti sve izmjene?", onDismiss = { reset = false }) {
        T("Briše tvoj redoslijed, nazive, sakrivene i zaključane grupe i kanale, i tvoje grupe za ovu playlistu.", 14.sp, C.Soft, maxLines = 3)
        OptionRow("Da, vrati kako je bilo", R.drawable.lc_rotate_ccw, color = C.Danger) { ovr.resetAll(); reset = false; rev++; nav.playlistsChanged() }
        OptionRow("Odustani", R.drawable.lc_x) { reset = false }
    }
}

/** Small live picture of a channel; registers with [Preview] so the player can stop it first. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PreviewBox(ch: Channel?, modifier: Modifier) {
    val ctx = LocalContext.current
    val player = remember { buildPlayer(ctx) }
    DisposableEffect(Unit) {
        Preview.player = player
        onDispose { if (Preview.player === player) Preview.player = null; player.release() }
    }
    LaunchedEffect(ch?.url) {
        val c = ch
        if (c == null || (Ovr(ctx, c.pid).isChLocked(c) && "c:${c.url}" !in Lock.unlocked)) { player.stop(); return@LaunchedEffect }
        delay(500)
        player.setMediaItem(androidx.media3.common.MediaItem.fromUri(c.url)); player.prepare(); player.playWhenReady = true
    }
    Box(modifier.aspectRatio(16f / 9f).clip(Rnd(14.dp)).background(Color.Black)) {
        androidx.compose.ui.viewinterop.AndroidView(factory = { v ->
            androidx.media3.ui.PlayerView(v).apply {
                this.player = player; useController = false; isFocusable = false
                descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
            }
        }, modifier = Modifier.fillMaxSize())
    }
}

/** EPG list of one channel (programme guide); OK on a coming show sets a reminder. */
@Composable
fun EpgList(ch: Channel, limit: Int = 12, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val st by load(ch.url, limit, Reminders.rev) { Store.byId(ctx, ch.pid)?.let { Repo.epg(it, ch, limit) } ?: emptyList() }
    when (val s = st) {
        is Load.Loading -> T("Učitavam program…", 13.sp, C.Muted)
        is Load.Err -> T("Program nije dostupan", 13.sp, C.Muted)
        is Load.Ok -> if (s.value.isEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                T("Nema EPG podataka za ovaj kanal.", 14.sp, C.Soft)
                T(if (Prefs.epgUrl.isBlank()) "Dodaj vlastiti EPG: Postavke › Playliste › Vlastiti EPG izvor" else "Ni tvoj EPG izvor nema ovaj kanal.", 12.sp, C.Muted, maxLines = 2)
            }
        } else LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(s.value) { _, e ->
                val now = e.start <= System.currentTimeMillis() && e.end > System.currentTimeMillis()
                val rem = Reminders.has(e.title, e.start)
                Focusable(modifier = Modifier.fillMaxWidth(), onClick = {
                    if (!now) {
                        val on = Reminders.toggle(Reminder("r${e.start}${e.title.hashCode()}", e.title, e.start, ch.url, ch.name, ch.logo, ch.pid, "tv"))
                        Toast.makeText(ctx, tr(if (on) "Podsjetnik postavljen" else "Podsjetnik uklonjen"), Toast.LENGTH_SHORT).show()
                    }
                }, normal = if (now) C.CardFocus else C.Card, shape = Rnd(12.dp), zoom = 1.01f) { _ ->
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            T(e.times(), 12.sp, if (now) C.RedText else C.Muted, FontWeight.SemiBold)
                            if (now) Chip("SADA", C.Red, C.OnRed)
                            if (rem) Ico(R.drawable.lc_bell, 14.dp, C.Amber)
                            if (!now && !rem) T("OK = podsjeti me", 11.sp, C.Muted)
                        }
                        T(e.title, 15.sp, C.Text, FontWeight.Medium, maxLines = 2)
                        if (now) {
                            Box(Modifier.fillMaxWidth().height(4.dp).clip(Rnd(2.dp)).background(C.CardHi)) {
                                Box(Modifier.fillMaxWidth(e.progress()).height(4.dp).background(C.Red))
                            }
                            e.desc?.let { T(it, 12.sp, C.Soft, maxLines = 4) }
                        } else e.desc?.let { T(it, 12.sp, C.Muted, maxLines = 2) }
                    }
                }
            }
        }
    }
}

/** Right side while browsing channels: live preview + programme guide of the focused channel. */
@Composable
fun EpgPanel(ch: Channel?, modifier: Modifier) {
    var shown by remember { mutableStateOf<Channel?>(null) }
    LaunchedEffect(ch?.url) { delay(450); shown = ch }
    Column(modifier.fillMaxHeight().background(C.Rail).padding(horizontal = 18.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val c = shown
        if (Look.preview) PreviewBox(c, Modifier.fillMaxWidth())
        if (c == null) { T("Stani na kanal da vidiš program", 14.sp, C.Muted); return@Column }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChannelLogo(c.logo, c.name)
            T(c.name, 17.sp, C.Text, FontWeight.SemiBold, modifier = Modifier.weight(1f))
            qualityOf(c.name)?.let { Chip(it, C.Card, C.Soft) }
        }
        EpgList(c)
    }
}

/** Red number badge. */
@Composable
fun Bell(count: Int) {
    if (count <= 0) return
    Box(Modifier.size(18.dp).clip(CircleShape).background(C.Danger), contentAlignment = Alignment.Center) {
        T(if (count > 99) "99" else "$count", 10.sp, Color.White, FontWeight.Bold)
    }
}
