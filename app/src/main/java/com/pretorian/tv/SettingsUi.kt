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



/** Playlist being edited in "Add playlist" (null = a new one). */
object EditPlaylist { var target: Playlist? = null }

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
    val classic = true
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
            // search only inside Settings (owner)
            Focusable(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), onClick = { openScopedSearch(nav, "settings") }, normal = Color.Transparent, focused = C.Red, zoom = 1f) { f ->
                Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(26.dp).clip(Rnd(8.dp)).background(if (f) C.OnRed.copy(alpha = 0.08f) else C.Card), contentAlignment = Alignment.Center) {
                        Ico(R.drawable.lc_search, 14.dp, if (f) C.OnRed else C.Text)
                    }
                    if (!slim) T("Traži postavku", 14.sp, if (f) C.OnRed else C.Text, FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
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
