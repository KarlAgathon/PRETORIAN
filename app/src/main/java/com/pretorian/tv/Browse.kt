package com.pretorian.tv

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One row of a two-column browser (Movies / Series categories, Catch-Up channels). */
class BrowseRow(
    val id: String, val title: String, val icon: Int? = null, val ch: Channel? = null,
    val count: String? = null, val badge: String? = null, val gapBefore: Boolean = false,
    val onLong: (() -> Unit)? = null, val direct: Boolean = false, val open: () -> Unit,
)

/**
 * The shared two-column browser (spec 13, reused by Catch-Up in spec 15):
 * left individual rows, right the information of the focused row and one button.
 * ▲▼ only change the right side; OK on a row moves to the button, a second OK there opens (that is the double OK);
 * ◀ or Back on the button return to the same row. Opening is guarded against repeats.
 */
@Composable
fun TwoColBrowser(
    rows: List<BrowseRow>, mem: HashMap<String, Int>, key: String, button: String, buttonIcon: Int,
    panel: @Composable ColumnScope.(BrowseRow) -> Unit,
    art: Int? = null,
) {
    if (rows.isEmpty()) return
    val start = (mem[key] ?: 0).coerceIn(0, rows.size - 1)
    var sel by remember(key) { mutableStateOf(start) }
    val cur = rows[sel.coerceIn(0, rows.size - 1)]
    val st = rememberLazyListState(initialFirstVisibleItemIndex = (start - 3).coerceAtLeast(0))
    val rowFrs = remember(rows.size) { List(rows.size) { FocusRequester() } }
    val btnFr = remember { FocusRequester() }
    var lastOpen by remember { mutableStateOf(0L) }
    fun openNow(r: BrowseRow) {
        val now = System.currentTimeMillis()
        if (now - lastOpen > 1200) { lastOpen = now; mem[key] = rows.indexOf(r).coerceAtLeast(0); r.open() }
    }
    Row(Modifier.fillMaxSize().padding(top = 8.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        LazyColumn(Modifier.weight(0.46f).fillMaxHeight(), state = st, verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp, start = 4.dp, end = 4.dp)) {
            itemsIndexed(rows, key = { _, r -> r.id }) { i, r ->
                if (r.gapBefore) Spacer(Modifier.height(14.dp))
                Focusable(onClick = { sel = i; mem[key] = i; if (r.direct) r.open() else runCatching { btnFr.requestFocus() } }, onMenu = r.onLong,
                    onFocus = { sel = i; mem[key] = i }, focusRequester = rowFrs[i],
                    shape = Rnd(14.dp), normal = Color(0x8C171B22), focused = Color(0x40FFFFFF),
                    borderColor = Color(0x1FFFFFFF), focusBorderColor = Color(0xD9FFFFFF), zoom = 1f,
                    modifier = Modifier.fillMaxWidth().height(58.dp)) { f ->
                    Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        val fg = if (f) Color.White else Color(0xFFD9DDE3)
                        if (r.ch != null) ChannelLogo(r.ch.logo, r.ch.name) else if (r.icon != null) Ico(r.icon, 20.dp, fg)
                        T(r.title, 17.sp, fg, if (f) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, modifier = Modifier.weight(1f))
                        r.badge?.let { T(it, 12.sp, Color(0xFFB8C0CC), FontWeight.SemiBold) }
                        r.count?.let { T(it, 14.sp, if (f) Color.White else Color(0xFF9AA3AF)) }
                        Ico(R.drawable.lc_chevron_right, 16.dp, if (f) Color.White else Color(0xFF7D8590))
                    }
                }
            }
        }
        Column(Modifier.weight(0.54f).fillMaxHeight().padding(top = 18.dp, end = 8.dp)) {
            panel(cur)
            Spacer(Modifier.height(22.dp))
            Box(Modifier.focusProperties { left = rowFrs[sel.coerceIn(0, rows.size - 1)] }.onPreviewKeyEvent { e ->
                val back = e.key == Key.Back || e.key == Key.Escape
                if (back && e.type == KeyEventType.KeyUp) { runCatching { rowFrs[sel.coerceIn(0, rows.size - 1)].requestFocus() }; true } else back
            }) {
                HeroBtn(button, buttonIcon, primary = false, fr = btnFr) { openNow(cur) }
            }
            if (art != null) Box(Modifier.weight(1f).fillMaxWidth().padding(top = 18.dp), contentAlignment = Alignment.BottomEnd) {
                Image(painterResource(art), null, Modifier.fillMaxWidth(0.82f).fillMaxHeight(), contentScale = ContentScale.Fit, alignment = Alignment.BottomEnd)
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { st.scrollToItem((start - 3).coerceAtLeast(0)) }; rowFrs[start].tryFocus() }
}

/** The usual right-hand information block: small label, big name, a fact line, a short description. */
@Composable
fun BrowsePanelText(label: String, name: String, facts: String?, desc: String?) {
    T(label, 13.sp, Color(0xFF9AA3AF), FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    T(name, 34.sp, Color.White, FontWeight.Bold, maxLines = 2)
    if (!facts.isNullOrBlank()) { Spacer(Modifier.height(8.dp)); T(facts, 16.sp, Color(0xFFC9CED6)) }
    if (!desc.isNullOrBlank()) { Spacer(Modifier.height(12.dp)); T(desc, 16.sp, Color(0xFFB0B8C4), maxLines = 3) }
}
