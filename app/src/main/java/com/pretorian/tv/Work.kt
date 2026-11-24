package com.pretorian.tv

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex

/**
 * 2.9.0 – one small coordinator for background work (no framework, no service, no wakelock).
 * Every heavy job (guide download, playlist refresh, cache/database upkeep, speed test, bulk channel checks) asks
 * [heavyAllowed] / waits in [awaitHeavy]; light jobs (Match Follow, progress, critical diagnostics) are never held back.
 *
 * Heavy work waits while: anything plays (preview or full screen, also paused – the provider connection may still be
 * open), the app is in the background, memory is low, there is no internet, the app is in safe mode after repeated
 * crashes, or the first 60 s after start (no startup burst). Two heavy jobs never run at the same time.
 */
object Work {
    @Volatile var background = false
    @Volatile var lowMemoryUntil = 0L
    @Volatile var online = true
    @Volatile var startedAt = android.os.SystemClock.elapsedRealtime()
    @Volatile var safeMode = false
    private val heavy = Mutex()
    /** speed tests never overlap (the Diagnostics button, the weekly profile, anything else) */
    val speedTest = Mutex()

    enum class State { STARTUP, PLAYBACK, BACKGROUND, LOW_MEMORY, OFFLINE, SAFE_MODE, IDLE }

    fun state(): State = when {
        safeMode -> State.SAFE_MODE
        playing() -> State.PLAYBACK
        background -> State.BACKGROUND
        android.os.SystemClock.elapsedRealtime() < lowMemoryUntil -> State.LOW_MEMORY
        !online -> State.OFFLINE
        android.os.SystemClock.elapsedRealtime() - startedAt < 60_000 -> State.STARTUP
        else -> State.IDLE
    }
    /** preview counts as playback; a paused player still holds its stream */
    fun playing() = ChannelHealth.previewOn || ChannelHealth.playerOn || PlayState.inPlayer
    fun heavyAllowed() = state() == State.IDLE

    /** waits until heavy work is allowed, then runs [block] alone; [label] goes to the diagnostics when it had to wait long */
    suspend fun <T> heavy(label: String, block: suspend () -> T): T {
        val t0 = android.os.SystemClock.elapsedRealtime()
        awaitHeavy()
        return heavy.lockWithWait {
            val waited = android.os.SystemClock.elapsedRealtime() - t0
            if (waited > 10 * 60_000L) DiagEvents.add("work", "$label started after waiting ${waited / 60_000} min (${state()})")  // i18n-ignore
            PerfLog.task(label, "start", waited)  // i18n-ignore
            val t1 = android.os.SystemClock.elapsedRealtime()
            try { block().also { PerfLog.task(label, "done", android.os.SystemClock.elapsedRealtime() - t1) } }  // i18n-ignore
            catch (e: Throwable) { PerfLog.task(label, if (e is kotlinx.coroutines.CancellationException) "cancelled" else "failed", android.os.SystemClock.elapsedRealtime() - t1, e.javaClass.simpleName); throw e }  // i18n-ignore
        }
    }
    suspend fun awaitHeavy() { while (!heavyAllowed()) delay(15_000) }
    private suspend inline fun <T> Mutex.lockWithWait(block: () -> T): T { lock(); try { return block() } finally { unlock() } }

    /** network state from Android (no polling) */
    fun watchNetwork(ctx: Context) {
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            cm.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(n: android.net.Network) { if (!online) DiagEvents.add("net", "internet back"); online = true }  // i18n-ignore
                override fun onLost(n: android.net.Network) { online = false; DiagEvents.add("netwarn", "network lost") }  // i18n-ignore
            })
        } catch (_: Throwable) {}
    }
}
