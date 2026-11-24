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
    const val opacity = 85
    const val mostWatched = true
    const val overlay = 0
    const val rowSize = 1
    var lang by mutableStateOf("en")           // app language (ISO code; Serbian Latin is "sr" + latin)
    /** 3.0.0.1: Serbian in Latin script (the Serbian texts, transliterated) */
    var latin by mutableStateOf(false)
    /** the code in the language list: "sr-Latn" for Serbian Latin, otherwise [lang] */
    val code get() = if (lang == "sr" && latin) "sr-Latn" else lang  // i18n-ignore
    const val resBadge = 1
    const val uiScale = 0

    private fun sp(c: Context) = c.getSharedPreferences("pretorian", Context.MODE_PRIVATE)

    fun load(c: Context) {
        // 3.0.0.1: a new install starts in the device language when the app has it, otherwise English
        lang = sp(c).getString("app_lang", null) ?: deviceLang().also { d -> latin = d == "sr-Latn" }.let { if (it == "sr-Latn") "sr" else it }  // i18n-ignore
        if (sp(c).contains("app_lang")) latin = sp(c).getBoolean("app_lang_latin", false)
    }

    fun save(c: Context) {
        sp(c).edit().putString("app_lang", lang).putBoolean("app_lang_latin", latin).apply()
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
    // Izgled › Kontrast teksta: "Visok" lifts the grey texts (titles stay white)
    val Muted get() = if (LookOpts.highContrast) Color(0xFFB8BCC4) else Silver.TextMuted
    val Soft get() = if (LookOpts.highContrast) Color(0xFFDDE1E6) else Silver.TextSoft
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

fun Rnd(d: androidx.compose.ui.unit.Dp): androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.RoundedCornerShape(d)

val Pill: androidx.compose.ui.graphics.Shape get() = androidx.compose.foundation.shape.RoundedCornerShape(50)

/** the tone of the Pure Graphite background (left side); artwork fades into it */
val GRAPHITE = androidx.compose.ui.graphics.Color(0xFF161A23)


/** the device language when the app offers it ("sr-Latn" for Serbian in Latin script), else "en" */
fun deviceLang(): String {
    val l = java.util.Locale.getDefault()
    if (l.language == "sr" && l.script == "Latn") return "sr-Latn"  // i18n-ignore
    val c = if (l.language == "in") "id" else l.language
    return Langs.APP.firstOrNull { it.code == c }?.code ?: "en"  // i18n-ignore
}
/** the locale for dates and numbers in the app language */
fun appLocale(): java.util.Locale = if (Look.lang == "sr" && Look.latin) java.util.Locale.forLanguageTag("sr-Latn") else java.util.Locale(Look.lang)  // i18n-ignore
