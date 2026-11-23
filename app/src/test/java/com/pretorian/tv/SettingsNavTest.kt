package com.pretorian.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Phase A: one menu at a time – Back is exactly one level, paths and deep links land on real pages. */
class SettingsNavTest {
    @Before fun reset() = SettingsStack.resetForTest()

    @Test fun eightCategoriesInApprovedOrder() {
        assertEquals(listOf("sources", "playback", "appearance", "home", "privacy", "sportscenter", "system", "notify"), SettingsTree.SECTIONS)
    }

    @Test fun everySectionResolvesToAPage() {
        SettingsTree.SECTIONS.forEach { s ->
            val p = SettingsTree.pathTo(s)
            assertEquals(s, 1, p.size)
            assertTrue(s, p.last() in SettingsTree.allPages())
        }
    }

    @Test fun pathsForPagesAndSubPages() {
        assertEquals(listOf("player"), SettingsTree.pathTo("playback"))          // one page: opens directly
        assertEquals(listOf("system", "update"), SettingsTree.pathTo("update"))
        assertEquals(listOf("player", "playermore"), SettingsTree.pathTo("playermore"))
        assertEquals(listOf("system", "about", "device"), SettingsTree.pathTo("device"))
        assertEquals(emptyList<String>(), SettingsTree.pathTo("nope"))
    }

    @Test fun pushAndPopAreExactlyOneLevel() {
        val s = SetSession()
        s.push("system"); s.push("update")
        assertEquals(3, s.stack.size)
        assertTrue(s.pop()); assertEquals("system", s.top.id)
        assertTrue(s.pop()); assertEquals(SettingsTree.ROOT, s.top.id)
        assertFalse(s.pop())   // Back on the main list leaves Settings
    }

    @Test fun updateDeepLinkGoesBackThroughSystem() {
        val s = SetSession(); s.openPath("update", fromSearch = false)
        assertEquals(listOf(SettingsTree.ROOT, "system", "update"), s.stack.map { it.id })
        assertEquals(SettingsTree.rowKey("system"), s.stack[0].focus)
        assertEquals(SettingsTree.rowKey("update"), s.stack[1].focus)
        assertTrue(s.pop()); assertTrue(s.pop()); assertFalse(s.pop())
    }

    @Test fun searchResultBackReturnsToSearch() {
        val s = SetSession(); s.openPath("diagnostics", fromSearch = true)
        assertEquals("diagnostics", s.top.id)
        assertFalse(s.pop())   // Back leaves this Settings copy: the search results are below it
    }

    @Test fun focusAndScrollAreKeptPerEntry() {
        val s = SetSession(); s.top.focus = "row:system"; s.top.scrollIndex = 3
        s.push("system"); s.pop()
        assertEquals("row:system", s.top.focus); assertEquals(3, s.top.scrollIndex)
    }

    @Test fun eachSettingsCopyHasItsOwnSession() {
        SettingsStack.opened(1, false); SettingsStack.depth = 1
        val a = SettingsStack.current(); a.push("system")
        SettingsStack.opened(2, true); SettingsStack.depth = 2
        val b = SettingsStack.current()
        assertTrue(SettingsStack.openedFromSearch())
        assertEquals(1, b.stack.size)
        SettingsStack.depth = 1
        assertEquals("system", SettingsStack.current().top.id)   // the first copy kept its place
        SettingsStack.opened(1, false)
        assertEquals(1, SettingsStack.current().stack.size)      // a newly opened Settings starts fresh
    }

    @Test fun bringIntoViewKeepsAMargin() {
        val spec = SetBringSpec(30f)
        assertEquals(0f, spec.calculateScrollDistance(100f, 60f, 500f), 0.01f)
        assertEquals(40f, spec.calculateScrollDistance(450f, 60f, 500f), 0.01f)   // bottom edge: row + margin visible
        assertEquals(-30f, spec.calculateScrollDistance(0f, 60f, 500f), 0.01f)    // top edge
    }
}
