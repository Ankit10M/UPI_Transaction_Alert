package com.upivoicealert.data.sync

import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit API for transaction cloud sync. Uses existing authenticated client (AuthInterceptor).
 * POST /api/transactions/sync is idempotent via transactionUuid.
 */
interface TransactionSyncApi {

    @POST("transactions/sync")
    suspend fun syncTransactions(@Body request: TransactionSyncRequestDto): TransactionSyncResponseDto
}

/**
 * Single transaction upload DTO — only fields required by backend.
 * Never send merchantId, firebaseUid, status, currency, createdAt, id.
 */
data class TransactionUploadDto(
    val transactionUuid: String,
    val deviceId: String,
    val amount: Double,
    val senderName: String,
    val senderVpa: String? = null,
    val upiReference: String? = null,
    val upiApp: String? = null,
    val transactionTime: String
)

data class TransactionSyncRequestDto(
    val transactions: List<TransactionUploadDto>
)

data class TransactionSyncResponseDto(
    val created: List<String> = emptyList(),
    val duplicates: List<String> = emptyList()
)
