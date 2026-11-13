package com.pretorian.tv

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/** keeps the last crash so it can be shown at the next start (no adb needed) */
object CrashLog {
    private fun file(c: Context) = File(c.filesDir, "last_crash.txt")

    fun install(c: Context) {
        val app = c.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        if (prev is Handler) return
        Thread.setDefaultUncaughtExceptionHandler(Handler(app, prev))
    }

    private class Handler(val app: Context, val prev: Thread.UncaughtExceptionHandler?) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(t: Thread, e: Throwable) {
            try {
                val sw = java.io.StringWriter(); e.printStackTrace(java.io.PrintWriter(sw))
                file(app).writeText("PRETORIAN ${BuildInfo.VERSION} · ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE}\n" + sw.toString().take(6000))
            } catch (_: Throwable) {}
            prev?.uncaughtException(t, e)
        }
    }

    fun take(c: Context): String? = try { file(c).takeIf { it.exists() }?.let { val s = it.readText(); it.delete(); s } } catch (e: Exception) { null }
}

@Composable
fun CrashDialog() {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf(CrashLog.take(ctx)) }
    val t = text ?: return
    Popup("Aplikacija se prošli put srušila", { text = null }, width = 900.dp) {
        T("Slikaj ovaj ekran i pošalji ga, da se greška može popraviti.", 14.sp, C.Soft)
        Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
            T(t, 11.sp, C.Muted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button("Zatvori", R.drawable.lc_x, primary = true) { text = null }
        }
    }
}
