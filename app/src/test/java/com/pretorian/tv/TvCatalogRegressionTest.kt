package com.pretorian.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression checks for provider ordering and channel identity.
 * These run in the existing GitHub release unit-test job.
 * They do not replace device tests for remote focus or playback.
 */
class TvCatalogRegressionTest {
    @Test fun providerOrderAndQualityVariantsAreNotSilentlyReordered() {
        val channels = M3u.parse("""
            #EXTM3U
            #EXTINF:-1 group-title="RS",RS | Arena Sport Premium 1 HD
            http://example.test/rs1hd
            #EXTINF:-1 group-title="RS",RS | Arena Sport Premium 2 FHD
            http://example.test/rs2fhd
            #EXTINF:-1 group-title="RS",RS | Arena Sport Premium 1 4K
            http://example.test/rs14k
            #EXTINF:-1 group-title="BA",BA | Arena Sport Premium 1 FHD
            http://example.test/ba1fhd
        """.trimIndent())
        assertEquals(
            listOf(
                "RS | Arena Sport Premium 1 HD",
                "RS | Arena Sport Premium 2 FHD",
                "RS | Arena Sport Premium 1 4K",
                "BA | Arena Sport Premium 1 FHD"
            ),
            channels.map { it.name }
        )
        assertEquals(listOf("RS", "RS", "RS", "BA"), channels.map { it.group })
        assertEquals(4, channels.map { it.url }.distinct().size)
    }

    @Test fun separateProviderGroupsStaySeparate() {
        val channels = M3u.parse("""
            #EXTM3U
            #EXTINF:-1 group-title="Bosna",Arena Sport 1
            http://example.test/ba
            #EXTINF:-1 group-title="Srbija",Arena Sport 1
            http://example.test/rs
        """.trimIndent())
        assertEquals(2, channels.size)
        assertEquals(listOf("Bosna", "Srbija"), channels.map { it.group })
        assertFalse(channels[0].url == channels[1].url)
    }

    @Test fun sportsMatchNeedsDistinctTeamEvidence() {
        assertFalse(Sports.matchesTeams("Manchester City", "Manchester United", setOf("manchester")))
        assertTrue(Sports.matchesTeams("Manchester City", "Manchester United", setOf("manchester", "city", "united")))
    }
}
