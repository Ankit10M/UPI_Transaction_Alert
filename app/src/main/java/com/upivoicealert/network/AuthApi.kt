package com.upivoicealert.network

import retrofit2.http.Body
import retrofit2.http.POST

data class LoginRequest(val firebaseIdToken: String, val deviceId: String, val deviceName: String?)
data class RefreshRequest(val refreshToken: String)
data class MerchantDto(val merchantId: String)
data class LoginResponse(val accessToken: String, val refreshToken: String, val merchant: MerchantDto)

interface AuthApi {
    @POST("auth/login") suspend fun login(@Body request: LoginRequest): LoginResponse
    @POST("auth/refresh") suspend fun refresh(@Body request: RefreshRequest): LoginResponse
    @POST("auth/logout") suspend fun logout()
}
