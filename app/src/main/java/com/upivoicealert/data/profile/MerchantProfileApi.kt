package com.upivoicealert.data.profile

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH

/**
 * Retrofit interface for merchant profile sync. Backend is source of truth.
 * Endpoints require JWT (added via AuthInterceptor).
 */
interface MerchantProfileApi {

    @GET("merchant/profile")
    suspend fun getProfile(): MerchantProfileResponseDto

    @PATCH("merchant/profile")
    suspend fun updateProfile(@Body request: UpdateMerchantProfileRequest): MerchantProfileResponseDto
}

/**
 * PATCH body — only ownerName and shopName are editable.
 * Client must never send merchantId, firebaseUid, phoneNumber, createdAt.
 */
data class UpdateMerchantProfileRequest(
    val ownerName: String?,
    val shopName: String?
)
