package com.upivoicealert.data.security

import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Retrofit interface for device management.
 * All requests use existing authenticated Retrofit client (AuthInterceptor + AuthAuthenticator).
 */
interface DeviceApi {

    @GET("auth/devices")
    suspend fun getDevices(): DevicesResponseDto

    @DELETE("auth/devices/{deviceId}")
    suspend fun logoutDevice(@Path("deviceId") deviceId: String)

    @DELETE("auth/devices/logout-others")
    suspend fun logoutOtherDevices()
}
