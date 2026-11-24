package com.pretorian.tv

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Phase D2: channels linked to a guide channel by hand. Stored per playlist and channel (key without the stream address). */
object EpgMap {
    private fun k(pid: String, url: String) = "epgmap_${pid}_" + ChannelHealth.key(url)
    fun of(pid: String, url: String): String? = Prefs.str(k(pid, url)).ifBlank { null }
    fun set(pid: String, url: String, guideId: String?) { if (guideId == null) Prefs.remove(k(pid, url)) else Prefs.put(k(pid, url), guideId.lowercase()); NowCache.drop(url) }
    /** every hand-linked guide id of a playlist (so the guide download keeps them) */
    fun ids(pid: String): Set<String> = Prefs.allStrings().filter { it.first.startsWith("epgmap_${pid}_") }.map { it.second }.toSet()
}

/**
 * The channels a guide source offers (id + display name), read from the start of the XMLTV file only (the channel list
 * comes before the programmes), kept on the box for a day. Bounded: at most 30,000 channels, 60 s.
 */
object GuideChannels {
    data class G(val id: String, val name: String)
    private val mem = HashMap<String, List<G>>()
    suspend fun of(ctx: Context, url: String): List<G> = withContext(Dispatchers.IO) {
        synchronized(mem) { mem[url] }?.let { return@withContext it }
        val f = File(File(ctx.cacheDir, "gchan").apply { mkdirs() }, ChannelHealth.key(url) + ".tsv")
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < 86_400_000L)
            return@withContext f.readLines().mapNotNull { l -> l.split('\t').takeIf { it.size == 2 }?.let { G(it[0], it[1]) } }.also { synchronized(mem) { mem[url] = it } }
        val out = ArrayList<G>()
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000; c.readTimeout = 20_000; c.setRequestProperty("User-Agent", Net.UA)
            val raw = java.io.BufferedInputStream(c.inputStream)
            raw.mark(2); val gz = raw.read() == 0x1f && raw.read() == 0x8b; raw.reset()
            val ins = if (gz) java.util.zip.GZIPInputStream(raw) else raw
            val xp = android.util.Xml.newPullParser(); xp.setInput(ins, null)
            val t0 = System.currentTimeMillis(); var id: String? = null; var inName = false
            loop@ while (true) {
                when (xp.next()) {
                    org.xmlpull.v1.XmlPullParser.END_DOCUMENT -> break@loop
                    org.xmlpull.v1.XmlPullParser.START_TAG -> when (xp.name) {
                        "channel" -> id = xp.getAttributeValue(null, "id")
                        "display-name" -> inName = id != null
                        "programme" -> break@loop
                    }
                    org.xmlpull.v1.XmlPullParser.TEXT -> if (inName) { id?.let { out += G(it, xp.text.trim()) }; id = null; inName = false }
                }
                if (out.size >= 30_000 || System.currentTimeMillis() - t0 > 60_000) break
            }
            c.disconnect()
        } catch (e: Throwable) { DiagEvents.add("epg", "guide channel list failed: " + e.javaClass.simpleName) }  // i18n-ignore
        if (out.isNotEmpty()) try { f.writeText(out.joinToString("\n") { it.id.replace('\t', ' ') + "\t" + it.name.replace('\t', ' ') }) } catch (_: Throwable) {}
        synchronized(mem) { mem[url] = out }
        out
    }
}

/** EPG › Pronađi kanal: choose a channel of the playlist (partial name, any case), then the guide channel that belongs to it. */
@Composable
fun FindChannelDialog(p: Playlist, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    var typing by remember { mutableStateOf(true) }
    var ch by remember { mutableStateOf<Channel?>(null) }
    val all by produceState<List<Channel>?>(null, p.id) { value = try { Repo.allChannels(p) } catch (_: Throwable) { emptyList() } }
    if (typing && ch == null) { TextDialog("Pronađi kanal", "Dio naziva kanala", q, onDismiss = { if (q.isBlank()) onDismiss() else typing = false }) { v -> q = v.trim(); typing = false }; return }
    val c = ch
    if (c == null) {
        val hits = all?.filter { q.isBlank() || it.name.contains(q, ignoreCase = true) }?.take(80)
        Popup(trf("Kanali · %s", q.ifBlank { p.name }), onDismiss) {
            OptionRow(trf("Traži: %s", q.ifBlank { "…" }), R.drawable.lc_search) { typing = true }
            when {
                hits == null -> T("Učitavam kanale…", 14.sp, C.Muted)
                hits.isEmpty() -> T("Nema rezultata!", 14.sp, C.Muted)
                else -> hits.forEach { h -> OptionRow(h.name, R.drawable.lc_tv, value = EpgMap.of(p.id, h.url)?.let { "✓ $it" }) { ch = h } }
            }
        }
        return
    }
    GuidePick(p, c, onBack = { ch = null }, onDone = onDismiss)
}

@Composable
private fun GuidePick(p: Playlist, c: Channel, onBack: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf(c.name.replace(Regex("^[A-Z]{2,3}\\s*[|:]\\s*"), "").replace(Regex("(?i)\\b(fhd|uhd|hd|sd|4k|raw|vip)\\b"), "").trim()) }
    var typing by remember { mutableStateOf(false) }
    val lists by produceState<List<GuideChannels.G>?>(null, p.id) {
        val urls = EpgSources.srcs(p.id).map { it.url } + (if (p.type == SourceType.XTREAM && EpgSources.providerOn(p.id)) listOf(Xtream(p).xmltvUrl()) else emptyList())
        value = urls.distinct().flatMap { GuideChannels.of(ctx, it) }.distinctBy { it.id.lowercase() }
    }
    val cur = EpgMap.of(p.id, c.url)
    if (typing) { TextDialog("Traži u vodiču", "Dio naziva", q, onDismiss = { typing = false }) { v -> q = v.trim(); typing = false }; return }
    Popup(trf("EPG za %s", c.name), onBack) {
        OptionRow(trf("Traži: %s", q.ifBlank { "…" }), R.drawable.lc_search) { typing = true }
        if (cur != null) OptionRow("Ukloni ručno povezivanje", R.drawable.lc_x, sub = cur) { EpgMap.set(p.id, c.url, null); onBack() }
        val l = lists
        when {
            l == null -> T("Učitavam listu kanala iz vodiča…", 14.sp, C.Muted)
            l.isEmpty() -> T("Vodič ove playliste nema listu kanala.", 14.sp, C.Muted)
            else -> {
                val words = q.lowercase().split(' ').filter { it.isNotBlank() }
                val hits = l.filter { g -> words.all { w -> g.name.lowercase().contains(w) || g.id.lowercase().contains(w) } }.take(60)
                if (hits.isEmpty()) T("Nema rezultata!", 14.sp, C.Muted)
                hits.forEach { g -> OptionRow(g.name, R.drawable.lc_calendar, sub = g.id, selected = cur == g.id.lowercase()) {
                    EpgMap.set(p.id, c.url, g.id)
                    GuideSync.later(ctx, force = true)
                    Toast.makeText(ctx, tr("Povezano. EPG se osvježava u pozadini."), Toast.LENGTH_SHORT).show()
                    onDone()
                } }
            }
        }
    }
}
