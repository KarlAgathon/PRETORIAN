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
    Popup("Aplikacija se ruši", onDismiss = { open = false; CrashGuard.clear(); CrashGuard.clearQuick() }, width = 600.dp) {
        T(if (quick) trf("Verzija %s se 5 puta srušila odmah pri pokretanju. Siguran start je već urađen. Vratiti zadnju Stable verziju?", BuildInfo.VERSION)
          else trf("Verzija %s se srušila više puta zaredom. Šta želiš?", BuildInfo.VERSION), 14.sp, C.Soft, maxLines = 4)
        OptionRow("Siguran start", R.drawable.lc_shield_check, sub = tr("briše keš i uključuje Lite"), color = C.Green) {
            try { ctx.cacheDir.deleteRecursively() } catch (_: Exception) {}
            Perf.set(1); Repo.clear(); CrashGuard.clear(); open = false
        }
        OptionRow("Vrati prethodnu stabilnu verziju", R.drawable.lc_rotate_ccw, sub = tr("backup i stara verzija u Download/MediaCenter")) {
            CrashGuard.clear(); open = false; SettingsNav.section = "update"; nav.root(Screen.Settings)
        }
        OptionRow("Pokušaj ponovo", R.drawable.lc_x) { CrashGuard.clear(); open = false }
    }
}
