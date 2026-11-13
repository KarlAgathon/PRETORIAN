package com.pretorian.tv

import android.content.Context
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Google Drive sync. Sign in with a code on the phone (google.com/device), everything is kept in the app's own hidden
 * folder on your Drive (only PRETORIAN can see it). A new device signed in with the same account takes it all over.
 * Needs a Google OAuth client of the type "TVs and Limited Input devices" (built in from GitHub secrets, or typed in Settings).
 */
object Drive {
    private const val SCOPE = "https://www.googleapis.com/auth/drive.appdata email"
    private const val FILE = "pretorian_sync.json"

    var loginOpen by mutableStateOf(false)
    var restoreAsk by mutableStateOf(false)
    var newerRemote by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)

    val clientId: String get() = Prefs.str("gd_client_id").ifBlank { BuildConfig.GOOGLE_CLIENT_ID }
    val clientSecret: String get() = Prefs.str("gd_client_secret").ifBlank { BuildConfig.GOOGLE_CLIENT_SECRET }
    val configured: Boolean get() = clientId.isNotBlank()
    val signedIn: Boolean get() = Prefs.str("gd_refresh").isNotBlank()
    val email: String get() = Prefs.str("gd_email")

    fun statusLine(): String = when {
        !signedIn -> tr("Nije povezano s Google Driveom")
        Prefs.long("gd_last_up") > 0 -> trf("Google Drive · %s · sinhronizovano %s", email, fmt(Prefs.long("gd_last_up")))
        else -> trf("Google Drive · %s", email)
    }
    private fun fmt(ms: Long) = SimpleDateFormat("d. M. HH:mm", Locale.getDefault()).format(Date(ms))

    /* ---------------------------------------------------------------- http */

    private fun call(url: String, method: String = "GET", body: String? = null, type: String = "application/x-www-form-urlencoded",
                     token: String? = null, extra: Map<String, String> = emptyMap()): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 30000
        c.requestMethod = if (method == "PATCH") "POST" else method
        if (method == "PATCH") c.setRequestProperty("X-HTTP-Method-Override", "PATCH")
        token?.let { c.setRequestProperty("Authorization", "Bearer $it") }
        extra.forEach { (k, v) -> c.setRequestProperty(k, v) }
        if (body != null) {
            c.doOutput = true; c.setRequestProperty("Content-Type", type)
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = c.responseCode
        val text = try { (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: "" } catch (e: Exception) { "" }
        c.disconnect()
        return code to text
    }
    private fun form(vararg kv: Pair<String, String>) = kv.joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8") }

    /* ---------------------------------------------------------------- sign in (device code) */

    data class Code(val device: String, val user: String, val url: String, val interval: Int, val expiresAt: Long)

    suspend fun startLogin(): Code = withContext(Dispatchers.IO) {
        val (code, text) = call("https://oauth2.googleapis.com/device/code", "POST", form("client_id" to clientId, "scope" to SCOPE))
        if (code != 200) throw java.io.IOException(trf("Google je odbio prijavu (%s)", code))
        val o = JSONObject(text)
        Code(o.str("device_code"), o.str("user_code"), o.str("verification_url").ifBlank { "https://www.google.com/device" },
            o.optInt("interval", 5), System.currentTimeMillis() + o.optLong("expires_in", 1800) * 1000)
    }

    /** waits until you confirm on the phone; true when signed in */
    suspend fun waitLogin(c: Code): Boolean = withContext(Dispatchers.IO) {
        var wait = c.interval * 1000L
        while (System.currentTimeMillis() < c.expiresAt) {
            delay(wait)
            val (code, text) = call("https://oauth2.googleapis.com/token", "POST", form("client_id" to clientId, "client_secret" to clientSecret,
                "device_code" to c.device, "grant_type" to "urn:ietf:params:oauth:grant-type:device_code"))
            val o = try { JSONObject(text) } catch (e: Exception) { JSONObject() }
            if (code == 200) {
                Prefs.put("gd_refresh", o.str("refresh_token")); Prefs.put("gd_access", o.str("access_token"))
                Prefs.put("gd_exp", System.currentTimeMillis() + o.optLong("expires_in", 3600) * 1000 - 60_000)
                Prefs.put("gd_email", emailOf(o.str("id_token")))
                return@withContext true
            }
            when (o.str("error")) {
                "authorization_pending" -> {}
                "slow_down" -> wait += 2000
                else -> throw java.io.IOException(o.str("error_description").ifBlank { o.str("error") })
            }
        }
        false
    }

    private fun emailOf(idToken: String): String = try {
        val part = idToken.split(".")[1]
        JSONObject(String(android.util.Base64.decode(part, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP))).str("email")
    } catch (e: Exception) { "" }

    fun logout() = Prefs.remove("gd_refresh", "gd_access", "gd_exp", "gd_email", "gd_last_up", "gd_remote_time", "sync_hash")

    private fun token(): String {
        if (Prefs.long("gd_exp") > System.currentTimeMillis() && Prefs.str("gd_access").isNotBlank()) return Prefs.str("gd_access")
        val (code, text) = call("https://oauth2.googleapis.com/token", "POST", form("client_id" to clientId, "client_secret" to clientSecret,
            "refresh_token" to Prefs.str("gd_refresh"), "grant_type" to "refresh_token"))
        if (code != 200) {
            if (code == 400 || code == 401) logout()
            throw java.io.IOException(tr("Google prijava je istekla, prijavi se ponovo"))
        }
        val o = JSONObject(text)
        Prefs.put("gd_access", o.str("access_token")); Prefs.put("gd_exp", System.currentTimeMillis() + o.optLong("expires_in", 3600) * 1000 - 60_000)
        return o.str("access_token")
    }

    /* ---------------------------------------------------------------- the file on Drive */

    private data class Remote(val id: String, val time: Long)

    private fun remote(t: String): Remote? {
        val q = URLEncoder.encode("name='$FILE'", "UTF-8")
        val (code, text) = call("https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=$q&fields=files(id,modifiedTime)", token = t)  // i18n-ignore
        if (code != 200) throw java.io.IOException(trf("Google Drive greška %s", code))
        val f = JSONObject(text).optJSONArray("files")?.optJSONObject(0) ?: return null
        val ms = try { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(f.str("modifiedTime"))?.time ?: 0 } catch (e: Exception) { 0L }
        return Remote(f.str("id"), ms)
    }

    private fun device(ctx: Context) = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "box"

    /** what is sent: every setting, every profile (without sign-in tokens) */
    fun payload(ctx: Context): String = JSONObject(Backup.export(ctx, null)).put("device", device(ctx)).put("device_name", android.os.Build.MODEL).toString()

    fun hash(ctx: Context): String {
        val o = JSONObject(Backup.export(ctx, null)).optJSONObject("data") ?: return ""
        o.optJSONObject("prefs")?.let { p -> listOf("upd_last", "backup_auto_at", "notes", "new_items", "follow_at", "profile_skip_ask").forEach { p.remove(it) } }
        return MessageDigest.getInstance("SHA-1").digest(o.toString().toByteArray()).joinToString("") { "%02x".format(it) }
    }

    suspend fun upload(ctx: Context) = withContext(Dispatchers.IO) {
        if (!signedIn) return@withContext
        busy = true
        try {
            val t = token()
            val body = payload(ctx)
            val r = remote(t)
            if (r == null) {
                val b = "pretorian${System.currentTimeMillis()}"
                val multi = "--$b\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" +
                    JSONObject().put("name", FILE).put("parents", org.json.JSONArray().put("appDataFolder")) +
                    "\r\n--$b\r\nContent-Type: application/json\r\n\r\n$body\r\n--$b--"
                val (code, _) = call("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart", "POST", multi, "multipart/related; boundary=$b", t)
                if (code !in 200..299) throw java.io.IOException(trf("Google Drive greška %s", code))
            } else {
                val (code, _) = call("https://www.googleapis.com/upload/drive/v3/files/${r.id}?uploadType=media", "PATCH", body, "application/json", t)  // i18n-ignore
                if (code !in 200..299) throw java.io.IOException(trf("Google Drive greška %s", code))
            }
            Prefs.put("gd_last_up", System.currentTimeMillis()); Prefs.put("sync_hash", hash(ctx))
            remote(t)?.let { Prefs.put("gd_remote_time", it.time) }
        } finally { busy = false }
    }

    /** null when there is nothing on Drive yet */
    suspend fun download(): String? = withContext(Dispatchers.IO) {
        val t = token()
        val r = remote(t) ?: return@withContext null
        val (code, text) = call("https://www.googleapis.com/drive/v3/files/${r.id}?alt=media", token = t)  // i18n-ignore
        if (code != 200) throw java.io.IOException(trf("Google Drive greška %s", code))
        Prefs.put("gd_remote_time", r.time)
        text
    }

    /** takes over everything from Drive, keeps this device's sign-in, then restarts */
    suspend fun restore(ctx: Context): Boolean {
        val text = download() ?: return false
        withContext(Dispatchers.IO) { Backup.restore(ctx, text, null) }
        Prefs.put("sync_hash", hash(ctx)); Prefs.put("profile_skip_ask", false)
        return true
    }

    /** at start: is there something newer on Drive from another device? */
    suspend fun checkRemote(ctx: Context) = withContext(Dispatchers.IO) {
        if (!signedIn) return@withContext
        try {
            val r = remote(token()) ?: return@withContext
            if (r.time > Prefs.long("gd_remote_time") + 5000) {
                val text = download() ?: return@withContext
                val o = JSONObject(text)
                if (o.str("device") != device(ctx)) newerRemote = o.str("device_name").ifBlank { tr("drugi uređaj") } + " · " + o.str("date")
            }
        } catch (_: Exception) {}
    }

    /** every few minutes: sends your changes when something changed */
    suspend fun autoSync(ctx: Context) {
        if (!signedIn || busy) return
        try { if (hash(ctx) != Prefs.str("sync_hash")) upload(ctx) } catch (_: Exception) {}
    }
}

/** Sign in: a code on the TV, confirmed on the phone. */
@Composable
fun DriveLogin(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var code by remember { mutableStateOf<Drive.Code?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var step by remember { mutableStateOf("code") }
    var setup by remember { mutableStateOf(!Drive.configured) }
    LaunchedEffect(setup) {
        if (setup) return@LaunchedEffect
        try {
            val c = Drive.startLogin(); code = c
            if (Drive.waitLogin(c)) {
                step = "sync"
                val had = try { Drive.restore(ctx) } catch (e: Exception) { false }
                if (had) { Toast.makeText(ctx, tr("Postavke s Google Drivea su preuzete"), Toast.LENGTH_LONG).show(); Profiles.restart(ctx) }
                else { Drive.upload(ctx); Toast.makeText(ctx, tr("Povezano. Postavke su poslane na Google Drive."), Toast.LENGTH_LONG).show(); onDone() }
            } else error = tr("Kod je istekao. Pokušaj ponovo.")
        } catch (e: Exception) { error = e.message ?: tr("Greška") }
    }
    if (setup) { DriveSetup(onDismiss = onDone, onSaved = { setup = false }); return }
    Popup(tr("Prijava Google nalogom"), onDone, width = 640.dp) {
        val c = code
        when {
            error != null -> { T(error!!, 15.sp, C.Danger, maxLines = 4); OptionRow("Zatvori", R.drawable.lc_x) { onDone() } }
            step == "sync" -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(color = C.Red, strokeWidth = 3.dp, modifier = Modifier.size(28.dp)); T("Povezujem i sinhronizujem…", 16.sp, C.Text)
            }
            c == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(color = C.Red, strokeWidth = 3.dp, modifier = Modifier.size(28.dp)); T("Pripremam kod…", 16.sp, C.Text)
            }
            else -> {
                T("1. Na telefonu ili laptopu otvori:", 15.sp, C.Soft)
                T(c.url.removePrefix("https://"), 26.sp, C.Text, FontWeight.Bold)
                T("2. Upiši ovaj kod:", 15.sp, C.Soft)
                Box(Modifier.fillMaxWidth().clip(Rnd(16.dp)).background(C.CardFocus).padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    T(c.user, 44.sp, C.Text, FontWeight.Bold, align = TextAlign.Center)
                }
                T("3. Izaberi svoj Google nalog i dozvoli. Ovaj ekran se sam zatvori.", 15.sp, C.Soft, maxLines = 2)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(color = C.Red, strokeWidth = 2.dp, modifier = Modifier.size(16.dp)); T("Čekam potvrdu…", 13.sp, C.Muted)
                }
                OptionRow("Odustani", R.drawable.lc_x) { onDone() }
            }
        }
    }
}

/** When no Google client is built in: where to type it (once). */
@Composable
fun DriveSetup(onDismiss: () -> Unit, onSaved: () -> Unit) {
    var id by remember { mutableStateOf(Prefs.str("gd_client_id")) }
    var secret by remember { mutableStateOf(Prefs.str("gd_client_secret")) }
    Popup(tr("Google Drive: jednokratno podešavanje"), onDismiss, width = 760.dp) {
        T("Google traži da aplikacija ima svoj \"OAuth klijent\". Napravi ga jednom u Google Cloud konzoli (vrsta: TV i uređaji s ograničenim unosom) i upiši ga ovdje. Uputstvo je na PRETORIAN GitHub stranici.", 13.sp, C.Soft, maxLines = 5)
        Field("Client ID", id, { id = it.trim() })
        Field("Client secret", secret, { secret = it.trim() }, password = true)
        Button("Spremi i nastavi", R.drawable.lc_check, primary = true) {
            Prefs.put("gd_client_id", id); Prefs.put("gd_client_secret", secret); if (id.isNotBlank()) onSaved()
        }
    }
}

/** Popups about Drive that can appear anywhere: sign in, restore, newer settings from another device. */
@Composable
fun DriveDialogs() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    if (Drive.loginOpen) DriveLogin { Drive.loginOpen = false; Profiles.rev++ }
    if (Drive.restoreAsk) Popup(tr("Vratiti sve s Google Drivea?"), { Drive.restoreAsk = false }) {
        T("Postavke, playliste i svi profili na ovom uređaju se zamjenjuju onima s Google Drivea.", 14.sp, C.Soft, maxLines = 3)
        OptionRow(if (working) "Preuzimam…" else "Da, preuzmi", R.drawable.lc_cloud_download, color = C.Green) {
            if (working) return@OptionRow
            working = true
            scope.launchSafe {
                val ok = Drive.restore(ctx); working = false; Drive.restoreAsk = false
                if (ok) Profiles.restart(ctx) else Toast.makeText(ctx, tr("Na Google Driveu još nema ništa"), Toast.LENGTH_LONG).show()
            }
        }
        OptionRow("Odustani", R.drawable.lc_x) { Drive.restoreAsk = false }
    }
    Drive.newerRemote?.let { from ->
        Popup(tr("Novije postavke na Google Driveu"), { Drive.newerRemote = null }) {
            T(trf("Promijenjene su na: %s. Preuzeti ih i na ovaj uređaj?", from), 14.sp, C.Soft, maxLines = 3)
            OptionRow(if (working) "Preuzimam…" else "Preuzmi", R.drawable.lc_cloud_download, color = C.Green) {
                if (working) return@OptionRow
                working = true
                scope.launchSafe { if (Drive.restore(ctx)) Profiles.restart(ctx); working = false; Drive.newerRemote = null }
            }
            OptionRow("Ne, zadrži ove", R.drawable.lc_x) { Drive.newerRemote = null; scope.launchSafe { Drive.upload(ctx) } }
        }
    }
}
