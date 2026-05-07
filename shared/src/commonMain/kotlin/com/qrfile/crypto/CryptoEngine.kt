package com.qrfile.crypto

/**
 * Shared interface for AES-GCM encryption/decryption.
 * Implemented per-platform using Google Tink (Android) and CryptoKit (iOS).
 *
 * Keys are derived via SHA-256 from a single-use session password exchanged
 * out-of-band during the NFC/QR handshake — never transmitted over the Wi-Fi link.
 */
expect class CryptoEngine() {
    fun generateSessionPassword(): String
    fun deriveKey(sessionPassword: String): ByteArray
    fun encrypt(data: ByteArray, key: ByteArray): ByteArray
    fun decrypt(ciphertext: ByteArray, key: ByteArray): ByteArray
}
