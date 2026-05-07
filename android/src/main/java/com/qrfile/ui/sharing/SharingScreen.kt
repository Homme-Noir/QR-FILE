package com.qrfile.ui.sharing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController

// TODO Phase 1: file picker → TransferManager.prepareSend() → display QR code
//               + NFC foreground dispatch for tap-to-send (Phase 3)
@Composable
fun SharingScreen(navController: NavController) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("File Sharing — coming in Phase 1")
    }
}
