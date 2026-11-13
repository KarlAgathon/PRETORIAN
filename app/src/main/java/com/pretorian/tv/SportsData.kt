package com.pretorian.tv

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/* ============================================================== model */

data class Team(val name: String, val abbr: String, val logo: String?, val color: Color?, val color2: Color?, val score: String?)

data class SportEvent(
    val id: String,
    /** football, basketball, volleyball, f1, tennis, handball, other */
    val sport: String,
    val league: String,
    val leagueAbbr: String,
    val round: String?,
    val start: Long,
    /** pre, in, post */
    val state: String,
    val clock: String?,
    val home: Team?,
    val away: Team?,
    /** for races, tennis and guide-only events */
    val title: String,
    val venue: String?,
    val halftime: String? = null,
    val channels: List<Channel> = emptyList(),
    /** channels that usually show this league (by name), when the guide does not say */
    val maybe: List<Channel> = emptyList(),
    /** found only in your TV guide (no live score) */
    val fromGuide: Boolean = false,
) {
    val live get() = state == "in"
}

data class League(val sport: String, val path: String, val name: String, val abbr: String)

object Sports {
    /** competitions read from ESPN's public scoreboard (free, no key) */
    val LEAGUES = listOf(
        League("football", "soccer/uefa.champions", "UEFA Liga prvaka", "UCL"),
        League("football", "soccer/uefa.europa", "UEFA Liga Evrope", "UEL"),
        League("football", "soccer/uefa.europa.conf", "UEFA Konferencijska liga", "UECL"),
        League("football", "soccer/eng.1", "Premier League", "PL"),
        League("football", "soccer/ger.1", "Bundesliga", "BL"),
        League("football", "soccer/esp.1", "La Liga", "LL"),
        League("football", "soccer/ita.1", "Serie A", "SA"),
        League("football", "soccer/fra.1", "Ligue 1", "L1"),
        League("football", "soccer/ned.1", "Eredivisie", "ERE"),
        League("football", "soccer/por.1", "Liga Portugal", "POR"),
        League("football", "soccer/tur.1", "Süper Lig", "TSL"),
        League("football", "soccer/aut.1", "Austrijska Bundesliga", "AUT"),
        League("football", "soccer/uefa.nations", "UEFA Liga nacija", "UNL"),
        League("football", "soccer/fifa.worldq.uefa", "Kvalifikacije SP (Evropa)", "WCQ"),
        League("football", "soccer/fifa.friendly", "Prijateljske utakmice", "FRI"),
        League("basketball", "basketball/nba", "NBA", "NBA"),
        League("f1", "racing/f1", "Formula 1", "F1"),
        League("tennis", "tennis/atp", "ATP", "ATP"),
        League("tennis", "tennis/wta", "WTA", "WTA"),
    )

    val SPORTS = listOf("all" to "Sve", "football" to "Fudbal", "basketball" to "Košarka", "volleyball" to "Odbojka",
        "f1" to "Formula 1", "tennis" to "Tenis", "handball" to "Rukomet")

    fun sportColor(s: String) = when (s) {
        "football" -> Color(0xFF22C55E); "basketball" -> Color(0xFFF97316); "volleyball" -> Color(0xFFEAB308)
        "f1" -> Color(0xFFEF4444); "tennis" -> Color(0xFFA3E635); "handball" -> Color(0xFF38BDF8); else -> Color(0xFF94A3B8)
    }

    /* ---------------------------------------------------------------- time */

    fun dayStart(offset: Int): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_MONTH, offset)
    }.timeInMillis

    private fun espnDate(ms: Long) = SimpleDateFormat("yyyyMMdd", Locale.US).format(java.util.Date(ms))

    private fun iso(s: String): Long? {
        for (p in listOf("yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm'Z'", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mmXXX")) {
            try { return SimpleDateFormat(p, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(s)?.time } catch (_: Exception) {}
        }
        return null
    }

    /* ---------------------------------------------------------------- ESPN */

    private fun http(url: String, timeout: Int = 15000): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = timeout; c.readTimeout = timeout
        c.setRequestProperty("User-Agent", Net.UA)
        try {
            if (c.responseCode !in 200..299) throw java.io.IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    private fun color(hex: String): Color? = try { if (hex.length == 6) Color(android.graphics.Color.parseColor("#$hex")) else null } catch (e: Exception) { null }

    private fun team(c: JSONObject?): Team? {
        if (c == null) return null
        val t = c.optJSONObject("team")
        val a = c.optJSONObject("athlete")
        val name = t?.str("displayName")?.ifBlank { null } ?: a?.str("displayName")?.ifBlank { null } ?: return null
        val score = c.str("score").ifBlank { null } ?: c.optJSONArray("linescores")?.let { ls ->
            (0 until ls.length()).mapNotNull { ls.optJSONObject(it)?.optDouble("value")?.takeIf { v -> !v.isNaN() }?.toInt()?.toString() }
                .joinToString(" ").ifBlank { null }
        }
        return Team(name, t?.str("abbreviation")?.ifBlank { null } ?: name.take(3).uppercase(),
            t?.str("logo")?.ifBlank { null } ?: a?.optJSONObject("flag")?.str("href")?.ifBlank { null },
            t?.str("color")?.let { color(it) }, t?.str("alternateColor")?.let { color(it) }, score)
    }

    private fun parseCompetition(lg: League, e: JSONObject, comp: JSONObject, title: String): SportEvent? {
        val start = iso(comp.str("date").ifBlank { e.str("date") }) ?: return null
        val st = comp.optJSONObject("status") ?: e.optJSONObject("status")
        val type = st?.optJSONObject("type")
        val state = type?.str("state")?.ifBlank { null } ?: "pre"
        val cs = comp.optJSONArray("competitors") ?: JSONArray()
        val list = (0 until cs.length()).mapNotNull { cs.optJSONObject(it) }
        val home = list.firstOrNull { it.str("homeAway") == "home" } ?: list.getOrNull(0)
        val away = list.firstOrNull { it.str("homeAway") == "away" } ?: list.getOrNull(1)
        val clock = when {
            type?.str("name") == "STATUS_HALFTIME" -> tr("Poluvrijeme")
            state == "in" && lg.sport == "football" -> st?.str("displayClock")?.ifBlank { null } ?: type?.str("shortDetail")
            state == "in" -> type?.str("shortDetail")
            state == "post" -> type?.str("shortDetail")?.ifBlank { null } ?: "FT"
            else -> null
        }
        val two = lg.sport != "f1" && home != null && away != null
        return SportEvent(
            id = lg.path + "/" + comp.str("id").ifBlank { e.str("id") },
            sport = lg.sport, league = lg.name, leagueAbbr = lg.abbr,
            round = comp.optJSONObject("round")?.str("displayName")?.ifBlank { null }
                ?: comp.optJSONArray("notes")?.optJSONObject(0)?.str("headline")?.ifBlank { null },
            start = start, state = state, clock = clock,
            home = if (two) team(home) else null, away = if (two) team(away) else null,
            title = title, venue = comp.optJSONObject("venue")?.str("fullName")?.ifBlank { null },
        )
    }

    private fun espn(lg: League, date: String): List<SportEvent> {
        val o = JSONObject(http("https://site.api.espn.com/apis/site/v2/sports/${lg.path}/scoreboard?dates=$date"))
        val evs = o.optJSONArray("events") ?: return emptyList()
        val out = ArrayList<SportEvent>()
        for (i in 0 until evs.length()) {
            val e = evs.optJSONObject(i) ?: continue
            when (lg.sport) {
                "tennis" -> {
                    val groups = e.optJSONArray("groupings")
                    if (groups != null) for (g in 0 until groups.length()) {
                        val comps = groups.optJSONObject(g)?.optJSONArray("competitions") ?: continue
                        for (c in 0 until comps.length()) comps.optJSONObject(c)?.let { parseCompetition(lg, e, it, e.str("name"))?.let(out::add) }
                    }
                    else e.optJSONArray("competitions")?.let { comps ->
                        for (c in 0 until comps.length()) comps.optJSONObject(c)?.let { parseCompetition(lg, e, it, e.str("name"))?.let(out::add) }
                    }
                }
                "f1" -> e.optJSONArray("competitions")?.let { comps ->
                    for (c in 0 until comps.length()) {
                        val comp = comps.optJSONObject(c) ?: continue
                        val kind = comp.optJSONObject("type")?.str("abbreviation")?.ifBlank { null }
                        parseCompetition(lg, e, comp, e.str("name") + (kind?.let { " · $it" } ?: ""))?.let(out::add)
                    }
                }
                else -> e.optJSONArray("competitions")?.optJSONObject(0)?.let { parseCompetition(lg, e, it, e.str("name"))?.let(out::add) }
            }
        }
        return out
    }

    /** Matches of one day from all leagues; leagues that fail are skipped. */
    suspend fun day(offset: Int): List<SportEvent> = withContext(Dispatchers.IO) {
        val from = dayStart(offset); val to = dayStart(offset + 1)
        coroutineScope {
            LEAGUES.map { lg ->
                async {
                    // US-dated feeds: European evening matches are on the same date, night games on the day before
                    val dates = listOf(espnDate(from), espnDate(from - 86_400_000L)).distinct()
                    dates.flatMap { d -> try { espn(lg, d) } catch (e: Exception) { emptyList() } }
                }
            }.awaitAll().flatten()
        }.filter { it.start in from until to }.distinctBy { it.id }.sortedBy { it.start }
    }

    /* ---------------------------------------------------------------- your TV guide */

    data class Prog(val channel: String, val start: Long, val end: Long, val title: String, val category: String?, val desc: String? = null)
    data class Guide(val progs: List<Prog>, val byEpg: Map<String, List<Channel>>, val all: List<Channel>, val stats: String)

    private var guideFor: String? = null
    private var guideAt = 0L
    private var guideData: Guide? = null
    private val vsWords = Regex("\\s(-|–|vs\\.?|v)\\s|formula|grand prix|\\bf1\\b|uživo|uzivo|live", RegexOption.IGNORE_CASE)

    /**
     * Your channels (all of them, of every turned-on playlist) and the matches in their TV guide.
     * Cached for 3 hours in memory and on the box, so the provider is asked rarely.
     */
    suspend fun guide(ctx: android.content.Context, pls: List<Playlist>): Guide = withContext(Dispatchers.IO) {
        val key = pls.joinToString(",") { it.id }
        val cur = guideData
        if (guideFor == key && cur != null && System.currentTimeMillis() - guideAt < 3 * 3600_000L) return@withContext cur
        val all = pls.flatMap { p -> try { Repo.allChannels(p) } catch (e: Exception) { emptyList() } }
        val byEpg = all.filter { !it.epg.isNullOrBlank() }.groupBy { it.epg!!.lowercase() }
        val cache = File(ctx.cacheDir, "sports_guide_${key.hashCode()}.json")
        var progs: List<Prog> = emptyList()
        if (cache.exists() && System.currentTimeMillis() - cache.lastModified() < 3 * 3600_000L) progs = try { readProgs(cache) } catch (e: Exception) { emptyList() }
        if (progs.isEmpty()) {
            val from = dayStart(-1); val to = dayStart(5)
            progs = pls.filter { it.type == SourceType.XTREAM }.flatMap { p ->
                Xmltv.read(Xtream(p).xmltvUrl(), byEpg.keys, from, to) { t -> vsWords.containsMatchIn(t) }
            }
            if (Prefs.epgUrl.isNotBlank()) progs = progs + Xmltv.read(Prefs.epgUrl, byEpg.keys, from, to) { t -> vsWords.containsMatchIn(t) }
            try { writeProgs(cache, progs) } catch (_: Exception) {}
        }
        val g = Guide(progs, byEpg, all, trf("Pretraženo %s kanala · program za %s · %s emisija", all.size, byEpg.size, progs.size))
        guideFor = key; guideAt = System.currentTimeMillis(); guideData = g
        g
    }

    private fun writeProgs(f: File, l: List<Prog>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("c", it.channel).put("s", it.start).put("e", it.end).put("t", it.title).put("k", it.category ?: "")) }
        f.writeText(a.toString())
    }
    private fun readProgs(f: File): List<Prog> {
        val a = JSONArray(f.readText())
        return (0 until a.length()).map { val o = a.getJSONObject(it); Prog(o.str("c"), o.optLong("s"), o.optLong("e"), o.str("t"), o.str("k").ifBlank { null }) }
    }

    /** who usually broadcasts a league: words searched in your channel names (shown as "possible broadcast") */
    private val RIGHTS = mapOf(
        "UCL" to listOf("ARENA SPORT", "ARENA PREMIUM", "DAZN", "PRIME", "SKY SPORT", "CANAL", "SPORT KLUB", "SPORTKLUB", "MAX SPORT", "NOVA SPORT"),
        "UEL" to listOf("ARENA SPORT", "RTL+", "RTL NITRO", "NITRO", "SPORT KLUB", "SPORTKLUB", "MAX SPORT", "NOVA SPORT"),
        "UECL" to listOf("ARENA SPORT", "RTL+", "NITRO", "SPORT KLUB", "SPORTKLUB"),
        "PL" to listOf("ARENA SPORT", "ARENA PREMIUM", "SKY SPORT", "SKY SPORTS", "MAX SPORT", "NOVA SPORT", "CANAL"),
        "BL" to listOf("SKY SPORT BUNDESLIGA", "SKY BUNDESLIGA", "SKY SPORT", "DAZN", "SPORT KLUB", "SPORTKLUB", "ARENA SPORT", "SAT.1", "SAT1"),
        "LL" to listOf("ARENA SPORT", "DAZN", "SPORT KLUB", "SPORTKLUB", "MAX SPORT"),
        "SA" to listOf("ARENA SPORT", "DAZN", "SPORT KLUB", "SPORTKLUB", "MAX SPORT"),
        "L1" to listOf("ARENA SPORT", "DAZN", "SPORT KLUB", "SPORTKLUB"),
        "ERE" to listOf("ARENA SPORT", "SPORT KLUB", "SPORTKLUB", "DAZN"),
        "POR" to listOf("ARENA SPORT", "SPORT KLUB", "SPORTKLUB"),
        "TSL" to listOf("ARENA SPORT", "SPORT KLUB", "SPORTKLUB", "BEIN"),
        "AUT" to listOf("SKY SPORT AUSTRIA", "SKY SPORT", "ORF SPORT", "SPORT KLUB"),
        "UNL" to listOf("ARENA SPORT", "RTS", "BHT", "HRT", "RTL", "ZDF", "ARD", "DAZN"),
        "WCQ" to listOf("ARENA SPORT", "RTS", "BHT", "HRT", "RTL", "ZDF", "ARD", "DAZN"),
        "FRI" to listOf("ARENA SPORT", "SPORT KLUB", "SPORTKLUB"),
        "NBA" to listOf("SPORT KLUB", "SPORTKLUB", "ARENA SPORT", "DAZN", "MAX SPORT"),
        "F1" to listOf("ARENA SPORT", "SKY SPORT F1", "SKY SPORT", "SERVUS", "ORF", "SPORT KLUB"),
        "ATP" to listOf("SPORT KLUB", "SPORTKLUB", "ARENA SPORT", "SKY SPORT", "EUROSPORT"),
        "WTA" to listOf("SPORT KLUB", "SPORTKLUB", "ARENA SPORT", "SKY SPORT", "EUROSPORT"),
    )

    private fun maybeFor(ev: SportEvent, all: List<Pair<Channel, String>>): List<Channel> {
        val words = RIGHTS[ev.leagueAbbr] ?: return emptyList()
        return all.filter { (_, n) -> words.any { n.contains(it) } }.map { it.first }
            .distinctBy { it.url }
            .sortedWith(compareBy({ words.indexOfFirst { w -> (" " + it.name.uppercase() + " ").contains(w) } }, { qualityRank(it.name) }, { it.name }))
            .take(12)
    }
    private fun qualityRank(n: String) = when (qualityOf(n)) { "4K" -> 0; "FHD" -> 1; "HD" -> 2; else -> 3 }

    /** order of importance for the big card on top */
    private val PRIORITY = listOf("UCL", "PL", "BL", "LL", "SA", "UEL", "L1", "UNL", "WCQ", "F1", "NBA", "UECL", "ERE", "POR", "TSL", "AUT", "ATP", "WTA", "TV", "FRI")
    fun priority(ev: SportEvent): Int = PRIORITY.indexOf(ev.leagueAbbr).let { if (it < 0) 50 else it } - (if (ev.channels.isNotEmpty()) 100 else 0)

    /** match details (goals, cards) from the feed, for finished and live matches */
    suspend fun details(ev: SportEvent): List<String> = withContext(Dispatchers.IO) {
        val lg = LEAGUES.firstOrNull { ev.id.startsWith(it.path + "/") } ?: return@withContext emptyList()
        val id = ev.id.substringAfterLast('/')
        val o = try { JSONObject(http("https://site.api.espn.com/apis/site/v2/sports/${lg.path}/summary?event=$id")) } catch (e: Exception) { return@withContext emptyList() }
        val out = ArrayList<String>()
        val ke = o.optJSONArray("keyEvents") ?: o.optJSONArray("scoringPlays") ?: JSONArray()
        for (i in 0 until ke.length()) {
            val e = ke.optJSONObject(i) ?: continue
            val type = e.optJSONObject("type")?.str("text") ?: ""
            if (type.isNotBlank() && !Regex("goal|card|penalty|own|substitution", RegexOption.IGNORE_CASE).containsMatchIn(type)) continue
            if (type.contains("substitution", true)) continue
            val clock = e.optJSONObject("clock")?.str("displayValue") ?: ""
            val text = e.str("text").ifBlank { e.str("shortText") }
            if (text.isNotBlank()) out.add(listOf(clock, text).filter { it.isNotBlank() }.joinToString("  "))
        }
        out.take(30)
    }

    /* ---------------------------------------------------------------- matching */

    private val STOP = setOf("fc", "fk", "cf", "sc", "ac", "afc", "cd", "ud", "sv", "vfl", "vfb", "tsg", "bsc", "ssc", "as", "rc", "rcd", "sd", "ca",
        "cp", "sl", "ss", "us", "nk", "hnk", "kk", "rk", "ok", "bc", "the", "de", "la", "le", "vs", "v", "club", "fsv", "if", "bk", "sk", "ogc", "losc")
    private val GENERIC = setOf("real", "city", "united", "athletic", "atletico", "sporting", "borussia", "olympique", "racing", "dynamo", "dinamo",
        "red", "star", "saint", "sankt", "st", "new", "west", "sport", "internacional", "manchester", "madrid", "munchen", "munich", "london", "young")
    private val ALIAS = mapOf("internazionale" to "inter", "munich" to "munchen", "bayern munich" to "bayern", "crvena zvezda" to "zvezda",
        "red star belgrade" to "zvezda", "paris saint-germain" to "psg", "wolverhampton wanderers" to "wolves")

    private val MARKS = Regex("\\p{M}+")
    private val NON_ALNUM = Regex("[^a-z0-9]+")
    fun norm(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(MARKS, "")
        .replace("ø", "o").replace("đ", "dj").replace("ß", "ss").replace(NON_ALNUM, " ").trim()

    private fun tokens(name: String): Set<String> {
        val n = norm(name)
        val t = n.split(' ').filter { it.length >= 3 && it !in STOP && !it.all(Char::isDigit) }.toMutableSet()
        ALIAS.forEach { (k, v) -> if (n.contains(norm(k))) t.add(v) }
        return t
    }

    private fun teamIn(team: Team, words: Set<String>): Boolean = teamIn(tokens(team.name), words)
    private fun teamIn(t: Set<String>, words: Set<String>): Boolean {
        if (t.isEmpty()) return false
        val distinct = t.filter { it !in GENERIC }
        return if (distinct.isNotEmpty()) distinct.any { it in words } else t.all { it in words }
    }

    private val REPLAY = Regex("(\\(r\\)|\\(snimak\\)|snimka|snimak|reprise|highlights|sažetak|sazetak|najava|magazin|studio|emisija)", RegexOption.IGNORE_CASE)

    /** Adds your channels to each match and returns extra matches found only in your guide. */
    /** Heavy: always call off the main thread (see [combineAsync]). */
    fun combine(events: List<SportEvent>, g: Guide, offset: Int): List<SportEvent> {
        val progs = g.progs; val chans = g.byEpg
        val from = dayStart(offset); val to = dayStart(offset + 1)
        val day = progs.filter { it.start < to + 2 * 3600_000L && it.end > from && !REPLAY.containsMatchIn(it.title) }.sortedBy { it.start }
        val starts = LongArray(day.size) { day[it].start }
        val normTitle = HashMap<Prog, String>(day.size * 2)
        val words = HashMap<Prog, Set<String>>(day.size * 2)
        fun nt(p: Prog) = normTitle.getOrPut(p) { norm(p.title) }
        fun wd(p: Prog) = words.getOrPut(p) { nt(p).split(' ').toSet() }
        /** programmes starting within [win] of [t], found by binary search instead of scanning the whole day */
        fun near(t: Long, win: Long): List<Prog> {
            var lo = java.util.Arrays.binarySearch(starts, t - win).let { if (it < 0) -it - 1 else it }
            while (lo > 0 && starts[lo - 1] >= t - win) lo--
            val out = ArrayList<Prog>()
            var i = lo
            while (i < day.size && starts[i] <= t + win) { out.add(day[i]); i++ }
            return out
        }
        val upperAll = g.all.map { it to (" " + it.name.uppercase() + " ") }
        val used = HashSet<Prog>()
        val withCh = events.map { ev ->
            if (ev.home == null || ev.away == null) {
                // races: guide titles with "Formula 1" / "Grand Prix" around the start
                if (ev.sport != "f1") return@map ev
                val hits = near(ev.start, 90 * 60_000L).filter {
                    nt(it).let { t -> t.contains("formula") || t.contains("grand prix") || t.contains(" f1") || t.startsWith("f1") } }
                used.addAll(hits)
                return@map ev.copy(channels = hits.flatMap { chans[it.channel] ?: emptyList() }.distinctBy { it.url })
            }
            val th = tokens(ev.home.name); val ta = tokens(ev.away.name)
            val hits = near(ev.start, 75 * 60_000L).filter { pr -> val w = wd(pr); teamIn(th, w) && teamIn(ta, w) }
            used.addAll(hits)
            ev.copy(channels = hits.flatMap { chans[it.channel] ?: emptyList() }.distinctBy { it.url })
        }
        // matches that only your guide knows: "Team A - Team B" on a sport channel
        val vs = Regex("^(?:(.{2,40}?):\\s*)?(.{2,40}?)\\s+(?:-|–|vs\\.?|v\\.?)\\s+(.{2,40}?)(?:\\s*[(\\[].*)?$", RegexOption.IGNORE_CASE)
        val extra = day.filter { it !in used && it.start in from until to }.mapNotNull { pr ->
            val m = vs.find(pr.title.trim()) ?: return@mapNotNull null
            val (lgName, a, b) = m.destructured
            if (a.any { it.isDigit() } && b.any { it.isDigit() }) return@mapNotNull null
            Triple(pr, lgName.ifBlank { pr.category ?: "" }, a.trim() to b.trim())
        }.groupBy { (pr, _, ab) -> norm(ab.first + " " + ab.second) + "@" + (pr.start / (20 * 60_000L)) }
            .map { (_, list) ->
                val (pr, lgName, ab) = list.first()
                val now = System.currentTimeMillis()
                val text = (lgName + " " + pr.title + " " + list.joinToString(" ") { (chans[it.first.channel]?.firstOrNull()?.name ?: "") }).lowercase()
                SportEvent(
                    id = "guide/" + norm(pr.title) + pr.start, sport = guessSport(text),
                    league = lgName.ifBlank { tr("Na tvojim kanalima") }, leagueAbbr = "TV", round = null,
                    start = pr.start, state = if (now < pr.start) "pre" else if (now < pr.end) "in" else "post", clock = null,
                    home = Team(ab.first, ab.first.take(3).uppercase(), null, null, null, null),
                    away = Team(ab.second, ab.second.take(3).uppercase(), null, null, null, null),
                    title = pr.title, venue = null,
                    channels = list.flatMap { chans[it.first.channel] ?: emptyList() }.distinctBy { it.url }, fromGuide = true,
                )
            }
        return (withCh.map { it.copy(maybe = if (it.channels.isEmpty()) maybeFor(it, upperAll) else emptyList()) } + extra).sortedBy { it.start }
    }

    private fun guessSport(t: String): String = when {
        listOf("košark", "kosark", "basket", "nba", "aba ", "euroleague", "evroliga", "euroliga", "kk ").any { t.contains(it) } -> "basketball"
        listOf("rukomet", "handball", "ehf", "rk ").any { t.contains(it) } -> "handball"
        listOf("odbojk", "volley", "ok ").any { t.contains(it) } -> "volleyball"
        listOf("tenis", "tennis", "atp", "wta").any { t.contains(it) } -> "tennis"
        listOf("formula", "grand prix", "f1").any { t.contains(it) } -> "f1"
        listOf("fudbal", "nogomet", "football", "fussball", "liga", "league", "cup", "kup", "fk ", "nk ", "fc ").any { t.contains(it) } -> "football"
        else -> "other"
    }
}

/* ============================================================== XMLTV */

object Xmltv {
    private fun time(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        val t = s.trim()
        for (p in listOf("yyyyMMddHHmmss Z", "yyyyMMddHHmmss")) {
            try { return SimpleDateFormat(p, Locale.US).apply { if (!p.contains('Z')) timeZone = TimeZone.getTimeZone("UTC") }.parse(t)?.time } catch (_: Exception) {}
        }
        return null
    }

    /** Streams the guide and keeps only programmes of [channels] (lower-case ids) between [from] and [to]. */
    fun read(url: String, channels: Set<String>?, from: Long, to: Long, keepTitle: ((String) -> Boolean)? = null): List<Sports.Prog> {
        if (channels != null && channels.isEmpty()) return emptyList()
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 20000; c.readTimeout = 120000
        c.setRequestProperty("User-Agent", Net.UA)
        val out = ArrayList<Sports.Prog>()
        try {
            if (c.responseCode !in 200..299) return emptyList()
            var input: java.io.InputStream = java.io.BufferedInputStream(c.inputStream, 64 * 1024)
            input.mark(4)
            val b0 = input.read(); val b1 = input.read(); input.reset()
            if (b0 == 0x1f && b1 == 0x8b) input = java.util.zip.GZIPInputStream(input, 64 * 1024)
            val xp = android.util.Xml.newPullParser()
            xp.setInput(input, null)
            var ch: String? = null; var start = 0L; var end = 0L; var title: String? = null; var cat: String? = null; var desc: String? = null
            var keep = false; var tag: String? = null
            var ev = xp.eventType
            while (ev != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                when (ev) {
                    org.xmlpull.v1.XmlPullParser.START_TAG -> {
                        tag = xp.name
                        if (tag == "programme") {
                            ch = xp.getAttributeValue(null, "channel")?.lowercase()
                            start = time(xp.getAttributeValue(null, "start")) ?: 0L
                            end = time(xp.getAttributeValue(null, "stop")) ?: start
                            keep = ch != null && (channels == null || ch in channels) && end > from && start < to
                            title = null; cat = null; desc = null
                        }
                    }
                    org.xmlpull.v1.XmlPullParser.TEXT -> if (keep) {
                        if (tag == "title" && title == null) title = xp.text?.trim()
                        else if (tag == "category" && cat == null) cat = xp.text?.trim()
                        else if (tag == "desc" && desc == null && channels == null) desc = xp.text?.trim()
                    }
                    org.xmlpull.v1.XmlPullParser.END_TAG -> {
                        if (xp.name == "programme") {
                            if (keep && !title.isNullOrBlank() && (keepTitle == null || keepTitle(title!!))) out.add(Sports.Prog(ch!!, start, end, title!!, cat, desc))
                            keep = false
                        }
                        tag = null
                    }
                }
                ev = xp.next()
            }
        } catch (e: Exception) {
            // a broken or cut guide still gives what was read so far
        } finally { c.disconnect() }
        return out
    }
}

/** Your own TV guide (XMLTV link from Settings), used when the provider sends no programme for a channel. */
object OwnGuide {
    private var forUrl: String? = null
    private var at = 0L
    private var data: Map<String, List<Sports.Prog>> = emptyMap()
    private val lock = kotlinx.coroutines.sync.Mutex()

    suspend fun of(epgId: String?, name: String): List<EpgItem> = withContext(Dispatchers.IO) {
        val url = Prefs.epgUrl.trim()
        if (url.isBlank()) return@withContext emptyList()
        lock.lock()
        try {
            if (forUrl != url || System.currentTimeMillis() - at > 6 * 3600_000L) {
                val now = System.currentTimeMillis()
                data = Xmltv.read(url, null, now - 3 * 3600_000L, now + 36 * 3600_000L).groupBy { it.channel }
                forUrl = url; at = now
            }
        } finally { lock.unlock() }
        val list = epgId?.lowercase()?.let { data[it] }
            ?: data[Sports.norm(name).replace(" ", "")]
            ?: emptyList()
        val now = System.currentTimeMillis()
        list.filter { it.end > now - 60_000 }.sortedBy { it.start }.take(12).map { EpgItem(it.title, it.desc, it.start, it.end) }
    }
}
