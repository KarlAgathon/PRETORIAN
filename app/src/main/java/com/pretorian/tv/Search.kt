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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** One search result. */
sealed class Hit {
    data class Ch(val ch: Channel) : Hit()
    data class Prog(val ch: Channel, val e: EpgItem) : Hit()
    data class Movie(val v: Vod) : Hit()
    data class Show(val s: SeriesItem) : Hit()
}

/** Programmes of the next hours of every channel, for searching shows (kept for 3 hours). */
object ProgIndex {
    private var at = 0L
    private var data: List<Pair<Channel, EpgItem>> = emptyList()
    suspend fun get(ctx: android.content.Context): List<Pair<Channel, EpgItem>> = withContext(Dispatchers.IO) {
        if (data.isNotEmpty() && System.currentTimeMillis() - at < 3 * 3600_000L) return@withContext data
        val pls = Store.actives(ctx)
        val all = pls.flatMap { p -> try { Repo.allChannels(p) } catch (e: Exception) { emptyList() } }
        val byEpg = all.filter { !it.epg.isNullOrBlank() }.groupBy { it.epg!!.lowercase() }
        val now = System.currentTimeMillis()
        val progs = pls.filter { it.type == SourceType.XTREAM }.flatMap { p -> Xmltv.read(Xtream(p).xmltvUrl(), byEpg.keys, now - 3600_000L, now + 30 * 3600_000L) }
        data = progs.mapNotNull { pr -> byEpg[pr.channel]?.firstOrNull()?.let { it to EpgItem(pr.title, pr.desc, pr.start, pr.end) } }
        at = now
        data
    }
}

private fun norm(s: String) = Sports.norm(s)

@Composable
fun SearchScreen(nav: Nav) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    var pick by remember { mutableStateOf<Hit?>(null) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(q) { delay(500); query = q.trim() }

    val channels by load(Unit) { Store.actives(ctx).flatMap { p -> try { Repo.allChannels(p) } catch (e: Exception) { emptyList() } } }
    val movies by load(Unit) { Store.actives(ctx).flatMap { p -> try { Repo.catalog(p, "vod") } catch (e: Exception) { emptyList() } } }
    val shows by load(Unit) { Store.actives(ctx).flatMap { p -> try { Repo.catalog(p, "series") } catch (e: Exception) { emptyList() } } }
    val progs by load(query.length >= 3 && (filter == "all" || filter == "prog")) {
        if (query.length >= 3 && (filter == "all" || filter == "prog")) ProgIndex.get(ctx) else emptyList()
    }

    val nq = norm(query)
    val hits: List<Hit> = remember(nq, channels, movies, shows, progs) {
        if (nq.length < 2) emptyList() else {
            val words = nq.split(' ').filter { it.isNotBlank() }
            fun ok(name: String) = norm(name).let { n -> words.all { n.contains(it) } }
            val out = ArrayList<Hit>()
            (channels as? Load.Ok)?.value?.filter { ok(it.name) || it.num.toString() == query }?.distinctBy { it.url }?.take(60)?.forEach { out.add(Hit.Ch(it)) }
            (progs as? Load.Ok)?.value?.filter { ok(it.second.title) }?.sortedBy { it.second.start }?.take(60)?.forEach { out.add(Hit.Prog(it.first, it.second)) }
            fun vod(o: JSONObject) = Vod(o.str("id"), o.str("n"), o.str("i").orNullIfBlank(), o.str("r").orNullIfBlank(), o.str("u"))
            (movies as? Load.Ok)?.value?.filter { ok(it.str("n")) }?.take(60)?.forEach { out.add(Hit.Movie(vod(it))) }
            (shows as? Load.Ok)?.value?.filter { ok(it.str("n")) }?.take(60)?.forEach {
                out.add(Hit.Show(SeriesItem(it.str("id"), it.str("n"), it.str("i").orNullIfBlank(), null, it.str("r").orNullIfBlank())))
            }
            out
        }
    }
    val shown = hits.filter { h ->
        when (filter) { "ch" -> h is Hit.Ch; "prog" -> h is Hit.Prog; "vod" -> h is Hit.Movie; "series" -> h is Hit.Show; else -> true }
    }

    Column(Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, top = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            T("Pretraga", 26.sp, C.Text, FontWeight.Bold)
            Field("Traži kanal, film, seriju, emisiju ili broj", q, { q = it }, Modifier.weight(1f).focusRequester(fr), onDone = { query = q.trim() })
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val counts = mapOf("all" to hits.size, "ch" to hits.count { it is Hit.Ch }, "prog" to hits.count { it is Hit.Prog },
                "vod" to hits.count { it is Hit.Movie }, "series" to hits.count { it is Hit.Show })
            items(listOf("all" to "Sve", "ch" to "Kanali", "prog" to "Emisije", "vod" to "Filmovi", "series" to "Serije")) { (id, label) ->
                val sel = filter == id
                Focusable(onClick = { filter = id }, shape = Pill, normal = if (sel) C.Red else C.Card, focused = if (sel) C.Red else C.CardHi,
                    zoom = 1.04f, modifier = Modifier.height(38.dp)) { _ ->
                    T(tr(label) + " · ${counts[id] ?: 0}", 14.sp, if (sel) C.OnRed else C.Text, FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 14.dp))
                }
            }
        }
        val busy = channels is Load.Loading || movies is Load.Loading || shows is Load.Loading || (progs is Load.Loading && query.length >= 3)
        if (busy) T("Učitavam listu za pretragu…", 12.sp, C.Muted)
        if (nq.length < 2) Message("Upiši bar 2 slova", "Tastatura daljinskog ili Google TV aplikacija na telefonu")
        else if (shown.isEmpty() && !busy) Message("Ništa nije pronađeno")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 30.dp)) {
            items(shown, key = { h -> when (h) { is Hit.Ch -> "c" + h.ch.url; is Hit.Prog -> "p" + h.ch.url + h.e.start; is Hit.Movie -> "m" + h.v.id; is Hit.Show -> "s" + h.s.id } }) { h ->
                Focusable(modifier = Modifier.fillMaxWidth().height(64.dp), onClick = {
                    when (h) { is Hit.Movie -> nav.push(Screen.MovieDetail(h.v)); is Hit.Show -> nav.push(Screen.SeriesDetail(h.s)); else -> pick = h }
                }, onMenu = { pick = h }, normal = C.Card, zoom = 1.01f) { f ->
                    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        when (h) {
                            is Hit.Ch -> { Ico(R.drawable.lc_tv, 18.dp, C.RedText); ChannelLogo(h.ch.logo, h.ch.name)
                                Column(Modifier.weight(1f)) { T(h.ch.name, 15.sp, C.Text, FontWeight.SemiBold); T(tr("Kanal") + " ${h.ch.num}", 12.sp, C.Muted) } }
                            is Hit.Prog -> { Ico(R.drawable.lc_clock, 18.dp, C.RedText)
                                Column(Modifier.weight(1f)) {
                                    T(h.e.title, 15.sp, C.Text, FontWeight.SemiBold)
                                    T(h.e.times() + " · " + h.ch.name, 12.sp, C.Muted)
                                }
                                if (h.e.start <= System.currentTimeMillis() && h.e.end > System.currentTimeMillis()) Badge(tr("UŽIVO"), C.Danger, androidx.compose.ui.graphics.Color.White)
                                if (Reminders.has(h.e.title, h.e.start)) Ico(R.drawable.lc_bell, 15.dp, C.Amber) }
                            is Hit.Movie -> { Ico(R.drawable.lc_film, 18.dp, C.RedText)
                                Box(Modifier.width(34.dp).fillMaxHeight().padding(vertical = 4.dp).clip(Rnd(6.dp)).background(C.Tile)) {
                                    if (h.v.poster != null) AsyncImage(model = h.v.poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                                Column(Modifier.weight(1f)) { T(h.v.name, 15.sp, C.Text, FontWeight.SemiBold); T("Film", 12.sp, C.Muted) } }
                            is Hit.Show -> { Ico(R.drawable.lc_monitor, 18.dp, C.RedText)
                                Box(Modifier.width(34.dp).fillMaxHeight().padding(vertical = 4.dp).clip(Rnd(6.dp)).background(C.Tile)) {
                                    if (h.s.cover != null) AsyncImage(model = h.s.cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                                Column(Modifier.weight(1f)) { T(h.s.name, 15.sp, C.Text, FontWeight.SemiBold); T("Serija", 12.sp, C.Muted) } }
                        }
                    }
                }
            }
        }
    }
    pick?.let { h -> HitActions(h, nav) { pick = null } }
    LaunchedEffect(Unit) { fr.tryFocus() }
}

/** What you can do with a result: watch, favourites, show in guide, remind, hide, open. */
@Composable
private fun HitActions(h: Hit, nav: Nav, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var fav by remember { mutableStateOf<Channel?>(null) }
    if (fav != null) { FavPicker(fav!!) { fav = null; onDismiss() }; return }
    fun guide(ch: Channel) {
        TvMemory.group = GroupRef(ch.pid, Category(ALL_GROUP, tr("Svi kanali"))); TvMemory.channel = -1
        Prefs.put("tv_view", "guide"); onDismiss(); nav.root(Screen.Tv)
    }
    when (h) {
        is Hit.Ch -> Popup(h.ch.name, onDismiss) {
            OptionRow("Gledaj sada", R.drawable.lc_play, color = C.Green) { onDismiss(); playChannels(nav, listOf(h.ch), 0) }
            OptionRow("Dodaj u favorite", R.drawable.lc_star, color = C.Amber) { fav = h.ch }
            OptionRow("Prikaži u vodiču", R.drawable.lc_calendar_clock) { guide(h.ch) }
            val ovr = Ovr(ctx, h.ch.pid)
            OptionRow(if (ovr.isChHidden(h.ch)) "Prikaži kanal" else "Sakrij kanal", R.drawable.lc_eye_off) {
                ovr.toggleChHidden(h.ch); Toast.makeText(ctx, tr("Spremljeno"), Toast.LENGTH_SHORT).show(); onDismiss()
            }
        }
        is Hit.Prog -> Popup(h.e.title, onDismiss) {
            T(h.e.times() + " · " + h.ch.name, 14.sp, C.Soft)
            h.e.desc?.let { T(it, 13.sp, C.Muted, maxLines = 4) }
            val live = h.e.start <= System.currentTimeMillis()
            OptionRow(if (live) "Gledaj sada" else "Gledaj kanal sada", R.drawable.lc_play, color = C.Green) { onDismiss(); playChannels(nav, listOf(h.ch), 0) }
            if (!live) {
                val has = Reminders.has(h.e.title, h.e.start)
                OptionRow(if (has) "Ukloni podsjetnik" else "Podsjeti me", R.drawable.lc_bell, color = C.Amber) {
                    val on = Reminders.toggle(Reminder("r${h.e.start}${h.e.title.hashCode()}", h.e.title, h.e.start, h.ch.url, h.ch.name, h.ch.logo, h.ch.pid, "tv"))
                    Toast.makeText(ctx, tr(if (on) "Podsjetnik postavljen" else "Podsjetnik uklonjen"), Toast.LENGTH_SHORT).show(); onDismiss()
                }
            }
            OptionRow("Prikaži u vodiču", R.drawable.lc_calendar_clock) { guide(h.ch) }
            OptionRow("Dodaj kanal u favorite", R.drawable.lc_star) { fav = h.ch }
        }
        is Hit.Movie -> Popup(h.v.name, onDismiss) { OptionRow("Otvori", R.drawable.lc_info) { onDismiss(); nav.push(Screen.MovieDetail(h.v)) } }
        is Hit.Show -> Popup(h.s.name, onDismiss) { OptionRow("Otvori", R.drawable.lc_info) { onDismiss(); nav.push(Screen.SeriesDetail(h.s)) } }
    }
}
