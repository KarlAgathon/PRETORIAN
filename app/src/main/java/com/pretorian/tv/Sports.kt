package com.pretorian.tv

import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.produceState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val AiColor = Silver.TextSoft

/** Rows scroll so the card you stand on starts at the left edge: no half cut card before it. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private val StartAligned = object : androidx.compose.foundation.gestures.BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        if (offset >= 0 && offset + size <= containerSize) 0f else offset
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun AlignStart(content: @Composable () -> Unit) =
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides StartAligned) { content() }

private fun hhmm(ms: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

/** Where you were in Sports: after the full screen Back returns to the same match, day and filters. */
object SportsMemory { var evId: String? = null; var day = 0; var sport = "all"; var mine = false; var view = "all"; var league: String? = null }

/** Club crest from the feed, or a round badge in the club colour with its short name. */
@Composable
fun Crest(t: Team, size: Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(if (t.logo != null) C.Text else (t.color ?: Color(0xFF334155))),
        contentAlignment = Alignment.Center) {
        if (t.logo != null) AsyncImage(model = t.logo, contentDescription = null, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(size * 0.12f))
        else T(t.abbr.take(3), (size.value * 0.3f).sp, Color.White, FontWeight.Bold)
    }
}

@Composable
private fun StateBadge(ev: SportEvent, big: Boolean = false) {
    val fs = if (big) 15.sp else 13.sp
    when (ev.state) {
        "in" -> Row(Modifier.clip(Pill).background(Color(0xFFE11D2E)).padding(horizontal = 10.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Dot(Color.White, 7.dp); T(ev.clock ?: tr("UŽIVO"), fs, Color.White, FontWeight.Bold)
        }
        "post" -> T(ev.clock ?: "FT", fs, C.Muted, FontWeight.SemiBold)
        else -> T(hhmm(ev.start), fs, C.Soft, FontWeight.SemiBold)
    }
}

@Composable
private fun Section(title: String, list: List<SportEvent>, live: Boolean = false, fr: FocusRequester? = null,
                    onFocus: (SportEvent) -> Unit = {}, memId: String? = null, memFr: FocusRequester? = null, onMenu: (SportEvent) -> Unit = {}, onOpen: (SportEvent) -> Unit) {
    if (list.isEmpty()) return
    val rowState = androidx.compose.foundation.lazy.rememberLazyListState()
    val memIdx = list.indexOfFirst { it.id == memId }
    LaunchedEffect(memIdx) { if (memIdx > 0) rowState.scrollToItem(memIdx) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (live) Dot(Color(0xFFFF4D55), 9.dp)
            T(title, 18.sp, C.Text, FontWeight.SemiBold)
            T("· ${list.size}", 15.sp, C.Muted)
        }
        AlignStart { LazyRow(state = rowState, horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 12.dp)) {
            itemsIndexed(list, key = { _, e -> e.id }) { i, ev -> EventCard(ev, if (ev.id == memId && memFr != null) memFr else if (i == 0) fr else null, { onFocus(ev) }, { onMenu(ev) }) { onOpen(ev) } }
        } }
    }
}

@Composable
private fun EventCard(ev: SportEvent, fr: FocusRequester? = null, onFocus: () -> Unit = {}, onMenu: (() -> Unit)? = null, onClick: () -> Unit) {
    val none = ev.channels.isEmpty() && ev.ai.isEmpty() && ev.maybe.isEmpty()
    Focusable(modifier = Modifier.width(272.dp).height(138.dp), onClick = onClick, onMenu = onMenu, shape = Rnd(18.dp), focusRequester = fr, onFocus = onFocus,
        normal = C.Card, focused = C.CardFocus, zoom = 1.03f) { f ->
        Column(Modifier.fillMaxSize().then(if (none && !f) Modifier.alpha(0.6f) else Modifier).padding(horizontal = 14.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(Sports.sportColor(ev.sport), 6.dp)
                T(tr(ev.league), 12.sp, C.Muted, modifier = Modifier.weight(1f).padding(start = 6.dp))
                StateBadge(ev)
            }
            if (ev.home != null && ev.away != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Crest(ev.home, 34.dp)
                    T(if (ev.state != "pre") "${ev.home.score ?: 0} : ${ev.away.score ?: 0}" else "vs", if (ev.state != "pre") 18.sp else 14.sp, if (ev.state != "pre") C.Text else C.Muted,
                        FontWeight.Bold, modifier = Modifier.weight(1f), align = androidx.compose.ui.text.style.TextAlign.Center)
                    Crest(ev.away, 34.dp)
                }
                T(ev.home.name + " – " + ev.away.name, 14.sp, C.Text, FontWeight.Bold)
            } else T(ev.title, 15.sp, C.Text, FontWeight.SemiBold, maxLines = 2)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (ev.channels.isNotEmpty()) {
                    Ico(R.drawable.lc_tv, 14.dp, C.Green)
                    T(ev.channels.first().name, 12.sp, C.Green, FontWeight.Medium, modifier = Modifier.weight(1f, fill = false))
                    T("· " + tr("u tvojoj listi") + if (ev.channels.size > 1) " +${ev.channels.size - 1}" else "", 12.sp, C.Green)
                } else if (ev.ai.isNotEmpty()) {
                    Ico(R.drawable.lc_sparkles, 14.dp, AiColor)
                    T("AI: " + ev.ai.first().name, 12.sp, AiColor, modifier = Modifier.weight(1f, fill = false))
                    if (ev.ai.size > 1) T("+${ev.ai.size - 1}", 12.sp, AiColor)
                } else if (ev.maybe.isNotEmpty()) {
                    Ico(R.drawable.lc_tv, 14.dp, C.Amber)
                    T(trf("Mogući prenos: %s", ev.maybe.first().name), 12.sp, C.Amber, modifier = Modifier.weight(1f, fill = false))
                    if (ev.maybe.size > 1) T("+${ev.maybe.size - 1}", 12.sp, C.Amber)
                } else T("Prenos nije pronađen u tvojoj listi", 12.sp, C.Muted, modifier = Modifier.weight(1f))
                if (ev.state == "pre") { Spacer(Modifier.weight(0.01f)); Countdown(ev.start) }
            }
        }
    }
}

/** "za 3:12:40" until the match starts */
@Composable
private fun Countdown(start: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(start) { while (now < start) { delay(1000); now = System.currentTimeMillis() } }
    val left = ((start - now) / 1000).coerceAtLeast(0)
    if (left <= 0) return
    T(if (left >= 86_400) trf("za %s d", left / 86_400) else trf("za %s", "%d:%02d:%02d".format(left / 3600, (left % 3600) / 60, left % 60)), 12.sp, C.Amber, FontWeight.SemiBold)
}

@Composable
private fun TeamLine(t: Team, score: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Crest(t, 22.dp)
        T(t.name, 15.sp, C.Text, FontWeight.Medium, modifier = Modifier.weight(1f))
        if (score != null) T(score, 16.sp, C.Text, FontWeight.Bold)
    }
}
