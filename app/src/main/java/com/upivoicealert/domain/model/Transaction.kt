package com.upivoicealert.domain.model

data class Transaction(
    val id: String,
    val amount: Double,
    val sender: String,
    val upiApp: String,
    val transactionType: TransactionType,
    val status: TransactionStatus,
    val transactionId: String?,
    val rawNotification: String,
    val parserVersion: String,
    val parseStatus: ParseStatus,
    val createdAt: Long
)