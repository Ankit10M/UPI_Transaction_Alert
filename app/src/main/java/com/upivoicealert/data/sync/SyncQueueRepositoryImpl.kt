package com.upivoicealert.data.sync

import com.upivoicealert.domain.sync.RetrySyncItemResult
import com.upivoicealert.domain.sync.StaleUploadRecoveryResult
import com.upivoicealert.domain.sync.SyncFailure
import com.upivoicealert.domain.sync.SyncItem
import com.upivoicealert.domain.sync.SyncQueueRepository
import com.upivoicealert.domain.sync.SyncStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Data implementation of SyncQueueRepository. Delegates to DAO, maps Entity <-> Domain.
 * Phase 7.2 adds controlled retry with concurrency safety (WHERE status=FAILED).
 */
@Singleton
class SyncQueueRepositoryImpl @Inject constructor(
    private val dao: SyncQueueDao
) : SyncQueueRepository {

    override suspend fun addQueueItem(entityType: String, entityId: String): Long {
        val now = System.currentTimeMillis()
        val entity = SyncQueueEntity(
            entityType = entityType,
            entityId = entityId,
            status = SyncQueueEntity.STATUS_PENDING,
            retryCount = 0,
            createdAt = now,
            updatedAt = now
        )
        return dao.insert(entity)
    }

    override suspend fun addSyncItem(item: SyncItem): Long {
        val entity = SyncQueueEntity(
            id = item.id,
            entityType = item.entityType,
            entityId = item.entityId,
            status = item.status.name,
            retryCount = item.retryCount,
            createdAt = item.createdAt,
            updatedAt = item.updatedAt,
            lastErrorCode = item.lastErrorCode,
            lastErrorMessage = item.lastErrorMessage,
            failedAt = item.failedAt
        )
        return dao.insert(entity)
    }

    override suspend fun getPendingItems(): List<SyncItem> {
        return dao.getPendingItems().map { it.toDomain() }
    }

    override suspend fun updateStatus(id: Long, status: SyncStatus) {
        dao.updateStatus(id, status.name, System.currentTimeMillis())
    }

    override suspend fun incrementRetryCount(id: Long) {
        dao.incrementRetryCount(id, System.currentTimeMillis())
    }

    override suspend fun getAll(): List<SyncItem> {
        return dao.getAll().map { it.toDomain() }
    }

    override suspend fun clearAll() {
        dao.clearAll()
    }

    override suspend fun getFailedItems(): List<SyncFailure> {
        return dao.getFailedItems().map { it.toFailure() }
    }

    override suspend fun getFailedItem(id: Long): SyncFailure? {
        return dao.getById(id)?.takeIf { it.status == SyncQueueEntity.STATUS_FAILED }?.toFailure()
    }

    override fun observeFailedItems(): Flow<List<SyncFailure>> {
        return dao.observeFailedItems().map { list -> list.map { it.toFailure() } }
    }

    override suspend fun retryFailedItem(id: Long): RetrySyncItemResult {
        val existing = dao.getById(id) ?: return RetrySyncItemResult.NotFound
        if (existing.status != SyncQueueEntity.STATUS_FAILED) {
            return RetrySyncItemResult.InvalidState
        }
        val updated = dao.retryFailedItem(id, System.currentTimeMillis())
        return if (updated == 1) RetrySyncItemResult.Success else RetrySyncItemResult.InvalidState
    }

    override suspend fun retryAllFailed(): Int {
        return dao.retryAllFailed(System.currentTimeMillis())
    }

    override suspend fun recoverStaleUploadingItems(): StaleUploadRecoveryResult {
        val cutoff = System.currentTimeMillis() - STALE_UPLOADING_THRESHOLD_MS
        val recovered = dao.recoverStaleUploading(cutoff, System.currentTimeMillis())
        return StaleUploadRecoveryResult(recovered)
    }

    override suspend fun getStaleUploadingItems(cutoffTime: Long): List<SyncItem> {
        return dao.getStaleUploadingItems(cutoffTime).map { it.toDomain() }
    }

    companion object {
        const val STALE_UPLOADING_THRESHOLD_MS: Long = 15 * 60 * 1000L // 15 minutes
    }

    private fun SyncQueueEntity.toDomain(): SyncItem = SyncItem(
        id = id,
        entityType = entityType,
        entityId = entityId,
        status = SyncStatus.fromString(status),
        retryCount = retryCount,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastErrorCode = lastErrorCode,
        lastErrorMessage = lastErrorMessage,
        failedAt = failedAt
    )

    private fun SyncQueueEntity.toFailure(): SyncFailure = SyncFailure(
        queueId = id,
        entityType = entityType,
        entityId = entityId,
        retryCount = retryCount,
        errorCode = lastErrorCode,
        errorMessage = lastErrorMessage,
        failedAt = failedAt
    )
}
