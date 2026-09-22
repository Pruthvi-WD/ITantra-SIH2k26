package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.presentation.MainViewModel
import com.example.presentation.navigation.NavRoutes
import com.example.presentation.screens.AlertEmergencyDialog
import com.example.presentation.screens.BenchmarkScreen
import com.example.presentation.screens.DevDashboardScreen
import com.example.presentation.screens.DevicesScreen
import com.example.presentation.screens.HistoryScreen
import com.example.presentation.screens.LanguageSelectorScreen
import com.example.presentation.screens.MainScreen
import com.example.presentation.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.VoxDarkCanvas

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true) {
                VoxLinkApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun VoxLinkApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val isAlertDialogVisible by viewModel.isAlertDialogVisible.collectAsState()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(VoxDarkCanvas),
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NavRoutes.MAIN,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(NavRoutes.MAIN) {
                MainScreen(
                    viewModel = viewModel,
                    onNavigateDevices = { navController.navigate(NavRoutes.DEVICES) },
                    onNavigateLanguages = { navController.navigate(NavRoutes.LANGUAGES) },
                    onNavigateHistory = { navController.navigate(NavRoutes.HISTORY) },
                    onNavigateDashboard = { navController.navigate(NavRoutes.DASHBOARD) },
                    onNavigateBenchmark = { navController.navigate(NavRoutes.BENCHMARK) },
                    onNavigateSettings = { navController.navigate(NavRoutes.SETTINGS) }
                )
            }

            composable(NavRoutes.DEVICES) {
                DevicesScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(NavRoutes.LANGUAGES) {
                LanguageSelectorScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(NavRoutes.HISTORY) {
                HistoryScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(NavRoutes.DASHBOARD) {
                DevDashboardScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(NavRoutes.BENCHMARK) {
                BenchmarkScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(NavRoutes.SETTINGS) {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        // Emergency Broadcast Modal Dialog
        if (isAlertDialogVisible) {
            AlertEmergencyDialog(
                viewModel = viewModel,
                onDismiss = { viewModel.showAlertDialog(false) }
            )
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    androidx.compose.material3.Text(
        text = "VoxLink: $name",
        color = com.example.ui.theme.VoxCyanPrimary,
        modifier = modifier
    )
}
