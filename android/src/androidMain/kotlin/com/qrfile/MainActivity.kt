package com.qrfile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.qrfile.ui.QRFileNavHost
import com.qrfile.ui.settings.SettingsViewModel
import com.qrfile.ui.theme.QRFileTheme

class MainActivity : ComponentActivity() {
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by settingsViewModel.themeMode.collectAsState()
            QRFileTheme(themeMode = themeMode) {
                QRFileNavHost(settingsViewModel = settingsViewModel)
            }
        }
    }
}
