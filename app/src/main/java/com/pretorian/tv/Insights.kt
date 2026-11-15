package com.pretorian.tv

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ============================================================== statistics for Settings › Diagnostics */

/** Counts what is watched, the data used, stream drops and starts, per day (kept for a year). */
object Stats {
    private var o: JSONObject? = null
    private var dirty = false
    val appStart = System.currentTimeMillis()
    var rev by mutableIntStateOf(0)

    private fun root(): JSONObject {
        o?.let { return it }
        val r = try { JSONObject(Prefs.str("stats", "{}")) } catch (e: Exception) { JSONObject() }
        o = r; return r
    }
    private fun dayKey(t: Long = System.currentTimeMillis()) = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(t))  // i18n-ignore
    private fun day(): JSONObject {
        val days = root().optJSONObject("d") ?: JSONObject().also { root().put("d", it) }
        val k = dayKey()
        return days.optJSONObject(k) ?: JSONObject().also { days.put(k, it) }
    }
    private fun inc(o: JSONObject, k: String, v: Long) { o.put(k, o.optLong(k) + v); dirty = true }

    /** [kind] tv, ser or mov */
    fun watch(kind: String, ms: Long, channel: String?) {
        val d = day(); inc(d, kind, ms)
        if (channel != null) { val c = d.optJSONObject("ch") ?: JSONObject().also { d.put("ch", it) }; c.put(channel, c.optLong(channel) + ms) }
    }
    fun bytes(kind: String, b: Long) = inc(day(), "b_$kind", b)
    fun episode(series: String, season: Int) {
        val d = day(); inc(d, "eps", 1)
        val a = d.optJSONArray("sea") ?: JSONArray().also { d.put("sea", it) }
        val k = "$series|$season"
        if ((0 until a.length()).none { a.optString(it) == k }) a.put(k)
    }
    fun film() = inc(day(), "films", 1)
    fun recovery() = inc(day(), "rec", 1)
    fun stall(ms: Long) = inc(day(), "stall", ms)
    fun uptime(ms: Long) = inc(day(), "up", ms)

    /** called once at start: a new version means an update was installed */
    fun onStart() {
        val last = Prefs.str("stats_ver")
        if (last.isNotBlank() && last != BuildInfo.VERSION) inc(day(), "upd", 1)
        if (last != BuildInfo.VERSION) { Prefs.put("stats_ver", BuildInfo.VERSION); Prefs.put("stats_upd_at", System.currentTimeMillis()) }
        // drop days older than a year
        val days = root().optJSONObject("d") ?: return
        val cut = dayKey(System.currentTimeMillis() - 366L * 86_400_000L)
        days.keys().asSequence().toList().filter { it < cut }.forEach { days.remove(it) }
        save()
    }

    fun save() { if (o != null) { Prefs.put("stats", o.toString()); dirty = false; rev++ } }
    fun saveIfDirty() { if (dirty) save() }

    /** the days in a period: 0 today, 1 last 7 days, 2 last 30 days, 3 last year */
    private fun daysOf(period: Int): List<JSONObject> {
        val days = root().optJSONObject("d") ?: return emptyList()
        val n = when (period) { 0 -> 1; 1 -> 7; 2 -> 30; else -> 365 }
        val cut = dayKey(System.currentTimeMillis() - (n - 1) * 86_400_000L)
        return days.keys().asSequence().filter { it >= cut }.mapNotNull { days.optJSONObject(it) }.toList()
    }
    fun sum(k: String, period: Int) = daysOf(period).sumOf { it.optLong(k) }
    fun seasons(period: Int) = daysOf(period).flatMap { d -> d.optJSONArray("sea")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList() }.distinct().size
    fun topChannel(period: Int): String? {
        val m = HashMap<String, Long>()
        daysOf(period).forEach { d -> d.optJSONObject("ch")?.let { c -> c.keys().forEach { k -> m[k] = (m[k] ?: 0) + c.optLong(k) } } }
        return m.maxByOrNull { it.value }?.key
    }
    fun totalUptime() = sum("up", 3)

    /** reset of one card: removes its fields from every day */
    fun reset(keys: List<String>) {
        val days = root().optJSONObject("d") ?: return
        days.keys().forEach { k -> val d = days.optJSONObject(k) ?: return@forEach; keys.forEach { d.remove(it) } }
        save()
    }
    fun resetAll() { o = JSONObject(); save() }
}

fun fmtDur(ms: Long): String {
    val m = ms / 60_000
    return if (m >= 60) listOf(m / 60, " h ", m % 60, " min").joinToString("") else listOf(m, " min").joinToString("")
}
fun fmtGb(b: Long): String = if (b >= 1_073_741_824L) String.format(Locale.US, "%.1f GB", b / 1_073_741_824.0) else String.format(Locale.US, "%d MB", b / 1_048_576)  // i18n-ignore

/* ============================================================== screen saver */

/** What the screen saver shows about a paused film or episode. */
data class Paused(val kind: String, val title: String, val sub: String?, val poster: String?, val desc: String?, val pos: Long, val dur: Long)

object Saver {
    val delays = listOf(1, 3, 5, 10, 15, 30)
    val on get() = Prefs.bool("ss_on", true)
    val delayMs get() = delays[Prefs.long("ss_delay", 2).toInt().coerceIn(0, delays.size - 1)] * 60_000L
    /** 0 only when paused, 1 also in the menus */
    val inMenus get() = Prefs.long("ss_when", 0) == 1L
    val show get() = Prefs.str("ss_show", "0,1,2,3,4,5").split(',').mapNotNull { it.toIntOrNull() }.toSet()
    val h24 get() = Prefs.bool("ss_24", true)
    /** 0 soft colours, 1 the poster, 2 black */
    val bg get() = Prefs.long("ss_bg", 0).toInt()
    val bright get() = listOf(1f, 0.7f, 0.45f)[Prefs.long("ss_bright", 0).toInt().coerceIn(0, 2)]
    val shift get() = Prefs.bool("ss_shift", true)

    /** last key press anywhere in the app */
    var lastKey = System.currentTimeMillis()
    var showing by mutableStateOf(false)
    var preview by mutableStateOf(false)
    fun touch(): Boolean { lastKey = System.currentTimeMillis(); val was = showing || preview; showing = false; preview = false; return was }
}

/** Big clock in the middle, the date under it and — when something is paused — what it is and how much is left. */
@Composable
fun ModernScreenSaver(paused: Paused?) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(1000) } }
    val show = Saver.show
    // slow drift so nothing burns into the screen
    var dx by remember { mutableIntStateOf(0) }; var dy by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (Saver.shift) { delay(60_000); dx = (-24..24).random(); dy = (-16..16).random() } }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (Saver.bg) {
            0 -> Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF4A3220), Color(0xFF0A0B10)), radius = 1400f,
                center = androidx.compose.ui.geometry.Offset(560f, 300f))))
            1 -> if (paused?.poster != null) { OnyxImage(paused.poster, "ss", Modifier.fillMaxSize().alpha(0.25f), maxW = 640); Box(Modifier.fillMaxSize().background(Color(0x99000000))) }
            else -> {}
        }
        T("PRETORIAN", 12.sp, Color(0x66FFFFFF), modifier = Modifier.padding(start = 28.dp, top = 22.dp))  // i18n-ignore
        Column(Modifier.fillMaxSize().offset(dx.dp, dy.dp).alpha(Saver.bright), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(0.8f))
            Row(verticalAlignment = Alignment.Top) {
                T(SimpleDateFormat(if (Saver.h24) "HH:mm" else "h:mm", Locale.US).format(now), 120.sp, Color.White, FontWeight.Bold)  // i18n-ignore
                if (0 in show) T(SimpleDateFormat("ss", Locale.US).format(now), 42.sp, Color(0x99FFFFFF), modifier = Modifier.padding(top = 18.dp, start = 6.dp))  // i18n-ignore
                if (!Saver.h24) T(SimpleDateFormat("a", Locale.US).format(now), 22.sp, Color(0x99FFFFFF), modifier = Modifier.padding(top = 26.dp, start = 6.dp))  // i18n-ignore
            }
            if (1 in show) T(SimpleDateFormat("EEEE, d. MMMM yyyy", Locale(Look.lang)).format(now).replaceFirstChar { it.uppercase() }, 22.sp, Color(0xFFD5D2CC))  // i18n-ignore
            Spacer(Modifier.weight(0.6f))
            if (paused != null && 2 in show) {
                Row(Modifier.widthIn(max = 520.dp).fillMaxWidth(0.55f).clip(Rnd(22.dp)).background(Color(0xCC1A1D26)).border(1.dp, Color(0x22FFFFFF), Rnd(22.dp)).padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (3 in show && paused.poster != null) Box(Modifier.width(74.dp).aspectRatio(2f / 3f).clip(Rnd(10.dp))) { OnyxImage(paused.poster, "ssp", Modifier.fillMaxSize(), maxW = 200) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Kicker(tr("Pauzirano") + " · " + tr(paused.kind), Color(0xFFFFC53D), size = 10.sp)
                        T(paused.title, 20.sp, Color.White, FontWeight.ExtraBold)
                        paused.sub?.let { T(it, 13.sp, Color(0xFFC8CBD4)) }
                        if (4 in show) paused.desc?.let { T(it, 12.sp, Color(0xFFA9AEB8), maxLines = 2) }
                        if (5 in show && paused.dur > 0) {
                            ThinBar(paused.pos.toFloat() / paused.dur, Color(0xFFFFC53D), Modifier.fillMaxWidth().padding(top = 4.dp), 3.dp)
                            Row {
                                T(listOf(paused.pos / 60_000, " / ", paused.dur / 60_000, " min").joinToString(""), 11.sp, Color(0xFFA9AEB8), modifier = Modifier.weight(1f))  // i18n-ignore
                                T(trf("još %s min", ((paused.dur - paused.pos) / 60_000).coerceAtLeast(1)), 11.sp, Color(0xFFFFC53D), FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.weight(0.5f))
            T("Pritisni bilo koje dugme za nastavak", 12.sp, Color(0x80FFFFFF), modifier = Modifier.padding(bottom = 18.dp))
        }
    }
}

/** Settings › Screen saver */
@Composable
fun SaverV2() {
    var rev by remember { mutableIntStateOf(0) }
    key(rev) {
        SetPage("Čuvar ekrana", "Sat i datum kad se ništa ne dešava; uz pauzu pokazuje šta je zaustavljeno.") {
            SetSwitch("Čuvar ekrana", "sat preko slike nakon pauze", Saver.on) { Prefs.put("ss_on", it); rev++ }
            SetDrop("Pokreni nakon", null, Saver.delays.map { trf("%s min", it) }, Prefs.long("ss_delay", 2).toInt()) { Prefs.put("ss_delay", it.toLong()); rev++ }
            SetDrop("Kada", null, listOf("Samo kad je pauzirano", "I u menijima"), Prefs.long("ss_when", 0).toInt()) { Prefs.put("ss_when", it.toLong()); rev++ }
            SetMulti("Prikaži", "više odjednom", listOf("Sekunde", "Datum", "Pauzirani naslov", "Poster", "Opis", "Napredak i preostalo vrijeme"), Saver.show, all = false) { s ->
                Prefs.put("ss_show", s.sorted().joinToString(",")); rev++
            }
            SetDrop("Format sata", null, listOf("24 h", "12 h"), if (Saver.h24) 0 else 1) { Prefs.put("ss_24", it == 0); rev++ }
            SetDrop("Pozadina", null, listOf("Meke boje", "Poster pauziranog", "Crna"), Saver.bg) { Prefs.put("ss_bg", it.toLong()); rev++ }
            SetDrop("Svjetlina", null, listOf("100 %", "70 %", "45 %"), Prefs.long("ss_bright", 0).toInt()) { Prefs.put("ss_bright", it.toLong()); rev++ }
            SetSwitch("Lagano pomjeranje", "štiti ekran od urezivanja slike", Saver.shift) { Prefs.put("ss_shift", it); rev++ }
            SetRow("Pregled", "pokaži čuvar ekrana sada", tr("Pokaži")) { Saver.preview = true }
        }
    }
}

/** Over every screen except the player: the menus' screen saver and the preview from Settings. */
@Composable
fun MenuSaver(inPlayer: Boolean) {
    LaunchedEffect(inPlayer) {
        while (true) {
            delay(5_000)
            if (!inPlayer && Saver.on && Saver.inMenus && !Saver.showing && System.currentTimeMillis() - Saver.lastKey > Saver.delayMs) Saver.showing = true
        }
    }
    if (Saver.preview || (Saver.showing && !inPlayer)) ModernScreenSaver(null)
}

/* ============================================================== shared card */

@Composable
fun InfoCard(label: String, title: String, sub: String?, icon: Int, color: Color, modifier: Modifier = Modifier, minH: Dp = 96.dp,
             bar: Float? = null, onClick: (() -> Unit)? = null, titleColor: Color = C.Text, action: (@Composable () -> Unit)? = null,
             extra: (@Composable ColumnScope.() -> Unit)? = null) {
    val inner: @Composable (Boolean) -> Unit = { f ->
        Row(Modifier.fillMaxWidth().then(if (f) Modifier.border(2.dp, Color.White, Rnd(16.dp)) else Modifier).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(38.dp).clip(Rnd(11.dp)).background(color), contentAlignment = Alignment.Center) { Ico(icon, 19.dp, Color.White) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Kicker(label, C.Muted, size = 10.sp, modifier = Modifier.weight(1f))
                    action?.invoke()
                }
                T(title, 17.sp, titleColor, FontWeight.ExtraBold, maxLines = 2)
                sub?.let { T(it, 12.sp, C.Soft, maxLines = 2) }
                if (bar != null) ThinBar(bar, color, Modifier.fillMaxWidth().padding(top = 6.dp), 4.dp)
                extra?.invoke(this)
            }
            if (onClick != null) T("›", 18.sp, C.Muted, modifier = Modifier.align(Alignment.CenterVertically))  // i18n-ignore
        }
    }
    val m = modifier.heightIn(min = minH).clip(Rnd(16.dp)).border(1.dp, C.Line, Rnd(16.dp))
    if (onClick != null) Focusable(onClick = onClick, shape = Rnd(16.dp), normal = C.Card, focused = C.CardHi, zoom = 1.02f, modifier = m) { f -> inner(f) }
    else Box(m.background(C.Card)) { inner(false) }
}

@Composable
private fun SmallBtn(text: String, onClick: () -> Unit) {
    Focusable(onClick = onClick, shape = Pill, normal = Color.White.copy(alpha = 0.06f), focused = Color.White, zoom = 1.05f,
        modifier = Modifier.height(26.dp).border(1.dp, C.Line, Pill)) { f ->
        T(text, 11.sp, if (f) Color.Black else C.Soft, FontWeight.Bold, modifier = Modifier.align(Alignment.Center).padding(horizontal = 10.dp))
    }
}

@Composable
private fun KV(k: String, v: String, vc: Color = C.Text) {
    Row(Modifier.fillMaxWidth()) { T(k, 12.sp, C.Soft, modifier = Modifier.weight(1f)); T(v, 12.sp, vc, FontWeight.Bold) }
}

/** n cards per row; the last row is filled with empty space. */
@Composable
fun CardGrid(n: Int, items: List<@Composable (Modifier) -> Unit>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(n).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { it(Modifier.weight(1f).fillMaxHeight()) }
                repeat(n - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/* ============================================================== Settings › Diagnostics */

@Composable
fun DiagnosticsV2() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var period by remember { mutableIntStateOf(0) }
    var rev by remember { mutableIntStateOf(0) }
    var speed by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var server by remember { mutableStateOf<Long?>(null) }
    var serverOk by remember { mutableStateOf<Boolean?>(null) }
    var cacheMb by remember { mutableLongStateOf(-1L) }
    var confirmAll by remember { mutableStateOf(false) }
    val vpn = remember { NetInfo.vpn(ctx) }
    fun runSpeed() {
        if (testing) return
        testing = true; speed = null
        scope.launch {
            val r = NetInfo.speedTest()
            speed = r
            if (r != null) Prefs.put(if (vpn) "speed_vpn" else "speed_norm", r)
            testing = false
        }
    }
    LaunchedEffect(Unit) {
        Stats.saveIfDirty()
        cacheMb = withContext(Dispatchers.IO) { ctx.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } / 1_048_576 }
        val p = Store.active(ctx)
        if (p != null) {
            val t0 = System.currentTimeMillis()
            serverOk = withContext(Dispatchers.IO) { try { Repo.test(p); true } catch (e: Exception) { false } }
            server = System.currentTimeMillis() - t0
        }
        runSpeed()
    }
    val periods = listOf("Danas", "7 dana", "Mjesec", "Godina")
    val pw = listOf("danas", "7 dana", "ovaj mjesec", "ova godina")[period]
    key(rev, Stats.rev) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                T("Dijagnostika", 23.sp, C.Text, FontWeight.Bold, modifier = Modifier.weight(1f))
                periods.forEachIndexed { i, l ->
                    val sel = i == period
                    Focusable(onClick = { period = i }, shape = Pill, normal = if (sel) Color.White else C.Card, focused = if (sel) Color.White else C.CardHi, zoom = 1.05f,
                        modifier = Modifier.height(32.dp)) { _ ->
                        T(l, 13.sp, if (sel) Color.Black else C.Soft, FontWeight.Bold, modifier = Modifier.align(Alignment.Center).padding(horizontal = 14.dp))
                    }
                }
                SmallBtn(tr("↺ Resetuj sve")) { confirmAll = true }
            }
            val tv = Stats.sum("tv", period); val ser = Stats.sum("ser", period); val mov = Stats.sum("mov", period)
            val total = tv + ser + mov
            // ---- watched (two columns wide) and uptime
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoCard("Gledano", fmtDur(total), trf("ukupno · %s", tr(pw)), R.drawable.lc_play, Color(0xFF3B82F6), Modifier.weight(2f).fillMaxHeight(),
                    action = { SmallBtn(tr("↺ Reset")) { Stats.reset(listOf("tv", "ser", "mov", "eps", "films", "sea", "ch")); rev++ } }) {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        listOf(Stats.sum("eps", period).toString() to "epizoda", Stats.seasons(period).toString() to "sezona",
                            Stats.sum("films", period).toString() to "filmova", fmtDur(tv) to "TV kanali").forEach { (v, l) ->
                            Column { T(v, 16.sp, C.Text, FontWeight.ExtraBold); T(l, 11.sp, C.Muted) }
                        }
                    }
                    if (total > 0) Row(Modifier.fillMaxWidth().padding(top = 8.dp).height(6.dp).clip(Pill)) {
                        listOf(tv to Color(0xFF22B8CF), ser to Color(0xFF8B5CF6), mov to Color(0xFFF59E0B)).filter { it.first > 0 }.forEach { (v, c) ->
                            Box(Modifier.weight(v.toFloat()).fillMaxHeight().background(c))
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        fun pct(v: Long) = if (total > 0) (v * 100 / total).toString() + " %" else "0 %"
                        T("● TV " + pct(tv), 11.sp, Color(0xFF22B8CF)); T("● " + tr("Serije") + " " + pct(ser), 11.sp, Color(0xFF8B5CF6))
                        T("● " + tr("Filmovi") + " " + pct(mov), 11.sp, Color(0xFFF59E0B))
                        Spacer(Modifier.weight(1f))
                        Stats.topChannel(period)?.let { T(tr("Najviše") + ": " + it, 11.sp, C.Text, FontWeight.Bold) }
                    }
                }
                InfoCard("Uptime", fmtDur(System.currentTimeMillis() - Stats.appStart), "od zadnjeg pokretanja", R.drawable.lc_clock, Color(0xFF22C55E),
                    Modifier.weight(1f).fillMaxHeight(), action = { SmallBtn(tr("↺ Reset")) { Stats.reset(listOf("up")); rev++ } }) {
                    KV(tr("Ukupno"), listOf(Stats.totalUptime() / 3_600_000, " h").joinToString(""))  // i18n-ignore
                    val crashes = Prefs.str("crash_times").split(',').count { it.isNotBlank() }
                    KV(tr("Rušenja (10 min)"), crashes.toString(), if (crashes == 0) C.Green else C.Danger)
                }
            }
            val bTv = Stats.sum("b_tv", period); val bSer = Stats.sum("b_ser", period); val bMov = Stats.sum("b_mov", period)
            val updW = Stats.sum("upd", 1); val updM = Stats.sum("upd", 2); val updY = Stats.sum("upd", 3)
            val recs = Stats.sum("rec", period)
            val stall = Stats.sum("stall", period)
            CardGrid(3, listOf(
                { m -> InfoCard("Protok podataka", fmtGb(bTv + bSer + bMov), trf("ukupno · %s", tr(pw)), R.drawable.lc_download, Color(0xFFF59E0B), m,
                    action = { SmallBtn(tr("↺ Reset")) { Stats.reset(listOf("b_tv", "b_ser", "b_mov")); rev++ } }) {
                    KV("TV", fmtGb(bTv)); KV(tr("Serije"), fmtGb(bSer)); KV(tr("Filmovi"), fmtGb(bMov))
                } },
                { m -> InfoCard("Brzina interneta", if (testing) tr("Mjerim…") else (speed ?: tr("Nije uspjelo")), if (vpn) tr("kroz VPN") else tr("normalno"),
                    R.drawable.lc_gauge2, Color(0xFF06B6D4), m, action = { SmallBtn(tr("▶ Testiraj")) { runSpeed() } }) {
                    KV(tr("Normalno"), if (vpn) Prefs.str("speed_norm").ifBlank { "—" } else speed ?: "—")
                    KV("VPN", if (vpn) speed ?: "—" else tr("VPN nije aktivan"), if (vpn) C.Text else C.Muted)
                } },
                { m -> InfoCard("Ažuriranja", trf("%s ovaj mjesec", updM), null, R.drawable.lc_refresh_cw, Color(0xFF8B5CF6), m,
                    action = { SmallBtn(tr("↺ Reset")) { Stats.reset(listOf("upd")); rev++ } }) {
                    KV(tr("Sedmica"), updW.toString()); KV(tr("Godina"), updY.toString())
                    KV(tr("Zadnje"), BuildInfo.VERSION + " · " + SimpleDateFormat("dd.MM.yy", Locale.US).format(Date(Prefs.long("stats_upd_at", System.currentTimeMillis()))))  // i18n-ignore
                } },
                { m -> InfoCard("Server providera", when (serverOk) { true -> tr("Radi"); false -> tr("Ne odgovara"); null -> tr("Provjeravam…") },
                    listOfNotNull(Store.active(ctx)?.name, server?.takeIf { serverOk == true }?.let { trf("odziv %s ms", it) }).joinToString(" · "),
                    R.drawable.lc_server, Color(0xFFEF4444), m, titleColor = if (serverOk == true) C.Green else if (serverOk == false) C.Danger else C.Text,
                    action = { SmallBtn(tr("↺ Reset")) { Stats.reset(listOf("rec", "stall")); rev++ } }) {
                    KV(tr("Prekidi streama"), recs.toString())
                    KV(tr("Baferovanje"), if (total > 0) String.format(Locale.US, "%.1f %%", stall * 100.0 / total) else "0 %")  // i18n-ignore
                } },
                { m -> InfoCard("Keš i memorija", if (cacheMb < 0) "…" else "$cacheMb MB", "slike, EPG, liste", R.drawable.lc_hard_drive, Color(0xFF64748B), m,
                    action = { SmallBtn(tr("✕ Očisti")) {
                        scope.launch(Dispatchers.IO) { ctx.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
                        cacheMb = 0; Toast.makeText(ctx, tr("Keš je očišćen"), Toast.LENGTH_SHORT).show()
                    } }) {
                    val rt = Runtime.getRuntime()
                    KV(tr("RAM aplikacije"), ((rt.totalMemory() - rt.freeMemory()) / 1_048_576).toString() + " MB")  // i18n-ignore
                    KV(tr("Način rada"), if (Perf.lite) "Lite" else "Full")  // i18n-ignore
                } },
                { m ->
                    val crash = remember { java.io.File(ctx.filesDir, "last_crash.txt").takeIf { it.exists() } }
                    val days = crash?.let { ((System.currentTimeMillis() - it.lastModified()) / 86_400_000L).toInt() }
                    InfoCard("Stabilnost", if (days == null || days >= 7) tr("Odlično") else tr("Bilo je rušenja"),
                        if (days == null) tr("bez rušenja") else trf("bez rušenja %s dana", days), R.drawable.lc_shield_check, Color(0xFFEC4899), m,
                        titleColor = if (days == null || days >= 7) C.Green else C.Amber,
                        action = { SmallBtn(tr("↺ Reset")) { crash?.delete(); Prefs.put("crash_times", ""); rev++ } }) {
                        KV(tr("Izvještaj o grešci"), if (crash == null) tr("nema") else tr("postoji"))
                        KV(tr("Automatski oporavak"), if (Recover.on) tr("uključen") else tr("isključen"), if (Recover.on) C.Green else C.Muted)
                    }
                },
            ))
        }
    }
    if (confirmAll) Popup("Resetuj sve brojače?", { confirmAll = false }) {
        OptionRow("Da, resetuj sve", R.drawable.lc_trash_2, color = C.Danger) { Stats.resetAll(); confirmAll = false; rev++ }
        OptionRow("Ne", R.drawable.lc_x) { confirmAll = false }
    }
}

/** Network bits: VPN on or off, a short speed test. */
object NetInfo {
    fun vpn(ctx: Context): Boolean = try {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true
    } catch (e: Exception) { false }

    suspend fun speedTest(): String? = withContext(Dispatchers.IO) {
        try {
            val t0 = System.currentTimeMillis()
            val c = java.net.URL("https://speed.cloudflare.com/__down?bytes=8000000").openConnection() as java.net.HttpURLConnection  // i18n-ignore
            c.connectTimeout = 5000; c.readTimeout = 5000
            var n = 0L; val buf = ByteArray(32768); var ping = 0L; var first = 0L
            c.inputStream.use { ins -> first = System.currentTimeMillis(); ping = first - t0; while (System.currentTimeMillis() - first < 4000) { val r = ins.read(buf); if (r < 0) break; n += r } }
            c.disconnect()
            val ms = (System.currentTimeMillis() - first).coerceAtLeast(1)
            listOf(n * 8 / 1000 / ms, " Mbit/s · ping ", ping, " ms").joinToString("")  // i18n-ignore
        } catch (e: Exception) { null }
    }
}

/* ============================================================== stream recovery */

/** Settings › Player › Automatic recovery. */
object Recover {
    val on get() = Prefs.bool("recover_on", true)
    val waitMs get() = listOf(3000L, 5000L, 10_000L)[Prefs.long("recover_wait", 1).toInt().coerceIn(0, 2)]
}

/* ============================================================== Settings › About › This device */

private data class DevCard(val label: String, val title: String, val sub: String?, val icon: Int, val color: Long, val bar: Float?, val rows: List<Pair<String, String>>)

@Composable
fun DeviceV2() {
    val ctx = LocalContext.current
    val cards by load("dev") { withContext(Dispatchers.IO) { deviceCards(ctx) } }
    var open by remember { mutableStateOf<DevCard?>(null) }
    val head = remember { DeviceInfo.head(ctx) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().clip(Rnd(20.dp)).background(C.CardHi).border(1.dp, C.Line, Rnd(20.dp)).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.size(72.dp).clip(Rnd(18.dp)).background(Brush.linearGradient(listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6)))), contentAlignment = Alignment.Center) {
                Ico(R.drawable.lc_monitor, 34.dp, Color.White)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Kicker("O uređaju", C.Muted, size = 10.sp)
                T(head.first, 26.sp, C.Text, FontWeight.ExtraBold)
                T(head.second, 13.sp, C.Soft)
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    head.third.forEach { b -> Box(Modifier.clip(Pill).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 9.dp, vertical = 3.dp)) { T(b, 11.sp, C.Text, FontWeight.Bold) } }
                }
            }
        }
        when (val st = cards) {
            is Load.Ok -> {
                val list: List<@Composable (Modifier) -> Unit> = st.value.map { c -> { m -> InfoCard(c.label, c.title, c.sub, c.icon, Color(c.color), m, bar = c.bar) { open = c } } }
                CardGrid(3, list)
            }
            is Load.Err -> Message("Greška", st.message)
            else -> Box(Modifier.fillMaxWidth().height(120.dp)) { Loading("") }
        }
    }
    open?.let { c ->
        Popup(c.label, { open = null }, width = 640.dp) {
            c.rows.forEach { (k, v) ->
                Row(Modifier.fillMaxWidth().clip(Rnd(10.dp)).background(C.Rail).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(k, 13.sp, C.Muted, modifier = Modifier.width(190.dp))
                    T(v, 13.sp, C.Text, FontWeight.SemiBold, maxLines = 3, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Device cards with a short line on top and every detail behind OK. */
private fun deviceCards(ctx: Context): List<DevCard> {
    fun gb(b: Long) = String.format(Locale.US, "%.1f GB", b / 1_073_741_824.0)  // i18n-ignore
    val out = ArrayList<DevCard>()
    val cores = Runtime.getRuntime().availableProcessors()
    val soc = if (Build.VERSION.SDK_INT >= 31) listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL).filter { it.isNotBlank() && it != Build.UNKNOWN }.joinToString(" ") else ""
    val ghz = DeviceInfo.maxGhz()
    val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: ""
    out += DevCard(tr("Procesor"), soc.ifBlank { Build.HARDWARE }, listOfNotNull("$cores " + tr("jezgre"), ghz, abi).joinToString(" · "), R.drawable.lc_layers, 0xFF3B82F6, null,
        listOfNotNull(tr("Čip") to soc.ifBlank { Build.HARDWARE }, tr("Jezgre") to cores.toString(), ghz?.let { tr("Najveći takt") to it },
            tr("Arhitektura") to Build.SUPPORTED_ABIS.joinToString(", "), tr("Platforma") to Build.BOARD))
    val gpu = DeviceInfo.gpu()
    out += DevCard(tr("Grafika"), gpu?.substringBefore(" · ") ?: "—", gpu?.substringAfter(" · ", "")?.ifBlank { null }, R.drawable.lc_sparkles, 0xFF8B5CF6, null,
        listOfNotNull(tr("Grafički procesor") to (gpu?.substringBefore(" · ") ?: "—"), gpu?.substringAfter(" · ", "")?.ifBlank { null }?.let { "OpenGL" to it }))  // i18n-ignore
    val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    val mi = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
    val rt = Runtime.getRuntime()
    out += DevCard("RAM", gb(mi.totalMem), trf("slobodno %s", gb(mi.availMem)), R.drawable.lc_gauge2, 0xFF10B981, 1f - mi.availMem.toFloat() / mi.totalMem,  // i18n-ignore
        listOf(tr("Ukupno") to gb(mi.totalMem), tr("Slobodno") to gb(mi.availMem), tr("Aplikacija koristi") to ((rt.totalMemory() - rt.freeMemory()) / 1_048_576).toString() + " MB",  // i18n-ignore
            tr("Slaba memorija") to tr(if (mi.lowMemory) "da" else "ne")))
    try {
        val st = android.os.StatFs(android.os.Environment.getDataDirectory().path)
        val appData = ctx.filesDir.parentFile?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
        out += DevCard(tr("Prostor"), gb(st.totalBytes), trf("slobodno %s", gb(st.availableBytes)), R.drawable.lc_hard_drive, 0xFFF59E0B, 1f - st.availableBytes.toFloat() / st.totalBytes,
            listOf(tr("Ukupno") to gb(st.totalBytes), tr("Slobodno") to gb(st.availableBytes), tr("PRETORIAN podaci") to (appData / 1_048_576).toString() + " MB"))  // i18n-ignore
    } catch (_: Exception) {}
    try {
        val d = (ctx.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager).getDisplay(android.view.Display.DEFAULT_DISPLAY)
        val mode = d.mode
        val hdr = if (Build.VERSION.SDK_INT >= 24) d.hdrCapabilities?.supportedHdrTypes?.map { t -> when (t) { 1 -> "Dolby Vision"; 2 -> "HDR10"; 3 -> "HLG"; 4 -> "HDR10+"; else -> "HDR" } }?.distinct()?.joinToString(" · ") else null  // i18n-ignore
        val modes = d.supportedModes.map { listOf(it.physicalWidth, "×", it.physicalHeight, " ", it.refreshRate.toInt(), " Hz").joinToString("") }.distinct()  // i18n-ignore
        out += DevCard(tr("Ekran"), listOf(mode.physicalWidth, " × ", mode.physicalHeight, " · ", mode.refreshRate.toInt(), " Hz").joinToString(""), hdr?.ifBlank { null } ?: tr("bez HDR-a"),  // i18n-ignore
            R.drawable.lc_monitor, 0xFFEC4899, null,
            listOf(tr("Rezolucija") to listOf(mode.physicalWidth, "×", mode.physicalHeight).joinToString(""), tr("Osvježavanje") to listOf(mode.refreshRate.toInt(), " Hz").joinToString(""),  // i18n-ignore
                "HDR" to (hdr?.ifBlank { null } ?: tr("nema")), tr("Gustoća") to ctx.resources.displayMetrics.densityDpi.toString() + " dpi", tr("Podržani načini") to modes.take(8).joinToString(", ")))  // i18n-ignore
    } catch (_: Exception) {}
    try {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val net = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(net)
        val lp = cm.getLinkProperties(net)
        val kind = when {
            caps == null -> tr("nije povezano")
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"  // i18n-ignore
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"  // i18n-ignore
            else -> tr("drugo")
        }
        val ip = lp?.linkAddresses?.firstOrNull { it.address is java.net.Inet4Address }?.address?.hostAddress
        val down = caps?.linkDownstreamBandwidthKbps?.takeIf { it > 0 }?.let { if (it >= 1_000_000) listOf(it / 1_000_000, " Gbit/s").joinToString("") else listOf(it / 1000, " Mbit/s").joinToString("") }  // i18n-ignore
        val vpn = caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true
        out += DevCard(tr("Mreža"), kind, listOfNotNull(down, ip).joinToString(" · ").ifBlank { null }, R.drawable.lc_wifi, 0xFF06B6D4, null,
            listOfNotNull(tr("Veza") to kind, ip?.let { tr("IP adresa") to it }, down?.let { tr("Brzina veze") to it },
                lp?.dnsServers?.takeIf { it.isNotEmpty() }?.let { "DNS" to it.mapNotNull { a -> a.hostAddress }.joinToString(", ") },  // i18n-ignore
                "VPN" to tr(if (vpn) "aktivan" else "nije aktivan")))
    } catch (_: Exception) {}
    val tvOs = if (ctx.packageManager.hasSystemFeature("com.google.android.feature.GOOGLE_EXPERIENCE") || ctx.packageManager.hasSystemFeature("com.google.android.tv.installed")) "Google TV" else "Android TV"  // i18n-ignore
    out += DevCard(tr("Sistem"), listOf(tvOs, " · Android ", Build.VERSION.RELEASE).joinToString(""), listOf("API ", Build.VERSION.SDK_INT).joinToString(""), R.drawable.lc_smartphone, 0xFF22C55E, null,  // i18n-ignore
        listOf(tr("Operativni sistem") to tvOs, "Android" to Build.VERSION.RELEASE, "API" to Build.VERSION.SDK_INT.toString(), tr("Build") to Build.DISPLAY,  // i18n-ignore
            tr("Kernel") to (System.getProperty("os.version") ?: "—")))
    val patch = Build.VERSION.SECURITY_PATCH
    val patchNice = try { SimpleDateFormat("d. MMMM yyyy", Locale(Look.lang)).format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(patch)!!) } catch (e: Exception) { patch }  // i18n-ignore
    out += DevCard(tr("Sigurnosna zakrpa"), patchNice, null, R.drawable.lc_shield_check, 0xFFEF4444, null, listOf(tr("Sigurnosna zakrpa") to patch))
    out += DevCard(tr("Način rada"), if (Perf.lite) "Lite" else "Full", "PRETORIAN " + BuildInfo.VERSION, R.drawable.lc_sparkle, 0xFF9CA3AF, null,  // i18n-ignore
        listOf(tr("Način rada") to (if (Perf.lite) "Lite" else "Full"), tr("Verzija") to BuildInfo.VERSION, tr("Slabiji uređaj") to tr(if (Perf.weak) "da" else "ne")))  // i18n-ignore
    return out
}
