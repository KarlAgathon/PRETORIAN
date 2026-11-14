package com.pretorian.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Asks Google Gemini (your own free API key from aistudio.google.com) which of YOUR channels show a match in your country.
 * Gemini searches the web for the broadcaster, then picks from your channel names. Shown as "AI" (purple), never as certain.
 * Answers are kept for 6 hours, so the free limit is enough.
 */
object SportsAI {
    data class Provider(val id: String, val name: String, val free: Boolean, val search: Boolean, val keyHint: String, val site: String)
    val PROVIDERS = listOf(
        Provider("gemini", "Gemini (Google)", true, true, "AIza…", "aistudio.google.com"),
        Provider("openai", "ChatGPT (OpenAI)", false, true, "sk-…", "platform.openai.com"),
        Provider("claude", "Claude (Anthropic)", false, true, "sk-ant-…", "console.anthropic.com"),
        Provider("groq", "Groq", true, false, "gsk_…", "console.groq.com"),
        Provider("openrouter", "OpenRouter", true, false, "sk-or-…", "openrouter.ai"),
    )
    var provider: String get() = Prefs.str("ai_provider", "gemini"); set(v) = Prefs.put("ai_provider", v)
    val prov: Provider get() = PROVIDERS.firstOrNull { it.id == provider } ?: PROVIDERS[0]
    fun keyOf(id: String) = Prefs.str("ai_key_$id").ifBlank { if (id == "gemini") Prefs.str("gemini_key") else "" }
    fun setKey(id: String, v: String) = Prefs.put("ai_key_$id", v.trim())
    var key: String get() = keyOf(provider); set(v) = setKey(provider, v)
    val ready: Boolean get() = key.isNotBlank() && Prefs.bool("sport_src_ai", true)
    /** true when the last answer used a web search, false when it was only the AI's own knowledge */
    var lastSearch by androidx.compose.runtime.mutableStateOf(true)
    private val mem = HashMap<String, List<String>>()
    var dir: File? = null

    private val SPORTY = Regex("sport|arena|dazn|eurosport|espn|bein|setanta|klub|bundesliga|laliga|premier|tennis|nba|sky|canal|nova|max|tnt|prime|magenta|rtl|nitro|sat\\.?1|zdf|ard|orf|servus|srf|rts|hrt|bht|ftv|n1", RegexOption.IGNORE_CASE)

    /** short list of your channels that could show sport (names only, duplicates in other qualities removed) */
    private fun candidates(all: List<Channel>): List<String> =
        all.map { it.name.trim() }.filter { SPORTY.containsMatchIn(it) }.distinct()
            .sortedBy { Sports.regionRank(it) }.take(300)

    private class Http(val code: Int, val text: String)
    private fun post(url: String, body: JSONObject, headers: Map<String, String>): Http {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 90000; c.requestMethod = "POST"; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = c.responseCode
        val text = try { (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: "" } catch (e: Exception) { "" }
        c.disconnect()
        return Http(code, text)
    }
    private fun fail(code: Int, text: String): Nothing {
        val msg = try { JSONObject(text).let { o -> o.optJSONObject("error")?.str("message") ?: o.str("message") } } catch (e: Exception) { "" }
        throw java.io.IOException(when (code) {
            400 -> if (msg.contains("key", true)) tr("Ključ ne radi (pogrešan ključ)") else trf("AI greška %s", "400 " + msg.take(80))
            401, 403 -> tr("Ključ ne radi (pogrešan ključ ili nema dozvolu)")
            402 -> tr("Na AI računu nema kredita")
            429 -> tr("Dnevni limit ključa je potrošen")
            else -> trf("AI greška %s", "$code " + msg.take(80))
        })
    }

    /** Asks the chosen AI. With [search] it first tries a web search and falls back to the AI's own knowledge. */
    private fun ask(prompt: String, search: Boolean): String {
        val k = key
        var last: java.io.IOException? = null
        val tries = if (search && prov.search) listOf(true, false) else listOf(false)
        for (web in tries) {
            try {
                val r = when (provider) {
                    "openai" -> openai(prompt, web, k)
                    "claude" -> claude(prompt, web, k)
                    "groq" -> chat("https://api.groq.com/openai/v1/chat/completions", "llama-3.3-70b-versatile", prompt, k)
                    "openrouter" -> chat("https://openrouter.ai/api/v1/chat/completions", "meta-llama/llama-3.3-70b-instruct:free", prompt, k)
                    else -> gemini(prompt, web, k)
                }
                lastSearch = web
                return r
            } catch (e: java.io.IOException) {
                last = e
                // a wrong key stays wrong without the search too
                if (e.message?.contains(tr("Ključ ne radi")) == true) throw e
            }
        }
        throw last ?: java.io.IOException(tr("AI nije dostupan"))
    }

    private fun gemini(prompt: String, web: Boolean, k: String): String {
        var last: Http? = null
        for (m in listOf("gemini-flash-latest", "gemini-2.5-flash", "gemini-flash-lite-latest", "gemini-2.0-flash")) {
            val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            if (web) body.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
            val r = post("https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent", body, mapOf("x-goog-api-key" to k))
            if (r.code in 200..299) {
                val parts = JSONObject(r.text).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: return ""
                return (0 until parts.length()).joinToString("\n") { parts.optJSONObject(it)?.str("text") ?: "" }
            }
            last = r
            if (r.code == 400 && r.text.contains("API_KEY_INVALID")) fail(401, r.text)
            if (r.code == 401 || r.code == 403) fail(r.code, r.text)
        }
        fail(last?.code ?: 0, last?.text ?: "")
    }

    private fun openai(prompt: String, web: Boolean, k: String): String {
        val body = JSONObject().put("model", "gpt-4.1-mini").put("input", prompt)
        if (web) body.put("tools", JSONArray().put(JSONObject().put("type", "web_search")))
        val r = post("https://api.openai.com/v1/responses", body, mapOf("Authorization" to "Bearer $k"))
        if (r.code !in 200..299) fail(r.code, r.text)
        val out = JSONObject(r.text).optJSONArray("output") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until out.length()) out.optJSONObject(i)?.optJSONArray("content")?.let { c -> for (j in 0 until c.length()) sb.append(c.optJSONObject(j)?.str("text") ?: "") }
        return sb.toString()
    }

    private fun claude(prompt: String, web: Boolean, k: String): String {
        var last: Http? = null
        for (m in listOf("claude-haiku-5-5", "claude-haiku-4-5")) {
            val body = JSONObject().put("model", m).put("max_tokens", 1024)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            if (web) body.put("tools", JSONArray().put(JSONObject().put("type", "web_search_20250305").put("name", "web_search").put("max_uses", 3)))
            val r = post("https://api.anthropic.com/v1/messages", body, mapOf("x-api-key" to k, "anthropic-version" to "2023-06-01"))
            if (r.code in 200..299) {
                val c = JSONObject(r.text).optJSONArray("content") ?: return ""
                return (0 until c.length()).mapNotNull { c.optJSONObject(it)?.takeIf { o -> o.str("type") == "text" }?.str("text") }.joinToString("\n")
            }
            last = r
            if (r.code == 401 || r.code == 403) fail(r.code, r.text)
        }
        fail(last?.code ?: 0, last?.text ?: "")
    }

    private fun chat(url: String, model: String, prompt: String, k: String): String {
        val body = JSONObject().put("model", model).put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        val r = post(url, body, mapOf("Authorization" to "Bearer $k"))
        if (r.code !in 200..299) fail(r.code, r.text)
        return JSONObject(r.text).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.str("content") ?: ""
    }

    /** test the key without a web search: null when it works, else the problem */
    suspend fun test(): String? = withContext(Dispatchers.IO) {
        try { ask("Reply with the single word OK.", search = false); null } catch (e: Exception) { e.message ?: "?" }
    }

    /** your channels that show this match, according to Gemini with a web search */
    suspend fun channelsFor(ev: SportEvent, all: List<Channel>): List<Channel> = withContext(Dispatchers.IO) {
        if (!ready || ev.home == null || ev.away == null) return@withContext emptyList()
        val id = "${ev.id}|${Sports.country}|$provider"
        val names = mem[id] ?: readCache(id) ?: run {
            val cands = candidates(all)
            if (cands.isEmpty()) return@withContext emptyList()
            val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(ev.start))
            val prompt = """Football/sport match: ${ev.home.name} vs ${ev.away.name}, competition ${ev.league}, kick-off $date (local time).
Viewer country: ${Sports.country}. Also consider the Balkans (Bosnia, Serbia, Croatia) and Germany/Austria, where the viewer's IPTV channels come from.
If you can, search the web: which TV channels broadcast this exact match live? Otherwise use what you know about who holds the TV rights for this competition in these countries.
Then choose ONLY from this list of the viewer's channels (exact names). Return ONLY a JSON array of the chosen names, best first, at most 6, or [] if none fit:
${JSONArray(cands)}"""
            val answer = ask(prompt, search = true)
            val arr = Regex("\\[[\\s\\S]*?\\]").find(answer)?.value ?: "[]"
            val picked = try { JSONArray(arr).let { a -> (0 until a.length()).map { a.getString(it) } } } catch (e: Exception) { emptyList() }
            mem[id] = picked; writeCache(id, picked); picked
        }
        val byName = all.groupBy { it.name.trim() }
        names.flatMap { n -> byName[n] ?: emptyList() }.distinctBy { it.url }.sortedBy { Sports.regionRank(it.name) }.take(8)
    }

    /** one TV station that shows a match: country code and name */
    data class Station(val country: String, val name: String)

    /**
     * every TV station that broadcasts this match, worldwide, whether or not it is in your list
     * (the AI searches the web). Kept 6 hours per match.
     */
    suspend fun broadcasters(ev: SportEvent): List<Station> = withContext(Dispatchers.IO) {
        if (!ready) return@withContext emptyList()
        val who = if (ev.home != null && ev.away != null) "${ev.home.name} vs ${ev.away.name}" else ev.title
        val id = "tv|${ev.id}|$provider"
        val raw = mem[id] ?: readCache(id) ?: run {
            val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(ev.start))
            val prompt = """Sport event: $who, competition ${ev.league}, start $date.
Search the web: which TV channels and streaming services broadcast this exact event live, in any country?
Put first the viewer's country (${Sports.country}), then Bosnia and Herzegovina, Serbia, Croatia, Germany, Austria, Switzerland, then the rest.
Return ONLY a JSON array, at most 20 items, like [{"c":"BA","n":"Arena Sport 1"},{"c":"DE","n":"DAZN"}] (c = 2-letter country code, n = channel name). Return [] if unknown."""
            val answer = ask(prompt, search = true)
            val arr = Regex("\\[[\\s\\S]*\\]").find(answer)?.value ?: "[]"
            val list = try {
                JSONArray(arr).let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) }
                    .map { it.optString("c").uppercase().take(3) + "|" + it.optString("n").trim() }.filter { it.substringAfter('|').isNotBlank() } }
            } catch (e: Exception) { emptyList() }
            mem[id] = list; writeCache(id, list); list
        }
        raw.map { Station(it.substringBefore('|'), it.substringAfter('|')) }.distinctBy { it.country + it.name.lowercase() }
    }

    /** your channels that carry a station's name ("Arena Sport 1" → "BA| ARENA SPORT 1 HD") */
    fun matchStation(s: Station, all: List<Channel>): List<Channel> {
        fun n(x: String) = x.lowercase().replace(Regex("^[a-z]{2,4}\\s*[|:-]\\s*"), "").replace(Regex("\\b(hd|fhd|uhd|4k|sd|hevc|raw|vip|\\+1)\\b"), "").replace(Regex("[^a-z0-9]"), "")
        val want = n(s.name)
        if (want.length < 3) return emptyList()
        return all.filter { val c = n(it.name); c == want || (c.startsWith(want) && c.length - want.length <= 2) }
            .sortedBy { Sports.regionRank(it.name) }.distinctBy { it.url }.take(3)
    }

    private fun cacheFile() = dir?.let { File(it, "ai_sport.json") }
    private fun readCache(id: String): List<String>? = try {
        val o = JSONObject(cacheFile()!!.readText()).optJSONObject(id) ?: null
        if (o == null || System.currentTimeMillis() - o.optLong("t") > 6 * 3600_000L) null
        else o.getJSONArray("n").let { a -> (0 until a.length()).map { a.getString(it) } }
    } catch (e: Exception) { null }
    private fun writeCache(id: String, names: List<String>) {
        try {
            val f = cacheFile() ?: return
            val o = try { JSONObject(f.readText()) } catch (e: Exception) { JSONObject() }
            o.put(id, JSONObject().put("t", System.currentTimeMillis()).put("n", JSONArray(names)))
            f.writeText(o.toString())
        } catch (_: Exception) {}
    }
}
