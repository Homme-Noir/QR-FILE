package com.qrfile.ui.sharing

import android.app.Application
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.qrfile.handshake.HandshakePayload
import com.qrfile.handshake.TcpDirect
import com.qrfile.network.AndroidP2pHooks
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
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
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

    /** Display name of the peer once Nearby reports a connection (receiver's model name, etc.). */
    private var connectedPeerName: String? = null

    override fun onCleared() {
        manager.stop()
        AndroidP2pHooks.closeParked()
        super.onCleared()
    }

    fun onFilesSelected(uris: List<Uri>) = viewModelScope.launch {
        val app = getApplication<Application>()
        val (tempFiles, payload) = withContext(Dispatchers.IO) {
            AndroidP2pHooks.closeParked()
            val files = uris.map { uri -> uri.toTempFile(app) }
            val totalBytes = files.sumOf { it.length() }
            val lanIp = localSiteLocalIpv4()
            val tcpDirect = if (lanIp != null) {
                val ss = ServerSocket()
                ss.reuseAddress = true
                ss.bind(InetSocketAddress("0.0.0.0", 0), 0)
                AndroidP2pHooks.park(ss)
                TcpDirect(host = lanIp, port = ss.localPort)
            } else {
                null
            }
            val payload = manager.prepareSend(
                filePaths = files.map { it.absolutePath },
                deviceName = Build.MODEL,
                tcpDirect = tcpDirect,
            ).copy(totalBytes = totalBytes)
            files to payload
        }
        _uiState.value = SharingUiState.ReadyToShare(tempFiles, payload)
    }

    fun startSend(filePaths: List<String>, payload: HandshakePayload) = viewModelScope.launch {
        connectedPeerName = null
        manager.send(filePaths, payload).collect { event ->
            when (event) {
                is com.qrfile.network.TransportEvent.Connected -> {
                    connectedPeerName = event.remoteDeviceName
                    _uiState.value = SharingUiState.Sending(0f)
                }
                is com.qrfile.network.TransportEvent.Progress -> {
                    val p = event.progress
                    val frac = if (p.totalBytes > 0) {
                        p.bytesTransferred.toFloat() / p.totalBytes.toFloat()
                    } else {
                        0f
                    }
                    _uiState.value = SharingUiState.Sending(frac.coerceIn(0f, 1f))
                }
                is com.qrfile.network.TransportEvent.Completed -> {
                    transferDao.insert(
                        TransferRecord(
                            id = UUID.randomUUID().toString(),
                            direction = TransferRecord.Direction.SENT,
                            remoteDeviceName = connectedPeerName ?: payload.deviceName,
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

    fun cancelActiveTransfer() {
        manager.stop()
        if (_uiState.value is SharingUiState.Sending) {
            _uiState.value = SharingUiState.Error("Cancelled")
        }
    }

    fun reset() {
        connectedPeerName = null
        AndroidP2pHooks.closeParked()
        _uiState.value = SharingUiState.Idle
    }
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

private fun localSiteLocalIpv4(): String? =
    runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()
