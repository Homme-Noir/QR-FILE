package com.qrfile.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.qrfile.storage.ScanRecordDao
import com.qrfile.storage.ScanRecordEntity
import com.qrfile.storage.TransferRecordDao
import com.qrfile.storage.TransferRecordEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed class HistoryItem {
    data class Transfer(val entity: TransferRecordEntity) : HistoryItem()
    data class Scan(val entity: ScanRecordEntity) : HistoryItem()

    val timestampMs: Long get() = when (this) {
        is Transfer -> entity.timestampMs
        is Scan -> entity.timestampMs
    }
}

class HistoryViewModel(
    transferDao: TransferRecordDao,
    scanDao: ScanRecordDao,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    val items: StateFlow<List<HistoryItem>> = combine(
        transferDao.observeAll(),
        scanDao.observeAll(),
        _query,
    ) { transfers, scans, q ->
        buildList {
            addAll(transfers.map { HistoryItem.Transfer(it) })
            addAll(
                scans
                    .filter { q.isBlank() || it.rawValue.contains(q, ignoreCase = true) }
                    .map { HistoryItem.Scan(it) }
            )
        }.sortedByDescending { it.timestampMs }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(q: String) { _query.value = q }
}

class HistoryViewModelFactory(
    private val transferDao: TransferRecordDao,
    private val scanDao: ScanRecordDao,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return HistoryViewModel(transferDao, scanDao) as T
    }
}
