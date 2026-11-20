package com.pretorian.tv

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BACKUP_PARTS = listOf("Playliste i lozinke", "EPG izvori", "Grupe i kanali (redoslijed, sakriveno, imena)", "Profili i PIN-ovi", "Favoriti",
    "Odgledano i „Nastavi gledanje”", "Podsjetnici", "Izgled (tema, boja, veličina)", "Jezik, titlovi i zvuk", "Sport (sportovi, takmičenja)", "AI ključevi")

@Composable
internal fun BackupSettings() {
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
            // a copy in Download / USB can be read by other apps: with a password it is locked (AES), and so are the copies before an update
            var passDlg by remember { mutableStateOf(false) }
            SetRow("Lozinka za kopije", if (Backup.pass.isBlank()) tr("nema: lozinke playlista su u fajlu čitljive") else tr("kopije u Download i na USB-u su zaključane"),
                tr(if (Backup.pass.isBlank()) "Postavi" else "Promijeni"), valueColor = if (Backup.pass.isBlank()) C.Amber else C.Green) { passDlg = true }
            if (passDlg) TextDialog("Lozinka za kopije", "Lozinka (prazno = bez lozinke)", onDismiss = { passDlg = false }) { v -> Prefs.put("backup_pass", v.trim()); passDlg = false; rev++ }
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
