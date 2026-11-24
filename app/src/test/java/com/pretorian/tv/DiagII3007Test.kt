package com.pretorian.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 3.0.0.7 Diagnostics II: rules that must not drift (classification, honesty of empty data, redaction, export safety). */
class DiagII3007Test {
    @Test fun networkConfigurationGroups() {
        assertEquals("Ethernet without VPN", DiagII.netCfg("ethernet"))
        assertEquals("Ethernet with VPN", DiagII.netCfg("ethernet+vpn"))
        assertEquals("Wi-Fi with VPN", DiagII.netCfg("wifi+vpn"))
        assertEquals("Wi-Fi without VPN", DiagII.netCfg("wifi"))
        assertEquals("unknown", DiagII.netCfg("none"))
        assertEquals("unknown", DiagII.netCfg("vpn"))
    }

    @Test fun requestCategoriesFromTheLinkOnly() {
        assertEquals("EPG", DiagII.category("http://h:80/xmltv.php?username=a&password=b"))
        assertEquals("playlist / provider API", DiagII.category("http://h:80/player_api.php?username=a&password=b&action=get_live_streams"))
        assertEquals("application updates", DiagII.category("https://github.com/KarlAgathon/PRETORIAN/releases/latest/download/update.json"))
        assertEquals("metadata (TMDB/OMDb)", DiagII.category("https://api.themoviedb.org/3/search/movie"))
        assertEquals("subtitles", DiagII.category("https://api.opensubtitles.com/api/v1/download"))
        assertEquals("other", DiagII.category("https://example.org/x"))
    }

    @Test fun noDataIsNeverReportedAsAResult() {
        assertTrue(DiagII.dist(emptyList()).contains("not observed"))
        assertFalse(DiagII.dist(emptyList()).contains("100"))
        assertEquals("1 samples · min 5 · median 5 · p95 5 · max 5 ms", DiagII.dist(listOf(5L)))
        assertEquals(0L, DiagII.pct(emptyList(), .5))
        assertEquals(50L, DiagII.pct(listOf(10L, 50L, 90L), .5))
    }

    @Test fun exitReasonsStayAmbiguousWhenTheyAre() {
        assertEquals("low-memory kill", DiagLife.classify(3, null, 10, 0))
        assertEquals("Java crash", DiagLife.classify(4, null, 10, 0))
        assertEquals("ANR", DiagLife.classify(6, null, 10, 0))
        assertEquals("update-related termination", DiagLife.classify(16, null, 10, 0))
        assertEquals("update-related termination", DiagLife.classify(13, "installPackageLI", 10, 0))
        assertTrue(DiagLife.classify(2, "", 5, 100).startsWith("ambiguous (ended before the last device start"))
        assertTrue(DiagLife.classify(0, "", 500, 100).startsWith("ambiguous"))
        assertFalse(DiagLife.classify(0, "", 500, 100).contains("crash"))
    }

    @Test fun playlistQualityCriteria() {
        val q = DiagII.Quality("TEST", "live")
        q.item("1", "One", "News", "logo", "one.epg", "http://x/1")
        q.item("1", "One again", "News", null, null, "http://x/1")   // duplicate id; missing image and EPG id are not invalid
        q.item("2", "", "News", "logo", "e", "http://x/2")            // no name → invalid
        q.item("3", "Three", null, "logo", "e", "")                    // no link → invalid, no category is only "missing"
        assertEquals(4L, q.items); assertEquals(1L, q.dup); assertEquals(2L, q.invalid)
        assertEquals(1L, q.noName); assertEquals(1L, q.noCat); assertEquals(1L, q.noImg); assertEquals(1L, q.noEpg)
    }

    @Test fun redactionCoversLinksHeadersAndServerAddresses() {
        val t = Redact.text("open http://203.0.113.45:8080/live/alice/s3cret/123.ts · rtmp://198.51.100.7/app · " +
            "get.php?username=bob&password=pw9 · Authorization: Bearer abc.def · Cookie: sid=1 · version 3.0.0.7")
        assertFalse(t.contains("203.0.113.45")); assertFalse(t.contains("198.51.100.7"))
        assertFalse(t.contains("alice")); assertFalse(t.contains("s3cret")); assertFalse(t.contains("pw9")); assertFalse(t.contains("abc.def"))
        assertTrue(t.contains("version 3.0.0.7"))   // a version number is not an address
        assertTrue(t.contains("http://[ip]:8080"))
    }

    @Test fun exportRefusesWhenASecretWouldRemain() {
        val secrets = listOf("VerySecretPass1")
        assertFalse(SecretScan.clean("line with VerySecretPass1 inside", secrets).contains("VerySecretPass1"))
        assertFalse(SecretScan.clean("encoded VerySecretPass1%21", secrets).contains("VerySecretPass1"))
    }
}
