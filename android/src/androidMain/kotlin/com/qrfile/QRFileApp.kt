package com.qrfile

import android.app.Application
import com.qrfile.network.P2PTransport
import com.qrfile.storage.AppDatabase

class QRFileApp : Application() {
    val database by lazy { AppDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        P2PTransport.init(this)
    }
}
