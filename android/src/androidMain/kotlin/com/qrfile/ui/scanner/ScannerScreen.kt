package com.qrfile.ui.scanner

import android.app.Activity
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.zxing.integration.android.IntentIntegrator
import com.qrfile.handshake.HandshakeJson
import com.qrfile.handshake.HandshakePayload
import com.qrfile.storage.ScanRecord
import com.qrfile.storage.ScanRecordEntity
import com.qrfile.ui.Screen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun ScannerScreen(navController: NavController, viewModel: ScannerViewModel) {
    val context = LocalContext.current
    val scans by viewModel.scans.collectAsState()
    val app = context.applicationContext as com.qrfile.QRFileApp
    val nfcRaw by app.nfcHandshakeJson.collectAsState()

    LaunchedEffect(nfcRaw) {
        val raw = nfcRaw ?: return@LaunchedEffect
        val handshake = runCatching {
            HandshakeJson.decodeFromString(HandshakePayload.serializer(), raw)
        }.getOrNull()
        if (handshake != null) {
            val b64 = Base64.encodeToString(
                raw.toByteArray(Charsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
            )
            navController.navigate(Screen.Receive.createRoute(b64))
        }
        app.consumeNfcHandshakeJson()
    }

    val scanLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val scanResult = IntentIntegrator.parseActivityResult(result.resultCode, result.data)
        scanResult?.contents?.let { rawValue ->
            val record = ScanRecord(
                id = UUID.randomUUID().toString(),
                rawValue = rawValue,
                category = categorize(rawValue),
                timestampMs = System.currentTimeMillis(),
            )
            viewModel.saveScan(record)
            val handshake = runCatching {
                HandshakeJson.decodeFromString(HandshakePayload.serializer(), rawValue)
            }.getOrNull()
            if (handshake != null) {
                val b64 = Base64.encodeToString(
                    rawValue.toByteArray(Charsets.UTF_8),
                    Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
                )
                navController.navigate(Screen.Receive.createRoute(b64))
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Scanner", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        Button(onClick = {
            val integrator = IntentIntegrator(context as Activity)
            integrator.setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES)
            integrator.setPrompt("Scan a QR code or barcode")
            integrator.setOrientationLocked(false)
            scanLauncher.launch(integrator.createScanIntent())
        }) {
            Text("Scan QR Code")
        }

        Spacer(Modifier.height(16.dp))

        if (scans.isEmpty()) {
            Text(
                "No scans yet. Tap the button to scan.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(scans) { scan -> ScanItem(scan) }
            }
        }
    }
}

@Composable
private fun ScanItem(scan: ScanRecordEntity) {
    val dateStr = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
        .format(Date(scan.timestampMs))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(scan.category, style = MaterialTheme.typography.labelSmall)
            Text(
                scan.rawValue,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(dateStr, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun categorize(rawValue: String): ScanRecord.Category = when {
    rawValue.startsWith("http://") || rawValue.startsWith("https://") ||
        rawValue.startsWith("ftp://") -> ScanRecord.Category.URL
    rawValue.startsWith("WIFI:") -> ScanRecord.Category.WIFI
    rawValue.startsWith("BEGIN:VCARD") -> ScanRecord.Category.CONTACT
    rawValue.all { it.isDigit() } && rawValue.length >= 8 -> ScanRecord.Category.PRODUCT
    rawValue.isNotBlank() -> ScanRecord.Category.TEXT
    else -> ScanRecord.Category.OTHER
}
