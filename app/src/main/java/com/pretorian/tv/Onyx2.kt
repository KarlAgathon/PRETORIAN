package com.pretorian.tv

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/* ============================================================== small pieces of the new look */

/** Glass panel: a see-through box with a thin line around it. */
fun Modifier.glass(r: Dp = 18.dp): Modifier = when (Look.theme) {
    "liquid" -> this.clip(Rnd(r)).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x14FFFFFF))))
        .border(1.dp, androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0x66FFFFFF), Color(0x1AFFFFFF))), Rnd(r))
    "classic" -> this.clip(Rnd(r)).background(C.Tile)
    "reddawn" -> this.clip(Rnd(r)).background(C.Tile).border(1.dp, C.Line, Rnd(r))
    "papir" -> this.clip(Rnd(r)).background(Color.White).border(1.dp, C.Line, Rnd(r))
    // Settings › Appearance › Transparency: 0 % = fuller panels, 50 % = almost only the frame
    else -> this.clip(Rnd(r)).background((100 - Look.opacity).coerceIn(0, 50).let { t -> if (C.IsLight) Color.Black.copy(alpha = 0.09f - t * 0.0015f) else Color.White.copy(alpha = 0.12f - t * 0.002f) }).border(1.dp, C.Line, Rnd(r))
}

/** Small spaced capitals above a block ("MOJE", "KATEGORIJE PROVIDERA"). */
@Composable
fun Kicker(t: String, color: Color = C.Muted, size: TextUnit = 10.sp, modifier: Modifier = Modifier) {
    Text(tr(t).uppercase(), style = TextStyle(fontFamily = Inter, fontSize = size, color = color, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp),
        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = modifier)
}

/** What is on now, per channel, kept until the show ends (asked only for the rows you see, three at a time). */
object NowCache {
    private val m = HashMap<String, EpgItem>()
    private val none = HashMap<String, Long>()
    val gate = Semaphore(3)
    fun get(url: String): EpgItem? = synchronized(m) { m[url]?.takeIf { it.end > System.currentTimeMillis() } }
    fun put(url: String, e: EpgItem?) = synchronized(m) { if (e != null) m[url] = e else none[url] = System.currentTimeMillis() }
    fun empty(url: String) = synchronized(m) { (none[url] ?: 0L) > System.currentTimeMillis() - 10 * 60_000L }
    fun drop(url: String) = synchronized(m) { m.remove(url); none.remove(url); Unit }
    fun clear() = synchronized(m) { m.clear(); none.clear() }
}

@Composable
fun nowOf(ch: Channel, wait: Long = 400): EpgItem? {
    val ctx = LocalContext.current
    val v by produceState(NowCache.get(ch.url), ch.url, EpgShift.rev) {
        if (value != null || NowCache.empty(ch.url)) return@produceState
        delay(wait)   // while you scroll fast nothing is asked
        val p = Store.byId(ctx, ch.pid) ?: return@produceState
        val now = System.currentTimeMillis()
        val e = NowCache.gate.withPermit { try { Repo.epg(p, ch, 3) } catch (e: Exception) { emptyList() } }.firstOrNull { it.start <= now && it.end > now }
        NowCache.put(ch.url, e); value = e
    }
    return v
}

/** what is on now and the next shows of a channel (for the wide channel list); asked only after a short pause while scrolling */
object NextCache { val m = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<EpgItem>>>() }
@Composable
fun ThinBar(f: Float, color: Color, modifier: Modifier = Modifier, h: Dp = 2.dp) {
    Box(modifier.height(h).clip(Pill).background(C.Line)) { Box(Modifier.fillMaxWidth(f.coerceIn(0.02f, 1f)).fillMaxHeight().background(color)) }
}

/* ============================================================== TV */
/** Which playlists are unfolded in TV (only for this run of the app; with several playlists all start folded). */
object TvFold {
    val open = HashSet<String>()
    fun toggle(id: String) { if (!open.remove(id)) open.add(id) }
}

@Composable
fun OnyxSearch(nav: Nav) {
    val ctx = LocalContext.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    // back from a film, series or channel: the same search, the same column, the focus on the results
    val back = remember { SearchMem.q.isNotBlank() }
    var q by remember { mutableStateOf(SearchMem.q) }
    var query by remember { mutableStateOf(SearchMem.q.trim()) }
    var pick by remember { mutableStateOf<Hit?>(null) }
    val fr = remember { FocusRequester() }
    val firstFr = remember { FocusRequester() }
    var jump by remember { mutableIntStateOf(if (back) 1 else 0) }
    LaunchedEffect(q) { SearchMem.q = q; delay(250); query = q.trim() }
    val voice = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { q = it; query = it.trim(); SearchHistory.add(it); jump++ }
    }
    // what you searched for stays in "Recent searches" (also when you leave with a result open)
    val lastQuery = androidx.compose.runtime.rememberUpdatedState(query)
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { SearchHistory.add(lastQuery.value) } }
    val chIdx by load(Unit) { SearchIndex.channels(ctx) }
    val vodIdx by load(Unit) { SearchIndex.movies(ctx) }
    val serIdx by load(Unit) { SearchIndex.shows(ctx) }
    @Composable
    fun hitsOf(idx: Load<List<SearchIndex.Item>>, max: Int): State<List<Hit>> = produceState(emptyList<Hit>(), query, idx) {
        // results from the first letter (approved)
        value = if (query.isEmpty()) emptyList() else (idx as? Load.Ok)?.value?.let { SearchIndex.find(it, query, max) } ?: emptyList()
    }
    val chHits by hitsOf(chIdx, 30)
    val vodHits by hitsOf(vodIdx, 30)
    val serHits by hitsOf(serIdx, 30)
    val busy = chIdx is Load.Loading || vodIdx is Load.Loading || serIdx is Load.Loading
    // focus moves to the results only right after Search/Done or a recent search, never while typing
    val anyNow = androidx.compose.runtime.rememberUpdatedState(chHits.isNotEmpty() || vodHits.isNotEmpty() || serHits.isNotEmpty())
    val qNow = androidx.compose.runtime.rememberUpdatedState(q)
    LaunchedEffect(jump) {
        if (jump == 0) return@LaunchedEffect
        val at = qNow.value
        repeat(50) { if (qNow.value != at) return@LaunchedEffect; if (anyNow.value) { firstFr.tryFocus(); return@LaunchedEffect }; delay(100) }
    }
    val only = SearchOnly.only
    var favAsk by remember { mutableStateOf<Channel?>(null) }
    fun longOk(h: Hit) {
        // long OK on a channel: into Favourites at once; only removing asks
        if (h is Hit.Ch) {
            if (Favs.inAny(ctx, h.ch)) favAsk = h.ch
            else { Favs.flip(ctx, h.ch); android.widget.Toast.makeText(ctx, tr("Dodano u favorite"), android.widget.Toast.LENGTH_SHORT).show() }
        } else pick = h
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // the big search field, with the microphone
        Row(Modifier.fillMaxWidth().padding(top = 6.dp).height(52.dp).clip(Pill).background(C.Rail).border(1.dp, C.Line, Pill),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.padding(start = 18.dp)) { Ico(R.drawable.lc_search, 18.dp, C.Text) }
            androidx.compose.foundation.text.BasicTextField(q, { q = it }, singleLine = true,
                textStyle = TextStyle(fontFamily = Inter, fontSize = 18.sp, color = C.Text, fontWeight = FontWeight.SemiBold),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(C.RedText),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search, autoCorrectEnabled = false),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { query = q.trim(); SearchHistory.add(q); keyboard?.hide(); jump++ }),
                modifier = Modifier.weight(1f).padding(horizontal = 14.dp).focusRequesterSafe(fr),
                decorationBox = { inner -> Box { if (q.isEmpty()) T("TV kanal, film, serija ili tim", 16.sp, C.Muted); inner() } })
            // clear the search
            if (q.isNotEmpty()) Focusable(onClick = { SearchHistory.add(q); q = ""; query = ""; SearchMem.clear(); try { fr.requestFocus() } catch (_: Exception) {} }, shape = Pill, normal = Color.Transparent, focused = C.CardHi,
                zoom = 1f, modifier = Modifier.height(40.dp)) { _ ->
                Row(Modifier.align(Alignment.Center).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Ico(R.drawable.lc_x, 16.dp, C.Muted); T("Obriši", 13.sp, C.Muted)
                }
            }
            Focusable(onClick = {
                try { voice.launch(android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, Look.lang)) }
                catch (e: Exception) { Toast.makeText(ctx, tr("Glasovna pretraga nije dostupna na ovom uređaju"), Toast.LENGTH_LONG).show() }
            }, shape = Pill, normal = Color.Transparent, focused = Color(0x1F000000), zoom = 1f, modifier = Modifier.padding(end = 8.dp).height(40.dp)) { _ ->
                Row(Modifier.align(Alignment.Center).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Ico(R.drawable.lc_mic, 16.dp, C.Muted); T("glasom", 13.sp, C.Muted)
                }
            }
        }
        if (query.isEmpty()) {
            SearchRecent { h -> q = h; query = h; jump++ }
            return@Column
        }
        // sport: matches already loaded in Sport (live scores) and sport broadcasts in the TV guide
        val sportHits by produceState(emptyList<SportEvent>(), query) {
            val nq = Sports.norm(query)
            value = if (nq.isEmpty()) emptyList() else withContext(Dispatchers.Default) {
                Sports.lastCombined.values.flatten().distinctBy { it.id }.filter { e ->
                    Sports.norm(listOfNotNull(e.home?.name, e.away?.name, e.title, e.league).joinToString(" ")).let { t -> nq.split(' ').all { w -> t.contains(w) } }
                }.sortedWith(compareBy({ !it.live }, { it.start })).take(30)
            }
        }
        val tvList = chHits
        val movies = vodHits
        val shows = serHits
        val setHits = remember(query, only) { if (only == null || only == "settings") SettingsSearchIndex.find(query) else emptyList() }
        if (!busy && tvList.isEmpty() && movies.isEmpty() && shows.isEmpty() && sportHits.isEmpty() && setHits.isEmpty()) { T("Ništa nije pronađeno", 15.sp, C.Soft); return@Column }
        // New Dawn: filters with counts on the left, one list of results on the right
        DawnSearchResults(query, if (only == null || only == "tv") tvList else emptyList(), if (only == null || only == "vod") movies else emptyList(),
            if (only == null || only == "series") shows else emptyList(), if (only == null) sportHits else emptyList(), setHits, firstFr,
            onHit = { h -> when (h) { is Hit.Ch -> playChannels(nav, listOf(h.ch), 0); is Hit.Movie -> nav.push(Screen.MovieDetail(h.v)); is Hit.Show -> nav.push(Screen.SeriesDetail(h.s)); else -> pick = h } },
            onMenu = { longOk(it) },
            onSport = { e -> val ch = (e.channels + e.ai + e.maybe).firstOrNull(); if (ch != null) playMatch(nav, e, ch) else nav.push(Screen.Sports) },
            onSetting = { h -> SettingsNav.section = h.id; nav.push(Screen.Settings) },
            modifier = Modifier.fillMaxWidth().weight(1f).padding(bottom = 8.dp), only = only)
        if (busy) T(tr("učitavam…"), 11.sp, C.Muted)
    }
    pick?.let { h -> HitActions(h, nav) { pick = null } }
    favAsk?.let { c ->
        Popup(c.name, { favAsk = null }, width = 460.dp) {
            T("Ukloniti kanal iz favorita?", 15.sp, C.Soft)
            OptionRow("Da, ukloni", R.drawable.lc_star, color = C.Danger) {
                Favs.flip(ctx, c); android.widget.Toast.makeText(ctx, tr("Uklonjeno iz favorita"), android.widget.Toast.LENGTH_SHORT).show(); favAsk = null
            }
            OptionRow("Ne", R.drawable.lc_x) { favAsk = null }
        }
    }
    LaunchedEffect(Unit) { if (!back) fr.tryFocus() }
}

/** focusRequester that does not crash when the item is not on screen yet. */
fun Modifier.focusRequesterSafe(fr: FocusRequester): Modifier = this.focusRequester(fr)

/* ============================================================== film and series pages */

object Welcome { var tab by mutableIntStateOf(-1) }



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
    Box(Modifier.size(18.dp).clip(Rnd(5.dp)).background(if (on) C.Green else Color.Transparent)
        .border(1.5.dp, if (on) C.Green else if (focused) C.OnRed.copy(alpha = 0.6f) else C.Muted, Rnd(5.dp)), contentAlignment = Alignment.Center) {
        if (on) Ico(R.drawable.lc_check, 12.dp, Color.White)
    }
}

/** Settings › Playlists › Groups and channels, like a file manager (FIX 5): groups left, the opened group's channels right.
 *  OK on a group opens its channels, ◀ goes back. Long OK = small menu. "Pomjeri" lifts the row: ▲▼ move it, OK puts it down, Back cancels. */
@Composable
fun BulkEditor(nav: Nav) {
    val ctx = LocalContext.current
    val p = remember { PlEdit.id?.let { id -> Store.playlists(ctx).firstOrNull { it.id == id } } ?: Store.active(ctx) } ?: run { NoPlaylist(nav); return }
    val ovr = remember(p.id) { Ovr(ctx, p.id) }
    val scope = rememberCoroutineScope()
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
    val chSt by load(p.id, cur?.id, rev, keep = true) {
        val g = cur ?: return@load emptyList<Channel>()
        if (g.id.startsWith("my_")) ovr.channels(g.id, emptyList(), all = true) else ovr.channels(g.id, Repo.channels(p, g.id), all = true)
    }
    val chans = if (kind == "live" && cur != null) (chSt as? Load.Ok)?.value ?: emptyList() else emptyList()
    var menuG by remember { mutableStateOf<Category?>(null) }
    var menuC by remember { mutableStateOf<Channel?>(null) }
    var renameC by remember { mutableStateOf<Channel?>(null) }
    var renameG by remember { mutableStateOf<Category?>(null) }
    var moveTo by remember { mutableStateOf<Channel?>(null) }
    var askAll by remember { mutableStateOf<String?>(null) }      // "g" or "c": hide all, asked first
    var pinThen by remember { mutableStateOf<(() -> Unit)?>(null) }
    // carrying a row: its id (group id or channel url), the column, the order while it moves
    var moving by remember { mutableStateOf<String?>(null) }
    var movingCol by remember { mutableStateOf("") }
    var work by remember { mutableStateOf<List<String>>(emptyList()) }
    var refocus by remember { mutableStateOf<String?>(null) }
    val gState = androidx.compose.foundation.lazy.rememberLazyListState()
    val cState = androidx.compose.foundation.lazy.rememberLazyListState()
    val reqs = remember { HashMap<String, FocusRequester>() }
    fun req(id: String) = reqs.getOrPut(id) { FocusRequester() }
    LaunchedEffect(tab) { cur = null; moving = null }
    fun done() { rev++; changed[0] = true }
    fun locked(run: () -> Unit) { if (!Lock.hasPin()) pinThen = run else run() }
    val gIds = groups.map { it.id }; val cIds = chans.map { it.url }
    val shownG = if (moving != null && movingCol == "g") work.mapNotNull { id -> groups.firstOrNull { it.id == id } } else groups
    val shownC = if (moving != null && movingCol == "c") work.mapNotNull { u -> chans.firstOrNull { it.url == u } } else chans
    fun startMove(col: String, id: String) { movingCol = col; work = if (col == "g") gIds else cIds; moving = id; refocus = id }
    fun place(col: String, id: String, top: Boolean) {
        val l = (if (col == "g") gIds else cIds).toMutableList(); if (!l.remove(id)) return
        if (top) l.add(0, id) else l.add(id)
        if (col == "g") ovr.setGroupOrder(kind, l) else cur?.let { ovr.setChannelOrder(it.id, l) }
        done(); refocus = id
    }
    fun step(d: Int) {
        val id = moving ?: return
        val l = work.toMutableList(); val i = l.indexOf(id); val j = i + d
        if (i < 0 || j !in l.indices) return
        l.removeAt(i); l.add(j, id); work = l
        val stt = if (movingCol == "g") gState else cState
        scope.launch {
            val vis = stt.layoutInfo.visibleItemsInfo
            if (vis.isEmpty() || j <= vis.first().index || j >= vis.last().index) stt.scrollToItem((j - 3).coerceAtLeast(0))
        }
    }
    fun finish(save: Boolean) {
        val id = moving ?: return
        if (save) { if (movingCol == "g") ovr.setGroupOrder(kind, work) else cur?.let { ovr.setChannelOrder(it.id, work) }; done() }
        moving = null; refocus = id
    }
    LaunchedEffect(refocus, shownG.size, shownC.size) { val id = refocus ?: return@LaunchedEffect; delay(60); reqs[id]?.tryFocus(4); refocus = null }
    // keys while a row is carried: ▲▼ move, OK puts down, Back cancels, ◀▶ do nothing
    val armed = remember { booleanArrayOf(false) }
    LaunchedEffect(moving) { armed[0] = false }
    fun Modifier.carry(id: String) = onPreviewKeyEvent { e ->
        if (moving != id) return@onPreviewKeyEvent false
        val down = e.type == KeyEventType.KeyDown
        when (e.key) {
            Key.DirectionUp -> { if (down) step(-1); true }
            Key.DirectionDown -> { if (down) step(1); true }
            Key.DirectionLeft, Key.DirectionRight -> true
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { if (down) armed[0] = true else if (armed[0]) finish(true); true }
            Key.Back, Key.Escape -> { if (!down) finish(false); true }
            else -> false
        }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T(tr("Playliste") + " › " + p.name, 11.sp, C.Muted)
                T("Grupe i kanali", 20.sp, C.Text, FontWeight.Bold)
            }
            Segmented(listOf("TV", "Filmovi", "Serije"), tab) { if (moving == null) tab = it }
        }
        T(if (moving != null) "▲ ▼ pomjeri · OK spusti · Back odustani" else "OK otvara · drži OK za opcije", 12.sp, C.Muted)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                T(trf("Grupe · %s", groups.size), 13.sp, C.Soft, FontWeight.Bold)
                if (st is Load.Loading && groups.isEmpty()) Loading("") else LazyColumn(state = gState, verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
                    if (groups.isEmpty()) item { T("Nema grupa", 13.sp, C.Muted) }
                    itemsIndexed(shownG, key = { _, g -> g.id }) { i, g ->
                        val hidden = ovr.hiddenNow(kind, g)
                        EditRow(i + 1, g.name, hidden, ovr.isLocked(kind, g.id), byParental = hidden && !ovr.isHidden(kind, g.id),
                            open = kind == "live" && cur?.id == g.id, carried = moving == g.id, chevron = kind == "live",
                            modifier = Modifier.focusRequester(req(g.id)).carry(g.id)
                                .onPreviewKeyEvent { e -> moving == null && kind == "live" && e.key == Key.DirectionRight && e.type == KeyEventType.KeyDown && cur?.id != g.id },
                            onLong = { if (moving == null) menuG = g }) {
                            if (moving == null && kind == "live") { cur = g; refocus = "first-ch" }
                        }
                    }
                }
            }
            if (kind == "live") Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                T(cur?.let { trf("Kanali · %s", it.name) } ?: tr("Kanali"), 13.sp, C.Soft, FontWeight.Bold)
                when {
                    cur == null -> T("OK na grupu prikazuje njene kanale", 13.sp, C.Muted)
                    chSt is Load.Loading && chans.isEmpty() -> Loading("")
                    else -> LazyColumn(state = cState, verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
                        if (chans.isEmpty()) item { T("Nema kanala", 13.sp, C.Muted) }
                        itemsIndexed(shownC, key = { _, c -> c.url }) { i, c ->
                            EditRow(i + 1, ovr.chName(c), ovr.isChHidden(c), ovr.isChLocked(c), carried = moving == c.url,
                                modifier = Modifier.focusRequester(if (i == 0 && moving == null) req("first-ch").also { reqs[c.url] = it } else req(c.url)).carry(c.url)
                                    .onPreviewKeyEvent { e ->
                                        // ◀ goes back to the group it belongs to
                                        if (moving == null && e.key == Key.DirectionLeft && e.type == KeyEventType.KeyDown) { cur?.let { reqs[it.id]?.requestFocus() }; true } else false
                                    },
                                onLong = { if (moving == null) menuC = c }) { if (moving == null) menuC = c }
                        }
                    }
                }
            }
        }
    }
    menuG?.let { g ->
        val hidden = ovr.hiddenNow(kind, g); val lk = ovr.isLocked(kind, g.id)
        Popup(g.name, { menuG = null; refocus = g.id }) {
            OptionRow("Pomjeri", R.drawable.lc_grip_vertical) { menuG = null; startMove("g", g.id) }
            OptionRow("Na vrh", R.drawable.lc_chevrons_up) { menuG = null; place("g", g.id, true) }
            OptionRow("Na dno", R.drawable.lc_chevrons_down) { menuG = null; place("g", g.id, false) }
            OptionRow(if (hidden) "Prikaži" else "Sakrij", if (hidden) R.drawable.lc_eye else R.drawable.lc_eye_off) { ovr.setHidden(kind, listOf(g.id), !hidden); menuG = null; done(); refocus = g.id }
            OptionRow(if (lk) "Otključaj" else "Zaključaj PIN-om", if (lk) R.drawable.lc_lock_open else R.drawable.lc_lock) { menuG = null; locked { ovr.toggleLocked(kind, g.id); done(); refocus = g.id } }
            OptionRow("Preimenuj", R.drawable.lc_pencil) { menuG = null; renameG = g }
            OptionRow("Sakrij sve", R.drawable.lc_eye_off) { menuG = null; askAll = "g" }
            OptionRow("Prikaži sve", R.drawable.lc_eye) { ovr.setHidden(kind, gIds, false); menuG = null; done(); refocus = g.id }
            if (g.id.startsWith("my_")) OptionRow("Obriši grupu", R.drawable.lc_trash_2, color = C.Danger) { ovr.deleteGroup(g.id); menuG = null; if (cur?.id == g.id) cur = null; done() }
        }
    }
    menuC?.let { c ->
        val hidden = ovr.isChHidden(c); val lk = ovr.isChLocked(c)
        Popup(ovr.chName(c), { menuC = null; refocus = c.url }) {
            OptionRow("Pomjeri", R.drawable.lc_grip_vertical) { menuC = null; startMove("c", c.url) }
            OptionRow("Na vrh", R.drawable.lc_chevrons_up) { menuC = null; place("c", c.url, true) }
            OptionRow("Na dno", R.drawable.lc_chevrons_down) { menuC = null; place("c", c.url, false) }
            OptionRow(if (hidden) "Prikaži" else "Sakrij", if (hidden) R.drawable.lc_eye else R.drawable.lc_eye_off) { ovr.toggleChHidden(c); menuC = null; done(); refocus = c.url }
            OptionRow(if (lk) "Otključaj" else "Zaključaj PIN-om", if (lk) R.drawable.lc_lock_open else R.drawable.lc_lock) { menuC = null; locked { ovr.toggleChLocked(c); done(); refocus = c.url } }
            OptionRow("Preimenuj", R.drawable.lc_pencil) { menuC = null; renameC = c }
            OptionRow("Premjesti u grupu", R.drawable.lc_layers) { menuC = null; moveTo = c }
            OptionRow("Sakrij sve", R.drawable.lc_eye_off) { menuC = null; askAll = "c" }
            OptionRow("Prikaži sve", R.drawable.lc_eye) { chans.forEach { if (ovr.isChHidden(it)) ovr.toggleChHidden(it) }; menuC = null; done(); refocus = c.url }
        }
    }
    askAll?.let { col ->
        Popup(if (col == "g") "Sakriti sve grupe?" else "Sakriti sve kanale ove grupe?", { askAll = null }) {
            OptionRow("Odustani", R.drawable.lc_x) { askAll = null }
            OptionRow("Sakrij sve", R.drawable.lc_eye_off) {
                if (col == "g") ovr.setHidden(kind, gIds, true) else chans.forEach { if (!ovr.isChHidden(it)) ovr.toggleChHidden(it) }
                askAll = null; done()
            }
        }
    }
    pinThen?.let { run -> PinSetupDialog(onDismiss = { pinThen = null }) { pinThen = null; run() } }
    moveTo?.let { c ->
        Popup("Premjesti u grupu", { moveTo = null }) {
            groups.filter { it.id != cur?.id }.forEach { tg ->
                OptionRow(tg.name, R.drawable.lc_layers) {
                    ovr.moveChannelTo(c, tg.id)
                    Toast.makeText(ctx, trf("Premješteno u %s", tg.name), Toast.LENGTH_SHORT).show()
                    moveTo = null; done()
                }
            }
        }
    }
    renameC?.let { c -> TextDialog("Preimenuj kanal", "Novi naziv", ovr.chName(c), onDismiss = { renameC = null }) { v -> if (v.isNotBlank()) ovr.renameChannel(c, v); renameC = null; done(); refocus = c.url } }
    renameG?.let { g -> TextDialog("Preimenuj grupu", "Novi naziv", g.name, onDismiss = { renameG = null }) { v -> if (v.isNotBlank()) ovr.renameGroup(kind, g.id, v); renameG = null; done(); refocus = g.id } }
}

/** One row of the group/channel editor: number, name, small state icons; a carried row is lifted with ☰ and an outline. */
@Composable
private fun EditRow(n: Int, name: String, hidden: Boolean, locked: Boolean, byParental: Boolean = false, open: Boolean = false, carried: Boolean = false,
                    chevron: Boolean = false, modifier: Modifier, onLong: () -> Unit, onClick: () -> Unit) {
    Focusable(onClick = onClick, onMenu = onLong, shape = Rnd(12.dp), normal = if (carried) C.CardHi else if (open) C.Card else Color.Transparent,
        focused = if (carried) C.CardHi else Silver.Focus, zoom = 1f, borderColor = if (carried) Color.White else null,
        focusBorderColor = if (carried) Color.White else null, modifier = modifier.fillMaxWidth().height(50.dp)) { _ ->
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (carried) Ico(R.drawable.lc_grip_vertical, 16.dp, Color.White)
            T("$n", 12.sp, C.Muted, modifier = Modifier.width(30.dp))
            T(name, 15.sp, if (hidden) C.Muted else C.Text, FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (byParental) Ico(R.drawable.lc_shield_check, 14.dp, C.Muted)
            if (hidden) Ico(R.drawable.lc_eye_off, 14.dp, C.Muted)
            if (locked) Ico(R.drawable.lc_lock, 14.dp, C.Soft)
            if (chevron) Ico(R.drawable.lc_chevron_right, 14.dp, if (open) C.Text else C.Muted)
        }
    }
}


/** The last search, kept while you look at a result (cleared with "Obriši" or when you go to another part of the app). */
object SearchMem {
    var q = ""
    var open: String? = null
    fun clear() { q = "" }  // the last category stays remembered
}
