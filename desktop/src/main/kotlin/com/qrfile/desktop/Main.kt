package com.qrfile.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.qrfile.handshake.HandshakePayload
import com.qrfile.handshake.TcpDirect
import com.qrfile.network.JvmP2pHooks
import com.qrfile.transfer.TransferManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "QR-FILE Desktop") {
        DesktopApp()
    }
}

@Composable
private fun DesktopApp() {
    val scope = rememberCoroutineScope()
    val manager = remember { TransferManager() }
    var status by remember { mutableStateOf("Pick files, then start listener.") }
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var payloadJson by remember { mutableStateOf("") }

    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("LAN send (TCP)", style = MaterialTheme.typography.titleLarge)
            Text(status, style = MaterialTheme.typography.bodyMedium)

            Button(onClick = {
                val chooser = javax.swing.JFileChooser().apply {
                    dialogTitle = "Select files"
                    isMultiSelectionEnabled = true
                }
                if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
                    files = chooser.selectedFiles.toList()
                    status = "${files.size} file(s) selected"
                }
            }) { Text("Choose files…") }

            Button(
                enabled = files.isNotEmpty(),
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        status = "Opening listener…"
                        val host = localIpv4() ?: run {
                            status = "Could not detect a LAN IPv4 address."
                            return@launch
                        }
                        val ss = ServerSocket(0)
                        JvmP2pHooks.park(ss)
                        val total = files.sumOf { it.length() }
                        val payload = manager.prepareSend(
                            filePaths = files.map { it.absolutePath },
                            deviceName = System.getProperty("os.name", "desktop"),
                            tcpDirect = TcpDirect(host = host, port = ss.localPort),
                        ).copy(totalBytes = total)
                        val json = Json.encodeToString(HandshakePayload.serializer(), payload)
                        withContext(Dispatchers.Main) {
                            payloadJson = json
                            status = "Listening on $host:${ss.localPort} — share JSON with receiver."
                        }
                        manager.send(files.map { it.absolutePath }, payload).collect { }
                        withContext(Dispatchers.Main) {
                            status = "Send finished."
                        }
                    }
                },
            ) { Text("Start listener & send") }

            OutlinedTextField(
                value = payloadJson,
                onValueChange = { payloadJson = it },
                modifier = Modifier.weight(1f, fill = true),
                label = { Text("Handshake JSON") },
            )
        }
    }
}

private fun localIpv4(): String? =
    NetworkInterface.getNetworkInterfaces().toList()
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
        ?.hostAddress
