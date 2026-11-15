package com.pretorian.tv

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.io.File

/** Everything about this box for Settings › About › This device. */
object DeviceInfo {
    private fun gb(b: Long) = String.format("%.1f GB", b / 1_073_741_824.0)  // i18n-ignore

    fun maxGhz(): String? = try {
        val f = File("/sys/devices/system/cpu").listFiles { x -> x.name.matches(Regex("cpu[0-9]+")) }
            ?.mapNotNull { File(it, "cpufreq/cpuinfo_max_freq").takeIf { f -> f.exists() }?.readText()?.trim()?.toLongOrNull() }?.maxOrNull()
        f?.let { String.format("%.2f GHz", it / 1_000_000.0) }  // i18n-ignore
    } catch (e: Exception) { null }

    /** The graphics chip, asked from a tiny hidden OpenGL context. */
    fun gpu(): String? = try {
        val dpy = android.opengl.EGL14.eglGetDisplay(android.opengl.EGL14.EGL_DEFAULT_DISPLAY)
        val v = IntArray(2); android.opengl.EGL14.eglInitialize(dpy, v, 0, v, 1)
        val cfg = arrayOfNulls<android.opengl.EGLConfig>(1); val n = IntArray(1)
        android.opengl.EGL14.eglChooseConfig(dpy, intArrayOf(android.opengl.EGL14.EGL_RENDERABLE_TYPE, android.opengl.EGL14.EGL_OPENGL_ES2_BIT,
            android.opengl.EGL14.EGL_SURFACE_TYPE, android.opengl.EGL14.EGL_PBUFFER_BIT, android.opengl.EGL14.EGL_NONE), 0, cfg, 0, 1, n, 0)
        val ctx = android.opengl.EGL14.eglCreateContext(dpy, cfg[0], android.opengl.EGL14.EGL_NO_CONTEXT,
            intArrayOf(android.opengl.EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, android.opengl.EGL14.EGL_NONE), 0)
        val surf = android.opengl.EGL14.eglCreatePbufferSurface(dpy, cfg[0], intArrayOf(android.opengl.EGL14.EGL_WIDTH, 1, android.opengl.EGL14.EGL_HEIGHT, 1, android.opengl.EGL14.EGL_NONE), 0)
        android.opengl.EGL14.eglMakeCurrent(dpy, surf, surf, ctx)
        val r = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_RENDERER)
        val ver = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_VERSION)
        android.opengl.EGL14.eglMakeCurrent(dpy, android.opengl.EGL14.EGL_NO_SURFACE, android.opengl.EGL14.EGL_NO_SURFACE, android.opengl.EGL14.EGL_NO_CONTEXT)
        android.opengl.EGL14.eglDestroySurface(dpy, surf); android.opengl.EGL14.eglDestroyContext(dpy, ctx); android.opengl.EGL14.eglTerminate(dpy)
        listOfNotNull(r, ver?.substringBefore(" build")).joinToString(" · ").ifBlank { null }
    } catch (e: Exception) { null }

    /** name, "model · manufacturer", badges for the top card */
    fun head(ctx: Context): Triple<String, String, List<String>> {
        val name = try { android.provider.Settings.Global.getString(ctx.contentResolver, "device_name") } catch (e: Exception) { null }
        val badges = ArrayList<String>()
        badges += "Android " + Build.VERSION.RELEASE  // i18n-ignore
        try {
            val d = (ctx.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager).getDisplay(android.view.Display.DEFAULT_DISPLAY)
            val h = d.mode.physicalHeight
            val hdr = Build.VERSION.SDK_INT >= 24 && (d.hdrCapabilities?.supportedHdrTypes?.isNotEmpty() == true)
            badges += (if (h >= 2160) "4K" else if (h >= 1080) "Full HD" else "HD") + if (hdr) " HDR" else ""  // i18n-ignore
        } catch (_: Exception) {}
        badges += if (Perf.lite) "Lite" else "Full"  // i18n-ignore
        return Triple(name ?: Build.MODEL, Build.MANUFACTURER.replaceFirstChar { it.uppercase() } + " " + Build.MODEL + " · " + Build.DEVICE, badges)
    }

    fun read(ctx: Context): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        val name = try { android.provider.Settings.Global.getString(ctx.contentResolver, "device_name") } catch (e: Exception) { null }
        out += tr("Naziv uređaja") to (name ?: Build.MODEL)
        out += tr("Model") to "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL} (${Build.DEVICE})"
        out += tr("Operativni sistem") to (if (ctx.packageManager.hasSystemFeature("com.google.android.feature.GOOGLE_EXPERIENCE") || ctx.packageManager.hasSystemFeature("com.google.android.tv.installed")) "Google TV" else "Android TV") +
            " · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        if (Build.VERSION.SDK_INT >= 23) out += tr("Sigurnosna zakrpa") to Build.VERSION.SECURITY_PATCH
        val soc = if (Build.VERSION.SDK_INT >= 31) listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL).filter { it.isNotBlank() && it != Build.UNKNOWN }.joinToString(" ") else ""
        out += tr("Procesor") to listOf(soc.ifBlank { Build.HARDWARE }, "${Runtime.getRuntime().availableProcessors()} " + tr("jezgre"), maxGhz()).filterNotNull().filter { it.isNotBlank() }.joinToString(" · ")
        out += tr("Arhitektura") to Build.SUPPORTED_ABIS.joinToString(", ")
        gpu()?.let { out += tr("Grafički procesor") to it }
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        out += tr("RAM") to gb(mi.totalMem) + " · " + trf("slobodno %s", gb(mi.availMem))
        try { val st = StatFs(Environment.getDataDirectory().path); out += tr("Prostor") to gb(st.totalBytes) + " · " + trf("slobodno %s", gb(st.availableBytes)) } catch (_: Exception) {}
        try {
            val d = (ctx.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager).getDisplay(android.view.Display.DEFAULT_DISPLAY)
            val mode = if (Build.VERSION.SDK_INT >= 23) d.mode else null
            val hdr = if (Build.VERSION.SDK_INT >= 24) d.hdrCapabilities?.supportedHdrTypes?.map { t -> when (t) { 1 -> "Dolby Vision"; 2 -> "HDR10"; 3 -> "HLG"; 4 -> "HDR10+"; else -> "HDR" } }?.distinct()?.joinToString(", ") else null  // i18n-ignore
            out += tr("Ekran") to listOfNotNull(mode?.let { "${it.physicalWidth}×${it.physicalHeight}" }, mode?.let { "${it.refreshRate.toInt()} Hz" }, hdr?.ifBlank { null }).joinToString(" · ")
        } catch (_: Exception) {}
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val caps = if (Build.VERSION.SDK_INT >= 23) cm.getNetworkCapabilities(cm.activeNetwork) else null
            val kind = when {
                caps == null -> tr("nije povezano")
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"  // i18n-ignore
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"  // i18n-ignore
                else -> tr("drugo")
            }
            out += tr("Mreža") to kind
        } catch (_: Exception) {}
        out += tr("Način rada aplikacije") to (if (Perf.lite) "Lite" else "Full") + " · PRETORIAN ${BuildInfo.VERSION}"  // i18n-ignore
        return out
    }
}
