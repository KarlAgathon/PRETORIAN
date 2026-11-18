package com.pretorian.tv

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/** Appearance settings. Compose state, so the whole app restyles the moment a value changes. */
object Look {
    var theme by mutableStateOf("dark")        // dark, light, bw, glass
    var accent by mutableIntStateOf(0)         // index in ACCENTS
    var shade by mutableIntStateOf(0)          // -2 darker … +2 lighter
    var opacity by mutableIntStateOf(80)       // panel opacity in %, steps of 5
    var tileSize by mutableIntStateOf(1)       // 0 small, 1 medium, 2 large
    var posterSize by mutableIntStateOf(1)     // 0 small, 1 medium, 2 large
    var tileStyle by mutableIntStateOf(4)      // 0 solid, 1 glass, 2 outline, 3 modern, 4 fluent
    var mostWatched by mutableStateOf(true)
    var overlay by mutableIntStateOf(0)        // menus and info bar: 0 glass, 1 black
    var preview by mutableStateOf(true)        // live preview of the focused channel (old switch)
    var previewMode by mutableIntStateOf(1)    // small live picture: 0 off, 1 only after OK, 2 by itself when you stop on a channel
    var menuOpacity by mutableIntStateOf(94)   // how solid menus/popups are, in %
    var rowSize by mutableIntStateOf(1)        // channel and group rows: 0 small, 1 medium, 2 large
    var lang by mutableStateOf("en")           // app language
    var iconSet by mutableStateOf("lc")        // lc lucide, tb tabler, fl fluent, ms material, ph phosphor, or a downloaded set "dl_<id>"
    var iconStyle by mutableIntStateOf(1)      // variant of the set (thin/normal/bold, outline/filled)
    var corner by mutableIntStateOf(0)         // 0 rounded, 1 slightly rounded, 2 sharp
    var groupFrame by mutableStateOf(true)     // thin accent frame around TV groups
    var resBadge by mutableIntStateOf(1)       // resolution in the player corner: 0 off, 1 on start, 2 always
    var bigText by mutableStateOf(false)      // larger text everywhere (for parents, far sofas)
    var layout by mutableIntStateOf(2)         // 0 classic, 1 New Wave, 2 New Age
    var tvDesign by mutableIntStateOf(1)       // 0 New Age, 1 New Era (three-panel TV browser)
    val wave get() = layout >= 1
    val age get() = layout == 2

    val ACCENTS = listOf(
        "Pretorian crvena" to Color(0xFFE5141B), "Plava" to Color(0xFF2563EB), "Zelena" to Color(0xFF16A34A),
        "Narandžasta" to Color(0xFFEA580C), "Ljubičasta" to Color(0xFF9333EA), "Tirkizna" to Color(0xFF0891B2),
        "Roza" to Color(0xFFDB2777), "Zlatna" to Color(0xFFCA8A04),
    )
    val THEMES = listOf("vece" to "Zadana", "bw" to "Crno-bijela", "pijesak" to "Pijesak",
        "liquid" to "Liquid", "classic" to "Classic", "oled" to "OLED crna", "kino" to "Kino", "reddawn" to "Red Dawn", "papir" to "Papir")
    /** themes with their own focus colour: accent and the text on it */
    val FIXED = mapOf(
        "liquid" to (Color(0xFF22D3EE) to Color(0xFF02141A)), "classic" to (Color(0xFFF1F5F9) to Color(0xFF1E293B)),
        "oled" to (Color(0xFFD4A537) to Color(0xFF120D00)),
        "kino" to (Color(0xFFE11D2A) to Color.White),
        "papir" to (Color(0xFF111827) to Color.White), "reddawn" to (Color(0xFFDC2626) to Color.White),
    )
    /** size of everything together, 0–100 (Settings › Look › Size): 100 is about 35 % bigger */
    var uiScale by mutableIntStateOf(0)

    private fun sp(c: Context) = c.getSharedPreferences("pretorian", Context.MODE_PRIVATE)

    fun load(c: Context) {
        val p = sp(c)
        theme = (p.getString("look_theme", "vece") ?: "vece").let { t -> if (THEMES.any { it.first == t }) t else "vece" }
        uiScale = p.getInt("look_scale", 0).coerceIn(0, 100)
        accent = p.getInt("look_accent", 0).coerceIn(0, ACCENTS.lastIndex)
        shade = p.getInt("look_shade", 0)
        opacity = p.getInt("look_opacity", 80)
        tileSize = p.getInt("look_tile", 1)
        posterSize = p.getInt("look_poster", 1)
        tileStyle = p.getInt("look_tilestyle", 0)
        mostWatched = p.getBoolean("look_mostwatched", true)
        overlay = p.getInt("look_overlay", 0)
        preview = p.getBoolean("look_preview", true)
        previewMode = p.getInt("look_previewmode", 2)
        menuOpacity = p.getInt("look_menuopacity", 94)
        rowSize = p.getInt("look_rowsize", 1)
        lang = p.getString("app_lang", null) ?: "en"   // a new install starts in English
        iconSet = p.getString("look_iconset", "lc") ?: "lc"
        iconStyle = p.getInt("look_iconstyle", 1)
        corner = p.getInt("look_corner", 0)
        groupFrame = p.getBoolean("look_groupframe", true)
        resBadge = p.getInt("look_resbadge", 1)
        layout = p.getInt("look_layout", 2).coerceIn(0, 2)
        tvDesign = p.getInt("look_tvdesign", 1).coerceIn(0, 1)
        bigText = false   // replaced by the size slider, which grows everything together
        // 0.3: the new modern home tiles become the default once
        if (p.getInt("look_v", 0) < 3) { tileStyle = 3; p.edit().putInt("look_v", 3).putInt("look_tilestyle", 3).apply() }
        // 0.4.2: inside a group the small picture starts by itself (you asked for it)
        if (p.getInt("look_v", 0) < 4) { previewMode = 2; p.edit().putInt("look_v", 4).putInt("look_previewmode", 2).apply() }
        if (p.getInt("look_v", 0) < 5) { tileStyle = 4; p.edit().putInt("look_v", 5).putInt("look_tilestyle", 4).apply() }
    }

    fun save(c: Context) {
        sp(c).edit().putString("look_theme", theme).putInt("look_accent", accent).putInt("look_shade", shade)
            .putInt("look_opacity", opacity).putInt("look_tile", tileSize).putInt("look_poster", posterSize)
            .putInt("look_tilestyle", tileStyle).putBoolean("look_mostwatched", mostWatched)
            .putInt("look_overlay", overlay).putBoolean("look_preview", preview).putInt("look_previewmode", previewMode)
            .putInt("look_menuopacity", menuOpacity).putInt("look_rowsize", rowSize).putString("app_lang", lang)
            .putString("look_iconset", iconSet).putInt("look_iconstyle", iconStyle).putInt("look_corner", corner)
            .putBoolean("look_groupframe", groupFrame).putInt("look_resbadge", resBadge).putInt("look_layout", layout).putInt("look_tvdesign", tvDesign).putBoolean("look_bigtext", bigText).putInt("look_scale", uiScale).apply()
    }
}

private data class Palette(
    val bg: Color, val rail: Color, val card: Color, val line: Color,
    val text: Color, val muted: Color, val soft: Color, val tile: Color, val light: Boolean,
)

private fun alpha(c: Color, pct: Int) = c.copy(alpha = (pct.coerceIn(0, 100) / 100f))

/**
 * Settings › Appearance › Text and boxes: your own fine tuning on top of any theme
 * (text weight, brightness and size; brightness of the background/menus and of the boxes). 0 = the theme as it is.
 */
object Tune {
    var weight by mutableIntStateOf(Prefs.long("tune_tw", 0).toInt())          // 0 as designed … 3 much bolder
    var textLight by mutableIntStateOf(Prefs.long("tune_tb", 0).toInt())       // -3 darker … +3 brighter
    var textSize by mutableIntStateOf(Prefs.long("tune_ts", 100).toInt())      // 90 … 120 %
    var bgLight by mutableIntStateOf(Prefs.long("tune_mb", 0).toInt())         // background and menus, -3 … +3
    var boxLight by mutableIntStateOf(Prefs.long("tune_cb", 0).toInt())        // boxes around groups, channels, cards, -3 … +3
    fun save() {
        Prefs.put("tune_tw", weight.toLong()); Prefs.put("tune_tb", textLight.toLong()); Prefs.put("tune_ts", textSize.toLong())
        Prefs.put("tune_mb", bgLight.toLong()); Prefs.put("tune_cb", boxLight.toLong())
    }
    fun reset() { weight = 0; textLight = 0; textSize = 100; bgLight = 0; boxLight = 0; save() }
    val isDefault get() = weight == 0 && textLight == 0 && textSize == 100 && bgLight == 0 && boxLight == 0
    /** a colour moved toward white (+) or black (−), keeping its own transparency */
    fun shift(c: Color, steps: Int, per: Float): Color {
        if (steps == 0) return c
        val t = (kotlin.math.abs(steps) * per).coerceIn(0f, 1f)
        val to = if (steps > 0) 1f else 0f
        return Color(c.red + (to - c.red) * t, c.green + (to - c.green) * t, c.blue + (to - c.blue) * t, c.alpha)
    }
    fun font(w: androidx.compose.ui.text.font.FontWeight) =
        if (weight == 0) w else androidx.compose.ui.text.font.FontWeight((w.weight + weight * 100).coerceAtMost(900))
}

private fun palette(): Palette {
    val p = basePalette()
    if (Tune.isDefault) return p
    // boxes: brighter = more light colour and a little more solid; darker = toward black
    fun box(c: Color) = Tune.shift(c, Tune.boxLight, 0.12f).let { x -> if (Tune.boxLight > 0) x.copy(alpha = (x.alpha + Tune.boxLight * 0.05f).coerceAtMost(1f)) else x }
    return p.copy(
        bg = Tune.shift(p.bg, Tune.bgLight, 0.06f), rail = Tune.shift(p.rail, Tune.bgLight, 0.07f),
        card = box(p.card), tile = box(p.tile), line = Tune.shift(p.line, Tune.boxLight, 0.10f),
        text = Tune.shift(p.text, Tune.textLight, 0.14f), muted = Tune.shift(p.muted, Tune.textLight, 0.14f), soft = Tune.shift(p.soft, Tune.textLight, 0.14f),
    )
}

private fun basePalette(): Palette {
    val o = Look.opacity
    return when (Look.theme) {
        "light" -> Palette(Color(0xFFF2F3F6), alpha(Color.White, o), Color(0x0F000000), Color(0x1A000000),
            Color(0xFF14171D), Color(0xFF555C68), Color(0xFF2B3039), Color.White, true)
        "bw" -> Palette(Color(0xFF050505), alpha(Color(0xFF0A0A0C), o.coerceAtLeast(55)), Color(0x14FFFFFF), Color(0x1FFFFFFF),
            Color(0xFFFAFAFA), Color(0xFF8A8A8A), Color(0xFFC4C4C4), Color(0xFF151515), false)
        "vece" -> Palette(Color(0xFF0A0F1F), alpha(Color(0xFF121B33), (o * 0.56f).toInt().coerceIn(8, 90)), Color(0x14FFFFFF), Color(0x1FFFFFFF),
            Color(0xFFFAFAFA), Color(0xFF8C94A8), Color(0xFFC8CDD8), Color(0xFF16203A), false)
        "staklo" -> Palette(Color(0xFFF7F7F8), alpha(Color.White, o.coerceAtMost(75)), Color(0x0F000000), Color(0x1A000000),
            Color(0xFF0B0B0F), Color(0xFF5A5A66), Color(0xFF2A2A33), Color(0xFFE9E9EE), true)
        "pijesak" -> Palette(Color(0xFF16100A), alpha(Color(0xFF2A1F14), (o * 0.7f).toInt().coerceIn(35, 95)), Color(0x1AE8C38A), Color(0x2EE8C38A),
            Color(0xFFF7EBD7), Color(0xFFA8977E), Color(0xFFE2D2B8), Color(0xFF2A1F14), false)
        "glass" -> Palette(Color(0xFF16202B), alpha(Color(0xFF26323F), o), Color(0x1AFFFFFF), Color(0x24FFFFFF),
            Color(0xFFF4F6F8), Color(0xFFA9B3BE), Color(0xFFD5DBE1), alpha(Color(0xFF2E3C4A), o), false)
        // Liquid: see-through menus over the picture, cyan focus
        "liquid" -> Palette(Color(0xFF07131C), Color(0x2EFFFFFF), Color(0x24FFFFFF), Color(0x47FFFFFF),
            Color(0xFFFFFFFF), Color(0xFFD0DAE4), Color(0xFFEDF2F7), Color(0x2BFFFFFF), false)
        // Classic: flat slate blue
        "classic" -> Palette(Color(0xFF2A3A4F), Color(0xFF1C2735), Color(0x1FFFFFFF), Color(0x29FFFFFF),
            Color(0xFFF1F5F9), Color(0xFFA9B6C6), Color(0xFFD5DEE8), Color(0xFF3B4E66), false)
        "oled" -> Palette(Color(0xFF000000), alpha(Color(0xFF000000), 100), Color(0x14FFFFFF), Color(0x1FFFFFFF),
            Color(0xFFF5F5F5), Color(0xFF8C8C8C), Color(0xFFCFCFCF), Color(0xFF0F0F0F), false)
        "kino" -> Palette(Color(0xFF111214), alpha(Color(0xFF17181B), o.coerceAtLeast(70)), Color(0x14FFFFFF), Color(0x1FFFFFFF),
            Color(0xFFF5F5F5), Color(0xFF9C9CA3), Color(0xFFD4D4D8), Color(0xFF1D1E22), false)
        "papir" -> Palette(Color(0xFFEEF0F3), Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFFD5D9DF),
            Color(0xFF111827), Color(0xFF6B7280), Color(0xFF374151), Color(0xFFFFFFFF), true)
        // Red Dawn: soft black, grey, white and red
        "reddawn" -> Palette(Color(0xFF18181B), Color(0xFF111113), Color(0xFF2F2F33), Color(0xFF3A3A40),
            Color(0xFFF4F4F5), Color(0xFFA1A1AA), Color(0xFFD4D4D8), Color(0xFF27272A), false)
        "dew" -> Palette(Color(0xFF221B36), alpha(Color(0xFF2A2340), (o * 0.55f).toInt().coerceAtLeast(25)), Color(0x1CFFFFFF), Color(0x2EFFFFFF),
            Color(0xFFF7F4FA), Color(0xFFB9B0C8), Color(0xFFE2DCEA), Color(0x26FFFFFF), false)
        else -> Palette(Color(0xFF0B0D12), alpha(Color(0xFF0E1117), o.coerceAtLeast(60)), Color(0x0FFFFFFF), Color(0x14FFFFFF),
            Color(0xFFF2F3F5), Color(0xFF9AA1AD), Color(0xFFC9CED6), Color(0xFF1A1F2A), false)
    }
}

/** Colours of the current theme. Every getter reads Compose state, so screens follow setting changes. */
object C {
    val Bg get() = palette().bg
    val Rail get() = palette().rail
    val Card get() = palette().card
    val CardHi get() = if (palette().light) Color(0x1F000000) else Color(0x24FFFFFF)
    val Line get() = palette().line
    val Text get() = palette().text
    val Muted get() = palette().muted
    val Soft get() = palette().soft
    val Tile get() = palette().tile
    val IsLight get() = palette().light
    /** app background: Winter Dew is a frosted dusk gradient, the others a plain colour */
    val BgBrush: androidx.compose.ui.graphics.Brush
        get() = if (Look.theme == "pijesak") androidx.compose.ui.graphics.Brush.linearGradient(
            listOf(Color(0xFF120C07), Color(0xFF241809), Color(0xFF3A2610)),
            start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(2400f, 1400f))
        else if (Look.theme == "glass") androidx.compose.ui.graphics.Brush.linearGradient(
            listOf(Color(0xFF0E1A26), Color(0xFF1B3346), Color(0xFF2B4A5E)),
            start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(2400f, 1400f))
        else if (Look.theme == "liquid") androidx.compose.ui.graphics.SolidColor(Color.Transparent)
        else if (Look.theme == "reddawn") androidx.compose.ui.graphics.Brush.linearGradient(
            listOf(Color(0xFF18181B), Color(0xFF1F1F23), Color(0xFF2A0D0F)),
            start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(2400f, 1400f))
        else if (Look.theme == "dew") androidx.compose.ui.graphics.Brush.linearGradient(
            listOf(Color(0xFF1C2140), Color(0xFF2E2347), Color(0xFF4A2A48), Color(0xFF5C3045)),
            start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(2400f, 1400f))
        else androidx.compose.ui.graphics.SolidColor(palette().bg)

    /** Settings › Appearance › "Selected colour": soft (default) mixes the theme colour a little into the background,
     *  so big selected boxes don't glare (white becomes a light grey, gold a calmer gold); strong = the pure colour */
    val soft get() = FocusCard.pct < 100
    private fun softened(c: Color): Color = if (!soft) c else lerp(palette().bg, c, FocusCard.pct / 100f)

    /** accent colour with the chosen shade; black-and-white theme uses white */
    val Red: Color get() = softened(pureRed)
    private val pureRed: Color
        get() {
            if (Look.theme == "bw" || Look.theme == "vece") return Color.White
            Look.FIXED[Look.theme]?.let { return it.first }
            if (Look.theme == "pijesak") return Color(0xFFE0B25C)
            val base = Look.ACCENTS.getOrElse(Look.accent) { Look.ACCENTS[0] }.second
            val s = Look.shade.coerceIn(-2, 2)
            return when {
                s > 0 -> lerp(base, Color.White, 0.18f * s)
                s < 0 -> lerp(base, Color.Black, 0.18f * -s)
                else -> base
            }
        }
    /** text drawn on top of the accent colour */
    val OnRed get() = if (soft) (if (Red.luminance() > 0.4f) Color(0xFF111111) else Color.White)
        else Look.FIXED[Look.theme]?.second ?: if (Look.theme == "bw" || Look.theme == "vece" || Look.theme == "pijesak") Color.Black else Color.White  // staklo/pijesak: black accent, white text
    val RedText get() = if (Look.theme == "classic" || Look.theme == "papir") (if (IsLight) Color(0xFF1E3A8A) else Color.White) else if (Look.FIXED.containsKey(Look.theme)) Red else if (Look.theme == "bw" || Look.theme == "vece") Color.White else if (Look.theme == "pijesak") Color(0xFFE0B25C) else lerp(Red, if (IsLight) Color.Black else Color.White, if (IsLight) 0.2f else 0.35f)
    val CardFocus get() = if (Look.theme == "dew") Color(0x40FFFFFF) else Red.copy(alpha = 0.18f)
    val Green get() = if (IsLight) Color(0xFF15803D) else Color(0xFF4ADE80)
    val Amber get() = if (IsLight) Color(0xFFB45309) else Color(0xFFFBBF24)
    val Danger = Color(0xFFEF4444)
    /** background of menus and popups: see-through glass or solid black; the glass takes the tone of the theme (no blue on a black or sand theme) */
    val Overlay get() = if (Look.theme == "liquid") Color(0xFF16222E).copy(alpha = 0.62f) else if (Look.theme == "papir") Color.White else if (Look.overlay == 1) (if (IsLight) Color.White else Color(0xFF07090D))
                        else (if (IsLight) Color.White else lerp(palette().bg, Color.White, 0.06f)).copy(alpha = Look.menuOpacity.coerceIn(60, 100) / 100f)
    /** player info bar background */
    val OverlayBar get() = if (Look.theme == "liquid") Color(0x66000000) else if (Look.overlay == 1) Color(0xF2000000) else Color(0x99000000)
}

/** Settings › Appearance › Focus card: how bright the box you stand on is, 20–100 % in every theme
 *  (100 = the pure theme colour, less = mixed into the background so big boxes don't glare). */
object FocusCard {
    private var v by mutableIntStateOf(Prefs.long("focus_pct", if (Prefs.long("look_focus_soft", 0) == 0L) 60 else 100).toInt().coerceIn(20, 100))
    val pct get() = v
    fun set(p: Int) { v = p.coerceIn(20, 100); Prefs.put("focus_pct", v.toLong()) }
}

/** Corner shape from Settings › Appearance › Corners. */
fun Rnd(d: androidx.compose.ui.unit.Dp): androidx.compose.ui.graphics.Shape =
    androidx.compose.foundation.shape.RoundedCornerShape(when (Look.corner) { 2 -> d * 0f; 1 -> d * 0.4f; else -> d })

/** Pill shape (chips), following the corner setting. */
val Pill: androidx.compose.ui.graphics.Shape
    get() = when (Look.corner) {
        2 -> androidx.compose.foundation.shape.RoundedCornerShape(0)
        1 -> androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
        else -> androidx.compose.foundation.shape.RoundedCornerShape(50)
    }
