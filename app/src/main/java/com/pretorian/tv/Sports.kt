package com.pretorian.tv

import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.produceState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val AiColor = Color(0xFFA78BFA)

/** Rows scroll so the card you stand on starts at the left edge: no half cut card before it. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private val StartAligned = object : androidx.compose.foundation.gestures.BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        if (offset >= 0 && offset + size <= containerSize) 0f else offset
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun AlignStart(content: @Composable () -> Unit) =
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides StartAligned) { content() }

private fun watchRank(e: SportEvent) = when { e.channels.isNotEmpty() -> 0; e.ai.isNotEmpty() -> 1; e.maybe.isNotEmpty() -> 2; else -> 3 }

private fun hhmm(ms: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

/** Where you were in Sports: after the full screen Back returns to the same match, day and filters. */
object SportsMemory { var evId: String? = null; var day = 0; var sport = "all"; var mine = false; var view = "all"; var league: String? = null }

@Composable
fun SportsScreen(nav: Nav) {
    val ctx = LocalContext.current
    val p = remember { Store.active(ctx) }
    var day by remember { mutableIntStateOf(SportsMemory.day) }
    var sport by remember { mutableStateOf(SportsMemory.sport) }
    var league by remember { mutableStateOf(SportsMemory.league) }
    var mine by remember { mutableStateOf(SportsMemory.mine) }
    var tick by remember { mutableIntStateOf(0) }
    var updated by remember { mutableLongStateOf(0L) }
    var pick by remember { mutableStateOf<SportEvent?>(null) }
    var view by remember { mutableStateOf(SportsMemory.view) }   // all, live, done
    val memFr = remember { FocusRequester() }
    var q by remember { mutableStateOf("") }
    var searchDlg by remember { mutableStateOf(false) }
    var dayMenu by remember { mutableStateOf(false) }
    val memId = remember { SportsMemory.evId }
    val sc = rememberScrollState()
    val scope = rememberCoroutineScope()
    val events by load(day, tick, keep = true) { (if (day == UPCOMING) emptyList() else Sports.day(day)).also { updated = System.currentTimeMillis() } }
    val guide by load(p?.id) { Sports.guide(ctx, Store.actives(ctx)) }
    val heroFr = remember { FocusRequester() }
    val dayFr = remember { FocusRequester() }

    // live scores refresh every minute while you look at today
    LaunchedEffect(day) { while (day == 0) { delay((Prefs.str("sport_refresh", "1").toLongOrNull() ?: 1L) * 60_000L); tick++ } }

    val g = (guide as? Load.Ok)?.value
    // matching matches to your guide is heavy: it runs in the background, the screen keeps the old result meanwhile
    // only the sports chosen in Settings › Sport (football by default)
    val evs = (events as? Load.Ok)?.value?.let { l -> val en = Prefs.str("sport_enabled", "football").split(',').filter { it.isNotBlank() }; if (en.isEmpty()) l else l.filter { it.sport in en } }
    var matching by remember { mutableStateOf(true) }
    var tookMs by remember { mutableStateOf(-1L) }
    // channels are matched once per day and guide; live score refreshes only update the scores (no new search)
    val evKey = evs?.joinToString(",") { it.id }?.hashCode()
    val matched by produceState(Sports.lastCombined[day], evKey, g, day) {
        Sports.lastCombined[day]?.let { value = it }
        val list = evs
        if (list == null || g == null) { matching = list == null || g == null && guide !is Load.Err; if (value == null) value = list; return@produceState }
        if (value == null) value = list
        matching = true
        val t0 = System.currentTimeMillis()
        val r = try { withContext(Dispatchers.Default) { Sports.combine(list, g, day) } } catch (e: Exception) { list }
        tookMs = System.currentTimeMillis() - t0
        Sports.lastCombined[day] = r
        value = r
        matching = false
    }
    // AI (Gemini): for the most important matches without a channel, asks which of your channels show them
    val aiMap by produceState(emptyMap<String, List<Channel>>(), matched, g) {
        val m = matched; val gg = g
        if (!SportsAI.ready || m == null || gg == null) return@produceState
        val want = m.filter { it.channels.isEmpty() && !it.fromGuide && it.home != null && it.state != "post" }
            .sortedBy { Sports.priority(it) }.take(6)
        val out = HashMap<String, List<Channel>>()
        for (e in want) {
            val r = try { SportsAI.channelsFor(e, gg.all) } catch (ex: Exception) { break }
            if (r.isNotEmpty()) { out[e.id] = r; value = HashMap(out) }
        }
    }
    val combined: List<SportEvent>? = remember(matched, evs, aiMap) {
        val m = matched ?: return@remember evs
        val fresh = evs?.associateBy { it.id } ?: return@remember m
        m.map { e -> (fresh[e.id]?.let { f -> e.copy(state = f.state, clock = f.clock, home = f.home?.let { h -> e.home?.copy(score = h.score) ?: h },
            away = f.away?.let { a -> e.away?.copy(score = a.score) ?: a }) } ?: e).let { x -> aiMap[x.id]?.let { x.copy(ai = it) } ?: x } }
    }
    val guideFailed = guide is Load.Err
    val all: List<SportEvent>? = combined
    val shown = remember(all, sport, league, mine, view, q) {
        all?.filter { e ->
            (q.isBlank() || listOfNotNull(e.title, e.league, e.home?.name, e.away?.name).any { it.contains(q.trim(), ignoreCase = true) }) &&
            (sport == "all" || e.sport == sport) && (league == null || e.league == league) && (!mine || e.channels.isNotEmpty() || e.maybe.isNotEmpty() || e.ai.isNotEmpty()) &&
                (view == "all" || (view == "live" && e.live) || (view == "done" && e.state == "post"))
        } ?: emptyList()
    }
    // the big card: the most important match that is on now, else the next one
    // the big card is always a real match from the results, never something found only in a TV guide
    val hero = remember(shown, view) {
        val real = shown.filter { !it.fromGuide }
        fun watchable(e: SportEvent) = e.channels.isNotEmpty()
        if (view == "done") null else (real.filter { it.live && watchable(it) }.minByOrNull { Sports.priority(it) }
            ?: real.filter { it.state == "pre" && watchable(it) }.minWithOrNull(compareBy({ it.start }, { Sports.priority(it) }))
            ?: real.filter { it.live }.minByOrNull { Sports.priority(it) }
            ?: real.filter { it.state == "pre" }.minWithOrNull(compareBy({ Sports.priority(it) }, { it.start })))
    }
    val hiddenByMine = remember(all, sport, league, view, mine) {
        if (!mine) 0 else (all ?: emptyList()).count { e ->
            (sport == "all" || e.sport == sport) && (league == null || e.league == league) &&
                (view == "all" || (view == "live" && e.live) || (view == "done" && e.state == "post")) && e.channels.isEmpty() && e.maybe.isEmpty() && e.ai.isEmpty()
        }
    }
    var sel by remember { mutableStateOf<SportEvent?>(null) }
    // the match on top is the one you picked with OK (it stays when you come back from the full screen)
    var chosen by remember { mutableStateOf(SportsMemory.evId) }
    val top = remember(chosen, shown, hero) { chosen?.let { id -> shown.find { it.id == id } } ?: hero }
    val miniFr = remember { FocusRequester() }
    fun choose(ev: SportEvent) {
        chosen = ev.id; SportsMemory.evId = ev.id; SportsMemory.day = day; SportsMemory.sport = sport; SportsMemory.mine = mine; SportsMemory.view = view; SportsMemory.league = league
        scope.launch { sc.animateScrollTo(0); delay(120); miniFr.tryFocus() }
    }
    val sections = remember(shown) {
        val rest = shown
        fun s(l: List<SportEvent>) = l.sortedWith(compareBy({ watchRank(it) }, { it.start }))
        Triple(s(rest.filter { it.live }), s(rest.filter { it.state == "pre" }), rest.filter { it.state == "post" }.sortedBy { watchRank(it) })
    }
    val leagues = remember(all, sport) { (all ?: emptyList()).filter { sport == "all" || it.sport == sport }.map { it.league to it.leagueAbbr }.distinct().take(20) }
    val liveCount = remember(all) { all?.count { it.live } ?: 0 }

    fun play(ev: SportEvent, chs: List<Channel>, i: Int) {
        SportsMemory.evId = ev.id; SportsMemory.day = day; SportsMemory.sport = sport; SportsMemory.mine = mine; SportsMemory.view = view; SportsMemory.league = league
        Preview.stop(); TvMemory.restore = false
        nav.push(Screen.Player(chs.map { PlayItem(it.name, it.url, it.logo, ev.title, it) }, i, live = true))
    }
    fun open(ev: SportEvent) {
        if (ev.channels.size == 1 && ev.maybe.isEmpty() && ev.live) play(ev, ev.channels, 0) else pick = ev
    }

    Column(Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, top = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ---------------------------------------------------------------- top
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                T("Sport", 24.sp, C.Text, FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val live = liveCount
                    val date = if (day in -1..6) SimpleDateFormat("EEEE, d. MMMM", Locale(Look.lang)).format(Date(Sports.dayStart(day))) else ""
                    T(dayName(day) + (if (date.isNotBlank()) " · $date" else ""), 12.sp, C.Muted)
                    if (live > 0) T(" · " + trf("%s uživo", live), 12.sp, Color(0xFFFF4D55), FontWeight.SemiBold)
                    T(" · " + trf("%s utakmica", all?.size ?: 0) + " · " + Prefs.str("sport_cont", "eu").split(',').mapNotNull { c -> Sports.CONTINENTS.firstOrNull { it.first == c }?.second?.let { tr(it) } }.joinToString(", "), 12.sp, C.Muted)
                }
            }
            Focusable(onClick = { searchDlg = true }, shape = Pill, normal = Color.Transparent, focused = C.Red, zoom = 1.03f, modifier = Modifier.height(34.dp).width(190.dp).border(1.dp, C.Line, Pill)) { f ->
                Row(Modifier.align(Alignment.CenterStart).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Ico(R.drawable.lc_search, 14.dp, if (f) C.OnRed else C.Muted)
                    T(q.ifBlank { tr("Traži tim ili ligu") }, 12.sp, if (f) C.OnRed else if (q.isBlank()) C.Muted else C.Text)
                }
            }
            Focusable(onClick = { tick++; Sports.lastCombined.clear() }, shape = Pill, normal = Color.Transparent, focused = C.Red, zoom = 1.03f, modifier = Modifier.height(34.dp).border(1.dp, C.Line, Pill)) { f ->
                Row(Modifier.align(Alignment.Center).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Ico(R.drawable.lc_refresh_cw, 14.dp, if (f) C.OnRed else C.Text); T("Osvježi", 12.sp, if (f) C.OnRed else C.Text, FontWeight.SemiBold)
                }
            }
            Box(Modifier.onPreviewKeyEvent { e ->
                // Up from the days goes to the menu on top (TV, Filmovi…)
                if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionUp) { try { TopNav.fr.requestFocus() } catch (_: Exception) {}; true } else false
            }.onFocusChanged { if (it.hasFocus) scope.launch { sc.animateScrollTo(0) } }) {
                Focusable(onClick = { dayMenu = true }, focusRequester = dayFr, shape = Pill, normal = C.CardHi, focused = C.Red, zoom = 1.03f, modifier = Modifier.height(34.dp)) { f ->
                    T(dayName(day) + " ▾", 12.sp, if (f) C.OnRed else C.Text, FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center).padding(horizontal = 14.dp))
                }
            }
        }
        if (day == UPCOMING) UpcomingView { pick = it } else {
        // ---------------------------------------------------------------- picture on the left, the match on the right
    Column(Modifier.fillMaxSize().verticalScroll(sc).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // the picture of the match and its broadcasts scroll with the list, so the lower rows can always be reached
        if (events is Load.Ok) Box(Modifier.onPreviewKeyEvent { e ->
            if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionUp) { scope.launch { sc.animateScrollTo(0) }; try { dayFr.requestFocus() } catch (_: Exception) {}; true } else false
        }) { SportTop(top, miniFr, onFocus = { scope.launch { sc.animateScrollTo(0) } }, onPlay = { chs -> top?.let { play(it, chs, 0) } }) { top?.let { open(it) } } }
        // every TV station that shows the match, worldwide; the ones you have are green and play on OK
        // the first row: its whole card stays visible, and Up goes to the days
        if (events is Load.Ok && top != null) Box(Modifier.onFocusChanged { if (it.hasFocus) scope.launch { sc.animateScrollTo(0) } }) { BroadcastRow(top) { chs, i -> play(top, chs, i) } }
        // ---------------------------------------------------------------- filters (one scrolling row, nothing is hidden)
        // one row: All · Live · My channels · then the sports (football first), then other sports that have matches
        val extra = remember(all) { Sports.MORE_SPORTS.filter { (id, _) -> all?.any { it.sport == id } == true } }
        AlignStart { LazyRow(Modifier.fillMaxWidth().onFocusChanged { if (it.hasFocus && sc.value > 0 && top == null) scope.launch { sc.animateScrollTo(0) } },
            horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            item {
                val on = sport == "all" && view == "all" && !mine
                FilterChip("Sve", on, if (on) C.Red else null, dot = Color(0xFFFF4D55)) { sport = "all"; view = "all"; mine = false; league = null }
            }
            item { FilterChip("Uživo sada", view == "live", if (view == "live") C.Red else null, dot = Color(0xFFFF4D55)) { view = if (view == "live") "all" else "live" } }
            item { FilterChip("Moji kanali", mine, if (mine) C.Green else null, icon = R.drawable.lc_tv) { mine = !mine } }
            item { Box(Modifier.width(1.dp).height(38.dp).background(C.Line)) }
            val enabledSports = Prefs.str("sport_enabled", "football").split(',').filter { it.isNotBlank() }
            items((Sports.SPORTS + extra).filter { enabledSports.isEmpty() || it.first in enabledSports }) { (id, label) ->
                FilterChip(label, sport == id, if (sport == id) Sports.sportColor(id) else null, dot = Sports.sportColor(id)) {
                    sport = if (sport == id) "all" else id; league = null
                }
            }
        } }
        if ((guide is Load.Loading || (matching && !guideFailed)) && p != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.CircularProgressIndicator(color = C.Red, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
            T("Tražim prenose u tvom TV programu…", 12.sp, C.Muted)
        }

        when (val s = events) {
            is Load.Loading -> Box(Modifier.fillMaxWidth().height(260.dp)) { Loading("Učitavam utakmice…") }
            is Load.Err -> Box(Modifier.fillMaxWidth().height(260.dp)) { Message("Greška", s.message) }
            is Load.Ok -> {
                val stillMatching = (guide is Load.Loading || matching) && !guideFailed && p != null
                if (shown.isEmpty()) {
                    if (stillMatching && mine) Box(Modifier.fillMaxWidth().height(220.dp)) { Loading("Tražim prenose u tvom TV programu…") }
                    else if (hiddenByMine > 0) Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        T(trf("%s utakmica sakriveno jer nije pronađen tvoj kanal", hiddenByMine), 18.sp, C.Text, FontWeight.SemiBold, maxLines = 2)
                        Button("Prikaži sve", R.drawable.lc_eye, primary = true) { mine = false }
                    }
                    else Box(Modifier.fillMaxWidth().height(220.dp)) {
                        Message(if (view == "live") "Trenutno nema utakmica uživo" else "Nema utakmica za ovaj dan")
                    }
                }
                val firstSec = listOf(sections.first, sections.second, sections.third).indexOfFirst { it.isNotEmpty() }
                Section("Uživo sada", sections.first, live = true, if (firstSec == 0) heroFr else null, { sel = it }, memId, memFr, onMenu = { pick = it }) { choose(it) }
                Section("Kasnije", sections.second, false, if (firstSec == 1) heroFr else null, { sel = it }, memId, memFr, onMenu = { pick = it }) { choose(it) }
                Section("Završeno", sections.third, false, if (firstSec == 2) heroFr else null, { sel = it }, memId, memFr, onMenu = { pick = it }) { choose(it) }
                // ---------------------------------------------------------- leagues
                if (leagues.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    T("Lige", 14.sp, C.Muted, FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(leagues) { (name, abbr) ->
                            val sel = league == name
                            Focusable(onClick = { league = if (sel) null else name }, shape = Pill,
                                normal = if (sel) C.CardFocus else C.Card, zoom = 1.04f, modifier = Modifier.height(38.dp)) { _ ->
                                Row(Modifier.align(Alignment.Center).padding(start = 6.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(Modifier.size(28.dp).clip(CircleShape).background(C.Red.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                                        T(abbr.take(4), 9.sp, C.Text, FontWeight.Bold)
                                    }
                                    T(tr(name), 14.sp, C.Text, FontWeight.Medium, maxLines = 1)
                                }
                            }
                        }
                    }
                }
                T("Rezultati: ESPN. Prenosi: TV program tvoje playliste.", 11.sp, C.Muted)
                g?.let { T(it.stats + (if (tookMs >= 0) " · " + trf("traženje: %s s", "%.1f".format(tookMs / 1000.0)) else ""), 11.sp, C.Muted) }
            }
        }
    }
    }
    }

    if (searchDlg) TextDialog("Traži tim ili ligu", "Tim ili liga", q, onDismiss = { searchDlg = false }) { v -> q = v; searchDlg = false }
    if (dayMenu) Popup("Dan", { dayMenu = false }) {
        (listOf(UPCOMING) + (-1..3)).forEach { d -> OptionRow(dayName(d), if (d == day) R.drawable.lc_check else null, color = if (d == day) C.Green else null) { day = d; league = null; SportsMemory.evId = null; dayMenu = false } }
    }
    pick?.let { ev -> EventPopup(ev, onDismiss = { pick = null }) { chs, i -> pick = null; play(ev, chs, i) } }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(events is Load.Ok) {
        if (focused || events !is Load.Ok) return@LaunchedEffect
        focused = true
        delay(100)
        // back from the full screen: the match you had picked, else the first one
        val back = memId != null && shown.any { it.id == memId }
        if (back) { sc.scrollTo(0); delay(200); if (!miniFr.tryFocus()) memFr.tryFocus() } else if (shown.isNotEmpty()) heroFr.tryFocus() else dayFr.tryFocus()
    }
}

/** one chip of the filter row; [fill] is its colour when it is on */
@Composable
private fun FilterChip(label: String, on: Boolean, fill: Color?, dot: Color? = null, icon: Int? = null, onClick: () -> Unit) {
    val fg = if (on) Color.White else C.Text
    Focusable(onClick = onClick, shape = Pill, normal = fill ?: C.Card, focused = fill ?: C.CardHi, zoom = 1.05f, modifier = Modifier.height(38.dp)) { _ ->
        Row(Modifier.align(Alignment.Center).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            if (icon != null) Ico(if (on) R.drawable.lc_check else icon, 15.dp, fg)
            else if (dot != null) Dot(if (on) Color.White else dot)
            T(label, 14.sp, fg, FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** the "Upcoming" tab next to the days */
private const val UPCOMING = 100

private fun dayName(offset: Int): String = when (offset) {
    UPCOMING -> tr("Nadolazeće")
    -1 -> tr("Jučer"); 0 -> tr("Danas"); 1 -> tr("Sutra")
    else -> SimpleDateFormat("EEE d.", Locale(Look.lang)).format(Date(Sports.dayStart(offset)))
}

/**
 * Upcoming matches of the next 7 days as cards: crests "A vs B", league, day and time, a live countdown.
 * Football first, then what is closest to where you live. OK opens the match (my channels | who else shows it).
 */
@Composable
private fun UpcomingView(onOpen: (SportEvent) -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    val data by load(tick) { Sports.upcoming(7) }
    var q by remember { mutableStateOf("") }
    var sport by remember { mutableStateOf("all") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Field("Traži utakmicu, tim ili ligu", q, { q = it }, Modifier.weight(1f))
            Button("Osvježi", R.drawable.lc_refresh_cw) { tick++ }
        }
        val list = (data as? Load.Ok)?.value ?: emptyList()
        val sportsHere = remember(list) { (Sports.SPORTS + Sports.MORE_SPORTS).filter { (id, _) -> list.any { it.sport == id } } }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            item { FilterChip("Sve", sport == "all", if (sport == "all") C.Red else null, dot = Color(0xFFFF4D55)) { sport = "all" } }
            items(sportsHere) { (id, label) -> FilterChip(label, sport == id, if (sport == id) Sports.sportColor(id) else null, dot = Sports.sportColor(id)) { sport = if (sport == id) "all" else id } }
        }
        val shown = remember(list, q, sport) {
            val w = q.trim().lowercase()
            list.filter { e -> (sport == "all" || e.sport == sport) &&
                (w.isEmpty() || listOfNotNull(e.title, e.league, e.home?.name, e.away?.name).any { it.lowercase().contains(w) }) }
        }
        when (val d = data) {
            is Load.Loading -> Box(Modifier.fillMaxSize()) { Loading("Učitavam utakmice…") }
            is Load.Err -> Box(Modifier.fillMaxSize()) { Message("Greška", d.message) }
            is Load.Ok -> {
                T(trf("%s utakmica u narednih 7 dana", shown.size), 13.sp, C.Muted)
                if (shown.isEmpty()) Box(Modifier.fillMaxSize()) { Message("Nema utakmica") }
                else androidx.compose.foundation.lazy.grid.LazyVerticalGrid(androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                    Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp, top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(shown.size, key = { shown[it].id }) { i -> UpcomingCard(shown[i], now) { onOpen(shown[i]) } }
                }
            }
        }
    }
}

@Composable
private fun UpcomingCard(ev: SportEvent, now: Long, onClick: () -> Unit) {
    val left = (ev.start - now).coerceAtLeast(0) / 1000
    val countdown = if (left >= 86_400) trf("za %s d %s h", left / 86_400, (left % 86_400) / 3600)
        else "%02d:%02d:%02d".format(left / 3600, (left % 3600) / 60, left % 60)
    val dayOff = ((ev.start - Sports.dayStart(0)) / 86_400_000L).toInt()
    Focusable(modifier = Modifier.fillMaxWidth().height(210.dp), onClick = onClick, shape = Rnd(20.dp), zoom = 1.03f) { _ ->
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Dot(Sports.sportColor(ev.sport), 7.dp)
                T(tr(ev.league), 12.sp, C.Soft, FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                if (ev.channels.isNotEmpty()) Ico(R.drawable.lc_tv, 14.dp, C.Green)
            }
            if (ev.home != null && ev.away != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                    Crest(ev.home, 52.dp); T("VS", 16.sp, C.Muted, FontWeight.Bold); Crest(ev.away, 52.dp)
                }
                T(ev.home.name + " – " + ev.away.name, 16.sp, C.Text, FontWeight.Bold, maxLines = 1)
            } else T(ev.title, 16.sp, C.Text, FontWeight.Bold, maxLines = 3)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Ico(R.drawable.lc_clock, 13.dp, C.Muted)
                T(dayName(dayOff) + " · " + hhmm(ev.start), 12.sp, C.Soft, maxLines = 1, modifier = Modifier.weight(1f))
                T(trf("Počinje za %s", countdown), 12.sp, C.Amber, FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun DayTabs(day: Int, fr: FocusRequester, onDay: (Int) -> Unit) {
    Row(Modifier.clip(Rnd(14.dp)).background(C.Card).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        (listOf(UPCOMING) + (-1..3)).forEach { d ->
            val sel = d == day
            Focusable(onClick = { onDay(d) }, focusRequester = if (d == 0) fr else null, normal = if (sel) C.Text else Color.Transparent,
                focused = if (sel) C.Text else C.CardHi, shape = Rnd(11.dp), zoom = 1f, modifier = Modifier.height(38.dp)) { _ ->
                T(dayName(d), 14.sp, if (sel) C.Bg else C.Soft, FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center).padding(horizontal = 12.dp))
            }
        }
    }
}

/** Club crest from the feed, or a round badge in the club colour with its short name. */
@Composable
fun Crest(t: Team, size: Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(if (t.logo != null) Color(0xFFF1F3F6) else (t.color ?: Color(0xFF334155))),
        contentAlignment = Alignment.Center) {
        if (t.logo != null) AsyncImage(model = t.logo, contentDescription = null, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(size * 0.12f))
        else T(t.abbr.take(3), (size.value * 0.3f).sp, Color.White, FontWeight.Bold)
    }
}

/** The kit: a shirt in the team colours. */
@Composable
private fun Kit(t: Team, size: Dp) {
    val c = t.color ?: return
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.shirt_fill), contentDescription = null, tint = c, modifier = Modifier.fillMaxSize())
        t.color2?.let { Box(Modifier.size(size * 0.22f, size * 0.5f).clip(Rnd(2.dp)).background(it)) }
    }
}

@Composable
private fun StateBadge(ev: SportEvent, big: Boolean = false) {
    val fs = if (big) 15.sp else 13.sp
    when (ev.state) {
        "in" -> Row(Modifier.clip(Pill).background(Color(0xFFE11D2E)).padding(horizontal = 10.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Dot(Color.White, 7.dp); T(ev.clock ?: tr("UŽIVO"), fs, Color.White, FontWeight.Bold)
        }
        "post" -> T(ev.clock ?: "FT", fs, C.Muted, FontWeight.SemiBold)
        else -> T(hhmm(ev.start), fs, C.Soft, FontWeight.SemiBold)
    }
}

@Composable
private fun HeroCard(ev: SportEvent, fr: FocusRequester, onClick: () -> Unit) {
    Focusable(modifier = Modifier.fillMaxWidth().height(196.dp), onClick = onClick, focusRequester = fr, shape = Rnd(22.dp),
        normal = C.Card, focused = C.CardFocus, zoom = 1.01f) { _ ->
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxHeight().padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.clip(Rnd(6.dp)).background(Sports.sportColor(ev.sport).copy(alpha = 0.25f)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                        T(ev.leagueAbbr, 11.sp, C.Text, FontWeight.Bold)
                    }
                    T(tr(ev.league) + (ev.round?.let { " · $it" } ?: ""), 13.sp, C.Muted, modifier = Modifier.weight(1f))
                }
                if (ev.home != null && ev.away != null) {
                    HeroTeam(ev.home); HeroTeam(ev.away)
                } else T(ev.title, 22.sp, C.Text, FontWeight.Bold, maxLines = 2)
                Spacer(Modifier.weight(1f))
                ev.venue?.let { T(it, 12.sp, C.Muted) }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(C.Line))
            Column(Modifier.width(150.dp).fillMaxHeight().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
                if (ev.state != "pre") StateBadge(ev, big = true) else T(dayName(((ev.start - Sports.dayStart(0)) / 86_400_000L).toInt()), 13.sp, C.Muted)
                if (ev.state != "pre" && ev.home?.score != null) T("${ev.home?.score} : ${ev.away?.score ?: "-"}", 40.sp, C.Text, FontWeight.Bold)
                else if (ev.state == "pre") T(hhmm(ev.start), 34.sp, C.Text, FontWeight.Bold)
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(C.Line))
            Column(Modifier.width(220.dp).fillMaxHeight().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                T("PRENOSE", 11.sp, C.Muted, FontWeight.Bold)
                if (ev.channels.isEmpty() && ev.maybe.isEmpty() && ev.ai.isEmpty()) T("Prenos nije pronađen u tvojoj listi", 13.sp, C.Muted, maxLines = 3)
                if (ev.channels.isEmpty()) ev.ai.take(3).forEach { ch ->
                    Row(Modifier.fillMaxWidth().clip(Rnd(10.dp)).background(AiColor.copy(alpha = 0.12f))
                        .border(1.dp, AiColor.copy(alpha = 0.6f), Rnd(10.dp)).padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChannelLogo(ch.logo, ch.name)
                        T(ch.name, 13.sp, C.Text, FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f))
                        T("AI", 11.sp, AiColor, FontWeight.Bold)
                    }
                }
                if (ev.channels.isEmpty() && ev.ai.isEmpty()) ev.maybe.take(3).forEach { ch ->
                    Row(Modifier.fillMaxWidth().clip(Rnd(10.dp)).background(C.Amber.copy(alpha = 0.10f))
                        .border(1.dp, C.Amber.copy(alpha = 0.5f), Rnd(10.dp)).padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChannelLogo(ch.logo, ch.name)
                        T(ch.name, 13.sp, C.Text, FontWeight.Medium, modifier = Modifier.weight(1f))
                        T("?", 13.sp, C.Amber, FontWeight.Bold)
                    }
                }
                ev.channels.take(3).forEach { ch ->
                    Row(Modifier.fillMaxWidth().clip(Rnd(10.dp)).background(C.Green.copy(alpha = 0.12f))
                        .border(1.dp, C.Green.copy(alpha = 0.6f), Rnd(10.dp)).padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChannelLogo(ch.logo, ch.name)
                        T(ch.name, 13.sp, C.Text, FontWeight.Medium, modifier = Modifier.weight(1f))
                        Ico(R.drawable.lc_tv, 15.dp, C.Green)
                    }
                }
                if (ev.channels.size > 3) T("+${ev.channels.size - 3}", 12.sp, C.Muted)
            }
        }
    }
}

/** all stations that broadcast [ev] (AI with a web search), under the picture; yours are green and play */
@Composable
private fun BroadcastRow(ev: SportEvent, onPlay: (List<Channel>, Int) -> Unit) {
    val ctx = LocalContext.current
    var list by remember(ev.id) { mutableStateOf<List<Pair<SportsAI.Station, List<Channel>>>?>(null) }
    var busy by remember(ev.id) { mutableStateOf(false) }
    var err by remember(ev.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(ev.id) {
        if (!SportsAI.ready) return@LaunchedEffect
        delay(1200)                                   // only for the match you stay on, not every card you pass
        busy = true
        try {
            val stations = SportsAI.broadcasters(ev)
            val all = try { Sports.guide(ctx, Store.actives(ctx)).all } catch (e: Exception) { emptyList() }
            list = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { stations.map { it to SportsAI.matchStation(it, all) } }
        } catch (e: Exception) { err = e.message }
        busy = false
    }
    // what PRETORIAN already found in your list comes first, then what the AI adds
    val own = remember(ev.id, ev.channels, ev.ai) { (ev.channels + ev.ai).distinctBy { it.name.trim().lowercase() }.take(6) }
    val l = list
    val extra = l?.filter { (s, mine) -> mine.isEmpty() || own.none { o -> mine.any { it.url == o.url } } } ?: emptyList()
    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Ico(R.drawable.lc_tv, 16.dp, AiColor)
        T("Prenose:", 13.sp, C.Soft, FontWeight.SemiBold)
        LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            itemsIndexed(own, key = { _, c -> "own" + c.url }) { i, c -> StationChip(c.name, true) { onPlay(own, i) } }
            items(extra, key = { "ai" + it.first.country + it.first.name }) { (st, mine) ->
                StationChip((if (st.country.isNotBlank()) st.country + " · " else "") + st.name, mine.isNotEmpty()) { if (mine.isNotEmpty()) onPlay(mine, 0) }
            }
            item {
                when {
                    !SportsAI.ready -> if (own.isEmpty()) T("Dodaj AI ključ u Postavke › Sport i AI da vidiš sve TV prenose", 12.sp, C.Muted, maxLines = 1)
                    busy -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.CircularProgressIndicator(color = AiColor, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                        T("AI traži TV prenose…", 12.sp, C.Muted)
                    }
                    err != null && own.isEmpty() -> T(err ?: "", 12.sp, C.Muted, maxLines = 1)
                    l != null && l.isEmpty() && own.isEmpty() -> T("AI nije našao TV prenose za ovu utakmicu", 12.sp, C.Muted)
                    else -> {}
                }
            }
        }
    }
}

/** a TV station: green with ▶ when it is in your list (plays on OK), grey when it is only information */
@Composable
private fun StationChip(label: String, mine: Boolean, onClick: () -> Unit) {
    if (mine) Focusable(modifier = Modifier.height(34.dp), onClick = onClick, shape = Pill, normal = C.Green.copy(alpha = 0.22f), zoom = 1.06f) { _ ->
        Row(Modifier.align(Alignment.Center).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Ico(R.drawable.lc_play, 12.dp, C.Green); T(label, 13.sp, C.Text, FontWeight.SemiBold, maxLines = 1)
        }
    } else Box(Modifier.height(34.dp).clip(Pill).background(C.Card).border(1.dp, C.Line, Pill).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
        T(label, 13.sp, C.Soft, maxLines = 1)
    }
}

/** top of Sports: live picture of the match on the left, score and details on the right */
@Composable
private fun SportTop(ev: SportEvent?, miniFr: FocusRequester, onFocus: () -> Unit = {}, onPlay: (List<Channel>) -> Unit = {}, onOpen: () -> Unit = {}) {
    val chs = ev?.let { it.channels.ifEmpty { it.ai } } ?: emptyList()
    val ch = chs.firstOrNull()
    // the small picture starts only when you pick it: OK on it starts it, OK again goes full screen
    var pv by remember { mutableStateOf<Channel?>(null) }
    LaunchedEffect(ch?.url) { pv = null }
    Row(Modifier.fillMaxWidth().height(200.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Focusable(onClick = {
            if (ch == null) onOpen() else if (pv == null && Prefs.bool("sport_preview", true)) pv = ch else onPlay(chs)
        }, onFocus = onFocus, focusRequester = miniFr, shape = Rnd(18.dp), normal = Color.Transparent, focused = Color.Transparent, zoom = 1.02f, modifier = Modifier.fillMaxHeight().aspectRatio(16f / 9f)) { f ->
            val c = pv
            if (c != null && Prefs.bool("sport_preview", true)) PreviewBox(c, Modifier.fillMaxSize())
            else Column(Modifier.fillMaxSize().clip(Rnd(18.dp)).background(Color.Black.copy(alpha = 0.55f)).border(1.dp, C.Line, Rnd(18.dp)),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
                if (ch != null) ChannelLogo(ch.logo, ch.name) else Ico(R.drawable.lc_tv, 34.dp, C.Soft)
                T(when {
                    ev == null -> tr("Izaberi utakmicu")
                    ch == null -> tr("Za ovu utakmicu još nije pronađen kanal")
                    ev.state == "pre" -> trf("Počinje u %s · %s", hhmm(ev.start), ch.name)
                    ev.state == "post" -> trf("Završeno · %s", ch.name)
                    else -> ch.name
                }, 13.sp, C.Soft, maxLines = 2, modifier = Modifier.padding(horizontal = 16.dp))
                if (ch != null && f) T(if (Prefs.bool("sport_preview", true)) "OK = mala slika · OK još jednom = cijeli ekran" else "OK = cijeli ekran", 11.sp, C.Text, FontWeight.SemiBold)
            }
            if (c != null && f) T("OK = cijeli ekran", 11.sp, Color.White, FontWeight.Bold, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                .clip(Pill).background(Color(0xB3000000)).padding(horizontal = 10.dp, vertical = 3.dp))
        }
        Focusable(onClick = onOpen, onFocus = onFocus, shape = Rnd(18.dp), normal = C.Card, focused = C.CardHi, zoom = 1.01f,
            modifier = Modifier.weight(1f).fillMaxHeight().border(1.dp, C.Line, Rnd(18.dp))) { _ ->
            if (ev == null) { Message("Izaberi utakmicu"); return@Focusable }
            // big crest in the background, like a watermark
            ev.home?.let { h -> Box(Modifier.align(Alignment.CenterEnd).padding(end = 10.dp).alpha(0.10f)) { Crest(h, 180.dp) } }
            Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Dot(Sports.sportColor(ev.sport), 7.dp)
                    T(tr(ev.league) + (ev.round?.let { " · $it" } ?: ""), 13.sp, C.Soft, modifier = Modifier.weight(1f))
                    StateBadge(ev, big = true)
                }
                if (ev.home != null && ev.away != null) {
                    TopTeam(ev.home, if (ev.state != "pre") ev.home.score else null)
                    TopTeam(ev.away, if (ev.state != "pre") ev.away.score else null)
                } else T(ev.title, 22.sp, C.Text, FontWeight.Bold, maxLines = 2)
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        ev.channels.isNotEmpty() -> { Ico(R.drawable.lc_tv, 14.dp, C.Green); T(ev.channels.joinToString(" · ") { it.name }, 12.sp, C.Green, maxLines = 1, modifier = Modifier.weight(1f)) }
                        ev.ai.isNotEmpty() -> { Ico(R.drawable.lc_sparkles, 14.dp, AiColor); T("AI: " + ev.ai.joinToString(" · ") { it.name }, 12.sp, AiColor, maxLines = 1, modifier = Modifier.weight(1f)) }
                        else -> { Ico(R.drawable.lc_tv, 14.dp, C.Muted); T("Za ovu utakmicu još nije pronađen kanal", 12.sp, C.Muted, maxLines = 1, modifier = Modifier.weight(1f)) }
                    }
                    if (ev.state == "pre") T(hhmm(ev.start), 13.sp, C.Soft, FontWeight.SemiBold)
                    else ev.venue?.let { T(it, 12.sp, C.Muted, maxLines = 1, modifier = Modifier.widthIn(max = 220.dp)) }
                }
            }
        }
    }
}

@Composable
private fun TopTeam(t: Team, score: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Crest(t, 40.dp)
        T(t.name, 24.sp, C.Text, FontWeight.Bold, modifier = Modifier.weight(1f))
        if (score != null) T(score, 32.sp, C.Text, FontWeight.Bold)
    }
}

@Composable
private fun HeroTeam(t: Team) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Crest(t, 38.dp)
        Kit(t, 20.dp)
        T(t.name, 21.sp, C.Text, FontWeight.Bold, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Section(title: String, list: List<SportEvent>, live: Boolean = false, fr: FocusRequester? = null,
                    onFocus: (SportEvent) -> Unit = {}, memId: String? = null, memFr: FocusRequester? = null, onMenu: (SportEvent) -> Unit = {}, onOpen: (SportEvent) -> Unit) {
    if (list.isEmpty()) return
    val rowState = androidx.compose.foundation.lazy.rememberLazyListState()
    val memIdx = list.indexOfFirst { it.id == memId }
    LaunchedEffect(memIdx) { if (memIdx > 0) rowState.scrollToItem(memIdx) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (live) Dot(Color(0xFFFF4D55), 9.dp)
            T(title, 18.sp, C.Text, FontWeight.SemiBold)
            T("· ${list.size}", 15.sp, C.Muted)
        }
        AlignStart { LazyRow(state = rowState, horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 12.dp)) {
            itemsIndexed(list, key = { _, e -> e.id }) { i, ev -> EventCard(ev, if (ev.id == memId && memFr != null) memFr else if (i == 0) fr else null, { onFocus(ev) }, { onMenu(ev) }) { onOpen(ev) } }
        } }
    }
}

@Composable
private fun EventCard(ev: SportEvent, fr: FocusRequester? = null, onFocus: () -> Unit = {}, onMenu: (() -> Unit)? = null, onClick: () -> Unit) {
    val none = ev.channels.isEmpty() && ev.ai.isEmpty() && ev.maybe.isEmpty()
    Focusable(modifier = Modifier.width(272.dp).height(138.dp), onClick = onClick, onMenu = onMenu, shape = Rnd(18.dp), focusRequester = fr, onFocus = onFocus,
        normal = C.Card, focused = C.CardFocus, zoom = 1.03f) { f ->
        Column(Modifier.fillMaxSize().then(if (none && !f) Modifier.alpha(0.6f) else Modifier).padding(horizontal = 14.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(Sports.sportColor(ev.sport), 6.dp)
                T(tr(ev.league), 12.sp, C.Muted, modifier = Modifier.weight(1f).padding(start = 6.dp))
                StateBadge(ev)
            }
            if (ev.home != null && ev.away != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Crest(ev.home, 34.dp)
                    T(if (ev.state != "pre") "${ev.home.score ?: 0} : ${ev.away.score ?: 0}" else "vs", if (ev.state != "pre") 18.sp else 14.sp, if (ev.state != "pre") C.Text else C.Muted,
                        FontWeight.Bold, modifier = Modifier.weight(1f), align = androidx.compose.ui.text.style.TextAlign.Center)
                    Crest(ev.away, 34.dp)
                }
                T(ev.home.name + " – " + ev.away.name, 14.sp, C.Text, FontWeight.Bold)
            } else T(ev.title, 15.sp, C.Text, FontWeight.SemiBold, maxLines = 2)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (ev.channels.isNotEmpty()) {
                    Ico(R.drawable.lc_tv, 14.dp, C.Green)
                    T(ev.channels.first().name, 12.sp, C.Green, FontWeight.Medium, modifier = Modifier.weight(1f, fill = false))
                    T("· " + tr("u tvojoj listi") + if (ev.channels.size > 1) " +${ev.channels.size - 1}" else "", 12.sp, C.Green)
                } else if (ev.ai.isNotEmpty()) {
                    Ico(R.drawable.lc_sparkles, 14.dp, AiColor)
                    T("AI: " + ev.ai.first().name, 12.sp, AiColor, modifier = Modifier.weight(1f, fill = false))
                    if (ev.ai.size > 1) T("+${ev.ai.size - 1}", 12.sp, AiColor)
                } else if (ev.maybe.isNotEmpty()) {
                    Ico(R.drawable.lc_tv, 14.dp, C.Amber)
                    T(trf("Mogući prenos: %s", ev.maybe.first().name), 12.sp, C.Amber, modifier = Modifier.weight(1f, fill = false))
                    if (ev.maybe.size > 1) T("+${ev.maybe.size - 1}", 12.sp, C.Amber)
                } else T("Prenos nije pronađen u tvojoj listi", 12.sp, C.Muted, modifier = Modifier.weight(1f))
                if (ev.state == "pre") { Spacer(Modifier.weight(0.01f)); Countdown(ev.start) }
            }
        }
    }
}

/** "za 3:12:40" until the match starts */
@Composable
private fun Countdown(start: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(start) { while (now < start) { delay(1000); now = System.currentTimeMillis() } }
    val left = ((start - now) / 1000).coerceAtLeast(0)
    if (left <= 0) return
    T(if (left >= 86_400) trf("za %s d", left / 86_400) else trf("za %s", "%d:%02d:%02d".format(left / 3600, (left % 3600) / 60, left % 60)), 12.sp, C.Amber, FontWeight.SemiBold)
}

@Composable
private fun TeamLine(t: Team, score: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Crest(t, 22.dp)
        T(t.name, 15.sp, C.Text, FontWeight.Medium, modifier = Modifier.weight(1f))
        if (score != null) T(score, 16.sp, C.Text, FontWeight.Bold)
    }
}

/**
 * A match, on half a screen each: left "My channels" (what you can watch, green sure, purple AI, amber maybe),
 * right "Who else shows it" (TV stations worldwide that are not in your list, from the AI with a web search).
 */
@Composable
private fun EventPopup(ev: SportEvent, onDismiss: () -> Unit, onPlay: (List<Channel>, Int) -> Unit) {
    val ctx = LocalContext.current
    val details by load(ev.id, ev.state) { if (ev.state != "pre" && !ev.fromGuide) Sports.details(ev) else emptyList() }
    var rev by remember { mutableIntStateOf(0) }
    val title = if (ev.home != null && ev.away != null) "${ev.home.name} – ${ev.away.name}" else ev.title
    // the AI's list of stations, and which of them you have
    var stations by remember { mutableStateOf<List<Pair<SportsAI.Station, List<Channel>>>?>(null) }
    var aiErr by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(ev.id) {
        if (!SportsAI.ready) return@LaunchedEffect
        try {
            val st = SportsAI.broadcasters(ev)
            val all = try { Sports.guide(ctx, Store.actives(ctx)).all } catch (e: Exception) { emptyList() }
            stations = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { st.map { it to SportsAI.matchStation(it, all) } }
        } catch (e: Exception) { aiErr = e.message; stations = emptyList() }
    }
    val known = (ev.channels + ev.ai + ev.maybe).map { it.url }.toSet()
    val aiMine = stations?.flatMap { it.second }?.filter { it.url !in known }?.distinctBy { it.url } ?: emptyList()
    val others = stations?.filter { it.second.isEmpty() }?.map { it.first } ?: emptyList()
    androidx.compose.runtime.key(rev) {
        Popup(title, onDismiss, width = 1100.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                T(tr(ev.league) + " · " + dayName(((ev.start - Sports.dayStart(0)).let { if (it < 0) it - 86_399_999L else it } / 86_400_000L).toInt()) + " " + hhmm(ev.start), 13.sp, C.Muted, modifier = Modifier.weight(1f))
                if (ev.state != "pre" && ev.home?.score != null) T("${ev.home.score} : ${ev.away?.score ?: "-"}", 24.sp, C.Text, FontWeight.Bold)
                StateBadge(ev)
            }
            (details as? Load.Ok)?.value?.takeIf { it.isNotEmpty() }?.let { list -> T(list.joinToString(" · "), 12.sp, C.Soft, maxLines = 2) }
            if (ev.state == "pre") {
                val has = Reminders.has(title, ev.start)
                OptionRow(if (has) "Ukloni podsjetnik" else "Podsjeti me", R.drawable.lc_bell, color = if (has) null else C.Amber) {
                    val ch = ev.channels.firstOrNull() ?: ev.maybe.firstOrNull()
                    val on = Reminders.toggle(Reminder("s${ev.id.hashCode()}", title, ev.start, ch?.url, ch?.name, ch?.logo, ch?.pid, "sport"))
                    Toast.makeText(ctx, tr(if (on) "Podsjetnik postavljen" else "Podsjetnik uklonjen"), Toast.LENGTH_SHORT).show(); rev++
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                // ------------------------------------------------ left: my channels
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Ico(R.drawable.lc_tv, 16.dp, C.Green); T("Moji kanali", 16.sp, C.Text, FontWeight.Bold)
                    }
                    ev.channels.forEachIndexed { i, ch -> ChannelChoice(ch, C.Green) { onPlay(ev.channels, i) } }
                    if (ev.ai.isNotEmpty()) T(SportsAI.prov.name + " · " + tr("procjena s interneta"), 12.sp, AiColor, FontWeight.Bold)
                    ev.ai.forEachIndexed { i, ch -> ChannelChoice(ch, AiColor) { onPlay(ev.ai, i) } }
                    if (aiMine.isNotEmpty()) T("AI: prenosi i ovaj tvoj kanal", 12.sp, AiColor, FontWeight.Bold)
                    aiMine.forEachIndexed { i, ch -> ChannelChoice(ch, AiColor) { onPlay(aiMine, i) } }
                    if (ev.maybe.isNotEmpty()) T("Mogući prenos (po ligi, nije sigurno)", 12.sp, C.Amber, FontWeight.Bold)
                    ev.maybe.forEachIndexed { i, ch -> ChannelChoice(ch, C.Amber) { onPlay(ev.maybe, i) } }
                    if (ev.channels.isEmpty() && ev.ai.isEmpty() && ev.maybe.isEmpty() && aiMine.isEmpty())
                        T(if (stations == null && SportsAI.ready) "AI traži TV prenose…" else "Prenos nije pronađen u tvojoj listi", 13.sp, C.Muted, maxLines = 2)
                }
                Box(Modifier.width(1.dp).height(260.dp).background(C.Line))
                // ------------------------------------------------ right: who else shows it
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Ico(R.drawable.lc_globe, 16.dp, AiColor); T("Ko još prenosi", 16.sp, C.Text, FontWeight.Bold)
                    }
                    T("TV stanice koje nisu na tvojoj listi", 12.sp, C.Muted, maxLines = 1)
                    when {
                        !SportsAI.ready -> T("Dodaj AI ključ u Postavke › Sport i AI da vidiš sve TV prenose", 13.sp, C.Muted, maxLines = 3)
                        stations == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.CircularProgressIndicator(color = AiColor, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                            T("AI traži TV prenose…", 13.sp, C.Muted)
                        }
                        aiErr != null -> T(aiErr ?: "", 13.sp, C.Muted, maxLines = 3)
                        others.isEmpty() -> T("AI nije našao druge TV prenose", 13.sp, C.Muted, maxLines = 2)
                        else -> others.forEach { st ->
                            Row(Modifier.fillMaxWidth().clip(Rnd(12.dp)).background(C.Card).border(1.dp, C.Line, Rnd(12.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.width(40.dp).clip(Rnd(6.dp)).background(C.CardHi).padding(vertical = 3.dp), contentAlignment = Alignment.Center) {
                                    T(st.country.ifBlank { "–" }, 12.sp, C.Text, FontWeight.Bold)
                                }
                                T(st.name, 15.sp, C.Text, FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelChoice(ch: Channel, color: Color, onClick: () -> Unit) {
    val ctx = LocalContext.current
    Focusable(modifier = Modifier.fillMaxWidth().height(56.dp).border(1.dp, color.copy(alpha = 0.5f), Rnd(14.dp)), onClick = onClick,
        normal = color.copy(alpha = 0.08f), zoom = 1.01f) { _ ->
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChannelLogo(ch.logo, ch.name)
            T(ch.name, 15.sp, C.Text, FontWeight.Medium, modifier = Modifier.weight(1f))
            Store.byId(ctx, ch.pid)?.takeIf { Store.actives(ctx).size > 1 }?.let { T(it.name, 12.sp, C.Muted) }
            Ico(R.drawable.lc_tv, 18.dp, color)
        }
    }
}
