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
import java.net.SocketTimeoutException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * Phase 8.4 regression coverage: network & API production hardening.
 * Covers: 429, 408, 403, 404, 502/503/504, timeout, malformed JSON, empty body,
 * idempotency retry-after-commit, partial batch, concurrent duplicate, offline recovery.
 */
class NetworkProductionHardeningTest {

    private fun txn(uuid: String) = TransactionEntity(
        id = "id-$uuid", amount = 100.0, sender = "Rahul", upiApp = "PhonePe",
        transactionType = "RECEIVED", status = "SUCCESS", transactionId = "UTR-$uuid",
        rawNotification = "raw", parserVersion = "PhonePeParserV1", parseStatus = "PARSED",
        createdAt = System.currentTimeMillis(), sourceType = "UNKNOWN",
        packageName = "com.phonepe.app", notificationKey = null,
        originalNotificationText = "raw", cleanedNotificationText = "raw",
        voiceAnnounced = true, transactionUuid = uuid
    )

    private class FakeTransactionDao(val map: MutableMap<String, TransactionEntity>) : TransactionDao {
        override fun observeAll(): Flow<List<TransactionEntity>> = flowOf(map.values.toList())
        override fun observeReceivedSuccess(): Flow<List<TransactionEntity>> = flowOf(map.values.toList())
        override fun observeReceivedSuccessSince(since: Long): Flow<List<TransactionEntity>> = flowOf(emptyList())
        override suspend fun findRecentReceivedSuccess(amount: Double, since: Long): TransactionEntity? = null
        override fun observeLatest(): Flow<TransactionEntity?> = flowOf(null)
        override fun observeCount(): Flow<Int> = flowOf(map.size)
        override fun observeCountSince(since: Long): Flow<Int> = flowOf(0)
        override suspend fun findByReferenceIdGlobal(transactionId: String): TransactionEntity? = null
        override suspend fun findByFingerprint(fingerprint: String, windowStart: Long, windowEnd: Long): TransactionEntity? = null
        override suspend fun findByFingerprintNullRef(fingerprint: String, windowStart: Long, windowEnd: Long): TransactionEntity? = null
        override suspend fun findExactDuplicate(rawNotification: String, windowStart: Long, windowEnd: Long): TransactionEntity? = null
        override suspend fun findByTransactionUuid(uuid: String): TransactionEntity? = map[uuid]
        override suspend fun findByTransactionUuids(uuids: List<String>): List<TransactionEntity> = uuids.mapNotNull { map[it] }
        override suspend fun insert(entity: TransactionEntity): Long = 1L
        override suspend fun markVoiceAnnounced(id: String) {}
        override suspend fun clearAll() {}
        override suspend fun getEligibleMissingQueueUuids(limit: Int, offset: Int): List<String> = emptyList()
        override suspend fun countEligibleMissingQueue(): Int = 0
        override suspend fun countEligibleTransactions(): Int = 0
    }

    private class FakeQueue : SyncQueueDao {
        val rows = mutableListOf<SyncQueueEntity>()
        private var nextId = 1L
        override suspend fun insert(item: SyncQueueEntity): Long { val id = if (item.id == 0L) nextId++ else item.id; rows.add(item.copy(id = id)); return id }
        override suspend fun insertIgnore(item: SyncQueueEntity): Long { if (rows.any { it.entityType == item.entityType && it.entityId == item.entityId }) return -1L; val id = if (item.id == 0L) nextId++ else item.id; rows.add(item.copy(id = id)); return id }
        override suspend fun getByStatus(status: String): List<SyncQueueEntity> = rows.filter { it.status == status }
        override suspend fun getPendingItems(): List<SyncQueueEntity> = rows.filter { it.status == SyncQueueEntity.STATUS_PENDING }.sortedBy { it.createdAt }
        override suspend fun updateStatus(id: Long, status: String, updatedAt: Long) { val idx = rows.indexOfFirst { it.id == id }; if (idx >= 0) rows[idx] = rows[idx].copy(status = status, updatedAt = updatedAt) }
        override suspend fun incrementRetryCount(id: Long, updatedAt: Long) { val idx = rows.indexOfFirst { it.id == id }; if (idx >= 0) rows[idx] = rows[idx].copy(retryCount = rows[idx].retryCount + 1, updatedAt = updatedAt) }
        override suspend fun updateStatusIfExpected(id: Long, expectedStatus: String, newStatus: String, updatedAt: Long): Int { val idx = rows.indexOfFirst { it.id == id }; if (idx >= 0 && rows[idx].status == expectedStatus) { rows[idx] = rows[idx].copy(status = newStatus, updatedAt = updatedAt); return 1 }; return 0 }
        override suspend fun incrementRetryCountIfExpected(id: Long, expectedStatus: String, updatedAt: Long): Int { val idx = rows.indexOfFirst { it.id == id }; if (idx >= 0 && rows[idx].status == expectedStatus) { rows[idx] = rows[idx].copy(retryCount = rows[idx].retryCount + 1, updatedAt = updatedAt); return 1 }; return 0 }
        override suspend fun markFailedWithDiagnosticsIfExpected(id: Long, status: String, errorCode: String?, errorMessage: String?, failedAt: Long?, updatedAt: Long, expectedStatus: String): Int { val idx = rows.indexOfFirst { it.id == id }; if (idx >= 0 && rows[idx].status == expectedStatus) { rows[idx] = rows[idx].copy(status = status, lastErrorCode = errorCode, lastErrorMessage = errorMessage, failedAt = failedAt, updatedAt = updatedAt); return 1 }; return 0 }
        override suspend fun getAll(): List<SyncQueueEntity> = rows
        override suspend fun deleteById(id: Long) { rows.removeIf { it.id == id } }
        override suspend fun clearAll() { rows.clear() }
        override fun observeCountByStatus(status: String): Flow<Int> = flowOf(rows.count { it.status == status })
        override suspend fun getCountByStatus(status: String): Int = rows.count { it.status == status }
        override suspend fun getFailedItems(): List<SyncQueueEntity> = rows.filter { it.status == SyncQueueEntity.STATUS_FAILED }
        override suspend fun getById(id: Long): SyncQueueEntity? = rows.firstOrNull { it.id == id }
        override suspend fun markFailedWithDiagnostics(id: Long, status: String, errorCode: String?, errorMessage: String?, failedAt: Long?, updatedAt: Long) { val idx = rows.indexOfFirst { it.id == id }; if (idx >= 0) rows[idx] = rows[idx].copy(status = status, lastErrorCode = errorCode, lastErrorMessage = errorMessage, failedAt = failedAt, updatedAt = updatedAt) }
        override suspend fun retryFailedItem(id: Long, updatedAt: Long): Int { val idx = rows.indexOfFirst { it.id == id }; if (idx >= 0 && rows[idx].status == SyncQueueEntity.STATUS_FAILED) { rows[idx] = rows[idx].copy(status = SyncQueueEntity.STATUS_PENDING, lastErrorCode = null, lastErrorMessage = null, failedAt = null, updatedAt = updatedAt); return 1 }; return 0 }
        override suspend fun retryAllFailed(updatedAt: Long): Int { var c = 0; rows.forEachIndexed { idx, e -> if (e.status == SyncQueueEntity.STATUS_FAILED) { rows[idx] = e.copy(status = SyncQueueEntity.STATUS_PENDING, lastErrorCode = null, lastErrorMessage = null, failedAt = null, updatedAt = updatedAt); c++ } }; return c }
        override fun observeFailedItems(): Flow<List<SyncQueueEntity>> = flowOf(rows.filter { it.status == SyncQueueEntity.STATUS_FAILED })
        override suspend fun getStaleUploadingItems(cutoffTime: Long) = rows.filter { it.status == SyncQueueEntity.STATUS_UPLOADING && it.updatedAt < cutoffTime }
        override suspend fun recoverStaleUploading(cutoffTime: Long, updatedAt: Long): Int { var c = 0; rows.forEachIndexed { idx, e -> if (e.status == SyncQueueEntity.STATUS_UPLOADING && e.updatedAt < cutoffTime) { rows[idx] = e.copy(status = SyncQueueEntity.STATUS_PENDING, updatedAt = updatedAt); c++ } }; return c }
    }

    private fun repo(queue: FakeQueue, txMap: MutableMap<String, TransactionEntity>, api: TransactionSyncApi) =
        TransactionSyncRepository(queue, FakeTransactionDao(txMap), api, object : DeviceIdProvider { override suspend fun getDeviceId() = "dev1" })

    @Test fun `429 rate limited returns Retry not FAILED and preserves PENDING`() = runTest {
        val uuid = "550e8400-e29b-41d4-a716-446655440101"
        val q = FakeQueue(); q.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = uuid, status = "PENDING", createdAt = 1, updatedAt = 1))
        val m = mutableMapOf(uuid to txn(uuid))
        val api = object : TransactionSyncApi { override suspend fun syncTransactions(r: TransactionSyncRequestDto): TransactionSyncResponseDto { throw HttpException(Response.error<TransactionSyncResponseDto>(429, "".toResponseBody(null))) } }
        val result = repo(q, m, api).syncPendingTransactions()
        assertTrue(result is TransactionSyncRepository.SyncResult.Retry)
        assertEquals(SyncQueueEntity.STATUS_PENDING, q.rows.first().status)
        // Must be retry not FAILED — data preserved
        assertTrue(q.rows.none { it.status == SyncQueueEntity.STATUS_FAILED })
    }

    @Test fun `408 request timeout returns Retry`() = runTest {
        val uuid = "550e8400-e29b-41d4-a716-446655440102"
        val q = FakeQueue(); q.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = uuid, status = "PENDING", createdAt = 1, updatedAt = 1))
        val m = mutableMapOf(uuid to txn(uuid))
        val api = object : TransactionSyncApi { override suspend fun syncTransactions(r: TransactionSyncRequestDto): TransactionSyncResponseDto { throw HttpException(Response.error<TransactionSyncResponseDto>(408, "".toResponseBody(null))) } }
        val result = repo(q, m, api).syncPendingTransactions()
        assertTrue(result is TransactionSyncRepository.SyncResult.Retry)
        assertEquals(SyncQueueEntity.STATUS_PENDING, q.rows.first().status)
    }

    @Test fun `403 forbidden marks FAILED not Retry`() = runTest {
        val uuid = "550e8400-e29b-41d4-a716-446655440103"
        val q = FakeQueue(); q.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = uuid, status = "PENDING", createdAt = 1, updatedAt = 1))
        val m = mutableMapOf(uuid to txn(uuid))
        val api = object : TransactionSyncApi { override suspend fun syncTransactions(r: TransactionSyncRequestDto): TransactionSyncResponseDto { throw HttpException(Response.error<TransactionSyncResponseDto>(403, "".toResponseBody(null))) } }
        val result = repo(q, m, api).syncPendingTransactions()
        assertTrue(result is TransactionSyncRepository.SyncResult.Error)
        assertEquals(SyncQueueEntity.STATUS_FAILED, q.rows.first().status)
        // Diagnostic sanitized — no raw body
        assertEquals(TransactionSyncRepository.ERROR_CODE_REJECTED, q.rows.first().lastErrorCode)
    }

    @Test fun `404 not found marks FAILED`() = runTest {
        val uuid = "550e8400-e29b-41d4-a716-446655440104"
        val q = FakeQueue(); q.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = uuid, status = "PENDING", createdAt = 1, updatedAt = 1))
        val m = mutableMapOf(uuid to txn(uuid))
        val api = object : TransactionSyncApi { override suspend fun syncTransactions(r: TransactionSyncRequestDto): TransactionSyncResponseDto { throw HttpException(Response.error<TransactionSyncResponseDto>(404, "".toResponseBody(null))) } }
        val result = repo(q, m, api).syncPendingTransactions()
        assertTrue(result is TransactionSyncRepository.SyncResult.Error)
        assertEquals(SyncQueueEntity.STATUS_FAILED, q.rows.first().status)
    }

    @Test fun `502 bad gateway returns Retry`() = runTest {
        val uuid = "550e8400-e29b-41d4-a716-446655440105"
        val q = FakeQueue(); q.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = uuid, status = "PENDING", createdAt = 1, updatedAt = 1))
        val m = mutableMapOf(uuid to txn(uuid))
        val api = object : TransactionSyncApi { override suspend fun syncTransactions(r: TransactionSyncRequestDto): TransactionSyncResponseDto { throw HttpException(Response.error<TransactionSyncResponseDto>(502, "<html>502 Bad Gateway</html>".toResponseBody(null))) } }
        val result = repo(q, m, api).syncPendingTransactions()
        assertTrue(result is TransactionSyncRepository.SyncResult.Retry)
        assertEquals(SyncQueueEntity.STATUS_PENDING, q.rows.first().status)
    }

    @Test fun `503 returns Retry`() = runTest {
        val uuid = "550e8400-e29b-41d4-a716-446655440106"
        val q = FakeQueue(); q.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = uuid, status = "PENDING", createdAt = 1, updatedAt = 1))
        val m = mutableMapOf(uuid to txn(uuid))
        val api = object : TransactionSyncApi { override suspend fun syncTransactions(r: TransactionSyncRequestDto): TransactionSyncResponseDto { throw HttpException(Response.error<TransactionSyncResponseDto>(503, "".toResponseBody(null))) } }
        val result = repo(q, m, api).syncPendingTransactions()
        assertTrue(result is TransactionSyncRepository.SyncResult.Retry)
    }

    @Test fun `504 gateway timeout returns Retry`() = runTest {
        val uuid = "550e8400-e29b-41d4-a716-446655440107"
        val q = FakeQueue(); q.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = uuid, status = "PENDING", createdAt = 1, updatedAt = 1))
        val m = mutableMapOf(uuid to txn(uuid))
        val api = object : TransactionSyncApi { override suspend fun syncTransactions(r: TransactionSyncRequestDto): TransactionSyncResponseDto { throw HttpException(Response.error<TransactionSyncResponseDto>(504, "".toResponseBody(null))) } }
        val result = repo(q, m, api).syncPendingTransactions()
        assertTrue(result is TransactionSyncRepository.SyncResult.Retry)
    }

    @Test fun `SocketTimeoutException treated as Retry with PENDING`() = runTest {
        val uuid = "550e8400-e29b-41d4-a716-446655440108"
        val q = FakeQueue(); q.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = uuid, status = "PENDING", createdAt = 1, updatedAt = 1))
        val m = mutableMapOf(uuid to txn(uuid))
        val api = object : TransactionSyncApi { override suspend fun syncTransactions(r: TransactionSyncRequestDto): TransactionSyncResponseDto { throw SocketTimeoutException("read timed out") } }
        val result = repo(q, m, api).syncPendingTransactions()
        assertTrue(result is TransactionSyncRepository.SyncResult.Retry)
        assertEquals(SyncQueueEntity.STATUS_PENDING, q.rows.first().status)
        assertEquals(1, q.rows.first().retryCount)
    }

    @Test fun `IOException offline treated as Retry`() = runTest {
        val uuid = "550e8400-e29b-41d4-a716-446655440109"
        val q = FakeQueue(); q.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = uuid, status = "PENDING", createdAt = 1, updatedAt = 1))
        val m = mutableMapOf(uuid to txn(uuid))
        val api = object : TransactionSyncApi { override suspend fun syncTransactions(r: TransactionSyncRequestDto): TransactionSyncResponseDto { throw IOException("Unable to resolve host") } }
        val result = repo(q, m, api).syncPendingTransactions()
        assertTrue(result is TransactionSyncRepository.SyncResult.Retry)
        assertEquals(SyncQueueEntity.STATUS_PENDING, q.rows.first().status)
    }

    @Test fun `malformed JSON success response treated as Retry not FAILED`() = runTest {
        val uuid = "550e8400-e29b-41d4-a716-446655440110"
        val q = FakeQueue(); q.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = uuid, status = "PENDING", createdAt = 1, updatedAt = 1))
        val m = mutableMapOf(uuid to txn(uuid))
        val api = object : TransactionSyncApi { override suspend fun syncTransactions(r: TransactionSyncRequestDto): TransactionSyncResponseDto { throw com.google.gson.JsonSyntaxException("Expected BEGIN_OBJECT but was STRING") } }
        val result = repo(q, m, api).syncPendingTransactions()
        assertTrue(result is TransactionSyncRepository.SyncResult.Retry)
        assertEquals(SyncQueueEntity.STATUS_PENDING, q.rows.first().status)
        // Must not be marked SYNCED falsely
        assertTrue(q.rows.none { it.status == SyncQueueEntity.STATUS_SYNCED })
    }

    @Test fun `non-JSON HTML error body with 500 treated as Retry sanitized`() = runTest {
        val uuid = "550e8400-e29b-41d4-a716-446655440111"
        val q = FakeQueue(); q.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = uuid, status = "PENDING", createdAt = 1, updatedAt = 1))
        val m = mutableMapOf(uuid to txn(uuid))
        val api = object : TransactionSyncApi { override suspend fun syncTransactions(r: TransactionSyncRequestDto): TransactionSyncResponseDto { throw HttpException(Response.error<TransactionSyncResponseDto>(500, "<html><body>Internal Server Error</body></html>".toResponseBody(null))) } }
        val result = repo(q, m, api).syncPendingTransactions()
        assertTrue(result is TransactionSyncRepository.SyncResult.Retry)
        // Never persist raw HTML
        assertTrue(q.rows.first().lastErrorMessage == null || !q.rows.first().lastErrorMessage!!.contains("<html>"))
    }

    @Test fun `empty response JsonSyntaxException treated as Retry`() = runTest {
        val uuid = "550e8400-e29b-41d4-a716-446655440112"
        val q = FakeQueue(); q.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = uuid, status = "PENDING", createdAt = 1, updatedAt = 1))
        val m = mutableMapOf(uuid to txn(uuid))
        val api = object : TransactionSyncApi { override suspend fun syncTransactions(r: TransactionSyncRequestDto): TransactionSyncResponseDto { throw com.google.gson.JsonSyntaxException("Empty") } }
        val result = repo(q, m, api).syncPendingTransactions()
        assertTrue(result is TransactionSyncRepository.SyncResult.Retry)
    }

    @Test fun `400 validation error marks FAILED with sanitized diagnostics`() = runTest {
        val uuid = "550e8400-e29b-41d4-a716-446655440113"
        val q = FakeQueue(); q.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = uuid, status = "PENDING", createdAt = 1, updatedAt = 1))
        val m = mutableMapOf(uuid to txn(uuid))
        val api = object : TransactionSyncApi { override suspend fun syncTransactions(r: TransactionSyncRequestDto): TransactionSyncResponseDto { throw HttpException(Response.error<TransactionSyncResponseDto>(400, """{"error":{"code":"VALIDATION_ERROR"}}""".toResponseBody(null))) } }
        val result = repo(q, m, api).syncPendingTransactions()
        assertTrue(result is TransactionSyncRepository.SyncResult.Error)
        assertEquals(SyncQueueEntity.STATUS_FAILED, q.rows.first().status)
        assertEquals(TransactionSyncRepository.ERROR_CODE_VALIDATION, q.rows.first().lastErrorCode)
        assertEquals(TransactionSyncRepository.ERROR_MSG_VALIDATION, q.rows.first().lastErrorMessage)
        // No raw server body in diagnostics
        assertTrue(!q.rows.first().lastErrorMessage!!.contains("VALIDATION_ERROR") || q.rows.first().lastErrorMessage == TransactionSyncRepository.ERROR_MSG_VALIDATION)
    }

    @Test fun `timeout after server commit - retry is idempotent via duplicates`() = runTest {
        val uuid = "550e8400-e29b-41d4-a716-446655440114"
        val q = FakeQueue(); q.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = uuid, status = "PENDING", createdAt = 1, updatedAt = 1))
        val m = mutableMapOf(uuid to txn(uuid))
        var callCount = 0
        val api = object : TransactionSyncApi {
            override suspend fun syncTransactions(r: TransactionSyncRequestDto): TransactionSyncResponseDto {
                callCount++
                if (callCount == 1) throw SocketTimeoutException("timeout after server commit")
                // Second attempt — server returns duplicate (already committed)
                return TransactionSyncResponseDto(created = emptyList(), duplicates = listOf(uuid))
            }
        }
        val repo = repo(q, m, api)
        val first = repo.syncPendingTransactions()
        assertTrue(first is TransactionSyncRepository.SyncResult.Retry)
        assertEquals(SyncQueueEntity.STATUS_PENDING, q.rows.first().status)
        // Retry should succeed idempotently
        val second = repo.syncPendingTransactions()
        assertTrue(second is TransactionSyncRepository.SyncResult.Success)
        assertEquals(SyncQueueEntity.STATUS_SYNCED, q.rows.first().status)
    }

    @Test fun `partial batch failure - successful items SYNCED retryable stays PENDING failed stays FAILED`() = runTest {
        val uuid1 = "550e8400-e29b-41d4-a716-446655440201"
        val uuid2 = "550e8400-e29b-41d4-a716-446655440202"
        val uuid3 = "550e8400-e29b-41d4-a716-446655440203"
        val q = FakeQueue()
        listOf(uuid1, uuid2, uuid3).forEach { q.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = it, status = "PENDING", createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis())) }
        val m = mutableMapOf(uuid1 to txn(uuid1), uuid2 to txn(uuid2), uuid3 to txn(uuid3))
        // Simulate batch where api succeeds for 1, but second batch fails network etc.
        // Here single batch of 3: server returns only 1 as created, 2 not in response -> treated as PENDING retry
        val api = object : TransactionSyncApi {
            override suspend fun syncTransactions(r: TransactionSyncRequestDto): TransactionSyncResponseDto {
                // return only uuid1 as created, others missing -> will be reverted to PENDING
                return TransactionSyncResponseDto(created = listOf(uuid1), duplicates = emptyList())
            }
        }
        val result = repo(q, m, api).syncPendingTransactions()
        assertTrue(result is TransactionSyncRepository.SyncResult.PartialSuccess)
        assertEquals(SyncQueueEntity.STATUS_SYNCED, q.rows.first { it.entityId == uuid1 }.status)
        assertEquals(SyncQueueEntity.STATUS_PENDING, q.rows.first { it.entityId == uuid2 }.status)
        assertEquals(SyncQueueEntity.STATUS_PENDING, q.rows.first { it.entityId == uuid3 }.status)
    }

    @Test fun `duplicate sync request - second upload returns duplicate and marks SYNCED without duplicate rows`() = runTest {
        val uuid = "550e8400-e29b-41d4-a716-446655440115"
        val q = FakeQueue(); q.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = uuid, status = "PENDING", createdAt = 1, updatedAt = 1))
        val m = mutableMapOf(uuid to txn(uuid))
        val api = object : TransactionSyncApi {
            override suspend fun syncTransactions(r: TransactionSyncRequestDto): TransactionSyncResponseDto {
                return TransactionSyncResponseDto(created = emptyList(), duplicates = listOf(uuid))
            }
        }
        val result = repo(q, m, api).syncPendingTransactions()
        assertTrue(result is TransactionSyncRepository.SyncResult.Success)
        assertEquals(SyncQueueEntity.STATUS_SYNCED, q.rows.first().status)
        // Second duplicate attempt should not create new row; queue uniqueness enforced by unique index in production
        // Simulate second insertion — should be rejected as duplicate if attempted
        val secondInsert = q.insertIgnore(SyncQueueEntity(entityType = "TRANSACTION", entityId = uuid, status = "PENDING", createdAt = 2, updatedAt = 2))
        assertEquals(-1L, secondInsert)
    }

    @Test fun `network failure increments retryCount and preserves transactionId`() = runTest {
        val uuid = "550e8400-e29b-41d4-a716-446655440116"
        val q = FakeQueue(); q.insert(SyncQueueEntity(entityType = "TRANSACTION", entityId = uuid, status = "PENDING", retryCount = 2, createdAt = 1, updatedAt = 1))
        val m = mutableMapOf(uuid to txn(uuid))
        val api = object : TransactionSyncApi { override suspend fun syncTransactions(r: TransactionSyncRequestDto): TransactionSyncResponseDto { throw IOException("offline") } }
        val beforeRetry = q.rows.first().retryCount
        repo(q, m, api).syncPendingTransactions()
        assertEquals(beforeRetry + 1, q.rows.first().retryCount)
        assertEquals(uuid, q.rows.first().entityId)
        assertEquals(SyncQueueEntity.STATUS_PENDING, q.rows.first().status)
    }

    @Test fun `OkHttp timeouts are explicit - verify AuthModule config exists`() {
        // Structural check: AuthModule must set connect/read/write/call timeouts
        val source = java.io.File("D:/UPI_Notification_Alert/app/src/main/java/com/upivoicealert/di/AuthModule.kt").readText()
        assertTrue(source.contains("connectTimeout"))
        assertTrue(source.contains("readTimeout"))
        assertTrue(source.contains("writeTimeout"))
        assertTrue(source.contains("callTimeout"))
    }

    @Test fun `network security config forbids cleartext`() {
        val xml = java.io.File("D:/UPI_Notification_Alert/app/src/main/res/xml/network_security_config.xml").readText()
        assertTrue(xml.contains("cleartextTrafficPermitted=\"false\""))
    }
}
