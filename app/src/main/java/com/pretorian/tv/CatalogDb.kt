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
    private var helper: SQLiteOpenHelper? = null
    private val locks = HashMap<String, Mutex>()
    private val bg = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(ctx: Context) {
        if (helper != null) return
        helper = object : SQLiteOpenHelper(ctx, "catalog.db", null, 1) { // i18n-ignore
            override fun onConfigure(db: SQLiteDatabase) { db.enableWriteAheadLogging() }
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL("CREATE TABLE items(pl TEXT, kind TEXT, id TEXT, c TEXT, n TEXT, i TEXT, r TEXT, u TEXT)") // i18n-ignore
                db.execSQL("CREATE INDEX items_pk ON items(pl, kind)") // i18n-ignore
                db.execSQL("CREATE TABLE meta(pl TEXT, kind TEXT, at INTEGER, n INTEGER, PRIMARY KEY(pl, kind))") // i18n-ignore
            }
            override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {
                db.execSQL("DROP TABLE IF EXISTS items"); db.execSQL("DROP TABLE IF EXISTS meta"); onCreate(db) // i18n-ignore
            }
        }
    }

    private fun db(): SQLiteDatabase? = try { helper?.writableDatabase } catch (e: Throwable) { null }

    /** (time of the last full download, how many) or null when this playlist part was never downloaded */
    private fun meta(pl: String, kind: String): Pair<Long, Int>? = try {
        db()?.rawQuery("SELECT at, n FROM meta WHERE pl=? AND kind=?", arrayOf(pl, kind))?.use { c -> // i18n-ignore
            if (c.moveToFirst()) c.getLong(0) to c.getInt(1) else null
        }
    } catch (e: Throwable) { null }

    fun count(pl: String, kind: String): Int = meta(pl, kind)?.second ?: -1
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
        d.beginTransactionNonExclusive()
        try {
            d.delete("items", "pl=? AND kind=?", arrayOf(p.id, kind)) // i18n-ignore
            val st = d.compileStatement("INSERT INTO items(pl, kind, id, c, n, i, r, u) VALUES(?,?,?,?,?,?,?,?)") // i18n-ignore
            Xtream(p).catalogEach(kind, bytes = { r, t -> lastBytes = r; total = t; progress?.invoke(n, r, t) }) { o ->
                st.clearBindings()
                st.bindString(1, p.id); st.bindString(2, kind); st.bindString(3, o.str("id")); st.bindString(4, o.str("c"))
                st.bindString(5, o.str("n")); st.bindString(6, o.str("i")); st.bindString(7, o.str("r")); st.bindString(8, o.str("u"))
                st.executeInsert()
                n++
                if (n % 500 == 0) progress?.invoke(n, lastBytes, total)
            }
            d.insertWithOnConflict("meta", null, ContentValues().apply { // i18n-ignore
                put("pl", p.id); put("kind", kind); put("at", System.currentTimeMillis()); put("n", n) // i18n-ignore
            }, SQLiteDatabase.CONFLICT_REPLACE)
            d.setTransactionSuccessful()
        } finally { d.endTransaction() }
        return n
    }

    private fun lock(key: String) = synchronized(locks) { locks.getOrPut(key) { Mutex() } }
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
    }
    var name by mutableStateOf<String?>(null)
    var show by mutableStateOf(false)
    var parts by mutableStateOf<List<Part>>(emptyList())
    var started by mutableLongStateOf(0L)
    var finished by mutableStateOf(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** playlists added while another one is still loading wait their turn */
    private val queue = ArrayDeque<Pair<Playlist, Boolean>>()

    fun run(p: Playlist, reset: Boolean = false, onDone: () -> Unit = {}) {
        if (name != null && !finished) { synchronized(queue) { queue.addLast(p to reset) }; return }
        val ch = Part("Kanali", R.drawable.lc_tv)
        val mv = if (p.type == SourceType.XTREAM && p.vod) Part("Filmovi", R.drawable.lc_film) else null
        val se = if (p.type == SourceType.XTREAM && p.series) Part("Serije", R.drawable.lc_layers) else null
        parts = listOfNotNull(ch, mv, se); name = p.name; finished = false; show = true; started = System.currentTimeMillis()
        scope.launch {
            val jobs = listOfNotNull(mv?.let { it to "vod" }, se?.let { it to "series" })
            // movies and series are held for this window, so nothing else starts the same download meanwhile
            val held = jobs.map { CatalogDb.lockFor(p.id, it.second) }
            held.forEach { it.lock() }
            try {
                if (reset) CatalogDb.forgetNow(p.id)
                ch.state = 1
                try { ch.n = Repo.allChannels(p).size; ch.pct = 1f; ch.state = 2 } catch (e: Throwable) { ch.state = 3 }
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
                    } catch (e: Throwable) { part.state = 3 }
                }
            } finally { held.forEach { it.unlock() } }
            Repo.forgetCatalogs(); SearchIndex.clear()
            finished = true
            kotlinx.coroutines.withContext(Dispatchers.Main) { onDone() }
            val next = synchronized(queue) { queue.removeFirstOrNull() }
            if (next != null) { kotlinx.coroutines.withContext(Dispatchers.Main) { name = null; run(next.first, next.second) }; return@launch }
            delay(2500)
            if (finished) { show = false; name = null }
        }
    }

    /** 0..1 over all parts (channels count little, movies and series most) */
    fun overall(): Float {
        val w = parts.map { if (it.icon == R.drawable.lc_tv) 0.15f else 1f }
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
        Button(if (Import.finished) "U redu" else "Nastavi u pozadini", R.drawable.lc_check, primary = true) { Import.show = false }
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
