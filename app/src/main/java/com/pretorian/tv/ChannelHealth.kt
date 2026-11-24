package com.pretorian.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * FIX 9: whether a channel works. Green = it played or answered with a picture stream, yellow = it does not work right now,
 * no mark = not checked yet. A result is trusted for one hour. Sources: real playback (the player's own retries) and a slow,
 * light check of the opened channel list that only runs while nothing plays (one connection at a time, a few KB per channel).
 */
object ChannelHealth {
    var rev by mutableIntStateOf(0)
    private const val TTL = 60 * 60_000L
    private val state = HashMap<String, Pair<Boolean, Long>>()     // url → (works, when)
    @Volatile private var loaded = false
    /** Settings switch "Provjera kanala" (on by default) */
    val probeOn get() = Prefs.bool("ch_probe", true)

    private fun load() {
        if (loaded) return; loaded = true
        try {
            val o = org.json.JSONObject(Prefs.str("ch_health", "{}")); val now = System.currentTimeMillis()
            o.keys().forEach { k -> o.optJSONArray(k)?.let { a -> val t = a.optLong(1); if (now - t < TTL) state[k] = (a.optInt(0) == 1) to t } }
        } catch (_: Exception) {}
    }
    private fun save() {
        val o = org.json.JSONObject()
        state.entries.sortedByDescending { it.value.second }.take(2000).forEach { (k, v) -> o.put(k, org.json.JSONArray().put(if (v.first) 1 else 0).put(v.second)) }
        Prefs.put("ch_health", o.toString())
    }

    /** true works, false not working now, null not checked (or older than an hour) */
    @Synchronized fun of(url: String): Boolean? {
        load(); val v = state[url] ?: return null
        return if (System.currentTimeMillis() - v.second < TTL) v.first else null
    }
    @Synchronized fun mark(url: String?, ok: Boolean) {
        if (url.isNullOrBlank()) return
        load(); val old = state[url]
        state[url] = ok to System.currentTimeMillis()
        if (old?.first != ok || System.currentTimeMillis() - (old?.second ?: 0) > 5 * 60_000L) { save(); rev++ }
    }

    /** something plays (small picture or full screen): then the check waits, the provider may allow one connection only */
    @Volatile var previewOn = false
    @Volatile var playerOn = false
    private fun busy() = previewOn || playerOn

    /** checks the channels of the open list one by one, slowly, skipping the ones with a fresh result; stops when the list closes */
    suspend fun probe(list: List<Channel>) = withContext(Dispatchers.IO) {
        if (!probeOn) return@withContext
        delay(4000)
        for (c in list) {
            if (!probeOn) return@withContext
            if (of(c.url) != null || Stalker.isLink(c.url)) continue
            while (busy()) delay(2000)
            val ok = try { check(Net.udp(c.url)) } catch (_: Exception) { null }
            if (ok != null) mark(c.url, ok)
            delay(1500)
        }
    }

    /** a light look at a stream: an HLS playlist that starts with #EXTM3U, or a transport stream with its sync byte; null = cannot tell */
    private fun check(url: String): Boolean? {
        if (!url.startsWith("http")) return null
        var cur = url
        repeat(4) {
            val c = URL(cur).openConnection() as HttpURLConnection
            c.connectTimeout = 4000; c.readTimeout = 4000; c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", Net.UA)
            try {
                val code = c.responseCode
                if (code in 300..399) { cur = URL(URL(cur), c.getHeaderField("Location") ?: return false).toString(); return@repeat }
                if (code !in 200..299) return false
                val buf = ByteArray(2048); var n = 0
                c.inputStream.use { ins -> while (n < buf.size) { val r = ins.read(buf, n, buf.size - n); if (r <= 0) break; n += r } }
                if (n <= 0) return false
                val head = String(buf, 0, minOf(n, 16), Charsets.ISO_8859_1)
                return head.trimStart('﻿').startsWith("#EXTM3U") || (0 until minOf(n, 400)).any { buf[it] == 0x47.toByte() && (it + 188 >= n || buf[it + 188] == 0x47.toByte()) }
            } finally { c.disconnect() }
        }
        return null
    }
}

/** The lamp after the quality label: green works, yellow not working now, an empty ring not checked yet. */
@Composable
fun HealthLamp(url: String, size: Dp = 8.dp) {
    @Suppress("UNUSED_VARIABLE") val r = ChannelHealth.rev
    when (ChannelHealth.of(url)) {
        true -> Box(Modifier.size(size).clip(CircleShape).background(Color(0xFF3FBF5F)))
        false -> Box(Modifier.size(size).clip(CircleShape).background(Color(0xFFF0B429)))
        null -> Box(Modifier.size(size).clip(CircleShape).border(1.dp, Color(0x80FFFFFF), CircleShape))
    }
}
