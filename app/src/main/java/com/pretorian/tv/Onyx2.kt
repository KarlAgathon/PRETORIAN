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

/** Settings › Playlists › Groups and channels: tick several, then move, hide, show or lock them together. */
@Composable
fun BulkEditor(nav: Nav) {
    val ctx = LocalContext.current
    val p = remember { PlEdit.id?.let { id -> Store.playlists(ctx).firstOrNull { it.id == id } } ?: Store.active(ctx) } ?: run { NoPlaylist(nav); return }
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
    // filter: all, only the shown ones, only the hidden ones (to see at once what you hid and edit it)
    var filter by remember { mutableIntStateOf(0) }
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
    val shownGroups = when (filter) { 1 -> groups.filter { !ovr.hiddenNow(kind, it) }; 2 -> groups.filter { ovr.hiddenNow(kind, it) }; else -> groups }
    val shownChans = when (filter) { 1 -> chans.filter { !ovr.isChHidden(it) }; 2 -> chans.filter { ovr.isChHidden(it) }; else -> chans }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T(tr("Playliste") + " › " + p.name, 10.sp, C.Muted)
                T("Uređivanje grupa i kanala", 19.sp, C.Text, FontWeight.Bold)
            }
            Segmented(listOf("Sve", "Prikazane", "Sakrivene"), filter) { filter = it; selG.clear(); selC.clear() }
            Spacer(Modifier.width(10.dp))
            Segmented(listOf("TV", "Filmovi", "Serije"), tab) { tab = it }
        }
        // what you can do with the ticked rows
        val n = if (selC.isNotEmpty()) selC.size else selG.size
        Row(Modifier.fillMaxWidth().clip(Rnd(14.dp)).background(C.Rail).border(1.dp, C.Line, Rnd(14.dp)).padding(horizontal = 10.dp, vertical = 7.dp),
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
                item { ToolBtn("Promijeni PIN", R.drawable.lc_key_round) { pinSetup = true } }
            }
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // groups
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    T(trf("Grupe · %s", shownGroups.size), 13.sp, C.Text, FontWeight.Bold, modifier = Modifier.weight(1f))
                    ToolBtn("Označi sve", R.drawable.lc_check) { selC.clear(); selG.clear(); selG.addAll(shownGroups.map { it.id }) }
                    ToolBtn("Sakrij sve", R.drawable.lc_eye_off) { ovr.setHidden(kind, groups.map { it.id }, true); done() }
                    ToolBtn("Prikaži sve", R.drawable.lc_eye) { ovr.setHidden(kind, groups.map { it.id }, false); done() }
                }
                if (st is Load.Loading && groups.isEmpty()) Loading("") else LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                    if (shownGroups.isEmpty()) item { T(if (filter == 2) "Nema sakrivenih grupa" else "Nema grupa", 12.sp, C.Muted) }
                    itemsIndexed(shownGroups, key = { _, g -> g.id }) { _, g ->
                        val i = groups.indexOf(g)
                        val on = g.id in selG
                        val hidden = ovr.hiddenNow(kind, g)
                        val byParental = hidden && !ovr.isHidden(kind, g.id)
                        Focusable(onClick = { if (on) selG.remove(g.id) else { selC.clear(); selG.add(g.id) } }, onFocus = { focusG = g }, onMenu = { menu = g },
                            shape = Rnd(12.dp), normal = if (on) C.Green.copy(alpha = 0.16f) else if (cur?.id == g.id) C.CardHi else C.Rail, focused = C.Red, zoom = 1.01f,
                            modifier = Modifier.fillMaxWidth().height(40.dp)) { f ->
                            Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CheckBox(on, f)
                                T("${i + 1}", 11.sp, if (f) C.OnRed else C.Muted, modifier = Modifier.width(22.dp))
                                T(g.name, 13.sp, if (f) C.OnRed else if (hidden) C.Muted else C.Text, FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                if (byParental) Ico(R.drawable.lc_shield_check, 13.dp, if (f) C.OnRed else C.Amber)
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
                        ToolBtn("Označi sve", R.drawable.lc_check) { selG.clear(); selC.clear(); selC.addAll(shownChans.map { it.url }) }
                        ToolBtn("Sakrij sve", R.drawable.lc_eye_off) { chans.forEach { if (!ovr.isChHidden(it)) ovr.toggleChHidden(it) }; done() }
                        ToolBtn("Prikaži sve", R.drawable.lc_eye) { chans.forEach { if (ovr.isChHidden(it)) ovr.toggleChHidden(it) }; done() }
                    }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                    if (cur != null && shownChans.isEmpty()) item { T(if (filter == 2) "Nema sakrivenih kanala" else "Nema kanala", 12.sp, C.Muted) }
                    itemsIndexed(shownChans, key = { j, c -> "$j${c.url}" }) { _, c ->
                        val i = chans.indexOf(c)
                        val on = c.url in selC
                        val hidden = ovr.isChHidden(c)
                        Focusable(onClick = { if (on) selC.remove(c.url) else { selG.clear(); selC.add(c.url) } }, shape = Rnd(12.dp),
                            normal = if (on) C.Green.copy(alpha = 0.16f) else C.Rail, focused = C.Red, zoom = 1.01f, modifier = Modifier.fillMaxWidth().height(40.dp)) { f ->
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


/** The last search, kept while you look at a result (cleared with "Obriši" or when you go to another part of the app). */
object SearchMem {
    var q = ""
    var open: String? = null
    fun clear() { q = "" }  // the last category stays remembered
}
