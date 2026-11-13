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
    private val DEVICE_ONLY = setOf("ext_player", "file_app", "profile_skip_ask")

    private fun payload(ctx: Context): JSONObject {
        val p = JSONObject()
        prefs(ctx).all.forEach { (k, v) ->
            if (k.startsWith("gd_") || k.startsWith("sync_") || k in DEVICE_ONLY) return@forEach
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
        listOf("progress.json").forEach { n -> File(ctx.filesDir, n).takeIf { it.exists() }?.let { files.put(n, it.readText()) } }
        return JSONObject().put("prefs", p).put("files", files).put("profiles", Profiles.allSnapshots(ctx))
    }

    private fun key(password: String, salt: ByteArray): SecretKeySpec {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val k = f.generateSecret(PBEKeySpec(password.toCharArray(), salt, 20_000, 256)).encoded
        return SecretKeySpec(k, "AES")
    }

    fun export(ctx: Context, password: String?): String {
        val data = payload(ctx)
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
        val keepHere = prefs(ctx).all.filterKeys { it.startsWith("gd_") || it.startsWith("sync_") || it in DEVICE_ONLY }
        val ed = prefs(ctx).edit().clear()
        keepHere.forEach { (k, v) -> when (v) { is String -> ed.putString(k, v); is Boolean -> ed.putBoolean(k, v); is Int -> ed.putInt(k, v); is Long -> ed.putLong(k, v); is Float -> ed.putFloat(k, v) } }
        p.keys().forEach { k ->
            if (k.startsWith("gd_") || k.startsWith("sync_")) return@forEach
            val v = p.getJSONObject(k)
            when (v.str("t")) {
                "s" -> ed.putString(k, v.str("v")); "b" -> ed.putBoolean(k, v.optBoolean("v"))
                "i" -> ed.putInt(k, v.optInt("v")); "l" -> ed.putLong(k, v.optLong("v")); "f" -> ed.putFloat(k, v.optDouble("v").toFloat())
            }
        }
        ed.commit()
        data.optJSONObject("files")?.let { f -> f.keys().forEach { n -> if (n.matches(Regex("[a-z_]+\\.json"))) File(ctx.filesDir, n).writeText(f.str(n)) } }
        data.optJSONObject("profiles")?.let { Profiles.restoreSnapshots(ctx, it) }
    }

    fun fileName() = "PRETORIAN-backup-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".json"

    /** weekly automatic backup inside the box (last 3 kept) */
    fun auto(ctx: Context) {
        if (!Prefs.bool("backup_auto", true)) return
        if (System.currentTimeMillis() - Prefs.long("backup_auto_at") < 7 * 86_400_000L) return
        try {
            val d = File(ctx.filesDir, "backups").apply { mkdirs() }
            File(d, fileName()).writeText(export(ctx, null))
            d.listFiles()?.sortedByDescending { it.lastModified() }?.drop(3)?.forEach { it.delete() }
            Prefs.put("backup_auto_at", System.currentTimeMillis())
        } catch (_: Exception) {}
    }

    fun autoList(ctx: Context): List<File> = File(ctx.filesDir, "backups").listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
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
            val next = Reminders.all().filter { !it.fired }.minOfOrNull { it.start - Reminders.before * 60_000L } ?: return
            if (next < System.currentTimeMillis()) return
            try { if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi) else am.set(AlarmManager.RTC_WAKEUP, next, pi) } catch (_: Exception) {}
        }
    }
}
