package com.pretorian.tv

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Media Center has ONE look: Monochrome Silver (Prime Directive §4). Black / charcoal / graphite backgrounds,
 * white and silver text, a soft silver focus with dark text. Colour only where it means something
 * (LIVE, warning, success) and in the media itself (posters, video, logos).
 *
 * The fields below are kept as fixed values so older screens that read them keep compiling;
 * none of them can be changed by the user any more.
 */
object Look {
    const val theme = "silver"
    const val accent = 0
    const val shade = 0
    const val opacity = 85
    const val tileSize = 1
    var posterSize by mutableIntStateOf(1)     // 0 small, 1 medium, 2 large (internal: Lite may use small)
    const val tileStyle = 4
    const val mostWatched = true
    const val overlay = 0
    const val preview = true
    /** the small live picture starts only after OK (focus never plays anything) */
    const val previewMode = 1
    const val menuOpacity = 94
    const val rowSize = 1
    var lang by mutableStateOf("en")           // app language
    const val corner = 0
    const val groupFrame = false
    const val resBadge = 1
    const val bigText = false
    /** one layout: one level on the screen at a time */
    const val layout = 3
    const val wave = false
    const val age = true
    const val dawn = true
    const val uiScale = 0

    private fun sp(c: Context) = c.getSharedPreferences("pretorian", Context.MODE_PRIVATE)

    fun load(c: Context) {
        lang = sp(c).getString("app_lang", null) ?: "en"   // a new install starts in English
    }

    fun save(c: Context) {
        sp(c).edit().putString("app_lang", lang).apply()
    }
}

/** Monochrome Silver design tokens. Every screen takes its colours from here (C.*), never its own hex values. */
object Silver {
    val Background = Color(0xFF0A0A0B)
    val Panel = Color(0xA614181C)          // glass: rgba(20,24,28) over the concrete
    val PanelSolid = Color(0xFF141416)
    val Card = Color(0x14FFFFFF)
    val CardHi = Color(0x24FFFFFF)
    val Border = Color(0x1FFFFFFF)
    val Text = Color(0xFFF4F4F5)
    val TextSoft = Color(0xFFB0B8C4)
    val TextMuted = Color(0xFF8E8E94)
    val Tile = Color(0xFF1A1A1D)
    val Focus = Color(0x29FFFFFF)          // redesign: translucent white pill (with a white hairline)
    val OnFocus = Color(0xFFFFFFFF)        // text stays white on the pill
    val Track = Color(0x33FFFFFF)          // progress track
    val Progress = Color(0xFFE6E6E9)       // progress fill
    // semantic colours, restrained, never the only signal
    val Live = Color(0xFFC62828)
    val Warning = Color(0xFFE0A526)
    val Success = Color(0xFF4CAF7A)
    val Error = Color(0xFFE04B4B)
}

/** Text size from Settings › Appearance (90–120 %). The other old tuning values stay at 0. */
object Tune {
    const val weight = 0
    const val textLight = 0
    var textSize by mutableIntStateOf(Prefs.long("tune_ts", 100).toInt().coerceIn(90, 120))
    const val bgLight = 0
    const val boxLight = 0
    fun save() { Prefs.put("tune_ts", textSize.toLong()) }
    fun reset() { textSize = 100; save() }
    val isDefault get() = textSize == 100
    fun shift(c: Color, steps: Int, per: Float): Color {
        if (steps == 0) return c
        val t = (kotlin.math.abs(steps) * per).coerceIn(0f, 1f)
        val to = if (steps > 0) 1f else 0f
        return Color(c.red + (to - c.red) * t, c.green + (to - c.green) * t, c.blue + (to - c.blue) * t, c.alpha)
    }
    fun font(w: androidx.compose.ui.text.font.FontWeight) = w
}

/** Colours used by every screen (Monochrome Silver). */
object C {
    val Bg get() = Silver.Background
    val Rail get() = Silver.Panel
    val Card get() = Silver.Card
    val CardHi get() = Silver.CardHi
    val Line get() = Silver.Border
    val Text get() = Silver.Text
    val Muted get() = Silver.TextMuted
    val Soft get() = Silver.TextSoft
    val Tile get() = Silver.Tile
    const val IsLight = false
    /** the shell draws the background behind every screen: screens stay see-through over it */
    val BgBrush: androidx.compose.ui.graphics.Brush get() = androidx.compose.ui.graphics.SolidColor(Color.Transparent)
    val soft get() = false
    /** focus / selection colour (the old name stays so all screens follow): soft silver */
    val Red: Color get() = Silver.Focus
    /** text and icons on the focus colour */
    val OnRed: Color get() = Silver.OnFocus
    /** emphasis text (was the accent text): white */
    val RedText: Color get() = Silver.Text
    val CardFocus get() = Color(0x33DADADE)
    val Green get() = Silver.Success
    val Amber get() = Silver.Warning
    val Danger = Silver.Error
    val Live = Silver.Live
    val Progress get() = Silver.Progress
    val Track get() = Silver.Track
    /** menus and popups */
    val Overlay get() = Color(0xF0141416)
    /** player info bar background */
    val OverlayBar get() = Color(0x99000000)
}

/** Focus strength, fixed (kept for older screens). */
object FocusCard {
    const val pct = 100
    @Suppress("UNUSED_PARAMETER") fun set(p: Int) {}
}

fun Rnd(d: androidx.compose.ui.unit.Dp): androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.RoundedCornerShape(d)

val Pill: androidx.compose.ui.graphics.Shape get() = androidx.compose.foundation.shape.RoundedCornerShape(50)
