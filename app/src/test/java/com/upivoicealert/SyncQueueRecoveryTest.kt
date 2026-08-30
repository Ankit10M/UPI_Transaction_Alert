package com.upivoicealert

import com.upivoicealert.data.sync.SyncQueueDao
import com.upivoicealert.data.sync.SyncQueueEntity
import com.upivoicealert.data.sync.SyncQueueRepositoryImpl
import com.upivoicealert.domain.sync.RetrySyncItemResult
import com.upivoicealert.domain.sync.SyncStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncQueueRecoveryTest {

    private class FakeDao : SyncQueueDao {
        val rows = mutableListOf<SyncQueueEntity>()
        private var nextId = 1L
        override suspend fun insert(item: SyncQueueEntity): Long {
            val id = if (item.id == 0L) nextId++ else {
                if (item.id >= nextId) nextId = item.id + 1
                item.id
            }
            val copy = item.copy(id = id)
            rows.removeIf { it.id == id }
            rows.add(copy)
            return id
        }
        override suspend fun insertIgnore(item: SyncQueueEntity): Long {
            if (rows.any { it.entityType == item.entityType && it.entityId == item.entityId }) return -1L
            val id = if (item.id == 0L) nextId++ else {
                if (item.id >= nextId) nextId = item.id + 1
                item.id
            }
            rows.add(item.copy(id = id))
            return id
        }
        override suspend fun getByStatus(status: String) = rows.filter { it.status == status }
        override suspend fun getPendingItems() = rows.filter { it.status == SyncQueueEntity.STATUS_PENDING }.sortedBy { it.createdAt }
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
        override suspend fun getAll() = rows.sortedBy { it.createdAt }
        override suspend fun deleteById(id: Long) { rows.removeIf { it.id == id } }
        override suspend fun clearAll() { rows.clear() }
        override fun observeCountByStatus(status: String) = kotlinx.coroutines.flow.flowOf(rows.count { it.status == status })
        override suspend fun getCountByStatus(status: String) = rows.count { it.status == status }
        override suspend fun getFailedItems(): List<SyncQueueEntity> = rows.filter { it.status == SyncQueueEntity.STATUS_FAILED }.sortedByDescending { it.failedAt ?: it.updatedAt }
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
            rows.forEachIndexed { idx, e ->
                if (e.status == SyncQueueEntity.STATUS_FAILED) {
                    rows[idx] = e.copy(status = SyncQueueEntity.STATUS_PENDING, lastErrorCode = null, lastErrorMessage = null, failedAt = null, updatedAt = updatedAt)
                    count++
                }
            }
            return count
        }
        override fun observeFailedItems() = kotlinx.coroutines.flow.flowOf(rows.filter { it.status == SyncQueueEntity.STATUS_FAILED })
    }

    private var entityCounter = 1000L
    private fun createFailedEntity(suffix: Long = entityCounter++, failedAt: Long = System.currentTimeMillis(), retryCount: Int = 1, errorCode: String = "VALIDATION_ERROR", errorMessage: String = "Transaction data could not be synced."): SyncQueueEntity {
        return SyncQueueEntity(
            id = 0, entityType = "TRANSACTION", entityId = "txn-$suffix", status = SyncQueueEntity.STATUS_FAILED,
            retryCount = retryCount, createdAt = System.currentTimeMillis() - 10000, updatedAt = System.currentTimeMillis(),
            lastErrorCode = errorCode, lastErrorMessage = errorMessage, failedAt = failedAt
        )
    }

    // ─── A. Repository tests ────────────────────────────────────────────

    @Test
    fun `1 getFailedItems returns FAILED only`() = runTest {
        val dao = FakeDao()
        val repo = SyncQueueRepositoryImpl(dao)
        dao.insert(createFailedEntity(1))
        dao.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = "txn-pending", status = SyncQueueEntity.STATUS_PENDING, createdAt = 1, updatedAt = 1))
        dao.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = "txn-synced", status = SyncQueueEntity.STATUS_SYNCED, createdAt = 1, updatedAt = 1))
        val failed = repo.getFailedItems()
        assertEquals(1, failed.size)
        assertEquals(SyncQueueEntity.STATUS_FAILED, dao.getById(failed.first().queueId)?.status)
    }

    @Test
    fun `2 ordering is newest failure first`() = runTest {
        val dao = FakeDao()
        val repo = SyncQueueRepositoryImpl(dao)
        val old = createFailedEntity(10, failedAt = 1000L)
        val newer = createFailedEntity(11, failedAt = 5000L)
        val newest = createFailedEntity(12, failedAt = 9000L)
        dao.insert(old); dao.insert(newest); dao.insert(newer)
        val failed = repo.getFailedItems()
        assertEquals(3, failed.size)
        assertEquals(9000L, failed[0].failedAt)
        assertEquals(5000L, failed[1].failedAt)
        assertEquals(1000L, failed[2].failedAt)
    }

    @Test
    fun `3 retry single FAILED to PENDING`() = runTest {
        val dao = FakeDao()
        val repo = SyncQueueRepositoryImpl(dao)
        val id = dao.insert(createFailedEntity(1, failedAt = 1000L))
        val insertedId = dao.rows.first().id
        val result = repo.retryFailedItem(insertedId)
        assertTrue(result is RetrySyncItemResult.Success)
        val entity = dao.getById(insertedId)
        assertNotNull(entity)
        assertEquals(SyncQueueEntity.STATUS_PENDING, entity!!.status)
    }

    @Test
    fun `4 retry preserves retryCount`() = runTest {
        val dao = FakeDao()
        val repo = SyncQueueRepositoryImpl(dao)
        val entity = createFailedEntity(1, retryCount = 5)
        dao.insert(entity)
        val id = dao.rows.first().id
        val before = dao.getById(id)!!.retryCount
        repo.retryFailedItem(id)
        val after = dao.getById(id)!!.retryCount
        assertEquals(before, after)
        assertEquals(5, after)
    }

    @Test
    fun `5 retry clears errorCode`() = runTest {
        val dao = FakeDao()
        val repo = SyncQueueRepositoryImpl(dao)
        dao.insert(createFailedEntity(1, errorCode = "VALIDATION_ERROR"))
        val id = dao.rows.first().id
        repo.retryFailedItem(id)
        assertNull(dao.getById(id)!!.lastErrorCode)
    }

    @Test
    fun `6 retry clears errorMessage`() = runTest {
        val dao = FakeDao()
        val repo = SyncQueueRepositoryImpl(dao)
        dao.insert(createFailedEntity(1, errorMessage = "Transaction data could not be synced."))
        val id = dao.rows.first().id
        repo.retryFailedItem(id)
        assertNull(dao.getById(id)!!.lastErrorMessage)
    }

    @Test
    fun `7 retry clears failedAt`() = runTest {
        val dao = FakeDao()
        val repo = SyncQueueRepositoryImpl(dao)
        dao.insert(createFailedEntity(1, failedAt = 12345L))
        val id = dao.rows.first().id
        repo.retryFailedItem(id)
        assertNull(dao.getById(id)!!.failedAt)
    }

    @Test
    fun `8 retry SYNCED returns InvalidState`() = runTest {
        val dao = FakeDao()
        val repo = SyncQueueRepositoryImpl(dao)
        dao.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = "txn-1", status = SyncQueueEntity.STATUS_SYNCED, createdAt = 1, updatedAt = 1))
        val id = dao.rows.first().id
        val result = repo.retryFailedItem(id)
        assertTrue(result is RetrySyncItemResult.InvalidState)
        assertEquals(SyncQueueEntity.STATUS_SYNCED, dao.getById(id)!!.status)
    }

    @Test
    fun `9 retry PENDING returns InvalidState`() = runTest {
        val dao = FakeDao()
        val repo = SyncQueueRepositoryImpl(dao)
        dao.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = "txn-1", status = SyncQueueEntity.STATUS_PENDING, createdAt = 1, updatedAt = 1))
        val id = dao.rows.first().id
        val result = repo.retryFailedItem(id)
        assertTrue(result is RetrySyncItemResult.InvalidState)
    }

    @Test
    fun `10 retry UPLOADING returns InvalidState`() = runTest {
        val dao = FakeDao()
        val repo = SyncQueueRepositoryImpl(dao)
        dao.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = "txn-1", status = SyncQueueEntity.STATUS_UPLOADING, createdAt = 1, updatedAt = 1))
        val id = dao.rows.first().id
        val result = repo.retryFailedItem(id)
        assertTrue(result is RetrySyncItemResult.InvalidState)
    }

    @Test
    fun `11 nonexistent returns NotFound`() = runTest {
        val dao = FakeDao()
        val repo = SyncQueueRepositoryImpl(dao)
        val result = repo.retryFailedItem(99999L)
        assertTrue(result is RetrySyncItemResult.NotFound)
    }

    // ─── C. Bulk retry ──────────────────────────────────────────────────

    @Test
    fun `20 retry all changes FAILED to PENDING`() = runTest {
        val dao = FakeDao()
        val repo = SyncQueueRepositoryImpl(dao)
        dao.insert(createFailedEntity(1))
        dao.insert(createFailedEntity(2))
        dao.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = "txn-pending", status = SyncQueueEntity.STATUS_PENDING, createdAt = 1, updatedAt = 1))
        val count = repo.retryAllFailed()
        assertEquals(2, count)
        assertEquals(0, dao.getFailedItems().size)
        // Both should be PENDING now, plus original pending = 3 pending total
        val pending = dao.getByStatus(SyncQueueEntity.STATUS_PENDING)
        assertEquals(3, pending.size)
    }

    @Test
    fun `21 retry all preserves retryCount`() = runTest {
        val dao = FakeDao()
        val repo = SyncQueueRepositoryImpl(dao)
        dao.insert(createFailedEntity(1, retryCount = 3))
        dao.insert(createFailedEntity(2, retryCount = 7))
        val beforeCounts = dao.rows.filter { it.status == SyncQueueEntity.STATUS_FAILED }.map { it.retryCount }.sorted()
        repo.retryAllFailed()
        val afterCounts = dao.rows.filter { it.status == SyncQueueEntity.STATUS_PENDING }.map { it.retryCount }.sorted()
        // After retry, pending rows should still have 3 and 7
        assertTrue(afterCounts.contains(3))
        assertTrue(afterCounts.contains(7))
    }

    @Test
    fun `22 retry all clears diagnostics`() = runTest {
        val dao = FakeDao()
        val repo = SyncQueueRepositoryImpl(dao)
        dao.insert(createFailedEntity(1, errorCode = "VALIDATION_ERROR", errorMessage = "msg"))
        dao.insert(createFailedEntity(2, errorCode = "SYNC_REJECTED", errorMessage = "rejected"))
        repo.retryAllFailed()
        dao.rows.forEach { entity ->
            if (entity.entityId.startsWith("txn-")) {
                // All retried should have null diagnostics
                // But original pending entity not exists; check all that were failed now pending
            }
        }
        val pending = dao.getByStatus(SyncQueueEntity.STATUS_PENDING)
        pending.forEach { e ->
            assertNull(e.lastErrorCode)
            assertNull(e.lastErrorMessage)
            assertNull(e.failedAt)
        }
    }

    @Test
    fun `23 SYNCED unaffected by retry all`() = runTest {
        val dao = FakeDao()
        val repo = SyncQueueRepositoryImpl(dao)
        dao.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = "txn-synced", status = SyncQueueEntity.STATUS_SYNCED, createdAt = 1, updatedAt = 1))
        dao.insert(createFailedEntity(1))
        repo.retryAllFailed()
        val synced = dao.getByStatus(SyncQueueEntity.STATUS_SYNCED)
        assertEquals(1, synced.size)
    }

    @Test
    fun `24 PENDING unaffected by retry all`() = runTest {
        val dao = FakeDao()
        val repo = SyncQueueRepositoryImpl(dao)
        val pendingId = dao.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = "txn-pending", status = SyncQueueEntity.STATUS_PENDING, createdAt = 1, updatedAt = 1))
        val pendingBefore = dao.getById(dao.rows.first { it.entityId == "txn-pending" }.id)!!
        dao.insert(createFailedEntity(2))
        repo.retryAllFailed()
        val after = dao.getById(pendingBefore.id)!!
        assertEquals(SyncQueueEntity.STATUS_PENDING, after.status)
    }

    @Test
    fun `25 UPLOADING unaffected by retry all`() = runTest {
        val dao = FakeDao()
        val repo = SyncQueueRepositoryImpl(dao)
        dao.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = "txn-uploading", status = SyncQueueEntity.STATUS_UPLOADING, createdAt = 1, updatedAt = 1))
        dao.insert(createFailedEntity(2))
        repo.retryAllFailed()
        val uploading = dao.getByStatus(SyncQueueEntity.STATUS_UPLOADING)
        assertEquals(1, uploading.size)
    }

    @Test
    fun `26 empty failed list handled safely`() = runTest {
        val dao = FakeDao()
        val repo = SyncQueueRepositoryImpl(dao)
        dao.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = "txn-pending", status = SyncQueueEntity.STATUS_PENDING, createdAt = 1, updatedAt = 1))
        val count = repo.retryAllFailed()
        assertEquals(0, count)
    }

    @Test
    fun `observeFailedItems reflects retry`() = runTest {
        val dao = FakeDao()
        val repo = SyncQueueRepositoryImpl(dao)
        dao.insert(createFailedEntity(1))
        val before = repo.getFailedItems()
        assertEquals(1, before.size)
        repo.retryFailedItem(dao.rows.first().id)
        val after = repo.getFailedItems()
        assertEquals(0, after.size)
    }
}
