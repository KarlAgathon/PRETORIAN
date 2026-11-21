package com.pretorian.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.Color
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
    if (Look.dawn) { DawnCatchup(nav); return }
    val ctx = LocalContext.current
    val chans by load(Unit) {
        Store.actives(ctx).filter { it.type == SourceType.XTREAM || (it.type == SourceType.M3U && Tech.m3uCatchup) }
            .flatMap { p -> try { Repo.allChannels(p).filter { Catchups.has(p, it) } } catch (e: Exception) { emptyList() } }
            .distinctBy { it.url }
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
    val list by load(ch.url) { archiveOf(p, ch) }
    ArchiveList(list, ch, p, day, onDay, nav)
}

/** Past shows of a channel that the provider keeps (its archive table, else the guide). */
private suspend fun archiveOf(p: Playlist, ch: Channel): List<EpgItem> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val x = Xtream(p)
            val now = System.currentTimeMillis()
            // M3U archive: the past shows come from the guide (no archive table at the provider)
            val a = if (p.type != SourceType.XTREAM) emptyList() else try { x.archive(ch.id) } catch (e: Exception) { emptyList() }
            if (a.any { it.end <= now && it.archived }) a
            else {
                // the archive table is empty: take past shows from the guide (own EPG sources first, then the provider's XMLTV)
                val days = ch.archive.coerceIn(1, 14)
                val own = EpgSources.urls(p.id).firstNotNullOfOrNull { url ->
                    val id = ch.epg?.lowercase()?.ifBlank { null } ?: return@firstNotNullOfOrNull null
                    try { Xmltv.read(url, setOf(id), now - days * 86_400_000L, now).filter { it.end <= now }.ifEmpty { null } } catch (e: Exception) { null }
                }?.map { EpgItem(it.title, it.desc, it.start, it.end, null, true) }
                own ?: (if (p.type == SourceType.XTREAM) x.archiveFromGuide(ch.epg, days) else emptyList()).ifEmpty { throw Exception(tr("Provider za ovaj kanal ne šalje listu arhiviranih emisija.")) }
            }
        }

@Composable
private fun ArchiveList(list: Load<List<EpgItem>>, ch: Channel, p: Playlist, day: Int, onDay: (Int) -> Unit, nav: Nav) {
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
                if (items.isEmpty()) Message("Nema emisija u arhivi za ovaj dan", "Probaj drugi dan gore")
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(items, key = { it.start }) { e ->
                        Focusable(modifier = Modifier.fillMaxWidth().height(66.dp), onClick = {
                            Preview.stop()
                            val url = Catchups.url(p, ch, e) ?: return@Focusable
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

/* ------------------------------------------------------------ New Dawn catch-up */

/**
 * New Dawn catch-up: channels with an archive on the left (all / favourites, search), on the right the channel with its days,
 * the past shows and the chosen show (first OK = small picture, second OK = full screen), Watch from start · Restart · Live TV.
 */
@Composable
private fun DawnCatchup(nav: Nav) {
    val ctx = LocalContext.current
    val chans by load(Unit) {
        Store.actives(ctx).filter { it.type == SourceType.XTREAM || (it.type == SourceType.M3U && Tech.m3uCatchup) }
            .flatMap { p -> try { Repo.allChannels(p).filter { Catchups.has(p, it) } } catch (e: Exception) { emptyList() } }
            .distinctBy { it.url }
    }
    var q by remember { mutableStateOf("") }
    var askQ by remember { mutableStateOf(false) }
    var favOnly by remember { mutableStateOf(false) }
    var ch by remember { mutableStateOf<Channel?>(null) }
    var day by remember { mutableIntStateOf(0) }
    val first = remember { FocusRequester() }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { Preview.stop(); PreviewSel.ch = null } }
    Row(Modifier.fillMaxSize().padding(start = 34.dp, end = 38.dp, top = 6.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.width(360.dp).fillMaxHeight().dawnGlass().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(false to "Svi kanali", true to "Favoriti").forEach { (fav, label) ->
                    Focusable(onClick = { favOnly = fav }, shape = Rnd(10.dp), normal = if (favOnly == fav) Color(0x33FFFFFF) else Color.Transparent,
                        focused = Dawn.pill, focusBorderColor = Color.Transparent, zoom = 1f, modifier = Modifier.weight(1f).height(42.dp)) { f ->
                        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Ico(if (fav) R.drawable.lc_star else R.drawable.lc_tv, 18.dp, if (f) Dawn.onPill else Color.White)
                            T(label, 14.sp, if (f) Dawn.onPill else Color.White, FontWeight.SemiBold)
                        }
                    }
                }
            }
            Focusable(onClick = { askQ = true }, onMenu = { q = "" }, shape = Pill, normal = Color.Transparent, focused = Dawn.pill, focusBorderColor = Color.Transparent,
                zoom = 1f, modifier = Modifier.fillMaxWidth().height(40.dp).border(1.dp, Color(0x4DFFFFFF), Pill)) { f ->
                Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Ico(R.drawable.lc_search, 16.dp, if (f) Dawn.onPill else Color.White)
                    T(q.ifBlank { tr("Traži kanal") }, 14.sp, if (f) Dawn.onPill else C.Soft)
                }
            }
            when (val s = chans) {
                is Load.Loading -> Loading("Učitavam kanale…")
                is Load.Err -> Message("Greška", s.message)
                is Load.Ok -> {
                    val list = s.value.filter { (q.isBlank() || Sports.norm(it.name).contains(Sports.norm(q))) && (!favOnly || Favs.inAny(ctx, it)) }
                    if (s.value.isEmpty()) Message("Nijedan kanal nema arhivu", "Provider ne nudi catch-up za tvoje kanale")
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        itemsIndexed(list, key = { _, c -> c.url }) { i, c ->
                            val sel = ch?.url == c.url
                            Focusable(modifier = Modifier.fillMaxWidth().height(48.dp), onClick = { ch = c; day = 0 }, onFocus = { if (ch?.url != c.url) { ch = c; day = 0; PreviewSel.ch = null; Preview.stop() } },
                                focusRequester = if (i == 0) first else null, shape = Rnd(10.dp), normal = if (sel) Color(0x26FFFFFF) else Color.Transparent,
                                focused = Dawn.pill, focusBorderColor = Color.Transparent, zoom = 1f) { f ->
                                Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    T("%03d".format(c.num), 14.sp, if (f) Dawn.onPill else C.Soft)
                                    ChannelLogo(c.logo, c.name)
                                    T(c.name, 15.sp, if (f) Dawn.onPill else Color.White, if (f) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    LaunchedEffect(list.isNotEmpty()) { if (list.isNotEmpty()) first.tryFocus() }
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight().dawnGlass().padding(14.dp)) {
            val c = ch
            if (c == null) Message("Izaberi kanal", "Emisije iz arhive možeš gledati i kad su prošle")
            else DawnArchive(c, day, { day = it }, nav)
        }
    }
    if (askQ) TextDialog("Traži kanal", "Naziv", q, onDismiss = { askQ = false }) { t -> q = t.trim(); askQ = false }
}

@Composable
private fun DawnArchive(c: Channel, day: Int, onDay: (Int) -> Unit, nav: Nav) {
    val ctx = LocalContext.current
    var shown by remember { mutableStateOf<Channel?>(null) }
    LaunchedEffect(c.url) { delay(350); shown = c }
    val ch = shown ?: return
    val p = Store.byId(ctx, ch.pid) ?: return
    val list by load(ch.url) { archiveOf(p, ch) }
    var pick by remember(ch.url, day) { mutableStateOf<EpgItem?>(null) }
    var fav by remember { mutableStateOf(false) }
    val curShow = nowOf(ch, 0)
    fun play(e: EpgItem) {
        Preview.stop(); PreviewSel.ch = null
        val url = Catchups.url(p, ch, e) ?: return
        nav.push(Screen.Player(listOf(PlayItem(e.title, url, ch.logo, ch.name + " · " + e.times())), 0, live = false))
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ChannelLogo(ch.logo, ch.name)
            Column(Modifier.weight(1f)) {
                T(ch.name, 20.sp, Color.White, FontWeight.Bold)
                T("Catch-up / Vraćanje programa", 13.sp, C.Soft)
            }
            Focusable(onClick = { fav = true }, shape = Rnd(10.dp), normal = Color.Transparent, focused = Dawn.pill, focusBorderColor = Color.Transparent, zoom = 1f,
                modifier = Modifier.height(40.dp).border(1.dp, Color(0x4DFFFFFF), Rnd(10.dp))) { f ->
                Row(Modifier.align(Alignment.Center).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Ico(R.drawable.lc_star, 16.dp, if (f) Dawn.onPill else Color.White); T("Dodaj u favorite", 13.sp, if (f) Dawn.onPill else Color.White)
                }
            }
        }
        // days: the oldest on the left, today on the right (only as far back as the provider keeps)
        val days = (0 until ch.archive.coerceIn(1, 14)).toList().reversed()
        val dState = androidx.compose.foundation.lazy.rememberLazyListState()
        LaunchedEffect(ch.url) { dState.scrollToItem(days.lastIndex.coerceAtLeast(0)) }
        LazyRow(state = dState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(days) { d ->
                val sel = d == day
                val label = if (d == 0) tr("Danas") else SimpleDateFormat("EEE", Locale(Look.lang)).format(Date(Sports.dayStart(-d)))
                val date = SimpleDateFormat("d MMM", Locale(Look.lang)).format(Date(Sports.dayStart(-d)))
                Focusable(onClick = { onDay(d) }, shape = Rnd(10.dp), normal = if (sel) Color(0xD9DADADE) else Color.Transparent, focused = Dawn.pill,
                    focusBorderColor = Color.Transparent, zoom = 1f, modifier = Modifier.width(120.dp).height(50.dp).border(1.dp, Color(0x4DFFFFFF), Rnd(10.dp))) { f ->
                    val fg = if (f || sel) Dawn.onPill else Color.White
                    Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Ico(R.drawable.lc_calendar, 16.dp, fg)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) { T(label, 13.sp, fg, FontWeight.SemiBold); T(date, 12.sp, fg) }
                    }
                }
            }
        }
        when (val s = list) {
            is Load.Loading -> Loading("Učitavam arhivu…")
            is Load.Err -> Message("Arhiva nije dostupna", s.message)
            is Load.Ok -> {
                val now = System.currentTimeMillis()
                val from = Sports.dayStart(-day); val to = Sports.dayStart(-day + 1)
                val items = s.value.filter { it.start in from until to && it.end <= now && it.archived }.sortedBy { it.start }
                if (items.isEmpty()) Message("Nema emisija u arhivi za ovaj dan", "Probaj drugi dan gore")
                else Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LazyColumn(Modifier.weight(0.48f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(items, key = { it.start }) { e ->
                            val on = pick?.start == e.start
                            Focusable(modifier = Modifier.fillMaxWidth().height(46.dp), onFocus = { if (pick?.start != e.start) { pick = e; PreviewSel.ch = null; Preview.stop() } },
                                onClick = {
                                    // first OK = small picture, second OK on the same show = full screen
                                    val url = Catchups.url(p, ch, e)
                                    if (url != null && PreviewSel.ch?.url == url) play(e) else if (url != null) PreviewSel.ch = ch.copy(url = url, name = e.title)
                                }, shape = Rnd(10.dp), normal = if (on) Color(0x26FFFFFF) else Color.Transparent, focused = Dawn.pill, focusBorderColor = Color.Transparent, zoom = 1f) { f ->
                                val fg = if (f) Dawn.onPill else Color.White
                                Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    T(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(e.start)), 14.sp, fg)
                                    T(e.title, 15.sp, fg, if (f) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                                    T(trf("%s min", ((e.end - e.start) / 60_000).toInt()), 12.sp, if (f) fg else C.Soft)
                                    Ico(if (f) R.drawable.lc_play else R.drawable.lc_history, 16.dp, fg)
                                }
                            }
                        }
                    }
                    Column(Modifier.weight(0.52f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val e = pick ?: items.last()
                        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).dawnGlass(12.dp), contentAlignment = Alignment.Center) {
                            val pc = PreviewSel.ch
                            if (pc != null) PreviewBox(pc, Modifier.fillMaxSize()) else Ico(R.drawable.lc_play, 46.dp, Color(0x99DADADE))
                        }
                        T(e.title, 19.sp, Color.White, FontWeight.Bold)
                        T(e.times() + " · " + trf("%s min", ((e.end - e.start) / 60_000).toInt()), 13.sp, C.Soft)
                        cleanDesc(e.desc)?.let { T(it, 13.sp, C.Soft, maxLines = 3) }
                        Spacer(Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            @Composable fun B(label: String, icon: Int, primary: Boolean, onClick: () -> Unit) {
                                Focusable(onClick = onClick, shape = Rnd(10.dp), normal = if (primary) Color(0xD9DADADE) else Color.Transparent, focused = Dawn.pill,
                                    focusBorderColor = Color.Transparent, zoom = 1f, modifier = Modifier.weight(1f).height(44.dp).border(1.dp, Color(0x4DFFFFFF), Rnd(10.dp))) { f ->
                                    val fg = if (f || primary) Dawn.onPill else Color.White
                                    Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Ico(icon, 16.dp, fg); T(label, 13.sp, fg, FontWeight.SemiBold)
                                    }
                                }
                            }
                            B(tr("Gledaj od početka"), R.drawable.lc_play, true) { play(e) }
                            // restart: the show that is on now, from its beginning
                            B(tr("Ispočetka"), R.drawable.lc_rotate_ccw, false) {
                                val cur = curShow
                                if (cur != null) Catchups.url(p, ch, cur)?.let { u -> Preview.stop(); PreviewSel.ch = null
                                    nav.push(Screen.Player(listOf(PlayItem(cur.title, u, ch.logo, ch.name + " · " + cur.times())), 0, live = false)) }
                                else android.widget.Toast.makeText(ctx, tr("Program koji sada ide nije poznat"), android.widget.Toast.LENGTH_SHORT).show()
                            }
                            B(tr("Uživo"), R.drawable.lc_tv, false) { Preview.stop(); PreviewSel.ch = null; playChannels(nav, listOf(ch), 0) }
                        }
                    }
                }
            }
        }
    }
    if (fav) FavPicker(ch) { fav = false }
}
