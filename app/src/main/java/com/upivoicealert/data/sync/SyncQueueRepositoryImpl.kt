package com.upivoicealert.data.sync

import com.upivoicealert.domain.sync.SyncItem
import com.upivoicealert.domain.sync.SyncQueueRepository
import com.upivoicealert.domain.sync.SyncStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data implementation of SyncQueueRepository. Delegates to DAO, maps Entity <-> Domain.
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
            updatedAt = item.updatedAt
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

    private fun SyncQueueEntity.toDomain(): SyncItem = SyncItem(
        id = id,
        entityType = entityType,
        entityId = entityId,
        status = SyncStatus.fromString(status),
        retryCount = retryCount,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
