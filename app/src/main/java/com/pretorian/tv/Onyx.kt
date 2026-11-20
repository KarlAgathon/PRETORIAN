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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
fun OnyxImage(url: String?, key: String, modifier: Modifier = Modifier, maxW: Int = 360, scale: ContentScale = ContentScale.Crop, highQuality: Boolean = false) {
    val ctx = LocalContext.current
    val fb = painterResource(fallbackFor(key))
    if (url.isNullOrBlank()) Image(fb, null, modifier, contentScale = ContentScale.Crop)
    else {
        // never decode more pixels than the screen shows; Lite also uses half the memory per pixel
        val w = if (highQuality) maxW else if (Perf.lite || Lean.posters) maxW * 2 / 3 else maxW
        val req = remember(url, w) {
            coil.request.ImageRequest.Builder(ctx).data(url).size(w, w * 3 / 2).allowRgb565(!highQuality && (Perf.lite || Lean.posters)).crossfade(!Perf.lite && !highQuality).build()
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

/** Menus opened with a long OK on the top bar (and on "Recently added"). */
object TopMenus {
    var clearKind by mutableStateOf<String?>(null)
    var searchWhere by mutableStateOf(false)
}

/** Compact active-playlist status on Home. Warning colors: green >7 days, orange <=7, red <=2. */
@Composable
fun SubscriptionCard() {
    val ctx = LocalContext.current
    val pls = remember { Store.actives(ctx) }
    val p = pls.firstOrNull() ?: return
    val sec = p.exp.toLongOrNull()?.takeIf { it > 0 }
    val days = sec?.let { (it * 1000 - System.currentTimeMillis()) / 86_400_000L }
    val accent = when {
        days == null -> C.Green
        days <= 2 -> C.Danger
        days <= 7 -> Color(0xFFFFA726)
        else -> C.Green
    }
    Row(
        Modifier.widthIn(min = 170.dp, max = 230.dp).height(42.dp)
            .clip(Rnd(12.dp))
            .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.15f), C.Rail)))
            .border(1.dp, accent.copy(alpha = 0.55f), Rnd(12.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
        T(p.name, 12.sp, C.Text, FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
        if (days != null) {
            T(if (days < 0) "0" else trf("%s dana", days.toString()), 12.sp, accent, FontWeight.Bold, maxLines = 1)
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
        Look.theme = "ponoc"; Look.layout = 3
        p.edit().putInt("look_v", 7).putString("look_theme", "ponoc").putInt("look_layout", 3).apply()
    }
    if (p.getInt("look_v", 0) < 8) { Look.theme = "ponoc"; p.edit().putInt("look_v", 8).putString("tv_view", "list").putString("look_theme", "ponoc").apply() }   // 0.8.0: TV opens as the new channel list once
    // 1.0.63: New Dawn is the only layout; every box gets midnight blue once (black and white stays)
    if (p.getInt("look_v", 0) < 9) {
        val t = if (p.getString("look_theme", "") == "bw") "bw" else "ponoc"
        Look.theme = t; Look.layout = 3
        p.edit().putInt("look_v", 9).putString("look_theme", t).putInt("look_layout", 3).apply()
    }
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
    val ALL = listOf("cont" to "Nastavi gledati", "recent" to "Nedavno gledani kanali", "follow" to "Pratiš · uživo")
    var rev by mutableIntStateOf(0)
    /** "Matches today" starts off (it loads live scores) */
    /** weak box (score under 50): "Recently added" posters also start off; the user can turn them on in Settings › Home */
    // first start on every box: "Newly added" (new films, series, channels) and "Matches today" are off until switched on
    private fun offStr() = Prefs.str("home_off", "fresh,mylist,new,today")
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
