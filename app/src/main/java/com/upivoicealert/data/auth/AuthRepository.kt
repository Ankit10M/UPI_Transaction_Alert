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
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import retrofit2.HttpException

@Singleton
class AuthRepository @Inject constructor(private val firebaseAuth: FirebaseAuth, private val authApi: AuthApi, private val deviceIdGenerator: DeviceIdGenerator, private val sessionStore: AuthSessionStore, private val stateManager: AuthStateManager) {
    private val refreshMutex = Mutex()
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
    suspend fun refresh(): Boolean = refreshMutex.withLock {
        val token = sessionStore.refreshToken ?: return false
        try {
            val response = authApi.refresh(RefreshRequest(token))
            sessionStore.save(response.accessToken, response.refreshToken, response.merchant.merchantId, sessionStore.firebaseUid ?: "")
            stateManager.update(AuthState.AUTHENTICATED)
            true
        } catch (e: IOException) {
            // Transient network failure — do NOT clear session; allow retry later
            false
        } catch (e: HttpException) {
            // 401 invalid/revoked/replay, 400 malformed, 429 rate-limited handled as non-clearing for 429
            if (e.code() == 429) {
                false
            } else {
                stateManager.update(AuthState.SESSION_EXPIRED)
                sessionStore.clear()
                false
            }
        } catch (e: Exception) {
            // Unknown — treat as auth failure only if not network-related; sanitize
            stateManager.update(AuthState.SESSION_EXPIRED)
            sessionStore.clear()
            false
        }
    }
    suspend fun logout() { runCatching { authApi.logout() }; sessionStore.clear(); stateManager.update(AuthState.LOGGED_OUT) }
}
