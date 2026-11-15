package com.pretorian.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Settings › General: start with the box, last channel at start, confirm exit (all off by default). */
object General {
    val boot get() = Prefs.bool("gen_boot", false)
    val lastChannel get() = Prefs.bool("gen_lastch", false)
    val confirmExit get() = Prefs.bool("gen_exit", false)
    /** the last channel opens once per start of the app */
    var startedOnce = false
}

/** Opens PRETORIAN when the box starts (only when chosen; some Android TV versions do not allow it). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val on = ctx.getSharedPreferences("pretorian", Context.MODE_PRIVATE).getBoolean("gen_boot", false)
        if (!on) return
        try { ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
    }
}
