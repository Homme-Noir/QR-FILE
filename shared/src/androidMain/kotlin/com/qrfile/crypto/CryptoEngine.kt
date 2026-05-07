package com.qrfile.crypto

import com.google.crypto.tink.subtle.AesGcmJce
import java.security.MessageDigest
import java.security.SecureRandom

actual class CryptoEngine actual constructor() {

    actual fun generateSessionPassword(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val rng = SecureRandom()
        return (1..20).map { chars[rng.nextInt(chars.length)] }.joinToString("")
    }

    actual fun deriveKey(sessionPassword: String): ByteArray =
        MessageDigest.getInstance("SHA-256")
            .digest(sessionPassword.toByteArray(Charsets.UTF_8))

    actual fun encrypt(data: ByteArray, key: ByteArray): ByteArray =
        AesGcmJce(key).encrypt(data, byteArrayOf())

    actual fun decrypt(ciphertext: ByteArray, key: ByteArray): ByteArray =
        AesGcmJce(key).decrypt(ciphertext, byteArrayOf())
}
