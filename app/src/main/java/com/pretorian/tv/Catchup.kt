package com.pretorian.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import kotlinx.coroutines.async
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Catch-up: channels that keep an archive at the provider, the days back, and the past shows to play. */
@Composable
fun CatchupScreen(nav: Nav) {
    if (Look.dawn) { DawnCatchup(nav); return }
    val ctx = LocalContext.current
    val chans by load(Unit) {
        Store.actives(ctx).filter { it.type == SourceType.XTREAM || (it.type == SourceType.M3U && Tech.m3uCatchup) }
            .flatMap { p -> try { Repo.allChannels(p).filter { Catchups.has(p, it) } } catch (e: Exception) { emptyList() } }
            .distinctBy { it.url }
    }
    var q by remember { mutableStateOf("") }
    var ch by remember { mutableStateOf<Channel?>(null) }
    var day by remember { mutableIntStateOf(0) }
    val first = remember { FocusRequester() }
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(320.dp).fillMaxHeight().padding(start = 16.dp, end = 10.dp, top = 22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            T("Catch-up", 26.sp, C.Text, FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
            T("Kanali s arhivom", 13.sp, C.Muted, modifier = Modifier.padding(start = 6.dp))
            Field("Traži kanal", q, { q = it }, Modifier.fillMaxWidth())
            when (val s = chans) {
                is Load.Loading -> Loading("Učitavam kanale…")
                is Load.Err -> Message("Greška", s.message)
                is Load.Ok -> {
                    val list = s.value.filter { q.isBlank() || Sports.norm(it.name).contains(Sports.norm(q)) }
                    if (s.value.isEmpty()) Message("Nijedan kanal nema arhivu", "Provider ne nudi catch-up za tvoje kanale")
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                        itemsIndexed(list, key = { _, c -> c.url }) { i, c ->
                            val sel = ch?.url == c.url
                            Focusable(modifier = Modifier.fillMaxWidth().height(Rows.channel), onClick = { ch = c; day = 0 }, onFocus = { ch = c },
                                focusRequester = if (i == 0) first else null, normal = if (sel) C.CardFocus else C.Card, zoom = 1.01f) { f ->
                                Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    ChannelLogo(c.logo, c.name)
                                    T(c.name, 14.sp, if (f || sel) C.Text else C.Soft, if (sel) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                                    T(trf("%s d", c.archive), 11.sp, C.RedText)
                                }
                            }
                        }
                    }
                    LaunchedEffect(list.isNotEmpty()) { if (list.isNotEmpty()) first.tryFocus() }
                }
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(C.Line))
        Box(Modifier.weight(1f).fillMaxHeight().padding(start = 20.dp, end = 24.dp, top = 22.dp)) {
            val c = ch
            if (c == null) Message("Izaberi kanal", "Emisije iz arhive možeš gledati i kad su prošle")
            else Archive(c, day, { day = it }, nav)
        }
    }
}

@Composable
private fun Archive(c: Channel, day: Int, onDay: (Int) -> Unit, nav: Nav) {
    val ctx = LocalContext.current
    var shown by remember { mutableStateOf<Channel?>(null) }
    LaunchedEffect(c.url) { delay(350); shown = c }
    val ch = shown ?: return
    val p = Store.byId(ctx, ch.pid) ?: return
    val list by load(ch.url) { archiveOf(p, ch) }
    ArchiveList(list, ch, p, day, onDay, nav)
}

/** Past shows of a channel that the provider keeps (its archive table, else the guide). */
private suspend fun archiveOf(p: Playlist, ch: Channel): List<EpgItem> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val x = Xtream(p)
            val now = System.currentTimeMillis()
            // M3U archive: the past shows come from the guide (no archive table at the provider)
            val a = if (p.type != SourceType.XTREAM) emptyList() else try { x.archive(ch.id) } catch (e: Exception) { emptyList() }
            if (a.any { it.end <= now && it.archived }) a
            else {
                // the archive table is empty: take past shows from the guide (own EPG sources first, then the provider's XMLTV)
                val days = ch.archive.coerceIn(1, 14)
                val own = EpgSources.urls(p.id).firstNotNullOfOrNull { url ->
                    val id = ch.epg?.lowercase()?.ifBlank { null } ?: return@firstNotNullOfOrNull null
                    try { Xmltv.read(url, setOf(id), now - days * 86_400_000L, now).filter { it.end <= now }.ifEmpty { null } } catch (e: Exception) { null }
                }?.map { EpgItem(it.title, it.desc, it.start, it.end, null, true) }
                own ?: (if (p.type == SourceType.XTREAM) x.archiveFromGuide(ch.epg, days) else emptyList()).ifEmpty { throw Exception(tr("Provider za ovaj kanal ne šalje listu arhiviranih emisija.")) }
            }
        }

@Composable
private fun ArchiveList(list: Load<List<EpgItem>>, ch: Channel, p: Playlist, day: Int, onDay: (Int) -> Unit, nav: Nav) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChannelLogo(ch.logo, ch.name)
            T(ch.name, 20.sp, C.Text, FontWeight.Bold, modifier = Modifier.weight(1f))
            T(trf("Zadnjih %s dana", ch.archive), 12.sp, C.Muted)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items((0 until ch.archive.coerceAtMost(14)).toList()) { d ->
                val sel = d == day
                val label = when (d) { 0 -> tr("Danas"); 1 -> tr("Jučer"); else -> SimpleDateFormat("EEE d.", Locale(Look.lang)).format(Date(Sports.dayStart(-d))) }
                Focusable(onClick = { onDay(d) }, shape = Pill, normal = if (sel) C.Red else C.Card, focused = if (sel) C.Red else C.CardHi,
                    zoom = 1.04f, modifier = Modifier.height(38.dp)) { _ ->
                    T(label, 14.sp, if (sel) C.OnRed else C.Text, FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center).padding(horizontal = 14.dp))
                }
            }
        }
        when (val s = list) {
            is Load.Loading -> Loading("Učitavam arhivu…")
            is Load.Err -> Message("Arhiva nije dostupna", s.message)
            is Load.Ok -> {
                val now = System.currentTimeMillis()
                val from = Sports.dayStart(-day); val to = Sports.dayStart(-day + 1)
                val items = s.value.filter { it.start in from until to && it.end <= now && it.archived }.sortedByDescending { it.start }
                if (items.isEmpty()) Message("Nema emisija u arhivi za ovaj dan", "Probaj drugi dan gore")
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(items, key = { it.start }) { e ->
                        Focusable(modifier = Modifier.fillMaxWidth().height(66.dp), onClick = {
                            Preview.stop()
                            val url = Catchups.url(p, ch, e) ?: return@Focusable
                            nav.push(Screen.Player(listOf(PlayItem(e.title, url, ch.logo, ch.name + " · " + e.times())), 0, live = false))
                        }, normal = C.Card, zoom = 1.01f) { f ->
                            Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Ico(R.drawable.lc_history, 18.dp, C.RedText)
                                Column(Modifier.weight(1f)) {
                                    T(e.title, 15.sp, if (f) C.Text else C.Soft, FontWeight.SemiBold)
                                    T(e.times(), 12.sp, C.Muted)
                                }
                                T("OK = gledaj", 11.sp, C.Muted)
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------ Catch-up (one menu at a time) */

/** Set by the Archive button of the TV screen: Catch-up opens straight on this channel's archive. */
object CatchupOpen { var ch: Channel? = null }

/** Catch-up shows you played: the entries of "Continue watching" (progress itself lives in [Watch]; removing here never deletes it). */
object CatchupHist {
    var rev by mutableIntStateOf(0)
    data class E(val url: String, val title: String, val ch: Channel, val start: Long, val end: Long, val ts: Long)
    private fun chJ(c: Channel) = org.json.JSONObject().put("id", c.id).put("num", c.num).put("n", c.name).put("l", c.logo ?: "").put("g", c.group)
        .put("u", c.url).put("e", c.epg ?: "").put("p", c.pid).put("a", c.archive).put("c", c.catchup ?: "")
    private fun chOf(o: org.json.JSONObject) = Channel(o.str("id"), o.optInt("num"), o.str("n"), o.str("l").ifBlank { null }, o.str("g"), o.str("u"),
        o.str("e").ifBlank { null }, o.str("p"), o.optInt("a"), o.str("c").ifBlank { null })
    fun all(): List<E> = try {
        val a = org.json.JSONArray(Prefs.str("cu_hist", "[]"))
        (0 until a.length()).map { a.getJSONObject(it) }.map { o -> E(o.str("url"), o.str("t"), chOf(o.getJSONObject("ch")), o.optLong("s"), o.optLong("e"), o.optLong("ts")) }
    } catch (e: Exception) { emptyList() }
    private fun save(l: List<E>) {
        val a = org.json.JSONArray()
        l.take(60).forEach { a.put(org.json.JSONObject().put("url", it.url).put("t", it.title).put("ch", chJ(it.ch)).put("s", it.start).put("e", it.end).put("ts", it.ts)) }
        Prefs.put("cu_hist", a.toString()); rev++
    }
    fun add(e: E) = save(listOf(e) + all().filter { it.url != e.url })
    fun remove(url: String) = save(all().filter { it.url != url })
    fun urls(): Set<String> = all().mapTo(HashSet()) { it.url }
    /** started and not finished, newest first */
    fun open(ctx: android.content.Context): List<Pair<E, Progress>> = all().mapNotNull { e -> Watch.get(ctx, e.url)?.takeIf { it.started }?.let { e to it } }
}

/** Last 10 Catch-up searches. */
object CatchupQ {
    var rev by mutableIntStateOf(0)
    fun list(): List<String> = Prefs.str("cu_q").split('\n').filter { it.isNotBlank() }
    private fun save(l: List<String>) { Prefs.put("cu_q", l.take(10).joinToString("\n")); rev++ }
    fun add(q: String) = save(listOf(q) + list().filter { !it.equals(q, true) })
    fun remove(q: String) = save(list().filter { it != q })
    fun clear() = save(emptyList())
}

/** the archive of a channel only reaches back this far */
private fun expired(ch: Channel, start: Long) = start < Sports.dayStart(-(ch.archive.coerceIn(1, 14) - 1))

private sealed class Cu {
    object Menu : Cu()
    object Continue : Cu()
    object Groups : Cu()
    object Search : Cu()
    data class Channels(val pid: String, val cat: Category) : Cu()
    data class Archive(val ch: Channel) : Cu()
    val key get() = when (this) { Menu -> "m"; Continue -> "c"; Groups -> "g"; Search -> "s"; is Channels -> "ch:$pid:${cat.id}"; is Archive -> "a:${ch.url}" }
}

private data class CuData(val chans: List<Channel>, val groups: List<Pair<String, Category>>)

/** Catch-up: Continue watching · Groups · Search; every level replaces the one before, Back goes exactly one level out. */
@Composable
private fun DawnCatchup(nav: Nav) {
    val ctx = LocalContext.current
    val stack = remember { androidx.compose.runtime.mutableStateListOf<Cu>(Cu.Menu).also { s -> CatchupOpen.ch?.let { s.add(Cu.Archive(it)) }; CatchupOpen.ch = null } }
    val mem = remember { HashMap<String, Int>() }
    val data by load(Unit) {
        val pls = Store.actives(ctx).filter { it.type == SourceType.XTREAM || (it.type == SourceType.M3U && Tech.m3uCatchup) }
        val chans = ArrayList<Channel>(); val groups = ArrayList<Pair<String, Category>>()
        pls.forEach { p ->
            val cs = try { Repo.allChannels(p).filter { Catchups.has(p, it) } } catch (e: Exception) { emptyList() }
            chans += cs
            val ids = cs.map { it.group }.toSet()
            try { Repo.liveGroups(p).filter { it.id in ids }.forEach { groups += p.id to it } } catch (_: Exception) {}
        }
        CuData(chans.distinctBy { it.url }, groups)
    }
    val top = stack.last()
    androidx.activity.compose.BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }
    fun push(c: Cu) { stack.add(c) }
    LaunchedEffect(top) {
        Dawn.crumb = when (top) {
            Cu.Menu -> ""; Cu.Continue -> tr("Nastavi gledati"); Cu.Groups -> tr("Grupe"); Cu.Search -> tr("Pretraga")
            is Cu.Channels -> top.cat.name; is Cu.Archive -> top.ch.name
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) { CuTitle.on = true; onDispose { Dawn.crumb = ""; CuTitle.on = false } }
    Box(Modifier.fillMaxSize().padding(start = 34.dp, end = 38.dp, top = 6.dp, bottom = 8.dp)) {
        when (val d = data) {
            is Load.Loading -> Loading("Učitavam kanale…")
            is Load.Err -> Message("Greška", d.message)
            is Load.Ok -> key(top.key) {
                val all = d.value
                when (top) {
                    Cu.Menu -> CuPanel {
                        CuList(mem, top.key, listOf(
                            CuItem("Nastavi gledati", icon = R.drawable.lc_play) { push(Cu.Continue) },
                            CuItem("Grupe", icon = R.drawable.lc_layers) { push(Cu.Groups) },
                            CuItem("Pretraga", icon = R.drawable.lc_search) { push(Cu.Search) },
                        ))
                    }
                    Cu.Continue -> CuContinue(mem, top.key, nav)
                    Cu.Groups -> CuPanel {
                        var q by remember { mutableStateOf("") }
                        CuSearchField("Traži grupe…", q) { q = it }
                        val list = all.groups.filter { q.isBlank() || Sports.norm(it.second.name).contains(Sports.norm(q)) }
                        if (all.chans.isEmpty()) Message("Nijedan kanal nema arhivu", "Provider ne nudi catch-up za tvoje kanale")
                        else if (list.isEmpty()) Message("Nema rezultata", null)
                        else CuList(mem, top.key, list.map { (pid, cat) ->
                            CuItem(cat.name, icon = R.drawable.lc_tv, trailing = all.chans.count { it.pid == pid && it.group == cat.id }.toString()) { push(Cu.Channels(pid, cat)) }
                        })
                    }
                    is Cu.Channels -> CuPanel {
                        var q by remember { mutableStateOf("") }
                        CuSearchField(trf("Traži kanale u %s…", top.cat.name), q) { q = it }
                        val list = all.chans.filter { it.pid == top.pid && it.group == top.cat.id && (q.isBlank() || Sports.norm(it.name).contains(Sports.norm(q))) }
                        if (list.isEmpty()) Message("Nema rezultata", null)
                        else CuList(mem, top.key, list.map { c -> CuItem(c.name, ch = c, trailing = trf("%sd", c.archive.coerceAtMost(14))) { push(Cu.Archive(c)) } })
                    }
                    is Cu.Archive -> CuArchive(top.ch, mem, top.key, nav)
                    Cu.Search -> CuSearch(all.chans, mem, top.key, nav, onChannel = { push(Cu.Archive(it)) })
                }
            }
        }
    }
}

internal class CuItem(val title: String, val icon: Int? = null, val ch: Channel? = null, val sub: String? = null, val trailing: String? = null,
                     val progress: Float? = null, val dim: Boolean = false, val onLong: (() -> Unit)? = null, val divider: Boolean = false, val onClick: () -> Unit)

@Composable
internal fun CuPanel(wide: Boolean = false, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column((if (wide) Modifier.fillMaxWidth() else Modifier.width(560.dp)).fillMaxHeight().dawnGlass().padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Catch-up: the title inside the panel (approved): "CATCH-UP TV / <level>"
        if (CuTitle.on) T("CATCH-UP TV" + (if (Dawn.crumb.isNotBlank()) " / " + Dawn.crumb.uppercase() else ""), 13.sp, C.Soft, FontWeight.SemiBold,  // i18n-ignore
            maxLines = 1, modifier = Modifier.padding(start = 8.dp, top = 2.dp))
        content()
    }
}

internal object CuTitle { var on by mutableStateOf(false) }

/** A list of one level; the focus comes back to the row you left (and the list scrolls to it). */
@Composable
internal fun CuList(mem: HashMap<String, Int>, key: String, items: List<CuItem>, modifier: Modifier = Modifier) {
    val start = (mem[key] ?: 0).coerceIn(0, (items.size - 1).coerceAtLeast(0))
    val st = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = (start - 3).coerceAtLeast(0))
    val fr = remember { FocusRequester() }
    LazyColumn(modifier, state = st, verticalArrangement = Arrangement.spacedBy(3.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        itemsIndexed(items) { i, it ->
            if (it.divider) Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).height(1.dp).background(Color(0x33FFFFFF)))
            Focusable(onClick = it.onClick, onMenu = it.onLong, onFocus = { mem[key] = i }, focusRequester = if (i == start) fr else null,
                shape = Rnd(12.dp), normal = Color.Transparent, focused = Dawn.pill, focusBorderColor = Color.Transparent, zoom = 1f,
                modifier = Modifier.fillMaxWidth().height(if (it.sub != null || it.progress != null) 62.dp else 50.dp)) { f ->
                val fg = if (f) Dawn.onPill else if (it.dim) C.Muted else C.Text
                Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (it.ch != null) ChannelLogo(it.ch.logo, it.ch.name) else if (it.icon != null) Ico(it.icon, 18.dp, fg)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        T(it.title, 17.sp, fg, if (f) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                        if (it.sub != null) T(it.sub, 13.sp, if (f) fg else C.Soft, maxLines = 1)
                        it.progress?.let { pr ->
                            Box(Modifier.fillMaxWidth().height(3.dp).background(if (f) Color(0x33000000) else Color(0x33FFFFFF))) {
                                Box(Modifier.fillMaxHeight().fillMaxWidth(pr.coerceIn(0.02f, 1f)).background(if (f) Dawn.onPill else Silver.Focus))
                            }
                        }
                    }
                    if (it.trailing != null) T(it.trailing, 13.sp, if (f) fg else C.Soft)
                    Ico(R.drawable.lc_chevron_right, 16.dp, if (f) fg else C.Muted)
                }
            }
        }
    }
    LaunchedEffect(items.isNotEmpty()) { if (items.isNotEmpty()) { runCatching { st.scrollToItem((start - 3).coerceAtLeast(0)) }; fr.tryFocus() } }
}

/** "Search …" line at the top of a level: OK opens the keyboard, long OK clears it. */
@Composable
internal fun CuSearchField(hint: String, q: String, onQ: (String) -> Unit) {
    var ask by remember { mutableStateOf(false) }
    Focusable(onClick = { ask = true }, onMenu = { onQ("") }, shape = Pill, normal = Color.Transparent, focused = Dawn.pill, focusBorderColor = Color.Transparent,
        zoom = 1f, modifier = Modifier.fillMaxWidth().height(42.dp).border(1.dp, Color(0x4DFFFFFF), Pill)) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Ico(R.drawable.lc_search, 16.dp, if (f) Dawn.onPill else Color.White)
            T(q.ifBlank { tr(hint) }, 15.sp, if (f) Dawn.onPill else if (q.isBlank()) C.Soft else C.Text, maxLines = 1)
        }
    }
    if (ask) TextDialog(hint.removeSuffix("…"), "", q, onDismiss = { ask = false }, onChange = { onQ(it.trim()) }) { t -> onQ(t.trim()); ask = false }
}

private fun mmss(ms: Long): String { val s = ms / 1000; return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60) }

/** Plays a show: from [at] ms (0 = from the beginning), and remembers it for "Continue watching". */
private fun cuPlay(ctx: android.content.Context, nav: Nav, ch: Channel, e: EpgItem, at: Long) {
    val p = Store.byId(ctx, ch.pid)
    if (expired(ch, e.start)) { android.widget.Toast.makeText(ctx, tr("Ova emisija više nije u arhivi"), android.widget.Toast.LENGTH_SHORT).show(); return }
    val url = Catchups.url(p, ch, e) ?: run { android.widget.Toast.makeText(ctx, tr("Arhiva nije dostupna"), android.widget.Toast.LENGTH_SHORT).show(); return }
    Preview.stop(); PreviewSel.ch = null
    CatchupHist.add(CatchupHist.E(url, e.title, ch, e.start, e.end, System.currentTimeMillis()))
    nav.push(Screen.Player(listOf(PlayItem(e.title, url, ch.logo, ch.name + " · " + e.times(), startAt = at)), 0, live = false))
}

/** The small action box of a show: Watch from the beginning, or Continue / Start from the beginning when it was partly watched. */
@Composable
private fun CuActions(ch: Channel, e: EpgItem, nav: Nav, onRemove: (() -> Unit)? = null, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val p = Store.byId(ctx, ch.pid)
    val pr = Catchups.url(p, ch, e)?.let { Watch.get(ctx, it) }?.takeIf { it.started }
    Popup(e.title, onDismiss) {
        T(ch.name + " · " + SimpleDateFormat("EEE d. MMM", Locale(Look.lang)).format(Date(e.start)) + " · " + e.times(), 14.sp, C.Soft)
        if (expired(ch, e.start)) T("Ova emisija više nije u arhivi.", 15.sp, C.Text)
        else if (pr != null) {
            OptionRow(trf("Nastavi (%s)", mmss(pr.pos)), R.drawable.lc_play) { onDismiss(); cuPlay(ctx, nav, ch, e, pr.pos) }
            OptionRow("Od početka", R.drawable.lc_rotate_ccw) { onDismiss(); cuPlay(ctx, nav, ch, e, 0L) }
        } else OptionRow("Gledaj od početka", R.drawable.lc_play) { onDismiss(); cuPlay(ctx, nav, ch, e, 0L) }
        if (onRemove != null) OptionRow("Ukloni iz „Nastavi gledati“", R.drawable.lc_x) { onDismiss(); onRemove() }
    }
}

@Composable
private fun CuContinue(mem: HashMap<String, Int>, key: String, nav: Nav) {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val r = CatchupHist.rev + Watch.rev
    var q by remember { mutableStateOf("") }
    var pick by remember { mutableStateOf<CatchupHist.E?>(null) }
    CuPanel(wide = true) {
        CuSearchField("Traži sačuvane emisije…", q) { q = it }
        val list = CatchupHist.open(ctx).filter { (e, _) -> q.isBlank() || Sports.norm(e.title).contains(Sports.norm(q)) || Sports.norm(e.ch.name).contains(Sports.norm(q)) }
        if (list.isEmpty()) Message(if (q.isBlank()) "Nema započetih emisija" else "Nema rezultata", if (q.isBlank()) "Ovdje se pojave emisije iz arhive koje nisi dogledao." else null)
        else CuList(mem, key, list.map { (e, pr) ->
            val exp = expired(e.ch, e.start)
            CuItem(e.title, ch = e.ch, dim = exp,
                sub = e.ch.name + " · " + SimpleDateFormat("EEE d. MMM HH:mm", Locale(Look.lang)).format(Date(e.start)) + if (exp) " · " + tr("Arhiva istekla") else "",
                trailing = "${(pr.fraction * 100).toInt()}%", progress = pr.fraction) { pick = e }
        })
    }
    pick?.let { e -> CuActions(e.ch, EpgItem(e.title, null, e.start, e.end, null, true), nav, onRemove = { CatchupHist.remove(e.url) }) { pick = null } }
}

@Composable
private fun CuArchive(ch: Channel, mem: HashMap<String, Int>, key: String, nav: Nav) {
    val ctx = LocalContext.current
    val p = Store.byId(ctx, ch.pid)
    val list by load(ch.url) { if (p == null) emptyList() else archiveOf(p, ch) }
    @Suppress("UNUSED_VARIABLE") val wr = Watch.rev
    var day by remember { mutableIntStateOf(mem[key + ":day"] ?: 0) }
    var q by remember { mutableStateOf("") }
    var pick by remember { mutableStateOf<EpgItem?>(null) }
    CuPanel(wide = true) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(horizontal = 6.dp)) {
            ChannelLogo(ch.logo, ch.name)
            T(ch.name, 20.sp, C.Text, FontWeight.Bold, modifier = Modifier.weight(1f))
            T(trf("Arhiva: %s dana", ch.archive.coerceIn(1, 14)), 13.sp, C.Soft)
        }
        // days: the oldest on the left, today on the right
        val days = (0 until ch.archive.coerceIn(1, 14)).toList().reversed()
        val dState = androidx.compose.foundation.lazy.rememberLazyListState()
        LaunchedEffect(Unit) { dState.scrollToItem(days.indexOf(day).coerceAtLeast(0)) }
        LazyRow(state = dState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(days) { d ->
                val sel = d == day
                val label = when (d) { 0 -> tr("Danas"); 1 -> tr("Jučer"); else -> SimpleDateFormat("EEE", Locale(Look.lang)).format(Date(Sports.dayStart(-d))) }
                Focusable(onClick = { day = d; mem[key + ":day"] = d; mem[key] = 0 }, shape = Rnd(10.dp), normal = if (sel) Color(0xD9DADADE) else Color.Transparent, focused = Dawn.pill,
                    focusBorderColor = Color.Transparent, zoom = 1f, modifier = Modifier.width(112.dp).height(48.dp).border(1.dp, Color(0x4DFFFFFF), Rnd(10.dp))) { f ->
                    val fg = if (f || sel) Dawn.onPill else Color.White
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        T(label, 13.sp, fg, FontWeight.SemiBold); T(SimpleDateFormat("d. MMM", Locale(Look.lang)).format(Date(Sports.dayStart(-d))), 12.sp, fg)
                    }
                }
            }
        }
        CuSearchField("Traži emisije…", q) { q = it }
        when (val s = list) {
            is Load.Loading -> Loading("Učitavam arhivu…")
            is Load.Err -> Message("Arhiva nije dostupna", s.message)
            is Load.Ok -> {
                val now = System.currentTimeMillis()
                val from = Sports.dayStart(-day); val to = Sports.dayStart(-day + 1)
                // a search looks through every day of the archive; without it only the chosen day
                val items = s.value.filter { it.end <= now && it.archived && !expired(ch, it.start) &&
                    (if (q.isBlank()) it.start in from until to else Sports.norm(it.title).contains(Sports.norm(q))) }.sortedBy { it.start }
                if (items.isEmpty()) Message(if (q.isBlank()) "Nema emisija u arhivi za ovaj dan" else "Nema rezultata", if (q.isBlank()) "Izaberi drugi dan gore" else null)
                else CuList(mem, key + ":" + day + ":" + q, items.map { e ->
                    val pr = Catchups.url(p, ch, e)?.let { Watch.get(ctx, it) }?.takeIf { it.started || it.watched }
                    CuItem(e.title, icon = R.drawable.lc_history,
                        sub = (if (q.isBlank()) "" else SimpleDateFormat("EEE d. MMM · ", Locale(Look.lang)).format(Date(e.start))) + e.times() + " · " + trf("%s min", ((e.end - e.start) / 60_000).toInt()),
                        trailing = pr?.let { if (it.watched) "✓" else "${(it.fraction * 100).toInt()}%" }, progress = pr?.takeIf { !it.watched }?.fraction) { pick = e }
                })
            }
        }
    }
    pick?.let { e -> CuActions(ch, e, nav) { pick = null } }
}

/** One search for Catch-up: channel names and show titles of the archive, best matches first, with the last 10 searches. */
@Composable
private fun CuSearch(chans: List<Channel>, mem: HashMap<String, Int>, key: String, nav: Nav, onChannel: (Channel) -> Unit) {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val r = CatchupQ.rev
    var q by remember { mutableStateOf(mem.keys.firstOrNull { it.startsWith("q=") }?.removePrefix("q=") ?: "") }
    var ask by remember { mutableStateOf(false) }
    var clear by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf<Pair<Channel, EpgItem>?>(null) }
    fun run(t: String) { mem.keys.removeAll { it.startsWith("q=") }; q = t; if (t.isNotBlank()) { mem["q=$t"] = 0; CatchupQ.add(t) } }
    // shows: searched in the guide kept on the box (past days as long as the archive goes back), every archive channel, no provider requests
    val shows by load(q) {
        if (q.isEmpty()) emptyList() else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val byEpg = chans.filter { !it.epg.isNullOrBlank() }.groupBy { it.epg!!.lowercase() }
            val srcs = chans.map { it.pid }.distinct().mapNotNull { Store.byId(ctx, it) }
                .flatMap { p -> listOf(GuideDb.srcOf(p)) + EpgSources.urls(p.id).map { GuideDb.srcOf(it) } }.distinct()
            val maxDays = chans.maxOfOrNull { it.archive }?.coerceIn(1, 14) ?: 7
            val n = Sports.norm(q)
            GuideDb.searchPast(srcs, q, System.currentTimeMillis() - maxDays * 86_400_000L)
                .flatMap { pr -> byEpg[pr.channel.lowercase()].orEmpty().map { c -> c to pr } }
                .filter { (c, pr) -> !expired(c, pr.start) && Sports.norm(pr.title).contains(n) }
                .distinctBy { (c, pr) -> c.url + pr.start }
                .sortedByDescending { it.second.start }
                .map { (c, pr) -> c to EpgItem(pr.title, pr.desc, pr.start, pr.end, null, true) }
        }
    }
    CuPanel(wide = true) {
        Focusable(onClick = { ask = true }, onMenu = { run("") }, shape = Pill, normal = Color.Transparent, focused = Dawn.pill, focusBorderColor = Color.Transparent,
            zoom = 1f, modifier = Modifier.fillMaxWidth().height(46.dp).border(1.dp, Color(0x4DFFFFFF), Pill)) { f ->
            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Ico(R.drawable.lc_search, 18.dp, if (f) Dawn.onPill else Color.White)
                T(q.ifBlank { tr("Pretraži Catch-up…") }, 16.sp, if (f) Dawn.onPill else if (q.isBlank()) C.Soft else C.Text, maxLines = 1)
            }
        }
        if (q.isBlank()) {
            val hist = CatchupQ.list()
            if (hist.isEmpty()) Message("Traži kanal ili emisiju", "Na primjer naziv kanala ili emisije iz arhive.")
            else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                    T("Nedavne pretrage", 14.sp, C.Soft, FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    T("Drži OK za brisanje jedne", 12.sp, C.Muted)
                }
                CuList(mem, key + ":h", hist.map { h -> CuItem(h, icon = R.drawable.lc_history, onLong = { CatchupQ.remove(h) }) { run(h) } } +
                    CuItem("Obriši historiju pretrage", icon = R.drawable.lc_trash_2) { clear = true })
            }
        } else {
            val n = Sports.norm(q)
            fun score(t: String): Int { val x = Sports.norm(t); return when { x == n -> 0; x.startsWith(n) -> 1; x.split(' ').any { it.startsWith(n) } -> 2; else -> 3 } }
            val chHits = chans.filter { Sports.norm(it.name).contains(n) }.sortedBy { score(it.name) }
            val showHits = ((shows as? Load.Ok)?.value ?: emptyList()).sortedWith(compareBy<Pair<Channel, EpgItem>> { score(it.second.title) }.thenByDescending { it.second.start })
            val items = chHits.map { c -> CuItem(c.name, ch = c, sub = tr("Kanal"), trailing = trf("%sd", c.archive.coerceAtMost(14))) { onChannel(c) } } +
                showHits.take(100).map { (c, e) -> CuItem(e.title, icon = R.drawable.lc_history,
                    sub = tr("Emisija") + " · " + c.name + " · " + SimpleDateFormat("EEE d. MMM HH:mm", Locale(Look.lang)).format(Date(e.start))) { pick = c to e } }
            if (items.isEmpty() && shows is Load.Loading) Loading("Tražim u arhivi…")
            else if (items.isEmpty()) Message("Nema rezultata", "Probaj drugi naziv.")
            else {
                if (shows is Load.Loading) T("Tražim još u arhivi…", 12.sp, C.Muted, modifier = Modifier.padding(horizontal = 8.dp))
                CuList(mem, key + ":r:" + q, items)
            }
        }
    }
    if (ask) TextDialog("Pretraži Catch-up", "", q, onDismiss = { ask = false }, onChange = { q = it.trim() }) { t -> ask = false; run(t.trim()) }
    if (clear) Popup("Obriši historiju pretrage?", { clear = false }) {
        OptionRow("Obriši", R.drawable.lc_trash_2) { CatchupQ.clear(); clear = false }
        OptionRow("Odustani", R.drawable.lc_x) { clear = false }
    }
    pick?.let { (c, e) -> CuActions(c, e, nav) { pick = null } }
}
