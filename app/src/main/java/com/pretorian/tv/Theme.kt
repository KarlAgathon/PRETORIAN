package com.pretorian.tv

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Appearance settings. Compose state, so the whole app restyles the moment a value changes. */
object Look {
    var theme by mutableStateOf("dark")        // dark, light, bw, glass
    var accent by mutableIntStateOf(0)         // index in ACCENTS
    var shade by mutableIntStateOf(0)          // -2 darker … +2 lighter
    var opacity by mutableIntStateOf(80)       // panel opacity in %, steps of 5
    var tileSize by mutableIntStateOf(1)       // 0 small, 1 medium, 2 large
    var posterSize by mutableIntStateOf(1)     // 0 small, 1 medium, 2 large
    var tileStyle by mutableIntStateOf(0)      // 0 solid, 1 glass, 2 outline
    var mostWatched by mutableStateOf(true)

    val ACCENTS = listOf(
        "Pretorian crvena" to Color(0xFFE5141B), "Plava" to Color(0xFF2563EB), "Zelena" to Color(0xFF16A34A),
        "Narandžasta" to Color(0xFFEA580C), "Ljubičasta" to Color(0xFF9333EA), "Tirkizna" to Color(0xFF0891B2),
        "Roza" to Color(0xFFDB2777), "Zlatna" to Color(0xFFCA8A04),
    )
    val THEMES = listOf("dark" to "Tamna", "light" to "Svijetla", "bw" to "Crno-bijela", "glass" to "Glass")

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
    }

    fun save(c: Context) {
        sp(c).edit().putString("look_theme", theme).putInt("look_accent", accent).putInt("look_shade", shade)
            .putInt("look_opacity", opacity).putInt("look_tile", tileSize).putInt("look_poster", posterSize)
            .putInt("look_tilestyle", tileStyle).putBoolean("look_mostwatched", mostWatched).apply()
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
    val CardFocus get() = Red.copy(alpha = 0.18f)
    val Green get() = if (IsLight) Color(0xFF15803D) else Color(0xFF4ADE80)
    val Amber get() = if (IsLight) Color(0xFFB45309) else Color(0xFFFBBF24)
    val Danger = Color(0xFFEF4444)
}
