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
        "Predvečerje" to "Nova zadana tema: mekana boja onoga na čemu stojiš iza cijele aplikacije.",
        "Zadnje gledano" to "Zadnjih 10 kanala na dnu TV-a i u grupi Zadnje gledano.",
        "Plejer" to "OK otvara brzi meni s tajmerom za spavanje i formatom slike. Broj na daljinskom bira kanal, 0 vraća prethodni.",
        "Filmovi" to "Veliki naslov s opisom i slikom filma, dugmad Gledaj i Moja lista.",
        "Pouzdanije" to "Kanal se sam ponovo spaja kad veza pukne, a kad provider ne radi, odmah piše na početnoj.",
    )
    var show by mutableStateOf(false)
    /** after an update (not on a brand new install, there is nothing "new" yet) */
    fun check(c: Context) {
        if (Prefs.str("whatsnew_seen") == VERSION) return
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
    private suspend fun reachable(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 5000; c.readTimeout = 5000; c.requestMethod = "GET"; c.instanceFollowRedirects = true
            val code = c.responseCode; c.disconnect(); code in 100..599
        } catch (e: Exception) { false }
    }
    suspend fun run(ctx: Context) {
        val p = Store.active(ctx) ?: return
        val base = when {
            p.server.isNotBlank() -> p.server.trimEnd('/') + "/player_api.php"
            p.url.startsWith("http") -> p.url
            else -> return
        }
        if (reachable(base)) { down = null; return }
        // the provider is silent; only blame it when the internet itself works
        down = if (reachable("https://connectivitycheck.gstatic.com/generate_204")) p.name else null
    }
}

@Composable
fun ProviderBanner() {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { while (true) { ProviderCheck.run(ctx); delay(if (ProviderCheck.down != null) 30_000 else 10 * 60_000L) } }
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

/** Screen saver of the player: a long pause dims the picture and shows the clock. */
@Composable
fun ScreenSaver(title: String) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(10_000) } }
    Box(Modifier.fillMaxSize().background(Color(0xF2000000)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            T(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), 110.sp, Color.White, FontWeight.ExtraLight)  // i18n-ignore
            T(SimpleDateFormat("EEEE, d. MMMM", Locale(Look.lang)).format(now).uppercase(), 15.sp, Color(0xFFC8CBD4))  // i18n-ignore
            Spacer(Modifier.height(24.dp))
            T(trf("⏸  %s · pauzirano", title), 14.sp, Color(0xFF9AA0AA))
            T("bilo koja tipka – nastavi", 12.sp, Color(0xFF6B7080))
        }
    }
}
