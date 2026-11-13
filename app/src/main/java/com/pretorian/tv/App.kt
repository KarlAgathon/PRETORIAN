package com.pretorian.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PretorianApp() }
    }
}

sealed class Screen {
    object Home : Screen()
    object Tv : Screen()
    object Movies : Screen()
    object Series : Screen()
    object Settings : Screen()
    object AddPlaylist : Screen()
    data class MovieDetail(val vod: Vod) : Screen()
    data class SeriesDetail(val item: SeriesItem) : Screen()
    data class Player(val items: List<PlayItem>, val index: Int, val live: Boolean) : Screen()
}

class Nav(
    val push: (Screen) -> Unit,
    val back: () -> Unit,
    val root: (Screen) -> Unit,
    val playlistsChanged: () -> Unit,
)

@Composable
fun PretorianApp() {
    val ctx = LocalContext.current
    val stack = remember {
        mutableStateListOf<Screen>(if (Store.playlists(ctx).isEmpty()) Screen.AddPlaylist else Screen.Home)
    }
    var version by remember { mutableIntStateOf(0) }
    val nav = remember {
        Nav(
            push = { stack.add(it) },
            back = { if (stack.size > 1) stack.removeAt(stack.lastIndex) },
            root = { s -> stack.clear(); stack.add(Screen.Home); if (s != Screen.Home) stack.add(s) },
            playlistsChanged = { Repo.clear(); version++ },
        )
    }
    BackHandler(enabled = stack.size > 1) { nav.back() }

    MaterialTheme(colorScheme = darkColorScheme(primary = C.Red, background = C.Bg, surface = C.Bg)) {
        val top = stack.last()
        if (top is Screen.Player) {
            PlayerScreen(top, nav)
        } else {
            Row(Modifier.fillMaxSize().background(C.Bg)) {
                Rail(top, nav)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    key(version, top) {
                        when (top) {
                            Screen.Home -> HomeScreen(nav)
                            Screen.Tv -> TvScreen(nav)
                            Screen.Movies -> MoviesScreen(nav)
                            Screen.Series -> SeriesScreen(nav)
                            Screen.Settings -> SettingsScreen(nav)
                            Screen.AddPlaylist -> AddPlaylistScreen(nav)
                            is Screen.MovieDetail -> MovieDetailScreen(top.vod, nav)
                            is Screen.SeriesDetail -> SeriesDetailScreen(top.item, nav)
                            is Screen.Player -> {}
                        }
                    }
                }
            }
        }
    }
}

private data class RailItem(val screen: Screen, val icon: Int, val label: String)

@Composable
fun Rail(current: Screen, nav: Nav) {
    val items = listOf(
        RailItem(Screen.Home, R.drawable.lc_house, "Početna"),
        RailItem(Screen.Tv, R.drawable.lc_tv, "TV"),
        RailItem(Screen.Movies, R.drawable.lc_film, "Filmovi"),
        RailItem(Screen.Series, R.drawable.lc_monitor, "Serije"),
    )
    val section = when (current) {
        is Screen.MovieDetail -> Screen.Movies
        is Screen.SeriesDetail -> Screen.Series
        Screen.AddPlaylist -> Screen.Settings
        else -> current
    }
    Column(
        Modifier.width(96.dp).fillMaxHeight().background(C.Rail).padding(vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Image(
            painterResource(R.mipmap.ic_launcher), contentDescription = "PRETORIAN",
            modifier = Modifier.size(46.dp).clip(CircleShape).background(Color.White),
        )
        Spacer(Modifier.height(16.dp))
        items.forEach { RailButton(it.icon, it.label, section == it.screen) { nav.root(it.screen) } }
        Spacer(Modifier.weight(1f))
        RailButton(R.drawable.lc_settings, "Postavke", section == Screen.Settings) { nav.root(Screen.Settings) }
    }
}

@Composable
private fun RailButton(icon: Int, label: String, selected: Boolean, onClick: () -> Unit) {
    Focusable(
        modifier = Modifier.width(76.dp).height(64.dp),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        normal = if (selected) C.Red else Color.Transparent,
        focused = if (selected) Color(0xFFFF2A31) else Color(0x1FFFFFFF),
        zoom = 1.06f,
    ) { f ->
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Ico(icon, 22.dp, if (selected || f) C.Text else C.Muted)
            T(label, 11.sp, if (selected || f) C.Text else C.Muted, if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

@Composable
fun Clock() {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(15_000) } }
    Column(horizontalAlignment = Alignment.End) {
        T(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), 22.sp, C.Text, FontWeight.SemiBold)
        T(SimpleDateFormat("EEE, d. MMM", Locale("bs")).format(now), 12.sp, C.Muted)
    }
}

@Composable
fun Header(title: String, subtitle: String? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 36.dp, end = 36.dp, top = 26.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            T(title, 28.sp, C.Text, FontWeight.Bold)
            if (subtitle != null) T(subtitle, 14.sp, C.Muted, modifier = Modifier.padding(bottom = 4.dp))
        }
        Clock()
    }
}

@Composable
fun HomeScreen(nav: Nav) {
    val ctx = LocalContext.current
    val p = remember { Store.active(ctx) }
    val first = remember { FocusRequester() }
    Column(Modifier.fillMaxSize()) {
        Header("Početna", p?.let { "${it.name} · ${if (it.type == SourceType.XTREAM) "Xtream Codes" else "M3U"}" })
        if (p == null) {
            Message("Nema playliste", "Dodaj playlistu u Postavkama")
            return@Column
        }
        Row(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 36.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
            BigTile("TV", R.drawable.lc_tv, Modifier.weight(1f), first) { nav.root(Screen.Tv) }
            BigTile("Filmovi", R.drawable.lc_film, Modifier.weight(1f)) { nav.root(Screen.Movies) }
            BigTile("Serije", R.drawable.lc_monitor, Modifier.weight(1f)) { nav.root(Screen.Series) }
        }
        T("PRETORIAN ${BuildInfo.VERSION} · rana verzija: TV, filmovi i serije. EPG, Sports Center i ostalo dolaze u sljedećim verzijama.",
            12.sp, C.Muted, modifier = Modifier.padding(start = 36.dp, end = 36.dp, bottom = 22.dp))
    }
    LaunchedEffect(Unit) { try { first.requestFocus() } catch (_: Exception) {} }
}

@Composable
private fun BigTile(label: String, icon: Int, modifier: Modifier, fr: FocusRequester? = null, onClick: () -> Unit) {
    Focusable(
        modifier = modifier.height(260.dp),
        onClick = onClick,
        focusRequester = fr,
        shape = RoundedCornerShape(30.dp),
        normal = Color(0xFF1A1F2A),
        focused = C.Red,
        zoom = 1.05f,
    ) { _ ->
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Ico(icon, 84.dp)
            T(label, 32.sp, C.Text, FontWeight.Bold)
        }
    }
}

object BuildInfo { const val VERSION = "0.1" }
