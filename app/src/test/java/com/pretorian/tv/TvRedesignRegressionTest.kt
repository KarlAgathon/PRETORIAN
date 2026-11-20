package com.pretorian.tv

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class TvRedesignRegressionTest {
    private val channel = Channel("42", 1, "News", null, "local", "https://example.test/42", "guide", "one", 7, "template")

    @Before fun setup() { Prefs.init(RuntimeEnvironment.getApplication()) }

    @Test fun playlistExpiryCountsCalendarDaysAcrossSpringClockChange() {
        val zone = java.util.TimeZone.getTimeZone("Europe/Berlin")
        val calendar = java.util.Calendar.getInstance(zone)
        calendar.clear()
        calendar.set(2026, java.util.Calendar.MARCH, 29, 0, 30)
        val start = calendar.timeInMillis
        calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
        assertEquals(23 * 3600000L, calendar.timeInMillis - start)
        assertEquals(1L, playlistDaysRemaining(start, calendar.timeInMillis, zone))
        assertEquals(0L, playlistDaysRemaining(start, start + 3600000L, zone))
    }

    @Test fun favoritesPreserveOriginalGroupAndCatchupMetadata() {
        Favs.toggle("chosen", channel)
        val favorite = Favs.channels("chosen").single()
        assertEquals(channel.group, favorite.group)
        assertEquals(channel.archive, favorite.archive)
        assertEquals(channel.catchup, favorite.catchup)
    }

    @Test fun groupCorrectionIsPlaylistSpecificAndKeepsChannelOffset() {
        EpgShift.setCh(channel.url, -30)
        EpgShift.setGroup("one", "local", 120)
        assertEquals(120, EpgShift.groupMinutes("one", "local"))
        assertEquals(0, EpgShift.groupMinutes("two", "local"))
        assertEquals(-30, EpgShift.chMin(channel.url))
    }

    @Test fun assignedGuideCanBeResetWithoutAffectingOtherChannels() {
        val other = channel.copy(url = "https://example.test/43")
        EpgShift.assign(channel, "provider" to "news")
        EpgShift.assign(other, "provider" to "sports")
        assertEquals("provider" to "news", EpgShift.mapping(channel))
        EpgShift.assign(channel, null)
        assertNull(EpgShift.mapping(channel))
        assertEquals("provider" to "sports", EpgShift.mapping(other))
    }
}
