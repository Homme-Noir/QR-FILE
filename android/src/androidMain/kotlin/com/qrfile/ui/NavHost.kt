package com.qrfile.ui

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.qrfile.QRFileApp
import com.qrfile.ui.history.HistoryScreen
import com.qrfile.ui.history.HistoryViewModel
import com.qrfile.ui.history.HistoryViewModelFactory
import com.qrfile.ui.scanner.ScannerScreen
import com.qrfile.ui.scanner.ScannerViewModel
import com.qrfile.ui.scanner.ScannerViewModelFactory
import com.qrfile.ui.settings.SettingsScreen
import com.qrfile.ui.settings.SettingsViewModel
import com.qrfile.ui.sharing.SharingScreen
import com.qrfile.ui.sharing.SharingViewModel
import com.qrfile.ui.sharing.SharingViewModelFactory

sealed class Screen(val route: String) {
    data object Scanner : Screen("scanner")
    data object Sharing : Screen("sharing")
    data object History : Screen("history")
    data object Settings : Screen("settings")
}

@Composable
fun QRFileNavHost(settingsViewModel: SettingsViewModel) {
    val context = LocalContext.current
    val app = context.applicationContext as QRFileApp
    val db = app.database

    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Scanner.route) {
        composable(Screen.Scanner.route) {
            val vm = viewModel<ScannerViewModel>(factory = ScannerViewModelFactory(db.scanRecordDao()))
            ScannerScreen(navController, vm)
        }
        composable(Screen.Sharing.route) {
            val vm = viewModel<SharingViewModel>(
                factory = SharingViewModelFactory(
                    context.applicationContext as Application,
                    db.transferRecordDao(),
                )
            )
            SharingScreen(navController, vm)
        }
        composable(Screen.History.route) {
            val vm = viewModel<HistoryViewModel>(
                factory = HistoryViewModelFactory(db.transferRecordDao(), db.scanRecordDao())
            )
            HistoryScreen(navController, vm)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(navController, settingsViewModel, db.transferRecordDao(), db.scanRecordDao())
        }
    }
}
