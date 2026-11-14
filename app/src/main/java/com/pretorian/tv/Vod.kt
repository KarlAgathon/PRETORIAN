package com.pretorian.tv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun MoviesScreen(nav: Nav) = VodBrowser(nav, series = false)

@Composable
fun SeriesScreen(nav: Nav) = VodBrowser(nav, series = true)

private data class Poster(val id: String, val name: String, val image: String?, val rating: String?, val url: String?, val isNew: Boolean,
                          val open: () -> Unit, val play: (() -> Unit)? = null, val added: Long = 0)

private const val G_CONTINUE = "__continue__"
private const val G_NEW = "__new__"
private const val G_MYLIST = "__mylist__"

/** Rows of the category column: playlist headers (when several are on), "Continue watching", "New", then the categories. */
private sealed class VRow {
    data class Header(val p: Playlist, val open: Boolean) : VRow()
    data class Group(val cat: Category, val count: Int = 0) : VRow()
    data class Title(val text: String) : VRow()
    val id get() = when (this) { is Header -> "h:" + p.id; is Group -> "g:" + cat.id; is Title -> "t:" + text }
}

@Composable
private fun VodBrowser(nav: Nav, series: Boolean) {
    if (Look.wave && !Look.age) { WaveBrowser(nav, series); return }
    VodClassic(nav, series)
}

/** Posters of a category (or of "Continue watching" / "New"). */
private suspend fun postersOf(ctx: Context, nav: Nav, p: Playlist, g: Category, series: Boolean): List<Poster> {
    val kind = if (series) "series" else "vod"
    return when (g.id) {
        G_CONTINUE -> Watch.continueList(ctx).filter { (it.series != null) == series }.map { pr ->
            val play = { nav.push(Screen.Player(listOf(PlayItem(pr.title, pr.url, pr.poster, null, null, pr.series, pr.season, pr.episode)), 0, live = false)) }
            Poster(pr.url, pr.title, pr.poster, null, pr.url, false, play, play)
        }
        G_NEW -> NewContent.items(p.id, kind).map { o ->
            if (series) { val si = SeriesItem(o.str("id"), o.str("n"), o.str("i").orNullIfBlank(), null, o.str("r").orNullIfBlank())
                Poster(si.id, si.name, si.cover, si.rating, null, true, { nav.push(Screen.SeriesDetail(si)) }) }
            else { val v = Vod(o.str("id"), o.str("n"), o.str("i").orNullIfBlank(), o.str("r").orNullIfBlank(), o.str("u"))
                Poster(v.id, v.name, v.poster, v.rating, v.url, true, { nav.push(Screen.MovieDetail(v)) }, { nav.push(Screen.Player(listOf(PlayItem(v.name, v.url, v.poster, null, null, v.name)), 0, live = false)) }) }
        }
        else -> if (series) Repo.series(p, g.id).map { s -> Poster(s.id, s.name, s.cover, s.rating, null, NewContent.isNew(p.id, kind, s.id), open = { nav.push(Screen.SeriesDetail(s)) }, added = s.added) }
        else Repo.movies(p, g.id).map { v -> Poster(v.id, v.name, v.poster, v.rating, v.url, NewContent.isNew(p.id, kind, v.id), open = { nav.push(Screen.MovieDetail(v)) }, play = { nav.push(Screen.Player(listOf(PlayItem(v.name, v.url, v.poster, null, null, v.name)), 0, live = false)) }, added = v.added) }
    }
}

/** New Wave / New Age: categories as chips on top, posters across the whole screen (New Age: a row per category). */
private val SORTS = listOf("Kao kod providera", "Zadnje dodano", "Naziv A–Ž", "Naziv Ž–A", "Ocjena")

@Composable
private fun WaveBrowser(nav: Nav, series: Boolean) {
    val ctx = LocalContext.current
    var rev by remember { mutableIntStateOf(0) }
    val p = remember(rev) { Store.active(ctx) } ?: run { NoPlaylist(nav); return }
    val pls = remember(rev) { Store.actives(ctx) }
    if (p.type != SourceType.XTREAM) { Message(if (series) "Serije nisu dostupne" else "Filmovi nisu dostupni", "Filmovi i serije rade s Xtream Codes playlistom."); return }
    val kind = if (series) "series" else "vod"
    val ovr = remember(p.id) { Ovr(ctx, p.id) }
    val cats by load(p.id, series, rev, NewContent.rev, Watch.rev, keep = true) {
        val out = ArrayList<Pair<Category, Int>>()
        val cont = Watch.continueList(ctx).filter { (it.series != null) == series }
        if (cont.isNotEmpty()) out.add(Category(G_CONTINUE, tr("Nastavi gledati")) to cont.size)
        val nw = NewContent.items(p.id, kind)
        if (nw.isNotEmpty()) out.add(Category(G_NEW, tr("Novo dodano")) to nw.size)
        ovr.groups(kind, if (series) Repo.seriesGroups(p) else Repo.vodGroups(p)).forEach { out.add(it to NewContent.countCat(p.id, kind, it.id)) }
        out
    }
    var group by remember { mutableStateOf<Category?>(null) }
    var sort by remember { mutableIntStateOf(Prefs.long("vod_sort", 0).toInt().coerceIn(0, SORTS.size - 1)) }
    var sortMenu by remember { mutableStateOf(false) }
    var groupMenu by remember { mutableStateOf<Category?>(null) }
    val gate = remember { PinGate() }
    val firstChip = remember { FocusRequester() }
    Column(Modifier.fillMaxSize().padding(top = 14.dp)) {
        Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            T(if (series) "Serije" else "Filmovi", 24.sp, C.Text, FontWeight.Bold)
            group?.let { T("›  " + it.name, 18.sp, C.Soft, FontWeight.SemiBold, modifier = Modifier.weight(1f)) } ?: Spacer(Modifier.weight(1f))
            if (pls.size > 1) pls.forEach { pl ->
                val sel = pl.id == p.id
                Focusable(onClick = { Store.open(ctx, pl.id); group = null; rev++ }, shape = Pill, normal = if (sel) C.CardFocus else C.Card, zoom = 1.04f, modifier = Modifier.height(34.dp)) { _ ->
                    T(pl.name, 13.sp, C.Text, if (sel) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.align(Alignment.Center).padding(horizontal = 12.dp))
                }
            }
            Focusable(onClick = { sortMenu = true }, shape = Pill, normal = C.Card, zoom = 1.04f, modifier = Modifier.height(34.dp)) { _ ->
                Row(Modifier.align(Alignment.Center).padding(start = 12.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Ico(R.drawable.lc_list, 14.dp, C.Soft)
                    T(tr("Sortiraj") + ": " + tr(SORTS[sort]), 13.sp, C.Text, maxLines = 1)
                    Ico(R.drawable.lc_chevron_down, 14.dp, C.Soft)
                }
            }
        }
        when (val s = cats) {
            is Load.Loading -> Loading("Učitavam kategorije…")
            is Load.Err -> Message("Greška", s.message)
            is Load.Ok -> {
                LazyRow(Modifier.padding(top = 10.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 24.dp)) {
                    if (Look.age) item {
                        val sel = group == null
                        Focusable(onClick = { group = null }, focusRequester = firstChip, shape = Pill, normal = if (sel) C.Red else C.Card,
                            focused = if (sel) C.Red else C.CardHi, zoom = 1.04f, modifier = Modifier.height(36.dp)) { _ ->
                            T("Sve kategorije", 13.sp, if (sel) C.OnRed else C.Text, FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center).padding(horizontal = 14.dp))
                        }
                    }
                    itemsIndexed(s.value, key = { _, c -> c.first.id }) { i, (c, n) ->
                        val sel = group?.id == c.id
                        val locked = ovr.isLocked(kind, c.id)
                        Focusable(onClick = { gate.run("$kind:${c.id}", locked) { group = c } },
                            onMenu = if (Lock.simple || c.id == G_CONTINUE || c.id == G_NEW) null else ({ groupMenu = c }),
                            focusRequester = if (!Look.age && i == 0) firstChip else null, shape = Pill,
                            normal = if (sel) C.Red else C.Card, focused = if (sel) C.Red else C.CardHi, zoom = 1.04f, modifier = Modifier.height(36.dp)) { _ ->
                            Row(Modifier.align(Alignment.Center).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (c.id == G_CONTINUE) Ico(R.drawable.lc_history, 14.dp, if (sel) C.OnRed else C.RedText)
                                if (c.id == G_NEW) Ico(R.drawable.lc_sparkles, 14.dp, if (sel) C.OnRed else C.Amber)
                                if (locked) Ico(R.drawable.lc_lock, 13.dp, if (sel) C.OnRed else C.Muted)
                                T(c.name + if (n > 0) " · $n" else "", 13.sp, if (sel) C.OnRed else C.Text, FontWeight.SemiBold)
                            }
                        }
                    }
                }
                LaunchedEffect(Unit) {
                    if (!Look.age && group == null) group = s.value.firstOrNull { it.first.id != G_CONTINUE }?.first ?: s.value.firstOrNull()?.first
                    firstChip.tryFocus()
                }
                val g = group
                if (g == null && Look.age) AgeRows(nav, p, s.value.map { it.first }.take(14), series) { group = it }
                else if (g != null) {
                    val st by load(p.id, g.id, series, Watch.rev, NewContent.rev) { postersOf(ctx, nav, p, g, series) }
                    when (val ps = st) {
                        is Load.Loading -> Loading()
                        is Load.Err -> Message("Nije se učitalo", ps.message)
                        is Load.Ok -> {
                            val sorted = remember(ps.value, sort) { when (sort) {
                                1 -> ps.value.sortedByDescending { it.added }
                                2 -> ps.value.sortedBy { cleanTitle(it.name).first.lowercase() }
                                3 -> ps.value.sortedByDescending { cleanTitle(it.name).first.lowercase() }
                                4 -> ps.value.sortedByDescending { it.rating?.toDoubleOrNull() ?: 0.0 }
                                else -> ps.value } }
                            if (sorted.isEmpty()) Message("Kategorija je prazna") else PosterGrid(g.name, sorted, g.id == G_NEW, kind, p.id, wide = true)
                        }
                    }
                }
            }
        }
    }
    if (sortMenu) Popup("Sortiraj", { sortMenu = false }) {
        SORTS.forEachIndexed { i, label ->
            OptionRow(label, if (i == sort) R.drawable.lc_check else R.drawable.lc_list, color = if (i == sort) C.Green else null) {
                sort = i; Prefs.put("vod_sort", i.toLong()); sortMenu = false
            }
        }
    }
    groupMenu?.let { g ->
        val list = (cats as? Load.Ok)?.value?.map { it.first }?.filter { it.id != G_CONTINUE && it.id != G_NEW } ?: emptyList()
        GroupOptions(kind, g, ovr, list, onDismiss = { groupMenu = null }) { close -> rev++; if (close) groupMenu = null }
    }
    PinGateDialog(gate)
}

/** New Age: a row of posters for each category, "See all" opens the whole category. */
@Composable
private fun AgeRows(nav: Nav, p: Playlist, cats: List<Category>, series: Boolean, onAll: (Category) -> Unit) {
    val ctx = LocalContext.current
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(start = 24.dp, bottom = 30.dp, top = 6.dp)) {
        items(cats, key = { it.id }) { c ->
            val st by load(p.id, c.id, series) { postersOf(ctx, nav, p, c, series).take(20) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.padding(end = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(c.name, 18.sp, C.Text, FontWeight.Bold, modifier = Modifier.weight(1f))
                    Focusable(onClick = { onAll(c) }, shape = Pill, normal = C.Card, zoom = 1.04f, modifier = Modifier.height(32.dp)) { _ ->
                        T("Prikaži sve", 12.sp, C.RedText, FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center).padding(horizontal = 12.dp))
                    }
                }
                when (val s = st) {
                    is Load.Ok -> LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 24.dp)) {
                        items(s.value, key = { it.id }) { pt ->
                            Focusable(modifier = Modifier.width(132.dp), onClick = pt.open, normal = Color.Transparent, focused = C.Card, zoom = 1.06f) { f ->
                                Column(Modifier.padding(4.dp)) {
                                    Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(Rnd(12.dp)).background(C.Tile)) {
                                        if (pt.image != null) AsyncImage(model = pt.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                        else T(cleanTitle(pt.name).first, 12.sp, C.Muted, maxLines = 4, modifier = Modifier.align(Alignment.Center).padding(8.dp))
                                        Row(Modifier.padding(6.dp)) {
                                            if (pt.isNew) Badge(tr("NOVO"), Color(0xFFF59E0B), Color(0xFF0B0D12))
                                            pt.rating?.toDoubleOrNull()?.takeIf { it > 0 }?.let { Badge("★ " + String.format("%.1f", it), Color(0xCC0B0D12), Color(0xFFF5C518)) }
                                        }
                                    }
                                    T(pt.name, 12.sp, if (f) C.Text else C.Soft, maxLines = 2, modifier = Modifier.padding(top = 5.dp))
                                }
                            }
                        }
                    }
                    is Load.Loading -> Box(Modifier.fillMaxWidth().height(60.dp)) { Loading("") }
                    else -> T("Nije se učitalo", 12.sp, C.Muted)
                }
            }
        }
    }
}

@Composable
private fun VodClassic(nav: Nav, series: Boolean) {
    val ctx = LocalContext.current
    var rev by remember { mutableIntStateOf(0) }
    val p = remember(rev) { Store.active(ctx) } ?: run { NoPlaylist(nav); return }
    val pls = remember(rev) { Store.actives(ctx) }
    val kind = if (series) "series" else "vod"
    val ovr = remember(p.id) { Ovr(ctx, p.id) }
    val rowsState by load(p.id, series, rev, NewContent.rev, Watch.rev, MyList.rev, keep = true) {
        val out = ArrayList<VRow>()
        if (pls.size > 1) out.add(VRow.Header(p, true))
        val cont = Watch.continueList(ctx).filter { (it.series != null) == series && (it.pid.isBlank() || it.pid == p.id) }
        if (Look.age) out.add(VRow.Title(tr("MOJE")))
        if (cont.isNotEmpty()) out.add(VRow.Group(Category(G_CONTINUE, tr("Nastavi gledati")), cont.size))
        if (Look.age) out.add(VRow.Group(Category(G_MYLIST, tr("Moja lista")), MyList.items(kind).size))
        val nw = NewContent.items(p.id, kind)
        if (nw.isNotEmpty()) out.add(VRow.Group(Category(G_NEW, tr("Novo dodano")), nw.size))
        if (Look.age) out.add(VRow.Title(tr("KATEGORIJE PROVIDERA")))
        if (p.type == SourceType.XTREAM) ovr.groups(kind, if (series) Repo.seriesGroups(p) else Repo.vodGroups(p))
            .forEach { out.add(VRow.Group(it, NewContent.countCat(p.id, kind, it.id))) }
        pls.drop(1).forEach { out.add(VRow.Header(it, false)) }
        out
    }
    var focusedGroup by remember { mutableStateOf<Category?>(null) }
    var group by remember { mutableStateOf<Category?>(null) }
    var groupMenu by remember { mutableStateOf<Category?>(null) }
    val gate = remember { PinGate() }
    val firstGroup = remember { FocusRequester() }
    val gWrap = remember { Wrap() }
    val gState = rememberLazyListState()
    WrapEffect(gWrap, gState)
    LaunchedEffect(focusedGroup) {
        val g = focusedGroup ?: return@LaunchedEffect
        delay(400)
        gate.run("$kind:${g.id}", ovr.isLocked(kind, g.id)) { group = g }
    }

    Row(Modifier.fillMaxSize()) {
        Column(if (Look.age) Modifier.padding(start = 24.dp, top = 6.dp, bottom = 14.dp).width(290.dp).fillMaxHeight().clip(Rnd(18.dp)).background(C.Rail)
                .border(1.dp, C.Line, Rnd(18.dp)).padding(6.dp)
            else Modifier.width(290.dp).fillMaxHeight().padding(start = 16.dp, end = 10.dp, top = 22.dp)) {
            if (!Look.age) T(if (series) "Serije" else "Filmovi", 26.sp, C.Text, FontWeight.Bold, modifier = Modifier.padding(start = 10.dp, bottom = 12.dp))
            when (val s = rowsState) {
                is Load.Loading -> Loading("Učitavam kategorije…")
                is Load.Err -> Message("Greška", s.message)
                is Load.Ok -> {
                    val all = s.value
                    val firstIdx = all.indexOfFirst { it !is VRow.Title }.coerceAtLeast(0)
                    LazyColumn(state = gState, verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                        itemsIndexed(all, key = { _, r -> r.id }) { i, r ->
                            val m = Modifier.fillMaxWidth().then(if (Look.age) Modifier.heightIn(min = Rows.group + 4.dp) else Modifier.height(Rows.group)).wrapKeys(i, all.size, gWrap)
                            val fr = gWrap.requester(i, if (i == firstIdx) firstGroup else null)
                            when (r) {
                                is VRow.Title -> androidx.compose.material3.Text(r.text, style = androidx.compose.ui.text.TextStyle(fontFamily = Inter, fontSize = 11.sp,
                                    color = C.Muted, fontWeight = FontWeight.Bold, letterSpacing = 2.sp), modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 2.dp))
                                is VRow.Header -> Focusable(modifier = m.height(Rows.group + 6.dp), focusRequester = fr, normal = C.Card, zoom = 1f,
                                    onClick = { if (!r.open) { Store.open(ctx, r.p.id); group = null; rev++ } }) { _ ->
                                    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Ico(if (r.open) R.drawable.lc_chevron_down else R.drawable.lc_chevron_right, 16.dp, C.RedText)
                                        T(r.p.name, 15.sp, C.Text, FontWeight.Bold, modifier = Modifier.weight(1f))
                                    }
                                }
                                is VRow.Group -> {
                                    val g = r.cat
                                    val selected = group?.id == g.id
                                    val locked = ovr.isLocked(kind, g.id)
                                    val special = g.id == G_CONTINUE || g.id == G_NEW
                                    val shape = Rnd(12.dp)
                                    Focusable(
                                        modifier = m.then(if (Look.groupFrame && !Look.age) Modifier.border(if (selected) 1.5.dp else 1.dp,
                                            C.Red.copy(alpha = if (selected) 0.9f else 0.28f), shape) else Modifier),
                                        shape = shape,
                                        onClick = { gate.run("$kind:${g.id}", locked) { group = g } },
                                        onFocus = { focusedGroup = g },
                                        onMenu = if (Lock.simple || special) null else ({ groupMenu = g }),
                                        focusRequester = fr,
                                        normal = if (selected) (if (Look.age) C.CardHi else C.Red.copy(alpha = 0.28f)) else Color.Transparent,
                                        focused = if (Look.age) C.Red else null,
                                        zoom = 1.0f,
                                    ) { gf ->
                                        val onW = Look.age && gf
                                        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (g.id == G_CONTINUE) Ico(R.drawable.lc_history, 15.dp, C.RedText)
                                            if (g.id == G_NEW) Ico(R.drawable.lc_sparkles, 15.dp, C.Amber)
                                            T(g.name, Rows.font, if (onW) C.OnRed else if (selected) C.Text else C.Soft, if (selected || special) FontWeight.SemiBold else FontWeight.Normal,
                                                modifier = Modifier.weight(1f).padding(vertical = 4.dp), maxLines = if (Look.age) 2 else 1)
                                            if (Look.age && r.count > 0 && g.id != G_NEW) T("${r.count}", 12.sp, if (onW) C.OnRed else C.Muted)
                                            if (locked) Ico(R.drawable.lc_lock, 15.dp, C.Muted)
                                            if (!Look.age && r.count > 0 && g.id != G_CONTINUE) Box(Modifier.clip(Pill).background(if (g.id == G_NEW) C.Amber else C.Danger).padding(horizontal = 7.dp, vertical = 1.dp)) {
                                                T(if (g.id == G_NEW) "${r.count}" else trf("%s nova", r.count), 10.sp, Color.White, FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    LaunchedEffect(Unit) { firstGroup.tryFocus() }
                }
            }
        }
        if (!Look.age) Box(Modifier.width(1.dp).fillMaxHeight().background(C.Line))
        Box(Modifier.weight(1f).fillMaxHeight()) {
            val g = group
            if (g == null) {
                Message("Izaberi kategoriju", "Drži OK na kategoriji za opcije")
            } else {
                val state by load(p.id, g.id, series, Watch.rev, NewContent.rev, MyList.rev) {
                    when (g.id) {
                        G_CONTINUE -> Watch.continueList(ctx).filter { (it.series != null) == series }.map { pr ->
                            val play = { nav.push(Screen.Player(listOf(PlayItem(pr.title, pr.url, pr.poster, null, null, pr.series, pr.season, pr.episode)), 0, live = false)) }
                            Poster(pr.url, pr.title, pr.poster, null, pr.url, false, play, play)
                        }
                        G_NEW -> NewContent.items(p.id, kind).map { o ->
                            if (series) { val si = SeriesItem(o.str("id"), o.str("n"), o.str("i").orNullIfBlank(), null, o.str("r").orNullIfBlank())
                                Poster(si.id, si.name, si.cover, si.rating, null, true, { nav.push(Screen.SeriesDetail(si)) }) }
                            else { val v = Vod(o.str("id"), o.str("n"), o.str("i").orNullIfBlank(), o.str("r").orNullIfBlank(), o.str("u"))
                                Poster(v.id, v.name, v.poster, v.rating, v.url, true, { nav.push(Screen.MovieDetail(v)) }, { nav.push(Screen.Player(listOf(PlayItem(v.name, v.url, v.poster, null, null, v.name)), 0, live = false)) }) }
                        }
                        G_MYLIST -> MyList.items(kind).map { o ->
                            if (series) { val si = SeriesItem(o.str("id"), o.str("n"), o.str("i").orNullIfBlank(), null, null)
                                Poster(si.id, si.name, si.cover, null, null, false, { nav.push(Screen.SeriesDetail(si)) }) }
                            else { val v = Vod(o.str("id"), o.str("n"), o.str("i").orNullIfBlank(), null, o.str("u"))
                                Poster(v.id, v.name, v.poster, null, v.url, false, { nav.push(Screen.MovieDetail(v)) }, { nav.push(Screen.Player(listOf(PlayItem(v.name, v.url, v.poster, null, null, v.name)), 0, live = false)) }) }
                        }
                        else -> if (series) Repo.series(p, g.id).map { s -> Poster(s.id, s.name, s.cover, s.rating, null, NewContent.isNew(p.id, kind, s.id), open = { nav.push(Screen.SeriesDetail(s)) }, added = s.added) }
                        else Repo.movies(p, g.id).map { v -> Poster(v.id, v.name, v.poster, v.rating, v.url, NewContent.isNew(p.id, kind, v.id), open = { nav.push(Screen.MovieDetail(v)) }, play = { nav.push(Screen.Player(listOf(PlayItem(v.name, v.url, v.poster, null, null, v.name)), 0, live = false)) }, added = v.added) }
                    }
                }
                when (val s = state) {
                    is Load.Loading -> Loading()
                    is Load.Err -> Message("Nije se učitalo", s.message)
                    is Load.Ok -> if (s.value.isEmpty()) Message(if (g.id == G_MYLIST) "Lista je prazna" else "Kategorija je prazna", if (g.id == G_MYLIST) "Drži OK na filmu ili seriji i izaberi Dodaj na Moju listu" else null)
                        else if (Look.age) OnyxPosters(g.name, s.value, kind, p.id)
                        else PosterGrid(g.name, s.value, g.id == G_NEW, kind, p.id)
                }
            }
        }
    }
    groupMenu?.let { g ->
        val cats = (rowsState as? Load.Ok)?.value?.mapNotNull { (it as? VRow.Group)?.cat }?.filter { it.id != G_CONTINUE && it.id != G_NEW } ?: emptyList()
        GroupOptions(kind, g, ovr, cats, onDismiss = { groupMenu = null }) { close -> rev++; if (close) groupMenu = null }
    }
    PinGateDialog(gate)
}

@Composable
private fun PosterGrid(title: String, items: List<Poster>, newList: Boolean, kind: String, pid: String, wide: Boolean = false) {
    val ctx = LocalContext.current
    val size = when (Look.posterSize) { 0 -> 120.dp; 2 -> 190.dp; else -> 150.dp }
    val state = rememberLazyGridState()
    var menu by remember { mutableStateOf<Poster?>(null) }
    Column(Modifier.fillMaxSize().padding(start = if (wide) 24.dp else 22.dp, end = if (wide) 24.dp else 26.dp, top = if (wide) 6.dp else 24.dp)) {
        Row(Modifier.padding(bottom = if (wide) 8.dp else 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            T("$title · ${items.size}", 20.sp, C.Text, FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (newList) Button("Označi sve kao viđeno", R.drawable.lc_check) { NewContent.clear(kind) }
        }
        LazyVerticalGrid(
            state = state,
            columns = GridCells.Adaptive(size),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 30.dp, top = 4.dp),
        ) {
            itemsIndexed(items) { _, it ->
                val pr = it.url?.let { u -> Watch.get(ctx, u) }
                Focusable(modifier = Modifier.fillMaxWidth(), onClick = it.open, onMenu = { menu = it }, normal = Color.Transparent, focused = C.Card, zoom = 1.05f) { f ->
                    Column(Modifier.padding(6.dp)) {
                        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(Rnd(12.dp)).background(C.Tile)) {
                            if (it.image != null) {
                                AsyncImage(model = it.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                T(cleanTitle(it.name).first, 13.sp, C.Muted, maxLines = 4, modifier = Modifier.align(Alignment.Center).padding(10.dp))
                            }
                            Row(Modifier.padding(7.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                if (it.isNew) Badge(tr("NOVO"), Color(0xFFF59E0B), Color(0xFF0B0D12))
                                val r = it.rating?.toDoubleOrNull()
                                if (r != null && r > 0) Badge(String.format("%.1f", r), Color(0xEEF5C518), Color(0xFF0B0D12))
                                Spacer(Modifier.weight(1f))
                                qualityOf(it.name)?.let { q -> Badge(q, Color(0xCC0B0D12), Color.White) }
                            }
                            if (pr != null) WatchMark(pr, Modifier.align(Alignment.BottomEnd).padding(7.dp))
                            if (pr != null && pr.started) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0x66000000))) {
                                Box(Modifier.fillMaxWidth(pr.fraction).height(4.dp).background(C.Red))
                            }
                        }
                        T(it.name, 13.sp, if (f) C.Text else C.Soft, if (f) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 2, modifier = Modifier.padding(top = 7.dp))
                    }
                }
            }
        }
    }
    menu?.let { m ->
        Popup(m.name, onDismiss = { menu = null }) {
            val u = m.url
            if (u != null) {
                val w = Watch.get(ctx, u)?.watched == true
                OptionRow(if (w) "Označi kao negledano" else "Označi kao gledano", if (w) R.drawable.lc_circle_slash else R.drawable.lc_circle_check) {
                    Watch.setWatched(ctx, u, m.name, m.image, !w); menu = null
                }
            }
            if (m.isNew) OptionRow("Ukloni oznaku NOVO", R.drawable.lc_sparkles) { NewContent.seen(pid, kind, m.id); menu = null }
            OptionRow("Otvori", R.drawable.lc_info) { menu = null; m.open() }
        }
    }
}

/** New look: the focused film as a big title with its picture behind, posters below. */
@Composable
private fun OnyxPosters(title: String, items: List<Poster>, kind: String, pid: String) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf<Poster?>(null) }
    // poster width from the screen width: about 3–4 big posters in a row on any TV
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    val minW = (cfg.screenWidthDp * 0.15f).coerceIn(130f, 220f).dp
    Column(Modifier.fillMaxSize().padding(start = 18.dp, end = 24.dp, top = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Kicker(title, C.Soft, size = 12.sp, modifier = Modifier.weight(1f))
            Kicker(trf("%s naslova", items.size), size = 11.sp)
        }
        LazyVerticalGrid(columns = GridCells.Adaptive(minW), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 30.dp, start = 4.dp, end = 4.dp), modifier = Modifier.fillMaxSize()) {
            items(items, key = { it.id }) { it ->
                val pr = it.url?.let { u -> Watch.get(ctx, u) }
                PosterCard(cleanTitle(it.name).first, it.image, it.id, pr?.takeIf { p -> p.started && !p.watched }?.fraction, Modifier.fillMaxWidth(),
                    onMenu = { menu = it }, rating = it.rating, onClick = it.open)
            }
        }
    }
    menu?.let { m -> PosterMenu(m, kind, pid) { menu = null } }
}

@Composable
private fun PosterMenu(m: Poster, kind: String, pid: String, onDismiss: () -> Unit) {
    val (name, year) = cleanTitle(m.name)
    PosterSheet(tr(if (kind == "series") "Serija" else "Film") + (year?.let { " · $it" } ?: ""), name, m.image, m.id, onDismiss) {
        val on = MyList.has(kind, m.id)
        SheetRow(if (on) "Ukloni s Moje liste" else "Dodaj na Moju listu", if (on) null else "da ga pogledaš kasnije", if (on) R.drawable.lc_x else R.drawable.lc_list) {
            MyList.toggle(kind, m.id, m.name, m.image, m.url, pid); onDismiss()
        }
    }
}

/** Green tick = watched, orange = started, red crossed circle = not watched. */
@Composable
fun WatchMark(pr: Progress?, modifier: Modifier = Modifier) {
    val (icon, color) = when {
        pr == null -> R.drawable.lc_circle_slash to Color(0xFFEF4444)
        pr.watched -> R.drawable.lc_circle_check to Color(0xFF22C55E)
        pr.started -> R.drawable.lc_history to Color(0xFFF59E0B)
        else -> R.drawable.lc_circle_slash to Color(0xFFEF4444)
    }
    Box(modifier.size(24.dp).clip(CircleShape).background(Color(0xCC0B0D12)), contentAlignment = Alignment.Center) { Ico(icon, 16.dp, color) }
}

@Composable
fun Badge(text: String, bg: Color, fg: Color) {
    Box(Modifier.clip(Rnd(6.dp)).background(bg).padding(horizontal = 6.dp, vertical = 2.dp)) { T(text, 11.sp, fg, FontWeight.Bold) }
}

/** "Episode 3" instead of "NF - House Of Guinness 4K (2025) (GB) - S01E03 - Episode 3". */
fun episodeTitle(raw: String, num: Int): String {
    var t = raw
    Regex("[Ss]\\d{1,2}\\s*[Ee]\\d{1,3}").find(t)?.let { t = t.substring(it.range.last + 1) }
    t = t.trim(' ', '-', '|', ':', '.')
    t = t.replace(Regex("\\b(4K|UHD|FHD|HD|SD|HEVC)\\b", RegexOption.IGNORE_CASE), "").trim(' ', '-')
    if (t.isBlank() || Regex("^(Episode|Epizoda|Folge|Ep\\.?)\\s*\\d+$", RegexOption.IGNORE_CASE).matches(t)) return trf("Epizoda %s", num)
    return t
}

/** Watches for an external player that closes right away (VLC crash) and offers the built-in player. */
@Composable
fun ExternalWatch(onFailed: () -> Unit) {
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME && ExtPlayer.launchedAt > 0) {
                val gone = System.currentTimeMillis() - ExtPlayer.launchedAt
                ExtPlayer.launchedAt = 0
                if (gone < 8000) onFailed()
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
}

fun openTrailer(ctx: Context, title: String, youtubeKey: String?) {
    if (youtubeKey != null) {
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$youtubeKey")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (_: Exception) {}
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$youtubeKey"))); return } catch (_: Exception) {}
    }
    try {
        ctx.startActivity(Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube.tv").putExtra("query", "${cleanTitle(title).first} trailer"))
    } catch (e: Exception) {
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode("${cleanTitle(title).first} trailer")))) }
        catch (e2: Exception) { Toast.makeText(ctx, tr("YouTube nije instaliran"), Toast.LENGTH_LONG).show() }
    }
}

@Composable
private fun Backdrop(url: String?) {
    if (url == null || Look.age) return
    Box(Modifier.fillMaxSize()) {
        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(C.Bg, C.Bg.copy(alpha = 0.9f), C.Bg.copy(alpha = 0.35f)))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, C.Bg), startY = 300f)))
    }
}

@Composable
private fun PeopleRow(people: List<Person>) {
    if (people.isEmpty()) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(people) { person ->
            Column(Modifier.width(78.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(C.Tile), contentAlignment = Alignment.Center) {
                    if (person.photo != null) AsyncImage(model = person.photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else T(person.name.split(' ').mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString(""), 14.sp, C.Muted, FontWeight.Bold)
                }
                T(person.name, 11.sp, C.Text, modifier = Modifier.padding(top = 5.dp))
                T(person.role, 10.sp, C.Muted)
            }
        }
    }
}

/** What was picked on the movie page before playing. */
data class TrackChoice(val subFile: File? = null, val subOff: Boolean = false, val sub: StreamTrack? = null, val audio: StreamTrack? = null) {
    fun apply(i: PlayItem) = i.copy(subFile = subFile?.path, subOff = subOff, subLang = sub?.lang, subLabel = sub?.label,
        audioLang = audio?.lang, audioLabel = audio?.label)
}

/** Subtitle + audio buttons next to Play: green frame when a subtitle is ready. */
@Composable
fun TrackButtons(search: String, season: Int?, episode: Int?, url: String, choice: TrackChoice, onChange: (TrackChoice) -> Unit) {
    var showSubs by remember { mutableStateOf(false) }
    var showAudio by remember { mutableStateOf(false) }
    val subText = when {
        choice.subFile != null -> tr("Titl") + ": " + Langs.label(choice.subFile.nameWithoutExtension.substringAfterLast('_'))
        choice.sub != null -> tr("Titl") + ": " + choice.sub.title.substringBefore(" · ")
        choice.subOff -> tr("Bez titla")
        else -> tr("Titlovi")
    }
    Button(subText, R.drawable.lc_captions, outline = if (choice.subFile != null || choice.sub != null) C.Green else null) { showSubs = true }
    Button(if (choice.audio != null) tr("Audio") + ": " + choice.audio.title.substringBefore(" · ") else tr("Audio"), R.drawable.lc_volume) { showAudio = true }
    if (showSubs) SubtitlePicker(search, season, episode, probeUrl = url,
        onProbed = { t -> onChange(choice.copy(sub = t, subFile = null, subOff = false)); showSubs = false },
        onDismiss = { showSubs = false }) { f ->
        onChange(if (f == null) choice.copy(subFile = null, sub = null, subOff = true) else choice.copy(subFile = f, sub = null, subOff = false))
        showSubs = false
    }
    if (showAudio) AudioPicker(url, choice.audio?.title, onDismiss = { showAudio = false }) { t -> onChange(choice.copy(audio = t)); showAudio = false }
}

@Composable
fun MovieDetailScreen(v: Vod, nav: Nav) {
    if (Look.age) { OnyxMovieDetail(v, nav); return }
    val ctx = LocalContext.current
    val p = remember { Store.active(ctx) } ?: return
    val info by load(v.id) { Repo.movieInfo(p, v.id) }
    val meta by load(v.name, Prefs.tmdbKey) { Tmdb.find(v.name, tv = false) }
    var choice by remember { mutableStateOf(TrackChoice()) }
    var ext by remember { mutableStateOf(false) }
    var extFailed by remember { mutableStateOf(false) }
    val playFr = remember { FocusRequester() }
    LaunchedEffect(v.id) { NewContent.seen(p.id, "vod", v.id) }
    ExternalWatch { extFailed = true }
    val i = (info as? Load.Ok)?.value
    val m = (meta as? Load.Ok)?.value
    Box(Modifier.fillMaxSize()) {
        Backdrop(m?.backdrop ?: i?.backdrop)
        Row(Modifier.fillMaxSize().padding(horizontal = 44.dp, vertical = 32.dp), horizontalArrangement = Arrangement.spacedBy(34.dp)) {
            Box(Modifier.width(220.dp).aspectRatio(2f / 3f).clip(Rnd(18.dp)).background(C.Tile)) {
                val poster = m?.poster ?: v.poster
                if (poster != null) AsyncImage(model = poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                T("FILM", 12.sp, C.RedText, FontWeight.Bold)
                T(m?.title ?: cleanTitle(v.name).first, 34.sp, C.Text, FontWeight.Bold, maxLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (m?.year ?: i?.year)?.let { Chip(it) }
                    (m?.runtime ?: i?.duration)?.let { Chip(it) }
                    translated(m?.genres ?: i?.genre)?.let { Chip(it.take(36)) }
                    qualityOf(v.name)?.let { Chip(it, C.Card, C.Text, C.Soft) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    m?.imdbRating?.let { Chip("IMDb $it", Color(0xFFF5C518), Color(0xFF0B0D12)) }
                    m?.tmdbRating?.let { Chip("TMDB ${(it * 10).toInt()}%", Color(0x2901B4E4), Color(0xFF67D4F5)) }
                    if (m == null) (i?.rating ?: v.rating)?.toDoubleOrNull()?.takeIf { it > 0 }?.let { Chip("★ ${String.format("%.1f", it)}", C.Card, C.Amber) }
                }
                val plot = translated(m?.overview ?: i?.plot)
                if (meta is Load.Loading && plot == null) T("Učitavam opis…", 14.sp, C.Muted)
                plot?.let { T(it, 15.sp, C.Soft, maxLines = 5) }
                if (m != null) PeopleRow(m.people) else {
                    i?.cast?.let { T(trf("Glumci: %s", it), 13.sp, C.Muted, maxLines = 2) }
                    i?.director?.let { T(trf("Režija: %s", it), 13.sp, C.Muted) }
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button("Pokreni", R.drawable.lc_play, primary = true, focusRequester = playFr) {
                        if (ExtPlayer.always) openExternalOrAsk(ctx, v.url, m?.title ?: v.name, Watch.get(ctx, v.url)?.pos ?: 0) { ext = true }
                        else nav.push(Screen.Player(listOf(choice.apply(PlayItem(m?.title ?: v.name, v.url, v.poster, null, null, v.name))), 0, live = false))
                    }
                    TrackButtons(v.name, null, null, v.url, choice) { choice = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button("Eksterni player", R.drawable.lc_external_link) { openExternalOrAsk(ctx, v.url, m?.title ?: v.name, Watch.get(ctx, v.url)?.pos ?: 0) { ext = true } }
                    Button("Trailer", R.drawable.lc_play) { openTrailer(ctx, v.name, m?.trailerKey) }
                    val w = Watch.get(ctx, v.url)?.watched == true
                    androidx.compose.runtime.key(Watch.rev) {
                        Button(if (w) "Gledano" else "Označi kao gledano", if (w) R.drawable.lc_circle_check else R.drawable.lc_circle_slash,
                            outline = if (w) C.Green else null) { Watch.setWatched(ctx, v.url, m?.title ?: v.name, v.poster, !w) }
                    }
                }
            }
        }
    }
    if (ext) ExternalPicker(v.url, m?.title ?: v.name, Watch.get(ctx, v.url)?.pos ?: 0, onDismiss = { ext = false })
    if (extFailed) Popup("Eksterni player se zatvorio", onDismiss = { extFailed = false }) {
        T("Izgleda da player nije mogao pustiti film.", 14.sp, C.Soft, maxLines = 2)
        OptionRow("Pusti u PRETORIAN plejeru", R.drawable.lc_play, color = C.Green) {
            extFailed = false
            nav.push(Screen.Player(listOf(choice.apply(PlayItem(m?.title ?: v.name, v.url, v.poster, null, null, v.name))), 0, live = false))
        }
        OptionRow("Izaberi drugi player", R.drawable.lc_external_link) { extFailed = false; ExtPlayer.saved = ""; ext = true }
        OptionRow("Zatvori", R.drawable.lc_x) { extFailed = false }
    }
    LaunchedEffect(Unit) { playFr.tryFocus() }
}

@Composable
fun SeriesDetailScreen(s: SeriesItem, nav: Nav) {
    if (Look.age) { OnyxSeriesDetail(s, nav); return }
    val ctx = LocalContext.current
    val p = remember { Store.active(ctx) } ?: return
    val eps by load(s.id) { Repo.episodes(p, s.id) }
    val meta by load(s.name, Prefs.tmdbKey) { Tmdb.find(s.name, tv = true) }
    val m = (meta as? Load.Ok)?.value
    var season by remember { mutableIntStateOf(-1) }
    var choice by remember { mutableStateOf(TrackChoice()) }
    var menu by remember { mutableStateOf<Episode?>(null) }
    var ext by remember { mutableStateOf<Episode?>(null) }
    var followRev by remember { mutableIntStateOf(0) }
    val firstFr = remember { FocusRequester() }
    LaunchedEffect(s.id) { NewContent.seen(p.id, "series", s.id) }
    val showTitle = m?.title ?: cleanTitle(s.name).first
    Box(Modifier.fillMaxSize()) {
        Backdrop(m?.backdrop ?: s.cover)
        Row(Modifier.fillMaxSize().padding(horizontal = 44.dp, vertical = 28.dp), horizontalArrangement = Arrangement.spacedBy(30.dp)) {
            Box(Modifier.width(180.dp).aspectRatio(2f / 3f).clip(Rnd(16.dp)).background(C.Tile)) {
                val poster = m?.poster ?: s.cover
                if (poster != null) AsyncImage(model = poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                T("SERIJA", 12.sp, C.RedText, FontWeight.Bold)
                T(showTitle, 32.sp, C.Text, FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    m?.year?.let { Chip(it) }; translated(m?.genres)?.let { Chip(it.take(36)) }
                    m?.imdbRating?.let { Chip("IMDb $it", Color(0xFFF5C518), Color(0xFF0B0D12)) }
                    m?.tmdbRating?.let { Chip("TMDB ${(it * 10).toInt()}%", Color(0x2901B4E4), Color(0xFF67D4F5)) }
                }
                translated(m?.overview ?: s.plot)?.let { T(it, 14.sp, C.Soft, maxLines = 3) }
                when (val st = eps) {
                    is Load.Loading -> Loading("Učitavam epizode…")
                    is Load.Err -> Message("Epizode se nisu učitale", st.message)
                    is Load.Ok -> {
                        val all = st.value
                        if (all.isEmpty()) { Message("Nema epizoda"); return@Column }
                        val seasons = all.map { it.season }.distinct().sorted()
                        val cur = if (season in seasons) season else seasons.first()
                        androidx.compose.runtime.key(Watch.rev) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(seasons) { n ->
                                    val inS = all.filter { it.season == n }
                                    val seen = inS.count { Watch.get(ctx, it.url)?.watched == true }
                                    Focusable(onClick = { season = n }, onFocus = { season = n },
                                        normal = if (n == cur) C.CardHi else C.Card, shape = Pill, modifier = Modifier.height(40.dp)) { _ ->
                                        T(trf("Sezona %s", n) + " · $seen/${inS.size}", 14.sp, C.Text, FontWeight.Medium, modifier = Modifier.align(Alignment.Center).padding(horizontal = 18.dp))
                                    }
                                }
                            }
                        }
                        val list = all.filter { it.season == cur }
                        val play = list.map {
                            (if (it == list.first()) choice else choice.copy(subFile = null)).apply(PlayItem("$showTitle · S${it.season}E${it.num}", it.url, s.cover,
                                episodeTitle(it.title, it.num), null, s.name, it.season, it.num))
                        }
                        // audio and subtitles for the whole season, read from its first episode
                        list.firstOrNull()?.let { e1 ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                TrackButtons(s.name, e1.season, e1.num, e1.url, choice) { choice = it }
                                androidx.compose.runtime.key(followRev) {
                                    val on = Follow.isOn(s.id)
                                    Button(if (on) "Pratiš" else "Prati", R.drawable.lc_bell, outline = if (on) C.Amber else null) {
                                        val now = Follow.toggle(p.id, s, all.size); followRev++
                                        Toast.makeText(ctx, tr(if (now) "Javiću kad izađe nova epizoda" else "Ne pratiš više ovu seriju"), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                        val wrap = remember { Wrap() }
                        val ls = rememberLazyListState()
                        WrapEffect(wrap, ls)
                        androidx.compose.runtime.key(Watch.rev) {
                            LazyColumn(state = ls, verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                                itemsIndexed(list) { idx, e ->
                                    val pr = Watch.get(ctx, e.url)
                                    Focusable(onClick = {
                                        if (ExtPlayer.always) openExternalOrAsk(ctx, e.url, play[idx].title, pr?.pos ?: 0) { ext = e }
                                        else nav.push(Screen.Player(play, idx, live = false))
                                    }, onMenu = { menu = e },
                                        focusRequester = wrap.requester(idx, if (idx == 0) firstFr else null),
                                        normal = C.Card, zoom = 1.01f, modifier = Modifier.fillMaxWidth().height(64.dp).wrapKeys(idx, list.size, wrap)) { f ->
                                        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                            T("${e.num}", 18.sp, C.Muted, FontWeight.SemiBold, modifier = Modifier.width(36.dp))
                                            Column(Modifier.weight(1f)) {
                                                T(episodeTitle(e.title, e.num), 15.sp, if (f) C.Text else C.Soft, FontWeight.Medium)
                                                if (pr != null && pr.started) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Box(Modifier.width(120.dp).height(4.dp).clip(Rnd(2.dp)).background(C.CardHi)) {
                                                        Box(Modifier.fillMaxWidth(pr.fraction).height(4.dp).background(C.Amber))
                                                    }
                                                    T(trf("%s od %s", fmtTime(pr.pos), fmtTime(pr.dur)), 11.sp, C.Muted)
                                                } else translated(e.plot)?.let { T(it, 12.sp, C.Muted) }
                                            }
                                            qualityOf(e.title)?.let { Badge(it, C.CardHi, C.Text) }
                                            WatchMark(pr)
                                        }
                                    }
                                }
                            }
                        }
                        menu?.let { e ->
                            val w = Watch.get(ctx, e.url)?.watched == true
                            Popup(episodeTitle(e.title, e.num), onDismiss = { menu = null }) {
                                OptionRow("Pusti", R.drawable.lc_play) { menu = null; nav.push(Screen.Player(play, list.indexOf(e).coerceAtLeast(0), live = false)) }
                                OptionRow(if (w) "Označi kao negledano" else "Označi kao gledano", if (w) R.drawable.lc_circle_slash else R.drawable.lc_circle_check) {
                                    Watch.setWatched(ctx, e.url, "$showTitle · S${e.season}E${e.num}", s.cover, !w); menu = null
                                }
                                OptionRow("Označi sezonu kao gledanu", R.drawable.lc_circle_check) {
                                    list.forEach { x -> Watch.setWatched(ctx, x.url, "$showTitle · S${x.season}E${x.num}", s.cover, true) }; menu = null
                                }
                                OptionRow("Eksterni player", R.drawable.lc_external_link) { menu = null; ext = e }
                            }
                        }
                        ext?.let { e -> ExternalPicker(e.url, "$showTitle · S${e.season}E${e.num}", Watch.get(ctx, e.url)?.pos ?: 0, onDismiss = { ext = null }) }
                    }
                }
            }
        }
    }
}
