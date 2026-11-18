package com.pretorian.tv

import android.app.Activity
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/**
 * Technical player options (Settings › Player › Technical). Each one can be switched off on its own
 * if it misbehaves on a box; the ones that cost power or can upset a TV are off on a new install.
 */
object Tech {
    /** built-in FFmpeg sound decoder (AC3, E-AC3, MP2, DTS…), used only when the box has no decoder of its own */
    val ffmpeg get() = Prefs.bool("tech_ffmpeg", true)
    /** 0 automatic, 1 hardware only, 2 software (more CPU) */
    val decoder get() = Prefs.long("tech_decoder", 0).toInt()
    /** auto frame rate: the TV switches to the film's/channel's refresh rate (off on a new install) */
    val afr get() = Prefs.bool("tech_afr", false)
    /** "sound delay" in the player menu */
    val audioDelay get() = Prefs.bool("tech_adelay", true)
    /** a channel that does not start: the same channel from another playlist */
    val backup get() = Prefs.bool("tech_backup", true)
    /** pause a live channel (what comes meanwhile is kept in the player's memory) */
    val livePause get() = Prefs.bool("tech_pause", true)
    /** ◀◀ on a channel with archive: back in time through the provider's archive */
    val rewind get() = Prefs.bool("tech_rewind", true)
    /** archive (catch-up) for M3U lists that announce it (catchup=…) */
    val m3uCatchup get() = Prefs.bool("tech_m3ucatch", true)
    /** clean the box's database now and then */
    val dbClean get() = Prefs.bool("tech_dbclean", true)
    /** memory / CPU log while the app runs (Diagnostics), off on a new install */
    val monitor get() = Prefs.bool("tech_monitor", false)
}

/** Sound ahead / behind the picture, per channel (and per film), in milliseconds. Positive = the sound comes late. */
object AudioDelay {
    @Volatile var us = 0L
    private fun key(url: String) = "adelay_" + url.hashCode().toUInt().toString(16) // i18n-ignore
    fun of(url: String): Int = if (!Tech.audioDelay) 0 else Prefs.long(key(url), 0).toInt()
    fun set(url: String, ms: Int) { Prefs.put(key(url), ms.toLong()); us = ms * 1000L }
    fun use(url: String) { us = of(url) * 1000L }
    fun label(ms: Int) = when { ms == 0 -> "0 ms"; ms > 0 -> "+$ms ms"; else -> "$ms ms" } // i18n-ignore
}

/** Moves the clock the picture follows: the picture waits for (or catches up with) the sound. */
@OptIn(UnstableApi::class)
class DelaySink(s: AudioSink) : ForwardingAudioSink(s) {
    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        val p = super.getCurrentPositionUs(sourceEnded)
        if (p == AudioSink.CURRENT_POSITION_NOT_SET) return p
        return (p - AudioDelay.us).coerceAtLeast(0L)
    }
}

/** Which decoders the player may use (Settings › Player › Decoder). */
@OptIn(UnstableApi::class)
fun codecSelector(): MediaCodecSelector = MediaCodecSelector { mime, secure, tunneling ->
    val all = MediaCodecSelector.DEFAULT.getDecoderInfos(mime, secure, tunneling)
    val isVideo = mime.startsWith("video/")
    if (!isVideo) return@MediaCodecSelector all
    when (Tech.decoder) {
        1 -> all.filter { it.hardwareAccelerated }.ifEmpty { all }
        2 -> (all.filter { it.softwareOnly } + all.filter { !it.softwareOnly })
        else -> all
    }
}

/**
 * Auto frame rate: picks the TV mode with the same picture size and a refresh rate that fits the video
 * (25 → 50 Hz, 23.976 → 23.976/24 Hz, 29.97 → 59.94 Hz…). Leaving the player gives the TV back its own mode.
 */
object Afr {
    private var original = -1
    fun apply(a: Activity?, fps: Float) {
        if (a == null || !Tech.afr || fps <= 1f || Build.VERSION.SDK_INT < 23) return
        try {
            @Suppress("DEPRECATION") val d = a.windowManager.defaultDisplay
            val cur = d.mode
            val same = d.supportedModes.filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
            fun fit(r: Float): Float {
                // how well a refresh rate shows this video: exact multiples first, the smaller rate first
                for (k in 1..3) if (kotlin.math.abs(r - fps * k) < 0.05f) return k.toFloat()
                for (k in 1..3) if (kotlin.math.abs(r - fps * k) < 0.6f) return 10f + k
                return Float.MAX_VALUE
            }
            val best = same.minByOrNull { fit(it.refreshRate) }?.takeIf { fit(it.refreshRate) < Float.MAX_VALUE } ?: return
            if (best.modeId == cur.modeId) return
            if (original < 0) original = cur.modeId
            val lp = a.window.attributes
            lp.preferredDisplayModeId = best.modeId
            a.window.attributes = lp
        } catch (_: Throwable) {}
    }
    fun reset(a: Activity?) {
        if (a == null || Build.VERSION.SDK_INT < 23) return
        try {
            val lp = a.window.attributes
            if (lp.preferredDisplayModeId != 0) { lp.preferredDisplayModeId = 0; a.window.attributes = lp }
            original = -1
        } catch (_: Throwable) {}
    }
}

/** Link to a past show (archive / catch-up): Xtream's timeshift, or the M3U list's catchup template. */
object Catchups {
    fun has(p: Playlist?, ch: Channel): Boolean = ch.archive > 0 && p != null &&
        (p.type == SourceType.XTREAM || (p.type == SourceType.M3U && ch.catchup != null && Tech.m3uCatchup))

    fun url(p: Playlist?, ch: Channel, e: EpgItem): String? {
        if (p == null) return null
        if (p.type == SourceType.XTREAM) return Xtream(p).timeshiftUrl(ch.id, e)
        val cu = ch.catchup ?: return null
        if (!Tech.m3uCatchup) return null
        val kind = cu.substringBefore('|'); val src = cu.substringAfter('|', "")
        val start = e.start / 1000; val end = e.end / 1000; val now = System.currentTimeMillis() / 1000
        fun fill(t: String): String {
            var r = t
            val utc = java.util.TimeZone.getTimeZone("UTC") // i18n-ignore
            fun f(p: String, z: java.util.TimeZone? = null) = java.text.SimpleDateFormat(p, java.util.Locale.US).apply { if (z != null) timeZone = z }.format(java.util.Date(e.start))
            val pairs = listOf(
                "{utc}" to "$start", "\${start}" to "$start", "{start}" to "$start", "{utcend}" to "$end", "\${end}" to "$end", "{end}" to "$end", // i18n-ignore
                "{lutc}" to "$now", "\${now}" to "$now", "{now}" to "$now", "\${timestamp}" to "$now", "{timestamp}" to "$now", // i18n-ignore
                "{duration}" to "${end - start}", "\${duration}" to "${end - start}", "{offset}" to "${now - start}", "\${offset}" to "${now - start}", // i18n-ignore
                "{Y}" to f("yyyy"), "{m}" to f("MM"), "{d}" to f("dd"), "{H}" to f("HH"), "{M}" to f("mm"), "{S}" to f("ss"), // i18n-ignore
                "{utc:YmdHMS}" to f("yyyyMMddHHmmss", utc), "{(b)YmdHMS}" to f("yyyyMMddHHmmss"), // i18n-ignore
            )
            pairs.forEach { (k, v) -> r = r.replace(k, v) }
            return r
        }
        return when (kind) {
            "default" -> if (src.isNotBlank()) fill(src) else null // i18n-ignore
            "append" -> if (src.isNotBlank()) ch.url + fill(src) else null // i18n-ignore
            "shift", "timeshift" -> ch.url + (if ('?' in ch.url) "&" else "?") + "utc=$start&lutc=$now" // i18n-ignore
            "flussonic", "flussonic-hls", "fs" -> { // i18n-ignore
                val base = ch.url.substringBeforeLast('/')
                if (ch.url.contains(".m3u8")) "$base/index-$start-${end - start}.m3u8" else "$base/archive-$start-${end - start}.ts" // i18n-ignore
            }
            else -> if (src.isNotBlank()) fill(if (src.startsWith("http")) src else ch.url + src) else null
        }
    }
}

/** A channel that does not play: the same channel elsewhere (same guide id, or the same name without "HD", "VIP", country prefix…). */
/** A match started from Sport: the other channels in your lists that show it. The player switches to one of them when the
 *  picture is stuck (2 s, then 2 reconnects). Cleared when the player closes. */
object MatchWatch {
    @Volatile var alts: List<Channel> = emptyList()
    @Volatile var title: String = ""
    val active get() = alts.size > 1
    /** only while one of the match channels is on screen (zapping to another channel ends the match rule) */
    fun on(url: String?) = active && url != null && alts.any { it.url == url }
    fun set(title: String, channels: List<Channel>) { this.title = title; alts = channels.distinctBy { it.url } }
    fun clear() { alts = emptyList(); title = "" }
    fun next(tried: Set<String>): Channel? = alts.firstOrNull { it.url !in tried }
}

object StreamBackup {
    private val prefix = Regex("^[\\p{L}]{2,4}\\s*[|:]\\s*")
    private val noise = Regex("\\b(fhd|uhd|hd|sd|4k|hevc|h265|h\\.265|vip|raw|backup|bkp|alt|50fps|60fps|hq|lq)\\b")
    fun key(name: String): String =
        Sports.norm(name.replace(prefix, "")).replace(noise, " ").replace(Regex("[^\\p{L}\\p{N}]+"), "")

    suspend fun find(ctx: android.content.Context, ch: Channel, tried: Set<String>): Channel? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val k = key(ch.name); val epg = ch.epg?.lowercase()?.ifBlank { null }
        if (k.length < 2 && epg == null) return@withContext null
        val pls = Store.actives(ctx)
        // the channel's own playlist first (another copy there), then the others
        val ordered = pls.sortedBy { if (it.id == ch.pid) 0 else 1 }
        for (p in ordered) {
            val all = try { Repo.allChannels(p) } catch (e: Exception) { continue }
            val hit = all.firstOrNull { it.url !in tried && it.url != ch.url && epg != null && it.epg?.lowercase() == epg && key(it.name) == k }
                ?: all.firstOrNull { it.url !in tried && it.url != ch.url && k.length >= 3 && key(it.name) == k }
            if (hit != null) return@withContext hit
        }
        null
    }
}
