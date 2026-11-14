package com.pretorian.tv

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
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
    /** channels the AI (Gemini, with web search) says show it in your country */
    val ai: List<Channel> = emptyList(),
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
        League("football", "soccer/fifa.world", "Svjetsko prvenstvo", "WC"),
        League("football", "soccer/uefa.euro", "Evropsko prvenstvo", "EURO"),
        League("football", "soccer/ger.2", "2. Bundesliga", "BL2"),
        League("football", "soccer/ger.dfb_pokal", "DFB Pokal", "DFB"),
        League("football", "soccer/usa.1", "MLS", "MLS"),
        League("football", "soccer/bih.1", "Premijer liga BiH", "BIH"),
        League("football", "soccer/srb.1", "SuperLiga Srbije", "SRB"),
        League("football", "soccer/cro.1", "HNL", "HNL"),
        League("football", "soccer/sui.1", "Švicarska Super liga", "SUI"),
        League("basketball", "basketball/nba", "NBA", "NBA"),
        League("f1", "racing/f1", "Formula 1", "F1"),
        League("tennis", "tennis/atp", "ATP", "ATP"),
        League("tennis", "tennis/wta", "WTA", "WTA"),
    )

    /** where a competition is played (Settings › Sport › Continents) */
    fun contOf(l: League) = when (l.abbr) { "MLS", "NBA" -> "na"; "WC", "FRI", "F1", "ATP", "WTA" -> "world"; else -> "eu" }
    val CONTINENTS = listOf("eu" to "Evropa", "sa" to "Južna Amerika", "na" to "Sjeverna Amerika", "af" to "Afrika", "as" to "Azija")
    /** competitions grouped as in Settings › Sport */
    val LEAGUE_GROUPS = listOf(
        "Reprezentacije" to listOf("WC", "EURO", "UNL", "WCQ", "FRI"),
        "Evropski klubovi" to listOf("UCL", "UEL", "UECL"),
        "Top lige" to listOf("PL", "LL", "BL", "SA", "L1"),
        "Naša regija" to listOf("BIH", "SRB", "HNL", "BL2", "AUT", "SUI"),
        "Ostale lige" to listOf("ERE", "POR", "TSL", "DFB", "MLS"),
        "Košarka" to listOf("NBA"), "Formula 1" to listOf("F1"), "Tenis" to listOf("ATP", "WTA"),
    )
    val BIG = listOf("WC", "EURO", "UNL", "WCQ", "UCL", "UEL", "UECL", "PL", "LL", "BL", "SA", "L1", "NBA", "F1", "ATP", "WTA")
    val REGION = listOf("BIH", "SRB", "HNL", "BL2", "AUT", "SUI")
    /** competitions you follow: chosen competitions (all when none chosen) on the chosen continents */
    fun activeLeagues(): List<League> {
        val raw = Prefs.str("sport_leagues", BIG.joinToString(","))
        if (raw == "-") return emptyList()
        val sel = raw.split(',').filter { it.isNotBlank() }.toSet()
        val cont = Prefs.str("sport_cont", "eu").split(',').map { if (it == "am") "na" else it }.toSet() + "world"
        return LEAGUES.filter { (sel.isEmpty() || it.abbr in sel) && contOf(it) in cont }
    }
    /** favourite teams, written in Settings (comma separated) */
    fun teams(): List<String> = Prefs.str("sport_teams").split(',').map { norm(it.trim()) }.filter { it.length >= 3 }
    fun isFav(ev: SportEvent): Boolean { val t = teams(); if (t.isEmpty()) return false
        val names = listOfNotNull(ev.home?.name, ev.away?.name, ev.title).map { norm(it) }; return t.any { w -> names.any { it.contains(w) } } }
    /** reminders for the matches of your favourite teams in the next 3 days */
    suspend fun remindTeams() {
        val min = Prefs.str("sport_team_remind", "0").toIntOrNull() ?: 0
        if (min <= 0 || teams().isEmpty()) return
        upcoming(3).filter { isFav(it) }.forEach { ev ->
            val t = listOfNotNull(ev.home?.name, ev.away?.name).joinToString(" – ").ifBlank { ev.title }
            if (!Reminders.has(t, ev.start)) Reminders.add(Reminder("sp" + ev.id.hashCode(), t, ev.start, null, ev.league, null, null, "sport"))
        }
    }

    /** the sports in the filter row, in this order; others that have matches that day follow */
    val SPORTS = listOf("football" to "Fudbal", "basketball" to "Košarka", "volleyball" to "Odbojka",
        "f1" to "Formula 1", "tennis" to "Tenis", "handball" to "Rukomet", "motogp" to "MotoGP")
    val MORE_SPORTS = listOf("hockey" to "Hokej", "waterpolo" to "Vaterpolo", "other" to "Ostalo")

    fun sportColor(s: String) = when (s) {
        "football" -> Color(0xFF22C55E); "basketball" -> Color(0xFFF97316); "volleyball" -> Color(0xFFEAB308)
        "f1" -> Color(0xFFEF4444); "tennis" -> Color(0xFFA3E635); "handball" -> Color(0xFF38BDF8)
        "motogp" -> Color(0xFFF43F5E); "hockey" -> Color(0xFF60A5FA); "waterpolo" -> Color(0xFF22D3EE); else -> Color(0xFF94A3B8)
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
            activeLeagues().map { lg ->
                async {
                    // US-dated feeds: European evening matches are on the same date, night games on the day before
                    val dates = listOf(espnDate(from), espnDate(from - 86_400_000L)).distinct()
                    dates.flatMap { d -> try { espn(lg, d) } catch (e: Exception) { emptyList() } }
                }
            }.awaitAll().flatten()
        }.filter { it.start in from until to }.distinctBy { it.id }.sortedBy { it.start }
    }

    /**
     * matches that have not started yet, the next [days] days: football first, then what is closest to you
     * (your country's leagues, the region, the big European ones), then by time
     */
    suspend fun upcoming(days: Int = 7): List<SportEvent> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val range = espnDate(dayStart(0) - 86_400_000L) + "-" + espnDate(dayStart(days))
        val list = coroutineScope {
            activeLeagues().map { lg -> async { try { espn(lg, range) } catch (e: Exception) { emptyList() } } }.awaitAll().flatten()
        }.filter { it.state == "pre" && it.start > now - 60_000 && it.start < dayStart(days) }.distinctBy { it.id }
        list.sortedWith(compareBy({ if (it.sport == "football") 0 else 1 }, { priority(it) }, { it.start }))
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
    /** one download at a time: a second caller waits for the first one instead of starting again */
    private val guideLock = kotlinx.coroutines.sync.Mutex()
    suspend fun guide(ctx: android.content.Context, pls: List<Playlist>): Guide = guideLock.withLock { guideNow(ctx, pls) }
    private suspend fun guideNow(ctx: android.content.Context, pls: List<Playlist>): Guide = withContext(Dispatchers.IO) {
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
            EpgSources.urls(null).forEach { u -> progs = progs + try { Xmltv.read(u, byEpg.keys, from, to) { t -> vsWords.containsMatchIn(t) } } catch (e: Exception) { emptyList() } }
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
        if (!Prefs.bool("sport_src_rights", true)) return emptyList()
        val words = RIGHTS[ev.leagueAbbr] ?: return emptyList()
        return all.filter { (_, n) -> words.any { n.contains(it) } }.map { it.first }
            .distinctBy { it.url }
            .sortedWith(compareBy({ regionRank(it.name) }, { words.indexOfFirst { w -> (" " + it.name.uppercase() + " ").contains(w) } }, { qualityRank(it.name) }, { it.name }))
            .take(12)
    }
    private fun qualityRank(n: String) = when (qualityOf(n)) { "4K" -> 0; "FHD" -> 1; "HD" -> 2; else -> 3 }

    /** order of importance for the big card on top */
    /** your country: chosen in Settings › Sport, else the box's country */
    val country: String get() = Prefs.str("sport_country").ifBlank { Locale.getDefault().country.uppercase().ifBlank { "DE" } }

    /** what matters most where you live; the biggest tournaments are always on top */
    private fun priorityList(): List<String> {
        val top = listOf("WC", "EURO", "UCL")
        val rest = when (country) {
            "DE", "AT", "CH" -> listOf("BL", "DFB", "UEL", "PL", "LL", "SA", "UNL", "WCQ", "BL2", "AUT", "F1", "UECL", "L1", "NBA", "ATP", "WTA")
            "GB", "UK", "IE" -> listOf("PL", "UEL", "LL", "SA", "BL", "UNL", "WCQ", "F1", "UECL", "L1", "NBA", "ATP", "WTA")
            "US", "CA" -> listOf("NBA", "MLS", "PL", "F1", "LL", "BL", "SA", "ATP", "WTA", "UEL")
            "ES" -> listOf("LL", "UEL", "PL", "SA", "BL", "F1", "NBA", "ATP", "WTA")
            "IT" -> listOf("SA", "UEL", "PL", "LL", "BL", "F1", "NBA", "ATP", "WTA")
            "FR" -> listOf("L1", "UEL", "PL", "LL", "SA", "BL", "F1", "NBA", "ATP", "WTA")
            "TR" -> listOf("TSL", "UEL", "PL", "LL", "SA", "BL", "NBA", "ATP", "WTA")
            else -> listOf("PL", "BL", "LL", "SA", "UEL", "UNL", "WCQ", "F1", "NBA", "UECL", "L1", "ATP", "WTA", "AUT")   // Balkan and the rest of Europe
        }
        return top + rest + listOf("ERE", "POR", "TSL", "MLS", "BL2", "TV", "FRI")
    }
    fun priority(ev: SportEvent): Int = priorityList().indexOf(ev.leagueAbbr).let { if (it < 0) 50 else it } - (if (isFav(ev)) 300 else 0) -
        (if (ev.channels.isNotEmpty()) 100 else if (ev.ai.isNotEmpty()) 60 else 0)

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
    private val ALIAS = mapOf("internazionale" to "inter", "munich" to "munchen", "bayern munich" to "bayern", "crvena zvezda" to "zvezda",  // i18n-ignore
        "red star belgrade" to "zvezda", "paris saint-germain" to "psg", "wolverhampton wanderers" to "wolves")

    private val MARKS = Regex("\\p{M}+")
    private val NON_ALNUM = Regex("[^a-z0-9]+")
    fun norm(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(MARKS, "")
        .replace("ø", "o").replace("đ", "dj").replace("ß", "ss").replace(NON_ALNUM, " ").trim()  // i18n-ignore

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
    /** Last result per day, shown at once when you come back to the Sports Center. */
    val lastCombined = HashMap<Int, List<SportEvent>>()

    /** Heavy: always call off the main thread. */
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
        val extra = day.filter { it !in used && it.start in from until to && !NOT_SPORT.containsMatchIn(it.title) }.mapNotNull { pr ->
            val m = vs.find(pr.title.trim()) ?: return@mapNotNull null
            // only real matches: on a sports channel, in a sports category, or naming a sport / club
            val chName = chans[pr.channel]?.joinToString(" ") { it.name } ?: ""
            val sporty = SPORT_CH.containsMatchIn(chName) || (pr.category?.let { SPORT_CAT.containsMatchIn(it) } == true) ||
                guessSport((pr.title + " " + (pr.category ?: "")).lowercase()) != "other"
            if (!sporty) return@mapNotNull null
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
        return (withCh.map { it.copy(maybe = if (it.channels.isEmpty()) maybeFor(it, upperAll) else emptyList()) } + extra)
            .map { it.copy(channels = it.channels.sortedBy { c -> regionRank(c.name) }) }
            .sortedBy { it.start }
    }

    private val SPORT_CH = Regex("sport|arena|dazn|eurosport|espn|bein|setanta|klub|bundesliga|laliga|premier|golf|tennis|ufc|fight|nba|nfl|nhl|mlb|tsn|bt sport|tnt sport|ziggo|digi sport|polsat sport|eleven|ssc|sky sport|canal\\+ sport|movistar|match|sportitalia|sport tv|nova s|max sport|supersport", RegexOption.IGNORE_CASE)
    private val SPORT_CAT = Regex("sport|fudbal|nogomet|football|fußball|fussball|soccer|basket|košark|tenis|tennis|rukomet|handball|odbojk|volley|motor|formula", RegexOption.IGNORE_CASE)
    private val NOT_SPORT = Regex("news|vijesti|vesti|nachrichten|dnevnik|morning|jutarnj|frühstück|talk|kitchen|cooking|oxygen|shop|teleshop|infomercial|paid program|weather|vrijeme|horoskop|documentary|dokumentar|episode|epizoda|folge|staffel|season|film|movie|cartoon|crtani", RegexOption.IGNORE_CASE)

    /** Channels from your part of the world first (BA| RS| HR| DE| ...), far-away ones (US| ...) last. */
    private val REGION: List<String> by lazy {
        val c = Locale.getDefault().country.uppercase()
        val balkan = listOf("BA", "RS", "HR", "ME", "SI", "MK", "EXYU", "EX-YU", "YU", "BALKAN")
        val dach = listOf("DE", "AT", "CH")
        val near = when (c) {
            in balkan -> listOf(c) + balkan + dach
            in dach -> listOf(c) + dach + balkan
            "US", "CA" -> listOf("US", "CA")
            "GB", "UK", "IE" -> listOf("UK", "GB", "IE")
            else -> listOf(c) + balkan + dach
        }
        (near + listOf("UK", "GB", "EU", "INT", "FR", "IT", "ES", "NL", "PT", "TR", "PL")).distinct()
    }
    private val PREFIX = Regex("^\\s*[\\[(|]?\\s*([A-Z-]{2,6})\\s*[|:\\])-]")
    private val rankCache = java.util.concurrent.ConcurrentHashMap<String, Int>()
    fun regionRank(name: String): Int = rankCache.getOrPut(name) {
        val m = PREFIX.find(name.uppercase()) ?: return@getOrPut 40
        val i = REGION.indexOf(m.groupValues[1])
        if (i < 0) 80 else i
    }

    private fun guessSport(t: String): String = when {
        listOf("košark", "kosark", "basket", "nba", "aba ", "euroleague", "evroliga", "euroliga", "kk ").any { t.contains(it) } -> "basketball"  // i18n-ignore
        listOf("rukomet", "handball", "ehf", "rk ").any { t.contains(it) } -> "handball"  // i18n-ignore
        listOf("odbojk", "volley", "ok ").any { t.contains(it) } -> "volleyball"
        listOf("tenis", "tennis", "atp", "wta").any { t.contains(it) } -> "tennis"  // i18n-ignore
        listOf("motogp", "moto gp", "moto2", "moto3", "superbike").any { t.contains(it) } -> "motogp"  // i18n-ignore
        listOf("hokej", "hockey", "nhl", "khl").any { t.contains(it) } -> "hockey"  // i18n-ignore
        listOf("vaterpolo", "waterpolo", "water polo").any { t.contains(it) } -> "waterpolo"  // i18n-ignore
        listOf("formula", "grand prix", "f1").any { t.contains(it) } -> "f1"
        listOf("fudbal", "nogomet", "football", "fussball", "liga", "league", "cup", "kup", "fk ", "nk ", "fc ").any { t.contains(it) } -> "football"  // i18n-ignore
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
    private class Cache(val at: Long, val data: Map<String, List<Sports.Prog>>)
    private val caches = HashMap<String, Cache>()
    private val lock = kotlinx.coroutines.sync.Mutex()
    fun drop(url: String) { caches.remove(url) }

    private suspend fun data(url: String): Map<String, List<Sports.Prog>> {
        lock.lock()
        try {
            val c = caches[url]
            if (c != null && System.currentTimeMillis() - c.at < 6 * 3600_000L) return c.data
            val now = System.currentTimeMillis()
            val d = try { Xmltv.read(url, null, now - 3 * 3600_000L, now + 36 * 3600_000L).groupBy { it.channel } } catch (e: Exception) { emptyMap() }
            caches[url] = Cache(now, d)
            return d
        } finally { lock.unlock() }
    }

    /** guide of a channel from your own sources (the playlist's own first) */
    suspend fun of(epgId: String?, name: String, pid: String? = null): List<EpgItem> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        for (url in EpgSources.urls(pid)) {
            val d = data(url)
            val list = epgId?.lowercase()?.ifBlank { null }?.let { d[it] } ?: d[Sports.norm(name).replace(" ", "")]
            if (!list.isNullOrEmpty()) return@withContext list.filter { it.end > now - 60_000 }.sortedBy { it.start }.take(40)
                .map { EpgItem(it.title, it.desc, it.start, it.end) }
        }
        emptyList()
    }
}
