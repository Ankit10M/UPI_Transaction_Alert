package com.upivoicealert

import com.upivoicealert.data.sync.StaleUploadRecoveryManager
import com.upivoicealert.data.sync.SyncQueueDao
import com.upivoicealert.data.sync.SyncQueueEntity
import com.upivoicealert.data.sync.SyncQueueRepositoryImpl
import com.upivoicealert.scheduler.SyncSchedulable
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.IOException

class StaleUploadRecoveryWorkerTest {

    private class FakeDao : SyncQueueDao {
        val rows = mutableListOf<SyncQueueEntity>()
        var throwIOException = false
        var throwPermanent = false
        private var nextId = 1L
        override suspend fun insert(item: SyncQueueEntity): Long { val id = if (item.id == 0L) nextId++ else item.id; rows.add(item.copy(id=id)); return id }
        override suspend fun insertIgnore(item: SyncQueueEntity): Long {
            if (rows.any { it.entityType == item.entityType && it.entityId == item.entityId }) return -1L
            val id = if (item.id == 0L) nextId++ else item.id; rows.add(item.copy(id=id)); return id
        }
        override suspend fun getByStatus(status: String) = rows.filter { it.status == status }
        override suspend fun getPendingItems() = rows.filter { it.status == SyncQueueEntity.STATUS_PENDING }
        override suspend fun updateStatus(id: Long, status: String, updatedAt: Long) {}
        override suspend fun incrementRetryCount(id: Long, updatedAt: Long) {}
        override suspend fun getAll() = rows
        override suspend fun deleteById(id: Long) {}
        override suspend fun clearAll() { rows.clear() }
        override fun observeCountByStatus(status: String) = kotlinx.coroutines.flow.flowOf(0)
        override suspend fun getCountByStatus(status: String) = 0
        override suspend fun getFailedItems() = emptyList<SyncQueueEntity>()
        override suspend fun getById(id: Long) = null
        override suspend fun markFailedWithDiagnostics(id: Long, status: String, errorCode: String?, errorMessage: String?, failedAt: Long?, updatedAt: Long) {}
        override suspend fun retryFailedItem(id: Long, updatedAt: Long) = 0
        override suspend fun retryAllFailed(updatedAt: Long) = 0
        override fun observeFailedItems() = kotlinx.coroutines.flow.flowOf(emptyList<SyncQueueEntity>())
        override suspend fun getStaleUploadingItems(cutoffTime: Long): List<SyncQueueEntity> {
            if (throwIOException) throw IOException("busy")
            if (throwPermanent) throw IllegalStateException("permanent")
            return rows.filter { it.status == SyncQueueEntity.STATUS_UPLOADING && it.updatedAt < cutoffTime }
        }
        override suspend fun recoverStaleUploading(cutoffTime: Long, updatedAt: Long): Int {
            if (throwIOException) throw IOException("busy")
            if (throwPermanent) throw IllegalStateException("permanent")
            var count = 0
            rows.forEachIndexed { idx, e ->
                if (e.status == SyncQueueEntity.STATUS_UPLOADING && e.updatedAt < cutoffTime) {
                    rows[idx] = e.copy(status = SyncQueueEntity.STATUS_PENDING, updatedAt = updatedAt)
                    count++
                }
            }
            return count
        }
        override suspend fun updateStatusIfExpected(id: Long, expectedStatus: String, newStatus: String, updatedAt: Long): Int { val idx=rows.indexOfFirst{it.id==id}; if(idx>=0 && rows[idx].status==expectedStatus){ rows[idx]=rows[idx].copy(status=newStatus, updatedAt=updatedAt); return 1 }; return 0 }
        override suspend fun incrementRetryCountIfExpected(id: Long, expectedStatus: String, updatedAt: Long): Int { val idx=rows.indexOfFirst{it.id==id}; if(idx>=0 && rows[idx].status==expectedStatus){ rows[idx]=rows[idx].copy(retryCount=rows[idx].retryCount+1, updatedAt=updatedAt); return 1 }; return 0 }
        override suspend fun markFailedWithDiagnosticsIfExpected(id: Long, status: String, errorCode: String?, errorMessage: String?, failedAt: Long?, updatedAt: Long, expectedStatus: String): Int { val idx=rows.indexOfFirst{it.id==id}; if(idx>=0 && rows[idx].status==expectedStatus){ rows[idx]=rows[idx].copy(status=status, lastErrorCode=errorCode, lastErrorMessage=errorMessage, failedAt=failedAt, updatedAt=updatedAt); return 1 }; return 0 }
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

    private fun manager(dao: FakeDao, scheduler: FakeScheduler): StaleUploadRecoveryManager {
        val repo = SyncQueueRepositoryImpl(dao)
        return StaleUploadRecoveryManager(repo, scheduler)
    }

    private suspend fun simulateWorker(manager: StaleUploadRecoveryManager): String {
        return try {
            manager.recover()
            "success"
        } catch (e: IOException) { "retry" }
        catch (e: android.database.sqlite.SQLiteException) { "retry" }
        catch (e: Exception) {
            val msg = e.message?.lowercase() ?: ""
            if (msg.contains("busy") || msg.contains("locked")) "retry" else "failure"
        }
    }

    @Test
    fun `no stale items success no schedule`() = runTest {
        val dao = FakeDao()
        val scheduler = FakeScheduler()
        val m = manager(dao, scheduler)
        val result = simulateWorker(m)
        assertEquals("success", result)
        assertEquals(0, scheduler.scheduled)
        assertFalse(dao.rows.any { it.status == SyncQueueEntity.STATUS_PENDING })
    }

    @Test
    fun `recovered items success and scheduled`() = runTest {
        val dao = FakeDao()
        dao.rows.add(SyncQueueEntity(entityType="TRANSACTION", entityId="txn-1", status=SyncQueueEntity.STATUS_UPLOADING, createdAt=1, updatedAt=System.currentTimeMillis() - 20*60*1000L))
        val scheduler = FakeScheduler()
        val m = manager(dao, scheduler)
        val result = simulateWorker(m)
        assertEquals("success", result)
        assertEquals(1, scheduler.scheduled)
        assertEquals(SyncQueueEntity.STATUS_PENDING, dao.rows.first().status)
    }

    @Test
    fun `transient DB error retry`() = runTest {
        val dao = FakeDao()
        dao.throwIOException = true
        val m = manager(dao, FakeScheduler())
        val result = simulateWorker(m)
        assertEquals("retry", result)
    }

    @Test
    fun `permanent unexpected error failure`() = runTest {
        val dao = FakeDao()
        dao.throwPermanent = true
        val m = manager(dao, FakeScheduler())
        val result = simulateWorker(m)
        assertEquals("failure", result)
    }

    @Test
    fun `worker never calls backend directly`() = runTest {
        val dao = FakeDao()
        dao.rows.add(SyncQueueEntity(entityType="TRANSACTION", entityId="txn-1", status=SyncQueueEntity.STATUS_UPLOADING, createdAt=1, updatedAt=System.currentTimeMillis() - 20*60*1000L))
        val scheduler = FakeScheduler()
        val m = manager(dao, scheduler)
        // Verify manager only uses repository, not api
        val result = m.recover()
        assertEquals(1, result.recoveredCount)
        // No backend call is possible via this manager — it only touches Room + scheduler
        assertEquals(1, scheduler.scheduled)
    }
}
