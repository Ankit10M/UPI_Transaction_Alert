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
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class TransactionSyncRepositoryTest {

    private fun createTransactionEntity(uuid: String, amount: Double = 100.0): TransactionEntity {
        return TransactionEntity(
            id = "id-$uuid",
            amount = amount,
            sender = "Rahul",
            upiApp = "PhonePe",
            transactionType = "RECEIVED",
            status = "SUCCESS",
            transactionId = "UTR-$uuid",
            rawNotification = "raw",
            parserVersion = "PhonePeParserV1",
            parseStatus = "PARSED",
            createdAt = System.currentTimeMillis(),
            sourceType = "UNKNOWN",
            packageName = "com.phonepe.app",
            notificationKey = null,
            originalNotificationText = "raw",
            cleanedNotificationText = "raw",
            voiceAnnounced = true,
            transactionUuid = uuid
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
        override suspend fun getByStatus(status: String): List<SyncQueueEntity> = rows.filter { it.status == status }
        override suspend fun getPendingItems(): List<SyncQueueEntity> = rows.filter { it.status == SyncQueueEntity.STATUS_PENDING }.sortedBy { it.createdAt }
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
        override suspend fun getAll(): List<SyncQueueEntity> = rows
        override suspend fun deleteById(id: Long) { rows.removeIf { it.id == id } }
        override suspend fun clearAll() { rows.clear() }
        override fun observeCountByStatus(status: String): kotlinx.coroutines.flow.Flow<Int> = kotlinx.coroutines.flow.flowOf(rows.count { it.status == status })
        override suspend fun getCountByStatus(status: String): Int = rows.count { it.status == status }
        override suspend fun getFailedItems(): List<SyncQueueEntity> = rows.filter { it.status == SyncQueueEntity.STATUS_FAILED }.sortedByDescending { it.failedAt ?: 0 }
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
            rows.forEachIndexed { idx, entity ->
                if (entity.status == SyncQueueEntity.STATUS_FAILED) {
                    rows[idx] = entity.copy(status = SyncQueueEntity.STATUS_PENDING, lastErrorCode = null, lastErrorMessage = null, failedAt = null, updatedAt = updatedAt)
                    count++
                }
            }
            return count
        }
        override fun observeFailedItems(): kotlinx.coroutines.flow.Flow<List<SyncQueueEntity>> = kotlinx.coroutines.flow.flowOf(rows.filter { it.status == SyncQueueEntity.STATUS_FAILED })
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

    private class FakeDeviceIdProvider : DeviceIdProvider {
        override suspend fun getDeviceId(): String = "test-device-123"
    }

    private fun createRepository(
        transactionDao: FakeTransactionDao,
        syncQueueDao: FakeSyncQueueDao,
        api: TransactionSyncApi
    ): TransactionSyncRepository {
        return TransactionSyncRepository(syncQueueDao, transactionDao, api, FakeDeviceIdProvider())
    }

    @Test
    fun `pending transaction uploaded successfully marks SYNCED`() = runTest {
        val uuid = "550e8400-e29b-41d4-a716-446655440001"
        val transactionDao = FakeTransactionDao(mutableMapOf(uuid to createTransactionEntity(uuid)))
        val syncQueueDao = FakeSyncQueueDao()
        syncQueueDao.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = uuid, status = "PENDING", createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))

        val api = object : TransactionSyncApi {
            override suspend fun syncTransactions(request: TransactionSyncRequestDto): TransactionSyncResponseDto {
                assertEquals(1, request.transactions.size)
                assertEquals(uuid, request.transactions[0].transactionUuid)
                // Verify DTO does not contain forbidden fields
                val json = com.google.gson.Gson().toJson(request)
                assertTrue(!json.contains("merchantId"))
                assertTrue(!json.contains("firebaseUid"))
                return TransactionSyncResponseDto(created = listOf(uuid), duplicates = emptyList())
            }
        }

        val repo = createRepository(transactionDao, syncQueueDao, api)
        val result = repo.syncPendingTransactions()
        assertTrue(result is TransactionSyncRepository.SyncResult.Success)
        assertEquals(SyncQueueEntity.STATUS_SYNCED, syncQueueDao.rows.first().status)
    }

    @Test
    fun `backend duplicate response marks SYNCED`() = runTest {
        val uuid = "550e8400-e29b-41d4-a716-446655440002"
        val transactionDao = FakeTransactionDao(mutableMapOf(uuid to createTransactionEntity(uuid)))
        val syncQueueDao = FakeSyncQueueDao()
        syncQueueDao.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = uuid, status = "PENDING", createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))

        val api = object : TransactionSyncApi {
            override suspend fun syncTransactions(request: TransactionSyncRequestDto): TransactionSyncResponseDto {
                return TransactionSyncResponseDto(created = emptyList(), duplicates = listOf(uuid))
            }
        }

        val repo = createRepository(transactionDao, syncQueueDao, api)
        val result = repo.syncPendingTransactions()
        assertTrue(result is TransactionSyncRepository.SyncResult.Success)
        assertEquals(SyncQueueEntity.STATUS_SYNCED, syncQueueDao.rows.first().status)
    }

    @Test
    fun `network failure returns Retry and increments retryCount`() = runTest {
        val uuid = "550e8400-e29b-41d4-a716-446655440003"
        val transactionDao = FakeTransactionDao(mutableMapOf(uuid to createTransactionEntity(uuid)))
        val syncQueueDao = FakeSyncQueueDao()
        syncQueueDao.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = uuid, status = "PENDING", createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))

        val api = object : TransactionSyncApi {
            override suspend fun syncTransactions(request: TransactionSyncRequestDto): TransactionSyncResponseDto {
                throw IOException("no internet")
            }
        }

        val repo = createRepository(transactionDao, syncQueueDao, api)
        val result = repo.syncPendingTransactions()
        assertTrue(result is TransactionSyncRepository.SyncResult.Retry)
        // retryCount incremented and status reverted to PENDING
        assertEquals(1, syncQueueDao.rows.first().retryCount)
        assertEquals(SyncQueueEntity.STATUS_PENDING, syncQueueDao.rows.first().status)
    }

    @Test
    fun `API 500 failure increments retryCount and returns Retry`() = runTest {
        val uuid = "550e8400-e29b-41d4-a716-446655440004"
        val transactionDao = FakeTransactionDao(mutableMapOf(uuid to createTransactionEntity(uuid)))
        val syncQueueDao = FakeSyncQueueDao()
        syncQueueDao.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = uuid, status = "PENDING", createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))

        val api = object : TransactionSyncApi {
            override suspend fun syncTransactions(request: TransactionSyncRequestDto): TransactionSyncResponseDto {
                throw HttpException(Response.error<TransactionSyncResponseDto>(500, "".toResponseBody(null)))
            }
        }

        val repo = createRepository(transactionDao, syncQueueDao, api)
        val result = repo.syncPendingTransactions()
        assertTrue(result is TransactionSyncRepository.SyncResult.Retry)
        assertEquals(1, syncQueueDao.rows.first().retryCount)
    }

    @Test
    fun `validation error marks FAILED`() = runTest {
        val uuid = "550e8400-e29b-41d4-a716-446655440005"
        val transactionDao = FakeTransactionDao(mutableMapOf(uuid to createTransactionEntity(uuid)))
        val syncQueueDao = FakeSyncQueueDao()
        syncQueueDao.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = uuid, status = "PENDING", createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))

        val api = object : TransactionSyncApi {
            override suspend fun syncTransactions(request: TransactionSyncRequestDto): TransactionSyncResponseDto {
                throw HttpException(Response.error<TransactionSyncResponseDto>(400, "".toResponseBody(null)))
            }
        }

        val repo = createRepository(transactionDao, syncQueueDao, api)
        val result = repo.syncPendingTransactions()
        // 400 is mapped to Error, and batch marked FAILED
        assertTrue(result is TransactionSyncRepository.SyncResult.Error)
        assertEquals(SyncQueueEntity.STATUS_FAILED, syncQueueDao.rows.first().status)
    }

    @Test
    fun `batch size max 100 - 250 items split into 3 batches`() = runTest {
        val syncQueueDao = FakeSyncQueueDao()
        val transactionMap = mutableMapOf<String, TransactionEntity>()
        // Create 250 pending items
        repeat(250) { i ->
            val uuid = "550e8400-e29b-41d4-a716-44665544${String.format("%04d", i)}".take(36)
            // Ensure valid UUID format: use fixed prefix + padded
            val validUuid = UUID.randomUUID().toString()
            transactionMap[validUuid] = createTransactionEntity(validUuid)
            syncQueueDao.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = validUuid, status = "PENDING", createdAt = System.currentTimeMillis() + i, updatedAt = System.currentTimeMillis() + i))
        }
        val transactionDao = FakeTransactionDao(transactionMap)
        var callCount = 0
        val batchSizes = mutableListOf<Int>()
        val api = object : TransactionSyncApi {
            override suspend fun syncTransactions(request: TransactionSyncRequestDto): TransactionSyncResponseDto {
                callCount++
                batchSizes.add(request.transactions.size)
                assertTrue(request.transactions.size <= 100)
                return TransactionSyncResponseDto(created = request.transactions.map { it.transactionUuid }, duplicates = emptyList())
            }
        }

        val repo = createRepository(transactionDao, syncQueueDao, api)
        val result = repo.syncPendingTransactions()
        assertTrue(result is TransactionSyncRepository.SyncResult.Success)
        assertEquals(3, callCount)
        assertEquals(listOf(100, 100, 50), batchSizes)
        // All should be SYNCED
        assertTrue(syncQueueDao.rows.all { it.status == SyncQueueEntity.STATUS_SYNCED })
    }

    @Test
    fun `401 returns SessionExpired`() = runTest {
        val uuid = "550e8400-e29b-41d4-a716-446655440006"
        val transactionDao = FakeTransactionDao(mutableMapOf(uuid to createTransactionEntity(uuid)))
        val syncQueueDao = FakeSyncQueueDao()
        syncQueueDao.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = uuid, status = "PENDING", createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))

        val api = object : TransactionSyncApi {
            override suspend fun syncTransactions(request: TransactionSyncRequestDto): TransactionSyncResponseDto {
                throw HttpException(Response.error<TransactionSyncResponseDto>(401, "".toResponseBody(null)))
            }
        }

        val repo = createRepository(transactionDao, syncQueueDao, api)
        val result = repo.syncPendingTransactions()
        assertTrue(result is TransactionSyncRepository.SyncResult.SessionExpired)
    }
}
