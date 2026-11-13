package com.pretorian.tv

import android.widget.Toast
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
    Section("playlists", R.drawable.lc_list, "Playliste", "Dodaj, aktiviraj, ažuriraj"),
    Section("groups", R.drawable.lc_layers, "Grupe i kanali", "Redoslijed, nazivi, sakrivanje, PIN"),
    Section("look", R.drawable.lc_palette, "Izgled", "Tema, boja, prozirnost, veličine"),
    Section("media", R.drawable.lc_captions, "Filmovi i titlovi", "TMDB, IMDb, titlovi, jezici"),
    Section("parental", R.drawable.lc_shield_check, "Roditelji i jednostavni mod", "PIN, jednostavni mod"),
    Section("update", R.drawable.lc_download, "Ažuriranje", "Nova verzija aplikacije"),
    Section("about", R.drawable.lc_info, "O aplikaciji", "Verzija i licence"),
)

@Composable
fun SettingsScreen(nav: Nav) {
    var section by remember { mutableStateOf(if (Updater.available != null) "update" else "playlists") }
    val first = remember { FocusRequester() }
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(320.dp).fillMaxHeight().padding(start = 18.dp, end = 12.dp, top = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            T("Postavke", 28.sp, C.Text, FontWeight.Bold, modifier = Modifier.padding(start = 10.dp, bottom = 12.dp))
            SECTIONS.forEachIndexed { i, s ->
                val sel = s.id == section
                Focusable(modifier = Modifier.fillMaxWidth().height(58.dp), onClick = { section = s.id }, onFocus = { section = s.id },
                    focusRequester = if (i == 0) first else null, normal = if (sel) C.CardFocus else Color.Transparent, zoom = 1f) { _ ->
                    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(if (sel) C.Red else C.Card), contentAlignment = Alignment.Center) {
                            Ico(s.icon, 18.dp, if (sel) C.OnRed else C.Text)
                        }
                        Column(Modifier.weight(1f)) {
                            T(s.title, 14.sp, C.Text, FontWeight.Medium)
                            T(s.sub, 11.sp, C.Muted)
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
                    "groups" -> GroupManager(nav)
                    "look" -> LookPage()
                    "media" -> MediaPage()
                    "parental" -> ParentalPage(nav)
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

@Composable
private fun PlaylistsPage(nav: Nav) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf(Store.playlists(ctx)) }
    var busy by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf<Playlist?>(null) }
    Page("Playliste", "Aktivna je samo jedna. OK na drugoj je aktivira, Meni otvara opcije.") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button("Dodaj playlistu", R.drawable.lc_plus, primary = true) { nav.push(Screen.AddPlaylist) }
            Button(if (busy) "Ažuriram…" else "Ažuriraj sve", R.drawable.lc_refresh_cw) {
                if (busy) return@Button
                busy = true
                scope.launch {
                    val updated = Store.playlists(ctx).map { pl ->
                        try { if (pl.type == SourceType.XTREAM) pl.copy(exp = Repo.test(pl)) else { Repo.test(pl); pl } } catch (e: Exception) { pl }
                    }
                    Store.savePlaylists(ctx, updated); list = updated
                    Repo.clear(); busy = false
                    Toast.makeText(ctx, "Playliste su ažurirane", Toast.LENGTH_SHORT).show()
                }
            }
        }
        list.forEach { pl ->
            val exp = expiryText(pl.exp)
            Focusable(
                modifier = Modifier.fillMaxWidth().height(78.dp).then(if (pl.active) Modifier.border(2.dp, C.Red, RoundedCornerShape(14.dp)) else Modifier),
                onClick = { Store.activate(ctx, pl.id); list = Store.playlists(ctx); nav.playlistsChanged() },
                onMenu = { del = pl },
                normal = if (pl.active) C.CardFocus else C.Card, zoom = 1.01f,
            ) { _ ->
                Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(if (pl.active) C.Red else C.CardHi), contentAlignment = Alignment.Center) {
                        if (pl.active) Dot(C.OnRed, 8.dp)
                    }
                    Column(Modifier.weight(1f)) {
                        T(pl.name, 17.sp, C.Text, FontWeight.SemiBold)
                        T(if (pl.type == SourceType.XTREAM) "Xtream Codes · ${pl.server}" else "M3U · ${pl.url}", 12.sp, C.Muted)
                    }
                    if (exp != null) {
                        val level = exp.second
                        Chip(exp.first, when (level) { 2 -> C.Danger.copy(alpha = 0.2f); 1 -> C.Amber.copy(alpha = 0.2f); else -> C.Card },
                            when (level) { 2 -> C.Danger; 1 -> C.Amber; else -> C.Soft })
                    }
                    if (pl.active) Chip("AKTIVNA", C.Red, C.OnRed)
                }
            }
        }
        T("Drži OK na playlisti: obriši. Datum isteka osvježava \"Ažuriraj sve\".", 12.sp, C.Muted)
    }
    del?.let { pl ->
        Popup(pl.name, onDismiss = { del = null }) {
            OptionRow("Obriši playlistu", R.drawable.lc_trash_2, color = C.Danger) {
                val rest = Store.playlists(ctx).filter { it.id != pl.id }
                Store.savePlaylists(ctx, if (rest.none { it.active } && rest.isNotEmpty()) rest.mapIndexed { i, x -> x.copy(active = i == 0) } else rest)
                list = Store.playlists(ctx); del = null; nav.playlistsChanged()
            }
            OptionRow("Odustani", R.drawable.lc_x) { del = null }
        }
    }
}

@Composable
fun AddPlaylistScreen(nav: Nav) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var live by remember { mutableStateOf(true) }
    var vod by remember { mutableStateOf(true) }
    var series by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        Header("Dodaj playlistu")
        Column(Modifier.fillMaxSize().padding(horizontal = 36.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Segmented(listOf("Xtream Codes", "M3U playlista", "Stalker portal"), tab) {
                if (it == 2) Toast.makeText(ctx, "Stalker portal dolazi u sljedećoj verziji", Toast.LENGTH_SHORT).show() else tab = it
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
                        id = System.currentTimeMillis().toString(),
                        name = name.trim().ifBlank { if (tab == 0) "Xtream" else "M3U" },
                        type = if (tab == 0) SourceType.XTREAM else SourceType.M3U,
                        server = server.trim(), user = user.trim(), pass = pass.trim(), url = url.trim(), active = true,
                        live = live, vod = vod, series = series,
                    )
                    if (tab == 0 && (base.server.isBlank() || base.user.isBlank() || base.pass.isBlank())) { error = "Unesi server, korisničko ime i lozinku"; return@Button }
                    if (tab == 1 && base.url.isBlank()) { error = "Unesi M3U link"; return@Button }
                    busy = true; error = null
                    scope.launch {
                        try {
                            val info = Repo.test(base)
                            val pl = if (base.type == SourceType.XTREAM) base.copy(exp = info) else base
                            Store.savePlaylists(ctx, Store.playlists(ctx).map { it.copy(active = false) } + pl)
                            Toast.makeText(ctx, if (pl.type == SourceType.XTREAM) "Povezano ✓" else "Učitano: $info", Toast.LENGTH_LONG).show()
                            nav.playlistsChanged(); nav.root(Screen.Home)
                        } catch (e: Exception) {
                            error = "Nije uspjelo: ${e.message ?: e.javaClass.simpleName}"
                        } finally { busy = false }
                    }
                }
                if (Store.playlists(ctx).isNotEmpty()) Button("Odustani", R.drawable.lc_x) { nav.back() }
            }
        }
    }
}

/* ---------------------------------------------------------------- appearance */

@Composable
private fun LookPage() {
    val ctx = LocalContext.current
    fun save() = Look.save(ctx)
    Page("Izgled", "Promjene se vide odmah.") {
        Label("Tema")
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
        Label("Prozirnost panela: ${Look.opacity} %")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Button("− 5 %", R.drawable.lc_minus) { Look.opacity = (Look.opacity - 5).coerceAtLeast(20); save() }
            Box(Modifier.width(260.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(C.CardHi)) {
                Box(Modifier.fillMaxWidth(Look.opacity / 100f).height(10.dp).background(C.Red))
            }
            Button("+ 5 %", R.drawable.lc_plus) { Look.opacity = (Look.opacity + 5).coerceAtMost(100); save() }
        }
        T("Najviše se vidi u temi Glass.", 12.sp, C.Muted)
        Label("Pločice na Početnoj")
        Segmented(listOf("Male", "Srednje", "Velike"), Look.tileSize) { Look.tileSize = it; save() }
        Segmented(listOf("Puna boja", "Glass", "Obris"), Look.tileStyle) { Look.tileStyle = it; save() }
        Label("Posteri filmova i serija")
        Segmented(listOf("Mali", "Srednji", "Veliki"), Look.posterSize) { Look.posterSize = it; save() }
        Label("Meniji i info traka preko slike")
        Segmented(listOf("Stakleno (prozirno)", "Crno"), Look.overlay) { Look.overlay = it; save() }
        Label("Kanali")
        ToggleRow("Pregled kanala uživo", Look.preview, R.drawable.lc_tv, "Kad staneš na kanal, desno se pušta mala slika i EPG") { Look.preview = it; save() }
        ToggleRow("Najgledaniji na vrhu grupe", Look.mostWatched, R.drawable.lc_arrow_up, "Kanali koje najčešće gledaš sami idu gore (kad nema ručnog redoslijeda)") {
            Look.mostWatched = it; save()
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
    fun masked(v: String) = if (v.isBlank()) "Nije upisan" else "••••" + v.takeLast(4)
    key(rev) {
        Page("Filmovi i titlovi", "Ključevi su besplatni i čuvaju se samo na ovom uređaju.") {
            Label("Podaci o filmovima")
            OptionRow("TMDB API ključ", R.drawable.lc_key_round, "Posteri, opis na tvom jeziku, glumci, trailer · themoviedb.org › Settings › API",
                value = masked(Prefs.tmdbKey)) { edit = "tmdb" }
            if (Prefs.tmdbKey.isNotBlank()) OptionRow("Provjeri TMDB ključ", R.drawable.lc_check, value = tmdbStatus) {
                tmdbStatus = "Provjeravam…"; scope.launch { tmdbStatus = Tmdb.test()?.let { "Ne radi: $it" } ?: "Radi ✓" }
            }
            OptionRow("OMDb API ključ (IMDb ocjena)", R.drawable.lc_key_round, "omdbapi.com › API Key › FREE", value = masked(Prefs.omdbKey)) { edit = "omdb" }
            OptionRow("Jezik opisa", R.drawable.lc_globe, "Ako opisa nema na tom jeziku, prikazuje se engleski", value = Subs.langName(Prefs.metaLang)) { edit = "metalang" }
            Label("Titlovi")
            OptionRow("OpenSubtitles API ključ", R.drawable.lc_key_round, "opensubtitles.com › Profil › API consumers", value = masked(Prefs.osKey)) { edit = "os" }
            OptionRow("OpenSubtitles korisnik (opciono)", R.drawable.lc_user, "Više preuzimanja dnevno", value = Prefs.osUser.ifBlank { "Nije upisan" }) { edit = "osuser" }
            if (Prefs.osUser.isNotBlank()) OptionRow("OpenSubtitles lozinka", R.drawable.lc_lock, value = if (Prefs.osPass.isBlank()) "Nije upisana" else "••••••") { edit = "ospass" }
            OptionRow("SubDL API ključ", R.drawable.lc_key_round, "subdl.com › Panel › API", value = masked(Prefs.subdlKey)) { edit = "subdl" }
            OptionRow("Jezici titla (redom)", R.drawable.lc_captions, value = Prefs.subLangs.uppercase()) { edit = "sublangs" }
            OptionRow("Jezici zvuka (redom)", R.drawable.lc_volume, value = Prefs.audioLangs.uppercase()) { edit = "audiolangs" }
        }
    }
    val e = edit
    if (e != null) {
        val pair = when (e) {
            "tmdb" -> "TMDB API ključ" to Prefs.tmdbKey
            "omdb" -> "OMDb API ključ" to Prefs.omdbKey
            "os" -> "OpenSubtitles API ključ" to Prefs.osKey
            "osuser" -> "OpenSubtitles korisničko ime" to Prefs.osUser
            "ospass" -> "OpenSubtitles lozinka" to ""
            "subdl" -> "SubDL API ključ" to Prefs.subdlKey
            "sublangs" -> "Jezici titla, npr. bs,hr,sr,en" to Prefs.subLangs
            "audiolangs" -> "Jezici zvuka, npr. en,bs,de" to Prefs.audioLangs
            else -> "Jezik opisa (bs, hr, sr, en, de…)" to Prefs.metaLang
        }
        TextDialog(pair.first, pair.first, pair.second, onDismiss = { edit = null }) { v ->
            when (e) {
                "tmdb" -> Prefs.tmdbKey = v
                "omdb" -> Prefs.omdbKey = v
                "os" -> Prefs.osKey = v
                "osuser" -> Prefs.osUser = v
                "ospass" -> Prefs.osPass = v
                "subdl" -> Prefs.subdlKey = v
                "sublangs" -> Prefs.subLangs = v.lowercase().replace(" ", "")
                "audiolangs" -> Prefs.audioLangs = v.lowercase().replace(" ", "")
                else -> Prefs.metaLang = v.lowercase().take(2).ifBlank { "bs" }
            }
            edit = null; rev++
        }
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
        "pin" -> PinSetupDialog(onDismiss = { setup = null }) { setup = null; rev++; Toast.makeText(ctx, "PIN je postavljen", Toast.LENGTH_SHORT).show() }
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
    Page("Ažuriranje", "Instalirana verzija: ${BuildInfo.VERSION}. Provjera ide sama pri pokretanju i svakih 6 sati.") {
        if (u != null) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.CardFocus).border(1.dp, C.Red, RoundedCornerShape(16.dp)).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Ico(R.drawable.lc_download, 26.dp, C.RedText)
                Column(Modifier.weight(1f)) {
                    T("Nova verzija ${u.name}", 17.sp, C.Text, FontWeight.SemiBold)
                    T(if (u.notes.isNotBlank()) u.notes else "Jedan klik: preuzima se i odmah instalira", 13.sp, C.Muted, maxLines = 3)
                }
                val pr = Updater.progress
                if (pr in 0..100) T("Preuzimam $pr %", 15.sp, C.Text)
                else if (pr == 101) T("Instaliram…", 15.sp, C.Text)
                else Button("Ažuriraj sada", R.drawable.lc_download, primary = true) { scope.launch { Updater.install(ctx, u) } }
            }
        }
        OptionRow(if (checking) "Provjeravam…" else "Provjeri ima li nova verzija", R.drawable.lc_refresh_cw, value = Updater.status) {
            if (!checking) { checking = true; scope.launch { Updater.check(ctx); checking = false } }
        }
        T("Prvi put Android može pitati da dozvoliš PRETORIAN-u instaliranje aplikacija. Dozvoli i ponovo klikni Ažuriraj.", 12.sp, C.Muted, maxLines = 2)
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
