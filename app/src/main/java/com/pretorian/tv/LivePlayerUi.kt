package com.pretorian.tv

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* Live TV fullscreen player (spec 07): one authoritative overlay state, six small centred actions,
 * every floating panel centred on the screen and opening upward above the actions. */

object LiveUi {
    const val NONE = "none"
    const val INFO = "info"
    const val OPTIONS = "options"
    const val EPG = "epg"
    const val PICTURE = "picture"
    const val CATCHUP = "catchup"
    const val HISTORY = "history"
    const val MORE = "more"
    const val MORE_AUDIO = "more_audio"
    const val MORE_SUBS = "more_subs"
    const val MORE_INFO = "more_info"
    /** the action icon a panel belongs to (focus goes back to it when the panel closes) */
    fun iconOf(state: String) = when (state) { MORE_AUDIO, MORE_SUBS, MORE_INFO -> MORE; else -> state }
    /** one step back: nested More pages → More, a panel → the actions, the actions / info → clean picture */
    fun back(state: String) = when (state) {
        MORE_AUDIO, MORE_SUBS, MORE_INFO -> MORE
        EPG, PICTURE, CATCHUP, HISTORY, MORE -> OPTIONS
        else -> NONE
    }
    val ACTIONS = listOf(
        Triple("fav", R.drawable.lc_star, "Favoriti"), Triple(EPG, R.drawable.lc_calendar, "EPG"),
        Triple(PICTURE, R.drawable.lc_monitor, "Slika"), Triple(CATCHUP, R.drawable.lc_rotate_ccw, "Catch-up"),
        Triple(HISTORY, R.drawable.lc_history, "Historija"), Triple(MORE, R.drawable.lc_more_horizontal, "Više"),
    )
}

private val PanelBg = Color(0xD914181C)
private val PanelLine = Color(0x29FFFFFF)

/** A restrained upward reveal for panels and the action bar. */
@Composable
private fun Modifier.reveal(): Modifier {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(180)) }
    return this.graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 16f }
}

/** STATE B: compact channel information near the bottom (number, logo, name, quality, time, NOW with a thin bar, NEXT). */
@Composable
fun LiveInfoOverlay(item: PlayItem, number: Int, quality: String?, epg: List<EpgItem>?) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); now = System.currentTimeMillis() } }
    val cur = epg?.firstOrNull { it.start <= now && it.end > now }
    val next = epg?.firstOrNull { it.start > now }
    val f = SimpleDateFormat("HH:mm", Locale.getDefault())  // i18n-ignore
    BoxWithConstraints(Modifier.fillMaxSize().padding(bottom = 36.dp), contentAlignment = Alignment.BottomCenter) {
        Row(Modifier.reveal().widthIn(max = maxWidth * 0.72f).fillMaxWidth().clip(Rnd(16.dp)).background(PanelBg).border(1.dp, PanelLine, Rnd(16.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            T(number.toString(), 22.sp, Color(0xFFB8BEC6), FontWeight.Medium)
            item.channel?.let { ChannelLogo(it, 48.dp) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    T(item.title, 18.sp, Color.White, FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
                    if (quality != null) Box(Modifier.border(1.dp, Color(0x99FFFFFF), Rnd(5.dp)).padding(horizontal = 6.dp, vertical = 1.dp)) {
                        T(quality, 11.sp, Color.White, FontWeight.Bold)
                    }
                    Spacer(Modifier.weight(1f))
                    T(f.format(Date(now)), 15.sp, Color(0xFFDDE1E6))
                }
                if (cur != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        T(tr("Sada").uppercase(), 12.sp, Color(0xFFB8BEC6), FontWeight.SemiBold)
                        T(cur.times(), 13.sp, Color(0xFFDDE1E6))
                        T(cur.title, 14.sp, Color.White, FontWeight.Medium, modifier = Modifier.weight(1f))
                    }
                    Box(Modifier.fillMaxWidth().height(3.dp).clip(Rnd(2.dp)).background(Color(0x33FFFFFF))) {
                        Box(Modifier.fillMaxWidth(cur.progress()).fillMaxHeight().background(Color(0xFFF2F3F5)))
                    }
                }
                if (next != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    T(tr("Zatim").uppercase(), 12.sp, Color(0xFFB8BEC6), FontWeight.SemiBold)
                    T(f.format(Date(next.start)), 13.sp, Color(0xFFDDE1E6))
                    T(next.title, 14.sp, Color(0xFFDDE1E6), modifier = Modifier.weight(1f))
                }
                // no guide: only the name line, never an empty bar
            }
        }
    }
}

/** STATE C: the six small actions, grouped and centred (not stretched across the screen). */
@Composable
fun LiveActionBar(fav: Boolean, catchupOk: Boolean, frs: Map<String, FocusRequester>, onAction: (String) -> Unit) {
    Row(Modifier.reveal(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        LiveUi.ACTIONS.forEach { (id, icon, label) ->
            val off = id == LiveUi.CATCHUP && !catchupOk
            Focusable(onClick = { onAction(id) }, focusRequester = frs[id], shape = Rnd(12.dp), normal = Color.Transparent, focused = Dawn.pill, zoom = 1f,
                modifier = Modifier.width(78.dp).height(62.dp)) { f ->
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    val tint = when { off -> Color(0x66FFFFFF); f -> Color.White; else -> Color(0xFFDDE1E6) }
                    Ico(if (id == "fav" && fav) R.drawable.lc_star else icon, 22.dp, if (id == "fav" && fav) C.Amber else tint)
                    Spacer(Modifier.height(5.dp))
                    T(label, 12.sp, tint, if (f) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

/** The shared floating panel: centred on the screen, above the actions, stays put — only its content scrolls.
 *  The focus cannot leave it with the arrows (Back closes it and the focus goes back to its icon). */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LivePanel(title: String, icon: Int?, wide: Boolean, first: FocusRequester, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        val w = if (wide) (maxWidth * 0.56f).coerceIn(420.dp, 820.dp) else (maxWidth * 0.26f).coerceIn(260.dp, 380.dp)
        Column(Modifier.reveal().width(w).clip(Rnd(16.dp)).background(PanelBg).border(1.dp, PanelLine, Rnd(16.dp))
            .focusProperties { exit = { FocusRequester.Cancel } }.focusGroup().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) { Ico(icon, 18.dp, Color.White); Spacer(Modifier.width(10.dp)) }
                T(title, 16.sp, Color.White, FontWeight.SemiBold)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x1FFFFFFF)))
            content()
        }
    }
    LaunchedEffect(Unit) { delay(60); first.tryFocus() }
}

/** A row of a vertical panel: focus = the pill with a white border, the active choice = ✓ (never confused). */
@Composable
fun LiveRow(label: String, on: Boolean = false, sub: String? = null, value: String? = null, fr: FocusRequester? = null, onClick: () -> Unit) {
    Focusable(onClick = onClick, focusRequester = fr, shape = Rnd(10.dp), normal = Color.Transparent, focused = Dawn.pill, zoom = 1f,
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) { _ ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                T(label, 15.sp, Color.White, if (on) FontWeight.SemiBold else FontWeight.Normal)
                if (sub != null) T(sub, 12.sp, Color(0xFFB8BEC6), maxLines = 2)
            }
            if (value != null) T(value, 13.sp, Color(0xFFDDE1E6))
            if (on) Ico(R.drawable.lc_check, 17.dp, Color.White)
        }
    }
}

/** Floating EPG: about three programmes visible, the rows scroll inside; NOW is a small mark, independent of the focus.
 *  OK on a programme shows its details with only the actions that really exist. */
@Composable
fun LiveEpgPanel(chName: String, epg: List<EpgItem>?, canFromStart: (EpgItem) -> Boolean, onFromStart: (EpgItem) -> Unit, first: FocusRequester) {
    var sel by remember { mutableStateOf<EpgItem?>(null) }
    val now = System.currentTimeMillis()
    LivePanel(tr("EPG") + " — " + chName.uppercase(), R.drawable.lc_calendar, true, first) {
        val d = sel
        when {
            epg == null -> LiveRow(tr("Učitavam program…"), fr = first) {}
            epg.isEmpty() -> LiveRow(tr("Nema EPG podataka."), fr = first) {}
            d != null -> {
                T(d.times() + "  •  " + d.title, 16.sp, Color.White, FontWeight.SemiBold, maxLines = 2)  // i18n-ignore
                T(d.desc?.ifBlank { null } ?: tr("Opis nije dostupan."), 13.sp, Color(0xFFDDE1E6), maxLines = 5)
                if (d.start <= now && canFromStart(d)) LiveRow(tr("Gledaj od početka"), fr = first) { onFromStart(d) }
                LiveRow(tr("Nazad na program"), fr = if (d.start <= now && canFromStart(d)) null else first) { sel = null }
            }
            else -> {
                val st = rememberLazyListState()
                val curIdx = epg.indexOfFirst { it.start <= now && it.end > now }.coerceAtLeast(0)
                LaunchedEffect(Unit) { st.scrollToItem(curIdx) }
                LazyColumn(Modifier.fillMaxWidth().height(210.dp), state = st, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(epg) { i, e ->
                        val isNow = e.start <= now && e.end > now
                        Focusable(onClick = { sel = e }, focusRequester = if (i == curIdx) first else null, shape = Rnd(10.dp), normal = Color(0x0FFFFFFF),
                            focused = Dawn.pill, zoom = 1f, modifier = Modifier.fillMaxWidth().height(64.dp)) { _ ->
                            Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                T(e.times(), 14.sp, Color(0xFFDDE1E6), modifier = Modifier.width(104.dp))
                                Box(Modifier.width(1.dp).height(34.dp).background(Color(0x33FFFFFF)))
                                Column(Modifier.weight(1f)) {
                                    T(e.title, 15.sp, Color.White, FontWeight.SemiBold)
                                    e.desc?.ifBlank { null }?.let { T(it, 12.sp, Color(0xFFB8BEC6)) }
                                }
                                if (isNow) Box(Modifier.clip(Rnd(6.dp)).background(Color(0x33FFFFFF)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                    T(tr("Sada").uppercase(), 11.sp, Color.White, FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Floating Catch-up: the archived shows of the channel (newest first), day · time — title; OK plays it from its start. */
@Composable
fun LiveCatchupPanel(chName: String, list: Load<List<EpgItem>>, onPlay: (EpgItem) -> Unit, first: FocusRequester) {
    LivePanel(tr("Catch-up") + " — " + chName.uppercase(), R.drawable.lc_rotate_ccw, true, first) {
        when (list) {
            is Load.Loading -> LiveRow(tr("Učitavam arhivu…"), fr = first) {}
            is Load.Err -> LiveRow(tr("Arhiva nije dostupna"), sub = list.message, fr = first) {}
            is Load.Ok -> {
                val now = System.currentTimeMillis()
                val past = list.value.filter { it.end <= now }.sortedByDescending { it.start }
                if (past.isEmpty()) LiveRow(tr("Arhiva nije dostupna"), fr = first) {}
                else LazyColumn(Modifier.fillMaxWidth().height(210.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(past) { i, e ->
                        LiveRow(dayLabel(e.start) + " · " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(e.start)) + " — " + e.title,  // i18n-ignore
                            sub = e.desc?.ifBlank { null }, fr = if (i == 0) first else null) { onPlay(e) }
                    }
                }
            }
        }
    }
}

/** "Danas", "Jučer" or the weekday. */
fun dayLabel(t: Long): String {
    val d0 = Sports.dayStart(0)
    return when {
        t >= d0 -> tr("Danas")
        t >= Sports.dayStart(-1) -> tr("Jučer")
        else -> SimpleDateFormat("EEEE", Locale(Look.lang)).format(Date(t)).replaceFirstChar { it.uppercase() }  // i18n-ignore
    }
}

/** Floating History: the last ten different channels in one row (◀ ▶ go round), Clear history below. */
@Composable
fun LiveHistoryPanel(list: List<Channel>, onPick: (Channel) -> Unit, onClear: () -> Unit, first: FocusRequester) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LivePanel(tr("Historija – zadnjih 10 kanala"), R.drawable.lc_history, true, first) {
        if (list.isEmpty()) { LiveRow(tr("Još nema gledanih kanala."), fr = first) {}; return@LivePanel }
        val wrap = remember(list.size) { RowWrap(list.size) }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gap = 8.dp
            val n = ((maxWidth + gap) / (96.dp + gap)).toInt().coerceIn(1, 10)
            val tw = (maxWidth - gap * (n - 1)) / n
            LazyRow(state = wrap.state, horizontalArrangement = Arrangement.spacedBy(gap), contentPadding = PaddingValues(0.dp)) {
                itemsIndexed(list) { i, ch ->
                    Focusable(onClick = { onPick(ch) }, focusRequester = if (i == 0) first else wrap.fr(i), shape = Rnd(10.dp), normal = Color(0x14FFFFFF),
                        focused = Dawn.pill, zoom = 1f, modifier = Modifier.width(tw).height(tw * 0.8f).then(wrap.keys(i, scope))) { _ ->
                        HistoryTile(ch)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Focusable(onClick = onClear, shape = Rnd(10.dp), normal = Color(0x14FFFFFF), focused = Dawn.pill, borderColor = Color(0x29FFFFFF), zoom = 1f,
                modifier = Modifier.height(40.dp)) { _ ->
                Row(Modifier.align(Alignment.Center).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Ico(R.drawable.lc_trash_2, 16.dp, Color.White); T(tr("Obriši historiju"), 14.sp, Color.White)
                }
            }
        }
    }
}

@Composable
private fun HistoryTile(ch: Channel) {
    var failed by remember(ch.logo) { mutableStateOf(ch.logo.isNullOrBlank()) }
    Column(Modifier.fillMaxSize().padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (!failed) AsyncImage(model = ch.logo, contentDescription = ch.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(), onError = { failed = true })
            else T(shortName(ch.name), 13.sp, Color.White, FontWeight.Bold, maxLines = 2, align = TextAlign.Center)
        }
        T(ch.name.substringAfterLast('|').trim(), 11.sp, Color(0xFFDDE1E6), maxLines = 1, align = TextAlign.Center)
    }
}
