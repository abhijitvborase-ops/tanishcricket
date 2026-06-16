package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.*
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val viewModel: ScorerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var isDarkMode by remember { mutableStateOf(false) }

            MyApplicationTheme(darkTheme = isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = "splash") {
                        composable("splash") {
                            SplashScreen(onDismiss = {
                                navController.navigate("login") {
                                    popUpTo("splash") { inclusive = true }
                                }
                            })
                        }

                        composable("login") {
                            LoginScreen(
                                viewModel = viewModel,
                                onLoginSuccess = {
                                    navController.navigate("dashboard") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                },
                                isDarkMode = isDarkMode,
                                onThemeToggle = { isDarkMode = !isDarkMode }
                            )
                        }

                        composable("dashboard") {
                            DashboardScreen(
                                viewModel = viewModel,
                                onCreateMatch = { navController.navigate("match_creation") },
                                onPlayerManagement = { navController.navigate("players") },
                                onMatchScoring = { navController.navigate("scoring") },
                                onMatchHistory = { navController.navigate("history") },
                                onStatsDashboard = { navController.navigate("stats") },
                                onLogout = {
                                    viewModel.signOut()
                                    navController.navigate("login") {
                                        popUpTo("dashboard") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("players") {
                            PlayerManagementScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("match_creation") {
                            MatchCreationScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onMatchScoringRedirect = { 
                                    navController.navigate("scoring") {
                                        popUpTo("match_creation") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("scoring") {
                            LiveScoringScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("history") {
                            MatchHistoryScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("stats") {
                            StatisticsScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
