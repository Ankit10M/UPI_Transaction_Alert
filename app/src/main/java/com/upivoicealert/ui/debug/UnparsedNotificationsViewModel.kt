package com.upivoicealert.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.upivoicealert.domain.model.UnparsedNotification
import com.upivoicealert.domain.repository.TransactionRepository
import com.upivoicealert.domain.usecases.RetryUnparsedQueueUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class UnparsedNotificationsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val retryUnparsedQueueUseCase: RetryUnparsedQueueUseCase
) : ViewModel() {

    val items: StateFlow<List<UnparsedNotification>> = transactionRepository.observeUnparsedNotifications()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun retryAll() = viewModelScope.launch {
        retryUnparsedQueueUseCase.retryAll()
    }

    fun clearAll() = viewModelScope.launch {
        transactionRepository.clearUnparsedNotifications()
    }
}