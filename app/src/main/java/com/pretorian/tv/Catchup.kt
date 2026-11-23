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
    DawnCatchup(nav)
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
internal suspend fun archiveOf(p: Playlist, ch: Channel): List<EpgItem> =
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

/** the archive of a channel only reaches back this far */
internal fun expired(ch: Channel, start: Long) = start < Sports.dayStart(-(ch.archive.coerceIn(1, 14) - 1))

private sealed class Cu {
    object Menu : Cu()
    object Continue : Cu()
    object Groups : Cu()
    object Search : Cu()
    data class Channels(val pid: String, val cat: Category) : Cu()
    data class Archive(val ch: Channel) : Cu()
    val key get() = when (this) { Menu -> "m"; Continue -> "c"; Groups -> "g"; Search -> "s"; is Channels -> "ch:$pid:${cat.id}"; is Archive -> "a:${ch.url}" }
}

/** Catch-up: Continue watching · Groups · Search; every level replaces the one before, Back goes exactly one level out. */
@Composable
private fun DawnCatchup(nav: Nav) {
    // spec 15: TV › Catch Up — channels with archive (two columns) → one channel's archive; each level replaces the one before
    val ctx = LocalContext.current
    val stack = remember { androidx.compose.runtime.mutableStateListOf<Cu>(Cu.Menu).also { s -> CatchupOpen.ch?.let { s.add(Cu.Archive(it)) }; CatchupOpen.ch = null } }
    val mem = remember { CuMem.mem }
    val data by load(Unit) {
        val pls = Store.actives(ctx).filter { it.type == SourceType.XTREAM || (it.type == SourceType.M3U && Tech.m3uCatchup) }
        pls.flatMap { p -> try { Repo.allChannels(p).filter { Catchups.has(p, it) } } catch (e: Exception) { emptyList() } }.distinctBy { it.url }
    }
    val top = stack.last()
    androidx.activity.compose.BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }
    LaunchedEffect(top) { Dawn.crumb = when (top) { Cu.Continue -> tr("Nastavi gledati"); is Cu.Archive -> top.ch.name; else -> "" } }
    androidx.compose.runtime.DisposableEffect(Unit) { CuTitle.on = true; onDispose { Dawn.crumb = ""; CuTitle.on = false } }
    Box(Modifier.fillMaxSize().padding(start = 34.dp, end = 38.dp, top = 6.dp, bottom = 8.dp)) {
        when (val d = data) {
            is Load.Loading -> Loading("Učitavam kanale…")
            is Load.Err -> Message("Greška", d.message)
            is Load.Ok -> key(top.key) {
                when (top) {
                    is Cu.Archive -> CuArchiveView(top.ch, mem, top.key, nav)
                    Cu.Continue -> CuContinue(mem, top.key, nav)
                    else -> if (d.value.isEmpty()) Message("Nijedan kanal nema arhivu", "Provider ne nudi catch-up za tvoje kanale")
                            else CuChannelBrowser(d.value, mem, onContinue = { stack.add(Cu.Continue) }, onOpen = { stack.add(Cu.Archive(it)) })
                }
            }
        }
    }
}

/** Where you were in Catch Up (channel row, chosen day), kept while the app runs. */
object CuMem { val mem = HashMap<String, Int>() }

internal class CuItem(val title: String, val icon: Int? = null, val ch: Channel? = null, val sub: String? = null, val trailing: String? = null,
                     val progress: Float? = null, val dim: Boolean = false, val onLong: (() -> Unit)? = null, val divider: Boolean = false, val onClick: () -> Unit)

@Composable
internal fun CuPanel(wide: Boolean = false, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    // the panel always uses the whole width of the screen (approved: no narrow panel with empty space beside it)
    Column(Modifier.fillMaxWidth().fillMaxHeight().dawnGlass().padding(10.dp),
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
                                Box(Modifier.fillMaxHeight().fillMaxWidth(pr.coerceIn(0.02f, 1f)).background(Silver.Progress))
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
internal fun cuPlay(ctx: android.content.Context, nav: Nav, ch: Channel, e: EpgItem, at: Long) {
    val p = Store.byId(ctx, ch.pid)
    if (expired(ch, e.start)) { android.widget.Toast.makeText(ctx, tr("Ova emisija više nije u arhivi"), android.widget.Toast.LENGTH_SHORT).show(); return }
    val url = Catchups.url(p, ch, e) ?: run { android.widget.Toast.makeText(ctx, tr("Arhiva nije dostupna"), android.widget.Toast.LENGTH_SHORT).show(); return }
    Preview.stop(); PreviewSel.ch = null
    CatchupHist.add(CatchupHist.E(url, e.title, ch, e.start, e.end, System.currentTimeMillis()))
    nav.push(Screen.Player(listOf(PlayItem(e.title, url, ch.logo, ch.name + " · " + e.times(), startAt = at)), 0, live = false))
}

/** The small action box of a show: Watch from the beginning, or Continue / Start from the beginning when it was partly watched. */
@Composable
internal fun CuActions(ch: Channel, e: EpgItem, nav: Nav, onRemove: (() -> Unit)? = null, onDismiss: () -> Unit) {
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
internal fun CuContinue(mem: HashMap<String, Int>, key: String, nav: Nav) {
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
