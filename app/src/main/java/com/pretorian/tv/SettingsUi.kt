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

private data class Section(val id: String, val icon: Int, val title: String, val sub: String)

private val SECTIONS = listOf(
    Section("profile", R.drawable.lc_users, "Profili i Google Drive", "Ko gleda, prijava, sinhronizacija"),
    Section("playlists", R.drawable.lc_list, "Playliste", "Dodaj, aktiviraj, ažuriraj"),
    Section("groups", R.drawable.lc_layers, "Grupe i kanali", "Redoslijed, nazivi, sakrivanje, PIN"),
    Section("language", R.drawable.lc_languages, "Jezik", "Meniji, dugmad i poruke"),
    Section("look", R.drawable.lc_palette, "Izgled", "Tema, boja, prozirnost, veličine"),
    Section("media", R.drawable.lc_captions, "Filmovi i titlovi", "TMDB, IMDb, titlovi, jezici"),
    Section("sport", R.drawable.lc_trophy, "Sport i AI", "Država, AI za prenose"),
    Section("reminders", R.drawable.lc_bell, "Podsjetnici", "Emisije, utakmice, obavještenja"),
    Section("parental", R.drawable.lc_shield_check, "Roditelji i jednostavni mod", "PIN, jednostavni mod"),
    Section("backup", R.drawable.lc_archive, "Backup i vraćanje", "Spasi sve postavke u fajl"),
    Section("about", R.drawable.lc_info, "O aplikaciji", "Verzija i licence"),
    Section("update", R.drawable.lc_download, "Ažuriranje", "Provjeri i instaliraj novu verziju"),
)

@Composable
fun SettingsScreen(nav: Nav) {
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
        Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 30.dp, vertical = 24.dp)) {
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
    } else Page("Playliste", if (multi) "Uključi jednu ili više. Gornja uključena je otvorena, ostale su sklopljene." else "Aktivna je samo jedna. OK na drugoj je aktivira.") {
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
            if (pl.type == SourceType.XTREAM) try { Repo.catalog(pl, "vod").size } catch (e: Exception) { -1 } else -1,
            if (pl.type == SourceType.XTREAM) try { Repo.catalog(pl, "series").size } catch (e: Exception) { -1 } else -1)
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
        OptionRow(s.name, R.drawable.lc_clock, sub = status, value = tr(if (s.on) "Uključen" else "Isključen"),
            color = if (s.error != null) C.Danger else null) { menu = s }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button("Dodaj EPG", R.drawable.lc_plus) { add = true }
        if (list.isNotEmpty()) Button(if (updating) "Ažuriram…" else "Ažuriraj sve EPG-ove", R.drawable.lc_refresh_cw) {
            if (updating) return@Button
            updating = true
            scope.launch { list.forEach { try { EpgSources.refresh(it) } catch (_: Exception) {} }; Repo.clear(); updating = false }
        }
    }
    if (add) TextDialog("Dodaj EPG", "XMLTV link (http://…/epg.xml)", onDismiss = { add = false }) { v ->
        add = false
        if (v.isNotBlank()) { val s = EpgSources.add(v, pl?.id); scope.launch { EpgSources.refresh(s); Repo.clear() } }
    }
    edit?.let { s ->
        TextDialog("Uredi EPG", "XMLTV link (http://…/epg.xml)", s.url, onDismiss = { edit = null }) { v ->
            edit = null
            if (v.isNotBlank()) { val n = s.copy(url = v.trim(), name = EpgSources.nameOf(v), channels = -1, error = null); EpgSources.update(n); scope.launch { EpgSources.refresh(n); Repo.clear() } }
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
    var tab by remember { mutableIntStateOf(if (old?.type == SourceType.M3U) 1 else 0) }
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
        Label("Raspored")
        Segmented(listOf("Klasični", "New Wave", "New Age"), Look.layout) { Look.layout = it; save() }
        T(when (Look.layout) {
            0 -> "Klasični: meni lijevo, kategorije u koloni."
            1 -> "New Wave: filmovi i serije preko cijelog ekrana, kategorije gore, pomoć za daljinski na dnu."
            else -> "New Age: meni gore, redovi postera po kategorijama, velike kartice epizoda."
        }, 12.sp, C.Muted, maxLines = 2)
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
        Segmented(Look.THEMES.map { it.second }, Look.THEMES.indexOfFirst { it.first == Look.theme }.coerceAtLeast(0)) { Look.theme = Look.THEMES[it].first; save() }
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
        Segmented(listOf("Moderne", "Puna boja", "Glass", "Obris"), listOf(3, 0, 1, 2).indexOf(Look.tileStyle).coerceAtLeast(0)) { Look.tileStyle = listOf(3, 0, 1, 2)[it]; save() }
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
            1 -> "Prvi OK na kanalu pušta malu sliku, drugi OK preko cijelog ekrana. Dok listaš, ništa se ne pušta."
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
        Page("Filmovi i titlovi", "Ključevi su besplatni i čuvaju se samo na ovom uređaju.") {
            Label("Podaci o filmovima")
            OptionRow("TMDB API ključ", R.drawable.lc_key_round, "Posteri, opis na tvom jeziku, glumci, trailer · themoviedb.org › Settings › API",
                value = masked(Prefs.tmdbKey)) { edit = "tmdb" }
            if (Prefs.tmdbKey.isNotBlank()) OptionRow("Provjeri TMDB ključ", R.drawable.lc_check, value = tmdbStatus) {
                tmdbStatus = "Provjeravam…"; scope.launch { tmdbStatus = Tmdb.test()?.let { trf("Ne radi: %s", it) } ?: tr("Radi ✓") }
            }
            OptionRow("OMDb API ključ (IMDb ocjena)", R.drawable.lc_key_round, "omdbapi.com › API Key › FREE", value = masked(Prefs.omdbKey)) { edit = "omdb" }
            OptionRow("Jezik opisa filmova", R.drawable.lc_globe, "Ako opisa nema na tom jeziku, prikazuje se engleski",
                value = if (Prefs.metaLang == "auto") tr("Kao jezik aplikacije") else Langs.label(Prefs.metaLang)) { edit = "metalang" }
            Label("Titlovi")
            OptionRow("OpenSubtitles API ključ", R.drawable.lc_key_round, "opensubtitles.com › Profil › API consumers", value = masked(Prefs.osKey)) { edit = "os" }
            OptionRow("OpenSubtitles korisnik (opciono)", R.drawable.lc_user, "Više preuzimanja dnevno", value = Prefs.osUser.ifBlank { "Nije upisan" }) { edit = "osuser" }
            if (Prefs.osUser.isNotBlank()) OptionRow("OpenSubtitles lozinka", R.drawable.lc_lock, value = if (Prefs.osPass.isBlank()) "Nije upisana" else "••••••") { edit = "ospass" }
            OptionRow("SubDL API ključ", R.drawable.lc_key_round, "subdl.com › Panel › API", value = masked(Prefs.subdlKey)) { edit = "subdl" }
            OptionRow("Jezici titla", R.drawable.lc_captions, value = langList(Prefs.subLangs)) { edit = "sublangs" }
            ToggleRow("Automatski uključi titl", Prefs.autoSubs, R.drawable.lc_captions,
                "Ugrađeni titl na tvom jeziku se sam uključi kad film krene") { Prefs.autoSubs = it; rev++ }
            Label("Plejer")
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
