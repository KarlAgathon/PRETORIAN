package com.pretorian.tv

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Playlist secrets (usernames, passwords, playlist links) are kept encrypted on the box with a key that lives in the
 * Android Keystore and never leaves it. Format: "k1:" + base64(iv + AES-GCM ciphertext).
 * If the Keystore is not available (some old boxes, unit tests), values stay as they were: playback always wins.
 */
object Secret {
    private const val ALIAS = "mc_secret_v1"
    private const val PREFIX = "k1:"

    private fun key(): SecretKey? = try {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    } catch (e: Throwable) { null }

    fun isSealed(v: String) = v.startsWith(PREFIX)

    /** encrypted text, or the plain value when encryption is not possible */
    fun seal(plain: String): String {
        if (plain.isEmpty() || isSealed(plain)) return plain
        val k = key() ?: return plain
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, k)
            PREFIX + Base64.encodeToString(c.iv + c.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        } catch (e: Throwable) { plain }
    }

    /** the plain value; an unreadable value (key lost after a restore to another box) becomes "" */
    fun open(v: String): String {
        if (!isSealed(v)) return v
        val k = key() ?: return ""
        return try {
            val raw = Base64.decode(v.removePrefix(PREFIX), Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, raw.copyOfRange(0, 12)))
            String(c.doFinal(raw.copyOfRange(12, raw.size)), Charsets.UTF_8)
        } catch (e: Throwable) { "" }
    }
}

/** Removes passwords, tokens and usernames from text that may leave the box (crash files, logs, stream log). */
object Redact {
    private val QUERY = Regex("(?i)((?:password|pass|pwd|username|user|token|auth|key|mac)=)[^&\\s\"']+")
    // Xtream paths: /live/<user>/<pass>/123.ts, /movie/<user>/<pass>/..., /series/<user>/<pass>/..., /timeshift/<user>/<pass>/...
    private val XTREAM = Regex("(?i)(/(?:live|movie|series|timeshift)/)[^/\\s]+/[^/\\s]+/")
    private val BASIC = Regex("(?i)(https?://)[^/\\s:@]+:[^/\\s@]+@")
    fun text(s: String): String = s.replace(QUERY, "$1***").replace(XTREAM, "$1***/***/").replace(BASIC, "$1***:***@")
}
