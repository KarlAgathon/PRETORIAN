package com.pretorian.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * Spec 16 — SPORTS: one dashboard (matches left, the confirmed match and its broadcasts right), Match Follow with a
 * small overlay, and Settings › Sport (SPORT master filter, search-first Country / National team / Favorites / Competitions,
 * notification settings). Data: ESPN's public scoreboards, summaries and team lists (no key), the user's own TV guide
 * for the channels that really carry a match, ESPN's broadcaster names for the stations outside the list.
 */

/* ============================================================== preferences */

/** A team picked in Settings › Sport, with ESPN's stable id. */
data class TeamPick(val id: String, val name: String, val logo: String?, val sport: String, val league: String)

object SportPrefs {
    var rev by mutableIntStateOf(0)
    /** sports the data source really serves (fixtures, scores) */
    val SUPPORTED = listOf("football" to "Fudbal", "basketball" to "Košarka", "tennis" to "Tenis", "f1" to "Formula 1")
    private val NATIONAL = setOf("WC", "EURO", "UNL", "WCQ", "FRI")

    private fun csv(k: String, def: String = "") = Prefs.str(k, def).split(',').map { it.trim() }.filter { it.isNotBlank() }
    fun sports(): Set<String> {
        if (Prefs.str("sp3_sports", "?") == "?") {
            val old = csv("sport_enabled", "football").filter { s -> SUPPORTED.any { it.first == s } }
            return (old.ifEmpty { listOf("football") }).toSet()
        }
        return csv("sp3_sports").toSet()
    }
    fun setSports(s: Set<String>) { Prefs.put("sp3_sports", s.joinToString(",")); changed() }
    fun countries(): List<String> = csv("sp3_countries")
    fun setCountries(l: List<String>) { Prefs.put("sp3_countries", l.distinct().joinToString(",")); changed() }
    fun comps(): List<String> = csv("sp3_comps")
    fun setComps(l: List<String>) { Prefs.put("sp3_comps", l.distinct().joinToString(",")); changed() }
    private fun teams(k: String): List<TeamPick> = try {
        val a = JSONArray(Prefs.str(k, "[]"))
        (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { TeamPick(it.str("id"), it.str("n"), it.str("l").ifBlank { null }, it.str("s"), it.str("g")) }
    } catch (e: Exception) { emptyList() }
    private fun saveTeams(k: String, l: List<TeamPick>) {
        val a = JSONArray(); l.distinctBy { it.sport + it.id }.forEach { a.put(JSONObject().put("id", it.id).put("n", it.name).put("l", it.logo ?: "").put("s", it.sport).put("g", it.league)) }
        Prefs.put(k, a.toString()); changed()
    }
    fun nations() = teams("sp3_nations"); fun setNations(l: List<TeamPick>) = saveTeams("sp3_nations", l)
    fun favs() = teams("sp3_favs"); fun setFavs(l: List<TeamPick>) = saveTeams("sp3_favs", l)

    // notifications (Match Follow overlay only)
    val DURS = listOf(2, 3, 5, 10)
    var dur: Int get() = Prefs.str("sp3_dur", "3").toIntOrNull()?.takeIf { it in DURS } ?: 3; set(v) { Prefs.put("sp3_dur", v.toString()); rev++ }
    var dnd: Boolean get() = Prefs.bool("sp3_dnd", false); set(v) { Prefs.put("sp3_dnd", v); rev++ }
    val CTX = listOf("live" to "TV uživo", "movie" to "Filmovi", "series" to "Serije")
    fun ctxOn(k: String) = k in csv("sp3_ctx", "live,movie,series")
    fun setCtx(s: Set<String>) { Prefs.put("sp3_ctx", if (s.isEmpty()) "-" else s.joinToString(",")); rev++ }
    val EVENTS = listOf("goal" to "Gol", "cards" to "Kartoni (žuti / crveni)", "pen" to "Penali", "ht" to "Poluvrijeme", "ft" to "Kraj utakmice")
    fun eventOn(k: String) = k in csv("sp3_events", "goal,cards,pen,ht,ft")
    fun setEvents(s: Set<String>) { Prefs.put("sp3_events", if (s.isEmpty()) "-" else s.joinToString(",")); rev++ }

    private fun changed() { rev++; Sports.lastCombined.clear(); SportCache.clear(); FavNext.clear() }

    /** competitions that are fetched: the big ones of the chosen sports plus everything the preferences point at */
    fun leagues(): List<League> {
        val sp = sports()
        val comps = comps().toSet(); val cs = countries().toSet()
        val favL = favs().filter { it.sport in sp }.map { it.league }.toSet()
        val favC = favs().mapNotNull { it.id.removePrefix("cc:").takeIf { _ -> it.id.startsWith("cc:") } }.toSet()
        val nat = nations().any { it.sport in sp } || favC.isNotEmpty() || favs().any { it.league in NATIONAL }
        return Sports.LEAGUES.filter { it.sport in sp }.filter { l ->
            l.abbr in Sports.BIG || l.abbr in comps || countryOf(l.path) in cs || countryOf(l.path) in favC || l.abbr in favL || (nat && l.abbr in NATIONAL)
        }
    }

    /** Strict order on the dashboard (owner's rule): 0 favourites · 1 followed matches · 2 national teams · 3 competitions ·
     *  4 countries · 5 everything else. A preference, never an exclusion. */
    fun tier(e: SportEvent): Int {
        val ids = setOfNotNull(e.home?.id?.ifBlank { null }, e.away?.id?.ifBlank { null })
        val names = setOfNotNull(e.home?.name, e.away?.name).map { nameKey(it) }.toSet()
        val fav = favs().filter { it.sport == e.sport }
        if (fav.any { f ->
                if (f.id.startsWith("cc:")) { val c = f.id.removePrefix("cc:"); countryKey(c) in names || e.country == c }
                else f.id in ids || (f.league in NATIONAL && nameKey(f.name) in names)
            }) return 0
        if (MatchFollow.has(e.id)) return 1
        if (nations().any { n -> n.sport == e.sport && (n.id in ids || nameKey(n.name) in names) }) return 2
        if (e.leagueAbbr in comps()) return 3
        if (countries().any { c -> e.country == c || countryKey(c) in names }) return 4
        return 5
    }
    /** "Bosnia-Herzegovina" and "Bosnia and Herzegovina" are the same team */
    fun nameKey(s: String) = Sports.norm(s).replace(" and ", " ").replace("&", " ").replace(Regex("[^a-z]"), "")
    fun countryKey(code: String) = nameKey(countryName(code, Locale.ENGLISH))
    fun isNational(abbr: String) = abbr in NATIONAL
}

fun countryName(code: String, loc: Locale = Locale(Look.lang)): String =
    if (code == "eu") tr("Evropa") else Locale("", code.uppercase()).getDisplayCountry(loc).ifBlank { code.uppercase() }

/* ============================================================== ESPN team lists (search source) */

object SportCatalog {
    private val mem = HashMap<String, List<TeamPick>>()
    /** every team of one competition, kept a week on the device */
    suspend fun teams(l: League): List<TeamPick> = withContext(Dispatchers.IO) {
        mem[l.abbr]?.let { return@withContext it }
        val k = "sp3cat_" + l.abbr
        val cached = try { JSONObject(Prefs.str(k, "{}")) } catch (e: Exception) { JSONObject() }
        val fresh = System.currentTimeMillis() - cached.optLong("t") < 7 * 86_400_000L
        val arr = if (fresh) cached.optJSONArray("a") else null
        val list = if (arr != null) (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map { TeamPick(it.str("id"), it.str("n"), it.str("l").ifBlank { null }, l.sport, l.abbr) }
        else {
            val o = JSONObject(Http.request("https://site.api.espn.com/apis/site/v2/sports/${l.path}/teams"))  // i18n-ignore
            val ts = o.optJSONArray("sports")?.optJSONObject(0)?.optJSONArray("leagues")?.optJSONObject(0)?.optJSONArray("teams") ?: JSONArray()
            val out = (0 until ts.length()).mapNotNull { ts.optJSONObject(it)?.optJSONObject("team") }.map { t ->
                TeamPick(t.str("id"), t.str("displayName").ifBlank { t.str("name") }, t.optJSONArray("logos")?.optJSONObject(0)?.str("href")?.ifBlank { null }, l.sport, l.abbr)
            }.filter { it.id.isNotBlank() && it.name.isNotBlank() }
            val a = JSONArray(); out.forEach { a.put(JSONObject().put("id", it.id).put("n", it.name).put("l", it.logo ?: "")) }
            Prefs.put(k, JSONObject().put("t", System.currentTimeMillis()).put("a", a).toString())
            out
        }
        mem[l.abbr] = list; list
    }
    suspend fun many(ls: List<League>): List<TeamPick> = coroutineScope {
        val sem = kotlinx.coroutines.sync.Semaphore(4)
        ls.map { l -> async { sem.acquire(); try { try { teams(l) } catch (e: Exception) { emptyList() } } finally { sem.release() } } }.awaitAll().flatten()
    }
}

/** The next matches of your favourites and national teams, even weeks ahead (each team's own schedule), kept for 6 hours. */
object FavNext {
    private var at = 0L; private var key = ""
    private var cached: Pair<List<SportEvent>, List<String>> = emptyList<SportEvent>() to emptyList()
    suspend fun load(): Pair<List<SportEvent>, List<String>> {
        val sp = SportPrefs.sports()
        val picks = (SportPrefs.favs() + SportPrefs.nations()).filter { it.sport in sp }.distinctBy { it.sport + it.id }
        val k = picks.joinToString { it.sport + it.id } + sp
        if (k == key && System.currentTimeMillis() - at < 6 * 3600_000L) return cached
        val natL = Sports.LEAGUES.filter { SportPrefs.isNational(it.abbr) }
        val natTeams = if (picks.any { it.id.startsWith("cc:") }) SportCatalog.many(natL) else emptyList()
        val now = System.currentTimeMillis()
        val out = ArrayList<SportEvent>(); val none = ArrayList<String>()
        coroutineScope {
            picks.map { t -> async {
                val evs = try {
                    when {
                        t.id.startsWith("cc:") -> {
                            val team = natTeams.firstOrNull { SportPrefs.nameKey(it.name) == SportPrefs.countryKey(t.id.removePrefix("cc:")) }
                            if (team == null) emptyList() else natL.flatMap { Sports.teamNext(it, team.id) }
                        }
                        SportPrefs.isNational(t.league) -> natL.flatMap { Sports.teamNext(it, t.id) }
                        else -> Sports.LEAGUES.firstOrNull { it.abbr == t.league }?.let { Sports.teamNext(it, t.id) } ?: emptyList()
                    }
                } catch (e: Exception) { emptyList() }
                val next = evs.filter { it.state != "post" && it.start > now - 3 * 3600_000L }.distinctBy { it.id }.sortedBy { it.start }.take(2)
                t.name to next
            } }.map { it.await() }.forEach { (n, l) -> if (l.isEmpty()) none += n else out += l }
        }
        cached = out.distinctBy { it.id } to none; key = k; at = System.currentTimeMillis()
        return cached
    }
    fun clear() { key = "" }
}

/* ============================================================== Settings › Sport */

@Composable
fun SportV3() {
    var rev by remember { mutableIntStateOf(0) }
    var pick by remember { mutableStateOf<String?>(null) }
    @Suppress("UNUSED_VARIABLE") val r = SportPrefs.rev
    fun names(l: List<String>) = if (l.isEmpty()) tr("＋ Dodaj") else l.take(2).joinToString(", ") + if (l.size > 2) " +" + (l.size - 2) else ""
    key(rev, SportPrefs.rev) {
        SetPage("Sport", "Šta pratiš u Sportu i kako ti javlja") {
            val sp = SportPrefs.sports()
            val S = SportPrefs.SUPPORTED
            SetMulti("Sport", "glavni filter, više izbora", S.map { it.second }, S.indices.filter { S[it].first in sp }.toSet(), all = false, none = true) { s2 ->
                SportPrefs.setSports(s2.map { S[it].first }.toSet()); rev++
            }
            SetRow("Država", "traži i dodaj (prednost na listi)", names(SportPrefs.countries().map { countryName(it) })) { pick = "country" }
            SetRow("Reprezentacija", "traži i dodaj reprezentacije", names(SportPrefs.nations().map { it.name })) { pick = "nation" }
            SetRow("Favoriti", "traži i dodaj klubove i timove", names(SportPrefs.favs().map { it.name })) { pick = "fav" }
            SetRow("Takmičenja", "traži i dodaj lige i turnire", names(SportPrefs.comps().mapNotNull { a -> Sports.LEAGUES.firstOrNull { it.abbr == a }?.name })) { pick = "comp" }
            SetSection("Obavještenja · praćenje utakmica")
            SetDrop("Trajanje prikaza", null, SportPrefs.DURS.map { trf("%s s", it) }, SportPrefs.DURS.indexOf(SportPrefs.dur)) { SportPrefs.dur = SportPrefs.DURS[it]; rev++ }
            SetSwitch("Isključi sva sportska obavještenja", null, SportPrefs.dnd) { SportPrefs.dnd = it; rev++ }
            val C = SportPrefs.CTX
            SetMulti("Prikaži tokom", null, C.map { it.second }, C.indices.filter { SportPrefs.ctxOn(C[it].first) }.toSet(), all = false, none = true) { s2 ->
                SportPrefs.setCtx(s2.map { C[it].first }.toSet()); rev++
            }
            val E = SportPrefs.EVENTS
            SetMulti("Događaji", null, E.map { it.second }, E.indices.filter { SportPrefs.eventOn(E[it].first) }.toSet(), all = false, none = true) { s2 ->
                SportPrefs.setEvents(s2.map { E[it].first }.toSet()); rev++
            }
        }
    }
    pick?.let { k -> SportPicker(k) { pick = null; rev++ } }
}

/** One search-and-select window for Country, National team, Favorites and Competitions: search on top, results with ✓, chosen below. */
@Composable
private fun SportPicker(kind: String, onClose: () -> Unit) {
    var q by remember { mutableStateOf("") }
    var rev by remember { mutableIntStateOf(0) }
    val sp = SportPrefs.sports()
    val title = when (kind) { "country" -> "Država"; "nation" -> "Reprezentacija"; "fav" -> "Favoriti"; else -> "Takmičenja" }
    // the choices: ids + names, from the data source only
    data class Opt(val id: String, val name: String, val sub: String?, val team: TeamPick? = null)
    val source by produceState<Load<List<Opt>>>(Load.Loading, kind, sp) {
        value = try {
            Load.Ok(withContext(Dispatchers.IO) {
                when (kind) {
                    "country" -> Sports.LEAGUES.filter { it.sport in sp }.map { countryOf(it.path) }.filter { it != "int" && it != "eu" }.distinct()
                        .map { Opt(it, countryName(it), null) }
                    "comp" -> Sports.LEAGUES.filter { it.sport in sp }.map { Opt(it.abbr, it.name, SportPrefs.SUPPORTED.firstOrNull { s -> s.first == it.sport }?.second?.let { s -> tr(s) }) }
                    "nation" -> if ("football" !in sp) emptyList() else
                        SportCatalog.many(Sports.LEAGUES.filter { it.sport == "football" && SportPrefs.isNational(it.abbr) }).distinctBy { it.id }.map { Opt(it.id, it.name, null, it) }
                    else -> {
                        val clubs = SportCatalog.many(Sports.LEAGUES.filter { it.sport in sp && !SportPrefs.isNational(it.abbr) && it.sport != "f1" && it.sport != "tennis" })
                            .distinctBy { it.sport + it.id }.map { t -> Opt(t.id, t.name, (Sports.LEAGUES.firstOrNull { it.abbr == t.league }?.name ?: "") + " · " + tr("Klub"), t) }
                        val nats = if ("football" !in sp) emptyList() else SportCatalog.many(Sports.LEAGUES.filter { it.sport == "football" && SportPrefs.isNational(it.abbr) })
                            .distinctBy { it.id }.map { Opt(it.id, it.name, tr("Reprezentacija"), it) }
                        val lands = if ("football" !in sp) emptyList() else Locale.getISOCountries().map { c -> c.lowercase() }.map { c ->
                            Opt("cc:$c", countryName(c), tr("Država") + " · " + tr("reprezentacija i lige"), TeamPick("cc:$c", countryName(c), null, "football", "")) }
                        lands + nats + clubs
                    }
                }
            })
        } catch (e: Exception) { Load.Err(e.message ?: tr("Pretraga trenutno nije dostupna")) }
    }
    fun chosen(): List<Pair<String, String>> = when (kind) {
        "country" -> SportPrefs.countries().map { it to countryName(it) }
        "comp" -> SportPrefs.comps().map { a -> a to (Sports.LEAGUES.firstOrNull { it.abbr == a }?.name ?: a) }
        "nation" -> SportPrefs.nations().map { it.id to it.name }
        else -> SportPrefs.favs().map { it.sport + it.id to it.name }
    }
    fun keyOf(o: Opt) = if (kind == "fav") (o.team?.sport ?: "") + o.id else o.id
    fun toggle(o: Opt) {
        val on = chosen().any { it.first == keyOf(o) }
        when (kind) {
            "country" -> SportPrefs.setCountries(if (on) SportPrefs.countries() - o.id else SportPrefs.countries() + o.id)
            "comp" -> SportPrefs.setComps(if (on) SportPrefs.comps() - o.id else SportPrefs.comps() + o.id)
            "nation" -> SportPrefs.setNations(if (on) SportPrefs.nations().filter { it.id != o.id } else SportPrefs.nations() + o.team!!)
            else -> SportPrefs.setFavs(if (on) SportPrefs.favs().filter { it.sport + it.id != keyOf(o) } else SportPrefs.favs() + o.team!!)
        }
        rev++
    }
    fun remove(k: String) {
        when (kind) {
            "country" -> SportPrefs.setCountries(SportPrefs.countries() - k)
            "comp" -> SportPrefs.setComps(SportPrefs.comps() - k)
            "nation" -> SportPrefs.setNations(SportPrefs.nations().filter { it.id != k })
            else -> SportPrefs.setFavs(SportPrefs.favs().filter { it.sport + it.id != k })
        }
        rev++
    }
    Popup(title, onClose, width = 720.dp) {
        Field(tr("Traži…"), q, { q = it }, Modifier.fillMaxWidth())
        key(rev) {
            val sel = chosen()
            val n = Sports.norm(q.trim())
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (n.isNotEmpty()) {
                        when (val s = source) {
                            is Load.Loading -> item { T(tr("Učitavam…"), 15.sp, C.Soft, modifier = Modifier.padding(12.dp)) }
                            is Load.Err -> item { T(tr("Pretraga trenutno nije dostupna"), 15.sp, C.Soft, modifier = Modifier.padding(12.dp)) }
                            is Load.Ok -> {
                                val hits = s.value.filter { Sports.norm(it.name).contains(n) || Sports.norm(it.id) == n }.take(30)
                                if (hits.isEmpty()) item { T(tr("Nema rezultata!"), 15.sp, C.Soft, modifier = Modifier.padding(12.dp)) }
                                itemsIndexed(hits, key = { _, o -> "r:" + keyOf(o) }) { _, o ->
                                    val on = sel.any { it.first == keyOf(o) }
                                    OptionRow(o.name, if (on) R.drawable.lc_check else R.drawable.lc_plus, sub = o.sub, selected = on) { toggle(o) }
                                }
                            }
                        }
                    } else if (sel.isEmpty()) item { T(tr("Upiši ime i izaberi rezultat."), 15.sp, C.Soft, modifier = Modifier.padding(12.dp)) }
                    if (sel.isNotEmpty()) {
                        item { T(tr("IZABRANO"), 13.sp, C.Muted, FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 4.dp)) }
                        itemsIndexed(sel, key = { _, it -> "s:" + it.first }) { _, (k, name) ->
                            OptionRow(name, R.drawable.lc_x, value = tr("Ukloni")) { remove(k) }
                        }
                    }
                }
            }
        }
    }
}

/* ============================================================== the dashboard */

@Composable
fun SportDashboard(nav: Nav) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    var visible by remember { mutableStateOf(true) }
    DisposableEffect(lifecycle) {
        val o = androidx.lifecycle.LifecycleEventObserver { _, _ -> visible = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) }
        lifecycle.addObserver(o); onDispose { lifecycle.removeObserver(o) }
    }
    LaunchedEffect(visible) { if (visible) while (true) { delay(60_000); tick++ } }
    @Suppress("UNUSED_VARIABLE") val pr = SportPrefs.rev
    val sports = remember(SportPrefs.rev) { SportPrefs.sports() }
    var today by remember { mutableStateOf(SportCache.today) }
    var soon by remember { mutableStateOf(SportCache.other["soon"]) }
    var loading by remember { mutableStateOf(today == null) }
    LaunchedEffect(tick, SportCache.dataRev, visible) {
        if (!visible || sports.isEmpty()) return@LaunchedEffect
        if (SportCache.today == null || System.currentTimeMillis() - SportCache.todayAt > 50_000L) {
            try { SportCache.today = Sports.day(0) { today = it; loading = false }; SportCache.todayAt = System.currentTimeMillis() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) {}
        }
        today = SportCache.today; loading = false
        GoalNote.check(today.orEmpty())
    }
    LaunchedEffect(SportCache.dataRev, visible, tick / 20) {
        if (!visible || sports.isEmpty()) return@LaunchedEffect
        if (SportCache.other["soon"] == null || System.currentTimeMillis() - (SportCache.otherAt["soon"] ?: 0L) > 1_200_000L) {
            try { val r = Sports.upcoming(3) { soon = it }; SportCache.other["soon"] = r; SportCache.otherAt["soon"] = System.currentTimeMillis() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) {}
        }
        soon = SportCache.other["soon"]
    }
    LaunchedEffect(visible, tick / 15) {
        if (!visible) return@LaunchedEffect
        val g = try { withContext(Dispatchers.IO) { Sports.guide(ctx, Store.actives(ctx)) } } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { return@LaunchedEffect }
        if (g !== SportCache.guide) { SportCache.guide = g; SportCache.guideRev++ }
    }
    val favNext by produceState<Pair<List<SportEvent>, List<String>>>(emptyList<SportEvent>() to emptyList(), SportPrefs.rev, sports) {
        value = try { FavNext.load() } catch (e: Exception) { emptyList<SportEvent>() to emptyList() }
    }
    val all = remember(today, soon, sports, favNext) {
        (today.orEmpty() + soon.orEmpty() + favNext.first).filter { !it.fromGuide && it.sport in sports && (it.state != "post" || System.currentTimeMillis() - it.start < 4 * 3600_000L) }
            .groupBy { it.id }.values.map { v -> v.maxBy { it.observedAt } }
    }
    val fixtures = remember(all) { all.map { e -> e.copy(state = "", clock = null, details = emptyList(), observedAt = 0, stage = "", halftime = null,
        home = e.home?.copy(score = null), away = e.away?.copy(score = null)) } }
    val matched by produceState(emptyList<SportEvent>(), fixtures, SportCache.guideRev) {
        val g = SportCache.guide ?: return@produceState
        value = withContext(Dispatchers.Default) { Sports.combine(fixtures, g, 0) }
    }
    val list = remember(all, matched, SportPrefs.rev, MatchFollow.rev) {
        val byId = matched.associateBy { it.id }
        all.map { e -> val m = byId[e.id]; if (m == null || m.start != e.start) e else e.copy(channels = m.channels, ai = m.ai, maybe = m.maybe) }
            .sortedWith(compareBy({ SportPrefs.tier(it) }, { if (it.live) 0 else if (it.state == "pre") 1 else 2 }, { if (it.state == "post") -it.start else it.start }))
    }
    var confirmed by remember { mutableStateOf(SportsMemory.evId) }
    var menu by remember { mutableStateOf<SportEvent?>(null) }
    val cur = list.firstOrNull { it.id == confirmed } ?: list.firstOrNull()
    val frs = remember { HashMap<String, FocusRequester>() }
    val listState = rememberLazyListState()
    fun backToList() { cur?.let { frs[it.id] }?.let { runCatching { it.requestFocus() } } }

    Box(Modifier.fillMaxSize().padding(start = 34.dp, end = 38.dp, top = 6.dp, bottom = 10.dp)) {
        when {
            sports.isEmpty() -> Message("Nijedan sport nije izabran", "Izaberi sportove u Postavke › Sport")
            list.isEmpty() && loading -> Loading("Učitavam utakmice…")
            list.isEmpty() -> Message("Trenutno nema utakmica", "Za izabrane sportove danas i narednih dana nema utakmica")
            else -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                // LEFT: matches
                Column(Modifier.weight(0.42f).fillMaxHeight().clip(Rnd(18.dp)).background(Color(0x66151920)).border(1.dp, Color(0x1FFFFFFF), Rnd(18.dp)).padding(14.dp)) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 6.dp, bottom = 10.dp)) {
                        T(tr("UTAKMICE"), 18.sp, Color.White, FontWeight.Bold); T(tr("uživo i uskoro"), 14.sp, Color(0xFF9AA3AF))
                    }
                    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                        if (favNext.second.isNotEmpty()) item(key = "none") {
                            T(favNext.second.joinToString(", ") + ": " + tr("nema zakazanih utakmica"), 13.sp, Color(0xFF9AA3AF), maxLines = 2,
                                modifier = Modifier.padding(start = 6.dp, bottom = 2.dp))
                        }
                        itemsIndexed(list, key = { _, e -> e.id }) { _, e ->
                            val fr = frs.getOrPut(e.id) { FocusRequester() }
                            MatchRow3(e, e.id == cur?.id, MatchFollow.has(e.id), fr, onMenu = { menu = e }) { confirmed = e.id; SportsMemory.evId = e.id }
                        }
                    }
                }
                // RIGHT: the confirmed match on top, its broadcasts (or stats and events) below
                Column(Modifier.weight(0.58f).fillMaxHeight().onPreviewKeyEvent { k ->
                    val back = k.key == Key.Back || k.key == Key.Escape
                    if (back && k.type == KeyEventType.KeyUp) { backToList(); true } else back
                }, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (cur != null) {
                        MatchHeader3(cur)
                        MatchLower3(cur, nav, tick)
                    }
                }
            }
        }
    }
    LaunchedEffect(list.isNotEmpty()) {
        if (list.isEmpty()) return@LaunchedEffect
        val i = list.indexOfFirst { it.id == cur?.id }.coerceAtLeast(0)
        runCatching { listState.scrollToItem((i - 2).coerceAtLeast(0)) }
        frs[list[i].id]?.tryFocus()
    }
    menu?.let { e ->
        val on = MatchFollow.has(e.id)
        Popup((e.home?.name ?: e.title) + (e.away?.let { " – " + it.name } ?: ""), { menu = null }, width = 460.dp) {
            OptionRow(if (on) "Ne prati ovu utakmicu" else "Prati ovu utakmicu", R.drawable.lc_bell) {
                MatchFollow.toggle(e.id); if (!on) FollowWatch.baseline(e); menu = null
            }
        }
    }
}

private fun hm(t: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t))  // i18n-ignore

/** a team name with a thin bar in its own colour */
@Composable
private fun TeamLine(t: Team, f: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(4.dp, 16.dp).background(t.color ?: Color(0x33FFFFFF), Rnd(2.dp)))
        T(t.name, 16.sp, Color.White, if (f) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
    }
}

/** red LIVE label with a softly beating dot */
@Composable
private fun LivePill(text: String, size: Int) {
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "live")
    val a by pulse.animateFloat(1f, 0.25f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(900),
        androidx.compose.animation.core.RepeatMode.Reverse), label = "dot")
    Row(Modifier.clip(Rnd(6.dp)).background(Color(0xFFC62828)).padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(7.dp).graphicsLayer { alpha = a }.background(Color.White, androidx.compose.foundation.shape.CircleShape))
        T(text, size.sp, Color.White, FontWeight.Bold)
    }
}

/** the score lights up for a moment when it changes while you look at it (a goal) */
@Composable
private fun ScoreFlash(id: String, score: String) {
    var last by remember(id) { mutableStateOf(score) }
    val glow = remember(id) { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(score) {
        if (score != last) { last = score; glow.snapTo(1f); glow.animateTo(0f, androidx.compose.animation.core.tween(1600)) }
    }
    Box(Modifier.clip(Rnd(10.dp)).background(Color.White.copy(alpha = 0.22f * glow.value)).padding(horizontal = 10.dp)) {
        T(score, 36.sp, Color.White, FontWeight.Bold, maxLines = 1)
    }
}

/** "LIVE · 68'", "Danas · 20:45", "Sutra · 20:45", "Uto · 21:00", or the final state */
private fun stateLine(e: SportEvent): String = when {
    e.live -> tr("UŽIVO") + (e.clock?.ifBlank { null }?.let { " · $it" } ?: "")
    e.state == "post" -> e.clock?.ifBlank { null } ?: tr("Kraj")
    else -> {
        val d = dayOffsetFwd(e.start)
        (when (d) { 0 -> tr("Danas"); 1 -> tr("Sutra"); else -> SimpleDateFormat("EEE", Locale(Look.lang)).format(Date(e.start)) }) + " · " + hm(e.start)  // i18n-ignore
    }
}
private fun dayOffsetFwd(t: Long): Int { var d = 0; while (d < 10 && t >= Sports.dayStart(d + 1)) d++; return d }

@Composable
private fun MatchRow3(e: SportEvent, current: Boolean, followed: Boolean, fr: FocusRequester, onMenu: () -> Unit, onClick: () -> Unit) {
    Focusable(onClick = onClick, onMenu = onMenu, focusRequester = fr, shape = Rnd(14.dp),
        normal = if (current) Color(0x2EFFFFFF) else Color(0x14FFFFFF), focused = Color(0x40FFFFFF),
        borderColor = if (current) Color(0x80FFFFFF) else Color(0x14FFFFFF), focusBorderColor = Color(0xE6FFFFFF), zoom = 1f,
        modifier = Modifier.fillMaxWidth().height(86.dp)) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                e.home?.let { Crest(it, 26.dp) }; e.away?.let { Crest(it, 26.dp) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                T(flagEmoji(e.country) + "  " + e.league.ifBlank { e.leagueAbbr }, 12.sp, Color(0xFF8A929C), FontWeight.SemiBold, maxLines = 1)
                if (e.home != null && e.away != null) {
                    TeamLine(e.home, f); TeamLine(e.away, f)
                } else T(e.title, 16.sp, Color.White, FontWeight.SemiBold, maxLines = 2)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (followed) Ico(R.drawable.lc_bell, 14.dp, Color(0xFFD9DDE3))
                    if (e.live) LivePill(stateLine(e), 12)
                    else T(stateLine(e), 13.sp, Color(0xFFB0B8C4))
                }
                if (e.state != "pre" && e.home?.score != null) T((e.home.score ?: "") + " – " + (e.away?.score ?: ""), 18.sp, Color.White, FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MatchHeader3(e: SportEvent) {
    Column(Modifier.fillMaxWidth().clip(Rnd(18.dp)).background(Color(0x66151920)).border(1.dp, Color(0x1FFFFFFF), Rnd(18.dp)).padding(horizontal = 22.dp, vertical = 16.dp)) {
        T(listOfNotNull(e.league, e.round).joinToString(" · "), 14.sp, Color(0xFFB0B8C4))
        Spacer(Modifier.height(10.dp))
        if (e.home != null && e.away != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val nat = SportPrefs.isNational(e.leagueAbbr)
                if (e.sport == "football") TeamKit(e.home, nat, 64.dp)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Crest(e.home, 64.dp); Spacer(Modifier.height(6.dp)); T(e.home.name, 17.sp, Color.White, FontWeight.SemiBold, maxLines = 1) }
                Column(Modifier.widthIn(min = 150.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (e.live) LivePill(tr("UŽIVO"), 13)
                    if (e.state == "pre") T(hm(e.start), 34.sp, Color.White, FontWeight.Bold, maxLines = 1)
                    else ScoreFlash(e.id, (e.home.score ?: "–") + " – " + (e.away.score ?: "–"))
                    T(if (e.state == "pre") stateLine(e) else (e.clock ?: ""), 15.sp, Color(0xFFC9CED6))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Crest(e.away, 64.dp); Spacer(Modifier.height(6.dp)); T(e.away.name, 17.sp, Color.White, FontWeight.SemiBold, maxLines = 1) }
                if (e.sport == "football") TeamKit(e.away, nat, 64.dp)
            }
        } else {
            T(e.title, 24.sp, Color.White, FontWeight.Bold, maxLines = 2)
            T(stateLine(e), 15.sp, Color(0xFFC9CED6))
        }
        val ev = e.details.filter { it.kind.startsWith("goal") || it.kind.startsWith("yellow") || it.kind.startsWith("red") || it.kind == "penalty" }
        if (ev.isNotEmpty()) {
            Spacer(Modifier.height(10.dp)); Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x1FFFFFFF))); Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                listOf("h", "a").forEach { side ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ev.filter { it.side == side }.takeLast(4).forEach { d -> DetailLine(d, if (side == "h") e.home?.name else e.away?.name, (if (side == "h") e.home else e.away)?.color) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailLine(d: MatchDetail, team: String?, color: Color? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(4.dp, 18.dp).background(color ?: Color(0x33FFFFFF), Rnd(2.dp)))
        when {
            d.kind.startsWith("red") -> Box(Modifier.size(11.dp, 15.dp).background(Color(0xFFD32F2F), Rnd(2.dp)))
            d.kind.startsWith("yellow") -> Box(Modifier.size(11.dp, 15.dp).background(Color(0xFFFBC02D), Rnd(2.dp)))
            d.kind == "penalty" -> T("◎", 15.sp, Color.White)  // i18n-ignore
            else -> T("⚽", 14.sp, Color.White)  // i18n-ignore
        }
        T(d.minute, 14.sp, Color(0xFFC9CED6), modifier = Modifier.width(44.dp))
        val what = when {
            d.kind.startsWith("red") -> tr("Crveni karton"); d.kind.startsWith("yellow") -> tr("Žuti karton")
            d.kind == "penalty" -> tr("Penal"); d.kind.contains("_pen") -> tr("Gol (penal)"); d.kind.contains("_og") -> tr("Autogol"); else -> tr("Gol")
        }
        T(what + " – " + (d.player.ifBlank { team ?: "" }), 14.sp, Color(0xFFD9DDE3), maxLines = 1)
    }
}

/** The lower panel adapts: many channels → only channels; one or two → channels and below them stats / events; none → a short note and stats / events. */
@Composable
private fun ColumnScope.MatchLower3(e: SportEvent, nav: Nav, tick: Int) {
    val sure = e.channels.distinctBy { it.url }
    // owner: only confirmed broadcasts (TV guide) and ESPN's stations; no guesses by channel name
    val maybe = emptyList<Channel>()
    val mine = (sure + maybe).map { Sports.norm(it.name) }
    val external = e.tv.filter { n -> val k = Sports.norm(n); mine.none { it.contains(k) || k.contains(it) } }.distinct().take(6)
    val stats by produceState(emptyList<Triple<String, String, String>>(), e.id, tick / 2, e.state) {
        value = if (e.state == "pre" || e.sport != "football") emptyList() else try { Sports.stats(e) } catch (x: Exception) { emptyList() }
    }
    val evs = e.details.filter { it.kind.startsWith("goal") || it.kind.startsWith("yellow") || it.kind.startsWith("red") }
    Column(Modifier.fillMaxWidth().weight(1f).clip(Rnd(18.dp)).background(Color(0x66151920)).border(1.dp, Color(0x1FFFFFFF), Rnd(18.dp)).padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Ico(R.drawable.lc_tv, 18.dp, Color.White)
            T(tr("GLEDAJ OVU UTAKMICU"), 16.sp, Color.White, FontWeight.Bold, modifier = Modifier.weight(1f))
            if (sure.isNotEmpty() || external.isNotEmpty()) T(trf("U tvojoj listi: %s", sure.size) + if (external.isNotEmpty()) " · " + trf("još: %s", external.size) else "", 13.sp, Color(0xFF9AA3AF))
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (sure.isEmpty() && maybe.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    T(tr("Nije pronađen kanal s prenosom"), 18.sp, Color.White, FontWeight.SemiBold)
                    T(tr("U tvojoj listi kanala trenutno nema potvrđenog prenosa ove utakmice."), 14.sp, Color(0xFF9AA3AF))
                }
            }
            itemsIndexed(sure, key = { _, c -> "s:" + c.url }) { _, c -> BroadcastRow3(c, true) { playMatch(nav, e, c) } }
            if (maybe.isNotEmpty()) {
                item { T(tr("MOGUĆI PRENOS · NIJE POTVRĐENO"), 12.sp, Color(0xFF9AA3AF), FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, start = 4.dp)) }
                itemsIndexed(maybe.take(4), key = { _, c -> "m:" + c.url }) { _, c -> BroadcastRow3(c, false) { playMatch(nav, e, c) } }
            }
            if (external.isNotEmpty()) {
                item { T(tr("PRENOSE I · NISU U TVOJOJ LISTI"), 12.sp, Color(0xFF9AA3AF), FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, start = 4.dp)) }
                itemsIndexed(external, key = { _, n -> "x:$n" }) { _, n ->
                    Row(Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) { T(n, 15.sp, Color(0xFFC9CED6)) }
                }
            }
            // room left (two channels or fewer): real statistics and events of the match
            if (sure.size + maybe.size <= 2 && (stats.isNotEmpty() || evs.isNotEmpty())) item {
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(Color(0x1FFFFFFF)))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    if (stats.isNotEmpty()) Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        T(tr("STATISTIKA"), 13.sp, Color.White, FontWeight.Bold)
                        stats.take(4).forEach { (label, h, a) -> StatBar(tr(label), h, a) }
                    }
                    if (evs.isNotEmpty()) Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        T(tr("DOGAĐAJI"), 13.sp, Color.White, FontWeight.Bold)
                        evs.takeLast(5).reversed().forEach { d -> DetailLine(d, if (d.side == "h") e.home?.name else e.away?.name, (if (d.side == "h") e.home else e.away)?.color) }
                    }
                }
            }
            if (sure.isEmpty() && maybe.isEmpty() && external.isEmpty() && stats.isEmpty() && evs.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    T(whenLong(e.start), 15.sp, Color(0xFFC9CED6))
                    e.venue?.let { T(it, 14.sp, Color(0xFF9AA3AF)) }
                }
            }
        }
    }
}

@Composable
private fun StatBar(label: String, h: String, a: String) {
    val x = h.removeSuffix("%").toFloatOrNull() ?: 0f; val y = a.removeSuffix("%").toFloatOrNull() ?: 0f
    val f = if (x + y > 0) x / (x + y) else 0.5f
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        T(label, 13.sp, Color(0xFFB0B8C4))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            T(h, 13.sp, Color.White, modifier = Modifier.width(40.dp))
            Box(Modifier.weight(1f).height(4.dp).background(Color(0x33FFFFFF), Rnd(2.dp))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(f.coerceIn(0.02f, 1f)).background(Color(0xFFD9DDE3), Rnd(2.dp)))
            }
            T(a, 13.sp, Color.White, modifier = Modifier.width(40.dp))
        }
    }
}

@Composable
private fun BroadcastRow3(c: Channel, sure: Boolean, onPlay: () -> Unit) {
    Focusable(onClick = onPlay, shape = Rnd(12.dp), normal = Color(0x14FFFFFF), focused = Color(0x40FFFFFF), borderColor = Color(0x14FFFFFF),
        focusBorderColor = Color(0xE6FFFFFF), zoom = 1f, modifier = Modifier.fillMaxWidth().height(52.dp)) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChannelLogo(c.logo, c.name)
            T(c.name, 16.sp, if (sure) Color.White else Color(0xFFC9CED6), if (f) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
            qualityTag(c.name)?.let { q -> Box(Modifier.border(1.dp, Color(0x80FFFFFF), Rnd(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) { T(q, 12.sp, Color.White) } }
            Box(Modifier.size(32.dp).border(1.dp, if (f) Color.White else Color(0x80FFFFFF), Rnd(16.dp)), contentAlignment = Alignment.Center) { Ico(R.drawable.lc_play, 14.dp, Color.White) }
        }
    }
}

/* ============================================================== Match Follow: events and the overlay */

/** Where the user is watching now (set by the player): "live", "movie", "series" or null; the live channel's url. */
object PlayCtx { @Volatile var kind: String? = null; @Volatile var chUrl: String? = null }

data class MatchNote(val head: String, val line: String?, val teams: String, val score: String?, val kind: String = "", val team: Team? = null, val side: String? = null)

object FollowWatch {
    val queue = mutableStateListOf<MatchNote>()
    private val seen: MutableSet<String> by lazy { Prefs.str("sp3_seen").split('\n').filter { it.isNotBlank() }.toMutableSet() }
    private val based: MutableSet<String> by lazy { Prefs.str("sp3_based").split(',').filter { it.isNotBlank() }.toMutableSet() }
    private fun save() { Prefs.put("sp3_seen", seen.toList().takeLast(500).joinToString("\n")); Prefs.put("sp3_based", based.toList().takeLast(100).joinToString(",")) }

    /** every real-world event of a match gets one stable key (dedup across refreshes and restarts) */
    private fun keys(e: SportEvent): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        e.details.forEach { d ->
            val k = e.id + "|" + d.kind + "|" + d.minute + "|" + d.player
            when {
                d.kind.startsWith("goal") -> out += k to (if (d.kind.contains("_pen")) "goalpen" else "goal")
                d.kind.startsWith("yellow") -> out += k to "yellow"
                d.kind.startsWith("red") -> out += k to "red"
                d.kind == "penalty" -> out += k to "pen"
            }
        }
        if (e.stage == "ht" || e.clock == tr("Poluvrijeme")) out += (e.id + "|ht") to "ht"
        if (e.state == "post") out += (e.id + "|ft") to "ft"
        return out
    }

    /** when a match is followed, what already happened is history: no notes for it */
    fun baseline(e: SportEvent) { keys(e).forEach { seen += it.first }; based += e.id; save() }

    @Synchronized fun check(evs: List<SportEvent>) {
        val ms = MatchFollow.list(); if (ms.isEmpty()) return
        var changed = false
        evs.filter { it.id in ms && !it.fromGuide && it.state != CACHED_SCORE && !it.stale }.forEach { e ->
            if (e.id !in based) { baseline(e); return@forEach }
            keys(e).forEach { (k, kind) ->
                if (!seen.add(k)) return@forEach
                changed = true
                note(e, kind, e.details.firstOrNull { e.id + "|" + it.kind + "|" + it.minute + "|" + it.player == k })
            }
            if (e.state == "post") MatchFollow.drop(e.id)
        }
        if (changed) save()
    }

    private fun note(e: SportEvent, kind: String, d: MatchDetail?) {
        val cat = when (kind) { "goal" -> "goal"; "goalpen" -> if (SportPrefs.eventOn("goal")) "goal" else "pen"; "yellow", "red" -> "cards"; "pen" -> "pen"; "ht" -> "ht"; else -> "ft" }
        if (SportPrefs.dnd || Quiet.now() || !SportPrefs.eventOn(cat)) return
        val pk = PlayCtx.kind
        if (pk != null && !SportPrefs.ctxOn(pk)) return
        // already watching this match on a channel the guide confirms: no duplicate overlay
        if (pk == "live" && PlayCtx.chUrl != null && e.channels.any { it.url == PlayCtx.chUrl }) return
        val head = when (kind) { "goal" -> tr("GOL!"); "goalpen" -> tr("GOL! (penal)"); "yellow" -> tr("ŽUTI KARTON"); "red" -> tr("CRVENI KARTON")
            "pen" -> tr("PENAL"); "ht" -> tr("POLUVRIJEME"); else -> tr("KRAJ UTAKMICE") }
        val line = d?.let { listOfNotNull(it.player.ifBlank { null }, it.minute.ifBlank { null }?.let { m -> if (m.endsWith("'")) m else "$m'" }).joinToString(" · ").ifBlank { null } }
        val teams = (e.home?.name ?: e.title) + (e.away?.let { " – " + it.name } ?: "")
        val score = if (e.home?.score != null && e.away?.score != null) e.home.score + " : " + e.away.score else null
        val team = when (d?.side) { "h" -> e.home; "a" -> e.away; else -> null }
        queue.add(MatchNote(head, line, teams, score, kind, team, d?.side))
    }
}

/** The Match Follow note: top right, a few seconds, never takes focus, never pauses anything.
 *  A goal shows the ball and the scoring team's crest and colour; cards show the card; half-time a clock; the end a flag. */
@Composable
fun MatchFollowOverlay() {
    val n = FollowWatch.queue.firstOrNull() ?: return
    val slide = remember(n) { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(n) {
        slide.animateTo(0f, androidx.compose.animation.core.tween(320, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        delay(SportPrefs.dur * 1000L)
        slide.animateTo(1f, androidx.compose.animation.core.tween(240))
        if (FollowWatch.queue.isNotEmpty()) FollowWatch.queue.removeAt(0)
    }
    val accent = n.team?.color ?: when (n.kind) { "yellow" -> Color(0xFFFBC02D); "red" -> Color(0xFFD32F2F); else -> Color(0x80FFFFFF) }
    Box(Modifier.fillMaxSize().padding(top = 28.dp, end = 34.dp), contentAlignment = Alignment.TopEnd) {
        Row(Modifier.width(470.dp).graphicsLayer { translationX = slide.value * 520.dp.toPx(); alpha = 1f - slide.value }
            .clip(Rnd(18.dp)).background(Color(0xEB151920)).border(1.dp, Color(0x59FFFFFF), Rnd(18.dp))) {
            Box(Modifier.width(6.dp).height(132.dp).background(accent))
            Column(Modifier.weight(1f).padding(start = 16.dp, end = 18.dp, top = 12.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Ico(R.drawable.lc_trophy, 16.dp, Color.White); T(tr("SPORT · PRAĆENJE UTAKMICE"), 12.sp, Color(0xFFD9DDE3), FontWeight.SemiBold)
                }
                Box(Modifier.fillMaxWidth().padding(vertical = 7.dp).height(1.dp).background(Color(0x26FFFFFF)))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // what happened, as a picture
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        when (n.kind) {
                            "goal", "goalpen" -> T("⚽", 34.sp, Color.White)  // i18n-ignore
                            "yellow" -> Box(Modifier.size(26.dp, 36.dp).background(Color(0xFFFBC02D), Rnd(4.dp)))
                            "red" -> Box(Modifier.size(26.dp, 36.dp).background(Color(0xFFD32F2F), Rnd(4.dp)))
                            "pen" -> T("◎", 32.sp, Color.White)  // i18n-ignore
                            "ht" -> Ico(R.drawable.lc_clock, 32.dp, Color.White)
                            else -> T("🏁", 30.sp, Color.White)  // i18n-ignore
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        T(n.head, 24.sp, Color.White, FontWeight.Bold)
                        n.line?.let { T(it, 15.sp, Color.White, FontWeight.SemiBold, maxLines = 1) }
                        T(n.teams, 14.sp, Color(0xFFB0B8C4), maxLines = 1)
                    }
                    n.team?.let { Crest(it, 36.dp) }
                    n.score?.let { T(it, 32.sp, Color.White, FontWeight.Bold) }
                }
            }
        }
    }
}
