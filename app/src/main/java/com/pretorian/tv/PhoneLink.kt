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

    private var socket: ServerSocket? = null
    private var token = ""
    @Volatile private var onData: ((Data) -> Unit)? = null

    /** address for the QR code, or null when the box has no network address */
    fun start(onReceive: (Data) -> Unit): String? {
        stop()
        val ip = lanIp() ?: return null
        token = ByteArray(9).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        onData = onReceive
        val s = try { ServerSocket(0, 8) } catch (e: Exception) { return null }
        socket = s
        Thread {
            while (!s.isClosed) {
                val c = try { s.accept() } catch (e: Exception) { break }
                Thread { try { handle(c) } catch (_: Exception) {} finally { try { c.close() } catch (_: Exception) {} } }.start()
            }
        }.apply { isDaemon = true }.start()
        return "http://$ip:${s.localPort}/?k=$token" // i18n-ignore
    }

    fun stop() { onData = null; try { socket?.close() } catch (_: Exception) {}; socket = null }

    private fun lanIp(): String? = try {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .sortedBy { if (it.name.startsWith("eth")) 0 else if (it.name.startsWith("wlan")) 1 else 2 } // i18n-ignore
            .flatMap { it.inetAddresses.toList() }.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
    } catch (e: Exception) { null }

    private fun handle(c: Socket) {
        c.soTimeout = 15_000
        val inp = c.getInputStream().buffered()
        // request line and headers
        fun line(): String { val b = StringBuilder(); while (true) { val ch = inp.read(); if (ch < 0 || ch == '\n'.code) break; if (ch != '\r'.code) b.append(ch.toChar()) }; return b.toString() }
        val req = line(); var len = 0
        while (true) { val h = line(); if (h.isEmpty()) break; if (h.lowercase().startsWith("content-length:")) len = h.substringAfter(':').trim().toIntOrNull() ?: 0 } // i18n-ignore
        val path = req.split(' ').getOrNull(1) ?: "/"
        val q = parse(path.substringAfter('?', ""))
        val out = c.getOutputStream()
        fun send(code: String, body: String) {
            val b = body.toByteArray(Charsets.UTF_8)
            out.write(("HTTP/1.1 $code\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${b.size}\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n").toByteArray()) // i18n-ignore
            out.write(b); out.flush()
        }
        if (q["k"] != token || token.isEmpty()) { send("403 Forbidden", page(tr("Ovaj QR kod više ne važi. Otvori ponovo „Dodaj playlistu“ na TV-u."), null)); return } // i18n-ignore
        if (req.startsWith("POST")) {
            val body = ByteArray(len.coerceIn(0, 64_000)).also { var r = 0; while (r < it.size) { val n = inp.read(it, r, it.size - r); if (n < 0) break; r += n } }
            val f = parse(String(body, Charsets.UTF_8))
            val d = Data((f["type"] ?: "0").toIntOrNull()?.coerceIn(0, 2) ?: 0, f["name"].orEmpty().trim(), f["server"].orEmpty().trim(),
                f["user"].orEmpty().trim(), f["pass"].orEmpty(), f["url"].orEmpty().trim(), f["epg"].orEmpty().trim())
            onData?.let { cb -> Handler(Looper.getMainLooper()).post { cb(d) } }
            send("200 OK", page(tr("✓ Poslano na TV. Provjeri podatke na TV-u i pritisni „Spremi i učitaj“."), null)) // i18n-ignore
        } else send("200 OK", page(null, token))
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
<style>body{font-family:system-ui,sans-serif;background:#0b1020;color:#f2f3f5;margin:0;padding:20px}h1{font-size:20px;letter-spacing:3px}
label{display:block;margin:14px 0 4px;color:#9aa1ad;font-size:14px}input{width:100%;box-sizing:border-box;padding:14px;margin-top:6px;border-radius:12px;border:1px solid #2a3350;background:#141b30;color:#fff;font-size:17px}
.seg{display:flex;gap:8px}.seg label{flex:1;margin:0;background:#141b30;border:1px solid #2a3350;border-radius:12px;padding:12px;text-align:center;color:#fff}.seg input{width:auto;margin:0 6px 0 0}
button{width:100%;margin-top:22px;padding:16px;border:0;border-radius:14px;background:#2563eb;color:#fff;font-size:18px;font-weight:600}p{font-size:16px;line-height:1.5}</style></head>
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
