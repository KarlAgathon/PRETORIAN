package com.pretorian.tv

import android.content.Context
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.GZIPOutputStream

/** 2.8.0: persistent diagnostics, retention, redaction, channel health, QR session. Synthetic values only. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Diagnostics280Test {
    private lateinit var ctx: Context

    @Before fun setUp() {
        ctx = RuntimeEnvironment.getApplication()
        Prefs.init(ctx)
        DiagStore.init(ctx)
        DiagStore.clearAll()
    }

    private fun fakeIncident(kind: String, time: Long, i: Int) =
        File(DiagStore.incidentDir(), "${kind}_${time}_${"%04x".format(i)}.txt").writeText("$kind $i\n  at com.pretorian.tv.X")

    @Test fun incidentsAreCappedAndCrashesKeptFirst() {
        val now = System.currentTimeMillis()
        repeat(10) { fakeIncident("crash", now - 50_000 - it, it) }          // older crashes
        repeat(100) { fakeIncident("freeze", now - it.toLong(), 100 + it) }   // newer freezes
        DiagStore.maintain()
        val left = DiagStore.incidents()
        assertEquals(DiagStore.MAX_INCIDENTS, left.size)
        assertEquals(10, left.count { it.kind == "crash" })
    }

    @Test fun incidentsOlderThan90DaysGo() {
        fakeIncident("crash", System.currentTimeMillis() - 91L * 86_400_000L, 1)
        fakeIncident("crash", System.currentTimeMillis(), 2)
        DiagStore.maintain()
        assertEquals(1, DiagStore.incidents().size)
    }

    @Test fun journalSurvivesAndIsBounded() {
        val pad = "x".repeat(80)
        repeat(13) { b -> DiagStore.writeNow((0 until 1000).map { DiagEvents.stamp() + "|I|test|$b-$it $pad" }) }
        DiagStore.maintain()
        assertTrue(DiagStore.segments().isNotEmpty())                 // rotated into compressed segments
        assertTrue(DiagStore.eventCount() <= DiagStore.MAX_EVENTS + 6000) // whole segments are dropped
        var n = 0; var last = ""
        DiagStore.eachEvent { n++; last = it }
        assertTrue(n in 1..(DiagStore.MAX_EVENTS + 6000))
        assertTrue(last.contains("12-999"))                            // the newest is always kept
    }

    @Test fun brokenSegmentIsRemovedNotCrashing() {
        File(DiagStore.journalDir(), "seg_1_2_5.log.gz").writeText("not gzip")
        DiagStore.maintain()
        assertTrue(DiagStore.segments().none { it.f.name == "seg_1_2_5.log.gz" })
        DiagStore.eachEvent { }   // must not throw
    }

    @Test fun compressedIncidentIsReadable() {
        val f = File(DiagStore.incidentDir(), "crash_1000_0001.txt.gz")
        GZIPOutputStream(f.outputStream()).bufferedWriter().use { it.write("java.lang.IllegalStateException: boom") }
        assertTrue(DiagStore.read(f).contains("boom"))
    }

    @Test fun longIncidentIsCutVisibly() {
        val t = DiagStore.bounded("a".repeat(DiagStore.INCIDENT_MAX_CHARS + 5000))
        assertTrue(t.contains("characters cut here"))
        assertTrue(t.length < DiagStore.INCIDENT_MAX_CHARS + 200)
    }

    @Test fun timestampsRoundTrip() {
        val t = 1_764_000_000_123L
        assertEquals(t, DiagEvents.parseTime(DiagEvents.stamp(t) + "|I|x|y"))
    }

    @Test fun redactsCookiesMacJsonAndEncodedLogins() {
        val r = Redact.text("Cookie: PHPSESSID=abc123 · mac 00:1A:79:12:34:56 · {\"password\":\"Synth3tic!\"} · u%3Dx&password%3DSecretX1")
        assertFalse(r.contains("abc123")); assertFalse(r.contains("00:1A:79")); assertFalse(r.contains("Synth3tic")); assertFalse(r.contains("SecretX1"))
    }

    @Test fun shortCredentialsOnlyWhereTheyAreALogin() {
        val t = SecretScan.clean("http://h:80/live/ab/cd/1.ts user=ab and table abc", listOf("ab", "cd"))
        assertFalse(t.contains("/ab/")); assertTrue(t.contains("table abc"))
    }

    @Test fun healthKeepsStaleAndPlaybackWins() {
        ChannelHealth.init(ctx)
        val u = "http://synthetic.example/live/u/p/1.ts"
        assertNull(ChannelHealth.of(u))
        ChannelHealth.mark(u, true)                 // playback
        ChannelHealth.probeResult(u, false)         // a light check right after must not overwrite it
        assertEquals(true, ChannelHealth.of(u))
        assertFalse(ChannelHealth.key(u).contains("synthetic"))
        ChannelHealth.probeResult("http://synthetic.example/live/u/p/2.ts", false)
        assertEquals(false, ChannelHealth.of("http://synthetic.example/live/u/p/2.ts"))
    }

    @Test fun healthClassification() {
        assertEquals(ChannelHealth.Res.AUTH, ChannelHealth.classify(403))
        assertEquals(ChannelHealth.Res.LIMIT, ChannelHealth.classify(458))
        assertEquals(ChannelHealth.Res.BAD, ChannelHealth.classify(404))
        assertNull(ChannelHealth.classify(200))
        assertTrue(ChannelHealth.looksLikeStream("#EXTM3U\n#EXT-X".toByteArray(), 14))
        val ts = ByteArray(400); ts[0] = 0x47; ts[188] = 0x47
        assertTrue(ChannelHealth.looksLikeStream(ts, 400))
        assertFalse(ChannelHealth.looksLikeStream("<html>".toByteArray(), 6))
    }

    @Test fun qrDownloadOnlyOncePerKeyEvenWithConcurrentRequests() {
        val f = File(ctx.cacheDir, "r.txt").apply { writeText("report ".repeat(20000)) }
        val r = PhoneLink.startDownload("r.txt", f, "text/plain") { }
        assumeTrue(r != null)   // no private LAN address on this machine: nothing to test here
        val (url, id) = r!!
        val base = url.substringBefore("/?k="); val k = url.substringAfter("k=")
        fun get(path: String): Int = try {
            val c = java.net.URL(base + path).openConnection() as java.net.HttpURLConnection
            c.connectTimeout = 3000; c.readTimeout = 5000
            val code = c.responseCode; try { (if (code < 400) c.inputStream else c.errorStream)?.use { it.readBytes() } } catch (_: Exception) {}; code
        } catch (_: Exception) { -1 }
        assertEquals(403, get("/r?k=wrong"))
        val codes = java.util.Collections.synchronizedList(ArrayList<Int>())
        val ts = (1..4).map { Thread { codes.add(get("/r?k=$k")) } }
        ts.forEach { it.start() }; ts.forEach { it.join() }
        assertEquals(1, codes.count { it == 200 })
        PhoneLink.stop(id + 1)                       // an older/other session id closes nothing
        assertEquals(409, get("/r?k=$k"))
        PhoneLink.stop(id)
    }
}
