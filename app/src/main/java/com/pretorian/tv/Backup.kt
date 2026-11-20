package com.pretorian.tv

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Backup of every setting (playlists, groups, favourites, look, keys, watched…) into one JSON file. */
object Backup {
    data class Summary(val date: String, val playlists: Int, val favLists: Int, val encrypted: Boolean)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("pretorian", Context.MODE_PRIVATE)
    /** stay on this device: sign-in, chosen external player and file manager */
    private val DEVICE_ONLY = setOf("ext_player", "file_app", "profile_skip_ask", "backup_pass", "gh_token")
    /** Settings › Backup › Password for copies: copies in Download / on USB are encrypted with it (empty = readable file) */
    val pass get() = Prefs.str("backup_pass")

    /** which part of the backup a setting belongs to (see "Šta uključiti"); -1 = always */
    private fun part(k: String): Int = when {
        k == "playlists" || k.startsWith("pl_") -> 0
        k.startsWith("epg") -> 1
        k.startsWith("ovr_") -> 2
        k.startsWith("profile") || k.startsWith("pin_") || k == "simple_mode" || k == "simple_items" -> 3
        k.startsWith("fav") || k == "mylist" -> 4
        k == "recent_ch" -> 5
        k == "reminders" || k.startsWith("rem_") -> 6
        k.startsWith("look_") || k == "perf_mode" || k == "tv_view" -> 7
        k in setOf("app_lang", "meta_lang", "sub_langs", "audio_langs", "auto_subs", "auto_translate", "seek_step", "sub_size", "sub_style", "audio_default") -> 8
        k.startsWith("sport_") || k.startsWith("follow") -> 9
        k.startsWith("ai_") || k.endsWith("_key") || k.startsWith("os_") || k == "gemini_key" -> 10
        else -> -1
    }

    internal fun portable(k: String) = k !in DEVICE_ONLY && !k.startsWith("gd_") && !k.startsWith("sync_")

    internal fun included(k: String, parts: Set<Int>?) =
        portable(k) && (parts == null || part(k) < 0 || part(k) in parts)

    private fun payload(ctx: Context, parts: Set<Int>? = null): JSONObject {
        Watch.flush()
        val p = JSONObject()
        prefs(ctx).all.forEach { (k, v) ->
            if (!included(k, parts)) return@forEach
            val o = JSONObject()
            when (v) {
                is String -> o.put("t", "s").put("v", v)
                is Boolean -> o.put("t", "b").put("v", v)
                is Int -> o.put("t", "i").put("v", v)
                is Long -> o.put("t", "l").put("v", v)
                is Float -> o.put("t", "f").put("v", v.toDouble())
                else -> return@forEach
            }
            p.put(k, o)
        }
        val files = JSONObject()
        if (parts == null || 5 in parts) listOf("progress.json").forEach { n -> File(ctx.filesDir, n).takeIf { it.exists() }?.let { files.put(n, it.readText()) } }
        val out = JSONObject().put("prefs", p).put("files", files).put("full", parts == null) // i18n-ignore
        if (parts != null) out.put("parts", org.json.JSONArray(parts.toList()))
        if (parts == null || 3 in parts) out.put("profiles", Profiles.allSnapshots(ctx).also { profiles ->
            for (id in profiles.keys()) {
                val snapshot = profiles.getJSONObject(id)
                val settings = snapshot.getJSONObject("prefs")
                settings.keys().asSequence().filter { !included(it, parts) }.toList().forEach { settings.remove(it) }
                if (parts != null && 5 !in parts) snapshot.remove("files")
            }
        })
        return out
    }

    private fun key(password: String, salt: ByteArray): SecretKeySpec {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val k = f.generateSecret(PBEKeySpec(password.toCharArray(), salt, 20_000, 256)).encoded
        return SecretKeySpec(k, "AES")
    }

    fun export(ctx: Context, password: String?, parts: Set<Int>? = null): String {
        val data = payload(ctx, parts)
        val out = JSONObject().put("pretorian_backup", 1).put("app", BuildInfo.VERSION)
            .put("date", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()))
            .put("playlists", Store.playlists(ctx).size).put("favLists", Favs.lists(ctx).size)
        if (password.isNullOrBlank()) out.put("data", data)
        else {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
            val enc = c.doFinal(data.toString().toByteArray())
            out.put("enc", Base64.encodeToString(enc, Base64.NO_WRAP)).put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                .put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
        }
        return out.toString()
    }

    /** null when the text is not a PRETORIAN backup */
    fun summary(text: String): Summary? = try {
        val o = JSONObject(text)
        if (o.optInt("pretorian_backup") != 1) null
        else Summary(o.str("date"), o.optInt("playlists"), o.optInt("favLists"), o.has("enc"))
    } catch (e: Exception) { null }

    /** restores everything; throws with a message when the password is wrong or the file is broken */
    fun restore(ctx: Context, text: String, password: String?) {
        Watch.flush()
        val o = JSONObject(text)
        if (o.optInt("pretorian_backup") != 1) throw IllegalArgumentException(tr("Fajl nije PRETORIAN backup"))
        val data = if (o.has("enc")) {
            if (password.isNullOrBlank()) throw IllegalArgumentException(tr("Backup je zaštićen lozinkom"))
            try {
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.DECRYPT_MODE, key(password, Base64.decode(o.str("salt"), Base64.NO_WRAP)), GCMParameterSpec(128, Base64.decode(o.str("iv"), Base64.NO_WRAP)))
                JSONObject(String(c.doFinal(Base64.decode(o.str("enc"), Base64.NO_WRAP))))
            } catch (e: Exception) { throw IllegalArgumentException(tr("Pogrešna lozinka")) }
        } else o.getJSONObject("data")
        val p = data.getJSONObject("prefs")
        val full = data.optBoolean("full", false) // i18n-ignore
        val selection = data.optJSONArray("parts")?.let { array ->
            (0 until array.length()).map { array.getInt(it) }.toSet()
        }
        require(!full || selection == null)
        val store = prefs(ctx)
        val original = store.all
        val ed = store.edit()
        if (full) original.keys.filter(::portable).forEach { ed.remove(it) }
        p.keys().forEach { k ->
            if (!included(k, selection)) return@forEach
            val v = p.getJSONObject(k)
            when (v.getString("t")) {
                "s" -> ed.putString(k, v.getString("v")); "b" -> ed.putBoolean(k, v.getBoolean("v"))
                "i" -> ed.putInt(k, v.getInt("v")); "l" -> ed.putLong(k, v.getLong("v")); "f" -> ed.putFloat(k, v.getDouble("v").toFloat())
                else -> throw IllegalArgumentException(tr("Fajl nije PRETORIAN backup"))
            }
        }
        val writes = mutableMapOf<File, String>()
        data.optJSONObject("files")?.let { files ->
            if (files.has("progress.json") && (selection == null || 5 in selection)) {
                val contents = files.getString("progress.json")
                org.json.JSONArray(contents)
                writes[File(ctx.filesDir, "progress.json")] = contents
            }
        }
        val profiles = data.optJSONObject("profiles")
        if (profiles != null && (selection == null || 3 in selection)) {
            for (id in profiles.keys()) {
                require(id.matches(Regex("[a-zA-Z0-9_-]+")))
                val file = File(File(ctx.filesDir, "profiles"), "$id.json")
                val snapshot = profiles.getJSONObject(id)
                val merged = if (!full && file.exists()) JSONObject(file.readText()) else JSONObject()
                val settings = merged.optJSONObject("prefs") ?: JSONObject()
                val incoming = snapshot.getJSONObject("prefs")
                for (k in incoming.keys()) {
                    if (included(k, selection) && !Profiles.isShared(k)) settings.put(k, incoming.getJSONObject(k))
                }
                settings.keys().asSequence().filterNot(::portable).toList().forEach { settings.remove(it) }
                merged.put("prefs", settings)
                if (selection == null || 5 in selection) snapshot.optJSONObject("files")?.let { merged.put("files", it) }
                writes[file] = merged.toString()
            }
        }
        // The live profile is already represented by the top-level preferences and progress.
        val previous = writes.keys.associateWith { if (it.exists()) it.readBytes() else null }
        val changed = mutableListOf<File>()
        try {
            writes.forEach { (file, contents) ->
                file.parentFile?.mkdirs()
                changed.add(file)
                file.writeText(contents)
            }
            if (!ed.commit()) {
                // SharedPreferences updates its in-memory map even when writing to disk fails.
                val rollback = store.edit()
                (p.keys().asSequence().toSet() + if (full) original.keys else emptySet())
                    .filter { included(it, selection) }.forEach { k ->
                        when (val value = original[k]) {
                            null -> rollback.remove(k)
                            is String -> rollback.putString(k, value)
                            is Boolean -> rollback.putBoolean(k, value)
                            is Int -> rollback.putInt(k, value)
                            is Long -> rollback.putLong(k, value)
                            is Float -> rollback.putFloat(k, value)
                            is Set<*> -> rollback.putStringSet(k, value.filterIsInstance<String>().toSet())
                            else -> error("Unsupported preference type")
                        }
                    }
                rollback.commit()
                throw java.io.IOException(tr("Fajl nije PRETORIAN backup"))
            }
        } catch (failure: Exception) {
            changed.asReversed().forEach { file ->
                runCatching { previous[file]?.let { file.writeBytes(it) } ?: file.delete() }
                    .onFailure { failure.addSuppressed(it) }
            }
            throw failure
        }
        Watch.reloadAfterRestore()
    }

    fun fileName() = "PRETORIAN-backup-" + SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date()) + ".json"

    /** weekly automatic backup inside the box (last 3 kept) */
    fun auto(ctx: Context) {
        if (!Prefs.bool("backup_auto", true)) return
        if (System.currentTimeMillis() - Prefs.long("backup_auto_at") < (if (Prefs.bool("backup_daily")) 1 else 7) * 86_400_000L) return
        try {
            val d = File(ctx.filesDir, "backups").apply { mkdirs() }
            File(d, fileName()).writeText(export(ctx, null))
            d.listFiles()?.sortedByDescending { it.lastModified() }?.drop(3)?.forEach { it.delete() }
            Prefs.put("backup_auto_at", System.currentTimeMillis())
        } catch (_: Exception) {}
    }

    fun autoList(ctx: Context): List<File> = File(ctx.filesDir, "backups").listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()

    /** USB sticks that are plugged in now (their root folder) */
    fun usbDirs(): List<File> {
        val out = ArrayList<File>()
        File("/storage").listFiles()?.filter { it.isDirectory && it.name != "emulated" && it.name != "self" && it.canRead() }?.let { out += it }
        return out
    }

    /** one backup found somewhere: where, when, how big, and how to read it */
    class Found(val where: String, val at: Long, val size: Long, val read: () -> String)

    /** every backup we can see: Download/PRETORIAN, USB sticks (PRETORIAN folder), automatic ones; newest first */
    fun found(ctx: Context): List<Found> {
        val out = ArrayList<Found>()
        val seen = HashSet<String>()
        // Download/PRETORIAN: the files this app wrote are always readable through the media store
        if (Build.VERSION.SDK_INT >= 29) try {
            val uri = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
            ctx.contentResolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns._ID, android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                android.provider.MediaStore.MediaColumns.DATE_MODIFIED, android.provider.MediaStore.MediaColumns.SIZE),
                android.provider.MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ?", arrayOf("PRETORIAN-backup%"), null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0); val name = c.getString(1) ?: continue
                    if (!seen.add("dl:$name")) continue
                    val u = android.content.ContentUris.withAppendedId(uri, id)
                    out += Found("Download/PRETORIAN", c.getLong(2) * 1000, c.getLong(3)) { ctx.contentResolver.openInputStream(u)!!.use { String(it.readBytes()) } }
                }
            }
        } catch (_: Exception) {}
        @Suppress("DEPRECATION")
        val dl = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "PRETORIAN")
        dl.listFiles()?.filter { it.name.startsWith("PRETORIAN-backup") && seen.add("dl:" + it.name) }?.forEach { f -> out += Found("Download/PRETORIAN", f.lastModified(), f.length()) { f.readText() } }
        usbDirs().forEach { d ->
            listOf(d, File(d, "PRETORIAN")).forEach { dir ->
                dir.listFiles()?.filter { it.name.startsWith("PRETORIAN-backup") && it.name.endsWith(".json") }?.forEach { f -> out += Found("USB", f.lastModified(), f.length()) { f.readText() } }
            }
        }
        autoList(ctx).forEach { f -> out += Found("Automatski", f.lastModified(), f.length()) { f.readText() } }
        return out.sortedByDescending { it.at }
    }

    /** saves straight to the chosen place; returns where it went */
    fun saveNow(ctx: Context, usb: Boolean, parts: Set<Int>): String {
        val name = fileName(); val text = export(ctx, pass.ifBlank { null }, parts)
        if (usb) {
            val d = usbDirs().firstOrNull() ?: throw java.io.IOException(tr("USB nije priključen"))
            val dir = File(d, "PRETORIAN").apply { mkdirs() }
            File(dir, name).writeText(text)
            return "USB/PRETORIAN"
        }
        PublicFiles.save(ctx, name, text.toByteArray())
        return "Download/PRETORIAN"
    }
}

/** Optional: wakes the box for a reminder (works only where Android allows it). */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        try {
            ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        } catch (_: Exception) {}
    }

    companion object {
        fun schedule(ctx: Context) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = PendingIntent.getBroadcast(ctx, 7, Intent(ctx, ReminderReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))
            am.cancel(pi)
            if (!Reminders.wake) return
            val now = System.currentTimeMillis()
            val next = Reminders.all().filter { !it.fired }.flatMap { r -> Reminders.offsets().map { r.start - it * 60_000L } }.filter { it > now }.minOrNull() ?: return
            if (next < System.currentTimeMillis()) return
            try { if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi) else am.set(AlarmManager.RTC_WAKEUP, next, pi) } catch (_: Exception) {}
        }
    }
}
