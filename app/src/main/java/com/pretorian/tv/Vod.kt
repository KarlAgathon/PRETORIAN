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

private data class Poster(val id: String, val name: String, val image: String?, val rating: String?, val open: () -> Unit)

@Composable
private fun VodBrowser(nav: Nav, series: Boolean) {
    val ctx = LocalContext.current
    val p = remember { Store.active(ctx) } ?: run { NoPlaylist(nav); return }
    if (p.type != SourceType.XTREAM) {
        Message(if (series) "Serije nisu dostupne" else "Filmovi nisu dostupni",
            "Ova M3U playlista ima samo TV kanale. Filmovi i serije rade s Xtream Codes playlistom.")
        return
    }
    val kind = if (series) "series" else "vod"
    val ovr = remember(p.id) { Ovr(ctx, p.id) }
    var rev by remember { mutableIntStateOf(0) }
    val groupsState by load(p.id, series, rev) { ovr.groups(kind, if (series) Repo.seriesGroups(p) else Repo.vodGroups(p)) }
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
        Column(Modifier.width(300.dp).fillMaxHeight().padding(start = 18.dp, end = 12.dp, top = 24.dp)) {
            T(if (series) "Serije" else "Filmovi", 28.sp, C.Text, FontWeight.Bold, modifier = Modifier.padding(start = 10.dp, bottom = 14.dp))
            when (val s = groupsState) {
                is Load.Loading -> Loading("Učitavam kategorije…")
                is Load.Err -> Message("Greška", s.message)
                is Load.Ok -> {
                    val all = s.value
                    LazyColumn(state = gState, verticalArrangement = Arrangement.spacedBy(3.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                        itemsIndexed(all, key = { i, g -> "$i${g.id}" }) { i, g ->
                            val selected = group?.id == g.id
                            val locked = ovr.isLocked(kind, g.id)
                            Focusable(
                                modifier = Modifier.fillMaxWidth().height(44.dp).wrapKeys(i, all.size, gWrap),
                                onClick = { gate.run("$kind:${g.id}", locked) { group = g } },
                                onFocus = { focusedGroup = g },
                                onMenu = if (Lock.simple) null else ({ groupMenu = g }),
                                focusRequester = gWrap.requester(i, if (i == 0) firstGroup else null),
                                normal = if (selected) C.CardFocus else Color.Transparent,
                                zoom = 1.0f,
                            ) { _ ->
                                Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    T(g.name, 14.sp, if (selected) C.Text else C.Soft, if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        modifier = Modifier.weight(1f))
                                    if (locked) Ico(R.drawable.lc_lock, 15.dp, C.Muted)
                                }
                            }
                        }
                    }
                    LaunchedEffect(Unit) { try { firstGroup.requestFocus() } catch (_: Exception) {} }
                }
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(C.Line))
        Box(Modifier.weight(1f).fillMaxHeight()) {
            val g = group
            if (g == null) {
                Message("Izaberi kategoriju", "Meni ili dugi OK na kategoriji otvara opcije")
            } else {
                val state by load(p.id, g.id, series) {
                    if (series) Repo.series(p, g.id).map { s -> Poster(s.id, s.name, s.cover, s.rating) { nav.push(Screen.SeriesDetail(s)) } }
                    else Repo.movies(p, g.id).map { v -> Poster(v.id, v.name, v.poster, v.rating) { nav.push(Screen.MovieDetail(v)) } }
                }
                when (val s = state) {
                    is Load.Loading -> Loading()
                    is Load.Err -> Message("Nije se učitalo", s.message)
                    is Load.Ok -> if (s.value.isEmpty()) Message("Kategorija je prazna") else PosterGrid(g.name, s.value)
                }
            }
        }
    }
    groupMenu?.let { g ->
        GroupOptions(kind, g, ovr, (groupsState as? Load.Ok)?.value ?: emptyList(), onDismiss = { groupMenu = null }) { rev++; groupMenu = null }
    }
    PinGateDialog(gate)
}

@Composable
private fun PosterGrid(title: String, items: List<Poster>) {
    val size = when (Look.posterSize) { 0 -> 120.dp; 2 -> 190.dp; else -> 150.dp }
    val state = rememberLazyGridState()
    Column(Modifier.fillMaxSize().padding(start = 22.dp, end = 26.dp, top = 24.dp)) {
        T("$title · ${items.size}", 20.sp, C.Text, FontWeight.SemiBold, modifier = Modifier.padding(bottom = 14.dp))
        LazyVerticalGrid(
            state = state,
            columns = GridCells.Adaptive(size),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 30.dp, top = 4.dp),
        ) {
            itemsIndexed(items) { _, it ->
                Focusable(modifier = Modifier.fillMaxWidth(), onClick = it.open, normal = Color.Transparent, focused = C.Card, zoom = 1.05f) { f ->
                    Column(Modifier.padding(6.dp)) {
                        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(C.Tile)) {
                            if (it.image != null) {
                                AsyncImage(model = it.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                T(cleanTitle(it.name).first, 13.sp, C.Muted, maxLines = 4, modifier = Modifier.align(Alignment.Center).padding(10.dp))
                            }
                            Row(Modifier.padding(7.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                val r = it.rating?.toDoubleOrNull()
                                if (r != null && r > 0) Badge(String.format("%.1f", r), Color(0xEEF5C518), Color(0xFF0B0D12))
                                Spacer(Modifier.weight(1f))
                                qualityOf(it.name)?.let { q -> Badge(q, Color(0xCC0B0D12), Color.White) }
                            }
                        }
                        T(it.name, 13.sp, if (f) C.Text else C.Soft, if (f) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 2, modifier = Modifier.padding(top = 7.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun Badge(text: String, bg: Color, fg: Color) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 6.dp, vertical = 2.dp)) { T(text, 11.sp, fg, FontWeight.Bold) }
}

fun openExternal(ctx: Context, url: String, title: String) {
    try {
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), "video/*").putExtra("title", title)
        ctx.startActivity(Intent.createChooser(i, "Otvori u playeru"))
    } catch (e: Exception) {
        Toast.makeText(ctx, "Nema instaliranog eksternog playera", Toast.LENGTH_LONG).show()
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
        catch (e2: Exception) { Toast.makeText(ctx, "YouTube nije instaliran", Toast.LENGTH_LONG).show() }
    }
}

@Composable
private fun Backdrop(url: String?) {
    if (url == null) return
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

/** Subtitle + audio buttons shown next to Play: green frame when a subtitle is ready. */
@Composable
private fun TrackButtons(search: String, season: Int?, episode: Int?, sub: File?, audio: String?, onSub: (File?) -> Unit, onAudio: (String?) -> Unit) {
    var showSubs by remember { mutableStateOf(false) }
    var showAudio by remember { mutableStateOf(false) }
    Button(if (sub != null) "Titl: ${Subs.langName(sub.nameWithoutExtension.substringAfterLast('_'))}" else "Titlovi",
        R.drawable.lc_captions, outline = if (sub != null) C.Green else null) { showSubs = true }
    Button(if (audio != null) "Audio: ${audio.uppercase()}" else "Audio", R.drawable.lc_volume) { showAudio = true }
    if (showSubs) SubtitlePicker(search, season, episode, onDismiss = { showSubs = false }) { f -> onSub(f); showSubs = false }
    if (showAudio) AudioPicker(audio, onDismiss = { showAudio = false }) { a -> onAudio(a); showAudio = false }
}

@Composable
fun MovieDetailScreen(v: Vod, nav: Nav) {
    val ctx = LocalContext.current
    val p = remember { Store.active(ctx) } ?: return
    val info by load(v.id) { Repo.movieInfo(p, v.id) }
    val meta by load(v.name, Prefs.tmdbKey) { Tmdb.find(v.name, tv = false) }
    var sub by remember { mutableStateOf<File?>(null) }
    var audio by remember { mutableStateOf<String?>(null) }
    val playFr = remember { FocusRequester() }
    val i = (info as? Load.Ok)?.value
    val m = (meta as? Load.Ok)?.value
    Box(Modifier.fillMaxSize()) {
        Backdrop(m?.backdrop ?: i?.backdrop)
        Row(Modifier.fillMaxSize().padding(horizontal = 44.dp, vertical = 32.dp), horizontalArrangement = Arrangement.spacedBy(34.dp)) {
            Box(Modifier.width(220.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(18.dp)).background(C.Tile)) {
                val poster = m?.poster ?: v.poster
                if (poster != null) AsyncImage(model = poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                T("FILM", 12.sp, C.RedText, FontWeight.Bold)
                T(m?.title ?: cleanTitle(v.name).first, 34.sp, C.Text, FontWeight.Bold, maxLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (m?.year ?: i?.year)?.let { Chip(it) }
                    (m?.runtime ?: i?.duration)?.let { Chip(it) }
                    (m?.genres ?: i?.genre)?.let { Chip(it.take(36)) }
                    qualityOf(v.name)?.let { Chip(it, C.Card, C.Text, C.Soft) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    m?.imdbRating?.let { Chip("IMDb $it", Color(0xFFF5C518), Color(0xFF0B0D12)) }
                    m?.tmdbRating?.let { Chip("TMDB ${(it * 10).toInt()}%", Color(0x2901B4E4), Color(0xFF67D4F5)) }
                    if (m == null) (i?.rating ?: v.rating)?.toDoubleOrNull()?.takeIf { it > 0 }?.let { Chip("★ ${String.format("%.1f", it)}", C.Card, C.Amber) }
                }
                val plot = m?.overview ?: i?.plot
                if (meta is Load.Loading && plot == null) T("Učitavam opis…", 14.sp, C.Muted)
                plot?.let { T(it + if (m?.overviewLang == "en" && Prefs.metaLang != "en") "  (EN)" else "", 15.sp, C.Soft, maxLines = 5) }
                if (m != null) PeopleRow(m.people) else {
                    i?.cast?.let { T("Glumci: $it", 13.sp, C.Muted, maxLines = 2) }
                    i?.director?.let { T("Režija: $it", 13.sp, C.Muted) }
                }
                if (!Tmdb.enabled()) T("Za postere, opis na bosanskom i glumce upiši TMDB ključ: Postavke › Filmovi i titlovi", 12.sp, C.Muted, maxLines = 2)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button("Pokreni", R.drawable.lc_play, primary = true, focusRequester = playFr) {
                        nav.push(Screen.Player(listOf(PlayItem(m?.title ?: v.name, v.url, v.poster, null, null, v.name, null, null, sub?.path, audio)), 0, live = false))
                    }
                    TrackButtons(v.name, null, null, sub, audio, { sub = it }, { audio = it })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button("Eksterni player", R.drawable.lc_link) { openExternal(ctx, v.url, v.name) }
                    Button("Trailer", R.drawable.lc_play) { openTrailer(ctx, v.name, m?.trailerKey) }
                }
            }
        }
    }
    LaunchedEffect(Unit) { try { playFr.requestFocus() } catch (_: Exception) {} }
}

@Composable
fun SeriesDetailScreen(s: SeriesItem, nav: Nav) {
    val ctx = LocalContext.current
    val p = remember { Store.active(ctx) } ?: return
    val eps by load(s.id) { Repo.episodes(p, s.id) }
    val meta by load(s.name, Prefs.tmdbKey) { Tmdb.find(s.name, tv = true) }
    val m = (meta as? Load.Ok)?.value
    var season by remember { mutableIntStateOf(-1) }
    var audio by remember { mutableStateOf<String?>(null) }
    var showAudio by remember { mutableStateOf(false) }
    val firstFr = remember { FocusRequester() }
    Box(Modifier.fillMaxSize()) {
        Backdrop(m?.backdrop ?: s.cover)
        Row(Modifier.fillMaxSize().padding(horizontal = 44.dp, vertical = 28.dp), horizontalArrangement = Arrangement.spacedBy(30.dp)) {
            Box(Modifier.width(180.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(16.dp)).background(C.Tile)) {
                val poster = m?.poster ?: s.cover
                if (poster != null) AsyncImage(model = poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                T("SERIJA", 12.sp, C.RedText, FontWeight.Bold)
                T(m?.title ?: cleanTitle(s.name).first, 32.sp, C.Text, FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    m?.year?.let { Chip(it) }; m?.genres?.let { Chip(it.take(36)) }
                    m?.imdbRating?.let { Chip("IMDb $it", Color(0xFFF5C518), Color(0xFF0B0D12)) }
                    m?.tmdbRating?.let { Chip("TMDB ${(it * 10).toInt()}%", Color(0x2901B4E4), Color(0xFF67D4F5)) }
                    Spacer(Modifier.weight(1f))
                    Button(if (audio != null) "Audio: ${audio!!.uppercase()}" else "Audio", R.drawable.lc_volume) { showAudio = true }
                }
                (m?.overview ?: s.plot)?.let { T(it, 14.sp, C.Soft, maxLines = 3) }
                when (val st = eps) {
                    is Load.Loading -> Loading("Učitavam epizode…")
                    is Load.Err -> Message("Epizode se nisu učitale", st.message)
                    is Load.Ok -> {
                        val all = st.value
                        if (all.isEmpty()) { Message("Nema epizoda"); return@Column }
                        val seasons = all.map { it.season }.distinct().sorted()
                        val cur = if (season in seasons) season else seasons.first()
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(seasons) { n ->
                                Focusable(onClick = { season = n }, onFocus = { season = n },
                                    normal = if (n == cur) C.CardHi else C.Card, shape = RoundedCornerShape(50), modifier = Modifier.height(40.dp)) { _ ->
                                    T("Sezona $n", 14.sp, C.Text, FontWeight.Medium, modifier = Modifier.align(Alignment.Center).padding(horizontal = 18.dp))
                                }
                            }
                        }
                        val list = all.filter { it.season == cur }
                        val play = list.map {
                            PlayItem("${m?.title ?: cleanTitle(s.name).first} · S${it.season}E${it.num}", it.url, s.cover, it.title,
                                null, s.name, it.season, it.num, null, audio)
                        }
                        val wrap = remember { Wrap() }
                        val ls = rememberLazyListState()
                        WrapEffect(wrap, ls)
                        LazyColumn(state = ls, verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                            itemsIndexed(list) { idx, e ->
                                Focusable(onClick = { nav.push(Screen.Player(play, idx, live = false)) },
                                    focusRequester = wrap.requester(idx, if (idx == 0) firstFr else null),
                                    normal = C.Card, zoom = 1.01f, modifier = Modifier.fillMaxWidth().height(64.dp).wrapKeys(idx, list.size, wrap)) { f ->
                                    Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        T("${e.num}", 18.sp, C.Muted, FontWeight.SemiBold, modifier = Modifier.width(36.dp))
                                        Column(Modifier.weight(1f)) {
                                            T(e.title.ifBlank { "Epizoda ${e.num}" }, 15.sp, if (f) C.Text else C.Soft, FontWeight.Medium)
                                            e.plot?.let { T(it, 12.sp, C.Muted) }
                                        }
                                        qualityOf(e.title)?.let { Badge(it, C.CardHi, C.Text) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showAudio) AudioPicker(audio, onDismiss = { showAudio = false }) { a -> audio = a; showAudio = false }
}
