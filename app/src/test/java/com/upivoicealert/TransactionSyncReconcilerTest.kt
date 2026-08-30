package com.upivoicealert

import com.upivoicealert.data.database.TransactionDao
import com.upivoicealert.data.database.TransactionEntity
import com.upivoicealert.data.datastore.ReconciliationStatusRecorder
import com.upivoicealert.data.sync.SyncQueueDao
import com.upivoicealert.data.sync.SyncQueueEntity
import com.upivoicealert.data.sync.TransactionSyncReconciler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionSyncReconcilerTest {

    private fun createTransaction(
        uuid: String,
        amount: Double = 100.0,
        status: String = "SUCCESS",
        type: String = "RECEIVED",
        createdAt: Long = System.currentTimeMillis()
    ): TransactionEntity {
        return TransactionEntity(
            id = "id-$uuid", amount = amount, sender = "Rahul", upiApp = "PhonePe",
            transactionType = type, status = status, transactionId = "UTR-$uuid",
            rawNotification = "raw", parserVersion = "PhonePeParserV1", parseStatus = "PARSED",
            createdAt = createdAt, sourceType = "UNKNOWN", packageName = "com.phonepe.app",
            notificationKey = null, originalNotificationText = "raw", cleanedNotificationText = "raw",
            voiceAnnounced = true, transactionUuid = uuid
        )
    }

    private class FakeTransactionDao(
        val transactions: MutableList<TransactionEntity> = mutableListOf()
    ) : TransactionDao {
        override fun observeAll(): Flow<List<TransactionEntity>> = flowOf(transactions)
        override fun observeReceivedSuccess(): Flow<List<TransactionEntity>> = flowOf(transactions.filter { it.status == "SUCCESS" && it.transactionType == "RECEIVED" })
        override fun observeReceivedSuccessSince(since: Long): Flow<List<TransactionEntity>> = flowOf(emptyList())
        override suspend fun findRecentReceivedSuccess(amount: Double, since: Long): TransactionEntity? = null
        override fun observeLatest(): Flow<TransactionEntity?> = flowOf(null)
        override fun observeCount(): Flow<Int> = flowOf(transactions.size)
        override fun observeCountSince(since: Long): Flow<Int> = flowOf(0)
        override suspend fun findByReferenceIdGlobal(transactionId: String): TransactionEntity? = null
        override suspend fun findByFingerprint(fingerprint: String, windowStart: Long, windowEnd: Long): TransactionEntity? = null
        override suspend fun findByFingerprintNullRef(fingerprint: String, windowStart: Long, windowEnd: Long): TransactionEntity? = null
        override suspend fun findExactDuplicate(rawNotification: String, windowStart: Long, windowEnd: Long): TransactionEntity? = null
        override suspend fun findByTransactionUuid(uuid: String): TransactionEntity? = transactions.firstOrNull { it.transactionUuid == uuid }
        override suspend fun findByTransactionUuids(uuids: List<String>): List<TransactionEntity> = transactions.filter { it.transactionUuid in uuids }
        override suspend fun insert(entity: TransactionEntity): Long { transactions.add(entity); return 1 }
        override suspend fun markVoiceAnnounced(id: String) {}
        override suspend fun clearAll() { transactions.clear() }

        override suspend fun getEligibleMissingQueueUuids(limit: Int, offset: Int): List<String> {
            // Simulate LEFT JOIN logic: eligible SUCCESS RECEIVED where not in sync_queue
            // This will be overridden by test's custom logic using syncQueue reference; for generic fake, return eligible filtered
            // But we need syncQueue reference; tests will use a wrapper that checks syncQueue manually
            return emptyList()
        }
        override suspend fun countEligibleMissingQueue(): Int = 0
        override suspend fun countEligibleTransactions(): Int = transactions.count { it.status == "SUCCESS" && it.transactionType == "RECEIVED" }
    }

    // Enhanced fake that knows about syncQueue
    private class FakeSyncQueueDao : SyncQueueDao {
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
        override fun observeCountByStatus(status: String) = flowOf(rows.count { it.status == status })
        override suspend fun getCountByStatus(status: String) = rows.count { it.status == status }
        override suspend fun getFailedItems() = rows.filter { it.status == SyncQueueEntity.STATUS_FAILED }
        override suspend fun getById(id: Long) = rows.firstOrNull { it.id == id }
        override suspend fun markFailedWithDiagnostics(id: Long, status: String, errorCode: String?, errorMessage: String?, failedAt: Long?, updatedAt: Long) {}
        override suspend fun retryFailedItem(id: Long, updatedAt: Long) = 0
        override suspend fun retryAllFailed(updatedAt: Long) = 0
        override fun observeFailedItems() = flowOf(emptyList<SyncQueueEntity>())
    }

    private class FakeReconciliationStore : ReconciliationStatusRecorder {
        var lastChecked: Long? = null
        var lastRepaired: Int = -1
        override suspend fun recordReconciliation(timestamp: Long, repairedCount: Int) {
            lastChecked = timestamp
            lastRepaired = repairedCount
        }
    }

    private fun createReconciler(
        txs: MutableList<TransactionEntity>,
        queue: FakeSyncQueueDao,
        storeFake: FakeReconciliationStore
    ): TransactionSyncReconciler {
        val txDao = object : TransactionDao {
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
            override suspend fun insert(entity: TransactionEntity): Long { txs.add(entity); return 1 }
            override suspend fun markVoiceAnnounced(id: String) {}
            override suspend fun clearAll() { txs.clear() }
            override suspend fun getEligibleMissingQueueUuids(limit: Int, offset: Int): List<String> {
                val queuedIds = queue.rows.filter { it.entityType == SyncQueueEntity.ENTITY_TYPE_TRANSACTION }.map { it.entityId }.toSet()
                return txs.filter { it.status == "SUCCESS" && it.transactionType == "RECEIVED" && it.transactionUuid !in queuedIds }
                    .sortedBy { it.createdAt }
                    .drop(offset).take(limit).map { it.transactionUuid }
            }
            override suspend fun countEligibleMissingQueue(): Int {
                val queuedIds = queue.rows.filter { it.entityType == SyncQueueEntity.ENTITY_TYPE_TRANSACTION }.map { it.entityId }.toSet()
                return txs.count { it.status == "SUCCESS" && it.transactionType == "RECEIVED" && it.transactionUuid !in queuedIds }
            }
            override suspend fun countEligibleTransactions(): Int = txs.count { it.status == "SUCCESS" && it.transactionType == "RECEIVED" }
        }
        return TransactionSyncReconciler(txDao, queue, storeFake)
    }

    @Test
    fun `1 eligible transaction missing queue is repaired`() = runTest {
        val txs = mutableListOf(createTransaction("uuid-1"))
        val queue = FakeSyncQueueDao()
        val store = FakeReconciliationStore()
        val reconciler = createReconciler(txs, queue, store)
        val result = reconciler.reconcile()
        assertEquals(1, result.repairedCount)
        assertEquals(1, queue.rows.size)
        assertEquals("uuid-1", queue.rows.first().entityId)
        assertEquals(SyncQueueEntity.STATUS_PENDING, queue.rows.first().status)
    }

    @Test
    fun `2 multiple missing transactions are all repaired`() = runTest {
        val txs = mutableListOf(createTransaction("u1"), createTransaction("u2"), createTransaction("u3"))
        val queue = FakeSyncQueueDao()
        val store = FakeReconciliationStore()
        val reconciler = createReconciler(txs, queue, store)
        val result = reconciler.reconcile()
        assertEquals(3, result.repairedCount)
        assertEquals(3, queue.rows.size)
    }

    @Test
    fun `3 existing PENDING queue is unchanged`() = runTest {
        val tx = createTransaction("uuid-1")
        val txs = mutableListOf(tx)
        val queue = FakeSyncQueueDao()
        queue.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = "uuid-1", status = SyncQueueEntity.STATUS_PENDING, createdAt = 1, updatedAt = 1))
        val beforeSize = queue.rows.size
        val reconciler = createReconciler(txs, queue, FakeReconciliationStore())
        val result = reconciler.reconcile()
        assertEquals(0, result.repairedCount)
        assertEquals(beforeSize, queue.rows.size)
        assertEquals(SyncQueueEntity.STATUS_PENDING, queue.rows.first().status)
    }

    @Test
    fun `4 existing SYNCED queue is unchanged`() = runTest {
        val tx = createTransaction("uuid-1")
        val txs = mutableListOf(tx)
        val queue = FakeSyncQueueDao()
        queue.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = "uuid-1", status = SyncQueueEntity.STATUS_SYNCED, createdAt = 1, updatedAt = 1))
        val reconciler = createReconciler(txs, queue, FakeReconciliationStore())
        val result = reconciler.reconcile()
        assertEquals(0, result.repairedCount)
        assertEquals(SyncQueueEntity.STATUS_SYNCED, queue.rows.first().status)
    }

    @Test
    fun `5 existing FAILED queue is unchanged`() = runTest {
        val tx = createTransaction("uuid-1")
        val txs = mutableListOf(tx)
        val queue = FakeSyncQueueDao()
        queue.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = "uuid-1", status = SyncQueueEntity.STATUS_FAILED, lastErrorCode = "VALIDATION_ERROR", lastErrorMessage = "msg", failedAt = 123, createdAt = 1, updatedAt = 1))
        val reconciler = createReconciler(txs, queue, FakeReconciliationStore())
        val result = reconciler.reconcile()
        assertEquals(0, result.repairedCount)
        assertEquals(SyncQueueEntity.STATUS_FAILED, queue.rows.first().status)
        assertEquals("VALIDATION_ERROR", queue.rows.first().lastErrorCode)
    }

    @Test
    fun `6 FAILED transaction does not create queue`() = runTest {
        val tx = createTransaction("uuid-1", status = "FAILED", type = "RECEIVED")
        val txs = mutableListOf(tx)
        val queue = FakeSyncQueueDao()
        val reconciler = createReconciler(txs, queue, FakeReconciliationStore())
        val result = reconciler.reconcile()
        assertEquals(0, result.repairedCount)
        assertEquals(0, queue.rows.size)
        assertEquals(0, result.scannedCount)
    }

    @Test
    fun `7 sent transaction does not create queue`() = runTest {
        val tx = createTransaction("uuid-1", status = "SUCCESS", type = "SENT")
        val txs = mutableListOf(tx)
        val queue = FakeSyncQueueDao()
        val reconciler = createReconciler(txs, queue, FakeReconciliationStore())
        val result = reconciler.reconcile()
        assertEquals(0, result.repairedCount)
        assertEquals(0, queue.rows.size)
    }

    @Test
    fun `8 UNKNOWN transaction does not create queue`() = runTest {
        val tx = createTransaction("uuid-1", status = "SUCCESS", type = "UNKNOWN")
        val txs = mutableListOf(tx)
        val queue = FakeSyncQueueDao()
        val reconciler = createReconciler(txs, queue, FakeReconciliationStore())
        val result = reconciler.reconcile()
        assertEquals(0, result.repairedCount)
        assertEquals(0, queue.rows.size)
    }

    @Test
    fun `9 duplicate reconciliation run does not create duplicate queue`() = runTest {
        val tx = createTransaction("uuid-1")
        val txs = mutableListOf(tx)
        val queue = FakeSyncQueueDao()
        val store = FakeReconciliationStore()
        val reconciler = createReconciler(txs, queue, store)
        val r1 = reconciler.reconcile()
        assertEquals(1, r1.repairedCount)
        assertEquals(1, queue.rows.size)
        val r2 = reconciler.reconcile()
        assertEquals(0, r2.repairedCount)
        assertEquals(1, queue.rows.size)
    }

    @Test
    fun `10 retryCount preserved on existing rows`() = runTest {
        val tx = createTransaction("uuid-1")
        val txs = mutableListOf(tx)
        val queue = FakeSyncQueueDao()
        queue.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = "uuid-1", status = SyncQueueEntity.STATUS_PENDING, retryCount = 5, createdAt = 1, updatedAt = 1))
        val reconciler = createReconciler(txs, queue, FakeReconciliationStore())
        reconciler.reconcile()
        assertEquals(5, queue.rows.first().retryCount)
    }

    @Test
    fun `11 diagnostics preserved on existing FAILED rows`() = runTest {
        val tx = createTransaction("uuid-1")
        val txs = mutableListOf(tx)
        val queue = FakeSyncQueueDao()
        queue.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = "uuid-1", status = SyncQueueEntity.STATUS_FAILED, lastErrorCode = "VALIDATION_ERROR", lastErrorMessage = "Transaction data could not be synced.", failedAt = 999, retryCount = 2, createdAt = 1, updatedAt = 1))
        val reconciler = createReconciler(txs, queue, FakeReconciliationStore())
        reconciler.reconcile()
        val e = queue.rows.first()
        assertEquals("VALIDATION_ERROR", e.lastErrorCode)
        assertEquals("Transaction data could not be synced.", e.lastErrorMessage)
        assertEquals(999L, e.failedAt)
        assertEquals(2, e.retryCount)
    }

    @Test
    fun `repaired item has correct PENDING fields`() = runTest {
        val tx = createTransaction("uuid-1")
        val txs = mutableListOf(tx)
        val queue = FakeSyncQueueDao()
        val reconciler = createReconciler(txs, queue, FakeReconciliationStore())
        reconciler.reconcile()
        val e = queue.rows.first()
        assertEquals(SyncQueueEntity.ENTITY_TYPE_TRANSACTION, e.entityType)
        assertEquals("uuid-1", e.entityId)
        assertEquals(SyncQueueEntity.STATUS_PENDING, e.status)
        assertEquals(0, e.retryCount)
        assertNull(e.lastErrorCode)
        assertNull(e.lastErrorMessage)
        assertNull(e.failedAt)
        assertTrue(e.createdAt > 0)
        assertTrue(e.updatedAt > 0)
    }
}
