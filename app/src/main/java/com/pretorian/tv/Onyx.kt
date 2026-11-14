package com.pretorian.tv

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
    fun init(c: Context) {
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo(); am.getMemoryInfo(mi)
        ramGb = mi.totalMem / 1_073_741_824.0
        // under ~3.5 GB of usable RAM (2 GB and 3 GB boxes like the Mi Box) or a "low RAM" device counts as weak
        weak = am.isLowRamDevice || ramGb < 3.5 || android.os.Build.VERSION.SDK_INT < 28
        mode = Prefs.long("perf_mode", 0).toInt().coerceIn(0, 2)
    }
    fun set(m: Int) { mode = m; Prefs.put("perf_mode", m.toLong()) }
    val MODES = listOf("Automatski", "Lite", "Full")
}

/** Pictures used when the provider has none for a film: calm landscapes that fit the dark look. */
private val FALLBACKS = intArrayOf(R.drawable.fallback_bd1, R.drawable.fallback_bd2, R.drawable.fallback_bd3,
    R.drawable.fallback_bd4, R.drawable.fallback_bd5, R.drawable.fallback_bd6)
fun fallbackFor(key: String): Int = FALLBACKS[(key.hashCode() and 0x7fffffff) % FALLBACKS.size]

/** Picture that falls back to a landscape when there is no image or it fails to load. */
@Composable
fun OnyxImage(url: String?, key: String, modifier: Modifier = Modifier, maxW: Int = 360) {
    val ctx = LocalContext.current
    val fb = painterResource(fallbackFor(key))
    if (url.isNullOrBlank()) Image(fb, null, modifier, contentScale = ContentScale.Crop)
    else {
        // never decode more pixels than the screen shows; Lite also uses half the memory per pixel
        val w = if (Perf.lite) maxW * 2 / 3 else maxW
        val req = remember(url, w) {
            coil.request.ImageRequest.Builder(ctx).data(url).size(w, w * 3 / 2).allowRgb565(Perf.lite).crossfade(!Perf.lite).build()
        }
        AsyncImage(model = req, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier, error = fb, placeholder = fb)
    }
}

/** "Predvečerje": the colour of what you are on, as a soft glow behind the whole app.
 *  A tiny 64 px copy of the picture is stretched over the screen - it looks blurred and costs almost nothing. */
object Dusk {
    var image by mutableStateOf<Any?>(null)
    fun set(url: String?, key: String) { image = url?.takeIf { it.isNotBlank() } ?: fallbackFor(key) }
}

@Composable
fun DuskBackground() {
    if (Look.theme != "vece") return
    val ctx = LocalContext.current
    val img = Dusk.image ?: R.drawable.fallback_bd2
    val req = remember(img) { coil.request.ImageRequest.Builder(ctx).data(img).size(64, 36).allowRgb565(true).crossfade(if (Perf.lite) 0 else 500).build() }
    Box(Modifier.fillMaxSize()) {
        AsyncImage(model = req, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), alpha = 0.9f)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xB8070912), Color(0x6B070912), Color(0x38070912)))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x73070912), Color.Transparent, Color.Transparent, Color(0xB305060C)))))
    }
}

/** Full-screen picture behind a screen, fading into the background on the left and at the bottom. */
@Composable
fun OnyxBackdrop(url: String?, key: String, startX: Float = 0f) {
    if (Look.theme == "vece") { LaunchedEffect(url, key) { Dusk.set(url, key) }; return }
    if (Perf.lite) {   // Lite: no picture, only a quiet light from the top right
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(C.Text.copy(alpha = 0.07f), Color.Transparent),
            center = androidx.compose.ui.geometry.Offset(1400f, 0f), radius = 1400f)))
        return
    }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().padding(start = startX.dp)) { OnyxImage(url, key, Modifier.fillMaxSize(), maxW = 960) }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            listOf(C.Bg, C.Bg.copy(alpha = 0.88f), C.Bg.copy(alpha = 0.2f), C.Bg.copy(alpha = 0.35f)))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            listOf(C.Bg.copy(alpha = 0.6f), Color.Transparent, Color.Transparent, C.Bg), startY = 0f)))
    }
}

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
        add(NavItem(Screen.Tv, R.drawable.lc_tv, "TV", Notes.unread("reminder")))
        add(NavItem(Screen.Movies, R.drawable.lc_film, "Filmovi", NewContent.count("vod")))
        add(NavItem(Screen.Series, R.drawable.lc_monitor, "Serije", NewContent.count("series")))
        if (!simple) { add(NavItem(Screen.Sports, R.drawable.lc_ball, "Sport")); add(NavItem(Screen.Search, R.drawable.lc_search, "Pretraga")) }
    }
    val section = when (current) { is Screen.MovieDetail -> Screen.Movies; is Screen.SeriesDetail -> Screen.Series; Screen.AddPlaylist -> Screen.Settings; else -> current }
    Column(Modifier.fillMaxWidth().padding(start = 40.dp, end = 40.dp, top = 18.dp)) {
        Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("PRETORIAN", style = TextStyle(fontFamily = Inter, fontSize = 17.sp, color = C.Text, fontWeight = FontWeight.Light, letterSpacing = 7.sp), maxLines = 1)
            if (simple) Box(Modifier.clip(Pill).border(1.dp, C.Line, Pill).padding(horizontal = 10.dp, vertical = 3.dp)) {
                T("JEDNOSTAVNI MOD", 10.sp, C.Soft, FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            val p = pls.firstOrNull()
            if (p != null) {
                Ico(R.drawable.lc_wifi, 15.dp, C.Muted)
                T(p.name, 13.sp, C.Text, maxLines = 1, modifier = Modifier.widthIn(max = 160.dp))
                val sec = p.exp.toLongOrNull()
                if (sec != null && sec > 0) {
                    val days = (sec * 1000 - System.currentTimeMillis()) / 86_400_000L
                    T(SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(Date(sec * 1000)), 13.sp, C.Muted)  // i18n-ignore
                    T(trf("%s dana", days.coerceAtLeast(0)), 13.sp, if (days <= 5) C.Danger else C.Muted, if (days <= 5) FontWeight.Bold else FontWeight.Normal)
                }
                Box(Modifier.width(1.dp).height(16.dp).background(C.Line))
            }
            T(SimpleDateFormat("EEE d.M.", Locale(Look.lang)).format(now), 13.sp, C.Muted)  // i18n-ignore
            T(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), 13.sp, C.Text, FontWeight.SemiBold)  // i18n-ignore
            if (!simple) ProfileButton(26.dp)
            Box {
                Focusable(onClick = { notes = true }, shape = CircleShape, normal = Color.Transparent, focused = C.CardHi, zoom = 1.08f,
                    modifier = Modifier.size(34.dp).border(1.dp, C.Line, CircleShape)) { _ ->
                    Box(Modifier.align(Alignment.Center)) { Ico(R.drawable.lc_bell, 16.dp, C.Text) }
                }
                if (Notes.unread() > 0) Box(Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 7.dp).size(6.dp).clip(CircleShape).background(C.Text))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items.forEachIndexed { i, it ->
                NavPill(it, section == it.screen, if (i == 0) firstFocus else null, big = simple) { nav.root(it.screen) }
            }
            Spacer(Modifier.weight(1f))
            NavPill(NavItem(Screen.Settings, if (simple) R.drawable.lc_lock else R.drawable.lc_settings, "Postavke",
                if (Updater.available != null) 1 else 0), section == Screen.Settings, null, big = false) {
                if (simple) OnyxState.askAdvanced = true else nav.root(Screen.Settings)
            }
        }
    }
    if (notes) NotesPopup({ notes = false }, nav)
}

@Composable
private fun NavPill(it: NavItem, selected: Boolean, fr: FocusRequester?, big: Boolean, onClick: () -> Unit) {
    Box {
        Focusable(onClick = onClick, focusRequester = fr, shape = Pill, normal = if (selected) C.CardHi else Color.Transparent,
            focused = C.Red, zoom = 1.04f, modifier = Modifier.height(if (big) 48.dp else 42.dp)) { f ->
            Row(Modifier.align(Alignment.Center).padding(horizontal = if (big) 22.dp else 16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                val col = if (f) C.OnRed else if (selected) C.Text else C.Soft
                Ico(it.icon, if (big) 22.dp else 18.dp, col)
                T(it.label, if (big) 18.sp else 15.sp, col, FontWeight.SemiBold)
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
    val p = remember { Store.active(ctx) }
    fun playOf(pr: Progress): () -> Unit = { nav.push(Screen.Player(listOf(PlayItem(pr.title, pr.url, pr.poster, null, null, pr.series, pr.season, pr.episode)), 0, live = false)) }
    val newest = remember(NewContent.rev) {
        if (p == null) emptyList() else NewContent.items(p.id, "vod").take(12).map { "vod" to it } + NewContent.items(p.id, "series").take(6).map { "series" to it }
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
        Column(Modifier.fillMaxSize()) {
            OnyxTopBar(Screen.Home, nav, simple, first)
            ProviderBanner()
            Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 40.dp), verticalArrangement = Arrangement.Bottom) {
                Box(Modifier.padding(bottom = 14.dp)) { ExpiryChip() }
                if (hero != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (cont.isEmpty()) Box(Modifier.clip(Rnd(5.dp)).background(C.Text).padding(horizontal = 8.dp, vertical = 2.dp)) { T("NOVO", 10.sp, C.Bg, FontWeight.ExtraBold) }
                        Text(hero.kicker, style = TextStyle(fontFamily = Inter, fontSize = 12.sp, color = C.Soft, fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp), maxLines = 1)
                    }
                    Text(hero.title, style = TextStyle(fontFamily = Inter, fontSize = 50.sp, color = C.Text, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp),
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp).widthIn(max = 760.dp))
                    if (hero.meta.isNotBlank()) T(hero.meta, 14.sp, C.Soft, modifier = Modifier.padding(top = 4.dp))
                    Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Focusable(onClick = hero.play, shape = Pill, normal = C.Text, focused = C.Text, zoom = 1.06f, modifier = Modifier.height(46.dp)) { _ ->
                            Row(Modifier.align(Alignment.Center).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                Ico(R.drawable.lc_play, 16.dp, C.Bg); T(hero.button, 15.sp, C.Bg, FontWeight.Bold)
                            }
                        }
                    }
                    hero.progress?.let { f ->
                        Box(Modifier.padding(top = 16.dp).width(300.dp).height(3.dp).clip(Pill).background(C.Line)) {
                            Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(C.Text))
                        }
                    }
                } else T("Dodaj playlistu u Postavkama", 18.sp, C.Soft)
                Spacer(Modifier.height(28.dp))
                if (cont.isNotEmpty()) {
                    RowTitle("NASTAVI GLEDANJE")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(end = 40.dp)) {
                        items(cont, key = { it.url }) { pr ->
                            WideCard(pr.series ?: pr.title, if (pr.season != null) "S${pr.season} E${pr.episode}" else fmtTime(pr.dur), pr.poster, pr.url,
                                if (pr.next) null else pr.fraction, playOf(pr))
                        }
                    }
                } else if (newest.isNotEmpty()) {
                    RowTitle("NOVO DODANO")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(end = 40.dp)) {
                        items(newest, key = { it.first + it.second.optString("id") }) { (kind, o) ->
                            PosterCard(cleanTitle(o.optString("n")).first, o.optString("i").ifBlank { null }, o.optString("id"), null, Modifier.width(118.dp)) { openNew(kind, o) }
                        }
                    }
                }
                Spacer(Modifier.height(26.dp))
            }
        }
    }
    LaunchedEffect(Unit) { first.tryFocus() }
    if (!simple) TipOnce("hold_ok", "Savjet", "Drži OK na bilo čemu za opcije: Moja lista, Favoriti, Sakrij.")
}

@Composable
private fun RowTitle(t: String) {
    Text(tr(t), style = TextStyle(fontFamily = Inter, fontSize = 12.sp, color = C.Muted, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp),
        modifier = Modifier.padding(bottom = 10.dp))
}

/** Landscape card with picture, title and a thin progress line. */
@Composable
fun WideCard(title: String, sub: String, image: String?, key: String, progress: Float?, onClick: () -> Unit) {
    Column(Modifier.width(250.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Focusable(onClick = onClick, shape = Rnd(12.dp), normal = C.Card, zoom = 1.05f, modifier = Modifier.fillMaxWidth().height(122.dp)) { f ->
            OnyxImage(image, key, Modifier.fillMaxSize().clip(Rnd(12.dp)), maxW = 420)
            Box(Modifier.fillMaxSize().clip(Rnd(12.dp)).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9000000)), startY = 90f)))
            if (f) Box(Modifier.fillMaxSize().border(2.dp, Color.White, Rnd(12.dp)))
            T(cleanTitle(title).first, 15.sp, Color.White, FontWeight.Bold, modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, end = 12.dp, bottom = 12.dp))
            if (progress != null) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color(0x33FFFFFF))) {
                Box(Modifier.fillMaxWidth(progress.coerceAtLeast(0.02f)).fillMaxHeight().background(Color.White))
            }
        }
        T(sub, 12.sp, C.Muted)
    }
}

/** Portrait poster with title on the picture and an optional progress line. */
@Composable
fun PosterCard(title: String, image: String?, key: String, progress: Float?, modifier: Modifier, onFocus: (() -> Unit)? = null,
               onMenu: (() -> Unit)? = null, onClick: () -> Unit) {
    Focusable(onClick = onClick, onFocus = onFocus, onMenu = onMenu, shape = Rnd(12.dp), normal = C.Card, zoom = 1.05f, modifier = modifier.aspectRatio(2f / 3f)) { f ->
        OnyxImage(image, key, Modifier.fillMaxSize().clip(Rnd(12.dp)))
        Box(Modifier.fillMaxSize().clip(Rnd(12.dp)).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE0000000)), startY = 160f)))
        if (f) Box(Modifier.fillMaxSize().border(2.dp, Color.White, Rnd(12.dp)))
        T(title, 13.sp, Color.White, FontWeight.Bold, maxLines = 2,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, end = 10.dp, bottom = if (progress != null) 18.dp else 10.dp))
        if (progress != null) Box(Modifier.align(Alignment.BottomStart).padding(start = 10.dp, end = 10.dp, bottom = 8.dp).fillMaxWidth().height(3.dp).clip(Pill).background(Color(0x40FFFFFF))) {
            Box(Modifier.fillMaxWidth(progress.coerceAtLeast(0.03f)).fillMaxHeight().background(Color.White))
        }
    }
}

/** 0.6.0/0.6.4: everyone gets the new look and the top menu once; afterwards they can change it. */
fun onyxMigrate(c: Context) {
    val p = c.getSharedPreferences("pretorian", Context.MODE_PRIVATE)
    if (p.getInt("look_v", 0) == 0 && !p.contains("look_theme")) p.edit().putBoolean("hide_adult_default", true).apply()  // brand new install
    if (p.getInt("look_v", 0) < 7) {   // 0.6.4: "Predvečerje" becomes the look for everyone once
        Look.theme = "vece"; Look.layout = 2
        p.edit().putInt("look_v", 7).putString("look_theme", "vece").putInt("look_layout", 2).apply()
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

object OnyxState { var askAdvanced by mutableStateOf(false) }

/** Simple mode: Settings are behind the PIN, with a warning first. */
@Composable
fun AdvancedGate(nav: Nav) {
    var pin by remember { mutableStateOf(false) }
    if (OnyxState.askAdvanced) Popup("Aktivirati napredni mod?", onDismiss = { OnyxState.askAdvanced = false }) {
        T("Pažnja: aktivirate napredni mod i dodatne opcije. Nastavite s pažnjom, jer možete izazvati nepoželjne posljedice i pokvariti podešavanja programa.",
            16.sp, C.Soft, maxLines = 5)
        OptionRow("Ostani u jednostavnom modu", R.drawable.lc_check) { OnyxState.askAdvanced = false }
        OptionRow("Nastavi i unesi PIN", R.drawable.lc_key_round, color = C.Amber) { OnyxState.askAdvanced = false; pin = true }
    }
    if (pin) PinDialog("PIN za napredni mod", onDismiss = { pin = false }) { entered ->
        val err = Lock.check(entered)
        if (err == null) { pin = false; Lock.switchSimple(false); nav.root(Screen.Settings) }
        err
    }
}
