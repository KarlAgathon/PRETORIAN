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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

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

/** Favourite teams: the list with ✕ to remove, and "＋ Add team" (search among known teams or type any name) — as many as you like. */
@Composable
fun FavTeamsDialog(onDismiss: () -> Unit) {
    var rev by remember { mutableIntStateOf(0) }
    var add by remember { mutableStateOf(false) }
    val mine = remember(rev, MyTeams.rev) { Prefs.str("sport_teams").split(',').map { it.trim() }.filter { it.isNotBlank() } }
    if (add) {
        var q by remember { mutableStateOf("") }
        val known = remember { (SportCache.today.orEmpty() + SportCache.other.values.flatten() + SportCache.combined)
            .flatMap { listOfNotNull(it.home?.name, it.away?.name) }.distinct().sorted() }
        Popup(tr("Dodaj tim"), { add = false }, width = 640.dp) {
            Field(tr("Traži ili upiši ime tima"), q, { q = it }, Modifier.fillMaxWidth())
            if (q.trim().length >= 2) {
                OptionRow(trf("Dodaj „%s“", q.trim()), R.drawable.lc_plus, color = C.Green) {
                    if (!MyTeams.has(q.trim())) MyTeams.toggle(q.trim()); q = ""; rev++; Sports.lastCombined.clear()
                }
                known.filter { it.contains(q.trim(), true) && !MyTeams.has(it) }.take(12).forEach { t ->
                    OptionRow(t, R.drawable.lc_plus) { MyTeams.toggle(t); q = ""; rev++; Sports.lastCombined.clear() }
                }
            } else T("Ukucaj bar 2 slova imena tima", 13.sp, C.Muted)
            Button("Gotovo", R.drawable.lc_check, primary = true) { add = false }
        }
        return
    }
    Popup(trf("Omiljeni timovi · %s", mine.size), onDismiss, width = 600.dp) {
        if (mine.isEmpty()) T("Još nema timova", 13.sp, C.Muted)
        mine.forEach { t -> OptionRow(t, R.drawable.lc_x, sub = tr("OK: ukloni")) { MyTeams.toggle(t); rev++; Sports.lastCombined.clear() } }
        OptionRow("＋  " + tr("Dodaj tim"), null, color = C.Green) { add = true }  // i18n-ignore
        Button("Gotovo", R.drawable.lc_check, primary = true) { onDismiss() }
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
    val KINDS = listOf("today" to "Danas igra tvoj tim", "goal" to "Gol", "red" to "Crveni karton", "et" to "Produžeci", "pen" to "Penali", "end" to "Kraj utakmice")
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
            val name = h.name + " – " + a.name
            val sc = (h.score ?: "0") + ":" + (a.score ?: "0")
            val board = h.name + " " + sc + " " + a.name
            if (team && e.state == "pre" && java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date(e.start)) == today)
                send("today", e, "🕘 " + tr("Danas igra tvoj tim") + " · " + name + " · " +
                    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(e.start)), "sp:${e.id}:today")  // i18n-ignore
            val reds = e.details.count { it.kind.startsWith("red") }
            val ph = phase(e)
            val now = sc + "|" + reds + "|" + ph
            val old = last.put(e.id, now) ?: return@forEach
            val (oSc, oRed, oPh) = old.split('|').let { Triple(it[0], it.getOrNull(1)?.toIntOrNull() ?: 0, it.getOrNull(2) ?: "") }
            if (oSc != sc && ph != "end") {
                val g = e.details.lastOrNull { it.kind.startsWith("goal") }
                send("goal", e, "⚽ " + tr("GOL") + " · " + board + (g?.let { " · " + it.minute + " " + it.player } ?: ""), "sp:${e.id}:g:$sc")  // i18n-ignore
            }
            if (reds > oRed) {
                val r = e.details.lastOrNull { it.kind.startsWith("red") }
                send("red", e, "🟥 " + tr("CRVENI KARTON") + " · " + name + (r?.let { " · " + it.minute + " " + it.player } ?: "") + " · " + sc, "sp:${e.id}:r:$reds")  // i18n-ignore
            }
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
        if (ch != null && ev.state == "in") { playChannels(nav, listOf(ch), 0); return }
        SportsMemory.evId = id
        SportsMemory.view = when (parts.getOrNull(2)) { "today" -> "soon"; "end" -> "done"; else -> "live" }
        nav.root(Screen.Sports)
    }
}

/**
 * Sport: matches on the left (your teams on top, Live / Upcoming / Finished, sport and country), the chosen match on the right
 * (small screen only after you pick a channel, score, goals and cards, the channels that show it).
 */
@Composable
fun SportMixScreen(nav: Nav) {
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf(SportsMemory.view.takeIf { it in listOf("live", "soon", "done", "fav", "mine") } ?: "live") }
    var tick by remember { mutableIntStateOf(0) }
    var sel by remember { mutableStateOf(SportsMemory.evId) }
    var playing by remember { mutableStateOf(SportUi.playing) }
    LaunchedEffect(playing) { SportUi.playing = playing }
    val previewFr = remember { FocusRequester() }
    val chipFr = remember { FocusRequester() }
    val firstChipFr = remember { FocusRequester() }
    var jumpTick by remember { mutableIntStateOf(0) }
    var listFocus by remember { mutableStateOf(false) }
    var favRowFocus by remember { mutableStateOf(false) }
    var focusedRow by remember { mutableStateOf<String?>(null) }
    val tabFr = remember { FocusRequester() }
    var menu by remember { mutableStateOf<SportEvent?>(null) }
    var pick by remember { mutableStateOf<String?>(null) }   // "sport" / "country"
    val listFr = remember { FocusRequester() }
    LaunchedEffect(tab) { SportsMemory.view = tab }
    // live scores every minute, only text (no pictures): light for the box
    LaunchedEffect(Unit) { while (true) { delay(60_000); tick++ } }
    // kept between visits: coming back to Sport shows the last list at once, only the scores are refreshed in the background
    var today by remember { mutableStateOf(SportCache.today) }
    var other by remember(tab) { mutableStateOf(SportCache.other[tab]) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(tick, SportCache.rev) {
        if (SportCache.today == null || System.currentTimeMillis() - SportCache.todayAt > 50_000L) {
            loading = SportCache.today == null
            val first = SportCache.today == null
            try {
                val r = Sports.day(0) { part -> if (first) { SportCache.today = part; today = part; loading = false } }
                // with an API-Football key: live football ESPN does not know about is added (one request, at most every 15 min)
                val all = if (ApiFootball.on) r + ApiFootball.extra(r, ApiFootball.live()) else r
                if (all.isNotEmpty() || SportCache.today == null) SportCache.today = all
                SportCache.todayAt = if (all.isEmpty()) 0L else System.currentTimeMillis()
            } catch (_: Exception) {}
            loading = false
            // nothing came (network hiccup): ask again in 10 seconds, not in a minute
            if (SportCache.today.isNullOrEmpty()) { delay(10_000); tick++ }
        }
        today = SportCache.today
    }
    LaunchedEffect(tab, SportCache.rev) {
        // kept 10 min; an empty answer (network hiccup) is asked again next time
        if (tab != "live" && (SportCache.other[tab].isNullOrEmpty() || System.currentTimeMillis() - (SportCache.otherAt[tab] ?: 0L) > 600_000L)) {
            loading = SportCache.other[tab] == null
            try {
                val r = when (tab) {
                    "soon" -> Sports.upcoming(3)
                    "fav", "mine" -> Sports.favorites(10, Prefs.str("sport_country").split(',').filter { it.isNotBlank() }.map { it.lowercase() }.toSet())
                    else -> Sports.day(-1)
                }
                // a refresh that comes back empty never wipes a list you already had
                if (r.isNotEmpty() || SportCache.other[tab] == null) { SportCache.other[tab] = r; SportCache.otherAt[tab] = System.currentTimeMillis() }
            } catch (_: Exception) {}
            loading = false
        }
        other = SportCache.other[tab]
    }
    LaunchedEffect(SportCache.rev) { if (SportCache.guide == null) try { SportCache.guide = Sports.guide(ctx, Store.actives(ctx)) } catch (_: Exception) {}; SportCache.guideRev++ }
    val all = remember(today, other) { ((today ?: emptyList()) + (other ?: emptyList())).distinctBy { it.id } }
    // your channels for each match: matched in the background, the list shows before that
    val withCh by produceState(SportCache.combined.takeIf { it.isNotEmpty() } ?: all, all, SportCache.guideRev) {
        if (value.isEmpty() || value.size != all.size) value = all
        val g = SportCache.guide ?: return@produceState
        value = withContext(Dispatchers.Default) { try { Sports.combine(all, g, 0) } catch (e: Exception) { all } }
        Sports.lastCombined[0] = value; SportCache.combined = value
        GoalNote.check(value)
    }
    val favC = remember(SportCache.rev) { Prefs.str("sport_country").split(',').filter { it.isNotBlank() }.map { it.lowercase() }.toSet() }
    // a favourite country = its leagues and its national team
    val favNations = remember(favC) { favC.flatMap { MyTeams.NATIONS[it.uppercase()] ?: emptyList() } }
    fun fav(e: SportEvent) = e.country in favC || MyTeams.has(e.home?.name ?: "-") || MyTeams.has(e.away?.name ?: "-") || MatchFollow.has(e.id) ||
        favNations.any { it.equals(e.home?.name, true) || it.equals(e.away?.name, true) }
    val sportsOn = remember(SportCache.rev) { Prefs.str("sm_sports", Prefs.str("sport_enabled", "football")).split(',').filter { it.isNotBlank() && it != "*" }.toSet() }
    val selectedTeams = remember(SportCache.rev) { Prefs.str("sm_teams").split('|').filter { it.isNotBlank() }.toSet() }
    val selectedCountries = remember(SportCache.rev) { Prefs.str("sm_countries").split(',').filter { it.isNotBlank() }.toSet() }
    val selectedLeagues = remember(SportCache.rev) { Prefs.str("sm_leagues").split('|').filter { it.isNotBlank() }.toSet() }
    // Independent multi-select filters apply to all Sport tabs.
    fun inSport(e: SportEvent) = sportsOn.isEmpty() || e.sport in sportsOn
    fun inUserFilters(e: SportEvent) =
        (selectedTeams.isEmpty() || e.home?.name in selectedTeams || e.away?.name in selectedTeams) &&
        (selectedCountries.isEmpty() || e.country.lowercase() in selectedCountries) &&
        (selectedLeagues.isEmpty() || e.league in selectedLeagues)
    fun followedEv(e: SportEvent) = MatchFollow.has(e.id) || MyTeams.list().any { t -> t.equals(e.home?.name, true) || t.equals(e.away?.name, true) }
    fun inTab(e: SportEvent, t: String) = when (t) { "live" -> e.live; "soon" -> e.state == "pre"; "mine" -> followedEv(e); "fav" -> fav(e) && !e.over; else -> e.over }
    val shown = remember(withCh, tab, SportCache.rev, MyTeams.rev, MatchFollow.rev) {
        withCh.filter { e -> inSport(e) && inUserFilters(e) && inTab(e, tab) }
            .sortedWith(if (tab == "mine" || tab == "fav") compareBy({ it.state != "in" }, { it.state == "post" }, { it.start }) else compareBy({ it.fromGuide }, { it.replay }, { !fav(it) }, { it.league }, { it.start }))
    }
    val ev = shown.firstOrNull { it.id == sel } ?: shown.firstOrNull()
    // Live: real matches by league, then what your channels show right now (from the guide, no live score)
    fun group(e: SportEvent) = if (e.onAir) tr("Sada na tvojim kanalima") else e.league
    val noRealLive = tab == "live" && shown.none { it.live }
    LaunchedEffect(ev?.id) { SportsMemory.evId = ev?.id; if (playing != null && ev?.let { e -> (e.channels + e.ai + e.maybe).none { it.url == playing?.url } } == true) playing = null }
    // Back: first the small screen stops, then out of Sport
    // the match you chose (OK) stays chosen while you move through the list; Left or Back from the right side lands on it again
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val rowFr = remember { HashMap<String, FocusRequester>() }
    var rightFocus by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(jumpTick) { if (jumpTick > 0) { delay(250); firstChipFr.tryFocus(attempts = 6) } }
    fun focusSel() {
        val id = ev?.id ?: return
        scope.launch {
            var idx = 0; var last = ""
            if (noRealLive) idx++
            for (e in shown) { if (group(e) != last) { last = group(e); idx++ }; if (e.id == id) break; idx++ }
            if (listState.layoutInfo.visibleItemsInfo.none { it.key == id }) listState.scrollToItem((idx - 1).coerceAtLeast(0))
            delay(40); rowFr[id]?.tryFocus()
        }
    }
    BackHandler(enabled = rightFocus || playing != null) { if (rightFocus) focusSel() else playing = null }
    // Back inside the list: first to the top match, then to the tabs; only from the tabs out of Sport
    BackHandler(enabled = favRowFocus) { scope.launch { tabFr.tryFocus() } }
    BackHandler(enabled = listFocus && !rightFocus && playing == null) {
        val first = shown.firstOrNull()?.id
        if (first != null && focusedRow != first) scope.launch { listState.scrollToItem(0); delay(40); rowFr[first]?.tryFocus() }
        else scope.launch { tabFr.tryFocus() }
    }
    // back from full screen: focus on the small screen or the channel you started
    LaunchedEffect(Unit) {
        val back = SportUi.back; SportUi.back = null
        if (back != null && playing != null) { delay(250); (if (back == "chip") chipFr else previewFr).tryFocus() }
    }
    val mineRev = MyTeams.rev
    val mine = remember(mineRev, withCh) { MyTeams.list().mapNotNull { t -> withCh.filter { e -> e.home?.name.equals(t, true) || e.away?.name.equals(t, true) }
        .sortedWith(compareBy({ it.state != "in" }, { it.state == "post" }, { it.start })).firstOrNull()?.let { t to it } } }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                T("SPORTS CENTER", 20.sp, C.Text, FontWeight.Bold)
                T("Utakmice, timovi i TV prenosi", 11.sp, C.Muted)
            }
            Spacer(Modifier.weight(1f))
            T(shown.size.toString() + "  MATCHES", 12.sp, C.Soft, FontWeight.SemiBold)
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp).horizontalScroll(androidx.compose.foundation.rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Chip2(tr("Sportovi") + if (sportsOn.isEmpty()) "" else " · " + sportsOn.size, false) { pick = "sport" }
            Chip2(tr("Timovi") + if (selectedTeams.isEmpty()) "" else " · " + selectedTeams.size, false) { pick = "teams" }
            Chip2(tr("Države") + if (selectedCountries.isEmpty()) "" else " · " + selectedCountries.size, false) { pick = "countries" }
            Chip2(tr("Regije") + " ▾", false) { pick = "region" }
            Chip2(tr("Takmičenja") + if (selectedLeagues.isEmpty()) "" else " · " + selectedLeagues.size, false) { pick = "leagues" }
        }
        Row(Modifier.weight(1f).fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        // ------------------------------------------------ left: your teams, filters, matches by league
        Column(Modifier.weight(1f).fillMaxHeight().glassSide(18.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (mine.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(mine, key = { it.first }) { (t, e) ->
                    val live = e.state == "in"
                    Focusable(onClick = { sel = e.id; tab = if (live) "live" else if (e.state == "pre") "soon" else "done" }, shape = Rnd(12.dp),
                        normal = if (live) C.Danger.copy(alpha = 0.14f) else C.Card, focused = C.Red, zoom = 1.03f,
                        modifier = Modifier.height(36.dp).then(if (live) Modifier.border(1.5.dp, C.Danger, Rnd(12.dp)) else Modifier)) { f ->
                        Row(Modifier.align(Alignment.Center).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Kit(if (e.home?.name.equals(t, true)) e.home else e.away, 16.dp)
                            T(t + if (live) " " + (e.home?.score ?: "") + ":" + (e.away?.score ?: "") else "", 12.sp, if (f) C.OnRed else C.Text, FontWeight.Bold, maxLines = 1)  // i18n-ignore
                            T(if (live) (e.clock ?: tr("UŽIVO")) else java.text.SimpleDateFormat("EEE HH:mm", java.util.Locale.getDefault()).format(java.util.Date(e.start)), 11.sp,  // i18n-ignore
                                if (f) C.OnRed else if (live) C.Danger else C.Muted, maxLines = 1)
                        }
                    }
                }
            }
            Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf("live" to "Uživo", "soon" to "Uskoro", "done" to "Završeno", "fav" to "★ Favoriti", "mine" to "Pratim").forEach { (id, l) ->
                    val n = withCh.count { e -> inSport(e) && inUserFilters(e) && (if (id == "live") e.live else inTab(e, id)) }
                    Chip2((if (id == "live") "● " else "") + tr(l) + (if (id == "done" || (id == "soon" && other == null && tab != "soon")) "" else " · $n"), tab == id, if (tab == id) tabFr else null) { tab = id }
                }
            }
            if (loading && shown.isEmpty()) Loading("Učitavam utakmice…")
            else if (shown.isEmpty()) Message(when (tab) { "live" -> "Trenutno nema utakmica uživo"; "soon" -> "Nema utakmica uskoro"; "mine" -> "Ne pratiš nijednu utakmicu · dugi OK na utakmicu › Prati"; "fav" -> "Nema utakmica tvojih favorita u idućih 10 dana · ＋ Države ili ＋ Timovi"; else -> "Nema završenih utakmica" })
            else LazyColumn(state = listState, modifier = Modifier.onFocusChanged { listFocus = it.hasFocus }, verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                var lastLeague = ""
                if (noRealLive) item(key = "nolive") { T("Trenutno nema utakmica uživo", 13.sp, C.Muted, FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp, start = 4.dp)) }
                shown.forEachIndexed { i, e ->
                    if (group(e) != lastLeague) { lastLeague = group(e)
                        item(key = "h" + lastLeague + i) { Kicker(if (e.onAir) "📺  " + lastLeague else flagEmoji(e.country) + "  " + lastLeague, if (e.onAir) C.Amber else C.Soft, modifier = Modifier.padding(top = 6.dp, start = 4.dp)) }  // i18n-ignore
                    }
                    item(key = e.id) { MatchRow(e, e.id == ev?.id, rowFr.getOrPut(e.id) { FocusRequester() }, onFocus = { focusedRow = e.id }, onMenu = { menu = e }) {
                        if (sel != e.id) { sel = e.id; playing = null }
                        // OK on a match: the focus goes on to its first channel on the right (Left / Back come back here)
                        jumpTick++ } }
                }
            }
        }
        // ------------------------------------------------ right: small screen (only after a channel is chosen) and the match
        Column(Modifier.width(420.dp).fillMaxHeight().glass(18.dp).padding(12.dp).onFocusChanged { rightFocus = it.hasFocus }
            .onPreviewKeyEvent { k -> if (k.type == KeyEventType.KeyDown && k.key == Key.DirectionLeft) { focusSel(); true } else false },
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val pc = playing
            if (pc != null) Focusable(onClick = { SportUi.back = "preview"; playChannels(nav, listOf(pc), 0) }, onLongClick = null, focusRequester = previewFr, shape = Rnd(14.dp),
                normal = Color.Transparent, focused = C.CardHi, zoom = 1f, modifier = Modifier.fillMaxWidth()) { f ->
                PreviewBox(pc, Modifier.fillMaxWidth())
                if (f) Box(Modifier.matchParentSize().border(3.dp, C.Red, Rnd(14.dp)))
            }
            else Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).glass(14.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { Ico(R.drawable.lc_play, 20.dp, C.Text) }
                    T("Izaberi kanal ispod", 14.sp, C.Text, FontWeight.Bold)
                    T("slika krene tek na OK · OK OK = cijeli ekran", 11.sp, C.Muted)
                }
            }
            if (ev != null) MatchCard(ev, playing, chipFr, firstChipFr, onPlay = { ch -> if (playing?.url == ch.url) { SportUi.back = "chip"; playChannels(nav, listOf(ch), 0) } else playing = ch }, onMenu = { menu = ev })
        }
    }
    }
    // long OK on a match: reminder, your team, watch now
    menu?.let { e ->
        val title = if (e.home != null && e.away != null) e.home.name + " – " + e.away.name else e.title
        val ch = (e.channels + e.ai + e.maybe).firstOrNull()
        Popup(title, { menu = null }) {
            T(java.text.SimpleDateFormat("EEE dd.MM. HH:mm", java.util.Locale.getDefault()).format(java.util.Date(e.start)) + " · " + e.league + (ch?.let { " · " + it.name } ?: ""), 12.sp, C.Muted)  // i18n-ignore
            if (e.state == "pre") {
                val has = Reminders.has(title, e.start)
                OptionRow(if (has) "Ukloni podsjetnik" else "Podsjeti me", R.drawable.lc_bell, color = C.Amber) {
                    val on = Reminders.toggle(Reminder("s${e.id.hashCode()}", title, e.start, ch?.url, ch?.name, ch?.logo, ch?.pid, "sport"))
                    Toast.makeText(ctx, tr(if (on) "Podsjetnik postavljen" else "Podsjetnik uklonjen"), Toast.LENGTH_SHORT).show(); menu = null
                }
            }
            if (ch != null) OptionRow("Gledaj kanal sada", R.drawable.lc_play, color = C.Green) { menu = null; playChannels(nav, listOf(ch), 0) }
            if (e.state != "post") {
                val fm = MatchFollow.has(e.id)
                OptionRow(if (fm) "Ne prati utakmicu" else "Prati utakmicu", R.drawable.lc_bell, sub = if (fm) null else tr("gol, crveni karton, produžeci, penali, kraj → obavijest na Početnoj")) {
                    val on = MatchFollow.toggle(e.id); menu = null
                    GoalNote.check(withCh)
                    Toast.makeText(ctx, tr(if (on) "Pratiš utakmicu" else "Ne pratiš više utakmicu"), Toast.LENGTH_SHORT).show()
                }
            }
            listOfNotNull(e.home?.name, e.away?.name).forEach { t ->
                OptionRow(if (MyTeams.has(t)) trf("Ne prati tim: %s", t) else trf("Prati tim: %s", t), R.drawable.lc_star,
                    sub = if (MyTeams.has(t)) null else tr("svaka njihova utakmica: „danas igra“ + sve obavijesti")) { MyTeams.toggle(t); menu = null }
            }
        }
    }
    // teams: the list with ✕ and "＋ Add team" (search or any name), several at once
    pick?.let { k ->
        Popup(tr(when (k) { "sport" -> "Sportovi"; "region" -> "Regije"; "fav" -> "Favoriti – države"; "countries" -> "Države"; "teams" -> "Timovi"; else -> "Takmičenja" }), { pick = null }, width = 520.dp) {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                when (k) {
                    "sport" -> {
                        ToggleRow(tr("Svi sportovi"), sportsOn.isEmpty()) { Prefs.put("sm_sports", "*"); SportCache.rev++; pick = null }
                        (Sports.SPORTS + Sports.MORE_SPORTS).forEach { (id, name) ->
                            ToggleRow(sportIcon(id) + "  " + tr(name), id in sportsOn) { v ->
                                Prefs.put("sm_sports", (if (v) sportsOn + id else sportsOn - id).joinToString(",").ifBlank { "*" }); SportCache.rev++; pick = null }
                        }
                    }
                    "region" -> {
                        // which parts of the world are read (fewer = faster); world competitions always
                        val on = Prefs.str("sport_cont", "eu").split(',').map { if (it == "am") "na" else it }.filter { it.isNotBlank() }.toSet()
                        Sports.CONTINENTS.forEach { (id, name) ->
                            ToggleRow(tr(name), id in on) { v ->
                                val nw = if (v) on + id else on - id
                                Prefs.put("sport_cont", nw.ifEmpty { setOf("eu") }.joinToString(",")); SportCache.clear(); Sports.lastCombined.clear(); pick = null
                            }
                        }
                    }
                    "countries" -> {
                        withCh.map { it.country.lowercase() }.filter { it.isNotBlank() }.distinct().sorted().forEach { code ->
                            val name = COUNTRIES.firstOrNull { it.first.equals(code, true) }?.second ?: code.uppercase()
                            ToggleRow(flagEmoji(code) + "  " + tr(name), code in selectedCountries) { v ->
                                val next = if (v) selectedCountries + code else selectedCountries - code
                                Prefs.put("sm_countries", next.joinToString(",")); SportCache.rev++; pick = null
                            }
                        }
                    }
                    "teams" -> {
                        withCh.flatMap { listOfNotNull(it.home?.name, it.away?.name) }.distinct().sorted().forEach { team ->
                            ToggleRow(team, team in selectedTeams) { v ->
                                val next = if (v) selectedTeams + team else selectedTeams - team
                                Prefs.put("sm_teams", next.joinToString("|")); SportCache.rev++; pick = null
                            }
                        }
                    }
                    "leagues" -> {
                        withCh.map { it.league }.filter { it.isNotBlank() }.distinct().sorted().forEach { league ->
                            ToggleRow(league, league in selectedLeagues) { v ->
                                val next = if (v) selectedLeagues + league else selectedLeagues - league
                                Prefs.put("sm_leagues", next.joinToString("|")); SportCache.rev++; pick = null
                            }
                        }
                    }
                    "fav" -> {
                        T("Odaberi države za Favoriti", 12.sp, C.Muted, maxLines = 2)
                        COUNTRIES.drop(1).forEach { (code, name) ->
                            ToggleRow(flagEmoji(code.lowercase()) + "  " + tr(name), code.lowercase() in favC) { v ->
                                val cur = Prefs.str("sport_country").split(',').filter { it.isNotBlank() }.toSet()
                                Prefs.put("sport_country", (if (v) cur + code else cur.filter { !it.equals(code, true) }.toSet()).joinToString(",")); SportCache.other.remove("fav"); SportCache.other.remove("mine"); SportCache.rev++
                            }
                        }
                    }
                    else -> {
                        var q by remember { mutableStateOf("") }
                        Field(tr("Traži tim"), q, { q = it }, Modifier.fillMaxWidth())
                        val names = remember(withCh) { withCh.flatMap { listOfNotNull(it.home?.name, it.away?.name) }.distinct().sorted() }
                        val list = (MyTeams.list() + names.filter { n -> q.length >= 2 && n.contains(q.trim(), true) }.take(40)).distinct()
                        if (list.isEmpty()) T(if (q.length < 2) "Ukucaj bar 2 slova imena tima" else "Ništa nije pronađeno", 13.sp, C.Muted)
                        list.forEach { t -> ToggleRow(t, MyTeams.has(t)) { MyTeams.toggle(t); SportCache.rev++ } }
                        var manage by remember { mutableStateOf(false) }
                        OptionRow("＋  " + tr("Dodaj tim"), null, sub = tr("više timova, i po imenu"), color = C.Green) { manage = true }  // i18n-ignore
                        if (manage) FavTeamsDialog { manage = false; SportCache.rev++ }
                    }
                }
            }
        }
    }
    LaunchedEffect(shown.isNotEmpty()) { if (shown.isNotEmpty()) { delay(100); focusSel() } }
}

@Composable
private fun Chip2(label: String, on: Boolean, fr: FocusRequester? = null, onClick: () -> Unit) {
    Focusable(onClick = onClick, focusRequester = fr, shape = Pill, normal = if (on) Color.White else C.Card, focused = C.Red, zoom = 1.04f, modifier = Modifier.height(34.dp)) { f ->
        T(label, 13.sp, if (f) C.OnRed else if (on) Color(0xFF0B0F1A) else C.Text, FontWeight.SemiBold, maxLines = 1,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 14.dp))
    }
}

/** A team's colours as a small square (a picture would cost more). */
@Composable
private fun Kit(t: Team?, size: androidx.compose.ui.unit.Dp) {
    val a = t?.color ?: Color(0xFF64748B); val b = t?.color2 ?: a
    Box(Modifier.size(size).clip(Rnd(size * 0.28f)).background(Brush.horizontalGradient(0.5f to a, 0.5f to b)).border(1.dp, Color.White.copy(alpha = 0.25f), Rnd(size * 0.28f)))
}

@Composable
private fun MatchRow(e: SportEvent, selected: Boolean, fr: FocusRequester?, onFocus: () -> Unit, onMenu: () -> Unit, onClick: () -> Unit) {
    val title = if (e.home != null && e.away != null) e.home.name + " – " + e.away.name else e.title
    val reminded = e.state == "pre" && Reminders.has(title, e.start)
    Focusable(onClick = onClick, onMenu = onMenu, onFocus = onFocus, focusRequester = fr, shape = Rnd(12.dp),
        normal = if (selected) C.CardFocus else C.Card, focused = C.Red, zoom = 1.01f,
        modifier = Modifier.fillMaxWidth().height(46.dp).then(if (reminded) Modifier.border(1.5.dp, C.Amber, Rnd(12.dp)) else Modifier)) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            T(when { e.onAir -> tr(if (e.replay) "SNIMAK" else "NA TV"); e.state == "in" -> e.clock ?: tr("UŽIVO"); e.state == "post" -> "FT"; else -> whenShort(e.start) },  // i18n-ignore
                11.sp, if (f) C.OnRed else if (e.onAir) C.Amber else if (e.state == "in") C.Danger else C.Muted, FontWeight.Bold, modifier = Modifier.width(70.dp), maxLines = 1)
            if (e.home != null && e.away != null) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    T(e.home.name, 14.sp, if (f) C.OnRed else C.Text, FontWeight.SemiBold, maxLines = 1); Kit(e.home, 15.dp)
                }
                T(if (e.state == "pre" || e.fromGuide) "–" else (e.home.score ?: "0") + " : " + (e.away.score ?: "0"), 15.sp, if (f) C.OnRed else C.Text, FontWeight.ExtraBold,  // i18n-ignore
                    modifier = Modifier.width(58.dp), maxLines = 1, align = androidx.compose.ui.text.style.TextAlign.Center)
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Kit(e.away, 15.dp); T(e.away.name, 14.sp, if (f) C.OnRed else C.Text, FontWeight.SemiBold, maxLines = 1)
                }
            } else T(e.title, 14.sp, if (f) C.OnRed else C.Text, FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
            if (reminded) Ico(R.drawable.lc_bell, 14.dp, if (f) C.OnRed else C.Amber)
        }
    }
}

@Composable
private fun MatchCard(e: SportEvent, playing: Channel?, playingFr: FocusRequester, firstFr: FocusRequester, onPlay: (Channel) -> Unit, onMenu: () -> Unit) {
    Column(Modifier.fillMaxWidth().glass(16.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (e.home != null && e.away != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Kit(e.home, 26.dp); T(e.home.name, 15.sp, C.Text, FontWeight.Bold, maxLines = 1) }
                T(if (e.state == "pre" || e.fromGuide) java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(e.start)) else (e.home.score ?: "0") + " : " + (e.away.score ?: "0"),  // i18n-ignore
                    28.sp, C.Text, FontWeight.ExtraBold, maxLines = 1)
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) { T(e.away.name, 15.sp, C.Text, FontWeight.Bold, maxLines = 1); Kit(e.away, 26.dp) }
            }
        } else T(e.title, 17.sp, C.Text, FontWeight.Bold, maxLines = 2)
        if (e.state == "pre") T(whenLong(e.start), 13.sp, C.Text, FontWeight.SemiBold, maxLines = 1, modifier = Modifier.fillMaxWidth(), align = androidx.compose.ui.text.style.TextAlign.Center)
        T(listOfNotNull(if (e.onAir) "📺 " + tr(if (e.replay) "SNIMAK" else "NA TV") else if (e.state == "in") "● " + (e.clock ?: tr("UŽIVO")) else null, e.league, e.round).joinToString(" · "), 11.sp,  // i18n-ignore
            if (e.onAir) C.Amber else if (e.state == "in") C.Danger else C.Muted, FontWeight.Bold, maxLines = 1, modifier = Modifier.fillMaxWidth(), align = androidx.compose.ui.text.style.TextAlign.Center)
        // goals and cards with the minute
        if (e.details.isNotEmpty()) Row(Modifier.fillMaxWidth()) {
            listOf("h", "a").forEach { side ->
                Column(Modifier.weight(1f), horizontalAlignment = if (side == "h") Alignment.Start else Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    e.details.filter { it.side == side }.take(6).forEach { d ->
                        val icon = when { d.kind.startsWith("goal") -> "⚽"; d.kind == "red" -> "🟥"; else -> "🟨" }  // i18n-ignore
                        T(icon + " " + d.player + " " + d.minute + (if (d.kind.contains("_pen")) " (pen.)" else "") + (if (d.kind.contains("_og")) " (ag)" else ""), 11.sp, C.Soft, maxLines = 1)  // i18n-ignore
                    }
                }
            }
        }
        val chs = (e.channels + e.ai + e.maybe).distinctBy { it.url }.take(6)
        Kicker(tr("Prenose") + " · " + tr("OK gledaj · drži OK 🔔"), C.Muted)
        if (chs.isEmpty()) T("Nijedan tvoj kanal ne prenosi ovu utakmicu", 12.sp, C.Muted)
        // who broadcasts it anywhere (TV houses), also when it is not in your list
        val tvNames = remember(e.id, e.tv) { e.tv }
        LaunchedEffect(Unit) { try { Rights.refresh() } catch (_: Exception) {} }
        var aiTv by remember(e.id) { mutableStateOf<List<String>>(emptyList()) }
        LaunchedEffect(e.id) {
            if (!SportsAI.ready) return@LaunchedEffect
            delay(1200)
            aiTv = try { SportsAI.broadcasters(e).map { (if (it.country.isNotBlank()) it.country + " · " else "") + it.name } } catch (_: Exception) { emptyList() }
        }
        val usual = remember(e.id) { Sports.usualTv(e) }
        // who shows it: rights where you live first, then ESPN's list, then the AI (each source can be switched off in Settings › Sports Center)
        val rights = remember(e.id) { Sports.rightsTv(e) }
        val espn = if (Prefs.bool("sport_src_espn", true)) tvNames else emptyList()
        if (rights.isNotEmpty() || espn.isNotEmpty() || aiTv.isNotEmpty()) T("📺 " + tr("TV:") + " " + (rights + espn + aiTv).distinct().take(10).joinToString(" · "), 12.sp, C.Soft, maxLines = 3)  // i18n-ignore
        else if (usual.isNotEmpty()) T("📺 " + tr("Obično prenose:") + " " + usual.take(8).joinToString(" · "), 12.sp, C.Soft, maxLines = 3)  // i18n-ignore
        // your channels that show it: always as buttons (OK on a match brings the focus to the first one)
        if (chs.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(chs, key = { _, c -> c.url }) { i, ch ->
                Focusable(onClick = { onPlay(ch) }, onMenu = onMenu, focusRequester = if (playing?.url == ch.url) playingFr else if (i == 0 && playing == null) firstFr else null, shape = Pill, normal = if (playing?.url == ch.url) Color.White else C.Card, focused = C.Red, zoom = 1.04f,
                    modifier = Modifier.height(34.dp)) { f ->
                    Row(Modifier.align(Alignment.Center).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(C.Green))
                        T(ch.name, 12.sp, if (f) C.OnRed else if (playing?.url == ch.url) Color(0xFF0B0F1A) else C.Text, FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun sportIcon(s: String) = when (s) { "football" -> "⚽"; "basketball" -> "🏀"; "f1" -> "🏎"; "tennis" -> "🎾"; "volleyball" -> "🏐"; "handball" -> "🤾"; "" -> "🏟"; else -> "🏅" }  // i18n-ignore
private fun sportName(s: String) = when (s) { "football" -> "Fudbal"; "basketball" -> "Košarka"; "f1" -> "Formula 1"; "tennis" -> "Tenis"; "volleyball" -> "Odbojka"; "handball" -> "Rukomet"; "" -> "Svi sportovi"; else -> s }
fun flagEmoji(c: String): String = when (c) {
    "eu" -> "🇪🇺"; "int", "" -> "🌍"
    else -> if (c.length == 2) String(Character.toChars(0x1F1E6 + (c.uppercase()[0] - 'A'))) + String(Character.toChars(0x1F1E6 + (c.uppercase()[1] - 'A'))) else "🌍"
}
private fun countryName(c: String) = when (c) {
    "eu" -> tr("Evropa"); "int" -> tr("Međunarodno"); else -> try { java.util.Locale("", c.uppercase()).getDisplayCountry(java.util.Locale(Look.lang)) } catch (e: Exception) { c.uppercase() }
}


/** The goal note in the corner, for 8 seconds; it never takes the focus (watching goes on). */
@Composable
fun GoalNoteOverlay() {
    val t = GoalNote.text ?: return
    LaunchedEffect(t) { delay(8000); GoalNote.text = null }
    Box(Modifier.fillMaxSize().padding(end = 30.dp, bottom = 30.dp), contentAlignment = Alignment.BottomEnd) {
        Row(Modifier.clip(Rnd(14.dp)).background(Color(0xF20F172A)).border(1.dp, Color(0xFF22C55E), Rnd(14.dp)).padding(horizontal = 16.dp, vertical = 10.dp)) {
            T(t, 14.sp, Color.White, FontWeight.Bold, maxLines = 1)
        }
    }
}


/** Matches hidden from Home with a long OK (until you follow them again). */
object HomeHide {
    fun list(): Set<String> = Prefs.str("home_hide").split(',').filter { it.isNotBlank() }.toSet()
    fun hide(id: String) { Prefs.put("home_hide", (list() + id).toList().takeLast(200).joinToString(",")); GoalNote.followed.removeAll { it.id == id } }
    fun show(id: String) { val l = list(); if (id in l) Prefs.put("home_hide", (l - id).joinToString(",")) }
    fun clear() = Prefs.put("home_hide", "")
}

/**
 * Home, under the playlist card: at most 2 of the matches you follow, only while they are live (a finished one stays greyed 15 min).
 * The one where something just happened (goal, card, penalties) comes first. More than 2: "Show more" opens Sport › Following.
 */
@Composable
fun FollowCard(nav: Nav, width: androidx.compose.ui.unit.Dp) {
    if (!Prefs.bool("home_sport_card", !Perf.lite)) return
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); tick++ } }
    val all = remember(GoalNote.followed.toList(), GoalNote.lastRev, tick) {
        val hidden = HomeHide.list()
        GoalNote.followed.filter { it.id !in hidden && it.live }
            .sortedWith(compareBy({ it.state == "post" }, { -GoalNote.lastEventAt(it.id) }))
    }
    var menu by remember { mutableStateOf<SportEvent?>(null) }
    menu?.let { e ->
        Popup(listOfNotNull(e.home?.name, e.away?.name).joinToString(" – ").ifBlank { e.title }, { menu = null }) {
            OptionRow("Ukloni s Početne", R.drawable.lc_x, sub = tr("vraća se kad ga opet staviš na Prati")) { HomeHide.hide(e.id); menu = null }
            if (MatchFollow.has(e.id)) OptionRow("Ne prati utakmicu", R.drawable.lc_bell) { MatchFollow.toggle(e.id); GoalNote.followed.removeAll { it.id == e.id }; menu = null }
            listOfNotNull(e.home?.name, e.away?.name).filter { MyTeams.has(it) }.forEach { t ->
                OptionRow(trf("Ne prati tim: %s", t), R.drawable.lc_star) { MyTeams.toggle(t); GoalNote.followed.removeAll { x -> x.home?.name.equals(t, true) || x.away?.name.equals(t, true) }; menu = null }
            }
            OptionRow("Otvori u Sportu", R.drawable.lc_trophy) { menu = null; GoalNote.open(nav, "sp:" + e.id + ":open") }  // i18n-ignore
        }
    }
    if (all.isEmpty()) return
    Column(Modifier.width(width), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        all.take(2).forEach { e ->
            val h = e.home; val a = e.away
            Focusable(onClick = { GoalNote.open(nav, "sp:" + e.id + ":" + (if (e.state == "post") "end" else "live")) }, onMenu = { menu = e },  // i18n-ignore
                shape = Rnd(14.dp), normal = C.Rail, focused = C.CardHi, zoom = 1.02f,
                modifier = Modifier.fillMaxWidth().border(1.dp, if (e.live) C.Danger.copy(alpha = 0.6f) else C.Line, Rnd(14.dp))) { f ->
                if (f) Box(Modifier.matchParentSize().border(2.dp, C.Red, Rnd(14.dp)))
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).alpha(if (e.state == "post") 0.5f else 1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (e.live) Box(Modifier.size(7.dp).clip(CircleShape).background(C.Danger))
                        T(if (e.live) (e.clock ?: tr("UŽIVO")) else tr("KRAJ"), 11.sp, if (e.live) C.Danger else C.Muted, FontWeight.Bold, maxLines = 1)
                        T(e.league, 10.sp, C.Muted, maxLines = 1, modifier = Modifier.weight(1f))
                    }
                    T(if (h != null && a != null) h.name + " " + (h.score ?: "0") + ":" + (a.score ?: "0") + " " + a.name else e.title,  // i18n-ignore
                        14.sp, C.Text, FontWeight.Bold, maxLines = 1)
                    e.details.lastOrNull { it.kind.startsWith("goal") || it.kind.startsWith("red") }?.let { d ->
                        T((if (d.kind.startsWith("goal")) "⚽ " else "🟥 ") + d.minute + " " + d.player, 11.sp, C.Soft, maxLines = 1)  // i18n-ignore
                    }
                }
            }
        }
        if (all.size > 2) Focusable(onClick = { SportsMemory.view = "mine"; nav.root(Screen.Sports) }, shape = Rnd(12.dp), normal = C.Rail, focused = C.CardHi, zoom = 1.02f,
            modifier = Modifier.fillMaxWidth().height(34.dp).border(1.dp, C.Line, Rnd(12.dp))) { _ ->
            T(trf("Prikaži više · još %s", all.size - 2), 12.sp, C.Text, FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center))
        }
    }
}

/** Sport data kept between visits; refreshed only when stale (scores) or when the filters change. */
object SportCache {
    var rev by mutableIntStateOf(0)
    var guideRev by mutableIntStateOf(0)
    var today: List<SportEvent>? = null; var todayAt = 0L
    val other = HashMap<String, List<SportEvent>>()
    val otherAt = HashMap<String, Long>()
    var combined: List<SportEvent> = emptyList()
    var guide: Sports.Guide? = null
    fun clear() { today = null; other.clear(); otherAt.clear(); combined = emptyList(); rev++ }
}


/** "Tue 20:45" for this week, "17.11. 20:45" later, only the time for today */
fun whenShort(ms: Long): String {
    val loc = java.util.Locale(Look.lang)
    val days = ((ms - Sports.dayStart(0)) / 86_400_000L).toInt()
    return when {
        days <= 0 -> java.text.SimpleDateFormat("HH:mm", loc).format(java.util.Date(ms))  // i18n-ignore
        days < 7 -> java.text.SimpleDateFormat("EEE HH:mm", loc).format(java.util.Date(ms))  // i18n-ignore
        else -> java.text.SimpleDateFormat("dd.MM. HH:mm", loc).format(java.util.Date(ms))  // i18n-ignore
    }
}
/** "Tuesday, 17 November" (today / tomorrow named) */
fun whenLong(ms: Long): String {
    val loc = java.util.Locale(Look.lang)
    val days = ((ms - Sports.dayStart(0)) / 86_400_000L).toInt()
    val d = java.text.SimpleDateFormat("EEEE, d. MMMM", loc).format(java.util.Date(ms))  // i18n-ignore
    return when (days) { 0 -> tr("Danas") + " · " + d; 1 -> tr("Sutra") + " · " + d; else -> d }  // i18n-ignore
}


/** What was playing small in Sport and what you pressed for full screen: Back from the player returns exactly there. */
object SportUi { var playing: Channel? = null; var back: String? = null }
