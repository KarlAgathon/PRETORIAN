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

private val SECTIONS = listOf(
    Section("profile", R.drawable.lc_users, "Profil", "Ko gleda, dječiji profil"),
    Section("playlists", R.drawable.lc_list, "Playliste", "Dodaj, aktiviraj, ažuriraj"),
    Section("groups", R.drawable.lc_layers, "Grupe i kanali", "Redoslijed, nazivi, sakrivanje, PIN"),
    Section("language", R.drawable.lc_languages, "Jezik", "Meniji, dugmad i poruke"),
    Section("look", R.drawable.lc_palette, "Izgled", "Tema, boja, prozirnost, veličine"),
    Section("media", R.drawable.lc_play, "Plejer", "Zvuk, titlovi, eksterni plejer"),
    Section("sport", R.drawable.lc_trophy, "Sport i AI", "Država, AI za prenose"),
    Section("reminders", R.drawable.lc_bell, "Podsjetnici", "Emisije, utakmice, obavještenja"),
    Section("parental", R.drawable.lc_shield_check, "Roditelji i jednostavni mod", "PIN, jednostavni mod"),
    Section("backup", R.drawable.lc_archive, "Backup i vraćanje", "Spasi sve postavke u fajl"),
    Section("update", R.drawable.lc_download, "Ažuriranje", "Provjeri i instaliraj novu verziju"),
    Section("about", R.drawable.lc_info, "O aplikaciji", "Verzija i licence"),
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
                    Toggle(pl.active, height = 24.dp)
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
                Watch.removePlaylist(ctx, pl.id); Store.savePlaylists(ctx, rest)
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
    if (Look.age && old == null && Store.playlists(ctx).isEmpty() && Welcome.tab < 0) { WelcomeScreen(nav); return }
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
    var live by remember { mutableStateOf(old?.live ?: true) }
    var vod by remember { mutableStateOf(old?.vod ?: true) }
    var series by remember { mutableStateOf(old?.series ?: true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        Header(if (old != null) "Uredi playlistu" else "Dodaj playlistu")
        Column(Modifier.fillMaxSize().padding(horizontal = 36.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                            if (old != null) {
                                Store.savePlaylists(ctx, Store.playlists(ctx).map { if (it.id == old.id) pl else it })
                                Toast.makeText(ctx, tr("Spremljeno"), Toast.LENGTH_SHORT).show()
                                EditPlaylist.target = null; Repo.clear(); Repo.dropCatalogs(); nav.playlistsChanged(); nav.back()
                            } else {
                                Store.savePlaylists(ctx, Store.playlists(ctx) + pl.copy(active = true))
                                Toast.makeText(ctx, if (pl.type != SourceType.M3U) tr("Povezano ✓") else trf("Učitano: %s", info), Toast.LENGTH_LONG).show()
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
        OptionRow("Mod programa", R.drawable.lc_gauge2, sub = trf("Ocjena uređaja %s/100 · %s GB RAM", Perf.score, String.format("%.1f", Perf.ramGb)),
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
            Label("Opisi filmova i serija")
            OptionRow("TMDB ključ", R.drawable.lc_film, sub = tr("opisi, plakati i glumci na tvom jeziku, i kad ih server nema · besplatno na themoviedb.org"),
                value = if (Prefs.tmdbKey.isBlank()) tr("Nije upisan") else (if (Prefs.str("tmdb_key").isBlank()) tr("ugrađen") else masked(Prefs.str("tmdb_key")))) { edit = "tmdb" }
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
                "tmdb" -> "TMDB API ključ" to Prefs.str("tmdb_key")
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

internal val COUNTRIES = listOf("" to "Automatski (po uređaju)", "DE" to "Njemačka", "AT" to "Austrija", "CH" to "Švicarska", "BA" to "Bosna i Hercegovina",
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
                value = if (cur.isBlank()) tr("Automatski (po uređaju)") + " · " + Sports.country else cur.split(',').joinToString(", ") { c -> tr(COUNTRIES.firstOrNull { it.first == c }?.second ?: c) }) { pickCountry = true }
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
            val on = Prefs.str("sport_country").split(',').filter { it.isNotBlank() }.toMutableSet()
            OptionRow(name, if (if (code.isBlank()) on.isEmpty() else code in on) R.drawable.lc_check else R.drawable.lc_map_pin) {
                // several countries: OK ticks or unticks; "Automatski" clears them
                if (code.isBlank()) on.clear() else if (code in on) on.remove(code) else on.add(code)
                Prefs.put("sport_country", on.joinToString(",")); Sports.lastCombined.clear(); rev++
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
    Page("Profili", "Svaki profil ima svoje favorite, gledano, izgled, jezik i grupe. Playliste su zajedničke.") {
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
    }
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
/** Every option is always there (Lite only saves the box, it hides nothing); only simple mode has no Settings. */
object SetMode {
    val advanced: Boolean get() = true
    @Suppress("UNUSED_PARAMETER") fun set(on: Boolean) {}
}

private val SECTIONS2 = listOf(
    Section("profile", R.drawable.lc_users, "Profil", "Ko gleda, dječiji profil"),
    Section("playlists", R.drawable.lc_list, "Playliste", "Dodaj, aktivne, EPG izvori"),
    Section("general", R.drawable.lc_settings, "Opće", "Napredne opcije, start, pozadina"),
    Section("home", R.drawable.lc_house, "Početna", "Redovi i redoslijed"),
    Section("look", R.drawable.lc_palette, "Izgled", "Tema, boja, veličina"),
    Section("language", R.drawable.lc_languages, "Jezik", "Meniji, opisi filmova"),
    Section("parental", R.drawable.lc_shield_check, "Modovi", "Jednostavni mod, PIN"),
    Section("kids", R.drawable.lc_lock, "Roditeljska kontrola", "Kategorije, vrijeme gledanja"),
    Section("media", R.drawable.lc_play, "Plejer", "Titlovi, zvuk, stream, kontrole"),
    Section("sport", R.drawable.lc_trophy, "Sports Center", "Sportovi, timovi, ko prenosi"),
    Section("saver", R.drawable.lc_moon, "Čuvar ekrana", "Sat, datum, pauzirano"),
    Section("diag", R.drawable.lc_gauge2, "Dijagnostika", "Gledano, protok, brzina, stabilnost"),
    Section("reminders", R.drawable.lc_bell, "Podsjetnici", "Emisije i utakmice"),
    Section("backup", R.drawable.lc_archive, "Backup", "Fajl, USB"),
    // always: Update second to last, About last
    Section("update", R.drawable.lc_download, "Ažuriranje", "Nova verzija"),
    Section("about", R.drawable.lc_info, "O aplikaciji", "Uređaj, kontakt, licence"),
)
private val BASIC = setOf("profile", "playlists", "general", "home", "look", "language", "parental", "kids", "update", "about")

/** Settings screen of the new look: sections on the left, the page on the right, sub-pages open inside. */
@Composable
fun SettingsScreen2(nav: Nav) {
    val ctx = LocalContext.current
    val list = SECTIONS2.filter { SetMode.advanced || it.id in BASIC }
    var section by remember { mutableStateOf((SettingsNav.section?.also { SettingsNav.section = null } ?: SettingsNav.last ?: if (Updater.available != null) "update" else "profile")
        .let { s -> if (list.any { it.id == s }) s else "profile" }) }
    var sub by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(section) { SettingsNav.last = section; sub = null }
    // a notification or a button elsewhere asks for a section while Settings is already open
    LaunchedEffect(SettingsNav.section) { SettingsNav.section?.let { s -> if (list.any { it.id == s }) section = s; SettingsNav.section = null } }
    var search by remember { mutableStateOf(false) }
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
    val railW by androidx.compose.animation.core.animateDpAsState(if (inPage) 74.dp else 270.dp, label = "rail")
    val slim = inPage
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(railW).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // search in all settings
            Focusable(modifier = Modifier.fillMaxWidth().height(40.dp).padding(bottom = 4.dp), onClick = { search = true }, shape = Pill,
                normal = Color.White.copy(alpha = 0.06f), focused = C.Red, zoom = 1f) { f ->
                Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Ico(R.drawable.lc_search, 14.dp, if (f) C.OnRed else C.Muted); if (!slim) T("Pretraži postavke", 13.sp, if (f) C.OnRed else C.Muted, maxLines = 1)
                }
            }
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
                        if (!slim && s.id == "update" && Updater.available != null) Box(Modifier.size(16.dp).clip(CircleShape).background(C.Danger), contentAlignment = Alignment.Center) {
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
            key(section, sub) {
                when (sub) {
                    "groups" -> GroupManager(nav)
                    "epg" -> { val epl = PlEdit.id?.let { id -> Store.playlists(ctx).firstOrNull { it.id == id } }
                        SetPage(if (epl != null) trf("EPG · %s", epl.name) else tr("EPG izvori"), "OK: meni · desno: uključi/ugasi · ▲ ▼ redoslijed") { EpgSourcesBlock(epl) } }
                    "plists" -> PlaylistsListV2(nav)
                    "lookmore" -> LookPage()
                    "player" -> MediaPage()
                    "device" -> DeviceV2()
                    else -> when (section) {
                        "playlists" -> PlaylistsV2(nav) { sub = it }
                        "profile" -> ProfilesV2()
                        "sport" -> SportV2()
                        "look" -> LookV2 { sub = it }
                        "language" -> LanguageV2()
                        "media" -> PlayerV2 { sub = it }
                        "parental" -> SimpleV2(nav)
                        "kids" -> ParentalV2()
                        "reminders" -> RemindersV2(nav)
                        "backup" -> BackupV2(nav)
                        "home" -> HomeV2()
                        "general" -> GeneralV2()
                        "update" -> UpdateV2()
                        "diag" -> DiagnosticsV2()
                        "saver" -> SaverV2()
                        else -> AboutV2(onSub = { sub = it }, onSection = { section = it })
                    }
                }
            }
        }
    }
    if (search) SettingsSearch(list.map { it.id }, onDismiss = { search = false }) { id -> search = false; section = id }
    LaunchedEffect(Unit) { try { first.requestFocus() } catch (_: Exception) {} }
}

/** Every option by name, with the section it lives in. */
private val SEARCH_INDEX = listOf(
    "playlists" to "Playliste, dodaj playlistu, Xtream, M3U, Stalker, EPG izvori, grupe i kanali, osvježi playlistu",
    "profile" to "Profil, ko gleda, profil za djecu",
    "home" to "Početna, redovi, nove epizode, nastavi gledati, novo dodano, nedavni kanali",
    "general" to "Opće, napredne opcije, početni ekran, start s boxom, zadnji kanal, potvrdi izlaz, rad u pozadini, User-Agent, UDP proxy",
    "kids" to "Roditeljska kontrola, sakrij kategorije, odrasli, XXX, LGBT, trans, zaključaj, vrijeme gledanja",
    "look" to "Izgled, tema, boja, veličina, ikone, mod programa, Lite, Full, oblik ivica",
    "language" to "Jezik, meniji, opisi filmova, prevod",
    "media" to "Plejer, titl, titlovi, veličina titla, kašnjenje titla, sync, zvuk, jezik zvuka, Dolby, passthrough, oporavak, prekid streama, bafer, rezolucija, preskok, nastavi gdje si stao, sljedeća epizoda, zamuti, trailer, eksterni plejer, file manager",
    "sport" to "Sports Center, sport, sportovi, takmičenja, država, reprezentacije, timovi, ko prenosi, AI pretraga, rezultati",
    "reminders" to "Podsjetnici, obavijest, utakmica, emisija, zvuk obavijesti",
    "parental" to "Modovi, jednostavni mod, PIN, promijeni PIN",
    "backup" to "Backup, vraćanje, USB, fajl, Download, automatski backup, vrati na početak",
    "saver" to "Čuvar ekrana, sat, datum, pauzirano, screensaver",
    "diag" to "Dijagnostika, brzina interneta, VPN, protok, gledano, uptime, keš, zapis ažuriranja, zapis streama",
    "update" to "Ažuriranje, nova verzija, beta, stable, kanal, pauza, potpis, vrati prethodnu verziju, USB",
    "about" to "O aplikaciji, o uređaju, ocjena boxa, kontakt, licence, privatnost, pomoć",
)

@Composable
private fun SettingsSearch(allowed: List<String>, onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    var q by remember { mutableStateOf<String?>(null) }
    if (q == null) { TextDialog("Pretraži postavke", "npr. titl, bafer, PIN", onDismiss = onDismiss) { q = it.trim() }; return }
    val words = q!!.lowercase().split(' ').filter { it.isNotBlank() }
    val hits = SEARCH_INDEX.filter { (id, text) -> id in allowed && words.all { w -> tr(text).lowercase().contains(w) || text.lowercase().contains(w) } }
    Popup(trf("Rezultati za „%s“", q!!), onDismiss, width = 640.dp) {
        if (hits.isEmpty()) T("Ništa nije nađeno. Uključi Napredne opcije za sve postavke.", 14.sp, C.Muted, maxLines = 2)
        hits.forEach { (id, text) ->
            val name = SECTIONS2.firstOrNull { it.id == id }?.title ?: id
            val found = tr(text).split(", ").filter { part -> words.any { part.lowercase().contains(it) } }.take(4).joinToString(" · ")
            OptionRow(name, SECTIONS2.firstOrNull { it.id == id }?.icon, sub = found) { onOpen(id) }
        }
    }
}

/* ---------------------------------------------------------------- Početna */

@Composable
private fun HomeV2() {
    key(HomeRows.rev) {
        SetPage("Početna", "OK uključi ili isključi red · ▲ ▼ pomjeri red") {
            val all = HomeRows.all()
            all.forEachIndexed { i, id ->
                val label = HomeRows.ALL.first { it.first == id }.second.let { if (Perf.lite && id == "new") tr(it) + " · " + tr("isključeno u Lite modu") else it }
                val on = HomeRows.isOn(id)
                // one compact line: the row (OK = on/off) and its own ▲ ▼ buttons; Left always goes back to the list of sections
                Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Focusable(modifier = Modifier.weight(1f).fillMaxHeight().border(1.dp, C.Line, Rnd(12.dp)),
                        onClick = { SetFocus.last = "home:$id"; SetFocus.pending = true; if (!(Perf.lite && id == "new")) HomeRows.toggle(id) }, focusRequester = setFocusFr("home:$id"),
                        onFocus = { SetFocus.last = "home:$id" }, shape = Rnd(12.dp), normal = Color.Transparent, focused = C.Red, zoom = 1f) { f ->
                        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            T((i + 1).toString(), 14.sp, if (f) C.OnRed else C.Muted, FontWeight.Bold)
                            T(label, 15.sp, if (f) C.OnRed else C.Text, FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                            Toggle(on, f, height = 22.dp)
                        }
                    }
                    listOf(-1 to "▲", 1 to "▼").forEach { (d, g) ->  // i18n-ignore
                        val can = if (d < 0) i > 0 else i < all.lastIndex
                        Focusable(modifier = Modifier.size(48.dp).border(1.dp, C.Line, Rnd(12.dp)),
                            onClick = { if (can) { HomeRows.move(id, d); SetFocus.last = "home:$id:$d"; SetFocus.pending = true } },
                            focusRequester = setFocusFr("home:$id:$d"), shape = Rnd(12.dp), normal = Color.Transparent, focused = C.Red, zoom = 1f) { f ->
                            T(g, 14.sp, if (!can) C.Line else if (f) C.OnRed else C.Text, FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
                        }
                    }
                }
            }
            ResetSection("Početna", listOf("home_rows", "home_off")) { HomeRows.rev++ }
        }
    }
}

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
        focusRequester = fr, shape = Rnd(12.dp), normal = C.Rail, focused = C.Red, zoom = 1.01f) { f ->
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    T(title, 15.sp, if (f) C.OnRed else C.Text, FontWeight.SemiBold, maxLines = 1)
                    if (new) Box(Modifier.clip(Rnd(4.dp)).background(C.Green.copy(alpha = 0.2f)).padding(horizontal = 4.dp)) { T("NOVO", 8.sp, C.Green, FontWeight.Bold) }
                }
                if (sub != null) T(sub, 12.sp, if (f) C.OnRed.copy(alpha = 0.65f) else C.Muted, maxLines = 2)
            }
            if (value != null || arrow.isNotEmpty()) Row(Modifier.clip(Rnd(9.dp)).background(if (f) Color(0x14000000) else C.Card)
                .border(1.dp, if (f) Color(0x26000000) else C.Line, Rnd(9.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                if (value != null) T(value, 14.sp, if (f) C.OnRed else (valueColor ?: C.Text), FontWeight.SemiBold, maxLines = 1, modifier = Modifier.widthIn(max = 220.dp))
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
        shape = Rnd(12.dp), normal = C.Rail, focused = C.Red, zoom = 1.01f) { f ->
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    T(title, 15.sp, if (f) C.OnRed else C.Text, FontWeight.SemiBold, maxLines = 1)
                    if (new) Box(Modifier.clip(Rnd(4.dp)).background(Color(0xFFFBBF24)).padding(horizontal = 4.dp)) { T("NOVO", 8.sp, Color(0xFF111111), FontWeight.Bold) }
                }
                if (sub != null) T(sub, 12.sp, if (f) C.OnRed.copy(alpha = 0.65f) else C.Muted, maxLines = 2)
            }
            Toggle(cur, f, height = if (sub != null) 24.dp else 22.dp)
        }
    }
}

/** Slider: the row shows the value; OK opens a small window where ◀ ▶ change it, OK or Back closes it.
 *  (◀ ▶ on the row itself move between rows, like everywhere else.) */
@Composable
fun SetSlider(title: String, sub: String?, value: Int, min: Int, max: Int, step: Int, fmt: (Int) -> String = { "$it" }, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var cur by remember(value) { mutableIntStateOf(value) }
    val id = "sl:$title"
    val fr = setFocusFr(id)
    @Composable
    fun Bar(v: Int, f: Boolean, h: androidx.compose.ui.unit.Dp) {
        Box(Modifier.fillMaxWidth().height(h).clip(Pill).background(if (f) Color(0x33000000) else C.Line)) {
            Box(Modifier.fillMaxWidth(((v - min).toFloat() / (max - min).coerceAtLeast(1)).coerceIn(0.02f, 1f)).fillMaxHeight().background(Color(0xFF2563EB)))
        }
    }
    Focusable(modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).border(1.dp, C.Line, Rnd(12.dp)),
        onClick = { SetFocus.last = id; open = true }, onFocus = { SetFocus.last = id }, focusRequester = fr,
        shape = Rnd(12.dp), normal = C.Rail, focused = C.Red, zoom = 1.01f) { f ->
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(title, 15.sp, if (f) C.OnRed else C.Text, FontWeight.SemiBold, modifier = Modifier.weight(1f))
                T(fmt(cur), 15.sp, if (f) C.OnRed else Color(0xFF60A5FA), FontWeight.Bold)
            }
            Bar(cur, f, 4.dp)
            if (sub != null) T(sub, 11.sp, if (f) C.OnRed.copy(alpha = 0.65f) else C.Muted, maxLines = 1)
        }
    }
    if (open) Popup(title, { open = false; SetFocus.tick++ }, width = 520.dp) {
        val pfr = remember { FocusRequester() }
        Focusable(onClick = { open = false; SetFocus.tick++ }, focusRequester = pfr, shape = Rnd(14.dp), normal = C.Card, focused = C.CardHi, zoom = 1f,
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

private val THEME_SWATCH = mapOf("vece" to (Color(0xFF0C1428) to Color(0xFF1C2A4D)), "bw" to (Color(0xFF050505) to Color(0xFF1A1A1A)),
    "staklo" to (Color(0xFFF7F7F8) to Color.White), "pijesak" to (Color(0xFF1E140A) to Color(0xFF8A6A3A)), "dark" to (Color(0xFF0B0D12) to Color(0xFF1A1F2A)),
    "light" to (Color(0xFFF2F3F6) to Color.White), "glass" to (Color(0xFF1B3346) to Color(0x66FFFFFF)), "dew" to (Color(0xFF221B36) to Color(0xFF3A2F55)),
    "liquid" to (Color(0xFF243B52) to Color(0x66FFFFFF)), "classic" to (Color(0xFF1C2735) to Color(0xFF3B4E66)),
    "oled" to (Color(0xFF000000) to Color(0xFFD4A537)), "kino" to (Color(0xFF111214) to Color(0xFFE11D2A)), "papir" to (Color(0xFFEEF0F3) to Color.White), "reddawn" to (Color(0xFF18181B) to Color(0xFFDC2626)))

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
    SetPage("Uredi playliste", "OK na playlisti: meni s opcijama.") {
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
                    Kicker("Aktivan jezik", Color(0xFF93C5FD))
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
            // these themes have their own colour (white or black focus): the dots would change nothing
            if (Look.theme != "glass") T("Ova tema ima svoju boju fokusa. Boje rade u temi Glass.", 12.sp, C.Muted, maxLines = 2)
            else ColorDots { rev++ }
            val transp = (100 - Look.opacity).coerceIn(0, 50)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { SetSlider("Prozirnost", "0 – 50 %, korak 5 %", transp, 0, 50, 5, { "$it %" }) { Look.opacity = 100 - it; save() } }
                Box(Modifier.weight(1f)) { SetSlider("Svjetlina boje", "tamnije ◀ ▶ svjetlije, korak 5 %", Look.shade, -2, 2, 1, { (if (it > 0) "+" else "") + "${it * 5} %" }) { Look.shade = it; save() } }
            }
            SetSection("Veličina i prikaz")
            SetDrop("Veličina", "tekst, pločice i plakati zajedno", listOf("Mala", "Srednja", "Velika"), Look.posterSize) {
                Look.posterSize = it; Look.tileSize = it; Look.rowSize = it; save(); rev++
            }
            SetSlider("Veličina svega", "slova, polja i slike zajedno · OK pa ◀ ▶", Look.uiScale, 0, 100, 5) { Look.uiScale = it; save() }
            SetDrop("Mala slika kanala", "pregled dok stojiš na kanalu", listOf("Automatski", "Na OK", "Isključena"), 2 - Look.previewMode) {
                Look.previewMode = 2 - it; Look.preview = Look.previewMode > 0; save(); rev++
            }
            SetDrop("Veličina male slike", "u listi kanala · veća slika = više opisa emisije", listOf("Mala", "Srednja", "Velika"), Prefs.long("tv_mini", 1).toInt().coerceIn(0, 2)) {
                Prefs.put("tv_mini", it.toLong()); rev++
            }
            SetDrop("Prikaz liste kanala", "u TV-u i u listi u playeru · TiviMate mod = grupe lijevo, kanali s programom po satima", listOf("Kompaktni", "Normalni", "Veliki", "TiviMate mod"), ListStyle.mode, new = true) {
                Prefs.put("tv_density", it.toLong())
                Prefs.put("tv_view", if (it == 3) "guide" else "list"); rev++
            }
            SetDrop("Broj kanala na ekranu", "Automatski = prema prikazu liste · uvijek cijeli redovi", listOf("Automatski", "4", "5", "6", "7", "8", "9"), Prefs.long("tv_rows", 0).toInt().let { n -> if (n in 4..9) n - 3 else 0 }) {
                Prefs.put("tv_rows", if (it == 0) 0L else (it + 3).toLong()); rev++
            }
            SetDrop("Boja odabranog", "u svim temama · blaga = ne bliješti", listOf("Blaga", "Jaka"), Prefs.long("look_focus_soft", 0).toInt().coerceIn(0, 1), new = true) {
                Prefs.put("look_focus_soft", it.toLong()); rev++
            }
            SetDrop("Odabrani kanal", "kako se ističe red na kojem stojiš", listOf("Okvir (mirno)", "Puna boja"), Prefs.long("tv_focus", 0).toInt().coerceIn(0, 1), new = true) {
                Prefs.put("tv_focus", it.toLong()); rev++
            }
            SetSwitch("Emisije široke po trajanju", "kratka epizoda uža, dug film širi", Prefs.bool("tv_prop", true)) { Prefs.put("tv_prop", it); rev++ }
            SetSwitch("Oznaka arhive ↺", "kanali na kojima možeš gledati unazad", Prefs.bool("tv_archmark", true)) { Prefs.put("tv_archmark", it); rev++ }
            SetSwitch("Skok brojem kanala", "ukucaj broj na daljinskom (npr. 7) i odmah si na kanalu", Prefs.bool("tv_digits", true)) { Prefs.put("tv_digits", it); rev++ }
            SetDrop("Upute na ekranu", "npr. ◀ ▶ program · OK podsjeti me", listOf("Nekoliko sekundi pa nestanu", "Uvijek", "Isključene"), Prefs.long("tv_hints", 0).toInt().coerceIn(0, 2), new = true) {
                Prefs.put("tv_hints", it.toLong()); rev++
            }
            SetDrop("Oznake u gornjoj traci", "Lite/Full i A/S pored Postavki", listOf("Samo ikone", "Obojeni kvadrati"), Prefs.long("bar_badges", 0).toInt().coerceIn(0, 1), new = true) {
                Prefs.put("bar_badges", it.toLong()); rev++
            }
            SetDrop("TV se otvara kao", null, listOf("Lista kanala", "TV vodič"), if (Prefs.str("tv_view", "list") == "guide") 1 else 0) {
                Prefs.put("tv_view", if (it == 1) "guide" else "list"); rev++
            }
            var stdMsg by remember { mutableStateOf<String?>(null) }
            SetRow("Snimi kao standardni prikaz", "ovaj izgled za svaku novu instalaciju i profil · fajl u Download/PRETORIAN", stdMsg ?: tr("Snimi"), new = true) {
                stdMsg = try { PublicFiles.save(ctx, "standardni_prikaz.json", StandardView.snapshot(ctx).toByteArray()); tr("Snimljeno ✓") } catch (e: Exception) { tr("Nije uspjelo") }
            }
            SetDrop("Mod programa", trf("Ocjena uređaja %s/100 · %s GB RAM", Perf.score, String.format("%.1f", Perf.ramGb)),
                listOf(tr("Automatski") + " (" + tr(if (Perf.weak) "Lite" else "Full") + ")", "Lite", "Full"), Perf.mode,
                note = tr("Lite isključuje: novo dodano na Početnoj, velike slike u pozadini, providne kartice, prelaze, malu sliku kanala (pušta na OK), karticu „Pratiš“ i AI pretragu ko prenosi. Rezultati svakih 10 min, nove epizode svakih 6 h, bafer 32 MB, čuvar ekrana samo sat. Sve se može ručno uključiti.")) { Perf.set(it); rev++ }
            if (Perf.lite) {
                SetSwitch("Lite: mala slika kanala", "pregled dok stojiš na kanalu", Prefs.bool("lite_preview")) { Prefs.put("lite_preview", it); rev++ }
                SetSwitch("Lite: AI pretraga ko prenosi", "traži TV kuće na internetu", Prefs.bool("lite_ai_tv")) { Prefs.put("lite_ai_tv", it); rev++ }
            }
            if (SetMode.advanced) {
                SetDrop("Oblik ivica", null, listOf("Zaobljene", "Blago zaobljene", "Oštre"), Look.corner) { Look.corner = it; save(); rev++ }
                val sets = IconSets.BUILTIN
                SetDrop("Set ikona", null, sets.map { it.name }, sets.indexOfFirst { it.id == Look.iconSet }.coerceAtLeast(0)) { i ->
                    Look.iconSet = sets[i].id; Look.iconStyle = if (sets[i].styles.size > 1) 1.coerceAtMost(sets[i].styles.lastIndex) else 0; save(); rev++
                }
                SetSwitch("Okvir oko grupa", "tanka linija oko TV grupa", Look.groupFrame) { Look.groupFrame = it; save() }
            }
        }
    }
}

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
            SetSection("Stream i bafer")
            SetSwitch("Automatski oporavak", "zamrznut ili prekinut stream se tiho ponovo spoji", Recover.on) { Prefs.put("recover_on", it); rev++ }
            if (Recover.on) SetDrop("Reaguj nakon", if (Recover.choice == 0) tr("Automatski") + ": " + (NetProfile.autoWait / 1000) + " s · " + NetProfile.why() else "bez slike, vremena ili podataka",
                Recover.OPTIONS, Recover.choice, new = true) { Prefs.put("recover_wait2", it.toLong()); rev++ }
            SetDrop("Bafer (video unaprijed)", if (Buffer.choice == 0) tr("Automatski") + ": " + NetProfile.autoBuffer + " MB · " + NetProfile.why() else "veći = manje zastoja, više memorije",  // i18n-ignore
                Buffer.OPTIONS, Buffer.choice, new = true, note = tr("Vrijedi od sljedećeg pokretanja kanala ili filma.")) { Prefs.put("buffer_mb", it.toLong()); rev++ }
            SetDrop("Rezolucija u uglu slike", null, listOf("Isključeno", "Na početku", "Uvijek"), Look.resBadge) { Look.resBadge = it; Look.save(ctx); rev++ }
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
                val fm = listOf("" to tr("Pitaj svaki put"), "builtin" to tr("PRETORIAN (ugrađeni)")) + FileAccess.pickers(ctx).map { it.pkg to it.label }
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
    val scope = rememberCoroutineScope()
    var rev by remember { mutableIntStateOf(0) }
    var keyDlg by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var more by remember { mutableStateOf(false) }
    var teamDlg by remember { mutableStateOf(false) }
    val main = listOf("football" to "Fudbal", "basketball" to "Košarka", "tennis" to "Tenis", "volleyball" to "Odbojka")
    val others = (Sports.SPORTS + Sports.MORE_SPORTS).filter { o -> main.none { it.first == o.first } }
    fun changed() { Sports.lastCombined.clear(); rev++ }
    key(rev) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // ---------------------------------------------------------- left: what you follow
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                T("Sports Center", 20.sp, C.Text, FontWeight.Bold)
                // one drop-down for the sports: only how many are chosen on the row; All / None / each, stays open until Back
                val en = csv("sport_enabled", "football").toMutableSet()
                val allSports = Sports.SPORTS + Sports.MORE_SPORTS
                SetMulti("Sportovi", null, allSports.map { it.second }, allSports.indices.filter { allSports[it].first in en }.toSet(), none = true) { s2 ->
                    Prefs.put("sport_enabled", (if (s2.isEmpty()) setOf(0) else s2).sorted().joinToString(",") { allSports[it].first }); changed()
                }
                val conts = Sports.CONTINENTS
                val cOn = csv("sport_cont", "eu").map { if (it == "am") "na" else it }
                SetMulti("Kontinenti", "svjetska takmičenja uvijek", conts.map { it.second }, conts.indices.filter { conts[it].first in cOn }.toSet(), all = false) { s2 ->
                    Prefs.put("sport_cont", (if (s2.isEmpty()) setOf(0) else s2).sorted().joinToString(",") { conts[it].first }); changed()
                }
                // several countries at once; none ticked = automatic (by the device)
                val picked = Prefs.str("sport_country").split(',').filter { it.isNotBlank() }.toSet()
                val opts = COUNTRIES.drop(1)
                SetMulti("Moja država", "ništa = po uređaju", opts.map { it.second }, opts.indices.filter { opts[it].first in picked }.toSet(), all = false,
                    note = tr("Ništa označeno = automatski (po uređaju)")) { s2 ->
                    Prefs.put("sport_country", s2.sorted().joinToString(",") { opts[it].first }); Sports.lastCombined.clear(); changed()
                }
                val nOpts = COUNTRIES.drop(1).filter { it.first in MyTeams.NATIONS }
                val nOn = Prefs.str("sport_nations").split(',').filter { it.isNotBlank() }.toSet()
                SetMulti("Moje reprezentacije", "igraju prve u Sportu", nOpts.map { it.second }, nOpts.indices.filter { nOpts[it].first in nOn }.toSet(), all = false) { s2 ->
                    Prefs.put("sport_nations", s2.sorted().joinToString(",") { nOpts[it].first }); MyTeams.rev++; changed()
                }
                val teams = Prefs.str("sport_teams").split(',').map { it.trim() }.filter { it.isNotBlank() }
                SetRow("Omiljeni timovi", "uvijek na vrhu", if (teams.isEmpty()) tr("Nijedan") else trf("%s tima", teams.size)) { teamDlg = true }
                SetSwitch("Automatski prati", "timove i reprezentacije", MyTeams.auto) { Prefs.put("sa_auto", it); MyTeams.rev++; rev++ }
                // the same switches as in Settings › Home ("Remove" on Home switches the first one off)
                SetSwitch("Utakmice danas na Početnoj", "„Ukloni“ na Početnoj ga isključi", HomeRows.isOn("today")) { HomeRows.setOn("today", it); rev++ }
                SetSwitch("Pratiš · uživo na Početnoj", "rezultat, kartoni, zadnji događaj", HomeRows.isOn("follow")) { HomeRows.setOn("follow", it); rev++ }
                SetSection("Obavijesti")
                val ks = GoalNote.KINDS
                SetMulti("Obavijesti", "za ono što pratiš", ks.map { it.second },
                    ks.indices.filter { GoalNote.on(ks[it].first) }.toSet(), all = false) { s2 -> ks.forEachIndexed { i, k -> Prefs.put("sa_" + k.first, i in s2) }; rev++ }
                SetSwitch("Podsjetnik prije početka", "za timove i utakmice koje pratiš", (Prefs.str("sport_team_remind", "1").toIntOrNull() ?: 1) > 0) {
                    Prefs.put("sport_team_remind", if (it) "1" else "0"); rev++
                }
                SetDrop("Osvježavanje", "dok je Sport otvoren", listOf("Svake minute", "Svake 3 min", "Svakih 5 min"),
                    listOf(1, 3, 5).indexOf(Prefs.str("sport_refresh", "1").toIntOrNull() ?: 1).coerceAtLeast(0)) { Prefs.put("sport_refresh", listOf(1, 3, 5)[it].toString()); rev++ }
                SetDrop("Mala slika utakmice", null, listOf("Na OK", "Isključena"), if (Prefs.bool("sport_preview", true)) 0 else 1) { Prefs.put("sport_preview", it == 0); rev++ }
                SetSection("Ko prenosi")
                val srcKeys = listOf("sport_src_rights", "sport_src_espn", "sport_src_ai")
                SetMulti("Izvori", "tvoji kanali (TV vodič) uvijek", listOf("Prava po ligi", "ESPN", "AI"),
                    srcKeys.indices.filter { Prefs.bool(srcKeys[it], true) }.toSet(), all = false) { s2 -> srcKeys.forEachIndexed { i, k -> Prefs.put(k, i in s2) }; changed() }
                var afDlg by remember { mutableStateOf(false) }
                SetRow("API-Football ključ", "brži rezultati uživo · besplatno 100 upita dnevno", if (ApiFootball.on) tr("upisan") else tr("nema"),
                    valueColor = if (ApiFootball.on) C.Green else null) { afDlg = true }
                if (afDlg) TextDialog("API-Football ključ", "ključ s dashboard.api-football.com", ApiFootball.key, onDismiss = { afDlg = false; SetFocus.tick++ }) { v ->
                    ApiFootball.key = v; afDlg = false; SportCache.clear(); rev++ }
                val pv = SportsAI.prov
                SetRow("AI ključ", pv.site, pv.name + " · " + tr(if (SportsAI.key.isNotBlank()) "upisan" else "nema"),
                    valueColor = if (SportsAI.key.isNotBlank()) C.Green else null) { keyDlg = true }
                SetDrop("AI", tr(if (pv.free) "besplatno" else "plaća se po upitu"), SportsAI.PROVIDERS.map { it.name },
                    SportsAI.PROVIDERS.indexOfFirst { it.id == SportsAI.provider }.coerceAtLeast(0)) { SportsAI.provider = SportsAI.PROVIDERS[it].id; status = null; rev++ }
                if (SportsAI.ready) SetRow(status ?: tr("Provjeri ključ"), null, arrow = "↻") {
                    status = tr("Provjeravam…"); scope.launch { status = SportsAI.test()?.let { trf("Ne radi: %s", it) } ?: tr("Radi ✓") }
                }
            }
            // ---------------------------------------------------------- right: competitions
            val en = csv("sport_enabled", "football")
            val raw = Prefs.str("sport_leagues", Sports.BIG.joinToString(","))
            val all = Sports.LEAGUES.map { it.abbr }
            val on: Set<String> = when (raw) { "-" -> emptySet(); "" -> all.toSet(); else -> raw.split(',').filter { it.isNotBlank() }.toSet() }
            fun save(x: Set<String>) { Prefs.put("sport_leagues", if (x.isEmpty()) "-" else if (x.size == all.size) "" else x.joinToString(",")); changed() }
            val groups = Sports.LEAGUE_GROUPS.filter { (_, abbrs) -> abbrs.any { a -> Sports.LEAGUES.firstOrNull { it.abbr == a }?.sport in en } }
            Column(Modifier.weight(1f).fillMaxHeight().glass(18.dp).verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(tr("Takmičenja") + (if (en.size == 1) " · " + tr(main.firstOrNull { it.first == en.first() }?.second ?: "") else ""), 16.sp, C.Text, FontWeight.Bold, modifier = Modifier.weight(1f))
                    T(trf("%s od %s uključeno", on.size, all.size), 10.sp, C.Muted)
                }
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SportChip("Velika takmičenja", on == Sports.BIG.toSet()) { save(Sports.BIG.toSet()) }
                    SportChip("Moja regija", on == (Sports.BIG + Sports.REGION_LEAGUES).toSet()) { save((Sports.BIG + Sports.REGION_LEAGUES).toSet()) }
                    SportChip("Sve", on.size == all.size) { save(all.toSet()) }
                    SportChip("Ništa", on.isEmpty()) { save(emptySet()) }
                }
                groups.forEach { (title, abbrs) ->
                    Kicker(title, modifier = Modifier.padding(top = 6.dp))
                    abbrs.forEach { a ->
                        val lg = Sports.LEAGUES.firstOrNull { it.abbr == a } ?: return@forEach
                        CheckRow(tr(lg.name), a in on) { save(if (a in on) on - a else on + a) }
                    }
                }
            }
        }
    }
    if (more) Popup("Ostali sportovi", { more = false; SetFocus.tick++ }) {
        val en = csv("sport_enabled", "football").toMutableSet()
        others.forEach { (id, label) ->
            CheckRow(tr(label), id in en) { if (id in en) en.remove(id) else en.add(id); Prefs.put("sport_enabled", en.joinToString(",")); Sports.lastCombined.clear(); more = false; rev++ }
        }
    }
    if (teamDlg) FavTeamsDialog { teamDlg = false; Sports.lastCombined.clear(); rev++; SetFocus.tick++ }
    if (keyDlg) TextDialog(trf("%s ključ", SportsAI.prov.name), trf("Ključ (%s)", SportsAI.prov.keyHint), SportsAI.key, onDismiss = { keyDlg = false; SetFocus.tick++ }) { v -> SportsAI.key = v; keyDlg = false; status = null; rev++ }
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
            SetDrop("Obavijest stoji", null, listOf("10 s", "20 s", "30 s"), listOf(10, 20, 30).indexOf(Reminders.popupSec).coerceAtLeast(1)) { Reminders.popupSec = listOf(10, 20, 30)[it]; rev++ }
            SetSwitch("Zvuk obavijesti", null, Prefs.bool("rem_sound", true), new = true) { Prefs.put("rem_sound", it) }
            SetSwitch("Utakmice koje pratiš", "same dobiju podsjetnik prije početka", Prefs.str("sport_team_remind", "1") != "0") { Prefs.put("sport_team_remind", if (it) "1" else "0"); rev++ }
            SetSection("Ostalo")
            SetDrop("Kad je aplikacija zatvorena", "ne radi na svim uređajima", listOf("Otvori PRETORIAN", "Ništa"), if (Reminders.wake) 0 else 1, new = true) { Reminders.wake = it == 0; rev++ }
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

/* ---------------------------------------------------------------- Jednostavni mod i PIN */

@Composable
private fun SimpleV2(nav: Nav) {
    val ctx = LocalContext.current
    var rev by remember { mutableIntStateOf(0) }
    var setup by remember { mutableStateOf<String?>(null) }
    val items = listOf("TV", "Filmovi", "Serije")
    val ids = listOf("tv", "vod", "series")
    key(rev) {
        SetPage("Modovi") {
            SetSection("Jednostavni mod")
            SetSwitch("Jednostavni mod", "početna samo s TV, Filmovi, Serije", Lock.simple) {
                if (it) { if (!Lock.hasPin()) setup = "simple" else { Lock.switchSimple(true); nav.root(Screen.Home) } } else { Lock.switchSimple(false); rev++ }
            }
            val on = csv("simple_items", "tv,vod,series")
            SetMulti("Šta se vidi", "lampicama biraš", items, ids.indices.filter { ids[it] in on }.toSet(), all = false) { s ->
                Prefs.put("simple_items", (if (s.isEmpty()) setOf(0) else s).sorted().joinToString(",") { ids[it] }); rev++
            }
            SetDrop("Izlaz iz jednostavnog moda", null, listOf("Traži PIN", "Bez PIN-a"), if (Prefs.bool("pin_simple_exit", true)) 0 else 1) { Prefs.put("pin_simple_exit", it == 0); rev++ }
            SetSection("PIN")
            if (Lock.isDefault()) Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(Rnd(12.dp)).background(C.Amber.copy(alpha = 0.16f))
                .border(1.dp, C.Amber.copy(alpha = 0.6f), Rnd(12.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Ico(R.drawable.lc_key_round, 18.dp, C.Amber)
                T("PIN je zadani 0000, promijeni ga", 14.sp, C.Amber, FontWeight.Bold, modifier = Modifier.weight(1f))
            }
            SetRow("PIN", if (Lock.isDefault()) "zadani PIN je 0000" else "promijeni ili postavi novi", tr(if (Lock.isDefault()) "0000" else "Postavljen ✓"),
                valueColor = if (Lock.isDefault()) C.Amber else C.Green) { setup = "pin" }
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
                listOf(30, 60, 120).forEach { sec -> val cur = General.sportLive == sec
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
            SetSwitch("Pokreni s boxom", "PRETORIAN se otvori kad se box upali", General.boot) { Prefs.put("gen_boot", it); rev++ }
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
    SetPage("Profil") {
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
        if (Profiles.askAtStart) SetDrop("Ne pitaj ako me nije bilo kraće od", "kratak izlazak iz aplikacije ne traži profil", Profiles.GRACE_NAMES,
            Profiles.GRACE.indexOf(Profiles.grace).coerceAtLeast(0)) { Profiles.grace = Profiles.GRACE[it]; Profiles.rev++ }
        val kid = Prefs.str("kids_profile")
        SetDrop("Profil za djecu", "ulazi odmah u jednostavni mod", listOf(tr("Nijedan")) + all.map { it.name },
            (all.indexOfFirst { it.id == kid } + 1).coerceAtLeast(0), new = true) { Prefs.put("kids_profile", if (it == 0) "" else all[it - 1].id); Profiles.rev++ }
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
    val found = remember(rev) { try { Backup.found(ctx) } catch (_: Exception) { emptyList() } }
    var done by remember { mutableStateOf<String?>(null) }
    key(rev) {
        SetPage("Backup i vraćanje") {
            SetSection("Napravi kopiju")
            val usbIn = Backup.usbDirs().isNotEmpty()
            val usb = Prefs.str("backup_where", "0") == "2" && usbIn
            // one OK: saved straight to the chosen place, no folder window
            SetRow("Napravi kopiju sada", done, arrow = "›", valueColor = C.Green) {
                done = try { trf("✓ Spremljeno u %s", Backup.saveNow(ctx, usb, parts)) } catch (e: Exception) { e.message ?: tr("Nije uspjelo") }
                rev++
            }
            SetDrop("Gdje", if (usbIn) null else "USB se pojavi kad ga priključiš", if (usbIn) listOf("Download/PRETORIAN", "USB") else listOf("Download/PRETORIAN"),
                if (usb) 1 else 0) { Prefs.put("backup_where", if (it == 1) "2" else "0"); rev++ }
            if (usb && !FileAccess.hasAll(ctx)) Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                T("Za USB aplikacija treba dozvolu za fajlove.", 13.sp, C.Amber, maxLines = 1, modifier = Modifier.weight(1f))
                Button("Dozvoli", R.drawable.lc_check) { FileAccess.allFilesIntent(ctx)?.let { i -> try { ctx.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {} } }
            }
            SetMulti("Šta uključiti", null, BACKUP_PARTS, parts,
                note = tr("AI ključevi su po defaultu isključeni, zbog sigurnosti")) { s -> Prefs.put("backup_parts", s.sorted().joinToString(",")); rev++ }
            SetDrop("Automatska kopija", "čuvaju se zadnje 3", listOf("Isključeno", "Sedmično", "Dnevno"),
                if (!Prefs.bool("backup_auto", true)) 0 else if (Prefs.bool("backup_daily")) 2 else 1) {
                Prefs.put("backup_auto", it > 0); Prefs.put("backup_daily", it == 2); rev++
            }
            SetSection("Vrati iz kopije")
            if (found.isEmpty()) T("Još nema nijedne kopije.", 13.sp, C.Muted)
            found.take(12).forEach { f ->
                SetRow(java.text.SimpleDateFormat("EEE, dd.MM.yy · HH:mm", java.util.Locale(Look.lang)).format(java.util.Date(f.at)),
                    trf("%s · %s KB", tr(f.where), (f.size / 1024).coerceAtLeast(1)), tr("Vrati")) {
                    text = try { f.read() } catch (_: Exception) { null }
                    if (text == null || Backup.summary(text!!) == null) Toast.makeText(ctx, tr("Fajl nije PRETORIAN backup"), Toast.LENGTH_LONG).show() else step = "preview"
                }
            }
            SetRow("Drugi fajl…", ".json s USB-a ili diska", tr("Izaberi")) { step = "pick" }
            SetSection("Opasno")
            SetRow("Vrati aplikaciju na početak", "briše sve: playliste, profile, postavke", tr("Resetuj"), valueColor = C.Danger) { reset = true }
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
private fun VerBox(modifier: Modifier, label: String, value: String, color: Color, icon: Int, stable: Boolean = false, onLong: (() -> Unit)? = null) {
    Box(modifier.padding(top = 8.dp)) {
        Focusable(onClick = {}, onMenu = onLong, shape = Rnd(16.dp), normal = color.copy(alpha = 0.14f), focused = color.copy(alpha = 0.3f), zoom = 1.01f,
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
    var verMenu by remember { mutableStateOf(false) }
    var tokenAsk by remember { mutableStateOf(0) }   // 0 no, 1 PIN, 2 key field
    var promoting by remember { mutableStateOf(false) }
    val rels by load(rev / 1000) { Updater.releases() }
    val releases = (rels as? Load.Ok)?.value ?: emptyList()
    val u = Updater.available
    key(rev) {
        SetPage("Ažuriranje") {
            // ---- on top: the version you have (green) and, next to it, the new one (yellow)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VerBox(Modifier.weight(1f), "Trenutna verzija", BuildInfo.VERSION + if (Updater.beta && Updater.stableName != BuildInfo.VERSION) " · Beta" else "", C.Green, R.drawable.lc_check,
                    stable = Updater.stableName == BuildInfo.VERSION, onLong = { verMenu = true })
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
                    T(mb(Updater.speed) + " MB/s" + if (leftS >= 0) " · " + trf("još %s s", leftS) else "", 12.sp, Color(0xFF4AA3FF))
                }
                Box(Modifier.fillMaxWidth().height(6.dp).clip(Pill).background(C.Line)) { Box(Modifier.fillMaxWidth(pr / 100f).fillMaxHeight().background(Color(0xFF4AA3FF))) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button("Pauza", R.drawable.lc_pause) { Updater.pause() }
                    Button("Otkaži", R.drawable.lc_x) { Updater.cancel = true }
                }
            } else if (pr == 101) T("Instaliram… Ne klikaj ponovo, Android sam završava i PRETORIAN se ponovo pokrene.", 12.sp, C.Text, maxLines = 2)
            Updater.signature?.let { ok ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Ico(if (ok) R.drawable.lc_shield_check else R.drawable.lc_x, 14.dp, if (ok) C.Green else C.Danger)
                    T(if (ok) "Potpis provjeren" else "Potpis ne odgovara – instalacija zaustavljena", 12.sp, if (ok) C.Green else C.Danger, FontWeight.Bold)
                }
            }
            Updater.status?.let { T(it, 12.sp, C.Amber, maxLines = 2) }
            // ---- channel
            SetSection("Verzija ažuriranja")
            SetDrop("Kanal", if (Updater.beta) "probne verzije odmah, prije svih" else "samo provjerene verzije",
                listOf("Stable", "Beta"), if (Updater.beta) 1 else 0) {
                Prefs.put("upd_channel", if (it == 1) "beta" else "stable"); Prefs.put("upd_skip", ""); scope.launch { Updater.check(ctx); rev++ }
            }
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
    // long OK on the version: make it the Stable one for everybody (author only, with his GitHub key)
    // the title shows the real state: Stable when this version is the Stable one, otherwise Beta
    if (verMenu) Popup(BuildInfo.VERSION + if (Updater.stableName == BuildInfo.VERSION) " · Stable" else " · Beta", { verMenu = false }, width = 520.dp) {  // i18n-ignore
        if (Updater.stableName == BuildInfo.VERSION) T("Ova verzija je već Stable.", 13.sp, C.Green)
        else OptionRow(if (promoting) "Objavljujem…" else "Označi kao Stable · pošalji svima", R.drawable.lc_shield_check, color = C.Amber) {
            if (promoting) return@OptionRow
            if (!Updater.canPublish) { verMenu = false; tokenAsk = 1; return@OptionRow }
            promoting = true
            scope.launch {
                val err = Updater.promote(); promoting = false; verMenu = false
                if (err == null) { Updater.stableName = BuildInfo.VERSION; Prefs.put("upd_stable_name", BuildInfo.VERSION) }
                Toast.makeText(ctx, err ?: tr("Poslano: GitHub pravi Stable izdanje (oko 5 min)"), Toast.LENGTH_LONG).show()
            }
        }
        if (Updater.canPublish) OptionRow("Promijeni GitHub ključ", R.drawable.lc_key_round) { verMenu = false; tokenAsk = 1 }
        OptionRow("Odustani", R.drawable.lc_x) { verMenu = false }
    }
    if (tokenAsk == 1) PinDialog(tr("GitHub ključ"), tr("Unesi PIN"), onDismiss = { tokenAsk = 0 }) { pin -> val e = Lock.check(pin); if (e == null) tokenAsk = 2; e }
    if (tokenAsk == 2) {
        var t by remember { mutableStateOf("") }
        Popup(tr("GitHub ključ (samo za autora)"), { tokenAsk = 0 }, width = 640.dp) {
            T("Ključ ostaje samo na ovom boxu i služi samo za „Označi kao Stable“.", 12.sp, C.Muted, maxLines = 2)
            Field("github_pat_…", t, { t = it }, Modifier.fillMaxWidth())  // i18n-ignore
            OptionRow("Spremi", R.drawable.lc_check, color = C.Green) { Prefs.put("gh_token", t.trim()); tokenAsk = 0; verMenu = true }
            OptionRow("Obriši ključ", R.drawable.lc_x) { Prefs.put("gh_token", ""); tokenAsk = 0 }
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
                T("Android ne dozvoljava da se aplikacija sama vrati na stariju verziju. Zato PRETORIAN radi ovo:", 14.sp, C.Text, maxLines = 3)
                listOf("Sprema tvoje postavke u Download/PRETORIAN", "Skida stariju verziju (APK) u isti folder", "Deinstalira se",
                    "Instaliraš stari APK (LEAD Launcher ili adb)", "Pri pokretanju vratiš postavke iz backupa").forEachIndexed { k, l -> T("${k + 1}. " + tr(l), 13.sp, C.Soft, maxLines = 2) }
                backErr?.let { T(trf("Nije uspjelo: %s", it), 12.sp, C.Danger, maxLines = 2) }
                OptionRow(if (Rollback.busy) "Pripremam…" else "OK, pripremi i deinstaliraj", R.drawable.lc_download, color = C.Amber) {
                    if (!Rollback.busy) scope.launch { val err = Rollback.prepare(ctx, r); if (err == null) backDone = true else backErr = err }
                }
                OptionRow("Odustani", R.drawable.lc_x) { back = null }
            } else {
                T(trf("Spremljeno u Download/PRETORIAN: Pretorian-%s.apk i backup.", r.name), 14.sp, C.Green, maxLines = 3)
                OptionRow("Deinstaliraj PRETORIAN sada", R.drawable.lc_trash_2, color = C.Danger) { Rollback.uninstall(ctx) }
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
    var cacheMb by remember { mutableStateOf(0L) }
    val crash = remember { try { java.io.File(ctx.filesDir, "last_crash.txt").takeIf { it.exists() }?.readText() } catch (e: Exception) { null } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ---- hero: logo, name, version, badges and the help QR
        Row(Modifier.fillMaxWidth().clip(Rnd(20.dp)).background(C.CardHi).border(1.dp, C.Line, Rnd(20.dp)).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            // only the logo (no "About / PRETORIAN" next to it): its frame follows the theme so it is always clearly visible
            LogoTile(96.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // compact: "v1.0.7" plus STABLE / Beta, so nothing is cut on any screen
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    T("v" + BuildInfo.VERSION, 22.sp, C.Text, FontWeight.ExtraBold, maxLines = 1)  // i18n-ignore
                    val stable = Updater.stableName == BuildInfo.VERSION
                    Box(Modifier.clip(Pill).background((if (stable) Color(0xFFFBBF24) else C.Muted).copy(alpha = 0.18f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        T(if (stable) "STABLE" else "Beta", 10.sp, if (stable) Color(0xFFFBBF24) else C.Soft, FontWeight.Bold, maxLines = 1)  // i18n-ignore
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val u = Updater.available
                    Box(Modifier.clip(Pill).background((if (u != null) C.Amber else C.Green).copy(alpha = 0.16f)).padding(horizontal = 9.dp, vertical = 3.dp)) {
                        T(if (u != null) trf("Dostupna %s", u.name) else tr("✓ Najnovija verzija"), 11.sp, if (u != null) C.Amber else C.Green, FontWeight.Bold, maxLines = 1)
                    }
                    listOf(if (Perf.lite) "Lite" else "Full").forEach { b ->  // i18n-ignore
                        Box(Modifier.clip(Pill).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 9.dp, vertical = 3.dp)) { T(b, 11.sp, C.Text, FontWeight.Bold) }
                    }
                }
            }
            // the score of this box, clear at the top: green Full, yellow / red Lite; OK shows where the points come from
            val sc = Perf.score
            val scColor = if (sc >= 70) C.Green else if (sc >= 50) C.Amber else C.Danger
            Focusable(onClick = { dlg = "score" }, shape = Rnd(16.dp), normal = scColor.copy(alpha = 0.12f), focused = scColor.copy(alpha = 0.3f), zoom = 1.03f,
                modifier = Modifier.width(184.dp).border(1.dp, scColor.copy(alpha = 0.6f), Rnd(16.dp))) { f ->
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp).then(if (f) Modifier else Modifier), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    FitText(tr("Ocjena boxa").uppercase(), 10.sp, scColor, FontWeight.Bold, spacing = 1.5f)
                    Row(verticalAlignment = Alignment.Bottom) {
                        T(sc.toString(), 38.sp, C.Text, FontWeight.ExtraBold)
                        T("/100", 16.sp, C.Muted, FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp, start = 2.dp))  // i18n-ignore
                    }
                    ThinBar(sc / 100f, scColor, Modifier.fillMaxWidth(), 6.dp)
                    Row { FitText("● " + (if (Perf.lite) "Lite" else "Full") + " " + tr("mod"), 11.sp, scColor, FontWeight.Bold, Modifier.weight(1f)); T("OK ›", 11.sp, C.Muted) }  // i18n-ignore
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val qr = remember { qrBitmap("https://github.com/KarlAgathon/PRETORIAN/issues", 240) }  // i18n-ignore
                if (qr != null) androidx.compose.foundation.Image(qr, null, Modifier.size(80.dp).clip(Rnd(8.dp)))
                T("Pomoć na telefonu", 10.sp, C.Muted)
            }
        }
        val dev = remember { DeviceInfo.head(ctx) }
        val lastCheck = Prefs.long("upd_checked_at", 0L)
        CardGrid(3, listOf(
            { m -> InfoCard("O uređaju", android.os.Build.MODEL, tr("procesor, RAM, ekran, mreža"), R.drawable.lc_monitor, Color(0xFF3B82F6), m, onClick = { onSub("device") }) },
            { m -> InfoCard("Šta je novo", trf("Verzija %s", BuildInfo.VERSION), tr("promjene u ovoj verziji"), R.drawable.lc_star, Color(0xFFF59E0B), m, onClick = { WhatsNew.show = true }) },
            { m -> InfoCard("Autor", "Svi3e", tr("PRETORIAN tim"), R.drawable.lc_user, Color(0xFF8B5CF6), m, onClick = { dlg = "author" }) },  // i18n-ignore
            { m -> InfoCard("Kontakt", "t3smo@aol.com", tr("prijedlozi i greške"), R.drawable.lc_file_text, Color(0xFF06B6D4), m, onClick = { dlg = "contact" }) },  // i18n-ignore
            { m -> InfoCard("Izvještaj o grešci", if (crash == null) tr("Nema grešaka") else tr("Ima zapis"), tr("pogledaj ili izvezi zapis"), R.drawable.lc_file, Color(0xFFEF4444), m,
                titleColor = if (crash == null) C.Text else C.Amber, onClick = { dlg = "crash" }) },
            { m -> InfoCard("Privatnost", tr("Šta se čuva i gdje"), tr("sve ostaje na uređaju"), R.drawable.lc_shield_check, Color(0xFF22C55E), m, onClick = { dlg = "priv" }) },
            { m -> InfoCard("Licence", tr("Otvoreni kod"), "Media3, Coil, OkHttp, zxing", R.drawable.lc_list, Color(0xFF64748B), m, onClick = { dlg = "lic" }) },  // i18n-ignore
            { m -> InfoCard("Ažuriranje", tr("Provjeri sada"), if (lastCheck > 0) tr("zadnja provjera") + ": " + java.text.SimpleDateFormat("dd.MM. HH:mm", java.util.Locale.US).format(java.util.Date(lastCheck)) else null,  // i18n-ignore
                R.drawable.lc_refresh_cw, Color(0xFFEC4899), m, onClick = { onSection("update") }) },
            { m -> InfoCard("Pomoć", tr("Kratko uputstvo"), tr("prvi koraci, daljinski, prečice"), R.drawable.lc_info, Color(0xFF9CA3AF), m, onClick = { dlg = "help" }) },
        ))
    }
    when (dlg) {
        "dev" -> Popup("O uređaju", { dlg = null }, width = 640.dp) {
            val rows = remember { DeviceInfo.read(ctx) }
            rows.forEach { (k, v) ->
                Row(Modifier.fillMaxWidth().clip(Rnd(10.dp)).background(C.Rail).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(k, 13.sp, C.Muted, modifier = Modifier.width(170.dp))
                    T(v, 13.sp, C.Text, FontWeight.SemiBold, maxLines = 2, modifier = Modifier.weight(1f))
                }
            }
        }
        "score" -> Popup(trf("Ocjena boxa %s/100", Perf.score), { dlg = null }, width = 600.dp) {
            Perf.parts.forEach { (k, v) ->
                Row(Modifier.fillMaxWidth().clip(Rnd(10.dp)).background(C.Rail).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(k, 13.sp, C.Muted, modifier = Modifier.weight(1f)); T(v, 13.sp, C.Text, FontWeight.Bold)
                }
            }
            T(if (Perf.weak) "Ispod 70 bodova ili manje od 3 GB RAM-a: Lite štedi memoriju i radi glađe." else "70 bodova i više: Full, puni izgled.", 13.sp, C.Soft, maxLines = 2)
            T("Mod možeš promijeniti u Izgled › Mod programa.", 12.sp, C.Muted)
            T("Ocjena se izmjeri jednom i ostaje ista.", 12.sp, C.Muted)
            OptionRow("Izmjeri ponovo", R.drawable.lc_refresh_cw) { Perf.remeasure(ctx); dlg = null; scope.launch { delay(50); dlg = "score" } }
        }
        "author" -> Popup("Autor", { dlg = null }) {
            T("Svi3e", 22.sp, C.Text, FontWeight.ExtraBold)  // i18n-ignore
            T("PRETORIAN je napravljen za porodični TV: brz, jednostavan i bez reklama.", 14.sp, C.Soft, maxLines = 4)
            T("github.com/KarlAgathon/PRETORIAN", 13.sp, C.Muted)  // i18n-ignore
        }
        "contact" -> Popup("Kontakt", { dlg = null }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val qr = remember { qrBitmap("mailto:t3smo@aol.com?subject=PRETORIAN%20" + BuildInfo.VERSION, 240) }  // i18n-ignore
                if (qr != null) androidx.compose.foundation.Image(qr, null, Modifier.size(110.dp).clip(Rnd(8.dp)))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    T("t3smo@aol.com", 18.sp, C.Text, FontWeight.Bold)  // i18n-ignore
                    T("Skeniraj telefonom i napiši e-mail. Navedi model uređaja i verziju.", 13.sp, C.Soft, maxLines = 3)
                }
            }
        }
        "help" -> Popup("Kratko uputstvo", { dlg = null }, width = 640.dp) {
            listOf("OK otvara, dugi OK otvara meni (označi, dodaj na listu, sakrij).",
                "Nazad uvijek ide jedan korak nazad i ostaje u istoj sekciji.",
                "U plejeru: ▲ ▼ mijenja kanal, ◀ ▶ premotava, OK pauza.",
                "Brojevi na daljinskom biraju kanal; 0 vraća na prethodni.",
                "Postavke › Jednostavni mod: samo TV, Filmovi i Serije, izlaz s PIN-om.",
                "Zadani PIN je 0000 – promijeni ga u Postavkama.").forEach { T("• " + tr(it), 14.sp, C.Soft, maxLines = 3) }
        }
        "lic" -> Popup("Licence", { dlg = null }) {
            listOf("Jetpack Compose · Apache 2.0", "Media3 / ExoPlayer · Apache 2.0", "Coil · Apache 2.0", "OkHttp · Apache 2.0", "Lucide ikone · ISC", "Inter font · OFL",
                "TMDB · This product uses the TMDB API but is not endorsed or certified by TMDB.", "PRETORIAN · MIT · github.com/KarlAgathon/PRETORIAN").forEach { T(it, 13.sp, C.Soft, maxLines = 2) }  // i18n-ignore
        }
        "priv" -> Popup("Privatnost", { dlg = null }) {
            T("Playliste, lozinke, PIN, favoriti i gledano čuvaju se samo na ovom uređaju. Na internet idu samo zahtjevi prema tvom provideru, slike filmova (TMDB) i, ako je uključiš, AI pretraga.", 13.sp, C.Soft, maxLines = 8)
        }
        "crash" -> Popup("Izvještaj o greškama", { dlg = null }, width = 760.dp) {
            T((crash ?: tr("Nema grešaka")).take(1500), 11.sp, C.Soft, maxLines = 24)
            OptionRow("Izvezi log za podršku", R.drawable.lc_download, sub = "na USB ili u folder") { dlg = "log" }
            // all crashes and freezes (last 10) from a laptop, without touching the box
            T("Sa laptopa (zadnjih 10 padova i zamrzavanja):", 12.sp, C.Muted)
            T("adb pull " + CrashLog.ADB_DIR, 13.sp, C.Text, FontWeight.Bold, maxLines = 2)  // i18n-ignore
            if (crash != null) OptionRow("Obriši izvještaj", R.drawable.lc_trash_2) { java.io.File(ctx.filesDir, "last_crash.txt").delete(); dlg = null }
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


/** the playlist whose menu opens on the "Edit playlists" page */
object PlMenu { var open: String? = null }
/** the playlist whose groups/channels or EPG are being edited (null = all / the active one) */
object PlEdit { var id: String? = null }


/** The PRETORIAN logo on a frame that suits the theme: glass in Liquid, a gold edge in Sand, a white card in Paper… */
@Composable
fun LogoTile(size: androidx.compose.ui.unit.Dp) {
    val shape = Rnd(size * 0.22f)
    val red = Color(0xFFE5141B)
    val (bg, edge, glow) = when (Look.theme) {
        "liquid" -> Triple<androidx.compose.ui.graphics.Brush, Color, Color>(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0x47FFFFFF), Color(0x14FFFFFF))), Color(0x73FFFFFF), red.copy(alpha = 0.35f))
        "classic" -> Triple(androidx.compose.ui.graphics.SolidColor(Color(0xFF1C2735)), Color(0xFFE2E8F0), Color.Transparent)
        "pijesak" -> Triple(androidx.compose.ui.graphics.SolidColor(Color(0xFF1D140B)), Color(0xFFE0B25C), Color(0x4DE0B25C))
        "papir" -> Triple(androidx.compose.ui.graphics.SolidColor(Color.White), Color(0xFFD5D9DF), Color(0x22000000))
        "reddawn" -> Triple(androidx.compose.ui.graphics.SolidColor(Color(0xFF111113)), Color(0xFF3F3F46), red.copy(alpha = 0.45f))
        else -> Triple(androidx.compose.ui.graphics.SolidColor(Color(0xFF0A0A0C)), Color(0x1FFFFFFF), red.copy(alpha = 0.5f))
    }
    Box(Modifier.size(size).shadow(if (glow.alpha > 0f) 18.dp else 0.dp, shape, ambientColor = glow, spotColor = glow).clip(shape).background(bg).border(1.5.dp, edge, shape),
        contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.logo_mark), contentDescription = "PRETORIAN", modifier = Modifier.size(size * 0.8f))  // i18n-ignore
    }
}

/** 30 s, 5 min, 3 h, daily — for the background check intervals */
fun everyStr(sec: Int): String = when {
    sec < 60 -> trf("%s s", sec)
    sec < 3600 -> trf("%s min", sec / 60)
    sec >= 86400 -> tr("dnevno")
    else -> trf("%s h", sec / 3600)
}
