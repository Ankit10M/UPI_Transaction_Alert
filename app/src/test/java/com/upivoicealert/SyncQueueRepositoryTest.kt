package com.upivoicealert

import com.upivoicealert.data.sync.SyncQueueDao
import com.upivoicealert.data.sync.SyncQueueEntity
import com.upivoicealert.data.sync.SyncQueueRepositoryImpl
import com.upivoicealert.domain.sync.SyncStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncQueueRepositoryTest {

    private class FakeDao : SyncQueueDao {
        private val rows = mutableListOf<SyncQueueEntity>()
        private var nextId = 1L
        override suspend fun insert(item: SyncQueueEntity): Long {
            val id = if (item.id == 0L) nextId++ else item.id
            val copy = item.copy(id = id)
            rows.removeIf { it.id == id }
            rows.add(copy)
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
        override suspend fun getAll() = rows.sortedBy { it.createdAt }
        override suspend fun deleteById(id: Long) { rows.removeIf { it.id == id } }
        override suspend fun clearAll() { rows.clear() }
        val allRows: List<SyncQueueEntity> get() = rows.toList()
    }

    @Test
    fun `add sync item persists with PENDING status`() = runTest {
        val dao = FakeDao()
        val repo = SyncQueueRepositoryImpl(dao)
        val id = repo.addQueueItem(SyncQueueEntity.ENTITY_TYPE_TRANSACTION, "txn-1")
        assertTrue(id > 0)
        val pending = repo.getPendingItems()
        assertEquals(1, pending.size)
        assertEquals("txn-1", pending[0].entityId)
        assertEquals(SyncStatus.PENDING, pending[0].status)
    }

    @Test
    fun `update sync state to SYNCED`() = runTest {
        val dao = FakeDao()
        val repo = SyncQueueRepositoryImpl(dao)
        val id = repo.addQueueItem("TRANSACTION", "txn-2")
        repo.updateStatus(id, SyncStatus.SYNCED)
        val pending = repo.getPendingItems()
        assertTrue(pending.isEmpty())
        val all = repo.getAll()
        assertEquals(SyncStatus.SYNCED, all[0].status)
    }

    @Test
    fun `increment retry count persists`() = runTest {
        val dao = FakeDao()
        val repo = SyncQueueRepositoryImpl(dao)
        val id = repo.addQueueItem("TRANSACTION", "txn-3")
        repo.incrementRetryCount(id)
        repo.incrementRetryCount(id)
        assertEquals(2, repo.getPendingItems()[0].retryCount)
    }
}
