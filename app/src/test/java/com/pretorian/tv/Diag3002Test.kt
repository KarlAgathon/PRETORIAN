package com.pretorian.tv

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 3.0.0.2 passive diagnostics: classification, account fields without credentials, collector isolation, report sections. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Diag3002Test {
    private val ctx get() = RuntimeEnvironment.getApplication()

    @Test fun networkErrorsAreClassified() {
        assertEquals("DNS resolution failure", DiagCollect.classify(RuntimeException(java.net.UnknownHostException("x"))))
        assertEquals("connection refused", DiagCollect.classify(java.net.ConnectException("refused")))
        assertEquals("connection reset", DiagCollect.classify(java.net.SocketException("Connection reset")))
        assertEquals("read timeout", DiagCollect.classify(java.net.SocketTimeoutException("Read timed out")))
        assertEquals("TLS / certificate failure", DiagCollect.classify(javax.net.ssl.SSLHandshakeException("bad")))
        assertEquals("unknown", DiagCollect.classify(IllegalStateException("?")))
    }
    @Test fun accountFieldsWithoutCredentials() {
        Prefs.init(ctx)
        val ui = org.json.JSONObject().put("username", "secretuser").put("password", "secretpw").put("status", "Active")
            .put("max_connections", "1").put("active_cons", "0").put("exp_date", "1798761600").put("auth", 1)
        DiagCollect.account("pl1", ui)
        val stored = Prefs.allStrings().first { it.first.startsWith("diag_acct_") }
        assertFalse(stored.second.contains("secretuser") || stored.second.contains("secretpw"))
        assertFalse(stored.first.contains("pl1"))
        val j = DiagCollect.accounts().first().second
        assertEquals("1", j.optString("max")); assertEquals("0", j.optString("active"))
    }
    @Test fun aFailingCollectorIsSwitchedOffNotThrown() {
        DiagCollect.guard("test-collector") { throw IllegalStateException("boom") }
        assertTrue("test-collector" in DiagCollect.disabled())
        var ran = false; DiagCollect.guard("test-collector") { ran = true }
        assertFalse(ran)
    }
    @Test fun repeatedEventsAreRateLimited() {
        repeat(50) { DiagCollect.limited("unit-key", 60_000, "test", "same") }
        assertTrue((DiagCollect.get("suppressed unit-key") ?: 0) >= 49)
    }
    @Test fun reportHasExtendedSectionsAndNoSecrets() {
        Prefs.init(ctx); DiagStore.init(ctx); DeviceIdentity.init(ctx)
        DiagCollect.account("plX", org.json.JSONObject().put("username", "zzuser").put("password", "zzpass").put("max_connections", "2"))
        DiagEvents.add("net", "network wifi → none")
        val f = java.io.File(ctx.cacheDir, "e.txt")
        DiagReport.build(ctx, DiagReport.Kind.FULL, f)
        val t = f.readText()
        for (n in 32..50) assertTrue("section $n", t.contains("\n$n. "))
        assertTrue(t.contains("max connections 2"))
        assertFalse(t.contains("zzpass")); assertFalse(t.contains("zzuser"))
        f.delete()
    }
}
