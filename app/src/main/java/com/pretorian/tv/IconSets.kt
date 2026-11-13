package com.pretorian.tv

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/** Icon sets for the whole app. Built-in sets are vector drawables named <prefix>_<icon>. */
object IconSets {
    data class Style(val prefix: String, val label: String)
    data class IconSet(val id: String, val name: String, val styles: List<Style>, val about: String)

    val BUILTIN = listOf(
        IconSet("lc", "Lucide", listOf(Style("lct", "Tanke"), Style("lc", "Normalne"), Style("lcb", "Debele")), "Čiste linije · ISC"),
        IconSet("fl", "Fluent (Windows 11)", listOf(Style("fl", "Obris"), Style("flf", "Popunjene")), "Microsoft Fluent UI · MIT"),
        IconSet("ms", "Material (Android)", listOf(Style("ms", "Obris"), Style("msf", "Popunjene")), "Google Material Symbols · Apache 2.0"),
        IconSet("ph", "Phosphor", listOf(Style("phl", "Tanke"), Style("ph", "Normalne"), Style("phb", "Debele"), Style("phf", "Popunjene")), "Mekane, moderne · MIT"),
        IconSet("tb", "Tabler", listOf(Style("tbt", "Tanke"), Style("tb", "Normalne"), Style("tbb", "Debele")), "Oštre linije · MIT"),
    )

    /** the five icons shown as a preview of a set */
    val PREVIEW = listOf(R.drawable.lc_tv, R.drawable.lc_film, R.drawable.lc_monitor, R.drawable.lc_trophy, R.drawable.lc_settings)

    private val cache = HashMap<String, Int>()

    fun prefix(set: String, style: Int): String {
        val s = BUILTIN.firstOrNull { it.id == set } ?: return "lc"
        return s.styles.getOrElse(style) { s.styles.getOrElse(1) { s.styles[0] } }.prefix
    }

    /** drawable of icon [id] (a Lucide "lc_" drawable) in the chosen set; the Lucide one when missing */
    fun resolve(ctx: Context, id: Int, set: String, style: Int): Int {
        val pre = prefix(set, style)
        if (pre == "lc") return id
        val key = "$pre:$id"
        cache[key]?.let { return it }
        val name = try { ctx.resources.getResourceEntryName(id) } catch (e: Exception) { return id }
        val r = if (name.startsWith("lc_")) ctx.resources.getIdentifier(pre + "_" + name.removePrefix("lc_"), "drawable", ctx.packageName) else 0
        val out = if (r != 0) r else id
        cache[key] = out
        return out
    }

    /* ---------------------------------------------------------------- downloaded sets */

    const val INDEX = "https://raw.githubusercontent.com/KarlAgathon/PRETORIAN/main/iconsets/index.json"
    data class Remote(val id: String, val name: String, val about: String, val url: String)

    private val bitmaps = HashMap<String, ImageBitmap?>()

    fun dir(ctx: Context, set: String) = File(ctx.filesDir, "iconsets/$set")

    fun installed(ctx: Context): List<Pair<String, String>> =
        File(ctx.filesDir, "iconsets").listFiles()?.filter { it.isDirectory }?.map { d ->
            d.name to (File(d, "name.txt").takeIf { it.exists() }?.readText()?.trim() ?: d.name.removePrefix("dl_"))
        } ?: emptyList()

    fun downloaded(ctx: Context, set: String, id: Int): ImageBitmap? {
        val key = "$set:$id"
        if (bitmaps.containsKey(key)) return bitmaps[key]
        val name = try { ctx.resources.getResourceEntryName(id).removePrefix("lc_") } catch (e: Exception) { null }
        val f = name?.let { File(dir(ctx, set), "$it.png") }
        val bmp = if (f != null && f.exists()) try { BitmapFactory.decodeFile(f.path)?.asImageBitmap() } catch (e: Exception) { null } else null
        bitmaps[key] = bmp
        return bmp
    }

    suspend fun remote(): List<Remote> = withContext(Dispatchers.IO) {
        val a = JSONArray(String(Http.bytes(INDEX, 200_000)))
        (0 until a.length()).map { a.getJSONObject(it) }.map { Remote("dl_" + it.str("id"), it.str("name"), it.str("about"), it.str("url")) }
            .filter { it.url.startsWith("https://github.com/KarlAgathon/PRETORIAN/") || it.url.startsWith("https://raw.githubusercontent.com/KarlAgathon/PRETORIAN/") }
    }

    /** downloads a set (zip of white PNG icons named like the built-in ones) */
    suspend fun download(ctx: Context, r: Remote) = withContext(Dispatchers.IO) {
        val bytes = Http.bytes(r.url, 8_000_000)
        val d = dir(ctx, r.id); d.deleteRecursively(); d.mkdirs()
        ZipInputStream(bytes.inputStream()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val n = File(e.name).name
                if (!e.isDirectory && n.endsWith(".png") && n.matches(Regex("[a-z0-9_]+\\.png"))) File(d, n).writeBytes(z.readBytes())
            }
        }
        File(d, "name.txt").writeText(r.name)
        bitmaps.keys.removeAll { it.startsWith(r.id + ":") }
    }
}
