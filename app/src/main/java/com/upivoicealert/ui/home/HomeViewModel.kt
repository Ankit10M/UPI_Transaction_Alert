package com.upivoicealert.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.upivoicealert.domain.model.BusinessSummary
import com.upivoicealert.domain.model.MerchantUser
import com.upivoicealert.domain.model.Transaction
import com.upivoicealert.domain.repository.SettingsRepository
import com.upivoicealert.domain.repository.TransactionRepository
import com.upivoicealert.domain.repository.UserRepository
import com.upivoicealert.domain.usecases.BusinessSummaryUseCase
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
 *
 * Phase 3: voice toggle is independent of the service running state. The
 * START/STOP button controls voice announcements only — transactions are
 * always recorded.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serviceController: ServiceController,
    private val settingsRepository: SettingsRepository,
    transactionRepository: TransactionRepository,
    businessSummaryUseCase: BusinessSummaryUseCase,
    userRepository: UserRepository
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

    /** Merchant profile (name, shop name). */
    private val merchantUser: StateFlow<MerchantUser?> = userRepository.observeUser()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Today's business summary (total collection, transaction count). */
    private val businessSummary: StateFlow<BusinessSummary> = businessSummaryUseCase.observeTodaySummary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BusinessSummary())

    /** Voice-only toggle state (independent of service running). */
    private val voiceEnabled: StateFlow<Boolean> = settingsRepository.voiceEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

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

    /** Single immutable UI state for the Home screen (Phase 3 spec). */
    val uiState: StateFlow<ShoutPayUiState> = combine(
        serviceState,
        latest,
        announcedToday,
        missedToday,
        merchantUser
    ) { service, latestTx, announced, missed, merchant ->
        PartialHomeState(service, latestTx, announced, missed, merchant)
    }.combine(businessSummary) { partial, summary ->
        partial.copy(businessSummary = summary)
    }.combine(voiceEnabled) { partial, voice ->
        ShoutPayUiState(
            isServiceRunning = partial.service.isRunning,
            notificationPermissionGranted = partial.service.notificationGranted,
            batteryPermissionGranted = partial.service.batteryAllowed,
            voiceEnabled = voice,
            latestTransaction = partial.latestTx,
            todayTransactionCount = partial.announced,
            missedTodayCount = partial.missed,
            merchantName = partial.merchant?.name.orEmpty(),
            shopName = partial.merchant?.shopName.orEmpty(),
            todayCollection = partial.businessSummary.totalCollection
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShoutPayUiState())

    /** The big START/STOP control — delegates to the ServiceController. */
    fun toggleService() = serviceController.toggle()

    /** Voice-only toggle — controls announcements only, not transaction recording. */
    fun toggleVoice() = serviceController.toggleVoice()

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

/** Intermediate state for chaining combine() beyond 5 parameters. */
private data class PartialHomeState(
    val service: ServiceState,
    val latestTx: Transaction?,
    val announced: Int,
    val missed: Int,
    val merchant: MerchantUser?,
    val businessSummary: BusinessSummary = BusinessSummary()
)

/** Complete UI state for the Home screen (Phase 3 spec). */
data class ShoutPayUiState(
    val isServiceRunning: Boolean = false,
    val notificationPermissionGranted: Boolean = false,
    val batteryPermissionGranted: Boolean = false,
    val voiceEnabled: Boolean = true,
    val latestTransaction: Transaction? = null,
    val todayTransactionCount: Int = 0,
    val missedTodayCount: Int = 0,
    val merchantName: String = "",
    val shopName: String = "",
    val todayCollection: Double = 0.0
)
