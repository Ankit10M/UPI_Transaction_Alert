package com.upivoicealert.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.upivoicealert.R
import com.upivoicealert.ui.debug.UnparsedNotificationsScreen
import com.upivoicealert.ui.history.HistoryScreen
import com.upivoicealert.ui.home.HomeScreen
import com.upivoicealert.ui.onboarding.LandingScreen
import com.upivoicealert.ui.onboarding.MobileNumberScreen
import com.upivoicealert.ui.onboarding.PermissionSetupScreen
import com.upivoicealert.ui.onboarding.PrivacyExplanationScreen
import com.upivoicealert.ui.profile.ProfileScreen

object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val PROFILE = "profile"
    const val UNPARSED = "unparsed"
    const val LANDING = "landing"
    const val PRIVACY = "privacy"
    const val MOBILE_NUMBER = "mobileNumber"
    const val PERMISSION_SETUP = "permissionSetup"
}

private data class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@Composable
private fun bottomTabs(): List<BottomTab> = listOf(
    BottomTab(Routes.HOME, stringResource(R.string.dashboard_title), Icons.Filled.Home),
    BottomTab(Routes.HISTORY, stringResource(R.string.history_title), Icons.Filled.ReceiptLong),
    BottomTab(Routes.PROFILE, stringResource(R.string.profile_title), Icons.Filled.Person)
)

@Composable
fun MainNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val tabs = bottomTabs()
    val bottomBarVisible = currentRoute in tabs.map { it.route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (bottomBarVisible) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.HOME) {
                HomeScreen(onOpenHistory = { navController.navigate(Routes.HISTORY) })
            }
            composable(Routes.HISTORY) { HistoryScreen() }
            composable(Routes.PROFILE) {
                ProfileScreen(onOpenDebug = { navController.navigate(Routes.UNPARSED) })
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
    NavHost(navController = navController, startDestination = Routes.LANDING) {
        composable(Routes.LANDING) {
            LandingScreen(onStart = { navController.navigate(Routes.PRIVACY) })
        }
        composable(Routes.PRIVACY) {
            PrivacyExplanationScreen(onContinue = { navController.navigate(Routes.MOBILE_NUMBER) })
        }
        composable(Routes.MOBILE_NUMBER) {
            MobileNumberScreen(onContinue = { navController.navigate(Routes.PERMISSION_SETUP) })
        }
        composable(Routes.PERMISSION_SETUP) {
            PermissionSetupScreen(onFinish = onFinished)
        }
    }
}
