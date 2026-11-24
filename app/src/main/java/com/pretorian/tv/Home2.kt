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
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.focus.focusRequester
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
    data class Place(val name: String, val area: String, val lat: Double, val lon: Double, val cc: String = "", val zip: String = "")

    /** FIX 11: where the box roughly is (country and coordinates from its internet address, kept a week; else the device region) */
    private suspend fun home(): Triple<String, Double?, Double?> = withContext(Dispatchers.IO) {
        val saved = Prefs.str("wx_home")
        if (saved.isNotBlank() && System.currentTimeMillis() - Prefs.long("wx_home_at") < 7 * 86_400_000L) {
            val p = saved.split('|'); return@withContext Triple(p[0], p.getOrNull(1)?.toDoubleOrNull(), p.getOrNull(2)?.toDoubleOrNull())
        }
        try {
            val j = JSONObject(Http.request("https://ipwho.is/?fields=success,country_code,latitude,longitude"))  // i18n-ignore
            if (j.optBoolean("success", false)) {
                val cc = j.optString("country_code").uppercase(); val la = j.optDouble("latitude"); val lo = j.optDouble("longitude")
                Prefs.put("wx_home", "$cc|$la|$lo"); Prefs.put("wx_home_at", System.currentTimeMillis())
                return@withContext Triple(cc, la, lo)
            }
        } catch (_: Exception) {}
        Triple(Locale.getDefault().country.uppercase(), null, null)
    }

    /** a place by name or by postal code; names without umlauts work too; the nearest places to this box come first */
    suspend fun search(q: String): List<Place> = withContext(Dispatchers.IO) {
        val query = q.trim()
        val (cc, hla, hlo) = home()
        fun ask(name: String, country: String?): List<Place> = try {
            val j = JSONObject(Http.request("https://geocoding-api.open-meteo.com/v1/search?count=20&language=" + Look.lang + "&name=" + Net.enc(name) +  // i18n-ignore
                (if (!country.isNullOrBlank()) "&countryCode=$country" else "")))
            val a = j.optJSONArray("results")
            if (a == null) emptyList() else (0 until a.length()).map { a.getJSONObject(it) }.map { o ->
                val zips = o.optJSONArray("postcodes")?.let { z -> (0 until z.length()).map { z.optString(it) } } ?: emptyList()
                Place(o.optString("name"), listOf(o.optString("admin1"), o.optString("country")).filter { it.isNotBlank() }.joinToString(", "),
                    o.getDouble("latitude"), o.getDouble("longitude"), o.optString("country_code").uppercase(),
                    zips.firstOrNull { it == name } ?: zips.firstOrNull().orEmpty())
            }
        } catch (_: Exception) { emptyList() }
        val zip = query.all { it.isDigit() || it == ' ' || it == '-' } && query.any { it.isDigit() }
        val out = LinkedHashMap<String, Place>()
        fun addAll(l: List<Place>) = l.forEach { out.putIfAbsent("${it.name}|${it.lat}|${it.lon}", it) }
        if (zip) {
            addAll(ask(query, cc))                      // your own country first, then everywhere else
            addAll(ask(query, null))
        } else {
            addAll(ask(query, null))
            // typed without umlauts: ue → ü, oe → ö, ae → ä, ss → ß
            val folded = query.replace("ue", "ü").replace("oe", "ö").replace("ae", "ä").replace("Ue", "Ü").replace("Oe", "Ö").replace("Ae", "Ä")
            if (folded != query) addAll(ask(folded, null))
            if (out.isEmpty() && query.contains("ss")) addAll(ask(query.replace("ss", "ß"), null))
        }
        fun dist(p: Place): Double {
            if (hla == null || hlo == null || hla.isNaN() || hlo.isNaN()) return 0.0
            val dLat = Math.toRadians(p.lat - hla); val dLon = Math.toRadians(p.lon - hlo)
            val h = Math.sin(dLat / 2).let { it * it } + Math.cos(Math.toRadians(hla)) * Math.cos(Math.toRadians(p.lat)) * Math.sin(dLon / 2).let { it * it }
            return 2 * 6371 * Math.asin(Math.sqrt(h))
        }
        out.values.withIndex().sortedWith(compareBy({ if (it.value.cc == cc) 0 else 1 }, { dist(it.value) }, { it.index })).map { it.value }.take(20)
    }
    /** WMO weather code → a short condition (Open-Meteo) */
    fun condition(): String = when (code) {
        0 -> "Vedro"; 1 -> "Pretežno vedro"; 2 -> "Djelimično oblačno"; 3 -> "Oblačno"; 45, 48 -> "Magla"
        51, 53, 55, 56, 57 -> "Rosulja"; 61, 63, 65, 66, 67 -> "Kiša"; 71, 73, 75, 77 -> "Snijeg"
        80, 81, 82 -> "Pljusak"; 85, 86 -> "Snijeg"; 95, 96, 99 -> "Grmljavina"; else -> ""
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
    if (ask) TextDialog("Vrijeme – lokacija", "Grad ili poštanski broj", "", onDismiss = { ask = false }) { q ->
        ask = false
        if (q.isBlank()) { Weather.clear(); name = "" } else scope.launch { results = Weather.search(q) }
    }
    results?.let { list ->
        Popup("Vrijeme – lokacija", { results = null }) {
            if (list.isEmpty()) T("Ništa nije pronađeno", 15.sp, C.Soft)
            list.forEach { p ->
                OptionRow(p.name, R.drawable.lc_map_pin, sub = listOf(p.zip, p.area).filter { it.isNotBlank() }.joinToString(" · ")) {
                    Weather.set(p.name, p.lat, p.lon); name = p.name; results = null
                    scope.launch { Weather.refresh() }
                }
            }
            OptionRow("Isključeno", R.drawable.lc_x) { Weather.clear(); name = ""; results = null }
        }
    }
}

/* ------------------------------------------------------------------ Home (final spec 06) */

/** The element of Home that had the focus: Back from a section, a title or the player lands on it again. */
object HomeMem { var key: String? = null }

private data class NavItem(val screen: Screen, val icon: Int, val label: String, val badge: Int = 0, val kind: String? = null)

/** One card of the upper section: a title in Continue Watching (with real progress) or a recently added film (no progress). */
private class HomeCard(val title: String, val series: Boolean, val image: String?, val pr: Progress?, val row: CatalogDb.Row?)

/**
 * Home (final spec 06): the PRETORIAN ROOM background, a strict two-column layout — wordmark and navigation on the left
 * (nothing under Settings), status bar, the upper media section (Continue Watching, else Recently added movies; 1 / 2 / 3
 * cards in one fixed frame) and the TV row (Recently watched TV, else Recently added TV channels; whole tiles only) on the right.
 * All positions are fractions of the screen, so the alignments hold on every TV.
 */
@Composable
fun HomeV2(nav: Nav) {
    @Suppress("UNUSED_VARIABLE") val r = NewContent.rev + Follow.rev + Watch.rev + Recent.rev
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val items = listOf(
        NavItem(Screen.Search, R.drawable.lc_search, "Pretraga"),
        NavItem(Screen.Home, R.drawable.lc_house, "Početna"),
        NavItem(Screen.Tv, R.drawable.lc_tv, "TV"),
        NavItem(Screen.Movies, R.drawable.lc_film, "Filmovi", NewContent.count("vod"), "vod"),
        NavItem(Screen.Series, R.drawable.lc_layers, "Serije", NewContent.count("series") + Follow.fresh().size, "series"),
        NavItem(Screen.Sports, R.drawable.lc_trophy, "Sport"),
        NavItem(Screen.Settings, R.drawable.lc_settings, "Postavke", if (Updater.available != null) 1 else 0, "system"),
    )
    val frs = remember { HashMap<String, FocusRequester>() }
    fun fr(k: String) = frs.getOrPut(k) { FocusRequester() }
    var clear by remember { mutableStateOf<NavItem?>(null) }
    var menu by remember { mutableStateOf<Progress?>(null) }
    DisposableEffect(Unit) { onDispose { Chrome.inRail = false } }
    LaunchedEffect(Unit) { Weather.refresh() }

    // ---- upper section: Continue Watching (one card per series), else the newest films of the catalogue
    val p = remember { Store.active(ctx) }
    val cont = remember(Watch.rev) {
        Watch.continueList(ctx).filter { !ContinueHidden.hidden(it.url, it.ts) }.distinctBy { it.series ?: it.url }.take(3)
    }
    val newest by produceState(emptyList<CatalogDb.Row>(), p?.id, Import.finished, cont.isEmpty()) {
        value = if (p == null || cont.isNotEmpty()) emptyList() else withContext(Dispatchers.IO) {
            val rows = CatalogDb.newest(p.id, "vod", 40)
            val ok = Parental.catalog(p, "vod", rows.map { JSONObject().put("c", it.c).put("n", it.n).put("id", it.id) }).map { it.optString("id") }.toSet()
            rows.filter { it.id in ok }.take(3)
        }
    }
    val cards = if (cont.isNotEmpty()) cont.map { HomeCard(it.series ?: it.title, it.series != null, it.poster, it, null) }
                else newest.map { HomeCard(it.n, false, it.i, null, it) }
    val upperTitle = if (cont.isNotEmpty()) "Nastavi gledati" else "Nedavno dodani filmovi"

    // ---- lower section: recently watched channels, else the newest channels of the playlist
    val recent = remember(Recent.rev) { Recent.list().map { it.ch to it.pid }.distinctBy { it.first.url } }
    val newCh by produceState(emptyList<Channel>(), p?.id, recent.isEmpty()) {
        value = if (p == null || recent.isNotEmpty()) emptyList() else try {
            val all = Repo.allChannels(p)
            // the newest first: the highest stream id; a list without ids (M3U) in reverse import order
            if (all.isNotEmpty() && all.all { it.id.toLongOrNull() != null }) all.sortedByDescending { it.id.toLong() }.take(16) else all.reversed().take(16)
        } catch (_: Exception) { emptyList() }
    }
    val tiles: List<Pair<Channel, String?>> = if (recent.isNotEmpty()) recent else newCh.map { it to p?.id }
    val lowerTitle = if (recent.isNotEmpty()) "Nedavno gledani TV kanali" else "Nedavno dodani TV kanali"

    // ---- first focus: the element that had it before, else Home
    LaunchedEffect(cards.size, tiles.size) {
        delay(140)
        val k = HomeMem.key?.takeIf { it in frs } ?: "nav:1"
        frs[k]?.tryFocus()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val W = maxWidth; val H = maxHeight
        val navX = W * 0.025f; val navW = W * 0.145f
        val cx = W * 0.19f; val cw = W * 0.97f - cx
        val headTop = H * 0.165f; val headH = H * 0.07f
        val cardsTop = H * 0.245f; val cardsH = H * 0.44f
        val tvHeadTop = H * 0.715f; val tilesTop = H * 0.785f; val tilesMaxH = H * 0.13f
        val rowH = H * 0.075f; val rowStep = H * 0.084f
        val bandTop = H * 0.03f; val bandH = H * 0.09f

        // ---- branding (final rule 12): only the Spartan helmet, lightly engraved into the graphite — no text, no line, no box.
        // Two copies of the same static picture: a dark one 1 dp lower-right (the cut), the grey one on top at low opacity.
        Box(Modifier.offset(navX, bandTop).width(navW).height(bandH), contentAlignment = Alignment.Center) {
            val helmet = androidx.compose.ui.res.painterResource(R.drawable.pretorian_spartan_helmet)
            androidx.compose.foundation.Image(helmet, null, Modifier.height(44.dp).offset(1.dp, 1.dp), contentScale = ContentScale.Fit, alpha = 0.45f,
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.Black, androidx.compose.ui.graphics.BlendMode.SrcIn))
            androidx.compose.foundation.Image(helmet, null, Modifier.height(44.dp), contentScale = ContentScale.Fit, alpha = 0.34f,
                colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }))
        }
        // ---- status bar: same band as the wordmark, its right end (the bell) on the content's right edge
        HomeStatus(nav, Modifier.offset(cx, bandTop).width(cw).height(bandH))

        // ---- navigation: SEARCH centred on the heading band, HOME's top on the cards' top
        Column(Modifier.offset(navX, 0.dp).width(navW).fillMaxHeight().onFocusChanged { Chrome.inRail = it.hasFocus }) {
            items.forEachIndexed { i, it ->
                val top = if (i == 0) headTop + (headH - rowH) / 2 else cardsTop + rowStep * (i - 1)
                val prevBottom = if (i == 0) 0.dp else if (i == 1) headTop + (headH - rowH) / 2 + rowH else cardsTop + rowStep * (i - 2) + rowH
                Spacer(Modifier.height((top - prevBottom).coerceAtLeast(0.dp)))
                val key = "nav:$i"
                Focusable(
                    modifier = Modifier.fillMaxWidth().height(rowH),
                    onClick = {
                        HomeMem.key = key
                        if (it.screen == Screen.Home) return@Focusable
                        if (it.screen == Screen.Search) { SearchSession.reset(); SearchOnly.only = null; SearchMem.q = "" }   // a new Search always starts empty, searching everything
                        Chrome.inRail = false; Dawn.last = it.screen; nav.root(it.screen)
                    },
                    onMenu = if (it.badge > 0 && it.kind != null && it.kind != "system") ({ clear = it }) else null,
                    onFocus = { HomeMem.key = key },
                    focusRequester = fr(key), shape = Rnd(12.dp), normal = Color.Transparent, focused = Dawn.pill, zoom = 1f,
                ) { f ->
                    val on = f || it.screen == Screen.Home
                    Row(Modifier.fillMaxSize().padding(start = 14.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Ico(it.icon, 21.dp, if (on) Color.White else Color(0xFFB8BEC6))
                        T(tr(it.label).uppercase(), 15.sp, if (on) Color.White else Color(0xFFB8BEC6), if (f) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f))
                        if (it.badge > 0) Box(Modifier.size(7.dp).clip(Pill).background(Color.White))
                    }
                    // the small white mark near the right inner edge of the focused entry
                    if (f) Box(Modifier.align(Alignment.CenterEnd).padding(end = 7.dp).width(2.dp).fillMaxHeight(0.42f).clip(Pill).background(Color.White))
                }
            }
        }

        // ---- upper media section (fixed frame; hidden with its heading when there is nothing real to show)
        if (cards.isNotEmpty()) {
            Box(Modifier.offset(cx, headTop).width(cw).height(headH), contentAlignment = Alignment.CenterStart) {
                T(tr(upperTitle).uppercase(), 19.sp, Color(0xFFE6E8EB), FontWeight.Medium)
            }
            val gap = 12.dp
            Row(Modifier.offset(cx, cardsTop).width(cw).height(cardsH), horizontalArrangement = Arrangement.spacedBy(gap)) {
                val click: (HomeCard) -> Unit = { c -> openHomeCard(ctx, nav, c.pr, c.row) }
                when (cards.size) {
                    1 -> HomeMediaCard(cards[0], Modifier.fillMaxSize(), true, "card:0", fr("card:0"), click) { menu = it }
                    2 -> {
                        HomeMediaCard(cards[0], Modifier.weight(0.6f).fillMaxHeight(), true, "card:0", fr("card:0"), click) { menu = it }
                        HomeMediaCard(cards[1], Modifier.weight(0.4f).fillMaxHeight(), false, "card:1", fr("card:1"), click) { menu = it }
                    }
                    else -> {
                        HomeMediaCard(cards[0], Modifier.weight(0.6f).fillMaxHeight(), true, "card:0", fr("card:0"), click) { menu = it }
                        Column(Modifier.weight(0.4f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                            HomeMediaCard(cards[1], Modifier.weight(1f).fillMaxWidth(), false, "card:1", fr("card:1"), click) { menu = it }
                            HomeMediaCard(cards[2], Modifier.weight(1f).fillMaxWidth(), false, "card:2", fr("card:2"), click) { menu = it }
                        }
                    }
                }
            }
        }

        // ---- TV row: whole tiles only, same left and right edges as the cards
        if (tiles.isNotEmpty()) {
            Box(Modifier.offset(cx, tvHeadTop).width(cw).height(H * 0.06f), contentAlignment = Alignment.CenterStart) {
                T(tr(lowerTitle).uppercase(), 17.sp, Color(0xFFE6E8EB), FontWeight.Medium)
            }
            val gap = 10.dp
            val n = ((cw + gap) / (76.dp + gap)).toInt().coerceIn(3, 14).coerceAtMost(tiles.size.coerceAtLeast(1))
            val slots = ((cw + gap) / (76.dp + gap)).toInt().coerceIn(3, 14)
            val tw = (cw - gap * (slots - 1)) / slots
            val th = (tw * 0.7f).coerceAtMost(tilesMaxH)
            Row(Modifier.offset(cx, tilesTop).width(cw), horizontalArrangement = Arrangement.spacedBy(gap)) {
                val list = tiles.take(n).map { it.first }
                tiles.take(n).forEachIndexed { i, (ch, _) ->
                    val key = "tile:$i"
                    Focusable(onClick = { HomeMem.key = key; playChannels(nav, list, i) }, onFocus = { HomeMem.key = key }, focusRequester = fr(key),
                        shape = Rnd(12.dp), normal = Color(0x8C14181C), focused = Color(0xB314181C), focusBorderColor = Color(0xE6FFFFFF),
                        borderColor = Color(0x1FFFFFFF), zoom = 1.06f, modifier = Modifier.width(tw).height(th)) { _ ->
                        HomeTile(ch)
                    }
                }
            }
        }
    }
    clear?.let { item ->
        Popup(item.label, onDismiss = { clear = null }) {
            OptionRow("Obriši obavještenja", R.drawable.lc_trash_2) {
                item.kind?.let { Notes.clearKind(it) }   // FIX 2: only the notices; "Nedavno dodano" keeps its titles
                clear = null
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
    @Suppress("UNUSED_VARIABLE") val sc = scope
}

/** OK on a Home card opens the title's own view (never plays at once): a recently added film → its movie view;
 *  Continue Watching → the movie / series view with Resume (the series view waits on that episode). */
internal fun openHomeCard(ctx: android.content.Context, nav: Nav, pr: Progress?, row: CatalogDb.Row?) {
    VodMemory.stack.remove("vod"); VodMemory.stack.remove("series")
    if (row != null) { nav.push(Screen.MovieDetail(Vod(row.id, row.n, row.i, row.r, row.u))); return }
    pr ?: return
    val pid = pr.pid.ifBlank { Store.active(ctx)?.id ?: "" }
    val name = pr.series ?: pr.title
    val words = Sports.norm(cleanTitle(name).first).split(' ').filter { it.length >= 2 }.take(3)
    val hits = try { CatalogDb.search(listOf(pid), if (pr.series != null) "series" else "vod", words, 60) } catch (_: Exception) { emptyList() }
    if (pr.series != null) {
        val h = hits.firstOrNull { it.n == pr.series } ?: hits.firstOrNull { cleanTitle(it.n).first.equals(cleanTitle(pr.series).first, true) }
        if (h != null) {
            if (pr.season != null && pr.episode != null) { SeriesJump.id = h.id; SeriesJump.season = pr.season; SeriesJump.num = pr.episode }
            nav.push(Screen.SeriesDetail(SeriesItem(h.id, h.n, h.i, null, h.r))); return
        }
    } else {
        val h = hits.firstOrNull { it.u == pr.url } ?: hits.firstOrNull { it.n == pr.title }
        if (h != null) { nav.push(Screen.MovieDetail(Vod(h.id, h.n, h.i, h.r, h.u.ifBlank { pr.url }))); return }
    }
    // the title is no longer in the catalogue: resume it directly
    resumeContinue(ctx, nav, pr, false)
}

/** weather icon + temperature + condition • time • Playlist: status (Exp. …) | bell */
@Composable
private fun HomeStatus(nav: Nav, modifier: Modifier) {
    val ctx = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(15_000) } }
    val c = Color(0xFFDDE1E6)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
        val dot: @Composable () -> Unit = { T("•", 14.sp, Silver.TextSoft) }  // i18n-ignore
        Weather.temp?.let { t ->
            Ico(Weather.icon(), 18.dp, Color.White)
            T(t.toString() + "°C " + tr(Weather.condition()), 14.sp, c, maxLines = 1)
            dot()
        }
        if (LookOpts.clockOn) T(LookOpts.time(ctx, now), 14.sp, c)
        Store.actives(ctx).firstOrNull()?.let { p -> dot(); PlaylistStatus(p, now.time) }
        Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(22.dp).background(Color(0x33FFFFFF)))
        NoteBell(nav)
    }
}

@Composable
private fun HomeTile(ch: Channel) {
    val src = rememberLogo(ch.logo, ch.name, ch.group)
    Box(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        if (src.url != null) AsyncImage(model = src.request, contentDescription = ch.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(), onError = { src.failed() })
        // no logo: the channel's name in clean white text (never a broken image or a TV icon)
        else T(ch.name.substringAfterLast('|').trim().ifBlank { ch.name }, 12.sp, Color.White, FontWeight.SemiBold, maxLines = 2, align = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** "HRT 1 HD" → "HRT 1"; the short channel name in capitals. */
fun shortName(n: String): String = n.substringAfterLast('|').substringAfterLast(':').trim()
    .replace(Regex("\\b(FHD|UHD|HD|SD|4K|HEVC|H265|RAW)\\b", RegexOption.IGNORE_CASE), "").trim().uppercase().ifBlank { "TV" }

@Composable
private fun HomeMediaCard(c: HomeCard, modifier: Modifier, big: Boolean, memKey: String, fr: FocusRequester, onOpen: (HomeCard) -> Unit, onMenu: (Progress) -> Unit) {
    val pr = c.pr
    val meta by produceState<Meta?>(null, c.title) { value = try { Tmdb.find(c.title, c.series) } catch (_: Exception) { null } }
    val (name, y0) = cleanTitle(c.title)
    val year = meta?.year?.take(4) ?: y0
    Focusable(onClick = { HomeMem.key = memKey; onOpen(c) }, onMenu = if (pr != null) ({ onMenu(pr) }) else null,
        onFocus = { HomeMem.key = memKey }, focusRequester = fr, shape = Rnd(16.dp), normal = Color(0x8C14181C), focused = Color(0x8C14181C),
        focusBorderColor = Color(0xF2FFFFFF), borderColor = Color(0x33FFFFFF), zoom = 1.03f, modifier = modifier) { _ ->
        // top-aligned crop: heads and faces stay, any excess goes off the bottom
        OnyxImage(meta?.backdrop ?: c.image, "hm:" + c.title + (meta?.backdrop != null), Modifier.fillMaxSize(), maxW = if (big) 1280 else 780, align = Alignment.TopCenter, wide = meta?.backdrop != null)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.40f to Color(0x00000000), 1f to Color(0xD9000000))))
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = if (big) 22.dp else 16.dp, vertical = if (big) 18.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            T((titleCase(meta?.title ?: name) + (if (year != null && !c.series) " ($year)" else "")).uppercase(), if (big) 22.sp else 16.sp, Color.White, FontWeight.Medium, maxLines = 1)
            val rating = (meta?.tmdbRating ?: c.row?.r?.replace(',', '.')?.toDoubleOrNull())?.takeIf { it > 0 }?.let { "★ " + String.format(Locale.US, "%.1f", it) }  // i18n-ignore
            val genre = translated(meta?.genres)?.split(", ")?.take(2)?.joinToString(" / ")
            val line = if (c.series) listOfNotNull(
                if (pr?.season != null && pr.episode != null) "S%dE%d".format(pr.season, pr.episode) else null, if (big) genre else null, rating)  // i18n-ignore
            else listOfNotNull(if (big) year else null, if (big) meta?.runtime else null, genre, rating)
            if (line.isNotEmpty()) T(line.joinToString(" • "), if (big) 14.sp else 12.sp, Color(0xFFDDE1E6), maxLines = 1)  // i18n-ignore
            // real progress only (Continue Watching); recently added films have none
            if (pr != null && pr.dur > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                Box(Modifier.weight(1f).height(3.dp).clip(Rnd(2.dp)).background(Color(0x40FFFFFF))) {
                    Box(Modifier.fillMaxWidth(pr.fraction.coerceAtLeast(0.02f)).fillMaxHeight().background(Color(0xFFF2F3F5)))
                }
                T(trf("još %s min", ((pr.dur - pr.pos) / 60_000).toInt().coerceAtLeast(1)), 12.sp, Color(0xFFDDE1E6))
            }
        }
    }
}
