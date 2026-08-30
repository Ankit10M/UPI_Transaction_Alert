package com.upivoicealert.domain.sync

import kotlinx.coroutines.flow.Flow

/**
 * Domain repository for sync queue. No business logic related to transactions.
 * Phase 7.2 adds failure inspection and controlled retry.
 */
interface SyncQueueRepository {
    suspend fun addQueueItem(entityType: String, entityId: String): Long
    suspend fun addSyncItem(item: SyncItem): Long
    suspend fun getPendingItems(): List<SyncItem>
    suspend fun updateStatus(id: Long, status: SyncStatus)
    suspend fun incrementRetryCount(id: Long)
    suspend fun getAll(): List<SyncItem>
    suspend fun clearAll()

    // Phase 7.2
    suspend fun getFailedItems(): List<SyncFailure>
    suspend fun getFailedItem(id: Long): SyncFailure?
    fun observeFailedItems(): Flow<List<SyncFailure>>
    suspend fun retryFailedItem(id: Long): RetrySyncItemResult
    suspend fun retryAllFailed(): Int

    // Phase 7.4
    suspend fun recoverStaleUploadingItems(): StaleUploadRecoveryResult = StaleUploadRecoveryResult(0)
    suspend fun getStaleUploadingItems(cutoffTime: Long): List<SyncItem> = emptyList()
}
