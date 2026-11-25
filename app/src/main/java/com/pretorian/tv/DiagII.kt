package com.pretorian.tv

import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 3.0.0.7 EXTENDED DIAGNOSTICS II (report sections 51–70). Same rules as [DiagCollect]: everything here only OBSERVES
 * what the app already does — Media3 callbacks of players that already exist, Android callbacks, the app's own existing
 * requests, imports and database work. Nothing here opens a request, a stream, a player, a test or a poll, and no
 * behaviour changes. Every collector is wrapped by [DiagCollect.guard]: its first error switches only that collector off.
 *
 * Storage: small bounded CSV tables through [PerfLog] (same 50 MB ceiling, same free-space floor, same writer thread).
 * Every row carries the session id; the "sessions" table maps each session to its app version and build commit, which is
 * what the version comparison (section 68) is built on. Older rows without a session entry stay "version unknown".
 */
object DiagII {
    const val SCHEMA = 3
    private fun g(name: String, block: () -> Unit) = DiagCollect.guard(name, block)
    fun now() = SystemClock.elapsedRealtime()

    // ------------------------------------------------------------------ small bounded statistics
    class Ring(private val cap: Int = 500) {
        private val q = java.util.ArrayDeque<Long>()
        @Volatile var total = 0L; private set
        fun add(v: Long) = synchronized(q) { q.addLast(v); total++; while (q.size > cap) q.removeFirst() }
        fun list(): List<Long> = synchronized(q) { q.toList() }
        fun drain(): List<Long> = synchronized(q) { val l = q.toList(); q.clear(); l }
    }
    fun pct(l: List<Long>, p: Double): Long = if (l.isEmpty()) 0 else l.sorted()[((l.size - 1) * p).toInt()]
    /** "n · min · median · p95 · max unit" or the honest absence of data */
    fun dist(l: List<Long>, unit: String = "ms"): String = if (l.isEmpty()) "[not observed] no samples" else  // i18n-ignore
        "${l.size} samples · min ${l.min()} · median ${pct(l, .5)} · p95 ${pct(l, .95)} · max ${l.max()} $unit"  // i18n-ignore
    private val rings = ConcurrentHashMap<String, Ring>()
    fun ring(k: String, cap: Int = 500): Ring = rings.getOrPut(k) { Ring(cap) }
    fun rings(prefix: String) = rings.filterKeys { it.startsWith(prefix) }.toSortedMap()
    fun inc(k: String, n: Long = 1) = DiagCollect.inc(k, n)
    fun cnt(k: String) = DiagCollect.counters[k]?.get() ?: 0L

    // ------------------------------------------------------------------ network configuration (sections 52, 53)
    /** "Ethernet without VPN" / "Ethernet with VPN" / "Wi-Fi without VPN" / "Wi-Fi with VPN" / "unknown" (from Android's own callbacks) */
    fun netCfg(t: String = NetState.transports): String {
        val base = when { t.contains("ethernet") -> "Ethernet"; t.contains("wifi") -> "Wi-Fi"; else -> return "unknown" }  // i18n-ignore
        return base + if (t.contains("vpn")) " with VPN" else " without VPN"  // i18n-ignore
    }
    fun vpnOn() = NetState.transports.contains("vpn")  // i18n-ignore
    private val netSince = AtomicLong(now())
    @Volatile private var netLast = "unknown"  // i18n-ignore
    /** time spent per configuration (ms, this session) and the moments of change; called from [NetState.changed] */
    fun netChanged(old: String, new: String) = g("net2") {  // i18n-ignore
        val t = now(); val was = netCfg(old)
        inc("time in $was (ms)", t - netSince.getAndSet(t))  // i18n-ignore
        val wasVpn = old.contains("vpn"); val isVpn = new.contains("vpn")  // i18n-ignore
        if (!wasVpn && isVpn) inc("VPN activations")  // i18n-ignore
        if (wasVpn && !isVpn) { inc("VPN deactivations"); if (new == "none") inc("VPN interruptions (network lost while VPN was on)") }  // i18n-ignore
        if (old == "none" && isVpn) inc("VPN reconnections (network back with VPN)")  // i18n-ignore
        lastNetChange = t; netLast = netCfg(new)
        if (playingNow) { inc("network transitions during playback"); netDuringPlay = t }  // i18n-ignore
    }
    @Volatile var lastNetChange = 0L
    @Volatile private var netDuringPlay = 0L
    fun netTimeNow(): Map<String, Long> {
        val m = HashMap<String, Long>()
        DiagCollect.counters.filterKeys { it.startsWith("time in ") }.forEach { (k, v) -> m[k.removePrefix("time in ").removeSuffix(" (ms)")] = v.get() }  // i18n-ignore
        val cur = netCfg(); m[cur] = (m[cur] ?: 0L) + (now() - netSince.get())
        return m
    }
    /** Wi-Fi signal and link speed only from the active network's own capabilities (API 29+, no location permission) */
    fun wifiInfo(ctx: Context): String = try {
        if (Build.VERSION.SDK_INT < 29) "[not supported] Android below 10" else {  // i18n-ignore
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            val wi = caps?.transportInfo as? android.net.wifi.WifiInfo
            if (wi == null) "[not observed] active network is not Wi-Fi (or Android did not expose it)" else  // i18n-ignore
                "[device reported] signal ${caps.signalStrength} dBm · link ${if (wi.linkSpeed > 0) "${wi.linkSpeed} Mbit/s" else "unavailable"} · band ${if (wi.frequency > 0) "${wi.frequency} MHz" else "unavailable"}"  // i18n-ignore
        }
    } catch (_: Throwable) { "[unavailable]" }  // i18n-ignore
    fun ipFamilies(ctx: Context): String = try {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val lp = cm.getLinkProperties(cm.activeNetwork)
        if (lp == null) "[unavailable]" else {  // i18n-ignore
            val a = lp.linkAddresses.map { it.address }
            "[device reported] IPv4 address " + (if (a.any { it is java.net.Inet4Address }) "present" else "absent") + " · IPv6 global address " +  // i18n-ignore
                (if (a.any { it is java.net.Inet6Address && !it.isLinkLocalAddress }) "present" else "absent") + " · which family each request used: [unavailable]"  // i18n-ignore
        }
    } catch (_: Throwable) { "[unavailable]" }  // i18n-ignore

    // ------------------------------------------------------------------ playback type of the full-screen player (set by PlayerScreen)
    /** "tv" / "movie" / "series" / "catchup"; the small picture is always "preview" */
    @Volatile var fullType = "tv"  // i18n-ignore
    @Volatile var subExternal = false
    @Volatile var playingNow = false

    // ------------------------------------------------------------------ section 51: stream connection lifecycle
    private val activeLoads = AtomicInteger(0)
    val peakLoads = AtomicInteger(0)
    private val activeByPlayer = ConcurrentHashMap<String, AtomicInteger>()
    @Volatile private var overlapFrom = 0L
    @Volatile private var lastCloseAt = 0L
    @Volatile private var lastCloseKind = ""  // i18n-ignore
    @Volatile private var lastClosePlayer = ""  // i18n-ignore
    private fun othersActive(id: String) = activeByPlayer.any { (k, v) -> k != id && v.get() > 0 }
    private fun loadOpened(id: String, kind: String) {
        val n = activeLoads.incrementAndGet(); peakLoads.accumulateAndGet(n, ::maxOf)
        val mine = activeByPlayer.getOrPut(id) { AtomicInteger() }.incrementAndGet()
        if (mine == 1) {
            if (othersActive(id)) {
                if (overlapFrom == 0L) { overlapFrom = now(); inc("overlapping stream requests (two players loading at once)")  // i18n-ignore
                    DiagCollect.limited("overlap", 5_000, "stream", "overlap: $kind started loading while another player was still loading · " + DiagCollect.playState()) }  // i18n-ignore
            } else if (lastCloseAt > 0 && lastClosePlayer != id) {
                val gap = now() - lastCloseAt
                if (gap in 0..60_000) { ring("gap close→next open").add(gap); if (lastCloseKind == "preview" && kind != "preview") ring("transition preview→fullscreen gap").add(gap)  // i18n-ignore
                    if (lastCloseKind != "preview" && kind == "preview") ring("transition fullscreen→preview gap").add(gap) }  // i18n-ignore
                lastCloseAt = 0
            }
        }
    }
    private fun loadClosed(id: String, kind: String) {
        activeLoads.updateAndGet { (it - 1).coerceAtLeast(0) }
        val mine = activeByPlayer[id]?.updateAndGet { (it - 1).coerceAtLeast(0) } ?: 0
        if (mine == 0) {
            if (overlapFrom > 0 && activeByPlayer.count { it.value.get() > 0 } <= 1) { val d = now() - overlapFrom; overlapFrom = 0; ring("overlap duration").add(d); PerfLog.rowII("overlap", listOf(d, kind)) }  // i18n-ignore
            lastCloseAt = now(); lastCloseKind = kind; lastClosePlayer = id
        }
    }
    private fun playerGone(id: String, kind: String) {
        val left = activeByPlayer.remove(id)?.get() ?: 0
        if (left > 0) { activeLoads.updateAndGet { (it - left).coerceAtLeast(0) }; inc("stream requests still open at player release (closed by release)", left.toLong()) }  // i18n-ignore
        if (overlapFrom > 0 && activeByPlayer.count { it.value.get() > 0 } <= 1) { val d = now() - overlapFrom; overlapFrom = 0; ring("overlap duration").add(d); PerfLog.rowII("overlap", listOf(d, kind)) }  // i18n-ignore
        lastCloseAt = now(); lastCloseKind = kind; lastClosePlayer = id
    }
    /** provider protection hooks ([ProbeGuard]) */
    fun cooldown(why: String, ms: Long) = g("stream") { inc("provider cooldown activations"); inc("provider cooldown: $why"); ring("provider cooldown duration", 100).add(ms)  // i18n-ignore
        DiagEvents.add("stream", "provider cooldown started: $why · ${ms / 1000} s · expires ${DiagEvents.stamp(System.currentTimeMillis() + ms)}") }  // i18n-ignore
    fun httpRestriction(code: Int, kind: String) = g("stream") { inc("HTTP $code restriction events"); DiagCollect.limited("restr$code", 10_000, "stream", "HTTP $code from the provider · $kind · " + netCfg()) }  // i18n-ignore
    private val requestSeq = AtomicLong(0)

    // ------------------------------------------------------------------ the per-player observer (sections 51, 54, 55, 56, 57, 58)
    private class Sess(val id: String, val preview: Boolean, val fixed: String?) {
        fun type() = fixed ?: if (preview) "preview" else fullType  // i18n-ignore
        var streamSeq = 0
        var streamId = ""  // i18n-ignore
        var streamType = "tv"  // i18n-ignore
        var streamNet = "unknown"  // i18n-ignore
        var streamVpnStart = false
        var openReason = "start"  // i18n-ignore
        var openedAt = 0L
        var readyOnce = false
        var everReady = false
        var state = Player.STATE_IDLE
        var bufFrom = 0L
        var seekAt = 0L
        var playFrom = 0L
        var pauseFrom = 0L
        var playMs = 0L
        var pauseMs = 0L
        var initMs = -1L
        var rebufN = 0; var rebufMs = 0L; var rebufMax = 0L; var seekBufN = 0
        var dropped = 0L; var rendered = 0L
        var errors = 0
        var lastErrorAt = 0L
        var lastErrorClass = ""  // i18n-ignore
        var recovering = false
        var recovOk = false
        var recovMs = -1L
        var lastLoadErrAt = 0L
        var lastDecoderErrAt = 0L
        var throughput = 0L
        var bitrate = 0L
        // buffer (section 55)
        var bufSampleAt = 0L
        var bufLast = -1L
        var bufMin = Long.MAX_VALUE; var bufMax = 0L; var bufWeighted = 0.0; var bufWeightMs = 0L
        // requests (sections 51, 52)
        val loads = HashMap<Long, Long>()
        var req = 0; var done = 0; var canceled = 0; var failed = 0; var bytes = 0L; var redirects = 0
        val durs = ArrayList<Long>(); val kbps = ArrayList<Long>()
        val codes = HashMap<Int, Int>()
        // decoders (section 56)
        var videoDecoder = ""; var videoHw = ""; var decInits = 0  // i18n-ignore
        var lastV: Format? = null
        var lastAudioLang: String? = null; var lastAudioMime: String? = null
        var maxDropBurst = 0
    }

    fun listener(player: ExoPlayer, preview: Boolean, fixedKind: String? = null): AnalyticsListener {
        val s = Sess(java.util.UUID.randomUUID().toString().take(6), preview, fixedKind)
        val pkind = if (preview) "preview" else "fullscreen"  // i18n-ignore
        memAtCreate(pkind)
        return object : AnalyticsListener {
            fun sampleBuffer(force: Boolean = false) = g("buffer") {  // i18n-ignore
                val t = now(); if (!force && t - s.bufSampleAt < 2_000) return@g
                val b = try { player.totalBufferedDuration } catch (_: Throwable) { return@g }
                if (s.bufLast >= 0 && s.bufSampleAt > 0) { val w = t - s.bufSampleAt; s.bufWeighted += s.bufLast.toDouble() * w; s.bufWeightMs += w }
                s.bufSampleAt = t; s.bufLast = b
                if (b < s.bufMin) s.bufMin = b; if (b > s.bufMax) s.bufMax = b
                ring("buffer ms ${s.type()}", 300).add(b)  // i18n-ignore
            }
            fun closeStream(reason: String) = g("qoe") {  // i18n-ignore
                if (s.openedAt == 0L) return@g
                val t = now()
                if (s.playFrom > 0) { s.playMs += t - s.playFrom; s.playFrom = 0 }
                if (s.pauseFrom > 0) { s.pauseMs += t - s.pauseFrom; s.pauseFrom = 0 }
                val outcome = when { s.everReady && s.errors == 0 -> "ok"; s.everReady && s.recovOk -> "ok after recovery"; s.everReady -> "ended after error"; s.errors > 0 -> "failed start"; else -> "closed before start" }  // i18n-ignore
                inc("qoe ${s.streamType} streams: $outcome")  // i18n-ignore
                if (!s.everReady && s.errors > 0) inc("qoe ${s.streamType} failed starts")  // i18n-ignore
                if (s.everReady && s.errors > 0 && !s.recovOk && reason != "switch") inc("qoe ${s.streamType} unexpected terminations")  // i18n-ignore
                PerfLog.rowII("qoe", listOf(s.streamType, s.streamNet, outcome, s.initMs.takeIf { it >= 0 } ?: "", s.playMs, s.pauseMs, s.rebufN, s.rebufMs, s.rebufMax,  // i18n-ignore
                    s.seekBufN, s.dropped, s.rendered, s.errors, if (s.openReason == "recovery") 1 else 0, if (s.recovOk) 1 else 0, s.recovMs.takeIf { it >= 0 } ?: "", reason))  // i18n-ignore
                val bufMean = if (s.bufWeightMs > 0) (s.bufWeighted / s.bufWeightMs).toLong() else -1
                if (s.bufMin != Long.MAX_VALUE) PerfLog.rowII("buffer", listOf(s.streamType, s.bufMin, s.bufMax, bufMean, s.bitrate / 1000, s.throughput / 1000, s.streamNet))  // i18n-ignore
                if (s.req > 0) PerfLog.rowII("streams", listOf(s.streamType, s.streamNet, s.req, s.done, s.canceled, s.failed, s.bytes / 1024,  // i18n-ignore
                    pct(s.durs, .5), pct(s.durs, .95), pct(s.kbps, .5), s.redirects, s.codes.entries.joinToString(" ") { "${it.key}x${it.value}" }, s.streamId, reason))  // i18n-ignore
                s.openedAt = 0
            }
            fun openStream(reason: String) = g("qoe") {  // i18n-ignore
                closeStream(if (reason == "recovery") "recovery" else "switch")  // i18n-ignore
                val t = now()
                s.streamSeq++; s.streamId = s.id + "-" + s.streamSeq; s.streamType = s.type(); s.streamNet = netCfg(); s.streamVpnStart = vpnOn()  // i18n-ignore
                s.openReason = reason; s.openedAt = t; s.readyOnce = false; s.everReady = false; s.bufFrom = 0; s.playFrom = 0; s.pauseFrom = 0
                s.playMs = 0; s.pauseMs = 0; s.initMs = -1; s.rebufN = 0; s.rebufMs = 0; s.rebufMax = 0; s.seekBufN = 0; s.dropped = 0; s.rendered = 0
                s.errors = 0; s.recovering = reason == "recovery"; s.recovOk = false; s.recovMs = -1  // i18n-ignore
                s.bufMin = Long.MAX_VALUE; s.bufMax = 0; s.bufWeighted = 0.0; s.bufWeightMs = 0; s.bufLast = -1; s.bufSampleAt = 0
                s.req = 0; s.done = 0; s.canceled = 0; s.failed = 0; s.bytes = 0; s.redirects = 0; s.durs.clear(); s.kbps.clear(); s.codes.clear()
                inc("logical streams opened (${s.streamType}, reason $reason)")  // i18n-ignore
                if (reason == "recovery") inc("qoe ${s.streamType} recovery attempts")  // i18n-ignore
                inc("qoe ${s.streamType} sessions")  // i18n-ignore
            }
            override fun onMediaItemTransition(t: AnalyticsListener.EventTime, item: androidx.media3.common.MediaItem?, reason: Int) = g("qoe") {  // i18n-ignore
                val r = if (s.lastErrorAt > 0 && now() - s.lastErrorAt < 30_000) "recovery" else if (s.streamSeq == 0) "start" else "switch"  // i18n-ignore
                openStream(r)
            }
            override fun onPlaybackStateChanged(t: AnalyticsListener.EventTime, state: Int) = g("qoe") {  // i18n-ignore
                val tt = now(); val was = s.state; s.state = state
                if (s.openedAt == 0L && state == Player.STATE_BUFFERING) openStream(if (s.lastErrorAt > 0 && tt - s.lastErrorAt < 30_000) "recovery" else "reopen")  // i18n-ignore
                if (state == Player.STATE_BUFFERING && was != Player.STATE_BUFFERING) {
                    s.bufFrom = tt; sampleBuffer(true)
                    if (s.playFrom > 0) { s.playMs += tt - s.playFrom; s.playFrom = 0 }
                }
                if (was == Player.STATE_BUFFERING && state != Player.STATE_BUFFERING && s.bufFrom > 0) {
                    val d = tt - s.bufFrom; s.bufFrom = 0
                    if (!s.readyOnce) { if (state == Player.STATE_READY) s.initMs = tt - s.openedAt }
                    else if (s.seekAt > 0 && s.bufFrom == 0L && tt - s.seekAt < d + 2_000) { s.seekBufN++; s.seekAt = 0; inc("qoe ${s.streamType} buffering after a seek (not counted as rebuffering)") }  // i18n-ignore
                    else {
                        s.rebufN++; s.rebufMs += d; if (d > s.rebufMax) s.rebufMax = d
                        val cause = suspectedCause(s, tt)
                        inc("rebuffering suspected cause: $cause"); PerfLog.rowII("rebuf", listOf(s.streamType, s.streamNet, d, cause))  // i18n-ignore
                        ring("rebuffer ms ${s.streamType}", 300).add(d)  // i18n-ignore
                    }
                }
                if (state == Player.STATE_READY) {
                    sampleBuffer(true)
                    if (!s.readyOnce) { s.readyOnce = true; s.everReady = true; if (s.initMs < 0) s.initMs = tt - s.openedAt }
                    if (s.recovering) { s.recovering = false; s.recovOk = true; s.recovMs = if (s.lastErrorAt > 0) tt - s.lastErrorAt else -1; inc("qoe ${s.streamType} recovery successes"); s.recovMs.takeIf { it >= 0 }?.let { ring("recovery ms ${s.streamType}", 200).add(it) } }  // i18n-ignore
                    if (player.playWhenReady) { if (s.playFrom == 0L) s.playFrom = tt } else if (s.pauseFrom == 0L) s.pauseFrom = tt
                }
                if (state == Player.STATE_ENDED || state == Player.STATE_IDLE) { if (s.playFrom > 0) { s.playMs += tt - s.playFrom; s.playFrom = 0 }; if (s.pauseFrom > 0) { s.pauseMs += tt - s.pauseFrom; s.pauseFrom = 0 } }
            }
            override fun onIsPlayingChanged(t: AnalyticsListener.EventTime, isPlaying: Boolean) = g("qoe") {  // i18n-ignore
                if (!preview && s.fixed == null) playingNow = isPlaying
                val tt = now()
                if (isPlaying) { if (s.playFrom == 0L) s.playFrom = tt; if (s.pauseFrom > 0) { s.pauseMs += tt - s.pauseFrom; s.pauseFrom = 0 } }
                else { if (s.playFrom > 0) { s.playMs += tt - s.playFrom; s.playFrom = 0 }; if (s.state == Player.STATE_READY && !player.playWhenReady && s.pauseFrom == 0L) s.pauseFrom = tt }
            }
            override fun onPlayWhenReadyChanged(t: AnalyticsListener.EventTime, playWhenReady: Boolean, reason: Int) = g("audio2") {  // i18n-ignore
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) inc("audio focus lost (playback paused by Android)")  // i18n-ignore
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY) inc("audio output became unavailable (paused)")  // i18n-ignore
            }
            override fun onPlaybackSuppressionReasonChanged(t: AnalyticsListener.EventTime, r: Int) = g("audio2") {  // i18n-ignore
                if (r == Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS) inc("audio focus lost for a moment (transient)")  // i18n-ignore
                if (r == Player.PLAYBACK_SUPPRESSION_REASON_NONE) inc("audio playback suppression ended (focus regained or route back)")  // i18n-ignore
            }
            override fun onPositionDiscontinuity(t: AnalyticsListener.EventTime, o: Player.PositionInfo, n: Player.PositionInfo, reason: Int) = g("qoe") {  // i18n-ignore
                if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) s.seekAt = now()
            }
            // ---- requests
            override fun onLoadStarted(t: AnalyticsListener.EventTime, l: LoadEventInfo, d: MediaLoadData, retryCount: Int) = g("stream") {  // i18n-ignore
                if (s.openedAt == 0L) openStream(if (s.lastErrorAt > 0 && now() - s.lastErrorAt < 30_000) "recovery" else "reopen")  // i18n-ignore
                if (retryCount > 0) inc("stream request retries by Media3 (${s.type()})")  // i18n-ignore
                if (s.loads.containsKey(l.loadTaskId)) return@g
                if (s.loads.size < 64) s.loads[l.loadTaskId] = now()
                s.req++; requestSeq.incrementAndGet(); inc("stream requests started (${s.type()})")  // i18n-ignore
                loadOpened(s.id, s.type())
            }
            private fun ended(l: LoadEventInfo, d: MediaLoadData, what: String) {
                if (s.loads.remove(l.loadTaskId) == null) return
                loadClosed(s.id, s.type())
                if (l.uri != l.dataSpec.uri) { s.redirects++; inc("stream redirects (${s.type()})") }  // i18n-ignore
                inc("stream requests $what (${s.type()})")  // i18n-ignore
                if (d.trackType == C.TRACK_TYPE_TEXT && what == "failed") inc("subtitle loading failures (embedded/side-loaded track)")  // i18n-ignore
            }
            override fun onLoadCompleted(t: AnalyticsListener.EventTime, l: LoadEventInfo, d: MediaLoadData) = g("stream") {  // i18n-ignore
                ended(l, d, "completed"); s.done++  // i18n-ignore
                s.bytes += l.bytesLoaded
                if (s.durs.size < 2000) s.durs += l.loadDurationMs
                ring("request ms playback ${s.type()}", 500).add(l.loadDurationMs)  // i18n-ignore
                if (l.loadDurationMs >= 200 && l.bytesLoaded >= 65_536) { val k = l.bytesLoaded * 8 / l.loadDurationMs; if (s.kbps.size < 2000) s.kbps += k; ring("throughput kbit/s playback ${s.type()}", 500).add(k) }  // i18n-ignore
                inc("bytes received playback (${s.type()})", l.bytesLoaded)  // i18n-ignore
                sampleBuffer()
            }
            override fun onLoadCanceled(t: AnalyticsListener.EventTime, l: LoadEventInfo, d: MediaLoadData) = g("stream") { ended(l, d, "cancelled"); s.canceled++ }  // i18n-ignore
            override fun onLoadError(t: AnalyticsListener.EventTime, l: LoadEventInfo, d: MediaLoadData, e: java.io.IOException, wasCanceled: Boolean) = g("stream") {  // i18n-ignore
                s.lastLoadErrAt = now()
                val c = DiagCollect.classify(e)
                inc("stream request errors: $c")  // i18n-ignore
                val code = (e as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode
                if (code != null) { s.codes.merge(code, 1, Int::plus); if (code == 458 || code == 429 || code == 509) httpRestriction(code, s.type()) }
                if (wasCanceled) { ended(l, d, "failed"); s.failed++ }  // i18n-ignore
            }
            override fun onBandwidthEstimate(t: AnalyticsListener.EventTime, ms: Int, bytes: Long, est: Long) = g("buffer") { s.throughput = est }  // i18n-ignore
            override fun onDownstreamFormatChanged(t: AnalyticsListener.EventTime, d: MediaLoadData) = g("buffer") {  // i18n-ignore
                val f = d.trackFormat ?: return@g
                if (d.trackType == C.TRACK_TYPE_VIDEO || d.trackType == C.TRACK_TYPE_DEFAULT) { val b = if (f.bitrate > 0) f.bitrate.toLong() else f.averageBitrate.toLong(); if (b > 0) s.bitrate = b }
            }
            // ---- errors and recovery
            override fun onPlayerError(t: AnalyticsListener.EventTime, e: androidx.media3.common.PlaybackException) = g("qoe") {  // i18n-ignore
                val tt = now(); s.errors++; s.lastErrorAt = tt
                val c = DiagCollect.classify(e); s.lastErrorClass = c
                inc("qoe ${s.type()} playback errors")  // i18n-ignore
                inc("buffer at failure: " + when { s.bufLast < 0 -> "unknown"; s.bufLast < 500 -> "empty (<0.5 s)"; s.bufLast < 3_000 -> "low (<3 s)"; else -> "had data (≥3 s)" })  // i18n-ignore
                DiagEvents.add("qoe", "${s.type()} error $c · stream ${s.streamId} · buffer ${if (s.bufLast >= 0) "${s.bufLast} ms" else "unknown"} · ${netCfg()} · VPN at start ${if (s.streamVpnStart) "on" else "off"}, now ${if (vpnOn()) "on" else "off"}")  // i18n-ignore
                if (c.startsWith("decoder")) { s.lastDecoderErrAt = tt; decoderFailure(s, c) }  // i18n-ignore
                if (c.startsWith("HTTP 4") || c.startsWith("HTTP 5")) Regex("HTTP (\\d+)").find(c)?.groupValues?.get(1)?.toIntOrNull()?.let { if (it == 458 || it == 429 || it == 509) httpRestriction(it, s.type()) }  // i18n-ignore
                if (vpnOn() != s.streamVpnStart) inc("errors after a VPN state change during the stream")  // i18n-ignore
            }
            // ---- video and decoders (56)
            override fun onVideoDecoderInitialized(t: AnalyticsListener.EventTime, name: String, initAt: Long, initMs: Long) = g("decoder2") {  // i18n-ignore
                val hw = DiagCollect.hw(name); s.decInits++
                ring("video decoder init ms", 200).add(initMs)  // i18n-ignore
                if (s.decInits > 1) inc("video decoder re-initialisations")  // i18n-ignore
                if (s.videoDecoder.isNotEmpty() && s.videoDecoder != name) {
                    if (s.videoHw == "hardware" && hw == "software") { inc("decoder fallback hardware → software (observed)"); DiagEvents.add("codec", "decoder fallback ${s.videoDecoder} → $name (hardware → software) · ${s.type()}") }  // i18n-ignore
                    else if (s.videoHw == "software" && hw == "hardware") inc("decoder change software → hardware (observed)")  // i18n-ignore
                }
                s.videoDecoder = name; s.videoHw = hw
                inc("video decoder used: $name ($hw)")  // i18n-ignore
            }
            override fun onVideoDecoderReleased(t: AnalyticsListener.EventTime, name: String) = g("decoder2") { inc("video decoder releases") }  // i18n-ignore
            override fun onVideoInputFormatChanged(t: AnalyticsListener.EventTime, f: Format, r: androidx.media3.exoplayer.DecoderReuseEvaluation?) = g("decoder2") {  // i18n-ignore
                val o = s.lastV
                if (o != null) {
                    if (o.width != f.width || o.height != f.height) inc("video resolution changes")  // i18n-ignore
                    if (o.frameRate != f.frameRate) inc("video frame-rate changes")  // i18n-ignore
                    if (o.bitrate != f.bitrate) inc("video bitrate changes")  // i18n-ignore
                    if (o.sampleMimeType != f.sampleMimeType || o.codecs != f.codecs) inc("video codec changes")  // i18n-ignore
                    if (o.colorInfo?.colorTransfer != f.colorInfo?.colorTransfer) inc("video HDR / transfer changes")  // i18n-ignore
                }
                s.lastV = f
                if (f.colorInfo?.colorTransfer == 6 || f.colorInfo?.colorTransfer == 7) inc("streams with HDR metadata in the source (${hdr(f)})")  // i18n-ignore
            }
            override fun onDroppedVideoFrames(t: AnalyticsListener.EventTime, count: Int, elapsedMs: Long) = g("frames2") {  // i18n-ignore
                s.dropped += count; if (count > s.maxDropBurst) s.maxDropBurst = count
                if (count.toLong() > (DiagCollect.counters["max dropped frames in one report"]?.get() ?: 0L)) DiagCollect.counters.getOrPut("max dropped frames in one report") { AtomicLong() }.set(count.toLong())  // i18n-ignore
                inc("qoe ${s.type()} dropped frames", count.toLong())  // i18n-ignore
            }
            override fun onVideoFrameProcessingOffset(t: AnalyticsListener.EventTime, offsetUs: Long, frameCount: Int) = g("frames2") { s.rendered += frameCount; inc("qoe ${s.type()} rendered frames", frameCount.toLong()) }  // i18n-ignore
            override fun onRenderedFirstFrame(t: AnalyticsListener.EventTime, output: Any, renderTimeMs: Long) = g("stream") {  // i18n-ignore
                if (s.openedAt > 0 && s.streamSeq == 1 && !preview) ring("fullscreen first picture after open (ms)", 200).add(now() - s.openedAt)  // i18n-ignore
            }
            // ---- audio (57)
            override fun onAudioDecoderInitialized(t: AnalyticsListener.EventTime, name: String, initAt: Long, initMs: Long) = g("audio2") { ring("audio decoder init ms", 200).add(initMs); inc("audio decoder used: $name") }  // i18n-ignore
            override fun onAudioInputFormatChanged(t: AnalyticsListener.EventTime, f: Format, r: androidx.media3.exoplayer.DecoderReuseEvaluation?) = g("audio2") {  // i18n-ignore
                if (s.lastAudioMime != null && s.lastAudioMime != f.sampleMimeType) inc("audio track / codec changes")  // i18n-ignore
                if (s.lastAudioLang != null && s.lastAudioLang != f.language) inc("audio language changes")  // i18n-ignore
                s.lastAudioMime = f.sampleMimeType; s.lastAudioLang = f.language
                inc("audio source format: " + listOfNotNull(f.sampleMimeType, if (f.channelCount > 0) "${f.channelCount} ch" else null, if (f.sampleRate > 0) "${f.sampleRate} Hz" else null).joinToString(" · "))  // i18n-ignore
            }
            override fun onAudioTrackInitialized(t: AnalyticsListener.EventTime, c: androidx.media3.exoplayer.audio.AudioSink.AudioTrackConfig) = g("audio2") {  // i18n-ignore
                val enc = when (c.encoding) { C.ENCODING_PCM_16BIT -> "PCM 16-bit"; C.ENCODING_PCM_FLOAT -> "PCM float"; C.ENCODING_AC3 -> "AC-3 (bitstream)"; C.ENCODING_E_AC3 -> "E-AC-3 (bitstream)"  // i18n-ignore
                    C.ENCODING_DTS -> "DTS (bitstream)"; C.ENCODING_E_AC3_JOC -> "E-AC-3 JOC (bitstream)"; C.ENCODING_DTS_HD -> "DTS-HD (bitstream)"; C.ENCODING_DOLBY_TRUEHD -> "TrueHD (bitstream)"; else -> "encoding ${c.encoding}" }  // i18n-ignore
                inc("audio output configured: $enc · ${c.sampleRate} Hz" + (if (c.tunneling) " · tunneling" else "") + (if (c.offload) " · offload" else ""))  // i18n-ignore
            }
            override fun onAudioUnderrun(t: AnalyticsListener.EventTime, size: Int, sizeMs: Long, sinceFeedMs: Long) = g("audio2") { ring("audio underrun: ms since last data", 200).add(sinceFeedMs) }  // i18n-ignore
            override fun onAudioCodecError(t: AnalyticsListener.EventTime, e: Exception) = g("audio2") { inc("audio decoder error: " + DiagCollect.classify(e)) }  // i18n-ignore
            // ---- subtitles (58)
            override fun onTracksChanged(t: AnalyticsListener.EventTime, tracks: androidx.media3.common.Tracks) = g("subs2") {  // i18n-ignore
                val text = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                if (text.isEmpty()) return@g
                var n = 0
                text.forEach { gr -> for (i in 0 until gr.length) { n++; inc("subtitle formats offered: " + (gr.getTrackFormat(i).sampleMimeType ?: "unknown")) } }  // i18n-ignore
                ring("subtitle tracks per stream", 200).add(n.toLong())  // i18n-ignore
                val sel = text.firstOrNull { it.isSelected }
                if (sel != null) {
                    val f = (0 until sel.length).firstOrNull { sel.isTrackSelected(it) }?.let { sel.getTrackFormat(it) }
                    inc("subtitle selected: " + (f?.sampleMimeType ?: "unknown") + " · " + (f?.language ?: "language not given") + " · " + if (subExternal) "external (downloaded/side-loaded)" else "embedded (from the stream)")  // i18n-ignore
                }
            }
            override fun onPlayerReleased(t: AnalyticsListener.EventTime) = g("stream") {  // i18n-ignore
                closeStream("player released"); playerGone(s.id, s.type()); if (!preview && s.fixed == null) playingNow = false  // i18n-ignore
                memAtRelease(pkind)
            }
        }
    }
    private fun hdr(f: Format) = when (f.colorInfo?.colorTransfer) { 6 -> "PQ/HDR10"; 7 -> "HLG"; else -> "SDR" }  // i18n-ignore
    private fun suspectedCause(s: Sess, t: Long): String = when {
        lastNetChange > 0 && t - lastNetChange < 15_000 -> "network transition shortly before"  // i18n-ignore
        s.lastLoadErrAt > 0 && t - s.lastLoadErrAt < 15_000 -> "request errors shortly before (incoming data)"  // i18n-ignore
        s.lastDecoderErrAt > 0 && t - s.lastDecoderErrAt < 15_000 -> "decoder error shortly before"  // i18n-ignore
        s.bitrate > 0 && s.throughput > 0 && s.throughput < s.bitrate * 12 / 10 -> "throughput close to or below the stream bitrate"  // i18n-ignore
        s.bufLast in 0..999 -> "buffer depleted (cause not identified)"  // i18n-ignore
        else -> "not identified"  // i18n-ignore
    }
    private fun decoderFailure(s: Sess, cls: String) = g("decoder2") {  // i18n-ignore
        val f = s.lastV
        PerfLog.rowII("decfail", listOf(s.type(), cls, f?.sampleMimeType ?: "", f?.codecs ?: "", if (f != null && f.width > 0) "${f.width}x${f.height}" else "",  // i18n-ignore
            if (f != null && f.frameRate > 0) String.format(java.util.Locale.US, "%.2f", f.frameRate) else "", f?.let { hdr(it) } ?: "", s.videoDecoder, s.videoHw,  // i18n-ignore
            Build.MANUFACTURER + " " + Build.MODEL, Build.VERSION.SDK_INT, Build.DISPLAY))  // i18n-ignore
    }

    // ------------------------------------------------------------------ section 61: memory around the player and over time
    private class MemSnap(val heap: Long, val native: Long)
    private fun snap(): MemSnap { val rt = Runtime.getRuntime(); return MemSnap((rt.totalMemory() - rt.freeMemory()) / 1024, android.os.Debug.getNativeHeapAllocatedSize() / 1024) }
    private val main by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }
    private fun memAtCreate(kind: String) = g("mem2") { val m = snap(); ring("heap KB before $kind player creation", 100).add(m.heap); ring("native KB before $kind player creation", 100).add(m.native)  // i18n-ignore
        main.postDelayed({ g("mem2") { val a = snap(); ring("heap KB delta 5 s after $kind player creation", 100).add(a.heap - m.heap); ring("native KB delta 5 s after $kind player creation", 100).add(a.native - m.native) } }, 5_000) }  // i18n-ignore
    private fun memAtRelease(kind: String) = g("mem2") { val m = snap()  // i18n-ignore
        main.postDelayed({ g("mem2") { val a = snap(); ring("heap KB delta 10 s after $kind player release", 100).add(a.heap - m.heap); ring("native KB delta 10 s after $kind player release", 100).add(a.native - m.native) } }, 10_000) }  // i18n-ignore

    private fun procStatus(key: String): Long = try { java.io.File("/proc/self/status").useLines { l -> l.firstOrNull { it.startsWith("$key:") }?.substringAfter(':')?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.toLongOrNull() ?: -1 } } catch (_: Throwable) { -1 }  // i18n-ignore
    private fun fdCount(): Int = try { java.io.File("/proc/self/fd").list()?.size ?: -1 } catch (_: Throwable) { -1 }  // i18n-ignore
    private fun gc(k: String): Long = try { if (Build.VERSION.SDK_INT >= 23) android.os.Debug.getRuntimeStat(k)?.toLongOrNull() ?: -1 else -1 } catch (_: Throwable) { -1 }
    @Volatile var appCtx: Context? = null
    /** every 5th minute from [Monitor] (background thread), with the PSS the monitor just took; graphics only every 30 min (expensive) */
    fun memSample(tick: Int, pssMb: Int) = g("mem2") {  // i18n-ignore
        val ctx = appCtx ?: return@g
        val rt = Runtime.getRuntime()
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo(); am.getMemoryInfo(mi)
        val gfx: Long = if (tick % 30 == 1 && Build.VERSION.SDK_INT >= 23) try { val d = android.os.Debug.MemoryInfo(); android.os.Debug.getMemoryInfo(d); d.getMemoryStat("summary.graphics")?.toLongOrNull()?.div(1024) ?: -1L } catch (_: Throwable) { -1L } else -1L  // i18n-ignore
        val cache: Long = try { coil.Coil.imageLoader(ctx).memoryCache?.size?.toLong()?.div(1_048_576) ?: -1L } catch (_: Throwable) { -1L }
        val upMin = (SystemClock.elapsedRealtime() - android.os.Process.getStartElapsedRealtime()) / 60_000
        PerfLog.rowII("mem", listOf(Work.state().name.lowercase(), (rt.totalMemory() - rt.freeMemory()) / 1_048_576, rt.totalMemory() / 1_048_576, rt.maxMemory() / 1_048_576,  // i18n-ignore
            android.os.Debug.getNativeHeapAllocatedSize() / 1_048_576, pssMb, procStatus("VmRSS").let { if (it > 0) it / 1024 else "" }, if (gfx >= 0) gfx else "",  // i18n-ignore
            procStatus("Threads").let { if (it > 0) it else "" }, fdCount().let { if (it >= 0) it else "" }, gc("art.gc.gc-count").let { if (it >= 0) it else "" },  // i18n-ignore
            gc("art.gc.gc-time").let { if (it >= 0) it else "" }, mi.availMem / 1_048_576, if (mi.lowMemory) 1 else 0, DiagCollect.playersLive.get(), upMin, if (cache >= 0) cache else "",  // i18n-ignore
            DiagStore.totalBytes() / 1024))
    }
    /** the image-loader timings since the last flush, as one summary row per category (history for section 68) */
    fun flushImages() = g("images2") {  // i18n-ignore
        rings("img pending ").forEach { (k, r) -> val l = r.drain(); if (l.isNotEmpty()) PerfLog.rowII("imgagg", listOf(k.removePrefix("img pending "), l.size, pct(l, .5), pct(l, .95), l.max())) }  // i18n-ignore
    }
    fun trim(level: Int) = g("mem2") { inc("Android memory trim requests (level $level)") }  // i18n-ignore

    // ------------------------------------------------------------------ section 52: the app's own non-playback requests
    /** category of an existing request, from its host or path only (the URL itself is never stored) */
    fun category(url: String): String {
        val u = url.lowercase()
        return when {
            u.contains("xmltv.php") || u.contains("get_short_epg") || u.contains("get_simple_data_table") || u.contains("epg") -> "EPG"  // i18n-ignore
            u.contains("player_api.php") || u.contains("get.php") || u.contains(".m3u") || u.contains("portal.php") || u.contains("stalker") -> "playlist / provider API"  // i18n-ignore
            u.contains("github") -> "application updates"  // i18n-ignore
            u.contains("themoviedb") || u.contains("tmdb") || u.contains("omdbapi") -> "metadata (TMDB/OMDb)"  // i18n-ignore
            u.contains("opensubtitles") || u.contains("subdl") || u.contains("podnapisi") -> "subtitles"  // i18n-ignore
            u.contains("open-meteo") || u.contains("weather") -> "weather"  // i18n-ignore
            u.contains("sport") || u.contains("football") -> "sports data"  // i18n-ignore
            else -> "other"  // i18n-ignore
        }
    }
    /** one finished request of the HttpURLConnection helpers ([Net.openLen], [Http]): DNS/TCP/TLS are not exposed there */
    fun httpDone(url: String, code: Int, totalMs: Long, ttfbMs: Long, bytes: Long, err: Throwable?) = g("net3") {  // i18n-ignore
        val c = category(url)
        val e = err?.let { DiagCollect.classify(it) } ?: ""  // i18n-ignore
        if (code > 0) inc("http $c status $code")  // i18n-ignore
        if (err != null) inc("http $c error: $e")  // i18n-ignore
        ring("http total ms $c").add(totalMs); if (ttfbMs >= 0) ring("http ttfb ms $c").add(ttfbMs)  // i18n-ignore
        if (bytes > 0) inc("http $c bytes", bytes)  // i18n-ignore
        if (bytes >= 65_536 && totalMs >= 200) ring("http kbit/s $c").add(bytes * 8 / totalMs)  // i18n-ignore
        if (c != "other" || Math.random() < 0.2) PerfLog.rowII("net", listOf(c, if (code > 0) code else "", totalMs, if (ttfbMs >= 0) ttfbMs else "", bytes, e, netCfg()))  // i18n-ignore
        if (code == 458 || code == 429 || code == 509) httpRestriction(code, c)
    }
    fun redirect(url: String) = g("net3") { inc("http ${category(url)} redirects") }  // i18n-ignore
    /** stream wrapper that counts bytes read (the body is read exactly as before) */
    class Counting(private val inner: java.io.InputStream) : java.io.FilterInputStream(inner) {
        var n = 0L
        override fun read(): Int { val r = super.read(); if (r >= 0) n++; return r }
        override fun read(b: ByteArray, off: Int, len: Int): Int { val r = super.read(b, off, len); if (r > 0) n += r; return r }
    }

    /** OkHttp phases for the image loader's own requests (posters, backdrops, logos): DNS, TCP, TLS, first byte */
    val okEvents: okhttp3.EventListener.Factory = object : okhttp3.EventListener.Factory {
        override fun create(call: okhttp3.Call): okhttp3.EventListener = object : okhttp3.EventListener() {
            val t0 = System.nanoTime(); var dns = 0L; var conn = 0L; var tls = 0L; var reqEnd = 0L; var bytes = 0L; var reused = true
            fun ms(a: Long, b: Long) = (b - a) / 1_000_000
            override fun dnsStart(call: okhttp3.Call, domainName: String) { dns = System.nanoTime(); reused = false }
            override fun dnsEnd(call: okhttp3.Call, domainName: String, inetAddressList: List<java.net.InetAddress>) { g("okhttp") { ring("images DNS ms").add(ms(dns, System.nanoTime())) } }  // i18n-ignore
            override fun connectStart(call: okhttp3.Call, inetSocketAddress: java.net.InetSocketAddress, proxy: java.net.Proxy) { conn = System.nanoTime(); reused = false
                g("okhttp") { inc("images connections over " + if (inetSocketAddress.address is java.net.Inet6Address) "IPv6" else "IPv4") } }  // i18n-ignore
            override fun secureConnectStart(call: okhttp3.Call) { tls = System.nanoTime() }
            override fun secureConnectEnd(call: okhttp3.Call, handshake: okhttp3.Handshake?) { g("okhttp") { ring("images TLS ms").add(ms(tls, System.nanoTime())) } }  // i18n-ignore
            override fun connectEnd(call: okhttp3.Call, inetSocketAddress: java.net.InetSocketAddress, proxy: java.net.Proxy, protocol: okhttp3.Protocol?) { g("okhttp") { ring("images TCP+TLS connect ms").add(ms(conn, System.nanoTime())) } }  // i18n-ignore
            override fun requestHeadersEnd(call: okhttp3.Call, request: okhttp3.Request) { reqEnd = System.nanoTime() }
            override fun responseHeadersStart(call: okhttp3.Call) { g("okhttp") { if (reqEnd > 0) ring("images TTFB ms").add(ms(reqEnd, System.nanoTime())) } }  // i18n-ignore
            override fun responseHeadersEnd(call: okhttp3.Call, response: okhttp3.Response) { g("okhttp") { inc("http images status ${response.code}") } }  // i18n-ignore
            override fun responseBodyEnd(call: okhttp3.Call, byteCount: Long) { bytes = byteCount }
            override fun callEnd(call: okhttp3.Call) { g("okhttp") { ring("http total ms images").add(ms(t0, System.nanoTime())); if (bytes > 0) inc("http images bytes", bytes); if (reused) inc("images requests on a reused connection") } }  // i18n-ignore
            override fun callFailed(call: okhttp3.Call, ioe: java.io.IOException) { g("okhttp") { inc("http images error: " + DiagCollect.classify(ioe)) } }  // i18n-ignore
        }
    }

    // ------------------------------------------------------------------ section 59: frames (FrameMetrics, API 24+)
    private class Screen(val name: String) {
        val hist = IntArray(501)  // 2 ms buckets up to 1 s
        var frames = 0L; var slow = 0L; var janky = 0L; var frozen = 0L; var max = 0L
        fun add(ms: Double, deadline: Double) {
            frames++; if (ms > deadline) slow++; if (ms > deadline * 2) janky++; if (ms > 700) frozen++
            val l = ms.toLong(); if (l > max) max = l; hist[(l / 2).toInt().coerceIn(0, 500)]++
        }
        fun p(q: Double): Long { if (frames == 0L) return 0; var acc = 0L; val target = (frames * q).toLong(); for (i in hist.indices) { acc += hist[i]; if (acc >= target) return i * 2L }; return 1000 }
    }
    private val screens = ConcurrentHashMap<String, Screen>()
    private val visit = arrayOfNulls<Screen>(1)
    @Volatile private var refreshHz = 60.0
    @Volatile var navNanos = 0L
    @Volatile var navComposedNanos = 0L
    @Volatile private var navFor = ""  // i18n-ignore
    @Volatile var keyNanos = 0L
    @Volatile var firstFrameMs = 0L
    private val framesOn = java.util.Collections.newSetFromMap(java.util.WeakHashMap<android.app.Activity, Boolean>())
    fun attachFrames(a: android.app.Activity) = g("frames") {  // i18n-ignore
        if (!synchronized(framesOn) { framesOn.add(a) }) return@g  // 3.0.0.8 (audit): one listener per window, never twice
        if (Build.VERSION.SDK_INT < 24) { inc("frame timing: not supported below Android 7"); return@g }  // i18n-ignore
        refreshHz = try { @Suppress("DEPRECATION") a.windowManager.defaultDisplay.refreshRate.toDouble().takeIf { it > 1 } ?: 60.0 } catch (_: Throwable) { 60.0 }  // i18n-ignore
        val ht = android.os.HandlerThread("pretorian-frames", android.os.Process.THREAD_PRIORITY_LOWEST).apply { start() }  // i18n-ignore
        a.window.addOnFrameMetricsAvailableListener({ _, m, _ -> g("frames") { frame(m) } }, android.os.Handler(ht.looper))  // i18n-ignore
    }
    private fun frame(m: android.view.FrameMetrics) {
        val total = m.getMetric(android.view.FrameMetrics.TOTAL_DURATION) / 1_000_000.0
        val deadline = 1000.0 / refreshHz
        // 3.0.0.8 (audit): frames of the first 15 s after the process started are app start-up work
        // (catalog load, first composition), not the Home screen's own drawing; counted under "startup"
        val sinceStart = if (Build.VERSION.SDK_INT >= 24) android.os.SystemClock.elapsedRealtime() - android.os.Process.getStartElapsedRealtime() else -1L
        val name = if (sinceStart in 0 until 15_000) "startup (first 15 s)" else CrashLog.screen.ifBlank { "unknown" }  // i18n-ignore
        if (firstFrameMs == 0L && sinceStart >= 0) { firstFrameMs = sinceStart; ring("process start to first drawn frame ms", 50).add(sinceStart) }  // i18n-ignore
        var v = visit[0]
        if (v == null || v.name != name) { v?.let { flushVisit(it) }; v = Screen(name); visit[0] = v }
        v.add(total, deadline)
        screens.getOrPut(name) { Screen(name) }.add(total, deadline)
        if (Build.VERSION.SDK_INT >= 26) {
            val end = m.getMetric(android.view.FrameMetrics.VSYNC_TIMESTAMP) + m.getMetric(android.view.FrameMetrics.TOTAL_DURATION)
            if (navComposedNanos > 0 && end >= navComposedNanos && navNanos > 0) { ring("first drawn frame after navigation ms $navFor", 200).add((end - navNanos) / 1_000_000); navComposedNanos = 0 }  // i18n-ignore
            if (keyNanos > 0 && end >= keyNanos) { ring("remote key to next completed frame ms", 500).add((end - keyNanos) / 1_000_000); keyNanos = 0 }  // i18n-ignore
        }
    }
    private fun flushVisit(v: Screen) { if (v.frames >= 10) PerfLog.rowII("jank", listOf(v.name, v.frames, v.slow, v.janky, v.frozen, v.p(.5), v.p(.95), v.max, String.format(java.util.Locale.US, "%.1f", refreshHz))) }  // i18n-ignore
    fun navComposed(screen: String) { navFor = screen; navComposedNanos = System.nanoTime() }
    fun navRequested() { navNanos = System.nanoTime() }
    fun key(down: Boolean) { if (down && keyNanos == 0L) keyNanos = System.nanoTime() }
    fun refresh() = refreshHz

    // ------------------------------------------------------------------ section 60: remote control and focus
    fun focusResult(ok: Boolean, attempts: Int) = g("focus") {  // i18n-ignore
        val scr = CrashLog.screen.ifBlank { "unknown" }  // i18n-ignore
        if (!ok) { inc("focus target missing after retries ($scr)"); DiagCollect.limited("focus-$scr", 30_000, "nav", "focus could not be placed on $scr (target not on screen); focus stayed where it was") }  // i18n-ignore
        else if (attempts > 1) inc("focus placed only after $attempts attempts")  // i18n-ignore
    }
    fun navMismatch(want: String, got: String) = g("focus") { inc("navigation request answered by another screen (suspected)"); DiagCollect.limited("navmis", 30_000, "nav", "navigation asked for $want, first composed $got") }  // i18n-ignore
    fun rowNotRestored(where: String) = g("focus") { inc("remembered row not found in the list ($where)") }  // i18n-ignore

    // ------------------------------------------------------------------ section 62: database
    fun db(op: String, ms: Long, rows: Long, err: Throwable?) = g("db2") {  // i18n-ignore
        val o = if (err == null) "ok" else dbError(op, err)  // i18n-ignore
        ring("db ms $op", 200).add(ms); if (ms > 500) inc("slow database operations (>500 ms): $op")  // i18n-ignore
        PerfLog.rowII("db", listOf(op, ms, rows, o))  // i18n-ignore
    }
    fun dbError(op: String, e: Throwable): String {
        val c = when {
            e is android.database.sqlite.SQLiteDatabaseLockedException -> "locked"  // i18n-ignore
            e is android.database.sqlite.SQLiteDatabaseCorruptException -> "corrupt"  // i18n-ignore
            e is android.database.sqlite.SQLiteFullException -> "disk full"  // i18n-ignore
            (e.message ?: "").contains("busy", true) -> "busy"  // i18n-ignore
            e is android.database.sqlite.SQLiteException -> "sqlite error"  // i18n-ignore
            e is IllegalStateException -> "database not available"  // i18n-ignore
            else -> "other (" + e.javaClass.simpleName + ")"  // i18n-ignore
        }
        g("db2") { inc("database errors: $c ($op)"); DiagCollect.limited("db-$c", 60_000, "db", "database $op failed: $c") }  // i18n-ignore
        return "failed: $c"  // i18n-ignore
    }
    fun migration(fromV: Int, toV: Int, ms: Long, err: Throwable?) = g("db2") {  // i18n-ignore
        DiagEvents.add("db", "catalog database migration $fromV → $toV " + (if (err == null) "completed in $ms ms" else "failed: " + dbError("migration", err)))  // i18n-ignore
        PerfLog.rowII("db", listOf("migration $fromV→$toV", ms, 0, if (err == null) "ok" else "failed"))  // i18n-ignore
    }
    inline fun <T> timed(op: String, rows: (T) -> Long = { 0 }, block: () -> T): T {
        val t = SystemClock.elapsedRealtime()
        try { val r = block(); db(op, SystemClock.elapsedRealtime() - t, try { rows(r) } catch (_: Throwable) { 0 }, null); return r }
        catch (e: Throwable) { db(op, SystemClock.elapsedRealtime() - t, 0, e); throw e }
    }

    /** live channel lists as the app already parsed them (M3U file, Xtream group or all channels) */
    fun liveQuality(src: String, list: List<Channel>) = g("quality") {  // i18n-ignore
        val q = Quality(src, "live")  // i18n-ignore
        list.forEach { c -> q.item(c.url.takeIf { src == "M3U" } ?: c.id, c.name, c.group, c.logo, c.epg, c.url) }  // i18n-ignore
        q.done(-1, null)
        inc("live channels seen in imports ($src)", list.size.toLong())  // i18n-ignore
    }
    // ------------------------------------------------------------------ section 58: subtitle downloads the user chose
    fun subtitleLoad(provider: String, ms: Long, err: Throwable?) = g("subs2") {  // i18n-ignore
        ring("subtitle download ms", 200).add(ms)  // i18n-ignore
        inc("subtitle downloads from $provider: " + if (err == null) "ok" else "failed (" + DiagCollect.classify(err).let { if (it == "unknown") err.javaClass.simpleName else it } + ")")  // i18n-ignore
    }

    // ------------------------------------------------------------------ section 63: playlist quality (from the existing import passes)
    class Quality(val src: String, val kind: String) {
        val t0 = SystemClock.elapsedRealtime()
        var items = 0L; var dup = 0L; var noName = 0L; var noCat = 0L; var noImg = 0L; var noEpg = 0L; var invalid = 0L
        /** 64-bit FNV-1a of each id: duplicates are found without keeping the ids themselves in memory */
        private val ids = HashSet<Long>()
        private fun h(s: String): Long { var x = -0x340d631b7bdddcdbL; for (ch in s) { x = x xor ch.code.toLong(); x *= 0x100000001b3L }; return x }
        private val cats = HashSet<String>()
        fun item(id: String?, name: String?, cat: String?, img: String?, epg: String? = "-", url: String? = "-") {  // i18n-ignore
            items++
            if (id.isNullOrBlank() || name.isNullOrBlank() || url.isNullOrBlank()) invalid++
            if (name.isNullOrBlank()) noName++
            if (cat.isNullOrBlank()) noCat++ else if (cats.size < 5000) cats += cat
            if (img.isNullOrBlank()) noImg++
            if (epg.isNullOrBlank()) noEpg++
            if (!id.isNullOrBlank() && ids.size < 200_000 && !ids.add(h(id))) dup++
        }
        fun done(prev: Int, err: Throwable?) = DiagCollect.guard("quality") {  // i18n-ignore
            val ms = SystemClock.elapsedRealtime() - t0
            PerfLog.rowII("playlist", listOf(src, kind, items, cats.size, dup, noName, noCat, noImg, if (kind == "live") noEpg else "", invalid, ms, if (prev >= 0) prev else "",  // i18n-ignore
                if (err == null) "ok" else "failed: " + DiagCollect.classify(err)))  // i18n-ignore
            ids.clear(); cats.clear()
        }
    }
}
