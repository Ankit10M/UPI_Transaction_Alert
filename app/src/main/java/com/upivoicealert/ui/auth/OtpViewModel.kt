package com.upivoicealert.ui.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import com.upivoicealert.data.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class OtpViewModel @Inject constructor(private val repository: AuthRepository) : ViewModel() {
    private val mutableState = MutableStateFlow<OtpState>(OtpState.UNAUTHENTICATED)
    val state: StateFlow<OtpState> = mutableState
    private var phoneNumber: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) { resendToken = token; mutableState.value = OtpState.OTP_SENT(id) }
        override fun onVerificationCompleted(credential: PhoneAuthCredential) { viewModelScope.launch { authenticate(credential) } }
        override fun onVerificationFailed(exception: FirebaseException) { mutableState.value = OtpState.FAILED("Verification failed") }
        override fun onCodeAutoRetrievalTimeOut(id: String) { mutableState.value = OtpState.OTP_SENT(id) }
    }
    fun sendOtp(phone: String, activity: Activity) { phoneNumber = phone; mutableState.value = OtpState.AUTHENTICATING; repository.sendOtp(phone, activity, callbacks) }
    fun resendOtp(activity: Activity) { val phone = phoneNumber; val token = resendToken; if (phone == null || token == null) mutableState.value = OtpState.FAILED("Resend unavailable") else repository.resendVerificationCode(phone, activity, token, callbacks) }
    fun verifyCode(id: String, code: String) = viewModelScope.launch { mutableState.value = OtpState.VERIFYING; runCatching { repository.verifyOtp(id, code, null) }.onSuccess { mutableState.value = OtpState.AUTHENTICATED }.onFailure { mutableState.value = OtpState.FAILED("Sign-in failed") } }
    private suspend fun authenticate(credential: PhoneAuthCredential) { mutableState.value = OtpState.VERIFYING; runCatching { repository.authenticate(credential, null) }.onSuccess { mutableState.value = OtpState.AUTHENTICATED }.onFailure { mutableState.value = OtpState.FAILED("Sign-in failed") } }
}
