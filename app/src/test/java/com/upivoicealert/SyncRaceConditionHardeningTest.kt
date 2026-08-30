package com.upivoicealert

import com.upivoicealert.data.sync.SyncQueueDao
import com.upivoicealert.data.sync.SyncQueueEntity
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Demonstrates race condition fixed in Phase 7.8:
 * Before fix, markFailedWithDiagnostics blindly overwrote any status.
 * If sync was marking FAILED while user concurrently retried FAILED→PENDING,
 * blind update would revert PENDING back to FAILED, losing merchant action.
 * Fix: restrictive WHERE status='UPLOADING' ensures only UPLOADING→FAILED.
 */
class SyncRaceConditionHardeningTest {

    private class RestrictiveFakeDao : SyncQueueDao {
        val rows = mutableListOf<SyncQueueEntity>()
        private var nextId=1L
        override suspend fun insert(item: SyncQueueEntity): Long { val id=if(item.id==0L) nextId++ else item.id; rows.add(item.copy(id=id)); return id }
        override suspend fun insertIgnore(item: SyncQueueEntity): Long = -1
        override suspend fun getByStatus(status: String)=rows.filter{it.status==status}
        override suspend fun getPendingItems()=rows.filter{it.status==SyncQueueEntity.STATUS_PENDING}
        override suspend fun updateStatus(id: Long, status: String, updatedAt: Long){ val idx=rows.indexOfFirst{it.id==id}; if(idx>=0) rows[idx]=rows[idx].copy(status=status, updatedAt=updatedAt) }
        override suspend fun updateStatusIfExpected(id: Long, expectedStatus: String, newStatus: String, updatedAt: Long): Int { val idx=rows.indexOfFirst{it.id==id}; if(idx>=0 && rows[idx].status==expectedStatus){ rows[idx]=rows[idx].copy(status=newStatus, updatedAt=updatedAt); return 1 }; return 0 }
        override suspend fun incrementRetryCount(id: Long, updatedAt: Long){}
        override suspend fun incrementRetryCountIfExpected(id: Long, expectedStatus: String, updatedAt: Long): Int =0
        override suspend fun getAll()=rows
        override suspend fun deleteById(id: Long){}
        override suspend fun clearAll(){}
        override fun observeCountByStatus(status: String)=flowOf(0)
        override suspend fun getCountByStatus(status: String)=0
        override suspend fun getFailedItems()=emptyList<SyncQueueEntity>()
        override suspend fun getById(id: Long)=rows.firstOrNull{it.id==id}
        override suspend fun markFailedWithDiagnostics(id: Long, status: String, errorCode: String?, errorMessage: String?, failedAt: Long?, updatedAt: Long){}
        override suspend fun markFailedWithDiagnosticsIfExpected(id: Long, status: String, errorCode: String?, errorMessage: String?, failedAt: Long?, updatedAt: Long, expectedStatus: String): Int {
            val idx=rows.indexOfFirst{it.id==id}
            if(idx>=0 && rows[idx].status==expectedStatus){ rows[idx]=rows[idx].copy(status=status, lastErrorCode=errorCode, lastErrorMessage=errorMessage, failedAt=failedAt, updatedAt=updatedAt); return 1 }
            return 0
        }
        override suspend fun retryFailedItem(id: Long, updatedAt: Long)=0
        override suspend fun retryAllFailed(updatedAt: Long)=0
        override fun observeFailedItems()=flowOf(emptyList<SyncQueueEntity>())
        override suspend fun getStaleUploadingItems(cutoffTime: Long)=emptyList<SyncQueueEntity>()
        override suspend fun recoverStaleUploading(cutoffTime: Long, updatedAt: Long)=0
    }

    @Test fun `restrictive FAILED prevents overwriting PENDING after manual retry`() = runTest {
        val dao=RestrictiveFakeDao()
        val id=1L
        // Start as UPLOADING
        dao.rows.add(SyncQueueEntity(id=id, entityType="TRANSACTION", entityId="u1", status=SyncQueueEntity.STATUS_UPLOADING, createdAt=1, updatedAt=1))
        // Concurrent manual retry: suppose sync hasn't yet marked FAILED, but user retried FAILED→PENDING earlier? Actually need FAILED state.
        // Simulate: item already manually retried to PENDING (status PENDING) — but sync's stale view still thinks UPLOADING and tries to mark FAILED
        dao.rows[0]=dao.rows[0].copy(status=SyncQueueEntity.STATUS_PENDING)
        // Now sync tries restrictive FAILED where expected UPLOADING — should fail to overwrite
        val updated=dao.markFailedWithDiagnosticsIfExpected(id, SyncQueueEntity.STATUS_FAILED, "VALIDATION_ERROR", "msg", 123, 456, SyncQueueEntity.STATUS_UPLOADING)
        assertEquals(0, updated)
        assertEquals(SyncQueueEntity.STATUS_PENDING, dao.rows[0].status) // not reverted to FAILED
    }

    @Test fun `restrictive PENDING to UPLOADING prevents duplicate UPLOADING`() = runTest {
        val dao=RestrictiveFakeDao()
        dao.rows.add(SyncQueueEntity(id=1, entityType="TRANSACTION", entityId="u1", status=SyncQueueEntity.STATUS_SYNCED, createdAt=1, updatedAt=1))
        // Try to mark SYNCED row as UPLOADING (should fail)
        val updated=dao.updateStatusIfExpected(1, SyncQueueEntity.STATUS_PENDING, SyncQueueEntity.STATUS_UPLOADING, 2)
        assertEquals(0, updated)
        assertEquals(SyncQueueEntity.STATUS_SYNCED, dao.rows[0].status)
    }

    @Test fun `restrictive UPLOADING to SYNCED only when UPLOADING`() = runTest {
        val dao=RestrictiveFakeDao()
        dao.rows.add(SyncQueueEntity(id=1, entityType="TRANSACTION", entityId="u1", status=SyncQueueEntity.STATUS_PENDING, createdAt=1, updatedAt=1))
        val updated=dao.updateStatusIfExpected(1, SyncQueueEntity.STATUS_UPLOADING, SyncQueueEntity.STATUS_SYNCED, 2)
        assertEquals(0, updated)
        assertEquals(SyncQueueEntity.STATUS_PENDING, dao.rows[0].status)
    }
}
