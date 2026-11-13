package com.pretorian.tv

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
    val combined by produceState(Sports.lastCombined[day], evs, g, day) {
        Sports.lastCombined[day]?.let { value = it }
        if (evs == null) { if (value == null) matching = true; return@produceState }
        if (value == null) value = evs
        if (g == null) { matching = true; return@produceState }
        matching = true
        val r = try { withContext(Dispatchers.Default) { Sports.combine(evs, g, day) } } catch (e: Exception) { evs }
        Sports.lastCombined[day] = r
        value = r
        matching = false
    }
    val guideFailed = guide is Load.Err
    val all: List<SportEvent>? = combined
    val shown = remember(all, sport, league, mine, view) {
        all?.filter { e ->
            (sport == "all" || e.sport == sport) && (league == null || e.league == league) && (!mine || e.channels.isNotEmpty() || e.maybe.isNotEmpty()) &&
                (view == "all" || (view == "live" && e.live) || (view == "done" && e.state == "post"))
        } ?: emptyList()
    }
    // the big card: the most important match that is on now, else the next one
    // the big card is always a real match from the results, never something found only in a TV guide
    val hero = remember(shown, view) {
        val real = shown.filter { !it.fromGuide }
        if (view == "done") null else (real.filter { it.live }.minByOrNull { Sports.priority(it) }
            ?: real.filter { it.state == "pre" }.minWithOrNull(compareBy({ Sports.priority(it) }, { it.start })))
    }
    val hiddenByMine = remember(all, sport, league, view, mine) {
        if (!mine) 0 else (all ?: emptyList()).count { e ->
            (sport == "all" || e.sport == sport) && (league == null || e.league == league) &&
                (view == "all" || (view == "live" && e.live) || (view == "done" && e.state == "post")) && e.channels.isEmpty() && e.maybe.isEmpty()
        }
    }
    val sections = remember(shown, hero) {
        val rest = shown.filter { it != hero }
        Triple(rest.filter { it.live }, rest.filter { it.state == "pre" }, rest.filter { it.state == "post" })
    }
    val leagues = remember(all, sport) { (all ?: emptyList()).filter { sport == "all" || it.sport == sport }.map { it.league to it.leagueAbbr }.distinct().take(20) }
    val liveCount = remember(all) { all?.count { it.live } ?: 0 }

    fun play(ev: SportEvent, chs: List<Channel>, i: Int) {
        TvMemory.restore = false
        nav.push(Screen.Player(chs.map { PlayItem(it.name, it.url, it.logo, ev.title, it) }, i, live = true))
    }
    fun open(ev: SportEvent) {
        if (ev.channels.size == 1 && ev.maybe.isEmpty() && ev.live) play(ev, ev.channels, 0) else pick = ev
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 28.dp, end = 28.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
        // ---------------------------------------------------------------- filters (one scrolling row, nothing is hidden)
        LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Focusable(onClick = { mine = !mine }, shape = Pill, normal = if (mine) C.Green.copy(alpha = 0.25f) else C.Card,
                    focused = C.Green.copy(alpha = 0.35f), zoom = 1.04f,
                    modifier = Modifier.height(38.dp).border(1.5.dp, C.Green.copy(alpha = if (mine) 1f else 0.5f), Pill)) { _ ->
                    Row(Modifier.align(Alignment.Center).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Ico(R.drawable.lc_tv, 16.dp, C.Green)
                        T("Samo moji kanali", 14.sp, C.Green, FontWeight.SemiBold)
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
        }
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
                if (hero != null) HeroCard(hero, heroFr) { open(hero) }
                Section("Uživo sada", sections.first, live = true) { open(it) }
                Section("Kasnije", sections.second) { open(it) }
                Section("Završeno", sections.third) { open(it) }
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
                g?.let { T(it.stats, 11.sp, C.Muted) }
            }
        }
    }

    pick?.let { ev -> EventPopup(ev, onDismiss = { pick = null }) { chs, i -> pick = null; play(ev, chs, i) } }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(events is Load.Ok) {
        if (focused || events !is Load.Ok) return@LaunchedEffect
        focused = true
        if (hero != null) heroFr.tryFocus() else dayFr.tryFocus()
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
            Column(Modifier.width(240.dp).fillMaxHeight().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                T("PRENOSE", 11.sp, C.Muted, FontWeight.Bold)
                if (ev.channels.isEmpty() && ev.maybe.isEmpty()) T("Prenos nije pronađen u tvojoj listi", 13.sp, C.Muted, maxLines = 3)
                if (ev.channels.isEmpty()) ev.maybe.take(3).forEach { ch ->
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

@Composable
private fun HeroTeam(t: Team) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Crest(t, 38.dp)
        Kit(t, 20.dp)
        T(t.name, 21.sp, C.Text, FontWeight.Bold, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Section(title: String, list: List<SportEvent>, live: Boolean = false, onOpen: (SportEvent) -> Unit) {
    if (list.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (live) Dot(Color(0xFFFF4D55), 9.dp)
            T(title, 18.sp, C.Text, FontWeight.SemiBold)
            T("· ${list.size}", 15.sp, C.Muted)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 12.dp)) {
            items(list, key = { it.id }) { ev -> EventCard(ev) { onOpen(ev) } }
        }
    }
}

@Composable
private fun EventCard(ev: SportEvent, onClick: () -> Unit) {
    Focusable(modifier = Modifier.width(272.dp).height(138.dp), onClick = onClick, shape = Rnd(18.dp),
        normal = C.Card, focused = C.CardFocus, zoom = 1.03f) { _ ->
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
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
