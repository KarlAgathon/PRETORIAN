package com.pretorian.tv

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Home kits for the Sport screen: the colours (body, sleeves, shorts) and simple pattern (stripes, hoops, halves)
 * from the team's Wikipedia infobox, drawn as a plain shirt — no maker logo, sponsor or crest (those are trademarks;
 * the real crest stands next to it). Nothing found → ESPN's team colours; none of those → no shirt.
 */
data class KitColors(val body: Color, val left: Color, val right: Color, val stripe: Color?, val pattern: String)

object WikiKits {
    private val mem = HashMap<String, KitColors?>()
    private const val UA = "MediaCenter/2.5 (Android TV; team kit colours)"  // i18n-ignore

    private fun hex(s: String?): Color? {
        val h = s?.trim()?.removePrefix("#")?.takeIf { Regex("^[0-9A-Fa-f]{6}$").matches(it) } ?: return null
        return Color(0xFF000000 or h.toLong(16))
    }
    private val WORDS = mapOf("white" to Color.White, "black" to Color(0xFF111111), "red" to Color(0xFFD50000), "blue" to Color(0xFF1E4FC2),
        "navy" to Color(0xFF14254F), "yellow" to Color(0xFFFFD600), "green" to Color(0xFF1B8A3A), "orange" to Color(0xFFF57C00),
        "sky" to Color(0xFF6CABDD), "claret" to Color(0xFF7A1E3A), "maroon" to Color(0xFF7A1E3A), "purple" to Color(0xFF5B2A86), "gold" to Color(0xFFD4AF37))  // i18n-ignore

    /** one key per team; the answer (also "nothing") is kept 30 days */
    suspend fun get(t: Team, national: Boolean): KitColors? {
        val key = (if (national) "n:" else "c:") + (t.id.ifBlank { Sports.norm(t.name) })
        if (mem.containsKey(key)) return mem[key]
        val pk = "kit3_$key"
        val saved = Prefs.str(pk)
        if (saved.isNotBlank()) try {
            val o = JSONObject(saved)
            if (System.currentTimeMillis() - o.optLong("t") < 30L * 86_400_000L) {
                val k = if (o.has("b")) KitColors(Color(o.getInt("b")), Color(o.getInt("l")), Color(o.getInt("r")),
                    if (o.has("s")) Color(o.getInt("s")) else null, o.optString("p")) else null
                mem[key] = k; return k
            }
        } catch (_: Exception) {}
        val k = try { withContext(Dispatchers.IO) { fetch(t.name, national) } } catch (e: Exception) { null }
        mem[key] = k
        val o = JSONObject().put("t", System.currentTimeMillis())
        if (k != null) { o.put("b", k.body.toArgb()).put("l", k.left.toArgb()).put("r", k.right.toArgb()).put("p", k.pattern); k.stripe?.let { o.put("s", it.toArgb()) } }
        Prefs.put(pk, o.toString())
        return k
    }

    private fun api(q: String) = "https://en.wikipedia.org/w/api.php?format=json&" + q  // i18n-ignore

    private fun fetch(name: String, national: Boolean): KitColors? {
        val titles = if (national) listOf("$name national football team", "$name men's national football team")  // i18n-ignore
        else search("$name football club").take(3)  // i18n-ignore
        for (title in titles) {
            val wt = try {
                JSONObject(Http.request(api("action=parse&prop=wikitext&section=0&redirects=1&page=" + URLEncoder.encode(title, "UTF-8")), mapOf("User-Agent" to UA)))
                    .optJSONObject("parse")?.optJSONObject("wikitext")?.optString("*") ?: continue
            } catch (e: Exception) { continue }
            // must really be the infobox of a football team, and of this team
            val box = if (national) "infobox national football team" else "infobox football club"  // i18n-ignore
            if (!wt.lowercase().contains(box)) continue
            if (!national && !Sports.norm(title).split(' ').any { w -> w.length > 2 && Sports.norm(name).contains(w) }) continue
            fun p(k: String) = Regex("\\|\\s*$k\\s*=\\s*([^|\\n}]*)").find(wt)?.groupValues?.get(1)?.trim()
            val body = hex(p("body1")) ?: continue
            val pat = (p("pattern_b1") ?: "").lowercase()
            val word = WORDS.entries.firstOrNull { pat.contains(it.key) }?.value
            val kind = when {
                pat.contains("stripe") -> "stripes"; pat.contains("hoop") -> "hoops"; pat.contains("half") -> "half"; pat.contains("sash") -> "sash"
                else -> ""
            }
            return KitColors(body, hex(p("leftarm1")) ?: body, hex(p("rightarm1")) ?: body, if (kind.isNotEmpty()) word else null, if (word != null) kind else "")
        }
        return null
    }

    private fun search(q: String): List<String> = try {
        val a = JSONObject(Http.request(api("action=query&list=search&srlimit=3&srsearch=" + URLEncoder.encode(q, "UTF-8")), mapOf("User-Agent" to UA)))
            .optJSONObject("query")?.optJSONArray("search")
        if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("title") }
    } catch (e: Exception) { emptyList() }
}

/** The shirt: Wikipedia's home kit, or ESPN's colours while loading / when Wikipedia has none. */
@Composable
fun TeamKit(t: Team, national: Boolean, size: Dp) {
    val wiki by produceState<KitColors?>(null, t.id, t.name) { value = WikiKits.get(t, national) }
    val k = wiki ?: t.color?.let { c -> KitColors(c, t.color2 ?: c, t.color2 ?: c, null, "") } ?: return
    ShirtDrawing(k, Modifier.size(size))
}

@Composable
fun ShirtDrawing(k: KitColors, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width / 100f; val h = size.height / 100f
        fun P(vararg pts: Float) = Path().apply { moveTo(pts[0] * w, pts[1] * h); var i = 2; while (i < pts.size) { lineTo(pts[i] * w, pts[i + 1] * h); i += 2 }; close() }
        val body = Path().apply {
            moveTo(30 * w, 12 * h); lineTo(40 * w, 8 * h); quadraticBezierTo(50 * w, 18 * h, 60 * w, 8 * h); lineTo(70 * w, 12 * h)
            lineTo(76 * w, 38 * h); lineTo(76 * w, 94 * h); lineTo(24 * w, 94 * h); lineTo(24 * w, 38 * h); close()
        }
        val leftSleeve = P(30f, 12f, 6f, 28f, 15f, 46f, 26f, 40f)
        val rightSleeve = P(70f, 12f, 94f, 28f, 85f, 46f, 74f, 40f)
        drawPath(leftSleeve, k.left); drawPath(rightSleeve, k.right)
        drawPath(body, k.body)
        k.stripe?.let { s ->
            clipPath(body) {
                when (k.pattern) {
                    "stripes" -> { var x = 27f; while (x < 76f) { drawRect(s, Offset(x * w, 0f), androidx.compose.ui.geometry.Size(6 * w, size.height)); x += 12f } }
                    "hoops" -> { var y = 22f; while (y < 94f) { drawRect(s, Offset(0f, y * h), androidx.compose.ui.geometry.Size(size.width, 8 * h)); y += 16f } }
                    "half" -> drawRect(s, Offset(50 * w, 0f), androidx.compose.ui.geometry.Size(26 * w, size.height))
                    "sash" -> drawPath(P(24f, 30f, 36f, 24f, 76f, 82f, 76f, 94f, 66f, 94f, 24f, 42f), s)
                }
            }
        }
        // a little depth and a thin outline, so a white or black shirt still reads on graphite
        drawPath(body, Brush.horizontalGradient(listOf(Color(0x22FFFFFF), Color.Transparent, Color(0x26000000)), 24 * w, 76 * w))
        val line = Color(0x59FFFFFF)
        listOf(body, leftSleeve, rightSleeve).forEach { drawPath(it, line, style = Stroke(width = 1.2f * w)) }
        drawPath(Path().apply { moveTo(40 * w, 8 * h); quadraticBezierTo(50 * w, 18 * h, 60 * w, 8 * h) }, Color(0x66000000), style = Stroke(width = 2.2f * w))
    }
}
