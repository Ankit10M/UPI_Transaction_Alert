package com.upivoicealert.data.sync

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room sync queue foundation for future Phase 6 Transaction Cloud Sync.
 * This table is only a future queue; not connected to transactions yet.
 * entityType = "TRANSACTION", status = PENDING/SYNCED/FAILED
 *
 * Phase 7.2: adds failure diagnostics for permanent FAILED items.
 * lastErrorCode/lastErrorMessage/failedAt are null unless status==FAILED.
 * Phase 7.3: adds unique index on (entityType, entityId) to prevent duplicate queue items.
 */
@Entity(
    tableName = "sync_queue",
    indices = [androidx.room.Index(value = ["entityType", "entityId"], unique = true)]
)
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String,
    val entityId: String,
    val status: String,
    val retryCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val lastErrorCode: String? = null,
    val lastErrorMessage: String? = null,
    val failedAt: Long? = null
) {
    companion object {
        const val ENTITY_TYPE_TRANSACTION = "TRANSACTION"
        const val STATUS_PENDING = "PENDING"
        const val STATUS_UPLOADING = "UPLOADING"
        const val STATUS_SYNCED = "SYNCED"
        const val STATUS_FAILED = "FAILED"
    }
}
