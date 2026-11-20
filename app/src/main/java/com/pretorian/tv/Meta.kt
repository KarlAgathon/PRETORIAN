package com.pretorian.tv

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
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
    val studio: String? = null, val tmdbId: Int = 0, val runtimeMin: Int = 0,
)

/** One episode as TMDB knows it, also the ones not out yet. */
data class TmdbEp(val season: Int, val num: Int, val name: String, val overview: String?, val air: String?, val still: String?, val runtime: Int)

/** headers of a normal browser: some subtitle sites refuse unknown apps */
val BROWSER = mapOf("User-Agent" to "Mozilla/5.0 (Linux; Android 12; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36")  // i18n-ignore

/** Small HTTP helper with headers, JSON body and byte download. */
object Http {
    /** when the network's DNS does not know a host, ask Cloudflare or Google DNS over HTTPS */
    private fun dohLookup(host: String): List<java.net.InetAddress> {
        for (u in listOf("https://1.1.1.1/dns-query?name=$host&type=A", "https://8.8.8.8/resolve?name=$host&type=A")) {
            try {
                val c = URL(u).openConnection() as HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 8000
                c.setRequestProperty("Accept", "application/dns-json")
                val a = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).optJSONArray("Answer") ?: continue
                val ips = (0 until a.length()).mapNotNull { a.optJSONObject(it) }.filter { it.optInt("type") == 1 }
                    .mapNotNull { try { java.net.InetAddress.getByName(it.str("data")) } catch (e: Exception) { null } }
                if (ips.isNotEmpty()) return ips
            } catch (_: Exception) {}
        }
        return emptyList()
    }
    private val client by lazy {
        okhttp3.OkHttpClient.Builder()
            .dns(object : okhttp3.Dns {
                override fun lookup(hostname: String): List<java.net.InetAddress> =
                    try { okhttp3.Dns.SYSTEM.lookup(hostname) } catch (e: java.net.UnknownHostException) { dohLookup(hostname).ifEmpty { throw e } }
            })
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS).readTimeout(60, java.util.concurrent.TimeUnit.SECONDS).build()
    }
    private fun viaDoh(url: String, headers: Map<String, String>, body: String?): okhttp3.Response {
        val b = okhttp3.Request.Builder().url(url).header("User-Agent", "PRETORIAN v${BuildInfo.VERSION}").header("Accept", "application/json")
        headers.forEach { (k, v) -> b.header(k, v) }
        if (body != null) b.post(body.toRequestBody("application/json".toMediaTypeOrNull()))
        return client.newCall(b.build()).execute()
    }

    fun request(url: String, headers: Map<String, String> = emptyMap(), body: String? = null): String =
        try { requestPlain(url, headers, body) } catch (e: java.net.UnknownHostException) {
            viaDoh(url, headers, body).use { r ->
                val text = r.body?.string() ?: ""
                if (!r.isSuccessful) throw IOException("HTTP ${r.code} ${text.take(120)}")
                text
            }
        }

    private fun requestPlain(url: String, headers: Map<String, String>, body: String?): String {
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

    fun bytes(url: String, max: Int = 50_000_000, ua: String? = null): ByteArray =
        try { bytesPlain(url, max, ua) } catch (e: java.net.UnknownHostException) {
            viaDoh(url, if (ua != null) mapOf("User-Agent" to ua) else emptyMap(), null).use { r ->
                if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                r.body?.bytes() ?: ByteArray(0)
            }
        }

    private fun bytesPlain(url: String, max: Int, ua: String?): ByteArray {
        var cur = url
        repeat(6) {
            val c = URL(cur).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000; c.readTimeout = 60_000; c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", ua ?: "PRETORIAN v${BuildInfo.VERSION}")
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
    private val cache = java.util.Collections.synchronizedMap(object : LinkedHashMap<String, Meta>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Meta>?) = size > 128
    })

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
        cache[ck]?.let { return@withContext it }
        val (title, year) = cleanTitle(rawTitle)
        val lang = metaLanguage()
        val kind = if (tv) "tv" else "movie"
        var q = "query=${Net.enc(title)}&language=$lang"
        if (year != null) q += if (tv) "&first_air_date_year=$year" else "&year=$year"
        var results = get("/search/$kind", q).optJSONArray("results")
        if ((results == null || results.length() == 0) && year != null) results = get("/search/$kind", "query=${Net.enc(title)}&language=$lang").optJSONArray("results")
        val first = results?.optJSONObject(0) ?: return@withContext null
        val id = first.optInt("id")
        val d = get("/$kind/$id", "language=$lang&append_to_response=credits,videos,external_ids,watch/providers")
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
        // who made it: the TV network for a series, the studio for a film
        // where it streams: the network for a series; for a film the subscription service in this country, else a studio only when it is a streaming brand
        // where it streams in this country (subscription) first – for films and series alike; then the network or studio
        val res = d.optJSONObject("watch/providers")?.optJSONObject("results")
        val cc = java.util.Locale.getDefault().country.ifBlank { "DE" }
        val flat = (res?.optJSONObject(cc) ?: res?.optJSONObject("DE") ?: res?.optJSONObject("US"))?.optJSONArray("flatrate")
        val fromFlat = flat?.let { f -> (0 until f.length()).firstNotNullOfOrNull { Platforms.norm(f.getJSONObject(it).str("provider_name")) } }
        val studio = fromFlat ?: if (tv) (d.optJSONArray("networks")?.let { nw -> (0 until nw.length()).firstNotNullOfOrNull { Platforms.norm(nw.getJSONObject(it).str("name")) } }
                ?: d.optJSONArray("production_companies")?.let { pc -> (0 until pc.length()).firstNotNullOfOrNull { Platforms.norm(pc.getJSONObject(it).str("name")) } }
                ?: d.optJSONArray("networks")?.optJSONObject(0)?.str("name")?.orNullIfBlank())
            else d.optJSONArray("production_companies")?.let { pc -> (0 until pc.length()).firstNotNullOfOrNull { Platforms.norm(pc.getJSONObject(it).str("name")) } }
        val rtMin = if (tv) d.optJSONArray("episode_run_time")?.optInt(0) ?: 0 else d.optInt("runtime")
        val m = Meta(
            studio = studio, tmdbId = id, runtimeMin = rtMin,
            title = d.str(if (tv) "name" else "title").ifBlank { title },
            overview = overview, overviewLang = overviewLang,
            poster = d.str("poster_path").orNullIfBlank()?.let { IMG + "w780" + it },
            backdrop = d.str("backdrop_path").orNullIfBlank()?.let { IMG + "w1280" + it },
            year = d.str(if (tv) "first_air_date" else "release_date").take(4).orNullIfBlank(),
            runtime = runtime, genres = genres,
            tmdbRating = d.optDouble("vote_average").takeIf { !it.isNaN() && it > 0 },
            imdbRating = imdbId?.let { omdb(it) }, imdbId = imdbId, trailerKey = trailer, people = people,
        )
        cache[ck] = m
        m
    }

    private val seasonCache = HashMap<String, List<TmdbEp>>()

    /** All episodes of one season with their air dates; empty without a key or when nothing is found. */
    suspend fun season(tvId: Int, n: Int): List<TmdbEp> = withContext(Dispatchers.IO) {
        if (!enabled() || tvId <= 0) return@withContext emptyList()
        val ck = "$tvId|$n|${metaLanguage()}"
        seasonCache[ck]?.let { return@withContext it }
        val out = try {
            val a = get("/tv/$tvId/season/$n", "language=${metaLanguage()}").optJSONArray("episodes") ?: JSONArray()
            (0 until a.length()).map { i ->
                val e = a.getJSONObject(i)
                TmdbEp(n, e.optInt("episode_number"), e.str("name"), e.str("overview").orNullIfBlank(), e.str("air_date").orNullIfBlank(),
                    e.str("still_path").orNullIfBlank()?.let { IMG + "w300" + it }, e.optInt("runtime"))
            }
        } catch (e: Exception) { emptyList() }
        seasonCache[ck] = out
        out
    }

    /** Season numbers TMDB knows for a series (without the "specials" season 0). */
    suspend fun seasons(tvId: Int): List<Int> = withContext(Dispatchers.IO) {
        if (!enabled() || tvId <= 0) return@withContext emptyList()
        try {
            val a = get("/tv/$tvId", "language=${metaLanguage()}").optJSONArray("seasons") ?: JSONArray()
            (0 until a.length()).map { a.getJSONObject(it).optInt("season_number") }.filter { it > 0 }
        } catch (e: Exception) { emptyList() }
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
        "bg" to "bul", "ro" to "rum", "ar" to "ara", "fr" to "fre", "es" to "spa", "sl" to "slv", "mk" to "mac", "ru" to "rus")

    /** the Podnapisi address that answered last (some networks resolve only one of them) */
    @Volatile private var podHost = "https://www.podnapisi.net"

    private fun legacyOs(title: String, year: String?, season: Int?, episode: Int?): List<SubResult> {
        // the server takes one language per request
        val out = ArrayList<SubResult>()
        var err: Exception? = null; var ok = false
        for (l3 in langs().mapNotNull { OS3[it] }.ifEmpty { listOf("eng") }.distinct().take(4)) {
            try { out += legacyOsOne(title, year, season, episode, l3); ok = true } catch (e: Exception) { err = e }
        }
        if (!ok) err?.let { throw it }
        return out
    }

    private fun legacyOsOne(title: String, year: String?, season: Int?, episode: Int?, l3: String): List<SubResult> {
        val parts = ArrayList<String>()
        if (episode != null) parts += "episode-$episode"
        parts += "query-" + Net.enc(title.lowercase()).replace("+", "%20")
        if (season != null) parts += "season-$season"
        parts += "sublanguageid-$l3"
        val text = Http.request("https://rest.opensubtitles.org/search/" + parts.joinToString("/"),
            mapOf("X-User-Agent" to "TemporaryUserAgent", "User-Agent" to "TemporaryUserAgent"))
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
        var lastError: Exception? = null; var answered = false
        fun ask(q: String): org.json.JSONArray? {
            for (host in listOf(podHost, "https://podnapisi.net", "https://www.podnapisi.net").distinct()) {
                try {
                    val o = JSONObject(Http.request("$host/subtitles/search/advanced?$q", BROWSER))
                    answered = true; podHost = host; return o.optJSONArray("data")
                } catch (e: Exception) { lastError = e }
            }
            return null
        }
        for (lang in langs().ifEmpty { listOf("en") }.take(4)) {
            var q = "keywords=${Net.enc(title)}&language=$lang"
            if (season != null) q += "&seasons=$season"
            if (episode != null) q += "&episodes=$episode"
            // with the year first; when that finds nothing, again without the year and type
            val data = (if (year != null && season == null) ask("$q&year=$year")?.takeIf { it.length() > 0 } else null) ?: ask(q) ?: continue
            for (i in 0 until data.length()) {
                val s = data.optJSONObject(i) ?: continue
                val rel = s.optJSONArray("releases")?.let { r -> (0 until r.length()).joinToString(" ") { r.optString(it) } }?.ifBlank { null }
                    ?: s.optJSONObject("movie")?.str("title") ?: title
                val dl = s.optJSONObject("stats")?.optInt("downloads") ?: s.optInt("downloads")
                out += SubResult("Podnapisi", s.str("language").take(2).lowercase().ifBlank { lang }, rel, dl, s.str("id"))
            }
        }
        if (!answered) lastError?.let { throw it }
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
        // nothing found and a source failed: say why, instead of "no subtitles"
        if (out.isEmpty() && errors.isNotEmpty()) throw IOException(errors.joinToString("\n"))
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
                val gz = Http.bytes(r.ref, ua = "TemporaryUserAgent")
                try { java.util.zip.GZIPInputStream(ByteArrayInputStream(gz)).use { it.readBytes() } } catch (e: Exception) { gz }
            }
            "Podnapisi" -> {
                val zip = Http.bytes("$podHost/subtitles/${r.ref}/download", ua = BROWSER["User-Agent"])
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
        "bg" -> "Bugarski"; "ar" -> "Arapski"; "fr" -> "Francuski"; "es" -> "Španski"; "ru" -> "Ruski"
        else -> code.uppercase()
    }
}

/**
 * Translates movie and series texts (plot, genres) into the description language, without any key.
 * The provider usually sends English; the result is kept in memory and on disk.
 */
object AutoTranslate {
    private val mem = java.util.concurrent.ConcurrentHashMap<String, String>()
    var dir: java.io.File? = null
    private val file get() = dir?.let { java.io.File(it, "translations.json") }
    @Volatile private var loaded = false

    val on get() = Prefs.bool("auto_translate", true)

    private fun load() {
        if (loaded) return
        loaded = true
        try { file?.takeIf { it.exists() }?.let { val o = JSONObject(it.readText()); o.keys().forEach { k -> mem[k] = o.getString(k) } } } catch (_: Exception) {}
    }
    private fun save() {
        try {
            val keys = mem.keys.toList().takeLast(800)
            val o = JSONObject(); keys.forEach { o.put(it, mem[it]) }
            file?.writeText(o.toString())
        } catch (_: Exception) {}
    }

    suspend fun text(s: String?, to: String = metaLanguage()): String? = withContext(Dispatchers.IO) {
        if (s.isNullOrBlank() || !on) return@withContext s
        load()
        val tl = to.substringBefore('-').lowercase()
        val key = tl + "|" + s.hashCode() + "|" + s.length
        mem[key]?.let { return@withContext it }
        try {
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$tl&dt=t&q=" + Net.enc(s.take(4500))  // i18n-ignore
            val a = org.json.JSONArray(Http.request(url))
            val src = a.optString(2)
            val out = if (src == tl) s else {
                val parts = a.optJSONArray(0) ?: return@withContext s
                (0 until parts.length()).joinToString("") { parts.optJSONArray(it)?.optString(0) ?: "" }.trim().ifBlank { s }
            }
            mem[key] = out; save(); out
        } catch (e: Exception) { s }
    }
}

/** shows [s] at once and swaps in the translation when it arrives */
@androidx.compose.runtime.Composable
fun translated(s: String?): String? {
    val lang = metaLanguage()
    return androidx.compose.runtime.produceState(s, s, lang) { value = AutoTranslate.text(s, lang) }.value
}


/** Streaming brands: names as they show up in TMDB and provider category names, mapped to one short label. */
object Platforms {
    private val brands = listOf(  // i18n-ignore
        "netflix" to "Netflix", "hbo max" to "HBO Max", "max" to "Max", "hbo" to "HBO", "disney" to "Disney+", "amazon" to "Prime Video",
        "prime video" to "Prime Video", "apple tv" to "Apple TV+", "hulu" to "Hulu", "paramount" to "Paramount+", "peacock" to "Peacock",
        "amc" to "AMC", "showtime" to "Showtime", "starz" to "Starz", "skyshowtime" to "SkyShowtime", "sky" to "Sky", "crunchyroll" to "Crunchyroll",
        "fx" to "FX", "bbc" to "BBC", "itv" to "ITV", "channel 4" to "Channel 4", "zdf" to "ZDF", "ard" to "ARD", "rtl" to "RTL+", "joyn" to "Joyn",
        "canal+" to "Canal+", "the cw" to "The CW", "nbc" to "NBC", "cbs" to "CBS", "abc" to "ABC", "fox" to "FOX", "wow" to "WOW", "magenta" to "MagentaTV",
    )
    /** A known brand in [name] (whole words), or null. */
    fun norm(name: String?): String? {
        val n = " " + (name ?: return null).lowercase().replace(Regex("[^a-z0-9+ ]"), " ") + " "
        return brands.firstOrNull { (k, _) -> n.contains(" $k ") || (k.endsWith("+") && n.contains(" $k")) }?.second
    }
    fun color(label: String): Long = when (label) {
        "Netflix" -> 0xFFE50914; "HBO", "HBO Max", "Max" -> 0xFF5B2BE0; "Disney+" -> 0xFF113CCF; "Prime Video" -> 0xFF00A8E1
        "Apple TV+" -> 0xFF1C1C1E; "Hulu" -> 0xFF1CE783; "Paramount+" -> 0xFF0064FF; "AMC" -> 0xFF2A2A2A; else -> 0xFF3A3F4B
    }
}

/**
 * Descriptions without any key, when neither the provider nor TMDB has one:
 * TVmaze for series and episodes, Wikipedia for films (and series TVmaze doesn't know). Kept in memory.
 */
object FreeMeta {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val NONE = "\u0000"
    private fun strip(html: String) = html.replace(Regex("<[^>]+>"), "").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").trim()
    private fun words(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").split(' ').filter { it.length > 2 }.toSet()
    /** the found name must really be this title (most of its words) */
    private fun same(a: String, b: String): Boolean { val wa = words(a); val wb = words(b); return wa.isNotEmpty() && wa.count { it in wb } * 3 >= wa.size * 2 }

    private suspend fun memo(key: String, f: () -> String?): String? = withContext(Dispatchers.IO) {
        cache[key]?.let { return@withContext it.takeIf { v -> v != NONE } }
        val v = try { f() } catch (e: Exception) { return@withContext null }
        cache[key] = v ?: NONE; v
    }

    private fun tvmazeShow(title: String): JSONObject? {
        val o = JSONObject(Http.request("https://api.tvmaze.com/singlesearch/shows?q=" + Net.enc(title)))  // i18n-ignore
        return o.takeIf { same(title, it.str("name")) }
    }

    private fun wiki(title: String, year: String?, kind: String): String? {
        for (lang in listOf("en")) {
            val q = Net.enc("$title ${year ?: ""} $kind".trim())
            val s = JSONObject(Http.request("https://$lang.wikipedia.org/w/api.php?action=query&list=search&format=json&srlimit=3&srsearch=$q"))  // i18n-ignore
            val hits = s.optJSONObject("query")?.optJSONArray("search") ?: continue
            for (i in 0 until hits.length()) {
                val page = hits.optJSONObject(i)?.str("title") ?: continue
                if (!same(title, page)) continue
                val sum = JSONObject(Http.request("https://$lang.wikipedia.org/api/rest_v1/page/summary/" + Net.enc(page.replace(' ', '_'))))  // i18n-ignore
                if (sum.str("type") == "disambiguation") continue
                sum.str("extract").takeIf { it.length > 40 }?.let { return it }
            }
        }
        return null
    }

    /** what the free sources give for a film or series */
    data class Free(val plot: String?, val cast: String?, val director: String?, val genre: String?, val year: String?, val runtime: String?)
    private val freeCache = java.util.concurrent.ConcurrentHashMap<String, Free>()

    /** surnames of a "A B, C D" list, lower case (to compare cast/director between sources) */
    private fun names(s: String?): Set<String> = s?.split(',', ';')?.mapNotNull { it.trim().split(' ').lastOrNull()?.lowercase()?.takeIf { n -> n.length > 2 } }?.toSet() ?: emptySet()
    private fun yearOf(s: String?) = s?.let { Regex("(19|20)\\d{2}").find(it)?.value?.toIntOrNull() }

    /**
     * Cinemeta (Stremio's open film/series catalog, IMDb data, no key): search by name, then the full card (story, cast, director).
     * Several titles with the same name (e.g. a 2025 thriller and a 2026 documentary "Tuner"): the year and the cast the
     * provider gives decide; when it cannot be told which one it is, nothing is shown rather than another film's story.
     */
    private fun cinemeta(title: String, year: String?, kind: String, cast: String? = null, director: String? = null): Free? {
        val base = "https://v3-cinemeta.strem.io"  // i18n-ignore
        val a = JSONObject(Http.request("$base/catalog/$kind/top/search=" + Net.enc(title) + ".json")).optJSONArray("metas") ?: return null  // i18n-ignore
        var hits = (0 until minOf(a.length(), 8)).mapNotNull { a.optJSONObject(it) }.filter { same(title, it.str("name")) }
        if (hits.isEmpty()) return null
        val y = year?.toIntOrNull()
        if (y != null) {
            // the year must fit (a series' year is its first season, so a little more room there)
            val near = hits.filter { h -> yearOf(h.str("releaseInfo"))?.let { kotlin.math.abs(it - y) <= (if (kind == "series") 2 else 1) } ?: false }
            if (near.isEmpty()) return null
            hits = near.sortedBy { h -> kotlin.math.abs((yearOf(h.str("releaseInfo")) ?: 0) - y) }
        }
        fun full(h: JSONObject): JSONObject {
            val id = h.str("imdb_id").ifBlank { h.str("id") }
            return if (id.isBlank()) h else try { JSONObject(Http.request("$base/meta/$kind/$id.json")).optJSONObject("meta") ?: h } catch (e: Exception) { h }  // i18n-ignore
        }
        fun list(m: JSONObject, k: String, n: Int) = m.optJSONArray(k)?.let { x -> (0 until x.length()).map { x.optString(it) }.filter { it.isNotBlank() }.take(n).joinToString(", ") }?.ifBlank { null }
        val want = names(cast) + names(director)
        val m: JSONObject = if (want.isNotEmpty()) {
            // the provider knows the cast: the card whose cast or director matches
            hits.take(3).map { full(it) }.firstOrNull { m -> (names(list(m, "cast", 12)) + names(list(m, "director", 3))).any { it in want } } ?: run {
                // nobody matches: only trust a single, year-checked candidate
                if (hits.size == 1 && y != null) full(hits[0]) else return null
            }
        } else {
            val exact = hits.filter { it.str("name").equals(title, true) }
            val years = exact.mapNotNull { yearOf(it.str("releaseInfo")) }.toSet()
            // same name, different years, nothing to tell them apart: better no story than a wrong one
            if (y == null && years.size > 1) return null
            full(hits[0])
        }
        return Free(
            plot = m.str("description").takeIf { it.length > 40 },
            cast = list(m, "cast", 6), director = list(m, "director", 2), genre = list(m, "genres", 3) ?: list(m, "genre", 3),
            year = m.str("releaseInfo").ifBlank { m.str("year") }.take(4).ifBlank { null },
            runtime = m.str("runtime").ifBlank { null },
        )
    }
    private fun <T> safe(f: () -> T?): T? = try { f() } catch (e: Exception) { null }
    /** the first [n] sentences */
    private fun sentences(t: String, n: Int) = Regex("(?<=[.!?])\\s+").split(t.trim()).take(n).joinToString(" ")

    /** a Wikipedia text belongs to the same film: it names its year (±1) or its director */
    private fun fits(text: String, year: String?, director: String?): Boolean {
        val y = year?.toIntOrNull()
        val ys = Regex("\\b(19|20)\\d{2}\\b").findAll(text.take(300)).mapNotNull { it.value.toIntOrNull() }.toList()
        val byYear = y != null && ys.any { kotlin.math.abs(it - y) <= 1 }
        val byDir = names(director).any { text.lowercase().contains(it) }
        if (y != null && ys.isNotEmpty() && !byYear && !byDir) return false
        return byYear || byDir
    }

    /** story (Cinemeta, a couple of sentences more from Wikipedia when short), cast, director, genre.
     *  [year], [cast], [director] from the provider, when it has them, to pick the right one of several films with that name */
    suspend fun movieFull(raw: String, year: String? = null, cast: String? = null, director: String? = null): Free? = withContext(Dispatchers.IO) {
        val (t, y0) = cleanTitle(raw); val y = y0 ?: year?.take(4)?.takeIf { it.length == 4 }
        val k = "M|$t|$y|${names(cast).sorted().take(3)}"
        freeCache[k]?.let { return@withContext it }
        val c = safe { cinemeta(t, y, "movie", cast, director) }
        val yy = c?.year ?: y; val dd = c?.director ?: director
        val w = if ((c?.plot?.length ?: 0) < 320 && (yy != null || dd != null)) safe { wiki(t, yy, "film") }?.takeIf { fits(it, yy, dd) } else null  // i18n-ignore
        val plot = listOfNotNull(c?.plot, w?.let { sentences(it, if (c?.plot == null) 5 else 2) }).joinToString(" ").ifBlank { null }
        val out = (c ?: Free(null, null, null, null, null, null)).copy(plot = plot)
        if (plot != null || out.cast != null) freeCache[k] = out
        out.takeIf { plot != null || it.cast != null }
    }
    suspend fun movie(raw: String): String? = movieFull(raw)?.plot
    suspend fun seriesFull(raw: String): Free? = withContext(Dispatchers.IO) {
        val (t, y) = cleanTitle(raw); val k = "S|$t"
        freeCache[k]?.let { return@withContext it }
        val c = safe { cinemeta(t, y, "series") }
        // TVmaze only when it is the same show (its first year fits the one Cinemeta found)
        val tv = if ((c?.plot?.length ?: 0) < 200) safe { tvmazeShow(t)?.takeIf { o ->
            val cy = (c?.year ?: y)?.toIntOrNull(); val ty = yearOf(o.str("premiered"))
            cy == null || ty == null || kotlin.math.abs(cy - ty) <= 1
        }?.str("summary")?.let(::strip)?.ifBlank { null } } else null
        val plot = (if ((tv?.length ?: 0) > (c?.plot?.length ?: 0)) tv else c?.plot)
            ?: (c?.year ?: y)?.let { yy -> safe { wiki(t, yy, "TV series") }?.takeIf { fits(it, yy, c?.director) } }  // i18n-ignore
        val out = (c ?: Free(null, null, null, null, null, null)).copy(plot = plot)
        if (plot != null || out.cast != null) freeCache[k] = out
        out.takeIf { plot != null || it.cast != null }
    }
    suspend fun series(raw: String): String? = seriesFull(raw)?.plot
    suspend fun episode(raw: String, season: Int, num: Int): String? { val (t, _) = cleanTitle(raw)
        return memo("e|$t|$season|$num") {
            val id = tvmazeShow(t)?.optInt("id") ?: return@memo null
            strip(JSONObject(Http.request("https://api.tvmaze.com/shows/$id/episodebynumber?season=$season&number=$num")).str("summary")).ifBlank { null }  // i18n-ignore
        }
    }
}
