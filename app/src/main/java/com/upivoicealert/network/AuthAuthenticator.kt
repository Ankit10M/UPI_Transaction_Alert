package com.upivoicealert.network

import com.upivoicealert.data.auth.AuthRepository
import com.upivoicealert.data.auth.AuthSessionStore
import dagger.Lazy
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject

class AuthAuthenticator @Inject constructor(
    private val repository: Lazy<AuthRepository>,
    private val sessionStore: AuthSessionStore
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        // Loop protection: never retry refresh endpoint itself, and only once per request
        if (response.request.url.encodedPath.endsWith("/auth/refresh") ||
            response.request.header("X-ShoutPay-Retry") == "1"
        ) return null
        // Count prior authenticator retries for same request chain to bound loop to 1
        var priorRetryCount = 0
        var prior: Response? = response.priorResponse
        while (prior != null) {
            priorRetryCount++
            prior = prior.priorResponse
        }
        if (priorRetryCount >= 1) return null

        // Deduplicate concurrent refresh: if Authorization header already stale vs current token,
        // reuse current token without triggering another refresh
        val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")?.trim()
        val currentToken = sessionStore.accessToken
        if (currentToken != null && failedToken != null && currentToken != failedToken) {
            return response.request.newBuilder()
                .header("Authorization", "Bearer $currentToken")
                .header("X-ShoutPay-Retry", "1")
                .build()
        }

        return if (runBlocking { repository.get().refresh() }) {
            val newToken = repository.get().accessToken() ?: sessionStore.accessToken
            if (newToken == null) null else response.request.newBuilder()
                .header("Authorization", "Bearer $newToken")
                .header("X-ShoutPay-Retry", "1")
                .build()
        } else null
    }
}
