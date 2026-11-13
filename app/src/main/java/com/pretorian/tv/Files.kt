package com.pretorian.tv

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/** Reading files on the box and on USB sticks. */
object FileAccess {
    fun hasAll(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ctx.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    /** the screen where Android grants "all files access", if this box has it */
    fun allFilesIntent(ctx: Context): Intent? {
        if (Build.VERSION.SDK_INT < 30) return null
        val list = listOf(
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}")),
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
        )
        return list.firstOrNull { it.resolveActivity(ctx.packageManager) != null }
    }

    /** places to start: app folder (always readable), Download, internal storage, USB sticks */
    fun roots(ctx: Context): List<Pair<String, File>> {
        val out = ArrayList<Pair<String, File>>()
        val ext = Environment.getExternalStorageDirectory()
        out.add(tr("Preuzimanja (Download)") to File(ext, Environment.DIRECTORY_DOWNLOADS))
        out.add(tr("Memorija boxa") to ext)
        File("/storage").listFiles()?.filter { it.isDirectory && it.name != "emulated" && it.name != "self" }?.forEach { out.add("USB · ${it.name}" to it) }
        File("/mnt/media_rw").listFiles()?.filter { it.isDirectory }?.forEach { d -> if (out.none { it.second.name == d.name }) out.add("USB · ${d.name}" to d) }
        ctx.getExternalFilesDir(null)?.let { out.add(tr("PRETORIAN folder") to it) }
        return out
    }

    /** apps that can pick a file (file managers) */
    fun pickers(ctx: Context): List<AppChoice> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
        @Suppress("DEPRECATION")
        return pm.queryIntentActivities(i, 0).map { it.activityInfo.packageName }.distinct().filter { it != ctx.packageName }.mapNotNull { pkg ->
            try { AppChoice(pkg, pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(), pm.getApplicationIcon(pkg)) } catch (e: Exception) { null }
        }
    }

    /** "builtin", a package name, or "" (ask) */
    var app: String get() = Prefs.str("file_app"); set(v) = Prefs.put("file_app", v)
}

/**
 * Built-in file browser for the remote. [exts] filters files (lower case, without dot); [pickDir] chooses a folder instead.
 * Back always closes it.
 */
@Composable
fun FileBrowser(title: String, exts: Set<String>, pickDir: Boolean = false, onDismiss: () -> Unit, onPick: (File) -> Unit) {
    val ctx = LocalContext.current
    var dir by remember { mutableStateOf<File?>(null) }
    var rev by remember { mutableIntStateOf(0) }
    val first = remember { FocusRequester() }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { rev++ }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { rev++ }
    androidx.compose.runtime.key(rev, dir) {
        Popup(title, onDismiss, width = 720.dp) {
            if (!FileAccess.hasAll(ctx)) {
                T("PRETORIAN treba dozvolu da vidi fajlove na boxu i USB-u.", 14.sp, C.Soft, maxLines = 2)
                OptionRow("Dozvoli pristup fajlovima", R.drawable.lc_shield_check, color = C.Amber) {
                    try {
                        if (Build.VERSION.SDK_INT >= 30) {
                            val i = FileAccess.allFilesIntent(ctx)
                            if (i != null) settings.launch(i)
                            else Toast.makeText(ctx, tr("Ovaj box nema taj ekran. Dozvoli sa laptopa (adb), uputstvo je ispod."), Toast.LENGTH_LONG).show()
                        } else perm.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    } catch (e: Exception) { Toast.makeText(ctx, tr("Dozvola se ne može otvoriti"), Toast.LENGTH_LONG).show() }
                }
                if (Build.VERSION.SDK_INT >= 30) T("adb shell appops set ${ctx.packageName} MANAGE_EXTERNAL_STORAGE allow", 12.sp, C.Muted, maxLines = 2)
            }
            val d = dir
            if (d == null) {
                FileAccess.roots(ctx).forEachIndexed { i, (name, f) ->
                    OptionRow(name, if (name.startsWith("USB")) R.drawable.lc_usb else R.drawable.lc_hard_drive, sub = f.path,
                        focusRequester = if (i == 0) first else null) { dir = f }
                }
            } else {
                T(d.path, 12.sp, C.Muted)
                if (pickDir) OptionRow("Spremi ovdje", R.drawable.lc_check, color = C.Green, focusRequester = first) { onPick(d) }
                OptionRow("..", R.drawable.lc_arrow_up, sub = tr("Nazad"), focusRequester = if (!pickDir) first else null) {
                    val up = d.parentFile
                    dir = if (up == null || FileAccess.roots(ctx).any { it.second == d }) null else up
                }
                val files = try { d.listFiles()?.toList() ?: emptyList() } catch (e: Exception) { emptyList() }
                if (files.isEmpty()) T("Folder je prazan ili nema dozvole.", 13.sp, C.Muted)
                files.filter { it.isDirectory && !it.name.startsWith(".") }.sortedBy { it.name.lowercase() }.forEach { f ->
                    OptionRow(f.name, R.drawable.lc_folder) { dir = f }
                }
                if (!pickDir) files.filter { it.isFile && it.extension.lowercase() in exts }.sortedBy { it.name.lowercase() }.forEach { f ->
                    OptionRow(f.name, R.drawable.lc_file_text, sub = "${(f.length() / 1024).coerceAtLeast(1)} KB") { onPick(f) }
                }
            }
        }
    }
    LaunchedEffect(dir, rev) { first.tryFocus() }
}

/**
 * Pick a file: the built-in browser or one of your file managers, once or always (Settings › Movies and subtitles › File manager).
 * [onBytes] gets the file content. Nothing here can lock the app: Back always works, and a failing app falls back to the built-in one.
 */
@Composable
fun FilePicker(title: String, exts: Set<String>, onDismiss: () -> Unit, onBytes: (ByteArray, String) -> Unit) {
    val ctx = LocalContext.current
    var mode by remember { mutableStateOf(when (FileAccess.app) { "builtin" -> "builtin"; "" -> "ask"; else -> "app" }) }
    var always by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val uri = r.data?.data
        if (uri == null) { onDismiss(); return@rememberLauncherForActivityResult }
        try {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw Exception()
            onBytes(bytes, uri.lastPathSegment ?: "file")
        } catch (e: Exception) { Toast.makeText(ctx, tr("Fajl se ne može pročitati"), Toast.LENGTH_LONG).show(); onDismiss() }
    }
    fun openApp(pkg: String) {
        try {
            val i = Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).setPackage(pkg)
            if (i.resolveActivity(ctx.packageManager) == null) throw Exception()
            launcher.launch(i)
        } catch (e: Exception) {
            Toast.makeText(ctx, tr("Taj file manager se ne može otvoriti, koristim ugrađeni"), Toast.LENGTH_LONG).show()
            mode = "builtin"
        }
    }
    LaunchedEffect(Unit) { if (mode == "app") openApp(FileAccess.app) }
    when (mode) {
        "builtin" -> FileBrowser(title, exts, onDismiss = onDismiss) { f ->
            try { onBytes(f.readBytes(), f.name) } catch (e: Exception) { Toast.makeText(ctx, tr("Fajl se ne može pročitati"), Toast.LENGTH_LONG).show() }
        }
        "ask" -> {
            val apps = remember { FileAccess.pickers(ctx).filter { !it.pkg.contains("documentsui") } }
            Popup("Izaberi file manager", onDismiss) {
                Segmented(listOf("Samo jednom", "Uvijek"), always) { always = it }
                OptionRow("PRETORIAN (ugrađeni)", R.drawable.lc_folder_open, sub = "Za daljinski: USB, Download, memorija boxa") {
                    if (always == 1) FileAccess.app = "builtin"
                    mode = "builtin"
                }
                apps.forEach { a ->
                    Focusable(modifier = Modifier.fillMaxWidth().height(60.dp), onClick = {
                        if (always == 1) FileAccess.app = a.pkg
                        mode = "app"; openApp(a.pkg)
                    }, normal = C.Card, zoom = 1.01f) { _ ->
                        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            AppIcon(a.icon)
                            T(a.label, 16.sp, C.Text, FontWeight.Medium, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        else -> Popup(title, onDismiss) {
            T("Otvoren je tvoj file manager. Ako se ništa ne desi, pritisni Nazad.", 14.sp, C.Soft, maxLines = 2)
            OptionRow("Koristi ugrađeni", R.drawable.lc_folder_open) { mode = "builtin" }
        }
    }
}
