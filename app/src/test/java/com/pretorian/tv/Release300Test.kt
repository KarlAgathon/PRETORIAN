package com.pretorian.tv

import android.graphics.Bitmap
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 3.0: strict logo matching, transparent-margin trim, incident signatures, full report ZIP. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Release300Test {
    @Test fun qualityAndPrefixIgnoredOnlyForMatching() {
        assertEquals(LogoResolver.norm("HR: RTL HD"), LogoResolver.norm("RTL"))
        assertEquals(LogoResolver.norm("Nova TV FHD"), LogoResolver.norm("nova tv"))
        assertNotEquals(LogoResolver.norm("RTL"), LogoResolver.norm("RTL 2"))
        assertNotEquals(LogoResolver.norm("Nova TV"), LogoResolver.norm("Nova BH"))
        assertNotEquals(LogoResolver.norm("ZDF"), LogoResolver.norm("ZDF +1"))
    }
    @Test fun countryFromNameOrGroup() {
        assertEquals("HR", LogoResolver.country("HR: RTL", null))
        assertEquals("BA", LogoResolver.country("Nova BH", "BOSNA I HERCEGOVINA"))
        assertEquals("GB", LogoResolver.country("UK: BBC One", null))
        assertNull(LogoResolver.country("RTL", "Sport"))
    }
    @Test fun ambiguousMeansNoLogo() {
        val c = listOf("DE" to "https://a/rtl.png", "HR" to "https://a/rtlhr.png")
        assertNull("same name in two countries, no country known", LogoResolver.pick(c, null))
        assertEquals("https://a/rtlhr.png", LogoResolver.pick(c, "HR"))
        assertNull("known country without an entry: no foreign logo", LogoResolver.pick(c, "RS"))
        assertEquals("https://a/x.png", LogoResolver.pick(listOf("DE" to "https://a/x.png"), null))
    }
    @Test fun onlySafeImageFormats() {
        assertTrue(LogoResolver.usable("https://x/y.png", "PNG"))
        assertFalse(LogoResolver.usable("http://x/y.png", "PNG"))
        assertFalse(LogoResolver.usable("https://x/y.svg", "SVG"))
    }
    @Test fun trimRemovesOnlyTransparentMargins() {
        val b = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888)
        for (x in 30 until 70) for (y in 10 until 40) b.setPixel(x, y, 0xFFFFFFFF.toInt())
        val t = LogoTrim.trim(b)
        assertTrue(t.width in 40..44 && t.height in 30..34)
        val opaque = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF102030.toInt()) }
        assertSame(opaque, LogoTrim.trim(opaque))
    }
    @Test fun sameCrashHasSameSignature() {
        val a = "java.lang.IllegalStateException: x\n  at com.pretorian.tv.Foo.bar(Foo.kt:1)\n time 1"
        val b = "java.lang.IllegalStateException: x\n  at com.pretorian.tv.Foo.bar(Foo.kt:1)\n time 2"
        assertEquals(DiagStore.signature("crash", a), DiagStore.signature("crash", b))
        assertNotEquals(DiagStore.signature("crash", a), DiagStore.signature("freeze", a))
    }
    @Test fun fullReportIsOneTxtWithAllSections() {
        val ctx = RuntimeEnvironment.getApplication()
        Prefs.init(ctx); DiagStore.init(ctx); DeviceIdentity.init(ctx)
        DiagEvents.add("net", "test event")
        val f = java.io.File(ctx.cacheDir, "t.txt")
        DiagReport.build(ctx, DiagReport.Kind.FULL, f)
        val t = f.readText(Charsets.UTF_8)
        for (n in 1..31) assertTrue("section $n", t.contains("\n$n. "))
        assertTrue(t.contains(DeviceIdentity.id()))
        assertTrue(t.contains("END OF REPORT"))
        f.delete()
    }
}
