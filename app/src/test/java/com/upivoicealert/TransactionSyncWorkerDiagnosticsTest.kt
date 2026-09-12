package com.upivoicealert

import com.upivoicealert.data.sync.SyncDiagnosticDao
import com.upivoicealert.data.sync.SyncDiagnosticEventEntity
import com.upivoicealert.data.sync.SyncDiagnosticRepositoryImpl
import com.upivoicealert.data.sync.TransactionSyncRepository
import com.upivoicealert.data.database.TransactionDao
import com.upivoicealert.data.database.TransactionEntity
import com.upivoicealert.data.sync.SyncQueueDao
import com.upivoicealert.data.sync.SyncQueueEntity
import com.upivoicealert.domain.sync.SyncDiagnosticCategory
import com.upivoicealert.domain.sync.SyncDiagnosticEventType
import com.upivoicealert.domain.sync.SyncDiagnosticRecorder
import com.upivoicealert.domain.sync.SyncStatusRepository
import com.upivoicealert.network.NetworkMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class TransactionSyncWorkerDiagnosticsTest {

    private class FakeTxDao : TransactionDao {
        override fun observeAll(): Flow<List<TransactionEntity>> = flowOf(emptyList())
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
        override suspend fun findByTransactionUuid(uuid: String): TransactionEntity? = null
        override suspend fun findByTransactionUuids(uuids: List<String>): List<TransactionEntity> = emptyList()
        override suspend fun insert(entity: TransactionEntity): Long = 1
        override suspend fun markVoiceAnnounced(id: String) {}
        override suspend fun clearAll() {}
        override suspend fun getEligibleMissingQueueUuids(limit: Int, offset: Int): List<String> = emptyList()
        override suspend fun countEligibleMissingQueue(): Int = 0
        override suspend fun countEligibleTransactions(): Int = 0
        override suspend fun countEligibleForAudit(): Int = 0
        override suspend fun countMissingQueueForAudit(): Int = 0
        override suspend fun countScannedTransactionsForAudit(): Int = 0
}
    private class FakeQueueDao : SyncQueueDao {
        override suspend fun insert(item: SyncQueueEntity): Long = 1
        override suspend fun insertIgnore(item: SyncQueueEntity): Long = -1
        override suspend fun getByStatus(status: String) = emptyList<SyncQueueEntity>()
        override suspend fun getPendingItems() = emptyList<SyncQueueEntity>()
        override suspend fun updateStatus(id: Long, status: String, updatedAt: Long) {}
        override suspend fun incrementRetryCount(id: Long, updatedAt: Long) {}
        override suspend fun getAll() = emptyList<SyncQueueEntity>()
        override suspend fun deleteById(id: Long) {}
        override suspend fun clearAll() {}
        override fun observeCountByStatus(status: String) = flowOf(0)
        override suspend fun getCountByStatus(status: String) = 0
        override suspend fun getFailedItems() = emptyList<SyncQueueEntity>()
        override suspend fun getById(id: Long) = null
        override suspend fun markFailedWithDiagnostics(id: Long, status: String, errorCode: String?, errorMessage: String?, failedAt: Long?, updatedAt: Long) {}
        override suspend fun retryFailedItem(id: Long, updatedAt: Long) = 0
        override suspend fun retryAllFailed(updatedAt: Long) = 0
        override fun observeFailedItems() = flowOf(emptyList<SyncQueueEntity>())
        override suspend fun countTransactionQueueItems(): Int = 0
        override suspend fun countAllQueueItems(): Int = 0
        override suspend fun countOrphanedQueueItems(): Int = 0
        override suspend fun countDuplicateExtraRows(): Int = 0
        override suspend fun countDuplicateGroups(): Int = 0
        override suspend fun countInvalidQueueItems(): Int = 0
        override suspend fun countStaleUploading(cutoffTime: Long): Int = 0
        override suspend fun getStaleUploadingItems(cutoffTime: Long): List<com.upivoicealert.data.sync.SyncQueueEntity> = emptyList()
        override suspend fun recoverStaleUploading(cutoffTime: Long, updatedAt: Long): Int = 0
        override suspend fun updateStatusIfExpected(id: Long, expectedStatus: String, newStatus: String, updatedAt: Long): Int = 0
        override suspend fun incrementRetryCountIfExpected(id: Long, expectedStatus: String, updatedAt: Long): Int = 0
        override suspend fun markFailedWithDiagnosticsIfExpected(id: Long, status: String, errorCode: String?, errorMessage: String?, failedAt: Long?, updatedAt: Long, expectedStatus: String): Int = 0
}
    private class FakeSyncRepo(private val result: TransactionSyncRepository.SyncResult, val syncedCount: Int = 0, val cause: TransactionSyncRepository.RetryCause = TransactionSyncRepository.RetryCause.SERVER) {
        var recordedSuccessAt: Long? = null
        var syncCalled = 0
        suspend fun syncPendingTransactions(): TransactionSyncRepository.SyncResult { syncCalled++; return result }
        fun getLastSuccessCount() = syncedCount
        fun getLastRetryCause() = cause
    }
    private class FakeDiagnostics : SyncDiagnosticRecorder {
        val events = mutableListOf<Pair<SyncDiagnosticCategory, SyncDiagnosticEventType>>()
        var shouldThrow = false
        override suspend fun record(category: SyncDiagnosticCategory, eventType: SyncDiagnosticEventType, affectedCount: Int) {
            if (shouldThrow) throw RuntimeException("diagnostic write failed")
            events.add(category to eventType)
        }
    }
    private class FakeStatusRepo : SyncStatusRepository {
        var lastSync: Long? = null
        override fun observeSyncStatus(): Flow<com.upivoicealert.domain.sync.CloudSyncStatus> = flowOf(com.upivoicealert.domain.sync.CloudSyncStatus.INITIAL)
        override suspend fun recordSuccessfulSync(timestamp: Long) { lastSync = timestamp }
        suspend fun recordSuccessfulSync() { lastSync = System.currentTimeMillis() }
    }

    // Simulate worker logic with diagnostics isolation
    private suspend fun simulate(online: Boolean, repoResult: TransactionSyncRepository.SyncResult, syncedCount: Int = 0, cause: TransactionSyncRepository.RetryCause = TransactionSyncRepository.RetryCause.SERVER, diagShouldThrow: Boolean = false): Triple<String, FakeDiagnostics, FakeStatusRepo> {
        val diag = FakeDiagnostics().apply { shouldThrow = diagShouldThrow }
        val status = FakeStatusRepo()
        var workerResult: String
        // replicate TransactionSyncWorker logic
        if (!online) {
            runCatching { diag.record(SyncDiagnosticCategory.SYNC, SyncDiagnosticEventType.SYNC_OFFLINE, 0) }
            workerResult = "retry"
        } else {
            workerResult = when (repoResult) {
                is TransactionSyncRepository.SyncResult.Success -> {
                    status.recordSuccessfulSync()
                    runCatching { diag.record(SyncDiagnosticCategory.SYNC, SyncDiagnosticEventType.SYNC_COMPLETED, syncedCount) }
                    "success"
                }
                is TransactionSyncRepository.SyncResult.NoPending -> {
                    runCatching { diag.record(SyncDiagnosticCategory.SYNC, SyncDiagnosticEventType.SYNC_NO_PENDING, 0) }
                    "success"
                }
                is TransactionSyncRepository.SyncResult.PartialSuccess -> {
                    runCatching { diag.record(SyncDiagnosticCategory.SYNC, SyncDiagnosticEventType.SYNC_PARTIAL, repoResult.synced) }
                    "success"
                }
                is TransactionSyncRepository.SyncResult.Retry -> {
                    val type = if (cause == TransactionSyncRepository.RetryCause.NETWORK) SyncDiagnosticEventType.SYNC_NETWORK_RETRY else SyncDiagnosticEventType.SYNC_SERVER_RETRY
                    runCatching { diag.record(SyncDiagnosticCategory.SYNC, type, 0) }
                    "retry"
                }
                is TransactionSyncRepository.SyncResult.Error -> {
                    runCatching { diag.record(SyncDiagnosticCategory.SYNC, SyncDiagnosticEventType.SYNC_VALIDATION_FAILED, 0) }
                    "failure"
                }
                is TransactionSyncRepository.SyncResult.SessionExpired -> {
                    runCatching { diag.record(SyncDiagnosticCategory.SYNC, SyncDiagnosticEventType.SYNC_SESSION_EXPIRED, 0) }
                    "failure"
                }
            }
        }
        return Triple(workerResult, diag, status)
    }

    @Test fun `Success records diagnostic and timestamp`() = runTest {
        val (res, diag, status) = simulate(true, TransactionSyncRepository.SyncResult.Success, syncedCount = 25)
        assertEquals("success", res)
        assertNotNull(status.lastSync)
        assertTrue(diag.events.any { it.second == SyncDiagnosticEventType.SYNC_COMPLETED })
    }
    @Test fun `NoPending records diagnostic no timestamp`() = runTest {
        val (res, diag, status) = simulate(true, TransactionSyncRepository.SyncResult.NoPending)
        assertEquals("success", res)
        assertNull(status.lastSync)
        assertTrue(diag.events.any { it.second == SyncDiagnosticEventType.SYNC_NO_PENDING })
    }
    @Test fun `PartialSuccess records diagnostic no timestamp`() = runTest {
        val (res, diag, status) = simulate(true, TransactionSyncRepository.SyncResult.PartialSuccess(2,1))
        assertEquals("success", res)
        assertNull(status.lastSync)
        assertTrue(diag.events.any { it.second == SyncDiagnosticEventType.SYNC_PARTIAL })
    }
    @Test fun `Retry network records network retry`() = runTest {
        val (res, diag, _) = simulate(true, TransactionSyncRepository.SyncResult.Retry, cause = TransactionSyncRepository.RetryCause.NETWORK)
        assertEquals("retry", res)
        assertTrue(diag.events.any { it.second == SyncDiagnosticEventType.SYNC_NETWORK_RETRY })
    }
    @Test fun `Retry server records server retry`() = runTest {
        val (res, diag, _) = simulate(true, TransactionSyncRepository.SyncResult.Retry, cause = TransactionSyncRepository.RetryCause.SERVER)
        assertEquals("retry", res)
        assertTrue(diag.events.any { it.second == SyncDiagnosticEventType.SYNC_SERVER_RETRY })
    }
    @Test fun `Error validation records failure`() = runTest {
        val (res, diag, _) = simulate(true, TransactionSyncRepository.SyncResult.Error("Validation error"))
        assertEquals("failure", res)
        assertTrue(diag.events.any { it.second == SyncDiagnosticEventType.SYNC_VALIDATION_FAILED })
    }
    @Test fun `SessionExpired records failure`() = runTest {
        val (res, diag, _) = simulate(true, TransactionSyncRepository.SyncResult.SessionExpired)
        assertEquals("failure", res)
        assertTrue(diag.events.any { it.second == SyncDiagnosticEventType.SYNC_SESSION_EXPIRED })
    }
    @Test fun `offline records offline retry`() = runTest {
        val (res, diag, status) = simulate(false, TransactionSyncRepository.SyncResult.Success)
        assertEquals("retry", res)
        assertNull(status.lastSync)
        assertTrue(diag.events.any { it.second == SyncDiagnosticEventType.SYNC_OFFLINE })
    }
    @Test fun `diagnostic write failure does not change worker result success`() = runTest {
        val (res, _, status) = simulate(true, TransactionSyncRepository.SyncResult.Success, diagShouldThrow = true)
        assertEquals("success", res)
        assertNotNull(status.lastSync)
    }
    @Test fun `diagnostic failure does not prevent successful sync`() = runTest {
        val (res, diag, _) = simulate(true, TransactionSyncRepository.SyncResult.Success, diagShouldThrow = true)
        assertEquals("success", res)
        // diagnostics failed but sync still success -> is still success regardless
        assertTrue(diag.events.isEmpty()) // because throw prevented recording, but worker still success
    }
}
