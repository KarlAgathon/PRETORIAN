package com.pretorian.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        Look.load(this)
        Lock.load()
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

object BuildInfo { const val VERSION = "0.2" }

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

    // look for a new version at start and every 6 hours
    LaunchedEffect(Unit) { while (true) { Updater.check(ctx); delay(6 * 3600_000L) } }

    val scheme = if (C.IsLight) lightColorScheme(primary = C.Red, background = C.Bg, surface = C.Bg)
                 else darkColorScheme(primary = C.Red, background = C.Bg, surface = C.Bg)
    MaterialTheme(colorScheme = scheme) {
        val top = stack.last()
        if (top is Screen.Player) {
            PlayerScreen(top, nav)
        } else if (Lock.simple) {
            Box(Modifier.fillMaxSize().background(C.Bg)) {
                key(version, top) {
                    when (top) {
                        Screen.Tv -> TvScreen(nav)
                        Screen.Movies -> MoviesScreen(nav)
                        Screen.Series -> SeriesScreen(nav)
                        is Screen.MovieDetail -> MovieDetailScreen(top.vod, nav)
                        is Screen.SeriesDetail -> SeriesDetailScreen(top.item, nav)
                        else -> SimpleHome(nav)
                    }
                }
            }
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
        Image(painterResource(R.mipmap.ic_launcher), contentDescription = "PRETORIAN", modifier = Modifier.size(46.dp).clip(CircleShape))
        Spacer(Modifier.height(16.dp))
        items.forEach { RailButton(it.icon, it.label, section == it.screen) { nav.root(it.screen) } }
        Spacer(Modifier.weight(1f))
        RailButton(R.drawable.lc_settings, "Postavke", section == Screen.Settings, badge = Updater.available != null) { nav.root(Screen.Settings) }
    }
}

@Composable
private fun RailButton(icon: Int, label: String, selected: Boolean, badge: Boolean = false, onClick: () -> Unit) {
    Box {
        Focusable(
            modifier = Modifier.width(76.dp).height(64.dp),
            onClick = onClick,
            shape = RoundedCornerShape(16.dp),
            normal = if (selected) C.Red else Color.Transparent,
            focused = if (selected) C.Red else C.CardHi,
            zoom = 1.06f,
        ) { f ->
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val c = if (selected) C.OnRed else if (f) C.Text else C.Muted
                Ico(icon, 22.dp, c)
                T(label, 11.sp, c, if (selected) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
        if (badge) Box(Modifier.align(Alignment.TopEnd).offset(x = (-6).dp, y = 4.dp).size(20.dp).clip(CircleShape).background(C.Danger),
            contentAlignment = Alignment.Center) { T("1", 11.sp, Color.White, FontWeight.Bold) }
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
    Row(Modifier.fillMaxWidth().padding(start = 36.dp, end = 36.dp, top = 26.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            T(title, 28.sp, C.Text, FontWeight.Bold)
            if (subtitle != null) T(subtitle, 14.sp, C.Muted, modifier = Modifier.padding(bottom = 4.dp))
        }
        Clock()
    }
}

fun expiryText(exp: String): Pair<String, Int>? {
    if (exp.isBlank()) return null
    if (exp == "0") return "Bez isteka" to 0
    val sec = exp.toLongOrNull() ?: return null
    val days = (sec * 1000 - System.currentTimeMillis()) / 86_400_000L
    val date = SimpleDateFormat("d. M. yyyy", Locale.getDefault()).format(Date(sec * 1000))
    return when {
        days < 0 -> "Istekla $date" to 2
        days < 7 -> "Ističe $date (još $days d)" to 1
        else -> "Ističe $date" to 0
    }
}

@Composable
fun UpdateBanner(nav: Nav) {
    val u = Updater.available ?: return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxWidth().padding(horizontal = 36.dp).clip(RoundedCornerShape(16.dp)).background(C.CardFocus)
        .border(1.dp, C.Red, RoundedCornerShape(16.dp)).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Ico(R.drawable.lc_download, 22.dp, C.RedText)
        T("Stigla je nova verzija ${u.name}", 15.sp, C.Text, FontWeight.SemiBold, modifier = Modifier.weight(1f))
        val pr = Updater.progress
        if (pr in 0..100) T("Preuzimam $pr %", 14.sp, C.Soft)
        else if (pr == 101) T("Instaliram…", 14.sp, C.Soft)
        else Button("Ažuriraj", R.drawable.lc_download, primary = true) { scope.launch { Updater.install(ctx, u) } }
    }
}

@Composable
fun HomeScreen(nav: Nav) {
    val ctx = LocalContext.current
    val p = remember { Store.active(ctx) }
    val first = remember { FocusRequester() }
    Column(Modifier.fillMaxSize()) {
        val exp = p?.let { expiryText(it.exp) }
        Header("Početna", p?.let { "${it.name} · ${if (it.type == SourceType.XTREAM) "Xtream Codes" else "M3U"}" + (exp?.let { e -> " · ${e.first}" } ?: "") })
        UpdateBanner(nav)
        if (p == null) { Message("Nema playliste", "Dodaj playlistu u Postavkama"); return@Column }
        Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 36.dp, vertical = 20.dp), contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                HomeTile("TV", R.drawable.lc_tv, first) { nav.root(Screen.Tv) }
                HomeTile("Filmovi", R.drawable.lc_film) { nav.root(Screen.Movies) }
                HomeTile("Serije", R.drawable.lc_monitor) { nav.root(Screen.Series) }
            }
        }
        T("PRETORIAN ${BuildInfo.VERSION}", 12.sp, C.Muted, modifier = Modifier.padding(start = 36.dp, end = 36.dp, bottom = 20.dp))
    }
    LaunchedEffect(Unit) { try { first.requestFocus() } catch (_: Exception) {} }
}

/** Big menu tile; size and style come from Settings › Appearance. */
@Composable
fun HomeTile(label: String, icon: Int, fr: FocusRequester? = null, sizeOverride: Dp? = null, onClick: () -> Unit) {
    val size = sizeOverride ?: when (Look.tileSize) { 0 -> 180.dp; 2 -> 280.dp; else -> 230.dp }
    val style = Look.tileStyle
    val shape = RoundedCornerShape(size * 0.13f)
    Focusable(
        modifier = Modifier.size(size * 1.12f, size).then(if (style != 0) Modifier.border(1.5.dp, C.Line, shape) else Modifier),
        onClick = onClick,
        focusRequester = fr,
        shape = shape,
        normal = when (style) { 1 -> C.Card; 2 -> Color.Transparent; else -> C.Tile },
        focused = when (style) { 1 -> C.CardFocus; 2 -> C.CardFocus; else -> C.Red },
        zoom = 1.06f,
    ) { f ->
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(size * 0.07f)) {
            val onAccent = f && style == 0
            Box(Modifier.size(size * 0.38f).clip(CircleShape).background(if (onAccent) C.OnRed.copy(alpha = 0.18f) else C.Red.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center) {
                Ico(icon, size * 0.2f, if (onAccent) C.OnRed else C.RedText)
            }
            T(label, (size.value * 0.12f).sp, if (onAccent) C.OnRed else C.Text, FontWeight.Bold)
        }
    }
}

/** Simple mode: only TV, Movies, Series, and a small "Advanced mode" button behind the PIN. */
@Composable
fun SimpleHome(nav: Nav) {
    val first = remember { FocusRequester() }
    var warn by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.mipmap.ic_launcher), contentDescription = null, modifier = Modifier.size(54.dp).clip(CircleShape))
            T("PRETORIAN", 26.sp, C.Text, FontWeight.Bold, modifier = Modifier.padding(start = 14.dp).weight(1f))
            Clock()
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                HomeTile("TV", R.drawable.lc_tv, first, 260.dp) { nav.push(Screen.Tv) }
                HomeTile("Filmovi", R.drawable.lc_film, null, 260.dp) { nav.push(Screen.Movies) }
                HomeTile("Serije", R.drawable.lc_monitor, null, 260.dp) { nav.push(Screen.Series) }
            }
        }
        Button("Napredni mod", R.drawable.lc_lock) { warn = true }
    }
    if (warn) Popup("Aktivirati napredni mod?", onDismiss = { warn = false }) {
        T("Pažnja: aktivirate napredni mod i dodatne opcije. Nastavite s pažnjom, jer možete izazvati nepoželjne posljedice i pokvariti podešavanja programa.",
            16.sp, C.Soft, maxLines = 5)
        OptionRow("Ostani u jednostavnom modu", R.drawable.lc_check) { warn = false }
        OptionRow("Nastavi i unesi PIN", R.drawable.lc_key_round, color = C.Amber) { warn = false; pin = true }
    }
    if (pin) PinDialog("PIN za napredni mod", onDismiss = { pin = false }) { entered ->
        val err = Lock.check(entered)
        if (err == null) { pin = false; Lock.switchSimple(false); nav.root(Screen.Home) }
        err
    }
    LaunchedEffect(Unit) { try { first.requestFocus() } catch (_: Exception) {} }
}
