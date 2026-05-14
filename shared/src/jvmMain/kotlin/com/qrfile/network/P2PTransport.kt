package com.qrfile.network

import com.qrfile.crypto.CryptoEngine
import com.qrfile.handshake.HandshakePayload
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

/**
 * JVM/desktop transport: plain TCP when [HandshakePayload.tcpDirect] is set (same-LAN transfer).
 * Wire format per file: `int64 ciphertextLength` then ciphertext bytes (from [CryptoEngine]).
 */
actual class P2PTransport actual constructor() {

    private val crypto = CryptoEngine()
    private var serverSocket: ServerSocket? = null
    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null

    actual fun startAdvertising(payload: HandshakePayload): Flow<TransportEvent> = callbackFlow {
        val direct = payload.tcpDirect
        if (direct == null) {
            trySend(TransportEvent.Failed("Missing tcpDirect in handshake (desktop sender must publish host:port)"))
            close()
            return@callbackFlow
        }
        try {
            withContext(Dispatchers.IO) {
                val ss = JvmP2pHooks.takeParked(direct.port) ?: run {
                    val s = ServerSocket()
                    s.reuseAddress = true
                    s.bind(InetSocketAddress("0.0.0.0", direct.port), 1)
                    s
                }
                serverSocket = ss
                val s = ss.accept()
                socket = s
                input = DataInputStream(s.getInputStream())
                output = DataOutputStream(s.getOutputStream())
                trySend(TransportEvent.Connected(direct.host))
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
            trySend(TransportEvent.Failed("Missing tcpDirect in handshake"))
            close()
            return@callbackFlow
        }
        try {
            withContext(Dispatchers.IO) {
                val s = Socket()
                s.connect(InetSocketAddress(direct.host, direct.port), 30_000)
                socket = s
                val inp = DataInputStream(s.getInputStream())
                input = inp
                output = DataOutputStream(s.getOutputStream())
                trySend(TransportEvent.Connected(payload.deviceName))
                val key = crypto.deriveKey(payload.sessionPassword)
                val tmp = File(System.getProperty("java.io.tmpdir"), "qrfile-recv")
                tmp.mkdirs()
                repeat(payload.fileCount.coerceAtLeast(1)) {
                    val len = inp.readLong()
                    TcpP2pFraming.requireValidCiphertextLength(len)
                    val enc = ByteArray(len.toInt())
                    inp.readFully(enc)
                    val plain = crypto.decrypt(enc, key)
                    val dest = File(tmp, "recv_${UUID.randomUUID()}")
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

    actual fun sendFiles(filePaths: List<String>, encryptionKey: ByteArray): Flow<TransferProgress> = callbackFlow {
        val out = output ?: run {
            close(Exception("Not connected"))
            return@callbackFlow
        }
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
    }

    actual fun stopAll() {
        closeTcp()
    }

    private fun closeTcp() {
        runCatching { output?.flush() }
        runCatching { socket?.close() }
        runCatching { serverSocket?.close() }
        socket = null
        serverSocket = null
        input = null
        output = null
    }
}
