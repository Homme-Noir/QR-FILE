package com.qrfile.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.qrfile.storage.ScanRecordDao
import com.qrfile.storage.TransferRecordDao

@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel,
    transferDao: TransferRecordDao,
    scanDao: ScanRecordDao,
) {
    val themeMode by viewModel.themeMode.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        Text("Appearance", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        ThemeMode.entries.forEach { mode ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                RadioButton(
                    selected = themeMode == mode,
                    onClick = { viewModel.setTheme(mode) },
                )
                Text(
                    text = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        Spacer(Modifier.height(32.dp))
        Text("Data", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { showClearDialog = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Text("Clear History")
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear history?") },
            text = { Text("This will permanently delete all scan and transfer records.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory(transferDao, scanDao)
                    showClearDialog = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            },
        )
    }
}
