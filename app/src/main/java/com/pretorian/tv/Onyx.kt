package com.pretorian.tv

import androidx.compose.foundation.lazy.itemsIndexed
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
    fun init(c: Context) {
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo(); am.getMemoryInfo(mi)
        ramGb = mi.totalMem / 1_073_741_824.0
        // weak (Lite on its own): under ~3.5 GB of usable RAM (2 GB and 3 GB boxes like the Mi Box), a "low RAM" device,
        // old Android, a 32-bit-only chip, fewer than 4 cores or a chip slower than 1.6 GHz (Mi Box S: 1.5 GHz; Google TV Streamer is above)
        val cores = Runtime.getRuntime().availableProcessors()
        val maxGhz = (0 until cores).maxOfOrNull { i ->
            try { java.io.File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq").readText().trim().toLong() / 1_000_000.0 } catch (e: Exception) { 0.0 }
        } ?: 0.0
        weak = am.isLowRamDevice || ramGb < 3.5 || android.os.Build.VERSION.SDK_INT < 28 || android.os.Build.SUPPORTED_64_BIT_ABIS.isEmpty() ||
            cores < 4 || (maxGhz > 0.1 && maxGhz < 1.6)
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
fun OnyxImage(url: String?, key: String, modifier: Modifier = Modifier, maxW: Int = 360, scale: ContentScale = ContentScale.Crop) {
    val ctx = LocalContext.current
    val fb = painterResource(fallbackFor(key))
    if (url.isNullOrBlank()) Image(fb, null, modifier, contentScale = ContentScale.Crop)
    else {
        // never decode more pixels than the screen shows; Lite also uses half the memory per pixel
        val w = if (Perf.lite) maxW * 2 / 3 else maxW
        val req = remember(url, w) {
            coil.request.ImageRequest.Builder(ctx).data(url).size(w, w * 3 / 2).allowRgb565(Perf.lite).crossfade(!Perf.lite).build()
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
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (simple) 10.dp else 6.dp)) {
                items.forEachIndexed { i, it ->
                    NavPill(it, section == it.screen, if (i == 1 && firstFocus != null) firstFocus else if (section == it.screen) TopNav.fr else null, big = simple) { nav.root(it.screen) }
                }
                if (simple) SimpleModePill(nav)
                else NavPill(NavItem(Screen.Settings, R.drawable.lc_settings, "Postavke",
                    if (Updater.available != null) 1 else 0), section == Screen.Settings, if (section == Screen.Settings) TopNav.fr else null, big = false) {
                    nav.root(Screen.Settings)
                }
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
}

@Composable
private fun NavPill(it: NavItem, selected: Boolean, fr: FocusRequester?, big: Boolean, onClick: () -> Unit) {
    // advanced mode: icons only; the open screen and the focused one show their name next to the icon
    Box {
        Focusable(onClick = onClick, focusRequester = fr, shape = Pill, normal = if (selected) C.CardHi else Color.Transparent,
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
    var contMenu by remember { mutableStateOf<Progress?>(null) }
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
            OnyxTopBar(Screen.Home, nav, simple)
            // everything sits higher and bigger: no empty half screen, nothing cut at the bottom of the TV
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // the card height follows the free space, so hero, Continue watching and Recent channels all fit without scrolling
            val cardH = (maxHeight - 372.dp).coerceIn(84.dp, 140.dp)
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
                SubscriptionCard()
                }
                Spacer(Modifier.height(12.dp))
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
                } else if (p != null && Recent.list().isEmpty()) {
                    // no history at all: the newest films, series and channels at the provider. Gone as soon as you watch something.
                    val disc by load(p.id) {
                        val mv = Repo.vodGroups(p).take(2).flatMap { g -> try { Repo.movies(p, g.id) } catch (e: Exception) { emptyList() } }
                            .distinctBy { it.id }.sortedByDescending { it.added }.take(40)
                        val se = Repo.seriesGroups(p).take(2).flatMap { g -> try { Repo.series(p, g.id) } catch (e: Exception) { emptyList() } }
                            .distinctBy { it.id }.sortedByDescending { it.added }.take(40)
                        val ch = try { Repo.allChannels(p).sortedByDescending { it.id.toLongOrNull() ?: 0L }.take(30) } catch (e: Exception) { emptyList() }
                        Triple(mv, se, ch)
                    }
                    (disc as? Load.Ok)?.value?.let { (mv, se, ch) ->
                        // 5 posters across the screen; each row scrolls sideways and goes round
                        val pw = ((maxWidth - 56.dp - 14.dp * 4) / 5).coerceIn(90.dp, 220.dp)
                        val dScope = rememberCoroutineScope()
                        if (mv.isNotEmpty()) {
                            val w = remember(mv.size) { RowWrap(mv.size) }
                            RowTitle("NAJNOVIJI FILMOVI")
                            LazyRow(state = w.state, horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(top = 6.dp, bottom = 12.dp)) {
                                itemsIndexed(mv, key = { _, v -> v.id }) { i, v ->
                                    PosterCard(cleanTitle(v.name).first, v.poster, v.id, null, Modifier.width(pw).then(w.keys(i, dScope)), fr = w.fr(i), rating = v.rating) { nav.push(Screen.MovieDetail(v)) }
                                }
                            }
                        }
                        if (se.isNotEmpty()) {
                            val w = remember(se.size) { RowWrap(se.size) }
                            RowTitle("NAJNOVIJE SERIJE")
                            LazyRow(state = w.state, horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(top = 6.dp, bottom = 12.dp)) {
                                itemsIndexed(se, key = { _, v -> v.id }) { i, v ->
                                    PosterCard(cleanTitle(v.name).first, v.cover, v.id, null, Modifier.width(pw).then(w.keys(i, dScope)), fr = w.fr(i), rating = v.rating) { nav.push(Screen.SeriesDetail(v)) }
                                }
                            }
                        }
                        if (ch.isNotEmpty()) {
                            val w = remember(ch.size) { RowWrap(ch.size) }
                            RowTitle("NOVI KANALI")
                            LazyRow(state = w.state, horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp)) {
                                itemsIndexed(ch, key = { _, c -> c.url }) { i, c ->
                                    Focusable(onClick = { dScope.launch { playInGroup(ctx, nav, c, p.id) } }, focusRequester = w.fr(i), shape = Rnd(14.dp), normal = C.Rail, focused = C.Red, zoom = 1.04f,
                                        modifier = Modifier.width(220.dp).height(60.dp).border(1.dp, C.Line, Rnd(14.dp)).then(w.keys(i, dScope))) { f ->
                                        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            ChannelLogo(c, 38.dp, onLight = f)
                                            T(c.name, 14.sp, if (f) C.OnRed else C.Text, FontWeight.Bold, modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
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
                Spacer(Modifier.height(6.dp))
                // the channels you watched last, next to Continue watching
                RecentStrip(nav)
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
            OnyxImage(image, key, Modifier.fillMaxSize().clip(Rnd(12.dp)).blur(18.dp).alpha(0.55f), maxW = 160)
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
    if (p.getInt("look_v", 0) == 0 && !p.contains("look_theme")) p.edit().putBoolean("hide_adult_default", true).putBoolean("simple_mode", true).apply()
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
