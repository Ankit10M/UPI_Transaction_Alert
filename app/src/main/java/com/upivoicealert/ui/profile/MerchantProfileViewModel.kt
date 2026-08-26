package com.upivoicealert.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.upivoicealert.data.profile.MerchantProfileRepository
import com.upivoicealert.data.profile.MerchantProfileResult
import com.upivoicealert.domain.profile.MerchantProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for merchant profile sync. Backend is source of truth.
 *
 * States: Loading, Success(profile), Offline(profile), Error(message), SessionExpired
 * Actions: loadProfile(), updateProfile(ownerName, shopName)
 */
@HiltViewModel
class MerchantProfileViewModel @Inject constructor(
    private val repository: MerchantProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<MerchantProfileUiState>(MerchantProfileUiState.Loading)
    val uiState: StateFlow<MerchantProfileUiState> = _uiState

    fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = MerchantProfileUiState.Loading
            when (val result = repository.fetchProfileWithCacheFallback()) {
                is MerchantProfileResult.Success -> _uiState.value = MerchantProfileUiState.Success(result.profile)
                is MerchantProfileResult.Offline -> _uiState.value = MerchantProfileUiState.Offline(result.profile)
                is MerchantProfileResult.Error -> {
                    // If cached available, prefer Offline, else Error
                    val cached = repository.getCachedProfile()
                    if (cached != null && result.message.contains("Network", ignoreCase = true)) {
                        _uiState.value = MerchantProfileUiState.Offline(cached)
                    } else {
                        _uiState.value = MerchantProfileUiState.Error(result.message)
                    }
                }
                is MerchantProfileResult.SessionExpired -> _uiState.value = MerchantProfileUiState.SessionExpired
            }
        }
    }

    fun updateProfile(ownerName: String?, shopName: String?) {
        viewModelScope.launch {
            _uiState.value = MerchantProfileUiState.Loading
            when (val result = repository.updateProfileWithResult(ownerName, shopName)) {
                is MerchantProfileResult.Success -> _uiState.value = MerchantProfileUiState.Success(result.profile)
                is MerchantProfileResult.Offline -> _uiState.value = MerchantProfileUiState.Offline(result.profile)
                is MerchantProfileResult.Error -> _uiState.value = MerchantProfileUiState.Error(result.message)
                is MerchantProfileResult.SessionExpired -> _uiState.value = MerchantProfileUiState.SessionExpired
            }
        }
    }
}

sealed interface MerchantProfileUiState {
    data object Loading : MerchantProfileUiState
    data class Success(val profile: MerchantProfile) : MerchantProfileUiState
    data class Offline(val profile: MerchantProfile) : MerchantProfileUiState
    data class Error(val message: String) : MerchantProfileUiState
    data object SessionExpired : MerchantProfileUiState
}
