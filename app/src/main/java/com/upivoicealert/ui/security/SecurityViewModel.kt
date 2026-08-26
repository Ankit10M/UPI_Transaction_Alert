package com.upivoicealert.ui.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.upivoicealert.data.security.DeviceRepository
import com.upivoicealert.domain.security.Device
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Account Security. Uses StateFlow with states:
 * Loading, Success(devices), Empty, Error(message), SessionExpired
 */
@HiltViewModel
class SecurityViewModel @Inject constructor(
    private val repository: DeviceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SecurityUiState>(SecurityUiState.Loading)
    val uiState: StateFlow<SecurityUiState> = _uiState

    fun loadDevices() {
        viewModelScope.launch {
            _uiState.value = SecurityUiState.Loading
            when (val result = repository.getDevices()) {
                is DeviceRepository.DeviceResult.Success -> {
                    if (result.data.isEmpty()) _uiState.value = SecurityUiState.Empty
                    else _uiState.value = SecurityUiState.Success(result.data)
                }
                is DeviceRepository.DeviceResult.Error -> _uiState.value = SecurityUiState.Error(result.message)
                is DeviceRepository.DeviceResult.SessionExpired -> _uiState.value = SecurityUiState.SessionExpired
            }
        }
    }

    fun logoutDevice(deviceId: String) {
        viewModelScope.launch {
            _uiState.value = SecurityUiState.Loading
            when (val result = repository.logoutDevice(deviceId)) {
                is DeviceRepository.DeviceResult.Success -> loadDevices()
                is DeviceRepository.DeviceResult.Error -> _uiState.value = SecurityUiState.Error(result.message)
                is DeviceRepository.DeviceResult.SessionExpired -> _uiState.value = SecurityUiState.SessionExpired
            }
        }
    }

    fun logoutOtherDevices() {
        viewModelScope.launch {
            _uiState.value = SecurityUiState.Loading
            when (val result = repository.logoutOtherDevices()) {
                is DeviceRepository.DeviceResult.Success -> loadDevices()
                is DeviceRepository.DeviceResult.Error -> _uiState.value = SecurityUiState.Error(result.message)
                is DeviceRepository.DeviceResult.SessionExpired -> _uiState.value = SecurityUiState.SessionExpired
            }
        }
    }
}

sealed interface SecurityUiState {
    data object Loading : SecurityUiState
    data class Success(val devices: List<Device>) : SecurityUiState
    data object Empty : SecurityUiState
    data class Error(val message: String) : SecurityUiState
    data object SessionExpired : SecurityUiState
}
