package com.pretorian.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Settings › General: start with the box, last channel at start, confirm exit (all off by default). */
object General {
    val boot get() = Prefs.bool("gen_boot", false)
    val lastChannel get() = start == 5
    /** what the app opens on: 0 Home, 1 TV, 2 Movies, 3 Series, 4 Sports Center, 5 last channel (old switch "last channel" counts as 5) */
    val start get() = Prefs.long("start_screen", if (Prefs.bool("gen_lastch", false)) 5L else 0L).toInt()
    /** background checks, how often (Settings › General › long OK) */
    val sportEvery get() = Prefs.long("bg_sport_min", if (Perf.lite) 10L else 3L).toInt()          // minutes
    val sportLive get() = Prefs.long("bg_sport_live", if (Perf.lite) 120L else 60L).toInt()         // seconds while a match is live
    val episodesEvery get() = Prefs.long("bg_ep_min", if (Perf.lite) 360L else 60L).toInt()         // minutes
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
