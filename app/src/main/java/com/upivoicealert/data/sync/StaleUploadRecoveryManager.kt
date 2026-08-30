package com.upivoicealert.data.sync

import android.util.Log
import com.upivoicealert.domain.sync.StaleUploadRecoveryResult
import com.upivoicealert.domain.sync.SyncQueueRepository
import com.upivoicealert.scheduler.SyncSchedulable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lifecycle hardening manager for stale UPLOADING items.
 * Single source of truth for recovery. Idempotent.
 */
@Singleton
class StaleUploadRecoveryManager @Inject constructor(
    private val syncQueueRepository: SyncQueueRepository,
    private val syncScheduler: SyncSchedulable
) {
    companion object {
        private const val TAG = "StaleRecoveryManager"
    }

    suspend fun recover(): StaleUploadRecoveryResult {
        return try {
            val result = syncQueueRepository.recoverStaleUploadingItems()
            if (result.recoveredCount > 0) {
                Log.i(TAG, "Recovered ${result.recoveredCount} stale UPLOADING -> PENDING")
                try {
                    syncScheduler.scheduleSync()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to schedule sync after recovery", e)
                }
            } else {
                Log.d(TAG, "No stale UPLOADING items")
            }
            result
        } catch (e: Exception) {
            Log.w(TAG, "Recovery failed", e)
            throw e
        }
    }

    /**
     * Recovery without scheduling — used inside TransactionSyncWorker where sync will run immediately.
     * Returns recovered count but does not enqueue another sync work.
     */
    suspend fun recoverWithoutScheduling(): StaleUploadRecoveryResult {
        return syncQueueRepository.recoverStaleUploadingItems()
    }
}
