package com.upivoicealert

import com.upivoicealert.data.database.TransactionDao
import com.upivoicealert.data.database.TransactionEntity
import com.upivoicealert.data.sync.SyncQueueDao
import com.upivoicealert.data.sync.SyncQueueEntity
import com.upivoicealert.data.sync.TransactionSyncApi
import com.upivoicealert.data.sync.TransactionSyncRepository
import com.upivoicealert.data.sync.TransactionSyncRequestDto
import com.upivoicealert.data.sync.TransactionSyncResponseDto
import com.upivoicealert.utils.DeviceIdProvider
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class TransactionSyncDiagnosticsTest {

    private fun createTransactionEntity(uuid: String): TransactionEntity {
        return TransactionEntity(
            id = "id-$uuid", amount = 100.0, sender = "Rahul", upiApp = "PhonePe",
            transactionType = "RECEIVED", status = "SUCCESS", transactionId = "UTR-$uuid",
            rawNotification = "raw", parserVersion = "PhonePeParserV1", parseStatus = "PARSED",
            createdAt = System.currentTimeMillis(), sourceType = "UNKNOWN", packageName = "com.phonepe.app",
            notificationKey = null, originalNotificationText = "raw", cleanedNotificationText = "raw",
            voiceAnnounced = true, transactionUuid = uuid
        )
    }

    private class FakeTransactionDao(val transactions: MutableMap<String, TransactionEntity> = mutableMapOf()) : TransactionDao {
        override fun observeAll(): Flow<List<TransactionEntity>> = flowOf(transactions.values.toList())
        override fun observeReceivedSuccess(): Flow<List<TransactionEntity>> = flowOf(transactions.values.toList())
        override fun observeReceivedSuccessSince(since: Long): Flow<List<TransactionEntity>> = flowOf(emptyList())
        override suspend fun findRecentReceivedSuccess(amount: Double, since: Long): TransactionEntity? = null
        override fun observeLatest(): Flow<TransactionEntity?> = flowOf(null)
        override fun observeCount(): Flow<Int> = flowOf(transactions.size)
        override fun observeCountSince(since: Long): Flow<Int> = flowOf(0)
        override suspend fun findByReferenceIdGlobal(transactionId: String): TransactionEntity? = null
        override suspend fun findByFingerprint(fingerprint: String, windowStart: Long, windowEnd: Long): TransactionEntity? = null
        override suspend fun findByFingerprintNullRef(fingerprint: String, windowStart: Long, windowEnd: Long): TransactionEntity? = null
        override suspend fun findExactDuplicate(rawNotification: String, windowStart: Long, windowEnd: Long): TransactionEntity? = null
        override suspend fun findByTransactionUuid(uuid: String): TransactionEntity? = transactions[uuid]
        override suspend fun findByTransactionUuids(uuids: List<String>): List<TransactionEntity> = uuids.mapNotNull { transactions[it] }
        override suspend fun insert(entity: TransactionEntity): Long = 1L
        override suspend fun markVoiceAnnounced(id: String) {}
        override suspend fun clearAll() {}
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
        override suspend fun getAll() = rows
        override suspend fun deleteById(id: Long) { rows.removeIf { it.id == id } }
        override suspend fun clearAll() { rows.clear() }
        override fun observeCountByStatus(status: String) = kotlinx.coroutines.flow.flowOf(rows.count { it.status == status })
        override suspend fun getCountByStatus(status: String) = rows.count { it.status == status }
        override suspend fun getFailedItems() = rows.filter { it.status == SyncQueueEntity.STATUS_FAILED }.sortedByDescending { it.failedAt ?: 0 }
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
        override suspend fun getStaleUploadingItems(cutoffTime: Long) = rows.filter { it.status == SyncQueueEntity.STATUS_UPLOADING && it.updatedAt < cutoffTime }
        override suspend fun recoverStaleUploading(cutoffTime: Long, updatedAt: Long): Int {
            var c = 0
            rows.forEachIndexed { idx, e -> if (e.status == SyncQueueEntity.STATUS_UPLOADING && e.updatedAt < cutoffTime) { rows[idx] = e.copy(status = SyncQueueEntity.STATUS_PENDING, updatedAt = updatedAt); c++ } }
            return c
        }
        override suspend fun countTransactionQueueItems(): Int = 0
        override suspend fun countAllQueueItems(): Int = 0
        override suspend fun countOrphanedQueueItems(): Int = 0
        override suspend fun countDuplicateExtraRows(): Int = 0
        override suspend fun countDuplicateGroups(): Int = 0
        override suspend fun countInvalidQueueItems(): Int = 0
        override suspend fun countStaleUploading(cutoffTime: Long): Int = 0
}

    private fun createRepo(transactionDao: FakeTransactionDao, dao: FakeSyncQueueDao, api: TransactionSyncApi) =
        TransactionSyncRepository(dao, transactionDao, api, object : DeviceIdProvider { override suspend fun getDeviceId() = "test-device" })

    @Test
    fun `12 HTTP 400 stores FAILED`() = runTest {
        val uuid = "550e8400-e29b-41d4-a716-446655440012"
        val dao = FakeSyncQueueDao()
        dao.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = uuid, status = "PENDING", createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
        val txnDao = FakeTransactionDao(mutableMapOf(uuid to createTransactionEntity(uuid)))
        val api = object : TransactionSyncApi {
            override suspend fun syncTransactions(request: TransactionSyncRequestDto): TransactionSyncResponseDto {
                throw HttpException(Response.error<TransactionSyncResponseDto>(400, "validation failed".toResponseBody(null)))
            }
        }
        val repo = createRepo(txnDao, dao, api)
        val result = repo.syncPendingTransactions()
        assertTrue(result is TransactionSyncRepository.SyncResult.Error)
        assertEquals(SyncQueueEntity.STATUS_FAILED, dao.rows.first().status)
    }

    @Test
    fun `13 FAILED stores safe error code`() = runTest {
        val uuid = "550e8400-e29b-41d4-a716-446655440013"
        val dao = FakeSyncQueueDao()
        dao.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = uuid, status = "PENDING", createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
        val txnDao = FakeTransactionDao(mutableMapOf(uuid to createTransactionEntity(uuid)))
        val api = object : TransactionSyncApi {
            override suspend fun syncTransactions(request: TransactionSyncRequestDto): TransactionSyncResponseDto {
                throw HttpException(Response.error<TransactionSyncResponseDto>(400, "bad".toResponseBody(null)))
            }
        }
        val repo = createRepo(txnDao, dao, api)
        repo.syncPendingTransactions()
        assertEquals(TransactionSyncRepository.ERROR_CODE_VALIDATION, dao.rows.first().lastErrorCode)
    }

    @Test
    fun `14 FAILED stores safe message`() = runTest {
        val uuid = "550e8400-e29b-41d4-a716-446655440014"
        val dao = FakeSyncQueueDao()
        dao.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = uuid, status = "PENDING", createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
        val txnDao = FakeTransactionDao(mutableMapOf(uuid to createTransactionEntity(uuid)))
        val api = object : TransactionSyncApi {
            override suspend fun syncTransactions(request: TransactionSyncRequestDto): TransactionSyncResponseDto {
                throw HttpException(Response.error<TransactionSyncResponseDto>(400, "whatever server says including PrismaClientKnownRequestError P2002".toResponseBody(null)))
            }
        }
        val repo = createRepo(txnDao, dao, api)
        repo.syncPendingTransactions()
        assertEquals(TransactionSyncRepository.ERROR_MSG_VALIDATION, dao.rows.first().lastErrorMessage)
        assertFalse(dao.rows.first().lastErrorMessage!!.contains("Prisma"))
    }

    @Test
    fun `15 FAILED stores failedAt`() = runTest {
        val uuid = "550e8400-e29b-41d4-a716-446655440015"
        val dao = FakeSyncQueueDao()
        dao.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = uuid, status = "PENDING", createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
        val txnDao = FakeTransactionDao(mutableMapOf(uuid to createTransactionEntity(uuid)))
        val before = System.currentTimeMillis()
        val api = object : TransactionSyncApi {
            override suspend fun syncTransactions(request: TransactionSyncRequestDto): TransactionSyncResponseDto {
                throw HttpException(Response.error<TransactionSyncResponseDto>(400, "".toResponseBody(null)))
            }
        }
        createRepo(txnDao, dao, api).syncPendingTransactions()
        val after = System.currentTimeMillis()
        val failedAt = dao.rows.first().failedAt
        assertNotNull(failedAt)
        assertTrue(failedAt!! in before..after)
    }

    @Test
    fun `16 raw backend error is not stored directly`() = runTest {
        val uuid = "550e8400-e29b-41d4-a716-446655440016"
        val dao = FakeSyncQueueDao()
        dao.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = uuid, status = "PENDING", createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
        val txnDao = FakeTransactionDao(mutableMapOf(uuid to createTransactionEntity(uuid)))
        val rawError = "Jwt malformed token eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9  stack trace at com.example.secret"
        val api = object : TransactionSyncApi {
            override suspend fun syncTransactions(request: TransactionSyncRequestDto): TransactionSyncResponseDto {
                throw HttpException(Response.error<TransactionSyncResponseDto>(400, rawError.toResponseBody(null)))
            }
        }
        createRepo(txnDao, dao, api).syncPendingTransactions()
        val msg = dao.rows.first().lastErrorMessage
        assertNotNull(msg)
        assertFalse(msg!!.contains("eyJhbG"))
        assertFalse(msg.contains("stack trace"))
        assertEquals(TransactionSyncRepository.ERROR_MSG_VALIDATION, msg)
    }

    @Test
    fun `17 network failure stays PENDING`() = runTest {
        val uuid = "550e8400-e29b-41d4-a716-446655440017"
        val dao = FakeSyncQueueDao()
        dao.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = uuid, status = "PENDING", createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
        val txnDao = FakeTransactionDao(mutableMapOf(uuid to createTransactionEntity(uuid)))
        val api = object : TransactionSyncApi {
            override suspend fun syncTransactions(request: TransactionSyncRequestDto): TransactionSyncResponseDto {
                throw IOException("no internet")
            }
        }
        val result = createRepo(txnDao, dao, api).syncPendingTransactions()
        assertTrue(result is TransactionSyncRepository.SyncResult.Retry)
        assertEquals(SyncQueueEntity.STATUS_PENDING, dao.rows.first().status)
        assertNull(dao.rows.first().lastErrorCode)
        assertNull(dao.rows.first().failedAt)
        assertEquals(1, dao.rows.first().retryCount)
    }

    @Test
    fun `18 500 stays PENDING`() = runTest {
        val uuid = "550e8400-e29b-41d4-a716-446655440018"
        val dao = FakeSyncQueueDao()
        dao.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = uuid, status = "PENDING", createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
        val txnDao = FakeTransactionDao(mutableMapOf(uuid to createTransactionEntity(uuid)))
        val api = object : TransactionSyncApi {
            override suspend fun syncTransactions(request: TransactionSyncRequestDto): TransactionSyncResponseDto {
                throw HttpException(Response.error<TransactionSyncResponseDto>(500, "".toResponseBody(null)))
            }
        }
        val result = createRepo(txnDao, dao, api).syncPendingTransactions()
        assertTrue(result is TransactionSyncRepository.SyncResult.Retry)
        assertEquals(SyncQueueEntity.STATUS_PENDING, dao.rows.first().status)
        assertNull(dao.rows.first().lastErrorCode)
    }

    @Test
    fun `19 session expiration does not become validation FAILED`() = runTest {
        val uuid = "550e8400-e29b-41d4-a716-446655440019"
        val dao = FakeSyncQueueDao()
        dao.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = uuid, status = "PENDING", createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
        val txnDao = FakeTransactionDao(mutableMapOf(uuid to createTransactionEntity(uuid)))
        val api = object : TransactionSyncApi {
            override suspend fun syncTransactions(request: TransactionSyncRequestDto): TransactionSyncResponseDto {
                throw HttpException(Response.error<TransactionSyncResponseDto>(401, "".toResponseBody(null)))
            }
        }
        val result = createRepo(txnDao, dao, api).syncPendingTransactions()
        assertTrue(result is TransactionSyncRepository.SyncResult.SessionExpired)
        assertEquals(SyncQueueEntity.STATUS_PENDING, dao.rows.first().status)
        assertNull(dao.rows.first().lastErrorCode)
        assertNull(dao.rows.first().failedAt)
    }

    @Test
    fun `other 4xx stores SYNC_REJECTED`() = runTest {
        val uuid = "550e8400-e29b-41d4-a716-446655440020"
        val dao = FakeSyncQueueDao()
        dao.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = uuid, status = "PENDING", createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
        val txnDao = FakeTransactionDao(mutableMapOf(uuid to createTransactionEntity(uuid)))
        val api = object : TransactionSyncApi {
            override suspend fun syncTransactions(request: TransactionSyncRequestDto): TransactionSyncResponseDto {
                throw HttpException(Response.error<TransactionSyncResponseDto>(403, "".toResponseBody(null)))
            }
        }
        createRepo(txnDao, dao, api).syncPendingTransactions()
        assertEquals(TransactionSyncRepository.ERROR_CODE_REJECTED, dao.rows.first().lastErrorCode)
        assertEquals(TransactionSyncRepository.ERROR_MSG_REJECTED, dao.rows.first().lastErrorMessage)
    }

    @Test
    fun `missing transaction marks FAILED with diagnostics`() = runTest {
        val uuid = "non-existent-uuid"
        val dao = FakeSyncQueueDao()
        dao.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = uuid, status = "PENDING", createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
        val txnDao = FakeTransactionDao(mutableMapOf()) // empty, so missing
        val api = object : TransactionSyncApi {
            override suspend fun syncTransactions(request: TransactionSyncRequestDto): TransactionSyncResponseDto {
                // Should not be called if missing? But our impl marks FAILED before API call
                // For missing case, the batch will have no uploads, returns Success
                // So verify missing path stores diagnostics
                return TransactionSyncResponseDto(emptyList(), emptyList())
            }
        }
        // Even though no API call, the missing txn is already marked FAILED with missing diagnostics logic
        // Our syncBatch marks missing as FAILED via markFailedWithDiagnostics before early return
        // However current impl returns Success for empty uploads; the FAILED still persists
        val repo = createRepo(txnDao, dao, api)
        // Need pending that will be processed; uuid not in txnDao
        repo.syncPendingTransactions()
        // The queue should be FAILED with ERROR_MSG_MISSING
        // But due to early return logic, success is returned and queue remains FAILED
        // Verify diagnostic stored
        val entity = dao.rows.first()
        assertEquals(SyncQueueEntity.STATUS_FAILED, entity.status)
        assertEquals(TransactionSyncRepository.ERROR_CODE_VALIDATION, entity.lastErrorCode)
    }
}
