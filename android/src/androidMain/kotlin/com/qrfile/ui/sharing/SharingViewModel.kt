package com.qrfile.ui.sharing

import android.app.Application
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.qrfile.handshake.HandshakePayload
import com.qrfile.storage.TransferRecord
import com.qrfile.storage.TransferRecordDao
import com.qrfile.storage.toEntity
import com.qrfile.transfer.TransferManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

sealed class SharingUiState {
    object Idle : SharingUiState()
    data class ReadyToShare(val files: List<File>, val payload: HandshakePayload) : SharingUiState()
    data class Sending(val progress: Float) : SharingUiState()
    object Done : SharingUiState()
    data class Error(val message: String) : SharingUiState()
}

class SharingViewModel(
    application: Application,
    private val transferDao: TransferRecordDao,
) : AndroidViewModel(application) {

    private val manager = TransferManager()
    private val _uiState = MutableStateFlow<SharingUiState>(SharingUiState.Idle)
    val uiState: StateFlow<SharingUiState> = _uiState

    fun onFilesSelected(uris: List<Uri>) = viewModelScope.launch {
        val app = getApplication<Application>()
        val tempFiles = withContext(Dispatchers.IO) {
            uris.map { uri -> uri.toTempFile(app) }
        }
        val totalBytes = tempFiles.sumOf { it.length() }
        val payload = manager.prepareSend(
            filePaths = tempFiles.map { it.absolutePath },
            deviceName = Build.MODEL,
        ).copy(totalBytes = totalBytes)
        _uiState.value = SharingUiState.ReadyToShare(tempFiles, payload)
    }

    fun startSend(filePaths: List<String>, payload: HandshakePayload) = viewModelScope.launch {
        manager.send(filePaths, payload).collect { event ->
            when (event) {
                is com.qrfile.network.TransportEvent.Connected ->
                    _uiState.value = SharingUiState.Sending(0f)
                is com.qrfile.network.TransportEvent.Completed -> {
                    transferDao.insert(
                        TransferRecord(
                            id = UUID.randomUUID().toString(),
                            direction = TransferRecord.Direction.SENT,
                            remoteDeviceName = event.savedPath,
                            fileNames = filePaths.map { File(it).name },
                            totalBytes = filePaths.sumOf { File(it).length() },
                            timestampMs = System.currentTimeMillis(),
                            success = true,
                        ).toEntity()
                    )
                    _uiState.value = SharingUiState.Done
                }
                is com.qrfile.network.TransportEvent.Failed ->
                    _uiState.value = SharingUiState.Error(event.reason)
                else -> {}
            }
        }
    }

    fun reset() { _uiState.value = SharingUiState.Idle }
}

class SharingViewModelFactory(
    private val application: Application,
    private val transferDao: TransferRecordDao,
) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return SharingViewModel(application, transferDao) as T
    }
}

private fun Uri.toTempFile(context: android.content.Context): File {
    val displayName = context.contentResolver.query(
        this, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: "file_${System.currentTimeMillis()}"

    val dest = File(context.cacheDir, displayName)
    context.contentResolver.openInputStream(this)?.use { input ->
        dest.outputStream().use { output -> input.copyTo(output) }
    }
    return dest
}
