package com.upivoicealert.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.upivoicealert.domain.repository.TransactionRepository
import com.upivoicealert.utils.Constants
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic cleanup of the failed-parse diagnostic queue (CLAUDE.md Section 10,
 * Phase 5). Never used for real-time detection.
 */
@HiltWorker
class CleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val transactionRepository: TransactionRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val cutoff = System.currentTimeMillis() -
                Constants.UNPARSED_RETENTION_DAYS * 24 * 60 * 60 * 1000
            transactionRepository.deleteUnparsedOlderThan(cutoff)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}