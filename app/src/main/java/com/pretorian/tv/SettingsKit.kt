package com.pretorian.tv

import androidx.activity.compose.BackHandler

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay


/** Keeps the focus on the row you worked on: after a choice, a switch or a closed list the page is drawn again
 *  and the same row takes the focus back (it never jumps up to the menu). */
/** settings rows are solid, so the background picture never shows through them */
val SolidRow: Color get() = androidx.compose.ui.graphics.Color(0xFF1B1B1E).let { base -> C.Rail.compositeOverSafe(base) }
private fun Color.compositeOverSafe(b: Color): Color { val a = alpha; return Color(red * a + b.red * (1 - a), green * a + b.green * (1 - a), blue * a + b.blue * (1 - a), 1f) }

object SetFocus { var last: String? = null; var pending = false; var tick by mutableIntStateOf(0) }

@Composable
fun setFocusFr(id: String, given: FocusRequester? = null): FocusRequester {
    val fr = given ?: remember { FocusRequester() }
    LaunchedEffect(Unit) { if (SetFocus.pending && SetFocus.last == id) { SetFocus.pending = false; fr.tryFocus() } }
    LaunchedEffect(SetFocus.tick) { if (SetFocus.tick > 0 && SetFocus.last == id) { delay(60); fr.tryFocus() } }
    return fr
}

@Composable
fun SetPage(title: String, sub: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        T(title, 23.sp, C.Text, FontWeight.Bold)
        if (sub != null) T(sub, 13.sp, C.Muted, maxLines = 2)
        content()
    }
}

@Composable
fun SetSection(t: String) {
    androidx.compose.material3.Text(tr(t).uppercase(), style = androidx.compose.ui.text.TextStyle(fontFamily = Inter, fontSize = 12.sp, color = C.Muted,
        fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp), modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
}

/** One settings row: title and a short line under it, the value on the right in a pill. */
@Composable
fun SetRow(title: String, sub: String? = null, value: String? = null, arrow: String = "›", valueColor: Color? = null, new: Boolean = false,
           focusRequester: FocusRequester? = null, onLong: (() -> Unit)? = null, onClick: () -> Unit) {
    val id = "row:$title"
    val fr = setFocusFr(id, focusRequester)
    Focusable(modifier = Modifier.fillMaxWidth().heightIn(min = if (sub != null) 56.dp else 48.dp).border(1.dp, C.Line, Rnd(12.dp)),
        onClick = { SetFocus.last = id; SetFocus.pending = true; onClick() }, onMenu = onLong, onFocus = { SetFocus.last = id },
        focusRequester = fr, shape = Rnd(12.dp), normal = SolidRow, focused = C.Red, zoom = 1.01f) { f ->
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    T(title, 15.sp, if (f) C.OnRed else C.Text, FontWeight.SemiBold, maxLines = 2)
                    // no "NEW" badges in Settings (approved: calm rows)
                }
                if (sub != null) T(sub, 12.sp, if (f) C.OnRed.copy(alpha = 0.65f) else C.Muted, maxLines = 2)
            }
            if (value != null || arrow.isNotEmpty()) Row(Modifier.clip(Rnd(9.dp)).background(if (f) Color(0x14000000) else C.Card)
                .border(1.dp, if (f) Color(0x26000000) else C.Line, Rnd(9.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                if (value != null) T(value, 14.sp, if (f) C.OnRed else (valueColor ?: C.Text), FontWeight.SemiBold, maxLines = 1, modifier = Modifier.widthIn(max = 170.dp))
                if (arrow.isNotEmpty()) T(arrow, 10.sp, if (f) C.OnRed else C.Muted)
            }
        }
    }
}

/** Drop-down: OK opens the list, OK on a choice picks it and closes. */
@Composable
fun SetDrop(title: String, sub: String? = null, options: List<String>, selected: Int, new: Boolean = false, note: String? = null,
            valueColor: Color? = null, disabled: Set<Int> = emptySet(), onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    SetRow(title, sub, tr(options.getOrElse(selected) { options.first() }), "▼", valueColor, new) { open = true }
    if (open) Popup(title, { open = false; SetFocus.tick++ }) {
        if (note != null) T(note, 12.sp, C.Soft, maxLines = 4)
        options.forEachIndexed { i, o ->
            // a choice that is not possible here (e.g. an app that is not installed) stays grey and does nothing
            if (i in disabled) OptionRow(tr(o) + " · " + tr("nije instalirano"), null, color = C.Muted) { }
            else OptionRow(o, if (i == selected) R.drawable.lc_check else null, color = if (i == selected) C.Green else null) { open = false; onSelect(i); SetFocus.tick++ }
        }
    }
}

/** On/off in lists: the same pill switch as everywhere (green = on, grey = off), only smaller. */
@Composable
fun Lamp(on: Boolean) = Toggle(on, height = 16.dp)

/** Several at once: lamps in a list; "Sve" first switches everything. */
@Composable
fun SetMulti(title: String, sub: String? = null, options: List<String>, on: Set<Int>, new: Boolean = false, all: Boolean = true,
             note: String? = null, none: Boolean = false, onChange: (Set<Int>) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var cur by remember(open) { mutableStateOf(on) }
    // short value, never cut: one name, or only how many are chosen
    val value = when {
        on.size == options.size && all -> tr("Sve") + " (${options.size})"
        on.isEmpty() -> tr("Ništa")
        on.size == 1 -> tr(options[on.first()])
        else -> trf("%s izabrano", on.size)
    }
    SetRow(title, sub, value, "▼", null, new) { open = true }
    if (open) Popup(title, { open = false; onChange(cur); SetFocus.tick++ }, width = 620.dp) {
        if (note != null) T(note, 12.sp, C.Soft, maxLines = 3)
        if (all) LampRow("Sve", cur.size == options.size) { cur = if (cur.size == options.size) emptySet() else options.indices.toSet() }
        if (none) LampRow("Nijedan", cur.isEmpty()) { cur = emptySet() }
        options.forEachIndexed { i, o -> LampRow(o, i in cur) { cur = if (i in cur) cur - i else cur + i } }
        Button("Gotovo", R.drawable.lc_check, primary = true) { open = false; onChange(cur); SetFocus.tick++ }
    }
}

@Composable
private fun LampRow(label: String, on: Boolean, onClick: () -> Unit) {
    Focusable(modifier = Modifier.fillMaxWidth().height(38.dp), onClick = onClick, shape = Rnd(10.dp), normal = Color.Transparent, focused = C.CardHi, zoom = 1f) { _ ->
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Lamp(on); T(label, 14.sp, if (on) C.Text else C.Muted, FontWeight.Medium, maxLines = 1)
        }
    }
}

/** Switch row: OK turns it on or off. */
@Composable
fun SetSwitch(title: String, sub: String? = null, on: Boolean, new: Boolean = false, onLong: (() -> Unit)? = null, onChange: (Boolean) -> Unit) {
    val id = "sw:$title"
    val fr = setFocusFr(id)
    var cur by remember(on) { mutableStateOf(on) }
    Focusable(modifier = Modifier.fillMaxWidth().heightIn(min = if (sub != null) 54.dp else 46.dp).border(1.dp, C.Line, Rnd(12.dp)),
        onClick = { SetFocus.last = id; cur = !cur; onChange(cur) }, onMenu = onLong, onFocus = { SetFocus.last = id }, focusRequester = fr,
        shape = Rnd(12.dp), normal = SolidRow, focused = C.Red, zoom = 1.01f) { f ->
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    T(title, 15.sp, if (f) C.OnRed else C.Text, FontWeight.SemiBold, maxLines = 2)
                }
                if (sub != null) T(sub, 12.sp, if (f) C.OnRed.copy(alpha = 0.65f) else C.Muted, maxLines = 2)
            }
            Toggle(cur, f, height = if (sub != null) 24.dp else 22.dp)
        }
    }
}

/** Slider: the row shows the value; OK opens a small window where ◀ ▶ change it, OK or Back closes it.
 *  (◀ ▶ on the row itself move between rows, like everywhere else.) */
/** which slider popup is open: kept outside the row, so a page that redraws itself on every change (key(rev)) does not close it */
object SliderOpen { var id by mutableStateOf<String?>(null) }

@Composable
fun SetSlider(title: String, sub: String?, value: Int, min: Int, max: Int, step: Int, fmt: (Int) -> String = { "$it" }, onChange: (Int) -> Unit) {
    val open = SliderOpen.id == "sl:$title"
    var cur by remember(value) { mutableIntStateOf(value) }
    val id = "sl:$title"
    val fr = setFocusFr(id)
    @Composable
    fun Bar(v: Int, f: Boolean, h: androidx.compose.ui.unit.Dp) {
        Box(Modifier.fillMaxWidth().height(h).clip(Pill).background(if (f) Color(0x33000000) else C.Line)) {
            Box(Modifier.fillMaxWidth(((v - min).toFloat() / (max - min).coerceAtLeast(1)).coerceIn(0.02f, 1f)).fillMaxHeight().background(Silver.TextSoft))
        }
    }
    Focusable(modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).border(1.dp, C.Line, Rnd(12.dp)),
        onClick = { SetFocus.last = id; SliderOpen.id = "sl:$title" }, onFocus = { SetFocus.last = id }, focusRequester = fr,
        shape = Rnd(12.dp), normal = SolidRow, focused = C.Red, zoom = 1.01f) { f ->
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(title, 15.sp, if (f) C.OnRed else C.Text, FontWeight.SemiBold, modifier = Modifier.weight(1f))
                T(fmt(cur), 15.sp, if (f) C.OnRed else Silver.TextSoft, FontWeight.Bold)
            }
            Bar(cur, f, 4.dp)
            if (sub != null) T(sub, 11.sp, if (f) C.OnRed.copy(alpha = 0.65f) else C.Muted, maxLines = 1)
        }
    }
    if (open) Popup(title, { SliderOpen.id = null; SetFocus.tick++ }, width = 520.dp) {
        val pfr = remember { FocusRequester() }
        Focusable(onClick = { SliderOpen.id = null; SetFocus.tick++ }, focusRequester = pfr, shape = Rnd(14.dp), normal = C.Card, focused = C.CardHi, zoom = 1f,
            modifier = Modifier.fillMaxWidth().height(96.dp).onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { cur = (cur - step).coerceAtLeast(min); onChange(cur); true }
                    Key.DirectionRight -> { cur = (cur + step).coerceAtMost(max); onChange(cur); true }
                    else -> false
                }
            }) { _ ->
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T("◀", 18.sp, C.Text); Spacer(Modifier.weight(1f))
                    T(fmt(cur), 26.sp, C.Text, FontWeight.ExtraBold)
                    Spacer(Modifier.weight(1f)); T("▶", 18.sp, C.Text)
                }
                Bar(cur, false, 8.dp)
            }
        }
        T("◀ ▶ mijenja · OK ili Nazad zatvara", 12.sp, C.Muted)
        LaunchedEffect(Unit) { pfr.tryFocus() }
    }
}



internal fun csv(k: String, def: String) = Prefs.str(k, def).split(',').filter { it.isNotBlank() }
private fun onOff(b: Boolean) = if (b) 0 else 1
