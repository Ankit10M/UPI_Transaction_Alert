package com.upivoicealert

import com.upivoicealert.data.sync.SyncQueueDao
import com.upivoicealert.data.sync.SyncQueueEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DAO tests using fake in-memory implementation mirroring Room behavior.
 * Verifies insert, pending read, status update, retry count.
 */
class SyncQueueDaoTest {

    private class FakeSyncQueueDao : SyncQueueDao {
        private val rows = mutableListOf<SyncQueueEntity>()
        private var nextId = 1L
        override suspend fun insert(item: SyncQueueEntity): Long {
            val id = if (item.id == 0L) nextId++ else item.id
            val copy = item.copy(id = id)
            rows.removeIf { it.id == id }
            rows.add(copy)
            return id
        }
        override suspend fun insertIgnore(item: SyncQueueEntity): Long {
            if (rows.any { it.entityType == item.entityType && it.entityId == item.entityId }) return -1L
            val id = if (item.id == 0L) nextId++ else item.id
            rows.add(item.copy(id = id))
            return id
        }
        override suspend fun getByStatus(status: String): List<SyncQueueEntity> = rows.filter { it.status == status }
        override suspend fun getPendingItems(): List<SyncQueueEntity> = rows.filter { it.status == SyncQueueEntity.STATUS_PENDING }.sortedBy { it.createdAt }
        override suspend fun updateStatus(id: Long, status: String, updatedAt: Long) {
            val idx = rows.indexOfFirst { it.id == id }
            if (idx >= 0) rows[idx] = rows[idx].copy(status = status, updatedAt = updatedAt)
        }
                override suspend fun incrementRetryCount(id: Long, updatedAt: Long) {
            val idx = rows.indexOfFirst { it.id == id }
            if (idx >= 0) rows[idx] = rows[idx].copy(retryCount = rows[idx].retryCount + 1, updatedAt = updatedAt)
        }
        override suspend fun updateStatusIfExpected(id: Long, expectedStatus: String, newStatus: String, updatedAt: Long): Int { val idx=rows.indexOfFirst{it.id==id}; if(idx>=0 && rows[idx].status==expectedStatus){ rows[idx]=rows[idx].copy(status=newStatus, updatedAt=updatedAt); return 1 }; return 0 }
        override suspend fun incrementRetryCountIfExpected(id: Long, expectedStatus: String, updatedAt: Long): Int { val idx=rows.indexOfFirst{it.id==id}; if(idx>=0 && rows[idx].status==expectedStatus){ rows[idx]=rows[idx].copy(retryCount=rows[idx].retryCount+1, updatedAt=updatedAt); return 1 }; return 0 }
        override suspend fun markFailedWithDiagnosticsIfExpected(id: Long, status: String, errorCode: String?, errorMessage: String?, failedAt: Long?, updatedAt: Long, expectedStatus: String): Int { val idx=rows.indexOfFirst{it.id==id}; if(idx>=0 && rows[idx].status==expectedStatus){ rows[idx]=rows[idx].copy(status=status, lastErrorCode=errorCode, lastErrorMessage=errorMessage, failedAt=failedAt, updatedAt=updatedAt); return 1 }; return 0 }
        override suspend fun getAll(): List<SyncQueueEntity> = rows.sortedBy { it.createdAt }
        override suspend fun deleteById(id: Long) { rows.removeIf { it.id == id } }
        override suspend fun clearAll() { rows.clear() }
        override fun observeCountByStatus(status: String): kotlinx.coroutines.flow.Flow<Int> = kotlinx.coroutines.flow.flowOf(rows.count { it.status == status })
        override suspend fun getCountByStatus(status: String): Int = rows.count { it.status == status }
        override suspend fun getFailedItems(): List<SyncQueueEntity> = rows.filter { it.status == SyncQueueEntity.STATUS_FAILED }.sortedByDescending { it.failedAt ?: 0 }
        override suspend fun getById(id: Long): SyncQueueEntity? = rows.firstOrNull { it.id == id }
        override suspend fun markFailedWithDiagnostics(id: Long, status: String, errorCode: String?, errorMessage: String?, failedAt: Long?, updatedAt: Long) {
            val idx = rows.indexOfFirst { it.id == id }
            if (idx >= 0) rows[idx] = rows[idx].copy(status = status, lastErrorCode = errorCode, lastErrorMessage = errorMessage, failedAt = failedAt, updatedAt = updatedAt)
        }
        override suspend fun retryFailedItem(id: Long, updatedAt: Long): Int {
            val idx = rows.indexOfFirst { it.id == id }
            if (idx >= 0 && rows[idx].status == SyncQueueEntity.STATUS_FAILED) {
                rows[idx] = rows[idx].copy(status = SyncQueueEntity.STATUS_PENDING, lastErrorCode = null, lastErrorMessage = null, failedAt = null, updatedAt = updatedAt)
                return 1
            }
            return 0
        }
        override suspend fun retryAllFailed(updatedAt: Long): Int {
            var count = 0
            rows.forEachIndexed { idx, entity ->
                if (entity.status == SyncQueueEntity.STATUS_FAILED) {
                    rows[idx] = entity.copy(status = SyncQueueEntity.STATUS_PENDING, lastErrorCode = null, lastErrorMessage = null, failedAt = null, updatedAt = updatedAt)
                    count++
                }
            }
            return count
        }
        override fun observeFailedItems(): kotlinx.coroutines.flow.Flow<List<SyncQueueEntity>> = kotlinx.coroutines.flow.flowOf(rows.filter { it.status == SyncQueueEntity.STATUS_FAILED })
        override suspend fun getStaleUploadingItems(cutoffTime: Long) = rows.filter { it.status == SyncQueueEntity.STATUS_UPLOADING && it.updatedAt < cutoffTime }
        override suspend fun recoverStaleUploading(cutoffTime: Long, updatedAt: Long): Int {
            var count = 0
            rows.forEachIndexed { idx, e -> if (e.status == SyncQueueEntity.STATUS_UPLOADING && e.updatedAt < cutoffTime) { rows[idx] = e.copy(status = SyncQueueEntity.STATUS_PENDING, updatedAt = updatedAt); count++ } }
            return count
        }
        override suspend fun countTransactionQueueItems(): Int = rows.count { it.entityType == SyncQueueEntity.ENTITY_TYPE_TRANSACTION }
        override suspend fun countAllQueueItems(): Int = rows.size
        override suspend fun countOrphanedQueueItems(): Int = 0
        override suspend fun countDuplicateExtraRows(): Int = 0
        override suspend fun countDuplicateGroups(): Int = 0
        override suspend fun countInvalidQueueItems(): Int = 0
        override suspend fun countStaleUploading(cutoffTime: Long): Int = rows.count { it.status == SyncQueueEntity.STATUS_UPLOADING && it.updatedAt < cutoffTime }
}

    @Test
    fun `insert queue item and read pending items`() = runTest {
        val dao = FakeSyncQueueDao()
        val now = System.currentTimeMillis()
        val entity = SyncQueueEntity(
            entityType = SyncQueueEntity.ENTITY_TYPE_TRANSACTION,
            entityId = "txn-123",
            status = SyncQueueEntity.STATUS_PENDING,
            retryCount = 0,
            createdAt = now,
            updatedAt = now
        )
        val id = dao.insert(entity)
        assertTrue(id > 0)
        val pending = dao.getPendingItems()
        assertEquals(1, pending.size)
        assertEquals("txn-123", pending[0].entityId)
        assertEquals(SyncQueueEntity.STATUS_PENDING, pending[0].status)
    }

    @Test
    fun `update status from PENDING to SYNCED`() = runTest {
        val dao = FakeSyncQueueDao()
        val now = System.currentTimeMillis()
        val id = dao.insert(
            SyncQueueEntity(
                entityType = "TRANSACTION",
                entityId = "txn-456",
                status = SyncQueueEntity.STATUS_PENDING,
                createdAt = now,
                updatedAt = now
            )
        )
        dao.updateStatus(id, SyncQueueEntity.STATUS_SYNCED, now + 1000)
        val synced = dao.getByStatus(SyncQueueEntity.STATUS_SYNCED)
        assertEquals(1, synced.size)
        assertEquals("txn-456", synced[0].entityId)
        val pending = dao.getPendingItems()
        assertTrue(pending.isEmpty())
    }

    @Test
    fun `increment retry count`() = runTest {
        val dao = FakeSyncQueueDao()
        val now = System.currentTimeMillis()
        val id = dao.insert(
            SyncQueueEntity(
                entityType = "TRANSACTION",
                entityId = "txn-789",
                status = SyncQueueEntity.STATUS_PENDING,
                retryCount = 0,
                createdAt = now,
                updatedAt = now
            )
        )
        dao.incrementRetryCount(id, now + 500)
        val pending = dao.getPendingItems()
        assertEquals(1, pending[0].retryCount)
        dao.incrementRetryCount(id, now + 1000)
        assertEquals(2, dao.getPendingItems()[0].retryCount)
    }
}
