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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

private data class Section(val id: String, val icon: Int, val title: String, val sub: String)

private val SECTIONS = listOf(
    Section("profile", R.drawable.lc_users, "Profili i Google Drive", "Ko gleda, prijava, sinhronizacija"),
    Section("playlists", R.drawable.lc_list, "Playliste", "Dodaj, aktiviraj, ažuriraj"),
    Section("groups", R.drawable.lc_layers, "Grupe i kanali", "Redoslijed, nazivi, sakrivanje, PIN"),
    Section("language", R.drawable.lc_languages, "Jezik", "Meniji, dugmad i poruke"),
    Section("look", R.drawable.lc_palette, "Izgled", "Tema, boja, prozirnost, veličine"),
    Section("media", R.drawable.lc_play, "Plejer", "Zvuk, titlovi, eksterni plejer"),
    Section("sport", R.drawable.lc_trophy, "Sport i AI", "Država, AI za prenose"),
    Section("reminders", R.drawable.lc_bell, "Podsjetnici", "Emisije, utakmice, obavještenja"),
    Section("parental", R.drawable.lc_shield_check, "Roditelji i jednostavni mod", "PIN, jednostavni mod"),
    Section("backup", R.drawable.lc_archive, "Backup i vraćanje", "Spasi sve postavke u fajl"),
    Section("about", R.drawable.lc_info, "O aplikaciji", "Verzija i licence"),
    Section("update", R.drawable.lc_download, "Ažuriranje", "Provjeri i instaliraj novu verziju"),
)

@Composable
fun SettingsScreen(nav: Nav) {
    if (Look.age) { SettingsScreen2(nav); return }
    var section by remember { mutableStateOf(SettingsNav.section?.also { SettingsNav.section = null } ?: SettingsNav.last ?: if (Updater.available != null) "update" else "playlists") }
    LaunchedEffect(section) { SettingsNav.last = section }
    val first = remember { FocusRequester() }
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 12.dp, top = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            T("Postavke", 26.sp, C.Text, FontWeight.Bold, modifier = Modifier.padding(start = 10.dp, bottom = 8.dp))
            SECTIONS.forEachIndexed { i, s ->
                val sel = s.id == section
                // a section opens on OK, not by just moving over it
                Focusable(modifier = Modifier.fillMaxWidth().height(50.dp), onClick = { section = s.id },
                    focusRequester = if (s.id == section) first else null, normal = if (sel) C.CardFocus else Color.Transparent, zoom = 1f) { _ ->
                    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(30.dp).clip(Rnd(9.dp)).background(if (sel) C.Red else C.Card), contentAlignment = Alignment.Center) {
                            Ico(s.icon, 16.dp, if (sel) C.OnRed else C.Text)
                        }
                        Column(Modifier.weight(1f)) {
                            T(s.title, 14.sp, C.Text, FontWeight.Medium, maxLines = 1)
                            T(s.sub, 11.sp, C.Muted, maxLines = 1)
                        }
                        if (s.id == "update" && Updater.available != null) Box(Modifier.size(18.dp).clip(CircleShape).background(C.Danger), contentAlignment = Alignment.Center) {
                            T("1", 10.sp, Color.White, FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(C.Line))
        // Back inside a page goes one step back: to this section in the list on the left, not out of Settings
        var inPage by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        BackHandler(enabled = inPage) { inPage = false; scope.launch { first.tryFocus() } }
        Box(Modifier.weight(1f).fillMaxHeight().onFocusChanged { inPage = it.hasFocus }.padding(horizontal = 30.dp, vertical = 24.dp)) {
            key(section) {
                when (section) {
                    "playlists" -> PlaylistsPage(nav)
                    "profile" -> ProfilePage()
                    "sport" -> SportPage()
                    "groups" -> GroupManager(nav)
                    "look" -> LookPage()
                    "language" -> LanguagePage()
                    "media" -> MediaPage()
                    "parental" -> ParentalPage(nav)
                    "reminders" -> RemindersPage(nav)
                    "backup" -> BackupPage(nav)
                    "update" -> UpdatePage()
                    else -> AboutPage()
                }
            }
        }
    }
    LaunchedEffect(Unit) { try { first.requestFocus() } catch (_: Exception) {} }
}

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

@Composable
private fun PlaylistsPage(nav: Nav) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf(Store.playlists(ctx)) }
    var busy by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<Playlist?>(null) }
    var del by remember { mutableStateOf<Playlist?>(null) }
    var openId by remember { mutableStateOf(PlNav.open) }
    var multi by remember { mutableStateOf(Store.multi) }
    val rowFr = remember { FocusRequester() }
    var lastId by remember { mutableStateOf<String?>(null) }
    fun refresh() { list = Store.playlists(ctx); Repo.clear() }
    LaunchedEffect(openId) { PlNav.open = openId }

    val opened = list.firstOrNull { it.id == openId }
    if (opened != null) {
        BackHandler { openId = null }
        PlaylistDetail(opened, nav, onChanged = { refresh() }, onDelete = { del = opened }, onClose = { openId = null })
    } else Page("Playliste", if (multi) "OK otvara playlistu, drži OK za brzi meni. Može biti uključeno više playlisti." else "OK otvara playlistu, drži OK za brzi meni.") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button("Dodaj playlistu", R.drawable.lc_plus, primary = true) { EditPlaylist.target = null; nav.push(Screen.AddPlaylist) }
            Button(if (busy) "Ažuriram…" else "Ažuriraj sve", R.drawable.lc_refresh_cw) {
                if (busy) return@Button
                busy = true
                Repo.dropCatalogs(); Sports.lastCombined.clear()
                scope.launch {
                    val updated = Store.playlists(ctx).map { pl ->
                        try { if (pl.type == SourceType.XTREAM) pl.copy(exp = Repo.test(pl)) else { Repo.test(pl); pl } } catch (e: Exception) { pl }
                    }
                    Store.savePlaylists(ctx, updated); list = updated
                    Repo.clear()
                    updated.filter { it.active }.forEach { pl -> try { NewContent.scan(ctx, pl, force = true) } catch (_: Exception) {} }
                    EpgSources.list().filter { it.on }.forEach { try { EpgSources.refresh(it) } catch (_: Exception) {} }
                    busy = false
                    Toast.makeText(ctx, tr("Playliste su ažurirane"), Toast.LENGTH_SHORT).show()
                }
            }
        }
        Segmented(listOf("Samo jedna aktivna", "Više aktivnih"), if (multi) 1 else 0) {
            multi = it == 1; Store.multi = multi
            if (!multi) { val keep = list.firstOrNull { p -> p.active } ?: list.firstOrNull(); if (keep != null) Store.savePlaylists(ctx, list.map { p -> p.copy(active = p.id == keep.id) }) }
            refresh()
        }
        list.forEach { pl ->
            val exp = expiryText(pl.exp)
            Focusable(
                modifier = Modifier.fillMaxWidth().height(78.dp).then(if (pl.active) Modifier.border(2.dp, C.Red, Rnd(14.dp)) else Modifier),
                onClick = { lastId = pl.id; openId = pl.id },
                onMenu = { lastId = pl.id; menu = pl },
                focusRequester = if (pl.id == lastId) rowFr else null,
                normal = if (pl.active) C.CardFocus else C.Card, zoom = 1.01f,
            ) { _ ->
                Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.width(44.dp).height(24.dp).clip(Rnd(12.dp)).background(if (pl.active) C.Red else C.CardHi)) {
                        Box(Modifier.align(if (pl.active) Alignment.CenterEnd else Alignment.CenterStart).padding(3.dp).size(18.dp)
                            .clip(CircleShape).background(if (pl.active) C.OnRed else C.Muted))
                    }
                    Column(Modifier.weight(1f)) {
                        T(pl.name, 17.sp, C.Text, FontWeight.SemiBold, maxLines = 1)
                        T(if (pl.type == SourceType.XTREAM) "Xtream Codes · ${pl.server}" else "M3U · ${pl.url}", 12.sp, C.Muted, maxLines = 1)
                    }
                    if (exp != null) {
                        val level = exp.second
                        Chip(exp.first, when (level) { 2 -> C.Danger.copy(alpha = 0.2f); 1 -> C.Amber.copy(alpha = 0.2f); else -> C.Card },
                            when (level) { 2 -> C.Danger; 1 -> C.Amber; else -> C.Soft })
                    }
                    if (pl.active) Chip(tr(if (Store.active(ctx)?.id == pl.id) "OTVORENA" else "AKTIVNA"), C.Red, C.OnRed)
                    Ico(R.drawable.lc_chevron_right, 18.dp, C.Muted)
                }
            }
        }
        T("OK na playlisti: uredi, ažuriraj, EPG, uključi. Drži OK za brzi meni.", 12.sp, C.Muted, maxLines = 2)
        EpgSourcesBlock(null)
    }
    LaunchedEffect(openId) { if (openId == null && lastId != null) rowFr.tryFocus() }
    menu?.let { pl ->
        val i = list.indexOfFirst { it.id == pl.id }
        Popup(pl.name, onDismiss = { menu = null }) {
            if (i >= 0) T("${i + 1} / ${list.size}", 13.sp, C.Muted)
            OptionRow("Otvori stranicu playliste", R.drawable.lc_chevron_right) { menu = null; openId = pl.id }
            OptionRow(if (pl.active) "Isključi" else "Uključi", R.drawable.lc_check) { Store.activate(ctx, pl.id); refresh(); menu = null }
            OptionRow("Otvori (gore u TV-u)", R.drawable.lc_chevron_down) { Store.open(ctx, pl.id); refresh(); menu = null }
            OptionRow("Pomjeri gore", R.drawable.lc_arrow_up) { Store.movePlaylist(ctx, pl.id, -1); list = Store.playlists(ctx) }
            OptionRow("Pomjeri dolje", R.drawable.lc_arrow_down) { Store.movePlaylist(ctx, pl.id, 1); list = Store.playlists(ctx) }
            OptionRow("Obriši", R.drawable.lc_trash_2, color = C.Danger) { menu = null; del = pl }
        }
    }
    del?.let { pl ->
        Popup(tr("Obrisati playlistu?") + " " + pl.name, onDismiss = { del = null }) {
            OptionRow("Da, obriši", R.drawable.lc_trash_2, color = C.Danger) {
                val rest = Store.playlists(ctx).filter { it.id != pl.id }
                Store.savePlaylists(ctx, if (rest.none { it.active } && rest.isNotEmpty()) rest.mapIndexed { i, x -> x.copy(active = i == 0) } else rest)
                del = null; openId = null; refresh()
            }
            OptionRow("Odustani", R.drawable.lc_x) { del = null }
        }
    }
}

/** One playlist: details, edit, update, its own EPG, on/off, delete. */
@Composable
private fun PlaylistDetail(pl: Playlist, nav: Nav, onChanged: () -> Unit, onDelete: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    val counts by load(pl.id, busy) {
        if (busy) null else Triple(
            try { Repo.allChannels(pl).size } catch (e: Exception) { -1 },
            if (pl.type == SourceType.XTREAM) try { Repo.catalogCount(pl, "vod") } catch (e: Throwable) { -1 } else -1,
            if (pl.type == SourceType.XTREAM) try { Repo.catalogCount(pl, "series") } catch (e: Throwable) { -1 } else -1)
    }
    val first = remember { FocusRequester() }
    Page(pl.name, if (pl.type == SourceType.XTREAM) "Xtream Codes · ${pl.server}" else "M3U · ${pl.url}") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            expiryText(pl.exp)?.let { Chip(it.first, C.Card, if (it.second > 0) C.Amber else C.Soft) }
            if (pl.active) Chip(tr(if (Store.active(ctx)?.id == pl.id) "OTVORENA" else "AKTIVNA"), C.Red, C.OnRed)
            val cs = (counts as? Load.Ok)?.value
            if (cs != null) {
                val (c, m, s) = cs
                T(listOfNotNull(if (c >= 0) trf("%s kanala", c) else null, if (m >= 0) trf("Filmova: %s", m) else null,
                    if (s >= 0) trf("Serija: %s", s) else null).joinToString(" · "), 13.sp, C.Muted, maxLines = 1)
            } else T("Učitavam…", 13.sp, C.Muted)
        }
        OptionRow("Uredi playlistu", R.drawable.lc_pencil, sub = if (pl.type == SourceType.XTREAM) tr("Naziv, server, korisničko ime, lozinka") else tr("Naziv, M3U link"),
            focusRequester = first) { EditPlaylist.target = pl; nav.push(Screen.AddPlaylist) }
        OptionRow(if (busy) "Ažuriram…" else "Ažuriraj ovu playlistu", R.drawable.lc_refresh_cw, sub = info ?: tr("Kanali, filmovi, serije i datum isteka")) {
            if (busy) return@OptionRow
            busy = true; info = null
            scope.launch {
                try {
                    Repo.dropCatalogs(); Repo.clear()
                    val e = Repo.test(pl)
                    if (pl.type == SourceType.XTREAM) Store.savePlaylists(ctx, Store.playlists(ctx).map { if (it.id == pl.id) it.copy(exp = e) else it })
                    try { NewContent.scan(ctx, pl, force = true) } catch (_: Exception) {}
                    info = tr("Ažurirano ✓")
                } catch (ex: Exception) { info = trf("Nije uspjelo: %s", ex.message ?: ex.javaClass.simpleName) }
                busy = false; onChanged()
            }
        }
        OptionRow(if (pl.active) "Isključi playlistu" else "Uključi playlistu", R.drawable.lc_check, color = if (pl.active) null else C.Green) {
            Store.activate(ctx, pl.id); onChanged()
        }
        if (pl.active) OptionRow("Otvori (gore u TV-u)", R.drawable.lc_chevron_down) { Store.open(ctx, pl.id); onChanged() }
        EpgSourcesBlock(pl)
        OptionRow("Obriši playlistu", R.drawable.lc_trash_2, color = C.Danger) { onDelete() }
        OptionRow("Nazad na playliste", R.drawable.lc_arrow_left) { onClose() }
    }
    LaunchedEffect(pl.id) { first.tryFocus() }
}

/** EPG sources: for every playlist ([pl] null) or only for one playlist. */
@Composable
private fun EpgSourcesBlock(pl: Playlist?) {
    val scope = rememberCoroutineScope()
    var add by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<EpgSources.Src?>(null) }
    var menu by remember { mutableStateOf<EpgSources.Src?>(null) }
    var updating by remember { mutableStateOf(false) }
    val list = remember(EpgSources.rev) { EpgSources.list().filter { it.pid == pl?.id } }
    Label(if (pl == null) tr("EPG izvori (za sve playliste)") else tr("EPG samo za ovu playlistu"))
    T("XMLTV link (.xml ili .xml.gz). Koristi se kad provider za kanal nema program.", 12.sp, C.Muted, maxLines = 2)
    list.forEach { s ->
        val status = when {
            s.error != null -> trf("Greška: %s", s.error)
            s.channels >= 0 -> trf("%s kanala · ažurirano %s", s.channels, java.text.SimpleDateFormat("d. M. HH:mm", java.util.Locale.getDefault()).format(java.util.Date(s.at)))
            else -> tr("Još nije provjeren")
        }
        OptionRow(s.name, if (s.on) R.drawable.lc_check else R.drawable.lc_x, sub = status, value = tr(if (s.on) "● Aktivan" else "○ Isključen"),
            color = if (s.error != null) C.Danger else if (s.on) null else C.Muted, valueColor = if (s.on) C.Green else C.Muted) { menu = s }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button("Dodaj EPG", R.drawable.lc_plus) { add = true }
        if (list.isNotEmpty()) Button(if (updating) "Ažuriram…" else "Ažuriraj sve EPG-ove", R.drawable.lc_refresh_cw) {
            if (updating) return@Button
            updating = true
            scope.launch { list.forEach { try { EpgSources.refresh(it) } catch (_: Exception) {} }; Repo.clear(); updating = false }
        }
    }
    if (add) NameLinkDialog("Dodaj EPG", onDismiss = { add = false }) { n, v ->
        add = false
        if (v.isNotBlank()) { val s = EpgSources.add(v, pl?.id, n); scope.launch { EpgSources.refresh(s); Repo.clear() } }
    }
    edit?.let { s ->
        NameLinkDialog("Uredi EPG", s.name, s.url, onDismiss = { edit = null }) { n, v ->
            edit = null
            if (v.isNotBlank()) {
                val changed = v != s.url
                val e = s.copy(url = v, name = n.ifBlank { EpgSources.nameOf(v) }, channels = if (changed) -1 else s.channels, error = if (changed) null else s.error)
                EpgSources.update(e); if (changed) scope.launch { EpgSources.refresh(e); Repo.clear() }
            }
        }
    }
    menu?.let { s ->
        Popup(s.name, { menu = null }) {
            T(s.url, 12.sp, C.Muted, maxLines = 3)
            OptionRow("Uredi", R.drawable.lc_pencil) { menu = null; edit = s }
            OptionRow("Ažuriraj", R.drawable.lc_refresh_cw) { menu = null; scope.launch { EpgSources.refresh(s); Repo.clear() } }
            OptionRow(if (s.on) "Isključi" else "Uključi", R.drawable.lc_check) { EpgSources.update(s.copy(on = !s.on)); Repo.clear(); menu = null }
            OptionRow("Pomjeri gore", R.drawable.lc_arrow_up) { EpgSources.move(s, -1) }
            OptionRow("Pomjeri dolje", R.drawable.lc_arrow_down) { EpgSources.move(s, 1) }
            OptionRow("Obriši", R.drawable.lc_trash_2, color = C.Danger) { EpgSources.delete(s); Repo.clear(); menu = null }
        }
    }
}

@Composable
fun AddPlaylistScreen(nav: Nav) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val old = remember { EditPlaylist.target }
    if (Look.age && old == null && Store.playlists(ctx).isEmpty() && Welcome.tab < 0) { WelcomeScreen(nav); return }
    var tab by remember { mutableIntStateOf(if (old?.type == SourceType.M3U || (old == null && Welcome.tab == 1)) 1 else 0) }
    var name by remember { mutableStateOf(old?.name ?: "") }
    var server by remember { mutableStateOf(old?.server ?: "") }
    var user by remember { mutableStateOf(old?.user ?: "") }
    var pass by remember { mutableStateOf(old?.pass ?: "") }
    var url by remember { mutableStateOf(old?.url ?: "") }
    var live by remember { mutableStateOf(old?.live ?: true) }
    var vod by remember { mutableStateOf(old?.vod ?: true) }
    var series by remember { mutableStateOf(old?.series ?: true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        Header(if (old != null) "Uredi playlistu" else "Dodaj playlistu")
        Column(Modifier.fillMaxSize().padding(horizontal = 36.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Segmented(listOf("Xtream Codes", "M3U playlista", "Stalker portal"), tab) {
                if (it == 2) Toast.makeText(ctx, tr("Stalker portal dolazi u sljedećoj verziji"), Toast.LENGTH_SHORT).show() else tab = it
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Field("Naziv playliste", name, { name = it }, Modifier.weight(1f))
                if (tab == 0) Field("Server adresa (http://server:port)", server, { server = it }, Modifier.weight(1f), KeyboardType.Uri)
                else Field("M3U link", url, { url = it }, Modifier.weight(1f), KeyboardType.Uri)
            }
            if (tab == 0) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Field("Korisničko ime", user, { user = it }, Modifier.weight(1f))
                Field("Lozinka", pass, { pass = it }, Modifier.weight(1f), password = true)
            }
            if (tab == 0) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { ToggleRow("TV kanali", live) { live = it } }
                Box(Modifier.weight(1f)) { ToggleRow("Filmovi", vod) { vod = it } }
                Box(Modifier.weight(1f)) { ToggleRow("Serije", series) { series = it } }
            }
            T("Dugačke linkove i lozinke možeš kucati s telefona: Google TV aplikacija › Daljinski › ikona tastature.", 12.sp, C.Muted, maxLines = 2)
            error?.let { T(it, 14.sp, C.Danger, maxLines = 3) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(if (busy) "Provjeravam…" else "Spremi i učitaj", R.drawable.lc_check, primary = true) {
                    if (busy) return@Button
                    val base = Playlist(
                        id = old?.id ?: System.currentTimeMillis().toString(),
                        name = name.trim().ifBlank { if (tab == 0) "Xtream" else "M3U" },
                        type = if (tab == 0) SourceType.XTREAM else SourceType.M3U,
                        server = server.trim(), user = user.trim(), pass = pass.trim(), url = url.trim(), active = old?.active ?: true,
                        live = live, vod = vod, series = series,
                    )
                    if (tab == 0 && (base.server.isBlank() || base.user.isBlank() || base.pass.isBlank())) { error = "Unesi server, korisničko ime i lozinku"; return@Button }
                    if (tab == 1 && base.url.isBlank()) { error = "Unesi M3U link"; return@Button }
                    busy = true; error = null
                    scope.launch {
                        try {
                            val info = Repo.test(base)
                            val pl = if (base.type == SourceType.XTREAM) base.copy(exp = info) else base
                            if (old != null) {
                                Store.savePlaylists(ctx, Store.playlists(ctx).map { if (it.id == old.id) pl else it })
                                Toast.makeText(ctx, tr("Spremljeno"), Toast.LENGTH_SHORT).show()
                                EditPlaylist.target = null; Repo.clear(); Repo.dropCatalogs(); nav.playlistsChanged(); nav.back()
                            } else {
                                Store.savePlaylists(ctx, Store.playlists(ctx).map { it.copy(active = false) } + pl)
                                Toast.makeText(ctx, if (pl.type == SourceType.XTREAM) tr("Povezano ✓") else trf("Učitano: %s", info), Toast.LENGTH_LONG).show()
                                nav.playlistsChanged(); nav.root(Screen.Home)
                            }
                        } catch (e: Exception) {
                            error = trf("Nije uspjelo: %s", e.message ?: e.javaClass.simpleName)
                        } finally { busy = false }
                    }
                }
                if (Store.playlists(ctx).isNotEmpty()) Button("Odustani", R.drawable.lc_x) { EditPlaylist.target = null; nav.back() }
                else if (Look.age) Button("Nazad", R.drawable.lc_x) { Welcome.tab = -1 }
            }
        }
    }
}

/* ---------------------------------------------------------------- appearance */

@Composable
private fun LookPage() {
    val ctx = LocalContext.current
    fun save() = Look.save(ctx)
    val scope = rememberCoroutineScope()
    var remote by remember { mutableStateOf<List<IconSets.Remote>?>(null) }
    var dlBusy by remember { mutableStateOf<String?>(null) }
    var rev by remember { mutableIntStateOf(0) }
    Page("Izgled", "Promjene se vide odmah.") {
        Label("Kako se otvara TV")
        var tvView by remember { mutableStateOf(Prefs.str("tv_view", "list")) }
        Segmented(listOf("Lista kanala", "TV vodič (mreža)"), if (tvView == "guide") 1 else 0) {
            tvView = if (it == 1) "guide" else "list"; Prefs.put("tv_view", tvView)
        }
        T(if (tvView == "guide") "TV vodič: kanali odozgo prema dolje, emisije vodoravno po vremenu (kao TiviMate)."
          else "Lista kanala: grupe lijevo, kanali u sredini, mali pregled slike i program desno.", 12.sp, C.Muted, maxLines = 2)
        Label("Oblik ivica")
        Segmented(listOf("Zaobljene", "Blago zaobljene", "Oštre"), Look.corner) { Look.corner = it; save() }
        Label("Set ikona")
        androidx.compose.runtime.key(rev) {
            IconSets.BUILTIN.forEach { set ->
                IconSetRow(set.name, set.about, Look.iconSet == set.id, previewSet = set.id) { Look.iconSet = set.id; Look.iconStyle = if (set.styles.size > 1) 1.coerceAtMost(set.styles.lastIndex) else 0; save() }
            }
            IconSets.installed(ctx).forEach { (id, name) ->
                IconSetRow(name, tr("Preuzeti set"), Look.iconSet == id, previewSet = id) { Look.iconSet = id; save() }
            }
        }
        IconSets.BUILTIN.firstOrNull { it.id == Look.iconSet }?.let { set ->
            if (set.styles.size > 1) Segmented(set.styles.map { it.label }, Look.iconStyle.coerceIn(0, set.styles.lastIndex)) { Look.iconStyle = it; save() }
        }
        OptionRow("Preuzmi još ikona", R.drawable.lc_download, sub = "Novi setovi s PRETORIAN GitHub stranice", value = dlBusy) {
            scope.launch { remote = try { IconSets.remote() } catch (e: Exception) { Toast.makeText(ctx, tr("Lista setova se nije učitala"), Toast.LENGTH_LONG).show(); null } }
        }
        OptionRow("Veći tekst", R.drawable.lc_type, sub = "za gledanje s daljeg kauča", value = tr(if (Look.bigText) "Uključeno" else "Isključeno") + "  ▼") {
            Look.bigText = !Look.bigText; save()
        }
        var perfMenu by remember { mutableStateOf(false) }
        OptionRow("Mod programa", R.drawable.lc_gauge2, sub = trf("Ovaj uređaj: %s GB RAM · %s", String.format("%.1f", Perf.ramGb), tr(if (Perf.weak) "slabiji" else "jak")),
            value = tr(Perf.MODES[Perf.mode]) + (if (Perf.mode == 0) " (" + tr(if (Perf.weak) "Lite" else "Full") + ")" else "") + "  ▼") { perfMenu = true }
        if (perfMenu) Popup("Mod programa", { perfMenu = false }) {
            T("Lite: bez velikih slika u pozadini i bez prelaza, manje slike. Za slabije uređaje. Full: puni izgled sa slikama.", 14.sp, C.Soft, maxLines = 4)
            Perf.MODES.forEachIndexed { i, name ->
                OptionRow(name, if (i == Perf.mode) R.drawable.lc_check else R.drawable.lc_gauge2, color = if (i == Perf.mode) C.Green else null,
                    sub = if (i == 0) trf("prepoznaje uređaj: sada %s", tr(if (Perf.weak) "Lite" else "Full")) else null) { Perf.set(i); perfMenu = false }
            }
        }
        var themeMenu by remember { mutableStateOf(false) }
        OptionRow("Tema", R.drawable.lc_palette, value = tr(Look.THEMES.firstOrNull { it.first == Look.theme }?.second ?: "Crno-bijela") + "  ▼") { themeMenu = true }
        if (themeMenu) Popup("Tema", { themeMenu = false }) {
            Look.THEMES.forEach { (id, name) ->
                OptionRow(name, if (id == Look.theme) R.drawable.lc_check else R.drawable.lc_palette, color = if (id == Look.theme) C.Green else null) {
                    Look.theme = id; save(); themeMenu = false
                }
            }
        }
        Label("Akcentna boja")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Look.ACCENTS.forEachIndexed { i, pair ->
                Focusable(onClick = { Look.accent = i; save() }, shape = CircleShape, normal = Color.Transparent, focused = C.CardHi,
                    modifier = Modifier.size(52.dp), zoom = 1.1f) { _ ->
                    Box(Modifier.align(Alignment.Center).size(36.dp).clip(CircleShape).background(pair.second)
                        .border(3.dp, if (Look.accent == i) C.Text else Color.Transparent, CircleShape))
                }
            }
        }
        T(Look.ACCENTS[Look.accent].first, 12.sp, C.Muted)
        Label("Nijansa boje")
        Segmented(listOf("Tamnija", "Malo tamnija", "Normalna", "Malo svjetlija", "Svjetlija"), Look.shade + 2) { Look.shade = it - 2; save() }
        Label(trf("Prozirnost panela: %s %%", Look.opacity))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Button("− 5 %", R.drawable.lc_minus) { Look.opacity = (Look.opacity - 5).coerceAtLeast(20); save() }
            Box(Modifier.width(260.dp).height(10.dp).clip(Rnd(5.dp)).background(C.CardHi)) {
                Box(Modifier.fillMaxWidth(Look.opacity / 100f).height(10.dp).background(C.Red))
            }
            Button("+ 5 %", R.drawable.lc_plus) { Look.opacity = (Look.opacity + 5).coerceAtMost(100); save() }
        }
        T("Najviše se vidi u temi Glass.", 12.sp, C.Muted)
        Label("Pločice na Početnoj")
        Segmented(listOf("Male", "Srednje", "Velike"), Look.tileSize) { Look.tileSize = it; save() }
        Segmented(listOf("Fluent", "Moderne", "Puna boja", "Glass", "Obris"), listOf(4, 3, 0, 1, 2).indexOf(Look.tileStyle).coerceAtLeast(0)) { Look.tileStyle = listOf(4, 3, 0, 1, 2)[it]; save() }
        Label("Posteri filmova i serija")
        Segmented(listOf("Mali", "Srednji", "Veliki"), Look.posterSize) { Look.posterSize = it; save() }
        Label("Meniji i info traka preko slike")
        Segmented(listOf("Stakleno (prozirno)", "Crno"), Look.overlay) { Look.overlay = it; save() }
        Label(tr("Providnost menija") + ": ${100 - Look.menuOpacity} %")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Button("− 5 %", R.drawable.lc_minus) { Look.menuOpacity = (Look.menuOpacity + 5).coerceAtMost(100); save() }
            Box(Modifier.width(260.dp).height(10.dp).clip(Rnd(5.dp)).background(C.CardHi)) {
                Box(Modifier.fillMaxWidth((100 - Look.menuOpacity) / 40f).height(10.dp).background(C.Red))
            }
            Button("+ 5 %", R.drawable.lc_plus) { Look.menuOpacity = (Look.menuOpacity - 5).coerceAtLeast(60); save() }
        }
        T("0 % = puni meni, ništa se ne vidi kroz njega. Najviše 40 %.", 12.sp, C.Muted)
        Label("Veličina redova (kanali i grupe)")
        Segmented(listOf("Mala", "Srednja", "Velika"), Look.rowSize) { Look.rowSize = it; save() }
        ToggleRow("Okvir oko grupa", Look.groupFrame, R.drawable.lc_square, "Tanki okvir u akcentnoj boji, otvorena grupa posvijetli") { Look.groupFrame = it; save() }
        Label("Rezolucija u uglu plejera")
        Segmented(listOf("Isključeno", "Pri pokretanju", "Uvijek"), Look.resBadge) { Look.resBadge = it; save() }
        Label("Kanali")
        Label("Pregled kanala (mala slika)")
        Segmented(listOf("Isključen", "Na OK", "Automatski"), Look.previewMode) { Look.previewMode = it; Look.preview = it > 0; save() }
        T(when (Look.previewMode) {
            0 -> "Nema male slike. OK na kanalu ga odmah pušta preko cijelog ekrana."
            1 -> "U TV vodiču: OK na emisiji › Pregled u malom prozoru. Dok listaš, ništa se ne pušta."
            else -> "Mala slika se pušta sama kad staneš na kanal."
        }, 12.sp, C.Muted, maxLines = 2)
        ToggleRow("Najgledaniji na vrhu grupe", Look.mostWatched, R.drawable.lc_arrow_up, "Kanali koje najčešće gledaš sami idu gore (kad nema ručnog redoslijeda)") {
            Look.mostWatched = it; save()
        }
    }
    remote?.let { list ->
        Popup("Preuzmi ikone", onDismiss = { remote = null }) {
            if (list.isEmpty()) T("Trenutno nema novih setova.", 14.sp, C.Muted)
            list.forEach { r ->
                OptionRow(r.name, R.drawable.lc_download, sub = r.about, value = if (IconSets.installed(ctx).any { it.first == r.id }) tr("Preuzeto") else null) {
                    dlBusy = tr("Preuzimam…"); remote = null
                    scope.launch {
                        try { IconSets.download(ctx, r); Look.iconSet = r.id; save(); Toast.makeText(ctx, trf("Set %s je spreman", r.name), Toast.LENGTH_SHORT).show() }
                        catch (e: Exception) { Toast.makeText(ctx, tr("Preuzimanje nije uspjelo"), Toast.LENGTH_LONG).show() }
                        dlBusy = null; rev++
                    }
                }
            }
        }
    }
}

/* ---------------------------------------------------------------- movies & subtitles */

@Composable
private fun MediaPage() {
    val scope = rememberCoroutineScope()
    var edit by remember { mutableStateOf<String?>(null) }
    var tmdbStatus by remember { mutableStateOf<String?>(null) }
    var rev by remember { mutableIntStateOf(0) }
    fun masked(v: String) = if (v.isBlank()) tr("Nije upisan") else "••••" + v.takeLast(4)
    key(rev) {
        Page("Plejer", "Zvuk, titlovi, premotavanje, eksterni plejer. Podaci o filmovima i titlovi s interneta rade bez ključeva.") {
            Label("Titlovi")
            OptionRow("Jezici titla", R.drawable.lc_captions, value = langList(Prefs.subLangs)) { edit = "sublangs" }
            ToggleRow("Automatski uključi titl", Prefs.autoSubs, R.drawable.lc_captions,
                "Ugrađeni titl na tvom jeziku se sam uključi kad film krene") { Prefs.autoSubs = it; rev++ }
            Label("Zvuk i premotavanje")
            OptionRow("Jezici zvuka", R.drawable.lc_volume, value = langList(Prefs.audioLangs)) { edit = "audiolangs" }
            T("Premotavanje lijevo/desno", 14.sp, C.Text, FontWeight.Medium)
            Segmented(listOf("10 s", "20 s", "30 s"), listOf(10, 20, 30).indexOf(Prefs.seekStep).coerceAtLeast(0)) {
                Prefs.seekStep = listOf(10, 20, 30)[it]; rev++
            }
            Label("Eksterni player i fajlovi")
            val ctx2 = LocalContext.current
            val players = remember { ExtPlayer.installed(ctx2) }
            OptionRow("Eksterni player", R.drawable.lc_external_link,
                value = players.firstOrNull { it.pkg == ExtPlayer.saved }?.label ?: tr("Pitaj svaki put")) { edit = "ext" }
            ToggleRow("Filmovi i serije uvijek u eksternom playeru", ExtPlayer.always, R.drawable.lc_play, "Pokreni otvara izabrani player umjesto ugrađenog") {
                ExtPlayer.always = it; rev++
            }
            val fm = remember { FileAccess.pickers(ctx2) }
            OptionRow("File manager", R.drawable.lc_folder_open, value = when (FileAccess.app) {
                "" -> tr("Pitaj svaki put"); "builtin" -> tr("PRETORIAN (ugrađeni)"); else -> fm.firstOrNull { it.pkg == FileAccess.app }?.label ?: FileAccess.app }) { edit = "fm" }
        }
    }
    val e = edit
    val ctx = LocalContext.current
    when (e) {
        null -> {}
        "ext" -> Popup("Eksterni player", onDismiss = { edit = null }) {
            OptionRow("Pitaj svaki put", R.drawable.lc_list, selected = ExtPlayer.saved.isBlank()) { ExtPlayer.saved = ""; edit = null; rev++ }
            ExtPlayer.installed(ctx).forEach { a -> OptionRow(a.label, R.drawable.lc_play, selected = ExtPlayer.saved == a.pkg) { ExtPlayer.saved = a.pkg; edit = null; rev++ } }
        }
        "fm" -> Popup("File manager", onDismiss = { edit = null }) {
            OptionRow("Pitaj svaki put", R.drawable.lc_list, selected = FileAccess.app.isBlank()) { FileAccess.app = ""; edit = null; rev++ }
            OptionRow("PRETORIAN (ugrađeni)", R.drawable.lc_folder_open, selected = FileAccess.app == "builtin") { FileAccess.app = "builtin"; edit = null; rev++ }
            FileAccess.pickers(ctx).forEach { a -> OptionRow(a.label, R.drawable.lc_folder, selected = FileAccess.app == a.pkg) { FileAccess.app = a.pkg; edit = null; rev++ } }
        }
        "metalang" -> LangPicker("Jezik opisa filmova", Prefs.metaLang, Langs.MEDIA, auto = true, onDismiss = { edit = null }) {
            Prefs.metaLang = it; edit = null; rev++; Toast.makeText(ctx, tr("Spremljeno"), Toast.LENGTH_SHORT).show()
        }
        "sublangs" -> LangOrderPicker("Jezici titla", Prefs.subLangs.split(',').filter { it.isNotBlank() }, onDismiss = { edit = null }) {
            if (it.isNotEmpty()) Prefs.subLangs = it.joinToString(","); edit = null; rev++; Toast.makeText(ctx, tr("Spremljeno"), Toast.LENGTH_SHORT).show()
        }
        "audiolangs" -> LangOrderPicker("Jezici zvuka", Prefs.audioLangs.split(',').filter { it.isNotBlank() }, onDismiss = { edit = null }) {
            if (it.isNotEmpty()) Prefs.audioLangs = it.joinToString(","); edit = null; rev++; Toast.makeText(ctx, tr("Spremljeno"), Toast.LENGTH_SHORT).show()
        }
        else -> {
            val pair = when (e) {
                "tmdb" -> "TMDB API ključ" to Prefs.tmdbKey
                "omdb" -> "OMDb API ključ" to Prefs.omdbKey
                "os" -> "OpenSubtitles API ključ" to Prefs.osKey
                "osuser" -> "OpenSubtitles korisničko ime" to Prefs.osUser
                "ospass" -> "OpenSubtitles lozinka" to ""
                else -> "SubDL API ključ" to Prefs.subdlKey
            }
            TextDialog(pair.first, pair.first, pair.second, onDismiss = { edit = null }) { v ->
                when (e) {
                    "tmdb" -> Prefs.tmdbKey = v
                    "omdb" -> Prefs.omdbKey = v
                    "os" -> Prefs.osKey = v
                    "osuser" -> Prefs.osUser = v
                    "ospass" -> Prefs.osPass = v
                    else -> Prefs.subdlKey = v
                }
                edit = null; rev++
                Toast.makeText(ctx, tr("Spremljeno"), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/** "Bosanski, Hrvatski, Engleski" from "bs,hr,en" */
fun langList(codes: String): String = codes.split(',').filter { it.isNotBlank() }.joinToString(", ") { Langs.label(it) }

/* ---------------------------------------------------------------- language */

@Composable
private fun LanguagePage() {
    val ctx = LocalContext.current
    var pick by remember { mutableStateOf(false) }
    var pickMeta by remember { mutableStateOf(false) }
    var metaLang by remember { mutableStateOf(Prefs.metaLang) }
    Page("Jezik") {
        val cur = Langs.APP.firstOrNull { it.code == Look.lang } ?: Langs.APP[0]
        T("Jezik aplikacije", 14.sp, C.Muted, FontWeight.SemiBold)
        Focusable(modifier = Modifier.fillMaxWidth().height(72.dp), onClick = { pick = true }, normal = C.Card, zoom = 1.01f) { _ ->
            Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Flag(cur.flag, 42)
                Column(Modifier.weight(1f)) {
                    T(cur.native, 18.sp, C.Text, FontWeight.SemiBold)
                    T("Meniji, dugmad i poruke", 12.sp, C.Muted)
                }
                Ico(R.drawable.lc_languages, 20.dp, C.Soft)
            }
        }
        // all languages right on the page as well: one click and done
        Langs.APP.forEach { l ->
            Focusable(modifier = Modifier.fillMaxWidth().height(52.dp), onClick = { setAppLang(ctx, l.code) },
                normal = if (l.code == Look.lang) C.CardFocus else C.Card, zoom = 1.01f) { _ ->
                Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Flag(l.flag, 32)
                    T(l.native, 15.sp, C.Text, FontWeight.Medium, modifier = Modifier.weight(1f))
                    T(Langs.label(l.code), 13.sp, C.Muted)
                    if (l.code == Look.lang) Ico(R.drawable.lc_check, 18.dp, C.Red)
                }
            }
        }
        T("Opisi filmova i serija", 14.sp, C.Muted, FontWeight.SemiBold)
        var autoTr by remember { mutableStateOf(AutoTranslate.on) }
        ToggleRow("Automatski prevedi opise", autoTr, R.drawable.lc_globe, "Opis, žanr i opis epizode prevode se na jezik opisa") { autoTr = it; Prefs.put("auto_translate", it) }
        OptionRow("Jezik opisa filmova", R.drawable.lc_globe, "Bez prijevoda prikazuje se opis kakav šalje provider",
            value = if (metaLang == "auto") tr("Kao jezik aplikacije") else Langs.label(metaLang)) { pickMeta = true }
    }
    if (pickMeta) LangPicker("Jezik opisa filmova", metaLang, Langs.MEDIA, auto = true, onDismiss = { pickMeta = false }) {
        Prefs.metaLang = it; metaLang = it; pickMeta = false
    }
    if (pick) LangPicker("Jezik aplikacije", Look.lang, onDismiss = { pick = false }) { setAppLang(ctx, it); pick = false }
}

fun setAppLang(ctx: android.content.Context, code: String) {
    Look.lang = code; Look.save(ctx)
    val l = Langs.of(code)
    Toast.makeText(ctx, tr("Jezik promijenjen") + ": " + (l?.native ?: code), Toast.LENGTH_SHORT).show()
}

/* ---------------------------------------------------------------- reminders */

@Composable
private fun RemindersPage(nav: Nav) {
    var rev by remember { mutableIntStateOf(0) }
    key(rev, Reminders.rev) {
        Page("Podsjetnici", "U TV vodiču ili Sports Centeru: OK na emisiji ili utakmici koja još nije počela › Podsjeti me.") {
            Label("Podsjeti prije početka (minuta)")
            Segmented(listOf("0", "1", "5", "10", "15"), listOf(0, 1, 5, 10, 15).indexOf(Reminders.before).coerceAtLeast(0)) { Reminders.before = listOf(0, 1, 5, 10, 15)[it]; rev++ }
            Label("Koliko dugo poruka stoji (sekundi)")
            Segmented(listOf("5", "10", "20", "30"), listOf(5, 10, 20, 30).indexOf(Reminders.popupSec).coerceAtLeast(0)) { Reminders.popupSec = listOf(5, 10, 20, 30)[it]; rev++ }
            Label("Kad dođe vrijeme")
            Segmented(listOf("Samo podsjeti", "Prebaci na kanal"), Reminders.action) { Reminders.action = it; rev++ }
            ToggleRow("Probudi box iz mirovanja", Reminders.wake, R.drawable.lc_alarm_clock, "Ne radi na svim uređajima. Ako je box potpuno ugašen, podsjetnik ne može doći.") { Reminders.wake = it; rev++ }
            Label("Tvoji podsjetnici")
            val all = Reminders.all().filter { it.start > System.currentTimeMillis() - 3600_000L }.sortedBy { it.start }
            if (all.isEmpty()) T("Nema podsjetnika.", 13.sp, C.Muted)
            all.forEach { r ->
                OptionRow(r.title, R.drawable.lc_bell, sub = java.text.SimpleDateFormat("EEE d. M. HH:mm", java.util.Locale(Look.lang)).format(java.util.Date(r.start)) + (r.channelName?.let { " · $it" } ?: ""),
                    value = tr("Obriši")) { Reminders.remove(r.id); rev++ }
            }
        }
    }
}

/* ---------------------------------------------------------------- backup */

@Composable
private fun BackupPage(nav: Nav) {
    val ctx = LocalContext.current
    var step by remember { mutableStateOf<String?>(null) }   // pass, dir, pick, preview, rpass
    var password by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf<String?>(null) }
    var rev by remember { mutableIntStateOf(0) }
    val saveDoc = rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) try {
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(Backup.export(ctx, password).toByteArray()) }
            Toast.makeText(ctx, tr("Backup je spremljen"), Toast.LENGTH_LONG).show()
        } catch (e: Exception) { Toast.makeText(ctx, tr("Backup nije spremljen"), Toast.LENGTH_LONG).show() }
        step = null
    }
    fun restoreNow(pass: String?) {
        try { Backup.restore(ctx, text ?: return, pass); Toast.makeText(ctx, tr("Sve je vraćeno. PRETORIAN se ponovo pokreće."), Toast.LENGTH_LONG).show()
            (ctx as? android.app.Activity)?.recreate()
        } catch (e: Exception) { Toast.makeText(ctx, e.message ?: tr("Vraćanje nije uspjelo"), Toast.LENGTH_LONG).show() }
        step = null
    }
    key(rev) {
        Page("Backup i vraćanje", "Spašava playliste, grupe, favorite, izgled, ključeve, jezike, gledano i podsjetnike.") {
            OptionRow("Napravi backup", R.drawable.lc_upload, sub = "Izaberi folder na boxu ili USB-u") { step = "pass" }
            OptionRow("Vrati iz backupa", R.drawable.lc_download, sub = "Izaberi PRETORIAN backup fajl") { step = "pick" }
            ToggleRow("Automatski backup svake sedmice", Prefs.bool("backup_auto", true), R.drawable.lc_history, "U memoriji boxa, čuvaju se zadnja 3") { Prefs.put("backup_auto", it); rev++ }
            Backup.autoList(ctx).forEach { f ->
                OptionRow(f.name, R.drawable.lc_archive, sub = tr("Automatski backup"), value = tr("Vrati")) { text = f.readText(); step = "preview" }
            }
            T("Backup sadrži lozinke playlista i API ključeve. Ne dijeli ga s drugima, ili ga zaštiti lozinkom.", 12.sp, C.Amber, maxLines = 2)
        }
    }
    when (step) {
        "pass" -> Popup("Zaštititi backup lozinkom?", onDismiss = { step = null }) {
            OptionRow("Bez lozinke", R.drawable.lc_lock_open) { password = null; step = "dir" }
            OptionRow("Sa lozinkom", R.drawable.lc_lock) { step = "setpass" }
        }
        "setpass" -> TextDialog("Lozinka za backup", "Lozinka", onDismiss = { step = null }) { v -> password = v.ifBlank { null }; step = "dir" }
        "dir" -> Popup("Gdje spremiti backup?", onDismiss = { step = null }) {
            OptionRow("PRETORIAN (ugrađeni)", R.drawable.lc_folder_open, sub = "USB, Download, memorija boxa") { step = "browse" }
            OptionRow("Tvoj file manager", R.drawable.lc_folder) {
                try { saveDoc.launch(Backup.fileName()) } catch (e: Exception) { Toast.makeText(ctx, tr("Taj file manager se ne može otvoriti, koristim ugrađeni"), Toast.LENGTH_LONG).show(); step = "browse" }
            }
        }
        "browse" -> FileBrowser("Izaberi folder", emptySet(), pickDir = true, onDismiss = { step = null }) { dir ->
            try { java.io.File(dir, Backup.fileName()).writeText(Backup.export(ctx, password)); Toast.makeText(ctx, trf("Spremljeno: %s", dir.path + "/" + Backup.fileName()), Toast.LENGTH_LONG).show() }
            catch (e: Exception) { Toast.makeText(ctx, tr("U taj folder se ne može pisati. Izaberi drugi ili PRETORIAN folder."), Toast.LENGTH_LONG).show() }
            step = null
        }
        "pick" -> FilePicker("Izaberi backup", setOf("json"), onDismiss = { step = null }) { bytes, _ ->
            val t = String(bytes)
            if (Backup.summary(t) == null) { Toast.makeText(ctx, tr("Fajl nije PRETORIAN backup"), Toast.LENGTH_LONG).show(); step = null }
            else { text = t; step = "preview" }
        }
        "preview" -> {
            val sm = text?.let { Backup.summary(it) }
            if (sm == null) step = null
            else Popup("Vratiti sve iz backupa?", onDismiss = { step = null }) {
                T(trf("Datum: %s · playlista: %s · lista favorita: %s", sm.date, sm.playlists, sm.favLists), 14.sp, C.Soft, maxLines = 2)
                T("Trenutne postavke će biti zamijenjene.", 13.sp, C.Amber)
                OptionRow("Vrati sve", R.drawable.lc_rotate_ccw, color = C.Danger) { if (sm.encrypted) step = "rpass" else restoreNow(null) }
                OptionRow("Odustani", R.drawable.lc_x) { step = null }
            }
        }
        "rpass" -> TextDialog("Lozinka backupa", "Lozinka", onDismiss = { step = null }) { v -> restoreNow(v) }
        else -> {}
    }
}

/* ---------------------------------------------------------------- parental */

@Composable
private fun ParentalPage(nav: Nav) {
    val ctx = LocalContext.current
    var setup by remember { mutableStateOf<String?>(null) }
    var rev by remember { mutableIntStateOf(0) }
    key(rev) {
        Page("Roditelji i jednostavni mod", "Jednostavni mod ostavlja samo TV, Filmove i Serije. Izlaz iz njega traži PIN.") {
            OptionRow("Uključi jednostavni mod", R.drawable.lc_layout_grid, "Za starije i djecu: bez postavki, ništa se ne može pokvariti") {
                if (!Lock.hasPin()) setup = "simple" else { Lock.switchSimple(true); nav.root(Screen.Home) }
            }
            OptionRow(if (Lock.hasPin()) "Resetuj PIN" else "Postavi PIN", R.drawable.lc_key_round,
                if (Lock.hasPin()) "Ne traži stari PIN, samo novi" else "4 cifre, za napredni mod i zaključane kanale") { setup = "pin" }
            T("Zaključavanje grupa i kanala: TV › drži OK na grupi ili kanalu › Zaključaj PIN-om, ili Postavke › Grupe i kanali.", 12.sp, C.Muted, maxLines = 2)
        }
    }
    when (setup) {
        "pin" -> PinSetupDialog(onDismiss = { setup = null }) { setup = null; rev++; Toast.makeText(ctx, tr("PIN je postavljen"), Toast.LENGTH_SHORT).show() }
        "simple" -> PinSetupDialog(onDismiss = { setup = null }) { setup = null; Lock.switchSimple(true); nav.root(Screen.Home) }
        else -> {}
    }
}

/* ---------------------------------------------------------------- update + about */

@Composable
private fun UpdatePage() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    val u = Updater.available
    val installFr = remember { FocusRequester() }
    Page("Ažuriranje", trf("Instalirana verzija: %s. Provjera ide sama pri pokretanju i svakih 6 sati.", BuildInfo.VERSION)) {
        // 1. state, big and clear
        Row(Modifier.fillMaxWidth().clip(Rnd(16.dp)).background(if (u != null) C.CardFocus else C.Card)
            .border(1.dp, if (u != null) C.Red else C.Line, Rnd(16.dp)).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Ico(if (u != null) R.drawable.lc_download else R.drawable.lc_check, 26.dp, if (u != null) C.RedText else C.Green)
            Column(Modifier.weight(1f)) {
                T(if (u != null) trf("Nova verzija %s", u.name) else "Imaš najnoviju verziju", 17.sp, C.Text, FontWeight.SemiBold, maxLines = 1)
                T(if (u != null) (u.notes.ifBlank { tr("Jedan klik: preuzima se i odmah instalira") }) else trf("Instalirano: %s", BuildInfo.VERSION), 13.sp, C.Muted, maxLines = 3)
            }
            val pr = Updater.progress
            if (pr in 0..100) T(trf("Preuzimam %s %%", pr), 15.sp, C.Text)
            else if (pr == 101) T("Instaliram…", 15.sp, C.Text)
        }
        // 2. update now
        OptionRow("Ažuriraj sada", R.drawable.lc_download, color = if (u != null) C.Green else null,
            value = if (u != null) u.name else tr("Nema nove verzije"), focusRequester = installFr) {
            if (u != null) scope.launch { Updater.install(ctx, u) }
            else if (!checking) { checking = true; scope.launch { Updater.check(ctx); checking = false } }
        }
        // 3. check
        OptionRow(if (checking) "Provjeravam…" else "Provjeri ima li nova verzija", R.drawable.lc_refresh_cw, value = Updater.status) {
            if (!checking) { checking = true; scope.launch { Updater.check(ctx); checking = false } }
        }
        T("Prvi put Android može pitati da dozvoliš PRETORIAN-u instaliranje aplikacija. Dozvoli i ponovo klikni Ažuriraj.", 12.sp, C.Muted, maxLines = 2)
    }
}

private val COUNTRIES = listOf("" to "Automatski (po uređaju)", "DE" to "Njemačka", "AT" to "Austrija", "CH" to "Švicarska", "BA" to "Bosna i Hercegovina",
    "RS" to "Srbija", "HR" to "Hrvatska", "ME" to "Crna Gora", "SI" to "Slovenija", "MK" to "Sjeverna Makedonija", "GB" to "Velika Britanija",
    "IT" to "Italija", "ES" to "Španija", "FR" to "Francuska", "TR" to "Turska", "US" to "SAD")

@Composable
private fun SportPage() {
    val scope = rememberCoroutineScope()
    var pickCountry by remember { mutableStateOf(false) }
    var keyDlg by remember { mutableStateOf(false) }
    var pickAi by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var rev by remember { mutableIntStateOf(0) }
    key(rev) {
        Page("Sport i AI", "Sports Center prvo prikazuje ono što je važno u tvojoj državi.") {
            val conts = Sports.CONTINENTS
            val cOn = csv("sport_cont", "eu,am,world")
            SetMulti("Kontinenti", "više odjednom", conts.map { it.second }, conts.indices.filter { conts[it].first in cOn }.toSet(), all = false) { s2 ->
                Prefs.put("sport_cont", (if (s2.isEmpty()) setOf(0) else s2).sorted().joinToString(",") { conts[it].first }); Sports.lastCombined.clear(); rev++
            }
            val cur = Prefs.str("sport_country")
            OptionRow("Država", R.drawable.lc_map_pin, sub = "Koje lige su važnije (npr. Njemačka: Bundesliga)",
                value = tr(COUNTRIES.firstOrNull { it.first == cur }?.second ?: cur) + if (cur.isBlank()) " · " + Sports.country else "") { pickCountry = true }
            Label("AI pretraga prenosa")
            T("Za utakmice koje nisu u TV programu, AI traži ko ih prenosi u tvojoj državi i bira tvoje kanale. Označeno je ljubičasto i kao procjena.", 12.sp, C.Muted, maxLines = 3)
            val pv = SportsAI.prov
            OptionRow("AI", R.drawable.lc_sparkles, sub = tr(if (pv.free) "besplatno" else "plaća se po upitu") + " · " + tr(if (pv.search) "pretraga interneta" else "bez pretrage interneta"),
                value = pv.name) { pickAi = true }
            OptionRow(trf("%s ključ", pv.name), R.drawable.lc_key_round, sub = pv.site,
                value = tr(if (SportsAI.ready) "Upisan" else "Nije upisan")) { keyDlg = true }
            if (SportsAI.ready) OptionRow(status ?: tr("Provjeri ključ"), R.drawable.lc_refresh_cw) {
                status = tr("Provjeravam…"); scope.launch { status = SportsAI.test()?.let { trf("Ne radi: %s", it) } ?: tr("Radi ✓") }
            }
        }
    }
    if (pickAi) Popup(tr("AI"), { pickAi = false }) {
        SportsAI.PROVIDERS.forEach { p ->
            OptionRow(p.name, if (p.id == SportsAI.provider) R.drawable.lc_check else R.drawable.lc_sparkles,
                sub = tr(if (p.free) "besplatno" else "plaća se po upitu") + " · " + tr(if (p.search) "pretraga interneta" else "bez pretrage interneta") + " · " + p.site) {
                SportsAI.provider = p.id; status = null; pickAi = false; rev++
            }
        }
    }
    if (keyDlg) TextDialog(trf("%s ključ", SportsAI.prov.name), trf("Ključ (%s)", SportsAI.prov.keyHint), SportsAI.key, onDismiss = { keyDlg = false }) { v -> SportsAI.key = v; keyDlg = false; status = null; rev++ }
    if (pickCountry) Popup(tr("Država"), { pickCountry = false }) {
        COUNTRIES.forEach { (code, name) ->
            OptionRow(name, if (Prefs.str("sport_country") == code) R.drawable.lc_check else R.drawable.lc_map_pin) {
                Prefs.put("sport_country", code); Sports.lastCombined.clear(); pickCountry = false; rev++
            }
        }
    }
}

@Composable
private fun ProfilePage() {
    val ctx = LocalContext.current
    val cur = remember(Profiles.rev) { Profiles.current(ctx) }
    val all = remember(Profiles.rev) { Profiles.list(ctx) }
    var setup by remember { mutableStateOf(false) }
    Page("Profili i Google Drive", "Svaki profil ima svoje favorite, gledano, izgled, jezik i grupe. Playliste su zajedničke.") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Avatar(cur, 56.dp)
            Column(Modifier.weight(1f)) {
                T(cur.name, 18.sp, C.Text, FontWeight.Bold, maxLines = 1)
                T(trf("Profila: %s", all.size), 13.sp, C.Muted)
            }
        }
        OptionRow("Profili", R.drawable.lc_users, sub = "Promijeni, dodaj, uredi, PIN") { Profiles.panel = true }
        OptionRow("Ko gleda?", R.drawable.lc_user, sub = "Ekran za izbor profila") { Profiles.picker = true }
        ToggleRow("Pitaj \"Ko gleda?\" pri pokretanju", Profiles.askAtStart, R.drawable.lc_users) { Profiles.askAtStart = it; Profiles.rev++ }
        Label("Google Drive")
        T(Drive.statusLine(), 13.sp, if (Drive.signedIn) C.Green else C.Muted, maxLines = 2)
        if (!Drive.signedIn) OptionRow("Prijavi se Google nalogom", R.drawable.lc_log_in, sub = "Kod na TV-u, potvrda na telefonu", color = C.Green) { Drive.loginOpen = true }
        else {
            OptionRow("Sinhronizuj sada", R.drawable.lc_cloud_upload, sub = "Pošalji postavke i profile na Google Drive") {
                kotlinx.coroutines.GlobalScope.launch { try { Drive.upload(ctx) } catch (_: Exception) {} ; Profiles.rev++ }
            }
            OptionRow("Vrati s Google Drivea", R.drawable.lc_cloud_download, sub = "Povuci sve na ovaj uređaj") { Drive.restoreAsk = true }
            OptionRow("Odjavi se", R.drawable.lc_log_out) { Drive.logout(); Profiles.rev++ }
        }
        T("Promjene se same šalju svakih 10 minuta i kad izađeš iz aplikacije. Na novom uređaju se prijaviš istim nalogom i sve se samo preuzme.", 12.sp, C.Muted, maxLines = 3)
        if (BuildConfig.GOOGLE_CLIENT_ID.isBlank()) OptionRow("Google OAuth klijent", R.drawable.lc_key_round, value = tr(if (Drive.configured) "Upisan" else "Nije upisan")) { setup = true }
    }
    if (setup) DriveSetup(onDismiss = { setup = false }, onSaved = { setup = false })
}

@Composable
private fun AboutPage() {
    Page("O aplikaciji") {
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.logo_full), contentDescription = "PRETORIAN",
            modifier = Modifier.size(180.dp))
        OptionRow("PRETORIAN ${BuildInfo.VERSION}", R.drawable.lc_info, "IPTV player za Android TV") {}
        OptionRow("Izvorni kod", R.drawable.lc_link, "github.com/KarlAgathon/PRETORIAN · MIT licenca") {}
        OptionRow("Ikone i font", R.drawable.lc_type, "Lucide (ISC) · Inter (OFL) · Media3 (Apache 2.0)") {}
        OptionRow("Podaci o filmovima", R.drawable.lc_film, "This product uses the TMDB API but is not endorsed or certified by TMDB.") {}
    }
}

/** A row with a preview of five icons of a set. */
@Composable
private fun IconSetRow(name: String, about: String, selected: Boolean, previewSet: String, onClick: () -> Unit) {
    val ctx = LocalContext.current
    Focusable(modifier = Modifier.fillMaxWidth().height(62.dp), onClick = onClick, normal = if (selected) C.CardFocus else C.Card, zoom = 1.01f) { _ ->
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f)) { T(name, 15.sp, C.Text, FontWeight.SemiBold); T(about, 12.sp, C.Muted) }
            IconSets.PREVIEW.forEach { id ->
                if (previewSet.startsWith("dl_")) {
                    val bmp = remember(previewSet, id) { IconSets.downloaded(ctx, previewSet, id) }
                    if (bmp != null) androidx.compose.material3.Icon(androidx.compose.ui.graphics.painter.BitmapPainter(bmp), null, Modifier.size(22.dp), tint = C.Text)
                } else {
                    val res = remember(previewSet, id) { IconSets.resolve(ctx, id, previewSet, 1) }
                    androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(res), null, Modifier.size(22.dp), tint = C.Text)
                }
            }
            if (selected) Ico(R.drawable.lc_check, 18.dp, C.Red)
        }
    }
}

/* ================================================================ Settings in the new look (0.7.0) */

/** Osnovno shows only what everyone needs; Napredno shows everything. */
object SetMode {
    var advanced by mutableStateOf(Prefs.bool("settings_adv", false))
    fun set(on: Boolean) { advanced = on; Prefs.put("settings_adv", on) }
}

private val SECTIONS2 = listOf(
    Section("profile", R.drawable.lc_users, "Profili i Google Drive", "Ko gleda, prijava, sinhronizacija"),
    Section("playlists", R.drawable.lc_list, "Playliste", "Dodaj, EPG izvori, grupe i kanali"),
    Section("language", R.drawable.lc_languages, "Jezik", "Meniji, opisi filmova"),
    Section("look", R.drawable.lc_palette, "Izgled", "Tema, boja, veličina"),
    Section("media", R.drawable.lc_play, "Plejer", "Zvuk, titlovi, kontrole"),
    Section("sport", R.drawable.lc_trophy, "Sport", "Sportovi, država, AI"),
    Section("reminders", R.drawable.lc_bell, "Podsjetnici", "Emisije i utakmice"),
    Section("parental", R.drawable.lc_shield_check, "Jednostavni mod i PIN", "Samo TV, Filmovi, Serije"),
    Section("backup", R.drawable.lc_archive, "Backup i vraćanje", "U fajl ili na USB"),
    Section("about", R.drawable.lc_info, "O aplikaciji", "Verzija, dijagnostika, kontakt"),
    Section("mode", R.drawable.lc_settings, "Način rada", "Osnovno ili Napredno"),
    Section("update", R.drawable.lc_download, "Ažuriranje", "Nova verzija"),
)
private val BASIC = setOf("playlists", "language", "parental", "mode", "update")

/** Settings screen of the new look: sections on the left, the page on the right, sub-pages open inside. */
@Composable
fun SettingsScreen2(nav: Nav) {
    val ctx = LocalContext.current
    val list = SECTIONS2.filter { SetMode.advanced || it.id in BASIC }
    var section by remember { mutableStateOf((SettingsNav.section?.also { SettingsNav.section = null } ?: SettingsNav.last ?: if (Updater.available != null) "update" else "playlists")
        .let { s -> if (list.any { it.id == s }) s else "playlists" }) }
    var sub by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(section) { SettingsNav.last = section; sub = null }
    val first = remember { FocusRequester() }
    // optional: Settings behind the PIN
    var unlocked by remember { mutableStateOf(!(Prefs.bool("pin_settings") && Lock.hasPin())) }
    if (!unlocked) {
        PinDialog("Postavke", tr("Unesi PIN"), onDismiss = { nav.back() }) { pin -> val err = Lock.check(pin); if (err == null) unlocked = true; err }
        return
    }
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(250.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            list.forEach { s ->
                val sel = s.id == section
                Focusable(modifier = Modifier.fillMaxWidth().height(44.dp), onClick = { section = s.id; sub = null },
                    focusRequester = if (sel) first else null, normal = if (sel) C.CardHi else Color.Transparent, focused = C.Red, zoom = 1f) { f ->
                    Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(26.dp).clip(Rnd(8.dp)).background(if (f) C.OnRed.copy(alpha = 0.08f) else C.Card), contentAlignment = Alignment.Center) {
                            Ico(s.icon, 14.dp, if (f) C.OnRed else C.Text)
                        }
                        Column(Modifier.weight(1f)) {
                            T(s.title, 12.sp, if (f) C.OnRed else C.Text, FontWeight.SemiBold, maxLines = 1)
                            T(if (s.id == "mode") (if (SetMode.advanced) "Napredno" else "Osnovno") else s.sub, 10.sp, if (f) C.OnRed.copy(alpha = 0.6f) else C.Muted, maxLines = 1)
                        }
                        if (s.id == "update" && Updater.available != null) Box(Modifier.size(16.dp).clip(CircleShape).background(C.Danger), contentAlignment = Alignment.Center) {
                            T("1", 9.sp, Color.White, FontWeight.Bold)
                        }
                    }
                }
            }
        }
        var inPage by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        BackHandler(enabled = inPage) { if (sub != null) sub = null else { inPage = false; scope.launch { first.tryFocus() } } }
        Box(Modifier.weight(1f).fillMaxHeight().onFocusChanged { inPage = it.hasFocus }.padding(start = 18.dp, end = 24.dp, top = 4.dp, bottom = 8.dp)) {
            key(section, sub) {
                when (sub) {
                    "groups" -> GroupManager(nav)
                    "epg" -> SetPage("EPG izvori", "Dodaj, uredi, osvježi ili izbriši izvore TV programa.") { EpgSourcesBlock(Store.active(ctx)) }
                    "plists" -> PlaylistsListV2(nav)
                    "lookmore" -> LookPage()
                    "player" -> MediaPage()
                    else -> when (section) {
                        "playlists" -> PlaylistsV2(nav) { sub = it }
                        "profile" -> ProfilesV2()
                        "sport" -> SportV2()
                        "look" -> LookV2 { sub = it }
                        "language" -> LanguageV2()
                        "media" -> PlayerV2 { sub = it }
                        "parental" -> SimpleV2(nav)
                        "reminders" -> RemindersV2(nav)
                        "backup" -> BackupV2(nav)
                        "mode" -> ModeV2()
                        "update" -> UpdateV2()
                        else -> AboutV2()
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { try { first.requestFocus() } catch (_: Exception) {} }
}

/* ---------------------------------------------------------------- building blocks */

/** Keeps the focus on the row you worked on: after a choice, a switch or a closed list the page is drawn again
 *  and the same row takes the focus back (it never jumps up to the menu). */
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
        T(title, 20.sp, C.Text, FontWeight.Bold)
        if (sub != null) T(sub, 11.sp, C.Muted, maxLines = 2)
        content()
    }
}

@Composable
fun SetSection(t: String) {
    androidx.compose.material3.Text(tr(t).uppercase(), style = androidx.compose.ui.text.TextStyle(fontFamily = Inter, fontSize = 10.sp, color = C.Muted,
        fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp), modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
}

/** One settings row: title and a short line under it, the value on the right in a pill. */
@Composable
fun SetRow(title: String, sub: String? = null, value: String? = null, arrow: String = "›", valueColor: Color? = null, new: Boolean = false,
           focusRequester: FocusRequester? = null, onClick: () -> Unit) {
    val id = "row:$title"
    val fr = setFocusFr(id, focusRequester)
    Focusable(modifier = Modifier.fillMaxWidth().height(if (sub != null) 50.dp else 42.dp).border(1.dp, C.Line, Rnd(12.dp)),
        onClick = { SetFocus.last = id; SetFocus.pending = true; onClick() }, onFocus = { SetFocus.last = id },
        focusRequester = fr, shape = Rnd(12.dp), normal = C.Rail, focused = C.Red, zoom = 1.01f) { f ->
        Row(Modifier.fillMaxSize().padding(start = 14.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    T(title, 13.sp, if (f) C.OnRed else C.Text, FontWeight.SemiBold, maxLines = 1)
                    if (new) Box(Modifier.clip(Rnd(4.dp)).background(C.Green.copy(alpha = 0.2f)).padding(horizontal = 4.dp)) { T("NOVO", 8.sp, C.Green, FontWeight.Bold) }
                }
                if (sub != null) T(sub, 10.sp, if (f) C.OnRed.copy(alpha = 0.65f) else C.Muted, maxLines = 1)
            }
            if (value != null || arrow.isNotEmpty()) Row(Modifier.clip(Rnd(9.dp)).background(if (f) Color(0x14000000) else C.Card)
                .border(1.dp, if (f) Color(0x26000000) else C.Line, Rnd(9.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                if (value != null) T(value, 12.sp, if (f) C.OnRed else (valueColor ?: C.Text), FontWeight.SemiBold, maxLines = 1)
                if (arrow.isNotEmpty()) T(arrow, 10.sp, if (f) C.OnRed else C.Muted)
            }
        }
    }
}

/** Drop-down: OK opens the list, OK on a choice picks it and closes. */
@Composable
fun SetDrop(title: String, sub: String? = null, options: List<String>, selected: Int, new: Boolean = false, note: String? = null,
            valueColor: Color? = null, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    SetRow(title, sub, tr(options.getOrElse(selected) { options.first() }), "▼", valueColor, new) { open = true }
    if (open) Popup(title, { open = false; SetFocus.tick++ }) {
        if (note != null) T(note, 12.sp, C.Soft, maxLines = 4)
        options.forEachIndexed { i, o ->
            OptionRow(o, if (i == selected) R.drawable.lc_check else null, color = if (i == selected) C.Green else null) { open = false; onSelect(i); SetFocus.tick++ }
        }
    }
}

/** Yellow lamp = on, grey = off. */
@Composable
fun Lamp(on: Boolean) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(if (on) Color(0xFFFBBF24) else Color(0xFF475569)))
}

/** Several at once: lamps in a list; "Sve" first switches everything. */
@Composable
fun SetMulti(title: String, sub: String? = null, options: List<String>, on: Set<Int>, new: Boolean = false, all: Boolean = true,
             note: String? = null, onChange: (Set<Int>) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var cur by remember(open) { mutableStateOf(on) }
    val value = when {
        on.size == options.size && all -> tr("Sve") + " (${options.size})"
        on.isEmpty() -> tr("Ništa")
        on.size <= 2 -> on.sorted().joinToString(", ") { tr(options[it]) }
        else -> trf("%s izabrano", on.size)
    }
    SetRow(title, sub, value, "▼", null, new) { open = true }
    if (open) Popup(title, { open = false; onChange(cur); SetFocus.tick++ }, width = 620.dp) {
        if (note != null) T(note, 12.sp, C.Soft, maxLines = 3)
        if (all) LampRow("Sve", cur.size == options.size) { cur = if (cur.size == options.size) emptySet() else options.indices.toSet() }
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
fun SetSwitch(title: String, sub: String? = null, on: Boolean, new: Boolean = false, onChange: (Boolean) -> Unit) {
    val id = "sw:$title"
    val fr = setFocusFr(id)
    Focusable(modifier = Modifier.fillMaxWidth().height(if (sub != null) 50.dp else 42.dp).border(1.dp, C.Line, Rnd(12.dp)),
        onClick = { SetFocus.last = id; SetFocus.pending = true; onChange(!on) }, onFocus = { SetFocus.last = id }, focusRequester = fr,
        shape = Rnd(12.dp), normal = C.Rail, focused = C.Red, zoom = 1.01f) { f ->
        Row(Modifier.fillMaxSize().padding(start = 14.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    T(title, 13.sp, if (f) C.OnRed else C.Text, FontWeight.SemiBold, maxLines = 1)
                    if (new) Box(Modifier.clip(Rnd(4.dp)).background(Color(0xFFFBBF24)).padding(horizontal = 4.dp)) { T("NOVO", 8.sp, Color(0xFF111111), FontWeight.Bold) }
                }
                if (sub != null) T(sub, 10.sp, if (f) C.OnRed.copy(alpha = 0.65f) else C.Muted, maxLines = 1)
            }
            Box(Modifier.width(40.dp).height(22.dp).clip(Pill).background(if (on) Color(0xFF2563EB) else (if (f) Color(0x26000000) else C.CardHi))) {
                Box(Modifier.align(if (on) Alignment.CenterEnd else Alignment.CenterStart).padding(3.dp).size(16.dp).clip(CircleShape).background(Color.White))
            }
        }
    }
}

/** Slider: ◀ ▶ on the remote change the value. */
@Composable
fun SetSlider(title: String, sub: String?, value: Int, min: Int, max: Int, step: Int, label: String, onChange: (Int) -> Unit) {
    val id = "sl:$title"
    val fr = setFocusFr(id)
    Focusable(modifier = Modifier.fillMaxWidth().height(62.dp).border(1.dp, C.Line, Rnd(12.dp)).onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (e.key) {
            Key.DirectionLeft -> { SetFocus.last = id; SetFocus.pending = true; onChange((value - step).coerceAtLeast(min)); true }
            Key.DirectionRight -> { SetFocus.last = id; SetFocus.pending = true; onChange((value + step).coerceAtMost(max)); true }
            else -> false
        }
    }, onClick = {}, onFocus = { SetFocus.last = id }, focusRequester = fr, shape = Rnd(12.dp), normal = C.Rail, focused = C.CardHi, zoom = 1.01f) { f ->
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(title, 13.sp, C.Text, FontWeight.SemiBold, modifier = Modifier.weight(1f))
                T(label, 13.sp, Color(0xFF60A5FA), FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                T("◀", 10.sp, if (f) C.Text else C.Muted)
                Box(Modifier.weight(1f).height(4.dp).clip(Pill).background(C.Line)) {
                    Box(Modifier.fillMaxWidth(((value - min).toFloat() / (max - min).coerceAtLeast(1)).coerceIn(0.02f, 1f)).fillMaxHeight().background(Color(0xFF2563EB)))
                }
                T("▶", 10.sp, if (f) C.Text else C.Muted)
            }
            if (sub != null) T(sub, 9.sp, C.Muted, maxLines = 1)
        }
    }
}

private val THEME_SWATCH = mapOf("vece" to (Color(0xFF0C1428) to Color(0xFF1C2A4D)), "bw" to (Color(0xFF050505) to Color(0xFF1A1A1A)),
    "staklo" to (Color(0xFFF7F7F8) to Color.White), "pijesak" to (Color(0xFFECE9E4) to Color(0xFFFAF8F4)), "dark" to (Color(0xFF0B0D12) to Color(0xFF1A1F2A)),
    "light" to (Color(0xFFF2F3F6) to Color.White), "glass" to (Color(0xFF16202B) to Color(0xFF2E3C4A)), "dew" to (Color(0xFF221B36) to Color(0xFF3A2F55)))

/** Themes as small pictures. */
@Composable
fun ThemeCards(onPick: () -> Unit) {
    val ctx = LocalContext.current
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(Look.THEMES) { (id, name) ->
            val sel = Look.theme == id
            val (bg, card) = THEME_SWATCH[id] ?: (Color.DarkGray to Color.Gray)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val fid = "theme:$id"
                Focusable(onClick = { SetFocus.last = fid; SetFocus.pending = true; Look.theme = id; Look.save(ctx); onPick() }, onFocus = { SetFocus.last = fid },
                    focusRequester = setFocusFr(fid), shape = Rnd(12.dp), normal = bg, focused = bg, zoom = 1.06f,
                    modifier = Modifier.width(112.dp).height(64.dp).border(2.dp, if (sel) Color(0xFF22C55E) else C.Line, Rnd(12.dp))) { _ ->
                    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.width(36.dp).height(4.dp).clip(Pill).background(card.copy(alpha = 0.9f)))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { repeat(3) { i -> Box(Modifier.size(22.dp).clip(Rnd(5.dp)).background(card).then(if (i == 0) Modifier.border(1.dp, Color(0xFF3B82F6), Rnd(5.dp)) else Modifier)) } }
                    }
                    if (sel) Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(16.dp).clip(CircleShape).background(Color(0xFF22C55E)), contentAlignment = Alignment.Center) { Ico(R.drawable.lc_check, 11.dp, Color.White) }
                }
                T(name, 11.sp, if (sel) C.Text else C.Soft, FontWeight.SemiBold)
            }
        }
    }
}

/** Accent colours as round dots. */
@Composable
fun ColorDots(onPick: () -> Unit) {
    val ctx = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Look.ACCENTS.forEachIndexed { i, (_, col) ->
            val sel = Look.accent == i
            val fid = "accent:$i"
            Focusable(onClick = { SetFocus.last = fid; SetFocus.pending = true; Look.accent = i; Look.save(ctx); onPick() }, onFocus = { SetFocus.last = fid },
                focusRequester = setFocusFr(fid), shape = CircleShape, normal = col, focused = col, zoom = 1.15f,
                modifier = Modifier.size(30.dp).border(2.dp, if (sel) Color.White else Color.Transparent, CircleShape)) { _ ->
                if (sel) Box(Modifier.align(Alignment.Center)) { Ico(R.drawable.lc_check, 14.dp, Color.White) }
            }
        }
        T(Look.ACCENTS.getOrNull(Look.accent)?.first ?: "", 12.sp, C.Soft)
    }
}

private fun csv(k: String, def: String) = Prefs.str(k, def).split(',').filter { it.isNotBlank() }
private fun onOff(b: Boolean) = if (b) 0 else 1
private val ON_OFF = listOf("Uključeno", "Isključeno")

/* ---------------------------------------------------------------- Playliste */

@Composable
private fun PlaylistsV2(nav: Nav, openSub: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf(Store.playlists(ctx)) }
    var busy by remember { mutableStateOf(false) }
    val act = Store.active(ctx)
    SetPage("Playliste", act?.let { a ->
        val sec = a.exp.toLongOrNull()
        trf("Aktivna: %s", a.name) + (if (sec != null && sec > 0) " · " + trf("ističe %s", java.text.SimpleDateFormat("dd.MM.yy", java.util.Locale.getDefault()).format(java.util.Date(sec * 1000))) else "")
    }) {
        T("OK otvara playlistu · drži OK za brzi meni", 11.sp, C.Muted)
        list.forEach { pl ->
            val sec = pl.exp.toLongOrNull()
            SetRow(pl.name, (if (pl.type == SourceType.XTREAM) "Xtream" else "M3U") +
                (if (sec != null && sec > 0) " · " + trf("ističe %s", java.text.SimpleDateFormat("dd.MM.yy", java.util.Locale.getDefault()).format(java.util.Date(sec * 1000))) else ""),
                if (pl.active) "● " + tr("Aktivna") else "○ " + tr("Isključena"), arrow = "", valueColor = if (pl.active) C.Green else C.Muted) { openSub("plists") }
        }
        SetRow("+  " + tr("Dodaj playlistu"), "Xtream, M3U link ili fajl") { EditPlaylist.target = null; nav.push(Screen.AddPlaylist) }
        if (act != null) {
            SetSection(trf("Za aktivnu playlistu · %s", act.name))
            SetRow("Grupe i kanali", "sakrij, pomjeri, PIN") { openSub("groups") }
            SetRow("EPG izvori", "dodaj, uredi, osvježi, izbriši", "● " + trf("%s aktivna", EpgSources.list().count { it.on } + 1), valueColor = C.Green) { openSub("epg") }
            SetRow(if (busy) "Osvježavam…" else "Osvježi sve", "kanali, filmovi, serije, EPG") {
                if (busy) return@SetRow
                busy = true
                Repo.dropCatalogs(); Sports.lastCombined.clear()
                scope.launch {
                    val updated = Store.playlists(ctx).map { pl -> try { if (pl.type == SourceType.XTREAM) pl.copy(exp = Repo.test(pl)) else { Repo.test(pl); pl } } catch (e: Exception) { pl } }
                    Store.savePlaylists(ctx, updated); list = updated; Repo.clear()
                    EpgSources.list().filter { it.on }.forEach { try { EpgSources.refresh(it) } catch (_: Exception) {} }
                    busy = false; Toast.makeText(ctx, tr("Osvježeno"), Toast.LENGTH_SHORT).show()
                }
            }
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
    SetPage("Uredi playliste", "OK na playlisti: meni s opcijama.") {
        list.forEach { pl ->
            val sec = pl.exp.toLongOrNull()
            SetRow(pl.name, (if (pl.type == SourceType.XTREAM) "Xtream" else "M3U") +
                (if (sec != null && sec > 0) " · " + trf("ističe %s", java.text.SimpleDateFormat("dd.MM.yy", java.util.Locale.getDefault()).format(java.util.Date(sec * 1000))) else ""),
                if (pl.active) "● " + tr("Aktivna") else "○ " + tr("Isključena"), valueColor = if (pl.active) C.Green else C.Muted) { menu = pl }
        }
    }
    menu?.let { pl ->
        Popup(pl.name, { menu = null }) {
            OptionRow("Uredi (naziv, link, korisnik, lozinka)", R.drawable.lc_pencil) { menu = null; EditPlaylist.target = pl; nav.push(Screen.AddPlaylist) }
            OptionRow(if (pl.active) "Isključi playlistu" else "Uključi playlistu", R.drawable.lc_check) { Store.activate(ctx, pl.id); refresh(); menu = null }
            OptionRow("Osvježi kanale, filmove i serije", R.drawable.lc_refresh_cw) {
                menu = null; Repo.dropCatalogs()
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
                Store.savePlaylists(ctx, if (rest.none { it.active } && rest.isNotEmpty()) rest.mapIndexed { i, x -> x.copy(active = i == 0) } else rest)
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
            SetSection("Aktivan jezik")
            Row(Modifier.fillMaxWidth().clip(Rnd(14.dp)).background(C.CardHi).border(1.dp, C.Line, Rnd(14.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Flag(cur.flag, 40); T(cur.native, 17.sp, C.Text, FontWeight.Bold)
            }
            SetSection("Dostupni jezici · OK aktivira")
            Langs.APP.forEach { l ->
                Focusable(modifier = Modifier.fillMaxWidth().height(44.dp), onClick = { setAppLang(ctx, l.code); rev++ }, shape = Rnd(12.dp),
                    normal = C.Rail, focused = C.Red, zoom = 1.01f) { f ->
                    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Flag(l.flag, 28)
                        T(l.native, 14.sp, if (f) C.OnRed else C.Text, FontWeight.Medium, modifier = Modifier.weight(1f))
                        T(Langs.label(l.code), 11.sp, if (f) C.OnRed.copy(alpha = 0.6f) else C.Muted)
                        if (l.code == Look.lang) Ico(R.drawable.lc_check, 16.dp, if (f) C.OnRed else C.Green)
                    }
                }
            }
            SetSection("Opisi filmova i serija")
            val metaOpts = listOf("auto") + Langs.MEDIA.map { it.code }
            SetDrop("Jezik opisa filmova", null, listOf(tr("Kao aplikacija")) + Langs.MEDIA.map { it.native },
                metaOpts.indexOf(Prefs.metaLang).coerceAtLeast(0)) { Prefs.metaLang = metaOpts[it]; rev++ }
            SetDrop("Automatski prevedi opise", "na jezik opisa", ON_OFF, onOff(AutoTranslate.on)) { Prefs.put("auto_translate", it == 0); rev++ }
        }
    }
}

/* ---------------------------------------------------------------- Izgled */

@Composable
private fun LookV2(openSub: (String) -> Unit) {
    val ctx = LocalContext.current
    fun save() = Look.save(ctx)
    var rev by remember { mutableIntStateOf(0) }
    key(rev) {
        SetPage("Izgled", "Promjene se vide odmah.") {
            SetSection("Tema")
            ThemeCards { rev++ }
            SetSection("Boja")
            ColorDots { rev++ }
            val transp = (100 - Look.opacity).coerceIn(0, 50)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { SetSlider("Prozirnost", "0 – 50 %, korak 5 %", transp, 0, 50, 5, "$transp %") { Look.opacity = 100 - it; save(); rev++ } }
                Box(Modifier.weight(1f)) { SetSlider("Svjetlina boje", "tamnije ◀ ▶ svjetlije, korak 5 %", Look.shade, -2, 2, 1, (if (Look.shade > 0) "+" else "") + "${Look.shade * 5} %") { Look.shade = it; save(); rev++ } }
            }
            SetSection("Veličina i prikaz")
            SetDrop("Veličina", "tekst, pločice i plakati zajedno", listOf("Mala", "Srednja", "Velika"), Look.posterSize) {
                Look.posterSize = it; Look.tileSize = it; Look.rowSize = it; save(); rev++
            }
            SetDrop("Veći tekst", "za gledanje s daljeg kauča", ON_OFF, onOff(Look.bigText)) { Look.bigText = it == 0; save(); rev++ }
            SetDrop("Mala slika kanala", "pregled dok stojiš na kanalu", listOf("Automatski", "Na OK", "Isključena"), 2 - Look.previewMode) {
                Look.previewMode = 2 - it; Look.preview = Look.previewMode > 0; save(); rev++
            }
            SetDrop("Kad otvoriš TV, prvo vidiš", null, listOf("Lista kanala", "TV vodič"), if (Prefs.str("tv_view", "list") == "guide") 1 else 0) {
                Prefs.put("tv_view", if (it == 1) "guide" else "list"); rev++
            }
            SetDrop("Mod programa", trf("Ovaj uređaj: %s GB RAM · %s", String.format("%.1f", Perf.ramGb), tr(if (Perf.weak) "slabiji" else "jak")),
                listOf(tr("Automatski") + " (" + tr(if (Perf.weak) "Lite" else "Full") + ")", "Lite", "Full"), Perf.mode,
                note = tr("Lite: bez velikih slika u pozadini i bez prelaza, manje slike. Za slabije uređaje. Full: puni izgled sa slikama.")) { Perf.set(it); rev++ }
            if (SetMode.advanced) SetRow("Dodatne opcije izgleda", "ikone, oblik ivica, okviri, rezolucija u plejeru") { openSub("lookmore") }
        }
    }
}

/* ---------------------------------------------------------------- Plejer */

@Composable
private fun PlayerV2(openSub: (String) -> Unit) {
    val ctx = LocalContext.current
    var rev by remember { mutableIntStateOf(0) }
    val langs = listOf("bs", "hr", "sr", "en", "de", "tr", "it", "ro", "bg", "ar")
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
            SetSwitch("Uključi titlove sami", "ugrađeni, na tvom jeziku", Prefs.autoSubs) { Prefs.autoSubs = it; rev++ }
            val sub0 = Prefs.subLangs.split(',').firstOrNull() ?: "bs"
            SetDrop("Jezik titla", null, listOf(tr("Default (od providera)")) + langs.map { Langs.label(it) },
                if (!Prefs.autoSubs) 0 else langs.indexOf(sub0) + 1) { i ->
                if (i == 0) Prefs.autoSubs = false
                else { val l = langs[i - 1]; Prefs.autoSubs = true; Prefs.subLangs = (listOf(l) + Prefs.subLangs.split(',').filter { it.isNotBlank() && it != l }).joinToString(",") }
                rev++
            }
            SetDrop("Veličina titla", null, listOf("Mala", "Srednja", "Velika", "Vrlo velika"), Prefs.str("sub_size", "1").toIntOrNull() ?: 1, new = true) { Prefs.put("sub_size", it.toString()); rev++ }
            SetDrop("Izgled titla", null, listOf("Sjena", "Okvir", "Tamna pozadina"), Prefs.str("sub_style", "0").toIntOrNull() ?: 0, new = true) { Prefs.put("sub_style", it.toString()); rev++ }
            SetSection("Zvuk")
            val a0 = Prefs.audioLangs.split(',').firstOrNull() ?: "en"
            SetDrop("Jezik zvuka", null, listOf(tr("Default (od providera)")) + langs.map { Langs.label(it) },
                if (Prefs.bool("audio_default")) 0 else langs.indexOf(a0) + 1) { i ->
                if (i == 0) Prefs.put("audio_default", true)
                else { val l = langs[i - 1]; Prefs.put("audio_default", false); Prefs.audioLangs = (listOf(l) + Prefs.audioLangs.split(',').filter { it.isNotBlank() && it != l }).joinToString(",") }
                rev++
            }
            SetSection("Kontrole")
            SetDrop("Preskok ◀ ▶", null, listOf("10 s", "20 s", "30 s"), listOf(10, 20, 30).indexOf(Prefs.seekStep).coerceAtLeast(0)) { Prefs.seekStep = listOf(10, 20, 30)[it]; rev++ }
            SetSwitch("▲ ▼ mijenja kanal", "u TV-u, preko cijelog ekrana", Prefs.bool("zap_updown", true), new = true) { Prefs.put("zap_updown", it); rev++ }
            SetDrop("Nastavi gdje si stao", null, listOf("Pitaj", "Uvijek nastavi", "Uvijek od početka"), Prefs.str("resume_mode", "0").toIntOrNull() ?: 0, new = true) {
                Prefs.put("resume_mode", it.toString()); rev++
            }
            SetSwitch("Sljedeća epizoda sama", "odbrojava 10 s na kraju", Prefs.bool("next_ep", true), new = true) { Prefs.put("next_ep", it); rev++ }
            if (SetMode.advanced) {
                SetSection("Napredno")
                val players = remember { ExtPlayer.installed(ctx) }
                SetDrop("Plejer", "za filmove i serije", listOf(tr("Ugrađeni")) + players.map { it.label },
                    if (!ExtPlayer.always) 0 else (players.indexOfFirst { it.pkg == ExtPlayer.saved } + 1).coerceAtLeast(0)) { i ->
                    if (i == 0) ExtPlayer.always = false else { ExtPlayer.saved = players[i - 1].pkg; ExtPlayer.always = true }; rev++
                }
                SetSwitch("Slab internet", "veći bafer, manje zastoja", Prefs.bool("slow_net"), new = true) { Prefs.put("slow_net", it); rev++ }
                SetRow("Ostale opcije plejera", "više jezika po redu, file manager") { openSub("player") }
            }
        }
    }
}

/* ---------------------------------------------------------------- Sport */

@Composable
private fun SportV2() {
    val scope = rememberCoroutineScope()
    var rev by remember { mutableIntStateOf(0) }
    var keyDlg by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val sports = Sports.SPORTS + Sports.MORE_SPORTS
    key(rev) {
        SetPage("Sport", "Učitava se tek kad otvoriš Sport. Ništa u pozadini.") {
            SetSection("Šta pratim")
            val en = csv("sport_enabled", "football")
            SetMulti("Sportovi", "više odjednom", sports.map { it.second }, sports.indices.filter { sports[it].first in en }.toSet()) { s ->
                Prefs.put("sport_enabled", s.sorted().joinToString(",") { sports[it].first }); rev++
            }
            val conts = Sports.CONTINENTS
            val cOn = csv("sport_cont", "eu,am,world")
            SetMulti("Kontinenti", "više odjednom", conts.map { it.second }, conts.indices.filter { conts[it].first in cOn }.toSet(), all = false) { s2 ->
                Prefs.put("sport_cont", (if (s2.isEmpty()) setOf(0) else s2).sorted().joinToString(",") { conts[it].first }); Sports.lastCombined.clear(); rev++
            }
            val cur = Prefs.str("sport_country")
            SetDrop("Moja država", "najbliže tebi ide prvo", COUNTRIES.map { it.second }, COUNTRIES.indexOfFirst { it.first == cur }.coerceAtLeast(0)) {
                Prefs.put("sport_country", COUNTRIES[it].first); Sports.lastCombined.clear(); rev++
            }
            val lgs = Sports.LEAGUES.filter { it.sport in en }
            val lOn = csv("sport_leagues", "")
            SetMulti("Takmičenja", lgs.take(3).joinToString(", ") { tr(it.name) } + "…", lgs.map { it.name },
                if (lOn.isEmpty()) lgs.indices.toSet() else lgs.indices.filter { lgs[it].abbr in lOn }.toSet()) { s2 ->
                Prefs.put("sport_leagues", if (s2.size == lgs.size) "" else s2.sorted().joinToString(",") { lgs[it].abbr }); Sports.lastCombined.clear(); rev++
            }
            var teamDlg by remember { mutableStateOf(false) }
            val teams = Prefs.str("sport_teams").split(',').map { it.trim() }.filter { it.isNotBlank() }
            SetRow("Omiljeni timovi", "njihove utakmice uvijek na vrhu", if (teams.isEmpty()) tr("Nijedan") else trf("%s tima", teams.size), new = true) { teamDlg = true }
            if (teamDlg) TextDialog("Omiljeni timovi", "Imena, odvojena zarezom (npr. Željezničar, Bayern)", Prefs.str("sport_teams"), onDismiss = { teamDlg = false; SetFocus.tick++ }) { v ->
                Prefs.put("sport_teams", v); teamDlg = false; Sports.lastCombined.clear(); rev++
            }
            SetSection("Prikaz")
            SetDrop("Mala slika utakmice", null, listOf("Automatski", "Isključena"), if (Prefs.bool("sport_preview", true)) 0 else 1) { Prefs.put("sport_preview", it == 0); rev++ }
            SetDrop("Podsjeti me prije utakmice", "za omiljene timove", listOf("Isključeno", "5 min", "15 min", "30 min"),
                listOf(0, 5, 15, 30).indexOf(Prefs.str("sport_team_remind", "0").toIntOrNull() ?: 0).coerceAtLeast(0), new = true) {
                Prefs.put("sport_team_remind", listOf(0, 5, 15, 30)[it].toString()); rev++
            }
            SetDrop("Osvježavanje rezultata", null, listOf("Svake minute", "Svake 3 min", "Svakih 5 min"),
                listOf(1, 3, 5).indexOf(Prefs.str("sport_refresh", "1").toIntOrNull() ?: 1).coerceAtLeast(0), new = true) { Prefs.put("sport_refresh", listOf(1, 3, 5)[it].toString()); rev++ }
            SetSection("Ko prenosi · izvori")
            val srcKeys = listOf("sport_src_rights", "sport_src_ai")
            SetMulti("Izvori", "odakle se traži ko prenosi · tvoj TV vodič uvijek", listOf("Prava prenosa po ligi", "AI pretraga"),
                srcKeys.indices.filter { Prefs.bool(srcKeys[it], true) }.toSet(), all = false, new = true) { s2 -> srcKeys.forEachIndexed { i, k -> Prefs.put(k, i in s2) }; Sports.lastCombined.clear(); rev++ }
            val pv = SportsAI.prov
            SetDrop("AI", tr(if (pv.free) "besplatno" else "plaća se po upitu"), SportsAI.PROVIDERS.map { it.name },
                SportsAI.PROVIDERS.indexOfFirst { it.id == SportsAI.provider }.coerceAtLeast(0)) { SportsAI.provider = SportsAI.PROVIDERS[it].id; status = null; rev++ }
            SetRow(trf("%s ključ", pv.name), pv.site, tr(if (SportsAI.ready) "Upisan ✓" else "Nije upisan"), valueColor = if (SportsAI.ready) C.Green else null) { keyDlg = true }
            if (SportsAI.ready) SetRow(status ?: tr("Provjeri ključ"), null, arrow = "↻") {
                status = tr("Provjeravam…"); scope.launch { status = SportsAI.test()?.let { trf("Ne radi: %s", it) } ?: tr("Radi ✓") }
            }
        }
    }
    if (keyDlg) TextDialog(trf("%s ključ", SportsAI.prov.name), trf("Ključ (%s)", SportsAI.prov.keyHint), SportsAI.key, onDismiss = { keyDlg = false }) { v -> SportsAI.key = v; keyDlg = false; status = null; rev++ }
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
                SetRow(r.title, java.text.SimpleDateFormat("EEE d. M. HH:mm", java.util.Locale(Look.lang)).format(java.util.Date(r.start)) + (r.channelName?.let { " · $it" } ?: ""), arrow = "›") { menu = r }
            }
            SetSection("Kad dođe vrijeme")
            SetDrop("Šta da uradi", null, listOf("Prikaži obavijest", "Prebaci na kanal sam"), Reminders.action) { Reminders.action = it; rev++ }
            SetDrop("Podsjeti ranije", null, listOf("Na vrijeme", "1 min", "5 min", "10 min", "15 min"), listOf(0, 1, 5, 10, 15).indexOf(Reminders.before).coerceAtLeast(0)) {
                Reminders.before = listOf(0, 1, 5, 10, 15)[it]; rev++
            }
            SetDrop("Obavijest stoji", null, listOf("10 s", "20 s", "30 s"), listOf(10, 20, 30).indexOf(Reminders.popupSec).coerceAtLeast(1)) { Reminders.popupSec = listOf(10, 20, 30)[it]; rev++ }
            SetSwitch("Zvuk obavijesti", null, Prefs.bool("rem_sound", true), new = true) { Prefs.put("rem_sound", it); rev++ }
            SetSection("Ostalo")
            SetDrop("Kad je aplikacija zatvorena", "ne radi na svim uređajima", listOf("Otvori PRETORIAN", "Ništa"), if (Reminders.wake) 0 else 1, new = true) { Reminders.wake = it == 0; rev++ }
            SetDrop("Stari podsjetnici", null, listOf("Obriši nakon 1 dan", "Obriši nakon 7 dana", "Obriši odmah"),
                listOf(24, 168, 1).indexOf(Prefs.str("rem_keep", "24").toIntOrNull() ?: 24).coerceAtLeast(0), new = true) { Prefs.put("rem_keep", listOf(24, 168, 1)[it].toString()); rev++ }
        }
    }
    menu?.let { r ->
        Popup(r.title, { menu = null }) {
            if (r.channelUrl != null) OptionRow("Gledaj kanal sada", R.drawable.lc_play, color = C.Green) { menu = null; playReminder(nav, r) }
            OptionRow("Pomjeri 5 min ranije", R.drawable.lc_arrow_up) { Reminders.remove(r.id); Reminders.add(r.copy(start = r.start - 5 * 60_000L)); menu = null; rev++ }
            OptionRow("Pomjeri 5 min kasnije", R.drawable.lc_arrow_down) { Reminders.remove(r.id); Reminders.add(r.copy(start = r.start + 5 * 60_000L)); menu = null; rev++ }
            OptionRow("Obriši podsjetnik", R.drawable.lc_trash_2, color = C.Danger) { Reminders.remove(r.id); menu = null; rev++ }
            OptionRow("Zatvori", R.drawable.lc_x) { menu = null }
        }
    }
}

/* ---------------------------------------------------------------- Jednostavni mod i PIN */

@Composable
private fun SimpleV2(nav: Nav) {
    val ctx = LocalContext.current
    var rev by remember { mutableIntStateOf(0) }
    var setup by remember { mutableStateOf<String?>(null) }
    val items = listOf("TV", "Filmovi", "Serije")
    val ids = listOf("tv", "vod", "series")
    key(rev) {
        SetPage("Jednostavni mod i PIN") {
            SetSection("Jednostavni mod")
            SetDrop("Jednostavni mod", "početna samo s TV, Filmovi, Serije", listOf("Isključen", "Uključen"), if (Lock.simple) 1 else 0) {
                if (it == 1) { if (!Lock.hasPin()) setup = "simple" else { Lock.switchSimple(true); nav.root(Screen.Home) } }
            }
            val on = csv("simple_items", "tv,vod,series")
            SetMulti("Šta se vidi", "lampicama biraš", items, ids.indices.filter { ids[it] in on }.toSet(), all = false) { s ->
                Prefs.put("simple_items", (if (s.isEmpty()) setOf(0) else s).sorted().joinToString(",") { ids[it] }); rev++
            }
            val lim = listOf(0, 60, 120, 180, 240)
            SetDrop("Dnevno vrijeme gledanja", "1 h, 2 h, 3 h… pa traži PIN", listOf("Bez ograničenja", "1 h", "2 h", "3 h", "4 h"),
                lim.indexOf(WatchLimit.limitMin).coerceAtLeast(0), new = true,
                note = tr("Kad vrijeme istekne, plejer stane i traži PIN. Treba postaviti PIN.")) { Prefs.put("watch_limit", lim[it].toString()); rev++ }
            SetDrop("Izlaz iz jednostavnog moda", null, listOf("Traži PIN", "Bez PIN-a"), if (Prefs.bool("pin_simple_exit", true)) 0 else 1) { Prefs.put("pin_simple_exit", it == 0); rev++ }
            SetSection("PIN")
            SetRow("PIN", if (Lock.hasPin()) "promijeni ili postavi novi" else "4 cifre", tr(if (Lock.hasPin()) "Postavljen ✓" else "Nije postavljen"),
                valueColor = if (Lock.hasPin()) C.Green else null) { setup = "pin" }
            if (Lock.hasPin()) SetRow("Zaboravljen PIN?", "upišeš novi, stari ne treba", tr("Resetuj PIN"), new = true) { setup = "pin" }
            val pinFor = listOf("Izlaz iz jednostavnog moda", "Zaključane grupe i kanali", "Postavke", "Brisanje playliste")
            val keys = listOf("pin_simple_exit", "pin_locked", "pin_settings", "pin_delpl")
            SetMulti("PIN traži za", "više odjednom", pinFor,
                keys.indices.filter { Prefs.bool(keys[it], it <= 1) }.toSet(), all = false) { s -> keys.forEachIndexed { i, k -> Prefs.put(k, i in s) }; rev++ }
        }
    }
    when (setup) {
        "pin" -> PinSetupDialog(onDismiss = { setup = null }) { setup = null; rev++; Toast.makeText(ctx, tr("PIN je postavljen"), Toast.LENGTH_SHORT).show() }
        "simple" -> PinSetupDialog(onDismiss = { setup = null }) { setup = null; Lock.switchSimple(true); nav.root(Screen.Home) }
        else -> {}
    }
}

/* ---------------------------------------------------------------- Profili */

@Composable
private fun ProfilesV2() {
    val ctx = LocalContext.current
    val cur = remember(Profiles.rev) { Profiles.current(ctx) }
    val all = remember(Profiles.rev) { Profiles.list(ctx) }
    var menu by remember { mutableStateOf<Profile?>(null) }
    var edit by remember { mutableStateOf<Profile?>(null) }
    var add by remember { mutableStateOf(false) }
    var setup by remember { mutableStateOf(false) }
    var pinFor by remember { mutableStateOf<Profile?>(null) }
    SetPage("Profili i Google Drive") {
        SetSection("Profili · OK = meni profila")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(all) { p ->
                val now = p.id == cur.id
                Focusable(onClick = { menu = p }, shape = Rnd(16.dp), normal = C.Rail, focused = C.Red, zoom = 1.05f,
                    modifier = Modifier.width(110.dp).height(116.dp).border(2.dp, if (now) Color(0xFF22C55E) else C.Line, Rnd(16.dp))) { f ->
                    Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
                        Avatar(p, 50.dp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            T(p.name, 13.sp, if (f) C.OnRed else C.Text, FontWeight.Bold, maxLines = 1)
                            if (p.pinHash != null) Ico(R.drawable.lc_lock, 11.dp, if (f) C.OnRed else C.Amber)
                        }
                        if (now) T("● gleda sada", 10.sp, if (f) C.OnRed else C.Green)
                    }
                }
            }
            item {
                Focusable(onClick = { add = true }, shape = Rnd(16.dp), normal = Color.Transparent, focused = C.Red, zoom = 1.05f,
                    modifier = Modifier.width(110.dp).height(116.dp).border(1.dp, C.Line, Rnd(16.dp))) { f ->
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
                        Ico(R.drawable.lc_plus, 22.dp, if (f) C.OnRed else C.Text); T("Dodaj", 13.sp, if (f) C.OnRed else C.Muted, FontWeight.SemiBold)
                    }
                }
            }
        }
        SetDrop("Pitaj „Ko gleda?” pri pokretanju", null, listOf("Da", "Ne"), if (Profiles.askAtStart) 0 else 1) { Profiles.askAtStart = it == 0; Profiles.rev++ }
        val kid = Prefs.str("kids_profile")
        SetDrop("Profil za djecu", "ulazi odmah u jednostavni mod", listOf(tr("Nijedan")) + all.map { it.name },
            (all.indexOfFirst { it.id == kid } + 1).coerceAtLeast(0), new = true) { Prefs.put("kids_profile", if (it == 0) "" else all[it - 1].id); Profiles.rev++ }
        SetSection("Google Drive")
        SetRow("Račun", "prijava kodom s telefona", if (Drive.signedIn) Drive.statusLine() else tr("Nije prijavljen"), valueColor = if (Drive.signedIn) C.Green else null) {
            if (!Drive.signedIn) Drive.loginOpen = true
        }
        if (Drive.signedIn) {
            SetDrop("Sinhronizacija", "playliste, favoriti, gledano, postavke", listOf("Svakih 10 min", "Svakih sat", "Isključena"),
                listOf(10, 60, 0).indexOf(Prefs.str("drive_every", "10").toIntOrNull() ?: 10).coerceAtLeast(0)) { Prefs.put("drive_every", listOf(10, 60, 0)[it].toString()); Profiles.rev++ }
            SetRow("Sinhronizuj sada", "playliste, favoriti, gledano, postavke") { kotlinx.coroutines.GlobalScope.launch { try { Drive.upload(ctx) } catch (_: Exception) {}; Profiles.rev++ } }
            SetRow("Vrati s Google Drivea", "povuci sve na ovaj uređaj") { Drive.restoreAsk = true }
            SetRow("Odjavi se", null) { Drive.logout(); Profiles.rev++ }
        }
        if (BuildConfig.GOOGLE_CLIENT_ID.isBlank()) SetRow("Google OAuth klijent", null, tr(if (Drive.configured) "Upisan" else "Nije upisan")) { setup = true }
    }
    menu?.let { p ->
        Popup(p.name, { menu = null }) {
            OptionRow("Uredi profil", R.drawable.lc_pencil, sub = "ime, slika, boja, PIN profila") { menu = null; edit = p }
            if (p.id != cur.id) OptionRow("Prebaci na ovaj profil", R.drawable.lc_user) { menu = null; if (p.pinHash != null) pinFor = p else Profiles.switchTo(ctx, p) }
            if (p.id != cur.id && all.size > 1) OptionRow("Obriši profil", R.drawable.lc_trash_2, color = C.Danger) { menu = null; Profiles.delete(ctx, p); Profiles.rev++ }
        }
    }
    edit?.let { p -> ProfileEditor(p) { edit = null; Profiles.rev++ } }
    if (add) ProfileEditor(null) { add = false; Profiles.rev++ }
    pinFor?.let { p ->
        PinDialog(p.name, tr("Unesi PIN profila"), onDismiss = { pinFor = null }) { pin ->
            if (Profiles.hash(p.id, pin) == p.pinHash) { pinFor = null; Profiles.switchTo(ctx, p); null } else tr("Pogrešan PIN")
        }
    }
    if (setup) DriveSetup(onDismiss = { setup = false }, onSaved = { setup = false })
}

/* ---------------------------------------------------------------- Backup */

private val BACKUP_PARTS = listOf("Playliste i lozinke", "EPG izvori", "Grupe i kanali (redoslijed, sakriveno, imena)", "Profili i PIN-ovi", "Favoriti",
    "Odgledano i „Nastavi gledanje”", "Podsjetnici", "Izgled (tema, boja, veličina)", "Jezik, titlovi i zvuk", "Sport (sportovi, takmičenja)", "AI ključevi")

@Composable
private fun BackupV2(nav: Nav) {
    val ctx = LocalContext.current
    var step by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf<String?>(null) }
    var rev by remember { mutableIntStateOf(0) }
    var reset by remember { mutableStateOf(false) }
    val parts = csv("backup_parts", (0 until 10).joinToString(",")).mapNotNull { it.toIntOrNull() }.toSet()
    fun restoreNow(pass: String?) {
        try { Backup.restore(ctx, text ?: return, pass); Toast.makeText(ctx, tr("Sve je vraćeno. PRETORIAN se ponovo pokreće."), Toast.LENGTH_LONG).show()
            (ctx as? android.app.Activity)?.recreate()
        } catch (e: Exception) { Toast.makeText(ctx, e.message ?: tr("Vraćanje nije uspjelo"), Toast.LENGTH_LONG).show() }
        step = null
    }
    key(rev) {
        SetPage("Backup i vraćanje") {
            SetSection("Napravi backup")
            val where = Prefs.str("backup_where", "0").toIntOrNull() ?: 0
            SetRow("Napravi backup sada", null, arrow = "›") {
                if (where == 1) {
                    try { val d = java.io.File(ctx.filesDir, "backups").apply { mkdirs() }; java.io.File(d, Backup.fileName()).writeText(Backup.export(ctx, null, parts))
                        Toast.makeText(ctx, tr("Spremljeno u memoriju TV-a"), Toast.LENGTH_LONG).show(); rev++ }
                    catch (e: Exception) { Toast.makeText(ctx, e.message ?: "", Toast.LENGTH_LONG).show() }
                } else step = "browse"
            }
            SetDrop("Gdje", "USB ili memorija TV-a", listOf("USB / folder", "Memorija TV-a"), where.coerceIn(0, 1)) { Prefs.put("backup_where", it.toString()); rev++ }
            SetMulti("Šta uključiti", "sve ili samo neke dijelove", BACKUP_PARTS, parts,
                note = tr("AI ključevi su po defaultu isključeni, zbog sigurnosti")) { s -> Prefs.put("backup_parts", s.sorted().joinToString(",")); rev++ }
            SetDrop("Automatski backup", "u memoriji TV-a, čuvaju se zadnja 3", listOf("Isključen", "Sedmično", "Dnevno"),
                if (!Prefs.bool("backup_auto", true)) 0 else if (Prefs.bool("backup_daily")) 2 else 1) {
                Prefs.put("backup_auto", it > 0); Prefs.put("backup_daily", it == 2); rev++
            }
            SetSection("Vrati iz backupa")
            Backup.autoList(ctx).forEach { f ->
                SetRow(java.text.SimpleDateFormat("EEE, dd.MM.yy · HH:mm", java.util.Locale(Look.lang)).format(java.util.Date(f.lastModified())),
                    tr("Memorija TV-a · automatski") + " · ${f.length() / 1024} KB", tr("Vrati")) { text = f.readText(); step = "preview" }
            }
            SetRow("Drugi fajl", ".json s USB-a ili diska", tr("Izaberi")) { step = "pick" }
            SetSection("Opasno")
            SetRow("Vrati aplikaciju na početak", "briše sve: playliste, profile, postavke", tr("Resetuj"), valueColor = C.Danger, new = true) { reset = true }
        }
    }
    when (step) {
        "browse" -> FileBrowser("Izaberi folder", emptySet(), pickDir = true, onDismiss = { step = null }) { dir ->
            try { java.io.File(dir, Backup.fileName()).writeText(Backup.export(ctx, null, parts)); Toast.makeText(ctx, trf("Spremljeno: %s", dir.path + "/" + Backup.fileName()), Toast.LENGTH_LONG).show() }
            catch (e: Exception) { Toast.makeText(ctx, tr("U taj folder se ne može pisati. Izaberi drugi ili PRETORIAN folder."), Toast.LENGTH_LONG).show() }
            step = null
        }
        "pick" -> FilePicker("Izaberi backup", setOf("json"), onDismiss = { step = null }) { bytes, _ ->
            val t = String(bytes)
            if (Backup.summary(t) == null) { Toast.makeText(ctx, tr("Fajl nije PRETORIAN backup"), Toast.LENGTH_LONG).show(); step = null } else { text = t; step = "preview" }
        }
        "preview" -> {
            val sm = text?.let { Backup.summary(it) }
            if (sm == null) step = null
            else Popup("Vratiti sve iz backupa?", onDismiss = { step = null }) {
                T(trf("Datum: %s · playlista: %s · lista favorita: %s", sm.date, sm.playlists, sm.favLists), 14.sp, C.Soft, maxLines = 2)
                T("Trenutne postavke će biti zamijenjene.", 13.sp, C.Amber)
                OptionRow("Vrati sve", R.drawable.lc_rotate_ccw, color = C.Danger) { if (sm.encrypted) step = "rpass" else restoreNow(null) }
                OptionRow("Odustani", R.drawable.lc_x) { step = null }
            }
        }
        "rpass" -> TextDialog("Lozinka backupa", "Lozinka", onDismiss = { step = null }) { v -> restoreNow(v) }
        else -> {}
    }
    if (reset) Popup("Vratiti aplikaciju na početak?", { reset = false }) {
        T("Briše sve: playliste, profile, favorite, gledano i postavke. Ne može se poništiti.", 14.sp, C.Amber, maxLines = 3)
        OptionRow("Da, obriši sve", R.drawable.lc_trash_2, color = C.Danger) {
            reset = false
            try { (ctx.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager).clearApplicationUserData() } catch (_: Exception) {}
        }
        OptionRow("Odustani", R.drawable.lc_x) { reset = false }
    }
}

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
private fun UpdateV2() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var rev by remember { mutableIntStateOf(0) }
    val u = Updater.available
    key(rev) {
        SetPage("Ažuriranje") {
            Column(Modifier.fillMaxWidth().clip(Rnd(16.dp)).background(if (u != null) C.Amber.copy(alpha = 0.12f) else C.Rail)
                .border(1.dp, if (u != null) C.Amber.copy(alpha = 0.5f) else C.Line, Rnd(16.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(38.dp).clip(Rnd(12.dp)).background(if (u != null) C.Amber else C.Green.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                        Ico(if (u != null) R.drawable.lc_download else R.drawable.lc_check, 18.dp, if (u != null) Color(0xFF1E293B) else C.Green)
                    }
                    Column(Modifier.weight(1f)) {
                        T(if (u != null) trf("Nova verzija %s", u.name) else "Imaš najnoviju verziju", 16.sp, C.Text, FontWeight.Bold, maxLines = 1)
                        T(trf("Imaš %s", BuildInfo.VERSION), 11.sp, C.Soft)
                    }
                }
                val pr = Updater.progress
                if (pr in 0..100) {
                    T(trf("Preuzimam %s %%", pr), 12.sp, C.Text)
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(Pill).background(C.Line)) { Box(Modifier.fillMaxWidth(pr / 100f).fillMaxHeight().background(C.Text)) }
                } else if (pr == 101) T("Instaliram…", 12.sp, C.Text)
                if (u != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button("Preuzmi i instaliraj", R.drawable.lc_download, primary = true) { scope.launch { Updater.install(ctx, u) } }
                    Button("Šta je novo", R.drawable.lc_sparkles) { WhatsNew.show = true }
                }
            }
            SetSection("Podešavanja")
            SetDrop("Provjeri nove verzije", Updater.status ?: "", listOf("Pri pokretanju i svakih 6 sati", "Jednom dnevno", "Isključeno"),
                Prefs.str("upd_mode", "0").toIntOrNull() ?: 0) { Prefs.put("upd_mode", it.toString()); rev++ }
            SetDrop("Obavijest", "zvonce gore desno", ON_OFF, onOff(Prefs.bool("upd_note", true))) { Prefs.put("upd_note", it == 0); rev++ }
            SetSection("Ručno")
            SetRow(if (checking) "Provjeravam…" else "Provjeri sada", "traži novu verziju odmah", arrow = "↻") {
                if (!checking) { checking = true; scope.launch { Updater.check(ctx); checking = false; rev++ } }
            }
            T("Prvi put Android može pitati da dozvoliš PRETORIAN-u instaliranje aplikacija. Dozvoli i ponovo klikni.", 11.sp, C.Muted, maxLines = 2)
        }
    }
}

/* ---------------------------------------------------------------- O aplikaciji */

@Composable
private fun AboutV2() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var dlg by remember { mutableStateOf<String?>(null) }
    // diagnostics run only while this page is open, one after another, each with a time limit
    var step by remember { mutableIntStateOf(0) }
    var net by remember { mutableStateOf<String?>(null) }
    var server by remember { mutableStateOf<Boolean?>(null) }
    var cacheMb by remember { mutableStateOf(0L) }
    val crash = remember { try { java.io.File(ctx.filesDir, "last_crash.txt").takeIf { it.exists() }?.readText() } catch (e: Exception) { null } }
    val freeGb = remember { try { val s = android.os.StatFs(ctx.filesDir.path); s.availableBytes / 1_073_741_824.0 } catch (e: Exception) { 0.0 } }
    LaunchedEffect(step) {
        if (step != 0) return@LaunchedEffect
        net = null; server = null
        cacheMb = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { (ctx.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }) / 1_048_576 }
        net = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val t0 = System.currentTimeMillis()
                val c = java.net.URL("https://speed.cloudflare.com/__down?bytes=3000000").openConnection() as java.net.HttpURLConnection
                c.connectTimeout = 4000; c.readTimeout = 4000
                var n = 0L; val buf = ByteArray(16384); val ping = System.currentTimeMillis()
                c.inputStream.use { ins -> val first = System.currentTimeMillis(); while (System.currentTimeMillis() - first < 3000) { val r = ins.read(buf); if (r < 0) break; n += r } }
                c.disconnect()
                val ms = (System.currentTimeMillis() - t0).coerceAtLeast(1)
                trf("%s Mbit/s", (n * 8 / 1000 / ms).toString()) + " · " + (ping - t0) + " ms"
            } catch (e: Exception) { tr("Nije uspjelo") }
        }
        val p = Store.active(ctx)
        server = if (p == null) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { try { Repo.test(p); true } catch (e: Exception) { false } }
        step = 1
    }
    SetPage("O aplikaciji") {
        Row(Modifier.fillMaxWidth().clip(Rnd(16.dp)).background(C.CardHi).border(1.dp, C.Line, Rnd(16.dp)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.logo_full), null, Modifier.size(48.dp))
            Column(Modifier.weight(1f)) {
                T("PRETORIAN", 18.sp, C.Text, FontWeight.ExtraBold)
                T(trf("Verzija %s", BuildInfo.VERSION), 11.sp, C.Soft)
            }
            val u = Updater.available
            T(if (u != null) trf("Dostupna %s", u.name) else tr("Najnovija"), 11.sp, if (u != null) C.Amber else C.Green, FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f).clip(Rnd(12.dp)).background(C.Rail).border(1.dp, C.Line, Rnd(12.dp)).padding(10.dp)) { T("Autor", 10.sp, C.Muted); T("Svi3e", 14.sp, C.Text, FontWeight.Bold) }
            Column(Modifier.weight(1f).clip(Rnd(12.dp)).background(C.Rail).border(1.dp, C.Line, Rnd(12.dp)).padding(10.dp)) { T("Kontakt", 10.sp, C.Muted); T("t3smo@aol.com", 14.sp, C.Text, FontWeight.Bold) }  // i18n-ignore
        }
        SetRow("Šta je novo", "promjene u ovoj verziji", BuildInfo.VERSION) { WhatsNew.show = true }
        SetRow("Licence", "biblioteke otvorenog koda") { dlg = "lic" }
        SetRow("Privatnost", "šta aplikacija čuva i gdje", tr("Pogledaj")) { dlg = "priv" }
        // help page as a QR code: open it with the phone
        Row(Modifier.fillMaxWidth().clip(Rnd(14.dp)).background(C.Rail).border(1.dp, C.Line, Rnd(14.dp)).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val qr = remember { qrBitmap("https://github.com/KarlAgathon/PRETORIAN/issues", 240) }  // i18n-ignore
            if (qr != null) androidx.compose.foundation.Image(qr, null, Modifier.size(64.dp).clip(Rnd(6.dp)))
            Column(Modifier.weight(1f)) {
                T("Pomoć i prijava greške", 13.sp, C.Text, FontWeight.Bold)
                T("Skeniraj telefonom – otvara stranicu projekta", 10.sp, C.Muted, maxLines = 2)
            }
        }
        SetSection(if (step == 0) tr("Dijagnostika") + " · " + tr("provjera u toku…") else tr("Dijagnostika") + " · " + tr("gotovo"))
        SetRow("Test interneta", "brzina i ping", net ?: tr("Provjeravam…"), "↻", valueColor = if (net != null) C.Green else C.Muted) { step = 0 }
        SetRow("Server playliste", Store.active(ctx)?.name, when (server) { true -> tr("Online"); false -> tr("Ne odgovara"); null -> tr("Provjeravam…") },
            valueColor = when (server) { true -> C.Green; false -> C.Danger; null -> C.Muted }) { step = 0 }
        SetRow("Izvještaj o greškama", "zadnji pad aplikacije", if (crash == null) tr("Nema grešaka") else tr("Pogledaj"), valueColor = if (crash == null) C.Green else C.Amber) { if (crash != null) dlg = "crash" }
        SetRow("Memorija", "slobodno na uređaju", String.format("%.1f GB", freeGb), valueColor = C.Green) {}
        SetSection("Uređaj")
        SetRow("Model", "i Android verzija", android.os.Build.MODEL + " · Android " + android.os.Build.VERSION.RELEASE) {}
        SetRow("Keš memorija", "slike, EPG, privremeni podaci", "$cacheMb MB", "▼") { dlg = "cache" }
        SetRow("Log za podršku", "sačuvaj na USB ili u folder", tr("Izvezi"), "▼") { dlg = "log" }
    }
    when (dlg) {
        "lic" -> Popup("Licence", { dlg = null }) {
            listOf("Jetpack Compose · Apache 2.0", "Media3 / ExoPlayer · Apache 2.0", "Coil · Apache 2.0", "OkHttp · Apache 2.0", "Lucide ikone · ISC", "Inter font · OFL",
                "TMDB · This product uses the TMDB API but is not endorsed or certified by TMDB.", "PRETORIAN · MIT · github.com/KarlAgathon/PRETORIAN").forEach { T(it, 13.sp, C.Soft, maxLines = 2) }  // i18n-ignore
        }
        "priv" -> Popup("Privatnost", { dlg = null }) {
            T("Playliste, lozinke, PIN, favoriti i gledano čuvaju se samo na ovom uređaju. Na internet idu samo zahtjevi prema tvom provideru, slike filmova (TMDB) i, ako ih uključiš, Google Drive i AI pretraga.", 13.sp, C.Soft, maxLines = 8)
        }
        "crash" -> Popup("Izvještaj o greškama", { dlg = null }, width = 760.dp) {
            T((crash ?: "").take(1500), 11.sp, C.Soft, maxLines = 24)
            OptionRow("Obriši izvještaj", R.drawable.lc_trash_2) { java.io.File(ctx.filesDir, "last_crash.txt").delete(); dlg = null }
        }
        "log" -> FileBrowser("Izaberi folder", emptySet(), pickDir = true, onDismiss = { dlg = null }) { dir ->
            try {
                val log = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "3000")).inputStream.bufferedReader().readText()
                val f = java.io.File(dir, "pretorian_log_" + System.currentTimeMillis() / 1000 + ".txt")
                f.writeText("PRETORIAN " + BuildInfo.VERSION + " · " + android.os.Build.MODEL + " · Android " + android.os.Build.VERSION.RELEASE + "\n\n" + (crash ?: "") + "\n\n" + log)
                Toast.makeText(ctx, trf("Spremljeno: %s", f.path), Toast.LENGTH_LONG).show()
            } catch (e: Exception) { Toast.makeText(ctx, tr("U taj folder se ne može pisati. Izaberi drugi ili PRETORIAN folder."), Toast.LENGTH_LONG).show() }
            dlg = null
        }
        "cache" -> Popup("Keš memorija", { dlg = null }) {
            OptionRow("Očisti keš", R.drawable.lc_trash_2, sub = "slike i privremeni podaci se ponovo učitaju") {
                scope.launch(kotlinx.coroutines.Dispatchers.IO) { ctx.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
                cacheMb = 0; dlg = null; Toast.makeText(ctx, tr("Keš je očišćen"), Toast.LENGTH_SHORT).show()
            }
        }
        else -> {}
    }
}

/** A QR code as a picture (black on white). */
fun qrBitmap(text: String, px: Int): androidx.compose.ui.graphics.ImageBitmap? = try {
    val m = com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, px, px, mapOf(com.google.zxing.EncodeHintType.MARGIN to 1))
    val b = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.RGB_565)
    for (x in 0 until px) for (y in 0 until px) b.setPixel(x, y, if (m.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    b.asImageBitmap()
} catch (e: Exception) { null }
