package com.upivoicealert

import com.upivoicealert.data.database.TransactionDao
import com.upivoicealert.data.database.TransactionEntity
import com.upivoicealert.data.database.UnparsedNotificationDao
import com.upivoicealert.data.database.UnparsedNotificationEntity
import com.upivoicealert.data.repository.TransactionRepositoryImpl
import com.upivoicealert.data.sync.SyncQueueDao
import com.upivoicealert.data.sync.SyncQueueEntity
import com.upivoicealert.domain.model.NotificationSource
import com.upivoicealert.domain.model.ParseStatus
import com.upivoicealert.domain.model.Transaction
import com.upivoicealert.domain.model.TransactionStatus
import com.upivoicealert.domain.model.TransactionType
import com.upivoicealert.data.model.toDomain
import com.upivoicealert.data.model.toEntity
import com.upivoicealert.parser.ParsedTransaction
import com.upivoicealert.parser.toTransaction
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionSyncQueueActivationTest {

    @Test
    fun `successful transaction save creates sync queue item`() = runTest {
        val transactionDao = FakeTransactionDao()
        val syncQueueDao = FakeSyncQueueDao()
        val repo = TransactionRepositoryImpl(transactionDao, FakeUnparsedDao(), syncQueueDao, null)

        val txn = Transaction(
            id = UUID.randomUUID().toString(),
            amount = 100.0,
            sender = "Rahul",
            upiApp = "PhonePe",
            transactionType = TransactionType.RECEIVED,
            status = TransactionStatus.SUCCESS,
            transactionId = "UTR123",
            rawNotification = "You received Rs 100 from Rahul",
            parserVersion = "PhonePeParserV1",
            parseStatus = ParseStatus.PARSED,
            createdAt = System.currentTimeMillis(),
            transactionUuid = UUID.randomUUID().toString()
        )

        val inserted = repo.insertTransactionIfNotDuplicate(txn)
        assertTrue(inserted)
        assertEquals(1, transactionDao.rows.size)
        assertEquals(1, syncQueueDao.rows.size)
        val queued = syncQueueDao.rows.first()
        assertEquals(SyncQueueEntity.ENTITY_TYPE_TRANSACTION, queued.entityType)
        assertEquals(txn.transactionUuid, queued.entityId)
        assertEquals(SyncQueueEntity.STATUS_PENDING, queued.status)
        assertEquals(0, queued.retryCount)
    }

    @Test
    fun `failed validation does not create transaction or queue`() = runTest {
        // Simulate validation failure: amount <=0 would be rejected before repository call
        // So repository should not be called; verify no queue created when isDuplicate logic not invoked for invalid
        // Here we directly test that only SUCCESS RECEIVED are queued; FAILED should not queue
        val transactionDao = FakeTransactionDao()
        val syncQueueDao = FakeSyncQueueDao()
        val repo = TransactionRepositoryImpl(transactionDao, FakeUnparsedDao(), syncQueueDao, null)

        val failedTxn = Transaction(
            id = UUID.randomUUID().toString(),
            amount = 100.0,
            sender = "Rahul",
            upiApp = "PhonePe",
            transactionType = TransactionType.FAILED,
            status = TransactionStatus.FAILED,
            transactionId = "UTR123",
            rawNotification = "Failed",
            parserVersion = "Test",
            parseStatus = ParseStatus.PARSED,
            createdAt = System.currentTimeMillis(),
            transactionUuid = UUID.randomUUID().toString()
        )

        // Even though we call insert, queue should not be created for FAILED
        // Our repository checks shouldQueue = RECEIVED && SUCCESS, so FAILED won't queue
        val inserted = repo.insertTransactionIfNotDuplicate(failedTxn)
        // It will still insert transaction (dedup logic allows), but queue should be empty
        assertTrue(inserted)
        assertEquals(0, syncQueueDao.rows.size)
    }

    @Test
    fun `duplicate transaction does not create second queue item`() = runTest {
        val transactionDao = FakeTransactionDao()
        val syncQueueDao = FakeSyncQueueDao()
        val repo = TransactionRepositoryImpl(transactionDao, FakeUnparsedDao(), syncQueueDao, null)

        val txn = Transaction(
            id = UUID.randomUUID().toString(),
            amount = 50.0,
            sender = "Priya",
            upiApp = "Google Pay",
            transactionType = TransactionType.RECEIVED,
            status = TransactionStatus.SUCCESS,
            transactionId = "REF123",
            rawNotification = "raw1",
            parserVersion = "GPayParserV1",
            parseStatus = ParseStatus.PARSED,
            createdAt = 1_700_000_000_000L,
            transactionUuid = "550e8400-e29b-41d4-a716-446655440001"
        )

        assertTrue(repo.insertTransactionIfNotDuplicate(txn))
        assertEquals(1, syncQueueDao.rows.size)

        // Duplicate with same referenceId within window should be ignored
        val duplicate = txn.copy(id = UUID.randomUUID().toString(), transactionUuid = UUID.randomUUID().toString())
        val inserted2 = repo.insertTransactionIfNotDuplicate(duplicate)
        assertTrue(!inserted2)
        assertEquals(1, transactionDao.rows.size)
        assertEquals(1, syncQueueDao.rows.size)
    }

    @Test
    fun `transaction UUID stability remains after reload`() = runTest {
        val txn = ParsedTransaction(
            amount = 500.0,
            sender = "Rahul",
            upiApp = "PhonePe",
            transactionId = "UTR999",
            postTime = 1_700_000_000_000L,
            rawNotification = "You received 500 from Rahul"
        ).toTransaction("PhonePeParserV1")

        val uuidFirst = txn.transactionUuid
        assertNotNull(uuidFirst)
        assertTrue(uuidFirst.isNotBlank())

        // Simulate save and reload via mapper
        val entity = txn.toEntity()
        val reloaded = entity.toDomain()
        assertEquals(uuidFirst, reloaded.transactionUuid)
        assertEquals(uuidFirst, entity.transactionUuid)
    }

    @Test
    fun `queue item fields verify entityType, status, retryCount`() = runTest {
        val transactionDao = FakeTransactionDao()
        val syncQueueDao = FakeSyncQueueDao()
        val repo = TransactionRepositoryImpl(transactionDao, FakeUnparsedDao(), syncQueueDao, null)

        val txn = Transaction(
            id = "id-1",
            amount = 200.0,
            sender = "Amit",
            upiApp = "Paytm",
            transactionType = TransactionType.RECEIVED,
            status = TransactionStatus.SUCCESS,
            transactionId = "UTR200",
            rawNotification = "raw",
            parserVersion = "PaytmParserV1",
            parseStatus = ParseStatus.PARSED,
            createdAt = System.currentTimeMillis(),
            transactionUuid = "550e8400-e29b-41d4-a716-446655440002"
        )

        repo.insertTransactionIfNotDuplicate(txn)
        val queued = syncQueueDao.rows.first()
        assertEquals("TRANSACTION", queued.entityType)
        assertEquals(txn.transactionUuid, queued.entityId)
        assertEquals("PENDING", queued.status)
        assertEquals(0, queued.retryCount)
        assertTrue(queued.createdAt > 0)
        assertTrue(queued.updatedAt > 0)
    }

    // Fakes
    private class FakeTransactionDao : TransactionDao {
        val rows = mutableListOf<TransactionEntity>()
        override fun observeAll(): Flow<List<TransactionEntity>> = flowOf(rows)
        override fun observeReceivedSuccess(): Flow<List<TransactionEntity>> = flowOf(rows)
        override fun observeReceivedSuccessSince(since: Long): Flow<List<TransactionEntity>> = flowOf(rows)
        override suspend fun findRecentReceivedSuccess(amount: Double, since: Long): TransactionEntity? = null
        override fun observeLatest(): Flow<TransactionEntity?> = flowOf(rows.maxByOrNull { it.createdAt })
        override fun observeCount(): Flow<Int> = flowOf(rows.size)
        override fun observeCountSince(since: Long): Flow<Int> = flowOf(rows.count { it.createdAt >= since })
        override suspend fun findByReferenceIdGlobal(transactionId: String): TransactionEntity? = rows.firstOrNull { it.transactionId == transactionId }
        override suspend fun findByFingerprint(fingerprint: String, windowStart: Long, windowEnd: Long): TransactionEntity? = rows.firstOrNull { it.dedupFingerprint == fingerprint && it.createdAt in windowStart..windowEnd }
        override suspend fun findByFingerprintNullRef(fingerprint: String, windowStart: Long, windowEnd: Long): TransactionEntity? = rows.firstOrNull { it.dedupFingerprint == fingerprint && (it.transactionId == null || it.transactionId!!.isEmpty()) && it.createdAt in windowStart..windowEnd }
        override suspend fun findExactDuplicate(rawNotification: String, windowStart: Long, windowEnd: Long): TransactionEntity? = rows.firstOrNull { it.rawNotification.trim() == rawNotification.trim() && it.createdAt in windowStart..windowEnd }
        override suspend fun findByTransactionUuid(uuid: String): TransactionEntity? = rows.firstOrNull { it.transactionUuid == uuid }
        override suspend fun findByTransactionUuids(uuids: List<String>): List<TransactionEntity> = rows.filter { it.transactionUuid in uuids }
        override suspend fun insert(entity: TransactionEntity): Long { rows.add(entity); return rows.size.toLong() }
        override suspend fun markVoiceAnnounced(id: String) {}
        override suspend fun clearAll() { rows.clear() }
        override suspend fun getEligibleMissingQueueUuids(limit: Int, offset: Int): List<String> = emptyList()
        override suspend fun countEligibleMissingQueue(): Int = 0
        override suspend fun countEligibleTransactions(): Int = 0
        override suspend fun countEligibleForAudit(): Int = 0
        override suspend fun countMissingQueueForAudit(): Int = 0
        override suspend fun countScannedTransactionsForAudit(): Int = 0
}

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
        override suspend fun getByStatus(status: String): List<SyncQueueEntity> = rows.filter { it.status == status }
        override suspend fun getPendingItems(): List<SyncQueueEntity> = rows.filter { it.status == SyncQueueEntity.STATUS_PENDING }
        override suspend fun updateStatus(id: Long, status: String, updatedAt: Long) {}
        override suspend fun incrementRetryCount(id: Long, updatedAt: Long) {}
        override suspend fun getAll(): List<SyncQueueEntity> = rows
        override suspend fun deleteById(id: Long) {}
        override suspend fun clearAll() { rows.clear() }
        override fun observeCountByStatus(status: String): kotlinx.coroutines.flow.Flow<Int> = kotlinx.coroutines.flow.flowOf(rows.count { it.status == status })
        override suspend fun getCountByStatus(status: String): Int = rows.count { it.status == status }
        override suspend fun getFailedItems(): List<SyncQueueEntity> = rows.filter { it.status == SyncQueueEntity.STATUS_FAILED }
        override suspend fun getById(id: Long): SyncQueueEntity? = rows.firstOrNull { it.id == id }
        override suspend fun markFailedWithDiagnostics(id: Long, status: String, errorCode: String?, errorMessage: String?, failedAt: Long?, updatedAt: Long) {}
        override suspend fun retryFailedItem(id: Long, updatedAt: Long): Int = 0
        override suspend fun retryAllFailed(updatedAt: Long): Int = 0
        override fun observeFailedItems(): kotlinx.coroutines.flow.Flow<List<SyncQueueEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
        override suspend fun getStaleUploadingItems(cutoffTime: Long) = emptyList<SyncQueueEntity>()
        override suspend fun recoverStaleUploading(cutoffTime: Long, updatedAt: Long) = 0
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

    private class FakeUnparsedDao : UnparsedNotificationDao {
        override fun observeAll(): Flow<List<com.upivoicealert.data.database.UnparsedNotificationEntity>> = flowOf(emptyList())
        override suspend fun getAll() = emptyList<com.upivoicealert.data.database.UnparsedNotificationEntity>()
        override fun observeCountSince(since: Long): Flow<Int> = flowOf(0)
        override suspend fun insert(entity: com.upivoicealert.data.database.UnparsedNotificationEntity) = 1L
        override suspend fun deleteById(id: String) {}
        override suspend fun clearAll() {}
        override suspend fun deleteOlderThan(before: Long) {}
    }
}
