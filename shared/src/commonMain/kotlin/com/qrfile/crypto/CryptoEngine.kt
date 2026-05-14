package com.qrfile.crypto

import korlibs.crypto.AES
import korlibs.crypto.CipherMode
import korlibs.crypto.CipherPadding
import korlibs.crypto.SecureRandom
import korlibs.crypto.SHA256
import korlibs.crypto.with

/**
 * AES-256-CBC with PKCS7 padding and random 16-byte IV prepended to ciphertext.
 * SHA-256 for session key derivation from the out-of-band password.
 *
 * Implemented with Korlibs Krypto on all Kotlin targets (Android, iOS, JVM).
 */
class CryptoEngine {

    fun generateSessionPassword(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..20).map { chars[SecureRandom.nextInt(chars.length)] }.joinToString("")
    }

    fun deriveKey(sessionPassword: String): ByteArray =
        SHA256.digest(sessionPassword.encodeToByteArray()).bytes

    fun encrypt(data: ByteArray, key: ByteArray): ByteArray {
        val iv = ByteArray(16)
        SecureRandom.nextBytes(iv)
        val cipher = AES(key).with(CipherMode.CBC, CipherPadding.PKCS7Padding, iv)
        return iv + cipher.encrypt(data)
    }

    fun decrypt(ciphertext: ByteArray, key: ByteArray): ByteArray {
        require(ciphertext.size >= 16) { "ciphertext too short" }
        val iv = ciphertext.copyOfRange(0, 16)
        val body = ciphertext.copyOfRange(16, ciphertext.size)
        val cipher = AES(key).with(CipherMode.CBC, CipherPadding.PKCS7Padding, iv)
        return cipher.decrypt(body)
    }
}
