package com.pretorian.tv

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ------------------------------------------------------------------ Weather (Open-Meteo, approved) */

/** Weather in the status bar: Open-Meteo, no key. The place is chosen in Settings › Appearance; no place = no weather. */
object Weather {
    var temp by mutableStateOf<Int?>(null)
    var code by mutableIntStateOf(-1)
    private var at = 0L
    val place get() = Prefs.str("wx_name")
    fun set(name: String, lat: Double, lon: Double) {
        Prefs.put("wx_name", name); Prefs.put("wx_lat", lat.toString()); Prefs.put("wx_lon", lon.toString()); at = 0; temp = null
    }
    fun clear() { Prefs.put("wx_name", ""); temp = null; at = 0 }
    suspend fun refresh() = withContext(Dispatchers.IO) {
        val lat = Prefs.str("wx_lat").toDoubleOrNull(); val lon = Prefs.str("wx_lon").toDoubleOrNull()
        if (place.isBlank() || lat == null || lon == null) { temp = null; return@withContext }
        if (temp != null && System.currentTimeMillis() - at < 30 * 60_000L) return@withContext
        try {
            val j = JSONObject(Http.request("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code"))  // i18n-ignore
            val c = j.getJSONObject("current")
            temp = kotlin.math.round(c.getDouble("temperature_2m")).toInt(); code = c.optInt("weather_code", -1); at = System.currentTimeMillis()
        } catch (_: Exception) {}
    }
    data class Place(val name: String, val area: String, val lat: Double, val lon: Double)
    suspend fun search(q: String): List<Place> = withContext(Dispatchers.IO) {
        try {
            val j = JSONObject(Http.request("https://geocoding-api.open-meteo.com/v1/search?count=8&name=" + Net.enc(q)))  // i18n-ignore
            val a = j.optJSONArray("results") ?: return@withContext emptyList()
            (0 until a.length()).map { a.getJSONObject(it) }.map { o ->
                Place(o.optString("name"), listOf(o.optString("admin1"), o.optString("country")).filter { it.isNotBlank() }.joinToString(", "),
                    o.getDouble("latitude"), o.getDouble("longitude"))
            }
        } catch (_: Exception) { emptyList() }
    }
    fun icon(): Int = when (code) {
        0, 1 -> R.drawable.lc_sun
        else -> R.drawable.lc_cloud
    }
}

/** Settings row + popup: pick the weather place by name (Open-Meteo search). */
@Composable
fun WeatherPlaceRow() {
    var ask by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<Weather.Place>?>(null) }
    var name by remember { mutableStateOf(Weather.place) }
    val scope = rememberCoroutineScope()
    SetRow("Vrijeme – lokacija", "Prikazuje se na početnom ekranu (Open-Meteo)", value = name.ifBlank { tr("Isključeno") }) { ask = true }
    if (ask) TextDialog("Vrijeme – lokacija", "Grad", name, onDismiss = { ask = false }) { q ->
        ask = false
        if (q.isBlank()) { Weather.clear(); name = "" } else scope.launch { results = Weather.search(q) }
    }
    results?.let { list ->
        Popup("Vrijeme – lokacija", { results = null }) {
            if (list.isEmpty()) T("Ništa nije pronađeno", 15.sp, C.Soft)
            list.forEach { p ->
                OptionRow(p.name, R.drawable.lc_map_pin, sub = p.area) {
                    Weather.set(p.name, p.lat, p.lon); name = p.name; results = null
                    scope.launch { Weather.refresh() }
                }
            }
            OptionRow("Isključeno", R.drawable.lc_x) { Weather.clear(); name = ""; results = null }
        }
    }
}

/* ------------------------------------------------------------------ Home v2.0 */

private data class NavItem(val screen: Screen, val icon: Int, val label: String, val badge: Int = 0, val kind: String? = null)

/** Home (spec 01 v2.0): wordmark and one navigation column on the left (nothing below it),
 *  status bar on the right, then Continue Watching (0–3 cards) and Recently Watched TV (whole tiles only). */
@Composable
fun HomeV2(nav: Nav) {
    @Suppress("UNUSED_VARIABLE") val r = NewContent.rev + Follow.rev + Watch.rev + Recent.rev
    val items = listOf(
        NavItem(Screen.Search, R.drawable.lc_search, "Pretraga"),
        NavItem(Screen.Home, R.drawable.lc_house, "Početna"),
        NavItem(Screen.Tv, R.drawable.lc_tv, "TV"),
        NavItem(Screen.Movies, R.drawable.lc_film, "Filmovi", NewContent.count("vod"), "vod"),
        NavItem(Screen.Series, R.drawable.lc_layers, "Serije", NewContent.count("series") + Follow.fresh().size, "series"),
        NavItem(Screen.Sports, R.drawable.lc_trophy, "Sport"),
        NavItem(Screen.Catchup, R.drawable.lc_history, "Catch-up"),
        NavItem(Screen.Settings, R.drawable.lc_settings, "Postavke", if (Updater.available != null) 1 else 0, "system"),
    )
    val frs = remember { items.map { FocusRequester() } }
    var clear by remember { mutableStateOf<NavItem?>(null) }
    DisposableEffect(Unit) { onDispose { Chrome.inRail = false } }
    LaunchedEffect(Unit) {
        val i = items.indexOfFirst { it.screen == (Dawn.last ?: Screen.Home) }.let { if (it < 0) 1 else it }
        delay(120); frs[i].tryFocus()
    }
    LaunchedEffect(Unit) { Weather.refresh() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val navW = (maxWidth * 0.17f).coerceIn(200.dp, 300.dp)
        val mh = maxHeight
        Row(Modifier.fillMaxSize().padding(start = 40.dp, end = 48.dp, top = 28.dp, bottom = 32.dp), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            // left: wordmark + navigation
            Column(Modifier.width(navW).fillMaxHeight().onFocusChanged { Chrome.inRail = it.hasFocus }) {
                Box(Modifier.height(46.dp).padding(start = 14.dp), contentAlignment = Alignment.CenterStart) {
                    T("MEDIA CENTER", 19.sp, Color.White, FontWeight.Bold)  // i18n-ignore
                }
                Spacer(Modifier.height(28.dp))
                val rowH = ((mh - 28.dp - 32.dp - 46.dp - 28.dp - 6.dp * (items.size - 1)) / items.size).coerceIn(34.dp, 52.dp)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items.forEachIndexed { i, it ->
                        Focusable(
                            modifier = Modifier.fillMaxWidth().height(rowH),
                            onClick = {
                                if (it.screen == Screen.Home) return@Focusable
                                Chrome.inRail = false; Dawn.last = it.screen; nav.root(it.screen)
                            },
                            onMenu = if (it.badge > 0 && it.kind != null && it.kind != "system") ({ clear = it }) else null,
                            focusRequester = frs[i], shape = Rnd(rowH / 2), normal = Color.Transparent, focused = Dawn.pill, zoom = 1f,
                        ) { f ->
                            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Ico(it.icon, 24.dp, if (f || it.screen == Screen.Home) Color.White else Silver.TextSoft)
                                T(it.label, 17.sp, if (f || it.screen == Screen.Home) Color.White else Silver.TextSoft,
                                    if (f) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                                if (it.badge > 0) Box(Modifier.size(8.dp).clip(Pill).background(Color.White))
                            }
                        }
                    }
                }
            }
            // right: status bar, continue watching, recently watched TV
            Column(Modifier.weight(1f).fillMaxHeight()) {
                HomeStatus(nav, Modifier.fillMaxWidth().height(46.dp))
                Spacer(Modifier.height(28.dp))
                HomeContent(nav, Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
    clear?.let { item ->
        Popup(item.label, onDismiss = { clear = null }) {
            OptionRow("Obriši obavještenja", R.drawable.lc_trash_2) {
                if (item.kind == "series") { NewContent.clear("series"); Follow.clearFresh() } else if (item.kind != null) NewContent.clear(item.kind)
                clear = null
            }
        }
    }
}

/** weather • time • playlist status | bell — aligned to the right edge of the content. */
@Composable
private fun HomeStatus(nav: Nav, modifier: Modifier) {
    val ctx = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(15_000) } }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.End)) {
        val dot: @Composable () -> Unit = { T("•", 15.sp, Silver.TextSoft) }
        Weather.temp?.let { t ->
            Ico(Weather.icon(), 18.dp, Color.White)
            T("$t°", 16.sp, Color.White, FontWeight.SemiBold)  // i18n-ignore
            dot()
        }
        T(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), 16.sp, Color.White, FontWeight.SemiBold)
        Store.actives(ctx).firstOrNull()?.let { p ->
            dot()
            val sec = p.exp.toLongOrNull()?.takeIf { it > 0 }
            val days = sec?.let { ((it * 1000 - now.time) / 86_400_000L).toInt() }
            T(p.name, 15.sp, Silver.TextSoft, maxLines = 1, modifier = Modifier.padding(end = 0.dp))
            if (days != null) {
                val col = when { days <= 2 -> Silver.Error; days <= 7 -> Silver.Warning; else -> Silver.TextSoft }
                T(if (days < 0) tr("Istekla") else trf("još %s dana", days), 15.sp, col)
            }
        }
        Box(Modifier.width(1.dp).height(20.dp).background(Silver.Border))
        NoteBell(nav)
    }
}

@Composable
private fun HomeContent(nav: Nav, modifier: Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cont = remember(Watch.rev) { Watch.continueList(ctx).filter { !ContinueHidden.hidden(it.url, it.ts) }.take(3) }
    val recent = remember(Recent.rev) { Recent.list() }
    var menu by remember { mutableStateOf<Progress?>(null) }
    BoxWithConstraints(modifier) {
        val h = maxHeight
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (cont.isNotEmpty()) {
                T("Nastavi gledati", 18.sp, Color.White, FontWeight.SemiBold)
                val cardH = if (recent.isEmpty()) h - 40.dp else (h * 0.56f)
                Row(Modifier.fillMaxWidth().height(cardH), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val open: (Progress) -> Unit = { resumeContinue(ctx, nav, it, false) }
                    when (cont.size) {
                        1 -> ContinueCard(cont[0], Modifier.fillMaxSize(), true, open) { menu = it }
                        2 -> {
                            ContinueCard(cont[0], Modifier.weight(0.6f).fillMaxHeight(), true, open) { menu = it }
                            ContinueCard(cont[1], Modifier.weight(0.4f).fillMaxHeight(), false, open) { menu = it }
                        }
                        else -> {
                            ContinueCard(cont[0], Modifier.weight(0.6f).fillMaxHeight(), true, open) { menu = it }
                            Column(Modifier.weight(0.4f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                ContinueCard(cont[1], Modifier.weight(1f).fillMaxWidth(), false, open) { menu = it }
                                ContinueCard(cont[2], Modifier.weight(1f).fillMaxWidth(), false, open) { menu = it }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (recent.isNotEmpty()) {
                T("Nedavno gledani TV kanali", 18.sp, Color.White, FontWeight.SemiBold)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val gap = 16.dp
                    val tileW = if (cont.isEmpty()) 220.dp else 170.dp
                    val n = ((maxWidth + gap) / (tileW + gap)).toInt().coerceAtLeast(1)
                    val w = (maxWidth - gap * (n - 1)) / n
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        recent.take(n).forEach { it ->
                            Focusable(onClick = { scope.launch { val pid = it.pid; if (pid != null) playInGroup(ctx, nav, it.ch, pid) else playChannels(nav, listOf(it.ch), 0) } },
                                shape = Rnd(14.dp), normal = Color(0x8C14181C), focused = Color(0x8C14181C), focusBorderColor = Color.White, borderColor = Color(0x1FFFFFFF),
                                zoom = 1.05f, modifier = Modifier.width(w).height(w * 0.6f)) { f ->
                                RecentTile(it.ch, f)
                            }
                        }
                    }
                }
            }
            if (cont.isEmpty() && recent.isEmpty()) Box(Modifier.fillMaxSize().glassCard(), contentAlignment = Alignment.Center) {
                T("Ovdje će se pojaviti ono što gledaš.", 16.sp, Silver.TextSoft)
            }
        }
    }
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

@Composable
private fun RecentTile(ch: Channel, f: Boolean) {
    var failed by remember(ch.logo) { mutableStateOf(ch.logo.isNullOrBlank()) }
    Box(Modifier.fillMaxSize().padding(14.dp), contentAlignment = Alignment.Center) {
        if (!failed) AsyncImage(model = ch.logo, contentDescription = ch.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(), onError = { failed = true })
        else T(shortName(ch.name), 18.sp, if (f) Color.White else Silver.TextSoft, FontWeight.Bold, maxLines = 2)
    }
}

/** "HRT 1 HD" → "HRT 1"; the short channel name in capitals for a tile without a logo. */
fun shortName(n: String): String = n.substringAfterLast('|').substringAfterLast(':').trim()
    .replace(Regex("\\b(FHD|UHD|HD|SD|4K|HEVC|H265|RAW)\\b", RegexOption.IGNORE_CASE), "").trim().uppercase().ifBlank { "TV" }

@Composable
private fun ContinueCard(pr: Progress, modifier: Modifier, big: Boolean, onOpen: (Progress) -> Unit, onMenu: (Progress) -> Unit) {
    val series = pr.series != null
    val name = cleanTitle(pr.series ?: pr.title).first
    val backdrop by produceState<String?>(null, pr.url) { value = try { Tmdb.find(pr.series ?: pr.title, series)?.backdrop } catch (_: Exception) { null } }
    Focusable(onClick = { onOpen(pr) }, onMenu = { onMenu(pr) }, shape = Rnd(16.dp), normal = Color(0x8C14181C), focused = Color(0x8C14181C),
        focusBorderColor = Color.White, borderColor = Color(0x1FFFFFFF), zoom = 1.03f, modifier = modifier) { _ ->
        OnyxImage(backdrop ?: pr.poster, "cwh:" + pr.url + (backdrop != null), Modifier.fillMaxSize(), maxW = if (big) 1280 else 780)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color(0x00000000), 1f to Color(0xE6000000))))
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            T(name, if (big) 22.sp else 17.sp, Color.White, FontWeight.Bold, maxLines = 1)
            val line = listOfNotNull(
                if (pr.season != null && pr.episode != null) "S%02dE%02d".format(pr.season, pr.episode) else null,  // i18n-ignore
                if (pr.dur > 0) trf("još %s min", ((pr.dur - pr.pos) / 60_000).toInt().coerceAtLeast(1)) else null,
            ).joinToString("  •  ")  // i18n-ignore
            if (line.isNotBlank()) T(line, 14.sp, Silver.TextSoft, maxLines = 1)
            Box(Modifier.fillMaxWidth().height(3.dp).clip(Rnd(2.dp)).background(Color(0x40FFFFFF))) {
                Box(Modifier.fillMaxWidth(pr.fraction.coerceAtLeast(0.02f)).fillMaxHeight().background(Color.White))
            }
        }
    }
}
