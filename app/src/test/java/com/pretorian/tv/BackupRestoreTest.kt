package com.pretorian.tv

import android.content.Context
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class BackupRestoreTest {
    private lateinit var context: Context

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        Prefs.init(context)
    }

    @Test fun selectiveRestoreKeepsDestinationPlaylistAndDevicePassword() {
        Prefs.put("favch_news", "source favorites")
        Prefs.put("backup_pass", "only-on-source")
        val exported = Backup.export(context, null, setOf(3, 4))
        assertFalse(exported.contains("only-on-source"))
        Prefs.put("playlists", "destination playlist")
        Prefs.put("backup_pass", "only-on-destination")
        Prefs.remove("favch_news")
        Backup.restore(context, exported, null)
        assertEquals("destination playlist", Prefs.str("playlists"))
        assertEquals("source favorites", Prefs.str("favch_news"))
        assertEquals("only-on-destination", Prefs.str("backup_pass"))
    }

    @Test fun legacyBackupWithUnknownScopePreservesAbsentSettings() {
        val exported = JSONObject(Backup.export(context, null, setOf(4)))
        exported.getJSONObject("data").apply { remove("full"); remove("parts") }
        Prefs.put("seek_step", "30")
        Backup.restore(context, exported.toString(), null)
        assertEquals(30, Prefs.seekStep)
    }

    @Test fun invalidProgressDoesNotApplyPreferenceChanges() {
        Prefs.put("seek_step", "20")
        val exported = JSONObject(Backup.export(context, null))
        exported.getJSONObject("data").getJSONObject("files").put("progress.json", "broken")
        Prefs.put("seek_step", "30")
        assertThrows(Exception::class.java) { Backup.restore(context, exported.toString(), null) }
        assertEquals(30, Prefs.seekStep)
    }
}
