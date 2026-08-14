package com.upivoicealert.ui.business

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.upivoicealert.domain.model.BusinessSummary
import com.upivoicealert.domain.usecases.BusinessSummaryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * State holder for the business summary screen (Feature 2). The summary is
 * computed by [BusinessSummaryUseCase] from the real Room transaction history.
 */
@HiltViewModel
class BusinessViewModel @Inject constructor(
    businessSummaryUseCase: BusinessSummaryUseCase
) : ViewModel() {

    val summary: StateFlow<BusinessSummary> = businessSummaryUseCase.observeTodaySummary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BusinessSummary())
}
