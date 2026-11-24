package com.pretorian.tv

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 2.9.1: gentle channel checks, AI key sealing, temporary parental unlock, clock format. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Release291Test {
    @Test fun checksStayInsideTheHourlyBudget() {
        ChannelHealth.recent.clear()
        val now = System.currentTimeMillis()
        repeat(60) { ChannelHealth.recent.addLast(now) }
        assertFalse(ChannelHealth.budgetOk())
        ChannelHealth.recent.clear(); repeat(60) { ChannelHealth.recent.addLast(now - 3_700_000L) }
        assertTrue("checks older than one hour no longer count", ChannelHealth.budgetOk())
        ChannelHealth.recent.clear()
    }
    @Test fun onlyChannelsAroundTheFocus() { assertTrue(ChannelHealth.WINDOW in 1..8) }

    @Test fun aiKeyIsStoredSealed() {
        Prefs.init(RuntimeEnvironment.getApplication())
        SportsAI.setKey("gemini", "AIzaTEST-1234567890")
        // the test machine has no Android keystore, so only the round trip is checked here (sealing is tested on the box)
        assertEquals("AIzaTEST-1234567890", SportsAI.keyOf("gemini"))
        Prefs.put("ai_key_gemini", "")
    }
    @Test fun oldPlainKeyIsSealedOnFirstRead() {
        Prefs.init(RuntimeEnvironment.getApplication())
        Prefs.put("ai_key_gemini", ""); Prefs.put("gemini_key", "AIzaOLD-plain")
        assertEquals("AIzaOLD-plain", SportsAI.keyOf("gemini"))
        assertEquals("", Prefs.str("gemini_key"))
        Prefs.put("ai_key_gemini", "")
    }

    @Test fun temporaryUnlockEndsWhenTheAppLeaves() {
        Lock.allOpen = true; Lock.unlocked.add("g:x")
        assertTrue(Lock.isOpen("c:anything"))
        Lock.relock()
        assertFalse(Lock.isOpen("c:anything")); assertFalse(Lock.isOpen("g:x"))
    }

    @Test fun clockFormatFollowsTheSetting() {
        val ctx = RuntimeEnvironment.getApplication()
        Prefs.init(ctx)
        val d = java.util.GregorianCalendar(2026, 0, 1, 15, 5).time
        LookOpts.set("clock_fmt", 1); assertEquals("15:05", LookOpts.time(ctx, d))
        LookOpts.set("clock_fmt", 2); assertTrue(LookOpts.time(ctx, d).startsWith("3:05"))
        LookOpts.set("clock_fmt", 0)
    }
}
