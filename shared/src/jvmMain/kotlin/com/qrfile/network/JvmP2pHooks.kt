package com.qrfile.network

import java.net.ServerSocket

/** Desktop helper: hold an ephemeral [ServerSocket] until [P2PTransport] consumes it by port. */
object JvmP2pHooks {
    @Volatile
    private var parked: ServerSocket? = null

    fun park(socket: ServerSocket) {
        parked = socket
    }

    fun takeParked(expectedPort: Int): ServerSocket? {
        val s = parked ?: return null
        return if (s.localPort == expectedPort) {
            parked = null
            s
        } else {
            null
        }
    }
}
