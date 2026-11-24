package com.pretorian.tv

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.text.Normalizer

/**
 * 3.0.0.1 – the identity of this installation.
 *
 * ID: 128 random bits (UUID v4 from SecureRandom), made once on the box without internet, never from MAC, serial, IMEI
 * or any hardware value. Kept in the app's no-backup folder (an AtomicFile), so it survives restarts, reboots and
 * updates, but a backup restored on another box never copies it; a full "clear data" makes a new one. Not editable.
 * Shown as "PTR-XXXX-XXXX-XXXX" (the first 48 bits); reports carry the full UUID.
 *
 * Name: chosen by the owner, any printable Unicode (letters of any script, emoji, symbols). Stored next to the ID as
 * UTF-8, exactly as entered after cleaning: control characters and broken surrogates removed, NFC, trimmed, at most
 * 256 code points and 1024 UTF-8 bytes. It is only ever shown as text; files use [fileSafe], never the name itself.
 */
object DeviceIdentity {
    const val MAX_CP = 256
    const val MAX_BYTES = 1024
    var rev by mutableIntStateOf(0)
    @Volatile private var idCache: String? = null
    @Volatile private var nameCache: String? = null
    @Volatile private var dir: File? = null

    fun init(ctx: Context) { if (dir == null) dir = ctx.noBackupFilesDir; id(); name() }

    private fun read(f: String): String? = try { val d = dir ?: return null; val af = android.util.AtomicFile(File(d, f)); if (!af.baseFile.exists()) null else String(af.readFully(), Charsets.UTF_8) } catch (_: Throwable) { null }
    private fun write(f: String, v: String): Boolean = try {
        val af = android.util.AtomicFile(File(dir ?: return false, f))
        val o = af.startWrite(); try { o.write(v.toByteArray(Charsets.UTF_8)); af.finishWrite(o); true } catch (e: Throwable) { af.failWrite(o); false }
    } catch (_: Throwable) { false }

    /** the installation id (full UUID); made the first time it is asked for */
    @Synchronized fun id(): String {
        idCache?.let { return it }
        if (dir == null) return "unknown"   // before init (very early crash): never make an id that is not saved  // i18n-ignore
        val old = read("device_id")?.trim()?.takeIf { isUuid(it) }
        if (old != null) { idCache = old; return old }
        val b = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        b[6] = ((b[6].toInt() and 0x0f) or 0x40).toByte(); b[8] = ((b[8].toInt() and 0x3f) or 0x80).toByte()
        val bb = java.nio.ByteBuffer.wrap(b)
        val u = java.util.UUID(bb.long, bb.long).toString()
        if (!write("device_id", u)) return u   // not saved: used for this run only, tried again next time
        idCache = u
        return u
    }
    internal fun isUuid(s: String) = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$").matches(s)  // i18n-ignore
    /** "PTR-8F2A-91C4-7D03" */
    fun short(id: String = id()): String = if (!isUuid(id)) "PTR-unknown" else id.replace("-", "").take(12).uppercase().chunked(4).joinToString("-", prefix = "PTR-")  // i18n-ignore

    /** the device name, "" when none was given yet */
    fun name(): String = nameCache ?: (read("device_name") ?: "").also { if (dir != null) nameCache = it }
    fun hasName() = name().isNotBlank()
    /** saves a cleaned name; returns false (and keeps the old one) when it is empty after cleaning */
    fun setName(raw: String): Boolean {
        val c = clean(raw); if (c.isBlank()) return false
        if (!write("device_name", c)) return false
        nameCache = c; rev++
        DiagEvents.add("app", "device name set")  // i18n-ignore
        return true
    }

    /** printable text only, NFC, trimmed, within the code point and byte limits, never cutting a character in half */
    fun clean(raw: String): String {
        val n = Normalizer.normalize(raw, Normalizer.Form.NFC)
        val sb = StringBuilder()
        var i = 0; var cps = 0; var bytes = 0
        while (i < n.length && cps < MAX_CP) {
            val ch = n[i]
            if (Character.isHighSurrogate(ch) && (i + 1 >= n.length || !Character.isLowSurrogate(n[i + 1]))) { i++; continue }   // broken pair
            if (Character.isLowSurrogate(ch)) { i++; continue }
            val cp = n.codePointAt(i); val len = Character.charCount(cp)
            i += len
            val t = Character.getType(cp)
            val keep = when {
                cp == '\t'.code || cp == '\n'.code || cp == '\r'.code -> { if (sb.isNotEmpty() && sb.last() != ' ') sb.append(' '); false }
                t == Character.CONTROL.toInt() || t == Character.UNASSIGNED.toInt() || t == Character.PRIVATE_USE.toInt() || t == Character.SURROGATE.toInt() -> false
                t == Character.LINE_SEPARATOR.toInt() || t == Character.PARAGRAPH_SEPARATOR.toInt() -> false
                // format characters: only the ones emoji need (joiner, variation selectors, tags)
                t == Character.FORMAT.toInt() -> cp == 0x200D || cp in 0xE0020..0xE007F
                else -> true
            }
            if (!keep) continue
            val b = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8).size
            if (bytes + b > MAX_BYTES) break
            sb.appendCodePoint(cp); bytes += b; cps++
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    /** a shorter form for tight places, cut at a whole character with "…" */
    fun display(name: String = name(), maxCp: Int = 40): String {
        if (name.codePointCount(0, name.length) <= maxCp) return name
        return name.substring(0, name.offsetByCodePoints(0, maxCp)).trimEnd() + "…"
    }

    /** letters and digits only, for file names: "Living-Room"; never empty, never longer than 40 */
    fun fileSafe(name: String = name()): String {
        val base = Normalizer.normalize(name, Normalizer.Form.NFKD).replace(Regex("\\p{M}+"), "")
            .map { c -> if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9') c else '-' }.joinToString("")
            .replace(Regex("-+"), "-").trim('-').take(40).trim('-')
        return base.ifEmpty { "device" }  // i18n-ignore
    }

    /** suggested name for a new box: its model ("Google TV Streamer"), else "Media Center" */
    fun suggestion(): String = clean(android.os.Build.MODEL ?: "").ifBlank { "Media Center" }  // i18n-ignore
}

/** Device identity: the id (read-only) and the name. Used in first setup, once after an update, and in Diagnostics. */
@Composable
fun DeviceIdentityStep(title: Boolean = true, onDone: () -> Unit) {
    var name by remember { mutableStateOf(DeviceIdentity.name().ifBlank { DeviceIdentity.suggestion() }) }
    var err by remember { mutableStateOf<String?>(null) }
    val field = remember { FocusRequester() }
    val cont = remember { FocusRequester() }
    fun save() {
        if (DeviceIdentity.clean(name).isBlank()) { err = tr("Upiši naziv uređaja."); return }
        if (!DeviceIdentity.setName(name)) { err = tr("Naziv se nije mogao sačuvati. Pokušaj ponovo."); return }
        onDone()
    }
    if (title) T("Identitet uređaja", 28.sp, C.Text, FontWeight.Bold)
    T("Ovaj uređaj je dobio jedinstveni ID. Daj mu naziv.", 16.sp, C.Soft, maxLines = 2)
    Column(Modifier.fillMaxWidth().dawnGlass(14.dp).padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        T("ID uređaja", 12.sp, C.Muted, FontWeight.Bold)
        T(DeviceIdentity.short(), 22.sp, C.Text, FontWeight.Bold)  // i18n-ignore
    }
    Field("Naziv uređaja", name, { name = it.take(4 * DeviceIdentity.MAX_CP); err = null }, Modifier.fillMaxWidth().focusRequester(field), onDone = { try { cont.requestFocus() } catch (_: Exception) {} })
    T(err ?: tr("Bilo koji naziv: slova, brojevi, znakovi, razmaci, emoji."), 13.sp, if (err != null) C.Danger else C.Muted, maxLines = 2)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button("Nastavi", R.drawable.lc_check, primary = true, focusRequester = cont) { save() }
    }
    LaunchedEffect(Unit) { cont.tryFocus() }
}
