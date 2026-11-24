package com.pretorian.tv

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 3.0 provider protection with a fake clock: 30 per rolling hour, 6 s spacing, persisted budget and cooldown. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ProviderGuard300Test {
    private val a = "acct-test"
    @Before fun setUp() { Prefs.init(RuntimeEnvironment.getApplication()); Prefs.remove("pg_t_$a", "pg_cool_$a", "pg_why_$a", "ch_probe_active") }

    @Test fun passiveByDefault() { assertFalse(ChannelHealth.probeOn) }

    @Test fun thirtyPerRollingHourAndSpacing() {
        var t = 1_000_000_000L
        repeat(30) { assertNull("request $it", ProbeGuard.wait(a, t)); assertTrue(ProbeGuard.spend(a, t)); t += ProbeGuard.GAP_MS }
        assertNotNull("31st within the hour must wait", ProbeGuard.wait(a, t))
        assertFalse(ProbeGuard.spend(a, t))
        assertNotNull("one second after the last request is too early", ProbeGuard.wait(a, t - ProbeGuard.GAP_MS + 1000))
        assertNull("after the rolling hour the budget is back", ProbeGuard.wait(a, 1_000_000_000L + 3600_000L + 30 * ProbeGuard.GAP_MS))
    }

    @Test fun budgetSurvivesRestart() {
        val t = 2_000_000_000L
        repeat(30) { ProbeGuard.spend(a, t + it * ProbeGuard.GAP_MS) }
        Prefs.init(RuntimeEnvironment.getApplication())   // a new process reads the same preferences
        assertNotNull(ProbeGuard.wait(a, t + 31 * ProbeGuard.GAP_MS))
    }

    @Test fun clockMovedBackStillCounts() {
        val t = 3_000_000_000L
        repeat(30) { ProbeGuard.spend(a, t + it * ProbeGuard.GAP_MS) }
        assertNotNull("stamps from the 'future' keep counting", ProbeGuard.wait(a, t - 86_400_000L))
    }

    @Test fun cooldownIsKeptAndEnds() {
        val t = 4_000_000_000L
        ProbeGuard.cool(a, 24 * 3600_000L, "connection limit", t)
        assertNotNull(ProbeGuard.blocked(a, t + 3600_000L))
        assertNull(ProbeGuard.blocked(a, t + 25 * 3600_000L))
    }

    @Test fun limitAndAuthAreNotRetriedAsChannelFailures() {
        assertEquals(ChannelHealth.Res.LIMIT, ChannelHealth.classify(429))
        assertEquals(ChannelHealth.Res.AUTH, ChannelHealth.classify(403))
    }

    @Test fun accountKeyHasNoLogin() {
        val k = ProbeGuard.account("http://prov.example:8080/live/user/secretpass/1.ts")
        assertFalse(k.contains("user") || k.contains("secretpass"))
        assertEquals(k, ProbeGuard.account("http://prov.example:8080/live/other/pw/2.ts"))
    }
}
