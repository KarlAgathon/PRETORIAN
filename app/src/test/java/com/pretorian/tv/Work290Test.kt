package com.pretorian.tv

import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 2.9.0: heavy background work only when the app is really idle; playback always wins. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Work290Test {
    @After fun reset() {
        ChannelHealth.previewOn = false; ChannelHealth.playerOn = false; PlayState.inPlayer = false
        Work.background = false; Work.online = true; Work.safeMode = false; Work.lowMemoryUntil = 0
    }
    private fun idleNow() { Work.startedAt = android.os.SystemClock.elapsedRealtime() - 120_000 }

    @Test fun idleAllowsHeavyWork() { idleNow(); assertTrue(Work.heavyAllowed()) }
    @Test fun previewCountsAsPlayback() { idleNow(); ChannelHealth.previewOn = true; assertEquals(Work.State.PLAYBACK, Work.state()); assertFalse(Work.heavyAllowed()) }
    @Test fun pausedPlayerStillHoldsTheStream() { idleNow(); PlayState.inPlayer = true; assertFalse(Work.heavyAllowed()) }
    @Test fun backgroundOfflineSafeModeAndStartupWait() {
        idleNow(); Work.background = true; assertFalse(Work.heavyAllowed()); Work.background = false
        Work.online = false; assertEquals(Work.State.OFFLINE, Work.state()); Work.online = true
        Work.safeMode = true; assertEquals(Work.State.SAFE_MODE, Work.state()); Work.safeMode = false
        Work.startedAt = android.os.SystemClock.elapsedRealtime(); assertEquals(Work.State.STARTUP, Work.state())
    }
    @Test fun lowMemoryWaits() { idleNow(); Work.lowMemoryUntil = android.os.SystemClock.elapsedRealtime() + 60_000; assertEquals(Work.State.LOW_MEMORY, Work.state()) }
    @Test fun speedTestsNeverOverlap() {
        assertTrue(Work.speedTest.tryLock()); assertFalse(Work.speedTest.tryLock()); Work.speedTest.unlock()
    }
}
