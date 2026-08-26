package com.upivoicealert.data.cloudtransaction

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit API for cloud transactions. Uses existing authenticated client (AuthInterceptor).
 * Endpoints are READ-ONLY, merchant isolated via JWT.sub.
 */
interface CloudTransactionApi {

    @GET("transactions")
    suspend fun getTransactions(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null
    ): CloudTransactionsResponseDto

    @GET("transactions/summary")
    suspend fun getSummary(): CloudSummaryDto
}
