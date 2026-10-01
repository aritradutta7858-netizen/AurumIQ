package com.aurumiq.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aurumiq.app.ui.navigation.Screen
import com.aurumiq.app.ui.screens.*
import com.aurumiq.app.ui.theme.AurumIQTheme
import com.aurumiq.app.ui.theme.SurfaceDark
import com.aurumiq.app.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private var pendingCsvCallback: ((Uri) -> Unit)? = null

    private val csvFilePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { pendingCsvCallback?.invoke(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AurumIQTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = SurfaceDark
                ) {
                    val viewModel: MainViewModel = viewModel()
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = Screen.Dashboard.route
                    ) {
                        composable(Screen.Dashboard.route) {
                            DashboardScreen(
                                viewModel = viewModel,
                                onNavigateToSignals = {
                                    navController.navigate(Screen.Signals.route)
                                },
                                onNavigateToPairDetail = { a, b ->
                                    navController.navigate(Screen.PairDetail.createRoute(a, b))
                                },
                                onNavigateToBacktest = {
                                    navController.navigate(Screen.Backtest.route)
                                },
                                onNavigateToMethodology = {
                                    navController.navigate(Screen.Methodology.route)
                                },
                                onImportCsv = {
                                    pendingCsvCallback = { uri ->
                                        contentResolver.openInputStream(uri)?.let { stream ->
                                            viewModel.importCsv(stream)
                                        }
                                    }
                                    csvFilePicker.launch("text/*")
                                }
                            )
                        }

                        composable(Screen.Signals.route) {
                            SignalsScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onPairSelected = { a, b ->
                                    navController.navigate(Screen.PairDetail.createRoute(a, b))
                                }
                            )
                        }

                        composable(
                            route = Screen.PairDetail.route,
                            arguments = listOf(
                                navArgument("symbolA") { type = NavType.StringType },
                                navArgument("symbolB") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val symbolA = backStackEntry.arguments?.getString("symbolA") ?: "GOLDM"
                            val symbolB = backStackEntry.arguments?.getString("symbolB") ?: "GOLDTEN"
                            PairDetailScreen(
                                viewModel = viewModel,
                                symbolA = symbolA,
                                symbolB = symbolB,
                                onBack = { navController.popBackStack() },
                                onNavigateToBacktest = {
                                    navController.navigate(Screen.Backtest.route)
                                }
                            )
                        }

                        composable(Screen.Backtest.route) {
                            BacktestScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(Screen.Methodology.route) {
                            MethodologyScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
