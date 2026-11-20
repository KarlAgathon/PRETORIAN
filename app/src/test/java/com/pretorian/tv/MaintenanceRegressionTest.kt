package com.pretorian.tv

import org.junit.Assert.*
import org.junit.Test

class MaintenanceRegressionTest {
    @Test fun backRestoresProviderGroupAndSkipsActionRows() {
        val a = GRow.Group(GroupRef("a", Category("1", "News")))
        val b = GRow.Group(GroupRef("b", Category("1", "News")))
        val rows = listOf(GRow.NewFav(), a, b)
        assertEquals(b.id, groupFocusTarget(rows, b.ref))
        assertEquals(a.id, groupFocusTarget(rows, null))
        assertNull(groupFocusTarget(listOf(GRow.NewFav()), b.ref))
    }

    @Test fun laterPlaybackInvalidatesPendingRecovery() {
        val state = PlaybackRequest()
        val first = state.begin()
        val next = state.begin()
        assertFalse(state.current(first))
        assertTrue(state.current(next))
    }

    @Test fun epgProgressHandlesEdgesAndInvalidDurations() {
        val show = EpgItem("News", null, 100L, 200L)
        assertEquals(0f, show.progress(99), 0f)
        assertEquals(0.5f, show.progress(150), 0f)
        assertEquals(1f, show.progress(210), 0f)
        assertEquals(0f, show.copy(end = 100L).progress(110), 0f)
    }

    @Test fun favoritesBackupExcludesPlaylistsAndDeviceSecrets() {
        assertTrue(Backup.included("favch_news", setOf(4)))
        assertFalse(Backup.included("playlists", setOf(4)))
        for (key in listOf("backup_pass", "gh_token", "gd_token", "ext_player")) {
            assertFalse(Backup.included(key, null))
            assertFalse(Backup.included(key, setOf(3, 4, 10)))
        }
    }

    @Test fun m3uRetainsProviderAndDoesNotReusePreviousGroup() {
        val channels = M3u.parse(sequenceOf("#EXTM3U", "#EXTINF:-1 group-title=\"A\",First",
            "https://example.test/a", "#EXTINF:-1,Second", "https://example.test/b"), "provider")
        assertEquals(listOf("A", "Ostalo"), channels.map { it.group })
        assertEquals(listOf("provider", "provider"), channels.map { it.pid })
    }
}
