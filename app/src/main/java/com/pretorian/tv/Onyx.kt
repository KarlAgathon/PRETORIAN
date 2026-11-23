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
        // the test runs on a quiet background thread, never on the main thread; the score is updated when it is done
        if (ms < 0 && (Prefs.long("perf_mode", -1) == 0L || forceMeasure) && !measuring) {
            measuring = true
            val app = c.applicationContext
            Thread {
                var best = Long.MAX_VALUE
                repeat(5) { val t0 = System.nanoTime(); var x = 0L; for (i in 0 until 3_000_000) x += (i xor (i shr 3)).toLong()
                    best = minOf(best, (System.nanoTime() - t0) / 1_000_000 + (x and 0)) }
                Prefs.put("perf_speed_ms", best)
                android.os.Handler(android.os.Looper.getMainLooper()).post { measuring = false; try { init(app) } catch (_: Exception) {} }
            }.apply { priority = Thread.MIN_PRIORITY; isDaemon = true }.start()
        }
        val speed = when { ms < 0 -> 7; ms < 12 -> 15; ms < 20 -> 11; ms < 35 -> 7; ms < 60 -> 3; else -> 0 }
        score = (ram + cpu + os + gpu + speed).coerceIn(0, 100)
        parts = listOf(tr("RAM") to listOf(ram, "/30").joinToString(""), tr("Procesor") to listOf(cpu, "/25").joinToString(""), "Android" to listOf(os, "/15").joinToString(""),  // i18n-ignore
            tr("Čip i grafika") to listOf(gpu, "/15").joinToString(""), tr("Test brzine") to listOf(speed, "/15").joinToString(""))
        // Lite below 3 GB RAM (approved); Lite looks the same as Full, it only saves memory and background work
        weak = am.isLowRamDevice || ramGb < 3.0 || score < 70
        // a new install (no playlist yet) always starts in Lite, whatever the box; the user switches to Full or Automatic later.
        // Boxes that already had PRETORIAN keep what they had.
        if (Prefs.long("perf_mode", -1) < 0) Prefs.put("perf_mode", if (Store.playlists(c).isEmpty()) 1L else 0L)
        mode = Prefs.long("perf_mode", 0).toInt().coerceIn(0, 2)
    }
    fun set(m: Int) { mode = m; Prefs.put("perf_mode", m.toLong()) }
    /** About › Box score › Measure again */
    private var forceMeasure = false
    @Volatile private var measuring = false
    fun remeasure(c: Context) { Prefs.remove("perf_speed_ms"); forceMeasure = true; init(c); forceMeasure = false }
    val MODES = listOf("Automatski", "Lite", "Full")
}

/** Pictures used when the provider has none for a film: calm landscapes that fit the dark look. */
private val FALLBACKS = intArrayOf(R.drawable.fallback_bd1, R.drawable.fallback_bd2, R.drawable.fallback_bd3,
    R.drawable.fallback_bd4, R.drawable.fallback_bd5, R.drawable.fallback_bd6)
fun fallbackFor(key: String): Int = FALLBACKS[(key.hashCode() and 0x7fffffff) % FALLBACKS.size]

/** Picture that falls back to a landscape when there is no image or it fails to load. */
@Composable
fun OnyxImage(url: String?, key: String, modifier: Modifier = Modifier, maxW: Int = 360, scale: ContentScale = ContentScale.Crop, highQuality: Boolean = false, wide: Boolean = false,
              align: androidx.compose.ui.Alignment = androidx.compose.ui.Alignment.Center, crossfade: Boolean = true) {
    val ctx = LocalContext.current
    val fb = painterResource(fallbackFor(key))
    if (url.isNullOrBlank()) Image(fb, null, modifier, contentScale = ContentScale.Crop)
    else {
        // never decode more pixels than the screen shows; Lite also uses half the memory per pixel
        val w = if (highQuality) maxW else if (Perf.lite || Lean.posters) maxW * 2 / 3 else maxW
        val req = remember(url, w, wide, crossfade) {
            coil.request.ImageRequest.Builder(ctx).data(url).size(w, if (wide) w * 9 / 16 else w * 3 / 2).allowRgb565(!highQuality && (Perf.lite || Lean.posters)).crossfade(!highQuality && crossfade).build()
        }
        // BUG 08: with crossfade the picture was sized against the placeholder (a big landscape drawable), so small posters came out tiny;
        // without crossfade, or with no placeholder, it is always sized by the real picture
        AsyncImage(model = req, contentDescription = null, contentScale = scale, alignment = align, modifier = modifier, error = fb, placeholder = if (crossfade) fb else null)
    }
}

/** One quiet picture behind the whole app (dark mountains). No film or channel pictures in the background:
 *  calmer to look at and lighter for the box. */
object Dusk {
    var image by mutableStateOf<Any?>(R.drawable.fallback_bd1)
    @Suppress("UNUSED_PARAMETER") fun set(url: String?, key: String) {}
}

/** First start of Media Center on this box: safe defaults (hardware decoder, adult/LGBT/trans categories hidden behind the PIN). */
fun onyxMigrate(c: Context) {
    val p = c.getSharedPreferences("pretorian", Context.MODE_PRIVATE)
    if (p.contains("mc_v")) return
    val e = p.edit().putInt("mc_v", 1).putString("tv_view", "list")
    if (!p.contains("playlists")) e.putBoolean("hide_adult_default", true).putLong("tech_decoder", 1L)
        .putBoolean("par_adult", true).putBoolean("par_lgbt", true).putBoolean("par_trans", true)
    e.apply()
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

/** Home's "newest" rows, read once per playlist and kept until a refresh or new items from the provider. */
object HomeCache {
    var rev by mutableIntStateOf(0)
    val disc = java.util.concurrent.ConcurrentHashMap<String, Triple<List<Vod>, List<SeriesItem>, List<Channel>>>()
    fun clear() { if (disc.isNotEmpty()) { disc.clear(); rev++ } }
}

