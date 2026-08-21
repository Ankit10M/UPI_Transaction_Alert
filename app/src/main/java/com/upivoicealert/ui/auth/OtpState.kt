package com.upivoicealert.ui.auth

sealed interface OtpState {
    data object UNAUTHENTICATED : OtpState
    data object AUTHENTICATING : OtpState
    data class OTP_SENT(val verificationId: String) : OtpState
    data object VERIFYING : OtpState
    data object AUTHENTICATED : OtpState
    data class FAILED(val message: String) : OtpState
}
