package com.pretorian.tv

import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.media3.common.Format
import androidx.media3.exoplayer.analytics.AnalyticsListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 3.0.0.2 Extended Passive Diagnostics. Everything here only OBSERVES what the app already does: Media3 callbacks of the
 * players that already exist, Android callbacks (network, screen, display, audio routing), the existing Xtream login
 * answer, the existing image loader. No request, stream, test or poll is started from here, and no behaviour changes.
 *
 * Every collector is wrapped: an error inside a collector disables that collector for the session ([guard]) and never
 * reaches the app. Repetitive events are rate-limited ([limited]); counters live in memory for the session and the
 * notable moments go to the existing bounded journal (same 50 MB / 10,000 events / 90 days limits).
 */
object DiagCollect {
    const val SCHEMA = 2
    val sessionStartWall = System.currentTimeMillis()
    private val broken = ConcurrentHashMap.newKeySet<String>()
    val counters = ConcurrentHashMap<String, AtomicLong>()
    fun inc(k: String, n: Long = 1) { counters.getOrPut(k) { AtomicLong() }.addAndGet(n) }
    fun get(k: String) = counters[k]?.get()

    /** runs a collector; its first error switches it off for this session (counted, never thrown) */
    inline fun guard(name: String, block: () -> Unit) {
        if (name in brokenSet()) return
        try { block() } catch (t: Throwable) { disable(name, t) }
    }
    fun brokenSet(): Set<String> = broken
    fun disable(name: String, t: Throwable) { if (broken.add(name)) { inc("collector disabled"); try { DiagEvents.add("diag", "collector '$name' switched off: " + t.javaClass.simpleName) } catch (_: Throwable) {} } }  // i18n-ignore
    fun disabled(): Set<String> = broken.toSet()

    private val lastAt = ConcurrentHashMap<String, Long>()
    /** at most one journal line per [key] every [ms]; the rest are only counted */
    fun limited(key: String, ms: Long, kind: String, msg: String) {
        val now = SystemClock.elapsedRealtime(); val l = lastAt[key] ?: 0L
        if (now - l < ms) { inc("suppressed $key"); return }
        lastAt[key] = now; DiagEvents.add(kind, msg)
    }

    /** what is playing now, for the context of every event */
    fun playState(): String = when {
        ChannelHealth.playerOn -> if (PlayState.inPlayer) "fullscreen" else "player"  // i18n-ignore
        ChannelHealth.previewOn -> "preview"  // i18n-ignore
        else -> "not playing"  // i18n-ignore
    }

    // ------------------------------------------------------------------ player lifecycle (§12)
    val playersLive = AtomicInteger(0)
    val playersPeak = AtomicInteger(0)
    /** 3.0.0.8 (audit): instances are counted by identity, and a release counts at the moment release() is called
     *  (synchronously), not when Media3 later reports onPlayerReleased. Before 3.0.0.8 the old instance was still
     *  counted for a moment after release(), which logged "2 player instances alive" although only one was. Idempotent. */
    private val livePlayers = java.util.concurrent.ConcurrentHashMap<Int, Boolean>()
    fun playerCreated(preview: Boolean, player: Any? = null) = guard("lifecycle") {
        inc(if (preview) "players created (preview)" else "players created (fullscreen)")  // i18n-ignore
        if (player != null) livePlayers[System.identityHashCode(player)] = preview
        val n = if (player != null) livePlayers.size.also { playersLive.set(it) } else playersLive.incrementAndGet()
        playersPeak.accumulateAndGet(n, ::maxOf)
        if (n > 1) limited("players>1", 5_000, "life", "$n player instances alive at once (" + playState() + ")")  // i18n-ignore
    }
    /** call right before (or instead of waiting for) player.release(); a second call for the same instance does nothing */
    fun playerReleasing(player: Any?) = guard("lifecycle") {
        if (player == null) return@guard
        val preview = livePlayers.remove(System.identityHashCode(player)) ?: return@guard
        inc(if (preview) "players released (preview)" else "players released (fullscreen)"); playersLive.set(livePlayers.size)  // i18n-ignore
    }
    fun playerReleased(preview: Boolean) = guard("lifecycle") { inc(if (preview) "players released (preview)" else "players released (fullscreen)"); playersLive.decrementAndGet() }  // i18n-ignore
    fun session(kind: String, on: Boolean) = guard("lifecycle") { inc("$kind sessions " + if (on) "started" else "stopped"); DiagEvents.add("life", "$kind " + if (on) "started" else "stopped") }  // i18n-ignore

    // ------------------------------------------------------------------ decoders and formats (§7, §9, §11)
    private val hwCache = ConcurrentHashMap<String, String>()
    /** "hardware" / "software" / "unknown" for a decoder name, from Android's own flags (API 29+), never from the name */
    fun hw(name: String): String = hwCache.getOrPut(name) {
        if (Build.VERSION.SDK_INT < 29) "unknown (Android < 10)" else try {  // i18n-ignore
            android.media.MediaCodecList(android.media.MediaCodecList.ALL_CODECS).codecInfos.firstOrNull { it.name == name }?.let {
                if (it.isHardwareAccelerated) "hardware" else if (it.isSoftwareOnly) "software" else "unknown"  // i18n-ignore
            } ?: "unknown"  // i18n-ignore
        } catch (_: Throwable) { "unknown" }  // i18n-ignore
    }
    fun video(f: Format): String = listOfNotNull(f.sampleMimeType, f.codecs, if (f.width > 0) "${f.width}×${f.height}" else null,
        if (f.frameRate > 0) String.format(java.util.Locale.US, "%.3f fps", f.frameRate) else "fps unavailable",  // i18n-ignore
        if (f.bitrate > 0) "${f.bitrate / 1000} kbit/s" else if (f.averageBitrate > 0) "${f.averageBitrate / 1000} kbit/s avg" else null,  // i18n-ignore
        f.colorInfo?.let { c -> "color space ${c.colorSpace} · transfer ${c.colorTransfer}" + (when (c.colorTransfer) { 6 -> " (PQ/HDR10)"; 7 -> " (HLG)"; else -> "" }) + " · range ${c.colorRange}" + if (c.hdrStaticInfo != null) " · HDR metadata present" else "" }  // i18n-ignore
            ?: "color info unavailable").joinToString(" · ")  // i18n-ignore
    fun audio(f: Format): String = listOfNotNull(f.sampleMimeType, f.codecs, if (f.channelCount > 0) "${f.channelCount} ch" else null,
        if (f.sampleRate > 0) "${f.sampleRate} Hz" else null, f.language?.let { "lang $it" }).joinToString(" · ")  // i18n-ignore

    /** one listener per player, added where the player is built (preview and fullscreen alike) */
    fun listener(preview: Boolean, player: Any? = null): AnalyticsListener = object : AnalyticsListener {
        val kind = if (preview) "preview" else "player"  // i18n-ignore
        var lastV = ""; var lastA = ""
        var dropped = 0L
        override fun onVideoDecoderInitialized(t: AnalyticsListener.EventTime, name: String, initAt: Long, initMs: Long) = guard("decoder") {
            DiagEvents.add("codec", "$kind video decoder $name (" + hw(name) + ") ready in $initMs ms · " + playState())  // i18n-ignore
        }
        override fun onAudioDecoderInitialized(t: AnalyticsListener.EventTime, name: String, initAt: Long, initMs: Long) = guard("decoder") {
            DiagEvents.add("codec", "$kind audio decoder $name ($initMs ms)")  // i18n-ignore
        }
        override fun onVideoInputFormatChanged(t: AnalyticsListener.EventTime, f: Format, r: androidx.media3.exoplayer.DecoderReuseEvaluation?) = guard("format") {
            val s = video(f); if (s != lastV) { lastV = s; DiagEvents.add("format", "$kind video: $s") }  // i18n-ignore
        }
        override fun onAudioInputFormatChanged(t: AnalyticsListener.EventTime, f: Format, r: androidx.media3.exoplayer.DecoderReuseEvaluation?) = guard("format") {
            val s = audio(f); if (s != lastA) { lastA = s; DiagEvents.add("format", "$kind audio: $s") }  // i18n-ignore
        }
        override fun onDroppedVideoFrames(t: AnalyticsListener.EventTime, count: Int, elapsedMs: Long) = guard("frames") {
            dropped += count; inc("dropped video frames", count.toLong())  // i18n-ignore
            if (count >= 10) limited("drop-$kind", 30_000, "frames", "$kind dropped $count frames in $elapsedMs ms (session total $dropped)")  // i18n-ignore
        }
        override fun onAudioUnderrun(t: AnalyticsListener.EventTime, size: Int, sizeMs: Long, sinceFeedMs: Long) = guard("audio") {
            inc("audio underruns"); limited("underrun-$kind", 30_000, "audio", "$kind audio underrun (buffer $sizeMs ms, $sinceFeedMs ms since last data)")  // i18n-ignore
        }
        override fun onAudioSinkError(t: AnalyticsListener.EventTime, e: Exception) = guard("audio") { inc("audio sink errors"); limited("sink", 10_000, "audio", "audio output error: " + e.javaClass.simpleName) }  // i18n-ignore
        override fun onVideoCodecError(t: AnalyticsListener.EventTime, e: Exception) = guard("decoder") { inc("video decoder errors"); limited("vcodec", 10_000, "codec", "video decoder error: " + e.javaClass.simpleName + " · " + (e.message ?: "").take(120)) }  // i18n-ignore
        override fun onAudioCodecError(t: AnalyticsListener.EventTime, e: Exception) = guard("decoder") { inc("audio decoder errors"); limited("acodec", 10_000, "codec", "audio decoder error: " + e.javaClass.simpleName) }  // i18n-ignore
        override fun onPlayerError(t: AnalyticsListener.EventTime, e: androidx.media3.common.PlaybackException) = guard("errors") {
            val c = classify(e); inc("$kind error: $c")
            DiagEvents.add("neterr", "$kind ${e.errorCodeName.removePrefix("ERROR_CODE_")} · cause: $c · " + NetState.now())  // i18n-ignore
        }
        override fun onPlayerReleased(t: AnalyticsListener.EventTime) { if (player != null) playerReleasing(player) else playerReleased(preview) }
    }

    /** the kind of failure, from the exception chain (no URL, no message bodies) */
    fun classify(e: Throwable): String {
        var c: Throwable? = e; var depth = 0
        while (c != null && depth++ < 8) {
            when (c) {
                is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException -> return when (c.responseCode) {
                    401, 403 -> "HTTP ${c.responseCode} (authentication)"; 429, 458, 509 -> "HTTP ${c.responseCode} (rate limit / connections)"  // i18n-ignore
                    else -> "HTTP ${c.responseCode}" }  // i18n-ignore
                is java.net.UnknownHostException -> return "DNS resolution failure"  // i18n-ignore
                is java.net.SocketTimeoutException -> return if ((c.message ?: "").contains("connect", true)) "connection timeout" else "read timeout"  // i18n-ignore
                is java.net.ConnectException -> return "connection refused"  // i18n-ignore
                is javax.net.ssl.SSLException -> return "TLS / certificate failure"  // i18n-ignore
                is java.security.cert.CertificateException -> return "TLS / certificate failure"  // i18n-ignore
                is java.net.SocketException -> return if ((c.message ?: "").contains("reset", true)) "connection reset" else "socket closed"  // i18n-ignore
                is java.io.EOFException -> return "unexpected connection closure"  // i18n-ignore
                is java.io.InterruptedIOException -> return "cancelled"  // i18n-ignore
                is androidx.media3.exoplayer.mediacodec.MediaCodecRenderer.DecoderInitializationException -> return "decoder initialization failure"  // i18n-ignore
                is android.media.MediaCodec.CodecException -> return "decoder runtime failure"  // i18n-ignore
                is androidx.media3.exoplayer.audio.AudioSink.InitializationException -> return "audio output initialization failure"  // i18n-ignore
                is androidx.media3.common.ParserException -> return "stream format / parsing error"  // i18n-ignore
            }
            c = c.cause
        }
        return "unknown"  // i18n-ignore
    }

    // ------------------------------------------------------------------ account (§6), from the existing login answer only
    /** called with the user_info object the app already parsed; nothing is requested for this */
    fun account(pid: String, ui: org.json.JSONObject) = guard("account") {
        val o = org.json.JSONObject()
            .put("status", ui.optString("status", "")).put("max", ui.optString("max_connections", "")).put("active", ui.optString("active_cons", ""))
            .put("exp", ui.optString("exp_date", "")).put("fields", listOf("max_connections", "active_cons", "exp_date").filter { ui.has(it) }.joinToString(","))  // i18n-ignore
            .put("at", System.currentTimeMillis())
        Prefs.put("diag_acct_" + ChannelHealth.key("pl:$pid"), o.toString())  // i18n-ignore
    }
    fun accounts(): List<Pair<String, org.json.JSONObject>> = Prefs.allStrings().filter { it.first.startsWith("diag_acct_") }
        .mapNotNull { (k, v) -> try { k.removePrefix("diag_acct_").take(8) to org.json.JSONObject(v) } catch (_: Throwable) { null } }

    // ------------------------------------------------------------------ navigation (§19)
    @Volatile private var navAt = 0L
    @Volatile private var navTo = ""
    fun navRequested(to: String) { navAt = SystemClock.elapsedRealtime(); navTo = to; DiagII.navRequested() }
    /** destination composed for the first time (not proof of a drawn frame) */
    fun navComposed(screen: String) = guard("nav") {
        if (navAt > 0 && screen == navTo) { val ms = SystemClock.elapsedRealtime() - navAt; navAt = 0; PerfLog.task("nav $screen", "composed", ms); DiagII.navComposed(screen) }  // i18n-ignore
        else if (navAt > 0 && SystemClock.elapsedRealtime() - navAt > 3_000) { DiagII.navMismatch(navTo, screen); navAt = 0 }
    }

    // ------------------------------------------------------------------ images (§20), from Coil's own events
    private val imgStart = ConcurrentHashMap<coil.request.ImageRequest, Long>()
    val imgMs = ConcurrentHashMap<String, java.util.ArrayDeque<Long>>()
    private fun cat(r: coil.request.ImageRequest) = if (r.transformations.any { it is LogoTrim }) "logos" else "artwork"  // i18n-ignore
    val imageListener = object : coil.EventListener {
        override fun onStart(request: coil.request.ImageRequest) = guard("images") { if (imgStart.size < 2000) imgStart[request] = SystemClock.elapsedRealtime(); inc("images ${cat(request)} attempts") }  // i18n-ignore
        override fun onSuccess(request: coil.request.ImageRequest, result: coil.request.SuccessResult) = guard("images") {
            val c = cat(request); inc("images $c ok (" + result.dataSource.name.lowercase() + ")")  // i18n-ignore
            imgStart.remove(request)?.let { t0 -> val ms = SystemClock.elapsedRealtime() - t0; val q = imgMs.getOrPut(c) { java.util.ArrayDeque() }; synchronized(q) { q.addLast(ms); while (q.size > 500) q.removeFirst() }; DiagII.ring("img pending $c", 2000).add(ms) }
        }
        override fun onError(request: coil.request.ImageRequest, result: coil.request.ErrorResult) = guard("images") { imgStart.remove(request); inc("images ${cat(request)} failed") }  // i18n-ignore
        override fun onCancel(request: coil.request.ImageRequest) = guard("images") { imgStart.remove(request); inc("images ${cat(request)} cancelled") }  // i18n-ignore
    }

    // ------------------------------------------------------------------ Android callbacks: screen, display, audio routes (§8, §9, §14)
    @Volatile var afrAskedAt = 0L
    @Volatile var afrAsked = ""
    /** 3.0.0.8 (audit): registered once per process. Before, every Activity onCreate added another set of
     *  receivers, so one screen-saver event was logged two or three times within the same millisecond. */
    private val started = java.util.concurrent.atomic.AtomicBoolean(false)
    fun start(ctx: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = ctx.applicationContext
        guard("screen") {
            val f = android.content.IntentFilter().apply {
                addAction(android.content.Intent.ACTION_SCREEN_OFF); addAction(android.content.Intent.ACTION_SCREEN_ON)
                addAction(android.content.Intent.ACTION_DREAMING_STARTED); addAction(android.content.Intent.ACTION_DREAMING_STOPPED)
            }
            val r = object : android.content.BroadcastReceiver() {
                override fun onReceive(c: Context?, i: android.content.Intent?) = guard("screen") {
                    val what = when (i?.action) { android.content.Intent.ACTION_SCREEN_OFF -> "screen off (standby / TV off)"; android.content.Intent.ACTION_SCREEN_ON -> "screen on (wake)"  // i18n-ignore
                        android.content.Intent.ACTION_DREAMING_STARTED -> "screen saver started"; else -> "screen saver stopped" }  // i18n-ignore
                    inc(what); DiagEvents.add("power", what + " · " + playState() + " · players alive " + playersLive.get())  // i18n-ignore
                }
            }
            if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED) else app.registerReceiver(r, f)
        }
        guard("display") {
            val dm = app.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
            var last = mode(app)
            dm.registerDisplayListener(object : android.hardware.display.DisplayManager.DisplayListener {
                override fun onDisplayAdded(id: Int) {}
                override fun onDisplayRemoved(id: Int) {}
                override fun onDisplayChanged(id: Int) = guard("display") {
                    if (id != 0) return@guard
                    val m = mode(app); if (m == last) return@guard
                    val after = if (afrAskedAt > 0) " · " + (SystemClock.elapsedRealtime() - afrAskedAt) + " ms after the frame-rate request for $afrAsked" else ""  // i18n-ignore
                    DiagEvents.add("display", "display mode $last → $m$after · " + playState()); last = m; afrAskedAt = 0  // i18n-ignore
                }
            }, android.os.Handler(android.os.Looper.getMainLooper()))
        }
        guard("audioroute") {
            if (Build.VERSION.SDK_INT >= 23) {
                val am = app.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                var first = true
                am.registerAudioDeviceCallback(object : android.media.AudioDeviceCallback() {
                    override fun onAudioDevicesAdded(d: Array<out android.media.AudioDeviceInfo>?) = guard("audioroute") {
                        if (first) { first = false; return@guard }   // Android reports the current devices once at registration
                        d?.filter { it.isSink }?.forEach { DiagEvents.add("audio", "audio output added: " + devType(it.type) + " · " + playState()) }  // i18n-ignore
                    }
                    override fun onAudioDevicesRemoved(d: Array<out android.media.AudioDeviceInfo>?) = guard("audioroute") {
                        d?.filter { it.isSink }?.forEach { DiagEvents.add("audio", "audio output removed: " + devType(it.type) + " · " + playState()) }  // i18n-ignore
                    }
                }, android.os.Handler(android.os.Looper.getMainLooper()))
            }
        }
    }
    fun mode(ctx: Context): String = try {
        val d = (ctx.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager).getDisplay(0)
        if (Build.VERSION.SDK_INT >= 23) d.mode.let { "${it.physicalWidth}×${it.physicalHeight}@" + String.format(java.util.Locale.US, "%.3f", it.refreshRate) } else String.format(java.util.Locale.US, "%.3f Hz", d.refreshRate)  // i18n-ignore
    } catch (_: Throwable) { "unavailable" }  // i18n-ignore
    fun devType(t: Int): String = when (t) {
        android.media.AudioDeviceInfo.TYPE_HDMI -> "HDMI"; android.media.AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI ARC"; 29 -> "HDMI eARC"  // i18n-ignore
        android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "built-in speaker"; android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth audio"  // i18n-ignore
        android.media.AudioDeviceInfo.TYPE_USB_DEVICE, android.media.AudioDeviceInfo.TYPE_USB_HEADSET -> "USB audio"; android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "wired headphones"  // i18n-ignore
        android.media.AudioDeviceInfo.TYPE_LINE_ANALOG, android.media.AudioDeviceInfo.TYPE_LINE_DIGITAL -> "line out"; android.media.AudioDeviceInfo.TYPE_TELEPHONY -> "telephony"  // i18n-ignore
        else -> "type $t"  // i18n-ignore
    }

    // ------------------------------------------------------------------ storage growth (§16): a small snapshot every 6 h, only while idle
    fun storageSnapshot(ctx: Context) = guard("storage") {
        fun dir(f: java.io.File?): Long = try { f?.walkTopDown()?.maxDepth(3)?.filter { it.isFile }?.take(20_000)?.sumOf { it.length() } ?: -1 } catch (_: Throwable) { -1 }
        val dbs = ctx.databaseList().filter { !it.endsWith("-journal") }.map { ctx.getDatabasePath(it) }
        val db = dbs.filter { !it.name.endsWith("-wal") && !it.name.endsWith("-shm") }.sumOf { it.length() }
        val wal = dbs.filter { it.name.endsWith("-wal") || it.name.endsWith("-shm") }.sumOf { it.length() }
        PerfLog.storage(db / 1024, wal / 1024, dir(java.io.File(ctx.cacheDir, "images")) / 1024, dir(ctx.cacheDir) / 1024,
            java.io.File(ctx.filesDir, "logo_index.tsv").length() / 1024, DiagStore.totalBytes() / 1024, ctx.filesDir.usableSpace / 1_048_576)
    }
}

/** network transitions from Android's own callbacks (§13): transport, VPN, validation, loss and how long until it was back */
object NetState {
    @Volatile var transports = "unknown"  // i18n-ignore
    @Volatile var validated = false
    @Volatile private var lostAt = 0L
    fun now() = "network $transports" + (if (validated) "" else " (not validated)") + " · " + DiagCollect.playState()  // i18n-ignore
    fun changed(caps: android.net.NetworkCapabilities?) = DiagCollect.guard("network") {
        val t = if (caps == null) "none" else listOfNotNull(  // i18n-ignore
            if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)) "ethernet" else null,  // i18n-ignore
            if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) "wifi" else null,  // i18n-ignore
            if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)) "vpn" else null).joinToString("+").ifEmpty { "other" }  // i18n-ignore
        val v = caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        if (t != transports || v != validated) {
            val back = if (lostAt > 0 && t != "none") " · back after " + (SystemClock.elapsedRealtime() - lostAt) + " ms" else ""  // i18n-ignore
            DiagEvents.add("net", "network $transports → $t" + (if (v) "" else " (not validated)") + back + " · " + DiagCollect.playState())  // i18n-ignore
            DiagCollect.inc("network changes"); if (t != "none") lostAt = 0
            DiagII.netChanged(transports, t)
            transports = t; validated = v
        }
    }
    fun lost() = DiagCollect.guard("network") { if (lostAt == 0L) lostAt = SystemClock.elapsedRealtime(); DiagCollect.inc("network lost"); DiagII.netChanged(transports, "none"); transports = "none"; validated = false }  // i18n-ignore
}
