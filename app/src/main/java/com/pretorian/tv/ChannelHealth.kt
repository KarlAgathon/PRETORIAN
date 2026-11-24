package com.pretorian.tv

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Channel health (2.8.0). Three lamps only: green = the last reliable check worked, orange = it failed, grey = nothing
 * reliable known. A result older than one hour stays visible and is only marked for re-checking (never erased to grey).
 *
 * Keys: the first 16 hex characters of SHA-256(stream address) — never the address itself, so no login is stored, and a
 * changed stream or changed credentials give a new key (the old result simply stops applying and ages out).
 * Storage: files/health.json, at most 20,000 entries, written at most every 5 s in the background.
 *
 * Evidence: real playback is stronger than the light check; a light check never overwrites a playback result from the
 * last 30 minutes. The light check reads only the start of the stream (HLS "#EXTM3U" or MPEG-TS sync bytes) — that
 * shows the provider serves it, not that every segment decodes.
 *
 * Scheduler: one worker, one connection at a time (providers often allow one), paused while a preview or the full
 * player plays. Order: the focused channel, then the channels around it (what is on screen), then the rest of the group.
 * Leaving a group cancels its pending work; finished results stay. No internet, timeouts, too many connections
 * (429/458/509), login refusals (401/403 on most channels) or a provider outage (5 errors in a row) are inconclusive:
 * the previous result stays and the run pauses or stops instead of painting the group orange.
 * Stalker portals are not checked (a check would need a portal handshake per channel); their lamps come from playback.
 */
object ChannelHealth {
    var rev by mutableIntStateOf(0)
    private const val STALE_MS = 60 * 60_000L
    private const val PLAYBACK_WINS_MS = 30 * 60_000L
    private const val MAX_ENTRIES = 20_000

    /** status 1 ok / 0 failed; at = last reliable result; tried = last attempt; src P playback / L light check */
    private class E(var ok: Boolean, var at: Long, var tried: Long, var src: Char)
    private val map = HashMap<String, E>()
    @Volatile private var file: File? = null
    @Volatile private var dirty = false
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Settings switch "Provjera kanala" (on by default) */
    val probeOn get() = Prefs.bool("ch_probe", true)
    @Volatile var previewOn = false
    @Volatile var playerOn = false
    private fun busy() = previewOn || playerOn || Work.background || !Work.online || Work.safeMode ||
        android.os.SystemClock.elapsedRealtime() < Work.lowMemoryUntil

    fun key(url: String): String = try {
        java.security.MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
    } catch (_: Throwable) { url.hashCode().toString(16) }

    fun init(ctx: Context) {
        if (file != null) return
        val f = File(ctx.filesDir, "health.json"); file = f
        io.launch {
            try {
                if (f.exists() && f.length() < 8L * 1024 * 1024) {
                    val o = org.json.JSONObject(f.readText())
                    synchronized(map) { o.keys().forEach { k -> o.optJSONArray(k)?.let { a -> map[k] = E(a.optInt(0) == 1, a.optLong(1), a.optLong(2), a.optString(3, "L").firstOrNull() ?: 'L') } } }
                    rev++
                }
            } catch (_: Throwable) { try { f.delete() } catch (_: Throwable) {} }   // a broken file is dropped, never a crash
            Prefs.remove("ch_health")   // 2.7.0 kept results in preferences
            while (true) { delay(5000); if (dirty) save() }
        }
    }
    private fun save() {
        val f = file ?: return
        if (!DiagSpace.ok(f.parentFile)) return
        val o = org.json.JSONObject()
        synchronized(map) {
            dirty = false
            if (map.size > MAX_ENTRIES) map.entries.sortedBy { it.value.tried }.take(map.size - MAX_ENTRIES).map { it.key }.forEach { map.remove(it) }
            map.forEach { (k, e) -> o.put(k, org.json.JSONArray().put(if (e.ok) 1 else 0).put(e.at).put(e.tried).put(e.src.toString())) }
        }
        try { val tmp = File(f.path + ".tmp"); tmp.writeText(o.toString()); tmp.renameTo(f) } catch (_: Throwable) {}
    }

    /** true works, false failed, null nothing reliable known (a stale result is still returned) */
    fun of(url: String): Boolean? = ofKey(key(url))
    fun ofKey(k: String): Boolean? = synchronized(map) { map[k]?.ok }
    fun stale(url: String): Boolean = synchronized(map) { map[key(url)]?.let { System.currentTimeMillis() - it.tried > STALE_MS } ?: true }

    /** a result from real playback (strongest evidence) */
    fun mark(url: String?, ok: Boolean) = put(url, ok, 'P')

    private fun put(url: String?, ok: Boolean, src: Char) {
        if (url.isNullOrBlank()) return
        val now = System.currentTimeMillis(); val k = key(url)
        val changed = synchronized(map) {
            val e = map[k]
            if (src == 'L' && e != null && e.src == 'P' && now - e.at < PLAYBACK_WINS_MS) { e.tried = now; false }   // playback wins
            else if (e == null) { map[k] = E(ok, now, now, src); true }
            else { val c = e.ok != ok; e.ok = ok; e.at = now; e.tried = now; e.src = src; c }
        }
        dirty = true
        if (changed) rev++
    }
    /** a light-check result (tests and the scheduler) */
    internal fun probeResult(url: String, ok: Boolean) = put(url, ok, 'L')
    private fun tried(url: String) { synchronized(map) { map[key(url)]?.tried = System.currentTimeMillis() }; dirty = true }

    fun stats(): String {
        val now = System.currentTimeMillis()
        val (ok, bad, st) = synchronized(map) { Triple(map.values.count { it.ok }, map.values.count { !it.ok }, map.values.count { now - it.tried > STALE_MS }) }
        return "$ok ok · $bad failed · $st waiting for re-check · scheduler " + (if (job?.isActive == true) (if (busy()) "paused (playback)" else "running") else "idle") +  // i18n-ignore
            (lastStop?.let { " · last stop: $it" } ?: "")  // i18n-ignore
    }

    // ------------------------------------------------------------------ scheduler
    @Volatile private var job: Job? = null
    @Volatile private var list: List<Channel> = emptyList()
    @Volatile private var focusUrl: String? = null
    @Volatile private var lastStop: String? = null
    private val done = HashSet<String>()

    /** the focused channel goes first, its neighbours (what is on screen) next */
    fun focus(url: String) { focusUrl = url }

    /** a TV group was opened: check it progressively; a previous group's pending work is cancelled */
    fun schedule(group: List<Channel>) {
        job?.cancel()
        list = group; synchronized(done) { done.clear() }
        if (!probeOn || group.isEmpty()) return
        job = io.launch { run() }
    }
    /** the group was left: pending work stops, results stay */
    fun leave() { job?.cancel(); job = null }

    private fun next(): Channel? {
        val l = list; if (l.isEmpty()) return null
        val f = focusUrl?.let { u -> l.indexOfFirst { it.url == u } } ?: -1
        // order: focused, then ±8 around it, then from the top
        val order = if (f >= 0) (listOf(f) + (1..8).flatMap { listOf(f + it, f - it) } + l.indices).filter { it in l.indices }.distinct() else l.indices.toList()
        return order.asSequence().map { l[it] }.firstOrNull { c ->
            synchronized(done) { c.url !in done } && !Stalker.isLink(c.url) && (of(c.url) == null || stale(c.url))
        }
    }

    private suspend fun run() {
        delay(1500)
        var fails = 0; var auth = 0; var oks = 0; var backoff = 0L
        while (coroutineContext.isActive) {
            if (!probeOn) return
            while (busy()) delay(2000)
            val c = next() ?: return
            synchronized(done) { done.add(c.url) }
            when (val r = check(Net.udp(c.url))) {
                Res.OK -> { put(c.url, true, 'L'); oks++; fails = 0; auth = 0; backoff = 0 }
                Res.BAD -> { put(c.url, false, 'L'); fails++ }
                Res.AUTH -> { tried(c.url); auth++; if (auth >= 3 && oks == 0) { stop("login refused by the provider"); return } }  // i18n-ignore
                Res.LIMIT -> { tried(c.url); synchronized(done) { done.remove(c.url) }; backoff = (backoff * 2).coerceIn(30_000, 300_000); DiagEvents.add("health", "provider connection limit, waiting ${backoff / 1000} s"); delay(backoff) }  // i18n-ignore
                Res.NO_NET -> { tried(c.url); stop("no internet"); return }  // i18n-ignore
                Res.UNSURE -> { tried(c.url); fails++ }
            }
            // five failures in a row without any success: probably the provider, not the channels — stop, keep old results
            if (fails >= 5 && oks == 0) { stop("provider not answering"); return }  // i18n-ignore
            delay(700)
        }
    }
    private fun stop(why: String) { lastStop = why + " · " + DiagEvents.stamp().substring(11, 19); DiagEvents.add("health", "check stopped: $why") }  // i18n-ignore

    enum class Res { OK, BAD, AUTH, LIMIT, NO_NET, UNSURE }

    /** a light look at a stream, classified */
    internal fun check(url: String): Res {
        if (!url.startsWith("http")) return Res.UNSURE
        var cur = url
        repeat(4) {
            val c = try { URL(cur).openConnection() as HttpURLConnection } catch (_: Throwable) { return Res.UNSURE }
            c.connectTimeout = 4000; c.readTimeout = 4000; c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", Net.UA)
            try {
                val code = c.responseCode
                if (code in 300..399) { cur = URL(URL(cur), c.getHeaderField("Location") ?: return Res.BAD).toString(); return@repeat }
                return classify(code) ?: run {
                    val buf = ByteArray(2048); var n = 0
                    c.inputStream.use { ins -> while (n < buf.size) { val r = ins.read(buf, n, buf.size - n); if (r <= 0) break; n += r } }
                    if (n <= 0) Res.BAD else if (looksLikeStream(buf, n)) Res.OK else Res.BAD
                }
            } catch (e: java.net.UnknownHostException) { return Res.NO_NET
            } catch (e: java.net.SocketTimeoutException) { return Res.UNSURE
            } catch (e: Exception) { return Res.UNSURE
            } finally { c.disconnect() }
        }
        return Res.UNSURE
    }
    /** null = 2xx, read the body */
    internal fun classify(code: Int): Res? = when {
        code in 200..299 -> null
        code == 401 || code == 403 -> Res.AUTH
        code == 429 || code == 458 || code == 509 || code == 503 -> Res.LIMIT
        code == 404 || code == 410 || code in 500..599 -> Res.BAD
        else -> Res.UNSURE
    }
    internal fun looksLikeStream(buf: ByteArray, n: Int): Boolean {
        val head = String(buf, 0, minOf(n, 16), Charsets.ISO_8859_1)
        return head.trimStart('﻿').startsWith("#EXTM3U") ||
            (0 until minOf(n, 400)).any { buf[it] == 0x47.toByte() && (it + 188 >= n || buf[it + 188] == 0x47.toByte()) }
    }
}

/** The lamp after the quality label: green works, orange failed at the last reliable check, an empty ring nothing known yet. */
@Composable
fun HealthLamp(url: String, size: Dp = 8.dp) {
    // 2.9.0: the key is hashed once per row, and a lamp redraws only when its own result changes (not on every result in the list)
    val k = androidx.compose.runtime.remember(url) { ChannelHealth.key(url) }
    val st by androidx.compose.runtime.remember(k) { androidx.compose.runtime.derivedStateOf { ChannelHealth.rev; ChannelHealth.ofKey(k) } }
    when (st) {
        true -> Box(Modifier.size(size).clip(CircleShape).background(Color(0xFF3FBF5F)))
        false -> Box(Modifier.size(size).clip(CircleShape).background(Color(0xFFF0A029)))
        null -> Box(Modifier.size(size).clip(CircleShape).border(1.dp, Color(0x80FFFFFF), CircleShape))
    }
}
