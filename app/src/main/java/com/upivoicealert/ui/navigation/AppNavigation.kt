package com.upivoicealert.ui.navigation

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.upivoicealert.R
import com.upivoicealert.ui.business.BusinessScreen
import com.upivoicealert.ui.debug.UnparsedNotificationsScreen
import com.upivoicealert.ui.history.HistoryScreen
import com.upivoicealert.ui.home.HomeScreen
import com.upivoicealert.ui.onboarding.LandingScreen
import com.upivoicealert.ui.onboarding.MerchantProfileScreen
import com.upivoicealert.ui.onboarding.MobileNumberScreen
import com.upivoicealert.ui.onboarding.PermissionSetupScreen
import com.upivoicealert.ui.onboarding.PrivacyExplanationScreen
import com.upivoicealert.ui.onboarding.ReadyScreen
import com.upivoicealert.ui.onboarding.VoiceTestScreen
import com.upivoicealert.ui.pricing.PricingScreen
import com.upivoicealert.ui.profile.ProfileScreen
import com.upivoicealert.ui.verification.VerificationScreen

object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val BUSINESS = "business"
    const val PROFILE = "profile"
    const val VERIFICATION = "verification"
    const val PRICING = "pricing"
    const val UNPARSED = "unparsed"
    const val LANDING = "landing"
    const val PRIVACY = "privacy"
    const val MOBILE_NUMBER = "mobileNumber"
    const val MERCHANT_PROFILE = "merchantProfile"
    const val PERMISSION_SETUP = "permissionSetup"
    const val VOICE_TEST = "voiceTest"
    const val READY = "ready"
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
    BottomTab(Routes.BUSINESS, stringResource(R.string.business_title), Icons.Filled.Storefront),
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
                NavigationBar(
                    modifier = Modifier.height(88.dp),
                    tonalElevation = 3.dp,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
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
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                HomeScreen(
                    onOpenHistory = { navController.navigate(Routes.HISTORY) },
                    onOpenVerification = { navController.navigate(Routes.VERIFICATION) }
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(onOpenVerification = { navController.navigate(Routes.VERIFICATION) })
            }
            composable(Routes.BUSINESS) { BusinessScreen() }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    onOpenDebug = { navController.navigate(Routes.UNPARSED) },
                    onOpenPricing = { navController.navigate(Routes.PRICING) }
                )
            }
            composable(Routes.VERIFICATION) {
                VerificationScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.PRICING) {
                PricingScreen(onBack = { navController.popBackStack() })
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
            MobileNumberScreen(onContinue = { navController.navigate(Routes.MERCHANT_PROFILE) })
        }
        composable(Routes.MERCHANT_PROFILE) {
            MerchantProfileScreen(onContinue = { navController.navigate(Routes.PERMISSION_SETUP) })
        }
        composable(Routes.PERMISSION_SETUP) {
            PermissionSetupScreen(onFinish = { navController.navigate(Routes.VOICE_TEST) })
        }
        composable(Routes.VOICE_TEST) {
            VoiceTestScreen(onContinue = { navController.navigate(Routes.READY) })
        }
        composable(Routes.READY) {
            ReadyScreen(onFinish = onFinished)
        }
    }
}
