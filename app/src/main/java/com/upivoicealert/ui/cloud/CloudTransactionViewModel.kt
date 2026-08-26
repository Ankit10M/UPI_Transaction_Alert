package com.upivoicealert.ui.cloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.upivoicealert.data.cloudtransaction.CloudTransactionRepository
import com.upivoicealert.domain.cloudtransaction.CloudSummary
import com.upivoicealert.domain.cloudtransaction.CloudTransaction
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class CloudTransactionViewModel @Inject constructor(
    private val repository: CloudTransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<CloudUiState>(CloudUiState.Loading)
    val uiState: StateFlow<CloudUiState> = _uiState

    private val _summaryState = MutableStateFlow<SummaryUiState>(SummaryUiState.Loading)
    val summaryState: StateFlow<SummaryUiState> = _summaryState

    private var currentPage = 1
    private val pageLimit = 20

    fun loadSummary() {
        viewModelScope.launch {
            _summaryState.value = SummaryUiState.Loading
            when (val result = repository.getSummary()) {
                is CloudTransactionRepository.CloudResult.Success -> _summaryState.value = SummaryUiState.Success(result.data)
                is CloudTransactionRepository.CloudResult.Error -> _summaryState.value = SummaryUiState.Error(result.message)
                is CloudTransactionRepository.CloudResult.SessionExpired -> _summaryState.value = SummaryUiState.SessionExpired
            }
        }
    }

    fun loadTransactions(page: Int = 1, startDate: String? = null, endDate: String? = null) {
        viewModelScope.launch {
            _uiState.value = CloudUiState.Loading
            when (val result = repository.getTransactions(page, pageLimit, startDate, endDate)) {
                is CloudTransactionRepository.CloudResult.Success -> {
                    val (transactions, pagination) = result.data
                    currentPage = page
                    if (transactions.isEmpty()) _uiState.value = CloudUiState.Empty
                    else _uiState.value = CloudUiState.Success(transactions, pagination)
                }
                is CloudTransactionRepository.CloudResult.Error -> _uiState.value = CloudUiState.Error(result.message)
                is CloudTransactionRepository.CloudResult.SessionExpired -> _uiState.value = CloudUiState.SessionExpired
            }
        }
    }

    fun loadNextPage() {
        val current = _uiState.value
        if (current is CloudUiState.Success) {
            val nextPage = current.pagination.page + 1
            if (nextPage <= current.pagination.totalPages) {
                loadTransactions(nextPage)
            }
        }
    }

    fun retry() = loadTransactions(currentPage)
}

sealed interface CloudUiState {
    data object Loading : CloudUiState
    data class Success(val transactions: List<CloudTransaction>, val pagination: com.upivoicealert.domain.cloudtransaction.CloudPagination) : CloudUiState
    data object Empty : CloudUiState
    data class Error(val message: String) : CloudUiState
    data object SessionExpired : CloudUiState
}

sealed interface SummaryUiState {
    data object Loading : SummaryUiState
    data class Success(val summary: CloudSummary) : SummaryUiState
    data class Error(val message: String) : SummaryUiState
    data object SessionExpired : SummaryUiState
}
