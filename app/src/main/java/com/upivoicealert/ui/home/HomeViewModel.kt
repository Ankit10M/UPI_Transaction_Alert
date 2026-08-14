package com.upivoicealert.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.upivoicealert.domain.model.Transaction
import com.upivoicealert.domain.repository.SettingsRepository
import com.upivoicealert.domain.repository.TransactionRepository
import com.upivoicealert.service.ServiceController
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

/**
 * Home screen state holder.
 *
 * The UI only controls and observes the service flow through
 * [ServiceController] — it never writes pipeline state directly. Permission
 * states are real Android states (re-read on every resume), and the activity
 * numbers come from the Room database flows.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serviceController: ServiceController,
    private val settingsRepository: SettingsRepository,
    transactionRepository: TransactionRepository
) : ViewModel() {

    private val startOfToday = DateTimeUtils.startOfToday()

    private val _notificationGranted = MutableStateFlow(NotificationAccessHelper.isGranted(context))
    private val _batteryIgnored = MutableStateFlow(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context))

    /** Latest received payment from Room (null = empty state). */
    private val latest: StateFlow<Transaction?> = transactionRepository.observeLatest()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Payments announced today (received-success count since midnight). */
    private val announcedToday: StateFlow<Int> = transactionRepository.observeCountSince(startOfToday)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Missed payments today = notifications that failed to parse/announce. */
    private val missedToday: StateFlow<Int> = transactionRepository.observeUnparsedCountSince(startOfToday)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val mobileNumber: StateFlow<String> = settingsRepository.mobileNumber
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /** Service + permission state (single source: ServiceController + Android). */
    private val serviceState: StateFlow<ServiceState> = combine(
        serviceController.isRunning,
        _notificationGranted,
        _batteryIgnored,
        serviceController.voiceEnabled
    ) { running, notification, battery, voice ->
        ServiceState(
            isRunning = running,
            notificationGranted = notification,
            batteryAllowed = battery,
            voiceEnabled = voice
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServiceState())

    /** Single immutable UI state for the Home screen. */
    val uiState: StateFlow<ShoutPayUiState> = combine(
        serviceState,
        latest,
        announcedToday,
        missedToday
    ) { service, latestTx, announced, missed ->
        ShoutPayUiState(
            isServiceRunning = service.isRunning,
            notificationPermissionGranted = service.notificationGranted,
            batteryPermissionGranted = service.batteryAllowed,
            voiceEnabled = service.voiceEnabled,
            latestTransaction = latestTx,
            todayTransactionCount = announced,
            missedTodayCount = missed
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShoutPayUiState())

    /** The big START/STOP control — delegates to the ServiceController. */
    fun toggleService() = serviceController.toggle()

    fun setMobileNumber(number: String) = viewModelScope.launch {
        settingsRepository.setMobileNumber(number)
    }

    fun refreshPermissionStatus() {
        _notificationGranted.value = NotificationAccessHelper.isGranted(context)
        _batteryIgnored.value = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
    }
}

/** Internal combined service + permission state. */
private data class ServiceState(
    val isRunning: Boolean = false,
    val notificationGranted: Boolean = false,
    val batteryAllowed: Boolean = false,
    val voiceEnabled: Boolean = true
)

/** Complete UI state for the Home screen (app_design spec). */
data class ShoutPayUiState(
    val isServiceRunning: Boolean = false,
    val notificationPermissionGranted: Boolean = false,
    val batteryPermissionGranted: Boolean = false,
    val voiceEnabled: Boolean = true,
    val latestTransaction: Transaction? = null,
    val todayTransactionCount: Int = 0,
    val missedTodayCount: Int = 0
)
