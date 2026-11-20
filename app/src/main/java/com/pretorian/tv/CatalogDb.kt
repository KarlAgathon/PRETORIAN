package com.pretorian.tv

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * Every movie and series of each playlist, kept in a small database on the box (like TiviMate).
 * Filled once when a playlist is added (with a progress window) and refreshed quietly when older than a day;
 * the old data stays usable until the new download is complete. If anything goes wrong, the app falls back to the old way.
 */
object CatalogDb {
    private const val MAX_AGE = 24 * 3600_000L
    private lateinit var cacheDir: java.io.File
    private var helper: SQLiteOpenHelper? = null
    private val locks = HashMap<String, Mutex>()
    private val bg = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(ctx: Context) {
        if (helper != null) return
        cacheDir = ctx.cacheDir
        helper = object : SQLiteOpenHelper(ctx, "catalog.db", null, 3) { // i18n-ignore
            override fun onConfigure(db: SQLiteDatabase) { db.enableWriteAheadLogging() }
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL("CREATE TABLE items(pl TEXT, kind TEXT, id TEXT, c TEXT, n TEXT, i TEXT, r TEXT, u TEXT, k TEXT)") // i18n-ignore
                db.execSQL("CREATE INDEX items_pk ON items(pl, kind)") // i18n-ignore
                db.execSQL("CREATE TABLE meta(pl TEXT, kind TEXT, at INTEGER, n INTEGER, PRIMARY KEY(pl, kind))") // i18n-ignore
                guideTables(db)
            }
            override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {
                // version 2 adds the TV guide; movies and series already on the box stay
                if (o < 2) guideTables(db)
                // version 3: the search key (name without accents, lower case) next to every movie / series; filled later in the background
                if (o < 3) try { db.execSQL("ALTER TABLE items ADD COLUMN k TEXT") } catch (_: Throwable) {} // i18n-ignore
            }
        }
    }

    private fun guideTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS progs(src TEXT, ch TEXT, s INTEGER, e INTEGER, t TEXT, d TEXT, k TEXT)") // i18n-ignore
        db.execSQL("CREATE INDEX IF NOT EXISTS progs_ch ON progs(src, ch, e)") // i18n-ignore
        db.execSQL("CREATE TABLE IF NOT EXISTS gmeta(src TEXT PRIMARY KEY, at INTEGER, n INTEGER)") // i18n-ignore
    }

    internal fun db(): SQLiteDatabase? = try { helper?.writableDatabase } catch (e: Throwable) { null }

    /** (time of the last full download, how many) or null when this playlist part was never downloaded */
    private fun meta(pl: String, kind: String): Pair<Long, Int>? = try {
        db()?.rawQuery("SELECT at, n FROM meta WHERE pl=? AND kind=?", arrayOf(pl, kind))?.use { c -> // i18n-ignore
            if (c.moveToFirst()) c.getLong(0) to c.getInt(1) else null
        }
    } catch (e: Throwable) { null }

    fun count(pl: String, kind: String): Int = meta(pl, kind)?.second ?: -1
    /** how many titles each category has (New Dawn shows the number next to every category) */
    fun counts(pl: String, kind: String): Map<String, Int> = try {
        db()?.rawQuery("SELECT c, COUNT(*) FROM items WHERE pl=? AND kind=? GROUP BY c", arrayOf(pl, kind))?.use { c -> // i18n-ignore
            val m = HashMap<String, Int>(); while (c.moveToNext()) m[c.getString(0) ?: ""] = c.getInt(1); m
        } ?: emptyMap()
    } catch (e: Throwable) { emptyMap() }
    fun has(pl: String, kind: String) = meta(pl, kind) != null

    /** "Refresh": everything counts as old, so it is downloaded again in the background (the old data stays until then) */
    fun markStale() { bg.launch { try { db()?.execSQL("UPDATE meta SET at=0") } catch (_: Throwable) {} } } // i18n-ignore

    /** never on the screen's thread: a running download holds the database for a while */
    fun forget(pl: String) { bg.launch { forgetNow(pl) } }
    fun forgetNow(pl: String) { try { db()?.let { it.delete("items", "pl=?", arrayOf(pl)); it.delete("meta", "pl=?", arrayOf(pl)) } } catch (_: Throwable) {} } // i18n-ignore

    private fun read(pl: String, kind: String): List<JSONObject>? {
        val d = db() ?: return null
        val out = ArrayList<JSONObject>(maxOf(16, count(pl, kind)))
        d.rawQuery("SELECT id, c, n, i, r, u FROM items WHERE pl=? AND kind=?", arrayOf(pl, kind)).use { c -> // i18n-ignore
            while (c.moveToNext()) {
                val o = JSONObject().put("id", c.getString(0)).put("c", c.getString(1) ?: "").put("n", c.getString(2) ?: "")
                    .put("i", c.getString(3) ?: "").put("r", c.getString(4) ?: "")
                c.getString(5)?.takeIf { it.isNotEmpty() }?.let { o.put("u", it) }
                out += o
            }
        }
        return out
    }

    /** downloads one part (movies or series) into the database; the old rows are replaced only when the download is complete */
    fun download(p: Playlist, kind: String, progress: ((items: Int, read: Long, total: Long) -> Unit)? = null): Int {
        val d = db() ?: throw IllegalStateException("db") // i18n-ignore
        var n = 0; var lastBytes = 0L; var total = -1L
        val stage = java.io.File.createTempFile("catalog-", ".jsonl", cacheDir)
        try {
            stage.bufferedWriter().use { writer ->
                Xtream(p).catalogEach(kind, bytes = { r, t -> lastBytes = r; total = t; progress?.invoke(n, r, t) }) { item ->
                    writer.write(item.toString())
                    writer.newLine()
                    n++
                    if (n % 500 == 0) progress?.invoke(n, lastBytes, total)
                }
            }
            d.beginTransactionNonExclusive()
            try {
                d.delete("items", "pl=? AND kind=?", arrayOf(p.id, kind)) // i18n-ignore
                d.compileStatement("INSERT INTO items(pl, kind, id, c, n, i, r, u, k) VALUES(?,?,?,?,?,?,?,?,?)").use { st -> // i18n-ignore
                    stage.forEachLine { line ->
                        val o = JSONObject(line)
                        st.bindString(1, p.id); st.bindString(2, kind); st.bindString(3, o.str("id")); st.bindString(4, o.str("c"))
                        st.bindString(5, o.str("n")); st.bindString(6, o.str("i")); st.bindString(7, o.str("r")); st.bindString(8, o.str("u"))
                        st.bindString(9, Sports.norm(o.str("n")))
                        st.executeInsert()
                    }
                }
                check(d.insertWithOnConflict("meta", null, ContentValues().apply { // i18n-ignore
                    put("pl", p.id); put("kind", kind); put("at", System.currentTimeMillis()); put("n", n) // i18n-ignore
                }, SQLiteDatabase.CONFLICT_REPLACE) != -1L)
                d.setTransactionSuccessful()
            } finally { d.endTransaction() }
        } finally { stage.delete() }
        return n
    }

    private fun lock(key: String) = synchronized(locks) { locks.getOrPut(key) { Mutex() } }

    /** downloads a part when it was never downloaded, without reading it into memory (search asks the database itself) */
    suspend fun prepare(p: Playlist, kind: String): Boolean {
        if (helper == null) return false
        lock(p.id + "_" + kind).withLock { if (meta(p.id, kind) == null) download(p, kind) }
        return meta(p.id, kind) != null
    }

    class Row(val pl: String, val id: String, val c: String, val n: String, val i: String?, val r: String?, val u: String, val k: String)

    /** Search straight in the database: every word must be in the name; at most [limit] rows. */
    fun search(pls: List<String>, kind: String, words: List<String>, limit: Int): List<Row> {
        val d = db() ?: return emptyList()
        if (pls.isEmpty() || words.isEmpty()) return emptyList()
        val where = StringBuilder("kind=? AND pl IN (" + pls.joinToString(",") { "?" } + ")") // i18n-ignore
        val args = ArrayList<String>().apply { add(kind); addAll(pls) }
        // rows from before version 3 may not have their key yet: their lower-case name is used meanwhile
        words.forEach { w -> where.append(" AND COALESCE(k, lower(n)) LIKE ?"); args += "%" + w.replace("%", "").replace("_", "") + "%" } // i18n-ignore
        return d.rawQuery("SELECT pl, id, c, n, i, r, u, k FROM items WHERE $where LIMIT $limit", args.toTypedArray()).use { c -> // i18n-ignore
            val out = ArrayList<Row>()
            while (c.moveToNext()) out += Row(c.getString(0) ?: "", c.getString(1) ?: "", c.getString(2) ?: "", c.getString(3) ?: "",
                c.getString(4)?.ifBlank { null }, c.getString(5)?.ifBlank { null }, c.getString(6) ?: "", c.getString(7) ?: (c.getString(3) ?: "").lowercase())
            out
        }
    }

    /** fills the search key of rows saved by older versions, a little at a time, never while the app starts */
    fun backfill() {
        bg.launch {
            delay(90_000)
            try {
                while (true) {
                    val d = db() ?: break
                    val rows = d.rawQuery("SELECT rowid, n FROM items WHERE k IS NULL LIMIT 2000", null).use { c -> // i18n-ignore
                        val l = ArrayList<Pair<Long, String>>(); while (c.moveToNext()) l += c.getLong(0) to (c.getString(1) ?: ""); l }
                    if (rows.isEmpty()) break
                    d.beginTransactionNonExclusive()
                    try {
                        val st = d.compileStatement("UPDATE items SET k=? WHERE rowid=?") // i18n-ignore
                        rows.forEach { (id, n) -> st.clearBindings(); st.bindString(1, Sports.norm(n)); st.bindLong(2, id); st.executeUpdateDelete() }
                        d.setTransactionSuccessful()
                    } finally { d.endTransaction() }
                    delay(200)
                }
            } catch (_: Throwable) {}
        }
    }
    fun lockFor(pl: String, kind: String) = lock(pl + "_" + kind)

    /** the playlist part from the database; downloads it first when it was never downloaded. Old data → refreshed in the background. */
    suspend fun ensure(p: Playlist, kind: String): List<JSONObject>? {
        if (helper == null) return null
        val key = p.id + "_" + kind
        lock(key).withLock {
            val m = meta(p.id, kind)
            if (m == null) download(p, kind)
            else if (System.currentTimeMillis() - m.first > MAX_AGE) refreshLater(p, kind)
            return read(p.id, kind)
        }
    }

    fun refreshLater(p: Playlist, kind: String) {
        bg.launch {
            val key = p.id + "_" + kind
            val l = lock(key)
            if (!l.tryLock()) return@launch
            try {
                val m = meta(p.id, kind)
                if (m == null || System.currentTimeMillis() - m.first > MAX_AGE) { download(p, kind); Repo.forgetCatalogs(); SearchIndex.clear() }
            } catch (_: Throwable) {} finally { l.unlock() }
        }
    }
}

/** The window shown when a playlist is added: channels, movies and series are loaded onto the box, with progress. */
object Import {
    class Part(val label: String, val icon: Int) {
        var n by mutableIntStateOf(0)
        var pct by mutableFloatStateOf(-1f)
        var state by mutableIntStateOf(0)  // 0 waiting, 1 loading, 2 done, 3 failed
        var err by mutableStateOf<String?>(null)
    }
    var name by mutableStateOf<String?>(null)
    var show by mutableStateOf(false)
    var parts by mutableStateOf<List<Part>>(emptyList())
    var started by mutableLongStateOf(0L)
    var finished by mutableStateOf(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** playlists added while another one is still loading wait their turn */
    private val queue = ArrayDeque<Pair<Playlist, Boolean>>()
    private var currentId: String? = null
    fun busy() = name != null && !finished

    /** at start: an active playlist whose movies/series were never put on the box (e.g. added before this version) gets the window too */
    fun missing(ctx: Context) {
        try {
            Store.actives(ctx).filter { it.type == SourceType.XTREAM &&
                ((it.vod && !CatalogDb.has(it.id, "vod")) || (it.series && !CatalogDb.has(it.id, "series")) ||
                 (EpgSources.providerOn(it.id) && !GuideDb.has(GuideDb.srcOf(it)))) }.forEach { run(it) }
        } catch (_: Throwable) {}
        GuideSync.start(ctx)
        // old guides are brought up to date a little after the start, so the first screens come quickly
        GuideSync.laterAfter(ctx, 45_000)
    }

    fun run(p: Playlist, reset: Boolean = false, onDone: () -> Unit = {}) { runWith(p, reset, onDone) }

    private fun runWith(p: Playlist, reset: Boolean, onDone: () -> Unit) {
        if (name != null && !finished) {
            if (p.id != currentId) synchronized(queue) { if (queue.none { it.first.id == p.id }) queue.addLast(p to reset) }
            return
        }
        currentId = p.id
        val ch = Part("Kanali", R.drawable.lc_tv)
        val mv = if (p.type == SourceType.XTREAM && p.vod) Part("Filmovi", R.drawable.lc_film) else null
        val se = if (p.type == SourceType.XTREAM && p.series) Part("Serije", R.drawable.lc_layers) else null
        val ep = Part("EPG", R.drawable.lc_calendar)
        parts = listOfNotNull(ch, mv, se, ep); name = p.name; finished = false; show = true; started = System.currentTimeMillis()
        scope.launch {
            val jobs = listOfNotNull(mv?.let { it to "vod" }, se?.let { it to "series" })
            // movies and series are held for this window, so nothing else starts the same download meanwhile
            val held = jobs.map { CatalogDb.lockFor(p.id, it.second) }
            held.forEach { it.lock() }
            try {
                if (reset) CatalogDb.forgetNow(p.id)
                ch.state = 1
                try { ch.n = Repo.allChannels(p).size; ch.pct = 1f; ch.state = 2 } catch (e: Throwable) { fail(ch, e) }
                for ((part, kind) in jobs) {
                    part.state = 1
                    val before = CatalogDb.count(p.id, kind)
                    try {
                        part.n = CatalogDb.download(p, kind) { items, read, total ->
                            part.n = items
                            part.pct = when {
                                total > 0 -> (read.toFloat() / total).coerceIn(0f, 0.99f)
                                before > 0 -> (items.toFloat() / before).coerceIn(0f, 0.99f)
                                else -> -1f
                            }
                        }
                        part.pct = 1f; part.state = 2
                    } catch (e: Throwable) { fail(part, e) }
                }
            } finally { held.forEach { it.unlock() } }
            // the TV guide of this playlist (provider's and your own EPG links), on the box too
            ep.state = 1
            try {
                GuideSync.holding {
                    val ctx = NewContent.appCtx
                    val js = if (ctx != null) GuideSync.jobs(ctx, p) else emptyList()
                    var done = 0; var total = 0
                    js.forEachIndexed { i, j ->
                        val n = GuideDb.download(j.src, j.url, j.channels) { items, read, size ->
                            ep.n = total + items
                            ep.pct = ((i + (if (size > 0) read.toFloat() / size else 0.5f)) / js.size).coerceIn(0f, 0.99f)
                        }
                        total += n; done++; ep.n = total
                        OwnGuide.drop(j.url)
                    }
                    ProgIndex.clear()
                }
                ep.pct = 1f; ep.state = 2
            } catch (e: Throwable) { fail(ep, e) }
            Repo.forgetCatalogs(); SearchIndex.clear()
            finished = true
            kotlinx.coroutines.withContext(Dispatchers.Main) { onDone() }
            val next = synchronized(queue) { queue.removeFirstOrNull() }
            if (next != null) { kotlinx.coroutines.withContext(Dispatchers.Main) { name = null; runWith(next.first, next.second) {} }; return@launch }
            // all fine: closes by itself; something failed: stays open with the reason until OK
            if (parts.none { it.state == 3 }) { delay(3000); if (finished) { show = false; name = null } }
        }
    }

    private fun fail(part: Part, e: Throwable) {
        part.state = 3; part.err = (e.message ?: e.javaClass.simpleName).take(160)
        android.util.Log.w("PRETORIAN", "import " + part.label + ": " + part.err, e) // i18n-ignore
        NewContent.appCtx?.let { CrashLog.archive(it, "import", part.label + "\n" + android.util.Log.getStackTraceString(e)) } // i18n-ignore
    }

    /** 0..1 over all parts (channels count little, movies and series most) */
    fun overall(): Float {
        val w = parts.map { if (it.icon == R.drawable.lc_tv) 0.15f else if (it.icon == R.drawable.lc_calendar) 0.6f else 1f }
        val sum = w.sum().takeIf { it > 0 } ?: return 0f
        return parts.mapIndexed { i, p -> w[i] * when (p.state) { 2, 3 -> 1f; 1 -> p.pct.coerceAtLeast(0f); else -> 0f } }.sum() / sum
    }
}

@Composable
fun ImportDialog() {
    if (!Import.show || Import.name == null) return
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(500); tick++ } }
    Popup(trf("Učitavam playlistu: %s", Import.name ?: ""), onDismiss = { Import.show = false }, width = 620.dp) {
        tick.let { }
        T("Kanali, filmovi i serije se spremaju na box, da poslije sve radi odmah.", 13.sp, C.Muted, maxLines = 2)
        Spacer(Modifier.height(4.dp))
        Import.parts.forEach { p ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Ico(p.icon, 20.dp, if (p.state == 2) C.Green else C.Text)
                    T(p.label, 16.sp, C.Text, FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    val right = when (p.state) {
                        0 -> tr("čeka")
                        3 -> tr("nije uspjelo")
                        2 -> "✓ " + "%,d".format(p.n).replace(',', '.')
                        else -> "%,d".format(p.n).replace(',', '.') + (if (p.pct >= 0) " · ${(p.pct * 100).toInt()}%" else "")
                    }
                    T(right, 14.sp, if (p.state == 3) C.Danger else if (p.state == 2) C.Green else C.Soft, FontWeight.SemiBold)
                }
                Bar(p)
                p.err?.let { T(it, 12.sp, C.Danger, maxLines = 2) }
            }
        }
        Spacer(Modifier.height(4.dp))
        val all = Import.overall()
        val el = System.currentTimeMillis() - Import.started
        val status = when {
            Import.finished -> tr("Gotovo ✓")
            all > 0.05f && el > 5000 -> {
                val left = ((el / all) * (1 - all) / 1000).toInt()
                trf("Ukupno %s%%", (all * 100).toInt()) + " · " + (if (left < 60) trf("još oko %s s", maxOf(5, left / 5 * 5)) else trf("još oko %s min", (left + 59) / 60))
            }
            else -> trf("Ukupno %s%%", (all * 100).toInt())
        }
        T(status, 15.sp, if (Import.finished) C.Green else C.Text, FontWeight.Bold)
        Button(if (Import.finished) "U redu" else "Nastavi u pozadini", R.drawable.lc_check, primary = true) { Import.show = false; if (Import.finished) Import.name = null }
    }
}

@Composable
private fun Bar(p: Import.Part) {
    var phase by remember { mutableFloatStateOf(0f) }
    if (p.state == 1 && p.pct < 0) LaunchedEffect(Unit) { while (true) { delay(40); phase = (phase + 0.015f) % 1f } }
    Box(Modifier.fillMaxWidth().height(8.dp).clip(Rnd(4.dp)).background(C.Line)) {
        val frac = when (p.state) { 2, 3 -> 1f; 1 -> p.pct; else -> 0f }
        val col = if (p.state == 3) C.Danger else if (p.state == 2) C.Green else C.Red
        val f = if (frac >= 0f) frac.coerceIn(0f, 1f) else (phase % 1f)
        Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(Rnd(4.dp)).background(if (frac >= 0f) col else col.copy(alpha = 0.6f)))
    }
}

/**
 * The TV guide kept on the box (in the same database): the provider's guide of every playlist ("pl:<id>")
 * and your own EPG links ("url:<link>"), only for channels you have, 6 h back and 36 h ahead (12 h in Lite).
 * Read from here first; when a channel has nothing here the old way (asking the provider) still works.
 */
object GuideDb {
    const val BACK = 6 * 3600_000L
    val ahead get() = (if (Perf.lite) 12 else 36) * 3600_000L
    private val known = java.util.concurrent.ConcurrentHashMap<String, Long>()
    /** guides that had nothing for your channels (in memory only): not downloaded again for a day */
    val empty = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun srcOf(p: Playlist) = "pl:" + p.id // i18n-ignore
    fun srcOf(url: String) = "url:" + url // i18n-ignore

    /** time of the last full download of a source, or null */
    fun at(src: String): Long? {
        known[src]?.let { return it }
        return try {
            CatalogDb.db()?.rawQuery("SELECT at FROM gmeta WHERE src=?", arrayOf(src))?.use { c -> if (c.moveToFirst()) c.getLong(0) else null } // i18n-ignore
        } catch (e: Throwable) { null }?.also { known[src] = it }
    }
    fun has(src: String) = at(src) != null

    fun channelIds(source: String, query: String): List<String> {
        if (query.isBlank()) return emptyList()
        return CatalogDb.db()?.rawQuery(
            "SELECT DISTINCT ch FROM progs WHERE src=? AND instr(ch,?)>0 ORDER BY ch LIMIT 30",
            arrayOf(source, query.trim().lowercase())
        )?.use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }.orEmpty()
    }

    fun items(src: String, ch: String?, limit: Int): List<EpgItem> {
        val id = ch?.lowercase()?.ifBlank { null } ?: return emptyList()
        val now = System.currentTimeMillis()
        return try {
            CatalogDb.db()?.rawQuery("SELECT t, d, s, e FROM progs WHERE src=? AND ch=? AND e>? ORDER BY s LIMIT ?", // i18n-ignore
                arrayOf(src, id, (now - 60_000).toString(), limit.toString()))?.use { c ->
                val out = ArrayList<EpgItem>()
                while (c.moveToNext()) out += EpgItem(c.getString(0) ?: "", c.getString(1), c.getLong(2), c.getLong(3))
                out
            } ?: emptyList()
        } catch (e: Throwable) { emptyList() }
    }

    /** every programme of a source between two times (search, sport) */
    fun range(src: String, from: Long, to: Long, channels: Set<String>? = null, title: Regex? = null): List<Sports.Prog> = try {
        CatalogDb.db()?.rawQuery("SELECT ch, s, e, t, k, d FROM progs WHERE src=? AND e>? AND s<?", arrayOf(src, from.toString(), to.toString()))?.use { c -> // i18n-ignore
            val out = ArrayList<Sports.Prog>()
            while (c.moveToNext()) {
                val ch = c.getString(0) ?: continue
                if (channels != null && ch !in channels) continue
                val t = c.getString(3) ?: continue
                if (title != null && !title.containsMatchIn(t)) continue
                out += Sports.Prog(ch, c.getLong(1), c.getLong(2), t, c.getString(4), c.getString(5))
            }
            out
        } ?: emptyList()
    } catch (e: Throwable) { emptyList() }

    /** downloads one guide into the database; the old one stays until the new one is complete (and not empty) */
    fun download(src: String, url: String, channels: Set<String>, progress: ((Int, Long, Long) -> Unit)? = null): Int {
        val d = CatalogDb.db() ?: throw IllegalStateException("db") // i18n-ignore
        val now = System.currentTimeMillis()
        var n = 0; var lastBytes = 0L; var total = -1L
        d.beginTransactionNonExclusive()
        try {
            d.delete("progs", "src=?", arrayOf(src)) // i18n-ignore
            val st = d.compileStatement("INSERT INTO progs(src, ch, s, e, t, d, k) VALUES(?,?,?,?,?,?,?)") // i18n-ignore
            val ok = Xmltv.each(url, channels, now - BACK, now + ahead, true, bytes = { r, t -> lastBytes = r; total = t; progress?.invoke(n, r, t) }) { pr ->
                st.clearBindings()
                st.bindString(1, src); st.bindString(2, pr.channel); st.bindLong(3, pr.start); st.bindLong(4, pr.end); st.bindString(5, pr.title)
                st.bindString(6, pr.desc ?: ""); st.bindString(7, pr.category ?: "")
                st.executeInsert()
                n++
                if (n % 1000 == 0) progress?.invoke(n, lastBytes, total)
            }
            if (!ok) throw java.io.IOException(tr("EPG server ne odgovara"))
            if (n > 0) {
                d.insertWithOnConflict("gmeta", null, ContentValues().apply { put("src", src); put("at", now); put("n", n) }, SQLiteDatabase.CONFLICT_REPLACE) // i18n-ignore
                d.setTransactionSuccessful()
            }
        } finally { d.endTransaction() }
        if (n == 0) {
            // nothing for your channels in this guide: the old one (if any) stays; asked again in a day, not every half hour
            empty[src] = now
            return 0
        }
        known[src] = now
        return n
    }

    fun forget(src: String) { try { CatalogDb.db()?.let { it.delete("progs", "src=?", arrayOf(src)); it.delete("gmeta", "src=?", arrayOf(src)) }; known.remove(src) } catch (_: Throwable) {} } // i18n-ignore
    fun markStale() { try { CatalogDb.db()?.execSQL("UPDATE gmeta SET at=0"); known.clear() } catch (_: Throwable) {} } // i18n-ignore
}

/** Which guides belong on the box and keeping them fresh (once a day, while nobody is watching). */
object GuideSync {
    private val lock = Mutex()
    private val bg = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop = false
    private const val MAX_AGE = 20 * 3600_000L

    class Job(val src: String, val url: String, val channels: Set<String>)

    /** guides of the active playlists: each provider's own one (if switched on) and your own EPG links */
    suspend fun jobs(ctx: Context, only: Playlist? = null): List<Job> {
        val pls = if (only != null) listOf(only) else Store.actives(ctx)
        val out = ArrayList<Job>()
        val allIds = HashSet<String>()
        for (p in pls) {
            val chans = try { Repo.allChannels(p) } catch (e: Throwable) { emptyList() }
            val ids = chans.mapNotNull { it.epg?.lowercase()?.ifBlank { null } }.toSet()
            allIds += ids; chans.forEach { allIds += Sports.norm(it.name).replace(" ", "") }
            if (p.type == SourceType.XTREAM && EpgSources.providerOn(p.id) && ids.isNotEmpty()) out += Job(GuideDb.srcOf(p), Xtream(p).xmltvUrl(), ids)
        }
        val urls = (if (only != null) EpgSources.srcs(only.id) else pls.flatMap { EpgSources.srcs(it.id) }).map { it.url }.distinct()
        urls.forEach { u -> out += Job(GuideDb.srcOf(u), u, allIds) }
        return out
    }

    fun stale(src: String) = (GuideDb.at(src) ?: GuideDb.empty[src])?.let { System.currentTimeMillis() - it > MAX_AGE } ?: true

    /** quietly brings old guides up to date; never while a channel is playing in full screen */
    fun later(ctx: Context, force: Boolean = false) {
        bg.launch {
            if (Import.busy()) return@launch   // the import window is doing it right now
            if (!lock.tryLock()) return@launch
            try {
                for (j in jobs(ctx)) {
                    if (!force && !stale(j.src)) continue
                    while (CrashLog.screen.startsWith("Player")) delay(60_000) // i18n-ignore
                    try { GuideDb.download(j.src, j.url, j.channels); OwnGuide.drop(j.url) } catch (_: Throwable) {}
                }
                ProgIndex.clear()
            } finally { lock.unlock() }
        }
    }

    fun laterAfter(ctx: Context, ms: Long) { val app = ctx.applicationContext; bg.launch { delay(ms); later(app) } }

    /** every half hour: is a guide older than a day? */
    fun start(ctx: Context) {
        if (loop) return
        loop = true
        val app = ctx.applicationContext
        bg.launch { while (true) { delay(30 * 60_000L); try { later(app) } catch (_: Throwable) {} } }
    }

    suspend fun <T> holding(block: suspend () -> T): T = lock.withLock { block() }
}
