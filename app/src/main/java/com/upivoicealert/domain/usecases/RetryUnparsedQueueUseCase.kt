package com.upivoicealert.domain.usecases

import com.upivoicealert.domain.repository.TransactionRepository
import javax.inject.Inject

class RetryUnparsedQueueUseCase @Inject constructor(
    private val repository: TransactionRepository,
    private val processTransactionUseCase: ProcessTransactionUseCase
) {

    /**
     * Re-runs the full pipeline for each queued unparsed notification. Records are
     * deleted before reprocessing to avoid queue duplication; records that still fail
     * are re-added by the pipeline. Returns the number of newly-processed transactions.
     *
     * Day 0 product fix: the START/STOP state gates ONLY the TTS announcement, so
     * retries always run the pipeline — a payment re-parsed while the service is
     * stopped is still saved (history + business summary update) but not spoken.
     */
    suspend fun retryAll(): Int {
        val pending = repository.getUnparsedNotifications()
        var succeeded = 0
        for (item in pending) {
            repository.deleteUnparsedNotification(item.id)
            val result = processTransactionUseCase.processNotification(
                packageName = item.packageName,
                rawText = item.rawNotification,
                postTime = item.createdAt
            )
            if (result == ProcessingResult.SAVED) {
                succeeded++
            }
        }
        return succeeded
    }
}