package com.pretorian.tv

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * Phase A – Settings page stack ("one menu at a time").
 * Only the top entry is on screen. Back removes exactly one entry; every entry remembers its focused row (a stable key,
 * never a list index alone) and its scroll position, so returning lands on the same row at the same place.
 *
 * Settings can be on the app's screen stack more than once (Settings › Traži postavku › result opens Settings again),
 * so each copy has its own session, numbered by how many Settings screens are on the app stack.
 */
class SetEntry(val id: String) {
    /** stable key of the focused row on this page */
    var focus: String? = null
    var scrollIndex: Int = 0
    var scrollOffset: Int = 0
}

class SetSession {
    val stack: SnapshotStateList<SetEntry> = mutableStateListOf(SetEntry(SettingsTree.ROOT))
    /** Back below this index leaves the Settings screen (a page opened from search returns to the search results) */
    var floor = 0
    val top: SetEntry get() = stack.last()

    fun push(id: String) { if (stack.last().id != id) stack.add(SetEntry(id)) }

    /** true when a level was removed; false when Back should leave the Settings screen */
    fun pop(): Boolean {
        if (stack.size - 1 <= floor) return false
        stack.removeAt(stack.lastIndex); return true
    }

    /** the root plus the full path to [id]; [fromSearch]: Back from that page goes back to the search results */
    fun openPath(id: String, fromSearch: Boolean) {
        val path = SettingsTree.pathTo(id)
        stack.clear(); stack.add(SetEntry(SettingsTree.ROOT)); path.forEach { stack.add(SetEntry(it)) }
        // the rows on the way remember which child was opened, so Back lands on it
        for (i in 0 until stack.size - 1) stack[i].focus = SettingsTree.rowKey(stack[i + 1].id)
        floor = if (fromSearch && path.isNotEmpty()) stack.lastIndex else 0
    }
}

object SettingsStack {
    private val sessions = ArrayList<SetSession>()
    /** how many Settings screens are on the app stack right now (set by the app before drawing Settings) */
    var depth = 1
    private var pendingSearch = BooleanArray(8)

    /** a Settings screen was just put on the app stack at [depth]: it starts fresh */
    fun opened(depth: Int, fromSearch: Boolean) {
        if (depth < 1) return
        while (sessions.size >= depth) sessions.removeAt(sessions.lastIndex)
        if (depth - 1 < pendingSearch.size) pendingSearch[depth - 1] = fromSearch
    }

    fun current(): SetSession {
        val d = depth.coerceAtLeast(1)
        while (sessions.size > d) sessions.removeAt(sessions.lastIndex)
        while (sessions.size < d) sessions.add(SetSession())
        return sessions[d - 1]
    }

    fun openedFromSearch(): Boolean = (depth - 1).let { it in pendingSearch.indices && pendingSearch[it] }

    /** tests */
    fun resetForTest() { sessions.clear(); depth = 1; pendingSearch = BooleanArray(8) }
}

/** Where every Settings page lives: sections, their pages and sub-pages. Pure data (tested without Android). */
object SettingsTree {
    const val ROOT = "root"
    const val SEARCH_ROW = "row:search"

    /** the eight categories, in the approved order */
    val SECTIONS = listOf("sources", "playback", "appearance", "home", "privacy", "sportscenter", "system", "notify")

    /** pages of each section (a section with one page opens that page directly) */
    val LINKS: Map<String, List<String>> = mapOf(
        "sources" to listOf("playlists", "epg", "groups"),
        "playback" to listOf("player"),
        "appearance" to listOf("language", "look", "saver"),
        "home" to listOf("general"),
        "privacy" to listOf("pin", "parental"),
        "sportscenter" to listOf("sports"),
        "system" to listOf("update", "backup", "diagnostics", "storage", "about"),
        "notify" to listOf("notifications", "reminders"),
    )

    /** sub-pages that open from inside a page */
    val SUBS: Map<String, String> = mapOf("device" to "about", "playermore" to "player")

    fun rowKey(id: String) = "row:$id"

    fun sectionOf(id: String): String? = when {
        id in SECTIONS -> id
        else -> LINKS.entries.firstOrNull { id in it.value }?.key ?: SUBS[id]?.let { sectionOf(it) }
    }

    /** the entries under the root that lead to [id] (empty for an unknown id or the root) */
    fun pathTo(id: String): List<String> {
        if (id == ROOT) return emptyList()
        SUBS[id]?.let { parent -> return pathTo(parent) + id }
        if (id in SECTIONS) return listOf(LINKS[id]?.singleOrNull() ?: id)
        val sec = LINKS.entries.firstOrNull { id in it.value }?.key ?: return emptyList()
        return if (LINKS[sec]!!.size == 1) listOf(id) else listOf(sec, id)
    }

    /** every page id that Settings can show (search results may only point here) */
    fun allPages(): Set<String> = (SECTIONS + LINKS.values.flatten() + SUBS.keys).toSet()
}
