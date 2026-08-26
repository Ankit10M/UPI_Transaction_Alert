package com.upivoicealert.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.upivoicealert.network.NetworkMonitor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Foundation worker for future Phase 6 Transaction Cloud Sync.
 * Currently does NOT upload transactions. Prepared structure only.
 * If offline → retry, if online → log "Sync worker ready" and success.
 */
@HiltWorker
class TransactionSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val networkMonitor: NetworkMonitor
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val online = networkMonitor.isCurrentlyOnline()
            if (!online) {
                Log.d(TAG, "Sync worker offline → retry")
                Result.retry()
            } else {
                Log.i(TAG, "Sync worker ready")
                Result.success()
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
