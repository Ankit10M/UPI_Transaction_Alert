package com.upivoicealert.domain.cloudtransaction

/**
 * Domain model for cloud transaction. DTO never reaches UI.
 */
data class CloudTransaction(
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

data class CloudPagination(
    val page: Int,
    val limit: Int,
    val total: Int,
    val totalPages: Int
)

data class CloudSummary(
    val todayTotal: Double,
    val todayCount: Int,
    val averageTransaction: Double,
    val largestTransaction: Double
)
