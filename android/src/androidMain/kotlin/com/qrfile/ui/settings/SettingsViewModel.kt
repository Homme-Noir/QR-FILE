package com.qrfile.ui.settings

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qrfile.storage.ScanRecordDao
import com.qrfile.storage.TransferRecordDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ThemeMode { LIGHT, DARK, SYSTEM }

private val android.content.Context.themeDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "settings")

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = application.themeDataStore

    val themeMode: StateFlow<ThemeMode> = dataStore.data
        .map { prefs ->
            val saved = prefs[THEME_KEY]
            if (saved != null) ThemeMode.valueOf(saved) else ThemeMode.SYSTEM
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    fun setTheme(mode: ThemeMode) = viewModelScope.launch {
        dataStore.edit { it[THEME_KEY] = mode.name }
    }

    fun clearHistory(transferDao: TransferRecordDao, scanDao: ScanRecordDao) =
        viewModelScope.launch {
            transferDao.deleteAll()
            scanDao.deleteAll()
        }

    companion object {
        private val THEME_KEY = stringPreferencesKey("theme_mode")
    }
}
