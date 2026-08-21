package com.upivoicealert.network

import com.upivoicealert.data.auth.AuthRepository
import dagger.Lazy
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject

class AuthAuthenticator @Inject constructor(private val repository: Lazy<AuthRepository>) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.url.encodedPath.endsWith("/auth/refresh") || response.request.header("X-ShoutPay-Retry") == "1") return null
        return if (runBlocking { repository.get().refresh() }) response.request.newBuilder().header("Authorization", "Bearer ${repository.get().accessToken()}").header("X-ShoutPay-Retry", "1").build() else null
    }
}
