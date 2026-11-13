package com.pretorian.tv

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/* ============================================================== favourite lists (shared by all playlists) */

data class FavList(val id: String, val name: String)

object Favs {
    /** bumps on every change so screens reload */
    var rev by mutableIntStateOf(0)
    private const val LISTS = "fav_lists"

    private fun chJson(c: Channel) = JSONObject().put("id", c.id).put("name", c.name).put("logo", c.logo ?: "").put("url", c.url)
        .put("epg", c.epg ?: "").put("pid", c.pid)

    fun lists(ctx: Context): List<FavList> {
        var a = try { JSONArray(Prefs.str(LISTS, "[]")) } catch (e: Exception) { JSONArray() }
        if (a.length() == 0) {
            // first start of 0.3: the old favourites of every playlist become the list "Favoriti"
            val merged = JSONArray()
            Store.playlists(ctx).forEach { p ->
                try { val old = JSONArray(Prefs.str("fav_${p.id}", "[]")); for (i in 0 until old.length()) merged.put(old.getJSONObject(i).put("pid", p.id)) } catch (_: Exception) {}
            }
            Prefs.put("favch_main", merged.toString())
            a = JSONArray().put(JSONObject().put("id", "main").put("name", "Favoriti"))
            Prefs.put(LISTS, a.toString())
        }
        return (0 until a.length()).map { val o = a.getJSONObject(it); FavList(o.str("id"), o.str("name")) }
    }

    private fun saveLists(l: List<FavList>) {
        val a = JSONArray(); l.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name)) }
        Prefs.put(LISTS, a.toString()); rev++
    }

    fun addList(ctx: Context, name: String): FavList {
        val f = FavList("l${System.currentTimeMillis()}", name); saveLists(lists(ctx) + f); return f
    }
    fun renameList(ctx: Context, id: String, name: String) = saveLists(lists(ctx).map { if (it.id == id) it.copy(name = name) else it })
    fun deleteList(ctx: Context, id: String) { saveLists(lists(ctx).filter { it.id != id }); Prefs.put("favch_$id", "[]") }
    fun moveList(ctx: Context, id: String, d: Int) {
        val l = lists(ctx).toMutableList(); val i = l.indexOfFirst { it.id == id }; val j = i + d
        if (i < 0 || j !in l.indices) return
        l.add(j, l.removeAt(i)); saveLists(l)
    }

    fun channels(id: String): List<Channel> = try {
        val a = JSONArray(Prefs.str("favch_$id", "[]"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Channel(o.str("id"), it + 1, o.str("name"), o.str("logo").orNullIfBlank(), FAV_PREFIX + id, o.str("url"), o.str("epg").orNullIfBlank(), o.str("pid"))
        }
    } catch (e: Exception) { emptyList() }

    private fun save(id: String, l: List<Channel>) { val a = JSONArray(); l.forEach { a.put(chJson(it)) }; Prefs.put("favch_$id", a.toString()); rev++ }

    fun contains(id: String, ch: Channel) = channels(id).any { it.url == ch.url }
    fun inAny(ctx: Context, ch: Channel) = lists(ctx).any { contains(it.id, ch) }
    fun toggle(id: String, ch: Channel) { val l = channels(id).toMutableList(); if (!l.removeAll { it.url == ch.url }) l.add(ch); save(id, l) }
    fun rename(id: String, ch: Channel, name: String) = save(id, channels(id).map { if (it.url == ch.url) it.copy(name = name) else it })
    fun move(id: String, ch: Channel, d: Int) {
        val l = channels(id).toMutableList(); val i = l.indexOfFirst { it.url == ch.url }; val j = i + d
        if (i < 0 || j !in l.indices) return
        l.add(j, l.removeAt(i)); save(id, l)
    }
}

const val FAV_PREFIX = "__fav__"
fun isFavGroup(id: String) = id.startsWith(FAV_PREFIX)
fun favListId(groupId: String) = groupId.removePrefix(FAV_PREFIX)

/* ============================================================== watched / continue watching */

data class Progress(
    val url: String, val title: String, val poster: String?, val pos: Long, val dur: Long, val ts: Long,
    val series: String? = null, val season: Int? = null, val episode: Int? = null, val next: Boolean = false, val done: Boolean = false,
    val pid: String = "",
) {
    val fraction get() = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    val watched get() = done || fraction >= 0.9f
    val started get() = !watched && pos > 30_000
}

object Watch {
    var rev by mutableIntStateOf(0)
    private var map: LinkedHashMap<String, Progress>? = null
    private lateinit var file: File

    private fun all(ctx: Context): LinkedHashMap<String, Progress> {
        map?.let { return it }
        file = File(ctx.filesDir, "progress.json")
        val m = LinkedHashMap<String, Progress>()
        try {
            val a = JSONArray(file.readText())
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val p = Progress(o.str("url"), o.str("title"), o.str("poster").orNullIfBlank(), o.optLong("pos"), o.optLong("dur"), o.optLong("ts"),
                    o.str("series").orNullIfBlank(), o.optInt("season", -1).takeIf { it >= 0 }, o.optInt("episode", -1).takeIf { it >= 0 },
                    o.optBoolean("next"), o.optBoolean("done"), o.str("pid"))
                m[p.url] = p
            }
        } catch (_: Exception) {}
        map = m
        return m
    }

    private fun persist() {
        val m = map ?: return
        val a = JSONArray()
        m.values.sortedByDescending { it.ts }.take(600).forEach { p ->
            a.put(JSONObject().put("url", p.url).put("title", p.title).put("poster", p.poster ?: "").put("pos", p.pos).put("dur", p.dur).put("ts", p.ts)
                .put("series", p.series ?: "").put("season", p.season ?: -1).put("episode", p.episode ?: -1).put("next", p.next).put("done", p.done).put("pid", p.pid))
        }
        try { file.writeText(a.toString()) } catch (_: Exception) {}
        rev++
    }

    fun get(ctx: Context, url: String): Progress? = all(ctx)[url]

    fun save(ctx: Context, p: Progress) { all(ctx)[p.url] = p; persist() }

    fun setWatched(ctx: Context, url: String, title: String, poster: String?, on: Boolean) {
        val m = all(ctx)
        if (on) m[url] = (m[url] ?: Progress(url, title, poster, 0, 0, System.currentTimeMillis())).copy(done = true, next = false, ts = System.currentTimeMillis())
        else m.remove(url)
        persist()
    }

    fun remove(ctx: Context, url: String) { all(ctx).remove(url); persist() }

    /** started and not finished, plus "next episode" entries, newest first */
    fun continueList(ctx: Context): List<Progress> = all(ctx).values.filter { (it.started || it.next) && !it.done }.sortedByDescending { it.ts }.take(30)
}

/* ============================================================== reminders */

data class Reminder(
    val id: String, val title: String, val start: Long, val channelUrl: String?, val channelName: String?, val logo: String?,
    val pid: String?, val kind: String, val fired: Boolean = false,
)

object Reminders {
    var rev by mutableIntStateOf(0)
    /** the reminder showing on screen now */
    var showing by mutableStateOf<Reminder?>(null)

    var before: Int get() = Prefs.str("rem_before", "5").toIntOrNull() ?: 5; set(v) = Prefs.put("rem_before", v.toString())
    var popupSec: Int get() = Prefs.str("rem_popup", "20").toIntOrNull() ?: 20; set(v) = Prefs.put("rem_popup", v.toString())
    /** 0 = only remind, 1 = switch to the channel */
    var action: Int get() = Prefs.str("rem_action", "0").toIntOrNull() ?: 0; set(v) = Prefs.put("rem_action", v.toString())
    var wake: Boolean get() = Prefs.bool("rem_wake", false); set(v) = Prefs.put("rem_wake", v)

    fun all(): List<Reminder> = try {
        val a = JSONArray(Prefs.str("reminders", "[]"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Reminder(o.str("id"), o.str("title"), o.optLong("start"), o.str("ch").orNullIfBlank(), o.str("chName").orNullIfBlank(), o.str("logo").orNullIfBlank(),
                o.str("pid").orNullIfBlank(), o.str("kind"), o.optBoolean("fired"))
        }
    } catch (e: Exception) { emptyList() }

    private fun save(l: List<Reminder>) {
        val a = JSONArray()
        l.filter { it.start > System.currentTimeMillis() - 6 * 3600_000L }.forEach {
            a.put(JSONObject().put("id", it.id).put("title", it.title).put("start", it.start).put("ch", it.channelUrl ?: "").put("chName", it.channelName ?: "")
                .put("logo", it.logo ?: "").put("pid", it.pid ?: "").put("kind", it.kind).put("fired", it.fired))
        }
        Prefs.put("reminders", a.toString()); rev++
    }

    fun has(title: String, start: Long) = all().any { it.title == title && it.start == start }
    fun add(r: Reminder) = save(all().filter { !(it.title == r.title && it.start == r.start) } + r)
    fun remove(id: String) = save(all().filter { it.id != id })
    fun toggle(r: Reminder): Boolean = if (has(r.title, r.start)) { save(all().filter { !(it.title == r.title && it.start == r.start) }); false } else { add(r); true }
    fun markFired(id: String) = save(all().map { if (it.id == id) it.copy(fired = true) else it })

    /** reminders whose time has come and that were not shown yet */
    fun due(): List<Reminder> {
        val now = System.currentTimeMillis()
        return all().filter { !it.fired && it.start - before * 60_000L <= now && it.start + 30 * 60_000L > now }
    }
}

/* ============================================================== notifications and "new" */

data class Note(val id: String, val kind: String, val text: String, val time: Long, val read: Boolean)

object Notes {
    val list = mutableStateListOf<Note>()
    private var loaded = false

    fun load() {
        if (loaded) return; loaded = true
        try {
            val a = JSONArray(Prefs.str("notes", "[]"))
            for (i in 0 until a.length()) { val o = a.getJSONObject(i); list.add(Note(o.str("id"), o.str("kind"), o.str("text"), o.optLong("time"), o.optBoolean("read"))) }
        } catch (_: Exception) {}
    }

    private fun save() {
        val a = JSONArray(); list.take(50).forEach { a.put(JSONObject().put("id", it.id).put("kind", it.kind).put("text", it.text).put("time", it.time).put("read", it.read)) }
        Prefs.put("notes", a.toString())
    }

    fun add(kind: String, text: String, id: String = "$kind:${System.currentTimeMillis()}") {
        load()
        if (list.any { it.id == id }) return
        list.add(0, Note(id, kind, text, System.currentTimeMillis(), false)); save()
    }

    fun unread(kind: String? = null) = list.count { !it.read && (kind == null || it.kind == kind) }
    fun readAll(kind: String? = null) {
        for (i in list.indices) if (kind == null || list[i].kind == kind) list[i] = list[i].copy(read = true)
        save()
    }
    fun clear() { list.clear(); save() }
}

/** Remembers which movies and series a playlist had, so new ones get a "NEW" badge after an update. */
object NewContent {
    var rev by mutableIntStateOf(0)
    /** key "pid|kind|id" -> {t: time first seen, c: category, n: name, i: image, r: rating, u: url} */
    private val fresh = HashMap<String, JSONObject>()
    private var loaded = false
    private const val DAYS7 = 7 * 86_400_000L

    private fun key(pid: String, kind: String, id: String) = "$pid|$kind|$id"

    private fun load() {
        if (loaded) return; loaded = true
        try { val o = JSONObject(Prefs.str("new_items", "{}")); o.keys().forEach { k -> o.optJSONObject(k)?.let { fresh[k] = it } } } catch (_: Exception) {}
        val now = System.currentTimeMillis(); fresh.entries.removeAll { now - it.value.optLong("t") > DAYS7 }
    }
    private fun save() { val o = JSONObject(); fresh.forEach { (k, v) -> o.put(k, v) }; Prefs.put("new_items", o.toString()); rev++ }

    /** set at start: needed to know which groups you hid */
    var appCtx: Context? = null

    /** new items whose group you did not hide: a hidden group stays hidden, also for "new" */
    private fun visible(): List<Map.Entry<String, JSONObject>> {
        val c = appCtx ?: return fresh.entries.toList()
        val ovrs = HashMap<String, Ovr>()
        return fresh.entries.filter { e ->
            val parts = e.key.split('|'); val pid = parts.getOrNull(0) ?: return@filter true; val kind = parts.getOrNull(1) ?: return@filter true
            val cat = e.value.str("c")
            cat.isBlank() || !ovrs.getOrPut(pid) { Ovr(c, pid) }.isHidden(kind, cat)
        }
    }

    fun isNew(pid: String, kind: String, id: String): Boolean { load(); return fresh.containsKey(key(pid, kind, id)) }
    fun seen(pid: String, kind: String, id: String) { load(); if (fresh.remove(key(pid, kind, id)) != null) save() }
    fun count(kind: String): Int { load(); return visible().count { it.key.split('|').getOrNull(1) == kind } }
    fun countCat(pid: String, kind: String, cat: String): Int { load(); return visible().count { it.key.startsWith("$pid|$kind|") && it.value.str("c") == cat } }
    fun clear(kind: String? = null) { load(); fresh.keys.removeAll { kind == null || it.split('|').getOrNull(1) == kind }; save() }

    /** the new items of a playlist, newest first: (id, name, image, rating, url) */
    fun items(pid: String, kind: String): List<JSONObject> { load(); return visible().filter { it.key.startsWith("$pid|$kind|") }
        .sortedByDescending { it.value.optLong("t") }.map { it.value.put("id", it.key.substringAfterLast('|')) } }

    /** compares the playlist's movies and series with the last snapshot (at most once every 12 hours, or [force]) */
    suspend fun scan(ctx: Context, p: Playlist, force: Boolean = false) = withContext(Dispatchers.IO) {
        if (p.type != SourceType.XTREAM) return@withContext
        val stamp = "new_scan_${p.id}"
        if (!force && System.currentTimeMillis() - Prefs.long(stamp) < 12 * 3600_000L) return@withContext
        load()
        val x = Xtream(p)
        var added = 0
        for (kind in listOf("vod", "series")) {
            val f = File(ctx.filesDir, "snap_${p.id}_$kind.txt")
            val old = try { if (f.exists()) f.readText().split(',').toHashSet() else null } catch (e: Throwable) { null }
            // read piece by piece: keep only the ids and the few new items
            val ids = StringBuilder()
            val fresh0 = ArrayList<JSONObject>()
            val ok = try {
                x.catalogEach(kind) { o ->
                    val id = o.str("id")
                    if (ids.isNotEmpty()) ids.append(',')
                    ids.append(id)
                    if (old != null && id !in old && fresh0.size < 2000) fresh0 += o
                }
                true
            } catch (e: Throwable) { false }   // also OutOfMemoryError: never take the app down for the "new" badges
            if (!ok || ids.isEmpty()) continue
            if (old != null) {
                val now = System.currentTimeMillis()
                fresh0.forEach { fresh[key(p.id, kind, it.str("id"))] = it.put("t", now); added++ }
                // the message counts only what you can see: nothing from hidden groups
                val ovr = Ovr(ctx, p.id)
                val shown = fresh0.count { o -> o.str("c").let { it.isBlank() || !ovr.isHidden(kind, it) } }
                if (shown > 0) Notes.add(kind, trf(if (kind == "vod") "Novih filmova: %s" else "Novih serija: %s", shown) + " · " + p.name)
            }
            f.writeText(ids.toString())
        }
        Prefs.put(stamp, System.currentTimeMillis())
        if (added > 0) save() else rev++
    }
}
