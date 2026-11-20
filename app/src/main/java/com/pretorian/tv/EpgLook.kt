package com.pretorian.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

/** your own look of the programme guide (EPG grid, guide in the player, programmes in the TV list): colour, size, transparency, brightness */
object EpgLook {
    val COLORS = listOf("Kao tema", "Plava", "Zelena", "Narandžasta", "Ljubičasta", "Siva")
    private val tints = listOf(Color.Unspecified, Color(0xFF2563EB), Color(0xFF16A34A), Color(0xFFEA580C), Color(0xFF7C3AED), Color(0xFF64748B))
    var color by mutableIntStateOf(Prefs.long("epg_color", 0).toInt().coerceIn(0, 5))
    var size by mutableIntStateOf(Prefs.long("epg_size", 100).toInt().coerceIn(80, 140))       // text %, 80 … 140
    var transp by mutableIntStateOf(Prefs.long("epg_transp", 0).toInt().coerceIn(0, 80))      // 0 = as the theme … 80 % see-through
    var light by mutableIntStateOf(Prefs.long("epg_light", 0).toInt().coerceIn(-3, 3))        // cells darker ◀ ▶ brighter
    fun save() { Prefs.put("epg_color", color.toLong()); Prefs.put("epg_size", size.toLong()); Prefs.put("epg_transp", transp.toLong()); Prefs.put("epg_light", light.toLong()) }
    fun reset() { color = 0; size = 100; transp = 0; light = 0; save() }
    val isDefault get() = color == 0 && size == 100 && transp == 0 && light == 0
    /** a programme cell: theme colour or your tint (stronger for "now"), then brightness and transparency */
    fun cell(base: Color, now: Boolean = false): Color {
        if (isDefault) return base
        val t = tints[color]
        var c = if (t == Color.Unspecified) base else Color(t.red, t.green, t.blue, base.alpha.coerceAtLeast(if (now) 0.55f else 0.32f)).let { x ->
            if (now) x else Tune.shift(x, -1, 0.35f) }
        c = Tune.shift(c, light, 0.12f)
        return c.copy(alpha = c.alpha * (1f - transp / 100f))
    }
    fun sp(v: androidx.compose.ui.unit.TextUnit) = if (size == 100) v else (v.value * size / 100f).sp
}
