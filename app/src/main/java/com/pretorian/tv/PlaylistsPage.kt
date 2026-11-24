package com.pretorian.tv

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phase B: one coordinator for playlist refreshes. Active playlist = full catalog reload; inactive ones = only login,
 * server and expiry check (never a catalog download). No duplicates; a request survives leaving the screen and an app
 * restart (pending list, 12 h); while a film or channel plays full screen the work waits.
 */
object RefreshCenter {
    enum class St { Idle, Waiting, Running, Done, Partial, Failed }
    var state by mutableStateOf(St.Idle)
    var done by mutableIntStateOf(0)
    var total by mutableIntStateOf(0)
    var rev by mutableIntStateOf(0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = LinkedHashSet<String>()
    @Volatile private var running = false

    private fun savePending() {
        val a = JSONArray(); synchronized(queue) { queue.forEach { a.put(it) } }
        Prefs.put("refresh_pending", JSONObject().put("at", System.currentTimeMillis()).put("ids", a).toString())
    }
    /** at start: a request that did not finish (app closed) runs again, when it is not older than 12 h */
    fun resume(ctx: Context) {
        try {
            val o = JSONObject(Prefs.str("refresh_pending", "{}"))
            if (System.currentTimeMillis() - o.optLong("at") > 12 * 3600_000L) { Prefs.put("refresh_pending", ""); return }
            val a = o.optJSONArray("ids") ?: return
            val ids = (0 until a.length()).map { a.getString(it) }
            if (ids.isNotEmpty()) request(ctx, ids)
        } catch (_: Exception) {}
    }

    fun request(ctx: Context, ids: List<String>) {
        synchronized(queue) { ids.forEach { queue.add(it) } }
        savePending()
        if (running) { total = done + synchronized(queue) { queue.size }; return }
        running = true; done = 0; total = synchronized(queue) { queue.size }; state = St.Waiting
        scope.launch {
            var fails = 0; var oks = 0
            while (true) {
                // full-screen playback first: the job waits without holding anything
                while (ChannelHealth.playerOn) { state = St.Waiting; delay(3000) }
                val id = synchronized(queue) { queue.firstOrNull()?.also { queue.remove(it) } } ?: break
                state = St.Running
                val p = Store.playlists(ctx).firstOrNull { it.id == id }
                if (p != null) { if (one(ctx, p)) oks++ else fails++ }
                done++; savePending(); rev++
            }
            Prefs.put("refresh_pending", "")
            state = when { fails == 0 -> St.Done; oks == 0 -> St.Failed; else -> St.Partial }
            running = false; rev++
        }
    }

    private suspend fun one(ctx: Context, p: Playlist): Boolean {
        val ok = try {
            val info = Repo.test(p)
            val up = if (p.type == SourceType.XTREAM) p.copy(exp = info) else p
            Store.savePlaylists(ctx, Store.playlists(ctx).map { if (it.id == up.id) up.copy(active = it.active) else it })
            PlCheck.put(p.id, true, null); true
        } catch (e: Exception) { PlCheck.put(p.id, false, e.message); false }
        if (ok && p.active) withContext(Dispatchers.Main) {
            Repo.dropCatalogs()
            if (ServerOrder.on) Ovr(ctx, p.id).takeServerOrder()
            Import.run(p, reset = true)
        }
        return ok
    }

    fun label(): String? = when (state) {
        St.Idle -> null
        St.Waiting -> tr("Čeka završetak reprodukcije")
        St.Running -> trf("Osvježavam %s/%s", (done + 1).coerceAtMost(total), total)
        St.Done -> tr("Osvježeno")
        St.Partial -> tr("Djelimično osvježeno")
        St.Failed -> tr("Osvježavanje nije uspjelo")
    }
}

/** The last connection check of a playlist: time, ok, a short message without secrets (pl_check_<id>). */
object PlCheck {
    fun put(id: String, ok: Boolean, msg: String?) =
        Prefs.put("pl_check_$id", JSONObject().put("at", System.currentTimeMillis()).put("ok", ok).put("msg", Redact.text(msg ?: "").take(160)).toString())
    fun get(id: String): Triple<Long, Boolean, String>? = try {
        val o = JSONObject(Prefs.str("pl_check_$id", "")); Triple(o.optLong("at"), o.optBoolean("ok"), o.optString("msg"))
    } catch (_: Exception) { null }
}

private fun typeName(t: SourceType) = when (t) { SourceType.XTREAM -> "Xtream"; SourceType.STALKER -> "Stalker"; else -> "M3U" }  // i18n-ignore
private fun typeIcon(t: SourceType) = when (t) { SourceType.XTREAM -> R.drawable.lc_tv; SourceType.STALKER -> R.drawable.lc_monitor; else -> R.drawable.lc_list }
private fun expiry(p: Playlist): String? = p.exp.toLongOrNull()?.takeIf { it > 0 }?.let { SimpleDateFormat("dd.MM.yyyy", Locale.US).format(Date(it * 1000)) }  // i18n-ignore

/** Settings › Playliste: the playlists, then reorder, groups and channels, EPG sources and the two refresh actions. */
@Composable
internal fun PlaylistsOverview(nav: Nav, ses: SetSession, e: SetEntry) {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val rr = RefreshCenter.rev
    var list by remember { mutableStateOf(Store.playlists(ctx)) }
    var reorder by remember { mutableStateOf(false) }
    LaunchedEffect(RefreshCenter.rev) { list = Store.playlists(ctx) }
    if (reorder) { PlaylistReorder(list, onDone = { list = Store.playlists(ctx); reorder = false; nav.playlistsChanged() }); return }
    val rows = ArrayList<SetItem>()
    rows += SetItem("add") { fr -> SetNavRow("Dodaj playlistu", "Xtream, M3U ili Stalker · i s telefona preko QR koda", R.drawable.lc_plus, focusRequester = fr) {
        EditPlaylist.target = null; nav.push(Screen.AddPlaylist) } }
    list.forEach { p ->
        rows += SetItem("pl:" + p.id) { fr ->
            val sub = listOfNotNull(typeName(p.type), tr(if (p.active) "Aktivna" else "Neaktivna"), expiry(p)?.let { trf("Ističe %s", it) }).joinToString(" · ")
            SetNavRow(p.name, sub, typeIcon(p.type), focusRequester = fr) { PlEdit.id = p.id; ses.push("pldetail") }
        }
    }
    if (list.size > 1) rows += SetItem("order") { fr -> SetNavRow("Promijeni redoslijed", null, R.drawable.lc_move_vertical, focusRequester = fr) { reorder = true } }
    if (list.isNotEmpty()) rows += SetItem("groups") { fr -> SetNavRow("Grupe i kanali", "Redoslijed, sakrivanje i zaključavanje", R.drawable.lc_layers, focusRequester = fr) {
        PlEdit.id = null; ses.push("groups") } }
    rows += SetItem("epgall") { fr -> SetNavRow("Svi EPG izvori", null, R.drawable.lc_calendar, focusRequester = fr) { PlEdit.id = null; ses.push("epg") } }
    if (list.isNotEmpty()) rows += SetItem("refresh") { fr ->
        Box(Modifier.focusRequesterOrNot(fr)) {
            SetActionPair("Osvježi sve EPG-ove" to R.drawable.lc_calendar, "Osvježi sve liste" to R.drawable.lc_refresh_cw,
                onA = { GuideSync.later(ctx, force = true); Toast.makeText(ctx, tr("EPG se osvježava u pozadini"), Toast.LENGTH_SHORT).show() },
                onB = { RefreshCenter.request(ctx, Store.playlists(ctx).map { it.id }) })
        }
    }
    Column(Modifier.fillMaxSize()) {
        RefreshCenter.label()?.let { T(it, 13.sp, C.Muted, modifier = Modifier.padding(start = 18.dp, bottom = 2.dp)) }
        if (list.isEmpty()) T("Nema aktivne playliste", 15.sp, C.Soft, modifier = Modifier.padding(start = 18.dp, top = 8.dp))
        SetList(e, rows, Modifier.weight(1f))
    }
}

/** lets the list's focus restore reach a row made of several focusable parts (its first part gets the focus) */
private fun Modifier.focusRequesterOrNot(fr: androidx.compose.ui.focus.FocusRequester) =
    this.then(androidx.compose.ui.Modifier.focusRequester(fr).focusGroup())

/** One playlist: source information, edit, on/off, refresh, its EPG, delete. */
@Composable
internal fun PlaylistDetail(nav: Nav, ses: SetSession, e: SetEntry) {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val rr = RefreshCenter.rev
    var rev by remember { mutableIntStateOf(0) }
    val p = remember(rev, RefreshCenter.rev) { Store.playlists(ctx).firstOrNull { it.id == PlEdit.id } }
    if (p == null) { LaunchedEffect(Unit) { ses.pop() }; return }
    var info by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf(false) }
    var pinDel by remember { mutableStateOf(false) }
    val rows = ArrayList<SetItem>()
    rows += SetItem("info") { fr -> SetNavRow("Informacije o izvoru", typeName(p.type) + (expiry(p)?.let { " · " + trf("Ističe %s", it) } ?: ""), R.drawable.lc_info, focusRequester = fr) { info = true } }
    rows += SetItem("edit") { fr -> SetNavRow("Uredi playlistu", "Naziv, adresa, korisnik, lozinka", R.drawable.lc_pencil, focusRequester = fr) {
        EditPlaylist.target = p; nav.push(Screen.AddPlaylist) } }
    rows += SetItem("active") { fr -> Box(Modifier.focusRequesterOrNot(fr)) {
        SetSwitch(if (p.active) "Aktivna" else "Neaktivna", "Samo jedna playlista može biti aktivna", p.active) {
            Store.activate(ctx, p.id); Repo.clear(); nav.playlistsChanged(); rev++
            if (Store.playlists(ctx).firstOrNull { it.id == p.id }?.active == true) Import.missing(ctx)
        } } }
    rows += SetItem("refresh") { fr -> SetNavRow("Osvježi", if (p.active) "Kanali, filmovi i serije" else "Samo provjera prijave i datuma isteka", R.drawable.lc_refresh_cw,
        value = RefreshCenter.label(), chevron = false, focusRequester = fr) { RefreshCenter.request(ctx, listOf(p.id)) } }
    rows += SetItem("epg") { fr -> SetNavRow("EPG", if (p.active) null else "EPG se osvježava kad je playlista aktivna.", R.drawable.lc_calendar, focusRequester = fr) {
        PlEdit.id = p.id; ses.push("epg") } }
    rows += SetItem("del") { fr -> SetNavRow("Obriši", null, R.drawable.lc_trash_2, chevron = false, focusRequester = fr) {
        if (Prefs.bool("pin_delpl") && Lock.hasPin()) pinDel = true else del = true } }
    Column(Modifier.fillMaxSize()) {
        T(p.name, 22.sp, C.Text, FontWeight.Bold, modifier = Modifier.padding(start = 18.dp, bottom = 4.dp))
        SetList(e, rows, Modifier.weight(1f))
    }
    if (info) {
        var show by remember { mutableStateOf(false) }
        val chk = PlCheck.get(p.id)
        Popup(tr("Informacije o izvoru"), { info = false }) {
            @Composable fun kv(k: String, v: String) = Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                T(k, 14.sp, C.Muted, modifier = Modifier.width(180.dp)); T(v, 14.sp, C.Text, FontWeight.SemiBold, maxLines = 2, modifier = Modifier.weight(1f)) }
            kv(tr("Naziv"), p.name); kv(tr("Vrsta"), typeName(p.type))
            if (p.server.isNotBlank()) kv(tr("Server"), p.server)
            if (p.url.isNotBlank()) kv(tr("Link"), if (show) p.url else Redact.text(p.url))
            if (p.user.isNotBlank()) kv(tr("Korisnik"), p.user)
            if (p.pass.isNotBlank()) kv(tr("Lozinka"), if (show) p.pass else "••••••••")
            if (p.mac.isNotBlank()) kv("MAC", p.mac)  // i18n-ignore
            kv(tr("Stanje"), tr(if (p.active) "Aktivna" else "Neaktivna"))
            expiry(p)?.let { kv(tr("Ističe"), it) }
            chk?.let { (at, ok, msg) -> kv(tr("Zadnja provjera"), SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).format(Date(at)) + " · " + tr(if (ok) "u redu" else "nije uspjela") +  // i18n-ignore
                (if (!ok && msg.isNotBlank()) " · $msg" else "")) }
            OptionRow(if (show) "Sakrij lozinku" else "Prikaži lozinku", if (show) R.drawable.lc_eye_off else R.drawable.lc_eye) { show = !show }
        }
    }
    if (pinDel) PinDialog(p.name, tr("Unesi PIN"), onDismiss = { pinDel = false }) { pin -> val err = Lock.check(pin); if (err == null) { pinDel = false; del = true }; err }
    if (del) SetConfirm(trf("Obrisati %s?", p.name), tr(if (p.active) "Ovo je aktivna playlista. Nakon brisanja nijedna neće biti aktivna." else "Playlista i njen EPG od providera bit će uklonjeni."),
        tr("Obriši"), danger = true, onCancel = { del = false }) {
        deletePlaylist(ctx, p); del = false; nav.playlistsChanged(); ses.pop()
    }
}

/** delete: the playlist, its provider guide and its EPG assignments go; shared EPG sources stay */
fun deletePlaylist(ctx: Context, p: Playlist) {
    Watch.removePlaylist(ctx, p.id)
    Store.savePlaylists(ctx, Store.playlists(ctx).filter { it.id != p.id })
    EpgSources.list().forEach { s ->
        when {
            s.pid == p.id -> EpgSources.delete(s)
            p.id in s.def -> EpgSources.update(s.copy(def = s.def - p.id))
        }
    }
    try { CatalogDb.forget(p.id) } catch (_: Exception) {}
    Prefs.remove("pl_check_" + p.id)
    Repo.clear()
}

/** "Promijeni redoslijed": OK lifts a playlist, ▲▼ move it, OK puts it down; Back saves nothing and leaves. */
@Composable
private fun PlaylistReorder(start: List<Playlist>, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var order by remember { mutableStateOf(start) }
    var lifted by remember { mutableStateOf<String?>(null) }
    androidx.activity.compose.BackHandler { if (lifted != null) { order = start; lifted = null } else onDone() }
    SetPage("Promijeni redoslijed", "OK podigni · ▲ ▼ pomjeri · OK spusti") {
        order.forEachIndexed { i, p ->
            val up = lifted == p.id
            Focusable(onClick = {
                if (up) { Store.savePlaylists(ctx, order.map { o -> Store.playlists(ctx).first { it.id == o.id } }); lifted = null } else lifted = p.id
            }, shape = Rnd(14.dp), normal = if (up) C.CardHi else androidx.compose.ui.graphics.Color.Transparent, focused = if (up) C.CardHi else Dawn.pill, zoom = 1f,
                borderColor = if (up) androidx.compose.ui.graphics.Color.White else null, focusBorderColor = if (up) androidx.compose.ui.graphics.Color.White else null,
                modifier = Modifier.fillMaxWidth().height(60.dp).onPreviewKeyEventSafe { up2, d ->
                    if (!up || !up2) return@onPreviewKeyEventSafe false
                    val j = i + d; if (j !in order.indices) return@onPreviewKeyEventSafe true
                    order = order.toMutableList().also { l -> l.add(j, l.removeAt(i)) }; true
                }) { _ ->
                Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Ico(if (up) R.drawable.lc_grip_vertical else typeIcon(p.type), 22.dp, C.Text)
                    T(p.name, 17.sp, C.Text, FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    T("${i + 1}", 14.sp, C.Muted)
                }
            }
        }
    }
}

/** ▲ / ▼ key-down on a lifted row: d = -1 / +1 */
private fun Modifier.onPreviewKeyEventSafe(on: (Boolean, Int) -> Boolean) = this.then(androidx.compose.ui.Modifier.onPreviewKeyEvent { e ->
    if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
    when (e.key) {
        androidx.compose.ui.input.key.Key.DirectionUp -> on(true, -1)
        androidx.compose.ui.input.key.Key.DirectionDown -> on(true, 1)
        else -> false
    }
})
