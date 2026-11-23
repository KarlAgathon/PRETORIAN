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

private data class Section(val id: String, val icon: Int, val title: String, val sub: String)



@Composable
private fun Page(title: String, sub: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        T(title, 22.sp, C.Text, FontWeight.Bold)
        if (sub != null) T(sub, 13.sp, C.Muted, maxLines = 2)
        content()
    }
}

@Composable
private fun Label(text: String) = T(text, 13.sp, C.Muted, FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))

/* ---------------------------------------------------------------- playlists */

/** Playlist being edited in "Add playlist" (null = a new one). */
object EditPlaylist { var target: Playlist? = null }

/** remembers an opened playlist page while you are in Settings */
object PlNav { var open: String? = null }



/** The same ▲ ▼ buttons everywhere something is reordered (Home rows, playlists, EPG…): grey at the top / bottom,
 *  the focus stays on the button of the moved item, so it follows the row. */
@Composable
fun UpDown(key: String, index: Int, last: Int, onMove: (Int) -> Unit) {
    listOf(-1 to "▲", 1 to "▼").forEach { (d, g) ->  // i18n-ignore
        val can = if (d < 0) index > 0 else index < last
        Focusable(modifier = Modifier.size(48.dp).border(1.dp, C.Line, Rnd(12.dp)),
            onClick = { if (can) { onMove(d); SetFocus.last = "$key:$d"; SetFocus.pending = true } },
            focusRequester = setFocusFr("$key:$d"), shape = Rnd(12.dp), normal = Color.Transparent, focused = C.Red, zoom = 1f) { f ->
            T(g, 14.sp, if (!can) C.Line else if (f) C.OnRed else C.Text, FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
        }
    }
}

/** A row with its own on/off pill: OK on the name opens the menu, Right → the pill, OK switches it at once. */
@Composable
fun PillRow(key: String, title: String, sub: String?, on: Boolean, titleColor: Color? = null, modifier: Modifier = Modifier,
            onLong: (() -> Unit)? = null, onToggle: () -> Unit, onOpen: () -> Unit) {
    Row(modifier.height(if (sub != null) 56.dp else 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Focusable(modifier = Modifier.weight(1f).fillMaxHeight().border(1.dp, C.Line, Rnd(12.dp)),
            onClick = { SetFocus.last = key; onOpen() }, onMenu = onLong, onFocus = { SetFocus.last = key }, focusRequester = setFocusFr(key),
            shape = Rnd(12.dp), normal = C.Rail, focused = C.Red, zoom = 1f) { f ->
            Column(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.Center) {
                T(title, 15.sp, if (f) C.OnRed else (titleColor ?: C.Text), FontWeight.SemiBold, maxLines = 1)
                if (sub != null) T(sub, 12.sp, if (f) C.OnRed.copy(alpha = 0.65f) else C.Muted, maxLines = 1)
            }
        }
        Focusable(modifier = Modifier.width(118.dp).fillMaxHeight().border(1.dp, C.Line, Rnd(12.dp)),
            onClick = { SetFocus.last = "$key:sw"; SetFocus.pending = true; onToggle() }, focusRequester = setFocusFr("$key:sw"),
            shape = Rnd(12.dp), normal = C.Rail, focused = C.Red, zoom = 1f) { f ->
            Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                T(if (on) "Aktivna" else "Isključena", 12.sp, if (f) C.OnRed else if (on) C.Green else C.Muted, FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
                Toggle(on, f, height = 20.dp)
            }
        }
    }
}

/** EPG sources: all of them ([pl] null) or the ones that apply to one playlist (its menu › EPG of this playlist). */
@Composable
private fun EpgSourcesBlock(pl: Playlist?) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var add by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<EpgSources.Src?>(null) }
    var menu by remember { mutableStateOf<EpgSources.Src?>(null) }
    var defFor by remember { mutableStateOf<EpgSources.Src?>(null) }
    var updating by remember { mutableStateOf(false) }
    val pls = remember { Store.playlists(ctx) }
    val all = remember(EpgSources.rev) { EpgSources.list() }
    val list = all.filter { pl == null || it.pid == null || it.pid == pl.id }
    T("XMLTV link (.xml ili .xml.gz) ili fajl. Redoslijed: zadani EPG, pa ostali uključeni, pa providerov.", 12.sp, C.Muted, maxLines = 1)
    // the provider's own guide: can be switched off too, but not edited or deleted
    (if (pl != null) listOf(pl) else pls).filter { it.type == SourceType.XTREAM }.forEach { p ->
        PillRow("epgprov:${p.id}", trf("%s · od providera", p.name), tr("ugrađeni program providera"), EpgSources.providerOn(p.id),
            onToggle = { EpgSources.setProvider(p.id, !EpgSources.providerOn(p.id)); Repo.clear() }) { EpgSources.setProvider(p.id, !EpgSources.providerOn(p.id)); Repo.clear() }
    }
    list.forEachIndexed { i, s ->
        val defNames = if ("*" in s.def) listOf(tr("sve playliste")) else pls.filter { it.id in s.def }.map { it.name }
        val status = when {
            s.error != null -> trf("Greška: %s", s.error)
            s.channels >= 0 -> trf("%s kanala · osvježeno %s", s.channels, java.text.SimpleDateFormat("d. M. HH:mm", java.util.Locale.getDefault()).format(java.util.Date(s.at)))
            else -> tr("Još nije provjeren")
        }
        val sub = (if (defNames.isNotEmpty()) "⭐ " + trf("zadani za %s", defNames.joinToString(", ")) + " · " else "") + status
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PillRow("epg:${s.id}", s.name, sub, s.on, titleColor = if (s.error != null) C.Danger else null, modifier = Modifier.weight(1f),
                onLong = { EpgSources.update(s.copy(on = !s.on)); Repo.clear() },
                onToggle = { EpgSources.update(s.copy(on = !s.on)); Repo.clear() }) { menu = s }
            UpDown("epg:${s.id}", i, list.lastIndex) { d -> EpgSources.move(s, d) }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button("＋ Dodaj EPG", null) { add = true }  // i18n-ignore
        if (list.isNotEmpty()) Button(if (updating) "Osvježavam…" else "Osvježi sve EPG-ove", R.drawable.lc_refresh_cw) {
            if (updating) return@Button
            updating = true
            scope.launch { list.forEach { try { EpgSources.refresh(it) } catch (_: Exception) {} }; Repo.clear(); updating = false }
        }
    }
    if (add) EpgForm(null, pl, onDismiss = { add = false }) { n, v, sh, ev, def ->
        add = false
        val s = EpgSources.add(v, null, n, def, sh, ev); scope.launch { EpgSources.refresh(s); Repo.clear() }
    }
    edit?.let { s ->
        EpgForm(s, pl, onDismiss = { edit = null }) { n, v, sh, ev, def ->
            edit = null
            val changed = v != s.url
            val e = s.copy(url = v, name = n.ifBlank { EpgSources.nameOf(v) }, shift = sh, every = ev, def = def,
                channels = if (changed) -1 else s.channels, error = if (changed) null else s.error)
            EpgSources.update(e); Repo.clear(); if (changed) scope.launch { EpgSources.refresh(e); Repo.clear() }
        }
    }
    menu?.let { s ->
        Popup(s.name, { menu = null; SetFocus.tick++ }) {
            T(s.url.removePrefix("file://"), 12.sp, C.Muted, maxLines = 2)
            OptionRow("⭐ Zadani za…", null, sub = tr("koje playliste ga koriste prvog")) { menu = null; defFor = s }  // i18n-ignore
            OptionRow("Uredi", R.drawable.lc_pencil) { menu = null; edit = s }
            OptionRow(if (s.on) "Ugasi" else "Uključi", R.drawable.lc_check) { EpgSources.update(s.copy(on = !s.on)); Repo.clear(); menu = null }
            OptionRow("Osvježi sada", R.drawable.lc_refresh_cw) { menu = null; scope.launch { EpgSources.refresh(s); Repo.clear() } }
            OptionRow("Obriši", R.drawable.lc_trash_2, color = C.Danger) { EpgSources.delete(s); Repo.clear(); menu = null }
        }
    }
    defFor?.let { s0 ->
        val s = all.firstOrNull { it.id == s0.id } ?: s0
        Popup(trf("%s · zadani za", s.name), { defFor = null; SetFocus.tick++ }, width = 600.dp) {
            fun set(d: Set<String>) { EpgSources.update(s.copy(def = d)); Repo.clear() }
            ToggleOption("Sve playliste", "*" in s.def) { set(if ("*" in s.def) emptySet() else setOf("*")) }
            pls.forEach { p -> ToggleOption(p.name, s.isDefault(p.id)) {
                val cur = if ("*" in s.def) pls.map { it.id }.toSet() else s.def
                set(if (p.id in cur) cur - p.id else cur + p.id)
            } }
            Button("Gotovo", R.drawable.lc_check, primary = true) { defFor = null; SetFocus.tick++ }
        }
    }
}

/** One choice with the pill switch (used in pickers). */
@Composable
fun ToggleOption(label: String, on: Boolean, onClick: () -> Unit) {
    Focusable(modifier = Modifier.fillMaxWidth().height(44.dp), onClick = onClick, shape = Rnd(10.dp), normal = Color.Transparent, focused = C.CardHi, zoom = 1f) { _ ->
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            T(label, 14.sp, if (on) C.Text else C.Muted, FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f))
            Toggle(on, height = 20.dp)
        }
    }
}

/** Add / edit an EPG: name, address (link or a file from USB / disk), time shift, refresh, default for… */
@Composable
private fun EpgForm(src: EpgSources.Src?, pl: Playlist?, onDismiss: () -> Unit, onOk: (String, String, Int, Int, Set<String>) -> Unit) {
    val ctx = LocalContext.current
    val pls = remember { Store.playlists(ctx) }
    var name by remember { mutableStateOf(src?.name ?: "") }
    var link by remember { mutableStateOf(src?.url ?: "") }
    var shift by remember { mutableIntStateOf(src?.shift ?: 0) }
    var every by remember { mutableIntStateOf(src?.every ?: 0) }
    var def by remember { mutableStateOf(src?.def ?: (if (pl != null) setOf(pl.id) else emptySet())) }
    var pick by remember { mutableStateOf<String?>(null) }
    val fr = remember { FocusRequester() }
    Popup(if (src == null) "Dodaj EPG" else "Uredi EPG", onDismiss, width = 700.dp) {
        Field("Ime", name, { name = it }, Modifier.fillMaxWidth().focusRequester(fr))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Adresa (http://…/epg.xml ili .xml.gz)", link.removePrefix("file://"), { link = it }, Modifier.weight(1f), KeyboardType.Uri)
            Button("Fajl…", R.drawable.lc_file) { pick = "file" }
        }
        val shifts = (-3..3).toList()
        OptionRow("Pomak vremena", null, value = if (shift == 0) tr("Bez pomaka") else (if (shift > 0) "+" else "") + shift + " h") { pick = "shift" }  // i18n-ignore
        val evs = listOf(0, 12, 24)
        val evNames = listOf("S playlistom", "Svakih 12 h", "Svaka 24 h")
        OptionRow("Osvježavanje", null, value = tr(evNames[evs.indexOf(every).coerceAtLeast(0)])) { pick = "every" }
        OptionRow("Zadani za…", null, value = if ("*" in def) tr("Sve playliste") else if (def.isEmpty()) tr("Nijednu") else pls.filter { it.id in def }.joinToString(", ") { it.name }) { pick = "def" }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button("Spremi", R.drawable.lc_check, primary = true) { if (link.isNotBlank()) onOk(name.trim(), link.trim(), shift, every, def) }
            Button("Odustani", R.drawable.lc_x) { onDismiss() }
        }
        when (pick) {
            "shift" -> Popup("Pomak vremena", { pick = null }) { shifts.forEach { h ->
                OptionRow(if (h == 0) tr("Bez pomaka") else (if (h > 0) "+" else "") + h + " h", if (h == shift) R.drawable.lc_check else null, color = if (h == shift) C.Green else null) { shift = h; pick = null } } }  // i18n-ignore
            "every" -> Popup("Osvježavanje", { pick = null }) { evs.forEachIndexed { i, e ->
                OptionRow(evNames[i], if (e == every) R.drawable.lc_check else null, color = if (e == every) C.Green else null) { every = e; pick = null } } }
            "def" -> Popup("Zadani za…", { pick = null }, width = 600.dp) {
                ToggleOption("Sve playliste", "*" in def) { def = if ("*" in def) emptySet() else setOf("*") }
                pls.forEach { p -> ToggleOption(p.name, "*" in def || p.id in def) {
                    val cur = if ("*" in def) pls.map { it.id }.toSet() else def
                    def = if (p.id in cur) cur - p.id else cur + p.id
                } }
                Button("Gotovo", R.drawable.lc_check, primary = true) { pick = null }
            }
            "file" -> FilePicker("Izaberi EPG fajl", setOf("xml", "gz"), onDismiss = { pick = null }) { bytes, fname ->
                try {
                    val f = java.io.File(ctx.filesDir, "epg_" + System.currentTimeMillis().toString(36) + (if (fname.endsWith(".gz")) ".xml.gz" else ".xml"))
                    f.writeBytes(bytes); link = "file://" + f.path; if (name.isBlank()) name = fname.substringBefore('.')
                } catch (_: Exception) {}
                pick = null
            }
            else -> {}
        }
    }
    LaunchedEffect(Unit) { try { fr.requestFocus() } catch (_: Exception) {} }
}

@Composable
fun AddPlaylistScreen(nav: Nav) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val old = remember { EditPlaylist.target }
    if (old == null && Store.playlists(ctx).isEmpty() && Welcome.tab < 0) { OnboardingScreen(nav); return }
    // first setup: Back from the form goes one step back to the source choice
    androidx.activity.compose.BackHandler(enabled = old == null && Store.playlists(ctx).isEmpty() && Welcome.tab >= 0) { Welcome.tab = -1 }
    var tab by remember { mutableIntStateOf(when { old?.type == SourceType.M3U -> 1; old?.type == SourceType.STALKER -> 2; old == null && Welcome.tab in 1..2 -> Welcome.tab; else -> 0 }) }
    var mac by remember { mutableStateOf(old?.mac ?: Stalker.suggestMac(ctx)) }
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
            Field("EPG link (nije obavezno)", epg, { epg = it }, Modifier.fillMaxWidth(), KeyboardType.Uri)
            if (tab == 0) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { ToggleRow("TV kanali", live) { live = it } }
                Box(Modifier.weight(1f)) { ToggleRow("Filmovi", vod) { vod = it } }
                Box(Modifier.weight(1f)) { ToggleRow("Serije", series) { series = it } }
            }
            T("Dugačke linkove i lozinke možeš kucati s telefona: Google TV aplikacija › Daljinski › ikona tastature.", 12.sp, C.Muted, maxLines = 2)
            error?.let { T(it, 14.sp, C.Danger, maxLines = 3) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(if (busy) "Provjeravam…" else "Spremi i učitaj", R.drawable.lc_check, primary = true, focusRequester = saveFr) {
                    if (busy) return@Button
                    val base = Playlist(
                        id = old?.id ?: System.currentTimeMillis().toString(),
                        name = name.trim().ifBlank { when (tab) { 0 -> "Xtream"; 2 -> "Stalker"; else -> "M3U" } },  // i18n-ignore
                        type = when (tab) { 0 -> SourceType.XTREAM; 2 -> SourceType.STALKER; else -> SourceType.M3U },
                        server = server.trim(), user = user.trim(), pass = pass.trim(), url = url.trim(), active = old?.active ?: true,
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
                                e.isBlank() -> { if (oldEpg != null) EpgSources.delete(oldEpg); null }
                                oldEpg != null && oldEpg.url == e -> null
                                oldEpg != null -> oldEpg.copy(url = e).also { EpgSources.update(it) }
                                else -> EpgSources.add(e, pl.id, def = setOf(pl.id))
                            }
                            if (epgSrc != null) launch { try { EpgSources.refresh(epgSrc) } catch (_: Exception) {} }
                            if (old != null) {
                                Store.savePlaylists(ctx, Store.playlists(ctx).map { if (it.id == old.id) pl else it })
                                Toast.makeText(ctx, tr("Spremljeno"), Toast.LENGTH_SHORT).show()
                                EditPlaylist.target = null; Repo.clear(); Repo.dropCatalogs(); nav.playlistsChanged(); nav.back()
                                if (pl.active) Import.run(pl, reset = true) else CatalogDb.forget(old.id)
                            } else {
                                // only one playlist is active (approved): the new one switches the others off
                                Store.savePlaylists(ctx, Store.playlists(ctx).map { it.copy(active = false) } + pl.copy(active = true))
                                Import.run(pl.copy(active = true))
                                Toast.makeText(ctx, if (pl.type != SourceType.M3U) tr("Povezano ✓") else trf("Učitano: %s", info), Toast.LENGTH_LONG).show()
                                nav.playlistsChanged(); nav.root(Screen.Home)
                            }
                        } catch (e: Exception) {
                            error = connectionError(e)
                        } finally { busy = false }
                    }
                }
                if (Store.playlists(ctx).isNotEmpty()) Button("Odustani", R.drawable.lc_x) { EditPlaylist.target = null; nav.back() }
                else if (Look.age) Button("Nazad", R.drawable.lc_x) { Welcome.tab = -1 }
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


/** "Bosanski, Hrvatski, Engleski" from "bs,hr,en" */
fun langList(codes: String): String = codes.split(',').filter { it.isNotBlank() }.joinToString(", ") { Langs.label(it) }

/* ---------------------------------------------------------------- language */


fun setAppLang(ctx: android.content.Context, code: String) {
    Look.lang = code; Look.save(ctx)
    val l = Langs.of(code)
    Toast.makeText(ctx, tr("Jezik promijenjen") + ": " + (l?.native ?: code), Toast.LENGTH_SHORT).show()
}

/* ---------------------------------------------------------------- reminders */


/* ---------------------------------------------------------------- backup */


/* ---------------------------------------------------------------- parental */


/* ---------------------------------------------------------------- update + about */


internal val COUNTRIES = listOf("" to "Automatski (po uređaju)", "DE" to "Njemačka", "AT" to "Austrija", "CH" to "Švicarska", "BA" to "Bosna i Hercegovina",
    "RS" to "Srbija", "HR" to "Hrvatska", "ME" to "Crna Gora", "SI" to "Slovenija", "MK" to "Sjeverna Makedonija", "GB" to "Velika Britanija",
    "IT" to "Italija", "ES" to "Španija", "FR" to "Francuska", "TR" to "Turska", "US" to "SAD")





/* ================================================================ Settings in the new look (0.7.0) */

/** Osnovno shows only what everyone needs; Napredno shows everything. */
/** Every option is always there (Lite only saves the box, it hides nothing); only simple mode has no Settings. */
object SetMode {
    val advanced: Boolean get() = true
    @Suppress("UNUSED_PARAMETER") fun set(on: Boolean) {}
}

// eight categories in this order (approved), one menu at a time
private val SECTIONS2 = listOf(
    Section("sources", R.drawable.lc_list, "Playliste", "Dodaj, uredi, osvježi, EPG"),
    Section("playback", R.drawable.lc_play, "Reprodukcija", "Plejer, titlovi, zvuk, stream"),
    Section("appearance", R.drawable.lc_languages, "Izgled i jezik", "Jezik, veličina teksta, Lite / Full"),
    Section("home", R.drawable.lc_house, "Početna i navigacija", "Pokretanje s boxom, rad u pozadini, mreža"),
    Section("privacy", R.drawable.lc_shield_check, "Roditeljska kontrola", "PIN i skrivene kategorije"),
    Section("sportscenter", R.drawable.lc_trophy, "Sport", "Sportovi, timovi, takmičenja i obavještenja"),
    Section("notify", R.drawable.lc_bell, "Obavještenja i podsjetnici", "Emisije i utakmice"),
    Section("system", R.drawable.lc_settings, "Sistem i održavanje", "Uređaj, backup, ažuriranje, dijagnostika"),
)
private val BASIC = SECTIONS2.map { it.id }.toSet()

private data class SettingsLink(val id: String, val title: String, val sub: String, val icon: Int)
private val SETTINGS_LINKS = mapOf(
    "sources" to listOf(
        SettingsLink("playlists", "Playliste", "Dodavanje, aktivacija i osvježavanje", R.drawable.lc_list),
        SettingsLink("epg", "EPG izvori", "Programski vodič i pomak vremena", R.drawable.lc_calendar),
        SettingsLink("groups", "Grupe i kanali", "Redoslijed, skrivanje i zaključavanje", R.drawable.lc_layers),
    ),
    "playback" to listOf(
        SettingsLink("player", "Plejer", "Titlovi, zvuk, stream i kontrole", R.drawable.lc_play),
    ),
    "appearance" to listOf(
        SettingsLink("language", "Jezik", "Jezik menija i opisa sadržaja", R.drawable.lc_languages),
        SettingsLink("look", "Izgled", "Veličina teksta, vrijeme i performanse", R.drawable.lc_palette),
    ),
    "home" to listOf(
        SettingsLink("general", "Pokretanje i mreža", "Start s boxom, rad u pozadini, mreža", R.drawable.lc_settings),
    ),
    "privacy" to listOf(
        SettingsLink("pin", "PIN", "Promijeni PIN i gdje se traži", R.drawable.lc_key_round),
        SettingsLink("parental", "Roditeljska kontrola", "Skrivene kategorije i vrijeme gledanja", R.drawable.lc_lock),
    ),
    "sportscenter" to listOf(
        SettingsLink("sports", "Sport", "Država, timovi i obavještenja", R.drawable.lc_trophy),
    ),
    // notifications (what the app tells by itself) and reminders (what you asked for) are two different things
    "notify" to listOf(
        SettingsLink("notifications", "Obavještenja", "Šta ti aplikacija sama javlja", R.drawable.lc_bell),
        SettingsLink("reminders", "Podsjetnici", "Emisije, filmovi, serije i utakmice", R.drawable.lc_clock),
    ),
    // Updates first; device facts are in About (approved)
    "system" to listOf(
        SettingsLink("update", "Ažuriranje", "Provjeri i instaliraj novu verziju", R.drawable.lc_download),
        SettingsLink("backup", "Backup i vraćanje", "Sačuvaj ili vrati postavke", R.drawable.lc_archive),
        SettingsLink("diagnostics", "Dijagnostika", "Zastoji, pali kanali i rušenja", R.drawable.lc_gauge2),
        SettingsLink("about", "O aplikaciji", "Verzija, uređaj i licence", R.drawable.lc_info),
    ),
)

/** Search › Settings: every settings page by its name, what it holds and the words people use for it. */
data class SettingHit(val id: String, val title: String, val path: String)
object SettingsSearchIndex {
    private val WORDS = mapOf(
        "playlists" to "playlista, Xtream, M3U, Stalker, dodaj, osvježi, provajder, server",
        "epg" to "EPG, vodič, program, pomak, vrijeme",
        "groups" to "grupe, kanali, redoslijed, sakrij, zaključaj",
        "player" to "plejer, titl, titlovi, zvuk, audio, dekoder, bafer, AFR, pomak zvuka, eksterni plejer, trailer",
        "general" to "start, pokretanje, box, pozadina, mreža, User-Agent, udpxy",
        "reminders" to "podsjetnik, emisija, utakmica, obavijest",
        "sports" to "sport, tim, liga, država, rezultati",
        "language" to "jezik, prevod, meni",
        "look" to "izgled, tekst, veličina, Lite, Full, performanse",
        "pin" to "PIN, šifra, zaključavanje",
        "parental" to "roditeljska kontrola, djeca, odrasli, kategorije, vrijeme gledanja",
        "backup" to "backup, vraćanje, USB, fajl, lozinka",
        "update" to "ažuriranje, nova verzija",
        "about" to "o aplikaciji, verzija, licence, privatnost, pomoć",
        "diagnostics" to "dijagnostika, zapis, greške, stream log",
        "device" to "uređaj, box, memorija, RAM, mreža, IP, keš, ocjena",
    )
    fun find(query: String): List<SettingHit> {
        val words = query.lowercase().split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return SETTINGS_LINKS.flatMap { (sec, links) ->
            val group = SECTIONS2.firstOrNull { it.id == sec }?.title ?: ""
            links.map { l -> Triple(l, group, listOf(l.title, l.sub, WORDS[l.id] ?: "").joinToString(", ")) }
        }.filter { (_, _, text) -> val t = text.lowercase(); val tt = tr(text).lowercase(); words.all { w -> t.contains(w) || tt.contains(w) } }
            .map { (l, group, _) -> SettingHit(l.id, tr(l.title), tr("Postavke") + " › " + tr(group)) }
    }
}

/** Settings screen of the new look: sections on the left, the page on the right, sub-pages open inside. */
@Composable
fun SettingsScreen(nav: Nav) {
    val ctx = LocalContext.current
    val list = SECTIONS2.filter { SetMode.advanced || it.id in BASIC }
    // a request can name a group ("about") or one of its pages ("update"): the page opens inside its group
    fun target(id: String): Pair<String, String?> =
        if (list.any { it.id == id }) id to null
        else SETTINGS_LINKS.entries.firstOrNull { e -> e.value.any { it.id == id } }?.let { it.key to id } ?: ("sources" to null)
    val start = remember { target(SettingsNav.section?.also { SettingsNav.section = null } ?: SettingsNav.last ?: if (Updater.available != null) "update" else "sources") }
    var section by remember { mutableStateOf(start.first) }
    var sub by remember { mutableStateOf(start.second) }
    var keepSub by remember { mutableStateOf(start.second != null) }
    LaunchedEffect(section) { SettingsNav.last = section; if (keepSub) keepSub = false else sub = null }
    // a notification or a button elsewhere asks for a section while Settings is already open
    LaunchedEffect(SettingsNav.section) { SettingsNav.section?.let { s -> val t = target(s); keepSub = t.second != null && t.first != section; section = t.first; sub = t.second; SettingsNav.section = null } }
    val first = remember { FocusRequester() }
    var listFocused by remember { mutableIntStateOf(0) }
    // optional: Settings behind the PIN
    var unlocked by remember { mutableStateOf(!(Prefs.bool("pin_settings") && Lock.hasPin())) }
    if (!unlocked) {
        PinDialog("Postavke", tr("Unesi PIN"), onDismiss = { nav.back() }) { pin -> val err = Lock.check(pin); if (err == null) unlocked = true; err }
        return
    }
    var inPage by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    // like Search: inside a section the list shrinks to a narrow rail of icons and the page gets the room
    // the section names always stay readable (approved): the rail only gets a little narrower inside a page
    val railW by androidx.compose.animation.core.animateDpAsState(if (inPage) 236.dp else 270.dp, label = "rail")
    val slim = false
    val classic = Look.layout == 0 || Look.dawn
    LaunchedEffect(classic, inPage) {
        if (classic) {
            Chrome.driven = true
            Chrome.rail = if (inPage) 0 else 2
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (classic) {
                Chrome.driven = false
                Chrome.rail = 2
            }
        }
    }
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(railW).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 16.dp)
            .onFocusChanged { if (classic && it.hasFocus) Chrome.rail = 2 },
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            list.forEach { s ->
                val sel = s.id == section
                Focusable(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), onClick = { section = s.id; sub = null }, onFocus = { listFocused++ },
                    focusRequester = if (sel) first else null, normal = if (sel) C.CardHi else Color.Transparent, focused = C.Red, zoom = 1f) { f ->
                    Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(26.dp).clip(Rnd(8.dp)).background(if (f) C.OnRed.copy(alpha = 0.08f) else C.Card), contentAlignment = Alignment.Center) {
                            Ico(s.icon, 14.dp, if (f) C.OnRed else C.Text)
                        }
                        if (!slim) Column(Modifier.weight(1f)) {
                            T(s.title, 14.sp, if (f) C.OnRed else C.Text, FontWeight.SemiBold, maxLines = 1)
                            T(if (s.id == "mode") (if (SetMode.advanced) "Napredno" else "Osnovno") else s.sub, 11.sp, if (f) C.OnRed.copy(alpha = 0.6f) else C.Muted, maxLines = 1)
                        }
                        if (!slim && s.id == "system" && Updater.available != null) Box(Modifier.size(16.dp).clip(CircleShape).background(C.Danger), contentAlignment = Alignment.Center) {
                            T("1", 9.sp, Color.White, FontWeight.Bold)
                        }
                    }
                }
            }
        }
        // Back goes one step: sub-page → its page, page → the list of sections, list → out of Settings
        BackHandler(enabled = true) {
            when {
                sub != null -> { SetFocus.pending = true; sub = null }
                inPage -> { inPage = false; scope.launch { first.tryFocus() } }
                else -> nav.back()
            }
        }
        LaunchedEffect(listFocused) { inPage = false }
        // into a section: Right lands on the item at the same height; Left from anywhere in the page goes back to this section on the left
        val pageFr = remember { FocusRequester() }
        // the page changed under the focus (a sub-page opened or closed): the focus goes back into the page,
        // never by itself to the first entry of the menu on the left (approved)
        LaunchedEffect(section, sub) { if (inPage) { delay(120); try { pageFr.requestFocus() } catch (_: Exception) {} } }
        Box(Modifier.weight(1f).fillMaxHeight().onFocusChanged { if (it.hasFocus) inPage = true }
            .onKeyEvent { ev ->
                if (ev.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && ev.key == Key.DirectionLeft) {
                    if (!focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Left)) scope.launch { inPage = false; first.tryFocus() }
                    true
                } else false
            }
            .focusProperties {
                @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
                exit = { d -> if (d == androidx.compose.ui.focus.FocusDirection.Left) { inPage = false; first } else FocusRequester.Default }
            }
            .focusRequester(pageFr).focusGroup()
            .padding(start = 18.dp, end = 24.dp, top = 4.dp, bottom = 8.dp)) {
            // a section with one page opens that page at once (approved: no extra step)
            val only = SETTINGS_LINKS[section]?.singleOrNull()?.id
            key(section, sub) {
                when (sub ?: only) {
                    "groups" -> GroupManager(nav)
                    "epg" -> { val epl = PlEdit.id?.let { id -> Store.playlists(ctx).firstOrNull { it.id == id } }
                        SetPage(if (epl != null) trf("EPG · %s", epl.name) else tr("EPG izvori"), "OK: meni · desno: uključi/ugasi · ▲ ▼ redoslijed") { EpgSourcesBlock(epl) } }
                    "plists", "playlists" -> PlaylistsListV2(nav)
                    "device" -> DeviceV2()
                    "pin" -> PinV2()
                    "parental" -> ParentalV2()
                    "general" -> GeneralV2()
                    "player" -> PlayerV2 { sub = it }
                    "look" -> AppearanceV2()
                    "language" -> LanguageV2()
                    "sports" -> SportV3()
                    "reminders" -> RemindersV2(nav)
                    "notifications" -> NotificationsV2()
                    "diagnostics" -> DiagnosticsV2()
                    "backup" -> BackupSettings()
                    "update" -> UpdateV2()
                    "about" -> AboutV2(onSub = { sub = it }, onSection = { id -> val t = target(id); if (t.first != section) { keepSub = t.second != null; section = t.first }; sub = t.second })
                    else -> SettingsHubPage(section, onOpen = { sub = it })
                }
            }
        }
    }
    LaunchedEffect(Unit) { try { first.requestFocus() } catch (_: Exception) {} }
}


@Composable
private fun SettingsHubPage(section: String, onOpen: (String) -> Unit) {
    val title = SECTIONS2.firstOrNull { it.id == section }?.title ?: "Postavke"
    val subtitle = SECTIONS2.firstOrNull { it.id == section }?.sub
    SetPage(title, subtitle) {
        SETTINGS_LINKS[section].orEmpty().forEach { item ->
            OptionRow(item.title, item.icon, sub = item.sub) { onOpen(item.id) }
        }
    }
}


/* ---------------------------------------------------------------- Početna */


/* ---------------------------------------------------------------- building blocks */

/** "Restore defaults" for one section: only its own settings go back, nothing else. */
@Composable
fun ResetSection(name: String, keys: List<String>, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var ask by remember { mutableStateOf(false) }
    SetRow("Vrati zadano", trf("samo postavke sekcije %s", tr(name)), "↺") { ask = true }
    if (ask) Popup(trf("Vratiti zadano za %s?", tr(name)), { ask = false; SetFocus.tick++ }) {
        OptionRow("Da, vrati zadano", R.drawable.lc_rotate_ccw, color = C.Amber) {
            Prefs.remove(*keys.toTypedArray()); ask = false
            android.widget.Toast.makeText(ctx, tr("Vraćeno na zadano"), android.widget.Toast.LENGTH_SHORT).show(); onDone()
        }
        OptionRow("Ne", R.drawable.lc_x) { ask = false; SetFocus.tick++ }
    }
}

/** Keeps the focus on the row you worked on: after a choice, a switch or a closed list the page is drawn again
 *  and the same row takes the focus back (it never jumps up to the menu). */
/** settings rows are solid, so the background picture never shows through them */
val SolidRow: Color get() = androidx.compose.ui.graphics.Color(0xFF1B1B1E).let { base -> C.Rail.compositeOverSafe(base) }
private fun Color.compositeOverSafe(b: Color): Color { val a = alpha; return Color(red * a + b.red * (1 - a), green * a + b.green * (1 - a), blue * a + b.blue * (1 - a), 1f) }

object SetFocus { var last: String? = null; var pending = false; var tick by mutableIntStateOf(0) }

@Composable
fun setFocusFr(id: String, given: FocusRequester? = null): FocusRequester {
    val fr = given ?: remember { FocusRequester() }
    LaunchedEffect(Unit) { if (SetFocus.pending && SetFocus.last == id) { SetFocus.pending = false; fr.tryFocus() } }
    LaunchedEffect(SetFocus.tick) { if (SetFocus.tick > 0 && SetFocus.last == id) { delay(60); fr.tryFocus() } }
    return fr
}

@Composable
fun SetPage(title: String, sub: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        T(title, 23.sp, C.Text, FontWeight.Bold)
        if (sub != null) T(sub, 13.sp, C.Muted, maxLines = 2)
        content()
    }
}

@Composable
fun SetSection(t: String) {
    androidx.compose.material3.Text(tr(t).uppercase(), style = androidx.compose.ui.text.TextStyle(fontFamily = Inter, fontSize = 12.sp, color = C.Muted,
        fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp), modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
}

/** One settings row: title and a short line under it, the value on the right in a pill. */
@Composable
fun SetRow(title: String, sub: String? = null, value: String? = null, arrow: String = "›", valueColor: Color? = null, new: Boolean = false,
           focusRequester: FocusRequester? = null, onLong: (() -> Unit)? = null, onClick: () -> Unit) {
    val id = "row:$title"
    val fr = setFocusFr(id, focusRequester)
    Focusable(modifier = Modifier.fillMaxWidth().heightIn(min = if (sub != null) 56.dp else 48.dp).border(1.dp, C.Line, Rnd(12.dp)),
        onClick = { SetFocus.last = id; SetFocus.pending = true; onClick() }, onMenu = onLong, onFocus = { SetFocus.last = id },
        focusRequester = fr, shape = Rnd(12.dp), normal = SolidRow, focused = C.Red, zoom = 1.01f) { f ->
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    T(title, 15.sp, if (f) C.OnRed else C.Text, FontWeight.SemiBold, maxLines = 2)
                    // no "NEW" badges in Settings (approved: calm rows)
                }
                if (sub != null) T(sub, 12.sp, if (f) C.OnRed.copy(alpha = 0.65f) else C.Muted, maxLines = 2)
            }
            if (value != null || arrow.isNotEmpty()) Row(Modifier.clip(Rnd(9.dp)).background(if (f) Color(0x14000000) else C.Card)
                .border(1.dp, if (f) Color(0x26000000) else C.Line, Rnd(9.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                if (value != null) T(value, 14.sp, if (f) C.OnRed else (valueColor ?: C.Text), FontWeight.SemiBold, maxLines = 1, modifier = Modifier.widthIn(max = 170.dp))
                if (arrow.isNotEmpty()) T(arrow, 10.sp, if (f) C.OnRed else C.Muted)
            }
        }
    }
}

/** Drop-down: OK opens the list, OK on a choice picks it and closes. */
@Composable
fun SetDrop(title: String, sub: String? = null, options: List<String>, selected: Int, new: Boolean = false, note: String? = null,
            valueColor: Color? = null, disabled: Set<Int> = emptySet(), onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    SetRow(title, sub, tr(options.getOrElse(selected) { options.first() }), "▼", valueColor, new) { open = true }
    if (open) Popup(title, { open = false; SetFocus.tick++ }) {
        if (note != null) T(note, 12.sp, C.Soft, maxLines = 4)
        options.forEachIndexed { i, o ->
            // a choice that is not possible here (e.g. an app that is not installed) stays grey and does nothing
            if (i in disabled) OptionRow(tr(o) + " · " + tr("nije instalirano"), null, color = C.Muted) { }
            else OptionRow(o, if (i == selected) R.drawable.lc_check else null, color = if (i == selected) C.Green else null) { open = false; onSelect(i); SetFocus.tick++ }
        }
    }
}

/** On/off in lists: the same pill switch as everywhere (green = on, grey = off), only smaller. */
@Composable
fun Lamp(on: Boolean) = Toggle(on, height = 16.dp)

/** Several at once: lamps in a list; "Sve" first switches everything. */
@Composable
fun SetMulti(title: String, sub: String? = null, options: List<String>, on: Set<Int>, new: Boolean = false, all: Boolean = true,
             note: String? = null, none: Boolean = false, onChange: (Set<Int>) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var cur by remember(open) { mutableStateOf(on) }
    // short value, never cut: one name, or only how many are chosen
    val value = when {
        on.size == options.size && all -> tr("Sve") + " (${options.size})"
        on.isEmpty() -> tr("Ništa")
        on.size == 1 -> tr(options[on.first()])
        else -> trf("%s izabrano", on.size)
    }
    SetRow(title, sub, value, "▼", null, new) { open = true }
    if (open) Popup(title, { open = false; onChange(cur); SetFocus.tick++ }, width = 620.dp) {
        if (note != null) T(note, 12.sp, C.Soft, maxLines = 3)
        if (all) LampRow("Sve", cur.size == options.size) { cur = if (cur.size == options.size) emptySet() else options.indices.toSet() }
        if (none) LampRow("Nijedan", cur.isEmpty()) { cur = emptySet() }
        options.forEachIndexed { i, o -> LampRow(o, i in cur) { cur = if (i in cur) cur - i else cur + i } }
        Button("Gotovo", R.drawable.lc_check, primary = true) { open = false; onChange(cur); SetFocus.tick++ }
    }
}

@Composable
private fun LampRow(label: String, on: Boolean, onClick: () -> Unit) {
    Focusable(modifier = Modifier.fillMaxWidth().height(38.dp), onClick = onClick, shape = Rnd(10.dp), normal = Color.Transparent, focused = C.CardHi, zoom = 1f) { _ ->
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Lamp(on); T(label, 14.sp, if (on) C.Text else C.Muted, FontWeight.Medium, maxLines = 1)
        }
    }
}

/** Switch row: OK turns it on or off. */
@Composable
fun SetSwitch(title: String, sub: String? = null, on: Boolean, new: Boolean = false, onLong: (() -> Unit)? = null, onChange: (Boolean) -> Unit) {
    val id = "sw:$title"
    val fr = setFocusFr(id)
    var cur by remember(on) { mutableStateOf(on) }
    Focusable(modifier = Modifier.fillMaxWidth().heightIn(min = if (sub != null) 54.dp else 46.dp).border(1.dp, C.Line, Rnd(12.dp)),
        onClick = { SetFocus.last = id; cur = !cur; onChange(cur) }, onMenu = onLong, onFocus = { SetFocus.last = id }, focusRequester = fr,
        shape = Rnd(12.dp), normal = SolidRow, focused = C.Red, zoom = 1.01f) { f ->
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    T(title, 15.sp, if (f) C.OnRed else C.Text, FontWeight.SemiBold, maxLines = 2)
                }
                if (sub != null) T(sub, 12.sp, if (f) C.OnRed.copy(alpha = 0.65f) else C.Muted, maxLines = 2)
            }
            Toggle(cur, f, height = if (sub != null) 24.dp else 22.dp)
        }
    }
}

/** Slider: the row shows the value; OK opens a small window where ◀ ▶ change it, OK or Back closes it.
 *  (◀ ▶ on the row itself move between rows, like everywhere else.) */
/** which slider popup is open: kept outside the row, so a page that redraws itself on every change (key(rev)) does not close it */
object SliderOpen { var id by mutableStateOf<String?>(null) }

@Composable
fun SetSlider(title: String, sub: String?, value: Int, min: Int, max: Int, step: Int, fmt: (Int) -> String = { "$it" }, onChange: (Int) -> Unit) {
    val open = SliderOpen.id == "sl:$title"
    var cur by remember(value) { mutableIntStateOf(value) }
    val id = "sl:$title"
    val fr = setFocusFr(id)
    @Composable
    fun Bar(v: Int, f: Boolean, h: androidx.compose.ui.unit.Dp) {
        Box(Modifier.fillMaxWidth().height(h).clip(Pill).background(if (f) Color(0x33000000) else C.Line)) {
            Box(Modifier.fillMaxWidth(((v - min).toFloat() / (max - min).coerceAtLeast(1)).coerceIn(0.02f, 1f)).fillMaxHeight().background(Silver.TextSoft))
        }
    }
    Focusable(modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).border(1.dp, C.Line, Rnd(12.dp)),
        onClick = { SetFocus.last = id; SliderOpen.id = "sl:$title" }, onFocus = { SetFocus.last = id }, focusRequester = fr,
        shape = Rnd(12.dp), normal = SolidRow, focused = C.Red, zoom = 1.01f) { f ->
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(title, 15.sp, if (f) C.OnRed else C.Text, FontWeight.SemiBold, modifier = Modifier.weight(1f))
                T(fmt(cur), 15.sp, if (f) C.OnRed else Silver.TextSoft, FontWeight.Bold)
            }
            Bar(cur, f, 4.dp)
            if (sub != null) T(sub, 11.sp, if (f) C.OnRed.copy(alpha = 0.65f) else C.Muted, maxLines = 1)
        }
    }
    if (open) Popup(title, { SliderOpen.id = null; SetFocus.tick++ }, width = 520.dp) {
        val pfr = remember { FocusRequester() }
        Focusable(onClick = { SliderOpen.id = null; SetFocus.tick++ }, focusRequester = pfr, shape = Rnd(14.dp), normal = C.Card, focused = C.CardHi, zoom = 1f,
            modifier = Modifier.fillMaxWidth().height(96.dp).onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { cur = (cur - step).coerceAtLeast(min); onChange(cur); true }
                    Key.DirectionRight -> { cur = (cur + step).coerceAtMost(max); onChange(cur); true }
                    else -> false
                }
            }) { _ ->
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T("◀", 18.sp, C.Text); Spacer(Modifier.weight(1f))
                    T(fmt(cur), 26.sp, C.Text, FontWeight.ExtraBold)
                    Spacer(Modifier.weight(1f)); T("▶", 18.sp, C.Text)
                }
                Bar(cur, false, 8.dp)
            }
        }
        T("◀ ▶ mijenja · OK ili Nazad zatvara", 12.sp, C.Muted)
        LaunchedEffect(Unit) { pfr.tryFocus() }
    }
}



internal fun csv(k: String, def: String) = Prefs.str(k, def).split(',').filter { it.isNotBlank() }
private fun onOff(b: Boolean) = if (b) 0 else 1
private val ON_OFF = listOf("Uključeno", "Isključeno")

/* ---------------------------------------------------------------- Playliste */

@Composable
private fun PlaylistsV2(nav: Nav, openSub: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf(Store.playlists(ctx)) }
    var busy by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<Playlist?>(null) }
    var del by remember { mutableStateOf<Playlist?>(null) }
    var pinDel by remember { mutableStateOf<Playlist?>(null) }
    fun refresh() { list = Store.playlists(ctx); Repo.clear(); nav.playlistsChanged() }
    // back from Groups and channels / EPG of a playlist: its menu opens again
    LaunchedEffect(Unit) { PlMenu.open?.let { id -> menu = list.firstOrNull { it.id == id }; PlMenu.open = null } }
    val on = list.count { it.active }
    SetPage("Playliste", if (list.isEmpty()) null else trf("Aktivne: %s od %s", on, list.size)) {
        T("OK na imenu: meni · desno: uključi/isključi · ▲ ▼ redoslijed", 11.sp, C.Muted)
        list.forEachIndexed { i, pl ->
            val sec = pl.exp.toLongOrNull()
            val sub = (when (pl.type) { SourceType.XTREAM -> "Xtream"; SourceType.STALKER -> "Stalker"; else -> "M3U" }) +  // i18n-ignore
                (if (sec != null && sec > 0) " · " + trf(if (Store.expired(pl)) "istekla %s" else "ističe %s", java.text.SimpleDateFormat("dd.MM.yy", java.util.Locale.getDefault()).format(java.util.Date(sec * 1000))) else "")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PillRow("pl:${pl.id}", pl.name, sub, pl.active, titleColor = if (Store.expired(pl)) C.Amber else null, modifier = Modifier.weight(1f),
                    onToggle = { Store.activate(ctx, pl.id); refresh() }) { menu = pl }
                UpDown("pl:${pl.id}", i, list.lastIndex) { d -> Store.movePlaylist(ctx, pl.id, d); refresh() }
            }
        }
        if (on > 0) SetRow("⏻  " + tr("Ugasi sve"), "nijedna playlista neće biti aktivna", arrow = "") { Store.setAll(ctx, false); refresh() }  // i18n-ignore
        SetRow("+  " + tr("Dodaj playlistu"), "Xtream, M3U link ili fajl") { EditPlaylist.target = null; nav.push(Screen.AddPlaylist) }
        SetRow("EPG izvori", "svi izvori TV programa", "● " + trf("%s aktivna", EpgSources.list().count { it.on }), valueColor = C.Green) { PlEdit.id = null; openSub("epg") }
        if (list.isNotEmpty()) {
            // refresh now: everything read again from the server, its group order taken, TV and Home load again
            fun refreshAll() {
                if (busy) return
                busy = true
                Repo.dropCatalogs(); Sports.lastCombined.clear()
                if (ServerOrder.on) Store.playlists(ctx).forEach { Ovr(ctx, it.id).takeServerOrder() }
                scope.launch {
                    val updated = Store.playlists(ctx).map { pl -> try { if (pl.type == SourceType.XTREAM) pl.copy(exp = Repo.test(pl)) else { Repo.test(pl); pl } } catch (e: Exception) { pl } }
                    Store.savePlaylists(ctx, updated); list = updated
                    Prefs.put("auto_refresh_at", System.currentTimeMillis())
                    updated.filter { it.active && it.type == SourceType.XTREAM }.forEach { CatalogDb.refreshLater(it, "vod"); CatalogDb.refreshLater(it, "series") }
                    GuideSync.later(ctx, force = true)
                    EpgSources.list().filter { it.on }.forEach { try { EpgSources.refresh(it) } catch (_: Exception) {} }
                    nav.playlistsChanged()
                    busy = false; Toast.makeText(ctx, tr("Osvježeno"), Toast.LENGTH_SHORT).show()
                }
            }
            SetRow(if (busy) "Osvježavam…" else "Osvježi sada", "kanali, grupe, filmovi, serije, EPG — sa servera") { refreshAll() }
            var arRev by remember { mutableIntStateOf(0) }
            key(arRev) {
                SetDrop("Osvježi samo", "playliste i EPG, bez čekanja ujutro", listOf("Osvježi sada") + AutoRefresh.OPTIONS, AutoRefresh.mode + 1) {
                    if (it == 0) refreshAll() else Prefs.put("auto_refresh", (it - 1).toLong()); arRev++ }
                SetSwitch("Raspored grupa sa servera", "osvježavanje preuzme redoslijed grupa i kanala sa servera (npr. IPTV Editor); sakrivene grupe ostaju sakrivene", ServerOrder.on) { Prefs.put("srv_order", it); arRev++ }
                val shifts = (-3..3).toList()
                SetDrop("Pomak TV programa (EPG)", "ako program kasni ili žuri", shifts.map { if (it == 0) tr("Bez pomaka") else (if (it > 0) "+" else "") + it + " h" },  // i18n-ignore
                    shifts.indexOf(EpgShift.hours).coerceAtLeast(3)) { Prefs.put("epg_shift", shifts[it].toLong()); Repo.clear(); arRev++ }
            }
        }
    }
    // the playlist's own menu: edit, on/off, its groups and channels, its EPG, refresh, delete
    menu?.let { pl0 ->
        val pl = list.firstOrNull { it.id == pl0.id } ?: pl0
        Popup(pl.name, { menu = null; SetFocus.tick++ }) {
            OptionRow("Uredi (naziv, link, korisnik, lozinka)", R.drawable.lc_pencil) { menu = null; EditPlaylist.target = pl; nav.push(Screen.AddPlaylist) }
            Focusable(onClick = { Store.activate(ctx, pl.id); refresh() }, shape = Rnd(12.dp), normal = Color.Transparent, focused = C.Red, zoom = 1f,
                modifier = Modifier.fillMaxWidth().height(52.dp)) { f ->
                Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Ico(R.drawable.lc_check, 18.dp, if (f) C.OnRed else C.Text)
                    T(if (pl.active) "Aktivna" else "Isključena", 15.sp, if (f) C.OnRed else C.Text, FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Toggle(pl.active, f, height = 24.dp)
                }
            }
            OptionRow("📂  " + tr("Grupe i kanali"), null, sub = tr("pomjeri, sakrij, zaključaj PIN-om")) { menu = null; PlEdit.id = pl.id; PlMenu.open = pl.id; openSub("groups") }  // i18n-ignore
            OptionRow("📺  " + tr("EPG ove playliste"), null, sub = tr("izvori, zadani, uključi/ugasi")) { menu = null; PlEdit.id = pl.id; PlMenu.open = pl.id; openSub("epg") }  // i18n-ignore
            OptionRow("Osvježi", R.drawable.lc_refresh_cw, sub = tr("kanali, filmovi i serije")) {
                menu = null; Repo.dropCatalogs()
                if (ServerOrder.on) Ovr(ctx, pl.id).takeServerOrder()
                scope.launch {
                    val up = try { if (pl.type == SourceType.XTREAM) pl.copy(exp = Repo.test(pl)) else { Repo.test(pl); pl } } catch (e: Exception) { pl }
                    Store.savePlaylists(ctx, Store.playlists(ctx).map { if (it.id == up.id) up else it }); refresh()
                    Toast.makeText(ctx, tr("Osvježeno"), Toast.LENGTH_SHORT).show()
                }
            }
            OptionRow("Obriši", R.drawable.lc_trash_2, color = C.Danger) { menu = null; if (Prefs.bool("pin_delpl") && Lock.hasPin()) pinDel = pl else del = pl }
        }
    }
    pinDel?.let { pl -> PinDialog(pl.name, tr("Unesi PIN"), onDismiss = { pinDel = null }) { pin -> val e = Lock.check(pin); if (e == null) { pinDel = null; del = pl }; e } }
    del?.let { pl ->
        Popup(trf("Obrisati %s?", pl.name), { del = null; SetFocus.tick++ }) {
            OptionRow("Obriši", R.drawable.lc_trash_2, color = C.Danger) {
                val rest = Store.playlists(ctx).filter { it.id != pl.id }
                Watch.removePlaylist(ctx, pl.id); Store.savePlaylists(ctx, rest)
                del = null; refresh()
            }
            OptionRow("Odustani", R.drawable.lc_x) { del = null }
        }
    }
}

@Composable
private fun PlaylistsListV2(nav: Nav) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf(Store.playlists(ctx)) }
    var menu by remember { mutableStateOf<Playlist?>(null) }
    var del by remember { mutableStateOf<Playlist?>(null) }
    var pinDel by remember { mutableStateOf<Playlist?>(null) }
    fun refresh() { list = Store.playlists(ctx); Repo.clear() }
    // a playlist clicked on the Playlists page opens its menu right away
    LaunchedEffect(Unit) { PlMenu.open?.let { id -> menu = list.firstOrNull { it.id == id }; PlMenu.open = null } }
    SetPage("Playliste", "OK na playlisti: uredi, uključi, osvježi, pomjeri ili ukloni") {
        // the first row adds a new playlist (approved)
        SetRow("＋ Dodaj playlistu", "Xtream, M3U ili Stalker · i s telefona preko QR koda", arrow = "›") { EditPlaylist.target = null; nav.push(Screen.AddPlaylist) }
        list.forEach { pl ->
            val sec = pl.exp.toLongOrNull()
            SetRow(pl.name, (when (pl.type) { SourceType.XTREAM -> "Xtream"; SourceType.STALKER -> "Stalker"; else -> "M3U" }) +
                (if (sec != null && sec > 0) " · " + trf("ističe %s", java.text.SimpleDateFormat("dd.MM.yy", java.util.Locale.getDefault()).format(java.util.Date(sec * 1000))) else ""),
                if (pl.active) "● " + tr("Aktivna") else "○ " + tr("Isključena"), valueColor = if (pl.active) C.Green else C.Muted) { menu = pl }
        }
    }
    menu?.let { pl ->
        Popup(pl.name, { menu = null }) {
            OptionRow("Uredi (naziv, link, korisnik, lozinka)", R.drawable.lc_pencil) { menu = null; EditPlaylist.target = pl; nav.push(Screen.AddPlaylist) }
            // on / off with the same switch as everywhere: green = on, grey = off
            val on = list.firstOrNull { it.id == pl.id }?.active ?: pl.active
            Focusable(onClick = { Store.activate(ctx, pl.id); refresh() }, shape = Rnd(12.dp), normal = Color.Transparent, focused = C.Red, zoom = 1f,
                modifier = Modifier.fillMaxWidth().height(52.dp)) { f ->
                Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Ico(R.drawable.lc_check, 18.dp, if (f) C.OnRed else C.Text)
                    T(if (on) "Aktivna" else "Isključena", 15.sp, if (f) C.OnRed else C.Text, FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Toggle(on, f, height = 24.dp)
                }
            }
            OptionRow("Osvježi kanale, filmove i serije", R.drawable.lc_refresh_cw) {
                menu = null; Repo.dropCatalogs()
                if (ServerOrder.on) Ovr(ctx, pl.id).takeServerOrder()
                scope.launch {
                    val up = try { if (pl.type == SourceType.XTREAM) pl.copy(exp = Repo.test(pl)) else { Repo.test(pl); pl } } catch (e: Exception) { pl }
                    Store.savePlaylists(ctx, Store.playlists(ctx).map { if (it.id == up.id) up else it }); refresh()
                    Toast.makeText(ctx, tr("Osvježeno"), Toast.LENGTH_SHORT).show()
                }
            }
            OptionRow("Pomjeri gore", R.drawable.lc_arrow_up) { Store.movePlaylist(ctx, pl.id, -1); list = Store.playlists(ctx); menu = null }
            OptionRow("Pomjeri dolje", R.drawable.lc_arrow_down) { Store.movePlaylist(ctx, pl.id, 1); list = Store.playlists(ctx); menu = null }
            OptionRow("Obriši playlistu", R.drawable.lc_trash_2, color = C.Danger) {
                menu = null; if (Prefs.bool("pin_delpl") && Lock.hasPin()) pinDel = pl else del = pl
            }
        }
    }
    pinDel?.let { pl -> PinDialog(pl.name, tr("Unesi PIN"), onDismiss = { pinDel = null }) { pin -> val e = Lock.check(pin); if (e == null) { pinDel = null; del = pl }; e } }
    del?.let { pl ->
        Popup(trf("Obrisati %s?", pl.name), { del = null }) {
            OptionRow("Obriši", R.drawable.lc_trash_2, color = C.Danger) {
                val rest = Store.playlists(ctx).filter { it.id != pl.id }
                Watch.removePlaylist(ctx, pl.id); Store.savePlaylists(ctx, rest)
                del = null; refresh()
            }
            OptionRow("Odustani", R.drawable.lc_x) { del = null }
        }
    }
}

/* ---------------------------------------------------------------- Jezik */

@Composable
private fun LanguageV2() {
    val ctx = LocalContext.current
    var rev by remember { mutableIntStateOf(0) }
    key(rev, Look.lang) {
        SetPage("Jezik") {
            val cur = Langs.APP.firstOrNull { it.code == Look.lang } ?: Langs.APP[0]
            Row(Modifier.fillMaxWidth().clip(Rnd(16.dp)).background(Color(0x332563EB)).border(1.dp, Color(0x552563EB), Rnd(16.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Flag(cur.flag, 52)
                Column {
                    Kicker("Aktivan jezik", Silver.TextSoft)
                    T(cur.native, 22.sp, C.Text, FontWeight.ExtraBold)
                }
            }
            SetSection("Dostupni jezici · OK aktivira")
            // the chosen language first, the others after it, two in a row
            val list = listOf(cur) + Langs.APP.filter { it.code != cur.code }
            list.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { l ->
                        val on = l.code == Look.lang
                        val id = "lang:" + l.code
                        Focusable(modifier = Modifier.weight(1f).height(54.dp).border(if (on) 2.dp else 1.dp, if (on) Color(0xFF22C55E) else C.Line, Rnd(14.dp)),
                            onClick = { SetFocus.last = id; SetFocus.pending = true; setAppLang(ctx, l.code); rev++ }, onFocus = { SetFocus.last = id },
                            focusRequester = setFocusFr(id), shape = Rnd(14.dp), normal = if (on) Color(0x1F22C55E) else C.Rail, focused = C.Red, zoom = 1.02f) { f ->
                            Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Flag(l.flag, 34)
                                Column(Modifier.weight(1f)) {
                                    T(l.native, 14.sp, if (f) C.OnRed else C.Text, FontWeight.Bold)
                                    T(Langs.label(l.code).lowercase(), 10.sp, if (f) C.OnRed.copy(alpha = 0.6f) else C.Muted)
                                }
                                Box(Modifier.size(20.dp).clip(CircleShape).background(if (on) Color(0xFF22C55E) else Color.Transparent)
                                    .border(1.5.dp, if (on) Color(0xFF22C55E) else if (f) C.OnRed.copy(alpha = 0.5f) else C.Muted, CircleShape), contentAlignment = Alignment.Center) {
                                    if (on) Ico(R.drawable.lc_check, 12.dp, Color.White)
                                }
                            }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val metaOpts = listOf("auto") + Langs.MEDIA.map { it.code }
                Box(Modifier.weight(1f)) {
                    SetDrop("Opisi filmova i serija", null, listOf(tr("Kao aplikacija")) + Langs.MEDIA.map { it.native },
                        metaOpts.indexOf(Prefs.metaLang).coerceAtLeast(0)) { Prefs.metaLang = metaOpts[it]; rev++ }
                }
                Box(Modifier.weight(1f)) { SetSwitch("Automatski prevedi opise", null, AutoTranslate.on) { Prefs.put("auto_translate", it) } }
            }
        }
    }
}

/* ---------------------------------------------------------------- Izgled */


/* ---------------------------------------------------------------- Plejer */

@Composable
private fun PlayerV2(openSub: (String) -> Unit) {
    val ctx = LocalContext.current
    var rev by remember { mutableIntStateOf(0) }
    val langs = listOf("bs", "hr", "sr", "en", "de", "tr", "it", "ro", "bg", "ar", "ru")
    key(rev) {
        SetPage("Plejer") {
            // how a subtitle will look, with the chosen size and style
            val sz = Prefs.str("sub_size", "1").toIntOrNull() ?: 1
            val sty = Prefs.str("sub_style", "0").toIntOrNull() ?: 0
            Box(Modifier.fillMaxWidth().height(74.dp).clip(Rnd(14.dp)).background(Brush.verticalGradient(listOf(Color(0xFF1E293B), Color(0xFF0B1020)))).border(1.dp, C.Line, Rnd(14.dp))) {
                T("Pregled titla", 10.sp, C.Muted, modifier = Modifier.padding(10.dp))
                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).then(if (sty == 2) Modifier.clip(Rnd(4.dp)).background(Color(0xCC000000)).padding(horizontal = 8.dp) else Modifier)) {
                    androidx.compose.material3.Text(tr("Ovako izgleda titl na ekranu."), style = androidx.compose.ui.text.TextStyle(fontFamily = Inter, fontSize = listOf(13, 16, 19, 22)[sz.coerceIn(0, 3)].sp,
                        color = Color.White, fontWeight = FontWeight.SemiBold,
                        shadow = if (sty == 0) androidx.compose.ui.graphics.Shadow(Color.Black, androidx.compose.ui.geometry.Offset(2f, 2f), 4f) else if (sty == 1) androidx.compose.ui.graphics.Shadow(Color.Black, androidx.compose.ui.geometry.Offset(0f, 0f), 6f) else null))
                }
            }
            SetSection("Titlovi")
            SetSwitch("Uključi titlove sami", "ugrađeni, na tvom jeziku", Prefs.autoSubs) { Prefs.autoSubs = it }
            // several languages at once: the player takes the first one the film or channel has; none ticked = provider default
            val subOn = if (!Prefs.autoSubs) emptyList() else Prefs.subLangs.split(',').filter { it.isNotBlank() && it in langs }
            SetMulti("Jezik titla", "više odjednom · ništa = od providera", langs.map { Langs.label(it) }, subOn.map { langs.indexOf(it) }.toSet(), all = false) { s2 ->
                val keep = subOn.filter { langs.indexOf(it) in s2 }; val added = s2.sorted().map { langs[it] }.filter { it !in keep }
                if (s2.isEmpty()) Prefs.autoSubs = false else { Prefs.autoSubs = true; Prefs.subLangs = (keep + added).joinToString(",") }
                rev++
            }
            SetDrop("Veličina titla", null, listOf("Mala", "Srednja", "Velika", "Vrlo velika"), Prefs.str("sub_size", "1").toIntOrNull() ?: 1, new = true) { Prefs.put("sub_size", it.toString()); rev++ }
            SetSwitch("Titlovi se pomjeraju uz listu", "kad je otvorena lista kanala ili vodič, titl ide u slobodni dio slike", Prefs.bool("sub_shift", true)) { Prefs.put("sub_shift", it); rev++ }
            SetDrop("Izgled titla", null, listOf("Sjena", "Okvir", "Tamna pozadina"), Prefs.str("sub_style", "0").toIntOrNull() ?: 0, new = true) { Prefs.put("sub_style", it.toString()); rev++ }
            SetRow("Kašnjenje titla", "u plejeru: meni › Podesi vrijeme titla · SYNC popravi cijeli fajl", tr("u plejeru"), new = true) {}
            SetSection("Zvuk")
            val audOn = if (Prefs.bool("audio_default")) emptyList() else Prefs.audioLangs.split(',').filter { it.isNotBlank() && it in langs }
            SetMulti("Jezik zvuka", "više odjednom · ništa = od providera", langs.map { Langs.label(it) }, audOn.map { langs.indexOf(it) }.toSet(), all = false) { s2 ->
                val keep = audOn.filter { langs.indexOf(it) in s2 }; val added = s2.sorted().map { langs[it] }.filter { it !in keep }
                if (s2.isEmpty()) Prefs.put("audio_default", true) else { Prefs.put("audio_default", false); Prefs.audioLangs = (keep + added).joinToString(",") }
                rev++
            }
            SetSwitch("Dolby zvuk direktno na TV (passthrough)", "ako neki kanali nemaju zvuk, ostavi isključeno", Prefs.bool("audio_passthrough", false)) { Prefs.put("audio_passthrough", it) }
            SetSwitch("Ugrađeni dekoder zvuka (FFmpeg)", "AC3, E-AC3, MP2, DTS kad ih box nema · pomaže kad kanal nema zvuka", Tech.ffmpeg, new = true) { Prefs.put("tech_ffmpeg", it); rev++ }
            SetSwitch("Pomak zvuka", "u plejeru: Više › Pomak zvuka · pamti se po kanalu", Tech.audioDelay, new = true) { Prefs.put("tech_adelay", it); rev++ }
            SetSection("Slika i dekoder")
            SetSwitch("Automatska frekvencija (AFR)", "TV prelazi na 24/25/50/60 Hz po videu · ekran može na tren pocrniti", Tech.afr, new = true) { Prefs.put("tech_afr", it); rev++ }
            SetDrop("Dekoder slike", "Softverski troši više procesora (ne za slabe boxove)", listOf("Automatski", "Hardverski", "Softverski"), Tech.decoder, new = true) { Prefs.put("tech_decoder", it.toLong()); rev++ }
            SetSection("Kanali uživo")
            SetSwitch("Rezervni stream", "kanal ne radi: isti kanal iz druge aktivne liste", Tech.backup, new = true) { Prefs.put("tech_backup", it); rev++ }
            SetSwitch("Pauza živog kanala", "pauza čuva ono što dolazi u memoriji plejera", Tech.livePause, new = true) { Prefs.put("tech_pause", it); rev++ }
            SetSwitch("Premotavanje unazad", "◀◀ na kanalima s arhivom (od providera)", Tech.rewind, new = true) { Prefs.put("tech_rewind", it); rev++ }
            SetSwitch("Arhiva za M3U liste", "kad lista ima catchup oznake", Tech.m3uCatchup, new = true) { Prefs.put("tech_m3ucatch", it); rev++ }
            SetSection("Održavanje")
            SetSwitch("Čišćenje baze", "stari program i obrisane liste se brišu sami · jednom sedmično se baza sabije", Tech.dbClean, new = true) { Prefs.put("tech_dbclean", it); rev++ }
            SetSwitch("Praćenje dugog rada", "memorija i procesor svake minute · Dijagnostika › Dugi rad · troši malo resursa", Tech.monitor, new = true) { Prefs.put("tech_monitor", it); rev++ }
            SetSection("Stream i bafer")
            SetSwitch("Automatski oporavak", "zamrznut ili prekinut stream se tiho ponovo spoji", Recover.on) { Prefs.put("recover_on", it); rev++ }
            if (Recover.on) SetDrop("Reaguj nakon", if (Recover.choice == 0) tr("Automatski") + ": " + (NetProfile.autoWait / 1000) + " s · " + NetProfile.why() else "bez slike, vremena ili podataka",
                Recover.OPTIONS, Recover.choice, new = true) { Prefs.put("recover_wait2", it.toLong()); rev++ }
            SetDrop("Bafer (video unaprijed)", if (Buffer.choice == 0) tr("Automatski") + ": " + NetProfile.autoBuffer + " MB · " + NetProfile.why() else "veći = manje zastoja, više memorije",  // i18n-ignore
                Buffer.OPTIONS, Buffer.choice, new = true, note = tr("Vrijedi od sljedećeg pokretanja kanala ili filma.")) { Prefs.put("buffer_mb", it.toLong()); rev++ }
            SetSection("Kontrole")
            SetDrop("Preskok ◀ ▶", null, listOf("10 s", "20 s", "30 s"), listOf(10, 20, 30).indexOf(Prefs.seekStep).coerceAtLeast(0)) { Prefs.seekStep = listOf(10, 20, 30)[it]; rev++ }
            SetSwitch("▲ ▼ mijenja kanal", "u TV-u, preko cijelog ekrana", Prefs.bool("zap_updown", true), new = true) { Prefs.put("zap_updown", it) }
            SetDrop("Nastavi gdje si stao", null, listOf("Pitaj", "Uvijek nastavi", "Uvijek od početka"), Prefs.str("resume_mode", "0").toIntOrNull() ?: 0, new = true) {
                Prefs.put("resume_mode", it.toString()); rev++
            }
            SetSection("Serije")
            SetSwitch("Sljedeća epizoda sama", "odbrojava 10 s na kraju", Prefs.bool("next_ep", true)) { Prefs.put("next_ep", it) }
            SetSwitch("Zamuti neodgledane epizode", "slike epizoda bez spojlera", Prefs.bool("blur_eps", true)) { Prefs.put("blur_eps", it) }
            // learned per series the first time you skip: then at the right second in every episode
            SetDrop("Preskoči prethodno", "„Previously on…“ na početku epizode, uči se za svaku seriju", listOf("Dugme", "Sam preskoči", "Isključeno"),
                    Prefs.long("skip_recap", 0).toInt().coerceIn(0, 2)) { Prefs.put("skip_recap", it.toLong()) }
                SetDrop("Preskoči uvod", "aplikacija nauči gdje je uvod u svakoj seriji", listOf("Dugme", "Sam preskoči", "Isključeno"),
                Prefs.long("skip_intro", 0).toInt()) { Prefs.put("skip_intro", it.toLong()); rev++ }
            SetSwitch("Sljedeća epizoda kad krene odjavna špica", "⏭ ili OK na kraju zapamti gdje špica počinje", Prefs.bool("skip_credits", true)) { Prefs.put("skip_credits", it) }
            SetSection("Trailer")
            // picked once here; the Trailer button then opens straight in this app
            // always the same list: Automatic, YouTube, SmartTube, TizenTube – the ones not installed are grey
            val slots = remember { TrailerApps.slots(ctx) }
            val auto = TrailerApps.chosen(ctx)
            val curPkg = Prefs.str("trailer_app")
            val sel = slots.indexOfFirst { it.second != null && it.second == curPkg } + 1
            SetDrop("Trailer otvaraj u", if (slots.all { it.second == null }) "nijedna YouTube aplikacija nije instalirana" else "bez pitanja, jednim klikom",
                listOf(tr("Automatski") + (slots.firstOrNull { it.second != null && it.second == auto }?.let { " · " + it.first } ?: "")) + slots.map { it.first },
                sel.coerceAtLeast(0), disabled = slots.indices.filter { slots[it].second == null }.map { it + 1 }.toSet()) { k ->
                Prefs.put("trailer_app", if (k == 0) "" else slots[k - 1].second ?: ""); rev++
            }
            if (SetMode.advanced) {
                SetSection("Napredno")
                val players = remember { ExtPlayer.installed(ctx) }
                SetDrop("Plejer", "za filmove i serije", listOf(tr("Ugrađeni")) + players.map { it.label },
                    if (!ExtPlayer.always) 0 else (players.indexOfFirst { it.pkg == ExtPlayer.saved } + 1).coerceAtLeast(0)) { i ->
                    if (i == 0) ExtPlayer.always = false else { ExtPlayer.saved = players[i - 1].pkg; ExtPlayer.always = true }; rev++
                }
                var langEdit by remember { mutableStateOf<String?>(null) }
                SetRow("Jezici titlova po redu", "prvi koji postoji se uključi", langList(Prefs.subLangs)) { langEdit = "sub" }
                SetRow("Jezici zvuka po redu", "prvi koji postoji se uključi", langList(Prefs.audioLangs)) { langEdit = "audio" }
                val fm = listOf("" to tr("Pitaj svaki put"), "builtin" to tr("Ugrađeni")) + FileAccess.pickers(ctx).map { it.pkg to it.label }
                SetDrop("File manager", "za titlove i backup s USB-a", fm.map { it.second }, fm.indexOfFirst { it.first == FileAccess.app }.coerceAtLeast(0)) { FileAccess.app = fm[it].first; rev++ }
                when (langEdit) {
                    "sub" -> LangOrderPicker("Jezici titla", Prefs.subLangs.split(',').filter { it.isNotBlank() }, onDismiss = { langEdit = null; SetFocus.tick++ }) { Prefs.subLangs = it.joinToString(","); langEdit = null; rev++ }
                    "audio" -> LangOrderPicker("Jezici zvuka", Prefs.audioLangs.split(',').filter { it.isNotBlank() }, onDismiss = { langEdit = null; SetFocus.tick++ }) { Prefs.audioLangs = it.joinToString(","); langEdit = null; rev++ }
                }
            }
            ResetSection("Plejer", listOf("sub_size", "sub_style", "recover_on", "recover_wait2", "buffer_mb", "zap_updown", "resume_mode", "next_ep", "blur_eps", "trailer_app", "audio_passthrough")) { rev++ }
        }
    }
}

/* ---------------------------------------------------------------- Sport */

@Composable
private fun SportChip(label: String, on: Boolean, onClick: () -> Unit) {
    val id = "sc:$label"
    Focusable(onClick = { SetFocus.last = id; SetFocus.pending = true; onClick() }, onFocus = { SetFocus.last = id }, focusRequester = setFocusFr(id),
        shape = Pill, normal = if (on) Color(0xFF16A34A) else Color.Transparent, focused = C.Red, zoom = 1.05f,
        modifier = Modifier.height(34.dp).border(1.dp, if (on) Color(0xFF16A34A) else C.Line, Pill)) { f ->
        T((if (on) "✓ " else "") + tr(label), 13.sp, if (f) C.OnRed else C.Text, FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center).padding(horizontal = 14.dp))
    }
}

@Composable
private fun CheckRow(label: String, on: Boolean, onClick: () -> Unit) {
    val id = "cr:$label"
    Focusable(onClick = { SetFocus.last = id; SetFocus.pending = true; onClick() }, onFocus = { SetFocus.last = id }, focusRequester = setFocusFr(id),
        shape = Rnd(8.dp), normal = Color.Transparent, focused = C.CardHi, zoom = 1f, modifier = Modifier.fillMaxWidth().height(30.dp)) { _ ->
        Row(Modifier.fillMaxSize().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(18.dp).clip(Rnd(5.dp)).background(if (on) Color(0xFF16A34A) else Color.Transparent)
                .border(1.5.dp, if (on) Color(0xFF16A34A) else C.Muted, Rnd(5.dp)), contentAlignment = Alignment.Center) { if (on) Ico(R.drawable.lc_check, 12.dp, Color.White) }
            T(label, 13.sp, if (on) C.Text else C.Soft, maxLines = 1)
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SportV2() {
    var rev by remember { mutableIntStateOf(0) }
    var more by remember { mutableStateOf(false) }
    var teamDlg by remember { mutableStateOf(false) }
    var leaguesDlg by remember { mutableStateOf(false) }
    var adv by remember { mutableStateOf(false) }
    val main = listOf("football" to "Fudbal", "basketball" to "Košarka", "tennis" to "Tenis", "volleyball" to "Odbojka")
    val others = (Sports.SPORTS + Sports.MORE_SPORTS).filter { o -> main.none { it.first == o.first } }
    fun changed() { Sports.lastCombined.clear(); SportCache.clear(); rev++ }
    val raw = Prefs.str("sport_leagues", Sports.BIG.joinToString(","))
    val allL = Sports.LEAGUES.map { it.abbr }
    val onL: Set<String> = when (raw) { "-" -> emptySet(); "" -> allL.toSet(); else -> raw.split(',').filter { it.isNotBlank() }.toSet() }
    fun saveL(x: Set<String>) { Prefs.put("sport_leagues", if (x.isEmpty()) "-" else if (x.size == allL.size) "" else x.joinToString(",")); changed() }
    // simple outside, complex inside (approved): one column of five rows, everything else under "Napredno"
    key(rev) {
        SetPage("Sportski centar", "Šta pratiš u Sportu") {
            val en = csv("sport_enabled", "football").toMutableSet()
            val allSports = Sports.SPORTS + Sports.MORE_SPORTS
            SetMulti("Sport", null, allSports.map { it.second }, allSports.indices.filter { allSports[it].first in en }.toSet(), none = true) { s2 ->
                Prefs.remove("sm_sports"); Prefs.put("sport_enabled", (if (s2.isEmpty()) setOf(0) else s2).sorted().joinToString(",") { allSports[it].first }); changed()
            }
            // my country also gives my national team, my region and the home league by itself
            val picked = Prefs.str("sport_country").split(',').filter { it.isNotBlank() }.toSet()
            val opts = COUNTRIES.drop(1)
            SetMulti("Moja država", "reprezentacija, regija i domaća liga idu same", opts.map { it.second }, opts.indices.filter { opts[it].first in picked }.toSet(), all = false,
                note = tr("Ništa označeno = automatski (po uređaju)")) { s2 ->
                val codes = s2.sorted().map { opts[it].first }
                Prefs.put("sport_country", codes.joinToString(","))
                if (Prefs.str("sport_nations").isBlank()) Prefs.put("sport_nations", codes.filter { it in MyTeams.NATIONS }.joinToString(","))
                MyTeams.rev++; changed()
            }
            val teams = Prefs.str("sport_teams").split(',').map { it.trim() }.filter { it.isNotBlank() }
            SetRow("Moji timovi", "uvijek na vrhu, s obavještenjima", if (teams.isEmpty()) tr("＋ Dodaj") else trf("%s tima", teams.size)) { teamDlg = true }
            val preset = when (onL) { Sports.BIG.toSet() -> 0; (Sports.BIG + Sports.REGION_LEAGUES).toSet() -> 1; allL.toSet() -> 2; else -> 3 }
            SetDrop("Takmičenja", null, listOf("Velika takmičenja", "Velika + moja regija", "Sva", "Prilagođeno…"), preset) {
                when (it) { 0 -> saveL(Sports.BIG.toSet()); 1 -> saveL((Sports.BIG + Sports.REGION_LEAGUES).toSet()); 2 -> saveL(allL.toSet()); else -> leaguesDlg = true }
            }
            SetRow("Napredno", "kontinenti, reprezentacije, osvježavanje, izvori", if (adv) "▲" else "▼", arrow = "") { adv = !adv }
            if (adv) {
                val conts = Sports.CONTINENTS
                val cOn = csv("sport_cont", "eu").map { if (it == "am") "na" else it }
                SetMulti("Kontinenti", "svjetska takmičenja uvijek", conts.map { it.second }, conts.indices.filter { conts[it].first in cOn }.toSet(), all = false) { s2 ->
                    Prefs.put("sport_cont", (if (s2.isEmpty()) setOf(0) else s2).sorted().joinToString(",") { conts[it].first }); changed()
                }
                val nOpts = COUNTRIES.drop(1).filter { it.first in MyTeams.NATIONS }
                val nOn = Prefs.str("sport_nations").split(',').filter { it.isNotBlank() }.toSet()
                SetMulti("Moje reprezentacije", "igraju prve u Sportu", nOpts.map { it.second }, nOpts.indices.filter { nOpts[it].first in nOn }.toSet(), all = false) { s2 ->
                    Prefs.put("sport_nations", s2.sorted().joinToString(",") { nOpts[it].first }); MyTeams.rev++; changed()
                }
                SetSwitch("Automatski prati", "timove i reprezentacije", MyTeams.auto) { Prefs.put("sa_auto", it); MyTeams.rev++; rev++ }
                SetSwitch("Utakmice danas na Početnoj", null, HomeRows.isOn("today")) { HomeRows.setOn("today", it); rev++ }
                SetSwitch("Pratiš · uživo na Početnoj", null, HomeRows.isOn("follow")) { HomeRows.setOn("follow", it); rev++ }
                SetDrop("Osvježavanje", "dok je Sport otvoren", listOf("Svake minute", "Svake 3 min", "Svakih 5 min"),
                    listOf(1, 3, 5).indexOf(Prefs.str("sport_refresh", "1").toIntOrNull() ?: 1).coerceAtLeast(0)) { Prefs.put("sport_refresh", listOf(1, 3, 5)[it].toString()); rev++ }
                SetDrop("Mala slika utakmice", null, listOf("Na OK", "Isključena"), if (Prefs.bool("sport_preview", true)) 0 else 1) { Prefs.put("sport_preview", it == 0); rev++ }
                val srcKeys = listOf("sport_src_rights", "sport_src_espn")
                SetMulti("Ko prenosi", "tvoji TV kanali uvijek", listOf("Prava po ligi", "ESPN"),
                    srcKeys.indices.filter { Prefs.bool(srcKeys[it], true) }.toSet(), all = false) { s2 -> srcKeys.forEachIndexed { i, k -> Prefs.put(k, i in s2) }; changed() }
                var afDlg by remember { mutableStateOf(false) }
                SetRow("API-Football ključ", "brži rezultati uživo", if (ApiFootball.on) tr("upisan") else tr("nema"), valueColor = if (ApiFootball.on) C.Green else null) { afDlg = true }
                if (afDlg) TextDialog("API-Football ključ", "ključ s dashboard.api-football.com", ApiFootball.key, onDismiss = { afDlg = false; SetFocus.tick++ }) { v ->
                    ApiFootball.key = v; afDlg = false; SportCache.clear(); rev++ }
            }
        }
    }
    // the full competition list only when you ask for it ("Prilagođeno…")
    if (leaguesDlg) Popup("Takmičenja", { leaguesDlg = false; SetFocus.tick++ }, width = 620.dp) {
        val en = csv("sport_enabled", "football")
        val groups = Sports.LEAGUE_GROUPS.filter { (_, abbrs) -> abbrs.any { a -> Sports.LEAGUES.firstOrNull { it.abbr == a }?.sport in en } }
        var cur by remember { mutableStateOf(onL) }
        groups.forEach { (title, abbrs) ->
            Kicker(tr(title), modifier = Modifier.padding(top = 6.dp))
            abbrs.forEach { a ->
                val lg = Sports.LEAGUES.firstOrNull { it.abbr == a } ?: return@forEach
                CheckRow(tr(lg.name), a in cur) { cur = if (a in cur) cur - a else cur + a }
            }
        }
        Button("Gotovo", R.drawable.lc_check, primary = true) { saveL(cur); leaguesDlg = false; SetFocus.tick++ }
    }
    if (more) Popup("Ostali sportovi", { more = false; SetFocus.tick++ }) {
        val en = csv("sport_enabled", "football").toMutableSet()
        others.forEach { (id, label) ->
            CheckRow(tr(label), id in en) { if (id in en) en.remove(id) else en.add(id); Prefs.put("sport_enabled", en.joinToString(",")); Sports.lastCombined.clear(); more = false; rev++ }
        }
    }
    if (teamDlg) FavTeamsDialog { teamDlg = false; Sports.lastCombined.clear(); rev++; SetFocus.tick++ }
}

/* ---------------------------------------------------------------- Podsjetnici */

@Composable
private fun RemindersV2(nav: Nav) {
    var rev by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf<Reminder?>(null) }
    key(rev, Reminders.rev) {
        SetPage("Podsjetnici") {
            val all = Reminders.all().filter { it.start > System.currentTimeMillis() - 3600_000L }.sortedBy { it.start }
            SetSection(trf("Nadolazeći · %s", all.size))
            if (all.isEmpty()) T("Nema podsjetnika. U TV vodiču ili Sportu: OK na emisiji › Podsjeti me.", 12.sp, C.Muted, maxLines = 2)
            all.forEach { r ->
                SetRow(r.title, java.text.SimpleDateFormat("EEE d. M. HH:mm", java.util.Locale(Look.lang)).format(java.util.Date(r.start)) + (r.channelName?.let { " · $it" } ?: ""), arrow = "›",
                    onLong = { menu = r }) { menu = r }
            }
            SetSection("Kad dođe vrijeme")
            SetDrop("Šta da uradi", null, listOf("Prikaži obavijest", "Prebaci na kanal sam"), Reminders.action) { Reminders.action = it; rev++ }
            // several times for the same show or match, e.g. 30 min (get ready) and 5 min (switch on)
            val offs = listOf(60, 30, 15, 5, 0)
            SetMulti("Podsjeti me", "više odjednom · emisije i utakmice", listOf("1 h prije", "30 min prije", "15 min prije", "5 min prije", "Na početku"),
                offs.indices.filter { offs[it] in Reminders.offsets() }.toSet(), all = false, new = true) { set ->
                Reminders.setOffsets(set.sorted().map { offs[it] }); rev++
            }
            SetSwitch("Utakmice koje pratiš", "same dobiju podsjetnik prije početka", Prefs.str("sport_team_remind", "1") != "0") { Prefs.put("sport_team_remind", if (it) "1" else "0"); rev++ }
            SetSection("Ostalo")
            SetDrop("Kad je aplikacija zatvorena", "ne radi na svim uređajima", listOf("Otvori aplikaciju", "Ništa"), if (Reminders.wake) 0 else 1, new = true) { Reminders.wake = it == 0; rev++ }
            SetDrop("Stari podsjetnici", null, listOf("Obriši nakon 1 dan", "Obriši nakon 7 dana", "Obriši odmah"),
                listOf(24, 168, 1).indexOf(Prefs.str("rem_keep", "24").toIntOrNull() ?: 24).coerceAtLeast(0), new = true) { Prefs.put("rem_keep", listOf(24, 168, 1)[it].toString()); rev++ }
        }
    }
    menu?.let { r ->
        Popup(r.title, { menu = null }) {
            if (r.channelUrl != null) OptionRow("Gledaj kanal sada", R.drawable.lc_play, color = C.Green) { menu = null; playReminder(nav, r) }
            OptionRow("Ukloni", R.drawable.lc_trash_2, color = C.Danger) { Reminders.remove(r.id); menu = null; rev++ }
            T("Promijeni vrijeme", 12.sp, C.Muted)
            OptionRow("Pomjeri 5 min ranije", R.drawable.lc_arrow_up) { Reminders.remove(r.id); Reminders.add(r.copy(start = r.start - 5 * 60_000L)); menu = null; rev++ }
            OptionRow("Pomjeri 5 min kasnije", R.drawable.lc_arrow_down) { Reminders.remove(r.id); Reminders.add(r.copy(start = r.start + 5 * 60_000L)); menu = null; rev++ }
            OptionRow("Zatvori", R.drawable.lc_x) { menu = null }
        }
    }
}

/* ---------------------------------------------------------------- Obavještenja */

/** What the app tells by itself: each kind on or off, sound and how long a notice stays. */
@Composable
private fun NotificationsV2() {
    var rev by remember { mutableIntStateOf(0) }
    key(rev) {
        SetPage("Obavještenja", "Šta ti aplikacija sama javlja (zvonce gore desno)") {
            SetSection("Javi mi")
            Notes.KINDS.forEach { (k, t) -> SetSwitch(t, null, Prefs.bool("note_" + k, true)) { Prefs.put("note_" + k, it); rev++ } }
            SetSection("Sport")
            val ks = GoalNote.KINDS
            SetMulti("Za utakmice koje pratiš", null, ks.map { it.second }, ks.indices.filter { GoalNote.on(ks[it].first) }.toSet(), all = false) { s2 ->
                ks.forEachIndexed { i, k -> Prefs.put("sa_" + k.first, i in s2) }; rev++ }
            SetSection("Izgled")
            SetDrop("Obavještenje stoji", null, listOf("10 s", "20 s", "30 s"), listOf(10, 20, 30).indexOf(Reminders.popupSec).coerceAtLeast(1)) { Reminders.popupSec = listOf(10, 20, 30)[it]; rev++ }
            SetSwitch("Zvuk", null, Prefs.bool("rem_sound", true)) { Prefs.put("rem_sound", it) }
        }
    }
}

/* ---------------------------------------------------------------- PIN */

@Composable
private fun PinV2() {
    val ctx = LocalContext.current
    var rev by remember { mutableIntStateOf(0) }
    var setup by remember { mutableStateOf(false) }
    key(rev) {
        SetPage("PIN") {
            if (Lock.isDefault()) Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(Rnd(12.dp)).background(C.Amber.copy(alpha = 0.16f))
                .border(1.dp, C.Amber.copy(alpha = 0.6f), Rnd(12.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Ico(R.drawable.lc_key_round, 18.dp, C.Amber)
                T("PIN je zadani 0000, promijeni ga", 14.sp, C.Amber, FontWeight.Bold, modifier = Modifier.weight(1f))
            }
            SetRow("PIN", if (Lock.isDefault()) "zadani PIN je 0000" else "promijeni ili postavi novi", tr(if (Lock.isDefault()) "0000" else "Postavljen ✓"),
                valueColor = if (Lock.isDefault()) C.Amber else C.Green) { setup = true }
            val pinFor = listOf("Zaključane grupe i kanali", "Postavke", "Brisanje playliste")
            val keys = listOf("pin_locked", "pin_settings", "pin_delpl")
            SetMulti("PIN traži za", "više odjednom", pinFor,
                keys.indices.filter { Prefs.bool(keys[it], it == 0) }.toSet(), all = false) { s -> keys.forEachIndexed { i, k -> Prefs.put(k, i in s) }; rev++ }
        }
    }
    if (setup) PinSetupDialog(onDismiss = { setup = false }) { setup = false; rev++; Toast.makeText(ctx, tr("PIN je postavljen"), Toast.LENGTH_SHORT).show() }
}



/* ---------------------------------------------------------------- Opće */

/** Start, exit and network – everything off or empty by default. */
@Composable
private fun GeneralV2() {
    var rev by remember { mutableIntStateOf(0) }
    var edit by remember { mutableStateOf<String?>(null) }
    var pick by remember { mutableStateOf<String?>(null) }
    // long OK on a background row: how often it checks (the fastest choices use more internet and battery)
    pick?.let { k ->
        val close = { pick = null; rev++; SetFocus.tick++ }
        Popup(tr(if (k == "sport") "Rezultati utakmica koje pratiš" else "Nove epizode serija koje gledaš"), { close() }) {
            if (k == "sport") {
                T(tr("Obično"), 12.sp, C.Muted)
                listOf(1, 2, 3, 5, 10).forEach { m -> val cur = General.sportEvery == m
                    OptionRow(everyStr(m * 60) + if (m == 1) " · " + tr("troši više") else "", if (cur) R.drawable.lc_check else null, color = if (cur) C.Green else null) { Prefs.put("bg_sport_min", m.toLong()); close() } }
                T(tr("Dok utakmica traje"), 12.sp, C.Muted)
                listOf(20, 30, 60, 120).forEach { sec -> val cur = General.sportLive == sec
                    OptionRow(everyStr(sec) + if (sec == 30) " · " + tr("troši više") else "", if (cur) R.drawable.lc_check else null, color = if (cur) C.Green else null) { Prefs.put("bg_sport_live", sec.toLong()); close() } }
            } else listOf(15, 30, 60, 180, 360, 720, 1440).forEach { m -> val cur = General.episodesEvery == m
                OptionRow(everyStr(m * 60) + if (m == 15) " · " + tr("troši više") else "", if (cur) R.drawable.lc_check else null, color = if (cur) C.Green else null) { Prefs.put("bg_ep_min", m.toLong()); close() } }
        }
    }
    key(rev) {
        SetPage("Opće", "Sve je isključeno dok ga ne uključiš.") {
            SetSection("Pokretanje")
            SetDrop("Kad se aplikacija otvori, prvo vidiš", "Nazad s tog ekrana vodi na Početnu",
                listOf("Početna", "TV", "Filmovi", "Serije", "Sports Center", "Zadnji kanal"), General.start) { Prefs.put("start_screen", it.toLong()); rev++ }
            SetSwitch("Pokreni s boxom", "Aplikacija se otvori kad se box upali", General.boot) { Prefs.put("gen_boot", it); rev++ }
            SetSection("Izlaz")
            SetSwitch("Potvrdi izlaz", "Nazad na Početnoj prvo pita", General.confirmExit) { Prefs.put("gen_exit", it); rev++ }
            SetSection("Rad u pozadini")
            T(tr("Dugi OK na redu: koliko često provjerava"), 12.sp, C.Muted, maxLines = 1)
            SetSwitch("Rezultati utakmica koje pratiš", trf("svakih %s · uživo svakih %s", everyStr(General.sportEvery * 60), everyStr(General.sportLive)),
                Prefs.bool("bg_sport", true), onLong = { pick = "sport" }) { Prefs.put("bg_sport", it); rev++ }
            SetSwitch("Nove epizode serija koje gledaš", trf("svakih %s", everyStr(General.episodesEvery * 60)),
                Prefs.bool("bg_episodes", true), onLong = { pick = "ep" }) { Prefs.put("bg_episodes", it); rev++ }
            SetSection("Štednja resursa")
            T(tr("Sve je uključeno u Lite i Full modu · isključi ono što ti ne treba"), 12.sp, C.Muted, maxLines = 1)
            Lean.ALL.forEach { (k, t, sub) -> SetSwitch(t, sub, Prefs.bool(k, true)) { Prefs.put(k, it); rev++ } }
            SetSection("Mreža")
            SetRow("User-Agent", "za providere koji puštaju samo određene aplikacije", Prefs.str("net_ua").ifBlank { tr("zadani") }) { edit = "net_ua" }
            SetRow("UDP proxy (udpxy)", "za multicast IPTV od operatera, npr. 192.168.178.1:4022", Prefs.str("net_udpxy").ifBlank { tr("isključen") }) { edit = "net_udpxy" }
        }
    }
    edit?.let { k ->
        var v by remember(k) { mutableStateOf(Prefs.str(k)) }
        Popup(tr(if (k == "net_ua") "User-Agent" else "UDP proxy (udpxy)"), { edit = null }, width = 640.dp) {  // i18n-ignore
            Field(if (k == "net_ua") "npr. VLC/3.0.20 LibVLC/3.0.20" else "adresa:port", v, { v = it }, Modifier.fillMaxWidth())  // i18n-ignore
            OptionRow("Spremi", R.drawable.lc_check, color = C.Green) { Prefs.put(k, v.trim()); edit = null; rev++ }
            OptionRow("Obriši (zadano)", R.drawable.lc_x) { Prefs.put(k, ""); edit = null; rev++ }
        }
    }
}

/* ---------------------------------------------------------------- Roditeljska kontrola */

/** Hidden categories, behind the PIN: the page opens only with the PIN. */
@Composable
private fun ParentalV2() {
    var open by remember { mutableStateOf(false) }
    var ask by remember { mutableStateOf(true) }
    var rev by remember { mutableIntStateOf(0) }
    if (!open) {
        SetPage("Roditeljska kontrola", "Meni je zaštićen PIN-om") {
            SetRow("Otključaj", "upiši PIN", "›") { ask = true }
        }
        if (ask) PinDialog("Roditeljska kontrola", tr("Unesi PIN"), onDismiss = { ask = false }) { pin -> val err = Lock.check(pin); if (err == null) { open = true; ask = false }; err }
        return
    }
    key(rev) {
        SetPage("Roditeljska kontrola", "Sakrivene kategorije se ne vide u TV-u, Filmovima, Serijama ni u Pretrazi") {
            SetSection("Zaključaj")
            SetSwitch("Zaključaj kategorije za odrasle", "XXX, Adult, 18+ … traže PIN, same od sebe", Prefs.bool("adult_lock", true)) {
                Prefs.put("adult_lock", it); if (it) Prefs.put("adult_lock_gen", Prefs.long("adult_lock_gen", 0) + 1); Repo.clear(); rev++
            }
            val lim = listOf(0, 60, 120, 180, 240)
            SetDrop("Dnevno vrijeme gledanja", "1 h, 2 h, 3 h… pa traži PIN", listOf("Bez ograničenja", "1 h", "2 h", "3 h", "4 h"),
                lim.indexOf(WatchLimit.limitMin).coerceAtLeast(0),
                note = tr("Kad vrijeme istekne, plejer stane i traži PIN. Treba postaviti PIN.")) { Prefs.put("watch_limit", lim[it].toString()); rev++ }
            SetSection("Sakrij")
            Parental.KINDS.forEach { (k, label, sub) ->
                SetSwitch(label, sub, Parental.on(k)) { Parental.set(k, it); rev++ }
            }
            SetSection("Pregled")
            SetSwitch("Prikaži sakriveno do izlaska iz aplikacije", "bez ponovnog PIN-a", Parental.unlocked) { Parental.unlocked = it; Repo.clear(); SearchIndex.reset(); rev++ }
            ResetSection("Roditeljska kontrola", Parental.KINDS.map { "par_" + it.first }) { Repo.clear(); rev++ }
        }
    }
}

/* ---------------------------------------------------------------- Profili */


/* ---------------------------------------------------------------- Način rada */

@Composable
private fun ModeV2() {
    SetPage("Način rada", "Osnovno pokazuje samo najvažnije. Napredno pokazuje sve postavke.") {
        listOf(false to ("Osnovno" to "Samo najvažnije postavke: playliste, jezik, jednostavni mod, ažuriranje"),
            true to ("Napredno" to "Sve postavke: izgled, plejer, sport, podsjetnici, backup, profili")).forEach { (adv, t) ->
            val on = SetMode.advanced == adv
            Focusable(modifier = Modifier.fillMaxWidth().height(70.dp).border(if (on) 2.dp else 1.dp, if (on) C.Text else C.Line, Rnd(14.dp)),
                onClick = { SetMode.set(adv) }, shape = Rnd(14.dp), normal = if (on) C.CardHi else C.Rail, focused = C.Red, zoom = 1.01f) { f ->
                Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Lamp(on)
                    Column(Modifier.weight(1f)) {
                        T(t.first, 16.sp, if (f) C.OnRed else C.Text, FontWeight.Bold)
                        T(t.second, 11.sp, if (f) C.OnRed.copy(alpha = 0.65f) else C.Muted, maxLines = 2)
                    }
                }
            }
        }
    }
}

/* ---------------------------------------------------------------- Ažuriranje */

@Composable
private fun VerBox(modifier: Modifier, label: String, value: String, color: Color, icon: Int, stable: Boolean = false) {
    Box(modifier.padding(top = 8.dp)) {
        Focusable(onClick = {}, shape = Rnd(16.dp), normal = color.copy(alpha = 0.14f), focused = color.copy(alpha = 0.3f), zoom = 1.01f,
            modifier = Modifier.fillMaxWidth().border(1.dp, color.copy(alpha = 0.55f), Rnd(16.dp))) { _ ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(38.dp).clip(Rnd(12.dp)).background(color), contentAlignment = Alignment.Center) { Ico(icon, 18.dp, Color(0xFF0F172A)) }
                Column(Modifier.weight(1f)) {
                    T(label, 11.sp, color, FontWeight.Bold, maxLines = 1)
                    FitText(value, 20.sp, C.Text, FontWeight.Bold)
                }
            }
        }
        // gold shield: this version is the Stable one
        if (stable) Row(Modifier.align(Alignment.TopEnd).offset(x = (-12).dp, y = (-9).dp).clip(Pill)
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFFFE08A), Color(0xFFD4A537)))).padding(start = 6.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Ico(R.drawable.lc_shield_check, 13.dp, Color(0xFF5A3D00)); T("STABLE", 10.sp, Color(0xFF3A2700), FontWeight.ExtraBold)  // i18n-ignore
        }
    }
}

@Composable
private fun UpdateV2() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var checkedAt by remember { mutableStateOf(0L) }
    var rev by remember { mutableIntStateOf(0) }
    var later by remember { mutableStateOf(false) }
    var usb by remember { mutableStateOf(false) }
    var back by remember { mutableStateOf<Release?>(null) }
    var backDone by remember { mutableStateOf(false) }
    var backErr by remember { mutableStateOf<String?>(null) }
    var showAll by remember { mutableStateOf<Release?>(null) }
    val rels by load(rev / 1000) { Updater.releases() }
    val releases = (rels as? Load.Ok)?.value ?: emptyList()
    // opening Updates checks at once and, when there is a new version, downloads and installs it in the same step (approved)
    LaunchedEffect(Unit) {
        checking = true
        try { Prefs.put("upd_skip", ""); Updater.check(ctx) } catch (_: Exception) {}
        checking = false; checkedAt = System.currentTimeMillis()
        Updater.available?.let { nu -> if (Updater.progress !in 0..101) Updater.install(ctx, nu) }
    }
    val u = Updater.available
    key(rev) {
        SetPage("Ažuriranje") {
            // ---- on top: the version you have (green) and, next to it, the new one (yellow)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VerBox(Modifier.weight(1f), "Trenutna verzija", BuildInfo.VERSION, C.Green, R.drawable.lc_check,
                    stable = Updater.stableName == BuildInfo.VERSION)
                if (u != null) VerBox(Modifier.weight(1f), "Nova verzija", u.name + (releases.firstOrNull { it.name == u.name }?.date?.let { " · $it" } ?: ""), C.Amber, R.drawable.lc_download)
            }
            val pr = Updater.progress
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // "Update now": downloads and installs at once, no extra question
                if (u != null && pr !in 0..101) Button(if (Updater.paused) "Nastavi preuzimanje" else "Ažuriraj sada", R.drawable.lc_download, primary = true) { scope.launch { Updater.install(ctx, u) } }
                // the page is not rebuilt after the check: focus stays on this button, the answer shows next to it
                Button(if (checking) "Provjeravam…" else "Provjeri novu verziju", R.drawable.lc_refresh_cw) {
                    if (!checking) { checking = true; scope.launch { Prefs.put("upd_skip", ""); Updater.check(ctx); checking = false; checkedAt = System.currentTimeMillis() } }
                }
                if (checkedAt > 0 && !checking && Updater.available == null) T("✓ Imaš najnoviju verziju", 13.sp, C.Green, FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterVertically))
            }
            if (u != null && pr !in 0..101) {
                val lines = u.lines.ifEmpty { releases.firstOrNull { it.name == u.name }?.notes?.lines()?.map { it.trim().removePrefix("-").trim() }?.filter { it.isNotBlank() }?.take(4) ?: emptyList() }
                lines.forEach { l -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { T("•", 13.sp, C.Amber, FontWeight.Bold); T(l, 13.sp, C.Text, maxLines = 2) } }
            }
            if (pr in 0..100) {
                val mb = { b: Long -> String.format("%.1f", b / 1_048_576.0) }
                val leftS = if (Updater.speed > 0 && Updater.totalBytes > 0) ((Updater.totalBytes - Updater.doneBytes) / Updater.speed).toInt() else -1
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(listOf(tr("Preuzimanje"), " · ", mb(Updater.doneBytes), " / ", mb(Updater.totalBytes), " MB · ", pr, " %").joinToString(""), 12.sp, C.Text, modifier = Modifier.weight(1f))  // i18n-ignore
                    T(mb(Updater.speed) + " MB/s" + if (leftS >= 0) " · " + trf("još %s s", leftS) else "", 12.sp, Silver.TextSoft)
                }
                Box(Modifier.fillMaxWidth().height(6.dp).clip(Pill).background(C.Line)) { Box(Modifier.fillMaxWidth(pr / 100f).fillMaxHeight().background(Silver.TextSoft)) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button("Pauza", R.drawable.lc_pause) { Updater.pause() }
                    Button("Otkaži", R.drawable.lc_x) { Updater.cancel = true }
                }
            } else if (pr == 101) T("Instaliram… Ne klikaj ponovo, Android sam završava i aplikacija se ponovo pokrene.", 12.sp, C.Text, maxLines = 2)
            Updater.signature?.let { ok ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Ico(if (ok) R.drawable.lc_shield_check else R.drawable.lc_x, 14.dp, if (ok) C.Green else C.Danger)
                    T(if (ok) "Potpis provjeren" else "Potpis ne odgovara – instalacija zaustavljena", 12.sp, if (ok) C.Green else C.Danger, FontWeight.Bold)
                }
            }
            Updater.status?.let { T(it, 12.sp, C.Amber, maxLines = 2) }
            // ---- how often
            SetSection("Provjera")
            SetDrop("Provjeravaj", "nove verzije", listOf("Pri pokretanju", "Svaki sat", "Dnevno", "Mjesečno"),
                Prefs.str("upd_every", "1").toIntOrNull() ?: 1) { Prefs.put("upd_every", it.toString()); rev++ }
            // ---- going back
            val prev = releases.filter { it.name != BuildInfo.VERSION && (it.name.split('.').map { x -> x.toIntOrNull() ?: 0 }.let { a ->
                val b = BuildInfo.VERSION.split('.').map { x -> x.toIntOrNull() ?: 0 }; (0 until 3).firstOrNull { i -> a.getOrElse(i) { 0 } != b.getOrElse(i) { 0 } }?.let { i -> a.getOrElse(i) { 0 } < b.getOrElse(i) { 0 } } ?: false }) }
            val stable = releases.firstOrNull()
            SetSection("Vrati verziju")
            val p0 = prev.firstOrNull()
            SetRow("Prethodna verzija", p0?.let { it.name + " · " + it.date + " · " + tr("drži OK: Vrati odmah") } ?: if (rels is Load.Loading) tr("Učitavam verzije…") else tr("Nema starijih verzija za vraćanje."),
                if (p0 != null) "›" else "", onLong = { p0?.let { back = it } }) { p0?.let { back = it } }
            SetRow("Zadnja Stable", stable?.let { it.name + " · " + it.date + if (it.name == BuildInfo.VERSION) " · " + tr("instalirana") else "" } ?: "",
                if (stable != null && stable.name != BuildInfo.VERSION) "›" else "") { stable?.takeIf { it.name != BuildInfo.VERSION }?.let { back = it } }
        }
    }
    if (later && u != null) Popup("Podsjeti me", { later = false }, width = 460.dp) {
        listOf(1 to "Za 1 sat", 24 to "Sutra", 72 to "Za 3 dana").forEach { (h, l) -> OptionRow(l, R.drawable.lc_clock) { Updater.snooze(h); later = false; rev++ } }
    }
    showAll?.let { r -> Popup(trf("Verzija %s", r.name), { showAll = null }) {
        T(r.date, 12.sp, C.Muted); T(r.notes.ifBlank { tr("Bez opisa.") }, 14.sp, C.Text, maxLines = 40)
    } }
    if (usb) FilePicker("Izaberi APK", setOf("apk"), onDismiss = { usb = false }) { bytes, _ -> usb = false; scope.launch { Updater.installBytes(ctx, bytes) } }
    back?.let { r ->
        Popup(trf("Vrati na %s", r.name), { back = null; backDone = false; backErr = null }) {
            if (!backDone) {
                T("Android ne dozvoljava da se aplikacija sama vrati na stariju verziju. Zato aplikacija radi ovo:", 14.sp, C.Text, maxLines = 3)
                listOf("Sprema tvoje postavke u Download/MediaCenter", "Skida stariju verziju (APK) u isti folder", "Deinstalira se",
                    "Instaliraš stari APK (LEAD Launcher ili adb)", "Pri pokretanju vratiš postavke iz backupa").forEachIndexed { k, l -> T("${k + 1}. " + tr(l), 13.sp, C.Soft, maxLines = 2) }
                backErr?.let { T(trf("Nije uspjelo: %s", it), 12.sp, C.Danger, maxLines = 2) }
                OptionRow(if (Rollback.busy) "Pripremam…" else "OK, pripremi i deinstaliraj", R.drawable.lc_download, color = C.Amber) {
                    if (!Rollback.busy) scope.launch { val err = Rollback.prepare(ctx, r); if (err == null) backDone = true else backErr = err }
                }
                OptionRow("Odustani", R.drawable.lc_x) { back = null }
            } else {
                T(trf("Spremljeno u Download/MediaCenter: MediaCenter-%s.apk i backup.", r.name), 14.sp, C.Green, maxLines = 3)
                OptionRow("Deinstaliraj aplikaciju sada", R.drawable.lc_trash_2, color = C.Danger) { Rollback.uninstall(ctx) }
                OptionRow("Kasnije", R.drawable.lc_x) { back = null; backDone = false }
            }
        }
    }
}

/* ---------------------------------------------------------------- O aplikaciji */

@Composable
private fun AboutV2(onSub: (String) -> Unit, onSection: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var dlg by remember { mutableStateOf<String?>(null) }
    val crash = remember { try { java.io.File(ctx.filesDir, "last_crash.txt").takeIf { it.exists() }?.readText() } catch (e: Exception) { null } }
    SetPage("O aplikaciji", "Media Center " + BuildInfo.VERSION) {
        val u = Updater.available
        SetRow("Verzija", if (u != null) trf("Dostupna %s", u.name) else tr("✓ Najnovija verzija"), BuildInfo.VERSION,
            valueColor = if (u != null) C.Amber else C.Green) { onSection("update") }
        SetRow("Šta je novo", trf("Verzija %s", BuildInfo.VERSION)) { WhatsNew.show = true }
        SetRow("O uređaju", tr("procesor, RAM, ekran, mreža"), android.os.Build.MODEL) { onSub("device") }
        SetRow("Ocjena uređaja", tr(if (Perf.lite) "Lite" else "Full"), "${Perf.score}/100") { dlg = "score" }  // i18n-ignore
        SetRow("Izvještaj o greškama", tr("pogledaj ili izvezi zapis"), if (crash == null) tr("Nema grešaka") else tr("Ima zapis"),
            valueColor = if (crash == null) null else C.Amber) { dlg = "crash" }
        SetRow("Privatnost", tr("sve ostaje na uređaju")) { dlg = "priv" }
        SetRow("Licence", tr("Otvoreni kod")) { dlg = "lic" }
        SetRow("Pomoć", tr("prvi koraci, daljinski, prečice")) { dlg = "help" }
    }
    when (dlg) {
        "score" -> Popup(trf("Ocjena boxa %s/100", Perf.score), { dlg = null }, width = 600.dp) {
            Perf.parts.forEach { (k, v) ->
                Row(Modifier.fillMaxWidth().clip(Rnd(10.dp)).background(C.Rail).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(k, 13.sp, C.Muted, modifier = Modifier.weight(1f)); T(v, 13.sp, C.Text, FontWeight.Bold)
                }
            }
            T("Ocjena se izmjeri jednom i ostaje ista.", 12.sp, C.Muted)
            OptionRow("Izmjeri ponovo", R.drawable.lc_refresh_cw) { Perf.remeasure(ctx); dlg = null; scope.launch { delay(50); dlg = "score" } }
        }
        "help" -> Popup("Kratko uputstvo", { dlg = null }, width = 640.dp) {
            listOf("OK otvara, dugi OK otvara meni (označi, dodaj na listu, sakrij).",
                "Nazad uvijek ide jedan korak nazad i ostaje u istoj sekciji.",
                "U plejeru: ▲ ▼ mijenja kanal, ◀ ▶ premotava, OK pauza.",
                "Brojevi na daljinskom biraju kanal; 0 vraća na prethodni.",
                "Zadani PIN je 0000 – promijeni ga u Postavkama.").forEach { T("• " + tr(it), 14.sp, C.Soft, maxLines = 3) }
        }
        "lic" -> Popup("Licence", { dlg = null }, width = 720.dp) {
            listOf("Media Center · MIT", "Jetpack Compose · Apache 2.0", "Media3 / ExoPlayer · Apache 2.0", "Coil · Apache 2.0", "OkHttp · Apache 2.0", "ZXing · Apache 2.0",
                "Lucide ikone · ISC", "Inter font · SIL Open Font License 1.1",
                "FFmpeg audio dekoder (Jellyfin media3-ffmpeg-decoder) · GPL-3.0. Izvorni kod: github.com/KarlAgathon/PRETORIAN",
                "TMDB · This product uses the TMDB API but is not endorsed or certified by TMDB.").forEach { T(it, 13.sp, C.Soft, maxLines = 3) }  // i18n-ignore
        }
        "priv" -> Popup("Privatnost", { dlg = null }) {
            T("Playliste, lozinke, PIN, favoriti i gledano čuvaju se samo na ovom uređaju. Na internet idu samo zahtjevi prema tvom provideru, slike i opisi filmova (TMDB) i provjera nove verzije.", 13.sp, C.Soft, maxLines = 8)
        }
        "crash" -> Popup("Izvještaj o greškama", { dlg = null }, width = 760.dp) {
            T((crash ?: tr("Nema grešaka")).take(1500), 11.sp, C.Soft, maxLines = 24)
            T("Sa laptopa (zadnjih 10 padova i zamrzavanja):", 12.sp, C.Muted)
            T("adb pull " + CrashLog.ADB_DIR, 13.sp, C.Text, FontWeight.Bold, maxLines = 2)  // i18n-ignore
            if (crash != null) OptionRow("Obriši izvještaj", R.drawable.lc_trash_2) { java.io.File(ctx.filesDir, "last_crash.txt").delete(); dlg = null }
        }
        else -> {}
    }
}



/** the playlist whose menu opens on the "Edit playlists" page */
object PlMenu { var open: String? = null }
/** the playlist whose groups/channels or EPG are being edited (null = all / the active one) */
object PlEdit { var id: String? = null }



/** 30 s, 5 min, 3 h, daily — for the background check intervals */
fun everyStr(sec: Int): String = when {
    sec < 60 -> trf("%s s", sec)
    sec < 3600 -> trf("%s min", sec / 60)
    sec >= 86400 -> tr("dnevno")
    else -> trf("%s h", sec / 3600)
}

/** Settings › Appearance & Language › Appearance: one design for everyone, only text size and the performance profile. */
@Composable
private fun AppearanceV2() {
    var rev by remember { mutableIntStateOf(0) }
    key(rev) {
        SetPage("Izgled", "Jedan izgled za sve. Ovdje biraš samo veličinu teksta i način rada.") {
            SetSlider("Veličina teksta", "za gledanje s daljeg kauča · 90 – 120 %", Tune.textSize, 90, 120, 5, { "$it %" }) { Tune.textSize = it; Tune.save() }
            WeatherPlaceRow()
            SetDrop("Način rada", trf("Ocjena uređaja %s/100 · %s GB RAM", Perf.score, String.format("%.1f", Perf.ramGb)),
                Perf.MODES, Perf.mode.coerceIn(0, Perf.MODES.lastIndex)) { Perf.set(it); rev++ }
            T("Lite štedi memoriju i procesor na slabijim uređajima. Izgled i funkcije ostaju isti.", 12.sp, C.Muted, maxLines = 2)
            // the mode that really runs now (Automatic picks it from RAM and the device score)
            T(trf("Sada radi: %s", if (Perf.lite) "Lite" else "Full"), 14.sp, C.Text, FontWeight.SemiBold)  // i18n-ignore
            T(when { Perf.mode == 1 -> "Izabrano ručno."; Perf.mode == 2 -> "Izabrano ručno."; Perf.lite -> "Automatski: uređaj ima manje od 3 GB RAM-a ili nižu ocjenu."; else -> "Automatski: uređaj je dovoljno jak." }, 12.sp, C.Muted, maxLines = 2)
        }
    }
}

/** Search › Settings: the real settings pages whose name or description matches (id, title, description). */
fun settingsSearch(q: String): List<Triple<String, String, String>> {
    val n = Sports.norm(q)
    return SETTINGS_LINKS.values.flatten().distinctBy { it.id }.map { Triple(it.id, tr(it.title), tr(it.sub)) }
        .filter { (_, t, sub) -> Sports.norm(t).contains(n) || Sports.norm(sub).contains(n) }
}
