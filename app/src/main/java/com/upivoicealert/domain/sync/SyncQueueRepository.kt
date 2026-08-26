package com.upivoicealert.domain.sync

/**
 * Domain repository for sync queue. No business logic related to transactions.
 */
interface SyncQueueRepository {
    suspend fun addQueueItem(entityType: String, entityId: String): Long
    suspend fun addSyncItem(item: SyncItem): Long
    suspend fun getPendingItems(): List<SyncItem>
    suspend fun updateStatus(id: Long, status: SyncStatus)
    suspend fun incrementRetryCount(id: Long)
    suspend fun getAll(): List<SyncItem>
    suspend fun clearAll()
}
