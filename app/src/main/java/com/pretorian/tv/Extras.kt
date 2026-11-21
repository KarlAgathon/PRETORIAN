package com.pretorian.tv

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** "What's new": a few short points about this version, shown once after an update (and from About). */
object WhatsNew {
    val VERSION = BuildInfo.VERSION
    val POINTS = listOf(
        "O aplikaciji" to "Stranica se otvara normalno. „Šta je novo“ više ne iskače samo – ovdje ga otvaraš kad želiš.",
        "Dijagnostika" to "Kartice jedna ispod druge, ispravan protok podataka i novi Zapis ažuriranja sa svakim korakom.",
        "Nastavi gledati" to "Dugi OK: Nastavi, Od početka ili Ukloni s liste.",
        "Titlovi" to "Titl nudi samo titlove providera. „Traži titl“: online ili s diska / USB-a.",
        "Čuvar ekrana" to "OK odmah nastavlja gledanje; u menijima pokazuje zadnji naslov.",
        "Lakša aplikacija" to "Manje memorije. Lite mod se bira sam prema ocjeni uređaja (vidi O uređaju).",
    )
    var show by mutableStateOf(false)
    /** after an update (not on a brand new install, there is nothing "new" yet) */
    fun check(c: Context) {
        // never opens by itself any more: it is in About › What's new when you want it
        Prefs.put("whatsnew_seen", VERSION); return
    }
    @Suppress("unused") private fun checkOld(c: Context) {
        if (Prefs.str("whatsnew_seen") == VERSION) return
        if (!Prefs.bool("upd_whatsnew", true)) { Prefs.put("whatsnew_seen", VERSION); return }
        if (Prefs.str("whatsnew_seen").isNotEmpty() || Store.playlists(c).isNotEmpty()) show = true
        Prefs.put("whatsnew_seen", VERSION)
    }
}

@Composable
fun WhatsNewDialog() {
    if (!WhatsNew.show) return
    Popup("Šta je novo", onDismiss = { WhatsNew.show = false }, width = 640.dp) {
        T(trf("Verzija %s", WhatsNew.VERSION), 13.sp, C.Muted)
        WhatsNew.POINTS.forEach { (t, d) ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 6.dp).size(7.dp).clip(CircleShape).background(C.Text))
                Column { T(t, 15.sp, C.Text, FontWeight.Bold); T(d, 13.sp, C.Soft, maxLines = 3) }
            }
        }
        Button("U redu", R.drawable.lc_check, primary = true) { WhatsNew.show = false }
    }
}

/** Tips that appear only the first time. */
object Tips {
    fun seen(key: String) = Prefs.bool("tip_$key")
    fun done(key: String) = Prefs.put("tip_$key", true)
}

@Composable
fun TipOnce(key: String, title: String, text: String) {
    var open by remember { mutableStateOf(!Tips.seen(key)) }
    if (!open) return
    Popup(title, onDismiss = { open = false; Tips.done(key) }) {
        T(text, 15.sp, C.Soft, maxLines = 4)
        T("pokazuje se samo jednom", 12.sp, C.Muted)
        Button("Razumijem", R.drawable.lc_check, primary = true) { open = false; Tips.done(key) }
    }
}

/** Is the provider answering? Checked on the home screen only; null = not known yet. */
object ProviderCheck {
    var down by mutableStateOf<String?>(null)   // playlist name when it does not answer
    private var fails = 0
    /** the same way the app itself talks to the provider (incl. its DNS fallback) */
    private suspend fun answers(url: String): Boolean = withContext(Dispatchers.IO) {
        try { Http.request(url); true } catch (e: java.io.IOException) {
            // an HTTP error page is still an answer; only no connection at all counts
            e.message?.contains("HTTP", ignoreCase = true) == true
        } catch (e: Exception) { true }
    }
    private suspend fun internet(): Boolean = withContext(Dispatchers.IO) {
        try { val c = URL("https://connectivitycheck.gstatic.com/generate_204").openConnection() as HttpURLConnection
            c.connectTimeout = 5000; c.readTimeout = 5000; val ok = c.responseCode in 200..399; c.disconnect(); ok } catch (e: Exception) { false }
    }
    suspend fun run(ctx: Context) {
        val p = Store.active(ctx) ?: return
        val url = when {
            p.server.isNotBlank() -> p.server.trimEnd('/') + "/player_api.php?username=" + java.net.URLEncoder.encode(p.user, "UTF-8") +
                "&password=" + java.net.URLEncoder.encode(p.pass, "UTF-8")
            p.url.startsWith("http") -> p.url
            else -> return
        }
        if (answers(url)) { fails = 0; down = null; return }
        fails++
        // two misses in a row while the internet works: only then it is the provider
        down = if (fails >= 2 && internet()) p.name else null
    }
}

@Composable
fun ProviderBanner() {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        delay(8_000)   // let the home screen settle first
        while (true) { ProviderCheck.run(ctx); delay(if (ProviderCheck.down != null) 30_000 else 10 * 60_000L) }
    }
    val name = ProviderCheck.down ?: return
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(busy) { if (busy) { ProviderCheck.run(ctx); busy = false } }
    Row(Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 6.dp).clip(Rnd(16.dp)).background(C.Overlay)
        .border(1.dp, C.Amber.copy(alpha = 0.6f), Rnd(16.dp)).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(C.Amber.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) { T("!", 18.sp, C.Amber, FontWeight.ExtraBold) }
        Column(Modifier.weight(1f)) {
            T(trf("Provider %s trenutno ne odgovara", name), 15.sp, C.Text, FontWeight.Bold)
            T("Problem nije u tvom uređaju ni internetu. Pokušavam ponovo svakih 30 s.", 12.sp, C.Soft, maxLines = 2)
        }
        Button(if (busy) "Provjeravam…" else "Pokušaj sada", R.drawable.lc_rotate_cw) { busy = true }
    }
}

/** Expiry warning on the home screen: shown in the last 7 days of the subscription. */
@Composable
fun ExpiryChip() {
    val ctx = LocalContext.current
    val p = remember { Store.active(ctx) } ?: return
    val sec = p.exp.toLongOrNull() ?: return
    if (sec <= 0) return
    val days = (sec * 1000 - System.currentTimeMillis()) / 86_400_000L
    if (days > 7) return
    Row(Modifier.clip(Rnd(14.dp)).background(C.Danger.copy(alpha = 0.16f)).border(1.dp, C.Danger.copy(alpha = 0.5f), Rnd(14.dp))
        .padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(C.Danger))
        Column {
            T(if (days < 0) trf("Pretplata %s je istekla", p.name) else trf("Pretplata %s ističe za %s dana", p.name, days.coerceAtLeast(0)), 13.sp, C.Text, FontWeight.Bold)
            T(SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(Date(sec * 1000)) + " · " + tr("obnovi kod providera"), 11.sp, C.Soft)  // i18n-ignore
        }
    }
}



/** The new version asks once when PRETORIAN starts ("window at start"). */
@Composable
fun UpdateAskDialog(nav: Nav) {
    val u = Updater.available
    if (!UpdateAsk.show || u == null) return
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Popup(trf("Nova verzija %s", u.name), onDismiss = { UpdateAsk.show = false }, width = 560.dp) {
        u.lines.forEach { l -> T("• $l", 14.sp, C.Text, maxLines = 2) }
        OptionRow("Instaliraj sada", R.drawable.lc_download, color = C.Amber) { UpdateAsk.show = false; scope.launch { Updater.install(ctx, u) } }
        OptionRow("Detalji u Ažuriranju", R.drawable.lc_settings) { UpdateAsk.show = false; SettingsNav.section = "update"; nav.root(Screen.Settings) }
        OptionRow("Kasnije", R.drawable.lc_clock) {
            UpdateAsk.show = false; Updater.snooze(listOf(1, 24, 72)[Prefs.str("upd_later", "0").toIntOrNull()?.coerceIn(0, 2) ?: 0])
        }
        OptionRow("Preskoči ovu verziju", R.drawable.lc_x) { UpdateAsk.show = false; Updater.skip(u) }
    }
}

/** After 3 crashes in 10 minutes: a safe start, going back to an older version, or trying again. */
@Composable
fun CrashGuardDialog(nav: Nav) {
    val quick = remember { CrashGuard.quick() }
    var open by remember { mutableStateOf(CrashGuard.tooMany() || quick) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // 5 crashes right at start: a safe start happens by itself so this question can be shown at all
    LaunchedEffect(Unit) { if (quick) { try { ctx.cacheDir.deleteRecursively() } catch (_: Exception) {}; Perf.set(1); Repo.clear() } }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(30_000); CrashGuard.clearQuick(); kotlinx.coroutines.delay(90_000); CrashGuard.clear() }   // quiet minutes: all fine again
    if (!open) return
    Popup("PRETORIAN se ruši", onDismiss = { open = false; CrashGuard.clear(); CrashGuard.clearQuick() }, width = 600.dp) {
        T(if (quick) trf("Verzija %s se 5 puta srušila odmah pri pokretanju. Siguran start je već urađen. Vratiti zadnju Stable verziju?", BuildInfo.VERSION)
          else trf("Verzija %s se srušila više puta zaredom. Šta želiš?", BuildInfo.VERSION), 14.sp, C.Soft, maxLines = 4)
        OptionRow("Siguran start", R.drawable.lc_shield_check, sub = tr("briše keš i uključuje Lite"), color = C.Green) {
            try { ctx.cacheDir.deleteRecursively() } catch (_: Exception) {}
            Perf.set(1); Repo.clear(); CrashGuard.clear(); open = false
        }
        OptionRow("Vrati prethodnu stabilnu verziju", R.drawable.lc_rotate_ccw, sub = tr("backup i stara verzija u Download/PRETORIAN")) {
            CrashGuard.clear(); open = false; SettingsNav.section = "update"; nav.root(Screen.Settings)
        }
        OptionRow("Pokušaj ponovo", R.drawable.lc_x) { CrashGuard.clear(); open = false }
    }
}
