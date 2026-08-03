package com.upivoicealert.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.upivoicealert.domain.model.Transaction
import com.upivoicealert.domain.usecases.GetTransactionHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class HistoryViewModel @Inject constructor(
    getTransactionHistoryUseCase: GetTransactionHistoryUseCase
) : ViewModel() {

    val transactions: StateFlow<List<Transaction>> = getTransactionHistoryUseCase.receivedSuccess()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}