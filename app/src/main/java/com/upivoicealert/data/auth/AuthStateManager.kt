package com.upivoicealert.data.auth

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class AuthState { UNAUTHENTICATED, AUTHENTICATING, AUTHENTICATED, OFFLINE_AUTHENTICATED, SESSION_EXPIRED, LOGGED_OUT }

@Singleton
class AuthStateManager @Inject constructor(private val sessionStore: AuthSessionStore) {
    private val mutableState = MutableStateFlow(sessionStore.authState)
    val state: StateFlow<AuthState> = mutableState
    fun update(state: AuthState) { mutableState.value = state; sessionStore.authState = state }
}
