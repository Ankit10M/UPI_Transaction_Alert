package com.upivoicealert.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.upivoicealert.data.sync.TransactionSyncRepository
import com.upivoicealert.network.NetworkMonitor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Transaction Sync Worker — Phase 6.3: uploads pending transactions via TransactionSyncRepository.
 * Offline → retry, online → syncPendingTransactions(), maps result to WorkManager Result.
 */
@HiltWorker
class TransactionSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val networkMonitor: NetworkMonitor,
    private val syncRepository: TransactionSyncRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val online = networkMonitor.isCurrentlyOnline()
            if (!online) {
                Log.d(TAG, "Sync worker offline → retry")
                return Result.retry()
            }

            when (val result = syncRepository.syncPendingTransactions()) {
                is TransactionSyncRepository.SyncResult.Success -> {
                    Log.i(TAG, "Sync success")
                    Result.success()
                }
                is TransactionSyncRepository.SyncResult.NoPending -> {
                    Log.d(TAG, "No pending items")
                    Result.success()
                }
                is TransactionSyncRepository.SyncResult.PartialSuccess -> {
                    Log.i(TAG, "Partial success synced=${result.synced} failed=${result.failed}")
                    // If partial due to validation failures (FAILED), don't retry those; consider success
                    Result.success()
                }
                is TransactionSyncRepository.SyncResult.Retry -> {
                    Log.w(TAG, "Sync retry needed")
                    Result.retry()
                }
                is TransactionSyncRepository.SyncResult.Error -> {
                    Log.w(TAG, "Sync error: ${result.message}")
                    // Validation errors already marked FAILED, treat as success to avoid infinite retry
                    if (result.message.contains("Validation", ignoreCase = true)) Result.failure()
                    else Result.retry()
                }
                is TransactionSyncRepository.SyncResult.SessionExpired -> {
                    Log.w(TAG, "Session expired, failing worker")
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Sync worker failed, retry", e)
            Result.retry()
        }
    }

    companion object {
        const val TAG = "TransactionSyncWorker"
        const val WORK_NAME = "transaction_sync_work"
    }
}
