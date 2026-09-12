package com.upivoicealert

import com.upivoicealert.data.sync.StaleUploadRecoveryManager
import com.upivoicealert.data.sync.SyncQueueDao
import com.upivoicealert.data.sync.SyncQueueEntity
import com.upivoicealert.data.sync.SyncQueueRepositoryImpl
import com.upivoicealert.scheduler.SyncSchedulable
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StaleUploadRecoveryManagerTest {

    private class FakeDao : SyncQueueDao {
        val rows = mutableListOf<SyncQueueEntity>()
        private var nextId = 1L
        override suspend fun insert(item: SyncQueueEntity): Long {
            val id = if (item.id == 0L) nextId++ else item.id
            rows.add(item.copy(id = id))
            return id
        }
        override suspend fun insertIgnore(item: SyncQueueEntity): Long {
            if (rows.any { it.entityType == item.entityType && it.entityId == item.entityId }) return -1L
            val id = if (item.id == 0L) nextId++ else item.id
            rows.add(item.copy(id = id))
            return id
        }
        override suspend fun getByStatus(status: String) = rows.filter { it.status == status }
        override suspend fun getPendingItems() = rows.filter { it.status == SyncQueueEntity.STATUS_PENDING }
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
        override suspend fun getAll() = rows
        override suspend fun deleteById(id: Long) { rows.removeIf { it.id == id } }
        override suspend fun clearAll() { rows.clear() }
        override fun observeCountByStatus(status: String) = kotlinx.coroutines.flow.flowOf(rows.count { it.status == status })
        override suspend fun getCountByStatus(status: String) = rows.count { it.status == status }
        override suspend fun getFailedItems() = rows.filter { it.status == SyncQueueEntity.STATUS_FAILED }
        override suspend fun getById(id: Long) = rows.firstOrNull { it.id == id }
        override suspend fun markFailedWithDiagnostics(id: Long, status: String, errorCode: String?, errorMessage: String?, failedAt: Long?, updatedAt: Long) {}
        override suspend fun retryFailedItem(id: Long, updatedAt: Long) = 0
        override suspend fun retryAllFailed(updatedAt: Long) = 0
        override fun observeFailedItems() = kotlinx.coroutines.flow.flowOf(emptyList<SyncQueueEntity>())
        override suspend fun getStaleUploadingItems(cutoffTime: Long) = rows.filter { it.status == SyncQueueEntity.STATUS_UPLOADING && it.updatedAt < cutoffTime }
        override suspend fun recoverStaleUploading(cutoffTime: Long, updatedAt: Long): Int {
            var count = 0
            rows.forEachIndexed { idx, e ->
                if (e.status == SyncQueueEntity.STATUS_UPLOADING && e.updatedAt < cutoffTime) {
                    rows[idx] = e.copy(status = SyncQueueEntity.STATUS_PENDING, updatedAt = updatedAt)
                    count++
                }
            }
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

    private class FakeScheduler : SyncSchedulable {
        var scheduled = 0
        override fun scheduleSync() { scheduled++ }
    }

    private fun staleManager(dao: FakeDao, scheduler: FakeScheduler): StaleUploadRecoveryManager {
        val repo = SyncQueueRepositoryImpl(dao)
        return StaleUploadRecoveryManager(repo, scheduler)
    }

    private fun createUploading(updatedAt: Long, retryCount: Int = 2, entityId: String = "txn-${System.nanoTime()}"): SyncQueueEntity {
        return SyncQueueEntity(entityType = "TRANSACTION", entityId = entityId, status = SyncQueueEntity.STATUS_UPLOADING, retryCount = retryCount, createdAt = updatedAt - 1000, updatedAt = updatedAt)
    }

    @Test
    fun `1 no stale uploads recovered 0 no schedule`() = runTest {
        val dao = FakeDao()
        val scheduler = FakeScheduler()
        val manager = staleManager(dao, scheduler)
        // No items
        val result = manager.recover()
        assertEquals(0, result.recoveredCount)
        assertEquals(0, scheduler.scheduled)
    }

    @Test
    fun `2 one stale upload recovers to PENDING and schedules`() = runTest {
        val dao = FakeDao()
        val scheduler = FakeScheduler()
        val manager = staleManager(dao, scheduler)
        val staleTime = System.currentTimeMillis() - 20 * 60 * 1000L
        dao.rows.add(createUploading(staleTime, entityId = "txn-1"))
        val result = manager.recover()
        assertEquals(1, result.recoveredCount)
        assertEquals(1, scheduler.scheduled)
        assertEquals(SyncQueueEntity.STATUS_PENDING, dao.rows.first().status)
    }

    @Test
    fun `3 multiple stale uploads all recover`() = runTest {
        val dao = FakeDao()
        val scheduler = FakeScheduler()
        val manager = staleManager(dao, scheduler)
        val stale = System.currentTimeMillis() - 20 * 60 * 1000L
        repeat(3) { i -> dao.rows.add(createUploading(stale - i * 1000, entityId = "txn-$i")) }
        val result = manager.recover()
        assertEquals(3, result.recoveredCount)
        assertEquals(1, scheduler.scheduled)
        assertTrue(dao.rows.all { it.status == SyncQueueEntity.STATUS_PENDING })
    }

    @Test
    fun `4 recent uploading not stale remains UPLOADING`() = runTest {
        val dao = FakeDao()
        val scheduler = FakeScheduler()
        val manager = staleManager(dao, scheduler)
        val recent = System.currentTimeMillis() - 5 * 60 * 1000L
        dao.rows.add(createUploading(recent, entityId = "txn-recent"))
        val result = manager.recover()
        assertEquals(0, result.recoveredCount)
        assertEquals(0, scheduler.scheduled)
        assertEquals(SyncQueueEntity.STATUS_UPLOADING, dao.rows.first().status)
    }

    @Test
    fun `5 preserves retry count`() = runTest {
        val dao = FakeDao()
        val scheduler = FakeScheduler()
        val manager = staleManager(dao, scheduler)
        val stale = System.currentTimeMillis() - 20 * 60 * 1000L
        dao.rows.add(createUploading(stale, retryCount = 4, entityId = "txn-1"))
        manager.recover()
        assertEquals(4, dao.rows.first().retryCount)
    }

    @Test
    fun `6 FAILED unchanged`() = runTest {
        val dao = FakeDao()
        val scheduler = FakeScheduler()
        val manager = staleManager(dao, scheduler)
        dao.rows.add(SyncQueueEntity(entityType = "TRANSACTION", entityId = "txn-f", status = SyncQueueEntity.STATUS_FAILED, lastErrorCode = "VALIDATION_ERROR", lastErrorMessage = "msg", failedAt = 123, createdAt = 1, updatedAt = System.currentTimeMillis() - 20 * 60 * 1000L))
        val result = manager.recover()
        assertEquals(0, result.recoveredCount)
        assertEquals(SyncQueueEntity.STATUS_FAILED, dao.rows.first().status)
    }

    @Test
    fun `7 SYNCED unchanged`() = runTest {
        val dao = FakeDao()
        val scheduler = FakeScheduler()
        val manager = staleManager(dao, scheduler)
        dao.rows.add(SyncQueueEntity(entityType = "TRANSACTION", entityId = "txn-s", status = SyncQueueEntity.STATUS_SYNCED, createdAt = 1, updatedAt = System.currentTimeMillis() - 20 * 60 * 1000L))
        val result = manager.recover()
        assertEquals(0, result.recoveredCount)
        assertEquals(SyncQueueEntity.STATUS_SYNCED, dao.rows.first().status)
    }

    @Test
    fun `8 PENDING unchanged`() = runTest {
        val dao = FakeDao()
        val scheduler = FakeScheduler()
        val manager = staleManager(dao, scheduler)
        dao.rows.add(SyncQueueEntity(entityType = "TRANSACTION", entityId = "txn-p", status = SyncQueueEntity.STATUS_PENDING, createdAt = 1, updatedAt = System.currentTimeMillis() - 20 * 60 * 1000L))
        val result = manager.recover()
        assertEquals(0, result.recoveredCount)
        assertEquals(SyncQueueEntity.STATUS_PENDING, dao.rows.first().status)
    }

    @Test
    fun `9 idempotent recovery`() = runTest {
        val dao = FakeDao()
        val scheduler = FakeScheduler()
        val manager = staleManager(dao, scheduler)
        val stale = System.currentTimeMillis() - 20 * 60 * 1000L
        dao.rows.add(createUploading(stale, entityId = "txn-1"))
        val r1 = manager.recover()
        assertEquals(1, r1.recoveredCount)
        val r2 = manager.recover()
        assertEquals(0, r2.recoveredCount)
        // second recover should not schedule again? Manager schedules only if recovered>0, so second no schedule
        assertEquals(1, scheduler.scheduled) // only first
    }

    @Test
    fun `10 diagnostics preserved appropriately`() = runTest {
        val dao = FakeDao()
        val scheduler = FakeScheduler()
        val manager = staleManager(dao, scheduler)
        val stale = System.currentTimeMillis() - 20 * 60 * 1000L
        // UPLOADING rows have no diagnostics, but ensure recovery doesn't clear unrelated fields incorrectly
        // Create stale uploading with some diagnostics (should not exist normally, but test preservation)
        val entity = SyncQueueEntity(entityType = "TRANSACTION", entityId = "txn-1", status = SyncQueueEntity.STATUS_UPLOADING, retryCount = 1, lastErrorCode = "OLD", lastErrorMessage = "old msg", failedAt = 999, createdAt = 1, updatedAt = stale)
        dao.rows.add(entity)
        manager.recover()
        val after = dao.rows.first()
        assertEquals(SyncQueueEntity.STATUS_PENDING, after.status)
        // Recovery only changes status+updatedAt, preserves retryCount and diagnostics
        assertEquals(1, after.retryCount)
        assertEquals("OLD", after.lastErrorCode)
        assertEquals("old msg", after.lastErrorMessage)
        assertEquals(999L, after.failedAt)
    }
}
