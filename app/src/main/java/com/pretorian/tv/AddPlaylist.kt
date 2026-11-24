package com.pretorian.tv

import androidx.activity.compose.BackHandler

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay


@Composable
fun AddPlaylistScreen(nav: Nav) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val old = remember { EditPlaylist.target }
    if (old == null && Store.playlists(ctx).isEmpty() && Welcome.tab < 0) { OnboardingScreen(nav); return }
    // first setup: Back from the form goes one step back to the source choice
    androidx.activity.compose.BackHandler(enabled = old == null && Store.playlists(ctx).isEmpty() && Welcome.tab >= 0) { Welcome.tab = -1 }
    var tab by remember { mutableIntStateOf(when { old?.type == SourceType.M3U -> 1; old?.type == SourceType.STALKER -> 2; old == null && Welcome.tab in 1..2 -> Welcome.tab; else -> 0 }) }
    var mac by remember { mutableStateOf(old?.mac ?: "") }   // Phase B: empty for a new playlist, an existing MAC is kept
    var serial by remember { mutableStateOf(old?.serial ?: "") }
    var devId by remember { mutableStateOf(old?.deviceId ?: "") }
    var devId2 by remember { mutableStateOf(old?.deviceId2 ?: "") }
    var name by remember { mutableStateOf(old?.name ?: "") }
    var server by remember { mutableStateOf(old?.server ?: "") }
    var user by remember { mutableStateOf(old?.user ?: "") }
    var pass by remember { mutableStateOf(old?.pass ?: "") }
    var url by remember { mutableStateOf(old?.url ?: "") }
    val oldEpg = remember { old?.let { o -> EpgSources.list().firstOrNull { it.pid == o.id } } }
    var epg by remember { mutableStateOf(oldEpg?.url ?: "") }
    var live by remember { mutableStateOf(old?.live ?: true) }
    var vod by remember { mutableStateOf(old?.vod ?: true) }
    var series by remember { mutableStateOf(old?.series ?: true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Phase B: a new playlist is switched on only when no other one is active; you may switch it on yourself
    var activate by remember { mutableStateOf(Store.actives(ctx).isEmpty()) }
    var more by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var askLeave by remember { mutableStateOf(false) }
    val first = remember { listOf(old?.name ?: "", old?.server ?: "", old?.user ?: "", old?.pass ?: "", old?.url ?: "", old?.mac ?: "") }
    val dirty = listOf(name, server, user, pass, url, mac) != first
    androidx.activity.compose.BackHandler(enabled = dirty && Store.playlists(ctx).isNotEmpty() && !askLeave) { askLeave = true }

    // type on the phone: a QR code opens a small form served by the box; what is sent fills these fields
    var phoneUrl by remember { mutableStateOf<String?>(null) }
    var phoneGot by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        phoneUrl = PhoneLink.start { d ->
            tab = d.type
            if (d.name.isNotBlank()) name = d.name
            if (d.server.isNotBlank()) server = d.server
            if (d.user.isNotBlank()) user = d.user
            if (d.pass.isNotBlank()) pass = d.pass
            if (d.url.isNotBlank()) url = d.url
            if (d.epg.isNotBlank()) epg = d.epg
            phoneGot = true
        }
        onDispose { PhoneLink.stop() }
    }
    val qr = remember(phoneUrl) { phoneUrl?.let { PhoneLink.qr(it) }?.asImageBitmap() }

    val saveFr = remember { FocusRequester() }
    // phone "Done": the fields are filled and the focus waits on "Save and load" (nothing is imported by itself)
    LaunchedEffect(phoneGot) { if (phoneGot) { delay(150); saveFr.tryFocus() } }
    Column(Modifier.fillMaxSize()) {
        Header(if (old != null) "Uredi playlistu" else "Dodaj playlistu")
        // two columns (approved): the form on the left, the phone QR always on the right
        Row(Modifier.fillMaxSize().padding(horizontal = 36.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Segmented(listOf("Xtream Codes", "M3U playlista", "Stalker portal"), tab) { tab = it }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Field("Naziv playliste", name, { name = it }, Modifier.weight(1f))
                when (tab) {
                    0 -> Field("Server adresa (http://server:port)", server, { server = it }, Modifier.weight(1f), KeyboardType.Uri)
                    2 -> Field("Portal (http://server:port/c/)", server, { server = it }, Modifier.weight(1f), KeyboardType.Uri)
                    else -> Field("M3U link", url, { url = it }, Modifier.weight(1f), KeyboardType.Uri)
                }
            }
            if (tab == 2) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Field("MAC adresa (00:1A:79:…)", mac, { mac = it }, Modifier.weight(1f))
                Field("Serijski broj (nije obavezno)", serial, { serial = it }, Modifier.weight(1f))
            }
            if (tab == 2) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Field("Device ID 1 (nije obavezno)", devId, { devId = it }, Modifier.weight(1f))
                Field("Device ID 2 (nije obavezno)", devId2, { devId2 = it }, Modifier.weight(1f))
            }
            if (tab == 0 || tab == 2) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Field(if (tab == 2) "Korisničko ime (nije obavezno)" else "Korisničko ime", user, { user = it }, Modifier.weight(1f))
                Field(if (tab == 2) "Lozinka (nije obavezno)" else "Lozinka", pass, { pass = it }, Modifier.weight(1f), password = true)
            }
            if (tab == 1) Button("Izaberi fajl na uređaju", R.drawable.lc_folder) { picking = true }
            if (old == null) ToggleRow("Aktiviraj playlistu", activate) { activate = it }
            // Xtream: what to load sits under "Više opcija" (D1)
            if (tab == 0) {
                Button(if (more) "Više opcija ▴" else "Više opcija ▾", R.drawable.lc_sliders_horizontal) { more = !more }
                if (more) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) { ToggleRow("TV kanali", live) { live = it } }
                    Box(Modifier.weight(1f)) { ToggleRow("Filmovi", vod) { vod = it } }
                    Box(Modifier.weight(1f)) { ToggleRow("Serije", series) { series = it } }
                }
            }
            T("Dugačke linkove i lozinke možeš kucati s telefona: Google TV aplikacija › Daljinski › ikona tastature.", 12.sp, C.Muted, maxLines = 2)
            error?.let { T(it, 14.sp, C.Danger, maxLines = 3) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(if (busy) "Provjeravam…" else "Sačuvaj", R.drawable.lc_check, primary = true, focusRequester = saveFr) {
                    if (busy) return@Button
                    val base = Playlist(
                        id = old?.id ?: System.currentTimeMillis().toString(),
                        name = name.trim().ifBlank { when (tab) { 0 -> "Xtream"; 2 -> "Stalker"; else -> "M3U" } },  // i18n-ignore
                        type = when (tab) { 0 -> SourceType.XTREAM; 2 -> SourceType.STALKER; else -> SourceType.M3U },
                        server = server.trim(), user = user.trim(), pass = pass.trim(), url = url.trim(), active = old?.active ?: activate,
                        live = live, vod = vod, series = series,
                        mac = if (tab == 2) mac.trim().uppercase() else "", serial = serial.trim(), deviceId = devId.trim(), deviceId2 = devId2.trim(),
                    )
                    if (tab == 0 && (base.server.isBlank() || base.user.isBlank() || base.pass.isBlank())) { error = "Unesi server, korisničko ime i lozinku"; return@Button }
                    if (tab == 1 && base.url.isBlank()) { error = "Unesi M3U link"; return@Button }
                    if (tab == 2 && (base.server.isBlank() || !Regex("([0-9A-F]{2}:){5}[0-9A-F]{2}").matches(base.mac))) { error = "Unesi portal i MAC adresu (00:1A:79:XX:XX:XX)"; return@Button }
                    busy = true; error = null
                    scope.launch {
                        try {
                            val info = Repo.test(base)
                            val pl = if (base.type != SourceType.M3U) base.copy(exp = info) else base
                            // EPG typed in the form: kept as this playlist's own guide (default for it), loaded at once
                            val e = epg.trim()
                            val epgSrc = when {
                                e.isBlank() -> null
                                oldEpg != null && oldEpg.url == e -> null
                                oldEpg != null -> oldEpg.copy(url = e).also { EpgSources.update(it) }
                                else -> EpgSources.add(e, pl.id, def = setOf(pl.id))
                            }
                            if (epgSrc != null) launch { try { EpgSources.refresh(epgSrc) } catch (_: Exception) {} }
                            if (old != null) {
                                Store.savePlaylists(ctx, Store.playlists(ctx).map { if (it.id == old.id) pl else it })
                                Toast.makeText(ctx, tr("Spremljeno"), Toast.LENGTH_SHORT).show()
                                val conn = listOf(old.server, old.user, old.pass, old.url, old.mac, old.type.name, old.live, old.vod, old.series) !=
                                    listOf(pl.server, pl.user, pl.pass, pl.url, pl.mac, pl.type.name, pl.live, pl.vod, pl.series)
                                PlCheck.put(pl.id, true, null)
                                EditPlaylist.target = null; Repo.clear(); nav.playlistsChanged(); nav.back()
                                if (conn) { Repo.dropCatalogs(); if (pl.active) Import.run(pl, reset = true) else CatalogDb.forget(old.id) }
                            } else {
                                // at most one playlist is active: switched on here, the others go off; otherwise it is only saved
                                PlCheck.put(pl.id, true, null)
                                if (activate) {
                                    Store.savePlaylists(ctx, Store.playlists(ctx).map { it.copy(active = false) } + pl.copy(active = true))
                                    Import.run(pl.copy(active = true))
                                } else Store.savePlaylists(ctx, Store.playlists(ctx) + pl.copy(active = false))
                                Toast.makeText(ctx, if (pl.type != SourceType.M3U) tr("Povezano ✓") else trf("Učitano: %s", info), Toast.LENGTH_LONG).show()
                                nav.playlistsChanged(); nav.root(Screen.Home)
                            }
                        } catch (e: Exception) {
                            error = connectionError(e)
                        } finally { busy = false }
                    }
                }
                if (Store.playlists(ctx).isNotEmpty()) Button("Odustani", R.drawable.lc_x) { EditPlaylist.target = null; nav.back() }
                else Button("Nazad", R.drawable.lc_x) { Welcome.tab = -1 }
            }
        }
        Column(Modifier.width(290.dp).padding(top = 4.dp).clip(Rnd(16.dp)).background(Color(0x14FFFFFF)).border(1.dp, Silver.Border, Rnd(16.dp)).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            T(if (phoneGot) "✓ Primljeno s telefona" else "Unesi s telefona", 17.sp, if (phoneGot) C.Green else C.Text, FontWeight.SemiBold)
            if (qr != null) androidx.compose.foundation.Image(qr, null, Modifier.size(200.dp).clip(Rnd(12.dp)).background(Color.White).padding(6.dp))
            else T("QR kod nije dostupan bez kućne mreže.", 13.sp, C.Muted, maxLines = 3)
            T("Skeniraj kamerom telefona (ista kućna mreža), upiši podatke i pritisni Gotovo.", 13.sp, C.Soft, maxLines = 4)
        }
        }
    }
    if (picking) FileBrowser(tr("Izaberi M3U fajl"), setOf("m3u", "m3u8", "txt"), onDismiss = { picking = false }) { f ->
        picking = false
        try { url = copyLocalM3u(ctx, f); if (name.isBlank()) name = f.nameWithoutExtension } catch (e: Exception) { error = e.message }
    }
    if (askLeave) Popup(tr("Imaš nesačuvane izmjene"), { askLeave = false }) {
        OptionRow("Nastavi uređivanje", R.drawable.lc_pencil) { askLeave = false }
        OptionRow("Odbaci", R.drawable.lc_x) { askLeave = false; EditPlaylist.target = null; nav.back() }
    }
}

/** Phase B: a local M3U file is copied into the app in 64 KB pieces (never whole in memory), after a free-space check; a failed copy is removed. */
fun copyLocalM3u(ctx: android.content.Context, src: java.io.File): String {
    val dir = java.io.File(ctx.filesDir, "m3u").apply { mkdirs() }
    if (src.length() + 20L * 1024 * 1024 > dir.usableSpace) throw java.io.IOException(tr("Nema dovoljno prostora na uređaju"))
    val out = java.io.File(dir, "local_" + System.currentTimeMillis() + ".m3u")  // i18n-ignore
    try {
        src.inputStream().use { i -> out.outputStream().use { o -> val b = ByteArray(64 * 1024); while (true) { val n = i.read(b); if (n <= 0) break; o.write(b, 0, n) } } }
    } catch (e: Exception) { out.delete(); throw e }
    return "file://" + out.path
}

/** Connection errors in plain words instead of technical messages. */
private fun connectionError(e: Exception): String {
    val m = (e.message ?: "").lowercase()
    return when {
        e is java.net.UnknownHostException || "unable to resolve" in m -> tr("Server nije pronađen. Provjeri adresu.")
        e is java.net.SocketTimeoutException || "timeout" in m || "timed out" in m -> tr("Server ne odgovara. Pokušaj ponovo malo kasnije.")
        e is java.net.ConnectException || "refused" in m -> tr("Veza sa serverom odbijena. Provjeri adresu i port.")
        "401" in m || "403" in m || "auth" in m || "lozink" in m -> tr("Pogrešno korisničko ime ili lozinka.")
        e is javax.net.ssl.SSLException -> tr("Sigurna veza nije uspjela. Probaj adresu s http://")
        "404" in m -> tr("Adresa ne postoji na serveru. Provjeri link.")
        else -> trf("Nije uspjelo: %s", e.message ?: e.javaClass.simpleName)
    }
}

/* ---------------------------------------------------------------- appearance */


/* ---------------------------------------------------------------- movies & subtitles */
