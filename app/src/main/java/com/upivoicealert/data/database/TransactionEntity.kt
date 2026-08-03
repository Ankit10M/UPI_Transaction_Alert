package com.upivoicealert.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val amount: Double,
    val sender: String,
    val upiApp: String,
    val transactionType: String,
    val status: String,
    val transactionId: String?,
    val rawNotification: String,
    val parserVersion: String,
    val parseStatus: String,
    val createdAt: Long
)