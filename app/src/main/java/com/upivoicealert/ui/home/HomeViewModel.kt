package com.upivoicealert.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.upivoicealert.domain.model.Transaction
import com.upivoicealert.domain.repository.SettingsRepository
import com.upivoicealert.domain.repository.TransactionRepository
import com.upivoicealert.utils.BatteryOptimizationHelper
import com.upivoicealert.utils.DateTimeUtils
import com.upivoicealert.utils.NotificationAccessHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    transactionRepository: TransactionRepository
) : ViewModel() {

    private val startOfToday = DateTimeUtils.startOfToday()

    /** Master voice-service switch (the big START/STOP control). */
    val monitoringEnabled: StateFlow<Boolean> = settingsRepository.monitoringEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Voice announcement preference (Profile toggle). */
    val voiceEnabled: StateFlow<Boolean> = settingsRepository.voiceEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val mobileNumber: StateFlow<String> = settingsRepository.mobileNumber
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /** Latest received payment from Room (null = empty state). */
    val latest: StateFlow<Transaction?> = transactionRepository.observeLatest()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Payments announced today (saved + announced = received-success count since midnight). */
    val announcedToday: StateFlow<Int> = transactionRepository.observeCountSince(startOfToday)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Missed payments today = notifications that failed to parse/announce. */
    val missedToday: StateFlow<Int> = transactionRepository.observeUnparsedCountSince(startOfToday)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _notificationGranted = MutableStateFlow(NotificationAccessHelper.isGranted(context))
    private val _batteryIgnored = MutableStateFlow(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context))

    /** Combined protection status: notification access + voice + battery. */
    val protectionStatus: StateFlow<HomeProtectionStatus> = combine(
        _notificationGranted,
        _batteryIgnored,
        voiceEnabled
    ) { notification, battery, voice ->
        HomeProtectionStatus(
            notificationConnected = notification,
            batteryAllowed = battery,
            voiceReady = voice
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeProtectionStatus())

    fun toggleMonitoring() {
        viewModelScope.launch {
            settingsRepository.setMonitoringEnabled(!monitoringEnabled.value)
        }
    }

    fun setMobileNumber(number: String) = viewModelScope.launch {
        settingsRepository.setMobileNumber(number)
    }

    fun refreshPermissionStatus() {
        _notificationGranted.value = NotificationAccessHelper.isGranted(context)
        _batteryIgnored.value = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
    }
}

data class HomeProtectionStatus(
    val notificationConnected: Boolean = false,
    val batteryAllowed: Boolean = false,
    val voiceReady: Boolean = false
)
