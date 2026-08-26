package com.upivoicealert.data.cloudtransaction

/**
 * DTO for cloud transaction (backend is source of truth, never expose internal fields).
 * Matches backend GET /api/transactions response public fields.
 */
data class CloudTransactionDto(
    val transactionUuid: String,
    val amount: Double,
    val currency: String,
    val senderName: String,
    val senderVpa: String?,
    val upiReference: String?,
    val upiApp: String?,
    val transactionTime: String,
    val status: String
)

data class CloudTransactionsResponseDto(
    val transactions: List<CloudTransactionDto>,
    val pagination: PaginationDto
)

data class PaginationDto(
    val page: Int,
    val limit: Int,
    val total: Int,
    val totalPages: Int
)

data class CloudSummaryDto(
    val summary: SummaryDto
)

data class SummaryDto(
    val todayTotal: Double,
    val todayCount: Int,
    val averageTransaction: Double,
    val largestTransaction: Double
)
