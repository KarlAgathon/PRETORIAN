package com.pretorian.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Your own TV guide sources (XMLTV links). Used when the provider sends no guide for a channel.
 * A source can be for every playlist or only for one. The first one that knows the channel wins.
 */
object EpgSources {
    data class Src(val id: String, val url: String, val name: String, val on: Boolean = true, val pid: String? = null,
                   val at: Long = 0, val channels: Int = -1, val error: String? = null) {
        fun toJson(): JSONObject = JSONObject().put("id", id).put("url", url).put("name", name).put("on", on).put("pid", pid ?: "")
            .put("at", at).put("ch", channels).put("err", error ?: "")
        companion object {
            fun from(o: JSONObject) = Src(o.str("id"), o.str("url"), o.str("name"), o.optBoolean("on", true), o.str("pid").ifBlank { null },
                o.optLong("at"), o.optInt("ch", -1), o.str("err").ifBlank { null })
        }
    }
    var rev by androidx.compose.runtime.mutableIntStateOf(0)

    fun list(): List<Src> {
        val a = try { JSONArray(Prefs.str("epg_sources", "[]")) } catch (e: Exception) { JSONArray() }
        val l = (0 until a.length()).map { Src.from(a.getJSONObject(it)) }
        // older versions had one link
        if (l.isEmpty() && Prefs.epgUrl.isNotBlank()) {
            val s = Src("e1", Prefs.epgUrl, nameOf(Prefs.epgUrl)); save(listOf(s)); return listOf(s)
        }
        return l
    }
    fun save(l: List<Src>) { val a = JSONArray(); l.forEach { a.put(it.toJson()) }; Prefs.put("epg_sources", a.toString()); rev++ }
    fun nameOf(url: String) = url.removePrefix("https://").removePrefix("http://").substringBefore("/").ifBlank { "EPG" }

    fun add(url: String, pid: String?): Src {
        val s = Src("e" + System.currentTimeMillis().toString(36), url.trim(), nameOf(url), true, pid)
        save(list() + s); return s
    }
    fun update(s: Src) = save(list().map { if (it.id == s.id) s else it })
    fun delete(s: Src) = save(list().filter { it.id != s.id })
    fun move(s: Src, d: Int) {
        val l = list().toMutableList(); val i = l.indexOfFirst { it.id == s.id }; val j = i + d
        if (i < 0 || j !in l.indices) return
        l.add(j, l.removeAt(i)); save(l)
    }

    /** links that apply to a playlist (its own first, then the ones for every playlist) */
    fun urls(pid: String?): List<String> = list().filter { it.on && (it.pid == null || it.pid == pid) }
        .sortedBy { if (it.pid != null) 0 else 1 }.map { it.url }.distinct()
    val any: Boolean get() = list().any { it.on }

    /** downloads a source to check it: how many channels it knows */
    suspend fun refresh(s: Src): Src = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val r = try {
            val progs = Xmltv.read(s.url, null, now - 3600_000L, now + 6 * 3600_000L)
            OwnGuide.drop(s.url)
            s.copy(at = now, channels = progs.map { it.channel }.distinct().size, error = null)
        } catch (e: Exception) { s.copy(at = now, error = e.message ?: e.javaClass.simpleName) }
        update(r); r
    }
}
