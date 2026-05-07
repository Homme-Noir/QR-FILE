package com.qrfile.network

import com.qrfile.handshake.HandshakePayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

// Android implementation uses Google Nearby Connections API.
// Strategy: POINT_TO_POINT for maximum throughput on 1-to-1 transfers.
// Permissions required in AndroidManifest: NEARBY_WIFI_DEVICES (SDK 32+),
// BLUETOOTH_SCAN, BLUETOOTH_CONNECT, ACCESS_WIFI_STATE, CHANGE_WIFI_STATE.
actual class P2PTransport actual constructor() {

    actual fun startAdvertising(payload: HandshakePayload): Flow<TransportEvent> = flow {
        // TODO: initialize Nearby Connections client and call startAdvertising()
        // with a ConnectionLifecycleCallback that emits TransportEvent.Connected
        // when the remote device accepts the connection.
        throw NotImplementedError("Phase 1 implementation")
    }

    actual fun startDiscovery(payload: HandshakePayload): Flow<TransportEvent> = flow {
        // TODO: call Nearby Connections startDiscovery() and emit
        // TransportEvent.Connected on successful initiation + acceptance.
        throw NotImplementedError("Phase 1 implementation")
    }

    actual fun sendFiles(filePaths: List<String>, encryptionKey: ByteArray): Flow<TransferProgress> = flow {
        // TODO: encrypt each file with CryptoEngine, send via Payload.fromFile(),
        // and emit TransferProgress on each OnPayloadTransferUpdate callback.
        throw NotImplementedError("Phase 1 implementation")
    }

    actual fun stopAll() {
        // TODO: call Nearby Connections stopAdvertising(), stopDiscovery(), disconnectFromEndpoint()
    }
}
