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
fun buildPlayer(ctx: Context): ExoPlayer {
    val http = DefaultHttpDataSource.Factory()
        .setUserAgent(Net.UA)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(30_000)
    val renderers = DefaultRenderersFactory(ctx)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        .setEnableDecoderFallback(true)
    val player = ExoPlayer.Builder(ctx, renderers)
        .setMediaSourceFactory(DefaultMediaSourceFactory(ctx).setDataSourceFactory(DefaultDataSource.Factory(ctx, http)))
        .build()
    val langs = Prefs.subLangs.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray()
    val audio = Prefs.audioLangs.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray()
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
        .setPreferredTextLanguages(*langs)
        .setPreferredAudioLanguages(*audio)
        .build()
    return player
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
            val f = g.getTrackFormat(i)
            val lang = f.language?.let { Subs.langName(it.take(2).lowercase()) } ?: "Nepoznat jezik"
            val extra = buildList {
                f.label?.let { add(it) }
                if (type == MC.TRACK_TYPE_AUDIO && f.channelCount > 0) add(when (f.channelCount) { 1 -> "Mono"; 2 -> "Stereo"; 6 -> "5.1"; 8 -> "7.1"; else -> "${f.channelCount} kan." })
                f.sampleMimeType?.substringAfter('/')?.uppercase()?.let { add(it) }
            }.joinToString(" · ")
            out.add(TrackItem(if (extra.isBlank()) lang else "$lang · $extra", g, i, g.isTrackSelected(i)))
        }
    }
    return out
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(s: Screen.Player, nav: Nav) {
    val ctx = LocalContext.current
    val n = s.items.size
    var index by remember { mutableIntStateOf(s.index.coerceIn(0, n - 1)) }
    var showInfo by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var buffering by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var videoH by remember { mutableIntStateOf(0) }
    var fps by remember { mutableStateOf(0f) }
    var channels by remember { mutableIntStateOf(0) }
    var nowNext by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var dialog by remember { mutableStateOf<String?>(null) }   // menu, audio, subs, info
    var subFile by remember { mutableStateOf(s.items.getOrNull(index)?.subFile) }
    var resize by remember { mutableIntStateOf(0) }
    val player = remember { buildPlayer(ctx) }
    val focus = remember { FocusRequester() }
    val p = remember { Store.active(ctx) }
    val item = s.items[index]

    DisposableEffect(Unit) {
        val l = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                error = if (s.live) "Kanal trenutno ne radi (${e.errorCodeName})" else "Video se ne može pustiti (${e.errorCodeName})"
            }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED && !s.live && index < n - 1) index++
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onTracksChanged(tracks: Tracks) {
                player.videoFormat?.let { videoH = it.height; fps = it.frameRate }
                player.audioFormat?.let { channels = it.channelCount }
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }

    fun loadMedia(item: PlayItem, file: String?, at: Long) {
        val b = MediaItem.Builder().setUri(item.url)
        if (file != null) b.setSubtitleConfigurations(listOf(
            MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(File(file)))
                .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                .setLanguage(File(file).nameWithoutExtension.substringAfterLast('_'))
                .setSelectionFlags(MC.SELECTION_FLAG_DEFAULT).build()))
        if (item.audioLang != null) player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguages(item.audioLang).build()
        player.setMediaItem(b.build(), at)
        player.prepare()
        player.playWhenReady = true
    }

    LaunchedEffect(index) {
        error = null; showInfo = true; videoH = 0
        val cur = s.items[index]
        subFile = cur.subFile
        loadMedia(cur, subFile, 0L)
        nowNext = emptyList()
        val ch = cur.channel
        if (s.live && ch != null && p != null) nowNext = Repo.nowNext(p, ch)
    }
    LaunchedEffect(showInfo, index, playing) { if (showInfo && (s.live || playing)) { delay(5000); showInfo = false } }
    LaunchedEffect(Unit) { while (true) { position = player.currentPosition; duration = player.duration.coerceAtLeast(0); delay(500) } }

    BackHandler(enabled = dialog != null) { dialog = null }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(focus)
            .onPreviewKeyEvent { e ->
                if (dialog != null || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.Menu, Key.Info -> { dialog = "menu"; true }
                    Key.DirectionUp, Key.ChannelUp -> if (s.live) { index = (index - 1 + n) % n; true } else { showInfo = true; true }
                    Key.DirectionDown, Key.ChannelDown -> if (s.live) { index = (index + 1) % n; true } else { dialog = "menu"; true }
                    Key.DirectionLeft, Key.MediaRewind -> if (!s.live) { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)); showInfo = true; true } else false
                    Key.DirectionRight, Key.MediaFastForward -> if (!s.live) { player.seekTo(player.currentPosition + 10_000); showInfo = true; true } else false
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause -> {
                        if (s.live) showInfo = !showInfo
                        else { if (player.isPlaying) player.pause() else player.play(); showInfo = true }
                        true
                    }
                    else -> false
                }
            }
            .focusable(),
    ) {
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

        if (buffering && error == null) Box(Modifier.align(Alignment.Center)) { Loading("") }

        error?.let { msg ->
            Column(Modifier.align(Alignment.Center).clip(RoundedCornerShape(20.dp)).background(Color(0xE6151A23)).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                T(msg, 18.sp, Color.White, FontWeight.SemiBold, maxLines = 2)
                T(if (s.live) "▲ ▼ drugi kanal · Nazad za izlaz" else "Meni › Eksterni player · Nazad za izlaz", 14.sp, Color(0xFFB6BCC6))
            }
        }

        if (showInfo) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                .padding(start = 48.dp, end = 48.dp, top = 70.dp, bottom = 34.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        if (s.live) ChannelLogo(item.logo, item.title)
                        Column(Modifier.weight(1f)) {
                            if (s.live) T("${item.subtitle ?: ""}  ·  ${index + 1}/$n", 14.sp, Color(0xFFB6BCC6))
                            T(item.title, 26.sp, Color.White, FontWeight.Bold)
                            if (!s.live && item.subtitle != null) T(item.subtitle, 15.sp, Color(0xFFD5DBE1))
                            nowNext.getOrNull(0)?.let { T("Sada: ${it.first}  ${it.second}", 15.sp, Color.White) }
                            nowNext.getOrNull(1)?.let { T("Zatim: ${it.first}  ${it.second}", 13.sp, Color(0xFFB6BCC6)) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (videoH > 0) Badge(qualityLabel(videoH), Color(0x33FFFFFF), Color.White)
                            if (fps > 0) Badge("${fps.toInt()} FPS", Color(0x33FFFFFF), Color.White)
                            if (channels > 0) Badge(if (channels >= 6) "5.1" else "Stereo", Color(0x33FFFFFF), Color.White)
                            if (subFile != null) Badge("TITL", Color(0x6622C55E), Color.White)
                            if (s.live) { Dot(Color(0xFFFF3B41)); T("UŽIVO", 13.sp, Color(0xFFFF8A8E), FontWeight.Bold) }
                        }
                    }
                    if (!s.live && duration > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            T(fmt(position), 13.sp, Color.White)
                            Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x40FFFFFF))) {
                                Box(Modifier.fillMaxWidth((position.toFloat() / duration).coerceIn(0f, 1f)).height(5.dp).background(C.Red))
                            }
                            T(fmt(duration), 13.sp, Color(0xFFB6BCC6))
                        }
                    }
                    T(if (s.live) "▲ ▼ kanal · OK info · Meni = opcije (favorit, audio, titl)"
                      else "OK pauza · ◀ ▶ 10 s · ▼ ili Meni = audio, titlovi, format slike", 13.sp, Color(0xFFB6BCC6))
                }
            }
        }
        if (!playing && !s.live && error == null && !buffering) {
            Box(Modifier.align(Alignment.Center).size(84.dp).clip(RoundedCornerShape(42.dp)).background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                Ico(R.drawable.lc_pause, 36.dp, Color.White)
            }
        }
    }

    when (dialog) {
        "menu" -> Popup(item.title, onDismiss = { dialog = null }) {
            val ch = item.channel
            if (s.live && ch != null && p != null) {
                val fav = Store.isFavorite(ctx, p.id, ch)
                OptionRow(if (fav) "Ukloni iz favorita" else "Dodaj u favorite", R.drawable.lc_star, color = if (fav) null else C.Amber) {
                    Store.toggleFavorite(ctx, p.id, ch)
                    Toast.makeText(ctx, if (fav) "Uklonjeno iz favorita" else "Dodano u favorite", Toast.LENGTH_SHORT).show()
                    dialog = null
                }
            }
            OptionRow("Audio", R.drawable.lc_volume, value = tracksOf(player, MC.TRACK_TYPE_AUDIO).firstOrNull { it.selected }?.label ?: "—") { dialog = "audio" }
            OptionRow("Titlovi", R.drawable.lc_captions, value = if (subFile != null) "Učitan" else tracksOf(player, MC.TRACK_TYPE_TEXT).firstOrNull { it.selected }?.label ?: "Isključeni") { dialog = "subs" }
            OptionRow("Format slike", R.drawable.lc_monitor, value = listOf("Prilagodi", "Zumiraj", "Rastegni")[resize]) { resize = (resize + 1) % 3 }
            OptionRow("Kvalitet i informacije", R.drawable.lc_info, value = if (videoH > 0) qualityLabel(videoH) else null) { dialog = "info" }
            if (!s.live) OptionRow("Eksterni player", R.drawable.lc_link) { player.pause(); openExternal(ctx, item.url, item.title); dialog = null }
        }
        "audio" -> {
            val tracks = tracksOf(player, MC.TRACK_TYPE_AUDIO)
            Popup("Audio", onDismiss = { dialog = null }) {
                if (tracks.isEmpty()) T("Ovaj video ima samo jednu stazu zvuka.", 14.sp, C.Muted)
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
                    Toast.makeText(ctx, "Titl učitan", Toast.LENGTH_SHORT).show()
                }
                dialog = null
            }
        }
        "info" -> Popup("Kvalitet i informacije", onDismiss = { dialog = null }) {
            val v = player.videoFormat; val a = player.audioFormat
            OptionRow("Slika", R.drawable.lc_monitor, value = if (v != null) "${qualityLabel(v.height)} · ${v.width}×${v.height}" + (if (v.frameRate > 0) " · ${v.frameRate.toInt()} FPS" else "") else "—") {}
            OptionRow("Video kodek", R.drawable.lc_file_video, value = v?.sampleMimeType?.substringAfter('/')?.uppercase() ?: "—") {}
            OptionRow("Zvuk", R.drawable.lc_volume, value = if (a != null) "${a.sampleMimeType?.substringAfter('/')?.uppercase() ?: ""} · ${if (a.channelCount >= 6) "5.1" else if (a.channelCount == 1) "Mono" else "Stereo"}" else "—") {}
            OptionRow("Bitrate", R.drawable.lc_gauge2, value = v?.bitrate?.takeIf { it > 0 }?.let { "${it / 1000} kbps" } ?: "—") {}
        }
    }

    LaunchedEffect(dialog) { if (dialog == null) try { focus.requestFocus() } catch (_: Exception) {} }
}

private fun fmt(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
