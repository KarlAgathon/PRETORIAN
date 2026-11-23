package com.pretorian.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* Shared pieces of the redesigned movie / series views and the player (spec 03 + 04):
 * exactly four icons — Subtitles, Audio, Playback, Picture — each opening one floating menu UPWARD. */

const val MI_SUBS = "subs"
const val MI_AUDIO = "audio"
const val MI_PLAYBACK = "playback"
const val MI_PICTURE = "picture"

/** Picture formats of films and series (approved: exactly these five), mapped to the player's resize modes. */
val PICTURE_MODES = listOf(0 to "Zadano", 3 to "Prilagodi", 5 to "Popuni", 4 to "Rastegni", 1 to "4:3")
val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
val SLEEP_MIN = listOf(0, 15, 30, 60, 90)

fun speedLabel(s: Float): String = (if (s == 1f) "1" else s.toString().trimEnd('0').trimEnd('.')) + "×"  // i18n-ignore

/** One of the four icons: a glass circle with the label under it; [menu] is its floating menu (shown while [open]). */
@Composable
fun MediaIcon(icon: Int, label: String, open: Boolean, fr: FocusRequester? = null, active: Boolean = false, onClick: () -> Unit, menu: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Focusable(onClick = onClick, focusRequester = fr, shape = androidx.compose.foundation.shape.CircleShape, normal = Color(0x8C14181C),
            focused = Dawn.pill, borderColor = Color(0x29FFFFFF), zoom = 1.06f, modifier = Modifier.size(54.dp)) { _ ->
            Box(Modifier.align(Alignment.Center)) { Ico(icon, 24.dp, Color.White) }
            if (active) Box(Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp).size(8.dp).clip(Pill).background(Color.White))
        }
        T(label, 12.sp, Silver.TextSoft)
        if (open) CompositionLocalProvider(LocalUpward provides true) { menu() }
    }
}

/** A small heading inside a floating menu. */
@Composable
fun MenuHead(t: String) {
    T(t, 12.sp, Silver.TextSoft, FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
}

/** A row of a floating menu: the focus is the pill, the active choice has a ✓. OK applies at once (no Save / Cancel). */
@Composable
fun MenuRow(label: String, on: Boolean = false, sub: String? = null, fr: FocusRequester? = null, onClick: () -> Unit) {
    Focusable(onClick = onClick, focusRequester = fr, shape = Rnd(12.dp), normal = Color.Transparent, focused = Dawn.pill, zoom = 1f,
        modifier = Modifier.fillMaxWidth().height(if (sub != null) 54.dp else 44.dp)) { _ ->
        Row(Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                T(label, 15.sp, Color.White, if (on) FontWeight.SemiBold else FontWeight.Normal)
                if (sub != null) T(sub, 12.sp, Silver.TextSoft)
            }
            if (on) Ico(R.drawable.lc_check, 18.dp, Color.White)
        }
    }
}

/** Playback menu of films and series: Start over · Speed · (series: Auto next, Skip intro) · Sleep timer. */
@Composable
fun PlaybackMenu(
    speed: Float, onSpeed: (Float) -> Unit, onStartOver: (() -> Unit)?, startOverLabel: String = "Pusti od početka",
    series: Boolean = false, onDismiss: () -> Unit,
) {
    Popup("Reprodukcija", onDismiss) {
        if (onStartOver != null) MenuRow(startOverLabel) { onStartOver() }
        MenuHead(tr("Brzina"))
        SPEEDS.forEach { s -> MenuRow(speedLabel(s), on = speed == s) { onSpeed(s) } }
        if (series) {
            MenuHead(tr("Serije"))
            val auto = Prefs.bool("next_ep", true)
            MenuRow("Automatski sljedeća epizoda", on = auto, sub = tr(if (auto) "Uključeno" else "Isključeno")) { Prefs.put("next_ep", !auto); onDismiss() }
            val skip = Prefs.long("skip_intro", 0).toInt() != 2
            MenuRow("Preskoči uvod", on = skip, sub = tr(if (skip) "Uključeno" else "Isključeno")) { Prefs.put("skip_intro", if (skip) 2L else 0L); onDismiss() }
        }
        MenuHead(tr("Tajmer za spavanje"))
        val left = if (Sleep.until > 0) ((Sleep.until - System.currentTimeMillis()) / 60_000 + 1).toInt() else 0
        SLEEP_MIN.forEach { m ->
            MenuRow(if (m == 0) tr("Isključen") else trf("%s min", m), on = if (m == 0) Sleep.until <= 0 else false,
                sub = if (m != 0 && left > 0 && left <= m && left > (SLEEP_MIN.getOrNull(SLEEP_MIN.indexOf(m) - 1) ?: 0)) trf("još %s min", left) else null) {
                Sleep.until = if (m == 0) 0 else System.currentTimeMillis() + m * 60_000L; onDismiss()
            }
        }
    }
}

/** Picture menu: exactly Default, Fit, Fill, Stretch, 4:3. */
@Composable
fun PictureMenu(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    Popup("Slika", onDismiss) {
        PICTURE_MODES.forEach { (i, label) -> MenuRow(tr(label), on = current == i) { onPick(i) } }
    }
}

/** Short subtitle language name for the online versions: "Eng ver.1", "Deu ver.1", "Bos ver.1" … */
fun subShort(code: String): String = when (code.lowercase()) {
    "en" -> "Eng"; "de" -> "Deu"; "bs" -> "Bos"; "hr" -> "Hrv"; "sr" -> "Srp"; "sl" -> "Slv"; "mk" -> "Mkd"; "bg" -> "Bul"
    "ro" -> "Ron"; "tr" -> "Tur"; "it" -> "Ita"; "ar" -> "Ara"; "ru" -> "Rus"; "fr" -> "Fra"; "es" -> "Spa"; "nl" -> "Nld"; "pl" -> "Pol"; "hu" -> "Hun"
    else -> code.replaceFirstChar { it.uppercase() }
}  // i18n-ignore
