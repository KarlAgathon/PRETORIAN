package com.pretorian.tv

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

@Composable
fun SportsScreen(nav: Nav) {
    val ctx = LocalContext.current
    val p = remember { Store.active(ctx) }
    var day by remember { mutableIntStateOf(0) }
    var sport by remember { mutableStateOf("all") }
    var league by remember { mutableStateOf<String?>(null) }
    var mine by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var updated by remember { mutableLongStateOf(0L) }
    var pick by remember { mutableStateOf<SportEvent?>(null) }
    var view by remember { mutableStateOf("all") }   // all, live, done
    val events by load(day, tick, keep = true) { Sports.day(day).also { updated = System.currentTimeMillis() } }
    val guide by load(p?.id) { Sports.guide(ctx, Store.actives(ctx)) }
    val heroFr = remember { FocusRequester() }
    val dayFr = remember { FocusRequester() }

    // live scores refresh every minute while you look at today
    LaunchedEffect(day) { while (day == 0) { delay(60_000); tick++ } }

    val g = (guide as? Load.Ok)?.value
    // matching matches to your guide is heavy: it runs in the background, the screen keeps the old result meanwhile
    val evs = (events as? Load.Ok)?.value
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
    val shown = remember(all, sport, league, mine, view) {
        all?.filter { e ->
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
    val top = remember(sel, shown, hero) { sel?.let { x -> shown.find { it.id == x.id } ?: x } ?: hero }
    val sections = remember(shown) {
        val rest = shown
        fun s(l: List<SportEvent>) = l.sortedWith(compareBy({ watchRank(it) }, { it.start }))
        Triple(s(rest.filter { it.live }), s(rest.filter { it.state == "pre" }), rest.filter { it.state == "post" }.sortedBy { watchRank(it) })
    }
    val leagues = remember(all, sport) { (all ?: emptyList()).filter { sport == "all" || it.sport == sport }.map { it.league to it.leagueAbbr }.distinct().take(20) }
    val liveCount = remember(all) { all?.count { it.live } ?: 0 }

    fun play(ev: SportEvent, chs: List<Channel>, i: Int) {
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
                T("Sports Center", 26.sp, C.Text, FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val live = liveCount
                    T(dayName(day) + " · " + trf("%s utakmica", all?.size ?: 0), 13.sp, C.Muted)
                    if (live > 0) T(" · " + trf("%s uživo", live), 13.sp, Color(0xFFFF4D55), FontWeight.SemiBold)
                    if (updated > 0) T(" · " + trf("ažurirano %s", hhmm(updated)), 13.sp, C.Muted)
                }
            }
            DayTabs(day, dayFr) { day = it; league = null }
        }
        // ---------------------------------------------------------------- picture on the left, the match on the right
        if (events is Load.Ok) SportTop(top)
        // every TV station that shows the match, worldwide; the ones you have are green and play on OK
        if (events is Load.Ok && top != null) BroadcastRow(top) { chs, i -> play(top, chs, i) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // ---------------------------------------------------------------- filters (one scrolling row, nothing is hidden)
        AlignStart { LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("all" to "Sve", "live" to "Uživo sada", "done" to "Završeno")) { (id, label) ->
                val sel = view == id
                Focusable(onClick = { view = id }, shape = Pill, normal = if (sel) C.Red else C.Card, focused = if (sel) C.Red else C.CardHi,
                    zoom = 1.04f, modifier = Modifier.height(38.dp)) { _ ->
                    Row(Modifier.align(Alignment.Center).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        if (id == "live") Dot(if (sel) C.OnRed else Color(0xFFFF4D55))
                        if (id == "done") Ico(R.drawable.lc_check, 14.dp, if (sel) C.OnRed else C.Soft)
                        T(label, 14.sp, if (sel) C.OnRed else C.Text, FontWeight.SemiBold)
                    }
                }
            }
            item {
                Focusable(onClick = { mine = !mine }, shape = Pill, normal = if (mine) C.Green else C.Card,
                    focused = if (mine) C.Green else C.CardHi, zoom = 1.04f, modifier = Modifier.height(38.dp)) { _ ->
                    Row(Modifier.align(Alignment.Center).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Ico(if (mine) R.drawable.lc_check else R.drawable.lc_tv, 16.dp, if (mine) Color.White else C.Soft)
                        T("Samo moji kanali", 14.sp, if (mine) Color.White else C.Text, FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
            item { Box(Modifier.width(1.dp).height(38.dp).background(C.Line)) }
            items(Sports.SPORTS) { (id, label) ->
                val sel = sport == id
                Focusable(onClick = { sport = id; league = null }, shape = Pill, normal = if (sel) C.Text else C.Card,
                    focused = if (sel) C.Text else C.CardHi, zoom = 1.04f, modifier = Modifier.height(38.dp)) { _ ->
                    Row(Modifier.align(Alignment.Center).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Dot(if (id == "all") Color(0xFFFF4D55) else Sports.sportColor(id))
                        T(label, 14.sp, if (sel) C.Bg else C.Text, FontWeight.Medium)
                    }
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
                Section("Uživo sada", sections.first, live = true, if (firstSec == 0) heroFr else null, { sel = it }) { open(it) }
                Section("Kasnije", sections.second, false, if (firstSec == 1) heroFr else null, { sel = it }) { open(it) }
                Section("Završeno", sections.third, false, if (firstSec == 2) heroFr else null, { sel = it }) { open(it) }
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

    pick?.let { ev -> EventPopup(ev, onDismiss = { pick = null }) { chs, i -> pick = null; play(ev, chs, i) } }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(events is Load.Ok) {
        if (focused || events !is Load.Ok) return@LaunchedEffect
        focused = true
        delay(100)
        if (shown.isNotEmpty()) heroFr.tryFocus() else dayFr.tryFocus()
    }
}

private fun dayName(offset: Int): String = when (offset) {
    -1 -> tr("Jučer"); 0 -> tr("Danas"); 1 -> tr("Sutra")
    else -> SimpleDateFormat("EEE d.", Locale(Look.lang)).format(Date(Sports.dayStart(offset)))
}

@Composable
private fun DayTabs(day: Int, fr: FocusRequester, onDay: (Int) -> Unit) {
    Row(Modifier.clip(Rnd(14.dp)).background(C.Card).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        (-1..3).forEach { d ->
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
    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Ico(R.drawable.lc_tv, 16.dp, AiColor)
        T("Prenose:", 13.sp, C.Soft, FontWeight.SemiBold)
        val l = list
        when {
            !SportsAI.ready -> T("Dodaj AI ključ u Postavke › Sport i AI da vidiš sve TV prenose", 12.sp, C.Muted, maxLines = 1)
            busy -> { androidx.compose.material3.CircularProgressIndicator(color = AiColor, strokeWidth = 2.dp, modifier = Modifier.size(14.dp)); T("AI traži TV prenose…", 12.sp, C.Muted) }
            err != null -> T(err ?: "", 12.sp, C.Muted, maxLines = 1)
            l != null && l.isEmpty() -> T("AI nije našao TV prenose za ovu utakmicu", 12.sp, C.Muted)
            l != null -> LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)) {
                items(l, key = { it.first.country + it.first.name }) { (s, mine) ->
                    val label = (if (s.country.isNotBlank()) s.country + " · " else "") + s.name
                    if (mine.isNotEmpty()) Focusable(modifier = Modifier.height(34.dp), onClick = { onPlay(mine, 0) }, shape = Pill,
                        normal = C.Green.copy(alpha = 0.22f), zoom = 1.06f) { _ ->
                        Row(Modifier.align(Alignment.Center).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Ico(R.drawable.lc_play, 12.dp, C.Green); T(label, 13.sp, C.Text, FontWeight.SemiBold, maxLines = 1)
                        }
                    } else Box(Modifier.height(34.dp).clip(Pill).background(C.Card).border(1.dp, C.Line, Pill).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                        T(label, 13.sp, C.Soft, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** top of Sports: live picture of the match on the left, score and details on the right */
@Composable
private fun SportTop(ev: SportEvent?) {
    val ch = ev?.let { it.channels.firstOrNull() ?: it.ai.firstOrNull() }
    // the picture starts only when you stay on a match for a moment
    var pv by remember { mutableStateOf<Channel?>(null) }
    LaunchedEffect(ch?.url, ev?.live) { pv = null; if (ch != null && ev.live && Look.previewMode != 0) { delay(600); pv = ch } }
    Row(Modifier.fillMaxWidth().height(200.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.fillMaxHeight().aspectRatio(16f / 9f)) {
            val c = pv
            if (c != null) PreviewBox(c, Modifier.fillMaxSize())
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
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight().clip(Rnd(18.dp)).background(C.Card).border(1.dp, C.Line, Rnd(18.dp))) {
            if (ev == null) { Message("Izaberi utakmicu"); return@Box }
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
                    onFocus: (SportEvent) -> Unit = {}, onOpen: (SportEvent) -> Unit) {
    if (list.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (live) Dot(Color(0xFFFF4D55), 9.dp)
            T(title, 18.sp, C.Text, FontWeight.SemiBold)
            T("· ${list.size}", 15.sp, C.Muted)
        }
        AlignStart { LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 12.dp)) {
            itemsIndexed(list, key = { _, e -> e.id }) { i, ev -> EventCard(ev, if (i == 0) fr else null, { onFocus(ev) }) { onOpen(ev) } }
        } }
    }
}

@Composable
private fun EventCard(ev: SportEvent, fr: FocusRequester? = null, onFocus: () -> Unit = {}, onClick: () -> Unit) {
    val none = ev.channels.isEmpty() && ev.ai.isEmpty() && ev.maybe.isEmpty()
    Focusable(modifier = Modifier.width(272.dp).height(138.dp), onClick = onClick, shape = Rnd(18.dp), focusRequester = fr, onFocus = onFocus,
        normal = C.Card, focused = C.CardFocus, zoom = 1.03f) { f ->
        Column(Modifier.fillMaxSize().then(if (none && !f) Modifier.alpha(0.6f) else Modifier).padding(horizontal = 14.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(Sports.sportColor(ev.sport), 6.dp)
                T(tr(ev.league), 12.sp, C.Muted, modifier = Modifier.weight(1f).padding(start = 6.dp))
                StateBadge(ev)
            }
            if (ev.home != null && ev.away != null) {
                TeamLine(ev.home, if (ev.state != "pre") ev.home.score else null)
                TeamLine(ev.away, if (ev.state != "pre") ev.away.score else null)
            } else T(ev.title, 15.sp, C.Text, FontWeight.SemiBold, maxLines = 2)
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
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
                } else T("Prenos nije pronađen u tvojoj listi", 12.sp, C.Muted)
            }
        }
    }
}

@Composable
private fun TeamLine(t: Team, score: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Crest(t, 22.dp)
        T(t.name, 15.sp, C.Text, FontWeight.Medium, modifier = Modifier.weight(1f))
        if (score != null) T(score, 16.sp, C.Text, FontWeight.Bold)
    }
}

/** A match: channels that show it (green, sure), possible channels (amber), reminder, details. */
@Composable
private fun EventPopup(ev: SportEvent, onDismiss: () -> Unit, onPlay: (List<Channel>, Int) -> Unit) {
    val ctx = LocalContext.current
    val details by load(ev.id, ev.state) { if (ev.state != "pre" && !ev.fromGuide) Sports.details(ev) else emptyList() }
    var rev by remember { mutableIntStateOf(0) }
    val title = if (ev.home != null && ev.away != null) "${ev.home.name} – ${ev.away.name}" else ev.title
    androidx.compose.runtime.key(rev) {
        Popup(title, onDismiss, width = 660.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                T(tr(ev.league) + " · " + dayName(((ev.start - Sports.dayStart(0)).let { if (it < 0) it - 86_399_999L else it } / 86_400_000L).toInt()) + " " + hhmm(ev.start), 13.sp, C.Muted, modifier = Modifier.weight(1f))
                StateBadge(ev)
            }
            if (ev.state != "pre" && ev.home?.score != null) T("${ev.home.score} : ${ev.away?.score ?: "-"}", 30.sp, C.Text, FontWeight.Bold)
            (details as? Load.Ok)?.value?.takeIf { it.isNotEmpty() }?.let { list ->
                T("Detalji", 13.sp, C.Muted, FontWeight.SemiBold)
                list.forEach { T(it, 13.sp, C.Soft, maxLines = 2) }
            }
            if (ev.state == "pre") {
                val has = Reminders.has(title, ev.start)
                OptionRow(if (has) "Ukloni podsjetnik" else "Podsjeti me", R.drawable.lc_bell, color = if (has) null else C.Amber) {
                    val ch = ev.channels.firstOrNull() ?: ev.maybe.firstOrNull()
                    val on = Reminders.toggle(Reminder("s${ev.id.hashCode()}", title, ev.start, ch?.url, ch?.name, ch?.logo, ch?.pid, "sport"))
                    Toast.makeText(ctx, tr(if (on) "Podsjetnik postavljen" else "Podsjetnik uklonjen"), Toast.LENGTH_SHORT).show(); rev++
                }
            }
            if (ev.channels.isNotEmpty()) T("PRENOSE · " + tr("u tvojoj listi"), 12.sp, C.Green, FontWeight.Bold)
            ev.channels.forEachIndexed { i, ch -> ChannelChoice(ch, C.Green) { onPlay(ev.channels, i) } }
            var askAi by remember { mutableStateOf<List<Channel>?>(null) }
            var asking by remember { mutableStateOf(false) }
            var aiErr by remember { mutableStateOf<String?>(null) }
            val aiList = askAi ?: ev.ai
            if (aiList.isNotEmpty()) T(SportsAI.prov.name + " · " + tr(if (SportsAI.lastSearch) "procjena s interneta" else "procjena (bez interneta)"), 12.sp, AiColor, FontWeight.Bold)
            aiList.forEachIndexed { i, ch -> ChannelChoice(ch, AiColor) { onPlay(aiList, i) } }
            if (ev.channels.isEmpty() && aiList.isEmpty() && ev.home != null) {
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                if (SportsAI.ready) OptionRow(if (asking) "AI traži…" else "Pitaj AI ko prenosi", R.drawable.lc_sparkles, color = AiColor) {
                    if (asking) return@OptionRow
                    asking = true; aiErr = null
                    scope.launchSafe {
                        try {
                            val all = Sports.guide(ctx, Store.actives(ctx)).all
                            askAi = SportsAI.channelsFor(ev, all).also { if (it.isEmpty()) aiErr = tr("AI nije našao prenos na tvojim kanalima") }
                        } catch (e: Exception) { aiErr = e.message }
                        asking = false
                    }
                } else T("AI pretraga: dodaj AI ključ u Postavke › Sport i AI", 12.sp, C.Muted, maxLines = 2)
                aiErr?.let { T(it, 12.sp, C.Muted, maxLines = 2) }
            }
            if (ev.maybe.isNotEmpty()) T("Mogući prenos (po ligi, nije sigurno)", 12.sp, C.Amber, FontWeight.Bold)
            ev.maybe.forEachIndexed { i, ch -> ChannelChoice(ch, C.Amber) { onPlay(ev.maybe, i) } }
            if (ev.channels.isEmpty() && ev.maybe.isEmpty()) T("Prenos nije pronađen u tvojoj listi", 13.sp, C.Muted)
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
