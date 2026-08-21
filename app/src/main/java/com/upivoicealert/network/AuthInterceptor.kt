package com.upivoicealert.network

import com.upivoicealert.data.auth.AuthSessionStore
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class AuthInterceptor @Inject constructor(private val sessionStore: AuthSessionStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = sessionStore.accessToken
        val request = chain.request().newBuilder().apply { if (token != null) header("Authorization", "Bearer $token") }.build()
        return chain.proceed(request)
    }
}
