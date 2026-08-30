package com.upivoicealert.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.upivoicealert.domain.sync.CloudSyncStatus
import com.upivoicealert.domain.sync.SyncStatusRepository
import com.upivoicealert.scheduler.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the CloudSyncStatusCard.
 * Exposes reactive [CloudSyncStatus] from [SyncStatusRepository] and provides a
 * [syncNow] action that delegates to [SyncScheduler].
 */
@HiltViewModel
class SyncStatusViewModel @Inject constructor(
    syncStatusRepository: SyncStatusRepository,
    private val syncScheduler: SyncScheduler
) : ViewModel() {

    /** Merchant-facing sync health — auto-updates when queue/network/DataStore change. */
    val syncStatus: StateFlow<CloudSyncStatus> = syncStatusRepository.observeSyncStatus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CloudSyncStatus.INITIAL)

    /**
     * Request an immediate sync. Delegates to [SyncScheduler] which uses
     * WorkManager with ExistingWorkPolicy.KEEP — no duplicate work created.
     */
    fun syncNow() {
        viewModelScope.launch {
            syncScheduler.scheduleSync()
        }
    }
}
