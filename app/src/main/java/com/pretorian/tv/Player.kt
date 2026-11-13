package com.pretorian.tv

import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

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
    return ExoPlayer.Builder(ctx, renderers)
        .setMediaSourceFactory(DefaultMediaSourceFactory(ctx).setDataSourceFactory(DefaultDataSource.Factory(ctx, http)))
        .build()
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
    val player = remember { buildPlayer(ctx) }
    val focus = remember { FocusRequester() }
    val p = remember { Store.active(ctx) }

    DisposableEffect(Unit) {
        val l = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                error = if (s.live) "Kanal trenutno ne radi (${e.errorCodeName})" else "Video se ne može pustiti (${e.errorCodeName})"
            }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED && !s.live && index < n - 1) index++
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }

    LaunchedEffect(index) {
        error = null
        showInfo = true
        val item = s.items[index]
        player.setMediaItem(MediaItem.fromUri(item.url))
        player.prepare()
        player.playWhenReady = true
    }
    LaunchedEffect(showInfo, index) { if (showInfo) { delay(5000); showInfo = false } }

    val item = s.items[index]
    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(focus)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (s.live) when (e.key) {
                    Key.DirectionUp, Key.ChannelUp -> { index = (index - 1 + n) % n; true }
                    Key.DirectionDown, Key.ChannelDown -> { index = (index + 1) % n; true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { showInfo = !showInfo; true }
                    Key.Menu -> {
                        val ch = item.channel
                        if (ch != null && p != null) {
                            Store.toggleFavorite(ctx, p.id, ch)
                            Toast.makeText(ctx, if (Store.isFavorite(ctx, p.id, ch)) "Dodano u favorite" else "Uklonjeno iz favorita", Toast.LENGTH_SHORT).show()
                        }
                        true
                    }
                    else -> false
                } else false
            }
            .focusable(),
    ) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    this.player = player
                    useController = !s.live
                    keepScreenOn = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    if (!s.live) { controllerShowTimeoutMs = 4000; isFocusable = true; post { requestFocus() } }
                    else { isFocusable = false; descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (buffering && error == null) {
            Box(Modifier.align(Alignment.Center)) { Loading("") }
        }

        error?.let { msg ->
            Column(Modifier.align(Alignment.Center).clip(RoundedCornerShape(20.dp)).background(Color(0xE6151A23)).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                T(msg, 18.sp, C.Text, FontWeight.SemiBold, maxLines = 2)
                T(if (s.live) "▲ ▼ za drugi kanal · Nazad za izlaz" else "Probaj Eksterni player · Nazad za izlaz", 14.sp, C.Muted)
            }
        }

        if (showInfo && (s.live || error == null)) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                .padding(start = 48.dp, end = 48.dp, top = 60.dp, bottom = if (s.live) 36.dp else 110.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    if (s.live) ChannelLogo(item.logo, item.title)
                    Column(Modifier.weight(1f)) {
                        if (s.live) T("${item.subtitle ?: ""}  ·  ${index + 1}/$n", 14.sp, C.Muted)
                        T(item.title, 26.sp, C.Text, FontWeight.Bold)
                        if (!s.live && item.subtitle != null) T(item.subtitle, 15.sp, C.Soft)
                        if (s.live) T("▲ ▼ promjena kanala · OK info · Meni = favorit · Nazad = lista", 13.sp, C.Muted)
                    }
                    if (s.live) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Dot(Color(0xFFFF3B41)); T("UŽIVO", 13.sp, C.RedText, FontWeight.Bold)
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { if (s.live) try { focus.requestFocus() } catch (_: Exception) {} }
}
