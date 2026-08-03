package com.upivoicealert.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.upivoicealert.domain.usecases.RetryUnparsedQueueUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic retry of the failed-parse queue (CLAUDE.md Section 10, Phase 5).
 */
@HiltWorker
class RetryFailedParseWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val retryUnparsedQueueUseCase: RetryUnparsedQueueUseCase
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            retryUnparsedQueueUseCase.retryAll()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}