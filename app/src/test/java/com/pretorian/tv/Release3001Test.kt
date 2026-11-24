package com.pretorian.tv

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 3.0.0.1: device identity, device name safety, report file name, Serbian Latin, full report privacy. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Release3001Test {
    private val ctx get() = RuntimeEnvironment.getApplication()
    @Before fun setUp() { Prefs.init(ctx); DeviceIdentity.init(ctx) }

    @Test fun idIsRandomUuidAndStable() {
        val a = DeviceIdentity.id()
        assertTrue(DeviceIdentity.isUuid(a))
        assertEquals(a, DeviceIdentity.id())
        assertTrue(java.io.File(ctx.noBackupFilesDir, "device_id").readText().trim() == a)
        assertTrue(Regex("^PTR-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}$").matches(DeviceIdentity.short()))
    }
    @Test fun idDoesNotChangeWithName() {
        val a = DeviceIdentity.id()
        assertTrue(DeviceIdentity.setName("Dnevni Boravak"))
        assertTrue(DeviceIdentity.setName("Edin's TV & Media"))
        assertEquals(a, DeviceIdentity.id())
        assertEquals("Edin's TV & Media", DeviceIdentity.name())
    }
    @Test fun unicodeAndEmojiNamesAreKept() {
        for (n in listOf("Google TV — Wohnzimmer", "PRETORIAN ★ 4K", "Schlafzimmer", "Дневна соба", "غرفة المعيشة", "TV 📺 👨‍👩‍👧", "TV #1")) {
            assertEquals(n, DeviceIdentity.clean(n))
        }
    }
    @Test fun controlCharactersAndBrokenTextAreRemoved() {
        assertEquals("Living Room", DeviceIdentity.clean("Living\u0000 Room\u0007"))
        assertEquals("a b", DeviceIdentity.clean("a\nb"))
        assertEquals("ab", DeviceIdentity.clean("a\uD800b"))   // lone surrogate
        assertEquals("", DeviceIdentity.clean("   \t  "))
        assertFalse(DeviceIdentity.setName("   "))
    }
    @Test fun longNamesAreBounded() {
        val c = DeviceIdentity.clean("😀".repeat(1000))
        assertTrue(c.codePointCount(0, c.length) <= DeviceIdentity.MAX_CP)
        assertTrue(c.toByteArray(Charsets.UTF_8).size <= DeviceIdentity.MAX_BYTES)
        assertEquals(0, c.length % 2)   // never half an emoji
        val d = DeviceIdentity.display("😀".repeat(100), 10)
        assertTrue(d.endsWith("…")); assertFalse(Character.isHighSurrogate(d[d.length - 2]))
    }
    @Test fun fileNameIsSafe() {
        assertEquals("Living-Room", DeviceIdentity.fileSafe("Living Room"))
        assertEquals("device", DeviceIdentity.fileSafe("📺📺"))
        assertEquals("etc-passwd", DeviceIdentity.fileSafe("../../etc/passwd"))
        assertEquals("Dnevna-soba-cevapi", DeviceIdentity.fileSafe("Dnevna soba ćevapi"))
        DeviceIdentity.setName("../Living Room ★")
        val f = DiagReport.fileName()
        assertTrue(f, Regex("^PTR-[0-9A-F-]{14}_[A-Za-z0-9-]+_DIAGNOSTICS_\\d{4}-\\d{2}-\\d{2}_\\d{6}\\.txt$").matches(f))
    }
    @Test fun serbianLatin() {
        assertEquals("Ljubav, Njegoš, Džep, đak, ćup", Latin.of("Љубав, Његош, Џеп, ђак, ћуп"))
        assertEquals("LJUBAV", Latin.of("ЉУБАВ"))
        Look.lang = "sr"; Look.latin = true
        assertTrue(tr("Postavke").none { it in 'а'..'я' })
        Look.lang = "en"; Look.latin = false
    }
    @Test fun reportHasIdentityButNoSecrets() {
        DiagStore.init(ctx)
        DeviceIdentity.setName("Living Room")
        DiagEvents.add("net", "url http://srv.example:8080/live/myuser/mypass123/1.ts")
        val f = java.io.File(ctx.cacheDir, "r.txt")
        DiagReport.build(ctx, DiagReport.Kind.FULL, f)
        val t = f.readText()
        assertTrue(t.contains("Living Room")); assertTrue(t.contains(DeviceIdentity.short()))
        assertFalse(t.contains("mypass123"))
        f.delete()
    }
}
