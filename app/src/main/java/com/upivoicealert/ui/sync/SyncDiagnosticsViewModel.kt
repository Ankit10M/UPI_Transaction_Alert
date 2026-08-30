package com.upivoicealert.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.upivoicealert.domain.sync.SyncDiagnosticEvent
import com.upivoicealert.domain.sync.SyncDiagnosticRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SyncDiagnosticsUiState {
    data object Loading : SyncDiagnosticsUiState
    data object Empty : SyncDiagnosticsUiState
    data class Success(val events: List<SyncDiagnosticEvent>) : SyncDiagnosticsUiState
    data class Error(val message: String) : SyncDiagnosticsUiState
}

@HiltViewModel
class SyncDiagnosticsViewModel @Inject constructor(
    private val repository: SyncDiagnosticRepository
) : ViewModel() {

    val uiState: StateFlow<SyncDiagnosticsUiState> = repository.observeRecent(100)
        .map { list ->
            if (list.isEmpty()) SyncDiagnosticsUiState.Empty
            else SyncDiagnosticsUiState.Success(list)
        }
        .catch { e -> emit(SyncDiagnosticsUiState.Error(e.message ?: "Unable to load sync activity")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncDiagnosticsUiState.Loading)

    private val _clearConfirm = MutableStateFlow(false)
    val clearConfirm: StateFlow<Boolean> = _clearConfirm

    fun requestClear() { _clearConfirm.value = true }
    fun cancelClear() { _clearConfirm.value = false }

    fun confirmClear() {
        viewModelScope.launch {
            repository.clearAll()
            _clearConfirm.value = false
        }
    }
}
