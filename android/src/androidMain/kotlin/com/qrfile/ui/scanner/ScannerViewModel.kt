package com.qrfile.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.qrfile.storage.ScanRecord
import com.qrfile.storage.ScanRecordDao
import com.qrfile.storage.ScanRecordEntity
import com.qrfile.storage.toEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScannerViewModel(private val dao: ScanRecordDao) : ViewModel() {

    val scans: StateFlow<List<ScanRecordEntity>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveScan(record: ScanRecord) = viewModelScope.launch {
        dao.insert(record.toEntity())
    }
}

class ScannerViewModelFactory(private val dao: ScanRecordDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ScannerViewModel(dao) as T
    }
}
