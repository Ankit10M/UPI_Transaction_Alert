package com.upivoicealert.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.upivoicealert.data.datastore.ReconciliationStatusStore
import com.upivoicealert.scheduler.ReconciliationScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReconciliationUiState(
    val lastCheckedAt: Long? = null,
    val lastRepairedCount: Int = 0,
    val isChecking: Boolean = false
)

@HiltViewModel
class ReconciliationViewModel @Inject constructor(
    private val reconciliationStatusStore: ReconciliationStatusStore,
    private val reconciliationScheduler: ReconciliationScheduler
) : ViewModel() {

    val uiState: StateFlow<ReconciliationUiState> = combine(
        reconciliationStatusStore.lastCheckedAt,
        reconciliationStatusStore.lastRepairedCount
    ) { checkedAt, repaired ->
        ReconciliationUiState(
            lastCheckedAt = checkedAt,
            lastRepairedCount = repaired
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReconciliationUiState())

    fun checkNow() {
        viewModelScope.launch {
            reconciliationScheduler.scheduleNow()
        }
    }
}
