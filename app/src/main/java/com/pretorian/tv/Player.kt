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
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.ui.focus.onFocusChanged
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
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import java.io.File
import androidx.media3.common.C as MC

@OptIn(UnstableApi::class)
fun buildPlayer(ctx: Context, preview: Boolean = false): ExoPlayer {
    val http = DefaultHttpDataSource.Factory()
        .setUserAgent(Net.UA)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(30_000)
    // sound: by default the box decodes Dolby (AC3/E-AC3) itself and sends plain sound over HDMI.
    // Sending Dolby "as it is" (passthrough) gives silence on many TVs and soundbars - that was why some channels had no sound.
    val passthrough = Prefs.bool("audio_passthrough", false)
    val renderers = object : DefaultRenderersFactory(ctx) {
        @Suppress("DEPRECATION")
        override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): androidx.media3.exoplayer.audio.AudioSink? {
            val sink = if (passthrough) super.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams)
            else androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                .setAudioCapabilities(androidx.media3.exoplayer.audio.AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .build()
            // Settings › Player › Sound delay: the picture follows a moved sound clock (not in the small preview)
            return if (sink != null && !preview && Tech.audioDelay) DelaySink(sink) else sink
        }
    }
        // FFmpeg (built in) only steps in when the box itself has no decoder for a sound (AC3, E-AC3, MP2, DTS)
        .setExtensionRendererMode(if (Tech.ffmpeg) DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON else DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
        .setMediaCodecSelector(codecSelector())
        .setEnableDecoderFallback(true)
    val builder = ExoPlayer.Builder(ctx, renderers)
        .setMediaSourceFactory(DefaultMediaSourceFactory(ctx).setDataSourceFactory(DefaultDataSource.Factory(ctx, http)))
    // the small picture starts as soon as half a second is loaded
    // buffer from Settings › Player (automatic: from the box and the internet); it lives in the app's memory, so it is capped
    if (preview) builder.setLoadControl(androidx.media3.exoplayer.DefaultLoadControl.Builder()
        .setBufferDurationsMs(2_000, 15_000, 500, 1_000).setTargetBufferBytes(if (Perf.lite) 6 shl 20 else 12 shl 20).build())
    else {
        val mb = Buffer.mb
        val lc = androidx.media3.exoplayer.DefaultLoadControl.Builder().setTargetBufferBytes(mb shl 20).setPrioritizeTimeOverSizeThresholds(false)
        when {
            mb <= 32 -> lc.setBufferDurationsMs(15_000, 20_000, 1_500, 3_000)
            mb >= 96 -> lc.setBufferDurationsMs(30_000, 120_000, 3_000, 6_000)
            else -> {}
        }
        builder.setLoadControl(lc.build())
    }
    val player = builder.build()
    val audio = Prefs.audioLangs.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray()
    val b = player.trackSelectionParameters.buildUpon()
    if (!Prefs.bool("audio_default")) b.setPreferredAudioLanguages(*audio)
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
suspend fun probeTracks(ctx: Context, link: String): Pair<List<androidx.media3.common.Format>, List<androidx.media3.common.Format>>? {
    val url = try { Stalker.resolve(ctx, link) } catch (e: Exception) { return null }
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

private inline fun <reified T : Throwable> causeOf(e: Throwable): Boolean {
    var c: Throwable? = e
    while (c != null) { if (c is T) return true; c = c.cause }
    return false
}

/** A clear message for a stream that does not play, in the app language. */
private fun errorText(e: PlaybackException, live: Boolean): String {
    val code = httpCode(e)
    return when {
        code == 401 || code == 403 -> tr("Server odbija pristup: nalog istekao ili blokiran") + " (HTTP $code)" + (if (Net.vpnOn()) " · " + tr("možda je VPN adresa blokirana, probaj drugi VPN server") else "")
        code == 429 || code == 458 || code == 509 || code == 456 -> tr("Previše konekcija: ugasi gledanje na drugom uređaju") + " (HTTP $code)"
        code == 404 -> tr("Kanal više ne postoji kod providera") + " (HTTP 404)"
        code != null && code >= 500 -> tr("Server trenutno ne radi") + " (HTTP $code)"
        code != null -> tr("Server je odbio zahtjev") + " (HTTP $code)"
        causeOf<java.net.UnknownHostException>(e) -> tr("Adresa servera nije pronađena (DNS): provjeri internet, VPN ili NextDNS / privatni DNS")
        e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT || causeOf<java.net.SocketTimeoutException>(e) ->
            tr("Server ne odgovara (isteklo vrijeme)") + (if (Net.vpnOn()) " · " + tr("VPN je uključen") else "")
        e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
            tr("Nema veze sa serverom") + (if (Net.vpnOn()) " · " + tr("VPN je uključen") else "")
        else -> tr(if (live) "Kanal trenutno ne radi" else "Video se ne može pustiti") + " (${e.errorCodeName.removePrefix("ERROR_CODE_")})"
    }
}

private object PlayerChannelHistory {
    private const val KEY = "player_channel_recent"
    var rev by androidx.compose.runtime.mutableIntStateOf(0)
    fun list(): List<Channel> = try {
        val a = org.json.JSONArray(Prefs.str(KEY, "[]"))
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val url = o.optString("url", "")
            if (url.isBlank()) return@mapNotNull null
            Channel(o.optString("id", ""), o.optInt("num", i + 1), o.optString("name", url),
                o.optString("logo", "").takeIf { it.isNotBlank() }, o.optString("group", ""), url,
                o.optString("epg", "").takeIf { it.isNotBlank() }, o.optString("pid", ""),
                o.optInt("archive", 0), o.optString("catchup", "").takeIf { it.isNotBlank() })
        }
    } catch (_: Exception) { emptyList() }
    fun add(ch: Channel) {
        val updated = (list().filterNot { it.url == ch.url }.let { listOf(ch) + it }).take(10)
        val a = org.json.JSONArray()
        updated.forEach { c -> a.put(org.json.JSONObject().put("id", c.id).put("num", c.num)
            .put("name", c.name).put("logo", c.logo ?: "").put("group", c.group).put("url", c.url)
            .put("epg", c.epg ?: "").put("pid", c.pid).put("archive", c.archive).put("catchup", c.catchup ?: "")) }
        Prefs.put(KEY, a.toString()); rev++
    }
    fun clear() { Prefs.put(KEY, "[]"); rev++ }
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(s: Screen.Player, nav: Nav) {
    // a match from Sport keeps its other channels only while this player is open
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { MatchWatch.clear() } }
    val ctx = LocalContext.current
    var items by remember { mutableStateOf(s.items) }
    val n = items.size
    var index by remember { mutableIntStateOf(s.index.coerceIn(0, (s.items.size - 1).coerceAtLeast(0))) }
    var showInfo by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retried by remember { mutableStateOf(false) }
    var retryToken by remember { mutableIntStateOf(0) }
    var reconnect by remember { mutableIntStateOf(0) }          // live: attempt 1..3 of getting the stream back
    val backupTried = remember { HashSet<String>() }             // links already tried as backup for this channel
    var shiftFrom by remember { mutableLongStateOf(0L) }          // live: ◀◀ in the archive, 0 = live
    var gaveUp by remember { mutableStateOf(false) }              // 4 quiet tries did not bring the picture back
    var frozenMsg by remember { mutableStateOf(false) }           // no backup either: say so and keep trying every 20 s
    var digits by remember { mutableStateOf("") }              // channel number typed on the remote
    var digitTick by remember { mutableIntStateOf(0) }
    var prevIndex by remember { mutableIntStateOf(-1) }
    var saver by remember { mutableStateOf(false) }   // screen saver after a long pause
    var buffering by remember { mutableStateOf(true) }
    var favAsk by remember { mutableStateOf<Channel?>(null) }
    var okLong by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var skipPill by remember { mutableStateOf<String?>(null) }
    var creditsLeft10 by remember { mutableIntStateOf(10) }
    var duration by remember { mutableLongStateOf(0L) }
    var videoH by remember { mutableIntStateOf(0) }
    var videoW by remember { mutableIntStateOf(0) }
    var fps by remember { mutableStateOf(0f) }
    var bitrate by remember { mutableLongStateOf(0L) }
    var resUntil by remember { mutableLongStateOf(0L) }
    var channels by remember { mutableIntStateOf(0) }
    var liveGuide by remember { mutableStateOf<List<EpgItem>>(emptyList()) }
    var openGuide by remember { mutableStateOf(false) }
    var nowNext by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var notice by remember { mutableStateOf<String?>(null) }
    var noticeSequence by remember { mutableIntStateOf(0) }
    fun inform(message: String) { notice = message; noticeSequence++ }
    LaunchedEffect(noticeSequence) { if (notice != null) { delay(1500); notice = null } }
    var dialog by remember { mutableStateOf<String?>(null) }   // menu, audio, subs, info, fav, ext

    var side by remember { mutableStateOf<String?>(null) }     // channels, groups, epg (live only)
    // the player owns the focus: a left menu that held it before must not block the lists here
    remember { Chrome.inRail = false; 0 }
    var sideGroup by remember { mutableStateOf<GroupRef?>(null) }
    var resume by remember { mutableStateOf<Progress?>(null) }
    var bar by remember { mutableStateOf(false) }   // button bar at the bottom
    var subFile by remember { mutableStateOf(s.items.getOrNull(index)?.subFile) }
    // picture format: 0 Auto, 1 4:3, 2 16:9, 3 Fit, 4 Stretch, 5 Original — remembered per channel
    var resize by remember { mutableIntStateOf(0) }
    val player = remember { Preview.stop(); buildPlayer(ctx) }
    val playerScope = rememberCoroutineScope()
    val mediaRequest = remember { PlaybackRequest() }
    val readyOnce = remember { booleanArrayOf(false) }   // the stream played since it was (re)loaded
    val applied = remember { booleanArrayOf(false) }
    val step = Prefs.seekStep * 1000L
    val focus = remember { FocusRequester() }
    val item = items.getOrElse(index) { items.first() }
    var moreSelection by remember(item.url) { mutableStateOf<String?>(null) }
    LaunchedEffect(dialog) { if (dialog == null) moreSelection = null }
    val p = item.channel?.let { Store.byId(ctx, it.pid) } ?: Store.ownerOf(ctx, item.url) ?: Store.active(ctx)

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
        val request = mediaRequest.begin()
        val selectedIndex = index
        // Stalker portal: the real address is asked for right before playing
        if (Stalker.isLink(item.url)) {
            playerScope.launch {
                try {
                    val resolved = Stalker.resolve(ctx, item.url)
                    if (mediaRequest.current(request) && index == selectedIndex) loadMedia(item.copy(url = resolved), file, at, play)
                }
                catch (e: Exception) { if (mediaRequest.current(request) && index == selectedIndex) error = e.message ?: tr("Portal nije dao link") }
            }
            return
        }
        val b = MediaItem.Builder().setUri(Net.udp(item.url))
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
        readyOnce[0] = false
        player.setMediaItem(b.build(), at)
        player.prepare()
        player.playWhenReady = play
    }

    /** remembers where you are in a movie or episode (continue watching, watched marks) */
    fun saveProgress(done: Boolean = false, background: Boolean = false) {
        if (s.live) return
        val it = items.getOrNull(index) ?: return
        val dur = player.duration.takeIf { d -> d > 0 } ?: return
        val pos = if (done) dur else player.currentPosition
        if ((done || pos >= dur * 0.9) && Watch.get(ctx, it.url)?.watched != true) {
            if (it.season != null) Stats.episode(it.search ?: it.title, it.season) else Stats.film()
        }
        val current = Progress(it.url, it.title, it.logo, pos, dur, System.currentTimeMillis(), it.search.takeIf { _ -> it.season != null },
            it.season, it.episode, next = false, done = done || pos >= dur * 0.9, pid = p?.id ?: "")
        val next = if (current.done && it.season != null) items.getOrNull(index + 1)
            ?.takeIf { nx -> Watch.get(ctx, nx.url)?.watched != true }
            ?.let { nx -> Progress(nx.url, nx.title, nx.logo, 0, 0, System.currentTimeMillis() + 1, nx.search, nx.season, nx.episode, next = true, pid = p?.id ?: "") }
        else null
        Watch.save(ctx, current, next, background)
    }

    /** ◀◀ / ▶▶ on a live channel with archive: 5 minutes per press through the provider's archive; past "now" = live again */
    fun liveShift(dir: Int) {
        val cur = items.getOrNull(index) ?: return
        val ch = cur.channel ?: return
        if (!Tech.rewind || !Catchups.has(p, ch)) { Toast.makeText(ctx, tr("Ovaj kanal nema arhivu za premotavanje"), Toast.LENGTH_SHORT).show(); return }
        val now = System.currentTimeMillis()
        val base = if (shiftFrom > 0) shiftFrom + (player.currentPosition.coerceAtLeast(0)) else now
        val to = (base + dir * 5 * 60_000L).coerceAtLeast(now - ch.archive * 86_400_000L)
        if (to >= now - 30_000L) {
            shiftFrom = 0L; loadMedia(cur, null, 0L)
            Toast.makeText(ctx, tr("Uživo"), Toast.LENGTH_SHORT).show(); return
        }
        val u = Catchups.url(p, ch, EpgItem("", null, to, now + 3 * 3600_000L)) ?: return
        shiftFrom = to
        loadMedia(cur.copy(url = u), null, 0L)
        Toast.makeText(ctx, trf("◀◀ %s min iza uživo", ((now - to) / 60_000).toInt()), Toast.LENGTH_SHORT).show()
    }

    /** the quiet tries are used up: the same channel from elsewhere (backup stream), otherwise a clear message — never a silent freeze */
    fun giveUp(e: PlaybackException?) {
        val cur = items.getOrNull(index)
        fun fail() { if (e != null) error = errorText(e, s.live) else frozenMsg = true }
        if (s.live && (Tech.backup || MatchWatch.on(cur?.url)) && cur?.channel != null && shiftFrom == 0L) {
            val request = mediaRequest.begin()
            val selectedIndex = index
            backupTried += cur.url
            val tried = backupTried.toSet()
            playerScope.launch {
                // a match: first another of your channels that shows the same match, then the same channel from elsewhere
                val alt = (if (MatchWatch.on(cur.url)) MatchWatch.next(tried) else null) ?: if (!Tech.backup) null else try { StreamBackup.find(ctx, cur.channel, tried) } catch (_: Exception) { null }
                if (!mediaRequest.current(request) || index != selectedIndex || items.getOrNull(index) != cur) return@launch
                if (alt != null) {
                    backupTried += alt.url
                    Toast.makeText(ctx, trf("Kanal ne radi · prebacujem na rezervni: %s", alt.name), Toast.LENGTH_LONG).show()
                    items = items.toMutableList().also { l -> l[index] = cur.copy(url = alt.url, channel = alt) }
                    reconnect = 0; gaveUp = false; frozenMsg = false
                    loadMedia(items[index], null, 0L)
                } else fail()
            }
        } else fail()
    }

    DisposableEffect(Unit) {
        val l = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                // automatic recovery: up to 3 quiet tries (live and films), keeping the position of a film
                if (Recover.on && reconnect < 4) { reconnect++; Stats.recovery(); StreamLog.event(if (reconnect == 1) "freeze" else "x", items.getOrNull(index)?.title ?: ""); StreamLog.event("try", items.getOrNull(index)?.title ?: ""); StreamLog.add(listOf(items.getOrNull(index)?.title ?: "", " · ", e.errorCodeName.removePrefix("ERROR_CODE_"), " · ", trf("spajam ponovo (%s/4)", reconnect)).joinToString("")); retryToken++ }  // i18n-ignore
                else if (!Recover.on && s.live && reconnect < 3) { reconnect++; retryToken++ }
                else if (!Recover.on && !s.live && !retried) { retried = true; retryToken++ }
                else { reconnect = 0; giveUp(e) }
            }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) { if (reconnect > 0) StreamLog.event("ok", items.getOrNull(index)?.title ?: ""); if (reconnect > 0) StreamLog.add(listOf(items.getOrNull(index)?.title ?: "", " · ", tr("radi ponovo ✓")).joinToString("")); error = null; reconnect = 0; readyOnce[0] = true }  // i18n-ignore
                if (state == Player.STATE_ENDED && !s.live) { saveProgress(done = true); if (index < n - 1 && Prefs.bool("next_ep", true)) index++ }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onTracksChanged(tracks: Tracks) {
                player.videoFormat?.let {
                    if (it.height != videoH) resUntil = System.currentTimeMillis() + 6000
                    videoH = it.height; videoW = it.width; fps = it.frameRate
                    // Settings › Player › Auto frame rate: the TV takes the video's refresh rate
                    if (it.frameRate > 1f) Afr.apply(ctx as? android.app.Activity, it.frameRate)
                }
                player.audioFormat?.let { channels = it.channelCount }
                if (!applied[0] && tracks.groups.isNotEmpty()) { applied[0] = true; applyChoices(items[index]) }
            }
        }
        val a = object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onBandwidthEstimate(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, totalLoadTimeMs: Int, totalBytesLoaded: Long, bitrateEstimate: Long) {
                bitrate = bitrateEstimate
                // data used for Diagnostics: the bytes loaded since the last estimate (works for live TV too)
                val it = items.getOrNull(index)
                if (totalBytesLoaded > 0) Stats.bytes(if (s.live) "tv" else if (it?.season != null) "ser" else "mov", totalBytesLoaded)
            }
        }
        player.addListener(l); player.addAnalyticsListener(a)
        onDispose {
            mediaRequest.begin()
            saveProgress(); player.removeListener(l); player.removeAnalyticsListener(a); player.release()
            Afr.reset(ctx as? android.app.Activity); AudioDelay.us = 0L
            // leaving the player: big lists and pictures are let go at once (they come back from the disk when needed)
            if (Lean.trim) { Repo.trim(true); try { coil.Coil.imageLoader(ctx).memoryCache?.trimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) } catch (_: Exception) {} }
        }
    }

    var lastIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(index, items) {
        if (lastIndex >= 0 && lastIndex != index) prevIndex = lastIndex
        lastIndex = index
        if (s.live) { TvMemory.channel = index; TvMemory.returnUrl = items.getOrNull(index)?.url }
        reconnect = 0
        if (s.live) items.getOrNull(index)?.channel?.let { c ->
            val g = TvMemory.group
            try { Recent.add(c, g?.pid, g?.cat?.id ?: c.group, g?.cat?.name ?: c.group) } catch (_: Exception) {}
            PlayerChannelHistory.add(c)
        }
        error = null; retried = false; showInfo = !s.live; videoH = 0
        liveGuide = emptyList(); openGuide = false
        val cur = items[index]
        AudioDelay.use(cur.url)   // sound delay kept for this channel / film
        if (cur.channel?.url != items.getOrNull(prevIndex)?.channel?.url) backupTried.clear()
        shiftFrom = 0L
        // a subtitle fixed with SYNC earlier for this film comes back by itself
        subFile = cur.subFile ?: if (!s.live) SubSync.kept(cur.url) else null
        val saved = if (!s.live) Watch.get(ctx, cur.url) else null
        val rm = Prefs.str("resume_mode", "0")
        if (saved != null && saved.started && cur.subFile == null && rm == "0") { resume = saved; loadMedia(cur, subFile, saved.pos, play = false) }
        else if (saved != null && saved.started && cur.subFile == null && rm == "1") loadMedia(cur, subFile, saved.pos)
        else loadMedia(cur, subFile, 0L)
        nowNext = emptyList()
        val ch = cur.channel
        if (s.live && ch != null && p != null) {
            liveGuide = Repo.epg(p, ch, 24)
            nowNext = liveGuide.take(2).map { it.title to it.times() }
            if (!bar && side == null && dialog == null) showInfo = liveGuide.any { System.currentTimeMillis() in it.start until it.end }
        }
    }
    LaunchedEffect(retryToken) {
        if (retryToken == 0) return@LaunchedEffect
        delay(if (Recover.on) when (reconnect) { 1 -> 300L; 2 -> 1500L; else -> 3000L }
              else if (s.live) when (reconnect) { 2 -> 5000L; 3 -> 10_000L; else -> 2000L } else 2000L)
        // the last try is the hand trick: close the stream completely and open it again, like going to another channel and back
        if (Recover.on && reconnect >= 3) { try { player.stop(); player.clearMediaItems() } catch (_: Exception) {}; delay(400) }
        shiftFrom = 0L
        loadMedia(items[index], subFile, if (s.live) 0L else player.currentPosition)
    }
    // the guide of this channel was moved: now / next in the info bar follow at once
    LaunchedEffect(EpgShift.rev) { val c = item.channel; if (EpgShift.rev > 0 && s.live && c != null && p != null) nowNext = try { Repo.nowNext(p, c) } catch (_: Exception) { nowNext } }
    LaunchedEffect(showInfo, index, playing, bar) { if (showInfo && !bar && (s.live || playing)) { delay(if (s.live) 2500 else 5000); showInfo = false } }
    LaunchedEffect(Unit) { while (true) { position = player.currentPosition; duration = player.duration.coerceAtLeast(0); delay(500) } }
    // time watched for Settings › Diagnostics
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            if (player.isPlaying) { val it = items.getOrNull(index); Stats.watch(if (s.live) "tv" else if (it?.season != null) "ser" else "mov", 10_000, if (s.live) it?.title else null) }
            if (buffering && player.playWhenReady) Stats.stall(10_000)
        }
    }
    // a stream that freezes (no progress for a few seconds) is quietly connected again, like switching away and back
    // three signs of a frozen stream: the time stops, the picture stops (time still running) or no new data comes in
    LaunchedEffect(index) {
        var last = -1L; var lastFrames = -1; var lastBuf = -1L; var stuck = 0L; var why = ""
        while (true) {
            delay(500)
            if (!Recover.on || error != null || resume != null || !player.playWhenReady || saver || !readyOnce[0]) { stuck = 0; last = -1; lastFrames = -1; continue }
            val pos = player.currentPosition
            val counters = player.videoDecoderCounters?.also { it.ensureUpdated() }
            val frames = counters?.renderedOutputBufferCount ?: -1
            val buf = player.bufferedPosition
            val state = player.playbackState
            val active = state == Player.STATE_BUFFERING || state == Player.STATE_READY
            val timeStuck = pos == last
            val picStuck = videoH > 0 && frames >= 0 && frames == lastFrames && state == Player.STATE_READY
            val dur = player.duration
            val dataStuck = buf == lastBuf && player.totalBufferedDuration < 800 && (dur <= 0 || buf < dur - 3000)
            if (active && (timeStuck || picStuck || dataStuck)) {
                stuck += 500; why = if (picStuck && !timeStuck) "slika stoji" else if (timeStuck) "vrijeme stoji" else "ne stižu podaci"
            } else {
                stuck = 0
                // the picture moves again: back to normal
                if ((gaveUp || frozenMsg) && pos != last && last >= 0) { gaveUp = false; frozenMsg = false; reconnect = 0 }
            }
            last = pos; lastFrames = frames; lastBuf = buf
            // a match from Sport: stuck 2 s → reconnect up to 2 times → another channel with the same match
            val match = MatchWatch.on(items.getOrNull(index)?.url)
            val waitMs = if (match) 2000L else Recover.waitMs
            val tries = if (match) 2 else 4
            if (stuck >= waitMs && reconnect < tries) {
                stuck = 0; reconnect++; Stats.recovery()
                (items.getOrNull(index)?.title ?: "").let { t -> if (reconnect == 1) StreamLog.event("freeze", t); StreamLog.event("try", t) }
                StreamLog.add(listOf(items.getOrNull(index)?.title ?: "", " · ", tr(why), " · ", trf("spajam ponovo (%s/4)", reconnect)).joinToString(""))  // i18n-ignore
                retryToken++
            } else if (stuck >= waitMs && reconnect >= tries) {
                // 4 tries did not help: backup stream once, then a new try every 20 s with a message on screen
                if (!gaveUp) { gaveUp = true; stuck = 0; giveUp(null) }
                else if (stuck >= 20_000L) {
                    stuck = 0; frozenMsg = true
                    StreamLog.add(listOf(items.getOrNull(index)?.title ?: "", " · ", tr("pokušavam ponovo svakih 20 s")).joinToString(""))  // i18n-ignore
                    retryToken++
                }
            }
        }
    }
    LaunchedEffect(index) { gaveUp = false; frozenMsg = false; if (!s.live) while (true) { delay(15_000); if (player.isPlaying) saveProgress(background = true) } }

    LaunchedEffect(openGuide, item.url) {
        if (!openGuide) return@LaunchedEffect
        val channel = item.channel
        val guide = if (channel != null && p != null) Repo.epg(p, channel, 24) else emptyList()
        openGuide = false
        if (guide.isEmpty()) inform(tr("Nema EPG podataka za ovaj kanal."))
        else { liveGuide = guide; bar = false; showInfo = false; side = "epg" }
    }

    BackHandler(enabled = dialog != null || side != null || resume != null || bar || openGuide || (s.live && showInfo)) {
        when {
            openGuide -> { openGuide = false; notice = null; showInfo = false; bar = false }
            resume != null -> { resume = null; player.seekTo(0); player.play() }
            dialog != null -> dialog = null
            bar -> { bar = false; showInfo = false; notice = null }
            side == null && showInfo -> { showInfo = false; notice = null }
            side == "channels" -> side = "groups"
            side == "groups" -> { side = null; sideGroup = null; nav.back() }
            else -> { side = null; sideGroup = null }
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(focus)
            .onPreviewKeyEvent { e ->
                // screen saver: OK goes on watching at once, any other button only closes it
                if (saver) {
                    if (e.type == KeyEventType.KeyDown) {
                        saver = false
                        if (e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.MediaPlay || e.key == Key.MediaPlayPause) player.play()
                        // a live channel never stays frozen behind the saver: start it again
                        if (s.live && !player.isPlaying) { player.playWhenReady = true; retryToken++ }
                    }
                    return@onPreviewKeyEvent true
                }
                // live TV: short OK pauses / plays, holding OK asks to add the channel to favourites
                val okKey = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                if (s.live && okKey && dialog == null && side == null && resume == null && !bar && error == null && digits.isEmpty() && favAsk == null) {
                    if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) { okLong = false; return@onPreviewKeyEvent true }
                    if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount > 0) {
                        if (!okLong) { okLong = true; items.getOrNull(index)?.channel?.let { favAsk = it } }
                        return@onPreviewKeyEvent true
                    }
                    if (e.type == KeyEventType.KeyUp) {
                        if (!okLong) { if (player.isPlaying) player.pause() else player.play(); showInfo = true }
                        okLong = false
                        return@onPreviewKeyEvent true
                    }
                }
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
                // series: OK on "Skip intro" / "Next episode" (the app remembers where they are in this series)
                val ser = if (item.season != null) item.search else null
                if (ser != null && skipPill != null && (e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter)) {
                    val pos = player.currentPosition; val dur = player.duration; val mem = SkipMem.of(ser)
                    if (skipPill == "recap") {
                        // to the start of the intro if it is known (the recap is right before it), else 60 s on – remembered for the series
                        val to = if (mem.introEnd > 0 && mem.introStart > pos + 5_000L) mem.introStart else pos + 60_000L
                        SkipMem.learnRecap(ser, to); player.seekTo(to)
                    } else if (skipPill == "intro") { if (mem.introEnd > 0) player.seekTo(mem.introEnd) else { SkipMem.learnIntro(ser, pos, pos + 85_000L); player.seekTo(pos + 85_000L) } }
                    else { if (dur > 0 && mem.creditsLeft == 0L) SkipMem.learnCredits(ser, dur - pos); saveProgress(done = true); if (index < n - 1) index++ }
                    skipPill = null
                    return@onPreviewKeyEvent true
                }
                // ⏭ near the end: from now on the next episode starts at this point of every episode of the series
                if (ser != null && e.key == Key.MediaNext && index < n - 1) {
                    val left = player.duration - player.currentPosition
                    if (left in 15_000L..600_000L) SkipMem.learnCredits(ser, left)
                    saveProgress(done = true); index++
                    return@onPreviewKeyEvent true
                }
                when (e.key) {
                    Key.Menu, Key.Info -> { dialog = "menu"; true }
                    Key.DirectionUp, Key.ChannelUp -> if (s.live && (e.key == Key.ChannelUp || Prefs.bool("zap_updown", true))) { index = (index - 1 + n) % n; true } else { showInfo = true; true }
                    // ▼ opens the channel options (the bar); CH− changes the channel
                    Key.DirectionDown, Key.ChannelDown -> if (s.live && e.key == Key.ChannelDown) { index = (index + 1) % n; true }
                        else { bar = true; showInfo = true; true }
                    // live with archive: ◀◀ goes 5 minutes back through the provider's archive, ▶▶ forward (back to live at the end)
                    Key.MediaRewind -> if (!s.live) { player.seekTo((player.currentPosition - step).coerceAtLeast(0)); showInfo = true; true }
                        else { liveShift(-1); true }
                    Key.MediaFastForward -> if (!s.live) { player.seekTo(player.currentPosition + step); showInfo = true; true }
                        else { liveShift(1); true }
                    Key.DirectionLeft -> if (!s.live) { player.seekTo((player.currentPosition - step).coerceAtLeast(0)); showInfo = true; true }
                        else { side = "channels"; showInfo = false; true }
                    Key.DirectionRight -> if (!s.live) { player.seekTo(player.currentPosition + step); showInfo = true; true }
                        else { openGuide = true; true }
                    Key.MediaPause, Key.MediaPlay -> { if (!s.live || Tech.livePause) { if (e.key == Key.MediaPause) player.pause() else player.play() }; showInfo = true; true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause -> {
                        if (s.live) {
                            if (e.key != Key.MediaPlayPause) { showInfo = true; bar = true }
                            else { if (Tech.livePause) { if (player.isPlaying) player.pause() else player.play() }; showInfo = true }
                        }
                        else { if (player.isPlaying) player.pause() else player.play(); showInfo = true }
                        true
                    }
                    else -> false
                }
            }
            .focusable(),
    ) {
        // the picture always stays full screen; the lists and the guide open over it (no black hole, no resizing of the picture)
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                factory = { c ->
                    PlayerView(c).apply {
                        this.player = player
                        useController = false
                        keepScreenOn = true
                        isFocusable = false
                        descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        setKeepContentOnPlayerReset(true)   // a silent reconnect keeps the last picture on screen
                        // subtitle size and look from Settings › Player
                        subtitleView?.let { sv ->
                            val size = listOf(0.8f, 1f, 1.25f, 1.5f).getOrElse(Prefs.str("sub_size", "1").toIntOrNull() ?: 1) { 1f }
                            sv.setFractionalTextSize(androidx.media3.ui.SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * size)
                            val st = Prefs.str("sub_style", "0").toIntOrNull() ?: 0
                            sv.setApplyEmbeddedStyles(st == 0)
                            sv.setStyle(androidx.media3.ui.CaptionStyleCompat(android.graphics.Color.WHITE,
                                if (st == 2) 0xB3000000.toInt() else android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT,
                                if (st == 1) androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE else if (st == 0) androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW else androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE,
                                android.graphics.Color.BLACK, null))
                        }
                    }
                },
                update = { v ->
                    // a list on the left or the guide on the right: the subtitles move into the free part of the picture (never cut)
                    if (Prefs.bool("sub_shift", true)) v.subtitleView?.let { sv ->
                        val dens = v.resources.displayMetrics.density
                        val l = if (side == "channels" || side == "groups") (380 * dens).toInt() else 0
                        val r = if (side == "epg") (v.width * 0.4f).toInt() else 0
                        if (sv.paddingLeft != l || sv.paddingRight != r) sv.setPadding(l, sv.paddingTop, r, sv.paddingBottom)
                    }
                    v.resizeMode = when (resize) {
                        3 -> if (s.live) AspectRatioFrameLayout.RESIZE_MODE_FIT else AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                        4 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        5 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    } },
                modifier = when (resize) {
                    1 -> Modifier.fillMaxSize().wrapContentSize().aspectRatio(4f / 3f)
                    2 -> Modifier.fillMaxSize().wrapContentSize().aspectRatio(16f / 9f)
                    else -> Modifier.fillMaxSize()
                },
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
        if (Recover.on && reconnect >= 3 && error == null) {
            Row(Modifier.align(Alignment.TopEnd).padding(24.dp).clip(Pill).background(Color(0xCC0E101A)).padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xFFFFC53D)))
                T("Ponovo spajam…", 13.sp, Color.White, FontWeight.SemiBold)
            }
        }
        if (!Recover.on && reconnect > 0 && error == null && s.live) {
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

        // daily watching limit (Settings › Simple mode and PIN): when the time is used up, the PIN is needed to go on today
        var limitPin by remember { mutableStateOf(false) }
        LaunchedEffect(playing) {
            while (playing) {
                delay(60_000L)
                if (WatchLimit.tick() && !limitPin) { player.pause(); limitPin = true }
            }
        }
        if (limitPin) PinDialog("Dnevno vrijeme je isteklo", tr("Unesi PIN za još gledanja danas"), onDismiss = { limitPin = false; nav.back() }) { pin ->
            val err = Lock.check(pin); if (err == null) { WatchLimit.allowToday(); limitPin = false; player.play() }; err
        }

        favAsk?.let { ch ->
            val lists = Favs.lists(ctx)
            val inFav = Favs.inAny(ctx, ch)
            Popup(if (inFav) trf("Ukloniti %s iz favorita?", ch.name) else trf("Dodati %s u favorite?", ch.name), { favAsk = null }) {
                OptionRow("Da", R.drawable.lc_check, color = C.Green) {
                    if (inFav) lists.forEach { l -> if (Favs.contains(l.id, ch)) Favs.toggle(l.id, ch) }
                    else (lists.firstOrNull() ?: Favs.addList(ctx, tr("Favoriti"))).let { Favs.toggle(it.id, ch) }
                    android.widget.Toast.makeText(ctx, tr(if (inFav) "Uklonjeno iz favorita" else "Dodano u favorite"), android.widget.Toast.LENGTH_SHORT).show()
                    favAsk = null
                }
                OptionRow("Ne", R.drawable.lc_x) { favAsk = null }
            }
        }

        // series: "Skip intro" and "Next episode" when the end credits start (times learned per series)
        LaunchedEffect(index, s.live) {
            skipPill = null; var autoDone = false; var autoRecap = false; var creditsAt = 0L
            val ser = (if (item.season != null) item.search else null) ?: return@LaunchedEffect
            if (s.live) return@LaunchedEffect
            while (true) {
                delay(500)
                val pos = player.currentPosition; val dur = player.duration
                val mem = SkipMem.of(ser)
                val mode = Prefs.long("skip_intro", 0).toInt()   // 0 button, 1 by itself, 2 off
                var pill: String? = null
                // "Previously on…" at the very start: learned per series; a series whose intro starts in the first 20 s has no recap
                val rMode = Prefs.long("skip_recap", 0).toInt()
                if (rMode != 2) {
                    if (mem.recapEnd > 0 && pos in 2_000L until mem.recapEnd - 1500) {
                        if (rMode == 1 && !autoRecap) { autoRecap = true; player.seekTo(mem.recapEnd) } else pill = "recap"
                    } else if (mem.recapEnd == 0L && pos in 3_000L..60_000L && !(mem.introEnd > 0 && mem.introStart < 20_000L)) pill = "recap"
                }
                if (mode != 2 && pill == null) {
                    if (mem.introEnd > 0 && pos >= mem.introStart && pos < mem.introEnd - 1500) {
                        if (mode == 1 && !autoDone) { autoDone = true; player.seekTo(mem.introEnd) } else pill = "intro"
                    } else if (mem.introEnd == 0L && pos in 5_000L..180_000L) pill = "intro"
                }
                if (Prefs.bool("skip_credits", true) && index < n - 1 && dur > 120_000L) {
                    val thr = if (mem.creditsLeft > 0) mem.creditsLeft else 40_000L
                    if (dur - pos in 1..thr) { pill = "credits"; if (creditsAt == 0L) creditsAt = System.currentTimeMillis() }
                    else creditsAt = 0L
                    if (creditsAt > 0) { creditsLeft10 = (10 - ((System.currentTimeMillis() - creditsAt) / 1000).toInt()).coerceAtLeast(0); if (creditsLeft10 == 0) { saveProgress(done = true); index++; break } }
                }
                skipPill = pill
            }
        }
        // paused and no remote for the chosen time (every key press starts the count again)
        // only a film or an episode that YOU paused: never a live channel, never a stream that stalls or reconnects
        LaunchedEffect(playing, buffering, saver) {
            if (saver) { if (playing) saver = false; return@LaunchedEffect }
            if (s.live || playing || buffering || !Saver.on || player.playWhenReady) return@LaunchedEffect
            val pausedAt = System.currentTimeMillis()
            while (true) {
                delay(1000)
                if (player.playWhenReady || s.live) break
                // counted from the pause or the last key, whichever is later
                if (System.currentTimeMillis() - maxOf(pausedAt, Saver.lastKey) > Saver.delayMs) { saver = true; break }
            }
        }
        // "Skip intro" / "Next episode in 10 s" in the lower right corner
        skipPill?.let { k ->
            Box(Modifier.fillMaxSize().padding(end = 40.dp, bottom = 90.dp), contentAlignment = Alignment.BottomEnd) {
                Row(Modifier.clip(Pill).background(Color.White).padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    T(when (k) { "recap" -> tr("Preskoči prethodno"); "intro" -> tr("Preskoči uvod"); else -> trf("Sljedeća epizoda za %s s", creditsLeft10) }, 15.sp, Color(0xFF0B0F1A), FontWeight.Bold)
                    T("OK", 11.sp, Color(0xFF64748B), FontWeight.Bold)  // i18n-ignore
                }
            }
        }
        // watching behind live through the archive: how far, in the corner
        if (s.live && shiftFrom > 0 && error == null) Box(Modifier.fillMaxSize().padding(top = 28.dp, end = 32.dp), contentAlignment = Alignment.TopEnd) {
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(shiftFrom) { while (true) { now = System.currentTimeMillis(); delay(30_000) } }
            val mins = ((now - (shiftFrom + player.currentPosition.coerceAtLeast(0))) / 60_000).toInt().coerceAtLeast(1)
            Row(Modifier.clip(Pill).background(Color(0xCC000000)).padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Ico(R.drawable.lc_rotate_ccw, 15.dp, Color.White)
                T(trf("−%s min iza uživo", mins), 14.sp, Color.White, FontWeight.SemiBold)
            }
        }
        // the stream sends no picture and no backup was found: say it plainly (a new try runs every 20 s)
        if (frozenMsg && error == null && !saver) Box(Modifier.fillMaxSize().padding(bottom = 70.dp), contentAlignment = Alignment.BottomCenter) {
            Row(Modifier.clip(Pill).background(Color(0xCC000000)).padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                T("Kanal trenutno ne šalje sliku · pokušavam ponovo…", 15.sp, Color.White, FontWeight.SemiBold)
            }
        }
        if (saver) ModernScreenSaver(if (s.live) null else Paused(if (item.season != null) "Serija" else "Film", item.search?.let { cleanTitle(it).first } ?: item.title,
            listOfNotNull(item.season?.let { trf("Sezona %s", it) }, item.episode?.let { trf("Epizoda %s", it) }, item.subtitle).joinToString(" · ").ifBlank { null },
            item.logo, null, position, duration))

        error?.let { msg ->
            Column(Modifier.align(Alignment.Center).clip(Rnd(20.dp)).background(Color(0xE6151A23)).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                T(msg, 18.sp, Color.White, FontWeight.SemiBold, maxLines = 3)
                T(if (s.live) "OK pokušaj ponovo · ▲ ▼ drugi kanal · Nazad za izlaz" else "OK pokušaj ponovo · Meni › Eksterni player · Nazad za izlaz", 14.sp, Color(0xFFB6BCC6), maxLines = 2)
            }
        }

        notice?.let { message ->
            Box(Modifier.align(Alignment.TopCenter).padding(top = 40.dp).clip(Rnd(9.dp)).background(Color(0xCC181818)).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Ico(R.drawable.lc_info, 18.dp, Color.White)
                    T(message, 15.sp, Color.White)
                }
            }
        }
        if (s.live && (showInfo || side != null || dialog != null)) {
            val time = java.util.Date()
            val current = liveGuide.firstOrNull { time.time in it.start until it.end }
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color(0x80000000)).padding(horizontal = 32.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChannelLogo(item.logo, item.title)
                Column(Modifier.weight(1f)) {
                    T(listOfNotNull(item.subtitle, item.title).joinToString("  "), 20.sp, Color.White, FontWeight.Bold)
                    current?.let { T(it.title, 15.sp, Color.White); ThinBar(it.progress(time.time), C.Red, Modifier.width(160.dp)) }
                    if (bar) liveGuide.firstOrNull { it.start > time.time }?.let { T(tr("Zatim") + " · " + it.title, 13.sp, Color.LightGray) }
                }
                T("UŽIVO", 13.sp, Color.White)
                Column(horizontalAlignment = Alignment.End) {
                    T(java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(time), 18.sp, Color.White)
                    T(java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL, Locale(Look.lang)).format(time), 13.sp, Color.LightGray)
                }
            }
        }
        if (showInfo && side == null && (!s.live || bar)) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(if (Look.overlay == 1) Brush.verticalGradient(listOf(C.OverlayBar, C.OverlayBar)) else Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                .padding(start = 48.dp, end = 48.dp, top = 70.dp, bottom = 34.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!s.live) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
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
                    if (!s.live && v != null) T(listOfNotNull("${v.width}×${v.height}", v.sampleMimeType?.substringAfter('/')?.uppercase(), if (v.frameRate > 0) "${v.frameRate.toInt()} fps" else null,
                        a?.let { (it.sampleMimeType?.substringAfter('/')?.uppercase() ?: "") + " " + if (it.channelCount >= 6) "5.1" else "Stereo" }).joinToString(" · "), 12.sp, Color(0xFF9AA4B2))
                    if (bar && s.live) {
                        val ch = item.channel
                        @Suppress("UNUSED_VARIABLE") val favRev = Favs.rev
                        ChannelOptionsBar(playing = playing, favOn = ch != null && Favs.inAny(ctx, ch), onAction = { action ->
                            when (action) {
                                "pause" -> if (player.playWhenReady) player.pause() else player.play()
                                "more" -> { bar = false; dialog = "menu" }
                                "archive" -> {
                                    if (ch != null && Catchups.has(p, ch)) dialog = "archive"
                                    else inform(tr("Arhiva nije dostupna za ovaj kanal."))
                                }
                                "favorites" -> ch?.let { c ->
                                    val lists = Favs.lists(ctx)
                                    val wasFavorite = Favs.inAny(ctx, c)
                                    if (wasFavorite) lists.forEach { l -> if (Favs.contains(l.id, c)) Favs.toggle(l.id, c) }
                                    else (lists.firstOrNull() ?: Favs.addList(ctx, tr("Favoriti"))).let { Favs.toggle(it.id, c) }
                                    Toast.makeText(ctx, tr(if (wasFavorite) "Uklonjeno iz favorita" else "Dodano u favorite"), Toast.LENGTH_SHORT).show()
                                }
                                "subs" -> { bar = false; dialog = "subs" }
                                "zoom" -> { bar = false; dialog = "zoom" }
                                "audio" -> { bar = false; dialog = "audio" }
                                "video" -> { bar = false; dialog = "video" }
                                "epg" -> { openGuide = true }
                                "recent" -> { bar = false; dialog = "recent" }
                            }
                        })
                    } else if (bar) {
                        val subT = tracksOf(player, MC.TRACK_TYPE_TEXT)
                        val subsOn = subFile != null || (!player.trackSelectionParameters.disabledTrackTypes.contains(MC.TRACK_TYPE_TEXT) && subT.any { it.selected })
                        VodPlayerBar(false, playing, false, favOn = false, hasSubs = subT.isNotEmpty(), subsOn = subsOn,
                            audioCount = tracksOf(player, MC.TRACK_TYPE_AUDIO).size, format = FORMATS[resize],
                            onPause = { if (player.isPlaying) player.pause() else player.play() },
                            onLong = { bar = false; dialog = "subs" },
                            onAction = { action -> when (action) {
                                "subs" -> { bar = false; dialog = "subs" }
                                "audio" -> { bar = false; dialog = "audio" }
                                "format" -> { resize = (resize + 1) % FORMATS.size }
                                "menu" -> { bar = false; dialog = "menu" }
                            } })
                    }
                    else T(if (s.live) "▲ ▼ kanal · ◀ lista kanala · ▶ EPG · OK još jednom = dugmad"
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
            },
            onBackToChannels = { side = null; sideGroup = null; nav.back() })
        // ------------------------------------------------------------ live: guide on the right, over the picture
        if (side == "epg") {
            val ch = item.channel
            Column(Modifier.align(Alignment.TopEnd).fillMaxWidth(0.4f).fillMaxHeight().background(C.Overlay).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChannelLogo(item.logo, item.title)
                    T(item.title, 17.sp, C.Text, FontWeight.SemiBold, modifier = Modifier.weight(1f))
                }
                if (ch != null) OptionRow("Pomak vremena", R.drawable.lc_clock,
                    value = EpgShift.label(EpgShift.chMin(ch.url))) { dialog = "epgshift" }
                if (Hints.show()) T("◀ ili Nazad = slika preko cijelog ekrana · OK na emisiji = podsjeti me", 11.sp, C.Muted, maxLines = 2)
                val fr = remember { FocusRequester() }
                Box(Modifier.weight(1f).focusRequester(fr).onPreviewKeyEvent { e ->
                    if (e.key == Key.Back || e.key == Key.Escape) { if (e.type == KeyEventType.KeyUp) side = null; true }
                    else if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft) { side = null; true } else false
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
            OptionRow("Ukloni nastavak", R.drawable.lc_x, sub = "Obriši zapamćenu poziciju za ovaj sadržaj") {
                Watch.remove(ctx, item.url)
                resume = null
                player.seekTo(0)
                player.play()
            }
        }
    }

    when (dialog) {
        "archive" -> item.channel?.let { channel ->
            var day by remember(channel.url) { mutableIntStateOf(0) }
            Popup("Arhiva", { dialog = null }, width = 800.dp) {
                Box(Modifier.fillMaxWidth().height(320.dp)) { Archive(channel, day, { day = it }, nav) }
            }
        }
        "menu" -> Popup(if (s.live) tr("Više") else item.title, onDismiss = { dialog = null }) {
            val returnFocus = remember { FocusRequester() }
            val menuSubs = "subs"
            val menuAudio = "audio"
            val menuSleep = "sleep"
            val menuInfo = "info"
            val menuEpgshift = "epgshift"
            val menuExt = "ext"
            val menuTimeshift = "timeshift"
            val menuAdelay = "adelay"
            fun requester(id: String) = returnFocus.takeIf { s.live && moreSelection == id }
            LaunchedEffect(Unit) { if (s.live && moreSelection != null) returnFocus.tryFocus() }

            if (s.live && tracksOf(player, MC.TRACK_TYPE_TEXT).isNotEmpty()) OptionRow("Titlovi", R.drawable.lc_captions,
                focusRequester = requester(menuSubs)) { moreSelection = menuSubs; dialog = menuSubs }
            if (tracksOf(player, MC.TRACK_TYPE_AUDIO).size > 1) OptionRow("Audio", R.drawable.lc_volume, value = tracksOf(player, MC.TRACK_TYPE_AUDIO).firstOrNull { it.selected }?.label ?: "—",
                focusRequester = requester(menuAudio)) { moreSelection = menuAudio; dialog = menuAudio }
            // Više: the rarer things, so the bar fits in one row
            OptionRow(if (Sleep.until > 0) trf("Tajmer: %s min", ((Sleep.until - System.currentTimeMillis()) / 60_000 + 1).coerceAtLeast(1)) else tr("Tajmer za spavanje"), R.drawable.lc_moon,
                focusRequester = requester(menuSleep)) { moreSelection = menuSleep; dialog = menuSleep }
            OptionRow("Kvalitet i informacije", R.drawable.lc_info, value = if (videoH > 0) qualityLabel(videoH) else null,
                focusRequester = requester(menuInfo)) { moreSelection = menuInfo; dialog = menuInfo }
            if (s.live) item.channel?.let { c -> OptionRow("Upravljanje EPG vodičem", R.drawable.lc_clock, value = EpgShift.label(EpgShift.chMin(c.url)),
                focusRequester = requester(menuEpgshift)) { moreSelection = menuEpgshift; dialog = menuEpgshift } }
            OptionRow("Vanjski plejer", R.drawable.lc_external_link,
                focusRequester = requester(menuExt)) { saveProgress(); moreSelection = menuExt; dialog = menuExt }
            if (s.live && player.isCurrentMediaItemLive && player.isCurrentMediaItemSeekable && duration > 0) OptionRow("Vremenski pomak uživo", R.drawable.lc_history,
                focusRequester = requester(menuTimeshift)) { moreSelection = menuTimeshift; dialog = menuTimeshift }
            if (!s.live && subFile != null) OptionRow("Podesi vrijeme titla", R.drawable.lc_clock, sub = "ako titl kasni ili žuri · SYNC") { dialog = "subsync" }
            if (Tech.audioDelay) OptionRow("Pomak zvuka", R.drawable.lc_volume, sub = tr("ako zvuk kasni ili žuri"), value = AudioDelay.label(AudioDelay.of(item.url)),
                focusRequester = requester(menuAdelay)) { moreSelection = menuAdelay; dialog = menuAdelay }
        }
        "fav" -> item.channel?.let { FavPicker(it) { dialog = null } }
        "epgshift" -> item.channel?.let { EpgShiftPopup(it) { dialog = if (s.live && moreSelection == "epgshift") "menu" else null } }
        "zoom" -> Popup("Format slike", onDismiss = { dialog = null }, width = 760.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Automatski / original", 3 to "Fit", 5 to "Zoom", 4 to "Rastegni").forEach { (mode, label) ->
                    val chosen = mode == resize
                    Focusable(modifier = Modifier.weight(1f).height(54.dp), onClick = {
                        resize = mode
                        item.channel?.let { Prefs.put("fmt_v2_" + it.url.hashCode(), mode.toLong()) }
                        dialog = null
                        bar = true
                    }, normal = Color(0xCC222222), borderColor = if (chosen) C.Red else C.Line, zoom = 1f) {
                        T(if (chosen) "✓ " + tr(label) else tr(label), 15.sp, Color.White, modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
        }
        "timeshift" -> {
            if (!player.isCurrentMediaItemLive || !player.isCurrentMediaItemSeekable || duration <= 0) {
                LaunchedEffect(Unit) { dialog = "menu" }
            } else Popup("Vremenski pomak uživo", { dialog = "menu" }) {
                T(fmt(position.coerceAtLeast(0)) + " / " + fmt(duration) + " · " + tr("UŽIVO"), 15.sp, C.Text)
                Box(Modifier.fillMaxWidth().height(4.dp).background(C.Line)) {
                    Box(Modifier.fillMaxWidth((position.toFloat() / duration).coerceIn(0f, 1f)).height(4.dp).background(C.Red))
                }
                OptionRow(if (playing) "Pauza" else "Nastavi") { if (player.playWhenReady) player.pause() else player.play() }
                OptionRow("Premotavanje unazad") { player.seekTo((player.currentPosition - step).coerceAtLeast(0)) }
                OptionRow("Premotaj unaprijed") { player.seekTo((player.currentPosition + step).coerceAtMost(duration)) }
                OptionRow("Vrati se na prijenos uživo") { player.seekToDefaultPosition(); player.play() }
            }
        }
        "recent" -> {
            @Suppress("UNUSED_VARIABLE") val historyRev = PlayerChannelHistory.rev
            val history = PlayerChannelHistory.list()
            Popup(trf("Historija · %s/10", history.size), onDismiss = { dialog = null }, width = 700.dp) {
                if (history.isEmpty()) T(tr("Nema gledanih kanala."), 14.sp, C.Muted)
                history.forEach { c ->
                    OptionRow("${c.num}. ${c.name}", R.drawable.lc_history, sub = c.group.takeIf { it.isNotBlank() },
                        selected = c.url == item.url) {
                        items = listOf(PlayItem(c.name, c.url, c.logo, c.num.toString(), c)) + items.filterNot { it.url == c.url }
                        index = 0; dialog = null; side = null; bar = false
                    }
                }
                if (history.isNotEmpty()) OptionRow(tr("Obriši historiju"), R.drawable.lc_trash_2, color = C.Danger) { dialog = "recent_clear" }
            }
        }
        "recent_clear" -> Popup(tr("Obrisati historiju?"), onDismiss = { dialog = "recent" }) {
            T(tr("Lista posljednjih gledanih kanala bit će obrisana."), 13.sp, C.Muted)
            OptionRow(tr("Obriši historiju"), R.drawable.lc_trash_2, color = C.Danger) { PlayerChannelHistory.clear(); dialog = "recent" }
            OptionRow("Odustani", R.drawable.lc_x) { dialog = "recent" }
        }
        "adelay" -> AudioDelayPopup(item.url) { dialog = null }
        "ext" -> ExternalPicker(item.url, item.title, player.currentPosition, onDismiss = { dialog = null },
            onBack = { dialog = if (s.live && moreSelection == "ext") "menu" else null })
        "subsync" -> {
            val base = remember { subFile }
            if (base == null) dialog = null
            else SubSyncDialog(File(base).nameWithoutExtension.take(30), onDismiss = { dialog = null },
                onPreview = { off -> if (off != 0L) try { val f = SubSync.shift(base, off); subFile = f.path; loadMedia(item, f.path, player.currentPosition) } catch (_: Exception) {} },
                onSync = { off ->
                    try {
                        val f = SubSync.sync(item.url, base, player.currentPosition, off)
                        subFile = f.path; loadMedia(item, f.path, player.currentPosition)
                        Toast.makeText(ctx, tr("Titl usklađen i snimljen za ovaj film"), Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) { Toast.makeText(ctx, tr("Ovaj titl se ne može uskladiti"), Toast.LENGTH_LONG).show() }
                    dialog = null
                },
                onReset = { subFile = base; loadMedia(item, base, player.currentPosition) })
        }
        "audio" -> {
            val tracks = tracksOf(player, MC.TRACK_TYPE_AUDIO)
            Popup("Audio", onDismiss = { dialog = if (s.live) "menu" else null }) {
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
            if (s.live) {
                val tracks = tracksOf(player, MC.TRACK_TYPE_TEXT)
                if (tracks.isEmpty()) {
                    LaunchedEffect(Unit) { dialog = "menu" }
                } else {
                    val off = player.trackSelectionParameters.disabledTrackTypes.contains(MC.TRACK_TYPE_TEXT) || tracks.none { it.selected }
                    Popup("Titlovi", onDismiss = { dialog = "menu" }) {
                        OptionRow("Isključeno", R.drawable.lc_x, selected = off) {
                            subFile = null
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                .setTrackTypeDisabled(MC.TRACK_TYPE_TEXT, true).build()
                            dialog = null
                        }
                        tracks.forEach { track ->
                            OptionRow(track.label, R.drawable.lc_captions, selected = !off && track.selected) {
                                subFile = null
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                    .setTrackTypeDisabled(MC.TRACK_TYPE_TEXT, false)
                                    .setOverrideForType(TrackSelectionOverride(track.group.mediaTrackGroup, track.index)).build()
                                dialog = null
                            }
                        }
                    }
                }
            } else {
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
        }
        "sleep" -> Popup("Tajmer za spavanje", onDismiss = { dialog = if (s.live) "menu" else null }) {
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
        "info" -> Popup("Kvalitet i informacije", onDismiss = { dialog = if (s.live) "menu" else null }) {
            val v = player.videoFormat; val a = player.audioFormat
            OptionRow("Slika", R.drawable.lc_monitor, value = if (v != null) "${qualityLabel(v.height)} · ${v.width}×${v.height}" + (if (v.frameRate > 0) " · ${v.frameRate.toInt()} FPS" else "") else "—") {}
            OptionRow("Video kodek", R.drawable.lc_file_video, value = v?.sampleMimeType?.substringAfter('/')?.uppercase() ?: "—") {}
            OptionRow("Zvuk", R.drawable.lc_volume, value = if (a != null) "${a.sampleMimeType?.substringAfter('/')?.uppercase() ?: ""} · ${if (a.channelCount >= 6) "5.1" else if (a.channelCount == 1) "Mono" else "Stereo"}" else "—") {}
            val br = v?.bitrate?.takeIf { it > 0 }?.toLong() ?: bitrate.takeIf { it > 0 }
            OptionRow("Bitrate", R.drawable.lc_gauge2, value = br?.let { String.format(Locale.US, "%.1f Mbps", it / 1_000_000.0) } ?: "—") {}
        }
    }

    LaunchedEffect(dialog, side, resume, bar) { if (dialog == null && side == null && resume == null && !bar) focus.tryFocus() }
    LaunchedEffect(item.url) { resize = item.channel?.let { Prefs.long("fmt_v2_" + it.url.hashCode(), 0).toInt().coerceIn(0, FORMATS.lastIndex) } ?: resize }
}

private val FORMATS = listOf("Zadano / automatski", "4:3", "16:9", "Prilagodi širinu", "Rastegni", "Zoom")

/** Buttons at the bottom of the player, in one row (no scrolling): Pause · ★ · [CC] · [Audio] · Format · Guide · Group · More.
 *  Subtitles only when the stream has subtitle tracks, Audio only with 2 or more tracks. */
@Composable
private fun ChannelOptionsBar(playing: Boolean, favOn: Boolean, onAction: (String) -> Unit) {
    val first = remember { FocusRequester() }
    data class B(val id: String, val label: String, val icon: Int)
    val list = listOf(
        B("pause", if (playing) "Pauza" else "Nastavi", if (playing) R.drawable.lc_pause else R.drawable.lc_play),
        B("favorites", "Favorit", R.drawable.lc_star),
        B("epg", "EPG", R.drawable.lc_calendar_clock),
        B("zoom", "Format slike", R.drawable.lc_maximize),
        B("archive", "Arhiva", R.drawable.lc_history),
        B("more", "Više", R.drawable.lc_settings),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        list.forEachIndexed { i, b ->
            val favorite = b.id == "favorites"
            Focusable(modifier = Modifier.weight(1f).height(70.dp), onClick = { onAction(b.id) },
                focusRequester = if (i == 0) first else null, normal = Color(0xBF191919),
                focused = Color(0xFFB00020), focusBorderColor = Color.White, borderColor = Color(0xFF71818D),
                shape = Rnd(10.dp), zoom = 1.015f) { focused ->
                Column(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Ico(b.icon, 23.dp, when {
                        favorite && favOn -> Color(0xFFF4C542)
                        favorite -> Color(0xFFB8C2CA)
                        focused -> Color.White
                        else -> Color(0xFFE2E8ED)
                    })
                    T(b.label, 11.sp, Color.White, FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
    LaunchedEffect(Unit) { delay(80); first.tryFocus() }
}
@Composable
private fun VodPlayerBar(live: Boolean, playing: Boolean, hasChannel: Boolean, favOn: Boolean, hasSubs: Boolean, subsOn: Boolean, audioCount: Int, format: String,
                      onPause: () -> Unit, onLong: (String) -> Unit, onAction: (String) -> Unit, rewind: Boolean = false, behind: Boolean = false) {
    val first = remember { FocusRequester() }
    data class B(val id: String, val label: String, val icon: Int, val tint: Color? = null)
    val list = buildList {
        add(B("pause", if (playing) "Pauza" else "Pusti", if (playing) R.drawable.lc_pause else R.drawable.lc_play))
        // channels with archive: back in time without ◀◀ keys on the remote, and straight back to live
        if (live && rewind) add(B("rew", "◀◀ 5 min", R.drawable.lc_rotate_ccw))
        if (live && rewind && behind) add(B("tolive", "Uživo", R.drawable.lc_radio, Color(0xFFEF4444)))
        if (live && hasChannel) add(B("fav", "Favorit", R.drawable.lc_star, if (favOn) Color(0xFFFBBF24) else null))
        if (hasSubs || !live) add(B("subs", "Titlovi", R.drawable.lc_captions, if (subsOn) Color(0xFF22C55E) else null))
        if (audioCount > 1) add(B("audio", "Audio", R.drawable.lc_volume))
        add(B("format", format, R.drawable.lc_maximize))
        if (live) { add(B("epg", "Vodič", R.drawable.lc_calendar_clock)); add(B("channels", "Grupa", R.drawable.lc_list)) }
        add(B("menu", "Više", R.drawable.lc_layout_grid))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        list.forEachIndexed { i, b ->
            Focusable(onClick = { if (b.id == "pause") onPause() else onAction(b.id) }, onMenu = if (b.id == "fav" || b.id == "subs") ({ onLong(b.id) }) else null,
                focusRequester = if (i == 0) first else null,
                normal = Color(0x33FFFFFF), focused = C.Red, shape = Rnd(12.dp), zoom = 1.05f, modifier = Modifier.height(42.dp)) { f ->
                Row(Modifier.align(Alignment.Center).padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // an "on" button keeps its colour also with focus (dark amber on the yellow focus, so it stays visible)
                    Ico(b.icon, 17.dp, if (b.tint != null && f) Color(0xFF92400E) else b.tint ?: if (f) C.OnRed else Color.White)
                    T(b.label, 13.sp, if (f) C.OnRed else Color.White, FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
    LaunchedEffect(Unit) { delay(80); first.tryFocus() }
}

/** Live: the channels of the group (Left again: the groups) over the picture. */
@Composable
private fun SideLists(mode: String, sideGroup: GroupRef?, items: List<PlayItem>, index: Int, p: Playlist?,
                      onGroups: () -> Unit, onClose: () -> Unit, onGroup: (GroupRef) -> Unit, onChannel: (List<Channel>, Int) -> Unit,
                      onBackToChannels: () -> Unit = {}) {
    val ctx = LocalContext.current
    val fr = remember { FocusRequester() }
    var has by remember { mutableStateOf(false) }
    // a slow list can come after the first focus try: keep trying for a few seconds until the panel holds the focus
    LaunchedEffect(mode) { repeat(30) { delay(200); if (!has) try { fr.requestFocus() } catch (_: Exception) {} } }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxHeight().width(380.dp).background(C.Overlay).padding(horizontal = 16.dp, vertical = 20.dp)
            .onFocusChanged { has = it.hasFocus }
            .onPreviewKeyEvent { e ->
                // Back works like Left: channels → groups → out of the player to TV
                if (e.key == Key.Back || e.key == Key.Escape) {
                    if (e.type == KeyEventType.KeyUp) { if (mode == "groups") onBackToChannels() else onGroups() }
                    return@onPreviewKeyEvent true
                }
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
                    is Load.Ok -> {
                        // the current group may be far down the list: scroll to it first, else the focus has nowhere to go (stuck panel)
                        val gList = rememberLazyListState()
                        val curKey = (sideGroup ?: TvMemory.group)?.key
                        val target = s.value.indexOfFirst { it.key == curKey }.coerceAtLeast(0)
                        LaunchedEffect(Unit) { gList.scrollToItem((target - 3).coerceAtLeast(0)); delay(60); fr.tryFocus() }
                        LazyColumn(state = gList, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        itemsIndexed(s.value, key = { _, g -> g.key }) { i, g ->
                            val sel = curKey == g.key
                            Focusable(modifier = Modifier.fillMaxWidth().height(Rows.group), onClick = { onGroup(g) },
                                focusRequester = if (i == target) fr else null,
                                normal = if (sel) C.CardFocus else Color.Transparent, zoom = 1f) { _ ->
                                T(g.cat.name, 15.sp, C.Text, if (sel) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 12.dp))
                            }
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
                                Focusable(modifier = Modifier.fillMaxWidth().height(ListStyle.sideRow), onClick = { onChannel(s.value, i) },
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

/** Daily watching limit: minutes counted per day; at the limit the PIN unlocks the rest of that day. */
object WatchLimit {
    private fun today() = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())  // i18n-ignore
    val limitMin get() = Prefs.str("watch_limit", "0").toIntOrNull() ?: 0
    /** adds one minute; true when the limit has just been reached */
    fun tick(): Boolean {
        val d = today()
        if (Prefs.str("watch_day") != d) { Prefs.put("watch_day", d); Prefs.put("watch_min", 0L); Prefs.put("watch_ok", "") }
        val used = Prefs.long("watch_min") + 1; Prefs.put("watch_min", used)
        return limitMin > 0 && Lock.hasPin() && used >= limitMin && Prefs.str("watch_ok") != d
    }
    fun allowToday() = Prefs.put("watch_ok", today())
}

/** Sleep timer of the player (survives switching channels). */
object Sleep { var until by mutableLongStateOf(0L) }

private fun digitOf(k: Key): Int? = when (k) {
    Key.Zero, Key.NumPad0 -> 0; Key.One, Key.NumPad1 -> 1; Key.Two, Key.NumPad2 -> 2; Key.Three, Key.NumPad3 -> 3; Key.Four, Key.NumPad4 -> 4
    Key.Five, Key.NumPad5 -> 5; Key.Six, Key.NumPad6 -> 6; Key.Seven, Key.NumPad7 -> 7; Key.Eight, Key.NumPad8 -> 8; Key.Nine, Key.NumPad9 -> 9
    else -> null
}


/** Where the intro and the end credits are, learned per series (the first time you skip them). */
object SkipMem {
    data class Mem(val introStart: Long, val introEnd: Long, val creditsLeft: Long, val recapEnd: Long = 0L)
    private fun key(series: String) = "skip_" + series.lowercase().hashCode()
    fun of(series: String): Mem {
        val p = Prefs.str(key(series)).split(',').map { it.toLongOrNull() ?: 0L }
        return Mem(p.getOrElse(0) { 0L }, p.getOrElse(1) { 0L }, p.getOrElse(2) { 0L }, p.getOrElse(3) { 0L })
    }
    private fun save(series: String, m: Mem) = Prefs.put(key(series), listOf(m.introStart, m.introEnd, m.creditsLeft, m.recapEnd).joinToString(","))
    fun learnIntro(series: String, start: Long, end: Long) = save(series, of(series).copy(introStart = (start - 2000).coerceAtLeast(0), introEnd = end))
    /** "Previously on…": where the recap ends (= where the episode really starts) */
    fun learnRecap(series: String, end: Long) = save(series, of(series).copy(recapEnd = end))
    fun learnCredits(series: String, left: Long) = save(series, of(series).copy(creditsLeft = left))
    fun forget(series: String) = Prefs.put(key(series), "")
}

/** Player › More › Sound delay: the picture waits for a late sound (or catches up with an early one); kept per channel / film. */
@Composable
fun AudioDelayPopup(url: String, onDismiss: () -> Unit) {
    val cur = AudioDelay.of(url)
    Popup(tr("Pomak zvuka"), onDismiss) {
        T("+ = zvuk kasni za slikom · − = zvuk žuri", 12.sp, C.Muted, maxLines = 2)
        listOf(0, 50, 100, 150, 200, 300, 400, 500, 750, 1000, -50, -100, -150, -200, -300, -500).forEach { ms ->
            OptionRow(AudioDelay.label(ms), if (ms == 0) R.drawable.lc_rotate_ccw else R.drawable.lc_volume, selected = ms == cur, color = if (ms == cur) C.Green else null) {
                AudioDelay.set(url, ms); onDismiss()
            }
        }
    }
}

internal class PlaybackRequest {
    private var generation = 0L
    fun begin(): Long = ++generation
    fun current(request: Long) = request == generation
}
