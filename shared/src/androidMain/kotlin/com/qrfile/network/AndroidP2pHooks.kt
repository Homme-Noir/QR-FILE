package com.qrfile.network

import java.net.ServerSocket

/** Android helper: hold a [ServerSocket] (e.g. pre-bound before QR shows tcpDirect) until [P2PTransport] consumes it. */
object AndroidP2pHooks {
    private val lock = Any()
    private var parked: ServerSocket? = null

    fun park(socket: ServerSocket) {
        synchronized(lock) {
            runCatching { parked?.close() }
            parked = socket
        }
    }

    fun takeParked(expectedPort: Int): ServerSocket? = synchronized(lock) {
        val s = parked ?: return null
        return if (s.localPort == expectedPort) {
            parked = null
            s
        } else {
            null
        }
    }

    /** If the user leaves the share screen before send, close the listener so the port is freed. */
    fun closeParked() {
        synchronized(lock) {
            runCatching { parked?.close() }
            parked = null
        }
    }
}
