package com.upivoicealert.data.auth

import android.app.Activity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.upivoicealert.network.AuthApi
import com.upivoicealert.network.LoginRequest
import com.upivoicealert.network.RefreshRequest
import com.upivoicealert.utils.DeviceIdGenerator
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

@Singleton
class AuthRepository @Inject constructor(private val firebaseAuth: FirebaseAuth, private val authApi: AuthApi, private val deviceIdGenerator: DeviceIdGenerator, private val sessionStore: AuthSessionStore, private val stateManager: AuthStateManager) {
    fun accessToken(): String? = sessionStore.accessToken
    fun sendOtp(phoneNumber: String, activity: Activity, callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks) {
        stateManager.update(AuthState.AUTHENTICATING)
        firebaseAuth.setLanguageCode("en")
        PhoneAuthProvider.verifyPhoneNumber(PhoneAuthOptions.newBuilder(firebaseAuth).setPhoneNumber(phoneNumber).setTimeout(60, TimeUnit.SECONDS).setActivity(activity).setCallbacks(callbacks).build())
    }

    fun resendVerificationCode(phoneNumber: String, activity: Activity, token: PhoneAuthProvider.ForceResendingToken, callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks) {
        PhoneAuthProvider.verifyPhoneNumber(PhoneAuthOptions.newBuilder(firebaseAuth).setPhoneNumber(phoneNumber).setTimeout(60, TimeUnit.SECONDS).setActivity(activity).setForceResendingToken(token).setCallbacks(callbacks).build())
    }

    suspend fun verifyOtp(verificationId: String, code: String, deviceName: String?) = authenticate(PhoneAuthProvider.getCredential(verificationId, code), deviceName)
    suspend fun authenticate(credential: PhoneAuthCredential, deviceName: String?) {
        stateManager.update(AuthState.AUTHENTICATING)
        val user = firebaseAuth.signInWithCredential(credential).await().user ?: error("Firebase user unavailable")
        val firebaseIdToken = user.getIdToken(true).await().token ?: error("Firebase ID token unavailable")
        val response = authApi.login(LoginRequest(firebaseIdToken, deviceIdGenerator.getOrCreate(), deviceName))
        sessionStore.save(response.accessToken, response.refreshToken, response.merchant.merchantId, user.uid)
        stateManager.update(AuthState.AUTHENTICATED)
    }
    suspend fun refresh(): Boolean = runCatching {
        val token = sessionStore.refreshToken ?: return false
        val response = authApi.refresh(RefreshRequest(token))
        sessionStore.save(response.accessToken, response.refreshToken, response.merchant.merchantId, sessionStore.firebaseUid ?: "")
        stateManager.update(AuthState.AUTHENTICATED); true
    }.getOrElse { stateManager.update(AuthState.SESSION_EXPIRED); sessionStore.clear(); false }
    suspend fun logout() { runCatching { authApi.logout() }; sessionStore.clear(); stateManager.update(AuthState.LOGGED_OUT) }
}
