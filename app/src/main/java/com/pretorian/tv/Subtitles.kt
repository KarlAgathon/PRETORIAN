package com.pretorian.tv

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Format
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/** A built-in track of the stream, as found before playing. */
data class StreamTrack(val lang: String?, val label: String?, val title: String)

/**
 * Audio choice before playing: the stream is opened for a moment to read its real audio tracks.
 * With only one track there is nothing to choose, and the picker says so.
 */
@Composable
fun AudioPicker(url: String, current: String?, onDismiss: () -> Unit, onPick: (StreamTrack?) -> Unit) {
    val ctx = LocalContext.current
    var tracks by remember { mutableStateOf<List<Format>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(url) {
        val r = probeTracks(ctx, url)
        if (r == null) failed = true
        tracks = r?.first ?: emptyList()
    }
    Popup("Audio staze", onDismiss) {
        val list = tracks
        when {
            list == null -> Box(Modifier.fillMaxWidth().height(110.dp)) { Loading("Provjeravam audio staze…") }
            failed -> T("Staze se nisu mogle pročitati. Izaberi audio u plejeru: strelica dole › Audio.", 14.sp, C.Muted, maxLines = 3)
            list.size <= 1 -> {
                T("Film ima samo jednu audio stazu", 15.sp, C.Text)
                list.firstOrNull()?.let { OptionRow(trackLabel(it, true), R.drawable.lc_volume, selected = true) { onDismiss() } }
            }
            else -> {
                OptionRow("Automatski", R.drawable.lc_volume, sub = langList(Prefs.audioLangs), selected = current == null) { onPick(null) }
                list.forEach { f ->
                    val t = StreamTrack(iso2(f.language), f.label, trackLabel(f, true))
                    OptionRow(t.title, R.drawable.lc_volume, selected = current == t.title) { onPick(t) }
                }
            }
        }
    }
}

/**
 * Subtitle timing fix: shifts every time in an .srt / .vtt file. SYNC writes the whole file again and keeps it for this film,
 * a second SYNC later in the film stretches the times between the two points, so a subtitle that drifts stays right to the end.
 */
object SubSync {
    private val TIME = Regex("(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{3})")
    private fun ms(m: MatchResult) = m.groupValues.let { (it[1].toLong() * 3600 + it[2].toLong() * 60 + it[3].toLong()) * 1000 + it[4].toLong() }
    private fun fmt(t: Long, comma: Boolean): String {
        val v = t.coerceAtLeast(0)
        return String.format(java.util.Locale.US, "%02d:%02d:%02d%s%03d", v / 3_600_000, v / 60_000 % 60, v / 1000 % 60, if (comma) "," else ".", v % 1000)  // i18n-ignore
    }
    /** writes [src] shifted by [map] (old time → new time) into a new file next to it */
    private fun rewrite(src: File, map: (Long) -> Long): File {
        val text = src.readText()
        val out = text.lines().joinToString("\n") { line ->
            if (!line.contains("-->")) line else TIME.replace(line) { m -> fmt(map(ms(m)), m.value.contains(',')) }
        }
        val f = File(src.parentFile, src.nameWithoutExtension.substringBefore("_sync") + "_sync" + System.currentTimeMillis() % 100000 + "." + src.extension)
        f.writeText(out); return f
    }
    private fun key(url: String) = "subfix_" + url.hashCode()
    /** a fixed subtitle kept for this film or episode, or null */
    fun kept(url: String): String? = Prefs.str(key(url)).takeIf { it.isNotBlank() && File(it).exists() }
    /** shift by [offsetMs] (positive: the subtitle came too late, so it moves earlier) */
    fun shift(path: String, offsetMs: Long): File = rewrite(File(path)) { it - offsetMs }
    /** SYNC: keeps the fixed file for this film; the second SYNC at another point fixes the drift between the two */
    fun sync(url: String, path: String, atMs: Long, offsetMs: Long): File {
        val prevAt = Prefs.long(key(url) + "_at", -1L)
        val f = if (prevAt >= 0 && kotlin.math.abs(atMs - prevAt) > 5 * 60_000L) {
            // stretch: no change at the first point, the full correction at this point, in between and after in proportion
            rewrite(File(path)) { t -> t - offsetMs * (t - prevAt) / (atMs - prevAt) }
        } else rewrite(File(path)) { it - offsetMs }
        Prefs.put(key(url), f.path); Prefs.put(key(url) + "_at", atMs)
        return f
    }
    fun forget(url: String) { Prefs.remove(key(url), key(url) + "_at") }
}

/** FIX 8: the subtitle choice of each film / episode is kept ("f:<file>", "t:<language>" or "off") and comes back on resume. */
object SubMem {
    private fun key(url: String) = "submem_" + url.hashCode()
    fun get(url: String): String? = Prefs.str(key(url)).ifBlank { null }
    fun set(url: String, v: String) { Prefs.put(key(url), v) }
    /** the remembered file, when it is still on the box */
    fun file(v: String?): String? = v?.takeIf { it.startsWith("f:") }?.removePrefix("f:")?.takeIf { File(it).exists() }
}

/** FIX 7: the subtitle offset window, small at the bottom so the picture and the subtitle stay visible (live preview).
 *  ◀ ▶ 0,5 s (holding speeds up), quick steps ±1 / ±5 / ±10 s, range ±60 s; SYNC writes the whole file, Back closes without saving. */
@Composable
fun SubSyncDialog(name: String, onDismiss: () -> Unit, onPreview: (Long) -> Unit, onSync: (Long) -> Unit, onReset: () -> Unit) {
    var off by remember { mutableLongStateOf(0L) }
    val fr = remember { FocusRequester() }
    fun add(d: Long) { off = (off + d).coerceIn(-60_000L, 60_000L) }
    LaunchedEffect(off) { kotlinx.coroutines.delay(600); onPreview(off) }   // shows the change after a short pause, not on every press
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().padding(bottom = 36.dp), contentAlignment = Alignment.BottomCenter) {
            Column(Modifier.widthIn(max = 820.dp).fillMaxWidth(0.8f).clip(Rnd(22.dp)).background(Color(0xE6101418)).border(1.dp, C.Line, Rnd(22.dp))
                .padding(horizontal = 22.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(tr("Pomak titla") + " · " + name, 14.sp, C.Soft, FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    T("◀ ▶ 0,5 s", 12.sp, C.Muted)  // i18n-ignore
                }
                // the value: ◀ ▶ change it by 0.5 s; holding goes faster
                Focusable(onClick = {}, shape = Rnd(14.dp), normal = C.Card, focused = Silver.Focus, zoom = 1f, focusRequester = fr,
                    modifier = Modifier.fillMaxWidth().height(64.dp).onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val rep = e.nativeKeyEvent.repeatCount
                        val stepMs = when { rep > 24 -> 2000L; rep > 8 -> 1000L; else -> 500L }
                        when (e.key) { Key.DirectionLeft -> { add(-stepMs); true }; Key.DirectionRight -> { add(stepMs); true }; else -> false }
                    }) { _ ->
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        T((if (off > 0) "+" else "") + String.format(java.util.Locale.US, "%.1f s", off / 1000.0).replace('.', ','), 26.sp, C.Text, FontWeight.ExtraBold)  // i18n-ignore
                        T(if (off > 0) trf("titl je kasnio %s s", String.format(java.util.Locale.US, "%.1f", off / 1000.0)) else if (off < 0) trf("titl je žurio %s s", String.format(java.util.Locale.US, "%.1f", -off / 1000.0)) else tr("bez pomaka"), 12.sp, C.Muted)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(-10_000L to "−10 s", -5_000L to "−5 s", -1_000L to "−1 s", 1_000L to "+1 s", 5_000L to "+5 s", 10_000L to "+10 s").forEach { (d, l) ->  // i18n-ignore
                        Button(l, null) { add(d) }
                    }
                    Spacer(Modifier.weight(1f))
                    Button("SYNC", R.drawable.lc_check, primary = true) { onSync(off) }  // i18n-ignore
                    Button("Poništi", R.drawable.lc_rotate_ccw) { off = 0; onReset() }
                }
            }
        }
    }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); fr.tryFocus() }
}
