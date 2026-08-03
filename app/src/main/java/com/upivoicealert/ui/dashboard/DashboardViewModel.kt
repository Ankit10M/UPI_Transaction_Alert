package com.upivoicealert.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.upivoicealert.domain.model.Transaction
import com.upivoicealert.domain.repository.TransactionRepository
import com.upivoicealert.utils.DateTimeUtils
import com.upivoicealert.utils.NotificationAccessHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    transactionRepository: TransactionRepository
) : ViewModel() {

    private val startOfToday = DateTimeUtils.startOfToday()

    val todayCount: StateFlow<Int> = transactionRepository.observeCountSince(startOfToday)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val allTimeCount: StateFlow<Int> = transactionRepository.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val latest: StateFlow<Transaction?> = transactionRepository.observeLatest()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _listenerActive = MutableStateFlow(NotificationAccessHelper.isGranted(context))
    val listenerActive: StateFlow<Boolean> = _listenerActive

    fun refreshListenerStatus() {
        _listenerActive.value = NotificationAccessHelper.isGranted(context)
    }
}