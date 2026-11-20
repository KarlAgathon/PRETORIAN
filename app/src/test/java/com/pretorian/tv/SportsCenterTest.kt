package com.pretorian.tv

import org.junit.Assert.*
import org.junit.Test

class SportsCenterTest {
    private val now = System.currentTimeMillis()
    private fun team(name: String) = Team(name, name, null, null, null, null)
    private val game = SportEvent("espn", "football", "Cup", "C", null, now - 60_000,
        "in", null, team("Köln"), team("Mainz"), "", null, country = "de", observedAt = now)

    @Test fun diskSnapshotSurvivesReopenAndExpiresAtBoundary() {
        val directory = java.nio.file.Files.createTempDirectory("sports-test").toFile()
        try {
            val file = directory.resolve("league.json")
            assertFalse(SportsFeedCache.fresh(file, now, 60_000L))
            SportsFeedCache.save(file, "first")
            SportsFeedCache.save(file, "second")
            val reopened = directory.resolve("league.json")
            val at = reopened.lastModified()
            assertEquals("second", reopened.readText())
            assertEquals(1, directory.listFiles()!!.size)
            assertTrue(SportsFeedCache.fresh(reopened, at + 59_999L, 60_000L))
            assertFalse(SportsFeedCache.fresh(reopened, at + 60_000L, 60_000L))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun cachedScoreExpiresWithoutClaimingFullTime() {
        val old = game.copy(observedAt = now - 180_000)
        assertTrue(game.live)
        assertTrue(old.stale)
        assertFalse(old.live)
        assertFalse(old.over)
    }

    @Test fun timezoneOffsetsPreserveKickoffAcrossClockChange() {
        assertEquals(Sports.iso("2026-10-25T00:30Z"), Sports.iso("2026-10-25T02:30+02:00"))
        assertNotEquals(Sports.iso("2026-10-25T02:30+01:00"), Sports.iso("2026-10-25T02:30+02:00"))
    }

    @Test fun deduplicateByTeamsSportAndKickoff() {
        val duplicate = game.copy(id = "af", home = team("Koln"))
        val later = duplicate.copy(id = "another", start = now + 86_400_000)
        assertEquals(listOf(later), ApiFootball.extra(listOf(game), listOf(duplicate, later, later)))
    }

    @Test fun restoreFocusAccountsForLeagueHeadingsAndBanner() {
        val rows = listOf(game, game.copy(id = "second"), game.copy(id = "third", league = "League"))
        assertEquals(1, sportsRowIndex(rows, "espn", false) { it.league })
        assertEquals(5, sportsRowIndex(rows, "third", true) { it.league })
        assertNull(sportsRowIndex(rows, "removed", true) { it.league })
    }

    @Test fun selectedFiltersIntersect() {
        assertTrue(sportsFilter(game, setOf("Mainz", "Bayern"), setOf("de"), setOf("Cup")))
        assertFalse(sportsFilter(game, setOf("Mainz"), setOf("ba"), setOf("Cup")))
        assertTrue(sportsFilter(game, emptySet(), emptySet(), emptySet()))
    }

    @Test fun channelTitleNeedsDistinctEvidenceForBothTeams() {
        val home = setOf("manchester", "city")
        val away = setOf("manchester", "united")
        assertFalse(Sports.matchesTeams(home, away, setOf("manchester")))
        assertTrue(Sports.matchesTeams(home, away, setOf("manchester", "city", "united")))
    }
}
