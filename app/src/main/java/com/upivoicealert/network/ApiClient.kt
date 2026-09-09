package com.upivoicealert.network

import retrofit2.Retrofit

/**
 * Legacy wrapper kept for test compatibility. Production code now obtains
 * APIs from the centrally-configured [retrofit2.Retrofit] instance in
 * [com.upivoicealert.di.AuthModule] which uses [com.upivoicealert.config.EnvironmentProvider].
 */
object ApiClient {
    fun create(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Deprecated("Use Retrofit instance from AuthModule instead — baseUrl is already configured")
    fun create(builder: Retrofit.Builder): AuthApi = builder.build().create(AuthApi::class.java)
}
