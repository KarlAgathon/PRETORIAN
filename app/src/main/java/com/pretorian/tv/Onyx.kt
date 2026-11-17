package com.pretorian.tv

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** "My list": movies and series you keep to watch later (long press OK on a poster). */
object MyList {
    var rev by mutableIntStateOf(0)
    private fun all(): JSONArray = try { JSONArray(Prefs.str("mylist", "[]")) } catch (e: Exception) { JSONArray() }
    fun items(kind: String): List<JSONObject> {
        val a = all(); return (0 until a.length()).map { a.getJSONObject(it) }.filter { it.optString("k") == kind }.reversed()
    }
    fun has(kind: String, id: String): Boolean = items(kind).any { it.optString("id") == id }
    fun toggle(kind: String, id: String, name: String, image: String?, url: String?, pid: String): Boolean {
        val a = all(); val out = JSONArray(); var removed = false
        for (i in 0 until a.length()) { val o = a.getJSONObject(i); if (o.optString("k") == kind && o.optString("id") == id) removed = true else out.put(o) }
        if (!removed) out.put(JSONObject().put("k", kind).put("id", id).put("n", name).put("i", image ?: "").put("u", url ?: "").put("p", pid))
        Prefs.put("mylist", out.toString()); rev++
        return !removed
    }
}

/** Program mode: Lite (no big pictures, smaller images, no fades) for weak boxes, Full for strong ones. */
object Perf {
    var mode by mutableIntStateOf(0)          // 0 automatic, 1 Lite, 2 Full
    var ramGb = 4.0; private set
    var weak = false; private set
    val lite get() = if (mode == 0) weak else mode == 1
    /** device score 0–100: below 70 (or under 3 GB RAM) PRETORIAN starts in Lite by itself */
    var score = 100; private set
    /** where the points come from, for About › Box score */
    var parts: List<Pair<String, String>> = emptyList(); private set
    fun init(c: Context) {
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo(); am.getMemoryInfo(mi)
        ramGb = mi.totalMem / 1_073_741_824.0
        val cores = Runtime.getRuntime().availableProcessors()
        val maxGhz = (0 until cores).maxOfOrNull { i ->
            try { java.io.File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq").readText().trim().toLong() / 1_000_000.0 } catch (e: Exception) { 0.0 }
        } ?: 0.0
        val sdk = android.os.Build.VERSION.SDK_INT
        // RAM: 30 points
        val ram = when { ramGb >= 3.5 -> 30; ramGb >= 2.8 -> 18; ramGb >= 1.8 -> 8; else -> 0 }
        // processor: 25 points (cores, clock, 64-bit)
        var cpu = when { cores >= 8 -> 10; cores >= 4 -> 7; else -> 2 }
        cpu += when { maxGhz >= 2.0 -> 12; maxGhz >= 1.8 -> 9; maxGhz >= 1.6 -> 5; maxGhz > 0.1 -> 1; else -> 6 }
        if (android.os.Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) cpu += 3
        // Android version: 15 points (Android 9 and older = an older box, usually from before 2022)
        val os = when { sdk >= 33 -> 15; sdk >= 31 -> 12; sdk >= 30 -> 8; sdk >= 29 -> 5; else -> 0 }
        // performance class (Android 12+, set by the maker) and chip name: 15 points
        val pc = if (sdk >= 31) android.os.Build.VERSION.MEDIA_PERFORMANCE_CLASS else 0
        val socOld = (if (sdk >= 31) android.os.Build.SOC_MODEL else android.os.Build.HARDWARE).lowercase().let { h ->
            listOf("amlogic", "s905x", "s905w", "s905l", "s905y2", "mt8695", "mt8167", "rk3229", "rk3328", "h616", "h313").any { h.contains(it) } }
        val gpu = when { pc >= 31 -> 15; socOld -> 3; sdk >= 31 -> 12; else -> 7 }
        // short speed test: 15 points
        // measured once (best of 5 runs, so a busy moment doesn't lower it) and kept: the score stays the same on every start
        var ms = Prefs.long("perf_speed_ms", -1)
        // the speed test only runs when the mode is "Automatic" (or on "Measure again"): a new install starts in Lite
        // without spending its first seconds on a benchmark
        if (ms < 0 && (Prefs.long("perf_mode", -1) == 0L || forceMeasure)) {
            ms = Long.MAX_VALUE
            repeat(5) { val t0 = System.nanoTime(); var x = 0L; for (i in 0 until 3_000_000) x += (i xor (i shr 3)).toLong()
                ms = minOf(ms, (System.nanoTime() - t0) / 1_000_000 + (x and 0)) }
            Prefs.put("perf_speed_ms", ms)
        }
        val speed = when { ms < 0 -> 7; ms < 12 -> 15; ms < 20 -> 11; ms < 35 -> 7; ms < 60 -> 3; else -> 0 }
        score = (ram + cpu + os + gpu + speed).coerceIn(0, 100)
        parts = listOf(tr("RAM") to listOf(ram, "/30").joinToString(""), tr("Procesor") to listOf(cpu, "/25").joinToString(""), "Android" to listOf(os, "/15").joinToString(""),  // i18n-ignore
            tr("Čip i grafika") to listOf(gpu, "/15").joinToString(""), tr("Test brzine") to listOf(speed, "/15").joinToString(""))
        weak = am.isLowRamDevice || ramGb < 2.8 || score < 70
        // a new install (no playlist yet) always starts in Lite, whatever the box; the user switches to Full or Automatic later.
        // Boxes that already had PRETORIAN keep what they had.
        if (Prefs.long("perf_mode", -1) < 0) Prefs.put("perf_mode", if (Store.playlists(c).isEmpty()) 1L else 0L)
        mode = Prefs.long("perf_mode", 0).toInt().coerceIn(0, 2)
    }
    fun set(m: Int) { mode = m; Prefs.put("perf_mode", m.toLong()) }
    /** About › Box score › Measure again */
    private var forceMeasure = false
    fun remeasure(c: Context) { Prefs.remove("perf_speed_ms"); forceMeasure = true; init(c); forceMeasure = false }
    val MODES = listOf("Automatski", "Lite", "Full")
}

/** Pictures used when the provider has none for a film: calm landscapes that fit the dark look. */
private val FALLBACKS = intArrayOf(R.drawable.fallback_bd1, R.drawable.fallback_bd2, R.drawable.fallback_bd3,
    R.drawable.fallback_bd4, R.drawable.fallback_bd5, R.drawable.fallback_bd6)
fun fallbackFor(key: String): Int = FALLBACKS[(key.hashCode() and 0x7fffffff) % FALLBACKS.size]

/** Picture that falls back to a landscape when there is no image or it fails to load. */
@Composable
fun OnyxImage(url: String?, key: String, modifier: Modifier = Modifier, maxW: Int = 360, scale: ContentScale = ContentScale.Crop) {
    val ctx = LocalContext.current
    val fb = painterResource(fallbackFor(key))
    if (url.isNullOrBlank()) Image(fb, null, modifier, contentScale = ContentScale.Crop)
    else {
        // never decode more pixels than the screen shows; Lite also uses half the memory per pixel
        val w = if (Perf.lite || Lean.posters) maxW * 2 / 3 else maxW
        val req = remember(url, w) {
            coil.request.ImageRequest.Builder(ctx).data(url).size(w, w * 3 / 2).allowRgb565(Perf.lite || Lean.posters).crossfade(!Perf.lite).build()
        }
        AsyncImage(model = req, contentDescription = null, contentScale = scale, modifier = modifier, error = fb, placeholder = fb)
    }
}

/** One quiet picture behind the whole app (dark mountains). No film or channel pictures in the background:
 *  calmer to look at and lighter for the box. */
object Dusk {
    var image by mutableStateOf<Any?>(R.drawable.fallback_bd1)
    @Suppress("UNUSED_PARAMETER") fun set(url: String?, key: String) {}
}

@Composable
fun DuskBackground() {
    DuskPicture()
    // Settings › Appearance › Background brightness: the whole background a little lighter or darker, in every theme
    val s = Look.shade.coerceIn(-2, 2)
    if (s != 0) Box(Modifier.fillMaxSize().background(if (s > 0) Color.White.copy(alpha = 0.05f * s) else Color.Black.copy(alpha = 0.22f * -s)))
}

@Composable
private fun DuskPicture() {
    // Lite: one calm picture in the theme's own tone (light themes light, dark themes dark), no blur, nothing that moves
    if (Perf.lite && Look.theme != "vece") {
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.fallback_bd1), null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(C.Bg.copy(alpha = 0.78f), C.Bg.copy(alpha = 0.88f), C.Bg.copy(alpha = 0.96f)))))
        return
    }
    if (Look.theme == "liquid") {
        // Liquid: a soft blurred picture behind see-through glass menus
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.fallback_bd1), null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(28.dp))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x80061018), Color(0x99061018), Color(0xCC03070C)))))
        return
    }
    if (Look.theme != "vece") return   // the evening blue belongs to Predvečerje only; other themes have their own background
    // "evening in New York": deep navy, a soft blue light at the top right, darker towards the bottom. No picture at all.
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0A0F1F), Color(0xFF0C1428), Color(0xFF080B14)))))
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0x593B5BA8), Color(0x1F27407A), Color.Transparent),
        center = androidx.compose.ui.geometry.Offset(1500f, 250f), radius = 1300f)))
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0x26303C78), Color.Transparent),
        center = androidx.compose.ui.geometry.Offset(150f, 900f), radius = 900f)))
}

/** Was the film's picture behind a screen; now the app has one background for everything. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun OnyxBackdrop(url: String?, key: String, startX: Float = 0f) {}

/** Height taken by the top bar; screens under it start below this. */
val ONYX_TOP = 118.dp

private data class NavItem(val screen: Screen, val icon: Int, val label: String, val badge: Int = 0)

/** Top of every screen: wordmark, playlist status, date and bell; below it the menu as pills. */
@Composable
fun OnyxTopBar(current: Screen, nav: Nav, simple: Boolean = false, firstFocus: FocusRequester? = null) {
    val ctx = LocalContext.current
    val pls = remember { Store.actives(ctx) }
    var now by remember { mutableStateOf(Date()) }
    var notes by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(15_000) } }
    val items = buildList {
        add(NavItem(Screen.Home, R.drawable.lc_house, "Početna"))
        val show = if (simple) Prefs.str("simple_items", "tv,vod,series").split(',') else listOf("tv", "vod", "series")
        if ("tv" in show) add(NavItem(Screen.Tv, R.drawable.lc_tv, "TV", Notes.unread("reminder")))
        if ("vod" in show) add(NavItem(Screen.Movies, R.drawable.lc_film, "Filmovi", NewContent.count("vod")))
        if ("series" in show) add(NavItem(Screen.Series, R.drawable.lc_monitor, "Serije", NewContent.count("series")))
        if (!simple) { add(NavItem(Screen.Sports, R.drawable.lc_ball, "Sport")); add(NavItem(Screen.Search, R.drawable.lc_search, "Pretraga")) }
    }
    val section = when (current) { is Screen.MovieDetail -> Screen.Movies; is Screen.SeriesDetail -> Screen.Series; Screen.AddPlaylist -> Screen.Settings; else -> current }
    // one compact row: wordmark · menu · status (a TV screen is only about 960×540 dp)
    Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 28.dp, end = 24.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("PRETORIAN", style = TextStyle(fontFamily = Inter, fontSize = 13.sp, color = C.Text, fontWeight = FontWeight.Light, letterSpacing = 4.sp), maxLines = 1)
            Spacer(Modifier.width(6.dp))
            // the menu takes the free width and scrolls sideways if a big size does not fit; the clock and profile never get covered
            Row(Modifier.weight(1f).focusProperties {
                    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
                    enter = { d -> if (d == androidx.compose.ui.focus.FocusDirection.Up && firstFocus == null) TopNav.fr else FocusRequester.Default }
                }.focusGroup().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (simple) 10.dp else 6.dp)) {
                items.forEachIndexed { i, it ->
                    // long OK: Movies / Series clear their "new" notices, Search chooses where it looks
                    val long: (() -> Unit)? = when (it.screen) {
                        Screen.Movies -> ({ TopMenus.clearKind = "vod" }); Screen.Series -> ({ TopMenus.clearKind = "series" })
                        Screen.Search -> ({ TopMenus.searchWhere = true }); else -> null
                    }
                    NavPill(it, section == it.screen, if (i == 1 && firstFocus != null) firstFocus else if (section == it.screen) TopNav.fr else null, big = simple, onLong = long) { nav.root(it.screen) }
                }
                if (simple) SimpleModePill(nav)
                else NavPill(NavItem(Screen.Settings, R.drawable.lc_settings, "Postavke",
                    if (Updater.available != null) 1 else 0), section == Screen.Settings, if (section == Screen.Settings) TopNav.fr else null, big = false) {
                    nav.root(Screen.Settings)
                }
                // what is on: Lite / Full gauge and S when simple mode is on (OK → Settings › Appearance › App mode)
                ModeBadges(simple) { SettingsNav.section = "look"; nav.root(Screen.Settings) }
            }
            Spacer(Modifier.width(8.dp))
            T(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), 13.sp, C.Text, FontWeight.SemiBold)  // i18n-ignore
            if (!simple) { Spacer(Modifier.width(4.dp)); ProfileButton(24.dp) }
            Box {
                Focusable(onClick = { notes = true }, shape = CircleShape, normal = Color.Transparent, focused = C.CardHi, zoom = 1.08f,
                    modifier = Modifier.size(30.dp).border(1.dp, C.Line, CircleShape)) { _ ->
                    Box(Modifier.align(Alignment.Center)) { Ico(R.drawable.lc_bell, 14.dp, C.Text) }
                }
                if (Notes.unread() > 0) Box(Modifier.align(Alignment.TopEnd).padding(top = 5.dp, end = 6.dp).size(6.dp).clip(CircleShape).background(C.Text))
            }
    }
    if (notes) NotesPopup({ notes = false }, nav)
    TopMenus.clearKind?.let { k ->
        Popup(tr(if (k == "vod") "Filmovi" else "Serije"), { TopMenus.clearKind = null }) {
            T(trf("%s novih · nestaju i s Početne", NewContent.count(k)), 13.sp, C.Muted)
            OptionRow("Obriši obavještenja", R.drawable.lc_trash_2, color = C.Danger) { NewContent.clear(k); TopMenus.clearKind = null }
            OptionRow("Odustani", R.drawable.lc_x) { TopMenus.clearKind = null }
        }
    }
    if (TopMenus.searchWhere) SearchWherePopup { TopMenus.searchWhere = false }
}

/** Menus opened with a long OK on the top bar (and on "Recently added"). */
object TopMenus {
    var clearKind by mutableStateOf<String?>(null)
    var searchWhere by mutableStateOf(false)
}

@Composable
private fun NavPill(it: NavItem, selected: Boolean, fr: FocusRequester?, big: Boolean, onLong: (() -> Unit)? = null, onClick: () -> Unit) {
    // advanced mode: icons only; the open screen and the focused one show their name next to the icon
    Box {
        Focusable(onClick = onClick, onMenu = onLong, focusRequester = fr, shape = Pill, normal = if (selected) C.CardHi else Color.Transparent,
            focused = C.Red, zoom = 1.04f, modifier = Modifier.height(if (big) 38.dp else 36.dp)) { f ->
            Row(Modifier.align(Alignment.Center).padding(horizontal = if (big) 16.dp else 10.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val col = if (f) C.OnRed else if (selected) C.Text else C.Soft
                Ico(it.icon, if (big) 17.dp else 18.dp, col)
                if (big || f || selected) T(it.label, if (big) 15.sp else 13.sp, col, FontWeight.SemiBold)
            }
        }
        if (it.badge > 0) Box(Modifier.align(Alignment.TopEnd)) { Bell(it.badge) }
    }
}

/** What the big picture on the home screen shows. */
private data class Hero(val key: String, val kicker: String, val title: String, val meta: String, val image: String?,
                        val button: String, val progress: Float?, val play: () -> Unit)

/** Home: the last film you watched (or the newest one at the provider) as a big picture, continue watching below. */
@Composable
fun OnyxHome(nav: Nav, simple: Boolean = false) {
    val ctx = LocalContext.current
    val first = remember { FocusRequester() }
    val cont = remember(Watch.rev) { Watch.continueList(ctx) }
    LaunchedEffect(Unit) { try { Watch.findAlternatives(ctx) } catch (_: Exception) {} }
    var contMenu by remember { mutableStateOf<Progress?>(null) }
    val p = remember { Store.active(ctx) }
    val homeScope = rememberCoroutineScope()
    // the film or series page goes under the player, so Back lands on it (same season and episode), a second Back on Home
    fun playOf(pr0: Progress): () -> Unit = {
        val why = Watch.blocked(ctx, pr0)
        val pr = if (why == null) Watch.playable(ctx, pr0) else pr0
        if (why != null) android.widget.Toast.makeText(ctx, why, android.widget.Toast.LENGTH_LONG).show()
        else homeScope.launch {
            val pl = Store.actives(ctx).firstOrNull { it.id == pr.pid } ?: p
            if (pl != null) try { kotlinx.coroutines.withTimeoutOrNull(2500) {
                if (pr.series != null) Repo.catalog(pl, "series").firstOrNull { it.str("n") == pr.series }?.let { o ->
                    SeriesJump.id = o.str("id"); SeriesJump.season = pr.season ?: -1; SeriesJump.num = pr.episode ?: -1
                    nav.push(Screen.SeriesDetail(SeriesItem(o.str("id"), o.str("n"), o.str("i").orNullIfBlank(), null, o.str("r").orNullIfBlank())))
                }
                else Repo.catalog(pl, "vod").firstOrNull { it.str("u") == pr.url }?.let { o ->
                    nav.push(Screen.MovieDetail(Vod(o.str("id"), o.str("n"), o.str("i").orNullIfBlank(), o.str("r").orNullIfBlank(), o.str("u"))))
                }
            } } catch (_: Exception) {}
            nav.push(Screen.Player(listOf(PlayItem(pr.title, pr.url, pr.poster, null, null, pr.series, pr.season, pr.episode)), 0, live = false))
        }
    }
    val newest = remember(NewContent.rev) {
        if (p == null || Perf.lite || !HomeRows.isOn("new")) emptyList() else NewContent.items(p.id, "vod").take(12).map { "vod" to it } + NewContent.items(p.id, "series").take(6).map { "series" to it }
    }
    fun openNew(kind: String, o: JSONObject) {
        if (kind == "series") nav.push(Screen.SeriesDetail(SeriesItem(o.optString("id"), o.optString("n"), o.optString("i").ifBlank { null }, null, o.optString("r").ifBlank { null })))
        else nav.push(Screen.MovieDetail(Vod(o.optString("id"), o.optString("n"), o.optString("i").ifBlank { null }, o.optString("r").ifBlank { null }, o.optString("u"))))
    }
    val hero: Hero? = cont.firstOrNull()?.let { pr ->
        val left = ((pr.dur - pr.pos) / 60_000L).coerceAtLeast(1)
        Hero(pr.url, tr(if (pr.series != null) "NASTAVI GLEDANJE · SERIJA" else "NASTAVI GLEDANJE · FILM"), cleanTitle(pr.series ?: pr.title).first,
            if (pr.next) trf("Sljedeća: S%sE%s", pr.season ?: 1, pr.episode ?: 1)
            else (if (pr.season != null) "S${pr.season} E${pr.episode} · " else "") + trf("ostalo %s min", left),
            pr.poster, tr("Nastavi"), if (pr.next) null else pr.fraction, playOf(pr))
    } ?: newest.firstOrNull()?.let { (kind, o) ->
        Hero(o.optString("id"), tr(if (kind == "series") "NOVO KOD PROVIDERA · SERIJA" else "NOVO KOD PROVIDERA · FILM"),
            cleanTitle(o.optString("n")).first, cleanTitle(o.optString("n")).second ?: "", o.optString("i").ifBlank { null }, tr("Gledaj"), null) { openNew(kind, o) }
    }
    Box(Modifier.fillMaxSize()) {
        OnyxBackdrop(hero?.image, hero?.key ?: "home")
        // one calm picture behind Home (no film posters): fades into the theme's own background, so the text stays easy to read
        HomeScenery()
        Column(Modifier.fillMaxSize()) {
            OnyxTopBar(Screen.Home, nav, simple)
            // everything sits higher and bigger: no empty half screen, nothing cut at the bottom of the TV
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // the card height follows the free space, so hero, Continue watching and Recent channels all fit without scrolling
            val cardH = (maxHeight - 372.dp).coerceIn(84.dp, 140.dp)
            val fullW = maxWidth
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 28.dp, end = 28.dp, top = 14.dp, bottom = 12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                if (hero != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (cont.isEmpty()) Box(Modifier.clip(Rnd(5.dp)).background(C.Text).padding(horizontal = 8.dp, vertical = 2.dp)) { T("NOVO", 10.sp, C.Bg, FontWeight.ExtraBold) }
                        Text(hero.kicker, style = TextStyle(fontFamily = Inter, fontSize = 12.sp, color = C.Soft, fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp), maxLines = 1)
                    }
                    Text(hero.title, style = TextStyle(fontFamily = Inter, fontSize = 38.sp, color = C.Text, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp).widthIn(max = 600.dp))
                    if (hero.meta.isNotBlank()) T(hero.meta, 12.sp, C.Soft, modifier = Modifier.padding(top = 2.dp))
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Focusable(onClick = hero.play, focusRequester = first, shape = Pill, normal = C.Text, focused = C.Text, zoom = 1.06f, modifier = Modifier.height(40.dp)) { _ ->
                            Row(Modifier.align(Alignment.Center).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                Ico(R.drawable.lc_play, 13.dp, C.Bg); T(hero.button, 13.sp, C.Bg, FontWeight.Bold)
                            }
                        }
                        // details of the film you continue (its number is in the link)
                        cont.firstOrNull()?.takeIf { it.series == null && it.url.contains("/movie/") }?.let { pr ->
                            PillBtn("Detalji") { nav.push(Screen.MovieDetail(Vod(pr.url.substringAfterLast('/').substringBefore('.'), pr.title, pr.poster, null, pr.url))) }
                        }
                    }
                    hero.progress?.let { f ->
                        Box(Modifier.padding(top = 10.dp).width(240.dp).height(3.dp).clip(Pill).background(C.Line)) {
                            Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(C.Text))
                        }
                    }
                } else if (p == null) T("Dodaj playlistu u Postavkama", 18.sp, C.Soft)
                else {
                    // nothing watched yet: a welcome with quick ways in instead of an empty screen
                    Text(tr("DOBRO DOŠAO"), style = TextStyle(fontFamily = Inter, fontSize = 12.sp, color = C.Soft, fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp))
                    Text(tr("Šta gledamo danas?"), style = TextStyle(fontFamily = Inter, fontSize = 34.sp, color = C.Text, fontWeight = FontWeight.ExtraBold),
                        modifier = Modifier.padding(top = 2.dp))
                    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(Triple(Screen.Tv, R.drawable.lc_tv, "TV uživo"), Triple(Screen.Movies, R.drawable.lc_film, "Filmovi"),
                            Triple(Screen.Series, R.drawable.lc_monitor, "Serije"), Triple(Screen.Sports, R.drawable.lc_ball, "Sport")).forEachIndexed { i, (scr, ic, lb) ->
                            if (simple && scr == Screen.Sports) return@forEachIndexed
                            Focusable(onClick = { nav.root(scr) }, focusRequester = if (i == 0) first else null, shape = Pill, normal = if (i == 0) C.Text else C.CardHi,
                                focused = C.Red, zoom = 1.06f, modifier = Modifier.height(42.dp)) { f ->
                                Row(Modifier.align(Alignment.Center).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val col = if (f) C.OnRed else if (i == 0) C.Bg else C.Text
                                    Ico(ic, 15.dp, col); T(lb, 14.sp, col, FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // playlist always on top; under it at most 2 live matches you follow, then new episodes
                    SubscriptionCard()
                    HomeAlerts(nav, (fullW * 0.34f).coerceIn(250.dp, 380.dp))
                }
                }
                Spacer(Modifier.height(12.dp))
                // rows in the order and with the switches from Settings › Home
                @Suppress("UNUSED_VARIABLE") val rowsRev = HomeRows.rev   // a row switched on/off (also "Remove") redraws Home at once
                HomeRows.order().forEach { id ->
                    when (id) {
                        "fresh" -> {
                // series you follow that got a new episode: straight into that season and episode
                val fresh = remember(Follow.rev) { Follow.fresh() }
                if (fresh.isNotEmpty()) {
                    RowTitle("NOVE EPIZODE")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(end = 40.dp, top = 6.dp, bottom = 10.dp)) {
                        items(fresh, key = { "f" + it.id }) { f ->
                            WideCard(f.name, epCode(f.season, f.num) + " · " + tr("nova"), f.cover, "fresh_" + f.id, null, height = cardH) {  // i18n-ignore
                                SeriesJump.id = f.id; SeriesJump.season = f.season; SeriesJump.num = f.num
                                nav.root(Screen.Series); nav.push(Screen.SeriesDetail(SeriesItem(f.id, f.name, f.cover, null, null)))
                            }
                        }
                    }
                }
                        }
                        "cont" -> {
                if (cont.isNotEmpty()) {
                    RowTitle("NASTAVI GLEDANJE")
                    // round trip: ▶ on the last card goes to the first, ◀ on the first goes to the last
                    val cState = androidx.compose.foundation.lazy.rememberLazyListState()
                    val cScope = rememberCoroutineScope()
                    val cFirst = remember { FocusRequester() }; val cLast = remember { FocusRequester() }
                    LazyRow(state = cState, horizontalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(end = 40.dp, top = 6.dp, bottom = 6.dp)) {
                        itemsIndexed(cont, key = { _, it -> it.url }) { i, pr ->
                            WideCard(pr.series ?: pr.title, if (pr.season != null) "S${pr.season} E${pr.episode}" else fmtTime(pr.dur), pr.poster, pr.url,
                                if (pr.next) null else pr.fraction, onMenu = { contMenu = pr }, height = cardH,
                                fr = if (i == 0) cFirst else if (i == cont.lastIndex) cLast else null,
                                keys = Modifier.onPreviewKeyEvent { e ->
                                    if (e.type != KeyEventType.KeyDown || cont.size < 2) false
                                    else if (i == cont.lastIndex && e.key == Key.DirectionRight) { cScope.launch { cState.scrollToItem(0); delay(30); cFirst.tryFocus() }; true }
                                    else if (i == 0 && e.key == Key.DirectionLeft) { cScope.launch { cState.scrollToItem(cont.lastIndex); delay(30); cLast.tryFocus() }; true }
                                    else false
                                }, onClick = playOf(pr))
                        }
                    }
                }
                        }
                        "mylist" -> {
                // films and series you saved with "+ My list"
                val mine = remember(MyList.rev) { MyList.items("vod").map { "vod" to it } + MyList.items("series").map { "series" to it } }
                if (mine.isNotEmpty()) {
                    RowTitle("MOJA LISTA")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(end = 40.dp, top = 6.dp, bottom = 10.dp)) {
                        items(mine, key = { "ml" + it.first + it.second.optString("id") }) { (k, o) ->
                            val name = o.optString("n"); val img = o.optString("i").ifBlank { null }
                            WideCard(cleanTitle(name).first, tr(if (k == "vod") "Film" else "Serija"), img, "ml_" + o.optString("id"), null, height = cardH) {
                                if (k == "vod") nav.push(Screen.MovieDetail(Vod(o.optString("id"), name, img, null, o.optString("u"))))
                                else nav.push(Screen.SeriesDetail(SeriesItem(o.optString("id"), name, img, null, null)))
                            }
                        }
                    }
                }
                        }
                        "follow" -> FollowRow(nav, cardH)
                        "today" -> {
                // today's matches (only the chosen sports): live first, then by start time
                val today by load("today_sport") {
                    val en = Prefs.str("sport_enabled", "football").split(',').filter { it.isNotBlank() }
                    (Sports.lastCombined[0] ?: Sports.day(0)).filter { en.isEmpty() || it.sport in en }.filter { !it.over }
                        .sortedWith(compareBy({ !it.live }, { it.start })).take(15)
                }
                (today as? Load.Ok)?.value?.takeIf { it.isNotEmpty() }?.let { ms ->
                    RowTitle("UTAKMICE DANAS")
                    // goes round: Left on the first → the last (Clear), Right on the last → the first
                    val w = remember(ms.size) { RowWrap(ms.size + 1) }
                    val tScope = rememberCoroutineScope()
                    LazyRow(state = w.state, horizontalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(end = 40.dp, top = 6.dp, bottom = 10.dp)) {
                        itemsIndexed(ms, key = { _, e -> "td" + e.id }) { i, e ->
                            MatchKitCard(e, cardH, w.fr(i), w.keys(i, tScope)) { SportsMemory.evId = e.id; SportsMemory.day = 0; nav.root(Screen.Sports) }
                        }
                        // last card: take the row off Home (back in Settings › Home)
                        item {
                            Focusable(onClick = { HomeRows.setOn("today", false) }, focusRequester = w.fr(ms.size), shape = Rnd(14.dp), normal = C.Rail, focused = C.Red, zoom = 1.04f,
                                modifier = Modifier.width(120.dp).height(cardH).border(1.dp, C.Line, Rnd(14.dp)).then(w.keys(ms.size, tScope))) { f ->
                                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Ico(R.drawable.lc_x, 20.dp, if (f) C.OnRed else C.Text)
                                    T("Ukloni", 13.sp, if (f) C.OnRed else C.Text, FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                        }
                        "new" -> {
                // only as many posters as fit across the screen (all the same 2:3 size), then "More ›" into Recently added.
                // Fewer pictures at once = no stutter on weaker boxes.
                val gap = 14.dp
                val pw = ((fullW - 56.dp - gap * 6) / 7).coerceIn(96.dp, 190.dp)
                val fit = ((((fullW - 56.dp) + gap) / (pw + gap)).toInt() - 1).coerceIn(3, if (Perf.lite) 5 else 8)
                val dScope = rememberCoroutineScope()
                @Composable
                fun posterRow(title: String, n: Int, total: Int, unit: String, more: () -> Unit, card: @Composable (Int, Modifier, FocusRequester?) -> Unit) {
                    if (n == 0) return
                    val w = remember(n) { RowWrap(n + 1) }
                    RowTitle(title)
                    LazyRow(state = w.state, horizontalArrangement = Arrangement.spacedBy(gap), contentPadding = PaddingValues(top = 6.dp, bottom = 12.dp, end = 40.dp)) {
                        items(n) { i -> card(i, Modifier.width(pw).then(w.keys(i, dScope)), w.fr(i)) }
                        item { MoreCard((total - n).coerceAtLeast(0), unit, pw, pw * 1.5f, w.fr(n), w.keys(n, dScope), more) }
                    }
                }
                if (p != null && cont.isEmpty() && Recent.list().isEmpty()) {
                    // no history at all: the newest films, series and channels at the provider. Gone as soon as you watch something.
                    // read once, then kept: coming back to Home shows it at once. New only after a playlist refresh or new items from the provider.
                    val discL by load(p.id, HomeCache.rev) { HomeCache.disc[p.id] ?: run {
                        val mv = Repo.vodGroups(p).take(2).flatMap { g -> try { Repo.movies(p, g.id) } catch (e: Exception) { emptyList() } }
                            .distinctBy { it.id }.sortedByDescending { it.added }.take(60)
                        val se = Repo.seriesGroups(p).take(2).flatMap { g -> try { Repo.series(p, g.id) } catch (e: Exception) { emptyList() } }
                            .distinctBy { it.id }.sortedByDescending { it.added }.take(60)
                        val ch = try { Repo.allChannels(p).sortedByDescending { it.id.toLongOrNull() ?: 0L }.take(30) } catch (e: Exception) { emptyList() }
                        Triple(mv, se, ch)
                    }.also { HomeCache.disc[p.id] = it } }
                    (HomeCache.disc[p.id] ?: (discL as? Load.Ok)?.value)?.let { (mv, se, ch) ->
                        // "Clear notifications" (long OK on Movies / Series) also takes these rows off Home
                        if (!Prefs.bool("disc_off_vod")) posterRow("NAJNOVIJI FILMOVI", minOf(fit, mv.size), mv.size, "filmova", { nav.root(Screen.Movies) }) { i, m, fr ->
                            val v = mv[i]; PosterCard(cleanTitle(v.name).first, v.poster, v.id, null, m, fr = fr, rating = v.rating) { nav.push(Screen.MovieDetail(v)) }
                        }
                        if (!Prefs.bool("disc_off_series")) posterRow("NAJNOVIJE SERIJE", minOf(fit, se.size), se.size, "serija", { nav.root(Screen.Series) }) { i, m, fr ->
                            val v = se[i]; PosterCard(cleanTitle(v.name).first, v.cover, v.id, null, m, fr = fr, rating = v.rating) { nav.push(Screen.SeriesDetail(v)) }
                        }
                        val cn = minOf(((fullW - 56.dp) / 232.dp).toInt().coerceAtLeast(2) - 1, ch.size)
                        if (cn > 0) {
                            val w = remember(cn) { RowWrap(cn + 1) }
                            RowTitle("NOVI KANALI")
                            LazyRow(state = w.state, horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp, end = 40.dp)) {
                                items(cn) { i -> val c = ch[i]
                                    Focusable(onClick = { dScope.launch { playInGroup(ctx, nav, c, p.id) } }, focusRequester = w.fr(i), shape = Rnd(14.dp), normal = C.Rail, focused = C.Red, zoom = 1.04f,
                                        modifier = Modifier.width(220.dp).height(60.dp).border(1.dp, C.Line, Rnd(14.dp)).then(w.keys(i, dScope))) { f ->
                                        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            ChannelLogo(c, 38.dp, onLight = f)
                                            T(c.name, 14.sp, if (f) C.OnRed else C.Text, FontWeight.Bold, modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                                item { MoreCard((ch.size - cn).coerceAtLeast(0), "kanala", 120.dp, 60.dp, w.fr(cn), w.keys(cn, dScope)) { nav.root(Screen.Tv) } }
                            }
                        }
                    }
                }
                else if (p != null) {
                    val nv = remember(NewContent.rev) { NewContent.items(p.id, "vod") }
                    val ns = remember(NewContent.rev) { NewContent.items(p.id, "series") }
                    posterRow("NOVO DODANO · FILMOVI", minOf(fit, nv.size), nv.size, "filmova", { VodMemory.group["vod"] = "__new__"; nav.root(Screen.Movies) }) { i, m, fr ->
                        val o = nv[i]; PosterCard(cleanTitle(o.optString("n")).first, o.optString("i").ifBlank { null }, o.optString("id"), null, m, fr = fr) { openNew("vod", o) }
                    }
                    posterRow("NOVO DODANO · SERIJE", minOf(fit, ns.size), ns.size, "serija", { VodMemory.group["series"] = "__new__"; nav.root(Screen.Series) }) { i, m, fr ->
                        val o = ns[i]; PosterCard(cleanTitle(o.optString("n")).first, o.optString("i").ifBlank { null }, o.optString("id"), null, m, fr = fr) { openNew("series", o) }
                    }
                }
                        }
                        "recent" -> {
                Spacer(Modifier.height(6.dp))
                // the channels you watched last, next to Continue watching
                RecentStrip(nav)
                        }
                    }
                }
            }
            }
        }
    }
    contMenu?.let { pr ->
        PosterSheet(tr(if (pr.series != null) "Serija" else "Film"), cleanTitle(pr.series ?: pr.title).first, pr.poster, pr.url, { contMenu = null }) {
            SheetRow("Nastavi", trf("ostalo %s min", ((pr.dur - pr.pos) / 60_000L).coerceAtLeast(1)), R.drawable.lc_play) { contMenu = null; playOf(pr)() }
            SheetRow("Ukloni iz Nastavi gledati", "film ostaje u listi, samo nestaje odavde", R.drawable.lc_x) { Watch.remove(ctx, pr.url); contMenu = null }
            SheetRow("Označi kao odgledano", null, R.drawable.lc_eye) { Watch.setWatched(ctx, pr.url, pr.title, pr.poster, true); contMenu = null }
            if (pr.series == null && pr.url.contains("/movie/")) SheetRow("Detalji", "opis, glumci, trailer", R.drawable.lc_info) {
                contMenu = null; nav.push(Screen.MovieDetail(Vod(pr.url.substringAfterLast('/').substringBefore('.'), pr.title, pr.poster, null, pr.url)))
            }
        }
    }
    LaunchedEffect(Unit) { first.tryFocus() }
    if (!simple) TipOnce("hold_ok", "Savjet", "Drži OK na bilo čemu za opcije: Moja lista, Favoriti, Sakrij.")
}

/** Subscription on Home: one line per active playlist with a big "days left" number. Red at 5 days or less. */
@Composable
fun SubscriptionCard() {
    val ctx = LocalContext.current
    val pls = remember { Store.actives(ctx) }
    if (pls.isEmpty()) return
    fun daysOf(p: Playlist) = p.exp.toLongOrNull()?.takeIf { it > 0 }?.let { (it * 1000 - System.currentTimeMillis()) / 86_400_000L }
    val anyWarn = pls.any { (daysOf(it) ?: 99) <= 5 }
    val accent = if (anyWarn) C.Danger else C.Green
    Row(Modifier.width(270.dp).height(IntrinsicSize.Min).clip(Rnd(18.dp)).background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.16f), C.Rail)))
        .border(1.dp, accent.copy(alpha = 0.45f), Rnd(18.dp))) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(accent))   // coloured edge: green fine, red soon
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
                Kicker(if (pls.size > 1) "Aktivne playliste" else "Aktivna playlista", size = 10.sp)
            }
            pls.take(3).forEach { p ->
                val sec = p.exp.toLongOrNull()?.takeIf { it > 0 }
                val days = daysOf(p)
                val warn = days != null && days <= 5
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        T(p.name, 17.sp, C.Text, FontWeight.Bold, maxLines = 1)
                        T(if (sec != null) tr("Ističe") + " " + SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(sec * 1000)) else tr("bez datuma isteka"),  // i18n-ignore
                            11.sp, C.Soft, maxLines = 1)
                    }
                    if (days != null) Column(horizontalAlignment = Alignment.End) {
                        T(if (days < 0) "0" else "$days", 26.sp, if (warn) C.Danger else C.Text, FontWeight.ExtraBold)
                        T(if (days < 0) tr("Isteklo") else tr("dana"), 10.sp, if (warn) C.Danger else C.Soft, FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/** A sideways row that goes round: ▶ on the last item jumps to the first, ◀ on the first jumps to the last. */
@Composable
fun RoundRow(count: Int, gap: Dp, item: @Composable (Int, FocusRequester?, Modifier) -> Unit) {
    val st = androidx.compose.foundation.lazy.rememberLazyListState()
    val sc = rememberCoroutineScope()
    val first = remember { FocusRequester() }; val last = remember { FocusRequester() }
    LazyRow(state = st, horizontalArrangement = Arrangement.spacedBy(gap), contentPadding = PaddingValues(end = 40.dp, top = 6.dp, bottom = 10.dp)) {
        items(count) { i ->
            val keys = Modifier.onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || count < 2) false
                else if (i == count - 1 && e.key == Key.DirectionRight) { sc.launch { st.scrollToItem(0); delay(30); first.tryFocus() }; true }
                else if (i == 0 && e.key == Key.DirectionLeft) { sc.launch { st.scrollToItem(count - 1); delay(30); last.tryFocus() }; true }
                else false
            }
            item(i, if (i == 0) first else if (i == count - 1) last else null, keys)
        }
    }
}

@Composable
private fun RowTitle(t: String) {
    Text(tr(t), style = TextStyle(fontFamily = Inter, fontSize = 11.sp, color = C.Muted, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp),
        modifier = Modifier.padding(bottom = 7.dp))
}

/** Landscape card with picture, title and a thin progress line. */
@Composable
fun WideCard(title: String, sub: String, image: String?, key: String, progress: Float?, onMenu: (() -> Unit)? = null, height: Dp = 140.dp,
             fr: FocusRequester? = null, keys: Modifier = Modifier, onClick: () -> Unit) {
    Column(Modifier.width(height * (250f / 140f)), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Focusable(onClick = onClick, onMenu = onMenu, focusRequester = fr, shape = Rnd(12.dp), normal = C.Card, zoom = 1.05f, modifier = Modifier.fillMaxWidth().height(height).then(keys)) { f ->
            // the whole poster is shown (never cut); the empty sides get a soft, blurred copy of the same picture
            if (!Perf.lite) OnyxImage(image, key, Modifier.fillMaxSize().clip(Rnd(12.dp)).blur(18.dp).alpha(0.55f), maxW = 160)
            OnyxImage(image, key, Modifier.fillMaxSize().clip(Rnd(12.dp)), maxW = 300, scale = ContentScale.Fit)
            if (f) Box(Modifier.fillMaxSize().border(2.dp, Color.White, Rnd(12.dp)))
            if (progress != null) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color(0x33FFFFFF))) {
                Box(Modifier.fillMaxWidth(progress.coerceAtLeast(0.02f)).fillMaxHeight().background(Color.White))
            }
        }
        T(cleanTitle(title).first, 14.sp, C.Text, FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(start = 2.dp))
        T(sub, 11.sp, C.Muted, maxLines = 1, modifier = Modifier.padding(start = 2.dp))
    }
}

/** Portrait poster with title on the picture and an optional progress line. */
@Composable
fun PosterCard(title: String, image: String?, key: String, progress: Float?, modifier: Modifier, onFocus: (() -> Unit)? = null,
               onMenu: (() -> Unit)? = null, fr: FocusRequester? = null, rating: String? = null, onClick: () -> Unit) {
    Focusable(onClick = onClick, onFocus = onFocus, onMenu = onMenu, focusRequester = fr, shape = Rnd(12.dp), normal = C.Card, zoom = 1.05f, modifier = modifier.aspectRatio(2f / 3f)) { f ->
        OnyxImage(image, key, Modifier.fillMaxSize().clip(Rnd(12.dp)))
        Box(Modifier.fillMaxSize().clip(Rnd(12.dp)).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE0000000)), startY = 160f)))
        if (f) Box(Modifier.fillMaxSize().border(2.dp, Color.White, Rnd(12.dp)))
        // the provider's rating in the corner
        rating?.toDoubleOrNull()?.takeIf { it > 0 }?.let { r ->
            Box(Modifier.align(Alignment.TopEnd).padding(6.dp).clip(Rnd(6.dp)).background(Color(0xCC000000)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                T("★ " + String.format("%.1f", if (r > 10) r / 10 else r), 11.sp, Color(0xFFFBBF24), FontWeight.Bold)
            }
        }
        T(title, 14.sp, Color.White, FontWeight.Bold, maxLines = 2,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, end = 10.dp, bottom = if (progress != null) 18.dp else 10.dp))
        if (progress != null) Box(Modifier.align(Alignment.BottomStart).padding(start = 10.dp, end = 10.dp, bottom = 8.dp).fillMaxWidth().height(3.dp).clip(Pill).background(Color(0x40FFFFFF))) {
            Box(Modifier.fillMaxWidth(progress.coerceAtLeast(0.03f)).fillMaxHeight().background(Color.White))
        }
    }
}

/** 0.6.0/0.6.4: everyone gets the new look and the top menu once; afterwards they can change it. */
fun onyxMigrate(c: Context) {
    val p = c.getSharedPreferences("pretorian", Context.MODE_PRIVATE)
    // brand new install: hide adult groups and start in simple mode (TV, Movies, Series); "Napredni mod" is one click away
    if (p.getInt("look_v", 0) == 0 && !p.contains("look_theme")) p.edit().putBoolean("hide_adult_default", true).putBoolean("simple_mode", true)
        // 1.0.38: a new box starts with the hardware decoder and with adult, XXX, LGBT and trans categories hidden (PIN opens them)
        .putLong("tech_decoder", 1L).putBoolean("par_adult", true).putBoolean("par_lgbt", true).putBoolean("par_trans", true).apply()
    if (p.getInt("look_v", 0) < 7) {   // 0.6.4: "Predvečerje" becomes the look for everyone once
        Look.theme = "vece"; Look.layout = 2
        p.edit().putInt("look_v", 7).putString("look_theme", "vece").putInt("look_layout", 2).apply()
    }
    if (p.getInt("look_v", 0) < 8) { Look.theme = "vece"; p.edit().putInt("look_v", 8).putString("tv_view", "list").putString("look_theme", "vece").apply() }   // 0.8.0: TV opens as the new channel list once
}

/** The last 10 TV channels you watched, newest first, with the group they were played from. */
object Recent {
    var rev by mutableIntStateOf(0)
    data class Item(val ch: Channel, val pid: String?, val catId: String, val catName: String)
    fun list(): List<Item> = try {
        val a = JSONArray(Prefs.str("recent_ch", "[]"))
        (0 until a.length()).map { a.getJSONObject(it) }.map { o ->
            Item(Channel(o.optString("id"), o.optInt("num"), o.optString("n"), o.optString("l").ifBlank { null }, o.optString("g"), o.optString("u"),
                o.optString("e").ifBlank { null }, o.optString("p"), o.optInt("a")), o.optString("gp").ifBlank { null }, o.optString("gi"), o.optString("gn"))
        }
    } catch (e: Exception) { emptyList() }
    fun add(c: Channel, pid: String?, catId: String, catName: String) {
        val keep = list().filter { it.ch.url != c.url }.take(9)
        val a = JSONArray()
        (listOf(Item(c, pid, catId, catName)) + keep).forEach { i ->
            a.put(JSONObject().put("id", i.ch.id).put("num", i.ch.num).put("n", i.ch.name).put("l", i.ch.logo ?: "").put("g", i.ch.group).put("u", i.ch.url)
                .put("e", i.ch.epg ?: "").put("p", i.ch.pid).put("a", i.ch.archive).put("gp", i.pid ?: "").put("gi", i.catId).put("gn", i.catName))
        }
        Prefs.put("recent_ch", a.toString()); rev++
    }
    fun clear() { Prefs.put("recent_ch", "[]"); rev++ }
    fun remove(url: String) {
        val a = JSONArray(); list().filter { it.ch.url != url }.forEach { i ->
            a.put(JSONObject().put("id", i.ch.id).put("num", i.ch.num).put("n", i.ch.name).put("l", i.ch.logo ?: "").put("g", i.ch.group).put("u", i.ch.url)
                .put("e", i.ch.epg ?: "").put("p", i.ch.pid).put("a", i.ch.archive).put("gp", i.pid ?: "").put("gi", i.catId).put("gn", i.catName))
        }
        Prefs.put("recent_ch", a.toString()); rev++
    }
}

/** Channel logo from the provider; without one a see-through glass tile with the first letters. */
@Composable
fun ChannelLogo(c: Channel, size: androidx.compose.ui.unit.Dp, onLight: Boolean = false) {
    var failed by remember(c.logo) { mutableStateOf(c.logo.isNullOrBlank()) }
    Box(Modifier.size(size).clip(Rnd(9.dp)).background(if (onLight) Color(0x12000000) else Color(0x1FFFFFFF))
        .border(1.dp, if (onLight) Color(0x26000000) else Color(0x38FFFFFF), Rnd(9.dp)), contentAlignment = Alignment.Center) {
        if (!failed) AsyncImage(model = c.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp),
            onError = { failed = true })
        else T(c.name.filter { it.isLetterOrDigit() }.take(2).uppercase().ifBlank { "TV" }, (size.value * 0.32f).sp, if (onLight) Color(0xFF0A0A0A) else Color.White, FontWeight.ExtraBold)
    }
}

/** The menu pill of the screen you are on: Up from the top of a screen comes here. */
object TopNav { val fr = FocusRequester() }

object OnyxState { var askAdvanced by mutableStateOf(false); var askPin by mutableStateOf(false) }

/** Round trip for a row: ▶ on the last item goes to the first, ◀ on the first goes to the last. */
class RowWrap(val n: Int) {
    val state = androidx.compose.foundation.lazy.LazyListState()
    val first = FocusRequester(); val last = FocusRequester()
    fun fr(i: Int) = if (i == 0) first else if (i == n - 1) last else null
    fun keys(i: Int, scope: kotlinx.coroutines.CoroutineScope) = Modifier.onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown || n < 2) false
        else if (i == n - 1 && e.key == Key.DirectionRight) { scope.launch { state.scrollToItem(0); delay(30); first.tryFocus() }; true }
        else if (i == 0 && e.key == Key.DirectionLeft) { scope.launch { state.scrollToItem(n - 1); delay(30); last.tryFocus() }; true }
        else false
    }
}

/** Remembers which big tile was opened last in simple mode. */
object SimpleMemory { var last: Screen = Screen.Tv }

/** Simple (parental) mode home: only TV, Movies and Series as three big tiles, and the mode switch below. */
@Composable
fun OnyxSimpleHome(nav: Nav) {
    val show = Prefs.str("simple_items", "tv,vod,series").split(',')
    val tiles = buildList {
        if ("tv" in show) add(Triple(Screen.Tv, R.drawable.lc_tv, "TV"))
        if ("vod" in show) add(Triple(Screen.Movies, R.drawable.lc_film, "Filmovi"))
        if ("series" in show) add(Triple(Screen.Series, R.drawable.lc_monitor, "Serije"))
    }
    val frs = remember { tiles.associate { it.first to FocusRequester() } }
    BackHandler(enabled = true) { }   // Back on the tiles does nothing: nobody leaves the app by accident
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("PRETORIAN", style = TextStyle(fontFamily = Inter, fontSize = 15.sp, color = C.Soft, fontWeight = FontWeight.Light, letterSpacing = 5.sp))
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val gap = 28.dp
                val w = ((maxWidth - gap * (tiles.size - 1)) / tiles.size.coerceAtLeast(1)).coerceAtMost(260.dp)
                val h = (w * 1.05f).coerceAtMost(maxHeight - 20.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                    tiles.forEach { (scr, icon, label) ->
                        Focusable(onClick = { SimpleMemory.last = scr; nav.root(scr) }, focusRequester = frs[scr], shape = Rnd(26.dp),
                            normal = C.Rail, focused = C.Red, zoom = 1.06f, modifier = Modifier.width(w).height(h).border(1.dp, C.Line, Rnd(26.dp))) { f ->
                            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                                Ico(icon, (h * 0.32f), if (f) C.OnRed else C.Text)
                                T(label, 28.sp, if (f) C.OnRed else C.Text, FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            SimpleModePill(nav)
        }
    }
    LaunchedEffect(Unit) { delay(50); (frs[SimpleMemory.last] ?: frs.values.firstOrNull())?.tryFocus() }
}

/** Simple mode: one pill; OK opens "Napredni mod" right under it (PIN if one is set). The pill moves up to make room. */
@Composable
fun SimpleModePill(nav: Nav) {
    // the menu opens right under the button (same width, centred); the button moves up to make room. Back or ▲ closes it.
    var open by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    val btnFr = remember { FocusRequester() }
    BackHandler(enabled = open) { open = false; try { btnFr.requestFocus() } catch (_: Exception) {} }
    Column(Modifier.width(260.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Focusable(onClick = { open = !open }, focusRequester = btnFr, shape = Pill, normal = C.CardHi, focused = C.Red, zoom = 1.03f,
            modifier = Modifier.fillMaxWidth().height(44.dp)) { f ->
            Row(Modifier.align(Alignment.Center).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Ico(R.drawable.lc_lock, 16.dp, if (f) C.OnRed else C.Text)
                T("Jednostavni mod", 15.sp, if (f) C.OnRed else C.Text, FontWeight.SemiBold)
                Ico(R.drawable.lc_chevron_down, 14.dp, if (f) C.OnRed else C.Soft)
            }
        }
        if (open) {
            Focusable(onClick = {
                open = false
                if (Prefs.bool("pin_simple_exit", true) && Lock.hasPin()) OnyxState.askPin = true else { Lock.switchSimple(false); nav.root(Screen.Home) }
            }, focusRequester = fr, shape = Pill, normal = C.Rail, focused = C.Red, zoom = 1.03f,
                modifier = Modifier.fillMaxWidth().height(44.dp).border(1.dp, C.Line, Pill).onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionUp) { open = false; try { btnFr.requestFocus() } catch (_: Exception) {}; true } else false
                }) { f ->
                Row(Modifier.align(Alignment.Center).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Ico(R.drawable.lc_key_round, 16.dp, if (f) C.OnRed else C.Amber)
                    T("Napredni mod", 15.sp, if (f) C.OnRed else C.Text, FontWeight.SemiBold)
                }
            }
            LaunchedEffect(Unit) { delay(40); fr.tryFocus() }
        }
    }
}

@Composable
fun AdvancedGate(nav: Nav) {
    var pin by remember { mutableStateOf(false) }
    if (OnyxState.askAdvanced) Popup("Aktivirati napredni mod?", onDismiss = { OnyxState.askAdvanced = false }) {
        T("Pažnja: aktivirate napredni mod i dodatne opcije. Nastavite s pažnjom, jer možete izazvati nepoželjne posljedice i pokvariti podešavanja programa.",
            16.sp, C.Soft, maxLines = 5)
        OptionRow("Ostani u jednostavnom modu", R.drawable.lc_check) { OnyxState.askAdvanced = false }
        OptionRow(if (Prefs.bool("pin_simple_exit", true) && Lock.hasPin()) "Nastavi i unesi PIN" else "Nastavi", R.drawable.lc_key_round, color = C.Amber) {
            OnyxState.askAdvanced = false
            if (Prefs.bool("pin_simple_exit", true) && Lock.hasPin()) pin = true else { Lock.switchSimple(false); nav.root(Screen.Settings) }
        }
    }
    LaunchedEffect(OnyxState.askPin) { if (OnyxState.askPin) { OnyxState.askPin = false; pin = true } }
    if (pin) PinDialog("PIN za napredni mod", onDismiss = { pin = false }) { entered ->
        val err = Lock.check(entered)
        if (err == null) { pin = false; Lock.switchSimple(false); nav.root(Screen.Home) }
        err
    }
}


/** Settings › Home: which rows the home screen shows and in which order. */
object HomeRows {
    // "Newly added": on in Full (can be switched off here), never in Lite
    val ALL = listOf("fresh" to "Nove epizode", "cont" to "Nastavi gledati", "mylist" to "Moja lista", "new" to "Novo dodano",
        "today" to "Utakmice danas", "follow" to "Pratiš · uživo", "recent" to "Nedavno gledani kanali")
    var rev by mutableIntStateOf(0)
    /** "Matches today" starts off (it loads live scores) */
    /** weak box (score under 50): "Recently added" posters also start off; the user can turn them on in Settings › Home */
    // first start on every box: "Newly added" (new films, series, channels) and "Matches today" are off until switched on
    private fun offStr() = Prefs.str("home_off", "today,new,follow")
    private fun savedOrder(): List<String> {
        val saved = Prefs.str("home_rows", "fresh,cont,new,recent").split(',').filter { s -> ALL.any { it.first == s } }.toMutableList()
        if ("follow" !in saved) { val i = saved.indexOf("today"); if (i >= 0) saved.add(i + 1, "follow") else saved.add("follow") }
        return saved
    }
    fun order(): List<String> {
        val saved = savedOrder()
        val off = offStr().split(',').toSet()
        // Lite: never "Newly added" (posters, the heaviest row), whatever is saved
        return (saved + ALL.map { it.first }.filter { it !in saved }).filter { it !in off && !(Perf.lite && it == "new") }
    }
    fun all(): List<String> { val saved = savedOrder(); return saved + ALL.map { it.first }.filter { it !in saved } }
    fun isOn(id: String) = id !in offStr().split(',') && !(Perf.lite && id == "new")
    fun setOn(id: String, on: Boolean) { val off = offStr().split(',').filter { it.isNotBlank() }.toMutableSet(); if (on) off.remove(id) else off.add(id); Prefs.put("home_off", off.joinToString(",")); rev++ }
    fun toggle(id: String) { val off = offStr().split(',').filter { it.isNotBlank() }.toMutableSet(); if (!off.add(id)) off.remove(id); Prefs.put("home_off", off.joinToString(",")); rev++ }
    fun move(id: String, by: Int) {
        val l = all().toMutableList(); val i = l.indexOf(id); val j = (i + by).coerceIn(0, l.lastIndex)
        if (i < 0 || i == j) return
        l.removeAt(i); l.add(j, id); Prefs.put("home_rows", l.joinToString(",")); rev++
    }
}


/** Last card of a Home row: "More ›" in the theme's colours, opens the whole list. */
@Composable
private fun MoreCard(rest: Int, unit: String, w: Dp, h: Dp, fr: FocusRequester?, keys: Modifier, onClick: () -> Unit) {
    Focusable(onClick = onClick, focusRequester = fr, shape = Rnd(12.dp), normal = C.Red.copy(alpha = 0.12f), focused = C.Red.copy(alpha = 0.30f), zoom = 1.05f,
        modifier = Modifier.width(w).height(h).border(1.dp, C.Red.copy(alpha = 0.35f), Rnd(12.dp)).then(keys)) { f ->
        if (f) Box(Modifier.fillMaxSize().border(2.dp, C.Red, Rnd(12.dp)))
        Column(Modifier.align(Alignment.Center).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            T("Više ›", 15.sp, C.Text, FontWeight.Bold)
            if (rest > 0 && h > 80.dp) T("+ " + rest + " " + tr(unit), 11.sp, C.Soft, maxLines = 1)  // i18n-ignore
        }
    }
}

/** Top right of Home: new episodes of series you watch and news of your teams / followed matches. OK opens it. */
@Composable
fun HomeAlerts(nav: Nav, width: Dp) {
    val items = Notes.list.filter { !it.read && it.kind == "series" && it.id.startsWith("ep:") }.take(2)
    if (items.isEmpty()) return
    var menu by remember { mutableStateOf<Note?>(null) }
    menu?.let { n -> Popup(tr("Nova epizoda"), { menu = null }) {
        OptionRow("Ukloni s Početne", R.drawable.lc_x) { Notes.markRead(n.id); menu = null }
    } }
    Column(Modifier.width(width), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { n ->
            Focusable(onClick = { openNote(nav, n) }, onMenu = { menu = n }, shape = Rnd(14.dp), normal = C.Rail, focused = C.CardHi, zoom = 1.03f,
                modifier = Modifier.fillMaxWidth().border(1.dp, C.Red.copy(alpha = 0.45f), Rnd(14.dp))) { f ->
                if (f) Box(Modifier.matchParentSize().border(2.dp, C.Red, Rnd(14.dp)))
                Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                    T("NOVA EPIZODA · SERIJA KOJU GLEDAŠ", 10.sp, C.Muted, FontWeight.SemiBold, maxLines = 1)
                    T(n.text, 14.sp, C.Text, FontWeight.Bold, maxLines = 2)
                }
            }
        }
    }
}


/** Home's "newest" rows, read once per playlist and kept until a refresh or new items from the provider. */
object HomeCache {
    var rev by mutableIntStateOf(0)
    val disc = java.util.concurrent.ConcurrentHashMap<String, Triple<List<Vod>, List<SeriesItem>, List<Channel>>>()
    fun clear() { if (disc.isNotEmpty()) { disc.clear(); rev++ } }
}


/** Small marks after Settings: a gauge (Lite = yellow, needle left · Full = green, needle right) and "S" while simple mode is on. */
@Composable
fun ModeBadges(simple: Boolean, onOpen: () -> Unit) {
    val lite = Perf.lite
    val c = if (lite) Color(0xFFFBBF24) else Color(0xFF22C55E)
    // Settings › Appearance › "Marks in the top bar": quiet icons like the rest of the bar (default) or the old coloured squares
    val boxed = Prefs.long("bar_badges", 0) == 1L
    @Composable fun badge(color: Color, content: @Composable BoxScope.() -> Unit) {
        if (boxed) Focusable(onClick = { if (!simple) onOpen() }, shape = Rnd(9.dp), normal = color.copy(alpha = 0.16f), focused = C.Red, zoom = 1.06f,
            modifier = Modifier.size(30.dp).border(1.dp, color.copy(alpha = 0.55f), Rnd(9.dp))) { _ -> content() }
        else Focusable(onClick = { if (!simple) onOpen() }, shape = CircleShape, normal = Color.Transparent, focused = C.CardHi, zoom = 1.06f,
            modifier = Modifier.size(30.dp)) { _ -> content() }
    }
    badge(c) {
        androidx.compose.foundation.Canvas(Modifier.size(18.dp).align(Alignment.Center)) {
            val w = size.width; val st = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.12f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawArc(c, 180f, 180f, false, topLeft = androidx.compose.ui.geometry.Offset(w * 0.1f, w * 0.25f),
                size = androidx.compose.ui.geometry.Size(w * 0.8f, w * 0.8f), style = st)
            val cx = w / 2; val cy = w * 0.65f
            val ex = if (lite) cx - w * 0.28f else cx + w * 0.28f
            drawLine(c, androidx.compose.ui.geometry.Offset(cx, cy), androidx.compose.ui.geometry.Offset(ex, cy - w * 0.3f), strokeWidth = w * 0.12f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
    // which app mode: S = simple (TV, Movies, Series only), A = normal with all options
    val mc = if (simple) Color(0xFF93C5FD) else Color(0xFFC4B5FD)
    badge(mc) { T(if (simple) "S" else "A", 13.sp, mc, FontWeight.ExtraBold, modifier = Modifier.align(Alignment.Center)) }  // i18n-ignore
}


/** The quiet landscape behind Home: visible on the right and at the bottom, blended into the theme colour on the left and top. */
@Composable
fun HomeScenery() {
    Box(Modifier.fillMaxSize()) {
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.fallback_bd3), null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize(), alpha = if (C.IsLight) 0.35f else 0.55f)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(C.Bg, C.Bg.copy(alpha = 0.85f), C.Bg.copy(alpha = 0.25f)))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(C.Bg.copy(alpha = 0.9f), Color.Transparent, C.Bg.copy(alpha = 0.55f)))))
    }
}


/** "Matches today" card: the two teams' shirts drawn in their colours (crest on the chest when ESPN has one), VS,
 *  the league on top, name and time (or live minute and score) below. Other sports: the sport's icon. */
@Composable
fun MatchKitCard(e: SportEvent, h: Dp, fr: FocusRequester?, keys: Modifier, onClick: () -> Unit) {
    val two = e.home != null && e.away != null && (e.sport == "football" || e.sport == "basketball" || e.sport == "handball" || e.sport == "volleyball" || e.sport == "hockey")
    Focusable(onClick = onClick, focusRequester = fr, shape = Rnd(14.dp), normal = C.Rail, focused = C.Red, zoom = 1.04f,
        modifier = Modifier.width(h * 1.6f).height(h).border(1.dp, C.Line, Rnd(14.dp)).then(keys)) { f ->
        Column(Modifier.fillMaxSize()) {
            T(flagEmoji(e.country) + "  " + e.league.uppercase(), 9.sp, if (f) C.OnRed else C.Muted, FontWeight.Bold, modifier = Modifier.padding(start = 10.dp, top = 6.dp, end = 10.dp))  // i18n-ignore
            Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (two) {
                    Shirt(e.home!!, h * 0.42f)
                    T("VS", 12.sp, if (f) C.OnRed else C.Muted, FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 12.dp))  // i18n-ignore
                    Shirt(e.away!!, h * 0.42f)
                } else T(sportEmoji(e.sport), 34.sp, C.Text)
            }
            Row(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.3f)).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                val name = if (e.home != null && e.away != null) e.home.name + " – " + e.away.name else e.title
                T(name, 12.sp, if (f) C.OnRed else C.Text, FontWeight.Bold, modifier = Modifier.weight(1f))
                val live = e.live
                val tag = if (live) "● " + listOfNotNull(e.clock, e.home?.score?.let { it + ":" + (e.away?.score ?: "") }).joinToString(" ")  // i18n-ignore
                          else java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(e.start))  // i18n-ignore
                // the time must be easy to read: a solid chip with big dark digits (red with minute and score when live)
                Box(Modifier.clip(Rnd(7.dp)).background(if (live) Color(0xFFEF4444) else Color(0xFFFBBF24)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    T(tag, 13.sp, if (live) Color.White else Color(0xFF111111), FontWeight.ExtraBold)
                }
            }
        }
    }
}

private fun sportEmoji(s: String) = when (s) { "f1" -> "🏎"; "tennis" -> "🎾"; "motogp" -> "🏍"; "hockey" -> "🏒"; else -> "🏅" }  // i18n-ignore

/** A shirt in the team's colours, with its crest on the chest (not in Lite: no picture). */
@Composable
private fun Shirt(t: Team, size: Dp) {
    val main = t.color ?: Color(0xFF64748B)
    val trim = t.color2?.takeIf { it != main } ?: if (main.luminance() > 0.6f) Color(0xFF1F2937) else Color.White
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val w = size.toPx(); val u = w / 74f
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(22 * u, 4 * u); lineTo(10 * u, 10 * u); lineTo(2 * u, 26 * u); lineTo(14 * u, 32 * u); lineTo(16 * u, 26 * u)
                lineTo(16 * u, 74 * u); lineTo(58 * u, 74 * u); lineTo(58 * u, 26 * u); lineTo(60 * u, 32 * u); lineTo(72 * u, 26 * u)
                lineTo(64 * u, 10 * u); lineTo(52 * u, 4 * u); quadraticBezierTo(37 * u, 14 * u, 22 * u, 4 * u); close()
            }
            drawPath(p, main)
            drawPath(p, trim, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2 * u))
            val collar = androidx.compose.ui.graphics.Path().apply { moveTo(22 * u, 4 * u); quadraticBezierTo(37 * u, 14 * u, 52 * u, 4 * u) }
            drawPath(collar, trim, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4 * u))
        }
        if (t.logo != null && !Perf.lite) coil.compose.AsyncImage(model = t.logo, contentDescription = null, modifier = Modifier.size(size * 0.3f).offset(y = size * 0.06f))
    }
}


/** Home › "Following · live": the matches of your teams, countries and matches, as cards like "Matches today":
 *  shirts with the score between them, yellow / red cards under each shirt, the last goal or card below.
 *  Live first, then the ones about to start; a finished one turns grey ("KRAJ") and goes after 15 minutes. */
@Composable
fun FollowRow(nav: Nav, h: Dp) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); tick++ } }
    // Home must not depend on a visit to Sport: when today's matches are not in memory (app start, playlist refresh), read them quietly here
    // and keep it fresh in the background while something is followed (every 15 min at most), so followed matches never drop off Home
    LaunchedEffect(Unit) {
        while (true) {
            val follows = MatchFollow.list().isNotEmpty() || Prefs.str("sport_teams").isNotBlank() || Prefs.str("sport_nations").isNotBlank()
            val empty = Sports.lastCombined[0] == null && SportCache.today == null
            if (follows && (empty || System.currentTimeMillis() - SportCache.todayAt > 15 * 60_000L)) {
                try { SportCache.today = Sports.day(0); SportCache.todayAt = System.currentTimeMillis(); SportCache.rev++ } catch (_: Exception) {}
            }
            delay(5 * 60_000L)
        }
    }
    val hidden = remember(tick, GoalNote.lastRev) { HomeHide.list() }
    val live = remember(GoalNote.followed.toList(), GoalNote.lastRev, tick) {
        // a finished match leaves Home at once (and is no longer followed)
        GoalNote.followed.filter { it.id !in hidden && it.live }
    }
    val soon = remember(tick, MyTeams.rev, MatchFollow.rev, SportCache.rev) {
        (Sports.lastCombined[0] ?: SportCache.today ?: emptyList()).filter { e ->
            e.state == "pre" && e.id !in hidden && (MatchFollow.has(e.id) || MyTeams.has(e.home?.name ?: "-") || MyTeams.has(e.away?.name ?: "-"))
        }.sortedBy { it.start }.take(6)
    }
    val ms = (live.sortedWith(compareBy({ it.over }, { -GoalNote.lastEventAt(it.id) })) + soon).distinctBy { it.id }
    if (ms.isEmpty()) return
    var menu by remember { mutableStateOf<SportEvent?>(null) }
    RowTitle("⭐ PRATIŠ · UŽIVO")  // i18n-ignore
    val w = remember(ms.size) { RowWrap(ms.size) }
    val scope = rememberCoroutineScope()
    LazyRow(state = w.state, horizontalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(end = 40.dp, top = 6.dp, bottom = 10.dp)) {
        itemsIndexed(ms, key = { _, e -> "fw" + e.id }) { i, e ->
            Focusable(onClick = { GoalNote.open(nav, "sp:" + e.id + ":" + (if (e.over) "end" else "live")) }, onMenu = { menu = e },  // i18n-ignore
                focusRequester = w.fr(i), shape = Rnd(14.dp), normal = C.Rail, focused = C.Red, zoom = 1.04f,
                modifier = Modifier.width(h * 2f).height(h).border(1.dp, if (e.live) C.Danger.copy(alpha = 0.6f) else C.Line, Rnd(14.dp)).then(w.keys(i, scope))) { f ->
                Column(Modifier.fillMaxSize().alpha(if (e.over) 0.55f else 1f)) {
                    Row(Modifier.fillMaxWidth().padding(start = 10.dp, top = 6.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        T(flagEmoji(e.country) + "  " + e.league.uppercase() + (if (e.live && e.clock != null) " · " + e.clock else ""), 9.sp,  // i18n-ignore
                            if (f) C.OnRed else C.Muted, FontWeight.Bold, modifier = Modifier.weight(1f))
                        val tag = when { e.live -> "● " + tr("UŽIVO"); e.over -> tr("KRAJ")  // i18n-ignore
                            else -> java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(e.start)) }  // i18n-ignore
                        Box(Modifier.clip(Rnd(6.dp)).background(if (e.live) Color(0xFFEF4444) else if (e.over) Color(0xFF475569) else Color.White).padding(horizontal = 6.dp, vertical = 1.dp)) {
                            T(tag, 10.sp, if (e.live || e.over) Color.White else Color(0xFF14121C), FontWeight.ExtraBold)
                        }
                    }
                    Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        @Composable fun side(t: Team?, s: String) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (t != null) Shirt(t, h * 0.38f)
                            Row(Modifier.height(12.dp).padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                e.details.filter { it.side == s && (it.kind.startsWith("yellow") || it.kind.startsWith("red")) }.take(4).forEach { d ->
                                    Box(Modifier.width(7.dp).height(10.dp).clip(Rnd(1.dp)).background(if (d.kind.startsWith("red")) Color(0xFFEF4444) else Color(0xFFFACC15)))
                                }
                            }
                        }
                        side(e.home, "h")
                        T(if (e.state == "pre") "VS" else (e.home?.score ?: "0") + " : " + (e.away?.score ?: "0"),  // i18n-ignore
                            if (e.state == "pre") 13.sp else 24.sp, if (f) C.OnRed else C.Text, FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 14.dp))
                        side(e.away, "a")
                    }
                    val last = e.details.lastOrNull { it.kind.startsWith("goal") || it.kind.startsWith("red") || it.kind.startsWith("yellow") }
                    val name = listOfNotNull(e.home?.name, e.away?.name).joinToString(" – ").ifBlank { e.title }
                    Row(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                        T((last?.let { (if (it.kind.startsWith("goal")) "⚽ " else if (it.kind.startsWith("red")) "🟥 " else "🟨 ") + it.minute + " " + it.player + " · " } ?: "") + name,  // i18n-ignore
                            11.sp, if (f) C.OnRed else C.Text, FontWeight.Bold)
                    }
                }
            }
        }
    }
    menu?.let { e ->
        Popup(listOfNotNull(e.home?.name, e.away?.name).joinToString(" – ").ifBlank { e.title }, { menu = null }) {
            OptionRow("Ukloni s Početne", R.drawable.lc_x, sub = tr("vraća se kad ga opet staviš na Prati")) { HomeHide.hide(e.id); menu = null }
            if (MatchFollow.has(e.id)) OptionRow("Ne prati utakmicu", R.drawable.lc_bell) { MatchFollow.toggle(e.id); GoalNote.followed.removeAll { it.id == e.id }; menu = null }
            listOfNotNull(e.home?.name, e.away?.name).filter { MyTeams.has(it) }.forEach { t ->
                OptionRow(trf("Ne prati tim: %s", t), R.drawable.lc_star) { MyTeams.toggle(t); menu = null }
            }
            OptionRow("Otvori u Sportu", R.drawable.lc_trophy) { menu = null; GoalNote.open(nav, "sp:" + e.id + ":open") }  // i18n-ignore
        }
    }
}
