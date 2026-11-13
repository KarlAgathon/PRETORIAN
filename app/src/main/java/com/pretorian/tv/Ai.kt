package com.pretorian.tv

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
    var key: String get() = Prefs.str("gemini_key"); set(v) = Prefs.put("gemini_key", v.trim())
    val ready: Boolean get() = key.isNotBlank()
    private val MODELS = listOf("gemini-flash-latest", "gemini-2.5-flash", "gemini-2.0-flash")
    private val mem = HashMap<String, List<String>>()
    var dir: File? = null

    private val SPORTY = Regex("sport|arena|dazn|eurosport|espn|bein|setanta|klub|bundesliga|laliga|premier|tennis|nba|sky|canal|nova|max|tnt|prime|magenta|rtl|nitro|sat\\.?1|zdf|ard|orf|servus|srf|rts|hrt|bht|ftv|n1", RegexOption.IGNORE_CASE)

    /** short list of your channels that could show sport (names only, duplicates in other qualities removed) */
    private fun candidates(all: List<Channel>): List<String> =
        all.map { it.name.trim() }.filter { SPORTY.containsMatchIn(it) }.distinct()
            .sortedBy { Sports.regionRank(it) }.take(300)

    private fun ask(prompt: String): String {
        var last: Exception? = null
        for (m in MODELS) {
            try {
                val c = URL("https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent").openConnection() as HttpURLConnection
                c.connectTimeout = 15000; c.readTimeout = 60000; c.requestMethod = "POST"; c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json"); c.setRequestProperty("x-goog-api-key", key)
                val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
                    .put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
                c.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = c.responseCode
                val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
                c.disconnect()
                if (code == 404) continue
                if (code == 429) throw java.io.IOException(tr("Dnevni limit Gemini ključa je potrošen"))
                if (code !in 200..299) throw java.io.IOException(trf("Gemini greška %s", code))
                val parts = JSONObject(text).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: return ""
                return (0 until parts.length()).joinToString("\n") { parts.optJSONObject(it)?.str("text") ?: "" }
            } catch (e: java.io.IOException) { last = e; if (e.message?.contains("Gemini") == true || e.message?.contains("limit") == true) throw e }
        }
        throw last ?: java.io.IOException(tr("Gemini nije dostupan"))
    }

    /** test the key: null when it works, else the problem */
    suspend fun test(): String? = withContext(Dispatchers.IO) {
        try { ask("Reply with the single word OK."); null } catch (e: Exception) { e.message ?: "?" }
    }

    /** your channels that show this match, according to Gemini with a web search */
    suspend fun channelsFor(ev: SportEvent, all: List<Channel>): List<Channel> = withContext(Dispatchers.IO) {
        if (!ready || ev.home == null || ev.away == null) return@withContext emptyList()
        val id = "${ev.id}|${Sports.country}"
        val names = mem[id] ?: readCache(id) ?: run {
            val cands = candidates(all)
            if (cands.isEmpty()) return@withContext emptyList()
            val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(ev.start))
            val prompt = """Football/sport match: ${ev.home.name} vs ${ev.away.name}, competition ${ev.league}, kick-off $date (local time).
Viewer country: ${Sports.country}. Also consider the Balkans (Bosnia, Serbia, Croatia) and Germany/Austria, where the viewer's IPTV channels come from.
Search the web: which TV channels broadcast this exact match live?
Then choose ONLY from this list of the viewer's channels (exact names). Return ONLY a JSON array of the chosen names, best first, at most 6, or [] if none fit:
${JSONArray(cands)}"""
            val answer = ask(prompt)
            val arr = Regex("\\[[\\s\\S]*?\\]").find(answer)?.value ?: "[]"
            val picked = try { JSONArray(arr).let { a -> (0 until a.length()).map { a.getString(it) } } } catch (e: Exception) { emptyList() }
            mem[id] = picked; writeCache(id, picked); picked
        }
        val byName = all.groupBy { it.name.trim() }
        names.flatMap { n -> byName[n] ?: emptyList() }.distinctBy { it.url }.sortedBy { Sports.regionRank(it.name) }.take(8)
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
