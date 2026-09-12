package com.upivoicealert

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.upivoicealert.domain.repository.SettingsRepository
import com.upivoicealert.ui.navigation.MainNavHost
import com.upivoicealert.ui.navigation.OnboardingNavHost
import com.upivoicealert.ui.theme.ShoutPayTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Phase 9.2: Prevent screenshots/screen recordings and recent-apps thumbnail exposure
        // for merchant payment data (transaction history, amounts, sender names). Single-Activity
        // app → global FLAG_SECURE is least invasive (no per-screen fragility, no navigation side-effects).
        // This does not affect payment capture (offline-first), only display security.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        setContent {
            ShoutPayTheme {
                val consented by settingsRepository.hasAcceptedPrivacyDisclosure
                    .collectAsStateWithLifecycle(initialValue = false)

                if (consented) {
                    MainNavHost()
                } else {
                    OnboardingNavHost(
                        onFinished = {
                            CoroutineScope(Dispatchers.IO).launch {
                                settingsRepository.setHasAcceptedPrivacyDisclosure(true)
                            }
                        }
                    )
                }
            }
        }
    }
}