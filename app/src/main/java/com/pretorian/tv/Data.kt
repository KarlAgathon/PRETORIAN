package com.pretorian.tv

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class SourceType { XTREAM, M3U }

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
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("type", type.name)
        put("server", server); put("user", user); put("pass", pass); put("url", url)
        put("active", active); put("live", live); put("vod", vod); put("series", series); put("exp", exp)
    }

    companion object {
        fun from(o: JSONObject) = Playlist(
            id = o.str("id"),
            name = o.str("name"),
            type = if (o.str("type") == "M3U") SourceType.M3U else SourceType.XTREAM,
            server = o.str("server"), user = o.str("user"), pass = o.str("pass"), url = o.str("url"),
            active = o.optBoolean("active", false),
            live = o.optBoolean("live", true), vod = o.optBoolean("vod", true), series = o.optBoolean("series", true),
            exp = o.str("exp"),
        )
    }
}

data class Category(val id: String, val name: String)
data class Channel(val id: String, val num: Int, val name: String, val logo: String?, val group: String, val url: String, val epg: String? = null, val pid: String = "", val archive: Int = 0)
data class Vod(val id: String, val name: String, val poster: String?, val rating: String?, val url: String, val added: Long = 0)
data class SeriesItem(val id: String, val name: String, val cover: String?, val plot: String?, val rating: String?, val added: Long = 0)
data class Episode(val id: String, val season: Int, val num: Int, val title: String, val url: String, val plot: String?)
data class VodInfo(
    val plot: String?, val genre: String?, val year: String?, val duration: String?,
    val cast: String?, val director: String?, val backdrop: String?, val rating: String?,
)
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

    /** turned-on playlists, the opened one first */
    fun actives(c: Context): List<Playlist> {
        val on = playlists(c).filter { it.active }.ifEmpty { playlists(c).take(1) }
        val open = Prefs.str("pl_open")
        return on.sortedBy { if (it.id == open) 0 else 1 }
    }

    /** the opened playlist (the one whose groups are unfolded) */
    fun active(c: Context): Playlist? = actives(c).firstOrNull()

    fun byId(c: Context, id: String?): Playlist? = playlists(c).firstOrNull { it.id == id } ?: active(c)

    /** several playlists may be on at once (Settings › Playlists) */
    var multi: Boolean get() = Prefs.bool("pl_multi", false); set(v) = Prefs.put("pl_multi", v)

    fun open(c: Context, id: String) { Prefs.put("pl_open", id) }

    fun activate(c: Context, id: String) {
        if (multi) savePlaylists(c, playlists(c).map { if (it.id == id) it.copy(active = !it.active) else it })
        else savePlaylists(c, playlists(c).map { it.copy(active = it.id == id) })
        open(c, id)
    }

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
    const val UA = "Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36 PRETORIAN/0.1"

    fun get(url: String): String {
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
                return c.inputStream.bufferedReader().use { r -> r.readText() }
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
    fun catalog(kind: String): List<JSONObject> {
        val a = array(api(if (kind == "vod") "get_vod_streams" else "get_series"))
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            if (kind == "vod") {
                val id = o.str("stream_id"); val ext = o.str("container_extension").ifBlank { "mp4" }
                JSONObject().put("id", id).put("c", o.str("category_id")).put("n", o.str("name")).put("i", o.str("stream_icon"))
                    .put("r", o.str("rating")).put("u", "$base/movie/${p.user}/${p.pass}/$id.$ext")
            } else JSONObject().put("id", o.str("series_id")).put("c", o.str("category_id")).put("n", o.str("name")).put("i", o.str("cover"))
                .put("r", o.str("rating"))
        }
    }

    /** past programmes of a channel with archive (catch-up) */
    fun archive(streamId: String): List<EpgItem> {
        val o = try { JSONObject(api("get_simple_data_table", "&stream_id=${Net.enc(streamId)}")) } catch (e: Exception) { return emptyList() }
        val a = o.optJSONArray("epg_listings") ?: return emptyList()
        fun b64(s: String) = try { String(android.util.Base64.decode(s, android.util.Base64.DEFAULT)).trim() } catch (e: Exception) { s }
        return (0 until a.length()).mapNotNull {
            val e = a.getJSONObject(it)
            val start = e.str("start_timestamp").toLongOrNull()?.times(1000) ?: return@mapNotNull null
            val end = e.str("stop_timestamp").toLongOrNull()?.times(1000) ?: start
            // the server's own clock text is what the archive link wants
            EpgItem(b64(e.str("title")), b64(e.str("description")).orNullIfBlank(), start, end, e.str("start"), e.optInt("has_archive", 1) == 1)
        }
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
        val o = try { JSONObject(api("get_vod_info", "&vod_id=${Net.enc(id)}")) } catch (e: Exception) { JSONObject() }
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
        val o = try { JSONObject(api("get_series_info", "&series_id=${Net.enc(seriesId)}")) } catch (e: Exception) { JSONObject() }
        val eps = o.optJSONObject("episodes") ?: return emptyList()
        val out = ArrayList<Episode>()
        eps.keys().forEach { key ->
            val arr = eps.optJSONArray(key) ?: return@forEach
            for (j in 0 until arr.length()) {
                val e = arr.getJSONObject(j)
                val id = e.str("id")
                val ext = e.str("container_extension").ifBlank { "mp4" }
                val info = e.optJSONObject("info")
                out.add(Episode(id, key.toIntOrNull() ?: e.optInt("season", 1), e.optInt("episode_num", j + 1),
                    e.str("title"), "$base/series/${p.user}/${p.pass}/$id.$ext", info?.str("plot")?.orNullIfBlank()))
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

    fun clear() { cache.clear(); m3uFor = null; m3u = emptyList() }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> cached(key: String, load: () -> T): T = withContext(Dispatchers.IO) {
        (cache[key] as T?) ?: load().also { cache[key] = it as Any }
    }

    private fun m3uChannels(p: Playlist): List<Channel> {
        if (m3uFor != p.id) { m3u = M3u.parse(Net.get(p.url.trim())).map { it.copy(pid = p.id) }; m3uFor = p.id }
        return m3u
    }

    suspend fun liveGroups(p: Playlist): List<Category> = cached("lg_${p.id}") {
        if (p.type == SourceType.XTREAM) Xtream(p).liveCategories()
        else m3uChannels(p).map { it.group }.distinct().map { Category(it, it) }
    }

    suspend fun channels(p: Playlist, groupId: String): List<Channel> = cached("ch_${p.id}_$groupId") {
        if (p.type == SourceType.XTREAM) Xtream(p).channels(groupId)
        else m3uChannels(p).filter { it.group == groupId }
    }

    /** every live channel of the playlist ("All channels", sports search) */
    suspend fun allChannels(p: Playlist): List<Channel> = cached("all_${p.id}") {
        if (p.type == SourceType.XTREAM) Xtream(p).allChannels() else m3uChannels(p)
    }

    /** every movie / series of the playlist (search) */
    /** Folder for lists kept on the box between starts (search catalog). Set at start. */
    var diskDir: java.io.File? = null

    /** Every movie or series of a playlist. Kept in memory and on the box for 24 hours, so search is instant. */
    suspend fun catalog(p: Playlist, kind: String): List<JSONObject> =
        if (p.type != SourceType.XTREAM) emptyList() else cached("cat_${kind}_${p.id}") {
            val f = diskDir?.let { java.io.File(it, "catalog_${kind}_${p.id.hashCode()}.json") }
            val fresh = f != null && f.exists() && System.currentTimeMillis() - f.lastModified() < 24 * 3600_000L
            val fromDisk = if (fresh) try { org.json.JSONArray(f!!.readText()).let { a -> (0 until a.length()).map { a.getJSONObject(it) } } } catch (e: Exception) { null } else null
            fromDisk ?: Xtream(p).catalog(kind).also { list ->
                try { f?.writeText(org.json.JSONArray(list).toString()) } catch (_: Exception) {}
            }
        }

    /** "Update all": forget the kept catalog so it is downloaded again. */
    fun dropCatalogs() { diskDir?.listFiles()?.filter { it.name.startsWith("catalog_") }?.forEach { it.delete() } }

    suspend fun vodGroups(p: Playlist): List<Category> =
        if (p.type == SourceType.XTREAM) cached("vg_${p.id}") { Xtream(p).vodCategories() } else emptyList()

    suspend fun movies(p: Playlist, groupId: String): List<Vod> = cached("mv_${p.id}_$groupId") { Xtream(p).movies(groupId) }

    suspend fun movieInfo(p: Playlist, id: String): VodInfo = cached("mi_${p.id}_$id") { Xtream(p).movieInfo(id) }

    suspend fun seriesGroups(p: Playlist): List<Category> =
        if (p.type == SourceType.XTREAM) cached("sg_${p.id}") { Xtream(p).seriesCategories() } else emptyList()

    suspend fun series(p: Playlist, groupId: String): List<SeriesItem> = cached("se_${p.id}_$groupId") { Xtream(p).series(groupId) }

    suspend fun episodes(p: Playlist, id: String): List<Episode> = cached("ep_${p.id}_$id") { Xtream(p).episodes(id) }

    suspend fun epg(p: Playlist, ch: Channel, limit: Int = 10): List<EpgItem> {
        val provider = cached("epg_${p.id}_${ch.url}_$limit") {
            if (p.type != SourceType.XTREAM || ch.id.isBlank() || !ch.url.contains("/live/")) emptyList() else try { Xtream(p).epg(ch.id, limit) } catch (e: Exception) { emptyList() }
        }
        if (provider.isNotEmpty() || !EpgSources.any) return provider
        return try { OwnGuide.of(ch.epg, ch.name, p.id).take(limit) } catch (e: Exception) { emptyList() }
    }

    suspend fun nowNext(p: Playlist, ch: Channel): List<Pair<String, String>> = epg(p, ch, 4).take(2).map { it.title to it.times() }

    suspend fun test(p: Playlist): String = withContext(Dispatchers.IO) {
        if (p.type == SourceType.XTREAM) Xtream(p).login()
        else { val n = M3u.parse(Net.get(p.url.trim())).size; trf("%s kanala", n) }
    }
}

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
    t = t.replace(Regex("[(\\[](19|20)\\d{2}[)\\]]"), "")
    t = t.replace(Regex("\\b(4K|UHD|FHD|HD|SD|HEVC|H265|1080P|720P|2160P|MULTI[- ]?SUB|SUB|DUB)\\b", RegexOption.IGNORE_CASE), "")
    t = t.replace(Regex("\\s+"), " ").trim(' ', '-', '|', ':', '.')
    return t to year
}
