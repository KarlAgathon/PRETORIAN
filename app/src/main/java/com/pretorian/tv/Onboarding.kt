package com.pretorian.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * First-time setup (approved design): 01 language · 02 legal notice · 03 how to begin (Xtream, M3U, Stalker or Skip).
 * Onboarding being finished is its own state: a user who skipped has finished it and is never sent back.
 * Adding a playlist later (Settings › Playlists) shows only step 03, never the language or the notice again.
 */
object Onboard {
    const val LEGAL_VERSION = 1
    var step by mutableIntStateOf(0)
    fun done(ctx: android.content.Context): Boolean = Prefs.bool("onboard_done") || Store.playlists(ctx).isNotEmpty()
    val legalOk get() = Prefs.long("legal_ack_v", 0) >= LEGAL_VERSION
    fun finish() { Prefs.put("onboard_done", true) }
}

/** Shown by Add playlist when there is no playlist yet. */
@Composable
fun OnboardingScreen(nav: Nav) {
    val ctx = LocalContext.current
    val first = remember { Onboard.done(ctx) || Onboard.legalOk }
    var step by remember { mutableIntStateOf(if (first) 2 else Onboard.step.coerceIn(0, 2)) }
    LaunchedEffect(step) { Onboard.step = step }
    BackHandler(enabled = step > 0 && !first) { step-- }
    Box(Modifier.fillMaxSize().background(Color(0x99000000)).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        // the panel scrolls when the screen is small (phones, big text): Continue is always reachable
        Column(Modifier.widthIn(max = 900.dp).fillMaxWidth(0.78f).dawnGlass(22.dp).verticalScroll(rememberScrollState()).padding(horizontal = 36.dp, vertical = 26.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (!first) Row(verticalAlignment = Alignment.CenterVertically) {
                T("PRVO POKRETANJE", 12.sp, C.Muted, FontWeight.Bold, modifier = Modifier.weight(1f))
                T("0${step + 1} / 03", 12.sp, C.Muted, FontWeight.Bold)  // i18n-ignore
            }
            when (step) {
                0 -> LanguageStep { step = 1 }
                1 -> LegalStep { Prefs.put("legal_ack_v", Onboard.LEGAL_VERSION.toLong()); step = 2 }
                else -> SourceStep(nav, showSkip = !Onboard.done(ctx))
            }
        }
    }
}

@Composable
private fun LanguageStep(onNext: () -> Unit) {
    val ctx = LocalContext.current
    var rev by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf(false) }
    val cont = remember { FocusRequester() }
    val drop = remember { FocusRequester() }
    val cur = remember { FocusRequester() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // English first, then German, then the rest in the app's order; the order never follows the device language
    val langs = remember { Langs.APP.sortedBy { when (it.code) { "en" -> 0; "de" -> 1; else -> 2 } } }
    androidx.activity.compose.BackHandler(enabled = open) { open = false; scope.launch { drop.tryFocus() } }
    key(rev) {
        T("Dobrodošli u vaš media centar.", 26.sp, C.Text, FontWeight.Bold)
        T("Nova era zabave počinje ovdje.", 17.sp, C.Soft)
        T("Složeno iznutra. Jednostavno spolja.", 13.sp, C.Muted)
        Spacer(Modifier.height(4.dp))
        // one row "Language: English ▾"; OK opens the list, OK on a language applies it at once (no extra question)
        val now = langs.firstOrNull { it.code == Look.lang } ?: langs.first()
        Focusable(onClick = { open = !open }, focusRequester = drop, shape = Rnd(12.dp), normal = Color(0x14FFFFFF), focused = C.Red,
            focusBorderColor = Color.Transparent, zoom = 1f, modifier = Modifier.fillMaxWidth().height(50.dp).border(1.dp, Color(0x4DFFFFFF), Rnd(12.dp))) { f ->
            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Ico(R.drawable.lc_globe, 18.dp, if (f) C.OnRed else C.Text)
                T("Jezik", 15.sp, if (f) C.OnRed else C.Soft)
                T(now.native, 17.sp, if (f) C.OnRed else C.Text, FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Ico(R.drawable.lc_chevron_down, 18.dp, if (f) C.OnRed else C.Text)
            }
        }
        if (open) {
            androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp).dawnGlass().padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(langs.size) { i ->
                    val l = langs[i]; val sel = l.code == Look.lang
                    Focusable(onClick = { open = false; if (!sel) { setAppLang(ctx, l.code); rev++ } else scope.launch { cont.tryFocus() } },
                        focusRequester = if (sel) cur else null, shape = Rnd(10.dp), normal = Color.Transparent, focused = C.Red,
                        focusBorderColor = Color.Transparent, zoom = 1f, modifier = Modifier.fillMaxWidth().height(44.dp)) { f ->
                        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            T(l.native, 16.sp, if (f) C.OnRed else C.Text, if (sel) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                            if (sel) Ico(R.drawable.lc_check, 16.dp, if (f) C.OnRed else C.Text)
                        }
                    }
                }
            }
            LaunchedEffect(Unit) { cur.tryFocus() }
        }
        Spacer(Modifier.height(4.dp))
        Button("Nastavi", R.drawable.lc_chevron_right, primary = true, focusRequester = cont) { onNext() }
    }
    // first open: on the language row; after a language was chosen: on Continue
    LaunchedEffect(rev) { if (rev == 0) drop.tryFocus() else cont.tryFocus() }
}

@Composable
private fun LegalStep(onAccept: () -> Unit) {
    val fr = remember { FocusRequester() }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Ico(R.drawable.lc_shield_check, 26.dp, C.Text)
        T("Pravna napomena", 28.sp, C.Text, FontWeight.Bold)
    }
    T("Napravljeno za zabavu. Namijenjeno zakonitoj upotrebi.", 16.sp, C.Soft)
    Column(Modifier.fillMaxWidth().height(230.dp).verticalScroll(rememberScrollState()).clip12().padding(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            "Ovaj softver je media plejer namijenjen zakonitoj upotrebi.",
            "Programer ne pruža, ne hostuje, ne prodaje, ne distribuira niti podržava neovlaštene streamove, sadržaj zaštićen autorskim pravima distribuiran bez dozvole ili nezakonite streaming usluge.",
            "Korisnici su odgovorni da sadržaj, playliste i streaming usluge kojima pristupaju budu zakonito odobreni za njihovu upotrebu.",
            "Koristite ovaj media centar samo sa sadržajem i uslugama kojima imate zakonito pravo pristupa.",
        ).forEach { T(it, 15.sp, C.Text, maxLines = 6) }
    }
    Button("Razumijem i nastavljam", R.drawable.lc_check, primary = true, focusRequester = fr) { onAccept() }
    LaunchedEffect(Unit) { fr.tryFocus() }
}

@Composable
private fun SourceStep(nav: Nav, showSkip: Boolean) {
    val first = remember { FocusRequester() }
    T("Kako želite početi?", 28.sp, C.Text, FontWeight.Bold)
    T("Dodajte svoj izvor sada ili prvo istražite aplikaciju.", 16.sp, C.Soft)
    Spacer(Modifier.height(2.dp))
    listOf(
        Triple("Xtream Codes", "Server · korisničko ime · lozinka", R.drawable.lc_server) to 0,
        Triple("M3U playlista", "Link playliste · EPG po želji", R.drawable.lc_list) to 1,
        Triple("Stalker portal", "Adresa portala · MAC adresa", R.drawable.lc_tv) to 2,
    ).forEachIndexed { i, (t, tab) ->
        Focusable(onClick = { Welcome.tab = tab }, focusRequester = if (i == 0) first else null, shape = Rnd(14.dp), normal = C.Card, focused = C.Red,
            focusBorderColor = Color.Transparent, zoom = 1f, modifier = Modifier.fillMaxWidth().height(64.dp).border(1.dp, Silver.Border, Rnd(14.dp))) { f ->
            Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Ico(t.third, 22.dp, if (f) C.OnRed else C.Text)
                Column(Modifier.weight(1f)) {
                    T(t.first, 17.sp, if (f) C.OnRed else C.Text, FontWeight.SemiBold)
                    T(t.second, 13.sp, if (f) C.OnRed.copy(alpha = 0.7f) else C.Muted)
                }
                T("›", 20.sp, if (f) C.OnRed else C.Muted)  // i18n-ignore
            }
        }
    }
    if (showSkip) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Silver.Border))
        Focusable(onClick = { Onboard.finish(); nav.root(Screen.Home) }, shape = Rnd(14.dp), normal = Color.Transparent, focused = C.Red,
            focusBorderColor = Color.Transparent, zoom = 1f, modifier = Modifier.fillMaxWidth().height(54.dp).border(1.dp, Silver.Border, Rnd(14.dp))) { f ->
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                T("Preskoči za sada", 16.sp, if (f) C.OnRed else C.Text, FontWeight.SemiBold)
                T("Playlistu možeš dodati kasnije u Postavke › Playliste.", 12.sp, if (f) C.OnRed.copy(alpha = 0.7f) else C.Muted)
            }
        }
    }
    LaunchedEffect(Unit) { first.tryFocus() }
}

private fun Modifier.clip12(): Modifier = this.then(Modifier.background(Color(0x14FFFFFF), Rnd(12.dp)))
