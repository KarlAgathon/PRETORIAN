package com.pretorian.tv

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.key.Key
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal fun sportsRowIndex(events: List<SportEvent>, id: String, banner: Boolean, group: (SportEvent) -> String): Int? {
    var index = if (banner) 1 else 0
    var previous: String? = null
    for (event in events) {
        val heading = group(event)
        if (heading != previous) { index++; previous = heading }
        if (event.id == id) return index
        index++
    }
    return null
}

internal fun sportsFilter(event: SportEvent, teams: Set<String>, countries: Set<String>, leagues: Set<String>) =
    (teams.isEmpty() || event.home?.name in teams || event.away?.name in teams) &&
    (countries.isEmpty() || event.country.lowercase() in countries) &&
    (leagues.isEmpty() || event.league in leagues)

internal const val CACHED_SCORE = "cached"

/** Your teams (long OK on a match adds them): shown on top of Sport, and a goal of theirs pops up anywhere in the app. */
object MyTeams {
    var rev by mutableIntStateOf(0)
    /** the same list as Settings › Sport › Favourite teams */
    private fun teams(): List<String> = Prefs.str("sport_teams").split(',').map { it.trim() }.filter { it.isNotBlank() }
    /** national teams by the country codes ticked in Settings › Sport › My national teams (names as ESPN writes them) */
    val NATIONS = mapOf("BA" to listOf("Bosnia and Herzegovina", "Bosnia-Herzegovina"), "DE" to listOf("Germany"), "AT" to listOf("Austria"),  // i18n-ignore
        "CH" to listOf("Switzerland"), "RS" to listOf("Serbia"), "HR" to listOf("Croatia"), "ME" to listOf("Montenegro"), "SI" to listOf("Slovenia"),  // i18n-ignore
        "MK" to listOf("North Macedonia"), "GB" to listOf("England", "Scotland", "Wales"), "IT" to listOf("Italy"), "ES" to listOf("Spain"),  // i18n-ignore
        "FR" to listOf("France"), "TR" to listOf("Türkiye", "Turkey"), "US" to listOf("United States", "USA"))  // i18n-ignore
    private fun nations(): List<String> = Prefs.str("sport_nations").split(',').filter { it.isNotBlank() }.flatMap { NATIONS[it] ?: emptyList() }
    /** on (default): your teams and national teams go first in Sport and are followed for notes by themselves */
    val auto get() = Prefs.bool("sa_auto", true)
    fun list(): List<String> = if (!auto) emptyList() else teams() + nations()
    fun has(t: String) = (teams() + nations()).any { it.equals(t, true) }
    fun toggle(t: String) {
        val l = teams().toMutableList(); if (l.removeAll { it.equals(t, true) }.not()) { l.add(t); HomeHide.clear() }
        Prefs.put("sport_teams", l.joinToString(",")); rev++
    }
}

/** Single matches you follow (long OK › Follow match); dropped by themselves when the match ends. */
object MatchFollow {
    var rev by mutableIntStateOf(0)
    fun list(): Set<String> = Prefs.str("follow_match").split(',').filter { it.isNotBlank() }.toSet()
    fun has(id: String) = id in list()
    fun toggle(id: String): Boolean { val l = list().toMutableSet(); val on = l.add(id); if (!on) l.remove(id); Prefs.put("follow_match", l.joinToString(",")); rev++; if (on) HomeHide.show(id); return on }
    fun drop(id: String) { val l = list(); if (id in l) { Prefs.put("follow_match", (l - id).joinToString(",")); rev++ } }
}

/**
 * Notes for your teams and followed matches: plays today, goal, red card, extra time, penalties, end.
 * They land on Home (top right) and in the bell; a live one also shows for 8 s in the corner, wherever you are.
 */
object GoalNote {
    var text by mutableStateOf<String?>(null)
    /** a followed match is live: check every minute instead of every 3 */
    var hot = false; private set
    private val last = HashMap<String, String>()
    private val sent: MutableSet<String> by lazy { Prefs.str("sa_sent").split(',').filter { it.isNotBlank() }.toMutableSet() }
    val KINDS = listOf("start" to "Početak utakmice", "today" to "Danas igra tvoj tim", "goal" to "Gol", "red" to "Crveni karton", "et" to "Produžeci", "pen" to "Penali", "end" to "Kraj utakmice")
    fun on(k: String) = Prefs.bool("sa_$k", true)
    private fun minuteOf(c: String?) = c?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
    private fun phase(e: SportEvent): String {
        val c = (e.clock ?: "").lowercase()
        return when {
            e.state == "post" -> "end"
            e.stage == "pen" -> "pen"
            e.stage == "et" -> "et"
            c.contains("pen") || c.contains("shootout") -> "pen"
            c.contains("et") || c.contains("extra") || (e.sport == "football" && minuteOf(c) > 90 && !c.contains('+')) -> "et"
            else -> e.state
        }
    }
    private val lastEvent = HashMap<String, Long>()
    var lastRev by mutableIntStateOf(0)
    fun lastEventAt(id: String) = lastEvent[id] ?: 0L
    private fun send(kind: String, e: SportEvent, txt: String, id: String) {
        if (kind != "today") { lastEvent[e.id] = System.currentTimeMillis(); lastRev++ }
        if (!on(kind) || !sent.add(id)) return
        Prefs.put("sa_sent", sent.toList().takeLast(300).joinToString(","))
        Notes.add("sport", txt, id)
        if (kind != "today") text = txt
    }
    /** followed matches of today (live first): the "Following" card on Home */
    val followed = androidx.compose.runtime.mutableStateListOf<SportEvent>()
    /** when a followed match was first seen finished: greyed in the bell, gone from Home 15 min later */
    private val endedAt = HashMap<String, Long>()
    fun endedLongAgo(id: String) = endedAt[id]?.let { System.currentTimeMillis() - it > 15 * 60_000L } ?: false
    fun ended(id: String) = endedAt.containsKey(id)
    fun idOf(noteId: String) = noteId.split(':').getOrNull(1) ?: ""
    fun check(evs: List<SportEvent>) {
        // spec 16: followed matches give only the small overlay (FollowWatch), never a bell note
        FollowWatch.check(evs)
        val mine = MyTeams.list(); val ms = MatchFollow.list()
        if (mine.isEmpty() && ms.isEmpty()) { hot = false; followed.clear(); return }
        val dayNow = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
        val f = evs.filter { e -> (e.id in ms || mine.any { t -> t.equals(e.home?.name, true) || t.equals(e.away?.name, true) }) &&
            (e.live || java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date(e.start)) == dayNow) }
            .sortedWith(compareBy({ it.state != "in" }, { it.state == "post" }, { it.start }))
        // keep matches the list did not bring this time (another sport tab), refresh the rest
        val merged = (f + followed.filter { o -> f.none { it.id == o.id } && (o.id in ms || mine.any { t -> t.equals(o.home?.name, true) || t.equals(o.away?.name, true) }) })
            .sortedWith(compareBy({ it.state != "in" }, { it.state == "post" }, { it.start })).take(6)
        merged.filter { it.state == "post" }.forEach { endedAt.putIfAbsent(it.id, System.currentTimeMillis()) }
        // finished (or stale "live" from hours ago): no longer followed and gone from Home at once; the "end" note is still sent below
        evs.filter { it.id in ms && it.over }.forEach { MatchFollow.drop(it.id) }
        followed.clear(); followed.addAll(merged.filter { !it.over })
        val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
        var anyLive = false
        evs.forEach { e ->
            val h = e.home ?: return@forEach; val a = e.away ?: return@forEach
            val team = mine.any { it.equals(h.name, true) || it.equals(a.name, true) }
            if (!team && e.id !in ms) return@forEach
            if (e.live) anyLive = true
            if (!team) return@forEach
            val name = h.name + " – " + a.name
            val sc = (h.score ?: "–") + ":" + (a.score ?: "–")
            val board = h.name + " " + sc + " " + a.name
            if (team && e.state == "pre" && java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date(e.start)) == today)
                send("today", e, "🕘 " + tr("Danas igra tvoj tim") + " · " + name + " · " +
                    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(e.start)), "sp:${e.id}:today")  // i18n-ignore
            // favourite teams only get the "plays today" note; in-match events belong to Match Follow (spec 16)
            if (true) return@forEach
            val reds = e.details.count { it.kind.startsWith("red") }
            val ph = phase(e)
            val penalties = e.details.count { it.kind.contains("_pen") || it.kind == "penalty" }
            val now = sc + "|" + reds + "|" + ph + "|" + penalties
            val old = last.put(e.id, now) ?: return@forEach
            val (oSc, oRed, oPh) = old.split('|').let { Triple(it[0], it.getOrNull(1)?.toIntOrNull() ?: 0, it.getOrNull(2) ?: "") }
            val oldTotal = oSc.split(':').mapNotNull { it.toIntOrNull() }
            val newTotal = sc.split(':').mapNotNull { it.toIntOrNull() }
            if (penalties > (old.substringAfterLast('|').toIntOrNull() ?: 0))
                send("pen", e, tr("Penali") + " · " + board, "sp:${e.id}:pen:$penalties") // i18n-ignore
            if (e.sport == "football" && oldTotal.size == 2 && newTotal.size == 2 && newTotal.sum() > oldTotal.sum() && ph != "end") {
                val g = e.details.lastOrNull { it.kind.startsWith("goal") }
                send("goal", e, "⚽ " + tr("GOL") + " · " + board + (g?.let { " · " + it.minute + " " + it.player } ?: ""), "sp:${e.id}:g:$sc")  // i18n-ignore
            }
            if (reds > oRed) {
                val r = e.details.lastOrNull { it.kind.startsWith("red") }
                send("red", e, "🟥 " + tr("CRVENI KARTON") + " · " + name + (r?.let { " · " + it.minute + " " + it.player } ?: "") + " · " + sc, "sp:${e.id}:r:$reds")  // i18n-ignore
            }
            if (oPh == "pre" && e.live) send("start", e, tr("Početak utakmice") + " · " + name, "sp:${e.id}:start") // i18n-ignore
            if (ph != oPh) when (ph) {
                "et" -> send("et", e, "⏱ " + tr("PRODUŽECI") + " · " + board, "sp:${e.id}:et")  // i18n-ignore
                "pen" -> send("pen", e, "🎯 " + tr("PENALI") + " · " + board, "sp:${e.id}:pen")  // i18n-ignore
                "end" -> { send("end", e, "🏁 " + tr("KRAJ") + " · " + board, "sp:${e.id}:end"); MatchFollow.drop(e.id) }  // i18n-ignore
            }
        }
        hot = anyLive
    }
    /** OK on a sport note: straight to the channel when one is known, otherwise Sport with that match open */
    fun open(nav: Nav, noteId: String) {
        val parts = noteId.split(':'); val id = parts.getOrNull(1) ?: return
        val ev = Sports.lastCombined.values.flatten().firstOrNull { it.id == id }
        val ch = ev?.let { (it.channels + it.ai + it.maybe).firstOrNull() }
        if (ch != null && ev.state == "in") { playMatch(nav, ev, ch); return }
        SportsMemory.evId = id
        SportsMemory.view = when (parts.getOrNull(2)) { "today" -> "soon"; "end" -> "done"; else -> "live" }
        nav.root(Screen.Sports)
    }
}

/** A match full screen: the other channels of yours that show it are kept, so a stuck picture switches to one of them. */
fun playMatch(nav: Nav, e: SportEvent, ch: Channel) {
    val all = e.channels.distinctBy { it.url }
    MatchWatch.set(e.title, listOf(ch) + all.filter { it.url != ch.url })
    playChannels(nav, listOf(ch), 0)
}

fun flagEmoji(c: String): String = when (c) {
    "eu" -> "🇪🇺"; "int", "" -> "🌍"
    else -> if (c.length == 2) String(Character.toChars(0x1F1E6 + (c.uppercase()[0] - 'A'))) + String(Character.toChars(0x1F1E6 + (c.uppercase()[1] - 'A'))) else "🌍"
}
private fun countryName(c: String) = when (c) {
    "eu" -> tr("Evropa"); "int" -> tr("Međunarodno"); else -> try { java.util.Locale("", c.uppercase()).getDisplayCountry(java.util.Locale(Look.lang)) } catch (e: Exception) { c.uppercase() }
}


/** Matches hidden from Home with a long OK (until you follow them again). */
object HomeHide {
    fun list(): Set<String> = Prefs.str("home_hide").split(',').filter { it.isNotBlank() }.toSet()
    fun hide(id: String) { Prefs.put("home_hide", (list() + id).toList().takeLast(200).joinToString(",")); GoalNote.followed.removeAll { it.id == id } }
    fun show(id: String) { val l = list(); if (id in l) Prefs.put("home_hide", (l - id).joinToString(",")) }
    fun clear() = Prefs.put("home_hide", "")
}

/** Sport data kept between visits; refreshed only when stale (scores) or when the filters change. */
object SportCache {
    var dataRev by mutableIntStateOf(0); private set
    var rev by mutableIntStateOf(0)
    var guideRev by mutableIntStateOf(0)
    var today: List<SportEvent>? = null; var todayAt = 0L
    val other = HashMap<String, List<SportEvent>>()
    val otherAt = HashMap<String, Long>()
    var combined: List<SportEvent> = emptyList()
    var guide: Sports.Guide? = null
    fun clear() { today = null; other.clear(); otherAt.clear(); combined = emptyList(); dataRev++; rev++ }
}


/** "Tuesday, 17 November" (today / tomorrow named) */
fun whenLong(ms: Long): String {
    val loc = java.util.Locale(Look.lang)
    val d = java.text.SimpleDateFormat("EEEE, d. MMMM", loc).format(java.util.Date(ms))  // i18n-ignore
    return when (ms) { in Sports.dayStart(0) until Sports.dayStart(1) -> tr("Danas") + " · " + d; in Sports.dayStart(1) until Sports.dayStart(2) -> tr("Sutra") + " · " + d; else -> d }  // i18n-ignore
}

