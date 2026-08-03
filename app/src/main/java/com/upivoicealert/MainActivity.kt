package com.upivoicealert

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.upivoicealert.domain.repository.SettingsRepository
import com.upivoicealert.ui.navigation.MainNavHost
import com.upivoicealert.ui.navigation.OnboardingNavHost
import com.upivoicealert.ui.theme.UPIVoiceAlertTheme
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
        setContent {
            UPIVoiceAlertTheme {
                val consented by settingsRepository.hasAcceptedPrivacyDisclosure
                    .collectAsStateWithLifecycle(initialValue = false)
                val debugMode by settingsRepository.debugModeEnabled
                    .collectAsStateWithLifecycle(initialValue = false)

                if (consented) {
                    MainNavHost(debugMode = debugMode)
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