package com.pretorian.tv

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Catch-up: channels that keep an archive at the provider, the days back, and the past shows to play. */
@Composable
fun CatchupScreen(nav: Nav) {
    val ctx = LocalContext.current
    val chans by load(Unit) {
        Store.actives(ctx).filter { it.type == SourceType.XTREAM }.flatMap { p -> try { Repo.allChannels(p) } catch (e: Exception) { emptyList() } }
            .filter { it.archive > 0 }.distinctBy { it.url }
    }
    var q by remember { mutableStateOf("") }
    var ch by remember { mutableStateOf<Channel?>(null) }
    var day by remember { mutableIntStateOf(0) }
    val first = remember { FocusRequester() }
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(320.dp).fillMaxHeight().padding(start = 16.dp, end = 10.dp, top = 22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            T("Catch-up", 26.sp, C.Text, FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
            T("Kanali s arhivom", 13.sp, C.Muted, modifier = Modifier.padding(start = 6.dp))
            Field("Traži kanal", q, { q = it }, Modifier.fillMaxWidth())
            when (val s = chans) {
                is Load.Loading -> Loading("Učitavam kanale…")
                is Load.Err -> Message("Greška", s.message)
                is Load.Ok -> {
                    val list = s.value.filter { q.isBlank() || Sports.norm(it.name).contains(Sports.norm(q)) }
                    if (s.value.isEmpty()) Message("Nijedan kanal nema arhivu", "Provider ne nudi catch-up za tvoje kanale")
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                        itemsIndexed(list, key = { _, c -> c.url }) { i, c ->
                            val sel = ch?.url == c.url
                            Focusable(modifier = Modifier.fillMaxWidth().height(Rows.channel), onClick = { ch = c; day = 0 }, onFocus = { ch = c },
                                focusRequester = if (i == 0) first else null, normal = if (sel) C.CardFocus else C.Card, zoom = 1.01f) { f ->
                                Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    ChannelLogo(c.logo, c.name)
                                    T(c.name, 14.sp, if (f || sel) C.Text else C.Soft, if (sel) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                                    T(trf("%s d", c.archive), 11.sp, C.RedText)
                                }
                            }
                        }
                    }
                    LaunchedEffect(list.isNotEmpty()) { if (list.isNotEmpty()) first.tryFocus() }
                }
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(C.Line))
        Box(Modifier.weight(1f).fillMaxHeight().padding(start = 20.dp, end = 24.dp, top = 22.dp)) {
            val c = ch
            if (c == null) Message("Izaberi kanal", "Emisije iz arhive možeš gledati i kad su prošle")
            else Archive(c, day, { day = it }, nav)
        }
    }
}

@Composable
private fun Archive(c: Channel, day: Int, onDay: (Int) -> Unit, nav: Nav) {
    val ctx = LocalContext.current
    var shown by remember { mutableStateOf<Channel?>(null) }
    LaunchedEffect(c.url) { delay(350); shown = c }
    val ch = shown ?: return
    val p = Store.byId(ctx, ch.pid) ?: return
    val list by load(ch.url) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Xtream(p).archive(ch.id) } }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChannelLogo(ch.logo, ch.name)
            T(ch.name, 20.sp, C.Text, FontWeight.Bold, modifier = Modifier.weight(1f))
            T(trf("Zadnjih %s dana", ch.archive), 12.sp, C.Muted)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items((0 until ch.archive.coerceAtMost(14)).toList()) { d ->
                val sel = d == day
                val label = when (d) { 0 -> tr("Danas"); 1 -> tr("Jučer"); else -> SimpleDateFormat("EEE d.", Locale(Look.lang)).format(Date(Sports.dayStart(-d))) }
                Focusable(onClick = { onDay(d) }, shape = Pill, normal = if (sel) C.Red else C.Card, focused = if (sel) C.Red else C.CardHi,
                    zoom = 1.04f, modifier = Modifier.height(38.dp)) { _ ->
                    T(label, 14.sp, if (sel) C.OnRed else C.Text, FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center).padding(horizontal = 14.dp))
                }
            }
        }
        when (val s = list) {
            is Load.Loading -> Loading("Učitavam arhivu…")
            is Load.Err -> Message("Arhiva nije dostupna", s.message)
            is Load.Ok -> {
                val now = System.currentTimeMillis()
                val from = Sports.dayStart(-day); val to = Sports.dayStart(-day + 1)
                val items = s.value.filter { it.start in from until to && it.end <= now && it.archived }.sortedByDescending { it.start }
                if (items.isEmpty()) Message("Nema emisija u arhivi za ovaj dan")
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(items, key = { it.start }) { e ->
                        Focusable(modifier = Modifier.fillMaxWidth().height(66.dp), onClick = {
                            Preview.stop()
                            val url = Xtream(p).timeshiftUrl(ch.id, e)
                            nav.push(Screen.Player(listOf(PlayItem(e.title, url, ch.logo, ch.name + " · " + e.times())), 0, live = false))
                        }, normal = C.Card, zoom = 1.01f) { f ->
                            Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Ico(R.drawable.lc_history, 18.dp, C.RedText)
                                Column(Modifier.weight(1f)) {
                                    T(e.title, 15.sp, if (f) C.Text else C.Soft, FontWeight.SemiBold)
                                    T(e.times(), 12.sp, C.Muted)
                                }
                                T("OK = gledaj", 11.sp, C.Muted)
                            }
                        }
                    }
                }
            }
        }
    }
}
