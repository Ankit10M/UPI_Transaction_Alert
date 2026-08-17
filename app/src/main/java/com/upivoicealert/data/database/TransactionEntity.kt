package com.upivoicealert.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [
        // Cross-source dedup support (schema v4): the reference-ID and fingerprint
        // lookups are indexed so isDuplicate() stays a fast point lookup even as
        // history grows. NOT unique on purpose — two legitimate same-fingerprint
        // payments must remain insertable outside the dedup time window.
        Index(value = ["transactionId"]),
        Index(value = ["dedupFingerprint"])
    ]
)
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
    val voiceAnnounced: Boolean = false,
    // Cross-source dedup fingerprint (schema v4): amount + normalized sender +
    // transaction type, written at insert time by TransactionFingerprint. NULL
    // for legacy rows migrated before v4 — those rely on reference-ID / exact-text
    // matching only. Added via MIGRATION_3_4.
    val dedupFingerprint: String? = null
)
