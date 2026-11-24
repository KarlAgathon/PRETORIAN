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
 * reliable known. A result older than six hours stays visible and is only marked for re-checking (never erased to grey).
 *
 * Keys: the first 16 hex characters of SHA-256(stream address) — never the address itself, so no login is stored, and a
 * changed stream or changed credentials give a new key (the old result simply stops applying and ages out).
 * Storage: files/health.json, at most 20,000 entries, written at most every 5 s in the background.
 *
 * Evidence: real playback is stronger than the light check; a light check never overwrites a playback result from the
 * last 30 minutes. The light check reads only the start of the stream (HLS "#EXTM3U" or MPEG-TS sync bytes) — that
 * shows the provider serves it, not that every segment decodes.
 *
 * 3.0 provider protection: active checks are OFF by default (passive mode — lamps come only from real playback).
 * When the owner switches them on (Settings › Plejer › Više opcija): one connection at a time, only the channels on
 * screen (focused ±6), at least 6 s apart, at most 30 network requests per rolling hour per provider (redirects count),
 * never during startup, preview, playback, pause, background, offline, low memory or within 3 s of a stream opening or
 * closing. Budget and cooldowns are kept in preferences, so a restart or a new group does not reset them (see ProbeGuard).
 * Leaving a group cancels its pending work; finished results stay. No internet, timeouts, too many connections
 * (429/458/509), login refusals (401/403 on most channels) or a provider outage (5 errors in a row) are inconclusive:
 * the previous result stays and the run pauses or stops instead of painting the group orange.
 * Stalker portals are not checked (a check would need a portal handshake per channel); their lamps come from playback.
 */
object ChannelHealth {
    var rev by mutableIntStateOf(0)
    private const val STALE_MS = 6 * 60 * 60_000L   // 2.9.1: a result is re-checked after 6 h, not 1 h
    private const val PLAYBACK_WINS_MS = 30 * 60_000L
    private const val MAX_ENTRIES = 20_000

    /** status 1 ok / 0 failed; at = last reliable result; tried = last attempt; src P playback / L light check */
    private class E(var ok: Boolean, var at: Long, var tried: Long, var src: Char)
    private val map = HashMap<String, E>()
    @Volatile private var file: File? = null
    @Volatile private var dirty = false
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 3.0: "Aktivna provjera kanala" — off by default (provider policy unknown → passive evidence only) */
    val probeOn get() = Prefs.bool("ch_probe_active", false)
    /** a stream opening or closing: probes keep away for 3 s and an open probe connection is closed at once */
    @Volatile var previewOn = false
        set(v) { if (field != v) { field = v; settle(); if (v) abortProbe() } }
    @Volatile var playerOn = false
        set(v) { if (field != v) { field = v; settle(); if (v) abortProbe() } }
    @Volatile private var quietUntil = 0L
    private fun settle() { quietUntil = android.os.SystemClock.elapsedRealtime() + 3000 }
    @Volatile private var live: HttpURLConnection? = null
    /** user playback wins: the probe in flight is closed before the stream opens */
    fun abortProbe() { val c = live ?: return; live = null; ProbeGuard.count("cancelled"); try { c.disconnect() } catch (_: Throwable) {} }
    /** why a probe may not run now (null = may run) */
    internal fun busyWhy(): String? = when {
        Work.state() != Work.State.IDLE -> Work.state().name.lowercase()
        android.os.SystemClock.elapsedRealtime() < quietUntil -> "stream opening or closing"  // i18n-ignore
        else -> null
    }

    fun key(url: String): String = try {
        java.security.MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
    } catch (_: Throwable) { url.hashCode().toString(16) }

    fun init(ctx: Context) {
        // 3.0.0.3: active checks are OFF for every user once (owner decision); the switch stays in Settings only
        if (!Prefs.bool("ch_default_off_303")) { Prefs.put("ch_probe_active", false); Prefs.put("ch_default_off_303", true) }  // i18n-ignore
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
        return "$ok ok · $bad failed · $st waiting for re-check · mode " + (if (probeOn) "active (opt-in)" else "passive") + " · scheduler " + (if (job?.isActive == true) (busyWhy()?.let { "paused ($it)" } ?: "running") else "idle") +  // i18n-ignore
            (lastStop?.let { " · last stop: $it" } ?: "")  // i18n-ignore
    }

    // ------------------------------------------------------------------ scheduler
    @Volatile private var job: Job? = null
    @Volatile private var list: List<Channel> = emptyList()
    @Volatile private var focusUrl: String? = null
    @Volatile private var lastStop: String? = null
    private val done = HashSet<String>()

    /** the focused channel goes first, its neighbours (what is on screen) next */
    fun focus(url: String) { focusUrl = url; if (job?.isActive != true && list.isNotEmpty() && probeOn && !limitHit) job = io.launch { run() } }

    /** only the channels on screen: the focused one ±WINDOW */
    internal const val WINDOW = 6
    @Volatile internal var limitHit = false

    /** a TV group was opened: check it progressively; a previous group's pending work is cancelled */
    fun schedule(group: List<Channel>) {
        job?.cancel()
        list = group; synchronized(done) { done.clear() }
        if (!probeOn || group.isEmpty() || limitHit) return
        job = io.launch { run() }
    }
    /** the group was left: pending work stops, results stay */
    fun leave() { job?.cancel(); job = null }

    private fun next(): Channel? {
        val l = list; if (l.isEmpty()) return null
        val f = focusUrl?.let { u -> l.indexOfFirst { it.url == u } } ?: -1
        // only what is on screen: the focused channel, then ±WINDOW around it (the first rows when nothing is focused yet)
        val c0 = if (f >= 0) f else 0
        val order = (listOf(c0) + (1..WINDOW).flatMap { listOf(c0 + it, c0 - it) }).filter { it in l.indices }.distinct()
        return order.asSequence().map { l[it] }.firstOrNull { c ->
            synchronized(done) { c.url !in done } && !Stalker.isLink(c.url) && (of(c.url) == null || stale(c.url))
        }
    }

    private suspend fun run() {
        delay(1500)   // debounce: fast scrolling settles first
        var fails = 0; var oks = 0
        while (coroutineContext.isActive) {
            if (!probeOn || limitHit) return
            busyWhy()?.let { ProbeGuard.skip(it); while (busyWhy() != null) delay(2000) }
            val c = next() ?: return
            val acct = ProbeGuard.account(c.url)
            val why = ProbeGuard.blocked(acct)
            if (why != null) { ProbeGuard.skip(why); stop(why); return }
            val waitMs = ProbeGuard.wait(acct)
            if (waitMs != null) { ProbeGuard.skip("budget"); delay(waitMs); continue }  // i18n-ignore
            synchronized(done) { done.add(c.url) }
            ProbeGuard.spend(acct)
            val r = check(Net.udp(c.url)) { ProbeGuard.wait(acct) == null && ProbeGuard.blocked(acct) == null && busyWhy() == null && ProbeGuard.spend(acct) }
            ProbeGuard.count(r.name.lowercase())
            when (r) {
                Res.OK -> { put(c.url, true, 'L'); oks++; fails = 0 }
                Res.BAD -> { put(c.url, false, 'L'); fails++ }
                // 3.0: one login refusal or one connection-limit answer is enough — no automatic retry, cooldown kept across restarts
                Res.AUTH -> { tried(c.url); ProbeGuard.cool(acct, 24 * 3600_000L, "login refused"); stop("login refused by the provider – active checks paused 24 h"); return }  // i18n-ignore
                Res.LIMIT -> { tried(c.url); limitHit = true; ProbeGuard.cool(acct, maxOf(24 * 3600_000L, lastRetryAfter), "connection limit"); stop("provider connection limit – active checks paused 24 h"); return }  // i18n-ignore
                Res.NO_NET -> { tried(c.url); stop("no internet"); return }  // i18n-ignore
                Res.UNSURE -> { tried(c.url); fails++ }
            }
            // ambiguous failures: conservative backoff, then stop for this group
            if (fails >= 3 && oks == 0) { ProbeGuard.cool(acct, 3600_000L, "provider not answering"); stop("provider not answering – paused 1 h"); return }  // i18n-ignore
            delay(ProbeGuard.GAP_MS + (fails * 4000L))
        }
    }
    @Volatile private var lastRetryAfter = 0L
    private fun stop(why: String) { lastStop = why + " · " + DiagEvents.stamp().substring(11, 19); DiagEvents.add("health", "check stopped: $why") }  // i18n-ignore

    enum class Res { OK, BAD, AUTH, LIMIT, NO_NET, UNSURE }

    /** a light look at a stream, classified */
    internal fun check(url: String, nextHop: () -> Boolean = { true }): Res {
        if (!url.startsWith("http")) return Res.UNSURE
        var cur = url
        repeat(4) { hop ->
            if (hop > 0 && !nextHop()) return Res.UNSURE   // every redirect is one more request for the budget
            val c = try { URL(cur).openConnection() as HttpURLConnection } catch (_: Throwable) { return Res.UNSURE }
            live = c
            c.connectTimeout = 4000; c.readTimeout = 4000; c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", Net.UA)
            try {
                val code = c.responseCode
                if (code == 429 || code == 503) lastRetryAfter = (c.getHeaderField("Retry-After")?.trim()?.toLongOrNull() ?: 0L).coerceIn(0, 7 * 86_400L) * 1000
                if (code in 300..399) { cur = URL(URL(cur), c.getHeaderField("Location") ?: return Res.BAD).toString(); return@repeat }
                return classify(code) ?: run {
                    val buf = ByteArray(2048); var n = 0
                    c.inputStream.use { ins -> while (n < buf.size) { val r = ins.read(buf, n, buf.size - n); if (r <= 0) break; n += r } }
                    if (n <= 0) Res.BAD else if (looksLikeStream(buf, n)) Res.OK else Res.BAD
                }
            } catch (e: java.net.UnknownHostException) { return Res.NO_NET
            } catch (e: java.net.SocketTimeoutException) { return Res.UNSURE
            } catch (e: Exception) { return Res.UNSURE
            } finally { live = null; c.disconnect() }
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


/**
 * 3.0 provider protection — the budget for active checks, per provider (scheme + host + port, hashed; no login stored).
 * At most PER_HOUR requests in any rolling hour (redirects count), at least GAP_MS apart, cooldowns after a connection-limit
 * answer, a login refusal or repeated silence. Kept in preferences, so restarts, new groups or playlist changes do not reset
 * it. A clock moved backwards keeps the old stamps counting (more careful, never less). These local counts cannot see other
 * devices on the same account or what the provider counts.
 */
object ProbeGuard {
    const val PER_HOUR = 30
    const val GAP_MS = 6000L
    private const val HOUR = 3600_000L
    private val counts = java.util.concurrent.ConcurrentHashMap<String, Int>()

    fun account(url: String): String = try {
        val u = URL(url); ChannelHealth.key("acct:" + u.protocol + "://" + u.host + ":" + u.port)  // i18n-ignore
    } catch (_: Throwable) { ChannelHealth.key("acct:?") }  // i18n-ignore

    private fun stamps(a: String): List<Long> = Prefs.str("pg_t_$a").split(',').mapNotNull { it.toLongOrNull() }
    /** ms to wait before the next request may go out, null = may go now */
    @Synchronized fun wait(a: String, now: Long = System.currentTimeMillis()): Long? {
        val live = stamps(a).filter { now - it < HOUR }   // a stamp in the future (clock moved back) still counts
        val last = live.maxOrNull()
        if (last != null && now - last < GAP_MS) return GAP_MS - (now - last).coerceAtLeast(0)
        if (live.size >= PER_HOUR) return (HOUR - (now - live.min())).coerceIn(GAP_MS, HOUR)
        return null
    }
    /** records one request; false when the budget is already used up */
    @Synchronized fun spend(a: String, now: Long = System.currentTimeMillis()): Boolean {
        val live = stamps(a).filter { now - it < HOUR }
        if (live.size >= PER_HOUR) { count("prevented: budget"); return false }  // i18n-ignore
        Prefs.put("pg_t_$a", (live + now).takeLast(PER_HOUR).joinToString(","))
        count("requests"); return true  // i18n-ignore
    }
    @Synchronized fun cool(a: String, ms: Long, why: String, now: Long = System.currentTimeMillis()) {
        Prefs.put("pg_cool_$a", now + ms); Prefs.put("pg_why_$a", why); count("cooldown: $why")  // i18n-ignore
        DiagEvents.add("health", "provider protection: $why, active checks paused ${ms / 60_000} min")  // i18n-ignore
    }
    /** the reason active checks are paused for this provider, or null */
    fun blocked(a: String, now: Long = System.currentTimeMillis()): String? {
        val until = Prefs.long("pg_cool_$a", 0)
        if (until <= now) return null
        if (until - now > 8 * 86_400_000L) { Prefs.put("pg_cool_$a", now + 86_400_000L) }   // clock jumped: keep a sane 24 h
        return "cooldown (" + Prefs.str("pg_why_$a") + ")"  // i18n-ignore
    }
    fun count(what: String) { counts.merge(what, 1, Int::plus) }
    fun skip(why: String) = count("skipped: $why")  // i18n-ignore
    /** for the Full Diagnostic Report (this session) */
    fun report(): String = buildString {
        append("mode: ").append(if (ChannelHealth.probeOn) "active checks on (owner opt-in)" else "passive (default) – lamps from real playback only").append('\n')  // i18n-ignore
        append("limits: $PER_HOUR requests per rolling hour per provider, ${GAP_MS / 1000} s apart, one at a time, redirects counted\n")  // i18n-ignore
        counts.toSortedMap().forEach { (k, v) -> append(k).append(": ").append(v).append('\n') }
        Prefs.allKeys().filter { it.startsWith("pg_cool_") }.forEach { k ->
            val until = Prefs.long(k, 0); if (until > System.currentTimeMillis()) append("cooldown until ").append(DiagEvents.stamp(until)).append(" · ").append(Prefs.str("pg_why_" + k.removePrefix("pg_cool_"))).append('\n')  // i18n-ignore
        }
        append("note: counts are this device only; other devices on the same account and provider-side counts are not visible\n")  // i18n-ignore
    }
}
