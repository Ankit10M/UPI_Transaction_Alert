package com.upivoicealert.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.upivoicealert.domain.sync.RetrySyncItemResult
import com.upivoicealert.domain.sync.SyncFailure
import com.upivoicealert.domain.sync.SyncQueueRepository
import com.upivoicealert.scheduler.SyncSchedulable
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

sealed interface SyncFailuresUiState {
    data object Loading : SyncFailuresUiState
    data class Success(val failures: List<SyncFailure>) : SyncFailuresUiState
    data object Empty : SyncFailuresUiState
    data class Error(val message: String) : SyncFailuresUiState
}

sealed interface RetryUiEvent {
    data object Success : RetryUiEvent
    data object QueuedForSync : RetryUiEvent
    data class InvalidState(val message: String) : RetryUiEvent
    data class NotFound(val message: String) : RetryUiEvent
    data class Error(val message: String) : RetryUiEvent
}

@HiltViewModel
class SyncFailuresViewModel @Inject constructor(
    private val syncQueueRepository: SyncQueueRepository,
    private val syncScheduler: SyncSchedulable
) : ViewModel() {

    private val _uiState = MutableStateFlow<SyncFailuresUiState>(SyncFailuresUiState.Loading)
    val uiState: StateFlow<SyncFailuresUiState> = _uiState.asStateFlow()

    private val _retryEvent = MutableStateFlow<RetryUiEvent?>(null)
    val retryEvent: StateFlow<RetryUiEvent?> = _retryEvent.asStateFlow()

    private val _isRetrying = MutableStateFlow(false)
    val isRetrying: StateFlow<Boolean> = _isRetrying.asStateFlow()

    init {
        observeFailures()
    }

    private fun observeFailures() {
        viewModelScope.launch {
            syncQueueRepository.observeFailedItems()
                .catch { e ->
                    _uiState.value = SyncFailuresUiState.Error(e.message ?: "Failed to load")
                }
                .collect { failures ->
                    _uiState.value = when {
                        failures.isEmpty() -> SyncFailuresUiState.Empty
                        else -> SyncFailuresUiState.Success(failures)
                    }
                }
        }
        // Also one-shot load for initial state if Flow not emitting immediately
        viewModelScope.launch {
            try {
                val list = syncQueueRepository.getFailedItems()
                if (_uiState.value is SyncFailuresUiState.Loading) {
                    _uiState.value = if (list.isEmpty()) SyncFailuresUiState.Empty
                    else SyncFailuresUiState.Success(list)
                }
            } catch (e: Exception) {
                _uiState.value = SyncFailuresUiState.Error(e.message ?: "Failed to load")
            }
        }
    }

    fun retryFailedItem(queueId: Long) {
        viewModelScope.launch {
            _isRetrying.value = true
            try {
                when (val result = syncQueueRepository.retryFailedItem(queueId)) {
                    is RetrySyncItemResult.Success -> {
                        syncScheduler.scheduleSync()
                        _retryEvent.value = RetryUiEvent.QueuedForSync
                        refresh()
                    }
                    is RetrySyncItemResult.NotFound -> {
                        _retryEvent.value = RetryUiEvent.NotFound("Transaction not found")
                    }
                    is RetrySyncItemResult.InvalidState -> {
                        _retryEvent.value = RetryUiEvent.InvalidState("Transaction cannot be retried in its current state")
                    }
                }
            } catch (e: Exception) {
                _retryEvent.value = RetryUiEvent.Error(e.message ?: "Retry failed")
            } finally {
                _isRetrying.value = false
            }
        }
    }

    fun retryAllFailed() {
        viewModelScope.launch {
            _isRetrying.value = true
            try {
                val count = syncQueueRepository.retryAllFailed()
                if (count > 0) {
                    syncScheduler.scheduleSync()
                    _retryEvent.value = RetryUiEvent.QueuedForSync
                    refresh()
                } else {
                    // No eligible items — still show success but nothing to do
                    _retryEvent.value = RetryUiEvent.Success
                }
            } catch (e: Exception) {
                _retryEvent.value = RetryUiEvent.Error(e.message ?: "Retry all failed")
            } finally {
                _isRetrying.value = false
            }
        }
    }

    fun clearRetryEvent() {
        _retryEvent.value = null
    }

    private suspend fun refresh() {
        try {
            val list = syncQueueRepository.getFailedItems()
            _uiState.value = if (list.isEmpty()) SyncFailuresUiState.Empty else SyncFailuresUiState.Success(list)
        } catch (e: Exception) {
            _uiState.value = SyncFailuresUiState.Error(e.message ?: "Failed to load")
        }
    }
}
