package com.pretorian.tv

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Stalker / Ministra portal (MAG boxes): handshake with the MAC address, then live TV and films.
 * A stream address is often made only when you play it, so channels carry "stalker://pid/kind?cmd=…"
 * and [resolve] asks the portal for the real link right before playing.
 */
class Stalker(private val p: Playlist) {
    companion object {
        private val tokens = HashMap<String, String>()
        private const val UA = "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3"  // i18n-ignore

        fun isLink(url: String) = url.startsWith("stalker://")

        /** the real stream address of a "stalker://" link (or the link itself when it is a normal one) */
        suspend fun resolve(ctx: Context, url: String): String = withContext(Dispatchers.IO) {
            if (!isLink(url)) return@withContext url
            val pid = url.removePrefix("stalker://").substringBefore('/')
            val kind = url.substringAfter("$pid/").substringBefore('?')
            val cmd = java.net.URLDecoder.decode(url.substringAfter("cmd=", ""), "UTF-8")
            val p = Store.byId(ctx, pid) ?: Store.active(ctx) ?: throw IllegalStateException("Playlist")  // i18n-ignore
            Stalker(p).link(kind, cmd)
        }

        /** a random-looking but fixed MAC in the MAG range for this box */
        fun suggestMac(ctx: Context): String {
            val id = android.provider.Settings.Secure.getString(ctx.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "pretorian"
            val h = MessageDigest.getInstance("MD5").digest(id.toByteArray())
            return "00:1A:79:" + (0..2).joinToString(":") { "%02X".format(h[it]) }  // i18n-ignore
        }
    }

    private val mac get() = p.mac.trim().uppercase()

    /** "http://host:port/c/" → "http://host:port/server/load.php"; a link that already ends in .php is used as it is */
    private val api: String get() {
        var b = p.server.trim().trimEnd('/')
        if (!b.startsWith("http")) b = "http://$b"
        if (b.endsWith(".php")) return b
        b = b.removeSuffix("/c")
        return if (b.contains("stalker_portal")) b.substringBefore("stalker_portal") + "stalker_portal/server/load.php" else "$b/server/load.php"
    }

    private fun call(query: String, auth: Boolean = true): JSONObject {
        val c = URL("$api?$query&JsHttpRequest=1-xml").openConnection() as HttpURLConnection
        c.connectTimeout = 12_000; c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("X-User-Agent", "Model: MAG250; Link: WiFi")  // i18n-ignore
        c.setRequestProperty("Referer", p.server.trim())
        c.setRequestProperty("Cookie", "mac=" + URLEncoder.encode(mac, "UTF-8") + "; stb_lang=en; timezone=Europe%2FBerlin")  // i18n-ignore
        if (auth) tokens[p.id]?.let { c.setRequestProperty("Authorization", "Bearer $it") }
        val code = c.responseCode
        if (code == 401 || code == 403) throw IllegalStateException(trf("Portal je odbio pristup (%s)", code))
        val text = (if (code >= 400) c.errorStream else c.inputStream)?.bufferedReader()?.use { it.readText() } ?: ""
        return try { JSONObject(text) } catch (e: Exception) { throw IllegalStateException(tr("Portal nije vratio podatke")) }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** handshake + profile (+ login with user and password when given); once per playlist and start */
    @Synchronized
    private fun ensure() {
        if (tokens[p.id] != null) return
        if (mac.length < 17) throw IllegalStateException(tr("Unesi MAC adresu (00:1A:79:…)"))
        val hs = call("type=stb&action=handshake&token=&prehash=0", auth = false)
        val tok = hs.optJSONObject("js")?.optString("token").orEmpty()
        if (tok.isBlank()) throw IllegalStateException(tr("Portal nije prihvatio MAC adresu"))
        tokens[p.id] = tok
        val sn = p.serial.ifBlank { MessageDigest.getInstance("MD5").digest(mac.toByteArray()).joinToString("") { "%02X".format(it) }.take(13) }
        val dev = p.deviceId.ifBlank { MessageDigest.getInstance("SHA-256").digest(mac.toByteArray()).joinToString("") { "%02X".format(it) } }
        val dev2 = p.deviceId2.ifBlank { dev }
        call("type=stb&action=get_profile&hd=1&ver=" + enc("ImageDescription: 0.2.18-r23-250; PORTAL version: 5.6.1; API Version: JS API version: 343") +  // i18n-ignore
            "&num_banks=2&sn=${enc(sn)}&stb_type=MAG250&client_type=STB&image_version=218&video_out=hdmi&device_id=${enc(dev)}&device_id2=${enc(dev2)}" +  // i18n-ignore
            "&signature=&auth_second_step=1&hw_version=1.7-BD-00&not_valid_token=0&api_signature=262")  // i18n-ignore
        if (p.user.isNotBlank()) {
            val r = call("type=stb&action=do_auth&login=${enc(p.user)}&password=${enc(p.pass)}&device_id=${enc(dev)}&device_id2=${enc(dev2)}")
            if (r.opt("js") == false) throw IllegalStateException(tr("Pogrešno korisničko ime ili lozinka"))
        }
    }

    private fun <T> withLogin(block: () -> T): T {
        ensure()
        return try { block() } catch (e: IllegalStateException) {
            // the token ran out: one new handshake
            tokens.remove(p.id); ensure(); block()
        }
    }

    /** checks the portal; returns the subscription end ("" unknown) */
    fun login(): String = withLogin {
        val js = try { call("type=account_info&action=get_main_info").optJSONObject("js") } catch (e: Exception) { null }
        val end = js?.optString("end_date").orEmpty().ifBlank { js?.optString("phone").orEmpty() }
        try { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(end.take(10))?.time?.div(1000)?.toString() ?: "" } catch (e: Exception) { "" }  // i18n-ignore
    }

    fun liveCategories(): List<Category> = withLogin {
        val a = call("type=itv&action=get_genres").optJSONArray("js") ?: JSONArray()
        (0 until a.length()).mapNotNull { a.optJSONObject(it) }.filter { it.optString("id") != "*" }.map { Category(it.optString("id"), it.optString("title")) }
    }

    fun allChannels(): List<Channel> = withLogin {
        val js = call("type=itv&action=get_all_channels").optJSONObject("js")
        val a = js?.optJSONArray("data") ?: JSONArray()
        (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { o ->
            Channel(o.optString("id"), o.optString("number").toIntOrNull() ?: 0, o.optString("name"), o.optString("logo").ifBlank { null }?.let { abs(it) },
                o.optString("tv_genre_id"), linkOf("itv", o.optString("cmd")), o.optString("xmltv_id").ifBlank { null }, p.id)
        }
    }

    fun channels(groupId: String) = allChannels().filter { it.group == groupId }

    fun vodCategories(): List<Category> = withLogin {
        val a = call("type=vod&action=get_categories").optJSONArray("js") ?: JSONArray()
        (0 until a.length()).mapNotNull { a.optJSONObject(it) }.filter { it.optString("id") != "*" }.map { Category(it.optString("id"), it.optString("title")) }
    }

    /** all films of a category, page by page (at most 40 pages) */
    fun movies(cat: String): List<Vod> = withLogin {
        val out = ArrayList<Vod>()
        var page = 1
        while (page <= 40) {
            val js = call("type=vod&action=get_ordered_list&category=${enc(cat)}&genre=*&sortby=added&p=$page").optJSONObject("js") ?: break
            val a = js.optJSONArray("data") ?: break
            if (a.length() == 0) break
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                if (o.optInt("is_series") == 1) continue   // series of a Stalker portal are not shown yet
                out += Vod(o.optString("id"), o.optString("name"), o.optString("screenshot_uri").ifBlank { null }?.let { abs(it) },
                    o.optString("rating_imdb").ifBlank { null }, linkOf("vod", o.optString("cmd")))
            }
            val total = js.optInt("total_items"); val per = js.optInt("max_page_items", 14).coerceAtLeast(1)
            if (page * per >= total) break
            page++
        }
        out
    }

    /** the stream address for a channel or film command */
    fun link(kind: String, cmd: String): String = withLogin {
        val r = call("type=$kind&action=create_link&cmd=${enc(cmd)}&series=&forced_storage=0&disable_ad=0&download=0")
        val c = r.optJSONObject("js")?.optString("cmd").orEmpty()
        clean(c.ifBlank { cmd }).ifBlank { throw IllegalStateException(tr("Portal nije dao link")) }
    }

    private fun clean(cmd: String) = cmd.trim().removePrefix("ffmpeg ").removePrefix("ffrt ").removePrefix("auto ").trim()

    /** a command that already holds a real address is played directly; others are asked for at play time */
    private fun linkOf(kind: String, cmd: String): String {
        val c = clean(cmd)
        return if (c.startsWith("http") && !c.contains("localhost") && !c.contains("127.0.0.1")) c
        else listOf("stalker://", p.id, "/", kind, "?cmd=", enc(cmd)).joinToString("")
    }

    private fun abs(u: String) = if (u.startsWith("http")) u else p.server.trim().substringBefore("/c").trimEnd('/') + "/" + u.trimStart('/')
}
