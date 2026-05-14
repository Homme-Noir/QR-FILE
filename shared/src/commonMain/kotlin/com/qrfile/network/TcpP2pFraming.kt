package com.qrfile.network

/** Plain-TCP transfer framing shared by JVM, Android, and iOS. */
object TcpP2pFraming {
    const val MAX_CIPHERTEXT_BYTES: Long = 512L * 1024 * 1024

    fun requireValidCiphertextLength(len: Long) {
        require(len > 0 && len <= MAX_CIPHERTEXT_BYTES) { "Invalid frame length" }
    }
}
