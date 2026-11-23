package com.pretorian.tv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun MoviesScreen(nav: Nav) = VodBrowser(nav, series = false)

@Composable
fun SeriesScreen(nav: Nav) = VodBrowser(nav, series = true)

private data class Poster(val id: String, val name: String, val image: String?, val rating: String?, val url: String?, val isNew: Boolean,
                          val open: () -> Unit, val play: (() -> Unit)? = null, val added: Long = 0)

/** Movies/Series remember the open category and the poster you opened, so Back from a film or series lands on it again. */
object VodMemory {
    val group = HashMap<String, String>()
    val item = HashMap<String, String>()
    /** true right after a poster was opened: on return focus goes to that poster, not to the categories */
    var returning = false
    /** the category a title was opened from (a provider category like "NETFLIX SERIJE" tells the platform) */
    val catOf = HashMap<String, String>()
    /** New Dawn: the search typed in Movies / Series, kept while you open a title and come back */
    val query = HashMap<String, String>()
    /** one-menu-at-a-time browser: the open levels and the focused row of each, per Movies / Series */
    val stack = HashMap<String, androidx.compose.runtime.snapshots.SnapshotStateList<Any>>()
    val levels = HashMap<String, HashMap<String, Int>>()
}

@Composable
private fun VodBrowser(nav: Nav, series: Boolean) {
    DawnVod(nav, series)
}

@Composable
fun Badge(text: String, bg: Color, fg: Color) {
    Box(Modifier.clip(Rnd(6.dp)).background(bg).padding(horizontal = 6.dp, vertical = 2.dp)) { T(text, 11.sp, fg, FontWeight.Bold) }
}

/** "Episode 3" instead of "NF - House Of Guinness 4K (2025) (GB) - S01E03 - Episode 3". */
fun episodeTitle(raw: String, num: Int): String {
    var t = raw
    Regex("[Ss]\\d{1,2}\\s*[Ee]\\d{1,3}").find(t)?.let { t = t.substring(it.range.last + 1) }
    t = t.trim(' ', '-', '|', ':', '.')
    t = t.replace(Regex("\\b(4K|UHD|FHD|HD|SD|HEVC)\\b", RegexOption.IGNORE_CASE), "").trim(' ', '-')
    if (t.isBlank() || Regex("^(Episode|Epizoda|Folge|Ep\\.?)\\s*\\d+$", RegexOption.IGNORE_CASE).matches(t)) return trf("Epizoda %s", num)
    return t
}

/** The trailer apps PRETORIAN offers: YouTube, SmartTube, TizenTube (nothing else, no system leftovers). */
object TrailerApps {
    private val FAMILIES = listOf(  // i18n-ignore
        "YouTube" to listOf("com.google.android.youtube.tv", "com.google.android.youtube"),
        "SmartTube" to listOf("com.liskovsoft.smarttubetv.beta", "com.liskovsoft.smarttubetv", "com.teamsmart.videomanager.tv"),
        "TizenTube" to listOf("io.gh.reisxd.tizentube.cobalt", "io.gh.reisxd.tizentube", "com.reisxd.tizentube"),
    )
    private fun has(ctx: Context, pkg: String) = try { ctx.packageManager.getPackageInfo(pkg, 0); true } catch (e: Exception) { false }
    /** name and installed package (null = not installed), always in the same order */
    fun slots(ctx: Context): List<Pair<String, String?>> = FAMILIES.map { (n, pk) -> n to (pk.firstOrNull { has(ctx, it) } ?: if (n == "TizenTube") findByName(ctx, "tizentube", "cobalt") else null) }  // i18n-ignore

    /** a build under another package name (e.g. TizenTube Cobalt): look for it among the box's TV apps by name */
    private fun findByName(ctx: Context, vararg words: String): String? = try {
        val pm = ctx.packageManager
        val apps = pm.queryIntentActivities(android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LEANBACK_LAUNCHER), 0) +
            pm.queryIntentActivities(android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER), 0)
        apps.firstOrNull { ri ->
            val pkg = ri.activityInfo.packageName.lowercase(); val label = ri.loadLabel(pm).toString().lowercase()
            pkg != "com.google.android.youtube.tv" && words.any { w -> w in pkg || w in label } && ("tube" in pkg || "tube" in label || "cobalt" in pkg)
        }?.activityInfo?.packageName
    } catch (e: Exception) { null }
    fun installed(ctx: Context): List<Pair<String, String>> = slots(ctx).mapNotNull { (n, p) -> p?.let { it to n } }

    /** The app picked in Settings, or by default: TizenTube first (no ads), then SmartTube, then YouTube. */
    fun chosen(ctx: Context): String? {
        val all = installed(ctx)
        val pref = Prefs.str("trailer_app")
        if (pref.isNotBlank() && all.any { it.first == pref }) return pref
        return (all.firstOrNull { it.second == "TizenTube" } ?: all.firstOrNull { it.second == "SmartTube" } ?: all.firstOrNull())?.first  // i18n-ignore
    }
}

/** no known trailer id: look up the first YouTube result ourselves, so apps that ignore search links (TizenTube) still play it */
private fun findTrailerId(title: String): String? = try {
    val q = Uri.encode("${cleanTitle(title).first} trailer")
    val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36" // i18n-ignore
    val cookie = "CONSENT=YES+1; SOCS=CAI" // i18n-ignore
    val url = "https://www.youtube.com/results?search_query=$q&hl=en" // i18n-ignore
    val html = Http.request(url, mapOf("User-Agent" to ua, "Accept" to "text/html", "Cookie" to cookie)) // i18n-ignore
    Regex("\"videoId\":\"([A-Za-z0-9_-]{11})\"").find(html)?.groupValues?.get(1) // i18n-ignore
} catch (_: Exception) { null }

fun openTrailer(ctx: Context, title: String, youtubeKey: String?) {
    if (youtubeKey == null) {
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        Thread {
            val id = findTrailerId(title)
            main.post { launchTrailer(ctx, title, id) }
        }.start()
        return
    }
    launchTrailer(ctx, title, youtubeKey)
}

private fun launchTrailer(ctx: Context, title: String, youtubeKey: String?) {
    val pkg = TrailerApps.chosen(ctx)
    val url = if (youtubeKey != null) "https://www.youtube.com/watch?v=$youtubeKey"
              else "https://www.youtube.com/results?search_query=" + Uri.encode("${cleanTitle(title).first} trailer")
    // straight into the chosen app, no question
    if (pkg != null) {
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (_: Exception) {}
        if (youtubeKey == null) try {
            ctx.startActivity(Intent(Intent.ACTION_SEARCH).setPackage(pkg).putExtra("query", "${cleanTitle(title).first} trailer").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return
        } catch (_: Exception) {}
    }
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    catch (e: Exception) { Toast.makeText(ctx, tr("YouTube nije instaliran"), Toast.LENGTH_LONG).show() }
}

/** What was picked on the movie page before playing. */
data class TrackChoice(val subFile: File? = null, val subOff: Boolean = false, val sub: StreamTrack? = null, val audio: StreamTrack? = null,
                       val speed: Float = 1f, val fmt: Int = -1) {
    fun apply(i: PlayItem) = i.copy(subFile = subFile?.path, subOff = subOff, subLang = sub?.lang, subLabel = sub?.label,
        audioLang = audio?.lang, audioLabel = audio?.label, speed = speed, fmt = fmt)
}

@Composable
fun MovieDetailScreen(v: Vod, nav: Nav) {
    OnyxMovieDetail(v, nav)
}

@Composable
fun SeriesDetailScreen(s: SeriesItem, nav: Nav) {
    OnyxSeriesDetail(s, nav)
}
