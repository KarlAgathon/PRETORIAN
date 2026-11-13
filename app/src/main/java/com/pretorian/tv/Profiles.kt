package com.pretorian.tv

import android.app.Activity
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** One person in the house: own favourites, watched, look, language, groups, simple mode. */
data class Profile(val id: String, val name: String, val avatar: String, val color: Int, val pinHash: String? = null) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("avatar", avatar).put("color", color).put("pin", pinHash ?: "")
    companion object {
        fun from(o: JSONObject) = Profile(o.str("id"), o.str("name"), o.str("avatar").ifBlank { "user" }, o.optInt("color"), o.str("pin").ifBlank { null })
    }
}

/**
 * Profiles keep their own copy of everything personal. Switching a profile stores the personal settings of
 * the current one in files/profiles/<id>.json, loads the other one's, and restarts the app.
 * Shared by the whole house: playlists, keys, reminders, parental PIN, sync account.
 */
object Profiles {
    val AVATARS = listOf("cat", "dog", "rabbit", "squirrel", "turtle", "bird", "fish", "panda", "rocket", "ghost", "crown", "gamepad_2",
        "heart", "smile", "baby", "music", "pizza", "bike", "car", "flower_2", "zap", "sword", "shield", "skull", "glasses", "popcorn",
        "clapperboard", "tv", "trophy", "star", "sun", "moon", "snowflake", "leaf", "cake", "coffee")
    val COLORS = listOf(0xFFE11D48, 0xFF2563EB, 0xFF16A34A, 0xFFF59E0B, 0xFF9333EA, 0xFF0D9488, 0xFFDB2777, 0xFFEA580C, 0xFF475569).map { it.toInt() }

    private val SHARED = setOf("playlists", "pl_multi", "pl_open", "epg_url", "tmdb_key", "omdb_key", "os_key", "os_user", "os_pass", "subdl_key",
        "upd_last", "backup_auto", "backup_auto_at", "ext_player", "file_app", "reminders", "rem_action", "rem_before", "rem_popup", "rem_wake",
        "notes", "new_items", "pin_hash", "pin_salt", "gemini_key", "sport_country")
    private val SHARED_PREFIX = listOf("prof", "gd_", "sync_")
    fun isShared(k: String) = k in SHARED || SHARED_PREFIX.any { k.startsWith(it) }

    var panel by mutableStateOf(false)
    var picker by mutableStateOf(false)
    var rev by mutableIntStateOf(0)

    private fun sp(c: Context) = c.getSharedPreferences("pretorian", Context.MODE_PRIVATE)
    private fun dir(c: Context) = File(c.filesDir, "profiles").apply { mkdirs() }

    fun list(c: Context): List<Profile> {
        val a = try { JSONArray(sp(c).getString("profiles", "[]")) } catch (e: Exception) { JSONArray() }
        val l = (0 until a.length()).map { Profile.from(a.getJSONObject(it)) }
        if (l.isNotEmpty()) return l
        // first start with profiles: what you have now becomes your profile
        val me = Profile("p1", tr("Moj profil"), "user", COLORS[0])
        save(c, listOf(me)); sp(c).edit().putString("profile_cur", me.id).apply()
        return listOf(me)
    }
    fun save(c: Context, l: List<Profile>) { val a = JSONArray(); l.forEach { a.put(it.toJson()) }; sp(c).edit().putString("profiles", a.toString()).apply(); rev++ }
    fun current(c: Context): Profile { val l = list(c); val id = sp(c).getString("profile_cur", null); return l.firstOrNull { it.id == id } ?: l.first() }
    var askAtStart: Boolean get() = Prefs.bool("profile_ask", true); set(v) = Prefs.put("profile_ask", v)

    fun hash(id: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest("pretorian:$id:$pin".toByteArray()).joinToString("") { "%02x".format(it) }

    fun add(c: Context, name: String, avatar: String, color: Int): Profile {
        val p = Profile("p" + System.currentTimeMillis().toString(36), name.ifBlank { tr("Profil") }, avatar, color)
        save(c, list(c) + p)
        // a new profile starts with the look and language of the one who made it
        try {
            val mine = snapshot(c).getJSONObject("prefs"); val start = JSONObject()
            mine.keys().forEach { k -> if (k.startsWith("look_") || k == "app_lang" || k == "meta_lang") start.put(k, mine.get(k)) }
            File(dir(c), p.id + ".json").writeText(JSONObject().put("prefs", start).put("files", JSONObject()).toString())
        } catch (_: Exception) {}
        return p
    }
    fun update(c: Context, p: Profile) = save(c, list(c).map { if (it.id == p.id) p else it })
    fun delete(c: Context, p: Profile) {
        if (p.id == current(c).id || list(c).size <= 1) return
        save(c, list(c).filter { it.id != p.id }); File(dir(c), p.id + ".json").delete()
    }

    /** personal settings of the profile that is open now (prefs + watched progress) */
    fun snapshot(c: Context): JSONObject {
        val p = JSONObject()
        sp(c).all.forEach { (k, v) ->
            if (isShared(k)) return@forEach
            val o = JSONObject()
            when (v) {
                is String -> o.put("t", "s").put("v", v); is Boolean -> o.put("t", "b").put("v", v)
                is Int -> o.put("t", "i").put("v", v); is Long -> o.put("t", "l").put("v", v)
                is Float -> o.put("t", "f").put("v", v.toDouble()); else -> return@forEach
            }
            p.put(k, o)
        }
        val files = JSONObject()
        File(c.filesDir, "progress.json").takeIf { it.exists() }?.let { files.put("progress.json", it.readText()) }
        return JSONObject().put("prefs", p).put("files", files)
    }

    private fun apply(c: Context, snap: JSONObject?) {
        val ed = sp(c).edit()
        sp(c).all.keys.filter { !isShared(it) }.forEach { ed.remove(it) }
        File(c.filesDir, "progress.json").delete()
        snap?.optJSONObject("prefs")?.let { p ->
            p.keys().forEach { k ->
                if (isShared(k)) return@forEach
                val v = p.getJSONObject(k)
                when (v.str("t")) {
                    "s" -> ed.putString(k, v.str("v")); "b" -> ed.putBoolean(k, v.optBoolean("v"))
                    "i" -> ed.putInt(k, v.optInt("v")); "l" -> ed.putLong(k, v.optLong("v")); "f" -> ed.putFloat(k, v.optDouble("v").toFloat())
                }
            }
        }
        ed.commit()
        snap?.optJSONObject("files")?.optString("progress.json")?.takeIf { it.isNotBlank() }?.let { File(c.filesDir, "progress.json").writeText(it) }
    }

    /** every profile's saved settings (for backup and Google Drive), the open one taken fresh */
    fun allSnapshots(c: Context): JSONObject {
        val o = JSONObject()
        val cur = current(c).id
        list(c).forEach { p ->
            if (p.id == cur) o.put(p.id, snapshot(c))
            else File(dir(c), p.id + ".json").takeIf { it.exists() }?.let { f -> try { o.put(p.id, JSONObject(f.readText())) } catch (_: Exception) {} }
        }
        return o
    }
    /** after a restore: writes the other profiles' files and loads the current one */
    fun restoreSnapshots(c: Context, o: JSONObject) {
        val cur = current(c).id
        o.keys().forEach { id -> if (id != cur) File(dir(c), "$id.json").writeText(o.getJSONObject(id).toString()) }
        o.optJSONObject(cur)?.let { apply(c, it) }
    }

    fun switchTo(c: Context, target: Profile) {
        val cur = current(c)
        if (cur.id == target.id) { picker = false; panel = false; return }
        File(dir(c), cur.id + ".json").writeText(snapshot(c).toString())
        val snap = File(dir(c), target.id + ".json").takeIf { it.exists() }?.let { try { JSONObject(it.readText()) } catch (e: Exception) { null } }
        apply(c, snap)
        sp(c).edit().putString("profile_cur", target.id).putBoolean("profile_skip_ask", true).commit()
        restart(c)
    }

    /** Starts the app again so every screen reads the new settings. */
    fun restart(c: Context) {
        val i = c.packageManager.getLaunchIntentForPackage(c.packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (i != null) c.startActivity(i)
        (c as? Activity)?.finishAffinity()
        Runtime.getRuntime().exit(0)
    }

    /** "Who is watching?" at start, when there are several profiles */
    fun onStart(c: Context) {
        val skip = Prefs.bool("profile_skip_ask")
        if (skip) Prefs.put("profile_skip_ask", false)
        picker = !skip && askAtStart && list(c).size > 1
    }
}

fun avatarIcon(ctx: Context, name: String): Int {
    val r = ctx.resources.getIdentifier("av_$name", "drawable", ctx.packageName)
    return if (r != 0) r else R.drawable.lc_user
}

@Composable
fun Avatar(p: Profile, size: Dp, ring: Boolean = false) {
    val ctx = LocalContext.current
    val c = Color(p.color)
    Box(Modifier.size(size).clip(CircleShape)
        .background(Brush.linearGradient(listOf(c, Color(android.graphics.Color.HSVToColor(FloatArray(3).also { android.graphics.Color.colorToHSV(p.color, it); it[0] = (it[0] + 35f) % 360f; it[2] = it[2] * 0.75f })))))
        .then(if (ring) Modifier.border(3.dp, Color.White, CircleShape) else Modifier),
        contentAlignment = Alignment.Center) {
        Icon(painterResource(avatarIcon(ctx, p.avatar)), contentDescription = p.name, tint = Color.White, modifier = Modifier.size(size * 0.55f))
    }
}

/** Full screen "Who is watching?" */
@Composable
fun ProfilePicker() {
    val ctx = LocalContext.current
    val list = remember(Profiles.rev) { Profiles.list(ctx) }
    val cur = remember(Profiles.rev) { Profiles.current(ctx) }
    var pinFor by remember { mutableStateOf<Profile?>(null) }
    var edit by remember { mutableStateOf<Profile?>(null) }
    var adding by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    fun choose(p: Profile) { if (p.pinHash != null && p.id != cur.id) pinFor = p else Profiles.switchTo(ctx, p) }
    androidx.activity.compose.BackHandler { Profiles.picker = false }
    Box(Modifier.fillMaxSize().background(C.Bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(30.dp)) {
            androidx.compose.foundation.Image(painterResource(R.drawable.logo_mark), contentDescription = null, modifier = Modifier.size(64.dp))
            T("Ko gleda?", 34.sp, C.Text, FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                itemsIndexed(list, key = { _, p -> p.id }) { i, p ->
                    Focusable(onClick = { choose(p) }, onMenu = { edit = p }, focusRequester = if (p.id == cur.id || (i == 0 && list.none { it.id == cur.id })) first else null,
                        shape = Rnd(24.dp), normal = Color.Transparent, focused = C.CardFocus, zoom = 1.08f, modifier = Modifier.width(170.dp).height(210.dp)) { f ->
                        Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Avatar(p, 120.dp, ring = f)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (p.pinHash != null) Ico(R.drawable.lc_lock, 14.dp, C.Muted)
                                T(p.name, 18.sp, C.Text, FontWeight.SemiBold, maxLines = 1)
                            }
                        }
                    }
                }
                item {
                    Focusable(onClick = { adding = true }, shape = Rnd(24.dp), normal = Color.Transparent, focused = C.CardFocus, zoom = 1.08f,
                        modifier = Modifier.width(170.dp).height(210.dp)) { _ ->
                        Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(120.dp).clip(CircleShape).background(C.Card).border(2.dp, C.Line, CircleShape), contentAlignment = Alignment.Center) {
                                Ico(R.drawable.lc_user_plus, 48.dp, C.Soft)
                            }
                            T("Dodaj profil", 18.sp, C.Soft, FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                }
            }
            T("Drži OK na profilu za uređivanje", 13.sp, C.Muted)
        }
    }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(200); first.tryFocus() }
    pinFor?.let { p ->
        PinDialog(p.name, tr("Unesi PIN profila"), onDismiss = { pinFor = null }) { pin ->
            if (Profiles.hash(p.id, pin) == p.pinHash) { pinFor = null; Profiles.switchTo(ctx, p); null } else tr("Pogrešan PIN")
        }
    }
    if (adding) ProfileEditor(null, onDismiss = { adding = false })
    edit?.let { p -> ProfileEditor(p, onDismiss = { edit = null }) }
}

/** Add or change a profile: name, picture, colour, PIN, delete. */
@Composable
fun ProfileEditor(p: Profile?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(p?.name ?: "") }
    var avatar by remember { mutableStateOf(p?.avatar ?: Profiles.AVATARS.random()) }
    var color by remember { mutableIntStateOf(p?.color ?: Profiles.COLORS.random()) }
    var pinSet by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val isCur = p != null && p.id == Profiles.current(ctx).id
    Popup(if (p == null) tr("Novi profil") else tr("Uredi profil"), onDismiss, width = 760.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Avatar(Profile("x", name, avatar, color), 84.dp)
            Field("Ime profila (npr. Svire, Supruga, Djeca)", name, { name = it.take(20) }, Modifier.weight(1f))
        }
        T("Slika", 13.sp, C.Muted, FontWeight.SemiBold)
        LazyVerticalGrid(GridCells.Fixed(12), Modifier.fillMaxWidth().height(138.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(Profiles.AVATARS) { a ->
                Focusable(onClick = { avatar = a }, shape = CircleShape, normal = if (a == avatar) Color(color) else C.Card, zoom = 1.12f,
                    modifier = Modifier.size(42.dp)) { _ ->
                    Icon(painterResource(avatarIcon(ctx, a)), contentDescription = a, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(22.dp))
                }
            }
        }
        T("Boja", 13.sp, C.Muted, FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Profiles.COLORS.forEach { c ->
                Focusable(onClick = { color = c }, shape = CircleShape, normal = Color(c), zoom = 1.15f,
                    modifier = Modifier.size(38.dp).then(if (c == color) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)) { _ -> }
            }
        }
        if (p != null) OptionRow(if (p.pinHash != null) "Ukloni PIN profila" else "Postavi PIN profila", R.drawable.lc_lock,
            sub = "Da drugi ne ulaze u ovaj profil") {
            if (p.pinHash != null) { Profiles.update(ctx, p.copy(pinHash = null)); onDismiss() } else pinSet = true
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button("Spremi", R.drawable.lc_check, primary = true) {
                if (p == null) Profiles.add(ctx, name, avatar, color) else Profiles.update(ctx, p.copy(name = name.ifBlank { p.name }, avatar = avatar, color = color))
                onDismiss()
            }
            if (p != null && !isCur && Profiles.list(ctx).size > 1) Button("Obriši profil", R.drawable.lc_trash_2) { confirmDelete = true }
        }
    }
    if (pinSet && p != null) PinDialog(tr("Novi PIN profila"), tr("Izaberi 4 cifre"), onDismiss = { pinSet = false }) { pin ->
        Profiles.update(ctx, p.copy(pinHash = Profiles.hash(p.id, pin))); pinSet = false; onDismiss(); null
    }
    if (confirmDelete && p != null) Popup(trf("Obrisati profil %s?", p.name), { confirmDelete = false }) {
        T("Njegovi favoriti, gledano i izgled se brišu. Playliste ostaju.", 14.sp, C.Soft, maxLines = 3)
        OptionRow("Da, obriši", R.drawable.lc_trash_2, color = C.Danger) { Profiles.delete(ctx, p); confirmDelete = false; onDismiss() }
    }
}

/** Opens from the logo / profile picture at the top left: everything about profiles and sync. */
@Composable
fun ProfilePanel(nav: Nav) {
    val ctx = LocalContext.current
    val cur = remember(Profiles.rev) { Profiles.current(ctx) }
    val others = remember(Profiles.rev) { Profiles.list(ctx).filter { it.id != cur.id } }
    var edit by remember { mutableStateOf<Profile?>(null) }
    var adding by remember { mutableStateOf(false) }
    var pinFor by remember { mutableStateOf<Profile?>(null) }
    var syncing by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Popup(tr("Profil"), { Profiles.panel = false }, width = 640.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Avatar(cur, 64.dp)
            Column(Modifier.weight(1f)) {
                T(cur.name, 20.sp, C.Text, FontWeight.Bold, maxLines = 1)
                T(Drive.statusLine(), 13.sp, if (Drive.signedIn) C.Green else C.Muted, maxLines = 2)
            }
        }
        if (others.isNotEmpty()) {
            T("Promijeni profil", 13.sp, C.Muted, FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(others, key = { _, p -> p.id }) { _, p ->
                    Focusable(onClick = { if (p.pinHash != null) pinFor = p else Profiles.switchTo(ctx, p) }, shape = Rnd(16.dp), normal = C.Card, zoom = 1.05f,
                        modifier = Modifier.width(120.dp).height(118.dp)) { _ ->
                        Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Avatar(p, 62.dp)
                            T(p.name, 13.sp, C.Text, FontWeight.Medium, maxLines = 1, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
        OptionRow("Dodaj profil", R.drawable.lc_user_plus) { adding = true }
        OptionRow("Uredi ovaj profil", R.drawable.lc_pencil, sub = "Ime, slika, boja, PIN") { edit = cur }
        OptionRow("Pitaj \"Ko gleda?\" pri pokretanju", R.drawable.lc_users, value = tr(if (Profiles.askAtStart) "Da" else "Ne")) {
            Profiles.askAtStart = !Profiles.askAtStart; Profiles.rev++
        }
        T("Google Drive", 13.sp, C.Muted, FontWeight.SemiBold)
        if (!Drive.signedIn) OptionRow("Prijavi se Google nalogom", R.drawable.lc_log_in, sub = "Backup i prenos na drugi uređaj preko interneta", color = C.Green) {
            Profiles.panel = false; Drive.loginOpen = true
        } else {
            OptionRow(if (syncing) "Sinhronizujem…" else "Sinhronizuj sada", R.drawable.lc_cloud_upload, sub = Drive.email) {
                if (!syncing) { syncing = true; scope.launchSafe { Drive.upload(ctx); syncing = false; Profiles.rev++ } }
            }
            OptionRow("Vrati s Google Drivea", R.drawable.lc_cloud_download, sub = "Povuci sve postavke i profile na ovaj uređaj") {
                Profiles.panel = false; Drive.restoreAsk = true
            }
            OptionRow("Odjavi se", R.drawable.lc_log_out) { Drive.logout(); Profiles.rev++ }
        }
        OptionRow("Backup na disk ili USB", R.drawable.lc_archive) { Profiles.panel = false; nav.root(Screen.Settings); SettingsNav.section = "backup" }
    }
    if (adding) ProfileEditor(null, onDismiss = { adding = false })
    edit?.let { p -> ProfileEditor(p, onDismiss = { edit = null }) }
    pinFor?.let { p ->
        PinDialog(p.name, tr("Unesi PIN profila"), onDismiss = { pinFor = null }) { pin ->
            if (Profiles.hash(p.id, pin) == p.pinHash) { pinFor = null; Profiles.switchTo(ctx, p); null } else tr("Pogrešan PIN")
        }
    }
}

/** The round button at the top left: your profile picture (opens the profile panel). */
@Composable
fun ProfileButton(size: Dp) {
    val ctx = LocalContext.current
    val cur = remember(Profiles.rev) { Profiles.current(ctx) }
    Focusable(onClick = { Profiles.panel = true }, shape = CircleShape, normal = Color.Transparent, zoom = 1.1f, modifier = Modifier.size(size + 6.dp)) { f ->
        Box(Modifier.align(Alignment.Center)) { Avatar(cur, size, ring = f) }
    }
}

/** Lets other screens open a settings section (e.g. Backup from the profile panel). */
object SettingsNav { var section: String? = null }

/** launch that never crashes the app */
fun kotlinx.coroutines.CoroutineScope.launchSafe(block: suspend () -> Unit) =
    launch { try { block() } catch (_: Exception) {} }
