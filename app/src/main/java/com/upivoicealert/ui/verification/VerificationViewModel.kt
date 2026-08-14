package com.upivoicealert.ui.verification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.upivoicealert.domain.model.VerificationResult
import com.upivoicealert.domain.usecases.VerifyPaymentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State holder for the payment-verification screen (Feature 1). Delegates to
 * [VerifyPaymentUseCase], which reads the real Room transaction history.
 */
@HiltViewModel
class VerificationViewModel @Inject constructor(
    private val verifyPaymentUseCase: VerifyPaymentUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(VerificationUiState())
    val uiState: StateFlow<VerificationUiState> = _uiState.asStateFlow()

    fun onAmountChange(value: String) {
        // Digits + at most one decimal separator; cap at a sane length.
        val filtered = value.filter { it.isDigit() || it == '.' }.take(10)
        _uiState.value = _uiState.value.copy(
            amountInput = filtered,
            amountError = false,
            result = null
        )
    }

    fun checkPayment() {
        val current = _uiState.value
        val amount = current.amountInput.toDoubleOrNull()
        if (amount == null || amount <= 0.0) {
            _uiState.value = current.copy(amountError = true)
            return
        }
        if (current.isChecking) return
        _uiState.value = current.copy(isChecking = true, amountError = false, result = null)
        viewModelScope.launch {
            val result = verifyPaymentUseCase(amount)
            _uiState.value = _uiState.value.copy(isChecking = false, result = result)
        }
    }

    fun resetResult() {
        _uiState.value = _uiState.value.copy(result = null)
    }
}

data class VerificationUiState(
    val amountInput: String = "",
    val isChecking: Boolean = false,
    val amountError: Boolean = false,
    val result: VerificationResult? = null
)
