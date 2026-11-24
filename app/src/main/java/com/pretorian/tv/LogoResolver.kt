package com.pretorian.tv

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 3.0 channel logos, in this order: the provider's logo (preferred whenever it loads), then a verified match from the
 * public iptv-org channel database (data under The Unlicense; the pictures stay their broadcasters' marks and are only
 * shown, never redistributed), then the existing text tile. Nothing about the user leaves the box: the catalog is
 * downloaded whole (no per-channel searches, no playlist names or links sent) and matched locally.
 *
 * Matching is strict. Names are compared after removing country prefixes, quality labels (HD, FHD, UHD, 4K, 1080p,
 * HEVC, H265…) and punctuation; "+1", numbers and regional words stay, so RTL / RTL 2 / RTL Hrvatska or Nova TV / Nova BH
 * never meet. When the channel carries a country (a "HR:" style prefix or a known country word in the group), only that
 * country's entry counts; without one the name must be unique in the whole catalog. Anything ambiguous = no logo (text tile).
 * Only PNG / JPEG / WebP over HTTPS are used (the app has no SVG decoder).
 *
 * The catalog is fetched at most every 14 days as a heavy background job (never during playback or startup), parsed
 * while streaming, and kept as a compact index (files/logo_index.tsv, at most 6 MB). A failed download waits 1 day, then 3.
 */
object LogoResolver {
    var rev by mutableIntStateOf(0)
    private const val CHANNELS = "https://iptv-org.github.io/api/channels.json"  // i18n-ignore
    private const val LOGOS = "https://iptv-org.github.io/api/logos.json"  // i18n-ignore
    private const val MAX_DOWNLOAD = 60L * 1024 * 1024
    private const val MAX_INDEX = 6L * 1024 * 1024
    private const val REFRESH_MS = 14L * 86_400_000L

    @Volatile private var index: Map<String, List<Pair<String, String>>>? = null
    @Volatile private var loading = false
    private val memo = java.util.concurrent.ConcurrentHashMap<String, String>()   // key → url or "" (no match)
    @Volatile private var app: Context? = null

    fun init(ctx: Context) { app = ctx.applicationContext }
    private fun file(ctx: Context) = File(ctx.filesDir, "logo_index.tsv")

    private val QUALITY = Regex("""\b(uhd|fhd|hd|sd|4k|8k|2160p|1080p|1080i|720p|576p|hevc|h\.?265|h\.?264|50fps|60fps|hdr|raw|backup|vip|multi|\*+)\b""")
    private val PREFIX = Regex("""^\s*[\[(|]?\s*([a-z]{2,3})\s*[\])|:]\s*[|:\-]?\s*""")
    private val COUNTRY_WORDS = mapOf("bosna" to "BA", "bosnia" to "BA", "bih" to "BA", "hrvatska" to "HR", "croatia" to "HR", "srbija" to "RS", "serbia" to "RS",  // i18n-ignore
        "crna gora" to "ME", "montenegro" to "ME", "slovenija" to "SI", "slovenia" to "SI", "makedonija" to "MK", "macedonia" to "MK", "deutschland" to "DE",  // i18n-ignore
        "germany" to "DE", "austria" to "AT", "österreich" to "AT", "schweiz" to "CH", "switzerland" to "CH", "turkey" to "TR", "türkiye" to "TR", "italia" to "IT",  // i18n-ignore
        "italy" to "IT", "france" to "FR", "spain" to "ES", "españa" to "ES", "united kingdom" to "GB", "romania" to "RO", "bulgaria" to "BG", "albania" to "AL",  // i18n-ignore
        "netherlands" to "NL", "poland" to "PL", "usa" to "US", "russia" to "RU", "hungary" to "HU", "greece" to "GR", "portugal" to "PT", "arabic" to "")  // i18n-ignore
    private val CODE_FIX = mapOf("UK" to "GB", "EXYU" to "", "EX" to "", "YU" to "", "BIH" to "BA", "SRB" to "RS", "CRO" to "HR", "HRV" to "HR", "GER" to "DE", "DEU" to "DE")  // i18n-ignore

    /** the name as it is compared (the displayed name never changes) */
    fun norm(name: String): String {
        var n = name.lowercase().replace('_', ' ')
        n = PREFIX.replace(n, "")
        n = QUALITY.replace(n, " ")
        return n.filter { it.isLetterOrDigit() || it == '+' }
    }
    /** a country written in the channel name or its group, or null */
    fun country(name: String, group: String?): String? {
        Regex("""^\s*[\[(|]?\s*([A-Za-z]{2,4})\s*[\])|:]""").find(name)?.groupValues?.get(1)?.uppercase()?.let { c ->  // i18n-ignore
            val f = CODE_FIX[c] ?: c; if (f.length == 2) return f
        }
        val g = (group ?: "").lowercase()
        COUNTRY_WORDS.entries.firstOrNull { (w, _) -> g.contains(w) }?.value?.let { if (it.isNotEmpty()) return it }
        Regex("""^\s*([a-z]{2,3})\s*[|:\-]""").find(g)?.groupValues?.get(1)?.uppercase()?.let { c -> val f = CODE_FIX[c] ?: c; if (f.length == 2) return f }  // i18n-ignore
        return null
    }

    /** pure matching rule (tested): one confident url or null */
    internal fun pick(cands: List<Pair<String, String>>?, country: String?): String? {
        if (cands.isNullOrEmpty()) return null
        val pool = if (country != null) cands.filter { it.first == country } else cands
        val urls = pool.map { it.second }.distinct()
        return urls.singleOrNull()
    }

    /** a verified external logo for this channel, or null; never blocks (the index loads in the background) */
    fun peek(name: String, group: String?): String? {
        val key = name + "\u0001" + (group ?: "")
        memo[key]?.let { return it.ifEmpty { null } }
        val idx = index ?: run { load(); return null }
        val n = norm(name); if (n.length < 2) { memo[key] = ""; return null }
        val c = country(name, group)
        val cands = idx[n]
        val r = pick(cands, c)
        PerfLog.count(when { r != null -> "logo external match"; cands.isNullOrEmpty() -> "logo no match"; else -> "logo ambiguous (text tile)" })  // i18n-ignore
        if (memo.size > 20_000) memo.clear()
        memo[key] = r ?: ""
        return r
    }

    private fun load() {
        val ctx = app ?: return
        if (loading) return
        loading = true
        Thread({
            try {
                val f = file(ctx)
                if (f.exists() && f.length() in 1..MAX_INDEX) {
                    val m = HashMap<String, MutableList<Pair<String, String>>>()
                    f.bufferedReader().useLines { s -> s.forEach { l -> val p = l.split('\t'); if (p.size == 3) m.getOrPut(p[0]) { ArrayList(1) }.add(p[1] to p[2]) } }
                    index = m; memo.clear(); rev++
                }
                val due = !f.exists() || System.currentTimeMillis() - f.lastModified() > REFRESH_MS
                if (due && System.currentTimeMillis() >= Prefs.long("logo_next_try", 0)) kotlinx.coroutines.runBlocking { Work.heavy("logo catalog") { refresh(ctx) } }  // i18n-ignore
            } catch (_: Throwable) {} finally { loading = false }
        }, "pretorian-logos").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
    }

    private fun open(u: String): HttpURLConnection {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 30_000; c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "MediaCenter/${BuildInfo.VERSION}")  // i18n-ignore
        if (c.responseCode != 200 || !(c.contentType ?: "").contains("json") || c.contentLengthLong > MAX_DOWNLOAD) { c.disconnect(); throw java.io.IOException("catalog " + c.responseCode) }  // i18n-ignore
        if (c.url.protocol != "https") { c.disconnect(); throw java.io.IOException("not https") }  // i18n-ignore
        return c
    }
    private class Capped(i: java.io.InputStream) : java.io.FilterInputStream(i) {
        var n = 0L
        override fun read(): Int { val r = super.read(); if (r >= 0 && ++n > MAX_DOWNLOAD) throw java.io.IOException("too big"); return r }  // i18n-ignore
        override fun read(b: ByteArray, o: Int, l: Int): Int { val r = super.read(b, o, l); if (r > 0) { n += r; if (n > MAX_DOWNLOAD) throw java.io.IOException("too big") }; return r }  // i18n-ignore
    }
    internal fun usable(url: String, format: String?): Boolean =
        url.startsWith("https://") && url.length < 400 && (format ?: url.substringAfterLast('.').substringBefore('?')).uppercase().let { it == "PNG" || it == "JPEG" || it == "JPG" || it == "WEBP" }  // i18n-ignore

    /** downloads both catalogs while streaming and writes the compact index */
    private fun refresh(ctx: Context) {
        val t0 = android.os.SystemClock.elapsedRealtime()
        try {
            val logos = HashMap<String, String>()
            open(LOGOS).let { c -> try { android.util.JsonReader(Capped(c.inputStream).bufferedReader()).use { r ->
                r.beginArray()
                while (r.hasNext()) {
                    var ch: String? = null; var feed: String? = null; var url: String? = null; var fmt: String? = null; var inUse = true
                    r.beginObject()
                    while (r.hasNext()) when (r.nextName()) {
                        "channel" -> ch = r.str(); "feed" -> feed = r.str(); "url" -> url = r.str(); "format" -> fmt = r.str()  // i18n-ignore
                        "in_use" -> inUse = if (r.peek() == android.util.JsonToken.BOOLEAN) r.nextBoolean() else { r.skipValue(); true }  // i18n-ignore
                        else -> r.skipValue()
                    }
                    r.endObject()
                    if (ch != null && url != null && feed == null && inUse && usable(url, fmt) && ch !in logos) logos[ch] = url
                }
                r.endArray()
            } } finally { c.disconnect() } }
            val tmp = File(ctx.filesDir, "logo_index.tmp")
            var rows = 0
            tmp.bufferedWriter().use { w ->
                open(CHANNELS).let { c -> try { android.util.JsonReader(Capped(c.inputStream).bufferedReader()).use { r ->
                    r.beginArray()
                    while (r.hasNext()) {
                        var id: String? = null; var name: String? = null; var country = ""; val alt = ArrayList<String>(); var skip = false
                        r.beginObject()
                        while (r.hasNext()) when (r.nextName()) {
                            "id" -> id = r.str(); "name" -> name = r.str(); "country" -> country = r.str() ?: ""  // i18n-ignore
                            "alt_names" -> if (r.peek() == android.util.JsonToken.BEGIN_ARRAY) { r.beginArray(); while (r.hasNext()) r.str()?.let { alt += it }; r.endArray() } else r.skipValue()  // i18n-ignore
                            "is_nsfw" -> if (r.peek() == android.util.JsonToken.BOOLEAN) { if (r.nextBoolean()) skip = true } else r.skipValue()  // i18n-ignore
                            "closed" -> if (r.peek() == android.util.JsonToken.NULL) r.nextNull() else { r.skipValue(); skip = true }  // i18n-ignore
                            else -> r.skipValue()
                        }
                        r.endObject()
                        val url = id?.let { logos[it] }
                        if (url != null && !skip && name != null) (listOf(name) + alt).map { norm(it) }.filter { it.length >= 2 }.distinct().forEach { n ->
                            w.write(n); w.write("\t"); w.write(country.uppercase()); w.write("\t"); w.write(url); w.write("\n"); rows++
                        }
                    }
                    r.endArray()
                } } finally { c.disconnect() } }
            }
            if (rows == 0 || tmp.length() > MAX_INDEX) { tmp.delete(); throw java.io.IOException("index empty or too big") }  // i18n-ignore
            tmp.renameTo(file(ctx))
            Prefs.put("logo_fail", 0L); index = null; memo.clear()
            PerfLog.task("logo catalog", "index", android.os.SystemClock.elapsedRealtime() - t0, "$rows names")  // i18n-ignore
            loading = false; load()
        } catch (e: Throwable) {
            val fails = Prefs.long("logo_fail", 0) + 1
            Prefs.put("logo_fail", fails); Prefs.put("logo_next_try", System.currentTimeMillis() + (if (fails <= 1) 1 else 3) * 86_400_000L)
            DiagEvents.add("net", "logo catalog not updated: " + e.javaClass.simpleName)  // i18n-ignore
        }
    }
    private fun android.util.JsonReader.str(): String? = if (peek() == android.util.JsonToken.NULL) { nextNull(); null } else if (peek() == android.util.JsonToken.STRING) nextString() else { skipValue(); null }

    fun stats(ctx: Context): String = "index: " + (file(ctx).takeIf { it.exists() }?.let { "${it.length() / 1024} KB, " + DiagEvents.stamp(it.lastModified()).take(10) } ?: "not downloaded") +  // i18n-ignore
        " · loaded " + (index?.size ?: 0) + " names · source iptv-org database (The Unlicense), pictures belong to their broadcasters"  // i18n-ignore
}

/**
 * Removes only fully see-through outer margins of a logo, so the mark itself fills the box (aspect kept, Fit).
 * Opaque backgrounds and the logo's own inner spacing stay untouched; a picture with no transparent margin is returned as is.
 */
class LogoTrim : coil.transform.Transformation {
    override val cacheKey = "logo-trim-v1"  // i18n-ignore
    override suspend fun transform(input: Bitmap, size: coil.size.Size): Bitmap = trim(input)
    companion object {
        fun trim(b: Bitmap): Bitmap {
            if (!b.hasAlpha() || b.width < 8 || b.height < 8 || b.width * b.height > 4_000_000) return b
            val w = b.width; val h = b.height
            val row = IntArray(w)
            fun rowEmpty(y: Int): Boolean { b.getPixels(row, 0, w, 0, y, w, 1); return row.all { (it ushr 24) < 10 } }
            var top = 0; while (top < h && rowEmpty(top)) top++
            if (top >= h) return b   // fully transparent: leave it, the text tile takes over on error only
            var bottom = h - 1; while (bottom > top && rowEmpty(bottom)) bottom--
            val col = IntArray(bottom - top + 1)
            fun colEmpty(x: Int): Boolean { b.getPixels(col, 0, 1, x, top, 1, col.size); return col.all { (it ushr 24) < 10 } }
            var left = 0; while (left < w && colEmpty(left)) left++
            var right = w - 1; while (right > left && colEmpty(right)) right--
            val cw = right - left + 1; val ch = bottom - top + 1
            if (cw == w && ch == h) return b
            if (cw < 4 || ch < 4) return b
            // a hair of breathing room so anti-aliased edges are never cut
            val pad = (maxOf(cw, ch) * 0.02f).toInt().coerceAtLeast(1)
            val l = (left - pad).coerceAtLeast(0); val t = (top - pad).coerceAtLeast(0)
            val r = (right + pad).coerceAtMost(w - 1); val bt = (bottom + pad).coerceAtMost(h - 1)
            return Bitmap.createBitmap(b, l, t, r - l + 1, bt - t + 1)
        }
    }
}
