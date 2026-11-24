package com.pretorian.tv

import org.junit.Assert.assertEquals
import org.junit.Test

/** 3.0.0.3: going back into TV groups returns to the row that had the focus; the channel check is off by default. */
class Release303Test {
    private fun g(pid: String, id: String) = GRow.Group(GroupRef(pid, Category(id, id)))

    @Test fun backReturnsToTheRememberedRow() {
        val rows = listOf(GRow.Find(), g("p", "a"), g("p", "german"), g("p", "z"))
        val remembered = rows[2].id
        assertEquals(remembered, groupFocusTarget(rows, GroupRef("p", Category("z", "z")), remembered))
    }

    @Test fun backFallsBackToTheSelectedGroup() {
        val rows = listOf(GRow.Find(), g("p", "a"), g("p", "german"))
        assertEquals(rows[2].id, groupFocusTarget(rows, GroupRef("p", Category("german", "german"))))
    }

    @Test fun backNeverJumpsToTheFirstGroupWhileTheSelectedOneExists() {
        val rows = listOf(GRow.Find(), g("p", "a"), g("p", "b"), g("p", "german"))
        assertEquals(rows[3].id, groupFocusTarget(rows, GroupRef("p", Category("german", "german")), "missing-row"))
    }
}
