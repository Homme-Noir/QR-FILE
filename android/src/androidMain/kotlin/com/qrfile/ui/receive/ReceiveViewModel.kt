package com.qrfile.ui.receive

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.qrfile.handshake.HandshakePayload
import com.qrfile.network.TransportEvent
import com.qrfile.storage.TransferRecord
import com.qrfile.storage.TransferRecordDao
import com.qrfile.storage.toEntity
import com.qrfile.transfer.TransferManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

sealed class ReceiveUiState {
    object Idle : ReceiveUiState()
    object Connecting : ReceiveUiState()
    data class Receiving(val savedPaths: List<String>, val expectedCount: Int) : ReceiveUiState()
    object Done : ReceiveUiState()
    data class Error(val message: String) : ReceiveUiState()
}

class ReceiveViewModel(
    application: Application,
    private val transferDao: TransferRecordDao,
) : AndroidViewModel(application) {

    private val manager = TransferManager()
    private val _uiState = MutableStateFlow<ReceiveUiState>(ReceiveUiState.Idle)
    val uiState: StateFlow<ReceiveUiState> = _uiState

    private var peerName: String? = null
    private var receiveJob: Job? = null

    override fun onCleared() {
        receiveJob?.cancel()
        manager.stop()
        super.onCleared()
    }

    fun startReceive(payload: HandshakePayload) {
        receiveJob?.cancel()
        receiveJob = viewModelScope.launch {
            peerName = null
            val expected = payload.fileCount.coerceAtLeast(1)
            val savedPaths = mutableListOf<String>()
            _uiState.value = ReceiveUiState.Connecting
            manager.receive(payload).collect { event ->
                when (event) {
                    is TransportEvent.Connected -> {
                        peerName = event.remoteDeviceName
                        _uiState.value = ReceiveUiState.Receiving(savedPaths.toList(), expected)
                    }
                    is TransportEvent.Completed -> {
                        savedPaths.add(event.savedPath)
                        _uiState.value = ReceiveUiState.Receiving(savedPaths.toList(), expected)
                        if (savedPaths.size >= expected) {
                            val totalBytes = savedPaths.sumOf { path -> File(path).length() }
                            transferDao.insert(
                                TransferRecord(
                                    id = UUID.randomUUID().toString(),
                                    direction = TransferRecord.Direction.RECEIVED,
                                    remoteDeviceName = peerName ?: payload.deviceName,
                                    fileNames = savedPaths.map { File(it).name },
                                    totalBytes = totalBytes,
                                    timestampMs = System.currentTimeMillis(),
                                    success = true,
                                ).toEntity(),
                            )
                            _uiState.value = ReceiveUiState.Done
                        }
                    }
                    is TransportEvent.Failed ->
                        _uiState.value = ReceiveUiState.Error(event.reason)
                    else -> Unit
                }
            }
        }
    }

    fun cancel() {
        receiveJob?.cancel()
        manager.stop()
        _uiState.value = ReceiveUiState.Error("Cancelled")
    }

    fun reset() {
        receiveJob?.cancel()
        peerName = null
        _uiState.value = ReceiveUiState.Idle
    }
}

class ReceiveViewModelFactory(
    private val application: Application,
    private val transferDao: TransferRecordDao,
) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ReceiveViewModel(application, transferDao) as T
    }
}
