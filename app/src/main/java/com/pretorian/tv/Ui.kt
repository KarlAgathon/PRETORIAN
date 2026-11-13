@file:OptIn(ExperimentalFoundationApi::class)

package com.pretorian.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_medium, FontWeight.SemiBold),
    Font(R.font.inter_medium, FontWeight.Bold),
)

/** Text that never runs outside its box: it ends with "…" when too long. */
@Composable
fun T(
    text: String,
    size: TextUnit = 14.sp,
    color: Color = C.Text,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    align: TextAlign? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(fontFamily = Inter, fontSize = size, color = color, fontWeight = weight, lineHeight = size * 1.35f),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = align,
    )
}

/** Remote-friendly focusable box: accent frame + slight zoom when focused. */
@Composable
fun Focusable(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(14.dp),
    normal: Color = Color.Transparent,
    focused: Color? = null,
    zoom: Float = 1.03f,
    focusRequester: FocusRequester? = null,
    onFocus: (() -> Unit)? = null,
    onMenu: (() -> Unit)? = null,
    content: @Composable BoxScope.(Boolean) -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val isFocused by src.collectIsFocusedAsState()
    LaunchedEffect(isFocused) { if (isFocused) onFocus?.invoke() }
    val scale by animateFloatAsState(if (isFocused) zoom else 1f, label = "zoom")
    var m = modifier
    if (focusRequester != null) m = m.focusRequester(focusRequester)
    if (onMenu != null) m = m.onPreviewKeyEvent { e ->
        if (e.type == KeyEventType.KeyUp && e.key == Key.Menu) { onMenu(); true } else false
    }
    Box(
        m.graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(if (isFocused) (focused ?: C.CardFocus) else normal)
            .border(2.dp, if (isFocused) C.Red else Color.Transparent, shape)
            .combinedClickable(interactionSource = src, indication = null, onClick = onClick, onLongClick = onLongClick ?: onMenu),
        content = { content(isFocused) },
    )
}

@Composable
fun Ico(id: Int, size: Dp = 22.dp, tint: Color = C.Text) {
    Icon(painterResource(id), contentDescription = null, modifier = Modifier.size(size), tint = tint)
}

@Composable
fun Chip(text: String, bg: Color = C.Card, fg: Color = C.Text, border: Color? = null) {
    var m = Modifier.clip(RoundedCornerShape(50)).background(bg)
    if (border != null) m = m.border(1.5.dp, border, RoundedCornerShape(50))
    Box(m.padding(horizontal = 11.dp, vertical = 5.dp)) { T(text, 13.sp, fg, FontWeight.Medium) }
}

@Composable
fun Button(text: String, icon: Int? = null, primary: Boolean = false, modifier: Modifier = Modifier,
           focusRequester: FocusRequester? = null, outline: Color? = null, onClick: () -> Unit) {
    Focusable(
        modifier = modifier.height(48.dp).then(if (outline != null) Modifier.border(2.dp, outline, RoundedCornerShape(14.dp)) else Modifier),
        onClick = onClick,
        focusRequester = focusRequester,
        normal = if (primary) C.Red else C.Card,
        focused = if (primary) C.Red else C.CardHi,
    ) { _ ->
        Row(Modifier.align(Alignment.Center).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            val fg = if (primary) C.OnRed else C.Text
            if (icon != null) Ico(icon, 18.dp, fg)
            T(text, 15.sp, fg, FontWeight.SemiBold)
        }
    }
}

sealed class Load<out V> {
    object Loading : Load<Nothing>()
    data class Ok<V>(val value: V) : Load<V>()
    data class Err(val message: String) : Load<Nothing>()
}

@Composable
fun <V> load(vararg keys: Any?, block: suspend () -> V): State<Load<V>> =
    produceState<Load<V>>(Load.Loading, *keys) {
        value = Load.Loading
        value = try { Load.Ok(block()) } catch (e: Exception) { Load.Err(e.message ?: e.javaClass.simpleName) }
    }

@Composable
fun Loading(text: String = "Učitavam…") {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = C.Red, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
        if (text.isNotEmpty()) T(text, 15.sp, C.Muted, modifier = Modifier.padding(top = 14.dp))
    }
}

@Composable
fun Message(title: String, text: String? = null) {
    Column(Modifier.fillMaxSize().padding(40.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        T(title, 20.sp, C.Text, FontWeight.SemiBold, align = TextAlign.Center, maxLines = 2)
        if (text != null) T(text, 15.sp, C.Muted, modifier = Modifier.padding(top = 8.dp).fillMaxWidth(), align = TextAlign.Center, maxLines = 4)
    }
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

/* ------------------------------------------------------------------ dialogs */

/** A centred card over the screen; Back closes it. */
@Composable
fun Popup(title: String, onDismiss: () -> Unit, width: Dp = 560.dp, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.widthIn(max = width).fillMaxWidth(0.9f).clip(RoundedCornerShape(26.dp))
                .background(if (C.IsLight) Color.White else Color(0xFF151A23))
                .border(1.dp, C.Line, RoundedCornerShape(26.dp))
                .heightIn(max = 640.dp)
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            T(title, 22.sp, C.Text, FontWeight.Bold, maxLines = 2)
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}

/** One choice inside a Popup or a settings page. */
@Composable
fun OptionRow(label: String, icon: Int? = null, sub: String? = null, value: String? = null, selected: Boolean = false,
              focusRequester: FocusRequester? = null, color: Color? = null, onClick: () -> Unit) {
    Focusable(
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
        onClick = onClick,
        focusRequester = focusRequester,
        normal = if (selected) C.CardFocus else C.Card,
        zoom = 1.01f,
    ) { _ ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (icon != null) Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(C.Card), contentAlignment = Alignment.Center) {
                Ico(icon, 18.dp, color ?: C.Text)
            }
            Column(Modifier.weight(1f)) {
                T(label, 15.sp, color ?: C.Text, FontWeight.Medium)
                if (sub != null) T(sub, 12.sp, C.Muted, maxLines = 2)
            }
            if (value != null) T(value, 14.sp, C.Soft, FontWeight.Medium, modifier = Modifier.widthIn(max = 260.dp))
            if (selected) Ico(R.drawable.lc_check, 18.dp, C.Red)
        }
    }
}

@Composable
fun ToggleRow(label: String, checked: Boolean, icon: Int? = null, sub: String? = null, onChange: (Boolean) -> Unit) {
    Focusable(modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), onClick = { onChange(!checked) }, normal = C.Card, zoom = 1.01f) { _ ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (icon != null) Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(C.Card), contentAlignment = Alignment.Center) { Ico(icon, 18.dp) }
            Column(Modifier.weight(1f)) {
                T(label, 15.sp, C.Text, FontWeight.Medium)
                if (sub != null) T(sub, 12.sp, C.Muted, maxLines = 2)
            }
            Box(Modifier.width(44.dp).height(24.dp).clip(RoundedCornerShape(12.dp)).background(if (checked) C.Red else C.CardHi)) {
                Box(Modifier.align(if (checked) Alignment.CenterEnd else Alignment.CenterStart).padding(3.dp).size(18.dp)
                    .clip(CircleShape).background(if (checked) C.OnRed else C.Muted))
            }
        }
    }
}

/** Row of choices, the chosen one filled. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(14.dp)).background(C.Card).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEachIndexed { i, label ->
            Focusable(onClick = { onSelect(i) }, normal = if (i == selected) C.Red else Color.Transparent,
                focused = if (i == selected) C.Red else C.CardHi, shape = RoundedCornerShape(11.dp), zoom = 1f,
                modifier = Modifier.height(40.dp)) { _ ->
                T(label, 14.sp, if (i == selected) C.OnRed else C.Soft, FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
fun Field(
    label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier,
    type: KeyboardType = KeyboardType.Text, password: Boolean = false, onDone: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        modifier = modifier,
        textStyle = TextStyle(fontFamily = Inter, fontSize = 16.sp, color = C.Text),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else type,
            imeAction = if (onDone != null) ImeAction.Done else ImeAction.Next, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = C.Red, unfocusedBorderColor = C.Line,
            focusedLabelColor = C.RedText, unfocusedLabelColor = C.Muted, cursorColor = C.Red,
            focusedTextColor = C.Text, unfocusedTextColor = C.Text,
        ),
    )
}

/** Ask for one line of text (rename, link…). */
@Composable
fun TextDialog(title: String, label: String, initial: String = "", onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    val fr = remember { FocusRequester() }
    Popup(title, onDismiss) {
        Field(label, v, { v = it }, Modifier.fillMaxWidth().focusRequester(fr), onDone = { onOk(v.trim()) })
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button("Spremi", R.drawable.lc_check, primary = true) { onOk(v.trim()) }
            Button("Odustani", R.drawable.lc_x) { onDismiss() }
        }
    }
    LaunchedEffect(Unit) { try { fr.requestFocus() } catch (_: Exception) {} }
}

/**
 * 4-digit PIN with an on-screen keypad (Google TV remotes have no number keys).
 * [onPin] returns an error text, or null when the PIN was accepted.
 */
@Composable
fun PinDialog(title: String, text: String? = null, onDismiss: () -> Unit, onPin: (String) -> String?) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    fun press(k: String) {
        when (k) {
            "⌫" -> pin = pin.dropLast(1)
            else -> if (pin.length < 4) pin += k
        }
        if (pin.length == 4) { error = onPin(pin); if (error != null) pin = "" }
    }
    Popup(title, onDismiss, width = 520.dp) {
        if (text != null) T(text, 15.sp, C.Soft, maxLines = 4)
        Row(Modifier.fillMaxWidth().onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val d = when (e.key) {
                Key.Zero, Key.NumPad0 -> "0"; Key.One, Key.NumPad1 -> "1"; Key.Two, Key.NumPad2 -> "2"
                Key.Three, Key.NumPad3 -> "3"; Key.Four, Key.NumPad4 -> "4"; Key.Five, Key.NumPad5 -> "5"
                Key.Six, Key.NumPad6 -> "6"; Key.Seven, Key.NumPad7 -> "7"; Key.Eight, Key.NumPad8 -> "8"
                Key.Nine, Key.NumPad9 -> "9"; else -> null
            }
            if (d != null) { press(d); true } else false
        }, horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
            repeat(4) { i ->
                Box(Modifier.size(54.dp, 62.dp).clip(RoundedCornerShape(14.dp)).background(C.Card)
                    .border(2.dp, if (i == pin.length) C.Red else C.Line, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    if (i < pin.length) T("●", 24.sp, C.Text)
                }
            }
        }
        error?.let { T(it, 14.sp, C.Danger, maxLines = 2, modifier = Modifier.fillMaxWidth(), align = TextAlign.Center) }
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "✕")
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            keys.chunked(3).forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEachIndexed { c, k ->
                        Focusable(onClick = { if (k == "✕") onDismiss() else press(k) },
                            focusRequester = if (r == 0 && c == 0) first else null,
                            normal = C.Card, focused = C.CardHi, modifier = Modifier.size(84.dp, 52.dp), zoom = 1.04f) { _ ->
                            T(k, 20.sp, C.Text, FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center))
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { try { first.requestFocus() } catch (_: Exception) {} }
}

/* ------------------------------------------------------------------ wrap-around lists */

/** Up on the first item jumps to the last one, Down on the last item jumps to the first. */
class Wrap {
    var target by mutableStateOf(-1)
    val fr = FocusRequester()
    fun requester(i: Int, default: FocusRequester? = null): FocusRequester? = if (i == target) fr else default
}

fun Modifier.wrapKeys(i: Int, n: Int, w: Wrap): Modifier = this.onPreviewKeyEvent { e ->
    if (e.type != KeyEventType.KeyDown || n < 2) return@onPreviewKeyEvent false
    when {
        e.key == Key.DirectionUp && i == 0 -> { w.target = n - 1; true }
        e.key == Key.DirectionDown && i == n - 1 -> { w.target = 0; true }
        else -> false
    }
}

@Composable
fun WrapEffect(w: Wrap, state: androidx.compose.foundation.lazy.LazyListState) {
    LaunchedEffect(w.target) {
        val t = w.target
        if (t < 0) return@LaunchedEffect
        state.scrollToItem(t)
        kotlinx.coroutines.delay(80)
        try { w.fr.requestFocus() } catch (_: Exception) {}
        w.target = -1
    }
}

@Composable
fun WrapEffectGrid(w: Wrap, state: androidx.compose.foundation.lazy.grid.LazyGridState) {
    LaunchedEffect(w.target) {
        val t = w.target
        if (t < 0) return@LaunchedEffect
        state.scrollToItem(t)
        kotlinx.coroutines.delay(80)
        try { w.fr.requestFocus() } catch (_: Exception) {}
        w.target = -1
    }
}
