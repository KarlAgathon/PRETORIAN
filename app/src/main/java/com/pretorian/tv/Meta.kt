package com.pretorian.tv

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream

data class Person(val name: String, val role: String, val photo: String?)
data class Meta(
    val title: String, val overview: String?, val overviewLang: String?, val poster: String?, val backdrop: String?,
    val year: String?, val runtime: String?, val genres: String?, val tmdbRating: Double?, val imdbRating: String?,
    val imdbId: String?, val trailerKey: String?, val people: List<Person>,
)

/** Small HTTP helper with headers, JSON body and byte download. */
object Http {
    fun request(url: String, headers: Map<String, String> = emptyMap(), body: String? = null): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", "PRETORIAN v${BuildInfo.VERSION}")
        c.setRequestProperty("Accept", "application/json")
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        try {
            if (body != null) {
                c.requestMethod = "POST"; c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IOException("HTTP $code ${text.take(120)}")
            return text
        } finally { c.disconnect() }
    }

    fun bytes(url: String, max: Int = 50_000_000): ByteArray {
        var cur = url
        repeat(6) {
            val c = URL(cur).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000; c.readTimeout = 60_000; c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "PRETORIAN v${BuildInfo.VERSION}")
            try {
                val code = c.responseCode
                if (code in 300..399) { cur = URL(URL(cur), c.getHeaderField("Location") ?: throw IOException("redirect")).toString(); return@repeat }
                if (code !in 200..299) throw IOException("HTTP $code")
                val out = java.io.ByteArrayOutputStream()
                c.inputStream.use { input ->
                    val buf = ByteArray(32 * 1024)
                    while (true) { val n = input.read(buf); if (n < 0) break; out.write(buf, 0, n); if (out.size() > max) throw IOException("Fajl je prevelik") }
                }
                return out.toByteArray()
            } finally { c.disconnect() }
        }
        throw IOException("Previše preusmjeravanja")
    }
}

/** TMDB (posters, overview in the app language, cast, trailer) + OMDb (IMDb rating). */
object Tmdb {
    private const val IMG = "https://image.tmdb.org/t/p/"
    private val cache = HashMap<String, Meta?>()

    fun enabled() = Prefs.tmdbKey.isNotBlank()

    private fun get(path: String, query: String): JSONObject {
        val key = Prefs.tmdbKey
        val bearer = key.length > 40
        val url = "https://api.themoviedb.org/3$path?$query" + if (bearer) "" else "&api_key=${Net.enc(key)}"  // i18n-ignore
        return JSONObject(Http.request(url, if (bearer) mapOf("Authorization" to "Bearer $key") else emptyMap()))
    }

    /** test the key; returns null when OK */
    suspend fun test(): String? = withContext(Dispatchers.IO) {
        try { get("/configuration", ""); null } catch (e: Exception) { e.message ?: "Greška" }
    }

    suspend fun find(rawTitle: String, tv: Boolean): Meta? = withContext(Dispatchers.IO) {
        if (!enabled()) return@withContext null
        val ck = "${if (tv) "tv" else "mv"}|$rawTitle|${metaLanguage()}"
        if (cache.containsKey(ck)) return@withContext cache[ck]
        val (title, year) = cleanTitle(rawTitle)
        val lang = metaLanguage()
        val kind = if (tv) "tv" else "movie"
        var q = "query=${Net.enc(title)}&language=$lang"
        if (year != null) q += if (tv) "&first_air_date_year=$year" else "&year=$year"
        var results = get("/search/$kind", q).optJSONArray("results")
        if ((results == null || results.length() == 0) && year != null) results = get("/search/$kind", "query=${Net.enc(title)}&language=$lang").optJSONArray("results")
        val first = results?.optJSONObject(0) ?: run { cache[ck] = null; return@withContext null }
        val id = first.optInt("id")
        val d = get("/$kind/$id", "language=$lang&append_to_response=credits,videos,external_ids")
        var overview = d.str("overview").orNullIfBlank()
        var overviewLang: String? = lang
        if (overview == null && lang != "en") {
            overview = try { get("/$kind/$id", "language=en-US").str("overview").orNullIfBlank() } catch (e: Exception) { null }
            overviewLang = "en"
        }
        val people = ArrayList<Person>()
        d.optJSONObject("credits")?.let { cr ->
            val crew = cr.optJSONArray("crew") ?: JSONArray()
            for (i in 0 until crew.length()) {
                val p = crew.getJSONObject(i)
                if (p.str("job") == "Director" || (tv && p.str("job") == "Creator")) {
                    people.add(Person(p.str("name"), "Režija", p.str("profile_path").orNullIfBlank()?.let { IMG + "w185" + it })); break
                }
            }
            if (tv) d.optJSONArray("created_by")?.optJSONObject(0)?.let {
                if (people.isEmpty()) people.add(Person(it.str("name"), "Autor", it.str("profile_path").orNullIfBlank()?.let { p -> IMG + "w185" + p }))
            }
            val cast = cr.optJSONArray("cast") ?: JSONArray()
            for (i in 0 until minOf(cast.length(), 10)) {
                val p = cast.getJSONObject(i)
                people.add(Person(p.str("name"), p.str("character"), p.str("profile_path").orNullIfBlank()?.let { IMG + "w185" + it }))
            }
        }
        var trailer: String? = null
        d.optJSONObject("videos")?.optJSONArray("results")?.let { v ->
            for (i in 0 until v.length()) { val x = v.getJSONObject(i); if (x.str("site") == "YouTube" && x.str("type") == "Trailer") { trailer = x.str("key"); break } }
        }
        val imdbId = d.optJSONObject("external_ids")?.str("imdb_id")?.orNullIfBlank() ?: d.str("imdb_id").orNullIfBlank()
        val genres = d.optJSONArray("genres")?.let { g -> (0 until g.length()).joinToString(", ") { g.getJSONObject(it).str("name") } }?.orNullIfBlank()
        val runtime = if (tv) d.optJSONArray("episode_run_time")?.optInt(0)?.takeIf { it > 0 }?.let { "$it min/ep" }
                      else d.optInt("runtime").takeIf { it > 0 }?.let { "${it / 60}h ${it % 60}m" }
        val m = Meta(
            title = d.str(if (tv) "name" else "title").ifBlank { title },
            overview = overview, overviewLang = overviewLang,
            poster = d.str("poster_path").orNullIfBlank()?.let { IMG + "w500" + it },
            backdrop = d.str("backdrop_path").orNullIfBlank()?.let { IMG + "w1280" + it },
            year = d.str(if (tv) "first_air_date" else "release_date").take(4).orNullIfBlank(),
            runtime = runtime, genres = genres,
            tmdbRating = d.optDouble("vote_average").takeIf { !it.isNaN() && it > 0 },
            imdbRating = imdbId?.let { omdb(it) }, imdbId = imdbId, trailerKey = trailer, people = people,
        )
        cache[ck] = m
        m
    }

    private fun omdb(imdbId: String): String? {
        val key = Prefs.omdbKey
        if (key.isBlank()) return null
        return try {
            JSONObject(Http.request("https://www.omdbapi.com/?apikey=${Net.enc(key)}&i=${Net.enc(imdbId)}")).str("imdbRating")
                .takeIf { it.isNotBlank() && it != "N/A" }
        } catch (e: Exception) { null }
    }
}

data class SubResult(val provider: String, val lang: String, val release: String, val downloads: Int, val ref: String)

/** Online subtitles. Without any key: Podnapisi.net and OpenSubtitles.org. With old keys also OpenSubtitles.com and SubDL. */
object Subs {
    private var osToken: String? = null

    fun providers(): List<String> = buildList {
        add("Podnapisi"); add("OpenSubtitles.org")
        if (Prefs.osKey.isNotBlank()) add("OpenSubtitles")
        if (Prefs.subdlKey.isNotBlank()) add("SubDL")
    }

    /** 2-letter code to the 3-letter one OpenSubtitles.org uses */
    private val OS3 = mapOf("bs" to "bos", "hr" to "hrv", "sr" to "scc", "en" to "eng", "de" to "ger", "tr" to "tur", "it" to "ita",
        "bg" to "bul", "ro" to "rum", "ar" to "ara", "fr" to "fre", "es" to "spa", "sl" to "slv", "mk" to "mac")

    private fun legacyOs(title: String, year: String?, season: Int?, episode: Int?): List<SubResult> {
        val l3 = langs().mapNotNull { OS3[it] }.joinToString(",").ifBlank { "eng" }
        val parts = ArrayList<String>()
        if (episode != null) parts += "episode-$episode"
        parts += "query-" + Net.enc(title.lowercase()).replace("+", "%20")
        if (season != null) parts += "season-$season"
        parts += "sublanguageid-$l3"
        val text = Http.request("https://rest.opensubtitles.org/search/" + parts.joinToString("/"), mapOf("X-User-Agent" to "TemporaryUserAgent"))
        val a = org.json.JSONArray(text)
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            if (year != null && season == null && o.str("MovieYear").isNotBlank() && o.str("MovieYear") != year) return@mapNotNull null
            SubResult("OpenSubtitles.org", o.str("ISO639").ifBlank { o.str("SubLanguageID").take(2) }.lowercase(),
                o.str("MovieReleaseName").ifBlank { o.str("SubFileName") }, o.str("SubDownloadsCnt").toIntOrNull() ?: 0, o.str("SubDownloadLink"))
        }
    }

    private fun podnapisi(title: String, year: String?, season: Int?, episode: Int?): List<SubResult> {
        val out = ArrayList<SubResult>()
        for (lang in langs().ifEmpty { listOf("en") }.take(4)) {
            var q = "keywords=${Net.enc(title)}&language=$lang"
            if (season != null) q += "&seasons=$season&movie_type=tv-series" else q += "&movie_type=movie"
            if (episode != null) q += "&episodes=$episode"
            if (year != null && season == null) q += "&year=$year"
            val o = try { JSONObject(Http.request("https://www.podnapisi.net/subtitles/search/advanced?$q")) } catch (e: Exception) { continue }
            val data = o.optJSONArray("data") ?: continue
            for (i in 0 until data.length()) {
                val s = data.optJSONObject(i) ?: continue
                val rel = s.optJSONArray("releases")?.let { r -> (0 until r.length()).joinToString(" ") { r.optString(it) } }?.ifBlank { null }
                    ?: s.optJSONObject("movie")?.str("title") ?: title
                val dl = s.optJSONObject("stats")?.optInt("downloads") ?: s.optInt("downloads")
                out += SubResult("Podnapisi", s.str("language").take(2).lowercase().ifBlank { lang }, rel, dl, s.str("id"))
            }
        }
        return out
    }

    /** how well a subtitle's release name fits the file you play (more shared words = better) */
    private fun fit(release: String, wanted: String): Int {
        fun words(s: String) = s.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 2 }.toSet()
        val a = words(release); val b = words(wanted)
        return a.intersect(b).size
    }

    private fun langs() = Prefs.subLangs.split(',').map { it.trim().lowercase() }.filter { it.length == 2 }

    suspend fun search(rawTitle: String, season: Int? = null, episode: Int? = null): List<SubResult> = withContext(Dispatchers.IO) {
        val (title, year) = cleanTitle(rawTitle)
        val out = ArrayList<SubResult>()
        val errors = ArrayList<String>()
        try { out += podnapisi(title, year, season, episode) } catch (e: Exception) { errors += "Podnapisi: ${e.message}" }
        try { out += legacyOs(title, year, season, episode) } catch (e: Exception) { errors += "OpenSubtitles.org: ${e.message}" }
        if (Prefs.osKey.isNotBlank()) try { out += openSubs(title, year, season, episode) } catch (e: Exception) { errors += "OpenSubtitles: ${e.message}" }
        if (Prefs.subdlKey.isNotBlank()) try { out += subdl(title, year, season, episode) } catch (e: Exception) { errors += "SubDL: ${e.message}" }
        if (out.isEmpty() && errors.size >= 2) throw IOException(errors.joinToString("\n"))
        val order = langs()
        // your languages first; inside a language the release that fits the file best, then the most downloaded
        out.distinctBy { it.provider + it.ref }
            .sortedWith(compareBy({ order.indexOf(it.lang).let { i -> if (i < 0) 99 else i } }, { -fit(it.release, rawTitle) }, { -it.downloads }))
    }

    private fun osHeaders(): Map<String, String> = buildMap {
        put("Api-Key", Prefs.osKey)
        osToken?.let { put("Authorization", "Bearer $it") }
    }

    private fun openSubs(title: String, year: String?, season: Int?, episode: Int?): List<SubResult> {
        var q = "query=${Net.enc(title.lowercase())}&languages=${langs().joinToString(",")}"
        if (season != null) q += "&season_number=$season"
        if (episode != null) q += "&episode_number=$episode"
        if (year != null && season == null) q += "&year=$year"
        val data = JSONObject(Http.request("https://api.opensubtitles.com/api/v1/subtitles?$q", osHeaders())).optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).mapNotNull { i ->
            val a = data.getJSONObject(i).optJSONObject("attributes") ?: return@mapNotNull null
            val file = a.optJSONArray("files")?.optJSONObject(0) ?: return@mapNotNull null
            SubResult("OpenSubtitles", a.str("language").take(2).lowercase(), a.str("release").ifBlank { file.str("file_name") },
                a.optInt("download_count"), file.optLong("file_id").toString())
        }
    }

    private fun subdl(title: String, year: String?, season: Int?, episode: Int?): List<SubResult> {
        var q = "api_key=${Net.enc(Prefs.subdlKey)}&film_name=${Net.enc(title)}&languages=${langs().joinToString(",") { it.uppercase() }}&subs_per_page=30"  // i18n-ignore
        if (season != null) q += "&type=tv&season_number=$season" else q += "&type=movie"
        if (episode != null) q += "&episode_number=$episode"
        if (year != null && season == null) q += "&year=$year"
        val o = JSONObject(Http.request("https://api.subdl.com/api/v1/subtitles?$q"))
        val a = o.optJSONArray("subtitles") ?: return emptyList()
        return (0 until a.length()).map { i ->
            val s = a.getJSONObject(i)
            val lang = s.str("language").ifBlank { s.str("lang") }.take(2).lowercase()
            SubResult("SubDL", lang, s.str("release_name").ifBlank { s.str("name") }, 0, s.str("url"))
        }
    }

    /** downloads the subtitle and returns a UTF-8 .srt file in the cache */
    suspend fun download(ctx: Context, r: SubResult): File = withContext(Dispatchers.IO) {
        val bytes = when (r.provider) {
            "OpenSubtitles" -> {
                if (osToken == null && Prefs.osUser.isNotBlank()) {
                    osToken = try {
                        JSONObject(Http.request("https://api.opensubtitles.com/api/v1/login", mapOf("Api-Key" to Prefs.osKey),
                            JSONObject().put("username", Prefs.osUser).put("password", Prefs.osPass).toString())).str("token").orNullIfBlank()
                    } catch (e: Exception) { null }
                }
                val d = JSONObject(Http.request("https://api.opensubtitles.com/api/v1/download", osHeaders(),
                    JSONObject().put("file_id", r.ref.toLong()).toString()))
                val link = d.str("link").orNullIfBlank() ?: throw IOException(d.str("message").ifBlank { "Dnevni limit preuzimanja je potrošen" })
                Http.bytes(link)
            }
            "OpenSubtitles.org" -> {
                val gz = Http.bytes(r.ref)
                try { java.util.zip.GZIPInputStream(ByteArrayInputStream(gz)).use { it.readBytes() } } catch (e: Exception) { gz }
            }
            "Podnapisi" -> {
                val zip = Http.bytes("https://www.podnapisi.net/subtitles/${r.ref}/download")
                unzipFirstSrt(zip) ?: throw IOException("U arhivi nema .srt titla")
            }
            else -> {
                val zip = Http.bytes("https://dl.subdl.com" + r.ref)
                unzipFirstSrt(zip) ?: throw IOException("U arhivi nema .srt titla")
            }
        }
        saveSrt(ctx, bytes, r.lang)
    }

    suspend fun fromUrl(ctx: Context, url: String): File = withContext(Dispatchers.IO) {
        val b = Http.bytes(url.trim())
        saveSrt(ctx, if (url.lowercase().endsWith(".zip")) unzipFirstSrt(b) ?: throw IOException("U arhivi nema .srt") else b, "xx")
    }

    fun fromBytes(ctx: Context, b: ByteArray): File = saveSrt(ctx, b, "xx")

    private fun unzipFirstSrt(zip: ByteArray): ByteArray? {
        ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            while (true) {
                val e = z.nextEntry ?: return null
                if (!e.isDirectory && e.name.lowercase().endsWith(".srt")) return z.readBytes()
            }
        }
    }

    /** Balkan subtitles are often Windows-1250: convert to UTF-8 so č, ć, š, ž, đ show correctly. */
    private fun saveSrt(ctx: Context, b: ByteArray, lang: String): File {
        val text = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b)).toString()
        } catch (e: CharacterCodingException) {
            String(b, Charset.forName("windows-1250"))
        }
        val dir = File(ctx.cacheDir, "subs").apply { mkdirs() }
        val f = File(dir, "sub_${System.currentTimeMillis()}_$lang.srt")
        f.writeText(text.removePrefix("﻿"), Charsets.UTF_8)
        return f
    }

    fun langName(code: String) = when (code) {
        "bs" -> "Bosanski"; "hr" -> "Hrvatski"; "sr" -> "Srpski"; "en" -> "Engleski"; "de" -> "Njemački"
        "sl" -> "Slovenski"; "mk" -> "Makedonski"; "tr" -> "Turski"; "it" -> "Italijanski"; "ro" -> "Rumunski"
        "bg" -> "Bugarski"; "ar" -> "Arapski"; "fr" -> "Francuski"; "es" -> "Španski"
        else -> code.uppercase()
    }
}
