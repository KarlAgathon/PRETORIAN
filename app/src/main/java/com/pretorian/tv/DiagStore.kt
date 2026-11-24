package com.pretorian.tv

import android.content.Context
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * 2.8.0 – the persistent diagnostic store. Everything lives on the box's own storage (survives a crash, a restart,
 * a reboot and an app update) under files/diag:
 *
 *  journal/current.log           the events being written now (plain text, one event per line, already redacted)
 *  journal/seg_<first>_<last>_<n>.log.gz   older events, compressed (n = number of events)
 *  incidents/<kind>_<time>_<id>.txt         one crash / freeze / ANR / native crash each (older than 3 days: .txt.gz)
 *  incidents/<same base>.log.txt            the app's own log saved after that incident (attached to it, never counted as an incident)
 *
 * Retention (amendment, 24.11.): at most 100 incidents, 10,000 events, 90 days, 50 MB in all, and nothing is written
 * while the box has less than 100 MB free. When a limit is reached the oldest ordinary events go first, then the oldest
 * freezes / ANRs, the crash reports last. Writes are batched on one background thread; critical events wake it at once.
 *
 * Unavoidable loss: when Android kills the process outright (out of memory, force stop, power cut) the events still
 * waiting in the queue — at most the last ~2 seconds of ordinary events — are lost; crashes are written synchronously
 * by the crash handler before the process ends.
 */
object DiagStore {
    const val MAX_INCIDENTS = 100
    const val MAX_EVENTS = 10_000
    const val MAX_DAYS = 90
    const val MAX_BYTES = 50L * 1024 * 1024
    const val MIN_FREE = 100L * 1024 * 1024
    const val INCIDENT_MAX_CHARS = 256 * 1024
    private const val SEGMENT_BYTES = 512L * 1024
    private const val COMPRESS_AFTER_MS = 3L * 86_400_000L

    @Volatile private var root: File? = null
    private val queue = LinkedBlockingQueue<String>()
    @Volatile private var dropped = 0
    @Volatile private var curLines = -1
    private val lock = Any()

    fun dir(ctx: Context): File = (ctx.getExternalFilesDir(null) ?: ctx.filesDir).let { File(it, "diag") }
    fun journalDir(): File? = root?.let { File(it, "journal") }
    fun incidentDir(): File? = root?.let { File(it, "incidents") }
    val ready get() = root != null

    /** started once from App.onCreate, before the crash handler */
    fun init(ctx: Context) {
        if (root != null) return
        val r = dir(ctx)
        try { File(r, "journal").mkdirs(); File(r, "incidents").mkdirs() } catch (_: Throwable) {}
        root = r
        Thread({ loop() }, "pretorian-diag").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
    }

    /** room to write: the 100 MB floor of free space on the box */
    fun spaceOk(): Boolean = try { val r = root ?: return false; val f = r.usableSpace; f <= 0L || f >= MIN_FREE } catch (_: Throwable) { false }

    fun enqueue(line: String, critical: Boolean) {
        queue.offer(line)
        if (critical) synchronized(lock) { (lock as Object).notifyAll() }
    }

    /** the crash handler: whatever is queued goes to disk now, on this thread */
    fun flushNow() { try { synchronized(lock) { drain() } } catch (_: Throwable) {} }

    private fun loop() {
        try { synchronized(lock) { maintain() } } catch (_: Throwable) {}
        var lastMaintain = System.currentTimeMillis()
        while (true) {
            try {
                // batch: wait up to 2 s (or until a critical event wakes us), then write everything queued in one go
                synchronized(lock) { if (queue.isEmpty()) (lock as Object).wait(2000) }
                val first = queue.poll(100, TimeUnit.MILLISECONDS) ?: continue
                synchronized(lock) {
                    val batch = ArrayList<String>(); batch += first; queue.drainTo(batch)
                    write(batch)
                    if (System.currentTimeMillis() - lastMaintain > 10 * 60_000L) { maintain(); lastMaintain = System.currentTimeMillis() }
                }
            } catch (_: InterruptedException) { return } catch (_: Throwable) {}
        }
    }

    private fun drain() { val b = ArrayList<String>(); queue.drainTo(b); if (b.isNotEmpty()) write(b) }

    private fun write(batch: List<String>) {
        val jd = journalDir() ?: return
        if (!spaceOk()) { dropped += batch.size; return }
        val cur = File(jd, "current.log")  // i18n-ignore
        val sb = StringBuilder()
        if (dropped > 0) { sb.append(DiagEvents.stamp()).append("|W|diag|").append(dropped).append(" events not saved: low storage\n"); dropped = 0 }  // i18n-ignore
        batch.forEach { sb.append(it).append('\n') }
        java.io.FileOutputStream(cur, true).use { it.write(sb.toString().toByteArray(Charsets.UTF_8)) }
        if (curLines < 0) curLines = countLines(cur) else curLines += batch.size
        if (cur.length() >= SEGMENT_BYTES) rotate(cur)
    }

    private fun countLines(f: File): Int = try { f.bufferedReader().use { r -> var n = 0; while (r.readLine() != null) n++; n } } catch (_: Throwable) { 0 }

    /** current.log → seg_<first>_<last>_<n>.log.gz */
    private fun rotate(cur: File) {
        val jd = cur.parentFile ?: return
        var first = 0L; var last = 0L; var n = 0
        val tmp = File(jd, "rotating.tmp")
        try {
            cur.bufferedReader().use { r ->
                GZIPOutputStream(tmp.outputStream()).bufferedWriter().use { w ->
                    while (true) {
                        val l = r.readLine() ?: break
                        val t = DiagEvents.parseTime(l); if (t > 0) { if (first == 0L) first = t; last = t }
                        w.write(l); w.write("\n"); n++
                    }
                }
            }
            if (first == 0L) first = cur.lastModified()
            if (last == 0L) last = cur.lastModified()
            if (tmp.renameTo(File(jd, "seg_${first}_${last}_$n.log.gz"))) cur.delete()  // i18n-ignore
            curLines = 0
        } catch (_: Throwable) { tmp.delete() }
        maintain()
    }

    data class Seg(val f: File, val first: Long, val last: Long, val n: Int)
    fun segments(): List<Seg> = journalDir()?.listFiles()?.mapNotNull { f ->
        val m = Regex("seg_(\\d+)_(\\d+)_(\\d+)\\.log\\.gz").matchEntire(f.name) ?: return@mapNotNull null
        Seg(f, m.groupValues[1].toLong(), m.groupValues[2].toLong(), m.groupValues[3].toInt())
    }?.sortedBy { it.first }.orEmpty()

    /** retention: age, number of events, number of incidents, total size; compress older incidents */
    fun maintain() {
        val jd = journalDir() ?: return; val idir = incidentDir() ?: return
        val now = System.currentTimeMillis(); val cutoff = now - MAX_DAYS * 86_400_000L
        File(jd, "rotating.tmp").delete()
        // a broken segment (cannot be opened) is removed
        segments().forEach { s -> if (s.last < cutoff || !readable(s.f)) s.f.delete() }
        var segs = segments()
        var events = segs.sumOf { it.n } + curLines.coerceAtLeast(0)
        while (events > MAX_EVENTS && segs.isNotEmpty()) { events -= segs.first().n; segs.first().f.delete(); segs = segs.drop(1) }
        // incidents: age, then count (logs follow their incident)
        var inc = incidents()
        inc.filter { it.time < cutoff }.forEach { deleteIncident(it) }
        inc = incidents()
        if (inc.size > MAX_INCIDENTS) {
            // over the limit: ordinary freezes / exits go before crashes
            val order = inc.sortedWith(compareBy({ if (it.kind == "crash" || it.kind == "native") 1 else 0 }, { it.time }))
            order.take(inc.size - MAX_INCIDENTS).forEach { deleteIncident(it) }
        }
        // compress incidents older than 3 days
        incidents().filter { now - it.time > COMPRESS_AFTER_MS && !it.f.name.endsWith(".gz") }.forEach { gzip(it.f) }
        idir.listFiles()?.filter { it.name.endsWith(".log.txt") && now - it.lastModified() > COMPRESS_AFTER_MS }?.forEach { gzip(it) }
        // total size: journal first, then freezes, then crashes
        fun total() = (root?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() }) ?: 0L
        var size = total()
        segs = segments()
        while (size > MAX_BYTES && segs.isNotEmpty()) { size -= segs.first().f.length(); segs.first().f.delete(); segs = segs.drop(1) }
        if (size > MAX_BYTES) incidents().sortedWith(compareBy({ if (it.kind == "crash" || it.kind == "native") 1 else 0 }, { it.time }))
            .forEach { if (size > MAX_BYTES) { size -= it.f.length() + (logOf(it)?.length() ?: 0); deleteIncident(it) } }
    }

    private fun readable(f: File) = try { GZIPInputStream(f.inputStream()).use { it.read(ByteArray(64)) }; true } catch (_: Throwable) { false }
    private fun gzip(f: File) {
        val out = File(f.path + ".gz"); val tmp = File(f.path + ".gz.tmp")
        try {
            f.inputStream().use { i -> GZIPOutputStream(tmp.outputStream()).use { o -> i.copyTo(o, 64 * 1024) } }
            if (tmp.renameTo(out)) f.delete()
        } catch (_: Throwable) { tmp.delete() }
    }

    // ---------------------------------------------------------------- incidents
    data class Incident(val f: File, val kind: String, val time: Long, val base: String)
    fun incidents(): List<Incident> = incidentDir()?.listFiles()?.mapNotNull { f ->
        if (f.name.contains(".log.txt")) return@mapNotNull null  // i18n-ignore
        val m = Regex("([a-z]+)_(\\d+)_([0-9a-f]+)\\.txt(\\.gz)?").matchEntire(f.name) ?: return@mapNotNull null
        Incident(f, m.groupValues[1], m.groupValues[2].toLong(), m.groupValues[1] + "_" + m.groupValues[2] + "_" + m.groupValues[3])
    }?.sortedByDescending { it.time }.orEmpty()
    fun logOf(i: Incident): File? = incidentDir()?.let { d -> listOf(File(d, i.base + ".log.txt"), File(d, i.base + ".log.txt.gz")).firstOrNull { it.exists() } }  // i18n-ignore
    private fun deleteIncident(i: Incident) { i.f.delete(); logOf(i)?.delete() }

    /** a new incident, bounded: past [INCIDENT_MAX_CHARS] the middle is cut out and the cut is written down. Returns its base name. */
    fun incident(kind: String, text: String): String? {
        val d = incidentDir() ?: return null
        if (!spaceOk()) return null
        return try {
            val now = System.currentTimeMillis()
            val id = java.lang.Long.toHexString(java.security.SecureRandom().nextLong() and 0xffffffL)
            val base = kind + "_" + now + "_" + id
            File(d, "$base.txt").writeText(bounded(Redact.text(text)))
            base
        } catch (_: Throwable) { null }
    }
    /** the app log for an incident; written once, never twice for the same incident */
    fun attachLog(base: String, text: String) {
        val d = incidentDir() ?: return
        if (!spaceOk()) return
        val f = File(d, "$base.log.txt"); if (f.exists() || File(d, "$base.log.txt.gz").exists()) return  // i18n-ignore
        try { f.writeText(bounded(Redact.text(text))) } catch (_: Throwable) {}
    }
    fun bounded(t: String): String = if (t.length <= INCIDENT_MAX_CHARS) t else
        t.take(INCIDENT_MAX_CHARS * 3 / 4) + "\n\n[… " + (t.length - INCIDENT_MAX_CHARS) + " characters cut here …]\n\n" + t.takeLast(INCIDENT_MAX_CHARS / 4)  // i18n-ignore

    /** a text file of the store (plain or .gz), at most [max] characters; the rest is marked */
    fun read(f: File, max: Int = INCIDENT_MAX_CHARS * 2): String = try {
        val ins = if (f.name.endsWith(".gz")) GZIPInputStream(f.inputStream()) else f.inputStream()
        BufferedReader(InputStreamReader(ins, Charsets.UTF_8)).use { r ->
            val buf = CharArray(8192); val sb = StringBuilder(); var cut = false
            while (true) { val n = r.read(buf); if (n < 0) break; if (sb.length + n > max) { sb.append(buf, 0, max - sb.length); cut = true; break }; sb.append(buf, 0, n) }
            if (cut) sb.append("\n[… file longer than ").append(max).append(" characters, rest not shown …]\n")  // i18n-ignore
            sb.toString()
        }
    } catch (_: Throwable) { "(unreadable)" }  // i18n-ignore

    /** every saved event, oldest first, streamed (never all in memory) */
    fun eachEvent(fn: (String) -> Unit) {
        val jd = journalDir() ?: return
        segments().forEach { s ->
            try { BufferedReader(InputStreamReader(GZIPInputStream(s.f.inputStream()), Charsets.UTF_8)).use { r -> while (true) fn(r.readLine() ?: break) } } catch (_: Throwable) {}
        }
        try { File(jd, "current.log").takeIf { it.exists() }?.bufferedReader()?.use { r -> while (true) fn(r.readLine() ?: break) } } catch (_: Throwable) {}  // i18n-ignore
    }
    fun eventCount(): Int = segments().sumOf { it.n } + curLines.coerceAtLeast(0)
    fun totalBytes(): Long = (root?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() }) ?: 0L

    /** tests: write a batch now, on this thread */
    internal fun writeNow(lines: List<String>) = synchronized(lock) { write(lines) }

    /** "Obriši dijagnostiku": everything, after the user confirmed */
    fun clearAll() = synchronized(lock) {
        queue.clear(); curLines = 0
        journalDir()?.listFiles()?.forEach { it.delete() }
        incidentDir()?.listFiles()?.forEach { it.delete() }
    }
}
