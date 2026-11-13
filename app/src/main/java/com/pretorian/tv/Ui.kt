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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object C {
    val Bg = Color(0xFF0B0D12)
    val Rail = Color(0xFF0E1117)
    val Card = Color(0x0FFFFFFF)
    val CardFocus = Color(0x29E5141B)
    val Line = Color(0x14FFFFFF)
    val Text = Color(0xFFF2F3F5)
    val Muted = Color(0xFF9AA1AD)
    val Soft = Color(0xFFC9CED6)
    val Red = Color(0xFFE5141B)
    val RedText = Color(0xFFFF6B70)
    val Green = Color(0xFF4ADE80)
    val Amber = Color(0xFFFBBF24)
}

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_medium, FontWeight.SemiBold),
    Font(R.font.inter_medium, FontWeight.Bold),
)

/** One line of text that never runs outside its box. */
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

/** A remote-friendly focusable box: red frame + slight zoom when focused. */
@Composable
fun Focusable(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(14.dp),
    normal: Color = Color.Transparent,
    focused: Color = C.CardFocus,
    zoom: Float = 1.03f,
    focusRequester: FocusRequester? = null,
    onFocus: (() -> Unit)? = null,
    content: @Composable BoxScope.(Boolean) -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val isFocused by src.collectIsFocusedAsState()
    LaunchedEffect(isFocused) { if (isFocused) onFocus?.invoke() }
    val scale by animateFloatAsState(if (isFocused) zoom else 1f, label = "zoom")
    var m = modifier
    if (focusRequester != null) m = m.focusRequester(focusRequester)
    Box(
        m.graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(if (isFocused) focused else normal)
            .border(2.dp, if (isFocused) C.Red else Color.Transparent, shape)
            .combinedClickable(interactionSource = src, indication = null, onClick = onClick, onLongClick = onLongClick),
        content = { content(isFocused) },
    )
}

@Composable
fun Ico(id: Int, size: Dp = 22.dp, tint: Color = C.Text) {
    Icon(painterResource(id), contentDescription = null, modifier = Modifier.size(size), tint = tint)
}

@Composable
fun Chip(text: String, bg: Color = Color(0x14FFFFFF), fg: Color = C.Text) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 11.dp, vertical = 5.dp)) {
        T(text, 13.sp, fg, FontWeight.Medium)
    }
}

@Composable
fun Button(text: String, icon: Int? = null, primary: Boolean = false, modifier: Modifier = Modifier,
           focusRequester: FocusRequester? = null, onClick: () -> Unit) {
    Focusable(
        modifier = modifier.height(48.dp),
        onClick = onClick,
        focusRequester = focusRequester,
        normal = if (primary) C.Red else Color(0x12FFFFFF),
        focused = if (primary) Color(0xFFFF2A31) else Color(0x2EFFFFFF),
    ) { _ ->
        Row(Modifier.align(Alignment.Center).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            if (icon != null) Ico(icon, 18.dp)
            T(text, 15.sp, C.Text, FontWeight.SemiBold)
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
        T(text, 15.sp, C.Muted, modifier = Modifier.padding(top = 14.dp))
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
