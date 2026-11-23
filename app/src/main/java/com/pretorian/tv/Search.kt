package com.pretorian.tv

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.foundation.layout.size
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
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
    fun clear() { at = 0L }
    private var data: List<Pair<Channel, EpgItem>> = emptyList()
    suspend fun get(ctx: android.content.Context): List<Pair<Channel, EpgItem>> = withContext(Dispatchers.IO) {
        if (data.isNotEmpty() && System.currentTimeMillis() - at < 3 * 3600_000L) return@withContext data
        val pls = Store.actives(ctx)
        val all = pls.flatMap { p -> try { Repo.allChannels(p) } catch (e: Exception) { emptyList() } }
        val byEpg = all.filter { !it.epg.isNullOrBlank() }.groupBy { it.epg!!.lowercase() }
        val now = System.currentTimeMillis()
        val keys = byEpg.keys
        // from the guide kept on the box when it is there, otherwise downloaded as before
        val progs = pls.filter { it.type == SourceType.XTREAM }.flatMap { p ->
            val src = GuideDb.srcOf(p)
            if (GuideDb.has(src)) GuideDb.range(src, now - 3600_000L, now + 30 * 3600_000L, keys)
            else Xmltv.read(Xtream(p).xmltvUrl(), keys, now - 3600_000L, now + 30 * 3600_000L)
        }
        data = progs.mapNotNull { pr -> byEpg[pr.channel]?.firstOrNull()?.let { it to EpgItem(pr.title, pr.desc, pr.start, pr.end) } }
        byId = data.groupBy({ it.first.epg?.lowercase() ?: "" }, { it.second }).mapValues { (_, l) -> l.sortedBy { it.start } }
        at = now
        data
    }
    private var byId: Map<String, List<EpgItem>> = emptyMap()

    /** the provider's full guide (XMLTV) for one channel: used when its short guide is empty */
    suspend fun forChannel(ctx: android.content.Context, ch: Channel): List<EpgItem> {
        val id = ch.epg?.lowercase()?.ifBlank { null } ?: return emptyList()
        get(ctx)
        return byId[id] ?: emptyList()
    }
}

private fun norm(s: String) = Sports.norm(s)

/** Everything you can search, split in parts that load one after another: channels, movies, series, programmes. */
object SearchIndex {
    /** [db] set = not a real entry: this part is searched in the box's database (movies / series), nothing is kept in memory */
    class Item(val n: String, val hit: Hit, val num: String? = null, val db: String? = null)
    private val parts = HashMap<String, Pair<String, List<Item>>>()
    fun clear() = synchronized(parts) { parts.clear() }

    private suspend fun part(ctx: android.content.Context, name: String, build: suspend (List<Playlist>) -> List<Item>): List<Item> = withContext(Dispatchers.IO) {
        val pls = Store.actives(ctx)
        val k = pls.joinToString { it.id }
        parts[name]?.takeIf { it.first == k }?.let { return@withContext it.second }
        val out = build(pls)
        parts[name] = k to out
        out
    }

    suspend fun channels(ctx: android.content.Context) = part(ctx, "ch") { pls ->
        // hidden groups and hidden channels stay hidden in search too
        pls.flatMap { p -> val o = Ovr(ctx, p.id); try { Repo.allChannels(p).filter { !o.isHidden("live", it.group) && !o.isChHidden(it) && !Parental.hides(it.name) && !Parental.hides(it.group) } } catch (e: Exception) { emptyList() } }.distinctBy { it.url }
            .map { Item(norm(it.name), Hit.Ch(it), it.num.toString()) }
    }
    suspend fun movies(ctx: android.content.Context) = part(ctx, "vod") { pls ->
        if (inDb(pls, "vod")) return@part listOf(Item("", Hit.Movie(Vod("", "", null, null, "")), db = "vod"))
        pls.flatMap { p -> val o = Ovr(ctx, p.id); try { Repo.catalog(p, "vod").filter { !o.isHidden("vod", it.str("c")) && !Parental.hides(it.str("n")) } } catch (e: Throwable) { emptyList() } }.map {
            Item(norm(it.str("n")), Hit.Movie(Vod(it.str("id"), it.str("n"), it.str("i").orNullIfBlank(), it.str("r").orNullIfBlank(), it.str("u"))))
        }
    }
    suspend fun shows(ctx: android.content.Context) = part(ctx, "series") { pls ->
        if (inDb(pls, "series")) return@part listOf(Item("", Hit.Show(SeriesItem("", "", null, null, null)), db = "series"))
        pls.flatMap { p -> val o = Ovr(ctx, p.id); try { Repo.catalog(p, "series").filter { !o.isHidden("series", it.str("c")) && !Parental.hides(it.str("n")) } } catch (e: Throwable) { emptyList() } }.map {
            Item(norm(it.str("n")), Hit.Show(SeriesItem(it.str("id"), it.str("n"), it.str("i").orNullIfBlank(), null, it.str("r").orNullIfBlank())))
        }
    }
    suspend fun progs(ctx: android.content.Context) = part(ctx, "prog") { _ ->
        ProgIndex.get(ctx).map { (ch, e) -> Item(norm(e.title), Hit.Prog(ch, e)) }
    }
    /** built again next time (Parental controls changed) */
    fun reset() = parts.clear()
    /** prepare everything quietly (called once after start) */
    suspend fun get(ctx: android.content.Context) { channels(ctx); movies(ctx); shows(ctx) }

    /** every playlist with movies / series has them in the box's database (downloaded now if needed) */
    private suspend fun inDb(pls: List<Playlist>, kind: String): Boolean {
        val xs = pls.filter { it.type == SourceType.XTREAM && (if (kind == "vod") it.vod else it.series) }
        if (xs.isEmpty() || xs.size != pls.count { it.type == SourceType.XTREAM }) return false
        return try { xs.all { CatalogDb.prepare(it, kind) } } catch (e: Throwable) { false }
    }

    /** search in the database: hidden categories and parental filter as before, names that start with the text first */
    private fun findDb(kind: String, words: List<String>, max: Int): List<Hit> {
        val ctx = NewContent.appCtx ?: return emptyList()
        val pls = Store.actives(ctx).filter { it.type == SourceType.XTREAM }
        val ovr = pls.associate { it.id to Ovr(ctx, it.id) }
        val rows = CatalogDb.search(pls.map { it.id }, kind, words, max * 6)
            .filter { r -> ovr[r.pl]?.isHidden(kind, r.c) != true && !Parental.hides(r.n) }
        return rows.sortedBy { if (it.k.startsWith(words[0])) 0 else 1 }.take(max).map { r ->
            if (kind == "vod") Hit.Movie(Vod(r.id, r.n, r.i, r.r, r.u)) else Hit.Show(SeriesItem(r.id, r.n, r.i, null, r.r))
        }
    }

    /** Runs on a background thread. Exact channel number first, then names that start with the text, then the rest. */
    suspend fun find(list: List<Item>, q: String, max: Int = 60): List<Hit> = withContext(Dispatchers.Default) {
        val t = q.trim()
        val words = norm(t).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return@withContext emptyList()
        list.singleOrNull()?.db?.let { kind -> return@withContext try { withContext(Dispatchers.IO) { findDb(kind, words, max) } } catch (e: Throwable) { emptyList() } }
        val exact = ArrayList<Hit>(); val starts = ArrayList<Hit>(); val other = ArrayList<Hit>()
        for (it in list) {
            if (it.num != null && it.num == t) { exact.add(it.hit); continue }
            if (words.all { w -> it.n.contains(w) }) {
                if (it.n.startsWith(words[0])) { if (starts.size < max) starts.add(it.hit) } else if (other.size < max) other.add(it.hit)
            }
            if (exact.size + starts.size >= max) break
        }
        (exact + starts + other).take(max)
    }
}

@Composable
fun SearchScreen(nav: Nav) {
    OnyxSearch(nav)
}

/** The search session: a new Search from the menu starts empty on TV; Back from an opened result finds it as it was. */
object SearchSession {
    var q by mutableStateOf("")
    var cat by mutableStateOf("tv")
    fun reset() { q = ""; cat = "tv" }
}

/** What you can do with a result: watch, favourites, show in guide, remind, hide, open. */
@Composable
fun HitActions(h: Hit, nav: Nav, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var fav by remember { mutableStateOf<Channel?>(null) }
    if (fav != null) { FavPicker(fav!!) { fav = null; onDismiss() }; return }
    when (h) {
        is Hit.Ch -> Popup(h.ch.name, onDismiss) {
            OptionRow("Gledaj sada", R.drawable.lc_play, color = C.Green) { onDismiss(); playChannels(nav, listOf(h.ch), 0) }
            OptionRow("Dodaj u favorite", R.drawable.lc_star, color = C.Amber) { fav = h.ch }
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
            OptionRow("Dodaj kanal u favorite", R.drawable.lc_star) { fav = h.ch }
        }
        is Hit.Movie -> Popup(h.v.name, onDismiss) { OptionRow("Otvori", R.drawable.lc_info) { onDismiss(); nav.push(Screen.MovieDetail(h.v)) } }
        is Hit.Show -> Popup(h.s.name, onDismiss) { OptionRow("Otvori", R.drawable.lc_info) { onDismiss(); nav.push(Screen.SeriesDetail(h.s)) } }
    }
}


/** The last 10 searches of the chosen category (TV, Movies, Series, Sport, Settings), newest first. */
/** Search opened inside TV, Movies, Series or Settings looks only there (owner); null = the general search from Home. */
object SearchOnly { var only: String? = null }

/** Opens the search limited to one part of the app, starting empty. */
fun openScopedSearch(nav: Nav, only: String?) {
    SearchOnly.only = only; SearchMem.q = ""; SearchMem.open = only; SearchSession.reset(); if (only != null) SearchSession.cat = only
    nav.push(Screen.Search)
}

object SearchHistory {
    var rev by androidx.compose.runtime.mutableIntStateOf(0)
    /** each part keeps its own recent searches */
    private fun k() = "search_hist_" + (SearchOnly.only ?: "all")
    fun list() = Prefs.str(k()).split('\n').filter { it.isNotBlank() }
    fun add(text: String) {
        val t = text.trim(); if (t.isEmpty()) return
        Prefs.put(k(), (listOf(t) + list().filter { !it.trim().equals(t, true) }).take(10).joinToString("\n")); rev++
    }
    fun remove(text: String) { Prefs.put(k(), list().filter { it != text }.joinToString("\n")); rev++ }
    fun clear() { Prefs.put(k(), ""); rev++ }
}

/** "Recent searches": OK searches again, long OK removes one, "Clear all" at the end. */
@Composable
fun SearchRecent(onPick: (String) -> Unit) {
    val items = remember(SearchHistory.rev, SearchMem.open) { SearchHistory.list() }
    var ask by remember { mutableStateOf(false) }
    // the category the history belongs to (the last one chosen)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = 6.dp)) {
        val cats = listOf("tv" to "TV", "vod" to "Filmovi", "series" to "Serije", "sport" to "Sport", "settings" to "Postavke")
        items(cats.size) { i ->
            val (id, label) = cats[i]
            val sel = (SearchMem.open ?: "tv") == id
            Focusable(onClick = { SearchMem.open = id; SearchHistory.rev++ }, shape = Pill, normal = if (sel) Color(0x33FFFFFF) else Color.Transparent, focused = C.Red,
                zoom = 1.04f, modifier = Modifier.height(38.dp)) { f ->
                T(label, 14.sp, if (f) C.OnRed else C.Text, if (sel) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.align(Alignment.Center).padding(horizontal = 14.dp))
            }
        }
    }
    if (ask) Popup("Obriši historiju", { ask = false }) {
        T("Obrisati zadnje pretrage za ovu kategoriju?", 14.sp, C.Soft, maxLines = 2)
        OptionRow("Obriši", R.drawable.lc_trash_2) { SearchHistory.clear(); ask = false }
        OptionRow("Odustani", R.drawable.lc_x) { ask = false }
    }
    if (items.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        T("Nedavne pretrage", 15.sp, C.Text, FontWeight.Bold, modifier = Modifier.weight(1f))
        T("OK traži ponovo · dugi OK briše", 11.sp, C.Muted, maxLines = 1)
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
        items(items.size) { i ->
            val h = items[i]
            Focusable(onClick = { SearchHistory.add(h); onPick(h) }, onMenu = { SearchHistory.remove(h) }, shape = Pill, normal = C.Card, focused = C.Red,
                zoom = 1.04f, modifier = Modifier.height(40.dp).widthIn(max = 260.dp)) { f ->
                Row(Modifier.align(Alignment.Center).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Ico(R.drawable.lc_clock, 14.dp, if (f) C.OnRed else C.Muted)
                    T(h, 14.sp, if (f) C.OnRed else C.Text, FontWeight.SemiBold, maxLines = 1)
                    Ico(R.drawable.lc_x, 13.dp, if (f) C.OnRed else C.Muted)
                }
            }
        }
        item {
            Focusable(onClick = { ask = true }, shape = Pill, normal = Color.Transparent, focused = C.Red, zoom = 1.04f, modifier = Modifier.height(40.dp)) { f ->
                Row(Modifier.align(Alignment.Center).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Ico(R.drawable.lc_trash_2, 14.dp, if (f) C.OnRed else C.Muted); T("Obriši historiju", 13.sp, if (f) C.OnRed else C.Soft)
                }
            }
        }
    }
}


@Composable
fun ToggleRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Focusable(onClick = { onChange(!on) }, shape = Rnd(12.dp), normal = Color.Transparent, focused = C.Red, zoom = 1f, modifier = Modifier.fillMaxWidth().height(48.dp)) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            T(label, 15.sp, if (f) C.OnRed else C.Text, FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Toggle(on, f, height = 24.dp)
        }
    }
}
