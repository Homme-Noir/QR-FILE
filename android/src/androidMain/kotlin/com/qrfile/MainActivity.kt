package com.qrfile

import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.qrfile.nfc.NfcDispatch
import com.qrfile.ui.QRFileNavHost
import com.qrfile.ui.settings.SettingsViewModel
import com.qrfile.ui.theme.QRFileTheme

class MainActivity : ComponentActivity() {
    private val settingsViewModel: SettingsViewModel by viewModels()
    private var nfcAdapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        dispatchNfcIntent(intent)
        setContent {
            val themeMode by settingsViewModel.themeMode.collectAsState()
            QRFileTheme(themeMode = themeMode) {
                QRFileNavHost(settingsViewModel = settingsViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        dispatchNfcIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        val adapter = nfcAdapter ?: return
        val pi = NfcDispatch.createPendingIntent(this)
        adapter.enableForegroundDispatch(this, pi, null, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    private fun dispatchNfcIntent(intent: Intent?) {
        if (intent == null) return
        val text = NfcDispatch.extractText(intent) ?: return
        (application as QRFileApp).postNfcHandshakeJson(text)
    }
}
