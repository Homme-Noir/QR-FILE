package com.qrfile.network

import com.qrfile.handshake.HandshakePayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

// iOS implementation uses Apple's WiFiAware framework (iOS 19+).
// Requires entitlement: com.apple.developer.wifi-aware
// Info.plist key: WiFiAwareServices (array of service name strings)
//
// Sender role: NetworkListener implementing ListenerProvider — publishes the service.
// Receiver role: NetworkBrowser using WASubscriberBrowser — discovers the service.
//
// Phase 2 target: blocked on iOS 19 public release (DMA compliance window).
actual class P2PTransport actual constructor() {

    actual fun startAdvertising(payload: HandshakePayload): Flow<TransportEvent> = flow {
        // TODO: create NetworkListener with WAPublisherParameters containing serviceId.
        // On new connection, emit TransportEvent.Connected.
        throw NotImplementedError("Phase 2 — awaiting iOS 19 Wi-Fi Aware availability")
    }

    actual fun startDiscovery(payload: HandshakePayload): Flow<TransportEvent> = flow {
        // TODO: create NetworkBrowser with WASubscriberBrowser targeting serviceId.
        // On endpoint resolution, initiate NetworkConnection and emit TransportEvent.Connected.
        throw NotImplementedError("Phase 2 — awaiting iOS 19 Wi-Fi Aware availability")
    }

    actual fun sendFiles(filePaths: List<String>, encryptionKey: ByteArray): Flow<TransferProgress> = flow {
        // TODO: stream encrypted file bytes over the established NetworkConnection.
        throw NotImplementedError("Phase 2 — awaiting iOS 19 Wi-Fi Aware availability")
    }

    actual fun stopAll() {
        // TODO: cancel NetworkListener and NetworkBrowser, close all NetworkConnections.
    }
}
