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



/* ---------------------------------------------------------------- Jezik */

@Composable
internal fun LanguageV2() {
    val ctx = LocalContext.current
    var rev by remember { mutableIntStateOf(0) }
    key(rev, Look.code) {
        SetPage("Jezik") {
            val cur = Langs.APP.firstOrNull { it.code == Look.code } ?: Langs.APP[0]
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
                        val on = l.code == Look.code
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
internal fun PlayerV2(openSub: (String) -> Unit) {
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
            SetDrop("Nastavi gdje si stao", null, listOf("Pitaj", "Uvijek nastavi", "Uvijek od početka"), Prefs.str("resume_mode", "0").toIntOrNull() ?: 0, new = true) {
                Prefs.put("resume_mode", it.toString()); rev++
            }
            SetSection("Serije")
            SetSwitch("Sljedeća epizoda sama", "odbrojava 10 s na kraju", Prefs.bool("next_ep", true)) { Prefs.put("next_ep", it) }
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
            // Phase A: the technical player options live one level down, in "Više opcija" (no Basic/Advanced mode)
            SetRow("Više opcija", "Vanjski plejer, jezici titla i zvuka, file manager", arrow = "›") { openSub("playermore") }
            ResetSection("Plejer", listOf("sub_size", "sub_style", "recover_on", "recover_wait2", "buffer_mb", "zap_updown", "resume_mode", "next_ep", "trailer_app", "audio_passthrough")) { rev++ }
        }
    }
}


/** Settings › Reprodukcija › Plejer › Više opcija: the technical player options (moved here unchanged from "Napredno"). */
@Composable
internal fun PlayerMoreV2() {
    val ctx = LocalContext.current
    var rev by remember { mutableIntStateOf(0) }
    key(rev) {
        SetPage("Više opcija") {
            var askReset by remember { mutableStateOf(false) }
            SetDrop("Vrijeme čekanja na vezu", "Koliko dugo plejer čeka server prije novog pokušaja", PlayOpts.TIMEOUTS.map { if (it == 0) tr("Automatski") else "$it s" },  // i18n-ignore
                PlayOpts.TIMEOUTS.indexOf(PlayOpts.timeout).coerceAtLeast(0)) { Prefs.put("conn_timeout", PlayOpts.TIMEOUTS[it].toLong()); rev++ }
            SetDrop("Titlovi", "Kada se titl uključuje sam", listOf(tr("Uvijek"), tr("Samo strani jezik"), tr("Isključeno")), PlayOpts.subBehaviour) { PlayOpts.setSubBehaviour(it); rev++ }
            // 3.0 provider protection: off = passive (lamps only from what you watch)
            SetSwitch("Aktivna provjera kanala", "isključeno po defaultu · uključi samo ako provider dozvoljava više konekcija · najviše 30 na sat", Prefs.bool("ch_probe_active", false)) { Prefs.put("ch_probe_active", it); rev++ }
            SetRow("Vrati postavke reprodukcije", "Playliste, EPG, favoriti i historija ostaju") { askReset = true }
            if (askReset) SetConfirm(tr("Vratiti postavke reprodukcije?"), tr("Plejer, zvuk, titlovi i stream vraćaju se na zadane vrijednosti."), tr("Vrati"),
                onCancel = { askReset = false }) { PlayOpts.reset(); askReset = false; rev++ }
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
    }
}

/* ---------------------------------------------------------------- Sport */

@Composable
internal fun RemindersV2(nav: Nav) {
    var rev by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf<Reminder?>(null) }
    key(rev, Reminders.rev) {
        SetPage("Podsjetnici") {
            val all = Reminders.all().filter { it.start > System.currentTimeMillis() - 3600_000L }.sortedBy { it.start }
            SetSection(trf("Nadolazeći · %s", all.size))
            if (all.isEmpty()) T("Nema podsjetnika. U TV vodiču ili Sportu: OK na emisiji › Podsjeti me.", 12.sp, C.Muted, maxLines = 2)
            all.forEach { r ->
                SetRow(r.title, java.text.SimpleDateFormat("EEE d. M. HH:mm", appLocale()).format(java.util.Date(r.start)) + (r.channelName?.let { " · $it" } ?: ""), arrow = "›",
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
internal fun NotificationsV2() {
    var rev by remember { mutableIntStateOf(0) }
    key(rev) {
        SetPage("Obavještenja", "Šta ti aplikacija sama javlja (zvonce gore desno)") {
            SetSwitch("Obavještenja", "Glavni prekidač · tvoji izbori ispod ostaju sačuvani", Notes.master) { Prefs.put("notes_master", it); rev++ }
            SetSwitch("Vrijeme bez ometanja", if (Quiet.on) trf("Od %s do %s h bez iskačućih obavještenja", Quiet.from, Quiet.to) else "Zvonce ih i dalje bilježi", Quiet.on) { Prefs.put("quiet_on", it); rev++ }
            if (Quiet.on) {
                SetDrop("Od", null, (0..23).map { "$it:00" }, Quiet.from) { Prefs.put("quiet_from", it.toLong()); rev++ }  // i18n-ignore
                SetDrop("Do", null, (0..23).map { "$it:00" }, Quiet.to) { Prefs.put("quiet_to", it.toLong()); rev++ }  // i18n-ignore
            }
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
internal fun PinV2() {
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
internal fun GeneralV2() {
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
internal fun ParentalV2() {
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
            SetSwitch("Privremeno otključaj", if (Lock.allOpen) "Otključano do izlaska iz aplikacije" else "Zaključano", Lock.allOpen) { Lock.allOpen = it; rev++ }
            SetSwitch("Prikaži sakriveno do izlaska iz aplikacije", "bez ponovnog PIN-a", Parental.unlocked) { Parental.unlocked = it; Repo.clear(); SearchIndex.reset(); rev++ }
            ResetSection("Roditeljska kontrola", Parental.KINDS.map { "par_" + it.first }) { Repo.clear(); rev++ }
        }
    }
}

/* ---------------------------------------------------------------- Profili */


/* ---------------------------------------------------------------- Način rada */

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
internal fun UpdateV2() {
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
internal fun AboutV2(onSub: (String) -> Unit, onSection: (String) -> Unit) {
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
                "TV: ▲ ▼ otvaraju radnje, CH+ i CH− mijenjaju kanal, ◀ lista kanala, ▶ EPG.",
                "Film i serija: ◀ ▶ premotava, OK pauza.",
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
            T("Sa laptopa (padovi, zamrzavanja i događaji):", 12.sp, C.Muted)
            T("adb pull " + CrashLog.ADB_DIR, 13.sp, C.Text, FontWeight.Bold, maxLines = 2)  // i18n-ignore
            if (crash != null) OptionRow("Obriši izvještaj", R.drawable.lc_trash_2) { java.io.File(ctx.filesDir, "last_crash.txt").delete(); dlg = null }
        }
        else -> {}
    }
}



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
internal fun AppearanceV2() {
    var rev by remember { mutableIntStateOf(0) }
    key(rev) {
        SetPage("Izgled", "Jedan izgled za sve. Ovdje biraš samo veličinu teksta i način rada.") {
            SetSlider("Veličina teksta", "za gledanje s daljeg kauča · 90 – 120 %", Tune.textSize, 90, 120, 5, { "$it %" }) { Tune.textSize = it; Tune.save() }
            WeatherPlaceRow()
            LookOptions()
            SetSection("Rad")
            SetDrop("Način rada", trf("Ocjena uređaja %s/100 · %s GB RAM", Perf.score, String.format("%.1f", Perf.ramGb)),
                Perf.MODES, Perf.mode.coerceIn(0, Perf.MODES.lastIndex)) { Perf.set(it); rev++ }
            T("Lite štedi memoriju i procesor na slabijim uređajima. Izgled i funkcije ostaju isti.", 12.sp, C.Muted, maxLines = 2)
            // the mode that really runs now (Automatic picks it from RAM and the device score)
            T(trf("Sada radi: %s", if (Perf.lite) "Lite" else "Full"), 14.sp, C.Text, FontWeight.SemiBold)  // i18n-ignore
            T(when { Perf.mode == 1 -> "Izabrano ručno."; Perf.mode == 2 -> "Izabrano ručno."; Perf.lite -> "Automatski: uređaj ima manje od 3 GB RAM-a ili nižu ocjenu."; else -> "Automatski: uređaj je dovoljno jak." }, 12.sp, C.Muted, maxLines = 2)
        }
    }
}
