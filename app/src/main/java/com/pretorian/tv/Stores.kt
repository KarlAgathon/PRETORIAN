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
            val a = JSONArray(android.util.AtomicFile(file).openRead().bufferedReader().use { it.readText() })
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

    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var pending: java.util.concurrent.Future<*>? = null

    @Synchronized
    fun flush() {
        pending?.get()
        pending = null
    }

    @Synchronized
    private fun persist(background: Boolean = false) {
        val entries = map?.values?.toList() ?: return
        pending = writer.submit { writeHistory(entries) }
        if (!background) flush()
        rev++
    }

    fun reloadAfterRestore() {
        flush()
        map = null
        alt.clear()
        rev++
    }

    private fun writeHistory(entries: List<Progress>) {
        val a = JSONArray()
        entries.sortedByDescending { it.ts }.take(600).forEach { p ->
            a.put(JSONObject().put("url", p.url).put("title", p.title).put("poster", p.poster ?: "").put("pos", p.pos).put("dur", p.dur).put("ts", p.ts)
                .put("series", p.series ?: "").put("season", p.season ?: -1).put("episode", p.episode ?: -1).put("next", p.next).put("done", p.done).put("pid", p.pid))
        }
        val atomic = android.util.AtomicFile(file)
        try {
            val stream = atomic.startWrite()
            try {
                stream.write(a.toString().toByteArray())
                atomic.finishWrite(stream)
            } catch (failure: Exception) {
                atomic.failWrite(stream)
                throw failure
            }
        } catch (failure: java.io.IOException) {
            android.util.Log.w("Watch", "Could not save playback progress", failure) // i18n-ignore
        }
    }

    fun get(ctx: Context, url: String): Progress? = all(ctx)[url]

    fun save(ctx: Context, current: Progress, next: Progress? = null, background: Boolean = false) {
        val entries = all(ctx)
        entries[current.url] = current
        if (next != null) entries[next.url] = next
        persist(background)
    }

    fun setWatched(ctx: Context, url: String, title: String, poster: String?, on: Boolean) {
        val m = all(ctx)
        if (on) m[url] = (m[url] ?: Progress(url, title, poster, 0, 0, System.currentTimeMillis())).copy(done = true, next = false, ts = System.currentTimeMillis())
        else m.remove(url)
        persist()
    }

    fun remove(ctx: Context, url: String) { all(ctx).remove(url); persist() }

    /** started and not finished, plus "next episode" entries, newest first — only from playlists that are switched on.
     *  A switched-off playlist keeps its entries: they come back when it is switched on again. */
    fun continueList(ctx: Context): List<Progress> {
        val on = Store.actives(ctx).map { it.id }.toSet()
        if (on.isEmpty()) return emptyList()
        val single = Store.playlists(ctx).size == 1
        return all(ctx).values.filter { (it.started || it.next) && !it.done && (owner(ctx, it, single) in on || alt[it.url]?.first in on) }
            .sortedByDescending { it.ts }
            // the same film / episode from two playlists: only the newest one
            .distinctBy { (it.series?.let { s -> "s:" + key(s) + ":" + it.season + ":" + it.episode } ?: ("m:" + key(it.title))) }
            .take(30)
    }

    /**
     * Older progress entries may predate poster persistence. Recover missing artwork
     * from active Xtream catalogs without changing playback position or timestamps.
     */
    suspend fun enrichMissingPosters(ctx: Context) {
        val missing = all(ctx).values.filter { it.poster.isNullOrBlank() && (it.started || it.next) && !it.done }
        if (missing.isEmpty()) return
        val active = Store.actives(ctx).filter { it.type == SourceType.XTREAM }
        if (active.isEmpty()) return
        val resolved = HashMap<String, String>()
        for (pl in active) {
            val needMovies = missing.any { it.series == null && it.url.let { u -> owner(ctx, it) == pl.id } }
            val needSeries = missing.any { it.series != null && owner(ctx, it) == pl.id }
            if (needMovies) try {
                val wanted = missing.filter { it.series == null && owner(ctx, it) == pl.id }
                Repo.catalog(pl, "vod").forEach { item ->
                    val image = item.str("i").trim()
                    if (image.isNotBlank()) {
                        val exactUrl = item.str("u")
                        wanted.filter { it.url == exactUrl || key(it.title) == key(item.str("n")) }
                            .forEach { resolved[it.url] = image }
                    }
                }
            } catch (_: Exception) {}
            if (needSeries) try {
                val wanted = missing.filter { it.series != null && owner(ctx, it) == pl.id }
                Repo.catalog(pl, "series").forEach { item ->
                    val image = item.str("i").trim()
                    if (image.isNotBlank()) {
                        val catalogName = key(item.str("n"))
                        wanted.filter { catalogName.isNotBlank() && key(it.series.orEmpty()) == catalogName }
                            .forEach { resolved[it.url] = image }
                    }
                }
            } catch (_: Exception) {}
        }
        if (resolved.isNotEmpty()) {
            val m = all(ctx)
            resolved.forEach { (url, poster) -> m[url]?.let { old ->
                if (old.poster.isNullOrBlank()) m[url] = old.copy(poster = poster)
            } }
            persist()
        }
    }

    /* ---- the same film or episode in another, switched-on playlist: Continue watching keeps it and plays it from there */
    private val alt = java.util.concurrent.ConcurrentHashMap<String, Pair<String, String>>()   // old link -> (playlist id, link there)
    fun altPid(url: String): String? = alt[url]?.first
    private fun key(s: String) = Sports.norm(cleanTitle(s).first).replace(Regex("[^a-z0-9]"), "")

    /** looks for every entry of a switched-off playlist in the switched-on ones (by name, season and episode) */
    suspend fun findAlternatives(ctx: Context) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val on = Store.actives(ctx)
        if (on.isEmpty()) return@withContext
        val onIds = on.map { it.id }.toSet()
        val single = Store.playlists(ctx).size == 1
        val need = all(ctx).values.filter { (it.started || it.next) && !it.done && owner(ctx, it, single) !in onIds && alt[it.url]?.first !in onIds }
        var found = 0
        for (pr in need) for (pl in on) {
            val hit = try { kotlinx.coroutines.withTimeoutOrNull(20_000) { match(pl, pr) } } catch (_: Exception) { null }
            if (hit != null) { alt[pr.url] = pl.id to hit; found++; break }
        }
        if (found > 0) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { rev++ }
    }

    private suspend fun match(pl: Playlist, pr: Progress): String? {
        if (pl.type != SourceType.XTREAM) return null
        if (pr.series != null) {
            val k = key(pr.series)
            val o = Repo.catalog(pl, "series").firstOrNull { key(it.str("n")) == k } ?: return null
            return Repo.episodes(pl, o.str("id")).firstOrNull { it.season == pr.season && it.num == pr.episode }?.url
        }
        val k = key(pr.title)
        return Repo.catalog(pl, "vod").firstOrNull { key(it.str("n")) == k }?.str("u")?.ifBlank { null }
    }

    /** what to play: the entry itself, or the same film / episode in a switched-on playlist (the progress goes with it) */
    fun playable(ctx: Context, p: Progress): Progress {
        if (blockedOwn(ctx, p) == null) return p
        val (pid, url) = alt[p.url] ?: return p
        if (Store.actives(ctx).none { it.id == pid }) return p
        val moved = (get(ctx, url) ?: p.copy(url = url, pid = pid)).let { if (it.ts < p.ts) p.copy(url = url, pid = pid) else it }
        save(ctx, moved)
        return moved
    }

    /** the playlist an entry came from (older entries without it: found from the link) */
    fun owner(ctx: Context, p: Progress, single: Boolean = Store.playlists(ctx).size == 1): String? =
        p.pid.ifBlank { null } ?: Store.ownerOf(ctx, p.url)?.id ?: if (single) Store.playlists(ctx).firstOrNull()?.id else null

    /** null when it can be played; otherwise a short reason (playlist off, deleted or expired) — never plays from a dead list */
    fun blocked(ctx: Context, p: Progress): String? {
        val own = blockedOwn(ctx, p) ?: return null
        val a = alt[p.url]
        return if (a != null && Store.actives(ctx).any { it.id == a.first }) null else own
    }

    private fun blockedOwn(ctx: Context, p: Progress): String? {
        val id = owner(ctx, p) ?: return null
        val pl = Store.playlists(ctx).firstOrNull { it.id == id } ?: return tr("Playlista više ne postoji")
        if (!pl.active) return tr("Playlista je isključena")
        if (Store.expired(pl)) return tr("Playlista nije dostupna (istekla ili offline)")
        return null
    }

    /** a deleted playlist takes its progress with it */
    fun removePlaylist(ctx: Context, pid: String) {
        CatalogDb.forget(pid)
        val m = all(ctx); val single = Store.playlists(ctx).size <= 1
        m.values.filter { owner(ctx, it, single) == pid || it.pid == pid }.map { it.url }.forEach { m.remove(it) }
        persist()
    }
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

    var before: Int get() = offsets().maxOrNull() ?: 5; set(v) = Prefs.put("rem_before", v.toString())
    /** minutes before the start, several at once (e.g. 30 and 5); one place for shows and matches */
    fun offsets(): List<Int> = Prefs.str("rem_offsets").ifBlank { Prefs.str("rem_before", "5") }.split(',').mapNotNull { it.trim().toIntOrNull() }.distinct().sortedDescending().ifEmpty { listOf(5) }
    fun setOffsets(l: List<Int>) = Prefs.put("rem_offsets", l.ifEmpty { listOf(0) }.joinToString(","))
    private fun firedSet(): MutableSet<String> = Prefs.str("rem_fired").split(',').filter { it.isNotBlank() }.toMutableSet()
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
        val keepH = Prefs.str("rem_keep", "24").toLongOrNull() ?: 24L
        l.filter { keepH <= 0 || it.start > System.currentTimeMillis() - keepH * 3600_000L }.forEach {
            a.put(JSONObject().put("id", it.id).put("title", it.title).put("start", it.start).put("ch", it.channelUrl ?: "").put("chName", it.channelName ?: "")
                .put("logo", it.logo ?: "").put("pid", it.pid ?: "").put("kind", it.kind).put("fired", it.fired))
        }
        Prefs.put("reminders", a.toString()); rev++
    }

    fun has(title: String, start: Long) = all().any { it.title == title && it.start == start }
    fun add(r: Reminder) = save(all().filter { !(it.title == r.title && it.start == r.start) } + r)
    fun remove(id: String) = save(all().filter { it.id != id })
    fun toggle(r: Reminder): Boolean = if (has(r.title, r.start)) { save(all().filter { !(it.title == r.title && it.start == r.start) }); false } else { add(r); true }
    /** marks every reminder time that has come for [id]; the reminder is done after its last (smallest) time */
    fun markFired(id: String) {
        val r = all().firstOrNull { it.id == id } ?: return
        val now = System.currentTimeMillis()
        val f = firedSet(); offsets().filter { r.start - it * 60_000L <= now }.forEach { f.add("$id|$it") }
        Prefs.put("rem_fired", f.filter { k -> all().any { k.startsWith(it.id + "|") } }.joinToString(","))
        if (offsets().all { "$id|$it" in f }) save(all().map { if (it.id == id) it.copy(fired = true) else it }) else rev++
    }

    /** reminders whose time has come and that were not shown yet */
    fun due(): List<Reminder> {
        val now = System.currentTimeMillis()
        val f = firedSet()
        return all().filter { r -> !r.fired && r.start + 30 * 60_000L > now && offsets().any { o -> r.start - o * 60_000L <= now && "${r.id}|$o" !in f } }
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
            for (i in 0 until a.length()) { val o = a.getJSONObject(i); if (o.str("kind") == "sport") continue; list.add(Note(o.str("id"), o.str("kind"), o.str("text"), o.optLong("time"), o.optBoolean("read"))) }
        } catch (_: Exception) {}
        // a note about an update that is already installed (or older) is gone
        if (list.removeAll { it.kind == "update" && !newer(it.id.removePrefix("update:"), BuildInfo.VERSION) }) save()
    }

    private fun newer(a: String, b: String): Boolean {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }; val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) { val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }; if (d != 0) return d > 0 }
        return false
    }

    private fun save() {
        val a = JSONArray(); list.take(50).forEach { a.put(JSONObject().put("id", it.id).put("kind", it.kind).put("text", it.text).put("time", it.time).put("read", it.read)) }
        Prefs.put("notes", a.toString())
    }

    fun add(kind: String, text: String, id: String = "$kind:${System.currentTimeMillis()}") {
        load()
        if (!Prefs.bool("general_notes", true) || list.any { it.id == id }) return
        if (kind == "update") list.removeAll { it.kind == "update" }   // only the newest version is announced
        list.add(0, Note(id, kind, text, System.currentTimeMillis(), false)); save()
    }

    fun unread(kind: String? = null) = list.count { !it.read && (kind == null || it.kind == kind) }
    fun readAll(kind: String? = null) {
        for (i in list.indices) if (kind == null || list[i].kind == kind) list[i] = list[i].copy(read = true)
        save()
    }
    fun markRead(id: String) { val i = list.indexOfFirst { it.id == id }; if (i >= 0 && !list[i].read) { list[i] = list[i].copy(read = true); save() } }
    /** clearing the bell also clears "Recently added": nothing comes back until the provider really adds something new */
    fun clear() { list.clear(); save(); NewContent.clear() }
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
    fun clear(kind: String? = null) {
        load(); fresh.keys.removeAll { kind == null || it.split('|').getOrNull(1) == kind }; save()
        // cleared once: Home no longer shows the "newest at the provider" rows of that kind either
        (if (kind == null) listOf("vod", "series") else listOf(kind)).forEach { Prefs.put("disc_off_$it", true) }
        HomeCache.rev++
    }

    /** the new items of a playlist, newest first: (id, name, image, rating, url) */
    fun items(pid: String, kind: String): List<JSONObject> { load(); return visible().filter { it.key.startsWith("$pid|$kind|") }
        .sortedByDescending { it.value.optLong("t") }.map { it.value.put("id", it.key.substringAfterLast('|')) } }

    /** compares the playlist's movies and series with the last snapshot (at most once every 12 hours, or [force]) */
    suspend fun scan(ctx: Context, p: Playlist, force: Boolean = false) = withContext(Dispatchers.IO) {
        if (p.type != SourceType.XTREAM) return@withContext
        val stamp = "new_scan_${p.id}"
        if (!force && System.currentTimeMillis() - Prefs.long(stamp) < (if (Lean.scan) 24 else 12) * 3600_000L) return@withContext
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
                if (shown > 0) Notes.list.removeAll { it.kind == kind && !it.id.startsWith("ep:") }
                if (shown > 0) Notes.add(kind, trf(if (kind == "vod") "Novih filmova: %s" else "Novih serija: %s", shown) + " · " + p.name)
            }
            f.writeText(ids.toString())
        }
        Prefs.put(stamp, System.currentTimeMillis())
        if (added > 0) { save(); HomeCache.clear() } else rev++
    }
}
