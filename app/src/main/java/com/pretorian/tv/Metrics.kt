package com.pretorian.tv

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 3.0 performance samples for the Full Diagnostic Report. Small CSV files inside the diagnostics folder, so they count
 * against the same 50 MB ceiling as everything else. Each file is at most 512 KB, with one older copy kept (.1):
 * at most ~5 MB for all metrics. Writing happens on one low-priority thread, in order; when the queue is full, space is
 * low or writes keep failing, samples are dropped and only counted (playback and the box always win).
 *
 * Times are monotonic (elapsedRealtime) differences in milliseconds. The session id is random per app start and says
 * nothing about the user. Titles are not written here: playback rows name only the kind (tv / movie / series).
 */
object PerfLog {
    private const val FILE_MAX = 512L * 1024
    private const val QUEUE_MAX = 500
    val session: String = java.util.UUID.randomUUID().toString().take(8)
    private val exec = Executors.newSingleThreadExecutor { r -> Thread(r, "pretorian-metrics").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } }
    private val pending = AtomicInteger(0)
    val dropped = AtomicInteger(0)
    private val failures = AtomicInteger(0)
    /** counters for this session (shown in PERFORMANCE_SUMMARY) */
    val counts = ConcurrentHashMap<String, Int>()
    fun count(k: String, n: Int = 1) { counts.merge(k, n, Int::plus) }

    val FILES = mapOf(
        "samples" to "time,session,state,cpu_pct_one_core,java_heap_mb,pss_mb",  // i18n-ignore
        "playback" to "time,session,kind,event,ms,detail",  // i18n-ignore
        "tasks" to "time,session,task,event,ms,detail",  // i18n-ignore
    )

    fun dir(): File? = DiagStore.journalDir()?.parentFile?.let { File(it, "metrics") }

    private fun row(file: String, cols: List<Any?>) {
        if (!DiagStore.ready) return
        if (pending.get() > QUEUE_MAX || failures.get() > 20 || !DiagStore.spaceOk()) { dropped.incrementAndGet(); return }
        pending.incrementAndGet()
        val line = DiagEvents.stamp() + "," + session + "," + cols.joinToString(",") { (it?.toString() ?: "").replace(',', ';').replace('\n', ' ').take(120) }
        exec.execute {
            try {
                val d = dir() ?: return@execute
                d.mkdirs()
                val f = File(d, "$file.csv")
                if (f.length() > FILE_MAX) { val old = File(d, "$file.1.csv"); old.delete(); f.renameTo(old) }
                if (!f.exists()) f.writeText(FILES[file] + "\n")
                f.appendText(line + "\n")
                failures.set(0)
            } catch (_: Throwable) { failures.incrementAndGet(); dropped.incrementAndGet() }
            finally { pending.decrementAndGet() }
        }
    }

    /** CPU / memory sample (Monitor, every minute; PSS only every 5 minutes, it is expensive) */
    fun sample(state: String, cpu: Int, heapMb: Int, pssMb: Int?) = row("samples", listOf(state, cpu, heapMb, pssMb ?: ""))

    fun firstFrame(kind: String, ms: Long, recovery: Boolean) {
        count("$kind first frame"); row("playback", listOf(kind, if (recovery) "first_frame_recovery" else "first_frame", ms, ""))  // i18n-ignore
    }
    fun ready(kind: String, ms: Long, recovery: Boolean) = row("playback", listOf(kind, if (recovery) "ready_recovery" else "ready", ms, ""))  // i18n-ignore
    fun buffering(kind: String, ms: Long, startup: Boolean) {
        count("$kind buffering"); row("playback", listOf(kind, if (startup) "buffer_startup" else "buffer_mid", ms, ""))  // i18n-ignore
    }
    fun playback(kind: String, event: String, detail: String = "") { count("$kind $event"); row("playback", listOf(kind, event, "", detail)) }
    fun task(name: String, event: String, ms: Long = 0, detail: String = "") { count("task $name $event"); row("tasks", listOf(name, event, ms, detail)) }

    /** the rows of one metrics file (older copy first), for the report */
    fun each(file: String, fn: (String) -> Unit) {
        val d = dir() ?: return
        listOf(File(d, "$file.1.csv"), File(d, "$file.csv")).forEach { f ->
            if (f.exists()) try { f.bufferedReader().useLines { s -> s.drop(1).forEach(fn) } } catch (_: Throwable) {}   // the header is written once by the report
        }
    }
}
