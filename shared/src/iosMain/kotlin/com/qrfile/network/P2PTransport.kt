package com.qrfile.network

import com.qrfile.crypto.CryptoEngine
import com.qrfile.handshake.HandshakePayload
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readFully
import io.ktor.utils.io.readLong
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writeLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

/**
 * iOS: plain TCP when [HandshakePayload.tcpDirect] is set (same-LAN as desktop/Android TCP).
 * Wi-Fi Aware without tcpDirect still requires a Swift/Network.framework bridge.
 */
actual class P2PTransport actual constructor() {

    private val crypto = CryptoEngine()
    private var selector: SelectorManager? = null
    private var serverSocket: ServerSocket? = null
    private var socket: Socket? = null

    actual fun startAdvertising(payload: HandshakePayload): Flow<TransportEvent> = callbackFlow {
        val direct = payload.tcpDirect
        if (direct == null) {
            trySend(
                TransportEvent.Failed(
                    "iOS Wi-Fi Aware (Network.framework) is not wired in Kotlin yet — use tcpDirect for LAN TCP.",
                ),
            )
            close()
            awaitClose { }
            return@callbackFlow
        }
        try {
            withContext(Dispatchers.Default) {
                val sel = SelectorManager(Dispatchers.Default)
                selector = sel
                val server = aSocket(sel).tcp().bind(InetSocketAddress("0.0.0.0", direct.port))
                serverSocket = server
                val s = server.accept()
                socket = s
                val peer = s.remoteAddress.toString()
                trySend(TransportEvent.Connected(peer))
            }
        } catch (e: Exception) {
            trySend(TransportEvent.Failed(e.message ?: "TCP listen failed"))
            close(e)
        }
        awaitClose { closeTcp() }
    }

    actual fun startDiscovery(payload: HandshakePayload): Flow<TransportEvent> = callbackFlow {
        val direct = payload.tcpDirect
        if (direct == null) {
            trySend(
                TransportEvent.Failed(
                    "iOS Wi-Fi Aware discovery is not wired in Kotlin yet — use tcpDirect for LAN TCP.",
                ),
            )
            close()
            awaitClose { }
            return@callbackFlow
        }
        try {
            withContext(Dispatchers.Default) {
                val sel = SelectorManager(Dispatchers.Default)
                selector = sel
                val s = aSocket(sel).tcp().connect(InetSocketAddress(direct.host, direct.port))
                socket = s
                trySend(TransportEvent.Connected(payload.deviceName))
                val read = s.openReadChannel()
                val key = crypto.deriveKey(payload.sessionPassword)
                val baseDir = NSTemporaryDirectory() + "qrfile_recv_" + NSUUID().UUIDString + "/"
                val basePath = baseDir.toPath()
                FileSystem.SYSTEM.createDirectories(basePath, mustCreate = false)
                repeat(payload.fileCount.coerceAtLeast(1)) {
                    val len = read.readLong()
                    TcpP2pFraming.requireValidCiphertextLength(len)
                    val enc = ByteArray(len.toInt())
                    read.readFully(enc)
                    val plain = crypto.decrypt(enc, key)
                    val name = "recv_${NSUUID().UUIDString}"
                    val dest = basePath / name
                    FileSystem.SYSTEM.write(dest, mustCreate = true) {
                        write(plain)
                    }
                    trySend(TransportEvent.Completed(dest.toString()))
                }
            }
        } catch (e: Exception) {
            trySend(TransportEvent.Failed(e.message ?: "TCP receive failed"))
            close(e)
        }
        awaitClose { closeTcp() }
    }

    actual fun sendFiles(filePaths: List<String>, encryptionKey: ByteArray): Flow<TransferProgress> = callbackFlow {
        val s = socket ?: run {
            close(Exception("Not connected"))
            return@callbackFlow
        }
        val totalBytes = filePaths.sumOf { path ->
            FileSystem.SYSTEM.metadataOrNull(path.toPath())?.size ?: 0L
        }
        var sentPlain = 0L
        try {
            withContext(Dispatchers.Default) {
                val write = s.openWriteChannel(autoFlush = true)
                for (path in filePaths) {
                    val plain = FileSystem.SYSTEM.read(path.toPath()) { readByteArray() }
                    val enc = crypto.encrypt(plain, encryptionKey)
                    write.writeLong(enc.size.toLong())
                    write.writeFully(enc)
                    sentPlain += plain.size
                    trySend(TransferProgress(sentPlain.coerceAtMost(totalBytes), totalBytes))
                }
            }
        } catch (e: Exception) {
            close(e)
        }
        awaitClose { }
    }

    actual fun stopAll() {
        closeTcp()
    }

    private fun closeTcp() {
        runCatching { serverSocket?.close() }
        runCatching { socket?.close() }
        runCatching { selector?.close() }
        serverSocket = null
        socket = null
        selector = null
    }
}
