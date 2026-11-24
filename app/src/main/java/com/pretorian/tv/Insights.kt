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
        // a new version, also the first one that keeps these counters (whatsnew_seen shows it is not a brand new install)
        if (last != BuildInfo.VERSION && (last.isNotBlank() || Prefs.str("whatsnew_seen").isNotBlank())) inc(day(), "upd", 1)
        if (last != BuildInfo.VERSION && last.isNotBlank()) UpdLog.add(trf("Pokrenuta nova verzija %s (prije %s)", BuildInfo.VERSION, last))
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
    /** every kept day: the total since the last Reset of that counter */
    fun sumAll(k: String): Long { val days = root().optJSONObject("d") ?: return 0; return days.keys().asSequence().mapNotNull { days.optJSONObject(it) }.sumOf { it.optLong(k) } }
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

/* ============================================================== shared card */

@Composable
fun InfoCard(label: String, title: String, sub: String?, icon: Int, color: Color, modifier: Modifier = Modifier, minH: Dp = 80.dp,
             bar: Float? = null, onClick: (() -> Unit)? = null, titleColor: Color = C.Text, action: (@Composable () -> Unit)? = null,
             stats: List<Triple<String, String, Color?>>? = null, extra: (@Composable ColumnScope.() -> Unit)? = null) {
    val inner: @Composable (Boolean) -> Unit = { f ->
        // the card follows its width: icon and letters grow on a wide card and get smaller (to a minimum) on a narrow one;
        // on a wide card the numbers stand in columns next to the title instead of one long list
        run {
            val cw = LocalCardW.current
            val k = (cw / 620.dp).coerceIn(0.82f, 1.45f)
            val wide = cw >= 640.dp
            Row(Modifier.fillMaxWidth().then(if (f) Modifier.border(2.dp, Color.White, Rnd(16.dp)) else Modifier).padding(horizontal = 14.dp * k, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp * k)) {
                Box(Modifier.size(34.dp * k).clip(Rnd(10.dp * k)).background(color), contentAlignment = Alignment.Center) { Ico(icon, 17.dp * k, Color.White) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FitText(tr(label).uppercase(), 10.sp * k, C.Muted, FontWeight.SemiBold, Modifier.weight(1f), spacing = 1.5f)
                        action?.invoke()
                    }
                    if (wide && stats != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Column(Modifier.weight(1f)) {
                            FitText(tr(title), 16.sp * k, titleColor, FontWeight.ExtraBold)
                            sub?.let { FitText(tr(it), 12.sp * k, C.Soft) }
                        }
                        Box(Modifier.weight(1.3f)) { StatCols(stats, k, cw * 0.5f) }
                    } else {
                        // one line each, the letters get a little smaller instead of breaking into a second line
                        FitText(tr(title), 16.sp * k, titleColor, FontWeight.ExtraBold)
                        sub?.let { FitText(tr(it), 12.sp * k, C.Soft) }
                        if (stats != null) Box(Modifier.padding(top = 4.dp)) { StatCols(stats, k, cw - 80.dp) }
                    }
                    if (bar != null) ThinBar(bar, color, Modifier.fillMaxWidth().padding(top = 6.dp), 4.dp)
                    extra?.invoke(this)
                }
                if (onClick != null) T("›", 18.sp, C.Muted, modifier = Modifier.align(Alignment.CenterVertically))  // i18n-ignore
            }
        }
    }
    val m = modifier.heightIn(min = minH).clip(Rnd(16.dp)).border(1.dp, C.Line, Rnd(16.dp))
    if (onClick != null) Focusable(onClick = onClick, shape = Rnd(16.dp), normal = C.Card, focused = C.CardHi, zoom = 1.02f, modifier = m) { f -> inner(f) }
    else Box(m.background(C.Card)) { inner(false) }
}

/** The width a card gets (set by the page or the card grid), so cards adapt without measuring themselves. */
val LocalCardW = androidx.compose.runtime.compositionLocalOf { 600.dp }

/** Numbers of a card side by side: the name small above, the number big under it; too narrow for that → name left, number right. */
@Composable
fun StatCols(items: List<Triple<String, String, Color?>>, k: Float = 1f, room: Dp = 400.dp) {
    run {
        if (room >= 110.dp * items.size) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items.forEach { (l, v, c) ->
                Column(Modifier.weight(1f)) {
                    FitText(tr(l), 11.sp * k, C.Muted, FontWeight.SemiBold)
                    FitText(v, 17.sp * k, c ?: C.Text, FontWeight.ExtraBold)
                }
            }
        } else Column { items.forEach { (l, v, c) -> KV(tr(l), v, c ?: C.Text) } }
    }
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
fun CardGrid(want: Int, items: List<@Composable (Modifier) -> Unit>) {
    // as many columns as fit: a card is never narrower than about 230 dp, so big text never breaks
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
    val n = (((maxWidth + 12.dp) / (230.dp + 12.dp)).toInt()).coerceIn(1, want)
    val cw = (maxWidth - 12.dp * (n - 1)) / n
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(n).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.runtime.CompositionLocalProvider(LocalCardW provides cw) { row.forEach { it(Modifier.weight(1f).fillMaxHeight()) } }
                repeat(n - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    }
}

/* ============================================================== Settings › Diagnostics */

/** Diagnostics: only stability and the stream log (approved 22.11.) */
const val DIAG_SIMPLE = true

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
    var logAll by remember { mutableStateOf(false) }
    var streamAll by remember { mutableStateOf(false) }
    var logExport by remember { mutableStateOf(false) }
    var reportExport by remember { mutableStateOf(false) }
    val vpn = remember { NetInfo.vpn(ctx) }
    var testingNorm by remember { mutableStateOf(false) }
    var bypassErr by remember { mutableStateOf(false) }
    /** [normal]: the speed without the VPN – while the VPN is on it goes past it over Wi-Fi/cable, if the VPN allows that */
    fun runSpeed(normal: Boolean) {
        if (testing) return
        testing = true; testingNorm = normal; speed = null; bypassErr = false
        scope.launch {
            val direct = normal && vpn
            val r = NetInfo.speedTest(ctx, direct)
            speed = r
            val asVpn = vpn && !normal
            if (r != null) { Prefs.put(if (asVpn) "speed_vpn" else "speed_norm", r)
                Prefs.put(if (asVpn) "speed_vpn_at" else "speed_norm_at", SimpleDateFormat("dd.MM. HH:mm", Locale.US).format(Date())) }  // i18n-ignore
            else if (direct) bypassErr = true
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
        runSpeed(!vpn)
    }
    val periods = listOf("Danas", "7 dana", "Mjesec", "Godina")
    val pw = listOf("danas", "7 dana", "ovaj mjesec", "ova godina")[period]
    // the counters change every minute: the page is redrawn in place (not rebuilt), so the focus stays where you are
    @Suppress("UNUSED_VARIABLE") val statsTick = Stats.rev
    key(rev) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        androidx.compose.runtime.CompositionLocalProvider(LocalCardW provides maxWidth) {
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
            // A0: the safe TXT report for a phone (QR), instead of photos of the TV
            SetRow("Izvezi izvještaj", "Siguran TXT izvještaj za telefon ili folder, bez lozinki", arrow = "›") { reportExport = true }
            val tv = Stats.sum("tv", period); val ser = Stats.sum("ser", period); val mov = Stats.sum("mov", period)
            val total = tv + ser + mov
            // ---- watched (two columns wide) and uptime
            // every card full width, one under the other, so all the text can be read
            // Diagnostics shows only what tells what went wrong (approved): stability and the stream log; device facts live in About
            if (!DIAG_SIMPLE) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoCard("Gledano", fmtDur(total), trf("ukupno · %s", tr(pw)), R.drawable.lc_play, Silver.TextSoft, Modifier.fillMaxWidth(),
                    action = { SmallBtn(tr("↺ Reset")) { Stats.reset(listOf("tv", "ser", "mov", "eps", "films", "sea", "ch")); rev++ } }) {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                        listOf(Stats.sum("eps", period).toString() to "epizoda", Stats.seasons(period).toString() to "sezona",
                            Stats.sum("films", period).toString() to "filmova", fmtDur(tv) to "TV kanali").forEach { (v, l) ->
                            Column { T(v, 16.sp, C.Text, FontWeight.ExtraBold); T(l, 11.sp, C.Muted) }
                        }
                    }
                    if (total > 0) Row(Modifier.fillMaxWidth().padding(top = 8.dp).height(6.dp).clip(Pill)) {
                        listOf(tv to Silver.TextSoft, ser to Silver.TextSoft, mov to Color(0xFFF59E0B)).filter { it.first > 0 }.forEach { (v, c) ->
                            Box(Modifier.weight(v.toFloat()).fillMaxHeight().background(c))
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        fun pct(v: Long) = if (total > 0) (v * 100 / total).toString() + " %" else "0 %"
                        T("● TV " + pct(tv), 11.sp, Silver.TextSoft); T("● " + tr("Serije") + " " + pct(ser), 11.sp, Silver.TextSoft)
                        T("● " + tr("Filmovi") + " " + pct(mov), 11.sp, Color(0xFFF59E0B))
                        Spacer(Modifier.weight(1f))
                        Stats.topChannel(period)?.let { T(tr("Najviše") + ": " + it, 11.sp, C.Text, FontWeight.Bold) }
                    }
                }
                InfoCard("Uptime", fmtDur(System.currentTimeMillis() - Stats.appStart), tr("od zadnjeg pokretanja"), R.drawable.lc_clock, Color(0xFF22C55E),
                    Modifier.fillMaxWidth(), action = { SmallBtn(tr("↺ Reset")) { Stats.reset(listOf("up")); rev++ } },
                    stats = Prefs.str("crash_times").split(',').count { it.isNotBlank() }.let { crashes ->
                        listOf(Triple("Ukupno", fmtDur(Stats.totalUptime()), null), Triple("Rušenja (10 min)", crashes.toString(), if (crashes == 0) C.Green else C.Danger)) })
            }
            val bTv = Stats.sum("b_tv", period); val bSer = Stats.sum("b_ser", period); val bMov = Stats.sum("b_mov", period)
            val updW = Stats.sum("upd", 1); val updM = Stats.sum("upd", 2); val updY = Stats.sum("upd", 3)
            val recs = Stats.sum("rec", period)
            val stall = Stats.sum("stall", period)
            CardGrid(1, listOf<@Composable (Modifier) -> Unit>(
                { m ->
                    // today on the left (with TV / Series / Movies under it), the total since the last Reset on the right
                    val dTv = Stats.sum("b_tv", 0); val dSer = Stats.sum("b_ser", 0); val dMov = Stats.sum("b_mov", 0)
                    val aTv = Stats.sumAll("b_tv"); val aSer = Stats.sumAll("b_ser"); val aMov = Stats.sumAll("b_mov")
                    val since = Prefs.long("b_reset_at", 0L)
                    InfoCard("Potrošeno", tr("Danas") + " " + fmtGb(dTv + dSer + dMov), "TV " + fmtGb(dTv) + " · " + tr("Serije") + " " + fmtGb(dSer) + " · " + tr("Filmovi") + " " + fmtGb(dMov),  // i18n-ignore
                        R.drawable.lc_download, Color(0xFFF59E0B), m,
                        action = { SmallBtn(tr("↺ Reset")) { Stats.reset(listOf("b_tv", "b_ser", "b_mov")); Prefs.put("b_reset_at", System.currentTimeMillis()); rev++ } },
                        stats = listOf(Triple(if (since > 0) trf("Ukupno od %s", SimpleDateFormat("dd.MM. HH:mm", Locale.US).format(Date(since))) else tr("Ukupno od reseta"), fmtGb(aTv + aSer + aMov), C.Amber),  // i18n-ignore
                            Triple("TV", fmtGb(aTv), null), Triple("Serije", fmtGb(aSer), null), Triple("Filmovi", fmtGb(aMov), null)))
                },
                { m -> InfoCard("Brzina interneta", if (vpn) tr("VPN je uključen") else tr("bez VPN-a"), null, R.drawable.lc_gauge2, Silver.TextSoft, m) {
                    // two rows, each with its own test: the VPN row only measures while a VPN is on
                    val norm = Prefs.str("speed_norm"); val vp = Prefs.str("speed_vpn")
                    run {
                    val two = LocalCardW.current >= 760.dp
                    val content: @Composable (Modifier) -> Unit = { rm ->
                    Box(rm) { SpeedRow("Normalna brzina", if (testing && testingNorm) tr("Mjerim…") else norm.ifBlank { "—" },
                        if (bypassErr) tr("Tvoj VPN ne pušta mjerenje mimo njega · ugasi VPN na tren za novo mjerenje")
                        else (Prefs.str("speed_norm_at").ifBlank { null }?.let { tr("izmjereno") + " " + it + if (vpn) " · " + tr("mimo VPN-a") else "" } ?: ""),
                        enabled = !testing) { runSpeed(true) } }
                    Box(rm) { SpeedRow("VPN brzina", if (!vpn) tr("VPN nije aktivan") else if (testing && !testingNorm) tr("Mjerim…") else vp.ifBlank { "—" },
                        if (!vpn) tr("uključi VPN na boxu pa testiraj") else (Prefs.str("speed_vpn_at").ifBlank { null }?.let { tr("izmjereno") + " " + it } ?: ""),
                        enabled = vpn && !testing, dim = !vpn) { runSpeed(false) } }
                    }
                    if (two) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { content(Modifier.weight(1f)) } else Column { content(Modifier.fillMaxWidth()) }
                    }
                } },
                { m -> InfoCard("Ažuriranja", trf("%s ovaj mjesec", updM), null, R.drawable.lc_refresh_cw, Silver.TextSoft, m,
                    action = { SmallBtn(tr("↺ Reset")) { Stats.reset(listOf("upd")); rev++ } },
                    stats = listOf(Triple("Sedmica", updW.toString(), null), Triple("Godina", updY.toString(), null),
                        Triple("Zadnje", BuildInfo.VERSION + " · " + SimpleDateFormat("dd.MM.yy", Locale.US).format(Date(Prefs.long("stats_upd_at", System.currentTimeMillis()))), null))) },  // i18n-ignore
                { m -> InfoCard("Server providera", when (serverOk) { true -> tr("Radi"); false -> tr("Ne odgovara"); null -> tr("Provjeravam…") },
                    listOfNotNull(Store.active(ctx)?.name, server?.takeIf { serverOk == true }?.let { trf("odziv %s ms", it) }).joinToString(" · "),
                    R.drawable.lc_server, Color(0xFFEF4444), m, titleColor = if (serverOk == true) C.Green else if (serverOk == false) C.Danger else C.Text,
                    action = { SmallBtn(tr("↺ Reset")) { Stats.reset(listOf("rec", "stall")); rev++ } },
                    stats = listOf(Triple("Prekidi streama", recs.toString(), null),
                        Triple("Baferovanje", if (total > 0) String.format(Locale.US, "%.1f %%", stall * 100.0 / total) else "0 %", null))) },  // i18n-ignore
                { m -> InfoCard("Keš i memorija", if (cacheMb < 0) "…" else "$cacheMb MB", tr("slike, EPG, liste"), R.drawable.lc_hard_drive, C.Muted, m,
                    action = { SmallBtn(tr("✕ Očisti")) {
                        scope.launch(Dispatchers.IO) { ctx.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
                        cacheMb = 0; Toast.makeText(ctx, tr("Keš je očišćen"), Toast.LENGTH_SHORT).show()
                    } }, stats = Runtime.getRuntime().let { rt -> listOf(Triple("RAM aplikacije", ((rt.totalMemory() - rt.freeMemory()) / 1_048_576).toString() + " MB", null),  // i18n-ignore
                        Triple("Način rada", if (Perf.lite) "Lite" else "Full", null)) }) },  // i18n-ignore
                { m ->
                    val crash = remember { java.io.File(ctx.filesDir, "last_crash.txt").takeIf { it.exists() } }
                    val days = crash?.let { ((System.currentTimeMillis() - it.lastModified()) / 86_400_000L).toInt() }
                    InfoCard("Stabilnost", if (days == null || days >= 7) tr("Odlično") else tr("Bilo je rušenja"),
                        if (days == null) tr("bez rušenja") else trf("bez rušenja %s dana", days), R.drawable.lc_shield_check, Silver.TextSoft, m,
                        titleColor = if (days == null || days >= 7) C.Green else C.Amber,
                        action = { SmallBtn(tr("↺ Reset")) { crash?.delete(); Prefs.put("crash_times", ""); rev++ } },
                        stats = listOf(Triple("Izvještaj o grešci", tr(if (crash == null) "nema" else "postoji"), null),
                            Triple("Automatski oporavak", tr(if (Recover.on) "uključen" else "isključen"), if (Recover.on) C.Green else C.Muted)))
                },
            ).let { if (DIAG_SIMPLE) it.takeLast(1) else it })
            // every step of the last updates, with the time: shows exactly where an update stops
            if (!DIAG_SIMPLE) key(UpdLog.rev) {
                val log = UpdLog.lines()
                InfoCard("Zapis ažuriranja", log.lastOrNull()?.substringAfter("  ") ?: tr("Još nema zapisa"), log.lastOrNull()?.substringBefore("  "),
                    R.drawable.lc_file_text, Silver.TextSoft, Modifier.fillMaxWidth(),
                    action = { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SmallBtn(tr("Prikaži sve")) { logAll = true }
                        SmallBtn(tr("Izvezi")) { logExport = true }
                        SmallBtn(tr("Obriši")) { UpdLog.clear() }
                    } }) {
                    log.takeLast(8).reversed().drop(1).forEach { T(it, 11.sp, C.Muted) }  // i18n-ignore
                }
            }
            // how often channels froze, whether the app had to step in, and on which channels
            key(StreamLog.rev) {
                val ev = StreamLog.events()
                val now = System.currentTimeMillis()
                fun cnt(ms: Long, k: String) = ev.count { it.kind == k && now - it.t < ms }
                val day = 86_400_000L
                InfoCard("Zapis streama", trf("%s zastoja danas", cnt(day, "freeze")), trf("7 dana: %s · 30 dana: %s", cnt(7 * day, "freeze"), cnt(30 * day, "freeze")),
                    R.drawable.lc_tv, Color(0xFFEF4444), Modifier.fillMaxWidth(),
                    action = { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SmallBtn(tr("Prikaži sve")) { streamAll = true }
                        SmallBtn(tr("Obriši zapis")) { StreamLog.clear() }
                    } }) {
                    val fz = ev.count { it.kind == "freeze" }; val ok = ev.count { it.kind == "ok" }
                    KV(tr("Aplikacija reagovala"), trf("%s puta", ev.count { it.kind == "try" }))
                    KV(tr("Proradilo samo"), trf("%s od %s", ok.coerceAtMost(fz), fz), C.Green)
                    KV(tr("Nije proradilo"), (fz - ok).coerceAtLeast(0).toString(), if (fz - ok > 0) C.Danger else C.Muted)
                    val top = ev.filter { it.kind == "freeze" }.groupingBy { it.ch }.eachCount().entries.sortedByDescending { it.value }.take(5)
                    if (top.isNotEmpty()) { T("Kanali koji najčešće zastaju", 12.sp, C.Soft, FontWeight.SemiBold); top.forEach { (ch, n) -> KV(ch, trf("%s×", n)) } }
                    SetDrop("Briši samo", "da zapis ne puni memoriju", StreamLog.CLEAR, StreamLog.clearMode) { Prefs.put("stream_clear", it.toLong()); StreamLog.rev++ }
                }
            }
        }
        }
        }
    }
    if (streamAll) Popup("Zapis streama", { streamAll = false }, width = 820.dp) {
        val l = StreamLog.lines()
        if (l.isEmpty()) T("Još nema zapisa.", 14.sp, C.Muted)
        l.reversed().forEach { T(it, 12.sp, C.Soft, maxLines = 3) }  // i18n-ignore
    }
    if (logAll) Popup("Zapis ažuriranja", { logAll = false }, width = 820.dp) {
        val l = UpdLog.lines()
        if (l.isEmpty()) T("Još nema zapisa.", 14.sp, C.Muted)
        l.reversed().forEach { T(it, 12.sp, C.Soft, maxLines = 3) }  // i18n-ignore
    }
    if (logExport) FileBrowser(tr("Izaberi folder"), emptySet(), pickDir = true, onDismiss = { logExport = false }) { dir ->
        try {
            val f = java.io.File(dir, "pretorian_update_log_" + System.currentTimeMillis() / 1000 + ".txt")
            f.writeText("Media Center " + BuildInfo.VERSION + " · " + Build.MODEL + " · Android " + Build.VERSION.RELEASE + "\n\n" + UpdLog.lines().joinToString("\n"))  // i18n-ignore
            Toast.makeText(ctx, trf("Spremljeno: %s", f.path), Toast.LENGTH_LONG).show()
        } catch (e: Exception) { Toast.makeText(ctx, tr("U taj folder se ne može pisati. Izaberi drugi ili folder aplikacije."), Toast.LENGTH_LONG).show() }
        logExport = false
    }
    if (reportExport) DiagExportDialog { reportExport = false }
    if (confirmAll) Popup("Resetuj sve brojače?", { confirmAll = false }) {
        OptionRow("Da, resetuj sve", R.drawable.lc_trash_2, color = C.Danger) { Stats.resetAll(); confirmAll = false; rev++ }
        OptionRow("Ne", R.drawable.lc_x) { confirmAll = false }
    }
}

/** Network bits: VPN on or off, a short speed test. */
object NetInfo {
    fun vpn(ctx: Context): Boolean = try {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val c = cm.getNetworkCapabilities(cm.activeNetwork)
        c != null && c.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) && !c.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
    } catch (e: Exception) { false }

    /** Mbit/s and ping in ms, or null */
    suspend fun measure(): Pair<Long, Long>? = withContext(Dispatchers.IO) {
        try {
            val t0 = System.currentTimeMillis()
            val c = java.net.URL("https://speed.cloudflare.com/__down?bytes=8000000").openConnection() as java.net.HttpURLConnection  // i18n-ignore
            c.connectTimeout = 5000; c.readTimeout = 5000
            var n = 0L; val buf = ByteArray(32768); var ping = 0L; var first = 0L
            c.inputStream.use { ins -> first = System.currentTimeMillis(); ping = first - t0; while (System.currentTimeMillis() - first < 3000) { val r = ins.read(buf); if (r < 0) break; n += r } }
            c.disconnect()
            val ms = (System.currentTimeMillis() - first).coerceAtLeast(1)
            (n * 8 / 1000 / ms) to ping
        } catch (e: Exception) { null }
    }

    /** the Wi-Fi or cable network under the VPN (null when there is none) */
    fun underlying(ctx: Context): android.net.Network? = try {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        @Suppress("DEPRECATION")
        cm.allNetworks.firstOrNull { n -> cm.getNetworkCapabilities(n)?.let { c ->
            c.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN) && c.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) } == true }
    } catch (e: Exception) { null }

    /** [direct]: past the VPN, straight over Wi-Fi/cable – works only when the VPN app allows it */
    suspend fun speedTest(ctx: Context? = null, direct: Boolean = false): String? = withContext(Dispatchers.IO) {
        try {
            val t0 = System.currentTimeMillis()
            val url = java.net.URL("https://speed.cloudflare.com/__down?bytes=8000000")  // i18n-ignore
            val net = if (direct && ctx != null) underlying(ctx) ?: return@withContext null else null
            val c = (net?.openConnection(url) ?: url.openConnection()) as java.net.HttpURLConnection
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
    /** 0 automatic, 1 = 2 s, 2 = 3 s, 3 = 5 s */
    val choice get() = Prefs.long("recover_wait2", 0).toInt().coerceIn(0, 3)
    val waitMs get() = when (choice) { 1 -> 2000L; 2 -> 3000L; 3 -> 5000L; else -> NetProfile.autoWait }
    val OPTIONS = listOf("Automatski", "2 s", "3 s", "5 s")
}

/** Player buffer: automatic from the box and the internet, or chosen by hand. */
object Buffer {
    /** 0 automatic, 1 small 32 MB, 2 medium 64 MB, 3 large 96 MB */
    val choice get() = Prefs.long("buffer_mb", 0).toInt().coerceIn(0, 3)
    val mb get() = when (choice) { 1 -> 32; 2 -> 64; 3 -> 96; else -> NetProfile.autoBuffer }
    val OPTIONS = listOf("Automatski", "Mali · 32 MB", "Srednji · 64 MB", "Veliki · 96 MB")
}

/** What the app knows about this box's internet: last speed and how often streams stopped. */
object NetProfile {
    val mbps get() = Prefs.long("net_mbps", 0)
    /** share of watching time spent buffering, last 7 days (0..1) */
    fun stallShare(): Double { val w = Stats.sum("tv", 1) + Stats.sum("ser", 1) + Stats.sum("mov", 1); return if (w < 600_000) 0.0 else Stats.sum("stall", 1).toDouble() / w }
    private val slow get() = (mbps in 1..9) || stallShare() > 0.05
    val autoBuffer get() = if (Perf.lite) 32 else if (slow) 96 else 64
    val autoWait get() = if (Perf.lite) 3000L else if (slow) 5000L else if (mbps >= 30 && stallShare() < 0.02) 2000L else 3000L
    /** "Full box · 113 Mbit/s · stabilno" */
    fun why(): String = listOf(if (Perf.lite) "Lite box" else "Full box", if (mbps > 0) listOf(mbps, " Mbit/s").joinToString("") else tr("brzina još nije izmjerena"),  // i18n-ignore
        tr(if (slow) "nestabilno" else "stabilno")).joinToString(" · ")
    /** speed test now and then: first start, a new network, or a week later */
    suspend fun refresh(ctx: Context) {
        val kind = try { val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            cm.getNetworkCapabilities(cm.activeNetwork)?.let { c -> listOf(1, 3, 4).filter { c.hasTransport(it) }.joinToString(",") } ?: "" } catch (e: Exception) { "" }
        val old = System.currentTimeMillis() - Prefs.long("net_test_at") > 7 * 86_400_000L
        if (!old && kind == Prefs.str("net_kind") && mbps > 0) return
        val r = NetInfo.measure() ?: return
        Prefs.put("net_mbps", r.first); Prefs.put("net_test_at", System.currentTimeMillis()); Prefs.put("net_kind", kind)
    }
}

/** Every freeze of a stream and what the automatic recovery did, for Settings › Diagnostics (the last 50). */
object StreamLog {
    var rev by mutableIntStateOf(0)
    data class Ev(val t: Long, val kind: String, val ch: String)
    /** never, every 24 h, monthly, yearly, when the app closes (= at the next start) */
    val CLEAR = listOf("Nikad", "Svaka 24 sata", "Mjesečno", "Godišnje", "Kad zatvorim aplikaciju")
    val clearMode get() = Prefs.long("stream_clear", 0).toInt().coerceIn(0, 4)
    fun add(text: String) {
        val t = SimpleDateFormat("dd.MM. HH:mm:ss", Locale.US).format(Date())  // i18n-ignore
        val lines = (Prefs.str("stream_log").split('\n').filter { it.isNotBlank() } + (t + "  " + text)).takeLast(300)
        Prefs.put("stream_log", lines.joinToString("\n")); rev++
    }
    /** freeze = a stream stopped (first try), try = every reconnect, ok = it played again */
    fun event(kind: String, ch: String) {
        val l = (Prefs.str("stream_ev").split('\n').filter { it.isNotBlank() } + listOf(System.currentTimeMillis(), kind, ch.replace('|', '/').replace('\n', ' ')).joinToString("|")).takeLast(500)
        Prefs.put("stream_ev", l.joinToString("\n")); rev++
    }
    fun events() = Prefs.str("stream_ev").split('\n').mapNotNull { l -> l.split('|').takeIf { it.size >= 3 }?.let { p -> p[0].toLongOrNull()?.let { Ev(it, p[1], p[2]) } } }
    fun lines() = Prefs.str("stream_log").split('\n').filter { it.isNotBlank() }
    fun clear() { Prefs.put("stream_log", ""); Prefs.put("stream_ev", ""); Prefs.put("stream_cleared", System.currentTimeMillis()); rev++ }
    /** at start: clears by itself as chosen in Diagnostics */
    fun autoClear() {
        val since = System.currentTimeMillis() - Prefs.long("stream_cleared", System.currentTimeMillis()).also { if (Prefs.long("stream_cleared") == 0L) Prefs.put("stream_cleared", System.currentTimeMillis()) }
        val day = 86_400_000L
        if (when (clearMode) { 1 -> since > day; 2 -> since > 30 * day; 3 -> since > 365 * day; 4 -> true; else -> false }) clear()
    }
}

/* ============================================================== Settings › About › This device */

private data class DevCard(val label: String, val title: String, val sub: String?, val icon: Int, val color: Long, val bar: Float?, val rows: List<Pair<String, String>>)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DeviceV2() {
    val ctx = LocalContext.current
    val cards by load("dev") { withContext(Dispatchers.IO) { deviceCards(ctx) } }
    var open by remember { mutableStateOf<DevCard?>(null) }
    val head = remember { DeviceInfo.head(ctx) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    // FIX 3: rows scroll whole, with half a box of margin at the bottom
    val spec = remember { SetBringSpec(with(density) { 56.dp.toPx() }) }
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides spec) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 10.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // a low hero: one line of name, one of version, small badges
        Row(Modifier.fillMaxWidth().clip(Rnd(18.dp)).background(C.Card).border(1.dp, C.Line, Rnd(18.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(48.dp).clip(Rnd(14.dp)).background(Color.White.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                Ico(R.drawable.lc_monitor, 24.dp, Color.White)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                T(head.first, 20.sp, C.Text, FontWeight.ExtraBold)
                T(head.second, 13.sp, C.Soft)
            }
            head.third.take(3).forEach { b -> Box(Modifier.clip(Pill).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 9.dp, vertical = 3.dp)) { T(b, 11.sp, C.Text, FontWeight.Bold) } }
        }
        when (val st = cards) {
            is Load.Ok -> st.value.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { c -> DevBox(c, Modifier.weight(1f)) { open = c } }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            is Load.Err -> Message("Greška", st.message)
            else -> Box(Modifier.fillMaxWidth().height(120.dp)) { Loading("") }
        }
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

/** One equal box of "This device": short label, one line of value, one line of detail (cut with …), an optional bar.
 *  Focus = the calm pill, no zoom, one hairline only. */
@Composable
private fun DevBox(c: DevCard, modifier: Modifier, onClick: () -> Unit) {
    Focusable(onClick = onClick, shape = Rnd(16.dp), normal = C.Card, focused = Silver.Focus, zoom = 1f, borderColor = C.Line,
        modifier = modifier.height(104.dp)) { _ ->
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(34.dp).clip(Rnd(10.dp)).background(Color.White.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) { Ico(c.icon, 18.dp, Color.White) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                T(c.label.uppercase(), 10.sp, C.Muted, FontWeight.SemiBold)
                T(c.title, 16.sp, C.Text, FontWeight.ExtraBold)
                T(c.sub ?: " ", 12.sp, C.Soft)
                if (c.bar != null) ThinBar(c.bar, Color(c.color), Modifier.fillMaxWidth().padding(top = 4.dp), 3.dp)
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
    out += DevCard(tr("Procesor"), soc.ifBlank { Build.HARDWARE }, listOfNotNull("$cores " + tr("jezgre"), ghz, abi).joinToString(" · "), R.drawable.lc_layers, 0xFFC9C9CE, null,
        listOfNotNull(tr("Čip") to soc.ifBlank { Build.HARDWARE }, tr("Jezgre") to cores.toString(), ghz?.let { tr("Najveći takt") to it },
            tr("Arhitektura") to Build.SUPPORTED_ABIS.joinToString(", "), tr("Platforma") to Build.BOARD))
    val gpu = DeviceInfo.gpu()
    out += DevCard(tr("Grafika"), gpu?.substringBefore(" · ") ?: "—", gpu?.substringAfter(" · ", "")?.ifBlank { null }, R.drawable.lc_sparkles, 0xFFC9C9CE, null,
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
            listOf(tr("Ukupno") to gb(st.totalBytes), tr("Slobodno") to gb(st.availableBytes), tr("Podaci aplikacije") to (appData / 1_048_576).toString() + " MB"))  // i18n-ignore
    } catch (_: Exception) {}
    try {
        val d = (ctx.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager).getDisplay(android.view.Display.DEFAULT_DISPLAY)
        val mode = d.mode
        val hdr = if (Build.VERSION.SDK_INT >= 24) d.hdrCapabilities?.supportedHdrTypes?.map { t -> when (t) { 1 -> "Dolby Vision"; 2 -> "HDR10"; 3 -> "HLG"; 4 -> "HDR10+"; else -> "HDR" } }?.distinct()?.joinToString(" · ") else null  // i18n-ignore
        val modes = d.supportedModes.map { listOf(it.physicalWidth, "×", it.physicalHeight, " ", it.refreshRate.toInt(), " Hz").joinToString("") }.distinct()  // i18n-ignore
        out += DevCard(tr("Ekran"), listOf(mode.physicalWidth, " × ", mode.physicalHeight, " · ", mode.refreshRate.toInt(), " Hz").joinToString(""), hdr?.ifBlank { null } ?: tr("bez HDR-a"),  // i18n-ignore
            R.drawable.lc_monitor, 0xFFC9C9CE, null,
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
        out += DevCard(tr("Mreža"), kind, listOfNotNull(down, ip).joinToString(" · ").ifBlank { null }, R.drawable.lc_wifi, 0xFFC9C9CE, null,
            listOfNotNull(tr("Veza") to kind, ip?.let { tr("IP adresa") to it }, down?.let { tr("Brzina veze") to it },
                lp?.dnsServers?.takeIf { it.isNotEmpty() }?.let { "DNS" to it.mapNotNull { a -> a.hostAddress }.joinToString(", ") },  // i18n-ignore
                "VPN" to tr(if (vpn) "aktivan" else "nije aktivan")))
    } catch (_: Exception) {}
    val tvOs = if (ctx.packageManager.hasSystemFeature("com.google.android.feature.GOOGLE_EXPERIENCE") || ctx.packageManager.hasSystemFeature("com.google.android.tv.installed")) "Google TV" else "Android TV"  // i18n-ignore
    out += DevCard(tr("Sistem"), listOf(tvOs, " · Android ", Build.VERSION.RELEASE).joinToString(""), listOf("API ", Build.VERSION.SDK_INT).joinToString(""), R.drawable.lc_smartphone, 0xFF22C55E, null,  // i18n-ignore
        listOf(tr("Operativni sistem") to tvOs, "Android" to Build.VERSION.RELEASE, "API" to Build.VERSION.SDK_INT.toString(), tr("Build") to Build.DISPLAY,  // i18n-ignore
            tr("Kernel") to (System.getProperty("os.version") ?: "—")))
    // Settings › Player › Long-run check: memory and CPU over hours (and how long the start took)
    Monitor.summary().let { rows ->
        out += DevCard(tr("Dugi rad"), rows.firstOrNull { it.first == tr("Memorija sada") }?.second ?: rows.first().second, rows.getOrNull(1)?.let { it.first + ": " + it.second },
            R.drawable.lc_gauge2, 0xFFC9C9CE, null, rows)
    }
    val patch = Build.VERSION.SECURITY_PATCH
    val patchNice = try { SimpleDateFormat("d. MMMM yyyy", Locale(Look.lang)).format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(patch)!!) } catch (e: Exception) { patch }  // i18n-ignore
    out += DevCard(tr("Sigurnosna zakrpa"), patchNice, null, R.drawable.lc_shield_check, 0xFFEF4444, null, listOf(tr("Sigurnosna zakrpa") to patch))
    out += DevCard(tr("Način rada"), (if (Perf.lite) "Lite" else "Full") + " · " + trf("ocjena %s/100", Perf.score), "Media Center " + BuildInfo.VERSION, R.drawable.lc_sparkle, 0xFF9CA3AF, Perf.score / 100f,  // i18n-ignore
        listOf(tr("Način rada") to (if (Perf.lite) "Lite" else "Full"), tr("Ocjena uređaja") to listOf(Perf.score, "/100").joinToString(""),  // i18n-ignore
            tr("Preporuka") to tr(if (Perf.weak) "Lite (ispod 70 bodova ili manje od 3 GB RAM-a)" else "Full"), tr("Verzija") to BuildInfo.VERSION))
    return out
}

/** Playlist and EPG refresh by themselves (Settings › Playlists): 0 off, 1 at night around 4:00, 2 every 12 h, 3 every 24 h. */
object AutoRefresh {
    val OPTIONS = listOf("Isključeno", "Noću, oko 4:00", "Svakih 12 sati", "Svaka 24 sata")
    val mode get() = Prefs.long("auto_refresh", 1).toInt().coerceIn(0, 3)
    fun due(): Boolean {
        val last = Prefs.long("auto_refresh_at", 0)
        val now = System.currentTimeMillis()
        return when (mode) {
            1 -> java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) in 3..5 && now - last > 12 * 3600_000L
            2 -> now - last > 12 * 3600_000L
            3 -> now - last > 24 * 3600_000L
            else -> false
        }
    }
    suspend fun run(ctx: Context, changed: () -> Unit) {
        Prefs.put("auto_refresh_at", System.currentTimeMillis())
        Repo.clear(); Repo.dropCatalogs()
        if (ServerOrder.on) Store.playlists(ctx).forEach { Ovr(ctx, it.id).takeServerOrder() }
        Store.actives(ctx).forEach { p -> try { NewContent.scan(ctx, p, force = true) } catch (_: Exception) {} }
        EpgSources.list().filter { it.on }.forEach { try { EpgSources.refresh(it) } catch (_: Exception) {} }
        withContext(Dispatchers.Main) { changed() }
    }
}

/** Settings › Playlists: a refresh takes the group and channel order from the server (e.g. IPTV Editor) */
object ServerOrder { val on get() = Prefs.bool("srv_order", true) }

/** EPG time shift in hours (some providers are an hour early or late). */
object EpgShift {
    val hours get() = Prefs.long("epg_shift", 0).toInt().coerceIn(-12, 12)
    val ms get() = hours * 3600_000L
    /** one channel's own shift in minutes, on top of the general one (player › More › Time shift, or long OK on a channel) */
    var rev by mutableIntStateOf(0)
    private fun key(url: String) = "epgsh_" + url.hashCode()
    fun chMin(url: String) = Prefs.long(key(url), 0).toInt().coerceIn(-720, 720)
    fun chMs(url: String) = chMin(url) * 60_000L
    fun setCh(url: String, min: Int) { Prefs.put(key(url), min.toLong()); NowCache.drop(url); NextCache.m.remove(url); rev++ }
    fun label(min: Int): String {
        if (min == 0) return tr("Bez pomaka")
        val a = Math.abs(min); val sign = if (min > 0) "+" else "−"
        return sign + (if (a % 60 == 0) "${a / 60} h" else "$a min")  // i18n-ignore
    }
}


/** Text in one line that shrinks a little (down to 75 %) instead of breaking or being cut. */
@Composable
fun FitText(text: String, size: androidx.compose.ui.unit.TextUnit, color: Color, weight: FontWeight = FontWeight.Normal, modifier: Modifier = Modifier, spacing: Float = 0f) {
    var scale by remember(text, size) { mutableStateOf(1f) }
    androidx.compose.material3.Text(text, modifier = modifier, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        style = androidx.compose.ui.text.TextStyle(fontFamily = Inter, fontSize = size * scale, color = color, fontWeight = weight,
            letterSpacing = if (spacing > 0f) (spacing * scale).sp else androidx.compose.ui.unit.TextUnit.Unspecified),
        onTextLayout = { r -> if (r.hasVisualOverflow && scale > 0.76f) scale -= 0.06f })
}


@Composable
private fun SpeedRow(label: String, value: String, sub: String, enabled: Boolean, dim: Boolean = false, onTest: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp).border(1.dp, C.Line, Rnd(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            FitText(tr(label).uppercase(), 10.sp, C.Muted, FontWeight.Bold, spacing = 1.5f)
            FitText(value, 20.sp, if (dim) C.Muted else C.Text, FontWeight.ExtraBold)
            if (sub.isNotBlank()) FitText(sub, 11.sp, C.Muted)
        }
        Focusable(onClick = { if (enabled) onTest() }, shape = Pill, normal = if (enabled) Color.White.copy(alpha = 0.1f) else Color.Transparent,
            focused = if (enabled) Color.White else C.CardHi, zoom = 1.05f, modifier = Modifier.height(32.dp).border(1.dp, C.Line, Pill)) { f ->
            T(tr("▶ Testiraj"), 12.sp, if (!enabled) C.Muted else if (f) Color.Black else C.Text, FontWeight.Bold, modifier = Modifier.align(Alignment.Center).padding(horizontal = 12.dp))
        }
    }
}

/**
 * Standard view: the look of films, series and channels set on one TV, saved and built into PRETORIAN, so every new
 * install and every new profile starts with it. Playlists, passwords, PIN and favourites are never part of it.
 */
object StandardView {
    private val KEYS = Regex("^(look_|tv_view|vod_sort|home_rows|home_off|poster|row_size|ss_|sub_size|sub_style|perf_mode).*")
    fun snapshot(ctx: Context): String {
        val o = JSONObject()
        ctx.getSharedPreferences("pretorian", Context.MODE_PRIVATE).all.forEach { (k, v) -> if (KEYS.matches(k) && v != null) o.put(k, v) }
        return JSONObject().put("pretorian_view", 1).put("version", BuildInfo.VERSION).put("prefs", o).toString(2)
    }
    fun apply(ctx: Context, json: String) {
        val p = JSONObject(json).optJSONObject("prefs") ?: return
        val e = ctx.getSharedPreferences("pretorian", Context.MODE_PRIVATE).edit()
        p.keys().forEach { k -> when (val v = p.get(k)) { is Boolean -> e.putBoolean(k, v); is Int -> e.putInt(k, v); is Long -> e.putLong(k, v); is Double -> e.putFloat(k, v.toFloat()); else -> e.putString(k, v.toString()) } }
        e.commit()
    }
    /** first start: the built-in standard view (assets/standard_view.json), when there is one */
    fun onFirstStart(ctx: Context) {
        if (Prefs.bool("std_view_done")) return
        Prefs.put("std_view_done", true)
        try { ctx.assets.open("standard_view.json").bufferedReader().use { apply(ctx, it.readText()) } } catch (_: Exception) {}
    }
}
