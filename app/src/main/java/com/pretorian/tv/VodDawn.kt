package com.pretorian.tv

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONObject

/* ============================================================== Movies and Series: one menu at a time */

/** A film or series of the playlist. */
data class VItem(val id: String, val name: String, val image: String?, val rating: String?, val url: String, val series: Boolean, val pid: String) {
    val kind get() = if (series) "series" else "vod"
    fun vod() = Vod(id, name, image, rating, url)
    fun si() = SeriesItem(id, name, image, null, rating)
    fun open(nav: Nav) { VodMemory.returning = true; nav.push(if (series) Screen.SeriesDetail(si()) else Screen.MovieDetail(vod())) }
}

/** The streaming services of Series › Streaming: TMDB network ids of their own series (originals). */
data class Service(val name: String, val networks: String)
val SERVICES = listOf(
    Service("Netflix", "213"), Service("Apple TV+", "2552"), Service("Disney+", "2739"),
    Service("Prime Video", "1024"), Service("HBO Max", "49%7C3186"), Service("Paramount+", "4330"),
    Service("Peacock", "3353"), Service("Hulu", "453"), Service("Starz", "318"),
)

/** TMDB titles → the same titles in the playlist (name and year must agree). Nothing is shown that the playlist does not have. */
object VodMatch {
    private val mem = HashMap<String, List<VItem>>()
    suspend fun match(p: Playlist, series: Boolean, key: String, hits: List<Tmdb.Hit>): List<VItem> {
        val ck = p.id + "|" + key
        mem[ck]?.let { return it }
        val kind = if (series) "series" else "vod"
        try { CatalogDb.ensure(p, kind) } catch (_: Exception) {}
        val out = LinkedHashMap<String, VItem>()
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            hits.forEach { h ->
                val want = setOf(Sports.norm(h.title), Sports.norm(h.original)).filter { it.isNotBlank() }
                val words = Sports.norm(h.title).split(' ').filter { it.length >= 2 }.take(3).ifEmpty { listOf(Sports.norm(h.title)) }
                CatalogDb.search(listOf(p.id), kind, words, 40).forEach { r ->
                    val (t, y) = cleanTitle(r.n)
                    val yOk = y == null || h.year == null || kotlin.math.abs((y.toIntOrNull() ?: 0) - (h.year.toIntOrNull() ?: 0)) <= 1
                    if (Sports.norm(t) in want && yOk && out.values.none { it.id == r.id }) out[h.id.toString() + "|" + r.id] =
                        VItem(r.id, r.n, r.i, r.r, r.u, series, p.id)
                }
            }
        }
        // one entry per TMDB title (the playlist may have the same film twice, e.g. in two languages)
        val list = out.entries.distinctBy { it.key.substringBefore('|') }.map { it.value }
        mem[ck] = list
        return list
    }
}

/** "Continue watching" entries that were removed: hidden until the title is watched again (the progress itself is kept). */
object ContinueHidden {
    private fun all(): JSONObject = try { JSONObject(Prefs.str("cw_hidden", "{}")) } catch (e: Exception) { JSONObject() }
    fun hide(url: String) { Prefs.put("cw_hidden", all().put(url, System.currentTimeMillis()).toString()); Watch.rev++ }
    fun hidden(url: String, ts: Long): Boolean = all().optLong(url, 0L).let { it > 0 && it >= ts }
}

private sealed class VL {
    object Menu : VL()
    data class Gallery(val id: String, val title: String, val icon: Int) : VL()
    object Streaming : VL()
    data class Svc(val s: Service) : VL()
    val key get() = when (this) { Menu -> "m"; is Gallery -> "g:$id"; Streaming -> "st"; is Svc -> "sv:" + s.name }
}

const val VG_CONT = "__continue__"
const val VG_NEW = "__new__"
const val VG_MY = "__mylist__"
const val VG_CINEMA = "__cinema__"

@Composable
fun DawnVod(nav: Nav, series: Boolean) {
    val ctx = LocalContext.current
    val p = remember { Store.active(ctx) } ?: run { NoPlaylist(nav); return }
    val kind = if (series) "series" else "vod"
    val mem = remember { VodMemory.levels.getOrPut(kind) { HashMap() } }
    // back from a title or the player: the same level as before; a fresh visit starts at the menu
    val stack = remember {
        val s = VodMemory.stack.getOrPut(kind) { mutableStateListOf<Any>(VL.Menu) }
        if (!VodMemory.returning) { s.clear(); s.add(VL.Menu) }
        VodMemory.returning = false
        s
    }
    val top = stack.last() as VL
    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }
    val gate = remember { PinGate() }
    val ovr = remember { Ovr(ctx, p.id) }
    var groupMenu by remember { mutableStateOf<Category?>(null) }
    var cats by remember { mutableStateOf(emptyList<Category>()) }
    var rev by remember { mutableStateOf(0) }
    LaunchedEffect(top) {
        Dawn.crumb = when (top) { VL.Menu -> ""; is VL.Gallery -> top.title; VL.Streaming -> "Streaming"; is VL.Svc -> top.s.name }
    }
    DisposableEffect(Unit) { onDispose { Dawn.crumb = "" } }
    Box(Modifier.fillMaxSize().padding(start = 34.dp, end = 38.dp, top = 6.dp, bottom = 8.dp)) {
        key(top.key) {
            when (top) {
                VL.Menu -> {
                    val groups by load(p.id, rev) { if (p.type == SourceType.M3U) emptyList() else ovr.groups(kind, if (series) Repo.seriesGroups(p) else Repo.vodGroups(p)) }
                    val totals = remember(p.id) { CatalogDb.counts(p.id, kind) }
                    LaunchedEffect(groups) { (groups as? Load.Ok)?.value?.let { cats = it } }
                    CuPanel {
                        val fixed = listOf(
                            CuItem("Nastavi gledati", icon = R.drawable.lc_play) { stack.add(VL.Gallery(VG_CONT, tr("Nastavi gledati"), R.drawable.lc_play)) },
                            CuItem("Nedavno dodano", icon = R.drawable.lc_clock) { stack.add(VL.Gallery(VG_NEW, tr("Nedavno dodano"), R.drawable.lc_clock)) },
                            if (series) CuItem("Streaming", icon = R.drawable.lc_tv) { stack.add(VL.Streaming) }
                            else CuItem("U kinima", icon = R.drawable.lc_film) { stack.add(VL.Gallery(VG_CINEMA, tr("U kinima"), R.drawable.lc_film)) },
                            CuItem("Moja lista", icon = R.drawable.lc_bookmark) { stack.add(VL.Gallery(VG_MY, tr("Moja lista"), R.drawable.lc_bookmark)) },
                        )
                        val provider = ((groups as? Load.Ok)?.value ?: emptyList()).mapIndexed { i, g ->
                            val locked = ovr.isLocked(kind, g.id)
                            CuItem(g.name, icon = if (locked) R.drawable.lc_lock else R.drawable.lc_layers, trailing = totals[g.id]?.takeIf { it > 0 }?.toString(),
                                divider = i == 0, onLong = { groupMenu = g }) {
                                gate.run("$kind:${g.id}", locked) { stack.add(VL.Gallery(g.id, g.name, R.drawable.lc_layers)) }
                            }
                        }
                        CuList(mem, "menu", fixed + provider)
                    }
                }
                is VL.Gallery -> VodGalleryLevel(p, series, top, mem, nav)
                VL.Streaming -> StreamingGrid(mem) { stack.add(VL.Svc(it)) }
                is VL.Svc -> {
                    val items by load(top.s.name) { VodMatch.match(p, true, "svc:" + top.s.name, Tmdb.network(top.s.networks)) }
                    VodGalleryBody(top.s.name, R.drawable.lc_tv, items, mem, top.key, nav,
                        empty = if (!Tmdb.enabled()) "Za ovu listu treba TMDB ključ (Postavke › Izvori)" else "U tvojoj playlisti nema serija ovog servisa")
                }
            }
        }
    }
    groupMenu?.let { g ->
        GroupOptions(kind, g, ovr, cats, onDismiss = { groupMenu = null }) { close -> rev++; if (close) groupMenu = null }
    }
    PinGateDialog(gate)
}

/** One category: Continue watching has its own gallery; everything else the cinematic browser. */
@Composable
private fun VodGalleryLevel(p: Playlist, series: Boolean, g: VL.Gallery, mem: HashMap<String, Int>, nav: Nav) {
    val ctx = LocalContext.current
    val kind = if (series) "series" else "vod"
    if (g.id == VG_CONT) { ContinueGallery(series, mem, g.key, nav); return }
    val items by load(g.id, MyList.rev, NewContent.rev) {
        when (g.id) {
            VG_NEW -> NewContent.items(p.id, kind).map { o -> VItem(o.str("id"), o.str("n"), o.str("i").orNullIfBlank(), o.str("r").orNullIfBlank(), o.str("u"), series, p.id) }
            VG_MY -> MyList.items(kind).map { o -> VItem(o.str("id"), o.str("n"), o.str("i").orNullIfBlank(), null, o.str("u"), series, o.str("p").ifBlank { p.id }) }
            VG_CINEMA -> VodMatch.match(p, false, "cinema", Tmdb.nowPlaying())
            else -> if (series) Repo.series(p, g.id).map { VItem(it.id, it.name, it.cover, it.rating, "", true, p.id) }
                    else Repo.movies(p, g.id).map { VItem(it.id, it.name, it.poster, it.rating, it.url, false, p.id) }
        }
    }
    LaunchedEffect(g.id) { if (g.id != VG_NEW && g.id != VG_MY && g.id != VG_CINEMA) (items as? Load.Ok)?.value?.forEach { VodMemory.catOf[it.id] = g.title } }
    VodGalleryBody(g.title, g.icon, items, mem, g.key, nav, empty = when (g.id) {
        VG_MY -> "Lista je prazna. U detaljima filma ili serije izaberi „Dodaj na moju listu“."
        VG_NEW -> "Ništa novo od zadnjeg ažuriranja playliste"
        VG_CINEMA -> if (!Tmdb.enabled()) "Za ovu listu treba TMDB ključ (Postavke › Izvori)" else "Nijedan film iz kina trenutno nije u tvojoj playlisti"
        else -> "Kategorija je prazna"
    })
}

/**
 * The cinematic browser: the focused title fills the top (picture, title, year, genres, story, Watch · Trailer · My List),
 * the posters run along the bottom. Moving over the posters changes the top; OK opens the title.
 */
@Composable
private fun VodGalleryBody(title: String, icon: Int, items: Load<List<VItem>>, mem: HashMap<String, Int>, key: String, nav: Nav, empty: String) {
    val ctx = LocalContext.current
    when (items) {
        is Load.Loading -> { Loading("Učitavam…"); return }
        is Load.Err -> { Message("Nije se učitalo", items.message); return }
        is Load.Ok -> if (items.value.isEmpty()) { Box(Modifier.fillMaxSize().dawnGlass()) { Message(title, empty) }; return }
    }
    val list = (items as Load.Ok).value
    val start = (mem[key] ?: 0).coerceIn(0, list.lastIndex)
    var focused by remember { mutableStateOf(list[start]) }
    var shown by remember { mutableStateOf(list[start]) }
    LaunchedEffect(focused) { delay(300); shown = focused }
    val m by load(shown.name, shown.series, Prefs.tmdbKey) { Tmdb.find(shown.name, tv = shown.series) }
    val meta = (m as? Load.Ok)?.value
    val st = rememberLazyListState(initialFirstVisibleItemIndex = (start - 2).coerceAtLeast(0))
    val fr = remember { FocusRequester() }
    Box(Modifier.fillMaxSize().clip(Rnd(18.dp)).background(Color(0xFF0B0B0C))) {
        // picture on the right, fading into black towards the text
        Box(Modifier.fillMaxHeight().fillMaxWidth(0.68f).align(Alignment.TopEnd)) {
            OnyxImage(meta?.backdrop ?: shown.image, "bd:" + shown.id, Modifier.fillMaxSize(), maxW = 1280, scale = ContentScale.Crop, highQuality = true)
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFF0B0B0C), Color(0x990B0B0C), Color(0x000B0B0C)))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color(0x000B0B0C), 1f to Color(0xFF0B0B0C))))
        }
        Column(Modifier.fillMaxSize().padding(start = 26.dp, end = 18.dp, top = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Ico(icon, 18.dp, C.Soft); T(title, 16.sp, C.Soft, FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                T("${(mem[key] ?: 0) + 1} / ${list.size}", 14.sp, C.Soft)  // i18n-ignore
            }
            T(titleCase(meta?.title ?: cleanTitle(shown.name).first), 34.sp, C.Text, FontWeight.Bold, maxLines = 1)
            val facts = listOfNotNull(meta?.year?.take(4) ?: cleanTitle(shown.name).second, meta?.runtime, translated(meta?.genres)?.replace(", ", " · "))
            if (facts.isNotEmpty()) T(facts.joinToString("  |  "), 15.sp, C.Soft)
            T(translated(meta?.overview) ?: "", 15.sp, C.Soft, maxLines = 3, modifier = Modifier.widthIn(max = 560.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeroBtn(if (shown.series) "Otvori" else "Gledaj", R.drawable.lc_play, primary = true) {
                    if (shown.series) shown.open(nav) else { VodMemory.returning = true; nav.push(Screen.Player(listOf(PlayItem(cleanTitle(shown.name).first, shown.url, shown.image, null, null, shown.name)), 0, live = false)) }
                }
                HeroBtn("Trailer", R.drawable.lc_film) { openTrailer(ctx, shown.name, meta?.trailerKey) }
                val on = remember(MyList.rev, shown.id) { MyList.has(shown.kind, shown.id) }
                HeroBtn(if (on) "Ukloni s moje liste" else "Dodaj na moju listu", if (on) R.drawable.lc_bookmark_filled else R.drawable.lc_bookmark) { myListToggle(ctx, shown) }
            }
            Spacer(Modifier.weight(1f))
            LazyRow(state = st, horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)) {
                itemsIndexed(list, key = { _, it -> it.id + it.url }) { i, it ->
                    Focusable(onClick = { mem[key] = i; it.open(nav) }, onFocus = { mem[key] = i; focused = it },
                        focusRequester = if (i == start) fr else null, shape = Rnd(12.dp), normal = Color.Transparent, focused = Color.Transparent,
                        focusBorderColor = Color.White, zoom = 1.04f, modifier = Modifier.width(150.dp).aspectRatio(2f / 3f)) { _ ->
                        OnyxImage(it.image, "p:" + it.id, Modifier.fillMaxSize().clip(Rnd(12.dp)), maxW = 300)
                        if (it.image == null) T(cleanTitle(it.name).first, 13.sp, C.Text, FontWeight.SemiBold, maxLines = 3, modifier = Modifier.align(Alignment.Center).padding(8.dp))
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { delay(60); fr.tryFocus() }
}

@Composable
private fun HeroBtn(label: String, icon: Int, primary: Boolean = false, onClick: () -> Unit) {
    Focusable(onClick = onClick, shape = Rnd(12.dp), normal = if (primary) Color(0xE6F2F2F4) else Color(0x14FFFFFF), focused = Dawn.pill,
        focusBorderColor = Color.Transparent, zoom = 1f, modifier = Modifier.height(48.dp).border(1.dp, if (primary) Color.Transparent else Color(0x99FFFFFF), Rnd(12.dp))) { f ->
        val fg = if (f || primary) Dawn.onPill else Color.White
        Row(Modifier.align(Alignment.Center).padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Ico(icon, 18.dp, fg); T(label, 16.sp, fg, FontWeight.SemiBold)
        }
    }
}

/** My List: add or remove at once, a short note, no question. */
fun myListToggle(ctx: Context, it: VItem) {
    val on = MyList.toggle(it.kind, it.id, it.name, it.image, it.url.ifBlank { null }, it.pid)
    Toast.makeText(ctx, tr(if (on) "Dodano na vašu listu" else "Uklonjeno s vaše liste"), Toast.LENGTH_SHORT).show()
}

/* ------------------------------------------------------------ Continue watching */

private fun leftText(pr: Progress): String? = if (pr.dur > 0 && pr.pos > 0) trf("još %s min", ((pr.dur - pr.pos) / 60_000).toInt().coerceAtLeast(1)) else null

/** Resume a Continue watching entry: the saved place (series: the unfinished episode, or the next one). [fromStart] = the same title at 00:00. */
fun resumeContinue(ctx: Context, nav: Nav, pr: Progress, fromStart: Boolean) {
    VodMemory.returning = true
    val q = Watch.playable(ctx, pr)
    val at = if (fromStart || !q.started) 0L else q.pos
    nav.push(Screen.Player(listOf(PlayItem(q.title, q.url, q.poster, null, null, q.series, q.season, q.episode, startAt = at)), 0, live = false))
}

/** Continue watching: posters with a thin progress line. OK resumes at once; hold OK: Continue · Play from the beginning · Remove. */
@Composable
private fun ContinueGallery(series: Boolean, mem: HashMap<String, Int>, key: String, nav: Nav) {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val r = Watch.rev
    val list = remember(Watch.rev) { Watch.continueList(ctx).filter { (it.series != null) == series && !ContinueHidden.hidden(it.url, it.ts) } }
    var menu by remember { mutableStateOf<Progress?>(null) }
    if (list.isEmpty()) { Box(Modifier.fillMaxSize().dawnGlass()) { Message("Nastavi gledati", if (series) "Ovdje se pojave serije koje nisi dogledao." else "Ovdje se pojave filmovi koje nisi dogledao.") }; return }
    val start = (mem[key] ?: 0).coerceIn(0, list.lastIndex)
    val fr = remember(list.size) { FocusRequester() }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(start = 8.dp, top = 6.dp)) {
            Ico(if (series) R.drawable.lc_layers else R.drawable.lc_film, 24.dp, C.Text)
            T(tr(if (series) "Serije" else "Filmovi") + " – " + tr("Nastavi gledati"), 26.sp, C.Text, FontWeight.Bold)
        }
        Spacer(Modifier.height(30.dp))
        val st = rememberLazyListState(initialFirstVisibleItemIndex = (start - 2).coerceAtLeast(0))
        LazyRow(state = st, horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)) {
            itemsIndexed(list, key = { _, it -> it.url }) { i, pr ->
                Focusable(onClick = { mem[key] = i; resumeContinue(ctx, nav, pr, false) }, onMenu = { mem[key] = i; menu = pr }, onFocus = { mem[key] = i },
                    focusRequester = if (i == start) fr else null, shape = Rnd(14.dp), normal = Color.Transparent, focused = Color.Transparent,
                    focusBorderColor = Color.White, zoom = 1.04f, modifier = Modifier.width(200.dp).aspectRatio(2f / 3f)) { _ ->
                    OnyxImage(pr.poster, "cw:" + pr.url, Modifier.fillMaxSize().clip(Rnd(14.dp)), maxW = 400)
                    Box(Modifier.fillMaxSize().clip(Rnd(14.dp)).background(Brush.verticalGradient(0.55f to Color(0x00000000), 1f to Color(0xE6000000))))
                    Column(Modifier.align(Alignment.BottomCenter).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (pr.poster == null) T(cleanTitle(pr.series ?: pr.title).first, 15.sp, C.Text, FontWeight.SemiBold, maxLines = 2)
                        val line = listOfNotNull(if (pr.season != null && pr.episode != null) "S${pr.season} • E${pr.episode}" else null, leftText(pr)?.let { "($it)" }).joinToString("  ")  // i18n-ignore
                        if (line.isNotBlank()) T(line, 13.sp, C.Text, maxLines = 1)
                        ThinBar(pr.fraction, Color.White, Modifier.fillMaxWidth(), 4.dp)
                    }
                }
            }
        }
        T("OK: nastavi · Drži OK: opcije", 13.sp, C.Muted, modifier = Modifier.padding(start = 8.dp))
    }
    LaunchedEffect(list.size) { delay(60); fr.tryFocus() }
    menu?.let { pr ->
        val ep = if (pr.season != null && pr.episode != null) " – S${pr.season} • E${pr.episode}" else ""  // i18n-ignore
        Popup(cleanTitle(pr.series ?: pr.title).first, { menu = null }) {
            OptionRow(tr("Nastavi") + ep, R.drawable.lc_play) { menu = null; resumeContinue(ctx, nav, pr, false) }
            OptionRow(tr("Pusti od početka") + ep, R.drawable.lc_rotate_ccw) { menu = null; resumeContinue(ctx, nav, pr, true) }
            OptionRow("Ukloni iz „Nastavi gledati“", R.drawable.lc_trash_2) {
                menu = null; ContinueHidden.hide(pr.url)
                Toast.makeText(ctx, tr("Uklonjeno iz „Nastavi gledati“"), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/* ------------------------------------------------------------ Series › Streaming */

@Composable
private fun StreamingGrid(mem: HashMap<String, Int>, onPick: (Service) -> Unit) {
    val start = (mem["st"] ?: 0).coerceIn(0, SERVICES.lastIndex)
    val fr = remember { FocusRequester() }
    Column(Modifier.fillMaxSize().padding(horizontal = 60.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(10.dp))
        LazyVerticalGrid(GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            itemsIndexed(SERVICES) { i, s ->
                Focusable(onClick = { mem["st"] = i; onPick(s) }, onFocus = { mem["st"] = i }, focusRequester = if (i == start) fr else null,
                    shape = Rnd(16.dp), normal = Color(0xCC161618), focused = Color(0xCC161618), focusBorderColor = Color.White, zoom = 1.03f,
                    modifier = Modifier.fillMaxWidth().height(118.dp).border(1.dp, Color(0x33FFFFFF), Rnd(16.dp))) { f ->
                    T(s.name, 26.sp, Color.White, if (f) FontWeight.Bold else FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
    LaunchedEffect(Unit) { delay(60); fr.tryFocus() }
}
