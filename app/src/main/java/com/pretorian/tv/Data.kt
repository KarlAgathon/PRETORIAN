package com.pretorian.tv

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class SourceType { XTREAM, M3U, STALKER }

data class Playlist(
    val id: String,
    val name: String,
    val type: SourceType,
    val server: String = "",
    val user: String = "",
    val pass: String = "",
    val url: String = "",
    val active: Boolean = false,
    val live: Boolean = true,
    val vod: Boolean = true,
    val series: Boolean = true,
    /** subscription end as unix seconds, "" = unknown, "0" = never */
    val exp: String = "",
    /** Stalker portal: MAC address and the optional box identity */
    val mac: String = "",
    val serial: String = "",
    val deviceId: String = "",
    val deviceId2: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("type", type.name)
        put("server", server); put("user", user); put("pass", pass); put("url", url)
        put("active", active); put("live", live); put("vod", vod); put("series", series); put("exp", exp)
        put("mac", mac); put("serial", serial); put("devid", deviceId); put("devid2", deviceId2)
    }

    companion object {
        fun from(o: JSONObject) = Playlist(
            id = o.str("id"),
            name = o.str("name"),
            type = when (o.str("type")) { "M3U" -> SourceType.M3U; "STALKER" -> SourceType.STALKER; else -> SourceType.XTREAM },
            server = o.str("server"), user = o.str("user"), pass = o.str("pass"), url = o.str("url"),
            active = o.optBoolean("active", false),
            live = o.optBoolean("live", true), vod = o.optBoolean("vod", true), series = o.optBoolean("series", true),
            exp = o.str("exp"),
            mac = o.str("mac"), serial = o.str("serial"), deviceId = o.str("devid"), deviceId2 = o.str("devid2"),
        )
    }
}

data class Category(val id: String, val name: String)
data class Channel(val id: String, val num: Int, val name: String, val logo: String?, val group: String, val url: String, val epg: String? = null, val pid: String = "", val archive: Int = 0)
data class Vod(val id: String, val name: String, val poster: String?, val rating: String?, val url: String, val added: Long = 0)
data class SeriesItem(val id: String, val name: String, val cover: String?, val plot: String?, val rating: String?, val added: Long = 0)
data class Episode(val id: String, val season: Int, val num: Int, val title: String, val url: String, val plot: String?,
                   val image: String? = null, val durSec: Int = 0, val air: String? = null)
data class VodInfo(
    val plot: String?, val genre: String?, val year: String?, val duration: String?,
    val cast: String?, val director: String?, val backdrop: String?, val rating: String?,
    /** picture and sound from the provider's file info: "4K", "HDR", "5.1", "Atmos"… */
    val marks: List<String> = emptyList(),
)

/** "4K · HDR · 5.1 · Dolby Atmos" from the video / audio info a provider gives (ffprobe style). */
fun avMarks(video: JSONObject?, audio: JSONObject?): List<String> {
    val out = ArrayList<String>()
    val h = video?.optInt("height") ?: 0
    if (h >= 2000) out += "4K" else if (h >= 1000) out += "FHD" else if (h >= 700) out += "HD"  // i18n-ignore
    val tr = (video?.optString("color_transfer") ?: "").lowercase()
    if (tr.contains("smpte2084") || tr.contains("arib-std-b67")) out += "HDR"  // i18n-ignore
    val ch = audio?.optInt("channels") ?: 0
    val codec = (audio?.optString("codec_name") ?: "").lowercase()
    val prof = (audio?.optString("profile") ?: "").lowercase()
    if (ch >= 8) out += "7.1" else if (ch >= 6) out += "5.1"  // i18n-ignore
    when {
        prof.contains("atmos") || prof.contains("joc") -> out += "Dolby Atmos"  // i18n-ignore
        codec == "truehd" -> out += "Dolby TrueHD"  // i18n-ignore
        codec == "eac3" || codec == "ac3" -> out += "Dolby"  // i18n-ignore
        codec.startsWith("dts") -> out += "DTS"  // i18n-ignore
    }
    return out
}
data class EpgItem(val title: String, val desc: String?, val start: Long, val end: Long, val server: String? = null, val archived: Boolean = false) {
    fun times(): String {
        val f = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        return "${f.format(java.util.Date(start))}–${f.format(java.util.Date(end))}"
    }
    fun progress(now: Long = System.currentTimeMillis()): Float = if (end <= start) 0f else ((now - start).toFloat() / (end - start)).coerceIn(0f, 1f)
}

data class PlayItem(
    val title: String, val url: String, val logo: String? = null, val subtitle: String? = null, val channel: Channel? = null,
    /** title used to search subtitles, plus season/episode for series */
    val search: String? = null, val season: Int? = null, val episode: Int? = null,
    /** subtitle file chosen before playing, and the chosen audio track (language + label) */
    val subFile: String? = null, val audioLang: String? = null,
    val audioLabel: String? = null,
    /** "no subtitles" was chosen */
    val subOff: Boolean = false,
    /** built-in subtitle chosen before playing: language + label */
    val subLang: String? = null, val subLabel: String? = null,
)

fun JSONObject.str(k: String): String = if (!has(k) || isNull(k)) "" else optString(k, "")
fun String.orNullIfBlank(): String? = if (isBlank()) null else this

const val FAV_GROUP = "__fav__"

/** Settings and saved lists (SharedPreferences, JSON). */
object Store {
    private fun prefs(c: Context) = c.getSharedPreferences("pretorian", Context.MODE_PRIVATE)

    fun playlists(c: Context): List<Playlist> = try {
        val a = JSONArray(prefs(c).getString("playlists", "[]") ?: "[]")
        (0 until a.length()).map { Playlist.from(a.getJSONObject(it)) }
    } catch (e: Exception) { emptyList() }

    fun savePlaylists(c: Context, list: List<Playlist>) {
        val a = JSONArray(); list.forEach { a.put(it.toJson()) }
        prefs(c).edit().putString("playlists", a.toString()).apply()
    }

    /** turned-on playlists, the opened one first; empty when all are switched off */
    fun actives(c: Context): List<Playlist> {
        val on = playlists(c).filter { it.active }
        val open = Prefs.str("pl_open")
        return on.sortedBy { if (it.id == open) 0 else 1 }
    }

    /** the opened playlist (the one whose groups are unfolded) */
    fun active(c: Context): Playlist? = actives(c).firstOrNull()

    fun byId(c: Context, id: String?): Playlist? = playlists(c).firstOrNull { it.id == id } ?: active(c)

    /** several playlists are on at once: adding or switching one on never switches the others off */
    var multi: Boolean get() = true; set(@Suppress("UNUSED_PARAMETER") v) {}

    fun open(c: Context, id: String) { Prefs.put("pl_open", id) }

    /** switches one playlist on or off; the others stay as they are (all may be off) */
    fun activate(c: Context, id: String) {
        val l = playlists(c).map { if (it.id == id) it.copy(active = !it.active) else it }
        savePlaylists(c, l)
        if (l.firstOrNull { it.id == id }?.active == true) open(c, id)
    }
    fun setAll(c: Context, on: Boolean) = savePlaylists(c, playlists(c).map { it.copy(active = on) })

    /** the playlist a film or episode link belongs to (Xtream links carry the server and user), else null */
    fun ownerOf(c: Context, url: String): Playlist? = playlists(c).firstOrNull { p ->
        when {
            p.type == SourceType.XTREAM && p.server.isNotBlank() -> url.startsWith(p.server.trimEnd('/')) && url.contains("/" + p.user + "/")
            p.type == SourceType.STALKER && p.server.isNotBlank() -> url.contains(p.server.substringAfter("://").substringBefore('/'))
            else -> false
        }
    }

    /** has its subscription ended? (Xtream tells the end date) */
    fun expired(p: Playlist): Boolean = p.exp.toLongOrNull()?.let { it > 0 && it * 1000 < System.currentTimeMillis() } ?: false

    fun movePlaylist(c: Context, id: String, d: Int) {
        val l = playlists(c).toMutableList(); val i = l.indexOfFirst { it.id == id }; val j = i + d
        if (i < 0 || j !in l.indices) return
        l.add(j, l.removeAt(i)); savePlaylists(c, l)
    }

    fun favorites(c: Context, playlistId: String): List<Channel> = try {
        val a = JSONArray(prefs(c).getString("fav_$playlistId", "[]") ?: "[]")
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Channel(o.str("id"), it + 1, o.str("name"), o.str("logo").orNullIfBlank(), FAV_GROUP, o.str("url"), o.str("epg").orNullIfBlank())
        }
    } catch (e: Exception) { emptyList() }

    fun renameFavorite(c: Context, playlistId: String, ch: Channel, name: String) =
        saveFavorites(c, playlistId, favorites(c, playlistId).map { if (it.url == ch.url) it.copy(name = name) else it })

    fun isFavorite(c: Context, playlistId: String, ch: Channel) = favorites(c, playlistId).any { it.url == ch.url }

    private fun saveFavorites(c: Context, playlistId: String, list: List<Channel>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("logo", it.logo ?: "").put("url", it.url).put("epg", it.epg ?: "")) }
        prefs(c).edit().putString("fav_$playlistId", a.toString()).apply()
    }

    fun toggleFavorite(c: Context, playlistId: String, ch: Channel) {
        val now = favorites(c, playlistId).toMutableList()
        if (now.removeAll { it.url == ch.url }.not()) now.add(ch)
        saveFavorites(c, playlistId, now)
    }

    fun moveFavorite(c: Context, playlistId: String, ch: Channel, delta: Int) {
        val now = favorites(c, playlistId).toMutableList()
        val i = now.indexOfFirst { it.url == ch.url }; val j = i + delta
        if (i < 0 || j !in now.indices) return
        val x = now.removeAt(i); now.add(j, x)
        saveFavorites(c, playlistId, now)
    }
}

object Net {
    private const val UA_DEFAULT = "Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36 PRETORIAN/0.1"  // i18n-ignore
    /** Settings › General › Network: own User-Agent for providers that only let certain apps in (empty = PRETORIAN's) */
    val UA get() = Prefs.str("net_ua").trim().ifBlank { UA_DEFAULT }
    /** udp://@239.1.1.1:1234 → http://proxy/udp/239.1.1.1:1234 (udpxy on the router), when a proxy is set */
    fun udp(url: String): String {
        val px = Prefs.str("net_udpxy").trim().removeSuffix("/")
        if (px.isBlank() || !(url.startsWith("udp://") || url.startsWith("rtp://"))) return url
        val addr = url.substringAfter("://").removePrefix("@")
        return (if (px.startsWith("http")) px else "http://$px") + "/" + (if (url.startsWith("rtp")) "rtp" else "udp") + "/" + addr
    }

    fun get(url: String): String = open(url) { it.bufferedReader().use { r -> r.readText() } }

    /** opens [url] (following redirects) and hands the body as a stream: big lists are read piece by piece, never whole */
    fun <T> open(url: String, read: (java.io.InputStream) -> T): T {
        var current = url
        repeat(5) {
            val c = URL(current).openConnection() as HttpURLConnection
            c.connectTimeout = 15000
            c.readTimeout = 90000
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", UA)
            try {
                val code = c.responseCode
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location") ?: throw IOException("Preusmjerenje bez adrese")
                    current = URL(URL(current), loc).toString()
                    return@repeat
                }
                if (code !in 200..299) throw IOException(trf("Server je odgovorio greškom %s", code))
                return c.inputStream.use(read)
            } finally {
                c.disconnect()
            }
        }
        throw IOException("Previše preusmjeravanja")
    }

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}

class Xtream(private val p: Playlist) {
    private val base = p.server.trim().trimEnd('/').let { if (it.startsWith("http")) it else "http://$it" }

    private fun api(action: String?, extra: String = ""): String =
        Net.get("$base/player_api.php?username=${Net.enc(p.user)}&password=${Net.enc(p.pass)}" +
            (action?.let { "&action=$it" } ?: "") + extra)

    /** slow servers (e.g. an editor in front of the provider): one more try before giving up */
    private fun retry2(f: () -> JSONObject): JSONObject = try { f() } catch (e: Exception) { try { Thread.sleep(800); f() } catch (e2: Exception) { JSONObject() } }

    private fun array(text: String): JSONArray = try { JSONArray(text) } catch (e: Exception) { JSONArray() }

    fun login(): String {
        val o = try { JSONObject(api(null)) } catch (e: Exception) { throw IOException("Server nije vratio Xtream odgovor. Provjeri adresu.") }
        val ui = o.optJSONObject("user_info") ?: throw IOException("Server nije vratio podatke o korisniku")
        if (ui.optInt("auth", 0) != 1) throw IOException("Pogrešno korisničko ime ili lozinka")
        val status = ui.str("status")
        if (status.isNotBlank() && !status.equals("Active", true)) throw IOException(trf("Nalog nije aktivan: %s", status))
        val exp = ui.str("exp_date")
        return if (exp.isBlank() || exp == "null") "0" else exp
    }

    /** coming programmes of a live stream (Xtream short EPG; title and description are base64) */
    fun epg(streamId: String, limit: Int): List<EpgItem> {
        val o = try { JSONObject(api("get_short_epg", "&stream_id=${Net.enc(streamId)}&limit=$limit")) } catch (e: Exception) { return emptyList() }  // i18n-ignore
        val a = o.optJSONArray("epg_listings") ?: return emptyList()
        fun b64(s: String) = try { String(android.util.Base64.decode(s, android.util.Base64.DEFAULT)).trim() } catch (e: Exception) { s }
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        return (0 until a.length()).mapNotNull {
            val e = a.getJSONObject(it)
            val start = e.str("start_timestamp").toLongOrNull()?.times(1000) ?: try { fmt.parse(e.str("start"))?.time } catch (x: Exception) { null } ?: return@mapNotNull null
            val end = e.str("stop_timestamp").toLongOrNull()?.times(1000) ?: try { fmt.parse(e.str("end"))?.time } catch (x: Exception) { null } ?: start
            EpgItem(b64(e.str("title")), b64(e.str("description")).orNullIfBlank(), start, end)
        }.filter { it.end > System.currentTimeMillis() - 60_000 }
    }

    private fun cats(action: String): List<Category> {
        val a = array(api(action))
        return (0 until a.length()).map { val o = a.getJSONObject(it); Category(o.str("category_id"), o.str("category_name")) }
    }

    fun liveCategories() = cats("get_live_categories")
    fun vodCategories() = cats("get_vod_categories")
    fun seriesCategories() = cats("get_series_categories")

    fun channels(categoryId: String): List<Channel> {
        val a = array(api("get_live_streams", "&category_id=${Net.enc(categoryId)}"))
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val id = o.str("stream_id")
            Channel(id, o.optInt("num", i + 1), o.str("name"), o.str("stream_icon").orNullIfBlank(), categoryId,
                "$base/live/${p.user}/${p.pass}/$id.ts", o.str("epg_channel_id").orNullIfBlank(), p.id)
        }
    }

    /** every live channel of the account (for the sports guide) */
    fun allChannels(): List<Channel> {
        val a = array(api("get_live_streams"))
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val id = o.str("stream_id")
            Channel(id, o.optInt("num", i + 1), o.str("name"), o.str("stream_icon").orNullIfBlank(), o.str("category_id"),
                "$base/live/${p.user}/${p.pass}/$id.ts", o.str("epg_channel_id").orNullIfBlank(), p.id,
                if (o.optInt("tv_archive", 0) == 1) o.optInt("tv_archive_duration", 1).coerceAtLeast(1) else 0)
        }
    }

    /** short list of every movie or series: id, category, name, image, rating, url (for the "new" badges) */
    fun catalog(kind: String): List<JSONObject> { val out = ArrayList<JSONObject>(); catalogEach(kind) { out += it }; return out }

    /**
     * every movie or series of the account, one at a time. The list can be hundreds of MB,
     * so it is read as a stream with only the few fields we need kept (a whole copy would not fit in a TV box's memory).
     */
    fun catalogEach(kind: String, each: (JSONObject) -> Unit) {
        val url = "$base/player_api.php?username=${Net.enc(p.user)}&password=${Net.enc(p.pass)}&action=" +
            (if (kind == "vod") "get_vod_streams" else "get_series")
        val want = setOf("stream_id", "series_id", "category_id", "name", "stream_icon", "cover", "rating", "container_extension")
        Net.open(url) { input ->
            val r = android.util.JsonReader(java.io.InputStreamReader(input, Charsets.UTF_8))
            r.isLenient = true
            if (r.peek() != android.util.JsonToken.BEGIN_ARRAY) return@open
            r.beginArray()
            while (r.hasNext()) {
                if (r.peek() != android.util.JsonToken.BEGIN_OBJECT) { r.skipValue(); continue }
                val f = HashMap<String, String>()
                r.beginObject()
                while (r.hasNext()) {
                    val n = r.nextName()
                    if (n in want) when (r.peek()) {
                        android.util.JsonToken.STRING, android.util.JsonToken.NUMBER -> f[n] = r.nextString()
                        android.util.JsonToken.BOOLEAN -> f[n] = r.nextBoolean().toString()
                        else -> r.skipValue()
                    } else r.skipValue()
                }
                r.endObject()
                val o = if (kind == "vod") {
                    val id = f["stream_id"] ?: continue; val ext = f["container_extension"].orEmpty().ifBlank { "mp4" }
                    JSONObject().put("id", id).put("c", f["category_id"].orEmpty()).put("n", f["name"].orEmpty()).put("i", f["stream_icon"].orEmpty())
                        .put("r", f["rating"].orEmpty()).put("u", "$base/movie/${p.user}/${p.pass}/$id.$ext")
                } else JSONObject().put("id", f["series_id"] ?: continue).put("c", f["category_id"].orEmpty()).put("n", f["name"].orEmpty())
                    .put("i", f["cover"].orEmpty()).put("r", f["rating"].orEmpty())
                each(o)
            }
        }
    }

    /** past programmes of a channel with archive (catch-up) */
    fun archive(streamId: String): List<EpgItem> {
        val raw = api("get_simple_data_table", "&stream_id=${Net.enc(streamId)}")  // i18n-ignore
        val a = try { JSONObject(raw).optJSONArray("epg_listings") } catch (e: Exception) { try { org.json.JSONArray(raw) } catch (x: Exception) { null } } ?: return emptyList()
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        fun b64(s: String): String {
            if (s.isBlank()) return s
            return try {
                val d = String(android.util.Base64.decode(s, android.util.Base64.DEFAULT), Charsets.UTF_8).trim()
                if (d.isNotEmpty() && d.none { it == '\uFFFD' || (it < ' ' && it != '\n' && it != '\r' && it != '\t') }) d else s
            } catch (e: Exception) { s }
        }
        fun ts(e: JSONObject, k: String, txt: String): Long? {
            e.opt(k)?.toString()?.toLongOrNull()?.let { return if (it < 10_000_000_000L) it * 1000 else it }
            val t = e.str(txt).ifBlank { return null }
            t.toLongOrNull()?.let { return if (it < 10_000_000_000L) it * 1000 else it }
            return try { fmt.parse(t)?.time } catch (x: Exception) { null }
        }
        val items = (0 until a.length()).mapNotNull {
            val e = a.optJSONObject(it) ?: return@mapNotNull null
            val start = ts(e, "start_timestamp", "start") ?: return@mapNotNull null
            val end = ts(e, "stop_timestamp", "end") ?: ts(e, "stop_timestamp", "stop") ?: start
            val flag = e.opt("has_archive")?.toString()
            // the server's own clock text is what the archive link wants
            EpgItem(b64(e.str("title")), b64(e.str("description")).orNullIfBlank(), start, end, e.str("start").takeIf { it.length >= 16 }, flag == null || flag == "1" || flag == "true")
        }
        // some servers never mark has_archive: then every past show in the archive window counts
        val now = System.currentTimeMillis()
        return if (items.none { it.archived && it.end <= now }) items.map { it.copy(archived = true) } else items
    }

    /** past shows from the account's own XMLTV, when the archive table is empty */
    fun archiveFromGuide(epgId: String?, days: Int): List<EpgItem> {
        val id = epgId?.lowercase()?.ifBlank { null } ?: return emptyList()
        val now = System.currentTimeMillis()
        return try {
            Xmltv.read(xmltvUrl(), setOf(id), now - days * 86_400_000L, now).filter { it.end <= now }
                .map { EpgItem(it.title, it.desc, it.start, it.end, null, true) }
        } catch (e: Exception) { emptyList() }
    }

    /** link to a past programme: /timeshift/user/pass/minutes/YYYY-MM-DD:HH-MM/stream.ts */
    fun timeshiftUrl(streamId: String, e: EpgItem): String {
        val t = e.server?.takeIf { it.length >= 16 }?.let { it.substring(0, 10) + ":" + it.substring(11, 13) + "-" + it.substring(14, 16) }
            ?: java.text.SimpleDateFormat("yyyy-MM-dd:HH-mm", java.util.Locale.US).format(java.util.Date(e.start))
        val minutes = ((e.end - e.start) / 60_000).coerceAtLeast(1)
        return "$base/timeshift/${p.user}/${p.pass}/$minutes/$t/$streamId.ts"
    }

    /** full TV guide of the account, XMLTV */
    fun xmltvUrl() = "$base/xmltv.php?username=${Net.enc(p.user)}&password=${Net.enc(p.pass)}"  // i18n-ignore

    fun movies(categoryId: String): List<Vod> {
        val a = array(api("get_vod_streams", "&category_id=${Net.enc(categoryId)}"))
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val id = o.str("stream_id")
            val ext = o.str("container_extension").ifBlank { "mp4" }
            Vod(id, o.str("name"), o.str("stream_icon").orNullIfBlank(), o.str("rating").orNullIfBlank(),
                "$base/movie/${p.user}/${p.pass}/$id.$ext", o.str("added").toLongOrNull() ?: 0L)
        }
    }

    fun movieInfo(id: String): VodInfo {
        val o = retry2 { JSONObject(api("get_vod_info", "&vod_id=${Net.enc(id)}")) }
        val i = o.optJSONObject("info") ?: JSONObject()
        val backdrop = i.optJSONArray("backdrop_path")?.let { if (it.length() > 0) it.optString(0) else null }
        return VodInfo(
            plot = i.str("plot").ifBlank { i.str("description") }.orNullIfBlank(),
            genre = i.str("genre").orNullIfBlank(),
            year = i.str("releasedate").take(4).orNullIfBlank(),
            duration = i.str("duration").orNullIfBlank(),
            cast = i.str("cast").ifBlank { i.str("actors") }.orNullIfBlank(),
            director = i.str("director").orNullIfBlank(),
            backdrop = backdrop?.orNullIfBlank() ?: i.str("movie_image").orNullIfBlank(),
            rating = i.str("rating").orNullIfBlank(),
            marks = avMarks(i.optJSONObject("video"), i.optJSONObject("audio")),
        )
    }

    fun series(categoryId: String): List<SeriesItem> {
        val a = array(api("get_series", "&category_id=${Net.enc(categoryId)}"))
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            SeriesItem(o.str("series_id"), o.str("name"), o.str("cover").orNullIfBlank(), o.str("plot").orNullIfBlank(),
                o.str("rating").orNullIfBlank(), o.str("last_modified").toLongOrNull() ?: 0L)
        }
    }

    fun episodes(seriesId: String): List<Episode> {
        val o = retry2 { JSONObject(api("get_series_info", "&series_id=${Net.enc(seriesId)}")) }
        val eps = o.optJSONObject("episodes") ?: return emptyList()
        val out = ArrayList<Episode>()
        eps.keys().forEach { key ->
            val arr = eps.optJSONArray(key) ?: return@forEach
            for (j in 0 until arr.length()) {
                val e = arr.getJSONObject(j)
                val id = e.str("id")
                val ext = e.str("container_extension").ifBlank { "mp4" }
                val info = e.optJSONObject("info")
                val dur = info?.optInt("duration_secs", 0)?.takeIf { it > 0 }
                    ?: info?.str("duration")?.split(':')?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 3 }?.let { it[0] * 3600 + it[1] * 60 + it[2] } ?: 0
                out.add(Episode(id, key.toIntOrNull() ?: e.optInt("season", 1), e.optInt("episode_num", j + 1),
                    e.str("title"), "$base/series/${p.user}/${p.pass}/$id.$ext", info?.str("plot")?.orNullIfBlank(),
                    info?.str("movie_image")?.orNullIfBlank(), dur, (info?.str("releasedate") ?: info?.str("air_date"))?.take(10)?.orNullIfBlank()))
            }
        }
        return out.sortedWith(compareBy({ it.season }, { it.num }))
    }
}

object M3u {
    private val attrCache = HashMap<String, Regex>()

    private fun attr(line: String, key: String): String? {
        val r = attrCache.getOrPut(key) { Regex("$key=\"([^\"]*)\"") }
        return r.find(line)?.groupValues?.get(1)?.orNullIfBlank()
    }

    fun parse(text: String): List<Channel> {
        val out = ArrayList<Channel>()
        var name = ""
        var logo: String? = null
        var epgId: String? = null
        var group = "Ostalo"
        var pending = false
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.startsWith("#EXTINF", true)) {
                val lastQuote = line.lastIndexOf('"').coerceAtLeast(0)
                val comma = line.indexOf(',', lastQuote)
                name = if (comma >= 0) line.substring(comma + 1).trim() else line
                logo = attr(line, "tvg-logo")
                epgId = attr(line, "tvg-id")
                group = attr(line, "group-title") ?: "Ostalo"
                pending = true
            } else if (line.isNotEmpty() && !line.startsWith("#") && pending) {
                out.add(Channel(out.size.toString(), out.size + 1, name, logo, group, line, epgId))
                pending = false
            }
        }
        if (out.isEmpty() && !text.trimStart().startsWith("#EXTM3U")) throw IOException("Link ne vraća M3U listu")
        return out
    }
}

/** Loads content of the active playlist, with a small in-memory cache. */
object Repo {
    private val cache = HashMap<String, Any>()
    private var m3uFor: String? = null
    private var m3u: List<Channel> = emptyList()

    /** small changes (EPG, favourites, settings): TV groups and channel lists stay, so TV opens at once */
    fun clear() { synchronized(cache) { cache.keys.removeAll { !isLive(it) } }; catalogs.clear(); HomeCache.clear() }
    private fun isLive(k: String) = k.startsWith("lg_") || k.startsWith("ch_") || k.startsWith("all_")

    /** frees memory: always the big catalogs, and with [hard] also films, series and all-channel lists (groups and the open channels stay) */
    fun trim(hard: Boolean) {
        catalogs.clear()
        if (hard) synchronized(cache) { cache.keys.removeAll { k -> k.startsWith("all_") || k.startsWith("mv_") || k.startsWith("se_") || k.startsWith("ep_") || k.startsWith("mi_") } }
    }

    /** the whole film / series catalog is big: it stays on the disk and in memory only as long as Android has room (soft) */
    private val catalogs = HashMap<String, java.lang.ref.SoftReference<List<JSONObject>>>()

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> cached(key: String, load: () -> T): T = withContext(Dispatchers.IO) {
        (synchronized(cache) { cache[key] } as T?) ?: load().also { synchronized(cache) { cache[key] = it as Any } }
    }

    private fun m3uChannels(p: Playlist): List<Channel> {
        if (m3uFor != p.id) { m3u = M3u.parse(m3uText(p.url.trim())).map { it.copy(pid = p.id) }; m3uFor = p.id }
        return m3u
    }

    suspend fun liveGroups(p: Playlist): List<Category> = disk("lg_${p.id}", ::catJson, ::catFrom) {
        if (p.type == SourceType.XTREAM) Xtream(p).liveCategories()
        else if (p.type == SourceType.STALKER) Stalker(p).liveCategories()
        else m3uChannels(p).map { it.group }.distinct().map { Category(it, it) }
    }

    suspend fun channels(p: Playlist, groupId: String): List<Channel> = disk("ch_${p.id}_$groupId", ::chJson, ::chFrom) {
        if (p.type == SourceType.XTREAM) Xtream(p).channels(groupId)
        else if (p.type == SourceType.STALKER) ((cache["all_${p.id}"] as? List<*>)?.filterIsInstance<Channel>() ?: Stalker(p).allChannels().also { cache["all_${p.id}"] = it }).filter { it.group == groupId }
        else m3uChannels(p).filter { it.group == groupId }
    }.let { l -> Parental.channels(p, l) }

    /** every live channel of the playlist ("All channels", sports search) */
    suspend fun allChannels(p: Playlist): List<Channel> = rawAllChannels(p).let { l -> Parental.channels(p, l) }
    private suspend fun rawAllChannels(p: Playlist): List<Channel> = disk("all_${p.id}", ::chJson, ::chFrom) {
        if (p.type == SourceType.XTREAM) Xtream(p).allChannels() else if (p.type == SourceType.STALKER) Stalker(p).allChannels() else m3uChannels(p)
    }

    /** every movie / series of the playlist (search) */
    /** Folder for lists kept on the box between starts (search catalog). Set at start. */
    var diskDir: java.io.File? = null

    /** Every movie or series of a playlist. Kept in memory and on the box for 24 hours, so search is instant. */
    suspend fun catalog(p: Playlist, kind: String): List<JSONObject> = rawCatalog(p, kind).let { l -> Parental.catalog(p, kind, l) }
    private suspend fun rawCatalog(p: Playlist, kind: String): List<JSONObject> =
        if (p.type != SourceType.XTREAM) emptyList() else withContext(Dispatchers.IO) {
            val key = "cat_${kind}_${p.id}"
            catalogs[key]?.get()?.let { return@withContext it }
            val f = diskDir?.let { java.io.File(it, "catalog_${kind}_${p.id.hashCode()}.json") }
            val fresh = f != null && f.exists() && System.currentTimeMillis() - f.lastModified() < 24 * 3600_000L
            val fromDisk = if (fresh) try { org.json.JSONArray(f!!.readText()).let { a -> (0 until a.length()).map { a.getJSONObject(it) } } } catch (e: Throwable) { null } else null
            val list = fromDisk ?: Xtream(p).catalog(kind).also { list ->
                try { f?.writeText(org.json.JSONArray(list).toString()) } catch (_: Exception) {}
            }
            catalogs[key] = java.lang.ref.SoftReference(list)
            list
        }

    /** how many movies or series a playlist has, counted while reading, nothing kept in memory */
    suspend fun catalogCount(p: Playlist, kind: String): Int =
        if (p.type != SourceType.XTREAM) 0 else cached("cnt_${kind}_${p.id}") { var n = 0; Xtream(p).catalogEach(kind) { n++ }; n }

    /** "Update all": forget the kept catalog so it is downloaded again. */
    /** "Refresh" of a playlist: everything is read again from the provider (also the TV lists kept on the disk) */
    fun dropCatalogs() {
        diskDir?.listFiles()?.filter { it.name.startsWith("catalog_") || it.name.startsWith("live_") }?.forEach { it.delete() }
        synchronized(cache) { cache.clear() }; m3uFor = null; m3u = emptyList()
    }

    /* ---- TV lists on the disk: shown at once (also after a restart), read again quietly when older than 12 hours */
    private fun liveFile(key: String) = diskDir?.let { java.io.File(it, "live_" + key.hashCode().toUInt().toString(16) + ".json") }
    private fun catJson(l: List<Category>) = JSONArray().apply { l.forEach { put(JSONArray().put(it.id).put(it.name)) } }.toString()
    private fun catFrom(t: String) = JSONArray(t).let { a -> (0 until a.length()).map { a.getJSONArray(it).let { x -> Category(x.getString(0), x.getString(1)) } } }
    private fun chJson(l: List<Channel>) = JSONArray().apply { l.forEach { c -> put(JSONArray().put(c.id).put(c.num).put(c.name).put(c.logo ?: "").put(c.group).put(c.url).put(c.epg ?: "").put(c.pid).put(c.archive)) } }.toString()
    private fun chFrom(t: String) = JSONArray(t).let { a -> (0 until a.length()).map { a.getJSONArray(it).let { x ->
        Channel(x.getString(0), x.getInt(1), x.getString(2), x.getString(3).ifBlank { null }, x.getString(4), x.getString(5), x.getString(6).ifBlank { null }, x.getString(7), x.getInt(8)) } } }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> disk(key: String, write: (T) -> String, read: (String) -> T, load: () -> T): T = withContext(Dispatchers.IO) {
        (synchronized(cache) { cache[key] } as T?)?.let { return@withContext it }
        val f = liveFile(key)
        val kept = try { if (f != null && f.exists() && System.currentTimeMillis() - f.lastModified() < 7 * 86_400_000L) read(f.readText()) else null } catch (e: Exception) { null }
        if (kept != null) {
            synchronized(cache) { cache[key] = kept as Any }
            // older than 12 hours: read again in the background, the next opening gets the new list
            if (System.currentTimeMillis() - f!!.lastModified() > 12 * 3600_000L) {
                @Suppress("OPT_IN_USAGE")
                kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) { try { val fresh = load(); f.writeText(write(fresh)); synchronized(cache) { cache[key] = fresh as Any } } catch (_: Exception) {} }
            }
            return@withContext kept
        }
        val fresh = load()
        synchronized(cache) { cache[key] = fresh as Any }
        try { f?.writeText(write(fresh)) } catch (_: Exception) {}
        fresh
    }

    suspend fun vodGroups(p: Playlist): List<Category> =
        if (p.type == SourceType.XTREAM) cached("vg_${p.id}") { Xtream(p).vodCategories() }
        else if (p.type == SourceType.STALKER) cached("vg_${p.id}") { Stalker(p).vodCategories() } else emptyList()

    suspend fun movies(p: Playlist, groupId: String): List<Vod> = cached("mv_${p.id}_$groupId") {
        if (p.type == SourceType.STALKER) Stalker(p).movies(groupId) else Xtream(p).movies(groupId)
    }.let { l -> if (!Parental.active) l else if (groupId in Parental.blocked(p, "vod")) emptyList() else l.filter { !Parental.hides(it.name) } }

    suspend fun movieInfo(p: Playlist, id: String): VodInfo = keepIf("mi_${p.id}_$id", { it.plot != null || it.year != null || it.genre != null }) {
        if (p.type == SourceType.STALKER) VodInfo(null, null, null, null, null, null, null, null) else Xtream(p).movieInfo(id)
    }

    /** like cached, but an empty answer (server busy) is not kept: the next opening asks again */
    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> keepIf(key: String, ok: (T) -> Boolean, load: () -> T): T = withContext(Dispatchers.IO) {
        (synchronized(cache) { cache[key] } as T?) ?: load().also { if (ok(it)) synchronized(cache) { cache[key] = it as Any } }
    }

    suspend fun seriesGroups(p: Playlist): List<Category> =
        if (p.type == SourceType.XTREAM) cached("sg_${p.id}") { Xtream(p).seriesCategories() }
        else if (p.type == SourceType.STALKER) cached("sg_${p.id}") { Stalker(p).seriesCategories() } else emptyList()

    suspend fun series(p: Playlist, groupId: String): List<SeriesItem> = cached("se_${p.id}_$groupId") {
        if (p.type == SourceType.STALKER) Stalker(p).series(groupId) else Xtream(p).series(groupId)
    }.let { l -> if (!Parental.active) l else if (groupId in Parental.blocked(p, "series")) emptyList() else l.filter { !Parental.hides(it.name) } }

    suspend fun episodes(p: Playlist, id: String): List<Episode> = keepIf("ep_${p.id}_$id", { l: List<Episode> -> l.isNotEmpty() }) {
        if (p.type == SourceType.STALKER) Stalker(p).episodes(id) else Xtream(p).episodes(id)
    }

    suspend fun epg(p: Playlist, ch: Channel, limit: Int = 10): List<EpgItem> {
        val sh = EpgShift.ms + EpgShift.chMs(ch.url)
        fun List<EpgItem>.shifted() = if (sh == 0L) this else map { it.copy(start = it.start + sh, end = it.end + sh) }
        suspend fun own() = try { OwnGuide.of(ch.epg, ch.name, p.id).take(limit).shifted() } catch (e: Exception) { emptyList() }
        // order: the playlist's default EPG, then the provider's own guide, then the other active EPGs
        if (EpgSources.hasDefault(p.id)) own().let { if (it.isNotEmpty()) return it }
        val provider = if (!EpgSources.providerOn(p.id)) emptyList() else cached("epg_${p.id}_${ch.url}_$limit") {
            if (p.type != SourceType.XTREAM || ch.id.isBlank() || !ch.url.contains("/live/")) emptyList() else try { Xtream(p).epg(ch.id, limit) } catch (e: Exception) { emptyList() }
        }
        if (provider.isNotEmpty() || EpgSources.srcs(p.id).isEmpty()) return provider.shifted()
        return own()
    }

    suspend fun nowNext(p: Playlist, ch: Channel): List<Pair<String, String>> = epg(p, ch, 4).take(2).map { it.title to it.times() }

    suspend fun test(p: Playlist): String = withContext(Dispatchers.IO) {
        if (p.type == SourceType.XTREAM) Xtream(p).login()
        else if (p.type == SourceType.STALKER) Stalker(p).login()
        else { val n = M3u.parse(m3uText(p.url.trim())).size; trf("%s kanala", n) }
    }
}

/** M3U text from a link, or from a file copied in from USB ("file://…"). */
fun m3uText(url: String): String = if (url.startsWith("file://")) java.io.File(url.removePrefix("file://")).readText() else Net.get(url)

/** Quality written in a title ("4K", "FHD", "UHD", "HD", "SD"), or null. */
fun qualityOf(name: String): String? {
    val n = name.uppercase()
    return when {
        Regex("\\b(4K|UHD|2160P?)\\b").containsMatchIn(n) -> "4K"
        Regex("\\b(FHD|1080P?)\\b").containsMatchIn(n) -> "FHD"
        Regex("\\b(HD|720P?|HEVC)\\b").containsMatchIn(n) -> "HD"
        Regex("\\bSD\\b").containsMatchIn(n) -> "SD"
        else -> null
    }
}

/** Title without provider prefixes, quality words and year, for metadata/subtitle search. Returns title to year. */
fun cleanTitle(raw: String): Pair<String, String?> {
    var t = raw
    val year = Regex("[(\\[](19|20)\\d{2}[)\\]]").find(t)?.value?.trim('(', ')', '[', ']')
        ?: Regex("\\b(19|20)\\d{2}\\b\\s*$").find(t)?.value?.trim()
    t = t.replace(Regex("^\\s*[|\\[(]?[A-Z0-9]{2,6}[|\\])]\\s*"), "")            // |EN| , [EXYU]
    t = t.replace(Regex("^\\s*(4K-)?[A-Z]{2,4}\\s*[-:|]\\s*"), "")                   // EN - , 4K-EN -
    t = t.replace(Regex("^\\s*[A-Z][A-Z0-9]{0,3}\\+\\s*[-:|]\\s*"), "")                     // D+ - , AP+ -
    t = t.replace(Regex("[(\\[](19|20)\\d{2}[)\\]]"), "")
    t = t.replace(Regex("\\b(4K|UHD|FHD|HD|SD|HEVC|H265|1080P|720P|2160P|MULTI[- ]?SUB|SUB|DUB)\\b", RegexOption.IGNORE_CASE), "")
    t = t.replace(Regex("[(\\[]\\s*[)\\]]"), "")                                    // empty () []
    t = t.replace(Regex("\\s+"), " ").trim(' ', '-', '|', ':', '.')
    return t to year
}
