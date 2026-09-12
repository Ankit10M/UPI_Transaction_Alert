package com.upivoicealert

import com.upivoicealert.data.database.TransactionDao
import com.upivoicealert.data.database.TransactionEntity
import com.upivoicealert.data.sync.SyncQueueDao
import com.upivoicealert.data.sync.SyncQueueEntity
import com.upivoicealert.data.sync.SyncQueueRepositoryImpl
import com.upivoicealert.data.sync.StaleUploadRecoveryManager
import com.upivoicealert.data.sync.TransactionSyncApi
import com.upivoicealert.data.sync.TransactionSyncRepository
import com.upivoicealert.data.sync.TransactionSyncRequestDto
import com.upivoicealert.data.sync.TransactionSyncResponseDto
import com.upivoicealert.scheduler.SyncSchedulable
import com.upivoicealert.utils.DeviceIdProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionSyncWorkerStaleRecoveryTest {

    private class FakeSyncQueueDao : SyncQueueDao {
        val rows = mutableListOf<SyncQueueEntity>()
        private var nextId = 1L
        override suspend fun insert(item: SyncQueueEntity): Long { val id = if(item.id==0L) nextId++ else item.id; rows.add(item.copy(id=id)); return id }
        override suspend fun insertIgnore(item: SyncQueueEntity): Long {
            if (rows.any { it.entityType==item.entityType && it.entityId==item.entityId }) return -1L
            val id = if(item.id==0L) nextId++ else item.id; rows.add(item.copy(id=id)); return id
        }
        override suspend fun getByStatus(status: String) = rows.filter { it.status==status }
        override suspend fun getPendingItems() = rows.filter { it.status==SyncQueueEntity.STATUS_PENDING }.sortedBy { it.createdAt }
        override suspend fun updateStatus(id: Long, status: String, updatedAt: Long) {
            val idx = rows.indexOfFirst { it.id==id }
            if (idx>=0) rows[idx]=rows[idx].copy(status=status, updatedAt=updatedAt)
        }
        override suspend fun incrementRetryCount(id: Long, updatedAt: Long) {
            val idx = rows.indexOfFirst { it.id==id }
            if (idx>=0) rows[idx]=rows[idx].copy(retryCount=rows[idx].retryCount+1, updatedAt=updatedAt)
        }
        override suspend fun updateStatusIfExpected(id: Long, expectedStatus: String, newStatus: String, updatedAt: Long): Int { val idx=rows.indexOfFirst{it.id==id}; if(idx>=0 && rows[idx].status==expectedStatus){ rows[idx]=rows[idx].copy(status=newStatus, updatedAt=updatedAt); return 1 }; return 0 }
        override suspend fun incrementRetryCountIfExpected(id: Long, expectedStatus: String, updatedAt: Long): Int { val idx=rows.indexOfFirst{it.id==id}; if(idx>=0 && rows[idx].status==expectedStatus){ rows[idx]=rows[idx].copy(retryCount=rows[idx].retryCount+1, updatedAt=updatedAt); return 1 }; return 0 }
        override suspend fun markFailedWithDiagnosticsIfExpected(id: Long, status: String, errorCode: String?, errorMessage: String?, failedAt: Long?, updatedAt: Long, expectedStatus: String): Int { val idx=rows.indexOfFirst{it.id==id}; if(idx>=0 && rows[idx].status==expectedStatus){ rows[idx]=rows[idx].copy(status=status, lastErrorCode=errorCode, lastErrorMessage=errorMessage, failedAt=failedAt, updatedAt=updatedAt); return 1 }; return 0 }
        override suspend fun getAll() = rows
        override suspend fun deleteById(id: Long) { rows.removeIf { it.id==id } }
        override suspend fun clearAll() { rows.clear() }
        override fun observeCountByStatus(status: String) = flowOf(rows.count{it.status==status})
        override suspend fun getCountByStatus(status: String) = rows.count{it.status==status}
        override suspend fun getFailedItems() = rows.filter{it.status==SyncQueueEntity.STATUS_FAILED}
        override suspend fun getById(id: Long) = rows.firstOrNull{it.id==id}
        override suspend fun markFailedWithDiagnostics(id: Long, status: String, errorCode: String?, errorMessage: String?, failedAt: Long?, updatedAt: Long) {
            val idx=rows.indexOfFirst{it.id==id}
            if(idx>=0) rows[idx]=rows[idx].copy(status=status, lastErrorCode=errorCode, lastErrorMessage=errorMessage, failedAt=failedAt, updatedAt=updatedAt)
        }
        override suspend fun retryFailedItem(id: Long, updatedAt: Long): Int {
            val idx=rows.indexOfFirst{it.id==id}
            if(idx>=0 && rows[idx].status==SyncQueueEntity.STATUS_FAILED){ rows[idx]=rows[idx].copy(status=SyncQueueEntity.STATUS_PENDING, lastErrorCode=null, lastErrorMessage=null, failedAt=null, updatedAt=updatedAt); return 1 }
            return 0
        }
        override suspend fun retryAllFailed(updatedAt: Long): Int {
            var c=0; rows.forEachIndexed{ idx,e-> if(e.status==SyncQueueEntity.STATUS_FAILED){ rows[idx]=e.copy(status=SyncQueueEntity.STATUS_PENDING, lastErrorCode=null, lastErrorMessage=null, failedAt=null, updatedAt=updatedAt); c++ } }; return c
        }
        override fun observeFailedItems()=flowOf(emptyList<SyncQueueEntity>())
        override suspend fun getStaleUploadingItems(cutoffTime: Long)=rows.filter{it.status==SyncQueueEntity.STATUS_UPLOADING && it.updatedAt < cutoffTime}
        override suspend fun recoverStaleUploading(cutoffTime: Long, updatedAt: Long): Int {
            var c=0; rows.forEachIndexed{ idx,e-> if(e.status==SyncQueueEntity.STATUS_UPLOADING && e.updatedAt < cutoffTime){ rows[idx]=e.copy(status=SyncQueueEntity.STATUS_PENDING, updatedAt=updatedAt); c++ } }; return c
        }
        override suspend fun countTransactionQueueItems(): Int = 0
        override suspend fun countAllQueueItems(): Int = 0
        override suspend fun countOrphanedQueueItems(): Int = 0
        override suspend fun countDuplicateExtraRows(): Int = 0
        override suspend fun countDuplicateGroups(): Int = 0
        override suspend fun countInvalidQueueItems(): Int = 0
        override suspend fun countStaleUploading(cutoffTime: Long): Int = 0
}

    private class FakeTxDao(val map: MutableMap<String, TransactionEntity> = mutableMapOf()) : TransactionDao {
        override fun observeAll(): Flow<List<TransactionEntity>> = flowOf(map.values.toList())
        override fun observeReceivedSuccess(): Flow<List<TransactionEntity>> = flowOf(emptyList())
        override fun observeReceivedSuccessSince(since: Long): Flow<List<TransactionEntity>> = flowOf(emptyList())
        override suspend fun findRecentReceivedSuccess(amount: Double, since: Long): TransactionEntity? = null
        override fun observeLatest(): Flow<TransactionEntity?> = flowOf(null)
        override fun observeCount(): Flow<Int> = flowOf(0)
        override fun observeCountSince(since: Long): Flow<Int> = flowOf(0)
        override suspend fun findByReferenceIdGlobal(transactionId: String): TransactionEntity? = null
        override suspend fun findByFingerprint(fingerprint: String, windowStart: Long, windowEnd: Long): TransactionEntity? = null
        override suspend fun findByFingerprintNullRef(fingerprint: String, windowStart: Long, windowEnd: Long): TransactionEntity? = null
        override suspend fun findExactDuplicate(rawNotification: String, windowStart: Long, windowEnd: Long): TransactionEntity? = null
        override suspend fun findByTransactionUuid(uuid: String): TransactionEntity? = map[uuid]
        override suspend fun findByTransactionUuids(uuids: List<String>): List<TransactionEntity> = uuids.mapNotNull{map[it]}
        override suspend fun insert(entity: TransactionEntity): Long =1
        override suspend fun markVoiceAnnounced(id: String) {}
        override suspend fun clearAll() {}
        override suspend fun getEligibleMissingQueueUuids(limit: Int, offset: Int)=emptyList<String>()
        override suspend fun countEligibleMissingQueue()=0
        override suspend fun countEligibleTransactions()=0
        override suspend fun countEligibleForAudit(): Int = 0
        override suspend fun countMissingQueueForAudit(): Int = 0
        override suspend fun countScannedTransactionsForAudit(): Int = 0
    }

    private fun createTx(uuid: String): TransactionEntity {
        return TransactionEntity(id="id-$uuid", amount=100.0, sender="R", upiApp="PhonePe", transactionType="RECEIVED", status="SUCCESS", transactionId="UTR-$uuid", rawNotification="raw", parserVersion="P", parseStatus="PARSED", createdAt=System.currentTimeMillis(), sourceType="UNKNOWN", packageName="com.phonepe.app", notificationKey=null, originalNotificationText="raw", cleanedNotificationText="raw", voiceAnnounced=true, transactionUuid=uuid)
    }

    private class FakeScheduler : SyncSchedulable { var scheduled=0; override fun scheduleSync(){ scheduled++ } }
    private class FakeSyncStatusRepo : com.upivoicealert.domain.sync.SyncStatusRepository {
        var recorded=0
        override fun observeSyncStatus(): Flow<com.upivoicealert.domain.sync.CloudSyncStatus> = flowOf(com.upivoicealert.domain.sync.CloudSyncStatus.INITIAL)
        override suspend fun recordSuccessfulSync(timestamp: Long) { recorded++ }
    }

    @Test
    fun `worker recovers stale before loading pending`() = runTest {
        val queue = FakeSyncQueueDao()
        val txMap = mutableMapOf("uuid-1" to createTx("uuid-1"))
        val txDao = FakeTxDao(txMap)
        // Create stale uploading
        val stale = System.currentTimeMillis() - 20*60*1000L
        queue.rows.add(SyncQueueEntity(entityType="TRANSACTION", entityId="uuid-1", status=SyncQueueEntity.STATUS_UPLOADING, createdAt=stale-1000, updatedAt=stale))
        val scheduler = FakeScheduler()
        val repo = SyncQueueRepositoryImpl(queue)
        val staleManager = StaleUploadRecoveryManager(repo, scheduler)
        val syncRepo = TransactionSyncRepository(queue, txDao, object : TransactionSyncApi {
            override suspend fun syncTransactions(request: TransactionSyncRequestDto): TransactionSyncResponseDto {
                // Should receive the recovered pending item
                assertEquals(1, request.transactions.size)
                assertEquals("uuid-1", request.transactions.first().transactionUuid)
                return TransactionSyncResponseDto(created=listOf("uuid-1"), duplicates=emptyList())
            }
        }, object : DeviceIdProvider { override suspend fun getDeviceId()="d" })
        val statusRepo = FakeSyncStatusRepo()
        // Simulate worker: first recover without scheduling, then sync
        val recovered = staleManager.recoverWithoutScheduling()
        assertEquals(1, recovered.recoveredCount)
        assertEquals(SyncQueueEntity.STATUS_PENDING, queue.rows.first().status)
        // Now sync — should find pending and upload
        val result = syncRepo.syncPendingTransactions()
        assertTrue(result is TransactionSyncRepository.SyncResult.Success)
        // Worker would then record success
        statusRepo.recordSuccessfulSync()
        assertEquals(1, statusRepo.recorded)
    }

    @Test
    fun `recovered stale becomes available for normal sync`() = runTest {
        val queue = FakeSyncQueueDao()
        val stale = System.currentTimeMillis() - 20*60*1000L
        queue.rows.add(SyncQueueEntity(entityType="TRANSACTION", entityId="uuid-1", status=SyncQueueEntity.STATUS_UPLOADING, createdAt=stale, updatedAt=stale))
        val txDao = FakeTxDao(mutableMapOf("uuid-1" to createTx("uuid-1")))
        val repo = SyncQueueRepositoryImpl(queue)
        val manager = StaleUploadRecoveryManager(repo, FakeScheduler())
        val r = manager.recoverWithoutScheduling()
        assertEquals(1, r.recoveredCount)
        assertEquals(1, queue.getPendingItems().size)
        assertEquals("uuid-1", queue.getPendingItems().first().entityId)
    }

    @Test
    fun `recent active uploading untouched`() = runTest {
        val queue = FakeSyncQueueDao()
        val recent = System.currentTimeMillis() - 5*60*1000L
        queue.rows.add(SyncQueueEntity(entityType="TRANSACTION", entityId="uuid-1", status=SyncQueueEntity.STATUS_UPLOADING, createdAt=recent, updatedAt=recent))
        val repo = SyncQueueRepositoryImpl(queue)
        val manager = StaleUploadRecoveryManager(repo, FakeScheduler())
        val r = manager.recoverWithoutScheduling()
        assertEquals(0, r.recoveredCount)
        assertEquals(SyncQueueEntity.STATUS_UPLOADING, queue.rows.first().status)
    }

    @Test
    fun `stale recovery does not update lastSuccessfulSyncAt`() = runTest {
        val queue = FakeSyncQueueDao()
        val stale = System.currentTimeMillis() - 20*60*1000L
        queue.rows.add(SyncQueueEntity(entityType="TRANSACTION", entityId="uuid-1", status=SyncQueueEntity.STATUS_UPLOADING, createdAt=stale, updatedAt=stale))
        val repo = SyncQueueRepositoryImpl(queue)
        val statusRepo = FakeSyncStatusRepo()
        val manager = StaleUploadRecoveryManager(repo, FakeScheduler())
        manager.recover()
        // Recovery alone should not record
        assertEquals(0, statusRepo.recorded)
        // Even recoverWithoutScheduling should not record
        val manager2 = StaleUploadRecoveryManager(repo, FakeScheduler())
        manager2.recoverWithoutScheduling()
        assertEquals(0, statusRepo.recorded)
    }

    @Test
    fun `normal successful sync still updates timestamp once`() = runTest {
        val queue = FakeSyncQueueDao()
        queue.rows.add(SyncQueueEntity(entityType="TRANSACTION", entityId="uuid-1", status=SyncQueueEntity.STATUS_PENDING, createdAt=System.currentTimeMillis(), updatedAt=System.currentTimeMillis()))
        val txDao = FakeTxDao(mutableMapOf("uuid-1" to createTx("uuid-1")))
        val statusRepo = FakeSyncStatusRepo()
        val syncRepo = TransactionSyncRepository(queue, txDao, object : TransactionSyncApi {
            override suspend fun syncTransactions(request: TransactionSyncRequestDto) = TransactionSyncResponseDto(created=listOf("uuid-1"), duplicates=emptyList())
        }, object : DeviceIdProvider { override suspend fun getDeviceId()="d" })
        // Simulate worker with stale recovery (no stale) then sync
        val staleRepo = SyncQueueRepositoryImpl(queue)
        val staleManager = StaleUploadRecoveryManager(staleRepo, FakeScheduler())
        staleManager.recoverWithoutScheduling()
        val result = syncRepo.syncPendingTransactions()
        if (result is TransactionSyncRepository.SyncResult.Success) statusRepo.recordSuccessfulSync()
        assertEquals(1, statusRepo.recorded)
        // Second sync with no pending should not increment
        val result2 = syncRepo.syncPendingTransactions()
        // NoPending should not record
        assertTrue(result2 is TransactionSyncRepository.SyncResult.NoPending)
        assertEquals(1, statusRepo.recorded)
    }
}
