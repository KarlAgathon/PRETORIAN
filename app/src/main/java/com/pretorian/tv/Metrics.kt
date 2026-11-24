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
        "samples" to "time,session,state,cpu_pct_one_core,java_heap_mb,pss_mb,native_heap_mb,play_state",  // i18n-ignore
        "storage" to "time,session,db_kb,db_wal_shm_kb,image_cache_kb,cache_total_kb,logo_index_kb,diagnostics_kb,free_mb",  // i18n-ignore
        "playback" to "time,session,kind,event,ms,detail",  // i18n-ignore
        "tasks" to "time,session,task,event,ms,detail",  // i18n-ignore
        // 3.0.0.7 Diagnostics II (sections 51–70); every row carries the session, "sessions" maps it to the version
        "sessions" to "time,session,version,code,commit,build_type,sdk,device_short,first_screen_ms",  // i18n-ignore
        "qoe" to "time,session,type,net,outcome,init_ms,play_ms,pause_ms,rebuf_n,rebuf_ms,rebuf_max_ms,seekbuf_n,dropped,rendered,errors,recovery,recov_ok,recov_ms,closed_by",  // i18n-ignore
        "rebuf" to "time,session,type,net,ms,suspected_cause",  // i18n-ignore
        "buffer" to "time,session,type,min_ms,max_ms,mean_ms,bitrate_kbps,throughput_kbps,net",  // i18n-ignore
        "streams" to "time,session,type,net,requests,completed,cancelled,failed,kb,req_ms_median,req_ms_p95,kbps_median,redirects,http_codes,stream_id,closed_by",  // i18n-ignore
        "overlap" to "time,session,ms,kind",  // i18n-ignore
        "mem" to "time,session,state,heap_used_mb,heap_total_mb,heap_max_mb,native_mb,pss_mb,rss_mb,graphics_mb,threads,fds,gc_count,gc_ms,avail_ram_mb,low_ram,players,uptime_min,image_cache_mb,diag_kb",  // i18n-ignore
        "jank" to "time,session,screen,frames,slow,janky,frozen,p50_ms,p95_ms,max_ms,refresh_hz",  // i18n-ignore
        "net" to "time,session,category,code,ms,ttfb_ms,bytes,error,net",  // i18n-ignore
        "imgagg" to "time,session,category,n,median_ms,p95_ms,max_ms",  // i18n-ignore
        "db" to "time,session,op,ms,rows,outcome",  // i18n-ignore
        "playlist" to "time,session,source,kind,items,categories,duplicate_ids,missing_name,missing_category,missing_image,missing_epg_id,invalid,ms,previous_count,outcome",  // i18n-ignore
        "decfail" to "time,session,type,class,mime,codecs,resolution,fps,hdr,decoder,hw,device,sdk,firmware",  // i18n-ignore
        "exits" to "time,session,exit_time,reason,status,importance,pss_mb,rss_mb,description,previous_session,previous_version,classification",  // i18n-ignore
        "updates" to "time,session,from_version,from_code,to_version,to_code,commit,first_screen_ms,device_id_same,before,after,verdict",  // i18n-ignore
        "exports" to "time,session,kind,ms,bytes,outcome,sha256_16",  // i18n-ignore
    )
    /** 3.0.0.7: the new tables are kept small (each file and its one older copy), so all of them together stay under ~5 MB */
    private val MAX_OF = mapOf("qoe" to 256L * 1024, "mem" to 256L * 1024, "jank" to 192L * 1024, "rebuf" to 128L * 1024, "streams" to 192L * 1024, "buffer" to 128L * 1024)
    private fun limitOf(file: String) = MAX_OF[file] ?: if (file in setOf("samples", "storage", "playback", "tasks")) FILE_MAX else 96L * 1024

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
                if (f.length() > limitOf(file)) { val old = File(d, "$file.1.csv"); old.delete(); f.renameTo(old) }
                if (!f.exists()) f.writeText(FILES[file] + "\n")
                f.appendText(line + "\n")
                failures.set(0)
            } catch (_: Throwable) { failures.incrementAndGet(); dropped.incrementAndGet() }
            finally { pending.decrementAndGet() }
        }
    }

    /** 3.0.0.7: a row of one of the Diagnostics II tables (same queue, limits and writer thread) */
    fun rowII(file: String, cols: List<Any?>) { if (file in FILES) row(file, cols) }

    /** CPU / memory sample (Monitor, every minute; PSS only every 5 minutes, it is expensive) */
    fun sample(state: String, cpu: Int, heapMb: Int, pssMb: Int?) = row("samples", listOf(state, cpu, heapMb, pssMb ?: "",
        android.os.Debug.getNativeHeapAllocatedSize() / 1_048_576, DiagCollect.playState()))
    fun storage(vararg v: Long) = row("storage", v.map { if (it < 0) "" else it.toString() })

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
