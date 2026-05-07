package com.qrfile.ui.history

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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(navController: NavController, viewModel: HistoryViewModel) {
    val items by viewModel.items.collectAsState()
    val query by viewModel.query.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text("History", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.setQuery(it) },
            placeholder = { Text("Search…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))

        if (items.isEmpty()) {
            Text(
                "No history yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(items) { item ->
                    when (item) {
                        is HistoryItem.Transfer -> TransferItem(item)
                        is HistoryItem.Scan -> ScanItem(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferItem(item: HistoryItem.Transfer) {
    val e = item.entity
    val dateStr = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(e.timestampMs))
    val direction = if (e.direction == "SENT") "Sent" else "Received"
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("$direction · ${e.remoteDeviceName}", style = MaterialTheme.typography.labelSmall)
            Text(
                "${e.fileNamesJson.count { it == '"' } / 2} file(s) · ${e.totalBytes.toHumanSize()}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(dateStr, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ScanItem(item: HistoryItem.Scan) {
    val e = item.entity
    val dateStr = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(e.timestampMs))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(e.category, style = MaterialTheme.typography.labelSmall)
            Text(
                e.rawValue,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(dateStr, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun Long.toHumanSize(): String = when {
    this < 1024 -> "$this B"
    this < 1024 * 1024 -> "${this / 1024} KB"
    else -> String.format("%.1f MB", this / (1024.0 * 1024.0))
}
