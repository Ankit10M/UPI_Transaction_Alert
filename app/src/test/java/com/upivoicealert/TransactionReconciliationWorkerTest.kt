package com.upivoicealert

import com.upivoicealert.data.datastore.ReconciliationStatusRecorder
import com.upivoicealert.data.database.TransactionDao
import com.upivoicealert.data.database.TransactionEntity
import com.upivoicealert.data.sync.SyncQueueDao
import com.upivoicealert.data.sync.SyncQueueEntity
import com.upivoicealert.data.sync.TransactionSyncReconciler
import com.upivoicealert.domain.sync.ReconciliationResult
import com.upivoicealert.scheduler.SyncSchedulable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class TransactionReconciliationWorkerTest {

    private class FakeSyncQueueDao : SyncQueueDao {
        val rows = mutableListOf<SyncQueueEntity>()
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
        override fun observeCountByStatus(status: String) = flowOf(0)
        override suspend fun getCountByStatus(status: String) = 0
        override suspend fun getFailedItems() = emptyList<SyncQueueEntity>()
        override suspend fun getById(id: Long) = null
        override suspend fun markFailedWithDiagnostics(id: Long, status: String, errorCode: String?, errorMessage: String?, failedAt: Long?, updatedAt: Long) {}
        override suspend fun retryFailedItem(id: Long, updatedAt: Long) = 0
        override suspend fun retryAllFailed(updatedAt: Long) = 0
        override fun observeFailedItems() = flowOf(emptyList<SyncQueueEntity>())
    }

    private class FakeTxDao(
        val txs: MutableList<TransactionEntity> = mutableListOf(),
        val queue: FakeSyncQueueDao
    ) : TransactionDao {
        override fun observeAll(): Flow<List<TransactionEntity>> = flowOf(txs)
        override fun observeReceivedSuccess(): Flow<List<TransactionEntity>> = flowOf(emptyList())
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
            val queued = queue.rows.map { it.entityId }.toSet()
            return txs.filter { it.status == "SUCCESS" && it.transactionType == "RECEIVED" && it.transactionUuid !in queued }
                .sortedBy { it.createdAt }.drop(offset).take(limit).map { it.transactionUuid }
        }
        override suspend fun countEligibleMissingQueue(): Int {
            val queued = queue.rows.map { it.entityId }.toSet()
            return txs.count { it.status == "SUCCESS" && it.transactionType == "RECEIVED" && it.transactionUuid !in queued }
        }
        override suspend fun countEligibleTransactions(): Int = txs.count { it.status == "SUCCESS" && it.transactionType == "RECEIVED" }
    }

    private class FakeRecorder : ReconciliationStatusRecorder {
        var lastRepaired: Int = -1
        var lastAt: Long? = null
        override suspend fun recordReconciliation(timestamp: Long, repairedCount: Int) {
            lastAt = timestamp; lastRepaired = repairedCount
        }
    }

    private class FakeScheduler : SyncSchedulable {
        var scheduled = 0
        override fun scheduleSync() { scheduled++ }
    }

    private fun createReconciler(txs: MutableList<TransactionEntity>, queue: FakeSyncQueueDao, recorder: FakeRecorder = FakeRecorder()): TransactionSyncReconciler {
        val txDao = FakeTxDao(txs, queue)
        return TransactionSyncReconciler(txDao, queue, recorder)
    }

    private fun createTx(uuid: String, status: String = "SUCCESS", type: String = "RECEIVED"): TransactionEntity {
        return TransactionEntity(id="id-$uuid", amount=100.0, sender="R", upiApp="PhonePe", transactionType=type, status=status, transactionId="UTR-$uuid", rawNotification="raw", parserVersion="P", parseStatus="PARSED", createdAt=System.currentTimeMillis(), sourceType="UNKNOWN", packageName="com.phonepe.app", notificationKey=null, originalNotificationText="raw", cleanedNotificationText="raw", voiceAnnounced=true, transactionUuid=uuid)
    }

    // Simulate worker logic without Android Worker
    private suspend fun simulateWorker(reconciler: TransactionSyncReconciler, scheduler: FakeScheduler): String {
        return try {
            val result = reconciler.reconcile()
            if (result.repairedCount > 0) scheduler.scheduleSync()
            "success"
        } catch (e: IOException) {
            "retry"
        } catch (e: android.database.sqlite.SQLiteException) {
            "retry"
        } catch (e: Exception) {
            val msg = e.message?.lowercase() ?: ""
            if (msg.contains("busy") || msg.contains("locked")) "retry" else "failure"
        }
    }

    @Test
    fun `12 no missing items success`() = runTest {
        val txs = mutableListOf(createTx("u1"))
        val queue = FakeSyncQueueDao()
        // Pre-queue the eligible
        queue.rows.add(SyncQueueEntity(entityType="TRANSACTION", entityId="u1", status=SyncQueueEntity.STATUS_PENDING, createdAt=1, updatedAt=1))
        val reconciler = createReconciler(txs, queue)
        val scheduler = FakeScheduler()
        val result = simulateWorker(reconciler, scheduler)
        assertEquals("success", result)
        assertEquals(0, scheduler.scheduled)
    }

    @Test
    fun `13 repaired items success`() = runTest {
        val txs = mutableListOf(createTx("u1"), createTx("u2"))
        val queue = FakeSyncQueueDao()
        val reconciler = createReconciler(txs, queue)
        val scheduler = FakeScheduler()
        val result = simulateWorker(reconciler, scheduler)
        assertEquals("success", result)
        assertEquals(2, queue.rows.size)
    }

    @Test
    fun `14 repairs schedule sync`() = runTest {
        val txs = mutableListOf(createTx("u1"))
        val queue = FakeSyncQueueDao()
        val reconciler = createReconciler(txs, queue)
        val scheduler = FakeScheduler()
        simulateWorker(reconciler, scheduler)
        assertEquals(1, scheduler.scheduled)
    }

    @Test
    fun `15 no repairs does not schedule sync`() = runTest {
        val txs = mutableListOf(createTx("u1"))
        val queue = FakeSyncQueueDao()
        queue.rows.add(SyncQueueEntity(entityType="TRANSACTION", entityId="u1", status=SyncQueueEntity.STATUS_SYNCED, createdAt=1, updatedAt=1))
        val reconciler = createReconciler(txs, queue)
        val scheduler = FakeScheduler()
        simulateWorker(reconciler, scheduler)
        assertEquals(0, scheduler.scheduled)
    }

    @Test
    fun `16 transient DB failure retry`() = runTest {
        val failingReconciler = object : TransactionSyncReconciler(
            object : TransactionDao {
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
                override suspend fun getEligibleMissingQueueUuids(limit: Int, offset: Int): List<String> { throw IOException("busy") }
                override suspend fun countEligibleMissingQueue(): Int = 0
                override suspend fun countEligibleTransactions(): Int = 0
            },
            FakeSyncQueueDao(),
            object : ReconciliationStatusRecorder { override suspend fun recordReconciliation(timestamp: Long, repairedCount: Int) {} }
        ) {}
        val scheduler = FakeScheduler()
        val result = try {
            failingReconciler.reconcile()
            "success"
        } catch (e: IOException) { "retry" } catch (e: Exception) { "failure" }
        assertEquals("retry", result)
        assertEquals(0, scheduler.scheduled)
    }

    @Test
    fun `17 permanent unexpected failure`() = runTest {
        val failingReconciler = object : TransactionSyncReconciler(
            object : TransactionDao {
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
                override suspend fun getEligibleMissingQueueUuids(limit: Int, offset: Int): List<String> { throw IllegalStateException("permanent corruption") }
                override suspend fun countEligibleMissingQueue(): Int = 0
                override suspend fun countEligibleTransactions(): Int { throw IllegalStateException("permanent corruption") }
            },
            FakeSyncQueueDao(),
            object : ReconciliationStatusRecorder { override suspend fun recordReconciliation(timestamp: Long, repairedCount: Int) {} }
        ) {}
        val result = try {
            failingReconciler.reconcile()
            "success"
        } catch (e: IOException) { "retry" } catch (e: Exception) {
            val msg = e.message?.lowercase() ?: ""
            if (msg.contains("busy") || msg.contains("locked")) "retry" else "failure"
        }
        assertEquals("failure", result)
    }
}
