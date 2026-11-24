package com.pretorian.tv

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What the player is doing, for the screensaver: it may never start during playback, loading or recovery. */
object PlayState {
    @Volatile var inPlayer = false
    @Volatile var active = false
    /** paused film or episode (the only playback state where the screensaver may start) */
    @Volatile var pausedVod = false
    fun update(live: Boolean, playWhenReady: Boolean, state: Int) {
        val idle = state == androidx.media3.common.Player.STATE_IDLE || state == androidx.media3.common.Player.STATE_ENDED
        active = playWhenReady && !idle
        pausedVod = !live && !playWhenReady && state == androidx.media3.common.Player.STATE_READY
    }
}

/**
 * Phase H – the in-app screensaver (no DreamService, no second player, no polling of the network, no wakelock).
 * Starts after the chosen idle time on menus and over a paused film or episode; never while anything plays, loads or
 * recovers (live, VOD, preview, sport, catch-up), and not when the state is unclear. Any key closes it and returns to the
 * same screen and focus; OK on a "Nastavi gledati" card resumes it. 2.8.0 also shows the last measured internet / VPN
 * speeds (from Diagnostics, with their time) — the screensaver itself never runs a speed test.
 */
object Saver {
    var lastInput by mutableLongStateOf(android.os.SystemClock.uptimeMillis())
    var showing by mutableStateOf(false)
    fun touch() { lastInput = android.os.SystemClock.uptimeMillis() }
    /** H1: 0 off / 5 / 10 / 15 / 30 min, default 10 */
    val minutes get() = Prefs.long("saver_min", 10).toInt()
    val MIN_OPTIONS = listOf(0, 5, 10, 15, 30)
    /** 0 Informacije, 1 Nastavak gledanja, 2 Oboje */
    val mode get() = Prefs.long("saver_mode", 0).toInt().coerceIn(0, 2)
    fun show(key: String) = Prefs.bool("saver_$key", true)
    /** may it start now? uncertain = no */
    fun allowed(): Boolean = try {
        minutes > 0 && !ChannelHealth.previewOn && (!PlayState.inPlayer || (PlayState.pausedVod && !PlayState.active))
    } catch (_: Throwable) { false }
    /** Android's own sleep timeout (ms) when it can be read; never changed */
    fun systemTimeoutMs(ctx: Context): Long? = try {
        android.provider.Settings.System.getLong(ctx.contentResolver, android.provider.Settings.System.SCREEN_OFF_TIMEOUT)
    } catch (_: Throwable) { null }
}

/** Placed once at the root: watches the idle time and shows the screensaver. A failure closes it, never the app. */
@Composable
fun SaverHost(nav: Nav) {
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            val idle = android.os.SystemClock.uptimeMillis() - Saver.lastInput
            if (!Saver.showing && Saver.minutes > 0 && idle >= Saver.minutes * 60_000L && Saver.allowed()) {
                Saver.showing = true; DiagEvents.add("screen", "screensaver on")  // i18n-ignore
            }
            // something started playing underneath: close at once
            if (Saver.showing && !Saver.allowed()) Saver.showing = false
        }
    }
    if (Saver.showing) {
        var failed by remember { mutableStateOf(false) }
        if (failed) { LaunchedEffect(Unit) { Saver.showing = false }; return }
        Dialog(onDismissRequest = { Saver.showing = false; Saver.touch() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            try { SaverScreen(nav) } catch (e: Throwable) { DiagEvents.add("screen", "screensaver failed: " + e.javaClass.simpleName); failed = true }  // i18n-ignore
        }
    }
}

@Composable
private fun SaverScreen(nav: Nav) {
    val ctx = LocalContext.current
    fun close() { Saver.showing = false; Saver.touch() }
    BackHandler { close() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(20_000); now = System.currentTimeMillis() } }
    LaunchedEffect(Unit) { try { Weather.refresh() } catch (_: Throwable) {} }
    val cont = remember { if (Saver.mode == 0) emptyList() else Watch.continueList(ctx).filter { !ContinueHidden.hidden(it.url, it.ts) }.distinctBy { it.series ?: it.url }.take(3) }
    val rootFr = remember { FocusRequester() }
    val firstFr = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); if (cont.isNotEmpty()) firstFr.tryFocus() else rootFr.tryFocus() }
    Box(Modifier.fillMaxSize().background(Color(0xFA0E1114)).focusRequester(rootFr).focusable()
        .onKeyEvent { e -> if (e.type == KeyEventType.KeyUp) { close(); true } else true }) {
        Column(Modifier.align(Alignment.TopStart).padding(start = 64.dp, top = 52.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (Saver.show("clock")) {
                val is24 = android.text.format.DateFormat.is24HourFormat(ctx)
                T(SimpleDateFormat(if (is24) "HH:mm" else "h:mm a", Locale.getDefault()).format(Date(now)), 96.sp, Color.White, FontWeight.Light)  // i18n-ignore
                T(SimpleDateFormat("EEEE, d. MMMM", Locale(Look.lang)).format(Date(now)), 24.sp, C.Soft)  // i18n-ignore
            }
            if (Saver.show("weather") && Weather.place.isNotBlank()) Weather.temp?.let { t ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 10.dp)) {
                    Ico(Weather.icon(), 26.dp, C.Text); T("$t° · " + tr(Weather.condition()) + " · " + Weather.place, 20.sp, C.Text)
                }
            }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 64.dp, bottom = 52.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (Saver.show("net")) {
                val vpn = remember { NetInfo.vpn(ctx) }
                val online = remember { onlineNow(ctx) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Ico(if (online == true) R.drawable.lc_wifi else R.drawable.lc_x, 20.dp, C.Soft)
                    T(when (online) { true -> tr("Povezano"); false -> tr("Nije povezano"); null -> tr("Nije dostupno") } + if (vpn) " · VPN" else "", 16.sp, C.Soft)  // i18n-ignore
                }
                // the last real measurements (Settings › Diagnostics), with their time; nothing is measured here
                val norm = Prefs.str("speed_norm"); val vs = Prefs.str("speed_vpn")
                if (norm.isNotBlank()) SpeedLine(R.drawable.lc_globe, tr("Internet"), norm, Prefs.str("speed_norm_at"))
                if (vs.isNotBlank()) SpeedLine(R.drawable.lc_shield_check, tr("VPN"), vs, Prefs.str("speed_vpn_at"))  // i18n-ignore
                if (norm.isBlank() && vs.isBlank()) T("Brzina još nije izmjerena (Postavke › Dijagnostika)", 13.sp, C.Muted)
            }
            if (Saver.show("sys")) {
                val h = remember { DeviceInfo.head(ctx) }
                T(h.first + " · " + h.second, 13.sp, C.Muted)
            }
        }
        if (cont.isNotEmpty()) Column(Modifier.align(Alignment.CenterEnd).padding(end = 64.dp).width(420.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            T("Nastavi gledati", 15.sp, C.Soft, FontWeight.SemiBold)
            cont.forEachIndexed { i, pr ->
                Focusable(onClick = { close(); openHomeCard(ctx, nav, pr, null) }, shape = Rnd(14.dp), normal = C.Card, focused = Silver.Focus, zoom = 1f,
                    focusRequester = if (i == 0) firstFr else null, modifier = Modifier.fillMaxWidth().height(64.dp)
                        .onKeyEvent { e -> if (e.type == KeyEventType.KeyUp && e.key != androidx.compose.ui.input.key.Key.DirectionUp &&
                            e.key != androidx.compose.ui.input.key.Key.DirectionDown && e.key != androidx.compose.ui.input.key.Key.DirectionCenter &&
                            e.key != androidx.compose.ui.input.key.Key.Enter) { close(); true } else false }) { _ ->
                    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.Center) {
                        T(pr.series ?: pr.title, 16.sp, C.Text, FontWeight.SemiBold)
                        if (pr.dur > 0) ThinBar((pr.pos.toFloat() / pr.dur).coerceIn(0f, 1f), Color.White, Modifier.fillMaxWidth().padding(top = 6.dp), 3.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedLine(icon: Int, label: String, value: String, at: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Ico(icon, 18.dp, C.Soft)
        T(label + " · " + value + (if (at.isNotBlank()) " · " + trf("izmjereno %s", at) else ""), 14.sp, C.Soft)
    }
}

private fun onlineNow(ctx: Context): Boolean? = try {
    val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    val c = cm.getNetworkCapabilities(cm.activeNetwork)
    c != null && c.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
} catch (_: Throwable) { null }

/** Settings › Izgled i jezik › Čuvar ekrana (H2: 8 rows). */
@Composable
fun SaverSettings() {
    val ctx = LocalContext.current
    var rev by remember { mutableIntStateOf(0) }
    var preview by remember { mutableStateOf(false) }
    @Suppress("UNUSED_VARIABLE") val r = rev
    val sys = remember { Saver.systemTimeoutMs(ctx) }
    SetPage("Čuvar ekrana", "Samo na menijima i preko pauziranog filma ili epizode, nikad dok nešto svira") {
        SetSwitch("Čuvar ekrana", null, Saver.minutes > 0) { Prefs.put("saver_min", if (it) 10L else 0L); rev++ }
        SetDrop("Pokreni nakon", if (sys != null && Saver.minutes > 0 && sys < Saver.minutes * 60_000L) trf("Android se gasi nakon %s min i može ga preduhitriti", sys / 60_000) else null,
            Saver.MIN_OPTIONS.map { if (it == 0) tr("Isključeno") else trf("%s min", it) }, Saver.MIN_OPTIONS.indexOf(Saver.minutes).coerceAtLeast(0)) { Prefs.put("saver_min", Saver.MIN_OPTIONS[it].toLong()); rev++ }
        SetDrop("Način", null, listOf(tr("Informacije"), tr("Nastavak gledanja"), tr("Oboje")), Saver.mode) { Prefs.put("saver_mode", it.toLong()); rev++ }
        SetSwitch("Sat i datum", null, Saver.show("clock")) { Prefs.put("saver_clock", it); rev++ }
        SetSwitch("Vrijeme", if (Weather.place.isBlank()) "Grad se bira u Izgled › Lokacija za vrijeme" else Weather.place, Saver.show("weather")) { Prefs.put("saver_weather", it); rev++ }
        SetSwitch("Mreža i brzina", "Povezano, VPN i zadnja izmjerena brzina", Saver.show("net")) { Prefs.put("saver_net", it); rev++ }
        SetSwitch("Podaci o uređaju", null, Saver.show("sys")) { Prefs.put("saver_sys", it); rev++ }
        SetRow("Pregled", "Prikaži čuvar ekrana sada") { preview = true }
    }
    if (preview) { LaunchedEffect(Unit) { preview = false; Saver.showing = true } }
}

/**
 * Phase G – Sistem i održavanje › Pohrana i resetovanje: one shared "clear temporary data" and the three resets,
 * each confirmed with the focus on Odustani.
 */
object Maintenance {
    /** rebuildable data only: caches, images, the TMDB/metadata caches; never playlists, logins, favourites, PIN, progress, settings */
    fun clearTemp(ctx: Context): Boolean {
        if (Import.busy()) return false
        try { ctx.cacheDir.listFiles()?.forEach { it.deleteRecursively() } } catch (_: Throwable) {}
        try { coil.Coil.imageLoader(ctx).memoryCache?.clear() } catch (_: Throwable) {}
        try { Repo.clear() } catch (_: Throwable) {}
        DiagEvents.add("app", "temporary data cleared")  // i18n-ignore
        return true
    }
    /** keys that are user data, not settings: they survive "Resetuj postavke" */
    private val KEEP = listOf("playlists", "pl_", "fav", "watch", "progress", "pin", "recent", "notes", "submem_", "epgoff_", "epg_off_v2", "diag_", "speed_", "wx_", "ch_", "first_run", "lang", "rem")
    fun resetSettings() {
        val keys = Prefs.allKeys().filter { k -> KEEP.none { k.startsWith(it) } }
        keys.forEach { Prefs.remove(it) }
        DiagEvents.add("app", "settings reset (${keys.size} values)")  // i18n-ignore
    }
    /** everything: Android removes all app data and closes the app (no half-reset state is possible) */
    fun wipeAll(ctx: Context) {
        DiagEvents.add("app", "all app data deleted by the user"); DiagStore.flushNow()  // i18n-ignore
        (ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager).clearApplicationUserData()
    }
}

@Composable
fun StorageResetPage() {
    val ctx = LocalContext.current
    var ask by remember { mutableStateOf<Int?>(null) }
    val cacheMb by produceState<Long?>(null) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ctx.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } / 1_048_576 } }
    SetPage("Pohrana i resetovanje", trf("Slobodno: %s MB", ctx.filesDir.usableSpace / 1_048_576)) {
        SetRow("Obriši privremene podatke", "Keš, slike i privremene datoteke · playliste, favoriti, PIN i postavke ostaju", value = cacheMb?.let { "$it MB" }) { ask = 1 }  // i18n-ignore
        SetRow("Resetuj postavke", "Vraća postavke na zadane · playliste, favoriti, PIN i historija ostaju") { ask = 0 }
        SetRow("Obriši sve podatke aplikacije", "Sve se briše i aplikacija kreće od početnog podešavanja") { ask = 2 }
    }
    when (ask) {
        1 -> SetConfirm(tr("Obrisati privremene podatke?"), tr("Keš i slike se brišu i ponovo učitavaju po potrebi."), tr("Obriši"), onCancel = { ask = null }) {
            ask = null
            android.widget.Toast.makeText(ctx, tr(if (Maintenance.clearTemp(ctx)) "Privremeni podaci obrisani" else "Pričekaj da se završi učitavanje playliste"), android.widget.Toast.LENGTH_SHORT).show()
        }
        0 -> SetConfirm(tr("Resetovati postavke?"), tr("Sve postavke se vraćaju na zadane. Playliste, prijave, favoriti, PIN i historija ostaju."), tr("Resetuj"), danger = true, onCancel = { ask = null }) {
            ask = null; Maintenance.resetSettings(); android.widget.Toast.makeText(ctx, tr("Postavke resetovane"), android.widget.Toast.LENGTH_SHORT).show()
        }
        2 -> SetConfirm(tr("Obrisati SVE podatke aplikacije?"), tr("Brišu se playliste, prijave, favoriti, PIN, historija, EPG i sve postavke. Aplikacija se zatvara i kreće od početka. Ovo se ne može vratiti."),
            tr("Obriši sve"), danger = true, onCancel = { ask = null }) { ask = null; Maintenance.wipeAll(ctx) }
    }
}

/** Phase F – Vrijeme bez ometanja: a daily quiet window for non-essential pop-up notices (sport); the bell still records them. */
object Quiet {
    val on get() = Prefs.bool("quiet_on", false)
    val from get() = Prefs.long("quiet_from", 22).toInt()
    val to get() = Prefs.long("quiet_to", 7).toInt()
    fun now(): Boolean {
        if (!on) return false
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return if (from <= to) h in from until to else h >= from || h < to
    }
}


/** Phase E: playback options with real effect (connection timeout, subtitle behaviour, reset of playback settings). */
object PlayOpts {
    val TIMEOUTS = listOf(0, 10, 20, 30)
    val timeout get() = Prefs.long("conn_timeout", 0).toInt()
    val connectMs get() = if (timeout == 0) 15_000 else timeout * 1000
    val readMs get() = if (timeout == 0) 30_000 else timeout * 2000
    /** 0 Uvijek, 1 Samo strani jezik, 2 Isključeno (stored through the existing auto_subs + sub_behav) */
    val subBehaviour get() = if (!Prefs.autoSubs) 2 else Prefs.long("sub_behav", 0).toInt().coerceIn(0, 1)
    fun setSubBehaviour(v: Int) { Prefs.autoSubs = v != 2; Prefs.put("sub_behav", if (v == 1) 1L else 0L) }
    /** only playback keys; playlists, EPG, favourites and history are not touched */
    private val KEYS = listOf("conn_timeout", "sub_behav", "auto_subs", "audio_passthrough", "buffer_mb", "recover_on", "recover_wait2", "sub_size", "sub_font", "sub_style", "skip_s", "tech_decoder", "afr_on", "ext_player")
    fun reset() { KEYS.forEach { Prefs.remove(it) }; DiagEvents.add("app", "playback settings reset") }  // i18n-ignore
}
