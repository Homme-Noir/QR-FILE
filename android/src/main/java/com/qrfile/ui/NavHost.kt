package com.qrfile.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.qrfile.ui.history.HistoryScreen
import com.qrfile.ui.scanner.ScannerScreen
import com.qrfile.ui.settings.SettingsScreen
import com.qrfile.ui.sharing.SharingScreen

sealed class Screen(val route: String) {
    data object Scanner : Screen("scanner")
    data object Sharing : Screen("sharing")
    data object History : Screen("history")
    data object Settings : Screen("settings")
}

@Composable
fun QRFileNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Screen.Scanner.route) {
        composable(Screen.Scanner.route) { ScannerScreen(navController) }
        composable(Screen.Sharing.route) { SharingScreen(navController) }
        composable(Screen.History.route) { HistoryScreen(navController) }
        composable(Screen.Settings.route) { SettingsScreen(navController) }
    }
}
