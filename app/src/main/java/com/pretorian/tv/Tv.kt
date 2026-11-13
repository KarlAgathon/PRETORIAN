package com.pretorian.tv

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.BackHandler
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

/** Last TV position, so Back from the player returns to the same group and channel. */
object TvMemory {
    var group: Category? = null
    var channel = -1
    /** true while coming back from the player: focus goes back to the channel that was playing */
    var restore = false
}

/** Row heights from Settings › Appearance › Row size. */
object Rows {
    val channel get() = when (Look.rowSize) { 0 -> 46.dp; 2 -> 60.dp; else -> 52.dp }
    val group get() = when (Look.rowSize) { 0 -> 36.dp; 2 -> 44.dp; else -> 40.dp }
    val font get() = when (Look.rowSize) { 0 -> 14.sp; 2 -> 16.sp; else -> 15.sp }
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

@Composable
fun TvScreen(nav: Nav) {
    val ctx = LocalContext.current
    val p = remember { Store.active(ctx) } ?: run { NoPlaylist(nav); return }
    val ovr = remember(p.id) { Ovr(ctx, p.id) }
    var rev by remember { mutableIntStateOf(0) }
    var chRev by remember { mutableIntStateOf(0) }
    val groupsState by load(p.id, rev, keep = true) { ovr.groups("live", Repo.liveGroups(p)) }
    var focusedGroup by remember { mutableStateOf<Category?>(null) }
    var group by remember { mutableStateOf(TvMemory.group) }
    var groupMenu by remember { mutableStateOf<Category?>(null) }
    var newGroup by remember { mutableStateOf(false) }
    val gate = remember { PinGate() }
    val gWrap = remember { Wrap() }
    val gState = rememberLazyListState()
    WrapEffect(gWrap, gState)
    val coming = TvMemory.restore && TvMemory.group != null && TvMemory.channel >= 0
    // channels mode: groups fold away, guide on the right. Left or Back brings the groups back.
    var inChannels by remember { mutableStateOf(coming) }
    var focusedCh by remember { mutableStateOf<Channel?>(null) }
    val groupFr = remember { FocusRequester() }
    var focusGroupId by remember { mutableStateOf(TvMemory.group?.id ?: FAV_GROUP) }
    var refocus by remember { mutableIntStateOf(if (coming) 0 else 1) }
    if (!coming) TvMemory.restore = false

    fun backToGroups() {
        focusGroupId = group?.id ?: FAV_GROUP
        inChannels = false
        refocus++
    }
    BackHandler(enabled = inChannels && groupMenu == null) { backToGroups() }

    LaunchedEffect(focusedGroup) {
        val g = focusedGroup ?: return@LaunchedEffect
        delay(350)
        if (!ovr.isLocked("live", g.id) || "g:${g.id}" in Lock.unlocked) { if (group?.id != g.id) TvMemory.channel = -1; group = g; TvMemory.group = g }
    }

    Row(Modifier.fillMaxSize()) {
        if (!inChannels) {
            Column(Modifier.width(290.dp).fillMaxHeight().padding(start = 16.dp, end = 10.dp, top = 22.dp)) {
                T("TV", 26.sp, C.Text, FontWeight.Bold, modifier = Modifier.padding(start = 10.dp, bottom = 2.dp))
                T(p.name, 13.sp, C.Muted, modifier = Modifier.padding(start = 10.dp, bottom = 10.dp))
                when (val s = groupsState) {
                    is Load.Loading -> Loading("Učitavam grupe…")
                    is Load.Err -> Message("Greška", s.message)
                    is Load.Ok -> {
                        val all = listOf(Category(FAV_GROUP, "★ Favoriti")) + s.value
                        val n = all.size + if (Lock.simple) 0 else 1
                        LazyColumn(state = gState, verticalArrangement = Arrangement.spacedBy(3.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                            itemsIndexed(all, key = { _, g -> g.id }) { i, g ->
                                val selected = group?.id == g.id
                                val locked = ovr.isLocked("live", g.id)
                                Focusable(
                                    modifier = Modifier.fillMaxWidth().height(Rows.group).wrapKeys(i, n, gWrap),
                                    onClick = { gate.run("g:${g.id}", locked) { group = g; TvMemory.group = g } },
                                    onFocus = { focusedGroup = g; focusGroupId = g.id },
                                    onMenu = if (Lock.simple || g.id == FAV_GROUP) null else ({ groupMenu = g }),
                                    focusRequester = gWrap.requester(i, if (g.id == focusGroupId) groupFr else null),
                                    normal = if (selected) C.CardFocus else Color.Transparent,
                                    zoom = 1.0f,
                                ) { _ ->
                                    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        T(g.name, Rows.font, if (selected) C.Text else C.Soft, if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                            modifier = Modifier.weight(1f))
                                        if (locked) Ico(R.drawable.lc_lock, 15.dp, C.Muted)
                                    }
                                }
                            }
                            if (!Lock.simple) item(key = "__new__") {
                                Focusable(modifier = Modifier.fillMaxWidth().height(Rows.group).wrapKeys(all.size, n, gWrap), onClick = { newGroup = true },
                                    focusRequester = gWrap.requester(all.size),
                                    normal = Color.Transparent, zoom = 1f) { _ ->
                                    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Ico(R.drawable.lc_plus, 16.dp, C.Muted); T("Nova grupa", 14.sp, C.Muted)
                                    }
                                }
                            }
                        }
                        // put the focus on the group we came from (start, Left/Back from channels, closed menu)
                        LaunchedEffect(refocus) {
                            if (refocus == 0) return@LaunchedEffect
                            val idx = all.indexOfFirst { it.id == focusGroupId }.coerceAtLeast(0)
                            if (gState.layoutInfo.visibleItemsInfo.none { it.index == idx }) gState.scrollToItem((idx - 3).coerceAtLeast(0))
                            groupFr.tryFocus()
                        }
                    }
                }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(C.Line))
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            val g = group
            if (g == null) Message("Izaberi grupu", "Drži OK na grupi za opcije")
            else Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight().onPreviewKeyEvent { e ->
                    // Left from the channel list always goes back to the groups
                    if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft) { backToGroups(); true } else false
                }) {
                    key(g.id, chRev) {
                        ChannelList(p, ovr, g, nav, gate, s = groupsState, onFocusCh = { focusedCh = it; inChannels = true }) { rev++; chRev++ }
                    }
                }
                if (inChannels) EpgPanel(p, focusedCh, Modifier.width(340.dp))
            }
        }
    }

    groupMenu?.let { g ->
        GroupOptions("live", g, ovr, (groupsState as? Load.Ok)?.value ?: emptyList(),
            onDismiss = { groupMenu = null; focusGroupId = g.id; refocus++ }) { close ->
            rev++
            if (close) { groupMenu = null; focusGroupId = g.id; refocus++ }
        }
    }
    if (newGroup) TextDialog("Nova grupa", "Naziv (npr. Dokumentarni, Sport, Bosna)", onDismiss = { newGroup = false; refocus++ }) { name ->
        if (name.isNotBlank()) ovr.addGroup(name)
        newGroup = false; rev++; refocus++
    }
    PinGateDialog(gate)
}

/** Rename, move, hide, lock or delete a group. Used by TV and the group manager.
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

@Composable
private fun ChannelList(p: Playlist, ovr: Ovr, g: Category, nav: Nav, gate: PinGate, s: Load<List<Category>>,
                        onFocusCh: (Channel) -> Unit = {}, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    var favRev by remember { mutableIntStateOf(0) }
    val isFav = g.id == FAV_GROUP
    val state by load(p.id, g.id, favRev, keep = true) {
        if (isFav) Store.favorites(ctx, p.id)
        else ovr.channels(g.id, if (g.id.startsWith("my_")) emptyList() else Repo.channels(p, g.id))
    }
    var menu by remember { mutableStateOf<Channel?>(null) }
    var menuUrl by remember { mutableStateOf<String?>(null) }
    val cWrap = remember { Wrap() }
    val cState = rememberLazyListState()
    val chFr = remember { FocusRequester() }
    WrapEffect(cWrap, cState)

    Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 22.dp)) {
        T(g.name, 20.sp, C.Text, FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())
        T("OK gledaj · drži OK = opcije", 12.sp, C.Muted, modifier = Modifier.padding(bottom = 10.dp))
        when (val st = state) {
            is Load.Loading -> Loading("Učitavam kanale…")
            is Load.Err -> Message("Kanali se nisu učitali", st.message)
            is Load.Ok -> {
                val list = st.value
                if (list.isEmpty()) {
                    Message(if (isFav) "Nema favorita" else "Grupa je prazna",
                        if (isFav) "Na kanalu drži OK › Dodaj u favorite" else "Kanale možeš prebaciti ovamo preko opcija kanala")
                } else {
                    val items = list.map { PlayItem(it.name, it.url, it.logo, "${it.num}", it) }
                    val favUrls = Store.favorites(ctx, p.id).map { it.url }.toSet()
                    LazyColumn(state = cState, verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 30.dp)) {
                        itemsIndexed(list, key = { i, ch -> "$i${ch.url}" }) { i, ch ->
                            val locked = ovr.isChLocked(ch)
                            Focusable(
                                modifier = Modifier.fillMaxWidth().height(Rows.channel).wrapKeys(i, list.size, cWrap),
                                onClick = {
                                    gate.run("c:${ch.url}", locked) {
                                        ovr.countWatch(ch)
                                        TvMemory.channel = i; TvMemory.group = g; TvMemory.restore = true
                                        nav.push(Screen.Player(items, i, live = true))
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
                                    if (ch.url in favUrls) T("★", 17.sp, C.Amber)
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
                    ChannelOptions(p, ovr, g, ch, list, (s as? Load.Ok)?.value ?: emptyList(), onDismiss = { menu = null }) { reload, close ->
                        favRev++
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

/** Options of one channel: favourites, order, group, name, hide, lock. */
@Composable
fun ChannelOptions(p: Playlist, ovr: Ovr, g: Category, ch: Channel, list: List<Channel>, groups: List<Category>,
                   onDismiss: () -> Unit, onChanged: (reload: Boolean, close: Boolean) -> Unit) {
    val ctx = LocalContext.current
    val isFav = g.id == FAV_GROUP
    val fav = Store.isFavorite(ctx, p.id, ch)
    var mode by remember { mutableStateOf("menu") }
    when (mode) {
        "rename" -> { TextDialog("Preimenuj kanal", "Novi naziv", ch.name, onDismiss = { mode = "menu" }) { n ->
            if (n.isNotBlank()) { if (isFav) Store.renameFavorite(ctx, p.id, ch, n) else ovr.renameChannel(ch, n) }; onChanged(true, true) }; return }
        "info" -> {
            Popup("Informacije o kanalu", onDismiss = { mode = "menu" }) {
                OptionRow("Naziv", R.drawable.lc_tv, value = ch.name) {}
                OptionRow("Grupa", R.drawable.lc_layers, value = if (isFav) "Favoriti" else g.name) {}
                OptionRow("Broj", R.drawable.lc_list, value = "${ch.num}") {}
                OptionRow("EPG ID", R.drawable.lc_clock, value = ch.epg ?: "Nema") {}
                OptionRow("Vrsta strima", R.drawable.lc_file_video, value = when {
                    ch.url.contains(".m3u8") -> "HLS (.m3u8)"; ch.url.contains(".ts") -> "MPEG-TS (.ts)"; else -> "Direktni strim" }) {}
                OptionRow("Kvalitet po nazivu", R.drawable.lc_monitor, value = qualityOf(ch.name) ?: "Nije naveden") {}
                OptionRow("Server", R.drawable.lc_globe, value = try { java.net.URL(ch.url).host } catch (e: Exception) { "—" }) {}
                OptionRow("Zaključan", R.drawable.lc_lock, value = if (ovr.isChLocked(ch)) "Da" else "Ne") {}
            }
            return
        }
        "delete" -> {
            Popup("Izbrisati kanal?", onDismiss = { mode = "menu" }) {
                T("Kanal \"${ch.name}\" nestaje iz liste. Vraćaš ga u Postavke › Grupe i kanali › Vrati sakrivene kanale.", 14.sp, C.Soft, maxLines = 3)
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
                groups.filter { it.id != g.id && it.id != FAV_GROUP }.forEach { target ->
                    OptionRow(target.name, R.drawable.lc_layers) {
                        ovr.moveChannelTo(ch, target.id)
                        Toast.makeText(ctx, "Premješteno u ${target.name}", Toast.LENGTH_SHORT).show()
                        onChanged(true, true)
                    }
                }
            }
            return
        }
    }
    Popup(ch.name, onDismiss) {
        OptionRow(if (fav) "Ukloni iz favorita" else "Dodaj u favorite", R.drawable.lc_star, color = if (fav) null else C.Amber) {
            Store.toggleFavorite(ctx, p.id, ch)
            Toast.makeText(ctx, if (fav) "Uklonjeno iz favorita" else "Dodano u favorite", Toast.LENGTH_SHORT).show()
            onChanged(false, true)
        }
        if (Lock.simple) return@Popup
        OptionRow("Pomjeri gore", R.drawable.lc_arrow_up) {
            if (isFav) Store.moveFavorite(ctx, p.id, ch, -1) else ovr.moveChannel(g.id, list, ch, -1); onChanged(false, false)
        }
        OptionRow("Pomjeri dolje", R.drawable.lc_arrow_down) {
            if (isFav) Store.moveFavorite(ctx, p.id, ch, 1) else ovr.moveChannel(g.id, list, ch, 1); onChanged(false, false)
        }
        OptionRow("Preimenuj kanal", R.drawable.lc_pencil) { mode = "rename" }
        if (!isFav) {
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
    Box(Modifier.width(w).height(h).clip(RoundedCornerShape(8.dp))
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
        T("Grupe i kanali · ${p.name}", 22.sp, C.Text, FontWeight.Bold)
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
        GroupOptions(kind, g, ovr, (st as? Load.Ok)?.value ?: emptyList(),
            onDismiss = { menu = null; refocus++ }) { close ->
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

/** Right side while browsing channels: live preview + programme guide of the focused channel. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun EpgPanel(p: Playlist, ch: Channel?, modifier: Modifier) {
    val ctx = LocalContext.current
    var shown by remember { mutableStateOf<Channel?>(null) }
    val ovr = remember(p.id) { Ovr(ctx, p.id) }
    LaunchedEffect(ch?.url) { delay(450); shown = ch }
    Column(modifier.fillMaxHeight().background(C.Rail).padding(horizontal = 20.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val c = shown
        if (Look.preview) {
            val player = remember { buildPlayer(ctx) }
            androidx.compose.runtime.DisposableEffect(Unit) { onDispose { player.release() } }
            LaunchedEffect(c?.url) {
                if (c == null || (ovr.isChLocked(c) && "c:${c.url}" !in Lock.unlocked)) { player.stop(); return@LaunchedEffect }
                player.setMediaItem(androidx.media3.common.MediaItem.fromUri(c.url)); player.prepare(); player.playWhenReady = true
            }
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(Color.Black)) {
                androidx.compose.ui.viewinterop.AndroidView(factory = { v ->
                    androidx.media3.ui.PlayerView(v).apply {
                        this.player = player; useController = false; isFocusable = false
                        descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    }
                }, modifier = Modifier.fillMaxSize())
            }
        }
        if (c == null) { T("Stani na kanal da vidiš program", 14.sp, C.Muted); return@Column }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChannelLogo(c.logo, c.name)
            T(c.name, 17.sp, C.Text, FontWeight.SemiBold, modifier = Modifier.weight(1f))
            qualityOf(c.name)?.let { Chip(it, C.Card, C.Soft) }
        }
        val st by load(p.id, c.url) { Repo.epg(p, c, 12) }
        when (val s = st) {
            is Load.Loading -> T("Učitavam program…", 13.sp, C.Muted)
            is Load.Err -> T("Program nije dostupan", 13.sp, C.Muted)
            is Load.Ok -> if (s.value.isEmpty()) {
                T("Nema EPG podataka za ovaj kanal.", 14.sp, C.Soft)
                T(if (Prefs.epgUrl.isBlank()) "Dodaj vlastiti EPG: Postavke › Playliste › Vlastiti EPG izvor" else "Ni tvoj EPG izvor nema ovaj kanal.", 12.sp, C.Muted, maxLines = 2)
            } else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(s.value) { i, e ->
                    val now = i == 0 && e.start <= System.currentTimeMillis()
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (now) C.CardFocus else C.Card).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            T(e.times(), 12.sp, if (now) C.RedText else C.Muted, FontWeight.SemiBold)
                            if (now) Chip("SADA", C.Red, C.OnRed)
                        }
                        T(e.title, 15.sp, C.Text, FontWeight.Medium, maxLines = 2)
                        if (now) {
                            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(C.CardHi)) {
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

