package com.pretorian.tv

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* ---------------------------------------------------------------- TV */

@Composable
fun TvScreen(nav: Nav) {
    val ctx = LocalContext.current
    val p = remember { Store.active(ctx) } ?: run { NoPlaylist(nav); return }
    val groupsState by load(p.id) { Repo.liveGroups(p) }
    var focusedGroup by remember { mutableStateOf<Category?>(null) }
    var group by remember { mutableStateOf<Category?>(null) }
    var favVersion by remember { mutableIntStateOf(0) }
    val firstGroup = remember { FocusRequester() }

    LaunchedEffect(focusedGroup) { val g = focusedGroup ?: return@LaunchedEffect; delay(350); group = g }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(290.dp).fillMaxHeight().padding(start = 18.dp, end = 12.dp, top = 24.dp)) {
            T("TV", 28.sp, C.Text, FontWeight.Bold, modifier = Modifier.padding(start = 10.dp, bottom = 4.dp))
            T(p.name, 13.sp, C.Muted, modifier = Modifier.padding(start = 10.dp, bottom = 12.dp))
            when (val s = groupsState) {
                is Load.Loading -> Loading("Učitavam grupe…")
                is Load.Err -> Message("Greška", s.message)
                is Load.Ok -> {
                    val all = listOf(Category(FAV_GROUP, "★ Favoriti")) + s.value
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                        itemsIndexed(all) { i, g ->
                            val selected = group?.id == g.id
                            Focusable(
                                modifier = Modifier.fillMaxWidth().height(42.dp),
                                onClick = { group = g },
                                onFocus = { focusedGroup = g },
                                focusRequester = if (i == 0) firstGroup else null,
                                normal = if (selected) Color(0x24E5141B) else Color.Transparent,
                                zoom = 1.0f,
                            ) { _ ->
                                T(g.name, 14.sp, if (selected) C.Text else C.Soft, if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 14.dp))
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
                Message("Izaberi grupu", "Lijevo izaberi grupu kanala")
            } else {
                ChannelList(p, g, favVersion, nav) { favVersion++ }
            }
        }
    }
}

@Composable
private fun ChannelList(p: Playlist, g: Category, favVersion: Int, nav: Nav, onFavChanged: () -> Unit) {
    val ctx = LocalContext.current
    val state by load(p.id, g.id, if (g.id == FAV_GROUP) favVersion else 0) {
        if (g.id == FAV_GROUP) Store.favorites(ctx, p.id) else Repo.channels(p, g.id)
    }
    Column(Modifier.fillMaxSize().padding(start = 22.dp, end = 30.dp, top = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            T(g.name, 20.sp, C.Text, FontWeight.SemiBold, modifier = Modifier.weight(1f))
            T("OK = gledaj · dugo OK ili Meni = favorit", 12.sp, C.Muted)
        }
        when (val s = state) {
            is Load.Loading -> Loading("Učitavam kanale…")
            is Load.Err -> Message("Kanali se nisu učitali", s.message)
            is Load.Ok -> {
                val list = s.value
                if (list.isEmpty()) {
                    Message(if (g.id == FAV_GROUP) "Nema favorita" else "Grupa je prazna",
                        if (g.id == FAV_GROUP) "Na kanalu drži OK ili pritisni Meni da ga dodaš" else null)
                } else {
                    val items = list.map { PlayItem(it.name, it.url, it.logo, "${it.num}", it) }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 30.dp)) {
                        itemsIndexed(list) { i, ch ->
                            var fav by remember(ch.url, favVersion) { mutableStateOf(Store.isFavorite(ctx, p.id, ch)) }
                            val toggle = {
                                Store.toggleFavorite(ctx, p.id, ch); fav = !fav; onFavChanged()
                                Toast.makeText(ctx, if (fav) "Dodano u favorite" else "Uklonjeno iz favorita", Toast.LENGTH_SHORT).show()
                            }
                            Focusable(
                                modifier = Modifier.fillMaxWidth().height(58.dp).onPreviewKeyEvent { e ->
                                    if (e.type == KeyEventType.KeyUp && e.key == Key.Menu) { toggle(); true } else false
                                },
                                onClick = { nav.push(Screen.Player(items, i, live = true)) },
                                onLongClick = { toggle() },
                                normal = Color(0x08FFFFFF),
                                zoom = 1.01f,
                            ) { f ->
                                Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    T("${ch.num}", 13.sp, C.Muted, modifier = Modifier.width(44.dp))
                                    ChannelLogo(ch.logo, ch.name)
                                    T(ch.name, 16.sp, if (f) C.Text else C.Soft, if (f) FontWeight.SemiBold else FontWeight.Normal,
                                        modifier = Modifier.weight(1f))
                                    if (fav) T("★", 16.sp, C.Amber)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelLogo(url: String?, name: String) {
    Box(Modifier.width(62.dp).height(38.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1C2230)),
        contentAlignment = Alignment.Center) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp))
        } else {
            T(name.filter { it.isLetterOrDigit() }.take(3).uppercase(), 11.sp, C.Muted, FontWeight.Bold)
        }
    }
}

@Composable
fun NoPlaylist(nav: Nav) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Message("Nema aktivne playliste", "Dodaj Xtream Codes ili M3U playlistu")
        Button("Dodaj playlistu", R.drawable.lc_plus, primary = true) { nav.push(Screen.AddPlaylist) }
    }
}

/* ---------------------------------------------------------- Movies */

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
        Message(if (series) "Serije nisu dostupne" else "Filmovi nisu dostupni", "Ova M3U playlista ima samo TV kanale. Filmovi i serije rade s Xtream Codes playlistom.")
        return
    }
    val groupsState by load(p.id, series) { if (series) Repo.seriesGroups(p) else Repo.vodGroups(p) }
    var focusedGroup by remember { mutableStateOf<Category?>(null) }
    var group by remember { mutableStateOf<Category?>(null) }
    val firstGroup = remember { FocusRequester() }
    LaunchedEffect(focusedGroup) { val g = focusedGroup ?: return@LaunchedEffect; delay(400); group = g }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(290.dp).fillMaxHeight().padding(start = 18.dp, end = 12.dp, top = 24.dp)) {
            T(if (series) "Serije" else "Filmovi", 28.sp, C.Text, FontWeight.Bold, modifier = Modifier.padding(start = 10.dp, bottom = 14.dp))
            when (val s = groupsState) {
                is Load.Loading -> Loading("Učitavam kategorije…")
                is Load.Err -> Message("Greška", s.message)
                is Load.Ok -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                        itemsIndexed(s.value) { i, g ->
                            val selected = group?.id == g.id
                            Focusable(
                                modifier = Modifier.fillMaxWidth().height(42.dp),
                                onClick = { group = g },
                                onFocus = { focusedGroup = g },
                                focusRequester = if (i == 0) firstGroup else null,
                                normal = if (selected) Color(0x24E5141B) else Color.Transparent,
                                zoom = 1.0f,
                            ) { _ ->
                                T(g.name, 14.sp, if (selected) C.Text else C.Soft, if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 14.dp))
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
                Message("Izaberi kategoriju")
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
}

@Composable
private fun PosterGrid(title: String, items: List<Poster>) {
    Column(Modifier.fillMaxSize().padding(start = 22.dp, end = 26.dp, top = 24.dp)) {
        T("$title · ${items.size}", 20.sp, C.Text, FontWeight.SemiBold, modifier = Modifier.padding(bottom = 14.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(bottom = 30.dp, top = 4.dp),
        ) {
            items(items) { it ->
                Focusable(modifier = Modifier.fillMaxWidth(), onClick = it.open, normal = Color.Transparent,
                    focused = Color(0x14FFFFFF), zoom = 1.05f) { f ->
                    Column(Modifier.padding(6.dp)) {
                        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1C2230))) {
                            if (it.image != null) {
                                AsyncImage(model = it.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                T(it.name, 13.sp, C.Muted, maxLines = 4, modifier = Modifier.align(Alignment.Center).padding(10.dp))
                            }
                            val r = it.rating?.toDoubleOrNull()
                            if (r != null && r > 0) {
                                Box(Modifier.padding(7.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xEEF5C518)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                    T(String.format("%.1f", r), 11.sp, Color(0xFF0B0D12), FontWeight.Bold)
                                }
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

/* ------------------------------------------------------- Details */

fun openExternal(ctx: android.content.Context, url: String, title: String) {
    try {
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), "video/*").putExtra("title", title)
        ctx.startActivity(Intent.createChooser(i, "Otvori u playeru"))
    } catch (e: Exception) {
        Toast.makeText(ctx, "Nema instaliranog eksternog playera", Toast.LENGTH_LONG).show()
    }
}

fun openTrailer(ctx: android.content.Context, title: String) {
    val q = Uri.encode("$title trailer")
    try {
        ctx.startActivity(Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube.tv").putExtra("query", "$title trailer"))
    } catch (e: Exception) {
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$q"))) }
        catch (e2: Exception) { Toast.makeText(ctx, "YouTube nije instaliran", Toast.LENGTH_LONG).show() }
    }
}

@Composable
private fun Backdrop(url: String?) {
    if (url == null) return
    Box(Modifier.fillMaxSize()) {
        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(C.Bg, Color(0xE60B0D12), Color(0x660B0D12)))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, C.Bg), startY = 300f)))
    }
}

@Composable
fun MovieDetailScreen(v: Vod, nav: Nav) {
    val ctx = LocalContext.current
    val p = remember { Store.active(ctx) } ?: return
    val info by load(v.id) { Repo.movieInfo(p, v.id) }
    val playFr = remember { FocusRequester() }
    val i = (info as? Load.Ok)?.value
    Box(Modifier.fillMaxSize()) {
        Backdrop(i?.backdrop)
        Row(Modifier.fillMaxSize().padding(horizontal = 44.dp, vertical = 36.dp), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            Box(Modifier.width(230.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(18.dp)).background(Color(0xFF1C2230))) {
                if (v.poster != null) AsyncImage(model = v.poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                T("FILM", 12.sp, C.RedText, FontWeight.Bold)
                T(v.name, 36.sp, C.Text, FontWeight.Bold, maxLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    i?.year?.let { Chip(it) }
                    i?.duration?.let { Chip(it) }
                    i?.genre?.let { Chip(it.take(40)) }
                    (i?.rating ?: v.rating)?.toDoubleOrNull()?.takeIf { it > 0 }?.let { Chip("★ ${String.format("%.1f", it)}", Color(0x24FACC15), C.Amber) }
                }
                if (info is Load.Loading) T("Učitavam opis…", 14.sp, C.Muted)
                i?.plot?.let { T(it, 15.sp, C.Soft, maxLines = 5) }
                i?.cast?.let { T("Glumci: $it", 13.sp, C.Muted, maxLines = 2) }
                i?.director?.let { T("Režija: $it", 13.sp, C.Muted) }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button("Pokreni", R.drawable.lc_play, primary = true, focusRequester = playFr) {
                        nav.push(Screen.Player(listOf(PlayItem(v.name, v.url, v.poster)), 0, live = false))
                    }
                    Button("Eksterni player", R.drawable.lc_link) { openExternal(ctx, v.url, v.name) }
                    Button("Trailer", R.drawable.lc_play) { openTrailer(ctx, v.name) }
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
    var season by remember { mutableIntStateOf(-1) }
    val firstFr = remember { FocusRequester() }
    Box(Modifier.fillMaxSize()) {
        Backdrop(s.cover)
        Column(Modifier.fillMaxSize().padding(horizontal = 44.dp, vertical = 30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            T("SERIJA", 12.sp, C.RedText, FontWeight.Bold)
            T(s.name, 34.sp, C.Text, FontWeight.Bold)
            s.plot?.let { T(it, 14.sp, C.Soft, maxLines = 3, modifier = Modifier.fillMaxWidth(0.7f)) }
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
                                normal = if (n == cur) Color(0x33FFFFFF) else Color(0x12FFFFFF), shape = RoundedCornerShape(50),
                                modifier = Modifier.height(40.dp)) { _ ->
                                T("Sezona $n", 14.sp, C.Text, FontWeight.Medium, modifier = Modifier.align(Alignment.Center).padding(horizontal = 18.dp))
                            }
                        }
                    }
                    val list = all.filter { it.season == cur }
                    val play = list.map { PlayItem("${s.name} · S${it.season}E${it.num}", it.url, s.cover, it.title) }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                        itemsIndexed(list) { idx, e ->
                            Focusable(onClick = { nav.push(Screen.Player(play, idx, live = false)) },
                                focusRequester = if (idx == 0) firstFr else null,
                                normal = Color(0x0FFFFFFF), zoom = 1.01f, modifier = Modifier.fillMaxWidth(0.75f).height(64.dp)) { f ->
                                Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    T("${e.num}", 18.sp, C.Muted, FontWeight.SemiBold, modifier = Modifier.width(36.dp))
                                    Column(Modifier.weight(1f)) {
                                        T(e.title.ifBlank { "Epizoda ${e.num}" }, 15.sp, if (f) C.Text else C.Soft, FontWeight.Medium)
                                        e.plot?.let { T(it, 12.sp, C.Muted) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------- Settings */

@Composable
fun SettingsScreen(nav: Nav) {
    val ctx = LocalContext.current
    var list by remember { mutableStateOf(Store.playlists(ctx)) }
    val addFr = remember { FocusRequester() }
    Column(Modifier.fillMaxSize()) {
        Header("Postavke", "Playliste")
        Column(Modifier.fillMaxSize().padding(horizontal = 36.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button("Dodaj playlistu", R.drawable.lc_plus, primary = true, focusRequester = addFr) { nav.push(Screen.AddPlaylist) }
                Button("Ažuriraj sve", R.drawable.lc_refresh_cw) {
                    Repo.clear(); Toast.makeText(ctx, "Playliste će se ponovo učitati", Toast.LENGTH_SHORT).show()
                }
            }
            T("Aktivna je samo jedna playlista. OK na drugoj je aktivira.", 13.sp, C.Muted)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 30.dp)) {
                items(list, key = { it.id }) { pl ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Focusable(
                            modifier = Modifier.weight(1f).height(74.dp),
                            onClick = { Store.activate(ctx, pl.id); list = Store.playlists(ctx); nav.playlistsChanged() },
                            normal = if (pl.active) Color(0x1FE5141B) else Color(0x0FFFFFFF),
                            zoom = 1.01f,
                        ) { _ ->
                            Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Box(Modifier.size(22.dp).clip(RoundedCornerShape(50)).background(if (pl.active) C.Red else Color(0x33FFFFFF)),
                                    contentAlignment = Alignment.Center) { if (pl.active) Dot(Color.White, 8.dp) }
                                Column(Modifier.weight(1f)) {
                                    T(pl.name, 17.sp, C.Text, FontWeight.SemiBold)
                                    T(if (pl.type == SourceType.XTREAM) "Xtream Codes · ${pl.server}" else "M3U · ${pl.url}", 12.sp, C.Muted)
                                }
                                if (pl.active) Chip("AKTIVNA", C.Red, Color.White)
                            }
                        }
                        Button("Obriši", R.drawable.lc_trash_2) {
                            val rest = Store.playlists(ctx).filter { it.id != pl.id }
                            Store.savePlaylists(ctx, if (rest.none { it.active } && rest.isNotEmpty()) rest.mapIndexed { i, x -> x.copy(active = i == 0) } else rest)
                            list = Store.playlists(ctx); nav.playlistsChanged()
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { try { addFr.requestFocus() } catch (_: Exception) {} }
}

@Composable
fun AddPlaylistScreen(nav: Nav) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val fm = LocalFocusManager.current
    var tab by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val firstTab = remember { FocusRequester() }

    Column(Modifier.fillMaxSize()) {
        Header("Dodaj playlistu")
        Column(Modifier.fillMaxSize().padding(horizontal = 36.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0x0FFFFFFF)).padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Xtream Codes", "M3U playlista", "Stalker portal").forEachIndexed { i, label ->
                    Focusable(onClick = { if (i < 2) tab = i else Toast.makeText(ctx, "Stalker portal dolazi u verziji 0.2", Toast.LENGTH_SHORT).show() },
                        focusRequester = if (i == 0) firstTab else null,
                        normal = if (tab == i) C.Text else Color.Transparent, focused = if (tab == i) Color.White else Color(0x24FFFFFF),
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.height(44.dp), zoom = 1.0f) { _ ->
                        T(label, 14.sp, if (tab == i) C.Bg else if (i == 2) C.Muted else C.Soft, FontWeight.SemiBold,
                            modifier = Modifier.align(Alignment.Center).padding(horizontal = 20.dp))
                    }
                }
            }
            val next = KeyboardActions(onNext = { fm.moveFocus(FocusDirection.Down) })
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Field("Naziv playliste", name, { name = it }, Modifier.weight(1f), next)
                if (tab == 0) Field("Server adresa (npr. http://server:8080)", server, { server = it }, Modifier.weight(1f), next, KeyboardType.Uri)
                else Field("M3U link", url, { url = it }, Modifier.weight(1f), next, KeyboardType.Uri)
            }
            if (tab == 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Field("Korisničko ime", user, { user = it }, Modifier.weight(1f), next)
                    Field("Lozinka", pass, { pass = it }, Modifier.weight(1f), next, password = true)
                }
            }
            T("Savjet: dugačke linkove i lozinke možeš kucati s telefona preko Google TV aplikacije (ikona tastature).", 12.sp, C.Muted)
            error?.let { T(it, 14.sp, C.RedText, maxLines = 3) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(if (busy) "Provjeravam…" else "Spremi i učitaj", R.drawable.lc_check, primary = true) {
                    if (busy) return@Button
                    val pl = Playlist(
                        id = System.currentTimeMillis().toString(),
                        name = name.trim().ifBlank { if (tab == 0) "Xtream" else "M3U" },
                        type = if (tab == 0) SourceType.XTREAM else SourceType.M3U,
                        server = server.trim(), user = user.trim(), pass = pass.trim(), url = url.trim(), active = true,
                    )
                    if (tab == 0 && (pl.server.isBlank() || pl.user.isBlank() || pl.pass.isBlank())) { error = "Unesi server, korisničko ime i lozinku"; return@Button }
                    if (tab == 1 && pl.url.isBlank()) { error = "Unesi M3U link"; return@Button }
                    busy = true; error = null
                    scope.launch {
                        try {
                            val info = Repo.test(pl)
                            Store.savePlaylists(ctx, Store.playlists(ctx).map { it.copy(active = false) } + pl)
                            Toast.makeText(ctx, if (tab == 0 && info.isNotBlank()) "Povezano ✓" else "Učitano: $info", Toast.LENGTH_LONG).show()
                            nav.playlistsChanged()
                            nav.root(Screen.Home)
                        } catch (e: Exception) {
                            error = "Nije uspjelo: ${e.message ?: e.javaClass.simpleName}"
                        } finally { busy = false }
                    }
                }
                if (Store.playlists(ctx).isNotEmpty()) Button("Odustani", R.drawable.lc_x) { nav.back() }
            }
        }
    }
    LaunchedEffect(Unit) { try { firstTab.requestFocus() } catch (_: Exception) {} }
}

@Composable
private fun Field(
    label: String, value: String, onChange: (String) -> Unit, modifier: Modifier,
    actions: KeyboardActions, type: KeyboardType = KeyboardType.Text, password: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        modifier = modifier,
        textStyle = TextStyle(fontFamily = Inter, fontSize = 16.sp, color = C.Text),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else type, imeAction = ImeAction.Next, autoCorrectEnabled = false),
        keyboardActions = actions,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = C.Red, unfocusedBorderColor = Color(0x33FFFFFF),
            focusedLabelColor = C.RedText, unfocusedLabelColor = C.Muted, cursorColor = C.Red,
            focusedTextColor = C.Text, unfocusedTextColor = C.Text,
        ),
    )
}
