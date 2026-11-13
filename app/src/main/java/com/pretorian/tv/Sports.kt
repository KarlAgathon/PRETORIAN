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
    val events by load(day, tick, keep = true) { Sports.day(day).also { updated = System.currentTimeMillis() } }
    val guide by load(p?.id) { p?.let { Sports.guide(it) } }
    val heroFr = remember { FocusRequester() }
    val dayFr = remember { FocusRequester() }

    // live scores refresh every minute while you look at today
    LaunchedEffect(day) { while (day == 0) { delay(60_000); tick++ } }

    val g = (guide as? Load.Ok)?.value
    val all: List<SportEvent>? = remember(events, guide, day) {
        (events as? Load.Ok)?.value?.let { ev -> if (g != null) Sports.combine(ev, g.first, g.second, day) else ev }
    }
    val shown = all?.filter { e ->
        (sport == "all" || e.sport == sport) && (league == null || e.league == league) && (!mine || e.channels.isNotEmpty())
    } ?: emptyList()
    val hero = shown.firstOrNull { it.live && it.channels.isNotEmpty() } ?: shown.firstOrNull { it.live }
        ?: shown.firstOrNull { it.state == "pre" && it.channels.isNotEmpty() } ?: shown.firstOrNull { it.state == "pre" }

    fun play(ev: SportEvent, chs: List<Channel>, i: Int) {
        TvMemory.restore = false
        nav.push(Screen.Player(chs.map { PlayItem(it.name, it.url, it.logo, ev.title, it) }, i, live = true))
    }
    fun open(ev: SportEvent) {
        when (ev.channels.size) {
            0 -> Toast.makeText(ctx, tr("Prenos nije pronađen u tvojoj listi"), Toast.LENGTH_SHORT).show()
            1 -> play(ev, ev.channels, 0)
            else -> pick = ev
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 28.dp, end = 28.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // ---------------------------------------------------------------- top
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                T("Sports Center", 26.sp, C.Text, FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val live = all?.count { it.live } ?: 0
                    T(dayName(day) + " · ${all?.size ?: 0} · ", 13.sp, C.Muted)
                    if (live > 0) T("$live " + tr("UŽIVO").lowercase(), 13.sp, Color(0xFFFF4D55), FontWeight.SemiBold)
                    if (updated > 0) T(" · " + hhmm(updated), 13.sp, C.Muted)
                }
            }
            DayTabs(day, dayFr) { day = it; league = null }
        }
        // ---------------------------------------------------------------- filters
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Sports.SPORTS) { (id, label) ->
                    val sel = sport == id
                    Focusable(onClick = { sport = id }, shape = RoundedCornerShape(50), normal = if (sel) C.Text else C.Card,
                        focused = if (sel) C.Text else C.CardHi, zoom = 1.04f, modifier = Modifier.height(38.dp)) { _ ->
                        Row(Modifier.align(Alignment.Center).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Dot(if (id == "all") Color(0xFFFF4D55) else Sports.sportColor(id))
                            T(label, 14.sp, if (sel) C.Bg else C.Text, FontWeight.Medium)
                        }
                    }
                }
            }
            Focusable(onClick = { mine = !mine }, shape = RoundedCornerShape(50), normal = if (mine) C.Green.copy(alpha = 0.25f) else C.Card,
                focused = C.Green.copy(alpha = 0.35f), zoom = 1.04f,
                modifier = Modifier.height(38.dp).border(1.5.dp, C.Green.copy(alpha = if (mine) 1f else 0.5f), RoundedCornerShape(50))) { _ ->
                Row(Modifier.align(Alignment.Center).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Ico(R.drawable.lc_tv, 16.dp, C.Green)
                    T("Samo moji kanali", 14.sp, C.Green, FontWeight.SemiBold)
                }
            }
        }
        if (guide is Load.Loading && p != null) T("Tražim prenose u tvom TV programu…", 12.sp, C.Muted)

        when (val s = events) {
            is Load.Loading -> Box(Modifier.fillMaxWidth().height(260.dp)) { Loading("Učitavam utakmice…") }
            is Load.Err -> Box(Modifier.fillMaxWidth().height(260.dp)) { Message("Greška", s.message) }
            is Load.Ok -> {
                if (shown.isEmpty()) Box(Modifier.fillMaxWidth().height(220.dp)) { Message("Nema utakmica za ovaj dan") }
                if (hero != null) HeroCard(hero, heroFr) { open(hero) }
                val rest = shown.filter { it != hero }
                Section("Uživo sada", rest.filter { it.live }, live = true) { open(it) }
                Section("Kasnije", rest.filter { it.state == "pre" }) { open(it) }
                Section("Završeno", rest.filter { it.state == "post" }) { open(it) }
                // ---------------------------------------------------------- leagues
                val leagues = (all ?: emptyList()).map { it.league to it.leagueAbbr }.distinct().take(20)
                if (leagues.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    T("Lige", 14.sp, C.Muted, FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(leagues) { (name, abbr) ->
                            val sel = league == name
                            Focusable(onClick = { league = if (sel) null else name }, shape = RoundedCornerShape(50),
                                normal = if (sel) C.CardFocus else C.Card, zoom = 1.04f, modifier = Modifier.height(38.dp)) { _ ->
                                Row(Modifier.align(Alignment.Center).padding(start = 6.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(Modifier.size(28.dp).clip(CircleShape).background(C.Red.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                                        T(abbr.take(4), 9.sp, C.Text, FontWeight.Bold)
                                    }
                                    T(name, 14.sp, C.Text, FontWeight.Medium, modifier = Modifier.width(150.dp))
                                }
                            }
                        }
                    }
                }
                T("Rezultati: ESPN. Prenosi: TV program tvoje playliste.", 11.sp, C.Muted)
            }
        }
    }

    pick?.let { ev ->
        Popup(tr("Izaberi kanal") + " · " + ev.title, onDismiss = { pick = null }) {
            ev.channels.forEachIndexed { i, ch ->
                Focusable(modifier = Modifier.fillMaxWidth().height(56.dp), onClick = { pick = null; play(ev, ev.channels, i) },
                    normal = C.Card, zoom = 1.01f) { _ ->
                    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ChannelLogo(ch.logo, ch.name)
                        T(ch.name, 15.sp, C.Text, FontWeight.Medium, modifier = Modifier.weight(1f))
                        Ico(R.drawable.lc_tv, 18.dp, C.Green)
                    }
                }
            }
        }
    }
    LaunchedEffect(hero != null) { if (hero != null) heroFr.tryFocus() else dayFr.tryFocus() }
}

private fun dayName(offset: Int): String = when (offset) {
    -1 -> tr("Jučer"); 0 -> tr("Danas"); 1 -> tr("Sutra")
    else -> SimpleDateFormat("EEE d.", Locale(Look.lang)).format(Date(Sports.dayStart(offset)))
}

@Composable
private fun DayTabs(day: Int, fr: FocusRequester, onDay: (Int) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(14.dp)).background(C.Card).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        (-1..3).forEach { d ->
            val sel = d == day
            Focusable(onClick = { onDay(d) }, focusRequester = if (d == 0) fr else null, normal = if (sel) C.Text else Color.Transparent,
                focused = if (sel) C.Text else C.CardHi, shape = RoundedCornerShape(11.dp), zoom = 1f, modifier = Modifier.height(38.dp)) { _ ->
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
        t.color2?.let { Box(Modifier.size(size * 0.22f, size * 0.5f).clip(RoundedCornerShape(2.dp)).background(it)) }
    }
}

@Composable
private fun StateBadge(ev: SportEvent, big: Boolean = false) {
    val fs = if (big) 15.sp else 13.sp
    when (ev.state) {
        "in" -> Row(Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFE11D2E)).padding(horizontal = 10.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Dot(Color.White, 7.dp); T(ev.clock ?: tr("UŽIVO"), fs, Color.White, FontWeight.Bold)
        }
        "post" -> T(ev.clock ?: "FT", fs, C.Muted, FontWeight.SemiBold)
        else -> T(hhmm(ev.start), fs, C.Soft, FontWeight.SemiBold)
    }
}

@Composable
private fun HeroCard(ev: SportEvent, fr: FocusRequester, onClick: () -> Unit) {
    Focusable(modifier = Modifier.fillMaxWidth().height(196.dp), onClick = onClick, focusRequester = fr, shape = RoundedCornerShape(22.dp),
        normal = C.Card, focused = C.CardFocus, zoom = 1.01f) { _ ->
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxHeight().padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Sports.sportColor(ev.sport).copy(alpha = 0.25f)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                        T(ev.leagueAbbr, 11.sp, C.Text, FontWeight.Bold)
                    }
                    T(ev.league + (ev.round?.let { " · $it" } ?: ""), 13.sp, C.Muted, modifier = Modifier.weight(1f))
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
                StateBadge(ev, big = true)
                if (ev.state != "pre" && ev.home?.score != null) T("${ev.home?.score} : ${ev.away?.score ?: "-"}", 40.sp, C.Text, FontWeight.Bold)
                else if (ev.state == "pre") T(hhmm(ev.start), 34.sp, C.Text, FontWeight.Bold)
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(C.Line))
            Column(Modifier.width(240.dp).fillMaxHeight().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                T("PRENOSE", 11.sp, C.Muted, FontWeight.Bold)
                if (ev.channels.isEmpty()) T("Prenos nije pronađen u tvojoj listi", 13.sp, C.Muted, maxLines = 3)
                ev.channels.take(3).forEach { ch ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.Green.copy(alpha = 0.12f))
                        .border(1.dp, C.Green.copy(alpha = 0.6f), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 5.dp),
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
    Focusable(modifier = Modifier.width(272.dp).height(138.dp), onClick = onClick, shape = RoundedCornerShape(18.dp),
        normal = C.Card, focused = C.CardFocus, zoom = 1.03f) { _ ->
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(Sports.sportColor(ev.sport), 6.dp)
                T(ev.league, 12.sp, C.Muted, modifier = Modifier.weight(1f).padding(start = 6.dp))
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
