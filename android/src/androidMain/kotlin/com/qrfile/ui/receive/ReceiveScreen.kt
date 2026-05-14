package com.qrfile.ui.receive

import android.util.Base64
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.qrfile.handshake.HandshakeJson
import com.qrfile.handshake.HandshakePayload
import com.qrfile.ui.Screen

@Composable
fun ReceiveScreen(
    navController: NavController,
    payloadB64: String,
    viewModel: ReceiveViewModel,
) {
    val payload = remember(payloadB64) { decodePayloadFromRoute(payloadB64) }
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(payloadB64) {
        val p = decodePayloadFromRoute(payloadB64) ?: return@LaunchedEffect
        viewModel.startReceive(p)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Receive files", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        when {
            payload == null -> {
                Text(
                    "Invalid or expired QR code payload.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { navController.popBackStack() }) { Text("Back") }
            }
            else -> {
                Text(
                    "From: ${payload.deviceName} · ${payload.fileCount} file(s)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))

                when (val state = uiState) {
                    is ReceiveUiState.Idle, ReceiveUiState.Connecting -> {
                        Text("Connecting…", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(16.dp))
                        CircularProgressIndicator()
                    }
                    is ReceiveUiState.Receiving -> {
                        Text("Receiving…", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${state.savedPaths.size} / ${state.expectedCount} complete",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(16.dp))
                        CircularProgressIndicator()
                    }
                    is ReceiveUiState.Done -> {
                        Text("All files saved.", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = {
                            viewModel.reset()
                            navController.navigate(Screen.Scanner.route) {
                                popUpTo(Screen.Scanner.route) { inclusive = true }
                            }
                        }) { Text("Done") }
                    }
                    is ReceiveUiState.Error -> {
                        Text(
                            state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = {
                            viewModel.reset()
                            navController.popBackStack()
                        }) { Text("Back") }
                    }
                }

                if (uiState is ReceiveUiState.Connecting || uiState is ReceiveUiState.Receiving) {
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = {
                        viewModel.cancel()
                    }) { Text("Cancel") }
                }
            }
        }
    }
}

private fun decodePayloadFromRoute(payloadB64: String): HandshakePayload? {
    val bytes = runCatching {
        Base64.decode(payloadB64, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }.recoverCatching {
        Base64.decode(payloadB64, Base64.URL_SAFE or Base64.NO_WRAP)
    }.getOrNull() ?: return null
    val json = String(bytes, Charsets.UTF_8)
    return runCatching { HandshakeJson.decodeFromString(HandshakePayload.serializer(), json) }.getOrNull()
}
