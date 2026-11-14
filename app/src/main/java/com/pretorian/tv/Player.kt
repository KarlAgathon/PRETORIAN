package com.pretorian.tv

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import java.util.Locale
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import java.io.File
import androidx.media3.common.C as MC

@OptIn(UnstableApi::class)
fun buildPlayer(ctx: Context, preview: Boolean = false): ExoPlayer {
    val http = DefaultHttpDataSource.Factory()
        .setUserAgent(Net.UA)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(30_000)
    val renderers = DefaultRenderersFactory(ctx)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        .setEnableDecoderFallback(true)
    val builder = ExoPlayer.Builder(ctx, renderers)
        .setMediaSourceFactory(DefaultMediaSourceFactory(ctx).setDataSourceFactory(DefaultDataSource.Factory(ctx, http)))
    // the small picture starts as soon as half a second is loaded
    if (preview) builder.setLoadControl(androidx.media3.exoplayer.DefaultLoadControl.Builder()
        .setBufferDurationsMs(2_000, 15_000, 500, 1_000).build())
    val player = builder.build()
    val audio = Prefs.audioLangs.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray()
    val b = player.trackSelectionParameters.buildUpon().setPreferredAudioLanguages(*audio)
    if (Prefs.autoSubs) {
        val langs = Prefs.subLangs.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray()
        b.setPreferredTextLanguages(*langs)
    } else {
        // subtitles stay off until you pick one (the audio choice never switches them on)
        b.setTrackTypeDisabled(MC.TRACK_TYPE_TEXT, true)
    }
    player.trackSelectionParameters = b.build()
    return player
}

/** ISO 639-1 code ("bs") from what the stream says ("bos", "bs", "bs-BA", "scc"…). */
fun iso2(raw: String?): String? {
    val c = raw?.trim()?.lowercase()?.substringBefore('-')?.substringBefore('_') ?: return null
    if (c.isEmpty() || c == "und") return null
    if (c.length == 2) return c
    when (c) { "scc", "srp" -> return "sr"; "scr", "hrv" -> return "hr"; "bos" -> return "bs"; "ger", "deu" -> return "de"; "fre", "fra" -> return "fr" }
    return java.util.Locale.getISOLanguages().firstOrNull { l -> try { java.util.Locale(l).isO3Language == c } catch (e: Exception) { false } } ?: c
}

/** Audio and subtitle tracks of a stream, read before playing (opens the stream for a moment). */
@OptIn(UnstableApi::class)
suspend fun probeTracks(ctx: Context, url: String): Pair<List<androidx.media3.common.Format>, List<androidx.media3.common.Format>>? {
    val player = buildPlayer(ctx)
    return try {
        kotlinx.coroutines.withTimeoutOrNull(20_000) {
            kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                val l = object : Player.Listener {
                    override fun onTracksChanged(tracks: Tracks) {
                        if (tracks.groups.isEmpty() || !cont.isActive) return
                        fun of(type: Int) = tracks.groups.filter { it.type == type }.flatMap { g -> (0 until g.length).map { g.getTrackFormat(it) } }
                        cont.resumeWith(Result.success(of(MC.TRACK_TYPE_AUDIO) to of(MC.TRACK_TYPE_TEXT)))
                    }
                    override fun onPlayerError(error: PlaybackException) { if (cont.isActive) cont.resumeWith(Result.success(null)) }
                }
                player.addListener(l)
                player.volume = 0f
                player.setMediaItem(MediaItem.fromUri(url)); player.prepare()
            }
        }
    } finally { player.release() }
}

fun trackLabel(f: androidx.media3.common.Format, audio: Boolean): String {
    val lang = iso2(f.language)?.let { Langs.label(it) } ?: tr("Nepoznat jezik")
    val extra = buildList {
        f.label?.takeIf { it.isNotBlank() }?.let { add(it) }
        if (audio && f.channelCount > 0) add(when (f.channelCount) { 1 -> "Mono"; 2 -> "Stereo"; 6 -> "5.1"; 8 -> "7.1"; else -> "${f.channelCount} ch" })
        f.sampleMimeType?.substringAfter('/')?.uppercase()?.let { add(it) }
    }.joinToString(" · ")
    return if (extra.isBlank()) lang else "$lang · $extra"
}

private fun qualityLabel(h: Int) = when {
    h >= 2000 -> "4K"; h >= 1000 -> "FHD"; h >= 700 -> "HD"; h > 0 -> "SD"; else -> "—"
}

data class TrackItem(val label: String, val group: Tracks.Group, val index: Int, val selected: Boolean)

@OptIn(UnstableApi::class)
private fun tracksOf(player: ExoPlayer, type: Int): List<TrackItem> {
    val out = ArrayList<TrackItem>()
    for (g in player.currentTracks.groups) {
        if (g.type != type) continue
        for (i in 0 until g.length) {
            if (!g.isTrackSupported(i)) continue
            out.add(TrackItem(trackLabel(g.getTrackFormat(i), type == MC.TRACK_TYPE_AUDIO), g, i, g.isTrackSelected(i)))
        }
    }
    return out
}

private fun httpCode(e: PlaybackException): Int? {
    var c: Throwable? = e.cause
    while (c != null) {
        if (c is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) return c.responseCode
        c = c.cause
    }
    return null
}

/** A clear message for a stream that does not play, in the app language. */
private fun errorText(e: PlaybackException, live: Boolean): String {
    val code = httpCode(e)
    return when {
        code == 401 || code == 403 -> tr("Server odbija pristup: nalog istekao ili blokiran") + " (HTTP $code)"
        code == 429 || code == 458 || code == 509 || code == 456 -> tr("Previše konekcija: ugasi gledanje na drugom uređaju") + " (HTTP $code)"
        code == 404 -> tr("Kanal više ne postoji kod providera") + " (HTTP 404)"
        code != null && code >= 500 -> tr("Server trenutno ne radi") + " (HTTP $code)"
        code != null -> tr("Server je odbio zahtjev") + " (HTTP $code)"
        e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED || e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            tr("Nema veze sa serverom")
        else -> tr(if (live) "Kanal trenutno ne radi" else "Video se ne može pustiti") + " (${e.errorCodeName.removePrefix("ERROR_CODE_")})"
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(s: Screen.Player, nav: Nav) {
    val ctx = LocalContext.current
    var items by remember { mutableStateOf(s.items) }
    val n = items.size
    var index by remember { mutableIntStateOf(s.index.coerceIn(0, (s.items.size - 1).coerceAtLeast(0))) }
    var showInfo by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retried by remember { mutableStateOf(false) }
    var retryToken by remember { mutableIntStateOf(0) }
    var reconnect by remember { mutableIntStateOf(0) }          // live: attempt 1..3 of getting the stream back
    var digits by remember { mutableStateOf("") }              // channel number typed on the remote
    var digitTick by remember { mutableIntStateOf(0) }
    var prevIndex by remember { mutableIntStateOf(-1) }
    var saver by remember { mutableStateOf(false) }   // screen saver after a long pause
    var buffering by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var videoH by remember { mutableIntStateOf(0) }
    var videoW by remember { mutableIntStateOf(0) }
    var fps by remember { mutableStateOf(0f) }
    var bitrate by remember { mutableLongStateOf(0L) }
    var resUntil by remember { mutableLongStateOf(0L) }
    var channels by remember { mutableIntStateOf(0) }
    var nowNext by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var dialog by remember { mutableStateOf<String?>(null) }   // menu, audio, subs, info, fav, ext
    var side by remember { mutableStateOf<String?>(null) }     // channels, groups, epg (live only)
    var sideGroup by remember { mutableStateOf<GroupRef?>(null) }
    var resume by remember { mutableStateOf<Progress?>(null) }
    var bar by remember { mutableStateOf(false) }   // button bar at the bottom
    var subFile by remember { mutableStateOf(s.items.getOrNull(index)?.subFile) }
    var resize by remember { mutableIntStateOf(0) }
    val player = remember { Preview.stop(); buildPlayer(ctx) }
    val applied = remember { booleanArrayOf(false) }
    val step = Prefs.seekStep * 1000L
    val focus = remember { FocusRequester() }
    val item = items.getOrElse(index) { items.first() }
    val p = item.channel?.let { Store.byId(ctx, it.pid) } ?: Store.active(ctx)

    /** audio / built-in subtitle picked on the movie page, applied when the stream's tracks are known */
    fun applyChoices(item: PlayItem) {
        fun find(type: Int, lang: String?, label: String?): Pair<Tracks.Group, Int>? {
            for (g in player.currentTracks.groups) {
                if (g.type != type) continue
                for (i in 0 until g.length) {
                    val f = g.getTrackFormat(i)
                    if ((lang == null || iso2(f.language) == lang) && (label == null || f.label == label)) return g to i
                }
            }
            return null
        }
        val b = player.trackSelectionParameters.buildUpon()
        if (item.audioLang != null || item.audioLabel != null) find(MC.TRACK_TYPE_AUDIO, item.audioLang, item.audioLabel)?.let { (g, i) ->
            b.setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i))
        }
        if (subFile != null) { /* a subtitle file is loaded: keep it */ }
        else if (item.subOff) b.setTrackTypeDisabled(MC.TRACK_TYPE_TEXT, true)
        else if (item.subLang != null || item.subLabel != null) find(MC.TRACK_TYPE_TEXT, item.subLang, item.subLabel)?.let { (g, i) ->
            b.setTrackTypeDisabled(MC.TRACK_TYPE_TEXT, false).setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i))
        }
        player.trackSelectionParameters = b.build()
    }

    fun loadMedia(item: PlayItem, file: String?, at: Long, play: Boolean = true) {
        val b = MediaItem.Builder().setUri(item.url)
        if (file != null) b.setSubtitleConfigurations(listOf(
            MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(File(file)))
                .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                .setLanguage(File(file).nameWithoutExtension.substringAfterLast('_'))
                .setSelectionFlags(MC.SELECTION_FLAG_DEFAULT).build()))
        val tb = player.trackSelectionParameters.buildUpon()
        if (item.audioLang != null) tb.setPreferredAudioLanguages(item.audioLang)
        if (file != null) tb.setTrackTypeDisabled(MC.TRACK_TYPE_TEXT, false)
        else if (item.subOff || (!Prefs.autoSubs && item.subLang == null && item.subLabel == null)) tb.setTrackTypeDisabled(MC.TRACK_TYPE_TEXT, true)
        player.trackSelectionParameters = tb.build()
        applied[0] = false
        player.setMediaItem(b.build(), at)
        player.prepare()
        player.playWhenReady = play
    }

    /** remembers where you are in a movie or episode (continue watching, watched marks) */
    fun saveProgress(done: Boolean = false) {
        if (s.live) return
        val it = items.getOrNull(index) ?: return
        val dur = player.duration.takeIf { d -> d > 0 } ?: return
        val pos = if (done) dur else player.currentPosition
        Watch.save(ctx, Progress(it.url, it.title, it.logo, pos, dur, System.currentTimeMillis(), it.search.takeIf { _ -> it.season != null },
            it.season, it.episode, next = false, done = done || pos >= dur * 0.9, pid = p?.id ?: ""))
        // finished an episode: the next one shows up in "Continue watching"
        if ((done || pos >= dur * 0.9) && it.season != null) items.getOrNull(index + 1)?.let { nx ->
            if (Watch.get(ctx, nx.url)?.watched != true)
                Watch.save(ctx, Progress(nx.url, nx.title, nx.logo, 0, 0, System.currentTimeMillis() + 1, nx.search, nx.season, nx.episode, next = true, pid = p?.id ?: ""))
        }
    }

    DisposableEffect(Unit) {
        val l = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                if (s.live && reconnect < 3) { reconnect++; retryToken++ }
                else if (!s.live && !retried) { retried = true; retryToken++ }
                else { reconnect = 0; error = errorText(e, s.live) }
            }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) { error = null; reconnect = 0 }
                if (state == Player.STATE_ENDED && !s.live) { saveProgress(done = true); if (index < n - 1) index++ }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onTracksChanged(tracks: Tracks) {
                player.videoFormat?.let {
                    if (it.height != videoH) resUntil = System.currentTimeMillis() + 6000
                    videoH = it.height; videoW = it.width; fps = it.frameRate
                }
                player.audioFormat?.let { channels = it.channelCount }
                if (!applied[0] && tracks.groups.isNotEmpty()) { applied[0] = true; applyChoices(items[index]) }
            }
        }
        val a = object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onBandwidthEstimate(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, totalLoadTimeMs: Int, totalBytesLoaded: Long, bitrateEstimate: Long) {
                bitrate = bitrateEstimate
            }
        }
        player.addListener(l); player.addAnalyticsListener(a)
        onDispose { saveProgress(); player.removeListener(l); player.removeAnalyticsListener(a); player.release() }
    }

    var lastIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(index, items) {
        if (lastIndex >= 0 && lastIndex != index) prevIndex = lastIndex
        lastIndex = index
        if (s.live) TvMemory.channel = index
        reconnect = 0
        if (s.live) items.getOrNull(index)?.channel?.let { c ->
            val g = TvMemory.group
            try { Recent.add(c, g?.pid, g?.cat?.id ?: c.group, g?.cat?.name ?: c.group) } catch (_: Exception) {}
        }
        error = null; retried = false; showInfo = true; videoH = 0
        val cur = items[index]
        subFile = cur.subFile
        val saved = if (!s.live) Watch.get(ctx, cur.url) else null
        if (saved != null && saved.started && cur.subFile == null) { resume = saved; loadMedia(cur, subFile, saved.pos, play = false) }
        else loadMedia(cur, subFile, 0L)
        nowNext = emptyList()
        val ch = cur.channel
        if (s.live && ch != null && p != null) nowNext = Repo.nowNext(p, ch)
    }
    LaunchedEffect(retryToken) {
        if (retryToken == 0) return@LaunchedEffect
        delay(if (s.live) when (reconnect) { 2 -> 5000L; 3 -> 10_000L; else -> 2000L } else 2000L)
        loadMedia(items[index], subFile, if (s.live) 0L else player.currentPosition)
    }
    LaunchedEffect(showInfo, index, playing, bar) { if (showInfo && !bar && (s.live || playing)) { delay(5000); showInfo = false } }
    LaunchedEffect(Unit) { while (true) { position = player.currentPosition; duration = player.duration.coerceAtLeast(0); delay(500) } }
    LaunchedEffect(index) { if (!s.live) while (true) { delay(15_000); if (player.isPlaying) saveProgress() } }

    BackHandler(enabled = dialog != null || side != null || resume != null || bar) {
        when {
            resume != null -> { resume = null; player.seekTo(0); player.play() }
            dialog != null -> dialog = null
            bar -> { bar = false; showInfo = false }
            side == "groups" -> side = "channels"
            else -> { side = null; sideGroup = null }
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(focus)
            .onPreviewKeyEvent { e ->
                if (saver) { if (e.type == KeyEventType.KeyDown) saver = false; return@onPreviewKeyEvent true }
                if (dialog != null || side != null || resume != null || bar || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (error != null && (e.key == Key.DirectionCenter || e.key == Key.Enter)) { error = null; retried = false; reconnect = 0; retryToken++; return@onPreviewKeyEvent true }
                // live: numbers on the remote pick a channel; 0 alone goes back to the previous channel
                val d = digitOf(e.key)
                if (s.live && d != null) {
                    if (digits.isEmpty() && d == 0) { if (prevIndex in 0 until n) index = prevIndex; return@onPreviewKeyEvent true }
                    if (digits.length < 4) { digits += d; digitTick++ }
                    return@onPreviewKeyEvent true
                }
                if (s.live && digits.isNotEmpty() && (e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter)) {
                    val t = items.indexOfFirst { it.channel?.num == digits.toIntOrNull() }; digits = ""
                    if (t >= 0) index = t
                    return@onPreviewKeyEvent true
                }
                when (e.key) {
                    Key.Menu, Key.Info -> { dialog = "menu"; true }
                    Key.DirectionUp, Key.ChannelUp -> if (s.live) { index = (index - 1 + n) % n; true } else { showInfo = true; true }
                    Key.DirectionDown, Key.ChannelDown -> if (s.live && e.key == Key.ChannelDown) { index = (index + 1) % n; true }
                        else if (s.live && !showInfo) { index = (index + 1) % n; true } else { bar = true; showInfo = true; true }
                    Key.DirectionLeft, Key.MediaRewind -> if (!s.live) { player.seekTo((player.currentPosition - step).coerceAtLeast(0)); showInfo = true; true }
                        else { side = "channels"; showInfo = false; true }
                    Key.DirectionRight, Key.MediaFastForward -> if (!s.live) { player.seekTo(player.currentPosition + step); showInfo = true; true }
                        else { side = "epg"; showInfo = false; true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause -> {
                        if (s.live) { if (e.key != Key.MediaPlayPause) { showInfo = true; bar = true } else showInfo = true }
                        else { if (player.isPlaying) player.pause() else player.play(); showInfo = true }
                        true
                    }
                    else -> false
                }
            }
            .focusable(),
    ) {
        // the picture: full screen, or small at the top left while the guide is open
        val small = side == "epg"
        Box(if (small) Modifier.padding(start = 28.dp, top = 28.dp).fillMaxWidth(0.56f).aspectRatio(16f / 9f).clip(Rnd(16.dp)) else Modifier.fillMaxSize()) {
            AndroidView(
                factory = { c ->
                    PlayerView(c).apply {
                        this.player = player
                        useController = false
                        keepScreenOn = true
                        isFocusable = false
                        descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    }
                },
                update = { v -> v.resizeMode = when (resize) { 1 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM; 2 -> AspectRatioFrameLayout.RESIZE_MODE_FILL; else -> AspectRatioFrameLayout.RESIZE_MODE_FIT } },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (buffering && error == null) Box(Modifier.align(Alignment.Center)) { Loading("") }

        // resolution in the top right corner
        val showRes = videoH > 0 && side == null && (Look.resBadge == 2 || (Look.resBadge == 1 && System.currentTimeMillis() < resUntil))
        LaunchedEffect(resUntil) { if (resUntil > 0) { delay(resUntil - System.currentTimeMillis() + 100); resUntil = resUntil } }
        if (showRes) Box(Modifier.align(Alignment.TopEnd).padding(24.dp).clip(Rnd(10.dp)).background(Color(0x99000000)).padding(horizontal = 12.dp, vertical = 6.dp)) {
            T("${qualityLabel(videoH)} · ${videoW}×$videoH" + (if (fps > 0) " · ${fps.toInt()} FPS" else ""), 14.sp, Color.White, FontWeight.SemiBold)
        }

        // channel number typed on the remote: shown top right, switches after 1.5 s
        LaunchedEffect(digitTick) {
            if (digits.isEmpty()) return@LaunchedEffect
            delay(1500)
            val t = items.indexOfFirst { it.channel?.num == digits.toIntOrNull() }
            if (t >= 0) index = t
            delay(if (t >= 0) 0 else 1200); digits = ""
        }
        if (digits.isNotEmpty()) {
            val m = items.firstOrNull { it.channel?.num == digits.toIntOrNull() }
            Row(Modifier.align(Alignment.TopEnd).padding(top = 36.dp, end = 44.dp).clip(Rnd(18.dp)).background(Color(0xE60E101A))
                .border(1.dp, Color(0x1FFFFFFF), Rnd(18.dp)).padding(horizontal = 22.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                T(digits, 46.sp, Color.White, FontWeight.ExtraBold)
                Box(Modifier.width(1.dp).height(46.dp).background(Color(0x26FFFFFF)))
                if (m?.channel != null) {
                    ChannelLogo(m.channel, 42.dp)
                    Column { T(m.title, 17.sp, Color.White, FontWeight.Bold); T("OK odmah", 12.sp, Color(0xFFC8CBD4)) }
                } else T(trf("Nema kanala %s", digits), 16.sp, Color(0xFFC8CBD4))
            }
        }
        // live stream dropped: trying again by itself
        if (reconnect > 0 && error == null && s.live) {
            Column(Modifier.align(Alignment.Center).clip(Rnd(22.dp)).background(Color(0xE60E101A)).border(1.dp, Color(0x1FFFFFFF), Rnd(22.dp)).padding(horizontal = 30.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                T("Spajam ponovo…", 20.sp, Color.White, FontWeight.Bold)
                T(trf("Veza s kanalom %s je prekinuta", item.title), 13.sp, Color(0xFFC8CBD4), maxLines = 2)
                T(trf("Pokušaj %s od 3", reconnect), 13.sp, Color(0xFFC8CBD4))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..3).forEach { k -> Box(Modifier.width(28.dp).height(4.dp).clip(Pill).background(if (k <= reconnect) Color.White else Color(0x33FFFFFF))) }
                }
            }
        }
        // sleep timer: stops and goes home when the time is up
        LaunchedEffect(Sleep.until) {
            val u = Sleep.until; if (u <= 0) return@LaunchedEffect
            delay((u - System.currentTimeMillis()).coerceAtLeast(0))
            if (Sleep.until == u) { Sleep.until = 0; player.pause(); nav.root(Screen.Home) }
        }

        LaunchedEffect(playing, buffering) { saver = false; if (!playing && !buffering) { delay(10 * 60_000L); saver = true } }
        if (saver) ScreenSaver(item.title)

        error?.let { msg ->
            Column(Modifier.align(Alignment.Center).clip(Rnd(20.dp)).background(Color(0xE6151A23)).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                T(msg, 18.sp, Color.White, FontWeight.SemiBold, maxLines = 3)
                T(if (s.live) "OK pokušaj ponovo · ▲ ▼ drugi kanal · Nazad za izlaz" else "OK pokušaj ponovo · Meni › Eksterni player · Nazad za izlaz", 14.sp, Color(0xFFB6BCC6), maxLines = 2)
            }
        }

        if (showInfo && side == null) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(if (Look.overlay == 1) Brush.verticalGradient(listOf(C.OverlayBar, C.OverlayBar)) else Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                .padding(start = 48.dp, end = 48.dp, top = 70.dp, bottom = 34.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        if (s.live) ChannelLogo(item.logo, item.title)
                        Column(Modifier.weight(1f)) {
                            if (s.live) T("${item.subtitle ?: ""}  ·  ${index + 1}/$n", 14.sp, Color(0xFFB6BCC6))
                            T(item.title, 26.sp, Color.White, FontWeight.Bold)
                            if (!s.live && item.subtitle != null) T(item.subtitle, 15.sp, Color(0xFFD5DBE1))
                            nowNext.getOrNull(0)?.let { T(tr("Sada") + ": ${it.first}  ${it.second}", 15.sp, Color.White) }
                            nowNext.getOrNull(1)?.let { T(tr("Zatim") + ": ${it.first}  ${it.second}", 13.sp, Color(0xFFB6BCC6)) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (videoH > 0) Badge(qualityLabel(videoH), Color(0x33FFFFFF), Color.White)
                            if (fps > 0) Badge("${fps.toInt()} FPS", Color(0x33FFFFFF), Color.White)
                            if (channels > 0) Badge(if (channels >= 6) "5.1" else "Stereo", Color(0x33FFFFFF), Color.White)
                            if (subFile != null) Badge(tr("TITL"), Color(0x6622C55E), Color.White)
                            if (s.live) { Dot(Color(0xFFFF3B41)); T("UŽIVO", 13.sp, Color(0xFFFF8A8E), FontWeight.Bold) }
                        }
                    }
                    if (!s.live && duration > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            T(fmt(position), 13.sp, Color.White)
                            Box(Modifier.weight(1f).height(5.dp).clip(Rnd(3.dp)).background(Color(0x40FFFFFF))) {
                                Box(Modifier.fillMaxWidth((position.toFloat() / duration).coerceIn(0f, 1f)).height(5.dp).background(C.Red))
                            }
                            T(fmt(duration), 13.sp, Color(0xFFB6BCC6))
                        }
                    }
                    val v = player.videoFormat; val a = player.audioFormat
                    if (v != null) T(listOfNotNull("${v.width}×${v.height}", v.sampleMimeType?.substringAfter('/')?.uppercase(), if (v.frameRate > 0) "${v.frameRate.toInt()} fps" else null,
                        a?.let { (it.sampleMimeType?.substringAfter('/')?.uppercase() ?: "") + " " + if (it.channelCount >= 6) "5.1" else "Stereo" }).joinToString(" · "), 12.sp, Color(0xFF9AA4B2))
                    if (bar) PlayerBar(s.live, playing, item.channel != null,
                        onPause = { if (player.isPlaying) player.pause() else player.play() },
                        onAction = { a2 -> bar = false; when (a2) { "epg" -> side = "epg"; "channels" -> side = "channels"; "format" -> { resize = (resize + 1) % 3; bar = true }; else -> dialog = a2 } })
                    else T(if (s.live) "▲ ▼ kanal · ◀ lista kanala · ▶ TV vodič · OK još jednom = dugmad"
                      else trf("OK pauza · ◀ ▶ %s s · ▼ = dugmad (audio, titlovi, format slike)", Prefs.seekStep), 13.sp, Color(0xFFB6BCC6))
                }
            }
        }
        if (!playing && !s.live && error == null && !buffering && resume == null) {
            Box(Modifier.align(Alignment.Center).size(84.dp).clip(Rnd(42.dp)).background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                Ico(R.drawable.lc_pause, 36.dp, Color.White)
            }
        }

        // ------------------------------------------------------------ live: channel list / groups on the left
        if (side == "channels" || side == "groups") SideLists(side!!, sideGroup, items, index, p,
            onGroups = { side = "groups" }, onClose = { side = null; sideGroup = null },
            onGroup = { g -> sideGroup = g; side = "channels" },
            onChannel = { list, i ->
                if (sideGroup != null) { items = list.map { c -> PlayItem(c.name, c.url, c.logo, "${c.num}", c) }; TvMemory.group = sideGroup }
                index = i; side = null; sideGroup = null
            })
        // ------------------------------------------------------------ live: guide on the right, picture small
        if (side == "epg") {
            val ch = item.channel
            Column(Modifier.align(Alignment.TopEnd).fillMaxWidth(0.4f).fillMaxHeight().background(C.Overlay).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChannelLogo(item.logo, item.title)
                    T(item.title, 17.sp, C.Text, FontWeight.SemiBold, modifier = Modifier.weight(1f))
                }
                T("◀ ili Nazad = slika preko cijelog ekrana · OK na emisiji = podsjeti me", 11.sp, C.Muted, maxLines = 2)
                val fr = remember { FocusRequester() }
                Box(Modifier.weight(1f).focusRequester(fr).onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft) { side = null; true } else false
                }) { if (ch != null) EpgList(ch, 24) else T("Nema EPG podataka za ovaj kanal.", 14.sp, C.Soft) }
                LaunchedEffect(Unit) { delay(150); fr.tryFocus() }
            }
        }
    }

    // ---------------------------------------------------------------- continue from where you stopped
    resume?.let { r ->
        Popup(item.title, onDismiss = { resume = null; player.play() }) {
            OptionRow(trf("Nastavi od %s", fmt(r.pos)), R.drawable.lc_play, sub = trf("od %s", fmt(r.dur))) { resume = null; player.seekTo(r.pos); player.play() }
            OptionRow("Od početka", R.drawable.lc_rotate_ccw) { resume = null; player.seekTo(0); player.play() }
        }
    }

    when (dialog) {
        "menu" -> Popup(item.title, onDismiss = { dialog = null }) {
            val ch = item.channel
            if (s.live && ch != null) {
                OptionRow(if (Favs.inAny(ctx, ch)) "Liste favorita…" else "Dodaj u favorite", R.drawable.lc_star, color = C.Amber) { dialog = "fav" }
                OptionRow("TV vodič", R.drawable.lc_calendar_clock) { dialog = null; side = "epg" }
                OptionRow("Lista kanala", R.drawable.lc_list) { dialog = null; side = "channels" }
            }
            OptionRow("Audio", R.drawable.lc_volume, value = tracksOf(player, MC.TRACK_TYPE_AUDIO).firstOrNull { it.selected }?.label ?: "—") { dialog = "audio" }
            OptionRow("Titlovi", R.drawable.lc_captions, value = if (subFile != null) tr("Učitan") else tracksOf(player, MC.TRACK_TYPE_TEXT).firstOrNull { it.selected }?.label ?: tr("Isključeni")) { dialog = "subs" }
            OptionRow("Format slike", R.drawable.lc_monitor, value = tr(listOf("Prilagodi", "Zumiraj", "Rastegni")[resize])) { resize = (resize + 1) % 3 }
            OptionRow("Kvalitet i informacije", R.drawable.lc_info, value = if (videoH > 0) qualityLabel(videoH) else null) { dialog = "info" }
            if (!s.live) OptionRow("Eksterni player", R.drawable.lc_external_link) { player.pause(); saveProgress(); dialog = "ext" }
        }
        "fav" -> item.channel?.let { FavPicker(it) { dialog = null } }
        "ext" -> ExternalPicker(item.url, item.title, player.currentPosition, onDismiss = { dialog = null })
        "audio" -> {
            val tracks = tracksOf(player, MC.TRACK_TYPE_AUDIO)
            Popup("Audio", onDismiss = { dialog = null }) {
                if (tracks.size <= 1) T("Film ima samo jednu audio stazu", 14.sp, C.Muted)
                tracks.forEach { t ->
                    OptionRow(t.label, R.drawable.lc_volume, selected = t.selected) {
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                            .setOverrideForType(TrackSelectionOverride(t.group.mediaTrackGroup, t.index)).build()
                        dialog = null
                    }
                }
            }
        }
        "subs" -> {
            val tracks = tracksOf(player, MC.TRACK_TYPE_TEXT)
            SubtitlePicker(item.search ?: item.title, item.season, item.episode, embedded = tracks.map { it.label },
                onEmbedded = { i ->
                    val t = tracks[i]
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(MC.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(TrackSelectionOverride(t.group.mediaTrackGroup, t.index)).build()
                    dialog = null
                },
                onDismiss = { dialog = null }) { f ->
                if (f == null) {
                    subFile = null
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(MC.TRACK_TYPE_TEXT, true).build()
                } else {
                    subFile = f.path
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(MC.TRACK_TYPE_TEXT, false).build()
                    loadMedia(item, f.path, player.currentPosition)
                    Toast.makeText(ctx, tr("Titl učitan"), Toast.LENGTH_SHORT).show()
                }
                dialog = null
            }
        }
        "sleep" -> Popup("Tajmer za spavanje", onDismiss = { dialog = null }) {
            T("Kad vrijeme istekne, gledanje stane i aplikacija se vrati na početnu.", 14.sp, C.Soft, maxLines = 3)
            OptionRow("Isključen", if (Sleep.until <= 0) R.drawable.lc_check else R.drawable.lc_moon, color = if (Sleep.until <= 0) C.Green else null) { Sleep.until = 0; dialog = null }
            listOf(15, 30, 60, 90).forEach { m ->
                OptionRow(trf("%s min", m), R.drawable.lc_moon) { Sleep.until = System.currentTimeMillis() + m * 60_000L; dialog = null }
            }
            nowNext.getOrNull(0)?.let { now ->
                val end = Regex("(\\d{1,2}):(\\d{2})").findAll(now.first).lastOrNull()
                if (end != null) OptionRow("Na kraju emisije", R.drawable.lc_moon, value = end.value) {
                    val c = java.util.Calendar.getInstance(); c.set(java.util.Calendar.HOUR_OF_DAY, end.groupValues[1].toInt()); c.set(java.util.Calendar.MINUTE, end.groupValues[2].toInt()); c.set(java.util.Calendar.SECOND, 0)
                    if (c.timeInMillis < System.currentTimeMillis()) c.add(java.util.Calendar.DAY_OF_MONTH, 1)
                    Sleep.until = c.timeInMillis; dialog = null
                }
            }
        }
        "info" -> Popup("Kvalitet i informacije", onDismiss = { dialog = null }) {
            val v = player.videoFormat; val a = player.audioFormat
            OptionRow("Slika", R.drawable.lc_monitor, value = if (v != null) "${qualityLabel(v.height)} · ${v.width}×${v.height}" + (if (v.frameRate > 0) " · ${v.frameRate.toInt()} FPS" else "") else "—") {}
            OptionRow("Video kodek", R.drawable.lc_file_video, value = v?.sampleMimeType?.substringAfter('/')?.uppercase() ?: "—") {}
            OptionRow("Zvuk", R.drawable.lc_volume, value = if (a != null) "${a.sampleMimeType?.substringAfter('/')?.uppercase() ?: ""} · ${if (a.channelCount >= 6) "5.1" else if (a.channelCount == 1) "Mono" else "Stereo"}" else "—") {}
            val br = v?.bitrate?.takeIf { it > 0 }?.toLong() ?: bitrate.takeIf { it > 0 }
            OptionRow("Bitrate", R.drawable.lc_gauge2, value = br?.let { String.format(Locale.US, "%.1f Mbps", it / 1_000_000.0) } ?: "—") {}
        }
    }

    LaunchedEffect(dialog, side, resume, bar) { if (dialog == null && side == null && resume == null && !bar) focus.tryFocus() }
}

/** Buttons at the bottom of the player. */
@Composable
private fun PlayerBar(live: Boolean, playing: Boolean, hasChannel: Boolean, onPause: () -> Unit, onAction: (String) -> Unit) {
    val first = remember { FocusRequester() }
    val list = buildList {
        add(Triple("pause", if (playing) "Pauza" else "Pusti", if (playing) R.drawable.lc_pause else R.drawable.lc_play))
        if (live && hasChannel) add(Triple("fav", "Favorit", R.drawable.lc_star))
        add(Triple("subs", "Titlovi", R.drawable.lc_captions))
        add(Triple("audio", "Audio", R.drawable.lc_volume))
        add(Triple("format", "Format slike", R.drawable.lc_maximize))
        add(Triple("sleep", if (Sleep.until > 0) trf("Tajmer: %s min", ((Sleep.until - System.currentTimeMillis()) / 60_000 + 1).coerceAtLeast(1)) else tr("Tajmer za spavanje"), R.drawable.lc_moon))
        add(Triple("info", "Kvalitet", R.drawable.lc_gauge2))
        if (live) { add(Triple("epg", "Vodič", R.drawable.lc_calendar_clock)); add(Triple("channels", "Grupa", R.drawable.lc_list)) }
        add(Triple("menu", "Više", R.drawable.lc_layout_grid))
        if (!live) add(Triple("ext", "Eksterni", R.drawable.lc_external_link))
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(list) { i, (id, label, icon) ->
            Focusable(onClick = { if (id == "pause") onPause() else onAction(id) }, focusRequester = if (i == 0) first else null,
                normal = Color(0x33FFFFFF), focused = C.Red, shape = Rnd(12.dp), zoom = 1.05f, modifier = Modifier.height(44.dp)) { f ->
                Row(Modifier.align(Alignment.Center).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Ico(icon, 18.dp, if (f) C.OnRed else Color.White)
                    T(label, 14.sp, if (f) C.OnRed else Color.White, FontWeight.SemiBold)
                }
            }
        }
    }
    LaunchedEffect(Unit) { delay(80); first.tryFocus() }
}

/** Live: the channels of the group (Left again: the groups) over the picture. */
@Composable
private fun SideLists(mode: String, sideGroup: GroupRef?, items: List<PlayItem>, index: Int, p: Playlist?,
                      onGroups: () -> Unit, onClose: () -> Unit, onGroup: (GroupRef) -> Unit, onChannel: (List<Channel>, Int) -> Unit) {
    val ctx = LocalContext.current
    val fr = remember { FocusRequester() }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxHeight().width(380.dp).background(C.Overlay).padding(horizontal = 16.dp, vertical = 20.dp)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { if (mode == "channels") onGroups(); true }
                    Key.DirectionRight -> { onClose(); true }
                    else -> false
                }
            }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (mode == "groups") {
                T("Grupe", 20.sp, C.Text, FontWeight.Bold)
                val rows by load(Favs.rev) { groupRows(ctx).mapNotNull { (it as? GRow.Group)?.ref } }
                when (val s = rows) {
                    is Load.Ok -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        itemsIndexed(s.value, key = { _, g -> g.key }) { i, g ->
                            val sel = (sideGroup ?: TvMemory.group)?.key == g.key
                            Focusable(modifier = Modifier.fillMaxWidth().height(Rows.group), onClick = { onGroup(g) },
                                focusRequester = if (sel || (i == 0 && (sideGroup ?: TvMemory.group) == null)) fr else null,
                                normal = if (sel) C.CardFocus else Color.Transparent, zoom = 1f) { _ ->
                                T(g.cat.name, 15.sp, C.Text, if (sel) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 12.dp))
                            }
                        }
                    }
                    else -> Loading("Učitavam grupe…")
                }
            } else {
                T(sideGroup?.cat?.name ?: TvMemory.group?.cat?.name ?: tr("Kanali"), 20.sp, C.Text, FontWeight.Bold)
                T("◀ grupe · ▶ zatvori", 12.sp, C.Muted)
                val list by load(sideGroup?.key) { sideGroup?.let { channelsOf(ctx, it) } ?: items.mapNotNull { it.channel } }
                val state = rememberLazyListState()
                when (val s = list) {
                    is Load.Ok -> {
                        val cur = if (sideGroup == null) index else -1
                        LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            itemsIndexed(s.value, key = { i, c -> "$i${c.url}" }) { i, c ->
                                Focusable(modifier = Modifier.fillMaxWidth().height(Rows.channel), onClick = { onChannel(s.value, i) },
                                    focusRequester = if (i == cur.coerceAtLeast(0)) fr else null,
                                    normal = if (i == cur) C.CardFocus else C.Card, zoom = 1.01f) { f ->
                                    Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        T("${c.num}", 12.sp, C.Muted, modifier = Modifier.width(30.dp))
                                        ChannelLogo(c.logo, c.name)
                                        T(c.name, 14.sp, if (f) C.Text else C.Soft, if (f || i == cur) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                        LaunchedEffect(Unit) { if (cur > 3) state.scrollToItem(cur - 3); delay(80); fr.tryFocus() }
                    }
                    else -> Loading("Učitavam kanale…")
                }
            }
        }
    }
    LaunchedEffect(mode) { delay(120); fr.tryFocus() }
}

private fun fmt(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/** Sleep timer of the player (survives switching channels). */
object Sleep { var until by mutableLongStateOf(0L) }

private fun digitOf(k: Key): Int? = when (k) {
    Key.Zero, Key.NumPad0 -> 0; Key.One, Key.NumPad1 -> 1; Key.Two, Key.NumPad2 -> 2; Key.Three, Key.NumPad3 -> 3; Key.Four, Key.NumPad4 -> 4
    Key.Five, Key.NumPad5 -> 5; Key.Six, Key.NumPad6 -> 6; Key.Seven, Key.NumPad7 -> 7; Key.Eight, Key.NumPad8 -> 8; Key.Nine, Key.NumPad9 -> 9
    else -> null
}
