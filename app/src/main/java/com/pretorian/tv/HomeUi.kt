package com.pretorian.tv

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** one big tile of the home screen */
data class NeonItem(val label: String, val icon: Int, val accent: Color, val play: Boolean, val badge: Int, val onClick: () -> Unit)

/**
 * The room behind the home screen: warm spot lights from the ceiling and a glowing shelf line,
 * on the theme's own background (dark stays dark, light stays light).
 */
@Composable
fun HomeBackdrop() {
    val light = C.IsLight
    val warm = Color(0xFFFFB86B)
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        // ceiling spots
        for (i in 0 until 4) {
            val x = w * (0.2f + 0.2f * i)
            drawCircle(Brush.radialGradient(listOf(warm.copy(alpha = if (light) 0.22f else 0.16f), Color.Transparent),
                center = Offset(x, 0f), radius = h * 0.55f), radius = h * 0.55f, center = Offset(x, 0f))
        }
        // walls light up at the sides
        drawRect(Brush.horizontalGradient(listOf(warm.copy(alpha = if (light) 0.10f else 0.07f), Color.Transparent, Color.Transparent, warm.copy(alpha = if (light) 0.10f else 0.07f))))
        // the shelf under the tiles
        val y = h * 0.74f
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, warm.copy(alpha = if (light) 0.18f else 0.12f), Color.Transparent), startY = y - 30f, endY = y + 50f),
            topLeft = Offset(0f, y - 30f), size = androidx.compose.ui.geometry.Size(w, 80f))
        drawLine(warm.copy(alpha = if (light) 0.45f else 0.35f), Offset(w * 0.04f, y), Offset(w * 0.96f, y), strokeWidth = 2f)
    }
}

/**
 * The tiles: all the same size, as big as fit in one row; if the screen is too narrow the row scrolls,
 * nothing is ever cut. Each tile is dark glass with a neon edge and a light wave in its colour.
 */
@Composable
fun NeonRow(items: List<NeonItem>, first: FocusRequester, modifier: Modifier = Modifier, maxTile: Dp = 250.dp) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val gap = 26.dp
        val side = 40.dp
        val fit = (maxWidth - side * 2 - gap * (items.size - 1)) / items.size
        val w = fit.coerceIn(150.dp, maxTile)
        val h = (w * 0.9f).coerceAtMost(maxHeight - 40.dp).coerceAtLeast(130.dp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
            contentPadding = PaddingValues(horizontal = side, vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            items(items.size) { i -> NeonTile(items[i], w, h, if (i == 0) first else null) }
        }
    }
}

@Composable
fun NeonTile(it: NeonItem, w: Dp, h: Dp, fr: FocusRequester?) {
    val light = C.IsLight
    val shape = Rnd(26.dp)
    Box {
        Focusable(modifier = Modifier.size(w, h), onClick = it.onClick, focusRequester = fr, shape = shape,
            normal = Color.Transparent, focused = Color.Transparent, zoom = 1.07f) { f ->
            Box(Modifier.fillMaxSize()
                .then(if (f) Modifier.shadow(28.dp, shape, ambientColor = it.accent, spotColor = it.accent) else Modifier)
                .clip(shape)
                .background(if (light) Color.White.copy(alpha = if (f) 0.85f else 0.62f) else Color(0xFF0D1324).copy(alpha = if (f) 0.92f else 0.78f))
                .background(Brush.linearGradient(listOf(it.accent.copy(alpha = if (f) 0.45f else 0.22f), Color.Transparent),
                    start = Offset(0f, 0f), end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)))
                .border(if (f) 3.dp else 1.5.dp, it.accent.copy(alpha = if (f) 1f else 0.45f), shape)) {
                // light wave at the bottom
                Canvas(Modifier.fillMaxSize()) {
                    val ww = size.width; val hh = size.height
                    fun wave(lift: Float, alpha: Float, width: Float) {
                        val p = Path().apply {
                            moveTo(0f, hh * (0.93f - lift))
                            cubicTo(ww * 0.35f, hh * (1.0f - lift), ww * 0.6f, hh * (0.72f - lift), ww, hh * (0.80f - lift))
                        }
                        drawPath(p, Brush.horizontalGradient(listOf(Color.Transparent, it.accent.copy(alpha = alpha), it.accent.copy(alpha = alpha * 0.6f))), style = Stroke(width))
                    }
                    wave(0f, if (f) 1f else 0.7f, 5f)
                    wave(0.05f, if (f) 0.6f else 0.35f, 2.5f)
                }
                Column(Modifier.align(Alignment.Center).padding(bottom = h * 0.06f), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(h * 0.06f)) {
                    val ic = w * 0.36f
                    val tint = if (light) it.accent else Color.White
                    Box(contentAlignment = Alignment.Center) {
                        Ico(it.icon, ic, tint)
                        if (it.play) Box(Modifier.padding(top = ic * 0.06f)) { Ico(R.drawable.lc_play, ic * 0.3f, tint) }
                    }
                    T(tr(it.label).uppercase(), (w.value * 0.1f).sp, if (light) Color(0xFF111827) else Color.White, FontWeight.Bold, maxLines = 1)
                }
            }
        }
        if (it.badge > 0) Box(Modifier.align(Alignment.TopEnd).padding(10.dp)) { Bell(it.badge) }
    }
}

/** Profiles · Add playlist · Settings (and the bell), in one rounded bar */
@Composable
fun HomeTopPill(nav: Nav, onBell: (() -> Unit)?) {
    Row(Modifier.clip(Pill).background(C.Card.copy(alpha = 0.85f)).border(1.dp, C.Line, Pill).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        PillButton("Profili", R.drawable.lc_user) { Profiles.panel = true }
        PillButton("Dodaj", R.drawable.lc_plus) { nav.push(Screen.AddPlaylist) }
        PillButton("Postavke", R.drawable.lc_settings, if (Updater.available != null) 1 else 0) { nav.root(Screen.Settings) }
        if (onBell != null) PillButton(null, R.drawable.lc_bell, Notes.unread()) { onBell() }
    }
}

@Composable
private fun PillButton(label: String?, icon: Int, badge: Int = 0, onClick: () -> Unit) {
    Box {
        Focusable(onClick = onClick, shape = Pill, normal = Color.Transparent, focused = C.CardHi, zoom = 1.05f, modifier = Modifier.height(40.dp)) { _ ->
            Row(Modifier.align(Alignment.Center).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Ico(icon, 18.dp, if (badge > 0 && label == null) C.Amber else C.Text)
                if (label != null) T(label, 15.sp, C.Text, FontWeight.Medium, maxLines = 1)
            }
        }
        if (badge > 0) Box(Modifier.align(Alignment.TopEnd)) { Bell(badge) }
    }
}

/** bottom bar: connected playlist · expiry · day, date and time */
@Composable
fun HomeStatusBar(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val pls = remember { Store.actives(ctx) }
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(15_000) } }
    Row(modifier.fillMaxWidth().clip(Pill).background(C.Card.copy(alpha = 0.85f)).border(1.dp, C.Line, Pill).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Ico(R.drawable.lc_wifi, 18.dp, C.Green)
            T(tr("Spojeno:") + " " + (pls.joinToString(", ") { it.name }.ifBlank { "—" }), 14.sp, C.Text, maxLines = 1)
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (pls.isNotEmpty()) C.Green else C.Muted))
        }
        Box(Modifier.width(1.dp).height(22.dp).background(C.Line))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Ico(R.drawable.lc_calendar, 18.dp, C.Soft)
            val e = pls.firstNotNullOfOrNull { expiryText(it.exp) }
            T(tr("Ističe:") + " " + (e?.first ?: "—"), 14.sp, if (e != null && e.second <= 7) C.Amber else C.Text, maxLines = 1)
        }
        Box(Modifier.width(1.dp).height(22.dp).background(C.Line))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            Ico(R.drawable.lc_clock, 18.dp, C.Soft)
            val stamp = SimpleDateFormat("EEEE, d. MMMM yyyy · HH:mm", Locale(Look.lang)).format(now)  // i18n-ignore
            T(stamp, 14.sp, C.Text, maxLines = 1)
        }
    }
}

/** logo on the left of the top row */
@Composable
fun HomeLogo() {
    Image(painterResource(R.drawable.logo_full), null, Modifier.height(46.dp))
}

/** the four tiles of the home screens */
fun homeTiles(nav: Nav, push: Boolean): List<NeonItem> {
    fun go(s: Screen) { if (push) nav.push(s) else nav.root(s) }
    return listOf(
        NeonItem("TV", R.drawable.lc_tv, Color(0xFF3B82F6), true, Notes.unread("reminder")) { go(Screen.Tv) },
        NeonItem("Filmovi", R.drawable.lc_film, Color(0xFFD946EF), false, NewContent.count("vod")) { go(Screen.Movies) },
        NeonItem("Serije", R.drawable.lc_monitor, Color(0xFF8B5CF6), true, NewContent.count("series")) { go(Screen.Series) },
        NeonItem("Sport", R.drawable.lc_ball, Color(0xFF22C55E), false, 0) { go(Screen.Sports) },
    )
}
