package com.qrfile.handshake

import kotlinx.serialization.Serializable

/**
 * Payload embedded in the NFC tag or QR code.
 * Contains everything the receiver needs to join the Wi-Fi Aware service
 * and derive the session encryption key — nothing more.
 *
 * Serialized as JSON before encoding into QR / written to NFC NDEF record.
 */
@Serializable
data class HandshakePayload(
    val serviceId: String,       // Wi-Fi Aware service name, unique per transfer
    val sessionPassword: String, // single-use password for AES-GCM key derivation
    val deviceName: String,      // human-readable sender name (public, not secret)
    val fileCount: Int,          // how many files are queued for transfer
    val totalBytes: Long,        // total transfer size in bytes
)

enum class HandshakeMethod { NFC, QR_CODE }
