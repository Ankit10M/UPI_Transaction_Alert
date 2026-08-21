package com.upivoicealert.data.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthSessionStore @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = EncryptedSharedPreferences.create(
        context, "shoutpay_auth_session",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var accessToken: String? get() = preferences.getString("access_token", null); private set(value) = preferences.edit().putString("access_token", value).apply()
    var refreshToken: String? get() = preferences.getString("refresh_token", null); private set(value) = preferences.edit().putString("refresh_token", value).apply()
    var merchantId: String? get() = preferences.getString("merchant_id", null); private set(value) = preferences.edit().putString("merchant_id", value).apply()
    var firebaseUid: String? get() = preferences.getString("firebase_uid", null); private set(value) = preferences.edit().putString("firebase_uid", value).apply()
    var authState: AuthState get() = runCatching { AuthState.valueOf(preferences.getString("auth_state", AuthState.UNAUTHENTICATED.name)!!) }.getOrDefault(AuthState.UNAUTHENTICATED); set(value) = preferences.edit().putString("auth_state", value.name).apply()

    fun save(accessToken: String, refreshToken: String, merchantId: String, firebaseUid: String) {
        this.accessToken = accessToken; this.refreshToken = refreshToken; this.merchantId = merchantId; this.firebaseUid = firebaseUid; this.authState = AuthState.AUTHENTICATED
    }
    fun clear() = preferences.edit().clear().putString("auth_state", AuthState.LOGGED_OUT.name).apply()
}
