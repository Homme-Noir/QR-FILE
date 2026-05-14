package com.qrfile.transfer

import com.qrfile.crypto.CryptoEngine
import com.qrfile.handshake.HandshakePayload
import com.qrfile.handshake.TcpDirect
import com.qrfile.network.P2PTransport
import com.qrfile.network.TransportEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withTimeoutOrNull

private const val TRANSFER_TIMEOUT_MS = 600_000L

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
    fun prepareSend(
        filePaths: List<String>,
        deviceName: String,
        tcpDirect: TcpDirect? = null,
    ): HandshakePayload {
        val sessionPassword = crypto.generateSessionPassword()
        return HandshakePayload(
            serviceId = "qrfile-${System.currentTimeMillis()}",
            sessionPassword = sessionPassword,
            deviceName = deviceName,
            fileCount = filePaths.size,
            totalBytes = 0L, // populated after file stat
            tcpDirect = tcpDirect,
        )
    }

    /**
     * Sender: advertise until a peer connects, stream encrypted files, then tear down transport.
     * Emits [TransportEvent.Connected], [TransportEvent.Progress] while sending, then
     * [TransportEvent.Completed] with an empty path (sender has no saved file path).
     */
    fun send(filePaths: List<String>, payload: HandshakePayload): Flow<TransportEvent> = channelFlow {
        val key = crypto.deriveKey(payload.sessionPassword)
        try {
            val finished = withTimeoutOrNull(TRANSFER_TIMEOUT_MS) {
                transport.startAdvertising(payload).collect { event ->
                    when (event) {
                        is TransportEvent.Connected -> {
                            send(event)
                            runCatching {
                                transport.sendFiles(filePaths, key).collect { progress ->
                                    send(TransportEvent.Progress(progress))
                                }
                                send(TransportEvent.Completed(""))
                            }.onFailure { e ->
                                send(TransportEvent.Failed(e.message ?: "Send failed"))
                            }
                            return@collect
                        }
                        is TransportEvent.Failed -> {
                            send(event)
                            close()
                        }
                        else -> Unit
                    }
                }
            }
            if (finished == null) {
                send(TransportEvent.Failed("Timed out waiting for a connection"))
            }
        } finally {
            transport.stopAll()
        }
    }

    fun receive(payload: HandshakePayload): Flow<TransportEvent> = channelFlow {
        try {
            val finished = withTimeoutOrNull(TRANSFER_TIMEOUT_MS) {
                transport.startDiscovery(payload).collect { send(it) }
            }
            if (finished == null) {
                send(TransportEvent.Failed("Receive timed out"))
            }
        } finally {
            transport.stopAll()
        }
    }

    fun stop() = transport.stopAll()
}
