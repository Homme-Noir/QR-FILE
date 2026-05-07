package com.qrfile.ui.sharing

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.qrfile.handshake.HandshakePayload
import kotlinx.serialization.json.Json
import java.io.File

@Composable
fun SharingScreen(navController: NavController, viewModel: SharingViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> if (uris.isNotEmpty()) viewModel.onFilesSelected(uris) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Text("Share Files", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        when (val state = uiState) {
            is SharingUiState.Idle -> {
                Text(
                    "Select files to share with a nearby device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                    Text("Pick Files")
                }
            }

            is SharingUiState.ReadyToShare -> {
                Text("${state.files.size} file(s) selected", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                state.files.forEach { file ->
                    Text(
                        "${file.name} (${file.length().toHumanSize()})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(16.dp))

                val qrBitmap = remember(state.payload) {
                    val json = Json.encodeToString(HandshakePayload.serializer(), state.payload)
                    BarcodeEncoder().encodeBitmap(json, BarcodeFormat.QR_CODE, 512, 512)
                }
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "QR code for receiver to scan",
                    modifier = Modifier.size(240.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Have the receiver scan this QR code, then tap Send.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    viewModel.startSend(
                        state.files.map { it.absolutePath },
                        state.payload,
                    )
                }) { Text("Send") }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { viewModel.reset() }) { Text("Pick different files") }
            }

            is SharingUiState.Sending -> {
                Text("Sending…", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator()
            }

            is SharingUiState.Done -> {
                Text("Transfer complete!", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { viewModel.reset() }) { Text("Share another file") }
            }

            is SharingUiState.Error -> {
                Text(
                    "Error: ${state.message}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { viewModel.reset() }) { Text("Try again") }
            }
        }
    }
}

private fun Long.toHumanSize(): String = when {
    this < 1024 -> "$this B"
    this < 1024 * 1024 -> "${this / 1024} KB"
    else -> String.format("%.1f MB", this / (1024.0 * 1024.0))
}
