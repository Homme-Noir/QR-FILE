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
import com.qrfile.handshake.TcpDirect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

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

    private var tcpServerSocket: ServerSocket? = null
    private var tcpSocket: Socket? = null
    private var tcpInput: DataInputStream? = null
    private var tcpOutput: DataOutputStream? = null

    /** Nearby endpoint id → human-readable name from [ConnectionInfo] / [DiscoveredEndpointInfo]. */
    private val endpointDisplayNames = ConcurrentHashMap<String, String>()

    private var onPayloadUpdate: ((PayloadTransferUpdate) -> Unit)? = null

    actual fun startAdvertising(payload: HandshakePayload): Flow<TransportEvent> {
        val direct = payload.tcpDirect
        if (direct != null) {
            return tcpStartAdvertising(direct)
        }
        return nearbyStartAdvertising(payload)
    }

    private fun tcpStartAdvertising(direct: TcpDirect): Flow<TransportEvent> = callbackFlow {
        try {
            withContext(Dispatchers.IO) {
                val ss = AndroidP2pHooks.takeParked(direct.port) ?: run {
                    val s = ServerSocket()
                    s.reuseAddress = true
                    s.bind(InetSocketAddress("0.0.0.0", direct.port), 1)
                    s
                }
                tcpServerSocket = ss
                val s = ss.accept()
                tcpSocket = s
                tcpInput = DataInputStream(s.getInputStream())
                tcpOutput = DataOutputStream(s.getOutputStream())
                val peer = s.inetAddress?.hostAddress ?: direct.host
                trySend(TransportEvent.Connected(peer))
            }
        } catch (e: Exception) {
            trySend(TransportEvent.Failed(e.message ?: "TCP listen failed"))
            close(e)
        }
        awaitClose { closeTcp() }
    }

    private fun nearbyStartAdvertising(payload: HandshakePayload): Flow<TransportEvent> = callbackFlow {
        val senderPayloadCallback = object : PayloadCallback() {
            override fun onPayloadReceived(endpointId: String, p: Payload) {}
            override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
                onPayloadUpdate?.invoke(update)
            }
        }

        val lifecycleCallback = object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
                endpointDisplayNames[endpointId] = info.endpointName
                client.acceptConnection(endpointId, senderPayloadCallback)
            }

            override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
                if (result.status.isSuccess) {
                    connectedEndpointId = endpointId
                    client.stopAdvertising()
                    trySend(TransportEvent.Connected(endpointDisplayNames[endpointId] ?: endpointId))
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

    actual fun startDiscovery(payload: HandshakePayload): Flow<TransportEvent> {
        if (payload.tcpDirect != null) {
            return tcpStartDiscovery(payload)
        }
        return nearbyStartDiscovery(payload)
    }

    private fun tcpStartDiscovery(payload: HandshakePayload): Flow<TransportEvent> = callbackFlow {
        val direct = payload.tcpDirect!!
        try {
            withContext(Dispatchers.IO) {
                val s = Socket()
                s.connect(InetSocketAddress(direct.host, direct.port), 30_000)
                tcpSocket = s
                val inp = DataInputStream(s.getInputStream())
                tcpInput = inp
                tcpOutput = DataOutputStream(s.getOutputStream())
                trySend(TransportEvent.Connected(payload.deviceName))
                val key = crypto.deriveKey(payload.sessionPassword)
                val downloads = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: appContext.filesDir
                repeat(payload.fileCount.coerceAtLeast(1)) {
                    val len = inp.readLong()
                    TcpP2pFraming.requireValidCiphertextLength(len)
                    val enc = ByteArray(len.toInt())
                    inp.readFully(enc)
                    val plain = crypto.decrypt(enc, key)
                    val dest = File(downloads, "recv_${UUID.randomUUID()}")
                    dest.writeBytes(plain)
                    trySend(TransportEvent.Completed(dest.absolutePath))
                }
            }
        } catch (e: Exception) {
            trySend(TransportEvent.Failed(e.message ?: "TCP receive failed"))
            close(e)
        }
        awaitClose { closeTcp() }
    }

    private fun nearbyStartDiscovery(payload: HandshakePayload): Flow<TransportEvent> = callbackFlow {
        val key = crypto.deriveKey(payload.sessionPassword)
        receiveEncryptionKey = key
        var filesRemaining = payload.fileCount.coerceAtLeast(1)

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
                    val downloads = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        ?: appContext.filesDir
                    val dest = File(downloads, ciphertextFile.name.removePrefix("enc_"))
                    dest.writeBytes(plaintext)
                    ciphertextFile.delete()
                    trySend(TransportEvent.Completed(dest.absolutePath))
                    filesRemaining--
                    if (filesRemaining <= 0) {
                        close()
                    }
                } catch (_: Exception) {
                    ciphertextFile.delete()
                }
            }
        }

        val lifecycleCallback = object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
                endpointDisplayNames[endpointId] = info.endpointName
                client.acceptConnection(endpointId, receiverPayloadCallback)
            }

            override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
                if (result.status.isSuccess) {
                    connectedEndpointId = endpointId
                    client.stopDiscovery()
                    trySend(TransportEvent.Connected(endpointDisplayNames[endpointId] ?: endpointId))
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
                endpointDisplayNames[endpointId] = info.endpointName
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
        val out = tcpOutput
        if (out != null) {
            val totalBytes = filePaths.sumOf { File(it).length() }
            var sentPlain = 0L
            try {
                withContext(Dispatchers.IO) {
                    for (path in filePaths) {
                        val plain = File(path).readBytes()
                        val enc = crypto.encrypt(plain, encryptionKey)
                        out.writeLong(enc.size.toLong())
                        out.write(enc)
                        sentPlain += plain.size
                        trySend(TransferProgress(sentPlain.coerceAtMost(totalBytes), totalBytes))
                    }
                    out.flush()
                }
            } catch (e: Exception) {
                close(e)
            }
            awaitClose { }
            return@callbackFlow
        }

        val endpointId = connectedEndpointId
        if (endpointId == null) {
            close(Exception("No connected endpoint"))
            return@callbackFlow
        }

        val totalBytes = filePaths.sumOf { File(it).length() }
        val pendingPayloads = mutableMapOf<Long, File>()
        val plaintextSizeByPayload = mutableMapOf<Long, Long>()
        val ciphertextSizeByPayload = mutableMapOf<Long, Long>()
        val transferredPerPayload = mutableMapOf<Long, Long>()

        fun emitAggregateProgress() {
            var plaintextProgress = 0L
            for ((id, encFile) in pendingPayloads) {
                val plainLen = plaintextSizeByPayload[id] ?: 0L
                val encLen = ciphertextSizeByPayload[id] ?: encFile.length().coerceAtLeast(1L)
                val sent = (transferredPerPayload[id] ?: 0L).coerceAtMost(encLen)
                plaintextProgress += (plainLen.toDouble() * sent.toDouble() / encLen.toDouble()).toLong()
            }
            trySend(TransferProgress(plaintextProgress.coerceAtMost(totalBytes), totalBytes))
        }

        onPayloadUpdate = { update ->
            when (update.status) {
                PayloadTransferUpdate.Status.IN_PROGRESS -> {
                    transferredPerPayload[update.payloadId] = update.bytesTransferred
                    emitAggregateProgress()
                }
                PayloadTransferUpdate.Status.SUCCESS -> {
                    transferredPerPayload[update.payloadId] =
                        ciphertextSizeByPayload[update.payloadId] ?: 0L
                    emitAggregateProgress()
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
            plaintextSizeByPayload[payload.id] = original.length()
            ciphertextSizeByPayload[payload.id] = tempFile.length()
            client.sendPayload(endpointId, payload)
        }

        awaitClose { onPayloadUpdate = null }
    }

    actual fun stopAll() {
        closeTcp()
        client.stopAdvertising()
        client.stopDiscovery()
        connectedEndpointId?.let { client.disconnectFromEndpoint(it) }
        connectedEndpointId = null
        onPayloadUpdate = null
        endpointDisplayNames.clear()
    }

    private fun closeTcp() {
        runCatching { tcpOutput?.flush() }
        runCatching { tcpSocket?.close() }
        runCatching { tcpServerSocket?.close() }
        tcpSocket = null
        tcpServerSocket = null
        tcpInput = null
        tcpOutput = null
    }
}
