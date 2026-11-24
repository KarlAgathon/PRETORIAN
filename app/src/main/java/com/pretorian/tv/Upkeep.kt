package com.pretorian.tv

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Quiet upkeep of the box's database: old guide rows, removed playlists and EPG links go; once a week it is packed. */
object Upkeep {
    private val bg = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    fun start(ctx: Context) {
        if (started) return
        started = true
        val app = ctx.applicationContext
        bg.launch {
            delay(120_000)   // never while the app is starting
            while (true) {
                if (Tech.dbClean) try { Work.heavy("db upkeep") { clean(app) } } catch (_: Throwable) {} // i18n-ignore
                delay(6 * 3600_000L)
            }
        }
    }

    fun clean(ctx: Context) {
        val d = CatalogDb.db() ?: return
        val now = System.currentTimeMillis()
        val pls = Store.playlists(ctx).map { it.id }.toSet()
        val urls = EpgSources.list().map { GuideDb.srcOf(it.url) }.toSet()
        // guide rows long past
        d.delete("progs", "e<?", arrayOf((now - GuideDb.BACK - 6 * 3600_000L).toString())) // i18n-ignore
        // movies / series of playlists that are gone
        d.rawQuery("SELECT DISTINCT pl FROM meta", null).use { c -> // i18n-ignore
            while (c.moveToNext()) { val pl = c.getString(0) ?: continue; if (pl !in pls) CatalogDb.forgetNow(pl) }
        }
        // guides of playlists / EPG links that are gone
        d.rawQuery("SELECT src FROM gmeta", null).use { c -> // i18n-ignore
            while (c.moveToNext()) {
                val src = c.getString(0) ?: continue
                val keep = if (src.startsWith("pl:")) src.removePrefix("pl:") in pls else src in urls // i18n-ignore
                if (!keep) GuideDb.forget(src)
            }
        }
        // once a week: give the freed space back to the box
        if (now - Prefs.long("db_vacuum_at", 0) > 7 * 86_400_000L && !Import.busy()) {
            try { d.execSQL("VACUUM"); Prefs.put("db_vacuum_at", now) } catch (_: Throwable) {} // i18n-ignore
        }
    }
}

/** Settings › Diagnostics › Long run: memory and CPU of the app every minute while it runs (off by default). */
object Monitor {
    class Sample(val at: Long, val pssMb: Int, val cpu: Int)
    val samples = ArrayDeque<Sample>()
    private val bg = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false
    /** how long the app took from start to the first screen (ms) */
    @Volatile var startMs = 0L
    val since = System.currentTimeMillis()

    private fun cpuTicks(): Long = try {
        val f = java.io.File("/proc/self/stat").readText().substringAfterLast(')').trim().split(' ')
        f[11].toLong() + f[12].toLong()   // utime + stime
    } catch (e: Throwable) { -1 }

    fun start() {
        if (started) return
        started = true
        bg.launch {
            var lastT = cpuTicks(); var lastW = System.currentTimeMillis()
            val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            while (true) {
                delay(60_000)
                if (!Tech.monitor) { lastT = cpuTicks(); lastW = System.currentTimeMillis(); continue }
                val t = cpuTicks(); val w = System.currentTimeMillis()
                // one tick = 10 ms; per cent of ONE core (like "top"), capped by the number of cores
                val cpu = if (t >= 0 && lastT >= 0 && w > lastW) (((t - lastT) * 10.0 / (w - lastW)) * 100).toInt().coerceIn(0, 100 * cores) else 0
                lastT = t; lastW = w
                @Suppress("DEPRECATION") val pss = (android.os.Debug.getPss() / 1024).toInt()
                synchronized(samples) { samples.addLast(Sample(w, pss, cpu)); while (samples.size > 1440) samples.removeFirst() }
            }
        }
    }

    fun summary(): List<Pair<String, String>> {
        val l = synchronized(samples) { samples.toList() }
        val out = ArrayList<Pair<String, String>>()
        if (startMs > 0) out += tr("Pokretanje do prvog ekrana") to listOf(startMs, " ms").joinToString("")
        if (l.isEmpty()) { out += tr("Praćenje") to tr(if (Tech.monitor) "skuplja podatke (svake minute)" else "Isključeno"); return out }
        val hours = (System.currentTimeMillis() - l.first().at) / 3600_000.0
        out += tr("Praćeno") to String.format(java.util.Locale.US, "%.1f h", hours) // i18n-ignore
        out += tr("Memorija sada") to "${l.last().pssMb} MB" // i18n-ignore
        out += tr("Memorija min / max") to listOf(l.minOf { it.pssMb }, " / ", l.maxOf { it.pssMb }, " MB").joinToString("")
        // memory that only grows over hours is a leak: compare the first and the last hour
        if (l.size >= 120) {
            val a = l.take(60).map { it.pssMb }.average(); val b = l.takeLast(60).map { it.pssMb }.average()
            out += tr("Rast memorije") to (if (b - a > 80) "⚠ +" + (b - a).toInt() + " MB" else "+" + (b - a).toInt().coerceAtLeast(0) + " MB · " + tr("U redu")) // i18n-ignore
        }
        out += tr("Procesor prosjek / max") to listOf(l.map { it.cpu }.average().toInt(), " % / ", l.maxOf { it.cpu }, " %").joinToString("")
        return out
    }
}
