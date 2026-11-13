package com.pretorian.tv

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
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
    var lang by mutableStateOf("bs")           // app language
    var iconSet by mutableStateOf("lc")        // lc lucide, tb tabler, fl fluent, ms material, ph phosphor, or a downloaded set "dl_<id>"
    var iconStyle by mutableIntStateOf(1)      // variant of the set (thin/normal/bold, outline/filled)
    var corner by mutableIntStateOf(0)         // 0 rounded, 1 slightly rounded, 2 sharp
    var groupFrame by mutableStateOf(true)     // thin accent frame around TV groups
    var resBadge by mutableIntStateOf(1)       // resolution in the player corner: 0 off, 1 on start, 2 always
    var layout by mutableIntStateOf(1)         // 0 classic, 1 New Wave (full-width grids, chips, hint bar), 2 New Age (top menu, poster rows)
    val wave get() = layout >= 1
    val age get() = layout == 2

    val ACCENTS = listOf(
        "Pretorian crvena" to Color(0xFFE5141B), "Plava" to Color(0xFF2563EB), "Zelena" to Color(0xFF16A34A),
        "Narandžasta" to Color(0xFFEA580C), "Ljubičasta" to Color(0xFF9333EA), "Tirkizna" to Color(0xFF0891B2),
        "Roza" to Color(0xFFDB2777), "Zlatna" to Color(0xFFCA8A04),
    )
    val THEMES = listOf("dark" to "Tamna", "light" to "Svijetla", "bw" to "Crno-bijela", "glass" to "Glass", "dew" to "Winter Dew")

    private fun sp(c: Context) = c.getSharedPreferences("pretorian", Context.MODE_PRIVATE)

    fun load(c: Context) {
        val p = sp(c)
        theme = p.getString("look_theme", "dark") ?: "dark"
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
        lang = p.getString("app_lang", "bs") ?: "bs"
        iconSet = p.getString("look_iconset", "lc") ?: "lc"
        iconStyle = p.getInt("look_iconstyle", 1)
        corner = p.getInt("look_corner", 0)
        groupFrame = p.getBoolean("look_groupframe", true)
        resBadge = p.getInt("look_resbadge", 1)
        layout = p.getInt("look_layout", 1)
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
            .putBoolean("look_groupframe", groupFrame).putInt("look_resbadge", resBadge).putInt("look_layout", layout).apply()
    }
}

private data class Palette(
    val bg: Color, val rail: Color, val card: Color, val line: Color,
    val text: Color, val muted: Color, val soft: Color, val tile: Color, val light: Boolean,
)

private fun alpha(c: Color, pct: Int) = c.copy(alpha = (pct.coerceIn(0, 100) / 100f))

private fun palette(): Palette {
    val o = Look.opacity
    return when (Look.theme) {
        "light" -> Palette(Color(0xFFF2F3F6), alpha(Color.White, o), Color(0x0F000000), Color(0x1A000000),
            Color(0xFF14171D), Color(0xFF555C68), Color(0xFF2B3039), Color.White, true)
        "bw" -> Palette(Color.Black, alpha(Color(0xFF0A0A0A), o), Color(0x14FFFFFF), Color(0x33FFFFFF),
            Color.White, Color(0xFFA6A6A6), Color(0xFFDDDDDD), Color(0xFF151515), false)
        "glass" -> Palette(Color(0xFF16202B), alpha(Color(0xFF26323F), o), Color(0x1AFFFFFF), Color(0x24FFFFFF),
            Color(0xFFF4F6F8), Color(0xFFA9B3BE), Color(0xFFD5DBE1), alpha(Color(0xFF2E3C4A), o), false)
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
        get() = if (Look.theme == "dew") androidx.compose.ui.graphics.Brush.linearGradient(
            listOf(Color(0xFF1C2140), Color(0xFF2E2347), Color(0xFF4A2A48), Color(0xFF5C3045)),
            start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(2400f, 1400f))
        else androidx.compose.ui.graphics.SolidColor(palette().bg)

    /** accent colour with the chosen shade; black-and-white theme uses white */
    val Red: Color
        get() {
            if (Look.theme == "bw") return Color.White
            val base = Look.ACCENTS.getOrElse(Look.accent) { Look.ACCENTS[0] }.second
            val s = Look.shade.coerceIn(-2, 2)
            return when {
                s > 0 -> lerp(base, Color.White, 0.18f * s)
                s < 0 -> lerp(base, Color.Black, 0.18f * -s)
                else -> base
            }
        }
    /** text drawn on top of the accent colour */
    val OnRed get() = if (Look.theme == "bw") Color.Black else Color.White
    val RedText get() = if (Look.theme == "bw") Color.White else lerp(Red, if (IsLight) Color.Black else Color.White, if (IsLight) 0.2f else 0.35f)
    val CardFocus get() = if (Look.theme == "dew") Color(0x40FFFFFF) else Red.copy(alpha = 0.18f)
    val Green get() = if (IsLight) Color(0xFF15803D) else Color(0xFF4ADE80)
    val Amber get() = if (IsLight) Color(0xFFB45309) else Color(0xFFFBBF24)
    val Danger = Color(0xFFEF4444)
    /** background of menus and popups: see-through glass or solid black */
    val Overlay get() = if (Look.overlay == 1) (if (IsLight) Color.White else Color(0xFF07090D))
                        else (if (IsLight) Color.White else Color(0xFF151A23)).copy(alpha = Look.menuOpacity.coerceIn(60, 100) / 100f)
    /** player info bar background */
    val OverlayBar get() = if (Look.overlay == 1) Color(0xF2000000) else Color(0x99000000)
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
