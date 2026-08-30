package com.upivoicealert

import com.upivoicealert.data.database.TransactionDao
import com.upivoicealert.data.database.TransactionEntity
import com.upivoicealert.data.sync.SyncIntegrityAuditor
import com.upivoicealert.data.sync.SyncQueueDao
import com.upivoicealert.data.sync.SyncQueueEntity
import com.upivoicealert.data.sync.SyncQueueRepositoryImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncIntegrityAuditorTest {

    private fun tx(uuid: String, status: String = "SUCCESS", type: String = "RECEIVED", txUuid: String = uuid): TransactionEntity {
        return TransactionEntity(
            id = "id-$uuid", amount = 100.0, sender = "Rahul", upiApp = "PhonePe",
            transactionType = type, status = status, transactionId = "UTR-$uuid",
            rawNotification = "raw", parserVersion = "PhonePeParserV1", parseStatus = "PARSED",
            createdAt = System.currentTimeMillis(), sourceType = "UNKNOWN", packageName = "com.phonepe.app",
            notificationKey = null, originalNotificationText = "raw", cleanedNotificationText = "raw",
            voiceAnnounced = true, transactionUuid = txUuid
        )
    }

    private class FakeTxDao(val txs: MutableList<TransactionEntity>) : TransactionDao {
        var insertCalled = 0
        override fun observeAll(): Flow<List<TransactionEntity>> = flowOf(txs)
        override fun observeReceivedSuccess(): Flow<List<TransactionEntity>> = flowOf(txs.filter { it.status == "SUCCESS" && it.transactionType == "RECEIVED" })
        override fun observeReceivedSuccessSince(since: Long): Flow<List<TransactionEntity>> = flowOf(emptyList())
        override suspend fun findRecentReceivedSuccess(amount: Double, since: Long): TransactionEntity? = null
        override fun observeLatest(): Flow<TransactionEntity?> = flowOf(null)
        override fun observeCount(): Flow<Int> = flowOf(txs.size)
        override fun observeCountSince(since: Long): Flow<Int> = flowOf(0)
        override suspend fun findByReferenceIdGlobal(transactionId: String): TransactionEntity? = null
        override suspend fun findByFingerprint(fingerprint: String, windowStart: Long, windowEnd: Long): TransactionEntity? = null
        override suspend fun findByFingerprintNullRef(fingerprint: String, windowStart: Long, windowEnd: Long): TransactionEntity? = null
        override suspend fun findExactDuplicate(rawNotification: String, windowStart: Long, windowEnd: Long): TransactionEntity? = null
        override suspend fun findByTransactionUuid(uuid: String): TransactionEntity? = txs.firstOrNull { it.transactionUuid == uuid }
        override suspend fun findByTransactionUuids(uuids: List<String>): List<TransactionEntity> = txs.filter { it.transactionUuid in uuids }
        override suspend fun insert(entity: TransactionEntity): Long { insertCalled++; txs.add(entity); return 1 }
        override suspend fun markVoiceAnnounced(id: String) {}
        override suspend fun clearAll() { txs.clear() }
        override suspend fun getEligibleMissingQueueUuids(limit: Int, offset: Int): List<String> = emptyList()
        override suspend fun countEligibleMissingQueue(): Int = 0
        override suspend fun countEligibleTransactions(): Int = txs.count { it.status == "SUCCESS" && it.transactionType == "RECEIVED" }

        // Audit queries — efficient COUNT semantics via Kotlin filtering (mirrors SQL)
        private fun isEligible(e: TransactionEntity) = e.status == "SUCCESS" && e.transactionType == "RECEIVED" && e.transactionUuid.trim().isNotEmpty()
        var queueRef: FakeQueueDao? = null

        override suspend fun countEligibleForAudit(): Int = txs.count { isEligible(it) }
        override suspend fun countMissingQueueForAudit(): Int {
            val q = queueRef ?: return 0
            val queuedIds = q.rows.filter { it.entityType == SyncQueueEntity.ENTITY_TYPE_TRANSACTION }.map { it.entityId }.toSet()
            return txs.count { isEligible(it) && it.transactionUuid !in queuedIds }
        }
        override suspend fun countScannedTransactionsForAudit(): Int = txs.count { it.transactionUuid.trim().isNotEmpty() }
    }

    private class FakeQueueDao : SyncQueueDao {
        val rows = mutableListOf<SyncQueueEntity>()
        var insertCalled = 0
        var insertIgnoreCalled = 0
        var updateStatusCalled = 0
        var recoverCalled = 0
        private var nextId = 1L
        override suspend fun insert(item: SyncQueueEntity): Long { insertCalled++; val id = if(item.id==0L) nextId++ else item.id; rows.add(item.copy(id=id)); return id }
        override suspend fun insertIgnore(item: SyncQueueEntity): Long {
            insertIgnoreCalled++
            if (rows.any { it.entityType==item.entityType && it.entityId==item.entityId }) return -1L
            val id = if(item.id==0L) nextId++ else item.id; rows.add(item.copy(id=id)); return id
        }
        override suspend fun getByStatus(status: String) = rows.filter { it.status == status }
        override suspend fun getPendingItems() = rows.filter { it.status==SyncQueueEntity.STATUS_PENDING }
        override suspend fun updateStatus(id: Long, status: String, updatedAt: Long) { updateStatusCalled++; val idx=rows.indexOfFirst{it.id==id}; if(idx>=0) rows[idx]=rows[idx].copy(status=status, updatedAt=updatedAt) }
        override suspend fun incrementRetryCount(id: Long, updatedAt: Long) {}
        override suspend fun getAll() = rows
        override suspend fun deleteById(id: Long) { rows.removeIf{it.id==id} }
        override suspend fun clearAll() { rows.clear() }
        override fun observeCountByStatus(status: String) = flowOf(rows.count{it.status==status})
        override suspend fun getCountByStatus(status: String) = rows.count{it.status==status}
        override suspend fun getFailedItems()= rows.filter{it.status==SyncQueueEntity.STATUS_FAILED}
        override suspend fun getById(id: Long)= rows.firstOrNull{it.id==id}
        override suspend fun markFailedWithDiagnostics(id: Long, status: String, errorCode: String?, errorMessage: String?, failedAt: Long?, updatedAt: Long) {}
        override suspend fun retryFailedItem(id: Long, updatedAt: Long) = 0
        override suspend fun retryAllFailed(updatedAt: Long)=0
        override fun observeFailedItems()= flowOf(emptyList<SyncQueueEntity>())

        // Audit impl
        override suspend fun countTransactionQueueItems(): Int = rows.count { it.entityType == SyncQueueEntity.ENTITY_TYPE_TRANSACTION }
        override suspend fun countAllQueueItems(): Int = rows.size
        override suspend fun countOrphanedQueueItems(): Int {
            // orphaned defined vs txs set; but fake needs txs. We'll compute via injected txs list externally — for test simplicity compute via placeholder
            // Actual orphan detection requires tx uuids; auditor will call queue's countOrphaned which needs txs.
            // To avoid circular, we store txsRef
            val txIds = txRef.map { it.transactionUuid }.toSet()
            return rows.count { it.entityType==SyncQueueEntity.ENTITY_TYPE_TRANSACTION && it.entityId !in txIds }
        }
        var txRef: List<TransactionEntity> = emptyList()
        override suspend fun countDuplicateExtraRows(): Int {
            val transRows = rows.filter{it.entityType==SyncQueueEntity.ENTITY_TYPE_TRANSACTION}
            val distinct = transRows.map{it.entityId}.toSet().size
            return (transRows.size - distinct).coerceAtLeast(0)
        }
        override suspend fun countDuplicateGroups(): Int {
            return rows.groupBy{ it.entityType to it.entityId }.count{ it.value.size>1 }
        }
        override suspend fun countInvalidQueueItems(): Int {
            val validStatuses = setOf("PENDING","UPLOADING","SYNCED","FAILED")
            return rows.count { it.entityId.trim().isEmpty() || it.entityType != SyncQueueEntity.ENTITY_TYPE_TRANSACTION || it.status !in validStatuses }
        }
        override suspend fun countStaleUploading(cutoffTime: Long): Int = rows.count{ it.status==SyncQueueEntity.STATUS_UPLOADING && it.updatedAt < cutoffTime }
        // keep recover methods for stale recovery manager compat
        override suspend fun getStaleUploadingItems(cutoffTime: Long)= rows.filter{it.status==SyncQueueEntity.STATUS_UPLOADING && it.updatedAt<cutoffTime}
        override suspend fun recoverStaleUploading(cutoffTime: Long, updatedAt: Long): Int { recoverCalled++; var c=0; rows.forEachIndexed{ idx, e-> if(e.status==SyncQueueEntity.STATUS_UPLOADING && e.updatedAt<cutoffTime){ rows[idx]=e.copy(status=SyncQueueEntity.STATUS_PENDING, updatedAt=updatedAt); c++ }}; return c }
    }

    private fun auditor(txs: MutableList<TransactionEntity>, queue: FakeQueueDao): Pair<FakeTxDao, SyncIntegrityAuditor> {
        val txDao = FakeTxDao(txs)
        txDao.queueRef = queue
        queue.txRef = txs
        return txDao to SyncIntegrityAuditor(txDao, queue)
    }

    @Test
    fun `1 healthy database returns isHealthy true`() = runTest {
        val txs = mutableListOf(tx("u1"), tx("u2"))
        val queue = FakeQueueDao()
        queue.rows.add(SyncQueueEntity(entityType="TRANSACTION", entityId="u1", status="PENDING", createdAt=1, updatedAt=1))
        queue.rows.add(SyncQueueEntity(entityType="TRANSACTION", entityId="u2", status="SYNCED", createdAt=1, updatedAt=1))
        val (_, a) = auditor(txs, queue)
        val r = a.audit()
        assertTrue(r.isHealthy)
        assertEquals(0, r.totalIssues)
        assertEquals(2, r.scannedTransactions)
        assertEquals(2, r.scannedQueueItems)
    }

    @Test
    fun `2 eligible transaction missing queue increments missing`() = runTest {
        val txs = mutableListOf(tx("u1"))
        val queue = FakeQueueDao()
        val (_, a) = auditor(txs, queue)
        val r = a.audit()
        assertEquals(1, r.missingQueueCount)
        assertFalse(r.isHealthy)
        assertEquals(1, r.totalIssues)
    }

    @Test
    fun `3 FAILED transaction without queue not counted as missing`() = runTest {
        val txs = mutableListOf(tx("u1", status="FAILED", type="RECEIVED"))
        val queue = FakeQueueDao()
        val (_, a) = auditor(txs, queue)
        val r = a.audit()
        assertEquals(0, r.missingQueueCount)
        assertTrue(r.isHealthy)
    }

    @Test
    fun `4 SENT transaction missing queue not counted`() = runTest {
        val txs = mutableListOf(tx("u1", status="SUCCESS", type="SENT"))
        val queue = FakeQueueDao()
        val (_, a) = auditor(txs, queue)
        val r = a.audit()
        assertEquals(0, r.missingQueueCount)
        assertTrue(r.isHealthy)
    }

    @Test
    fun `5 UNKNOWN transaction missing queue not counted`() = runTest {
        val txs = mutableListOf(tx("u1", status="SUCCESS", type="UNKNOWN"))
        val queue = FakeQueueDao()
        val (_, a) = auditor(txs, queue)
        val r = a.audit()
        assertEquals(0, r.missingQueueCount)
        assertTrue(r.isHealthy)
    }

    @Test
    fun `6 orphaned queue item increments orphaned`() = runTest {
        val txs = mutableListOf<TransactionEntity>()
        val queue = FakeQueueDao()
        queue.rows.add(SyncQueueEntity(entityType="TRANSACTION", entityId="orphan-1", status="PENDING", createdAt=1, updatedAt=1))
        val (_, a) = auditor(txs, queue)
        val r = a.audit()
        assertEquals(1, r.orphanedQueueCount)
        assertFalse(r.isHealthy)
    }

    @Test
    fun `7 duplicate logical queue records detected`() = runTest {
        val txs = mutableListOf(tx("u1"))
        val queue = FakeQueueDao()
        // Bypass unique index by directly adding duplicates
        queue.rows.add(SyncQueueEntity(id=1, entityType="TRANSACTION", entityId="u1", status="PENDING", createdAt=1, updatedAt=1))
        queue.rows.add(SyncQueueEntity(id=2, entityType="TRANSACTION", entityId="u1", status="PENDING", createdAt=1, updatedAt=1))
        queue.rows.add(SyncQueueEntity(id=3, entityType="TRANSACTION", entityId="u1", status="PENDING", createdAt=1, updatedAt=1))
        val (_, a) = auditor(txs, queue)
        val r = a.audit()
        // extra duplicate rows = 2 (3 total - 1 distinct)
        assertEquals(2, r.duplicateQueueCount)
    }

    @Test
    fun `8 invalid entity type detected`() = runTest {
        val txs = mutableListOf<TransactionEntity>()
        val queue = FakeQueueDao()
        queue.rows.add(SyncQueueEntity(entityType="UNKNOWN_TYPE", entityId="u1", status="PENDING", createdAt=1, updatedAt=1))
        val (_, a) = auditor(txs, queue)
        val r = a.audit()
        assertEquals(1, r.invalidQueueCount)
    }

    @Test
    fun `9 blank entityId detected`() = runTest {
        val txs = mutableListOf<TransactionEntity>()
        val queue = FakeQueueDao()
        queue.rows.add(SyncQueueEntity(entityType="TRANSACTION", entityId="", status="PENDING", createdAt=1, updatedAt=1))
        queue.rows.add(SyncQueueEntity(entityType="TRANSACTION", entityId="   ", status="PENDING", createdAt=1, updatedAt=1))
        val (_, a) = auditor(txs, queue)
        val r = a.audit()
        assertEquals(2, r.invalidQueueCount)
    }

    @Test
    fun `10 invalid status detected safely`() = runTest {
        val txs = mutableListOf<TransactionEntity>()
        val queue = FakeQueueDao()
        queue.rows.add(SyncQueueEntity(entityType="TRANSACTION", entityId="u1", status="BOGUS_STATUS", createdAt=1, updatedAt=1))
        val (_, a) = auditor(txs, queue)
        val r = a.audit()
        assertEquals(1, r.invalidQueueCount)
        // Should not crash
        assertFalse(r.isHealthy)
    }

    @Test
    fun `11 recent UPLOADING not stale`() = runTest {
        val txs = mutableListOf<TransactionEntity>()
        val queue = FakeQueueDao()
        val recent = System.currentTimeMillis() - 5*60*1000L
        queue.rows.add(SyncQueueEntity(entityType="TRANSACTION", entityId="u1", status="UPLOADING", createdAt=recent, updatedAt=recent))
        val (_, a) = auditor(txs, queue)
        val r = a.audit()
        assertEquals(0, r.staleUploadingCount)
    }

    @Test
    fun `12 old UPLOADING counted as stale`() = runTest {
        val txs = mutableListOf<TransactionEntity>()
        val queue = FakeQueueDao()
        val stale = System.currentTimeMillis() - 20*60*1000L
        queue.rows.add(SyncQueueEntity(entityType="TRANSACTION", entityId="u1", status="UPLOADING", createdAt=stale-1000, updatedAt=stale))
        val (_, a) = auditor(txs, queue)
        val r = a.audit()
        assertEquals(1, r.staleUploadingCount)
    }

    @Test
    fun `13 PENDING SYNCED FAILED not stale`() = runTest {
        val txs = mutableListOf<TransactionEntity>()
        val queue = FakeQueueDao()
        val stale = System.currentTimeMillis() - 20*60*1000L
        queue.rows.add(SyncQueueEntity(entityType="TRANSACTION", entityId="u1", status="PENDING", createdAt=1, updatedAt=stale))
        queue.rows.add(SyncQueueEntity(entityType="TRANSACTION", entityId="u2", status="SYNCED", createdAt=1, updatedAt=stale))
        queue.rows.add(SyncQueueEntity(entityType="TRANSACTION", entityId="u3", status="FAILED", createdAt=1, updatedAt=stale))
        val (_, a) = auditor(txs, queue)
        val r = a.audit()
        assertEquals(0, r.staleUploadingCount)
    }

    @Test
    fun `14 multiple categories aggregated correctly`() = runTest {
        val txs = mutableListOf(tx("u1"), tx("u2"))
        val queue = FakeQueueDao()
        // u1 missing queue -> missing 1 ; orphaned 1 ; invalid 1 ; stale 1
        queue.rows.add(SyncQueueEntity(entityType="TRANSACTION", entityId="orphan", status="PENDING", createdAt=1, updatedAt=1)) // orphaned
        queue.rows.add(SyncQueueEntity(entityType="TRANSACTION", entityId="bad", status="INVALID", createdAt=1, updatedAt=1)) // orphaned+invalid? counts in both categories but total aggregates
        val stale = System.currentTimeMillis() - 20*60*1000L
        queue.rows.add(SyncQueueEntity(entityType="TRANSACTION", entityId="stale-1", status="UPLOADING", createdAt=stale, updatedAt=stale)) // orphaned + stale
        // Need txs for u1,u2 missing -> 2 missing, but orphaned also counts
        queue.txRef = txs
        val txDao = FakeTxDao(txs)
        txDao.queueRef = queue
        val auditor2 = SyncIntegrityAuditor(txDao, queue)
        val r = auditor2.audit()
        assertEquals(2, r.missingQueueCount) // u1,u2 missing
        assertEquals(3, r.orphanedQueueCount) // orphan, bad, stale-1 all not in txs
        assertEquals(1, r.invalidQueueCount) // bad status
        assertEquals(1, r.staleUploadingCount)
        assertEquals(2+3+1+1, r.totalIssues) // missing+orphaned+invalid+stale (duplicate=0)
        assertFalse(r.isHealthy)
    }

    @Test
    fun `15 auditor performs no mutation`() = runTest {
        val txs = mutableListOf(tx("u1"))
        val queue = FakeQueueDao()
        queue.rows.add(SyncQueueEntity(entityType="TRANSACTION", entityId="orphan", status="PENDING", createdAt=1, updatedAt=1))
        queue.rows.add(SyncQueueEntity(entityType="TRANSACTION", entityId="", status="PENDING", createdAt=1, updatedAt=1))
        val (txDao, a) = auditor(txs, queue)
        val beforeRows = queue.rows.size
        val beforeTxs = txs.size
        a.audit()
        assertEquals(beforeRows, queue.rows.size)
        assertEquals(beforeTxs, txs.size)
        assertEquals(0, txDao.insertCalled)
        assertEquals(0, queue.insertCalled)
        assertEquals(0, queue.insertIgnoreCalled)
        assertEquals(0, queue.updateStatusCalled)
        assertEquals(0, queue.recoverCalled)
    }

    @Test
    fun `blank transactionUuid not counted as eligible missing`() = runTest {
        val txs = mutableListOf(tx("u1", txUuid = ""), tx("u2", txUuid = "   "))
        val queue = FakeQueueDao()
        val (_, a) = auditor(txs, queue)
        val r = a.audit()
        assertEquals(0, r.missingQueueCount)
        assertEquals(0, r.scannedTransactions)
    }

    @Test
    fun `uses threshold from repository`() {
        assertEquals(15*60*1000L, SyncQueueRepositoryImpl.STALE_UPLOADING_THRESHOLD_MS)
    }
}
