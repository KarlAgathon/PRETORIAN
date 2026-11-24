package com.pretorian.tv

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom

/**
 * Type on the phone instead of the remote: while "Add playlist" is open, the box serves a small form on the home
 * network. The QR code holds its address and a one-time key; whatever is sent fills the fields on the TV.
 * Nothing leaves the home network, the form exists only while that screen is open, and without the key nothing is accepted.
 */
object PhoneLink {
    class Data(val type: Int, val name: String, val server: String, val user: String, val pass: String, val url: String, val epg: String)

    /**
     * 2.8.0: every QR code is its own session — its own key, socket, expiry and mode (playlist form or one file).
     * A callback of an older session can never close a newer one; the key is checked and used up in one atomic step.
     * Expiry uses the monotonic clock (changing the box's time does not extend or shorten it).
     */
    private class Session(val id: Long, val token: String, val socket: ServerSocket, val lifetimeMs: Long,
                          val onData: ((Data) -> Unit)?, val file: java.io.File?, val fileName: String, val mime: String, val served: (() -> Unit)?) {
        val started = android.os.SystemClock.elapsedRealtime()
        val used = java.util.concurrent.atomic.AtomicBoolean(false)
        val misses = java.util.concurrent.atomic.AtomicInteger(0)
        val busy = java.util.concurrent.atomic.AtomicInteger(0)
        fun expired() = android.os.SystemClock.elapsedRealtime() - started > lifetimeMs
        /** constant-time comparison of the key */
        fun keyOk(k: String?): Boolean = k != null && token.isNotEmpty() && java.security.MessageDigest.isEqual(k.toByteArray(), token.toByteArray())
    }
    @Volatile private var cur: Session? = null
    private val ids = java.util.concurrent.atomic.AtomicLong(0)
    private val main = Handler(Looper.getMainLooper())
    private const val DOWNLOAD_MS = 5 * 60_000L
    /** a QR code for the playlist form works for 10 minutes and for one sent form */
    private const val LIFETIME_MS = 10 * 60_000L

    /** address for the QR code, or null when the box has no home-network address */
    fun start(onReceive: (Data) -> Unit): String? = open(LIFETIME_MS, onReceive, null, "", "", null)?.first

    /**
     * Diagnostics: serves one file for one finished download. The address shows a small page with a download button
     * (link previews do not use up the key). An interrupted transfer can be tried again (at most 3 times) until it expires;
     * five wrong requests, Back on the TV or 5 minutes stop it. Returns (address, session id).
     */
    fun startDownload(name: String, file: java.io.File, mime: String, onServed: () -> Unit): Pair<String, Long>? =
        open(DOWNLOAD_MS, null, file, name.replace(Regex("[^A-Za-z0-9._-]"), "_"), mime, onServed)  // i18n-ignore

    private fun open(life: Long, onData: ((Data) -> Unit)?, file: java.io.File?, name: String, mime: String, served: (() -> Unit)?): Pair<String, Long>? {
        stop()
        val ip = lanIp() ?: return null
        val token = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        // only on the home network address shown in the QR code (not on VPN or other interfaces)
        val sock = try { ServerSocket(0, 4, java.net.InetAddress.getByName(ip)) } catch (e: Exception) { return null }
        val ses = Session(ids.incrementAndGet(), token, sock, life, onData, file, name, mime, served)
        cur = ses
        main.postDelayed({ stop(ses.id) }, life)
        Thread {
            while (!sock.isClosed) {
                val c = try { sock.accept() } catch (e: Exception) { break }
                // at most 4 phones talking at once; more are turned away at the door
                if (ses.busy.incrementAndGet() > 4) { ses.busy.decrementAndGet(); try { c.close() } catch (_: Exception) {}; continue }
                Thread { try { handle(ses, c) } catch (_: Exception) {} finally { ses.busy.decrementAndGet(); try { c.close() } catch (_: Exception) {} } }.apply { isDaemon = true }.start()
            }
        }.apply { isDaemon = true }.start()
        return "http://$ip:${sock.localPort}/?k=$token" to ses.id // i18n-ignore
    }

    /** stops the current session */
    fun stop() { cur?.let { stop(it.id) } }
    /** stops session [id] only (an older window closing never stops a newer QR) */
    fun stop(id: Long) {
        val s = cur ?: return
        if (s.id != id) return
        cur = null
        try { s.socket.close() } catch (_: Exception) {}
    }

    /**
     * The home-network address: Ethernet first, then Wi-Fi; only private (RFC 1918) IPv4 addresses, never a VPN tunnel
     * (tun, ppp, wg, ipsec, tap…) and never the carrier-grade range 100.64/10 used by some VPNs. When a VPN blocks the
     * home network, the phone cannot reach the box: that is the VPN's own setting and is respected.
     */
    internal fun lanIp(): String? = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual && !Regex("^(tun|ppp|wg|ipsec|tap|utun|nordlynx|vpn)").containsMatchIn(it.name) }  // i18n-ignore
            .sortedBy { if (it.name.startsWith("eth")) 0 else if (it.name.startsWith("wlan")) 1 else 2 } // i18n-ignore
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress && it.isSiteLocalAddress }?.hostAddress
    } catch (e: Exception) { null }

    private fun handle(ses: Session, c: Socket) {
        c.soTimeout = 10_000
        val inp = c.getInputStream().buffered()
        // request line and headers, bounded: at most 4 KB per line and 8 KB of headers in total
        var used = 0
        fun line(): String {
            val b = StringBuilder()
            while (true) {
                val ch = inp.read(); if (ch < 0 || ch == '\n'.code) break
                if (++used > 8192 || b.length > 4096) throw java.io.IOException("too long")  // i18n-ignore
                if (ch != '\r'.code) b.append(ch.toChar())
            }
            return b.toString()
        }
        val req = line(); var len = 0
        var lines = 0
        while (true) { val h = line(); if (h.isEmpty() || ++lines > 40) break; if (h.lowercase().startsWith("content-length:")) len = h.substringAfter(':').trim().toIntOrNull() ?: 0 } // i18n-ignore
        val path = req.split(' ').getOrNull(1) ?: "/"
        val q = parse(path.substringAfter('?', ""))
        val out = c.getOutputStream()
        fun send(code: String, body: String) {
            val b = body.toByteArray(Charsets.UTF_8)
            out.write(("HTTP/1.1 $code\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${b.size}\r\nConnection: close\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nX-Content-Type-Options: nosniff\r\nContent-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; form-action 'self'\r\n\r\n").toByteArray()) // i18n-ignore
            out.write(b); out.flush()
        }
        if (cur !== ses) { send("403 Forbidden", page(tr("Ovaj QR kod više ne važi."), null)); return }
        if (ses.file != null) { serveFile(ses, req, path, q, ::send, out); return }
        if (ses.expired() || ses.used.get() || !ses.keyOk(q["k"])) { send("403 Forbidden", page(tr("Ovaj QR kod više ne važi. Otvori ponovo „Dodaj playlistu“ na TV-u."), null)); return } // i18n-ignore
        if (req.startsWith("POST")) {
            val body = ByteArray(len.coerceIn(0, 16_000)).also { var r = 0; while (r < it.size) { val n = inp.read(it, r, it.size - r); if (n < 0) break; r += n } }
            // one form per QR code: the key is used up atomically the moment the data arrives (no replay, no double send)
            if (!ses.used.compareAndSet(false, true)) { send("403 Forbidden", page(tr("Ovaj QR kod više ne važi."), null)); return }
            val f = parse(String(body, Charsets.UTF_8))
            val d = Data((f["type"] ?: "0").toIntOrNull()?.coerceIn(0, 2) ?: 0, f["name"].orEmpty().trim(), f["server"].orEmpty().trim(),
                f["user"].orEmpty().trim(), f["pass"].orEmpty(), f["url"].orEmpty().trim(), f["epg"].orEmpty().trim())
            ses.onData?.let { cb -> main.post { if (cur === ses) cb(d) } }
            send("200 OK", page(tr("✓ Poslano na TV. Provjeri podatke na TV-u i pritisni „Sačuvaj“."), null)) // i18n-ignore
        } else send("200 OK", page(null, ses.token))
    }

    private val tries = java.util.concurrent.ConcurrentHashMap<Long, Int>()
    private fun serveFile(ses: Session, req: String, path: String, q: Map<String, String>, send: (String, String) -> Unit, out: java.io.OutputStream) {
        val f = ses.file
        if (f == null || !f.exists() || ses.expired() || !ses.keyOk(q["k"]) || !req.startsWith("GET")) {
            if (ses.misses.incrementAndGet() >= 5) main.post { stop(ses.id) }
            send("403 Forbidden", page(tr("Ovaj link više ne važi. Otvori ponovo „Izvezi izvještaj“ na TV-u."), null)) // i18n-ignore
            return
        }
        if (!path.startsWith("/r?")) {
            // landing page: a button for the one download (a link preview never uses up the key)
            val body = """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Media Center</title>
<style>body{font-family:system-ui,sans-serif;background:#101B2B;color:#f2f3f5;margin:0;padding:24px}a{display:block;margin-top:24px;padding:16px;border-radius:14px;background:#dadade;color:#111113;text-align:center;font-size:18px;font-weight:600;text-decoration:none}p{color:#9aa1ad}</style></head>
<body><h2>Media Center</h2><p>${esc(tr("Dijagnostički izvještaj, bez lozinki i ključeva. Link radi jednom. Prenos u kućnoj mreži nije šifrovan."))}</p><a href="/r?k=${ses.token}">${esc(ses.fileName)}</a></body></html>""" // i18n-ignore
            send("200 OK", body); return
        }
        // the download: only one at a time (atomic); a broken transfer frees the key again for a new try (max 3)
        if (!ses.used.compareAndSet(false, true)) { send("409 Conflict", page(tr("Preuzimanje je već u toku ili je završeno."), null)); return } // i18n-ignore
        try {
            out.write(("HTTP/1.1 200 OK\r\nContent-Type: ${ses.mime}\r\nContent-Length: ${f.length()}\r\n" +
                "Content-Disposition: attachment; filename=\"${ses.fileName}\"\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\n\r\n").toByteArray()) // i18n-ignore
            f.inputStream().use { it.copyTo(out, 64 * 1024) }
            out.flush()
        } catch (e: Exception) {
            val n = (tries[ses.id] ?: 0) + 1; tries[ses.id] = n
            DiagEvents.add("diag", "export download interrupted ($n)")  // i18n-ignore
            if (n < 3) ses.used.set(false) else main.post { stop(ses.id) }
            return
        }
        val cb = ses.served
        main.post { if (cur === ses) { cb?.invoke(); stop(ses.id) } }
    }

    private fun parse(s: String): Map<String, String> = s.split('&').mapNotNull { kv ->
        val i = kv.indexOf('='); if (i <= 0) null else try { URLDecoder.decode(kv.substring(0, i), "UTF-8") to URLDecoder.decode(kv.substring(i + 1), "UTF-8") } catch (e: Exception) { null } // i18n-ignore
    }.toMap()

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;") // i18n-ignore

    /** the phone page: plain, large fields, works in any phone browser */
    private fun page(msg: String?, key: String?): String {
        val form = if (key == null) "" else """
<form method="post" action="/?k=$key">
<div class="seg"><label><input type="radio" name="type" value="0" checked onchange="sw()"> Xtream</label><label><input type="radio" name="type" value="1" onchange="sw()"> M3U</label><label><input type="radio" name="type" value="2" onchange="sw()"> Stalker</label></div>
<label>${esc(tr("Naziv playliste"))}<input name="name" autocomplete="off"></label>
<div id="x"><label>${esc(tr("Server adresa (http://server:port)"))}<input name="server" inputmode="url" autocapitalize="off" autocomplete="off"></label>
<label id="u">${esc(tr("Korisničko ime"))}<input name="user" autocapitalize="off" autocomplete="off"></label>
<label id="p">${esc(tr("Lozinka"))}<input name="pass" autocapitalize="off" autocomplete="off"></label></div>
<div id="m" style="display:none"><label>${esc(tr("M3U link"))}<input name="url" inputmode="url" autocapitalize="off" autocomplete="off"></label></div>
<label>${esc(tr("EPG link (nije obavezno)"))}<input name="epg" inputmode="url" autocapitalize="off" autocomplete="off"></label>
<button>${esc(tr("Pošalji na TV"))}</button></form>
<script>function sw(){var t=document.querySelector('input[name=type]:checked').value;document.getElementById('x').style.display=t=='1'?'none':'';document.getElementById('m').style.display=t=='1'?'':'none';document.getElementById('u').style.display=t=='2'?'none':'';document.getElementById('p').style.display=t=='2'?'none':''}</script>"""
        return """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Media Center</title>
<style>body{font-family:system-ui,sans-serif;background:#0a0a0b;color:#f2f3f5;margin:0;padding:20px}h1{font-size:20px;letter-spacing:3px}
label{display:block;margin:14px 0 4px;color:#9aa1ad;font-size:14px}input{width:100%;box-sizing:border-box;padding:14px;margin-top:6px;border-radius:12px;border:1px solid #3a3a3e;background:#161618;color:#fff;font-size:17px}
.seg{display:flex;gap:8px}.seg label{flex:1;margin:0;background:#161618;border:1px solid #3a3a3e;border-radius:12px;padding:12px;text-align:center;color:#fff}.seg input{width:auto;margin:0 6px 0 0}
button{width:100%;margin-top:22px;padding:16px;border:0;border-radius:14px;background:#dadade;color:#111113;font-size:18px;font-weight:600}p{font-size:16px;line-height:1.5}</style></head>
<body><h1>Media Center</h1>${if (msg != null) "<p>${esc(msg)}</p>" else ""}$form</body></html>""" // i18n-ignore
    }

    /** black-on-white QR code of [text], [size] pixels */
    fun qr(text: String, size: Int = 360): Bitmap? = try {
        val m = com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, size, size,
            mapOf(com.google.zxing.EncodeHintType.MARGIN to 1))
        val px = IntArray(size * size) { i -> if (m.get(i % size, i / size)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
    } catch (e: Throwable) { null }
}
