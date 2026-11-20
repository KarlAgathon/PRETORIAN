package com.pretorian.tv

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
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
    /** goals and cards: (side "h"/"a", kind "goal"/"yellow"/"red", minute, player) */
    val details: List<MatchDetail> = emptyList(),
    /** the league's country code (flag), e.g. "de", "ba", "eu" */
    val country: String = "",
    /** TV stations ESPN lists for the match (any country) */
    val tv: List<String> = emptyList(),
    /** a guide broadcast that is a recording / replay (snimak, repriza, or the match was played at another time) */
    val replay: Boolean = false,
    /** "ht" halftime, "et" extra time, "pen" penalties, from the feed's status (not translated) */
    val stage: String = "",
    val observedAt: Long = 0,
) {
    val stale get() = observedAt > 0 && state == "in" && System.currentTimeMillis() - observedAt > 120_000L
    /** live now: a real match from the score feed ("in"), never a guide broadcast, never one that started more than 6 hours ago */
    val live get() = !fromGuide && state == "in" && System.currentTimeMillis() - start in 0 until 6 * 3600_000L && !stale
    /** a guide broadcast on your channels right now (no live score) */
    val onAir get() = fromGuide && state == "in"
    /** finished, also a stale "live" one */
    val over get() = state == "post" || (state == "in" && !fromGuide && !live && !stale)
}

data class League(val sport: String, val path: String, val name: String, val abbr: String)

data class MatchDetail(val side: String, val kind: String, val minute: String, val player: String)

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
    val REGION_LEAGUES = listOf("BIH", "SRB", "HNL", "BL2", "AUT", "SUI")
    /** competitions you follow: chosen competitions (all when none chosen) on the chosen continents */
    fun activeLeagues(): List<League> {
        val raw = Prefs.str("sport_leagues", BIG.joinToString(","))
        val sel = raw.split(',').filter { it.isNotBlank() }.toSet()
        val cont = Prefs.str("sport_cont", "eu").split(',').map { if (it == "am") "na" else it }.toSet() + "world"
        val followed = Prefs.str("sm_follow_leagues").split('|').toSet()
        return LEAGUES.filter { it.name in followed || ((sel.isEmpty() || it.abbr in sel) && contOf(it) in cont) }
    }
    /** favourite teams, written in Settings (comma separated) */
    fun teams(): List<String> = Prefs.str("sport_teams").split(',').map { norm(it.trim()) }.filter { it.length >= 3 }
    fun isFav(ev: SportEvent): Boolean { val t = teams(); if (t.isEmpty()) return false
        val names = listOfNotNull(ev.home?.name, ev.away?.name, ev.title).map { norm(it) }; return t.any { w -> names.any { it.contains(w) } } }
    /** reminders for the matches of your favourite teams in the next 3 days */
    suspend fun remindTeams() {
        // followed teams and matches get a reminder before kick-off by themselves (Reminders › "Remind me" times)
        val min = Prefs.str("sport_team_remind", "1").toIntOrNull() ?: 1
        val follow = MatchFollow.list()
        if (min <= 0 || (teams().isEmpty() && follow.isEmpty())) return
        upcoming(3).filter { isFav(it) || it.id in follow }.forEach { ev ->
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

    internal fun iso(s: String): Long? {
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
        // penalties (shoot-out) score, when ESPN gives it
        val pen = home?.optString("shootoutScore")?.takeIf { it.isNotBlank() && it != "null" }?.let { h ->
            away?.optString("shootoutScore")?.takeIf { it.isNotBlank() && it != "null" }?.let { a -> h.substringBefore('.') + ":" + a.substringBefore('.') } }
        val sName = type?.str("name") ?: ""
        val clock = when {
            sName == "STATUS_HALFTIME" || type?.str("shortDetail") == "HT" -> tr("Poluvrijeme")
            sName.contains("SHOOTOUT") || (state == "in" && pen != null) -> tr("Penali") + (pen?.let { " $it" } ?: "")
            state == "in" && lg.sport == "football" -> (st?.str("displayClock")?.ifBlank { null } ?: type?.str("shortDetail")) +
                (if (sName.contains("OVERTIME") || sName.contains("EXTRA")) " · " + tr("produžeci") else "")
            state == "in" -> type?.str("shortDetail")
            state == "post" -> (type?.str("shortDetail")?.ifBlank { null } ?: "FT") + (pen?.let { " · " + tr("pen.") + " " + it } ?: "")
            else -> null
        }
        val two = lg.sport != "f1" && home != null && away != null
        // goals and cards (ESPN gives them for football)
        val homeId = home?.optJSONObject("team")?.str("id") ?: ""
        val det = comp.optJSONArray("details")?.let { d -> (0 until d.length()).mapNotNull { d.optJSONObject(it) }.mapNotNull { x ->
            val t = x.optJSONObject("type")?.str("text")?.lowercase() ?: ""
            val kind = when { x.optBoolean("redCard") || t.contains("red") -> "red"; x.optBoolean("yellowCard") || t.contains("yellow") -> "yellow"
                x.optBoolean("scoringPlay") || t.contains("goal") -> "goal"; t.contains("penalty awarded") -> "penalty"; else -> return@mapNotNull null }
            val who = x.optJSONArray("athletesInvolved")?.optJSONObject(0)?.str("displayName") ?: ""
            val min = x.optJSONObject("clock")?.str("displayValue") ?: ""
            MatchDetail(if (x.optJSONObject("team")?.str("id") == homeId) "h" else "a", kind + (if (x.optBoolean("penaltyKick")) "_pen" else "") + (if (x.optBoolean("ownGoal")) "_og" else ""), min, who)
        } } ?: emptyList()
        val tvs = ArrayList<String>()
        comp.optJSONArray("broadcasts")?.let { b -> for (i in 0 until b.length()) b.optJSONObject(i)?.optJSONArray("names")?.let { n -> for (j in 0 until n.length()) tvs += n.optString(j) } }
        comp.optJSONArray("geoBroadcasts")?.let { b -> for (i in 0 until b.length()) b.optJSONObject(i)?.optJSONObject("media")?.str("shortName")?.let { tvs += it } }
        return SportEvent(
            details = det, country = countryOf(lg.path), tv = tvs.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
            id = lg.path + "/" + comp.str("id").ifBlank { e.str("id") },
            sport = lg.sport, league = lg.name, leagueAbbr = lg.abbr,
            round = comp.optJSONObject("round")?.str("displayName")?.ifBlank { null }
                ?: comp.optJSONArray("notes")?.optJSONObject(0)?.str("headline")?.ifBlank { null },
            start = start, state = state, clock = clock,
            stage = when { sName == "STATUS_HALFTIME" -> "ht"; sName.contains("SHOOTOUT") || (state == "in" && pen != null) -> "pen"
                sName.contains("OVERTIME") || sName.contains("EXTRA") -> "et"; else -> "" },
            home = if (two) team(home) else null, away = if (two) team(away) else null,
            title = title, venue = comp.optJSONObject("venue")?.str("fullName")?.ifBlank { null },
        )
    }

    private val requests = kotlinx.coroutines.sync.Semaphore(4)
    private val locks = Array(32) { kotlinx.coroutines.sync.Mutex() }
    private val failures = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val cooldown = java.util.concurrent.ConcurrentHashMap<String, Long>()
    @Volatile private var rateLimitUntil = 0L
    fun feedDelayed() = System.currentTimeMillis().let { now -> rateLimitUntil > now || cooldown.values.any { it >= now - 60_000L } }

    private fun cacheFile(lg: League, date: String) = Repo.diskDir?.resolve("sports-feeds")?.resolve(lg.path.replace('/', '_') + "_" + date + ".json")

    private fun cached(lg: League, date: String): List<SportEvent> {
        val file = cacheFile(lg, date) ?: return emptyList()
        if (!file.isFile) return emptyList()
        return try { parseBoard(lg, file.readText(), file.lastModified()) } catch (_: Exception) { emptyList() }
    }

    private suspend fun espn(lg: League, date: String): List<SportEvent> {
        val key = lg.path + date
        return locks[(key.hashCode() and Int.MAX_VALUE) % locks.size].withLock {
            val file = cacheFile(lg, date)
            val now = System.currentTimeMillis()
            val ttl = if ('-' in date) 20 * 60_000L else if (date < espnDate(dayStart(-1))) 6 * 3600_000L else 50_000L
            if (SportsFeedCache.fresh(file, now, ttl) ||
                now < rateLimitUntil || now < (cooldown[key] ?: 0L)) return@withLock cached(lg, date)
            try {
                val raw = requests.withPermit {
                    if (System.currentTimeMillis() < rateLimitUntil) return@withLock cached(lg, date)
                    http("https://site.api.espn.com/apis/site/v2/sports/${lg.path}/scoreboard?dates=$date&limit=500", 6500)  // i18n-ignore
                }
                val at = System.currentTimeMillis()
                val events = parseBoard(lg, raw, at)
                if (file != null) try {
                    SportsFeedCache.save(file, raw)
                } catch (_: java.io.IOException) {}
                failures.remove(key)
                cooldown.remove(key)
                events
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                if (e.message == "HTTP 429") rateLimitUntil = System.currentTimeMillis() + 5 * 60_000L
                val expired = cooldown.filterValues { it < now - 86_400_000L }.keys
                expired.forEach { cooldown.remove(it); failures.remove(it) }
                val attempts = ((failures[key] ?: 0) + 1).coerceAtMost(5)
                failures[key] = attempts
                cooldown[key] = System.currentTimeMillis() + (30_000L shl (attempts - 1))
                cached(lg, date)
            }
        }
    }

    private fun parseBoard(lg: League, raw: String, at: Long): List<SportEvent> {
        val evs = JSONObject(raw).getJSONArray("events")
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
        return out.map { it.copy(observedAt = at) }
    }

    /** Matches of one day from all leagues; leagues that fail are skipped. */
    suspend fun day(offset: Int, onPart: ((List<SportEvent>) -> Unit)? = null): List<SportEvent> = withContext(Dispatchers.IO) {
        val from = dayStart(offset); val to = dayStart(offset + 1)
        val dates = listOf(espnDate(from), espnDate(dayStart(offset - 1))).distinct()
        val leagues = activeLeagues()
        val result = boards(leagues, dates, onPart) { it.start in from until to }
        if (offset == 0 && onPart != null) {
            Repo.diskDir?.resolve("sports-feeds")?.listFiles()?.filter {
                it.lastModified() < System.currentTimeMillis() - 7 * 86_400_000L
            }?.forEach { it.delete() }
        }
        result
    }

    private suspend fun boards(leagues: List<League>, dates: List<String>,
        onPart: ((List<SportEvent>) -> Unit)?, include: (SportEvent) -> Boolean): List<SportEvent> = coroutineScope {
        val rows = linkedMapOf<String, List<SportEvent>>()
        fun snapshot() = rows.values.flatten().filter(include).distinctBy { it.id }.sortedBy { it.start }
        if (onPart != null) {
            leagues.forEach { lg -> rows[lg.path] = dates.flatMap { cached(lg, it) } }
            snapshot().takeIf { it.isNotEmpty() }?.let(onPart)
        }
        val countries = Prefs.str("sport_country").lowercase().split(',').toSet()
        val followed = Prefs.str("sm_follow_leagues").split('|').toSet()
        leagues.sortedBy { when { it.name in followed -> 0; countryOf(it.path) in countries -> 1; it.abbr in BIG -> 2; else -> 3 } }.map { lg ->
            async {
                val result = dates.flatMap { espn(lg, it) }
                synchronized(rows) {
                    val previous = rows.put(lg.path, result)
                    if (result != previous && (result.isNotEmpty() || !previous.isNullOrEmpty())) onPart?.invoke(snapshot())
                }
            }
        }.awaitAll()
        snapshot()
    }

    /** only the leagues of these matches, today (a followed match is live: one small request instead of every league) */
    suspend fun leaguesOf(evs: List<SportEvent>): List<SportEvent> = withContext(Dispatchers.IO) {
        val abbrs = evs.map { it.leagueAbbr }.toSet()
        val from = dayStart(0)
        LEAGUES.filter { it.abbr in abbrs }.distinctBy { it.path }.flatMap { lg ->
            listOf(espnDate(from), espnDate(from - 86_400_000L)).distinct().flatMap { d -> try { espn(lg, d) } catch (e: Exception) { emptyList() } }
        }.distinctBy { it.id }
    }

    /**
     * matches that have not started yet, the next [days] days: football first, then what is closest to you
     * (your country's leagues, the region, the big European ones), then by time
     */
    suspend fun upcoming(days: Int = 7, onPart: ((List<SportEvent>) -> Unit)? = null): List<SportEvent> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val range = espnDate(dayStart(-1)) + "-" + espnDate(dayStart(days))
        val end = dayStart(days)
        val list = boards(activeLeagues(), listOf(range), onPart) { it.state == "pre" && it.start >= now && it.start < end }
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
        val key = pls.joinToString(",") { it.id } + "|" + EpgSources.urls(null).joinToString("|").hashCode()
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
                val src = GuideDb.srcOf(p)
                if (GuideDb.has(src)) GuideDb.range(src, from, to, byEpg.keys, vsWords)
                else Xmltv.read(Xtream(p).xmltvUrl(), byEpg.keys, from, to) { t -> vsWords.containsMatchIn(t) }
            }
            EpgSources.urls(null).forEach { u -> progs = progs + try {
                val src = GuideDb.srcOf(u)
                if (GuideDb.has(src)) GuideDb.range(src, from, to, byEpg.keys, vsWords)
                else Xmltv.read(u, byEpg.keys, from, to) { t -> vsWords.containsMatchIn(t) }
            } catch (e: Exception) { emptyList() } }
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

    /** who usually has the rights for this league (names, for showing; not tied to your list) */
    /** who holds the rights where you live (built-in table, refreshed from GitHub once a day): "BA · Arena Sport" */
    fun rightsTv(ev: SportEvent): List<String> {
        if (!Prefs.bool("sport_src_rights", true)) return emptyList()
        val m = Rights.table()[ev.leagueAbbr] ?: return emptyList()
        val mine = countries.map { if (it == "GB") "UK" else it }
        return (mine.filter { it in m } + m.keys.filter { it !in mine }.take(if (mine.any { it in m }) 0 else 3))
            .flatMap { c -> m[c].orEmpty().map { "$c · $it" } }.distinct()  // i18n-ignore
    }

    fun usualTv(ev: SportEvent): List<String> = (RIGHTS[ev.leagueAbbr] ?: emptyList()).map { w ->
        w.lowercase().split(' ').joinToString(" ") { p -> if (p.length <= 3 && p.none { it.isDigit() }) p.uppercase() else p.replaceFirstChar { it.uppercase() } } }.distinct()

    /** live and coming matches of the next [days] days for your favourite countries (national teams and their leagues) and teams */
    suspend fun favorites(days: Int, codes: Set<String>, onPart: ((List<SportEvent>) -> Unit)? = null): List<SportEvent> = withContext(Dispatchers.IO) {
        val nat = setOf("WC", "EURO", "UNL", "WCQ", "FRI")
        val lgs = (activeLeagues() + LEAGUES.filter { it.abbr in nat || countryOf(it.path) in codes }).distinctBy { it.path }
        val range = espnDate(dayStart(0)) + "-" + espnDate(dayStart(days))
        boards(lgs, listOf(range), onPart) { it.state != "post" }
    }

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
    /** one or more countries ("BA,DE"); empty = the box's country */
    val countries: List<String> get() = Prefs.str("sport_country").split(',').map { it.trim().uppercase() }.filter { it.isNotBlank() }
        .ifEmpty { listOf(Locale.getDefault().country.uppercase().ifBlank { "DE" }) }
    val country: String get() = countries.first()

    /** what matters most where you live; the biggest tournaments are always on top */
    private fun priorityList(): List<String> {
        val top = listOf("WC", "EURO", "UCL")
        // several countries: their lists are merged in turn, so each one's leagues come up near the top
        val lists = countries.map { restFor(it) }
        val rest = LinkedHashSet<String>()
        (0 until (lists.maxOfOrNull { it.size } ?: 0)).forEach { i -> lists.forEach { l -> l.getOrNull(i)?.let { rest.add(it) } } }
        return top + rest + listOf("ERE", "POR", "TSL", "MLS", "BL2", "TV", "FRI")
    }
    private fun restFor(country: String): List<String> {
        return when (country) {
            "DE", "AT", "CH" -> listOf("BL", "DFB", "UEL", "PL", "LL", "SA", "UNL", "WCQ", "BL2", "AUT", "F1", "UECL", "L1", "NBA", "ATP", "WTA")
            "GB", "UK", "IE" -> listOf("PL", "UEL", "LL", "SA", "BL", "UNL", "WCQ", "F1", "UECL", "L1", "NBA", "ATP", "WTA")
            "US", "CA" -> listOf("NBA", "MLS", "PL", "F1", "LL", "BL", "SA", "ATP", "WTA", "UEL")
            "ES" -> listOf("LL", "UEL", "PL", "SA", "BL", "F1", "NBA", "ATP", "WTA")
            "IT" -> listOf("SA", "UEL", "PL", "LL", "BL", "F1", "NBA", "ATP", "WTA")
            "FR" -> listOf("L1", "UEL", "PL", "LL", "SA", "BL", "F1", "NBA", "ATP", "WTA")
            "TR" -> listOf("TSL", "UEL", "PL", "LL", "SA", "BL", "NBA", "ATP", "WTA")
            else -> listOf("PL", "BL", "LL", "SA", "UEL", "UNL", "WCQ", "F1", "NBA", "UECL", "L1", "ATP", "WTA", "AUT")   // Balkan and the rest of Europe
        }
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

    internal fun matchesTeams(home: Set<String>, away: Set<String>, title: Set<String>) =
        teamIn(home - away, title) && teamIn(away - home, title)

    /** not a match at all: previews, magazines, studio shows */
    private val NOT_MATCH = Regex("highlights|sažetak|sazetak|najava|magazin|studio|emisija", RegexOption.IGNORE_CASE)

    /** Adds your channels to each match and returns extra matches found only in your guide. */
    /** Last result per day, shown at once when you come back to the Sports Center. */
    val lastCombined = java.util.concurrent.ConcurrentHashMap<Int, List<SportEvent>>()

    /** Heavy: always call off the main thread. */
    fun combine(events: List<SportEvent>, g: Guide, offset: Int): List<SportEvent> {
        val progs = g.progs; val chans = g.byEpg
        val from = dayStart(offset); val to = dayStart(offset + 1)
        val day = progs.filterNot { NOT_MATCH.containsMatchIn(it.title) }.sortedBy { it.start }
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
                val hits = near(ev.start, 90 * 60_000L).filterNot { REPLAY.containsMatchIn(it.title) }.filter {
                    nt(it).let { t -> t.contains("formula") || t.contains("grand prix") || t.contains(" f1") || t.startsWith("f1") } }
                used.addAll(hits)
                return@map ev.copy(ai = hits.flatMap { chans[it.channel] ?: emptyList() }.distinctBy { it.url })
            }
            val th = tokens(ev.home.name); val ta = tokens(ev.away.name)
            val window = near(ev.start, 75 * 60_000L).filterNot { REPLAY.containsMatchIn(it.title) }
            val hits = window.filter { pr -> matchesTeams(th, ta, wd(pr)) }
            val probable = window.filter { pr ->
                val words = wd(pr)
                pr !in hits && norm(ev.league).takeIf { it.length >= 4 }?.let { it in nt(pr) } == true &&
                    (teamIn(th - ta, words) || teamIn(ta - th, words))
            }
            used.addAll(hits)
            ev.copy(channels = hits.flatMap { chans[it.channel].orEmpty() }.distinctBy { it.url },
                ai = probable.flatMap { chans[it.channel].orEmpty() }.distinctBy { it.url })
        }
        // teams the score feed knows (today and the days already loaded): their match not in today's feed = a recording
        val known = (withCh + lastCombined.values.flatten()).flatMap { listOfNotNull(it.home, it.away) }.map { tokens(it.name).filter { w -> w !in GENERIC }.toSet() }.filter { it.isNotEmpty() }
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
                    replay = isReplay(list.map { it.first }, ab, known),
                )
            }
        return (withCh.map { it.copy(maybe = if (it.channels.isEmpty()) maybeFor(it, upperAll) else emptyList()) } + extra)
            .map { it.copy(channels = it.channels.sortedBy { c -> regionRank(c.name) }) }
            .sortedBy { it.start }
    }

    /** A guide match that is not live: the guide says so, the feed knows the team but has no such match now, or a weekday morning. */
    private fun isReplay(progs: List<Prog>, ab: Pair<String, String>, known: List<Set<String>>): Boolean {
        if (progs.any { REPLAY.containsMatchIn(it.title + " " + (it.category ?: "") + " " + (it.desc ?: "")) }) return true
        val a = tokens(ab.first).filter { it !in GENERIC }; val b = tokens(ab.second).filter { it !in GENERIC }
        if (known.any { k -> a.any { it in k } } || known.any { k -> b.any { it in k } }) return true
        val c = java.util.Calendar.getInstance().apply { timeInMillis = progs.first().start }
        val wd = c.get(java.util.Calendar.DAY_OF_WEEK) in java.util.Calendar.MONDAY..java.util.Calendar.FRIDAY
        return wd && c.get(java.util.Calendar.HOUR_OF_DAY) < 14
    }
    /** a recording or a replay, not live */
    private val REPLAY = Regex("snimak|snimka|repriz|reprise|replay|wiederholung|whlg|rewind|classic|klasik|\\(r\\)|\\(p\\)", RegexOption.IGNORE_CASE)  // i18n-ignore
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
    /** "20261116183000 +0100" read by hand: a big guide has hundreds of thousands of times, and a date parser for each one
     *  made the box crawl (search and menus froze while the guide was being read) */
    private fun fastTime(t: String): Long? {
        if (t.length < 14) return null
        fun num(a: Int, b: Int): Int { var v = 0; for (k in a until b) { val d = t[k] - '0'; if (d < 0 || d > 9) return -1; v = v * 10 + d }; return v }
        val y = num(0, 4); val mo = num(4, 6); val d = num(6, 8); val h = num(8, 10); val mi = num(10, 12); val sec = num(12, 14)
        if (y < 1970 || mo !in 1..12 || d !in 1..31 || h !in 0..23 || mi !in 0..59 || sec !in 0..60) return null
        val yy = if (mo <= 2) y - 1 else y
        val era = yy / 400; val yoe = yy - era * 400; val mp = (mo + 9) % 12
        val doy = (153 * mp + 2) / 5 + d - 1; val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        val days = era * 146097L + doe - 719468L
        var ms = (((days * 24 + h) * 60 + mi) * 60 + sec) * 1000L
        val z = t.substring(14).trim()
        if (z.isEmpty()) return ms
        if (z.length != 5 || (z[0] != '+' && z[0] != '-')) return null
        val zh = (z[1] - '0') * 10 + (z[2] - '0'); val zm = (z[3] - '0') * 10 + (z[4] - '0')
        if (zh !in 0..14 || zm !in 0..59) return null
        val off = (zh * 60 + zm) * 60_000L
        ms += if (z[0] == '+') -off else off
        return ms
    }

    private fun time(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        val t = s.trim()
        fastTime(t)?.let { return it }
        for (p in listOf("yyyyMMddHHmmss Z", "yyyyMMddHHmmss")) {
            try { return SimpleDateFormat(p, Locale.US).apply { if (!p.contains('Z')) timeZone = TimeZone.getTimeZone("UTC") }.parse(t)?.time } catch (_: Exception) {}
        }
        return null
    }

    /** Streams the guide and keeps only programmes of [channels] (lower-case ids) between [from] and [to]. */
    fun read(url: String, channels: Set<String>?, from: Long, to: Long, keepTitle: ((String) -> Boolean)? = null): List<Sports.Prog> {
        val out = ArrayList<Sports.Prog>()
        each(url, channels, from, to, channels == null, keepTitle = keepTitle) { out.add(it) }
        return out
    }

    /**
     * Same, one programme at a time (for the guide kept on the box). [bytes] gets (read, size or -1) now and then.
     * Returns false when the guide could not be opened at all.
     */
    fun each(url: String, channels: Set<String>?, from: Long, to: Long, withDesc: Boolean, bytes: ((Long, Long) -> Unit)? = null,
             keepTitle: ((String) -> Boolean)? = null, out: (Sports.Prog) -> Unit): Boolean {
        if (channels != null && channels.isEmpty()) return true
        // a guide file copied in from USB or disk ("file://…") or a link
        val local = url.startsWith("file://")
        val c = if (local) null else (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000; readTimeout = 120000; setRequestProperty("User-Agent", Net.UA)
        }
        try {
            if (c != null && c.responseCode !in 200..299) return false
            val total = c?.contentLengthLong ?: java.io.File(url.removePrefix("file://")).length()
            val raw0 = if (c != null) c.inputStream else java.io.FileInputStream(url.removePrefix("file://"))
            val raw = if (bytes == null) raw0 else object : java.io.FilterInputStream(raw0) {
                var n = 0L; var last = 0L
                private fun add(k: Int) { if (k > 0) { n += k; if (n - last > 256_000) { last = n; bytes(n, total) } } }
                override fun read(): Int = super.read().also { if (it >= 0) add(1) }
                override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { add(it) }
            }
            var input: java.io.InputStream = java.io.BufferedInputStream(raw, 64 * 1024)
            input.mark(4)
            val b0 = input.read(); val b1 = input.read(); input.reset()
            if (b0 == 0x1f && b1 == 0x8b) input = java.util.zip.GZIPInputStream(input, 64 * 1024)
            val xp = android.util.Xml.newPullParser()
            xp.setInput(input, null)
            var ch: String? = null; var start = 0L; var end = 0L; var title: String? = null; var cat: String? = null; var desc: String? = null
            var keep = false; var tag: String? = null
            var ev = xp.eventType
            try {
                while (ev != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    when (ev) {
                        org.xmlpull.v1.XmlPullParser.START_TAG -> {
                            tag = xp.name
                            if (tag == "programme") {
                                ch = xp.getAttributeValue(null, "channel")?.lowercase()
                                // channels nobody asked for are skipped before their times are even read
                                keep = ch != null && (channels == null || ch in channels)
                                if (keep) {
                                    start = time(xp.getAttributeValue(null, "start")) ?: 0L
                                    end = time(xp.getAttributeValue(null, "stop")) ?: start
                                    keep = end > from && start < to
                                }
                                title = null; cat = null; desc = null
                            }
                        }
                        org.xmlpull.v1.XmlPullParser.TEXT -> if (keep) {
                            if (tag == "title" && title == null) title = xp.text?.trim()
                            else if (tag == "category" && cat == null) cat = xp.text?.trim()
                            else if (tag == "desc" && desc == null && withDesc) desc = xp.text?.trim()
                        }
                        org.xmlpull.v1.XmlPullParser.END_TAG -> {
                            if (xp.name == "programme") {
                                if (keep && !title.isNullOrBlank() && (keepTitle == null || keepTitle(title!!))) out(Sports.Prog(ch!!, start, end, title!!, cat, desc))
                                keep = false
                            }
                            tag = null
                        }
                    }
                    ev = xp.next()
                }
            } catch (e: org.xmlpull.v1.XmlPullParserException) {
                // a broken or cut guide still gives what was read so far
            } catch (e: java.io.EOFException) {}
            return true
        } catch (e: Exception) {
            if (bytes != null) throw e
            return false
        } finally { c?.disconnect() }
    }
}

/** Your own TV guide (XMLTV link from Settings), used when the provider sends no programme for a channel. */
object OwnGuide {
    private class Cache(val at: Long, val data: Map<String, List<Sports.Prog>>)
    private val caches = HashMap<String, Cache>()
    private val lock = kotlinx.coroutines.sync.Mutex()
    fun drop(url: String) { caches.remove(url) }

    private suspend fun data(url: String, everyH: Int = 0): Map<String, List<Sports.Prog>> {
        lock.lock()
        try {
            val c = caches[url]
            val maxAge = (if (everyH > 0) everyH else 6) * 3600_000L
            if (c != null && System.currentTimeMillis() - c.at < maxAge) return c.data
            val now = System.currentTimeMillis()
            val d = try { Xmltv.read(url, null, now - 3 * 3600_000L, now + (if (Lean.epg) 12 else 36) * 3600_000L).groupBy { it.channel } } catch (e: Exception) { emptyMap() }
            caches[url] = Cache(now, d)
            return d
        } finally { lock.unlock() }
    }

    /** guide of a channel from your own sources (the playlist's own first) */
    suspend fun of(epgId: String?, name: String, pid: String? = null): List<EpgItem> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        for (src in EpgSources.srcs(pid)) {
            val sh = src.shift * 3600_000L
            // the guide kept on the box first
            val dbSrc = GuideDb.srcOf(src.url)
            if (GuideDb.has(dbSrc)) {
                val l = GuideDb.items(dbSrc, epgId, 40).ifEmpty { GuideDb.items(dbSrc, Sports.norm(name).replace(" ", ""), 40) }
                if (l.isNotEmpty()) return@withContext l.map { it.copy(start = it.start + sh, end = it.end + sh) }
                continue
            }
            val d = data(src.url, src.every)
            val list = epgId?.lowercase()?.ifBlank { null }?.let { d[it] } ?: d[Sports.norm(name).replace(" ", "")]
            if (!list.isNullOrEmpty()) return@withContext list.filter { it.end + sh > now - 60_000 }.sortedBy { it.start }.take(40)
                .map { EpgItem(it.title, it.desc, it.start + sh, it.end + sh) }
        }
        emptyList()
    }
}


/** The country of a league from its ESPN path ("ger.1" → de, "uefa.champions" → eu), for the flag and the country filter. */
fun countryOf(path: String): String {
    val p = path.substringAfter('/').lowercase()
    return when {
        p.startsWith("uefa") || p.startsWith("fifa") -> "eu"; p.startsWith("eng") -> "gb"; p.startsWith("ger") -> "de"; p.startsWith("esp") -> "es"
        p.startsWith("ita") -> "it"; p.startsWith("fra") -> "fr"; p.startsWith("ned") -> "nl"; p.startsWith("por") -> "pt"; p.startsWith("tur") -> "tr"
        p.startsWith("aut") -> "at"; p.startsWith("sui") -> "ch"; p.startsWith("bih") -> "ba"; p.startsWith("cro") -> "hr"; p.startsWith("srb") -> "rs"
        p.startsWith("usa") || path.startsWith("basketball/nba") || path.startsWith("football/nfl") -> "us"
        else -> "int"
    }
}

/** Rights per league per country. A JSON file on GitHub (rights.json), so it can be fixed without a new app version;
 *  read at most once a day, the last good copy is kept, the built-in table is the fallback. */
object Rights {
    private const val URL = "https://raw.githubusercontent.com/KarlAgathon/PRETORIAN/beta/rights.json"  // i18n-ignore
    private const val BUILTIN = """{"UCL":{"BA":["Arena Sport"],"HR":["Arena Sport"],"RS":["Arena Sport"],"DE":["DAZN","Prime Video"],"AT":["Canal+","ServusTV"],"CH":["blue Sport","SRF"],"UK":["TNT Sports","Prime Video"],"US":["Paramount+","CBS"]},"UEL":{"BA":["Arena Sport"],"HR":["Arena Sport"],"RS":["Arena Sport"],"DE":["RTL+","Nitro"],"AT":["Canal+"],"CH":["blue Sport"],"UK":["TNT Sports"],"US":["Paramount+"]},"UECL":{"BA":["Arena Sport"],"HR":["Arena Sport"],"RS":["Arena Sport"],"DE":["RTL+"],"AT":["Canal+"],"CH":["blue Sport"],"UK":["TNT Sports"],"US":["Paramount+"]},"PL":{"BA":["Arena Sport"],"HR":["Arena Sport"],"RS":["Arena Sport"],"DE":["Sky Sport"],"AT":["Sky Sport Austria"],"CH":["blue Sport"],"UK":["Sky Sports","TNT Sports"],"US":["NBC","Peacock","USA Network"]},"BL":{"BA":["Arena Sport"],"HR":["Arena Sport"],"RS":["Arena Sport"],"DE":["Sky Sport","DAZN","Sat.1"],"AT":["Sky Sport Austria"],"CH":["blue Sport"],"UK":["Sky Sports"],"US":["ESPN+"]},"LL":{"BA":["Arena Sport"],"HR":["Arena Sport"],"RS":["Arena Sport"],"DE":["DAZN"],"AT":["DAZN"],"CH":["blue Sport"],"UK":["Premier Sports"],"US":["ESPN+"]},"SA":{"BA":["Arena Sport"],"HR":["Arena Sport"],"RS":["Arena Sport"],"DE":["DAZN"],"AT":["DAZN"],"CH":["blue Sport"],"UK":["TNT Sports"],"US":["CBS","Paramount+"]},"L1":{"BA":["Arena Sport"],"HR":["Arena Sport"],"RS":["Arena Sport"],"DE":["DAZN"],"AT":["DAZN"],"UK":["Ligue 1+"],"US":["beIN Sports"]}}"""  // i18n-ignore
    @Volatile private var cache: Map<String, Map<String, List<String>>>? = null
    private fun parse(t: String): Map<String, Map<String, List<String>>> {
        val o = org.json.JSONObject(t); val out = HashMap<String, Map<String, List<String>>>()
        o.keys().forEach { lg -> val c = o.optJSONObject(lg) ?: return@forEach
            out[lg] = c.keys().asSequence().associateWith { k -> c.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList() } }
        return out
    }
    fun table(): Map<String, Map<String, List<String>>> = cache ?: (try { parse(Prefs.str("rights_json").ifBlank { BUILTIN }) } catch (e: Exception) { parse(BUILTIN) }).also { cache = it }
    suspend fun refresh() = withContext(Dispatchers.IO) {
        if (System.currentTimeMillis() - Prefs.long("rights_at") < 24 * 3600_000L) return@withContext
        try { val t = Net.get(URL); parse(t); Prefs.put("rights_json", t); Prefs.put("rights_at", System.currentTimeMillis()); cache = null } catch (_: Exception) {}
    }
}


/** Optional: API-Football (api-sports.io). With the user's own free key one request brings every live football match;
 *  asked at most every 15 minutes (the free key allows 100 a day). Nothing happens without a key. */
object ApiFootball {
    var key: String get() = Prefs.str("apifb_key"); set(v) = Prefs.put("apifb_key", v.trim())
    val on get() = key.isNotBlank()
    @Volatile private var cache: List<SportEvent> = emptyList()
    @Volatile private var at = 0L
    suspend fun live(): List<SportEvent> = withContext(Dispatchers.IO) {
        if (!on) return@withContext emptyList()
        if (System.currentTimeMillis() - at < 15 * 60_000L) return@withContext cache
        at = System.currentTimeMillis()
        try {
            val c = java.net.URL("https://v3.football.api-sports.io/fixtures?live=all").openConnection() as java.net.HttpURLConnection  // i18n-ignore
            c.connectTimeout = 8000; c.readTimeout = 8000; c.setRequestProperty("x-apisports-key", key)
            val t = try { c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }
            val arr = JSONObject(t).optJSONArray("response") ?: return@withContext cache
            fun team(o: JSONObject?, goals: Any?) = o?.let { Team(it.str("name"), it.str("name").take(3).uppercase(), it.str("logo").ifBlank { null }, null, null, goals?.toString()?.takeIf { g -> g != "null" }) }
            cache = (0 until arr.length()).mapNotNull { i ->
                val r = arr.optJSONObject(i) ?: return@mapNotNull null
                val f = r.optJSONObject("fixture") ?: return@mapNotNull null
                val lg = r.optJSONObject("league"); val tm = r.optJSONObject("teams"); val g = r.optJSONObject("goals")
                val el = f.optJSONObject("status")?.optInt("elapsed", 0) ?: 0
                SportEvent("af" + f.optLong("id"), "football", lg?.str("name") ?: "", "AF", lg?.str("round"), f.optLong("timestamp") * 1000, "in",
                    if (el > 0) "$el'" else null, team(tm?.optJSONObject("home"), g?.opt("home")), team(tm?.optJSONObject("away"), g?.opt("away")),
                    "", null, observedAt = at, country = (lg?.str("country") ?: "").let { cn -> Locale.getAvailableLocales().firstOrNull { it.getDisplayCountry(Locale.ENGLISH).equals(cn, true) }?.country?.lowercase() ?: "int" })  // i18n-ignore
            }
        } catch (_: Exception) {}
        cache
    }
    /** live matches ESPN does not have (same two teams = the same match) */
    fun extra(espn: List<SportEvent>, af: List<SportEvent>): List<SportEvent> {
        fun same(a: SportEvent, b: SportEvent): Boolean {
            if (a.sport != b.sport || kotlin.math.abs(a.start - b.start) > 30 * 60_000L) return false
            val teams = listOf(a.home, a.away, b.home, b.away)
            if (teams.any { it == null }) return false
            return Sports.norm(a.home!!.name) == Sports.norm(b.home!!.name) &&
                Sports.norm(a.away!!.name) == Sports.norm(b.away!!.name)
        }
        return af.distinctBy { it.id }.filter { candidate -> espn.none { same(it, candidate) } }
    }
}
