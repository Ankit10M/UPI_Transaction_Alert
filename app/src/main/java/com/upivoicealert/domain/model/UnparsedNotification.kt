package com.upivoicealert.domain.model

data class UnparsedNotification(
    val id: String,
    val packageName: String,
    val rawNotification: String,
    val failureReason: String,
    val createdAt: Long
)