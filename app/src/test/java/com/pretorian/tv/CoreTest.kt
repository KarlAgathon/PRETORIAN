package com.pretorian.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs on every build (GitHub): the parts that every list and every film depend on. A failure stops the build. */
class CoreTest {
    @Test fun titlesAreCleaned() {
        assertEquals("Tuner" to "2025", cleanTitle("EN - Tuner (2025)"))
        assertEquals("The Office", cleanTitle("D+ - The Office").first)
        assertEquals("Dune Part Two", cleanTitle("|EN| Dune Part Two 4K").first)
        assertNull(cleanTitle("Gladiator").second)
    }

    @Test fun m3uChannelsAndGroups() {
        val l = M3u.parse("""
            #EXTM3U
            #EXTINF:-1 tvg-id="hrt1.hr" tvg-logo="http://l/1.png" group-title="HR",HRT 1
            http://s/1.ts
            #EXTINF:-1 group-title="DE",Das Erste
            http://s/2.ts
        """.trimIndent())
        assertEquals(2, l.size)
        assertEquals("HRT 1", l[0].name); assertEquals("HR", l[0].group); assertEquals("hrt1.hr", l[0].epg)
        assertEquals("Das Erste", l[1].name); assertEquals(0, l[1].archive); assertNull(l[1].catchup)
    }

    @Test fun m3uCatchupIsRead() {
        val l = M3u.parse("""
            #EXTM3U
            #EXTINF:-1 catchup="default" catchup-days="7" catchup-source="http://s/arch?utc={utc}&d={duration}" group-title="A",Chan
            http://s/1.ts
            #EXTINF:-1 catchup="shift" group-title="A",Chan2
            http://s/2.ts
        """.trimIndent())
        assertEquals(7, l[0].archive)
        assertEquals("default|http://s/arch?utc={utc}&d={duration}", l[0].catchup)
        assertTrue(l[1].archive >= 1); assertEquals("shift|", l[1].catchup)
    }

    @Test(expected = java.io.IOException::class) fun notAnM3uIsRefused() { M3u.parse("<html>not a list</html>") }
}
