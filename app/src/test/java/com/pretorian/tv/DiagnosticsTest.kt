package com.pretorian.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase A0: nothing secret may leave the box in a diagnostic report. */
class DiagnosticsTest {
    @Test fun redactsXtreamLinksWithAndWithoutPrefix() {
        val t = Redact.text("err http://host.tv:8080/live/alice/s3cret/123.ts and http://host.tv:8080/alice/s3cret/456.m3u8 end")
        assertFalse(t.contains("alice")); assertFalse(t.contains("s3cret"))
        assertTrue(t.contains("456.m3u8"))
    }

    @Test fun redactsQueryBearerStalkerAndGoogleKeys() {
        val t = Redact.text("get.php?username=bob&password=pw9&type=m3u Authorization: Bearer abc.def.ghi sn=123456 device_id=DEV1 " +
            "key=zzz AIzaSyA1234567890abcdefghijklmnopqrstuv mac=00:1A:79:12:34:56")
        listOf("bob", "pw9", "abc.def.ghi", "123456", "DEV1", "zzz", "AIzaSyA1234567890", "00:1A:79:12:34:56").forEach { assertFalse(it, t.contains(it)) }
    }

    @Test fun finalCheckReplacesRealValuesEvenWithoutAPattern() {
        val out = SecretScan.clean("player said: login for MyLongPass failed (user johnny)", listOf("MyLongPass", "johnny"))
        assertFalse(out.contains("MyLongPass")); assertFalse(out.contains("johnny"))
    }

    @Test fun finalCheckReplacesUrlEncodedValues() {
        val out = SecretScan.clean("url=http://x/a?q=p%40ss%2Bword", listOf("p@ss+word"))
        assertFalse(out.contains("p%40ss%2Bword"))
    }

    @Test fun eventsAreCappedAndRedacted() {
        DiagEvents.clearForTest()
        repeat(DiagEvents.MAX + 50) { DiagEvents.add("player", "error $it http://h/live/u1/p1/9.ts") }
        val l = DiagEvents.lines()
        assertEquals(DiagEvents.MAX, l.size)
        assertTrue(l.last().contains("error ${DiagEvents.MAX + 49}"))
        assertFalse(l.any { it.contains("u1/p1") })
        DiagEvents.clearForTest()
    }

    @Test fun lowStorageGuardAllowsNormalDirs() {
        assertTrue(DiagSpace.ok(null))
    }
}
