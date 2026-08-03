package com.upivoicealert.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.upivoicealert.ui.dashboard.DashboardScreen
import com.upivoicealert.ui.debug.UnparsedNotificationsScreen
import com.upivoicealert.ui.history.HistoryScreen
import com.upivoicealert.ui.onboarding.ConsentScreen
import com.upivoicealert.ui.onboarding.PermissionSetupScreen
import com.upivoicealert.ui.onboarding.PrivacyExplanationScreen
import com.upivoicealert.ui.settings.SettingsScreen

object Routes {
    const val DASHBOARD = "dashboard"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val UNPARSED = "unparsed"
    const val CONSENT = "consent"
    const val PRIVACY = "privacy"
    const val PERMISSION_SETUP = "permissionSetup"
}

@Composable
fun MainNavHost(debugMode: Boolean) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val bottomBarVisible = currentRoute in listOf(Routes.DASHBOARD, Routes.HISTORY, Routes.SETTINGS)

    val startDestinationId = navController.graph.findStartDestination().id

    Scaffold(
        bottomBar = {
            if (bottomBarVisible) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == Routes.DASHBOARD,
                        onClick = {
                            navController.navigate(Routes.DASHBOARD) {
                                popUpTo(startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        label = { Text("Dashboard") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.HISTORY,
                        onClick = {
                            navController.navigate(Routes.HISTORY) {
                                popUpTo(startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                        label = { Text("History") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.SETTINGS,
                        onClick = {
                            navController.navigate(Routes.SETTINGS) {
                                popUpTo(startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        label = { Text("Settings") }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.DASHBOARD) { DashboardScreen() }
            composable(Routes.HISTORY) { HistoryScreen() }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    debugMode = debugMode,
                    onOpenDebug = { navController.navigate(Routes.UNPARSED) }
                )
            }
            composable(Routes.UNPARSED) {
                UnparsedNotificationsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
fun OnboardingNavHost(onFinished: () -> Unit) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.CONSENT) {
        composable(Routes.CONSENT) {
            ConsentScreen(onContinue = { navController.navigate(Routes.PRIVACY) })
        }
        composable(Routes.PRIVACY) {
            PrivacyExplanationScreen(onContinue = { navController.navigate(Routes.PERMISSION_SETUP) })
        }
        composable(Routes.PERMISSION_SETUP) {
            PermissionSetupScreen(onFinish = onFinished)
        }
    }
}