package com.upivoicealert.ui.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.upivoicealert.domain.model.VoiceLanguage
import com.upivoicealert.domain.repository.SettingsRepository
import com.upivoicealert.utils.BatteryOptimizationHelper
import com.upivoicealert.utils.NotificationAccessHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
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

    private val _listenerGranted = MutableStateFlow(NotificationAccessHelper.isGranted(context))
    val listenerGranted: StateFlow<Boolean> = _listenerGranted

    private val _batteryIgnored = MutableStateFlow(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context))
    val batteryIgnored: StateFlow<Boolean> = _batteryIgnored

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
}