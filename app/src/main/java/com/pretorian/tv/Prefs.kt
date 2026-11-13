package com.pretorian.tv

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

/** Plain settings: API keys, languages, simple mode, PIN. */
object Prefs {
    private lateinit var sp: SharedPreferences
    fun init(c: Context) { sp = c.getSharedPreferences("pretorian", Context.MODE_PRIVATE) }

    fun str(k: String, d: String = ""): String = sp.getString(k, d) ?: d
    fun put(k: String, v: String) = sp.edit().putString(k, v).apply()
    fun bool(k: String, d: Boolean = false) = sp.getBoolean(k, d)
    fun put(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()
    fun long(k: String, d: Long = 0) = sp.getLong(k, d)
    fun put(k: String, v: Long) = sp.edit().putLong(k, v).apply()

    var tmdbKey: String get() = str("tmdb_key"); set(v) = put("tmdb_key", v.trim())
    var omdbKey: String get() = str("omdb_key"); set(v) = put("omdb_key", v.trim())
    var osKey: String get() = str("os_key"); set(v) = put("os_key", v.trim())
    var osUser: String get() = str("os_user"); set(v) = put("os_user", v.trim())
    var osPass: String get() = str("os_pass"); set(v) = put("os_pass", v)
    var subdlKey: String get() = str("subdl_key"); set(v) = put("subdl_key", v.trim())
    /** preferred subtitle languages, first = most wanted (ISO 639-1) */
    var subLangs: String get() = str("sub_langs", "bs,hr,sr,en"); set(v) = put("sub_langs", v)
    var audioLangs: String get() = str("audio_langs", "en,bs,hr,sr,de"); set(v) = put("audio_langs", v)
    var metaLang: String get() = str("meta_lang", "bs"); set(v) = put("meta_lang", v)
    var autoSubs: Boolean get() = bool("auto_subs", true); set(v) = put("auto_subs", v)
}

/** Simple mode + PIN. The PIN is stored only as a salted SHA-256 hash. */
object Lock {
    var simple by mutableStateOf(false)
    /** groups/channels unlocked with the PIN in this session */
    val unlocked = HashSet<String>()
    private var fails = 0
    private var blockedUntil = 0L

    fun load() { simple = Prefs.bool("simple_mode") }

    fun switchSimple(on: Boolean) { simple = on; Prefs.put("simple_mode", on) }

    fun hasPin() = Prefs.str("pin_hash").isNotEmpty()

    private fun hash(pin: String, salt: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray()).joinToString("") { "%02x".format(it) }

    fun setPin(pin: String) {
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val salt = bytes.joinToString("") { "%02x".format(it) }
        Prefs.put("pin_salt", salt); Prefs.put("pin_hash", hash(pin, salt))
        fails = 0
    }

    /** null = correct, else the error to show */
    fun check(pin: String): String? {
        val now = System.currentTimeMillis()
        if (now < blockedUntil) return "Previše pokušaja. Sačekaj ${((blockedUntil - now) / 1000) + 1} s."
        if (hash(pin, Prefs.str("pin_salt")) == Prefs.str("pin_hash")) { fails = 0; return null }
        fails++
        if (fails >= 5) { fails = 0; blockedUntil = now + 60_000; return "Pogrešan PIN. Unos blokiran 1 minut." }
        return "Pogrešan PIN (${5 - fails} pokušaja preostalo)"
    }
}

/**
 * Your own changes to a playlist: order, names, hidden and locked groups/channels, own groups,
 * channels moved to another group. Kept per playlist; they survive playlist updates.
 * [kind] is "live", "vod" or "series".
 */
class Ovr(private val ctx: Context, private val pid: String) {
    private val key = "ovr_$pid"
    private val o: JSONObject = try { JSONObject(Prefs.str(key, "{}")) } catch (e: Exception) { JSONObject() }

    private fun save() = Prefs.put(key, o.toString())
    private fun arr(k: String): JSONArray = o.optJSONArray(k) ?: JSONArray().also { o.put(k, it) }
    private fun map(k: String): JSONObject = o.optJSONObject(k) ?: JSONObject().also { o.put(k, it) }
    private fun list(k: String): MutableList<String> { val a = arr(k); return (0 until a.length()).map { a.getString(it) }.toMutableList() }
    private fun setList(k: String, l: List<String>) { o.put(k, JSONArray(l)); save() }
    private fun toggle(k: String, v: String) { val l = list(k); if (!l.remove(v)) l.add(v); setList(k, l) }

    // ---------------------------------------------------------------- groups
    fun customGroups(kind: String): List<Category> {
        if (kind != "live") return emptyList()
        val a = arr("custom")
        return (0 until a.length()).map { val g = a.getJSONObject(it); Category(g.str("id"), g.str("name")) }
    }

    fun addGroup(name: String): Category {
        val g = Category("my_${System.currentTimeMillis()}", name)
        arr("custom").put(JSONObject().put("id", g.id).put("name", g.name)); save()
        return g
    }

    fun deleteGroup(id: String) {
        val a = arr("custom"); val keep = JSONArray()
        for (i in 0 until a.length()) if (a.getJSONObject(i).str("id") != id) keep.put(a.getJSONObject(i))
        o.put("custom", keep)
        val moved = map("chMoved"); val rm = moved.keys().asSequence().filter { moved.str(it) == id }.toList()
        rm.forEach { moved.remove(it) }
        save()
    }

    fun groupName(kind: String, g: Category) = map("gName_$kind").str(g.id).ifBlank { g.name }
    fun renameGroup(kind: String, id: String, name: String) { map("gName_$kind").put(id, name); save() }
    fun isHidden(kind: String, id: String) = id in list("gHidden_$kind")
    fun toggleHidden(kind: String, id: String) = toggle("gHidden_$kind", id)
    fun isLocked(kind: String, id: String) = id in list("gLocked_$kind")
    fun toggleLocked(kind: String, id: String) = toggle("gLocked_$kind", id)

    /** custom groups + provider groups, renamed and in your order; hidden ones only when [all] */
    fun groups(kind: String, raw: List<Category>, all: Boolean = false): List<Category> {
        val merged = customGroups(kind) + raw
        val order = list("gOrder_$kind")
        val sorted = merged.withIndex().sortedBy { (i, g) -> order.indexOf(g.id).let { if (it < 0) 100_000 + i else it } }.map { it.value }
        return sorted.filter { all || !isHidden(kind, it.id) }.map { Category(it.id, groupName(kind, it)) }
    }

    fun moveGroup(kind: String, current: List<Category>, id: String, delta: Int) {
        val ids = current.map { it.id }.toMutableList()
        val i = ids.indexOf(id); val j = i + delta
        if (i < 0 || j !in ids.indices) return
        ids.removeAt(i); ids.add(j, id)
        setList("gOrder_$kind", ids)
    }

    // ---------------------------------------------------------------- channels (live)
    fun chName(ch: Channel) = map("chName").str(ch.url).ifBlank { ch.name }
    fun renameChannel(ch: Channel, name: String) { map("chName").put(ch.url, name); save() }
    fun isChHidden(ch: Channel) = ch.url in list("chHidden")
    fun toggleChHidden(ch: Channel) = toggle("chHidden", ch.url)
    fun isChLocked(ch: Channel) = ch.url in list("chLocked")
    fun toggleChLocked(ch: Channel) = toggle("chLocked", ch.url)

    fun moveChannelTo(ch: Channel, groupId: String) {
        map("chMoved").put(ch.url, groupId)
        map("chData").put(ch.url, JSONObject().put("id", ch.id).put("name", ch.name).put("logo", ch.logo ?: "").put("epg", ch.epg ?: ""))
        save()
    }

    /** provider channels of a group with your changes applied */
    fun channels(groupId: String, raw: List<Channel>, all: Boolean = false): List<Channel> {
        val moved = map("chMoved"); val data = map("chData")
        val stay = raw.filter { val to = moved.str(it.url); to.isEmpty() || to == groupId }
        val incoming = moved.keys().asSequence().filter { moved.str(it) == groupId && stay.none { c -> c.url == it } }.map { url ->
            val d = data.optJSONObject(url) ?: JSONObject()
            Channel(d.str("id"), 0, d.str("name"), d.str("logo").orNullIfBlank(), groupId, url, d.str("epg").orNullIfBlank())
        }.toList()
        val merged = stay + incoming
        val order = list("chOrder_$groupId")
        var out = merged.withIndex().sortedBy { (i, c) -> order.indexOf(c.url).let { if (it < 0) 100_000 + i else it } }.map { it.value }
        if (Look.mostWatched && order.isEmpty()) {
            val counts = map("watch")
            out = out.withIndex().sortedWith(compareBy({ -counts.optInt(it.value.url, 0) }, { it.index })).map { it.value }
        }
        return out.filter { all || !isChHidden(it) }.mapIndexed { i, c -> c.copy(name = chName(c), num = if (c.num > 0) c.num else i + 1) }
    }

    fun moveChannel(groupId: String, current: List<Channel>, ch: Channel, delta: Int) {
        val urls = current.map { it.url }.toMutableList()
        val i = urls.indexOf(ch.url); val j = i + delta
        if (i < 0 || j !in urls.indices) return
        urls.removeAt(i); urls.add(j, ch.url)
        setList("chOrder_$groupId", urls)
    }

    fun countWatch(ch: Channel) { val w = map("watch"); w.put(ch.url, w.optInt(ch.url, 0) + 1); save() }

    fun resetAll() { Prefs.put(key, "{}") }
}
