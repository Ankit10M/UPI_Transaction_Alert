package com.upivoicealert.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.upivoicealert.data.datastore.SyncIntegrityStatusStore
import com.upivoicealert.domain.sync.SyncIntegrityStatus
import com.upivoicealert.scheduler.SyncIntegrityAuditScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SyncIntegrityUiState {
    data object Loading : SyncIntegrityUiState
    data object NeverChecked : SyncIntegrityUiState
    data class Healthy(val status: SyncIntegrityStatus) : SyncIntegrityUiState
    data class IssuesDetected(val status: SyncIntegrityStatus) : SyncIntegrityUiState
    data class Error(val message: String, val previousStatus: SyncIntegrityStatus?) : SyncIntegrityUiState
}

@HiltViewModel
class SyncIntegrityViewModel @Inject constructor(
    private val statusStore: SyncIntegrityStatusStore,
    private val scheduler: SyncIntegrityAuditScheduler
) : ViewModel() {

    private val isChecking = MutableStateFlow(false)

    val uiState: StateFlow<SyncIntegrityUiState> = combine(
        statusStore.status,
        isChecking
    ) { status, checking ->
        when {
            status.lastAuditAt == null -> SyncIntegrityUiState.NeverChecked
            status.isHealthy == true -> SyncIntegrityUiState.Healthy(status)
            status.isHealthy == false -> SyncIntegrityUiState.IssuesDetected(status)
            else -> SyncIntegrityUiState.NeverChecked
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncIntegrityUiState.Loading)

    // Expose raw status for card convenience
    val status: StateFlow<SyncIntegrityStatus> = statusStore.status
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncIntegrityStatus.NEVER_CHECKED)

    val checking: StateFlow<Boolean> = isChecking

    fun checkNow() {
        if (isChecking.value) return
        viewModelScope.launch {
            try {
                isChecking.value = true
                scheduler.scheduleNow()
            } finally {
                // Reset checking after a short delay; WorkManager will update DataStore when done.
                // Use immediate reset since scheduleNow is synchronous enqueue; UI shows "Checking..."
                // until DataStore updates or manual timeout.
                // Keep checking true for 1.5s to give UX feedback even if work hasn't persisted yet.
                kotlinx.coroutines.delay(1500)
                isChecking.value = false
            }
        }
    }
}
