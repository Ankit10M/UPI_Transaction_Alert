package com.upivoicealert.data.sync

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room sync queue foundation for future Phase 6 Transaction Cloud Sync.
 * This table is only a future queue; not connected to transactions yet.
 * entityType = "TRANSACTION", status = PENDING/SYNCED/FAILED
 */
@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String,
    val entityId: String,
    val status: String,
    val retryCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long
) {
    companion object {
        const val ENTITY_TYPE_TRANSACTION = "TRANSACTION"
        const val STATUS_PENDING = "PENDING"
        const val STATUS_SYNCED = "SYNCED"
        const val STATUS_FAILED = "FAILED"
    }
}
