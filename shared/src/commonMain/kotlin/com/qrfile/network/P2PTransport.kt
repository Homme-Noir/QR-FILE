package com.qrfile.network

import com.qrfile.handshake.HandshakePayload
import kotlinx.coroutines.flow.Flow

data class TransferProgress(val bytesTransferred: Long, val totalBytes: Long)

sealed class TransportEvent {
    data class Connected(val remoteDeviceName: String) : TransportEvent()
    data class Progress(val progress: TransferProgress) : TransportEvent()
    data class Completed(val savedPath: String) : TransportEvent()
    data class Failed(val reason: String) : TransportEvent()
}

/**
 * Platform-specific P2P transport layer.
 *
 * Android: implemented via Google Nearby Connections API (Wi-Fi Direct + BLE).
 * iOS: implemented via Apple WiFiAware framework (requires iOS 19+, com.apple.developer.wifi-aware entitlement).
 *
 * The shared module only depends on this interface — all networking code
 * lives in androidMain / iosMain to keep hardware access fully native.
 */
expect class P2PTransport() {
    fun startAdvertising(payload: HandshakePayload): Flow<TransportEvent>
    fun startDiscovery(payload: HandshakePayload): Flow<TransportEvent>
    fun sendFiles(filePaths: List<String>, encryptionKey: ByteArray): Flow<TransferProgress>
    fun stopAll()
}
