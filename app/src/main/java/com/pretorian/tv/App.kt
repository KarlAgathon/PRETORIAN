package com.pretorian.tv

import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        Prefs.init(this)
        NewContent.appCtx = applicationContext
        Repo.diskDir = cacheDir
        Profiles.onStart(this)
        SportsAI.dir = cacheDir
        AutoTranslate.dir = filesDir
        StandardView.onFirstStart(this)   // a new install starts with the built-in standard view
        Look.load(this)
        onyxMigrate(this)
        Perf.init(this)
        // picture cache: limited so the disk never fills up (150 MB on weaker boxes, 400 MB otherwise)
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(this)
            .memoryCache { coil.memory.MemoryCache.Builder(this).maxSizePercent(if (Perf.lite) 0.10 else 0.20).build() }
            .diskCache { coil.disk.DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(if (Perf.lite) 150L shl 20 else 400L shl 20).build() }
            .build())
        CrashLog.watch(this)
        Stats.onStart()
        WhatsNew.check(this)
        Lock.load()
        Notes.load()
        CrashGuard.startedAt = System.currentTimeMillis()
        try { StreamLog.autoClear() } catch (_: Exception) {}
        setContent { PretorianApp() }
    }

    private var stoppedAt = 0L

    // Android asks for memory back: drop lists that are not on the screen, they load again when needed
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN || level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) Repo.trim(level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) try { coil.Coil.imageLoader(this).memoryCache?.clear() } catch (_: Exception) {}
    }

    private var swallowUp = -1

    // any key wakes the screen saver (and is used up for that)
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val ok = event.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == android.view.KeyEvent.KEYCODE_ENTER
        if (event.action == android.view.KeyEvent.ACTION_DOWN && Saver.touch(ok)) { swallowUp = event.keyCode; return true }
        // the release of the key that woke the screen saver is used up too (it must not press a button below)
        if (event.action == android.view.KeyEvent.ACTION_UP && event.keyCode == swallowUp) { swallowUp = -1; return true }
        if (Saver.showing || Saver.preview) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        // give the system confirm screen a moment to send its answer first
        window.decorView.postDelayed({ Updater.onResume(this) }, 1500)
    }

    // back in the app after being away (Home button, other app): ask "Who is watching?" again,
    // but not when coming back from the external player
    override fun onRestart() {
        super.onRestart()
        val away = System.currentTimeMillis() - stoppedAt
        val fromExternal = ExtPlayer.launchedAt > 0 && ExtPlayer.launchedAt >= stoppedAt - 10_000
        if (stoppedAt > 0 && away > 15_000 && !fromExternal && Profiles.askAtStart && Profiles.awayLong(away)) Profiles.picker = true
        // every start of the app opens Home (not after the external player or a short trip to the system)
        if (stoppedAt > 0 && away > 15_000 && !fromExternal) AppHome.tick++
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = System.currentTimeMillis()
        Prefs.put("last_stop", stoppedAt)
        Stats.saveIfDirty()
    }
}

/** Asks the app to go back to Home (a new start from the launcher). */
object AppHome { var tick by mutableIntStateOf(0) }

sealed class Screen {
    object Home : Screen()
    object Tv : Screen()
    object Movies : Screen()
    object Series : Screen()
    object Sports : Screen()
    object Search : Screen()
    object Catchup : Screen()
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

object BuildInfo { const val VERSION = "1.0.8" }

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
            back = {
                if (stack.size > 1) {
                    val left = stack.removeAt(stack.lastIndex)
                    // a new version waits: once per version, right after the player, Settings › Update opens by itself
                    val u = Updater.available
                    if (left is Screen.Player && u != null && Updater.mayAsk(u) && Updater.notify("player") && Prefs.str("upd_asked") != u.name) {
                        Prefs.put("upd_asked", u.name); SettingsNav.section = "update"; stack.add(Screen.Settings)
                    }
                }
            },
            root = { s -> if (s != Screen.Search) SearchMem.clear(); stack.clear(); stack.add(Screen.Home); if (s != Screen.Home) stack.add(s) },
            playlistsChanged = { Repo.clear(); version++ },
        )
    }
    BackHandler(enabled = stack.size > 1) { nav.back() }
    // Settings › General › Confirm exit: Back on Home asks first
    var askExit by remember { mutableStateOf(false) }
    BackHandler(enabled = stack.size <= 1 && General.confirmExit) { askExit = true }
    if (askExit) Popup(tr("Izaći iz PRETORIAN-a?"), { askExit = false }) {
        OptionRow("Izađi", R.drawable.lc_x, color = C.Danger) { askExit = false; (ctx as? android.app.Activity)?.finish() }
        OptionRow("Ostani", R.drawable.lc_check) { askExit = false }
    }
    // goals of your teams (Sport › long OK › My teams): checked every 3 minutes wherever you are, only when you have teams
    LaunchedEffect(Unit) {
        delay(4_000)
        while (true) {
            if (Prefs.bool("bg_sport", true) && (MyTeams.list().isNotEmpty() || MatchFollow.list().isNotEmpty())) try { GoalNote.check(Sports.day(0)) } catch (_: Exception) {}
            delay(if (GoalNote.hot) General.sportLive * 1000L else General.sportEvery * 60_000L)
        }
    }
    // Settings › General › Last channel at start
    LaunchedEffect(Unit) {
        if (General.start != 0 && !General.startedOnce && !Lock.simple) {
            General.startedOnce = true
            delay(if (General.start == 5) 1500 else 50)
            // Settings › General › "When the app opens, you first see": Back from there goes to Home
            if (stack.lastOrNull() == Screen.Home) when (General.start) {
                1 -> nav.push(Screen.Tv)
                2 -> nav.push(Screen.Movies)
                3 -> nav.push(Screen.Series)
                4 -> nav.push(Screen.Sports)
                5 -> Recent.list().firstOrNull()?.let { r -> playChannels(nav, listOf(r.ch), 0) }
            }
        }
    }
    LaunchedEffect(AppHome.tick) { if (AppHome.tick > 0 && Store.playlists(ctx).isNotEmpty()) { Preview.stop(); stack.clear(); stack.add(Screen.Home) } }

    // new version at start and every 6 hours; "new" movies and series once in 12 hours; weekly backup
    LaunchedEffect(Unit) {
        Backup.auto(ctx)
        var first = true
        var epAt = 0L
        while (true) {
            // Settings › Update › Check: at start / hourly / daily / monthly
            val ev = Prefs.str("upd_every", "1"); val since = System.currentTimeMillis() - Prefs.long("upd_last")
            if ((ev == "0" && first) || ev == "1" || (ev == "2" && since > 20 * 3600_000L) || (ev == "3" && since > 29 * 24 * 3600_000L))
                Updater.check(ctx)?.let { u ->
                    if (Updater.mayAsk(u)) {
                        if (Updater.notify("bell")) Notes.add("update", trf("Nova verzija %s", u.name), "update:${u.name}")
                        if (Prefs.str("upd_dl", "0") == "0") Updater.predownload(ctx, u)
                        // "install by itself at the next start" / "window at start"
                        if (first && Prefs.str("upd_inst", "0") == "1" && Updater.ready(ctx, u)) Updater.install(ctx, u)
                        else if (first && Updater.notify("start")) UpdateAsk.show = true
                    }
                }
            first = false
            Store.actives(ctx).forEach { p -> try { NewContent.scan(ctx, p) } catch (_: Exception) {} }
            if (Prefs.bool("bg_episodes", true) && System.currentTimeMillis() - epAt >= General.episodesEvery * 60_000L - 60_000L) { epAt = System.currentTimeMillis(); try { Follow.check(ctx) } catch (_: Exception) {} }
            try { Sports.remindTeams() } catch (_: Exception) {}
            // playlist and EPG refresh by themselves: at night (around 4:00) or every 12 / 24 hours (Settings › Playlists)
            try { if (AutoRefresh.due()) AutoRefresh.run(ctx) { nav.playlistsChanged() } } catch (_: Exception) {}
            delay(3600_000L)
        }
    }
    // Sports Center guide and search list are prepared quietly in the background, so they open at once
    LaunchedEffect(Unit) {
        delay(8_000)
        if (!Perf.lite) try { Sports.guide(ctx, Store.actives(ctx)) } catch (_: Exception) {}
        try { SearchIndex.get(ctx) } catch (_: Exception) {}
    }
    // reminders: checked every 15 seconds, wherever you are in the app
    LaunchedEffect(Unit) {
        while (true) {
            if (Reminders.showing == null) Reminders.due().firstOrNull()?.let { r ->
                Reminders.markFired(r.id)
                Notes.add("reminder", r.title + (r.channelName?.let { " · $it" } ?: ""), "rem:${r.id}")
                if (Reminders.action == 1 && r.channelUrl != null) playReminder(nav, r) else Reminders.showing = r
            }
            delay(15_000)
        }
    }
    LaunchedEffect(Reminders.rev) { ReminderReceiver.schedule(ctx) }
    // a playlist switched on or off: the same films / episodes are looked for in the switched-on ones
    LaunchedEffect(version) { delay(3_000); try { Watch.findAlternatives(ctx) } catch (_: Exception) {} }
    // OK on the menus' screen saver: the latest "Continue watching" title plays right away
    LaunchedEffect(Saver.resume) {
        if (Saver.resume == 0) return@LaunchedEffect
        val pr = Watch.continueList(ctx).firstOrNull()?.takeIf { Watch.blocked(ctx, it) == null }?.let { Watch.playable(ctx, it) } ?: return@LaunchedEffect
        nav.push(Screen.Player(listOf(PlayItem(pr.title, pr.url, pr.poster, null, null, pr.series, pr.season, pr.episode)), 0, live = false))
    }
    // the install confirm screen is opened from here, by the visible app (not from the background)
    LaunchedEffect(Updater.confirm) {
        val c = Updater.confirm ?: return@LaunchedEffect
        Updater.confirm = null
        try { Updater.confirmAt = System.currentTimeMillis(); (ctx as? android.app.Activity)?.startActivity(c) ?: ctx.startActivity(c.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)); UpdLog.add(tr("Ekran za potvrdu otvoren")) }
        catch (e: Exception) { Updater.systemFallback(ctx, trf("Ekran za potvrdu se nije otvorio: %s", e.message ?: "")) }
    }
    // time the app is open, and the counters written down once a minute
    LaunchedEffect(Unit) { while (true) { delay(60_000); Stats.uptime(60_000); Stats.saveIfDirty() } }
    // internet speed for the automatic buffer: first start, a new network, or once a week (a 3-second test, nothing visible)
    LaunchedEffect(Unit) { delay(20_000); try { NetProfile.refresh(ctx) } catch (_: Exception) {} }

    val scheme = if (C.IsLight) lightColorScheme(primary = C.Red, background = C.Bg, surface = C.Bg)
                 else darkColorScheme(primary = C.Red, background = C.Bg, surface = C.Bg)
    val dens = androidx.compose.ui.platform.LocalDensity.current
    CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(dens.density * (1f + Look.uiScale * 0.0035f), dens.fontScale)) {
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalLayoutDirection provides if (Look.lang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr) {
            val top = stack.last()
            CrashLog.screen = top.toString().take(80)
            if (Profiles.picker) {
                ProfilePicker()
            } else if (top is Screen.Player) {
                PlayerScreen(top, nav)
            } else if (Lock.simple && Look.age) {
                Box(Modifier.fillMaxSize().background(C.BgBrush)) {
                    DuskBackground()
                    key(version, top) {
                        // simple (parental) mode: three big tiles; TV, Movies and Series open without any top menu
                        if (top == Screen.Home || top == Screen.Settings) OnyxSimpleHome(nav)
                        else Column(Modifier.fillMaxSize().padding(top = 14.dp)) {
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                when (top) {
                                    Screen.Tv -> TvScreen(nav)
                                    Screen.Movies -> MoviesScreen(nav)
                                    Screen.Series -> SeriesScreen(nav)
                                    is Screen.MovieDetail -> MovieDetailScreen(top.vod, nav)
                                    is Screen.SeriesDetail -> SeriesDetailScreen(top.item, nav)
                                    else -> {}
                                }
                            }
                        }
                    }
                }
                AdvancedGate(nav)
            } else if (Lock.simple) {
                Box(Modifier.fillMaxSize().background(C.BgBrush)) {
                    key(version, top) {
                        when (top) {
                            Screen.Tv -> TvScreen(nav)
                            Screen.Movies -> MoviesScreen(nav)
                            Screen.Series -> SeriesScreen(nav)
                            Screen.Sports -> SportMixScreen(nav)
                            is Screen.MovieDetail -> MovieDetailScreen(top.vod, nav)
                            is Screen.SeriesDetail -> SeriesDetailScreen(top.item, nav)
                            else -> SimpleHome(nav)
                        }
                    }
                }
            } else {
                val content: @Composable () -> Unit = {
                        // Settings are not rebuilt when something is saved there: you stay on the page and row you were on
                        key(if (top == Screen.Settings) 0 else version, top) {
                            when (top) {
                                Screen.Home -> HomeScreen(nav)
                                Screen.Tv -> TvScreen(nav)
                                Screen.Movies -> MoviesScreen(nav)
                                Screen.Series -> SeriesScreen(nav)
                                Screen.Settings -> SettingsScreen(nav)
                                Screen.AddPlaylist -> AddPlaylistScreen(nav)
                                is Screen.MovieDetail -> MovieDetailScreen(top.vod, nav)
                                is Screen.SeriesDetail -> SeriesDetailScreen(top.item, nav)
                                Screen.Sports -> SportMixScreen(nav)
                                Screen.Search -> SearchScreen(nav)
                                Screen.Catchup -> CatchupScreen(nav)
                                is Screen.Player -> {}
                            }
                        }
                }
                // holding ▲ anywhere (outside the player) goes straight to the top bar, on the screen you are on
                if (Look.age) Box(Modifier.fillMaxSize().background(C.BgBrush).onPreviewKeyEvent { ev ->
                    if (ev.key == androidx.compose.ui.input.key.Key.DirectionUp && ev.type == androidx.compose.ui.input.key.KeyEventType.KeyDown &&
                        ev.nativeKeyEvent.repeatCount >= 6) { try { TopNav.fr.requestFocus() } catch (_: Exception) {}; true } else false
                }) {
                    DuskBackground()
                    // every playlist switched off: Home, TV, Movies and Series say so, with a button to switch one on
                    val noActive = remember(version, top) { Store.playlists(ctx).isNotEmpty() && Store.actives(ctx).isEmpty() } &&
                        (top == Screen.Home || top == Screen.Tv || top == Screen.Movies || top == Screen.Series)
                    if (top == Screen.Home && !noActive) key(version) { OnyxHome(nav) }
                    else if (top == Screen.AddPlaylist && Store.playlists(ctx).isEmpty() && Welcome.tab < 0) content()
                    else Column(Modifier.fillMaxSize()) {
                        OnyxTopBar(top, nav)
                        Box(Modifier.weight(1f).fillMaxWidth()) { if (noActive) NoPlaylist(nav) else content() }
                    }
                } else Row(Modifier.fillMaxSize().background(C.BgBrush)) {
                    Rail(top, nav)
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
                        if (Look.wave && !Chrome.driven) HintBar()
                    }
                }
            }
            ReminderPopup(nav)
            if (Profiles.panel) ProfilePanel(nav)
            CrashDialog()
            WhatsNewDialog()
            UpdateAskDialog(nav)
            CrashGuardDialog(nav)
            GoalNoteOverlay()
            MenuSaver(inPlayer = top is Screen.Player)
        }
    }
    }
}

fun playReminder(nav: Nav, r: Reminder) {
    val url = r.channelUrl ?: return
    Preview.stop()
    val ch = Channel("", 0, r.channelName ?: r.title, r.logo, "", url, null, r.pid ?: "")
    nav.push(Screen.Player(listOf(PlayItem(ch.name, url, r.logo, r.title, ch)), 0, live = true))
}

/** "Starts in 5 min: …" over any screen, with Switch and Close. */
@Composable
private fun ReminderPopup(nav: Nav) {
    val r = Reminders.showing ?: return
    LaunchedEffect(r.id) {
        if (Prefs.bool("rem_sound", true)) try { android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 70).startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 400) } catch (_: Exception) {}
    }
    LaunchedEffect(r.id) { delay(Reminders.popupSec * 1000L); if (Reminders.showing?.id == r.id) Reminders.showing = null }
    val mins = ((r.start - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)
    Popup(if (mins > 0) trf("Za %s min počinje", mins) else tr("Počinje sada"), onDismiss = { Reminders.showing = null }) {
        T(r.title, 20.sp, C.Text, FontWeight.Bold, maxLines = 2)
        r.channelName?.let { T(it + " · " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(r.start)), 14.sp, C.Soft) }
        if (r.channelUrl != null) OptionRow("▶  " + tr("Prebaci na kanal"), null, color = C.Green) { Reminders.showing = null; playReminder(nav, r) }  // i18n-ignore
        // snooze: the same notice again in 5 minutes
        OptionRow("⏰  " + tr("Za 5 min"), null) {  // i18n-ignore
            Reminders.showing = null
            @Suppress("OPT_IN_USAGE")
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) { delay(5 * 60_000L); if (Reminders.showing == null) Reminders.showing = r }
        }
        OptionRow("Zatvori", R.drawable.lc_x) { Reminders.showing = null }
    }
}

private data class RailItem(val screen: Screen, val icon: Int, val label: String, val badge: Int = 0)

@Composable
fun Rail(current: Screen, nav: Nav) {
    val mode = Chrome.rail
    if (mode == 0) return
    val items = listOf(
        RailItem(Screen.Home, R.drawable.lc_house, "Početna", Notes.unread("update").coerceAtMost(0)),
        RailItem(Screen.Tv, R.drawable.lc_tv, "TV", Notes.unread("reminder")),
        RailItem(Screen.Movies, R.drawable.lc_film, "Filmovi", NewContent.count("vod")),
        RailItem(Screen.Series, R.drawable.lc_monitor, "Serije", NewContent.count("series")),
        RailItem(Screen.Sports, R.drawable.lc_trophy, "Sport"),
        RailItem(Screen.Search, R.drawable.lc_search, "Pretraga"),
    )
    val section = when (current) {
        is Screen.MovieDetail -> Screen.Movies
        is Screen.SeriesDetail -> Screen.Series
        Screen.AddPlaylist -> Screen.Settings
        else -> current
    }
    // one look everywhere: icon with the name under it
    val width = 96.dp
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    // a section opens only on OK; moving through the menu changes nothing (the last one you clicked stays open)
    Column(
        Modifier.width(width).fillMaxHeight().background(C.Rail).padding(vertical = 14.dp)
            .onFocusChanged { Chrome.inRail = it.hasFocus },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ProfileButton(if (mode == 1) 36.dp else 42.dp)
        Spacer(Modifier.height(6.dp))
        fun go(s: Screen) {
            Chrome.inRail = false
            if (section == s) focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Right) else nav.root(s)
        }
        items.forEach { RailButton(it.icon, it.label, section == it.screen, 3, it.badge) { go(it.screen) } }
        Spacer(Modifier.weight(1f))
        RailButton(R.drawable.lc_settings, "Postavke", section == Screen.Settings, 3, if (Updater.available != null) 1 else 0) { go(Screen.Settings) }
    }
}

@Composable
private fun RailButton(icon: Int, label: String, selected: Boolean, mode: Int, badge: Int, onFocus: () -> Unit = {}, onClick: () -> Unit) {
    Box {
        Focusable(
            modifier = if (mode == 2) Modifier.width(186.dp).height(46.dp) else Modifier.width(if (mode == 1) 56.dp else 76.dp).height(if (mode == 1) 46.dp else 52.dp),
            onClick = onClick,
            onFocus = { if (Chrome.driven) Chrome.rail = 3; onFocus() },
            shape = Rnd(16.dp),
            normal = if (selected) C.Red else Color.Transparent,
            focused = if (selected) C.Red else C.CardHi,
            zoom = 1.06f,
        ) { f ->
            val c = if (selected) C.OnRed else if (f) C.Text else C.Muted
            if (mode == 2) Row(Modifier.align(Alignment.CenterStart).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Ico(icon, 22.dp, c); T(label, 15.sp, c, if (selected) FontWeight.SemiBold else FontWeight.Medium)
            } else Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Ico(icon, 20.dp, c)
                if (mode == 3) T(label, 10.sp, c, if (selected) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
        if (badge > 0) Box(Modifier.align(Alignment.TopEnd).offset(x = (-4).dp, y = 2.dp)) { Bell(badge) }
    }
}

@Composable
fun Clock() {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(15_000) } }
    Column(horizontalAlignment = Alignment.End) {
        T(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), 22.sp, C.Text, FontWeight.SemiBold)
        T(SimpleDateFormat("EEE, d. MMM", Locale(Look.lang)).format(now), 12.sp, C.Muted)
    }
}

@Composable
fun Header(title: String, subtitle: String? = null, extra: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 36.dp, end = 36.dp, top = 26.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            T(title, 28.sp, C.Text, FontWeight.Bold)
            if (subtitle != null) T(subtitle, 14.sp, C.Muted, modifier = Modifier.padding(bottom = 4.dp))
        }
        extra?.invoke()
        Clock()
    }
}

fun expiryText(exp: String): Pair<String, Int>? {
    if (exp.isBlank()) return null
    if (exp == "0") return tr("Bez isteka") to 0
    val sec = exp.toLongOrNull() ?: return null
    val days = (sec * 1000 - System.currentTimeMillis()) / 86_400_000L
    val date = SimpleDateFormat("d. M. yyyy", Locale.getDefault()).format(Date(sec * 1000))
    return when {
        days < 0 -> trf("Istekla %s", date) to 2
        days < 7 -> trf("Ističe %s (još %s d)", date, days) to 1
        else -> trf("Ističe %s", date) to 0
    }
}

@Composable
fun UpdateBanner(nav: Nav) {
    val u = Updater.available ?: return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxWidth().padding(horizontal = 36.dp).clip(Rnd(16.dp)).background(C.CardFocus)
        .border(1.dp, C.Red, Rnd(16.dp)).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Ico(R.drawable.lc_download, 22.dp, C.RedText)
        T(trf("Stigla je nova verzija %s", u.name), 15.sp, C.Text, FontWeight.SemiBold, modifier = Modifier.weight(1f))
        val pr = Updater.progress
        if (pr in 0..100) T(trf("Preuzimam %s %%", pr), 14.sp, C.Soft)
        else if (pr == 101) T("Instaliram…", 14.sp, C.Soft)
        else Button("Ažuriraj", R.drawable.lc_download, primary = true) { scope.launch { Updater.install(ctx, u) } }
    }
}

/** Where OK on a notification leads: the new episode itself, the new films or series, the match, the update. */
fun openNote(nav: Nav, n: Note) {
    Notes.markRead(n.id)
    when {
        n.kind == "update" -> { SettingsNav.section = "update"; nav.root(Screen.Settings) }
        n.kind == "sport" -> GoalNote.open(nav, n.id)
        n.kind == "series" && n.id.startsWith("ep:") -> Follow.open(nav, n.id.split(':').getOrElse(1) { "" })
        n.kind == "vod" -> { VodMemory.group["vod"] = "__new__"; nav.root(Screen.Movies) }
        n.kind == "series" -> { VodMemory.group["series"] = "__new__"; nav.root(Screen.Series) }
        else -> nav.root(Screen.Tv)
    }
}

/** Notifications: reminders, new version, new movies and series. */
@Composable
fun NotesPopup(onDismiss: () -> Unit, nav: Nav) {
    Popup("Obavještenja", onDismiss, width = 640.dp) {
        if (Notes.list.isEmpty()) T("Nema obavještenja.", 14.sp, C.Muted)
        Notes.list.take(30).forEach { n ->
            val icon = when (n.kind) { "update" -> R.drawable.lc_download; "vod" -> R.drawable.lc_film; "series" -> R.drawable.lc_monitor; else -> R.drawable.lc_bell }
            // a finished match is greyed out
            val done = n.kind == "sport" && GoalNote.ended(GoalNote.idOf(n.id))
            OptionRow(n.text, icon, sub = SimpleDateFormat("d. M. HH:mm", Locale.getDefault()).format(Date(n.time)), color = if (done) C.Muted else if (n.read) null else C.Amber) {
                onDismiss(); openNote(nav, n)
            }
        }
        if (Notes.list.isNotEmpty()) OptionRow("Obriši sva obavještenja", R.drawable.lc_trash_2) { Notes.clear(); onDismiss() }
    }
    LaunchedEffect(Unit) { delay(1500); Notes.readAll() }
}

@Composable
fun HomeScreen(nav: Nav) {
    val ctx = LocalContext.current
    val pls = remember { Store.actives(ctx) }
    val first = remember { FocusRequester() }
    var notes by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        HomeBackdrop()
        Column(Modifier.fillMaxSize().padding(top = 22.dp, bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                HomeLogo()
                Spacer(Modifier.weight(1f))
                HomeTopPill(nav) { notes = true }
            }
            UpdateBanner(nav)
            if (pls.isEmpty()) { Message("Nema playliste", "Dodaj playlistu u Postavkama"); return@Column }
            val cont = remember(Watch.rev) { Watch.continueList(ctx) }
            NeonRow(homeTiles(nav, push = false), first, Modifier.weight(1f))
            if (cont.isNotEmpty()) ContinueRow(cont, nav, Modifier.padding(start = 36.dp, bottom = 12.dp))
            HomeStatusBar(Modifier.padding(horizontal = 36.dp))
        }
    }
    if (notes) NotesPopup({ notes = false }, nav)
    LaunchedEffect(Unit) { first.tryFocus() }
}

/** "Continue watching": started movies and episodes, and the next episode of a series. */
@Composable
fun ContinueRow(list: List<Progress>, nav: Nav, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf<Progress?>(null) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Ico(R.drawable.lc_history, 18.dp, C.RedText); T("Nastavi gledati", 17.sp, C.Text, FontWeight.SemiBold)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 36.dp)) {
            items(list, key = { it.url }) { pr ->
                Focusable(modifier = Modifier.width(280.dp).height(86.dp), onClick = {
                    val why = Watch.blocked(ctx, pr)
                    if (why != null) android.widget.Toast.makeText(ctx, why, android.widget.Toast.LENGTH_LONG).show()
                    else Watch.playable(ctx, pr).let { q -> nav.push(Screen.Player(listOf(PlayItem(q.title, q.url, q.poster, null, null, q.series, q.season, q.episode)), 0, live = false)) }
                }, onMenu = { menu = pr }, normal = C.Card, shape = Rnd(14.dp), zoom = 1.04f) { _ ->
                    Row(Modifier.fillMaxSize().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.width(48.dp).fillMaxHeight().clip(Rnd(8.dp)).background(C.Tile)) {
                            if (pr.poster != null) AsyncImage(model = pr.poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            T(pr.title, 14.sp, C.Text, FontWeight.SemiBold, maxLines = 2)
                            if (pr.next) T(trf("Sljedeća: S%sE%s", pr.season ?: 1, pr.episode ?: 1), 12.sp, C.Green)
                            else {
                                T(trf("Stao si na %s od %s", fmtTime(pr.pos), fmtTime(pr.dur)), 11.sp, C.Muted)
                                Box(Modifier.fillMaxWidth().height(4.dp).clip(Rnd(2.dp)).background(C.CardHi)) {
                                    Box(Modifier.fillMaxWidth(pr.fraction).height(4.dp).background(C.Red))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    menu?.let { pr ->
        Popup(pr.title, onDismiss = { menu = null }) {
            OptionRow("Ukloni iz Nastavi gledati", R.drawable.lc_x) { Watch.remove(ctx, pr.url); menu = null }
            OptionRow("Označi kao gledano", R.drawable.lc_circle_check) { Watch.setWatched(ctx, pr.url, pr.title, pr.poster, true); menu = null }
        }
    }
}

fun fmtTime(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/** Big menu tile; size and style come from Settings › Appearance. Style 3 is the modern one. */
@Composable
fun HomeTile(label: String, icon: Int, fr: FocusRequester? = null, sizeOverride: Dp? = null, badge: Int = 0, onClick: () -> Unit) {
    val size = sizeOverride ?: when (Look.tileSize) { 0 -> 180.dp; 2 -> 280.dp; else -> 230.dp }
    val style = Look.tileStyle
    val shape = Rnd(size * 0.13f)
    if (style == 4) { FluentTile(label, icon, fr, size, badge, onClick); return }
    Box {
        Focusable(
            modifier = Modifier.size(size * 1.12f, size).then(if (style == 1 || style == 2) Modifier.border(1.5.dp, C.Line, shape) else Modifier),
            onClick = onClick,
            focusRequester = fr,
            shape = shape,
            normal = when (style) { 1 -> C.Card; 2 -> Color.Transparent; 3 -> C.Card; else -> C.Tile },
            focused = when (style) { 1, 2 -> C.CardFocus; 3 -> C.CardHi; else -> C.Red },
            zoom = 1.06f,
        ) { f ->
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(size * 0.08f)) {
                if (style == 3) {
                    // modern: a soft gradient square with a white icon, like One UI / Windows 11
                    Box(Modifier.size(size * 0.42f).clip(Rnd(size * 0.12f))
                        .background(Brush.linearGradient(listOf(lerp(C.Red, Color.White, 0.18f), lerp(C.Red, Color.Black, 0.28f)))),
                        contentAlignment = Alignment.Center) {
                        Ico(icon, size * 0.21f, Color.White)
                    }
                    T(label, (size.value * 0.105f).sp, if (f) C.Text else C.Soft, FontWeight.SemiBold)
                } else {
                    val onAccent = f && style == 0
                    Box(Modifier.size(size * 0.38f).clip(CircleShape).background(if (onAccent) C.OnRed.copy(alpha = 0.18f) else C.Red.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center) {
                        Ico(icon, size * 0.2f, if (onAccent) C.OnRed else C.RedText)
                    }
                    T(label, (size.value * 0.12f).sp, if (onAccent) C.OnRed else C.Text, FontWeight.Bold)
                }
            }
        }
        if (badge > 0) Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) { Bell(badge) }
    }
}

/** filled icon and colours for the main tiles (Fluent style) */
private fun fluentOf(icon: Int): Triple<Int, Color, Color> = when (icon) {
    R.drawable.lc_tv -> Triple(R.drawable.ic_fill_tv, Color(0xFF2563EB), Color(0xFF06B6D4))
    R.drawable.lc_film -> Triple(R.drawable.ic_fill_movie, Color(0xFFDB2777), Color(0xFFF97316))
    R.drawable.lc_monitor -> Triple(R.drawable.ic_fill_series, Color(0xFF7C3AED), Color(0xFF4F46E5))
    R.drawable.lc_trophy -> Triple(R.drawable.ic_fill_trophy, Color(0xFF059669), Color(0xFF84CC16))
    else -> Triple(icon, Color(0xFF475569), Color(0xFF1E293B))
}

/** Windows 11 / iOS like: colour gradient, big filled icon, name bottom left */
@Composable
private fun FluentTile(label: String, icon: Int, fr: FocusRequester?, size: Dp, badge: Int, onClick: () -> Unit) {
    val (ic, c1, c2) = fluentOf(icon)
    val shape = Rnd(size * 0.14f)
    Box {
        Focusable(modifier = Modifier.size(size * 1.12f, size), onClick = onClick, focusRequester = fr, shape = shape,
            normal = Color.Transparent, focused = Color.Transparent, zoom = 1.07f) { f ->
            Box(Modifier.fillMaxSize().clip(shape)
                .background(Brush.linearGradient(listOf(c1, c2)), alpha = if (f) 1f else 0.7f)
                .then(if (f) Modifier.border(3.dp, Color.White, shape) else Modifier.border(1.dp, Color.White.copy(alpha = 0.14f), shape))) {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent, Color.Black.copy(alpha = 0.30f)))))
                Box(Modifier.align(Alignment.TopEnd).padding(top = size * 0.09f, end = size * 0.09f)) {
                    androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(ic), null, Modifier.size(size * 0.48f), tint = Color.White)
                }
                T(label, (size.value * 0.13f).sp, Color.White, FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = size * 0.1f, bottom = size * 0.08f, end = 8.dp))
            }
        }
        if (badge > 0) Box(Modifier.align(Alignment.TopStart).padding(8.dp)) { Bell(badge) }
    }
}

/** big modern tile for the simple home: colour gradient, filled icon that fills the tile, label below left */
@Composable
private fun BigTile(label: String, sub: String, icon: Int, c1: Color, c2: Color, fr: FocusRequester?, onClick: () -> Unit) {
    val shape = Rnd(30.dp)
    Focusable(modifier = Modifier.size(300.dp, 250.dp), onClick = onClick, focusRequester = fr, shape = shape,
        normal = Color.Transparent, focused = Color.Transparent, zoom = 1.07f) { f ->
        Box(Modifier.fillMaxSize().clip(shape)
            .background(Brush.linearGradient(listOf(c1, c2)), alpha = if (f) 1f else 0.72f)
            .then(if (f) Modifier.border(3.dp, Color.White, shape) else Modifier)) {
            // soft light from the top
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent, Color.Black.copy(alpha = 0.35f)))))
            Box(Modifier.align(Alignment.TopEnd).padding(top = 22.dp, end = 22.dp)) {
                androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(icon), null, Modifier.size(128.dp), tint = Color.White)
            }
            Column(Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 20.dp, end = 16.dp)) {
                T(label, 32.sp, Color.White, FontWeight.Bold)
                T(sub, 14.sp, Color.White.copy(alpha = 0.85f), FontWeight.Medium)
            }
        }
    }
}

/** Simple mode: only TV, Movies, Series, and a small "Advanced mode" button behind the PIN. */
@Composable
fun SimpleHome(nav: Nav) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    var warn by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
    HomeBackdrop()
    Column(Modifier.fillMaxSize().padding(top = 26.dp, bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            HomeLogo()
            Spacer(Modifier.weight(1f))
            ProfileButton(46.dp)
        }
        NeonRow(homeTiles(nav, push = true), first, Modifier.weight(1f), maxTile = 280.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
            Button("Napredni mod", R.drawable.lc_lock) { warn = true }
            Updater.available?.let { u ->
                val pr = Updater.progress
                val label = when { pr in 0..100 -> trf("Preuzimam… %s%%", pr); pr == 101 -> tr("Instaliram…"); else -> trf("Ažuriraj na %s", u.name) }
                Button(label, R.drawable.lc_download, primary = true) { if (pr < 0) scope.launch { Updater.install(ctx, u) } }
            }
        }
        Updater.status?.let { T(it, 13.sp, C.Danger, maxLines = 2, modifier = Modifier.padding(bottom = 8.dp)) }
        HomeStatusBar(Modifier.padding(horizontal = 40.dp))
    }
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
    LaunchedEffect(Unit) { first.tryFocus() }
}

/** Series you follow: a note when a new episode appears. */
/** "Window at start": the new version asks once when PRETORIAN opens. */
object UpdateAsk { var show by mutableStateOf(false) }

object Follow {
    var rev by mutableIntStateOf(0)
    private fun all(): org.json.JSONObject = try { org.json.JSONObject(Prefs.str("follow", "{}")) } catch (e: Exception) { org.json.JSONObject() }
    fun isOn(id: String) = all().has(id)
    fun toggle(pid: String, s: SeriesItem, count: Int): Boolean {
        val o = all()
        val on = if (o.has(s.id)) { o.remove(s.id); false } else { o.put(s.id, org.json.JSONObject().put("pid", pid).put("name", s.name).put("cover", s.cover ?: "").put("n", count)); true }
        Prefs.put("follow", o.toString()); rev++; return on
    }
    /** The series page was opened: its "new episode" card on Home goes away. */
    fun seen(id: String) {
        val o = all(); val e = o.optJSONObject(id) ?: return
        if (e.optBoolean("fresh")) { e.put("fresh", false); Prefs.put("follow", o.toString()); rev++ }
    }
    /** a new-episode note: the series page at that season and episode; Back goes to the series list, then Home */
    fun open(nav: Nav, id: String) {
        val e = all().optJSONObject(id) ?: run { nav.root(Screen.Series); return }
        SeriesJump.id = id; SeriesJump.season = e.optInt("s", -1); SeriesJump.num = e.optInt("e", -1)
        nav.root(Screen.Series); nav.push(Screen.SeriesDetail(SeriesItem(id, e.str("name"), e.str("cover").orNullIfBlank(), null, null)))
    }
    data class Fresh(val id: String, val pid: String, val name: String, val cover: String?, val season: Int, val num: Int)
    /** Series with a new episode not opened yet, newest first. */
    fun fresh(): List<Fresh> {
        val o = all()
        return o.keys().asSequence().mapNotNull { id ->
            val e = o.getJSONObject(id)
            if (!e.optBoolean("fresh")) null else Fresh(id, e.str("pid"), e.str("name"), e.str("cover").orNullIfBlank(), e.optInt("s"), e.optInt("e")) to e.optLong("at")
        }.sortedByDescending { it.second }.map { it.first }.toList()
    }
    /** Every hour: did the provider add an episode to a series you follow? */
    suspend fun check(ctx: android.content.Context) {
        if (System.currentTimeMillis() - Prefs.long("follow_at") < 3600_000L) return
        Prefs.put("follow_at", System.currentTimeMillis())
        val o = all()
        o.keys().asSequence().toList().forEach { id ->
            val e = o.getJSONObject(id)
            val p = Store.byId(ctx, e.str("pid")) ?: return@forEach
            val list = try { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Xtream(p).episodes(id) } } catch (x: Exception) { return@forEach }
            val n = list.size
            if (n > e.optInt("n")) {
                val last = list.maxWithOrNull(compareBy({ it.season }, { it.num }))
                Notes.add("series", trf("Nova epizoda: %s", cleanTitle(e.str("name")).first) + (last?.let { " · S${it.season} E${it.num}" } ?: ""), "ep:$id:$n")
                e.put("n", n).put("fresh", true).put("at", System.currentTimeMillis())
                if (last != null) e.put("s", last.season).put("e", last.num)
            }
        }
        Prefs.put("follow", o.toString()); rev++
    }
}

/** New Age: the menu as a bar on top. */
@Composable
private fun TopBar(current: Screen, nav: Nav) {
    val items = listOf(
        RailItem(Screen.Home, R.drawable.lc_house, "Početna"),
        RailItem(Screen.Tv, R.drawable.lc_tv, "TV", Notes.unread("reminder")),
        RailItem(Screen.Movies, R.drawable.lc_film, "Filmovi", NewContent.count("vod")),
        RailItem(Screen.Series, R.drawable.lc_monitor, "Serije", NewContent.count("series")),
        RailItem(Screen.Sports, R.drawable.lc_trophy, "Sport"),
        RailItem(Screen.Search, R.drawable.lc_search, "Pretraga"),
        RailItem(Screen.Settings, R.drawable.lc_settings, "Postavke", if (Updater.available != null) 1 else 0),
    )
    val section = when (current) { is Screen.MovieDetail -> Screen.Movies; is Screen.SeriesDetail -> Screen.Series; Screen.AddPlaylist -> Screen.Settings; else -> current }
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp).clip(Rnd(22.dp)).background(C.Rail).border(1.dp, C.Line, Rnd(22.dp))
        .padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ProfileButton(32.dp)
        T("PRETORIAN", 15.sp, C.Text, FontWeight.Bold, modifier = Modifier.padding(start = 6.dp, end = 10.dp))
        items.forEach { it ->
            val sel = section == it.screen
            Box {
                Focusable(modifier = Modifier.height(40.dp), onClick = { nav.root(it.screen) }, shape = Rnd(14.dp),
                    normal = if (sel) C.Red.copy(alpha = 0.22f) else androidx.compose.ui.graphics.Color.Transparent, focused = C.CardHi, zoom = 1.04f) { f ->
                    Row(Modifier.align(Alignment.Center).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Ico(it.icon, 17.dp, if (sel) C.RedText else if (f) C.Text else C.Soft)
                        T(it.label, 13.sp, if (sel || f) C.Text else C.Soft, if (sel) FontWeight.SemiBold else FontWeight.Medium)
                    }
                }
                if (it.badge > 0) Box(Modifier.align(Alignment.TopEnd)) { Bell(it.badge) }
            }
        }
        Spacer(Modifier.weight(1f))
        Clock()
    }
}

/** Remote help at the bottom (New Wave / New Age). */
@Composable
fun HintBar() {
    Row(Modifier.fillMaxWidth().background(C.Rail).padding(horizontal = 24.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        listOf("◀ ▲ ▼ ▶" to "Kretanje", "OK" to "Izaberi", tr("Drži OK") to "Opcije", tr("Nazad") to "Natrag").forEach { (k, v) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.clip(Rnd(6.dp)).background(C.CardHi).padding(horizontal = 7.dp, vertical = 2.dp)) { T(k, 11.sp, C.Text, FontWeight.Bold) }
                T(v, 11.sp, C.Muted)
            }
        }
    }
}
