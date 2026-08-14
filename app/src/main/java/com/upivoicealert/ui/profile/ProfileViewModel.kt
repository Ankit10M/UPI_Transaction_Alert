package com.upivoicealert.ui.profile

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.upivoicealert.domain.model.MerchantUser
import com.upivoicealert.domain.model.SubscriptionInfo
import com.upivoicealert.domain.model.VoiceLanguage
import com.upivoicealert.domain.repository.SettingsRepository
import com.upivoicealert.domain.repository.SubscriptionRepository
import com.upivoicealert.domain.repository.UserRepository
import com.upivoicealert.utils.BatteryOptimizationHelper
import com.upivoicealert.utils.NotificationAccessHelper
import com.upivoicealert.voice.AmountToWordsConverter
import com.upivoicealert.voice.VoiceAnnouncementEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ProfileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val userRepository: UserRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val voiceEngine: VoiceAnnouncementEngine,
    private val amountToWordsConverter: AmountToWordsConverter
) : ViewModel() {

    val voiceEnabled: StateFlow<Boolean> = settingsRepository.voiceEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val language: StateFlow<VoiceLanguage> = settingsRepository.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceLanguage.ENGLISH)

    val speechRate: StateFlow<Float> = settingsRepository.speechRate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.0f)

    val debugMode: StateFlow<Boolean> = settingsRepository.debugModeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val ttsFallbackOccurred: StateFlow<Boolean> = settingsRepository.ttsFallbackOccurred
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val mobileNumber: StateFlow<String> = settingsRepository.mobileNumber
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val userName: StateFlow<String> = settingsRepository.userName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /** Merchant profile (Feature 5) — single source via [UserRepository]. */
    val user: StateFlow<MerchantUser?> = userRepository.observeUser()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Current subscription (Feature 3). */
    val subscription: StateFlow<SubscriptionInfo> = subscriptionRepository.observeSubscription()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubscriptionInfo(
            plan = com.upivoicealert.domain.model.SubscriptionPlans.FREE_TRIAL,
            status = com.upivoicealert.domain.model.SubscriptionStatus.FREE_TRIAL
        ))

    private val _listenerGranted = MutableStateFlow(NotificationAccessHelper.isGranted(context))
    val listenerGranted: StateFlow<Boolean> = _listenerGranted

    private val _batteryIgnored = MutableStateFlow(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context))
    val batteryIgnored: StateFlow<Boolean> = _batteryIgnored

    /** App version label for the About dialog, e.g. "1.0 (1)". */
    val appVersion: String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "${info.versionName} (${info.versionCode})"
    }.getOrDefault("1.0")

    fun setVoiceEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setVoiceEnabled(enabled)
    }

    fun setLanguage(language: VoiceLanguage) = viewModelScope.launch {
        settingsRepository.setLanguage(language)
    }

    fun setSpeechRate(rate: Float) = viewModelScope.launch {
        settingsRepository.setSpeechRate(rate)
    }

    fun setDebugMode(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setDebugModeEnabled(enabled)
    }

    fun setMobileNumber(number: String) = viewModelScope.launch {
        settingsRepository.setMobileNumber(number)
    }

    fun setUserName(name: String) = viewModelScope.launch {
        settingsRepository.setUserName(name)
    }

    /** Saves the full profile (name + shop name + phone) via the UserRepository. */
    fun saveProfile(name: String, shopName: String, phone: String) = viewModelScope.launch {
        userRepository.saveProfile(name, shopName, phone)
    }

    /**
     * Feature 7 — test voice: speaks a sample announcement immediately through
     * the existing TTS engine using the current language + speech rate.
     */
    fun testVoice() = viewModelScope.launch {
        try {
            val selected = settingsRepository.getLanguage()
            val rate = settingsRepository.getSpeechRate()
            val fellBackToEnglish = voiceEngine.prepare(selected, rate)
            val effective = if (fellBackToEnglish) VoiceLanguage.ENGLISH else selected
            voiceEngine.speak(testPhrase(effective))
            Log.i(TAG, "TEST_VOICE language=$selected effective=$effective rate=$rate fallback=$fellBackToEnglish")
        } catch (e: Exception) {
            Log.w(TAG, "TEST_VOICE failed (non-blocking)", e)
        }
    }

    fun refreshPermissionStatus() {
        _listenerGranted.value = NotificationAccessHelper.isGranted(context)
        _batteryIgnored.value = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
    }

    fun openNotificationAccessSettings() {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    fun requestBatteryExemption() {
        BatteryOptimizationHelper.requestExemption(context)
    }

    private fun testPhrase(language: VoiceLanguage): String {
        val words = amountToWordsConverter.convert(500.0, language)
        return when (language) {
            VoiceLanguage.ENGLISH -> "Test voice. Received $words via ShoutPay"
            VoiceLanguage.HINDI -> "टेस्ट आवाज़. $words प्राप्त हुए ShoutPay के माध्यम से"
            VoiceLanguage.MARATHI -> "चाचणी आवाज. $words मिळाले ShoutPay द्वारे"
        }
    }

    private companion object {
        const val TAG = "SHOUTPAY_TTS_DEBUG"
    }
}
