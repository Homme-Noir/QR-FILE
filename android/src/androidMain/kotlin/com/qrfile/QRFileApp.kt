package com.qrfile

import android.app.Application
import com.qrfile.network.P2PTransport
import com.qrfile.storage.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class QRFileApp : Application() {
    val database by lazy { AppDatabase.getInstance(this) }

    private val _nfcHandshakeJson = MutableStateFlow<String?>(null)
    val nfcHandshakeJson: StateFlow<String?> = _nfcHandshakeJson.asStateFlow()

    fun postNfcHandshakeJson(json: String) {
        _nfcHandshakeJson.value = json
    }

    fun consumeNfcHandshakeJson() {
        _nfcHandshakeJson.value = null
    }

    override fun onCreate() {
        super.onCreate()
        P2PTransport.init(this)
    }
}
