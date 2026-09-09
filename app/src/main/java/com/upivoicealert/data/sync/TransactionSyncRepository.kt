package com.upivoicealert.data.sync

import android.util.Log
import com.upivoicealert.data.database.TransactionDao
import com.upivoicealert.utils.DeviceIdProvider
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.HttpException

/**
 * Repository handling cloud sync for pending transactions.
 * Reads pending SyncQueue, loads Room transactions, batches (max 100), calls API, updates queue.
 * Never modifies offline pipeline; network failure never affects local storage/TTS.
 * Phase 7.2: stores sanitized failure diagnostics for HTTP 400 -> FAILED.
 */
@Singleton
class TransactionSyncRepository @Inject constructor(
    private val syncQueueDao: SyncQueueDao,
    private val transactionDao: TransactionDao,
    private val api: TransactionSyncApi,
    private val deviceIdProvider: DeviceIdProvider
) {

    enum class RetryCause { NETWORK, SERVER }

    sealed interface SyncResult {
        data object Success : SyncResult
        data class PartialSuccess(val synced: Int, val failed: Int) : SyncResult
        data object Retry : SyncResult
        data class Error(val message: String) : SyncResult
        data object SessionExpired : SyncResult
        data object NoPending : SyncResult
    }

    // Diagnostic helpers — set during syncPendingTransactions, read by worker for sanitized diagnostics only
    var lastSuccessCount: Int = 0
        private set
    var lastRetryCause: RetryCause = RetryCause.SERVER
        private set

    companion object {
        const val BATCH_SIZE = 100
        private const val TAG = "TransactionSync"
        const val ERROR_CODE_VALIDATION = "VALIDATION_ERROR"
        const val ERROR_CODE_REJECTED = "SYNC_REJECTED"
        const val ERROR_MSG_VALIDATION = "Transaction data could not be synced."
        const val ERROR_MSG_REJECTED = "The transaction was rejected by the server."
        const val ERROR_MSG_MISSING = "Transaction data could not be synced."
    }

    suspend fun syncPendingTransactions(): SyncResult {
        val pending = syncQueueDao.getPendingItems()
        if (pending.isEmpty()) {
            Log.d(TAG, "No pending items")
            return SyncResult.NoPending
        }

        // Batch processing
        val batches = pending.chunked(BATCH_SIZE)
        var totalSynced = 0
        var totalFailed = 0
        var shouldRetry = false

        for (batch in batches) {
            val result = syncBatch(batch)
            when (result) {
                is SyncResult.Success -> totalSynced += batch.size
                is SyncResult.PartialSuccess -> {
                    totalSynced += result.synced
                    totalFailed += result.failed
                    shouldRetry = true
                }
                is SyncResult.Retry -> {
                    shouldRetry = true
                    // Increment retry for this batch — restrictive to PENDING only
                    batch.forEach { syncQueueDao.incrementRetryCountIfExpected(it.id, SyncQueueEntity.STATUS_PENDING, System.currentTimeMillis()) }
                }
                is SyncResult.Error -> {
                    // Validation error → already marked FAILED with diagnostics inside syncBatch
                    if (result.message.contains("Validation", ignoreCase = true) || result.message.contains("400")) {
                        totalFailed += batch.size
                    } else {
                        shouldRetry = true
                        batch.forEach { syncQueueDao.incrementRetryCountIfExpected(it.id, SyncQueueEntity.STATUS_PENDING, System.currentTimeMillis()) }
                    }
                }
                is SyncResult.SessionExpired -> return SyncResult.SessionExpired
                is SyncResult.NoPending -> {}
            }
        }

        return when {
            totalFailed == 0 && !shouldRetry && totalSynced > 0 -> {
                lastSuccessCount = totalSynced
                SyncResult.Success
            }
            totalSynced > 0 && (totalFailed > 0 || shouldRetry) -> SyncResult.PartialSuccess(totalSynced, totalFailed)
            shouldRetry -> SyncResult.Retry
            totalFailed > 0 -> SyncResult.Error("Validation failed for $totalFailed items")
            else -> {
                lastSuccessCount = 0
                SyncResult.Success
            }
        }
    }

    private suspend fun syncBatch(batch: List<SyncQueueEntity>): SyncResult {
        // Mark as UPLOADING — restrictive: only PENDING → UPLOADING to avoid overwriting concurrent state changes
        val now = System.currentTimeMillis()
        batch.forEach {
            val updated = syncQueueDao.updateStatusIfExpected(it.id, SyncQueueEntity.STATUS_PENDING, SyncQueueEntity.STATUS_UPLOADING, now)
            if (updated == 0) Log.w(TAG, "Skip UPLOADING for ${it.id}: not PENDING (race)")
        }

        // Load matching transactions
        val uuids = batch.map { it.entityId }
        val transactions = transactionDao.findByTransactionUuids(uuids)
        val transactionMap = transactions.associateBy { it.transactionUuid }

        // Build upload DTOs, skipping missing transactions (mark as FAILED with diagnostics)
        val deviceId = try { deviceIdProvider.getDeviceId() } catch (_: Exception) { "unknown-device" }
        val uploads = mutableListOf<TransactionUploadDto>()
        val missingIds = mutableListOf<Long>()
        var missingCount = 0

        for (queueItem in batch) {
            val txn = transactionMap[queueItem.entityId]
            if (txn == null) {
                Log.w(TAG, "Missing transaction for queue ${queueItem.id} uuid=${queueItem.entityId}, marking FAILED")
                markFailedWithDiagnostics(
                    queueItem.id,
                    ERROR_CODE_VALIDATION,
                    ERROR_MSG_MISSING
                )
                missingCount++
                continue
            }
            uploads.add(
                TransactionUploadDto(
                    transactionUuid = txn.transactionUuid,
                    deviceId = deviceId,
                    amount = txn.amount,
                    senderName = txn.sender,
                    senderVpa = null, // not stored locally, optional
                    upiReference = txn.transactionId,
                    upiApp = txn.upiApp,
                    transactionTime = Instant.ofEpochMilli(txn.createdAt).toString()
                )
            )
        }

        if (uploads.isEmpty()) {
            // All missing were marked FAILED — report as permanent failure batch
            return if (missingCount > 0) SyncResult.Error("Missing transactions: $missingCount") else SyncResult.Success
        }

        return try {
            val request = TransactionSyncRequestDto(transactions = uploads)
            val response = api.syncTransactions(request)

            // Backend returns created + duplicates → both considered SYNCED (idempotent)
            val syncedUuids = (response.created + response.duplicates).toSet()
            for (queueItem in batch) {
                if (syncedUuids.contains(queueItem.entityId)) {
                    val updated = syncQueueDao.updateStatusIfExpected(queueItem.id, SyncQueueEntity.STATUS_UPLOADING, SyncQueueEntity.STATUS_SYNCED, System.currentTimeMillis())
                    if (updated == 0) Log.w(TAG, "Skip SYNCED for ${queueItem.id}: not UPLOADING")
                } else if (!missingIds.contains(queueItem.id)) {
                    // Not in created/duplicates but was uploaded → treat as failed? Keep pending for retry
                    val reverted = syncQueueDao.updateStatusIfExpected(queueItem.id, SyncQueueEntity.STATUS_UPLOADING, SyncQueueEntity.STATUS_PENDING, System.currentTimeMillis())
                    if (reverted == 0) Log.w(TAG, "Skip PENDING revert for ${queueItem.id}: not UPLOADING")
                    else syncQueueDao.incrementRetryCountIfExpected(queueItem.id, SyncQueueEntity.STATUS_PENDING, System.currentTimeMillis())
                }
            }

            if (syncedUuids.size == uploads.size) SyncResult.Success
            else SyncResult.PartialSuccess(syncedUuids.size, uploads.size - syncedUuids.size)

        } catch (e: IOException) {
            Log.w(TAG, "Network failure, will retry", e)
            // Revert UPLOADING → PENDING for retry (no diagnostics, preserve for retry) — restrictive
            batch.forEach {
                val reverted = syncQueueDao.updateStatusIfExpected(it.id, SyncQueueEntity.STATUS_UPLOADING, SyncQueueEntity.STATUS_PENDING, System.currentTimeMillis())
                if (reverted == 0) Log.w(TAG, "Skip PENDING revert (network) for ${it.id}")
            }
            lastRetryCause = RetryCause.NETWORK
            SyncResult.Retry
        } catch (e: HttpException) {
            val code = e.code()
            Log.w(TAG, "HTTP $code during sync", e)
            when {
                code == 401 -> {
                    batch.forEach {
                        val reverted = syncQueueDao.updateStatusIfExpected(it.id, SyncQueueEntity.STATUS_UPLOADING, SyncQueueEntity.STATUS_PENDING, System.currentTimeMillis())
                        if (reverted == 0) Log.w(TAG, "Skip PENDING revert (401) for ${it.id}")
                    }
                    SyncResult.SessionExpired
                }
                code == 429 || code == 408 -> {
                    // Rate limited / request timeout — transient, retry with backoff; never mark FAILED
                    batch.forEach {
                        val reverted = syncQueueDao.updateStatusIfExpected(it.id, SyncQueueEntity.STATUS_UPLOADING, SyncQueueEntity.STATUS_PENDING, System.currentTimeMillis())
                        if (reverted == 0) Log.w(TAG, "Skip PENDING revert ($code) for ${it.id}")
                    }
                    lastRetryCause = RetryCause.SERVER
                    SyncResult.Retry
                }
                code in 500..599 -> {
                    batch.forEach {
                        val reverted = syncQueueDao.updateStatusIfExpected(it.id, SyncQueueEntity.STATUS_UPLOADING, SyncQueueEntity.STATUS_PENDING, System.currentTimeMillis())
                        if (reverted == 0) Log.w(TAG, "Skip PENDING revert (5xx) for ${it.id}")
                    }
                    lastRetryCause = RetryCause.SERVER
                    SyncResult.Retry
                }
                code == 400 -> {
                    // Validation error → permanent failure with sanitized diagnostics
                    batch.forEach {
                        markFailedWithDiagnostics(
                            it.id,
                            ERROR_CODE_VALIDATION,
                            ERROR_MSG_VALIDATION
                        )
                    }
                    SyncResult.Error("Validation error: ${e.message()}")
                }
                code in 400..499 -> {
                    // Other 4xx (except 400/401/429/408) -> also permanent but generic message
                    batch.forEach {
                        markFailedWithDiagnostics(
                            it.id,
                            ERROR_CODE_REJECTED,
                            ERROR_MSG_REJECTED
                        )
                    }
                    SyncResult.Error("Validation error: ${e.message()}")
                }
                else -> {
                    batch.forEach {
                        val reverted = syncQueueDao.updateStatusIfExpected(it.id, SyncQueueEntity.STATUS_UPLOADING, SyncQueueEntity.STATUS_PENDING, System.currentTimeMillis())
                        if (reverted == 0) Log.w(TAG, "Skip PENDING revert (else) for ${it.id}")
                    }
                    lastRetryCause = RetryCause.SERVER
                    SyncResult.Retry
                }
            }
        } catch (e: Exception) {
            // Covers JsonSyntaxException, malformed JSON, empty body, HTML error page etc.
            // Never mark SYNCED; revert to PENDING and retry — response validation failure is transient.
            // Diagnostics remain sanitized (no raw body persisted).
            Log.w(TAG, "Unexpected sync failure (likely malformed response), will retry", e)
            batch.forEach {
                val reverted = syncQueueDao.updateStatusIfExpected(it.id, SyncQueueEntity.STATUS_UPLOADING, SyncQueueEntity.STATUS_PENDING, System.currentTimeMillis())
                if (reverted == 0) Log.w(TAG, "Skip PENDING revert (exception) for ${it.id}")
            }
            lastRetryCause = RetryCause.SERVER
            SyncResult.Retry
        }
    }

    private suspend fun markFailedWithDiagnostics(
        queueId: Long,
        errorCode: String,
        errorMessage: String
    ) {
        val now = System.currentTimeMillis()
        val updated = syncQueueDao.markFailedWithDiagnosticsIfExpected(
            id = queueId,
            status = SyncQueueEntity.STATUS_FAILED,
            errorCode = errorCode,
            errorMessage = errorMessage,
            failedAt = now,
            updatedAt = now,
            expectedStatus = SyncQueueEntity.STATUS_UPLOADING
        )
        if (updated == 0) {
            Log.w(TAG, "Skip FAILED for $queueId: not UPLOADING (race with manual retry or recovery)")
            // Fallback: if not UPLOADING anymore, don't overwrite SYNCED/PENDING/FAILED state
        }
    }
}
