package com.pretorian.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Settings › General: start with the box and confirm exit (all off by default). */
object General {
    val boot get() = Prefs.bool("gen_boot", false)
    /** background checks, how often (Settings › General › long OK) */
    val sportEvery get() = Prefs.long("bg_sport_min", if (Perf.lite) 10L else 3L).toInt()          // minutes
    val sportLive get() = Prefs.long("bg_sport_live", if (Perf.lite) 30L else 20L).toInt()         // seconds while a match is live
    val episodesEvery get() = Prefs.long("bg_ep_min", if (Perf.lite) 360L else 60L).toInt()         // minutes
    val confirmExit get() = Prefs.bool("gen_exit", false)
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


/** Resource savers: on by default in every mode (Lite and Full), each one can be switched off in Settings › General. */
object Lean {
    val images get() = Prefs.bool("lean_images", true)     // smaller image memory: fewer pictures held at once
    val epg get() = Prefs.bool("lean_epg", true)           // own TV guide: 12 hours ahead instead of 36
    val search get() = Prefs.bool("lean_search", true)     // search list is built when Search is first opened
    val posters get() = Prefs.bool("lean_posters", true)   // posters in a smaller resolution
    val trim get() = Prefs.bool("lean_trim", true)         // memory freed when leaving the player
    val saver get() = Prefs.bool("lean_saver", true)       // screen saver clock changes once a minute, no seconds
    val scan get() = Prefs.bool("lean_scan", true)         // new films / series checked once a day
    val ALL = listOf(
        Triple("lean_images", "Manje slika odjednom", "slike se drže u memoriji samo dok su na ekranu"),
        Triple("lean_epg", "Manji TV vodič", "12 sati unaprijed umjesto 36"),
        Triple("lean_search", "Pretraga kad je otvoriš", "lista se pravi tek pri prvom otvaranju"),
        Triple("lean_posters", "Plakati u manjoj rezoluciji", "na TV-u se razlika skoro ne vidi"),
        Triple("lean_trim", "Očisti memoriju poslije plejera", "velike liste i slike se oslobode"),
        Triple("lean_saver", "Čuvar ekrana bez sekundi", "sat se mijenja jednom u minuti"),
        Triple("lean_scan", "Nove filmove i serije provjeri jednom dnevno", "umjesto svakih 12 sati"),
    )
}
