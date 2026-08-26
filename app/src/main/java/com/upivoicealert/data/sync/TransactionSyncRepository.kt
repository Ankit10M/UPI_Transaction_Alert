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
 */
@Singleton
class TransactionSyncRepository @Inject constructor(
    private val syncQueueDao: SyncQueueDao,
    private val transactionDao: TransactionDao,
    private val api: TransactionSyncApi,
    private val deviceIdProvider: DeviceIdProvider
) {

    sealed interface SyncResult {
        data object Success : SyncResult
        data class PartialSuccess(val synced: Int, val failed: Int) : SyncResult
        data object Retry : SyncResult
        data class Error(val message: String) : SyncResult
        data object SessionExpired : SyncResult
        data object NoPending : SyncResult
    }

    companion object {
        const val BATCH_SIZE = 100
        private const val TAG = "TransactionSync"
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
                    // Increment retry for this batch
                    batch.forEach { syncQueueDao.incrementRetryCount(it.id, System.currentTimeMillis()) }
                }
                is SyncResult.Error -> {
                    // Validation error → mark FAILED
                    if (result.message.contains("Validation", ignoreCase = true) || result.message.contains("400")) {
                        batch.forEach { syncQueueDao.updateStatus(it.id, SyncQueueEntity.STATUS_FAILED, System.currentTimeMillis()) }
                        totalFailed += batch.size
                    } else {
                        shouldRetry = true
                        batch.forEach { syncQueueDao.incrementRetryCount(it.id, System.currentTimeMillis()) }
                    }
                }
                is SyncResult.SessionExpired -> return SyncResult.SessionExpired
                is SyncResult.NoPending -> {}
            }
        }

        return when {
            totalFailed == 0 && !shouldRetry && totalSynced > 0 -> SyncResult.Success
            totalSynced > 0 && (totalFailed > 0 || shouldRetry) -> SyncResult.PartialSuccess(totalSynced, totalFailed)
            shouldRetry -> SyncResult.Retry
            totalFailed > 0 -> SyncResult.Error("Validation failed for $totalFailed items")
            else -> SyncResult.Success
        }
    }

    private suspend fun syncBatch(batch: List<SyncQueueEntity>): SyncResult {
        // Mark as UPLOADING
        val now = System.currentTimeMillis()
        batch.forEach { syncQueueDao.updateStatus(it.id, SyncQueueEntity.STATUS_UPLOADING, now) }

        // Load matching transactions
        val uuids = batch.map { it.entityId }
        val transactions = transactionDao.findByTransactionUuids(uuids)
        val transactionMap = transactions.associateBy { it.transactionUuid }

        // Build upload DTOs, skipping missing transactions (mark as FAILED)
        val deviceId = try { deviceIdProvider.getDeviceId() } catch (_: Exception) { "unknown-device" }
        val uploads = mutableListOf<TransactionUploadDto>()
        val missingIds = mutableListOf<Long>()

        for (queueItem in batch) {
            val txn = transactionMap[queueItem.entityId]
            if (txn == null) {
                Log.w(TAG, "Missing transaction for queue ${queueItem.id} uuid=${queueItem.entityId}, marking FAILED")
                syncQueueDao.updateStatus(queueItem.id, SyncQueueEntity.STATUS_FAILED, System.currentTimeMillis())
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
            // All missing were marked FAILED, treat as partial
            return if (missingIds.isNotEmpty()) SyncResult.PartialSuccess(0, missingIds.size) else SyncResult.Success
        }

        return try {
            val request = TransactionSyncRequestDto(transactions = uploads)
            val response = api.syncTransactions(request)

            // Backend returns created + duplicates → both considered SYNCED (idempotent)
            val syncedUuids = (response.created + response.duplicates).toSet()
            for (queueItem in batch) {
                if (syncedUuids.contains(queueItem.entityId)) {
                    syncQueueDao.updateStatus(queueItem.id, SyncQueueEntity.STATUS_SYNCED, System.currentTimeMillis())
                } else if (!missingIds.contains(queueItem.id)) {
                    // Not in created/duplicates but was uploaded → treat as failed? Keep pending for retry
                    syncQueueDao.updateStatus(queueItem.id, SyncQueueEntity.STATUS_PENDING, System.currentTimeMillis())
                    syncQueueDao.incrementRetryCount(queueItem.id, System.currentTimeMillis())
                }
            }

            if (syncedUuids.size == uploads.size) SyncResult.Success
            else SyncResult.PartialSuccess(syncedUuids.size, uploads.size - syncedUuids.size)

        } catch (e: IOException) {
            Log.w(TAG, "Network failure, will retry", e)
            // Revert UPLOADING → PENDING for retry
            batch.forEach { syncQueueDao.updateStatus(it.id, SyncQueueEntity.STATUS_PENDING, System.currentTimeMillis()) }
            SyncResult.Retry
        } catch (e: HttpException) {
            val code = e.code()
            Log.w(TAG, "HTTP $code during sync", e)
            when {
                code == 401 -> {
                    batch.forEach { syncQueueDao.updateStatus(it.id, SyncQueueEntity.STATUS_PENDING, System.currentTimeMillis()) }
                    SyncResult.SessionExpired
                }
                code in 500..599 -> {
                    batch.forEach {
                        syncQueueDao.updateStatus(it.id, SyncQueueEntity.STATUS_PENDING, System.currentTimeMillis())
                    }
                    SyncResult.Retry
                }
                code == 400 -> {
                    // Validation error → permanent failure
                    batch.forEach { syncQueueDao.updateStatus(it.id, SyncQueueEntity.STATUS_FAILED, System.currentTimeMillis()) }
                    SyncResult.Error("Validation error: ${e.message()}")
                }
                else -> {
                    batch.forEach { syncQueueDao.updateStatus(it.id, SyncQueueEntity.STATUS_PENDING, System.currentTimeMillis()) }
                    SyncResult.Retry
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unexpected sync failure", e)
            batch.forEach { syncQueueDao.updateStatus(it.id, SyncQueueEntity.STATUS_PENDING, System.currentTimeMillis()) }
            SyncResult.Error(e.message ?: "Unknown error")
        }
    }
}
