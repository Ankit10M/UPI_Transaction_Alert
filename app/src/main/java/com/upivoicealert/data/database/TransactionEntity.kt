package com.upivoicealert.data.database

import androidx.room.ColumnInfo
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
    val createdAt: Long,
    // Multi-source metadata (schema v2). Added via MIGRATION_1_2; legacy rows
    // carry defaults (UNKNOWN / '' / NULL / backfilled from rawNotification).
    val sourceType: String,
    val packageName: String,
    val notificationKey: String?,
    val originalNotificationText: String,
    val cleanedNotificationText: String,
    // Voice status (schema v3). Set true when the TTS engine announced the
    // payment; legacy rows default to false via the v2->v3 migration.
    @ColumnInfo(defaultValue = "0")
    val voiceAnnounced: Boolean = false
)
