package com.pretorian.tv

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
    val groupsState by load(p.id, rev) { ovr.groups("live", Repo.liveGroups(p)) }
    var focusedGroup by remember { mutableStateOf<Category?>(null) }
    var group by remember { mutableStateOf<Category?>(null) }
    var groupMenu by remember { mutableStateOf<Category?>(null) }
    var newGroup by remember { mutableStateOf(false) }
    val gate = remember { PinGate() }
    val firstGroup = remember { FocusRequester() }
    val gWrap = remember { Wrap() }
    val gState = rememberLazyListState()
    WrapEffect(gWrap, gState)

    LaunchedEffect(focusedGroup) {
        val g = focusedGroup ?: return@LaunchedEffect
        delay(350)
        gate.run("g:${g.id}", ovr.isLocked("live", g.id)) { group = g }
    }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(300.dp).fillMaxHeight().padding(start = 18.dp, end = 12.dp, top = 24.dp)) {
            T("TV", 28.sp, C.Text, FontWeight.Bold, modifier = Modifier.padding(start = 10.dp, bottom = 2.dp))
            T(p.name, 13.sp, C.Muted, modifier = Modifier.padding(start = 10.dp, bottom = 12.dp))
            when (val s = groupsState) {
                is Load.Loading -> Loading("Učitavam grupe…")
                is Load.Err -> Message("Greška", s.message)
                is Load.Ok -> {
                    val all = listOf(Category(FAV_GROUP, "★ Favoriti")) + s.value
                    val n = all.size + if (Lock.simple) 0 else 1
                    LazyColumn(state = gState, verticalArrangement = Arrangement.spacedBy(3.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                        itemsIndexed(all, key = { i, g -> "$i${g.id}" }) { i, g ->
                            val selected = group?.id == g.id
                            val locked = ovr.isLocked("live", g.id)
                            Focusable(
                                modifier = Modifier.fillMaxWidth().height(44.dp).wrapKeys(i, n, gWrap),
                                onClick = { gate.run("g:${g.id}", locked) { group = g } },
                                onFocus = { focusedGroup = g },
                                onMenu = if (Lock.simple || g.id == FAV_GROUP) null else ({ groupMenu = g }),
                                focusRequester = gWrap.requester(i, if (i == 0) firstGroup else null),
                                normal = if (selected) C.CardFocus else Color.Transparent,
                                zoom = 1.0f,
                            ) { _ ->
                                Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    T(g.name, 14.sp, if (selected) C.Text else C.Soft, if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        modifier = Modifier.weight(1f))
                                    if (locked) Ico(R.drawable.lc_lock, 15.dp, C.Muted)
                                }
                            }
                        }
                        if (!Lock.simple) item {
                            Focusable(modifier = Modifier.fillMaxWidth().height(44.dp).wrapKeys(all.size, n, gWrap), onClick = { newGroup = true },
                                focusRequester = gWrap.requester(all.size),
                                normal = Color.Transparent, zoom = 1f) { _ ->
                                Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Ico(R.drawable.lc_plus, 16.dp, C.Muted); T("Nova grupa", 14.sp, C.Muted)
                                }
                            }
                        }
                    }
                    LaunchedEffect(Unit) { try { firstGroup.requestFocus() } catch (_: Exception) {} }
                }
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(C.Line))
        Box(Modifier.weight(1f).fillMaxHeight()) {
            val g = group
            if (g == null) Message("Izaberi grupu", "Meni ili dugi OK na grupi otvara opcije")
            else key(g.id, rev) { ChannelList(p, ovr, g, nav, gate, s = groupsState) { rev++ } }
        }
    }

    groupMenu?.let { g ->
        GroupOptions("live", g, ovr, (groupsState as? Load.Ok)?.value ?: emptyList(), onDismiss = { groupMenu = null }) { rev++; groupMenu = null }
    }
    if (newGroup) TextDialog("Nova grupa", "Naziv (npr. Dokumentarni, Sport, Bosna)", onDismiss = { newGroup = false }) { name ->
        if (name.isNotBlank()) ovr.addGroup(name)
        newGroup = false; rev++
    }
    PinGateDialog(gate)
}

/** Rename, move, hide, lock or delete a group. Used by TV and the group manager. */
@Composable
fun GroupOptions(kind: String, g: Category, ovr: Ovr, current: List<Category>, onDismiss: () -> Unit, onChanged: () -> Unit) {
    var rename by remember { mutableStateOf(false) }
    var setup by remember { mutableStateOf(false) }
    val custom = g.id.startsWith("my_")
    if (rename) { TextDialog("Preimenuj grupu", "Novi naziv", g.name, onDismiss = { rename = false }) { n -> if (n.isNotBlank()) ovr.renameGroup(kind, g.id, n); rename = false; onChanged() }; return }
    if (setup) { PinSetupDialog(onDismiss = { setup = false }) { ovr.toggleLocked(kind, g.id); setup = false; onChanged() }; return }
    Popup(g.name, onDismiss) {
        OptionRow("Pomjeri gore", R.drawable.lc_arrow_up) { ovr.moveGroup(kind, current, g.id, -1); onChanged() }
        OptionRow("Pomjeri dolje", R.drawable.lc_arrow_down) { ovr.moveGroup(kind, current, g.id, 1); onChanged() }
        OptionRow("Preimenuj", R.drawable.lc_pencil) { rename = true }
        OptionRow(if (ovr.isHidden(kind, g.id)) "Prikaži grupu" else "Sakrij grupu", R.drawable.lc_eye_off) { ovr.toggleHidden(kind, g.id); onChanged() }
        OptionRow(if (ovr.isLocked(kind, g.id)) "Otključaj (ukloni PIN)" else "Zaključaj PIN-om", R.drawable.lc_lock,
            sub = "Za kanale koje djeca ne smiju gledati") {
            if (!Lock.hasPin()) setup = true else { ovr.toggleLocked(kind, g.id); onChanged() }
        }
        if (custom) OptionRow("Obriši grupu", R.drawable.lc_trash_2, color = C.Danger) { ovr.deleteGroup(g.id); onChanged() }
    }
}

@Composable
private fun ChannelList(p: Playlist, ovr: Ovr, g: Category, nav: Nav, gate: PinGate, s: Load<List<Category>>, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    var favRev by remember { mutableIntStateOf(0) }
    val isFav = g.id == FAV_GROUP
    val state by load(p.id, g.id, favRev) {
        if (isFav) Store.favorites(ctx, p.id)
        else ovr.channels(g.id, if (g.id.startsWith("my_")) emptyList() else Repo.channels(p, g.id))
    }
    var menu by remember { mutableStateOf<Channel?>(null) }
    val first = remember { FocusRequester() }
    var focusIndex by remember { mutableIntStateOf(0) }
    val cWrap = remember { Wrap() }
    val cState = rememberLazyListState()
    WrapEffect(cWrap, cState)

    Column(Modifier.fillMaxSize().padding(start = 22.dp, end = 30.dp, top = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            T(g.name, 20.sp, C.Text, FontWeight.SemiBold, modifier = Modifier.weight(1f))
            T("OK gledaj · Meni ili dugi OK = opcije", 12.sp, C.Muted)
        }
        when (val st = state) {
            is Load.Loading -> Loading("Učitavam kanale…")
            is Load.Err -> Message("Kanali se nisu učitali", st.message)
            is Load.Ok -> {
                val list = st.value
                if (list.isEmpty()) {
                    Message(if (isFav) "Nema favorita" else "Grupa je prazna",
                        if (isFav) "Na kanalu pritisni Meni ili drži OK › Dodaj u favorite" else "Kanale možeš prebaciti ovamo preko opcija kanala")
                } else {
                    val items = list.map { PlayItem(it.name, it.url, it.logo, "${it.num}", it) }
                    val favUrls = Store.favorites(ctx, p.id).map { it.url }.toSet()
                    LazyColumn(state = cState, verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 30.dp)) {
                        itemsIndexed(list, key = { i, ch -> "$i${ch.url}" }) { i, ch ->
                            val locked = ovr.isChLocked(ch)
                            Focusable(
                                modifier = Modifier.fillMaxWidth().height(60.dp).wrapKeys(i, list.size, cWrap),
                                onClick = {
                                    gate.run("c:${ch.url}", locked) {
                                        ovr.countWatch(ch)
                                        nav.push(Screen.Player(items, i, live = true))
                                    }
                                },
                                onMenu = { menu = ch },
                                onFocus = { focusIndex = i },
                                focusRequester = cWrap.requester(i, if (i == 0) first else null),
                                normal = C.Card,
                                zoom = 1.01f,
                            ) { f ->
                                Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    T("${ch.num}", 13.sp, C.Muted, modifier = Modifier.width(44.dp))
                                    ChannelLogo(ch.logo, ch.name)
                                    T(ch.name, 16.sp, if (f) C.Text else C.Soft, if (f) FontWeight.SemiBold else FontWeight.Normal,
                                        modifier = Modifier.weight(1f))
                                    qualityOf(ch.name)?.let { Chip(it, C.Card, C.Soft) }
                                    if (locked) Ico(R.drawable.lc_lock, 16.dp, C.Muted)
                                    if (ch.url in favUrls) T("★", 17.sp, C.Amber)
                                    if (f) T("⋮", 20.sp, C.Muted, FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                menu?.let { ch ->
                    ChannelOptions(p, ovr, g, ch, list, (s as? Load.Ok)?.value ?: emptyList(), onDismiss = { menu = null }) { reload ->
                        menu = null; favRev++; if (reload) onChanged()
                    }
                }
            }
        }
    }
}

/** Options of one channel: favourites, order, group, name, hide, lock. */
@Composable
fun ChannelOptions(p: Playlist, ovr: Ovr, g: Category, ch: Channel, list: List<Channel>, groups: List<Category>,
                   onDismiss: () -> Unit, onChanged: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val isFav = g.id == FAV_GROUP
    val fav = Store.isFavorite(ctx, p.id, ch)
    var mode by remember { mutableStateOf("menu") }
    when (mode) {
        "rename" -> { TextDialog("Preimenuj kanal", "Novi naziv", ch.name, onDismiss = { mode = "menu" }) { n -> if (n.isNotBlank()) ovr.renameChannel(ch, n); onChanged(true) }; return }
        "pin" -> { PinSetupDialog(onDismiss = { mode = "menu" }) { ovr.toggleChLocked(ch); onChanged(false) }; return }
        "newgroup" -> { TextDialog("Nova grupa", "Naziv nove grupe", onDismiss = { mode = "move" }) { n ->
            if (n.isNotBlank()) { val ng = ovr.addGroup(n); ovr.moveChannelTo(ch, ng.id) }; onChanged(true) }; return }
        "move" -> {
            Popup("Premjesti u grupu", onDismiss = { mode = "menu" }) {
                OptionRow("Nova grupa…", R.drawable.lc_plus) { mode = "newgroup" }
                groups.filter { it.id != g.id && it.id != FAV_GROUP }.forEach { target ->
                    OptionRow(target.name, R.drawable.lc_layers) {
                        ovr.moveChannelTo(ch, target.id)
                        Toast.makeText(ctx, "Premješteno u ${target.name}", Toast.LENGTH_SHORT).show()
                        onChanged(true)
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
            onChanged(false)
        }
        if (Lock.simple) return@Popup
        OptionRow("Pomjeri gore", R.drawable.lc_arrow_up) {
            if (isFav) Store.moveFavorite(ctx, p.id, ch, -1) else ovr.moveChannel(g.id, list, ch, -1); onChanged(false)
        }
        OptionRow("Pomjeri dolje", R.drawable.lc_arrow_down) {
            if (isFav) Store.moveFavorite(ctx, p.id, ch, 1) else ovr.moveChannel(g.id, list, ch, 1); onChanged(false)
        }
        if (!isFav) {
            OptionRow("Premjesti u drugu grupu", R.drawable.lc_layers) { mode = "move" }
            OptionRow("Preimenuj kanal", R.drawable.lc_pencil) { mode = "rename" }
            OptionRow(if (ovr.isChHidden(ch)) "Prikaži kanal" else "Sakrij kanal", R.drawable.lc_eye_off) { ovr.toggleChHidden(ch); onChanged(false) }
            OptionRow(if (ovr.isChLocked(ch)) "Otključaj kanal" else "Zaključaj PIN-om", R.drawable.lc_lock) {
                if (!Lock.hasPin()) mode = "pin" else { ovr.toggleChLocked(ch); onChanged(false) }
            }
        }
    }
}

@Composable
fun ChannelLogo(url: String?, name: String) {
    Box(Modifier.width(62.dp).height(38.dp).clip(RoundedCornerShape(8.dp)).background(if (C.IsLight) Color(0xFFE6E8EC) else Color(0xFF1C2230)),
        contentAlignment = Alignment.Center) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp))
        } else {
            T(name.filter { it.isLetterOrDigit() }.take(3).uppercase(), 11.sp, C.Muted, FontWeight.Bold)
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
    val st by load(p.id, kind, rev) {
        val raw = when (kind) { "live" -> Repo.liveGroups(p); "vod" -> Repo.vodGroups(p); else -> Repo.seriesGroups(p) }
        ovr.groups(kind, raw, all = true)
    }
    var menu by remember { mutableStateOf<Category?>(null) }
    var newGroup by remember { mutableStateOf(false) }
    var reset by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        T("Grupe i kanali · ${p.name}", 22.sp, C.Text, FontWeight.Bold)
        T("OK na grupi otvara opcije: redoslijed, naziv, sakrivanje, PIN. Kanale uređuješ u TV-u (Meni na kanalu).", 13.sp, C.Muted, maxLines = 2)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Segmented(listOf("TV", "Filmovi", "Serije"), tab) { tab = it }
            if (tab == 0) Button("Nova grupa", R.drawable.lc_plus) { newGroup = true }
            Button("Vrati sve izmjene", R.drawable.lc_rotate_ccw) { reset = true }
        }
        when (val s = st) {
            is Load.Loading -> Loading()
            is Load.Err -> Message("Greška", s.message)
            is Load.Ok -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 30.dp)) {
                itemsIndexed(s.value, key = { i, g -> "$i${g.id}" }) { _, g ->
                    val hidden = ovr.isHidden(kind, g.id); val locked = ovr.isLocked(kind, g.id)
                    Focusable(modifier = Modifier.fillMaxWidth().height(52.dp), onClick = { menu = g }, onMenu = { menu = g },
                        normal = C.Card, zoom = 1.01f) { _ ->
                        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
    menu?.let { g -> GroupOptions(kind, g, ovr, (st as? Load.Ok)?.value ?: emptyList(), onDismiss = { menu = null }) { rev++ } }
    if (newGroup) TextDialog("Nova grupa", "Naziv", onDismiss = { newGroup = false }) { n -> if (n.isNotBlank()) ovr.addGroup(n); newGroup = false; rev++ }
    if (reset) Popup("Vratiti sve izmjene?", onDismiss = { reset = false }) {
        T("Briše tvoj redoslijed, nazive, sakrivene i zaključane grupe i kanale, i tvoje grupe za ovu playlistu.", 14.sp, C.Soft, maxLines = 3)
        OptionRow("Da, vrati kako je bilo", R.drawable.lc_rotate_ccw, color = C.Danger) { ovr.resetAll(); reset = false; rev++; nav.playlistsChanged() }
        OptionRow("Odustani", R.drawable.lc_x) { reset = false }
    }
}
