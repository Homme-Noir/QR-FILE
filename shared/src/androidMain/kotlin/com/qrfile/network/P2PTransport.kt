package com.qrfile.network

import android.content.Context
import android.os.Build
import android.os.Environment
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.qrfile.crypto.CryptoEngine
import com.qrfile.handshake.HandshakePayload
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File

actual class P2PTransport actual constructor() {

    companion object {
        private lateinit var appContext: Context
        fun init(context: Context) {
            appContext = context.applicationContext
        }
    }

    private val client: ConnectionsClient by lazy { Nearby.getConnectionsClient(appContext) }
    private val crypto = CryptoEngine()

    private var connectedEndpointId: String? = null
    private var receiveEncryptionKey: ByteArray? = null

    // Sender-side callback: set by sendFiles(), invoked by the advertiser's PayloadCallback
    private var onPayloadUpdate: ((PayloadTransferUpdate) -> Unit)? = null

    // Receiver-side callback: invoked when a file is fully received and decrypted
    private var onFileReceived: ((String) -> Unit)? = null

    actual fun startAdvertising(payload: HandshakePayload): Flow<TransportEvent> = callbackFlow {
        val senderPayloadCallback = object : PayloadCallback() {
            override fun onPayloadReceived(endpointId: String, p: Payload) {}
            override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
                onPayloadUpdate?.invoke(update)
            }
        }

        val lifecycleCallback = object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
                client.acceptConnection(endpointId, senderPayloadCallback)
            }

            override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
                if (result.status.isSuccess) {
                    connectedEndpointId = endpointId
                    trySend(TransportEvent.Connected(endpointId))
                } else {
                    trySend(TransportEvent.Failed(result.status.statusMessage ?: "Connection failed"))
                }
            }

            override fun onDisconnected(endpointId: String) {
                connectedEndpointId = null
            }
        }

        val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()
        client.startAdvertising(payload.deviceName, payload.serviceId, lifecycleCallback, options)
            .addOnFailureListener { e ->
                trySend(TransportEvent.Failed(e.message ?: "Advertising failed"))
                close(e)
            }

        awaitClose { client.stopAdvertising() }
    }

    actual fun startDiscovery(payload: HandshakePayload): Flow<TransportEvent> = callbackFlow {
        val key = crypto.deriveKey(payload.sessionPassword)
        receiveEncryptionKey = key

        val receiverPayloadCallback = object : PayloadCallback() {
            private val pendingFiles = mutableMapOf<Long, Payload>()

            override fun onPayloadReceived(endpointId: String, p: Payload) {
                if (p.type == Payload.Type.FILE) pendingFiles[p.id] = p
            }

            override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
                if (update.status != PayloadTransferUpdate.Status.SUCCESS) return
                val p = pendingFiles.remove(update.payloadId) ?: return
                val ciphertextFile = p.asFile()?.asJavaFile() ?: return
                val encKey = receiveEncryptionKey ?: return

                try {
                    val plaintext = crypto.decrypt(ciphertextFile.readBytes(), encKey)
                    val downloads = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS
                    )
                    val dest = File(downloads, ciphertextFile.name.removePrefix("enc_"))
                    dest.writeBytes(plaintext)
                    ciphertextFile.delete()
                    onFileReceived?.invoke(dest.absolutePath)
                    trySend(TransportEvent.Completed(dest.absolutePath))
                } catch (_: Exception) {
                    ciphertextFile.delete()
                }
            }
        }

        val lifecycleCallback = object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
                client.acceptConnection(endpointId, receiverPayloadCallback)
            }

            override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
                if (result.status.isSuccess) {
                    connectedEndpointId = endpointId
                    trySend(TransportEvent.Connected(endpointId))
                } else {
                    trySend(TransportEvent.Failed(result.status.statusMessage ?: "Connection failed"))
                }
            }

            override fun onDisconnected(endpointId: String) {
                connectedEndpointId = null
            }
        }

        val discoveryCallback = object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                client.requestConnection(Build.MODEL, endpointId, lifecycleCallback)
            }

            override fun onEndpointLost(endpointId: String) {}
        }

        val options = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()
        client.startDiscovery(payload.serviceId, discoveryCallback, options)
            .addOnFailureListener { e ->
                trySend(TransportEvent.Failed(e.message ?: "Discovery failed"))
                close(e)
            }

        awaitClose { client.stopDiscovery() }
    }

    actual fun sendFiles(filePaths: List<String>, encryptionKey: ByteArray): Flow<TransferProgress> = callbackFlow {
        val endpointId = connectedEndpointId
        if (endpointId == null) {
            close(Exception("No connected endpoint"))
            return@callbackFlow
        }

        val totalBytes = filePaths.sumOf { File(it).length() }
        var totalSent = 0L
        val pendingPayloads = mutableMapOf<Long, File>()

        onPayloadUpdate = { update ->
            when (update.status) {
                PayloadTransferUpdate.Status.IN_PROGRESS -> {
                    totalSent += update.bytesTransferred
                    trySend(TransferProgress(totalSent.coerceAtMost(totalBytes), totalBytes))
                }
                PayloadTransferUpdate.Status.SUCCESS -> {
                    pendingPayloads.remove(update.payloadId)?.delete()
                    if (pendingPayloads.isEmpty()) close()
                }
                PayloadTransferUpdate.Status.FAILURE -> {
                    close(Exception("Transfer failed for payload ${update.payloadId}"))
                }
                else -> {}
            }
        }

        filePaths.forEach { path ->
            val original = File(path)
            val ciphertext = crypto.encrypt(original.readBytes(), encryptionKey)
            val tempFile = File(appContext.cacheDir, "enc_${original.name}")
            tempFile.writeBytes(ciphertext)
            val payload = Payload.fromFile(tempFile)
            pendingPayloads[payload.id] = tempFile
            client.sendPayload(endpointId, payload)
        }

        awaitClose { onPayloadUpdate = null }
    }

    actual fun stopAll() {
        client.stopAdvertising()
        client.stopDiscovery()
        connectedEndpointId?.let { client.disconnectFromEndpoint(it) }
        connectedEndpointId = null
        onPayloadUpdate = null
        onFileReceived = null
    }
}
