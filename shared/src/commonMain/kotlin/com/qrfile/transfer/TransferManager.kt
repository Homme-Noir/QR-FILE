package com.qrfile.transfer

import com.qrfile.crypto.CryptoEngine
import com.qrfile.handshake.HandshakePayload
import com.qrfile.network.P2PTransport
import com.qrfile.network.TransportEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Coordinates the full transfer lifecycle:
 *   1. Generate session credentials (HandshakePayload)
 *   2. Encrypt file chunks using the session key
 *   3. Delegate transport to P2PTransport (platform-specific)
 *   4. Report progress and completion back to the UI
 */
class TransferManager(
    private val crypto: CryptoEngine = CryptoEngine(),
    private val transport: P2PTransport = P2PTransport(),
) {
    fun prepareSend(filePaths: List<String>, deviceName: String): HandshakePayload {
        val sessionPassword = crypto.generateSessionPassword()
        return HandshakePayload(
            serviceId = "qrfile-${System.currentTimeMillis()}",
            sessionPassword = sessionPassword,
            deviceName = deviceName,
            fileCount = filePaths.size,
            totalBytes = 0L, // populated after file stat
        )
    }

    fun send(filePaths: List<String>, payload: HandshakePayload): Flow<TransportEvent> {
        val key = crypto.deriveKey(payload.sessionPassword)
        return transport.startAdvertising(payload)
    }

    fun receive(payload: HandshakePayload): Flow<TransportEvent> {
        return transport.startDiscovery(payload)
    }

    fun stop() = transport.stopAll()
}
