package com.pretorian.tv

import androidx.compose.ui.input.key.key
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
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
        Prefs.init(this)
        CrashLog.install(this)
        ChannelHealth.init(this)
        Work.watchNetwork(this)
        Work.safeMode = CrashGuard.quick() || CrashGuard.tooMany()
        if (Work.safeMode) {
            // safe mode: heavy background work and channel checks wait; nothing is deleted or reset.
            // After 3 minutes without a crash the app returns to normal by itself.
            DiagEvents.add("app", "safe mode on after repeated crashes")  // i18n-ignore
            window.decorView.postDelayed({ Work.safeMode = false; CrashGuard.clearQuick(); DiagEvents.add("app", "safe mode ended, 3 min without a crash") }, 180_000)  // i18n-ignore
        }
        NewContent.appCtx = applicationContext
        Repo.diskDir = cacheDir
        CatalogDb.init(applicationContext)
        CatalogDb.backfill()
        SportsAI.dir = cacheDir
        AutoTranslate.dir = filesDir
        StandardView.onFirstStart(this)   // a new install starts with the built-in standard view
        Look.load(this)
        onyxMigrate(this)
        Perf.init(this)
        // after Perf (Lite / Full decides how far ahead the guide goes)
        Import.missing(this)
        EpgShift.migrate(this)
        RefreshCenter.resume(this)
        Upkeep.start(this); Monitor.start()
        // picture cache: limited so the disk never fills up (150 MB on weaker boxes, 400 MB otherwise)
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(this)
            .memoryCache { coil.memory.MemoryCache.Builder(this).maxSizePercent(if (Perf.lite || Lean.images) 0.10 else 0.20).build() }
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

    // TV browser: Back / ◀ in the options row under the preview go to the channel list before Compose sees them
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        Saver.touch()
        if (TvFocus.onKey(event)) return true
        return super.dispatchKeyEvent(event)
    }

    // Android asks for memory back: drop lists that are not on the screen, they load again when needed
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) Work.lowMemoryUntil = android.os.SystemClock.elapsedRealtime() + 5 * 60_000L
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) DiagEvents.add("memwarn", "memory pressure level $level on ${CrashLog.screen}")  // i18n-ignore
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN || level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) Repo.trim(level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) try { coil.Coil.imageLoader(this).memoryCache?.clear() } catch (_: Exception) {}
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
        // every start of the app opens Home (not after the external player or a short trip to the system)
        if (stoppedAt > 0 && away > 15_000 && !fromExternal) AppHome.tick++
    }

    override fun onStart() {
        super.onStart()
        Work.background = false
        LookOpts.readSystem(this)
    }

    override fun onStop() {
        super.onStop()
        Work.background = true
        Lock.relock()
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

object BuildInfo { val VERSION: String = BuildConfig.VERSION_NAME }

@Composable
fun PretorianApp() {
    val ctx = LocalContext.current
    // Diagnostics › Long run: how long the start took, up to the first screen
    LaunchedEffect(Unit) { if (Monitor.startMs == 0L) Monitor.startMs = android.os.SystemClock.uptimeMillis() - android.os.Process.getStartUptimeMillis() }
    val stack = remember {
        mutableStateListOf<Screen>(if (!Onboard.done(ctx)) Screen.AddPlaylist else Screen.Home)
    }
    var version by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { Chrome.rail = 3 }
    val nav = remember {
        Nav(
            // Settings keeps its own page stack per copy on the app stack (Phase A); a newly opened copy starts fresh
            push = { if (it == Screen.Settings) SettingsStack.opened(stack.count { s -> s == Screen.Settings } + 1, stack.lastOrNull() == Screen.Search); stack.add(it) },
            back = {
                if (stack.size > 1) {
                    val left = stack.removeAt(stack.lastIndex)
                    // a new version waits: once per version, right after the player, Settings › Update opens by itself
                    val u = Updater.available
                    if (left is Screen.Player && u != null && Updater.mayAsk(u) && Updater.notify("player") && Prefs.str("upd_asked") != u.name) {
                        Prefs.put("upd_asked", u.name); SettingsNav.section = "update"
                        SettingsStack.opened(stack.count { s -> s == Screen.Settings } + 1, false); stack.add(Screen.Settings)
                    }
                }
            },
            root = { s -> if (s != Screen.Search) SearchMem.clear() else SearchOnly.only = null; if (s == Screen.Settings) SettingsStack.opened(1, false); stack.clear(); stack.add(Screen.Home); if (s != Screen.Home) stack.add(s) },
            playlistsChanged = { Repo.clear(); version++ },
        )
    }
    BackHandler(enabled = stack.size > 1) { nav.back() }
    // Settings › General › Confirm exit: Back on Home asks first
    var askExit by remember { mutableStateOf(false) }
    BackHandler(enabled = stack.size <= 1 && General.confirmExit) { askExit = true }
    if (askExit) Popup(tr("Izaći iz aplikacije?"), { askExit = false }) {
        OptionRow("Izađi", R.drawable.lc_x, color = C.Danger) { askExit = false; (ctx as? android.app.Activity)?.finish() }
        OptionRow("Ostani", R.drawable.lc_check) { askExit = false }
    }
    // goals of your teams (Sport › long OK › My teams): checked every 3 minutes wherever you are, only when you have teams
    LaunchedEffect(Unit) {
        delay(4_000)
        var full = 0L
        while (true) {
            if (Prefs.bool("bg_sport", true) && (MyTeams.list().isNotEmpty() || MatchFollow.list().isNotEmpty())) try {
                val live = GoalNote.followed.filter { it.state == "in" }
                // a followed match is live: only its league, often; every league only at the usual pace
                if (GoalNote.hot && live.isNotEmpty() && System.currentTimeMillis() - full < General.sportEvery * 60_000L) GoalNote.check(Sports.leaguesOf(live))
                else { GoalNote.check(Sports.day(0)); full = System.currentTimeMillis() }
            } catch (_: Exception) {}
            delay(if (GoalNote.hot) General.sportLive * 1000L else General.sportEvery * 60_000L)
        }
    }
    // Settings › General › Last channel at start
    LaunchedEffect(Unit) {
        if (General.start != 0 && !General.startedOnce) {
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
        if (!Lean.search) try { SearchIndex.get(ctx) } catch (_: Exception) {}
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
    LaunchedEffect(Unit) { delay(20_000); try { Work.heavy("speed profile") { NetProfile.refresh(ctx) } } catch (_: Exception) {} }

    val scheme = if (C.IsLight) lightColorScheme(primary = C.Red, background = C.Bg, surface = C.Bg)
                 else darkColorScheme(primary = C.Red, background = C.Bg, surface = C.Bg)
    val dens = androidx.compose.ui.platform.LocalDensity.current
    CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(dens.density * (1f + Look.uiScale * 0.0035f), dens.fontScale)) {
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalLayoutDirection provides if (Look.lang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr) {
            val top = stack.last()
            CrashLog.screen = (top::class.simpleName ?: top.toString()).take(80)   // i18n-ignore (2.9.1: short class name instead of Screen$Settings@hash)
            if (top is Screen.Player) {
                PlayerScreen(top, nav)
            } else {
                val content: @Composable () -> Unit = {
                        // Settings are not rebuilt when something is saved there: you stay on the page and row you were on
                        key(if (top == Screen.Settings) 0 else version, top) {
                            when (top) {
                                Screen.Home -> {}
                                Screen.Tv -> TvScreen(nav)
                                Screen.Movies -> MoviesScreen(nav)
                                Screen.Series -> SeriesScreen(nav)
                                Screen.Settings -> { SettingsStack.depth = stack.count { s -> s == Screen.Settings }; SettingsScreen(nav) }
                                Screen.AddPlaylist -> AddPlaylistScreen(nav)
                                is Screen.MovieDetail -> MovieDetailScreen(top.vod, nav)
                                is Screen.SeriesDetail -> SeriesDetailScreen(top.item, nav)
                                Screen.Sports -> SportDashboard(nav)
                                Screen.Search -> SearchScreen(nav)
                                Screen.Catchup -> CatchupScreen(nav)
                                is Screen.Player -> {}
                            }
                        }
                }
                // New Dawn: one level at a time, the menu only on Home
                DawnShell(top, nav) {
                    val noActive = remember(version, top) { Store.playlists(ctx).isNotEmpty() && Store.actives(ctx).isEmpty() } &&
                        (top == Screen.Home || top == Screen.Tv || top == Screen.Movies || top == Screen.Series)
                    if (noActive) NoPlaylist(nav) else content()
                }
            }
            ReminderPopup(nav)
            CrashDialog()
            SaverHost(nav)
            WhatsNewDialog()
            ImportDialog()
            UpdateAskDialog(nav)
            CrashGuardDialog(nav)
            MatchFollowOverlay()
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
    /** Home › long OK › Clear notifications: no series is marked as having a new episode any more */
    fun clearFresh() {
        val o = all(); var changed = false
        o.keys().forEach { id -> o.optJSONObject(id)?.let { e -> if (e.optBoolean("fresh")) { e.put("fresh", false); changed = true } } }
        if (changed) { Prefs.put("follow", o.toString()); rev++ }
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
