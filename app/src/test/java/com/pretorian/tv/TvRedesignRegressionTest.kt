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

    @Test fun recentHistoryRetainsArchiveAndPromotesRewatchedChannels() {
        Recent.clear()
        for (i in 1..11) Recent.add(channel.copy(url = "stream/$i"), "one", "local", "News")
        Recent.add(channel.copy(url = "stream/3"), "one", "local", "News")
        assertEquals(listOf(3, 11, 10, 9, 8, 7, 6, 5, 4, 2).map { "stream/$it" }, Recent.list().map { it.ch.url })
        Recent.remove("stream/10")
        assertTrue(Recent.list().all { it.ch.catchup == channel.catchup })
        Recent.clear()
        assertTrue(Recent.list().isEmpty())
    }

    @Test fun freshTvEntryStartsAtRecentlyWatchedAndPlaybackReturnKeepsItsGroup() {
        val previous = TvMemory.group
        val restoring = TvMemory.restore
        try {
            val group = GroupRef("one", Category("local", "News"))
            TvMemory.group = group
            TvMemory.restore = false
            assertNull(TvState().group)
            TvMemory.restore = true
            assertEquals(group, TvState().group)
        } finally {
            TvMemory.group = previous
            TvMemory.restore = restoring
        }
    }

    @Test fun remoteOkTapAndHoldNeverDispatchBothActions() {
        val hold = RemoteOkHold()
        var favorites = 0
        var plays = 0
        fun key(down: Boolean, repeat: Int, ms: Long) {
            val consumed = hold.handle(down, repeat, ms, 650L) { favorites++ }
            if (!down && !consumed) plays++
        }
        key(true, 0, 0); key(false, 0, 649)
        key(true, 0, 0); key(true, 1, 500); key(true, 2, 650); key(true, 3, 800); key(false, 0, 900)
        key(true, 0, 0); key(false, 0, 650)
        key(true, 0, 0); key(false, 0, 100)
        assertEquals(2, plays)
        assertEquals(2, favorites)
    }

    @Test fun multiplePlaylistsShowOnlyOneNavigationLevel() {
        val context = RuntimeEnvironment.getApplication() as android.content.Context
        val lists = listOf("one", "two").map { Playlist(it, it, SourceType.M3U, active = true) }
        Store.savePlaylists(context, lists)
        val rows = kotlinx.coroutines.runBlocking { groupRows(context) }
        assertEquals(listOf(RECENT_GROUP, "__all_favorites__"), rows.filterIsInstance<GRow.Group>().map { it.ref.cat.id })
        assertEquals(lists.map { it.id }, rows.filterIsInstance<GRow.Header>().map { it.p.id })
    }

    @Test fun sportsEventsStayOutOfGeneralNotifications() {
        val before = Notes.unread()
        Notes.add("sport", "Goal", "test-goal")
        assertEquals(before, Notes.unread())
        assertFalse(Notes.list.any { it.id == "test-goal" })
    }

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
