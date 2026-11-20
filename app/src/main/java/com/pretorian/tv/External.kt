package com.pretorian.tv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

/** An installed app that can open something (video player, file manager). */
data class AppChoice(val pkg: String, val label: String, val icon: android.graphics.drawable.Drawable?)

/** Video players on the box, and how each one wants a link. */
object ExtPlayer {
    /** time an external player was started, to notice when it closes right away */
    var launchedAt = 0L
    var lastUrl: String? = null

    var saved: String get() = Prefs.str("ext_player"); set(v) = Prefs.put("ext_player", v)
    /** movies and series always open in the external player */
    var always: Boolean get() = Prefs.bool("ext_always", false); set(v) = Prefs.put("ext_always", v)

    fun mime(url: String): String {
        val path = url.substringBefore('?').lowercase()
        return when {
            path.endsWith(".mkv") -> "video/x-matroska"
            path.endsWith(".mp4") || path.endsWith(".m4v") -> "video/mp4"
            path.endsWith(".ts") -> "video/mp2t"
            path.endsWith(".m3u8") -> "application/x-mpegURL"
            path.endsWith(".avi") -> "video/x-msvideo"
            path.endsWith(".webm") -> "video/webm"
            else -> "video/*"
        }
    }

    fun installed(ctx: Context): List<AppChoice> {
        val pm = ctx.packageManager
        val probe = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("http://example.com/v.mp4"), "video/*")
        @Suppress("DEPRECATION")
        val found = pm.queryIntentActivities(probe, 0).map { it.activityInfo.packageName }
        return found.distinct().filter { it != ctx.packageName }.mapNotNull { pkg ->
            try { val ai = pm.getApplicationInfo(pkg, 0); AppChoice(pkg, pm.getApplicationLabel(ai).toString(), pm.getApplicationIcon(pkg)) } catch (e: Exception) { null }
        }.sortedBy { if (it.pkg.startsWith("org.videolan")) 0 else 1 }
    }

    /** the intent each player understands: VLC, MX Player, Just Player, Kodi, mpv, others */
    fun intent(pkg: String, url: String, title: String, posMs: Long): Intent {
        val i = Intent(Intent.ACTION_VIEW).setPackage(pkg).setDataAndType(Uri.parse(url), mime(url))
        i.putExtra("title", title)
        when {
            pkg.startsWith("org.videolan.vlc") -> {
                i.setClassName(pkg, "org.videolan.vlc.gui.video.VideoPlayerActivity")
                i.putExtra("from_start", posMs <= 0); if (posMs > 0) i.putExtra("position", posMs)
            }
            pkg.startsWith("com.mxtech.videoplayer") -> { i.putExtra("position", posMs.toInt()); i.putExtra("return_result", true) }
            pkg == "com.brouken.player" -> i.putExtra("position", posMs.toInt())
        }
        return i
    }

    /** opens in the saved player; false when none is saved or it is gone */
    fun openSaved(ctx: Context, url: String, title: String, posMs: Long = 0): Boolean {
        val pkg = saved
        if (pkg.isBlank() || installed(ctx).none { it.pkg == pkg }) return false
        return launch(ctx, pkg, url, title, posMs)
    }

    fun launch(ctx: Context, pkg: String, url: String, title: String, posMs: Long): Boolean {
        return try {
            launchedAt = System.currentTimeMillis(); lastUrl = url
            ctx.startActivity(intent(pkg, url, title, posMs)); true
        } catch (e: Exception) {
            // VLC: some versions do not export the player screen, open it the usual way
            try { ctx.startActivity(Intent(Intent.ACTION_VIEW).setPackage(pkg).setDataAndType(Uri.parse(url), "video/*").putExtra("title", title)); true }
            catch (e2: Exception) { launchedAt = 0; Toast.makeText(ctx, tr("Player se ne može otvoriti"), Toast.LENGTH_LONG).show(); false }
        }
    }
}

@Composable
fun AppIcon(d: android.graphics.drawable.Drawable?) {
    val bmp = remember(d) { try { d?.toBitmap(96, 96)?.asImageBitmap() } catch (e: Exception) { null } }
    if (bmp != null) Image(BitmapPainter(bmp), contentDescription = null, modifier = Modifier.size(34.dp)) else Ico(R.drawable.lc_play, 22.dp)
}

/** Choose the external player, once or always. */
@Composable
fun ExternalPicker(url: String, title: String, posMs: Long, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val apps = remember { ExtPlayer.installed(ctx) }
    var selected by remember(url) { mutableStateOf<AppChoice?>(null) }
    var saved by remember { mutableStateOf(ExtPlayer.saved) }
    val choice = selected
    if (choice != null) {
        fun launch(rememberChoice: Boolean) {
            if (ExtPlayer.launch(ctx, choice.pkg, url, title, posMs)) {
                if (rememberChoice) ExtPlayer.saved = choice.pkg
                onDismiss()
            } else selected = null
        }
        Popup(trf("Otvori pomoću %s?", choice.label), { selected = null }) {
            OptionRow("Samo jednom", R.drawable.lc_play) { launch(false) }
            OptionRow("Zapamti moj izbor", R.drawable.lc_check) { launch(true) }
        }
    } else Popup("Vanjski plejer", onDismiss) {
        OptionRow("Ugrađeni plejer", R.drawable.lc_play, value = if (saved.isEmpty()) "✓" else null) {
            ExtPlayer.saved = ""; onDismiss()
        }
        apps.forEach { app ->
            Focusable(modifier = Modifier.fillMaxWidth().height(52.dp), onClick = { selected = app },
                normal = C.Card, zoom = 1f) { _ ->
                Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AppIcon(app.icon)
                    T(app.label, 16.sp, C.Text, FontWeight.Medium, modifier = Modifier.weight(1f))
                    if (saved == app.pkg) T("✓", 16.sp, androidx.compose.ui.graphics.Color.White)
                }
            }
        }
        if (saved.isNotEmpty()) OptionRow("Poništi zapamćeni izbor", R.drawable.lc_rotate_ccw,
            sub = "Vrati zadani ugrađeni plejer.") {
            ExtPlayer.saved = ""
            saved = ""
            Toast.makeText(ctx, tr("Ugrađeni plejer"), Toast.LENGTH_SHORT).show()
        }
    }
}

/** Opens [url] in the saved external player, or asks which one. Returns true when it opened right away. */
fun openExternalOrAsk(ctx: Context, url: String, title: String, posMs: Long, ask: () -> Unit): Boolean =
    if (ExtPlayer.openSaved(ctx, url, title, posMs)) true else { ask(); false }
