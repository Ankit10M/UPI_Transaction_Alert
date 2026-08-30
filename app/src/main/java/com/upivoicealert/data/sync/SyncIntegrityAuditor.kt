package com.upivoicealert.data.sync

import com.upivoicealert.data.database.TransactionDao
import com.upivoicealert.domain.sync.SyncIntegrityResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 7.5 integrity auditor — local-first, Room-only, read-only, non-destructive.
 * Never sits in the payment announcement path; never blocks TTS or transaction save.
 * Operates asynchronously via WorkManager only.
 *
 * All checks are efficient COUNT/EXISTS/GROUP BY queries, not full table loads.
 * Suitable for 10k+ transactions.
 *
 * Category counts:
 * - missingQueue: eligible SUCCESS+RECEIVED with valid transactionUuid but no TRANSACTION queue row
 * - orphanedQueue: TRANSACTION queue row with no matching transactionUuid
 * - duplicateQueue: extra duplicate rows beyond first per (entityType, entityId) — defensive
 * - invalidQueue: blank entityId, unknown entityType, or unsupported status
 * - staleUploading: UPLOADING rows older than threshold (reuses SyncQueueRepositoryImpl threshold)
 */
@Singleton
open class SyncIntegrityAuditor @Inject constructor(
    private val transactionDao: TransactionDao,
    private val syncQueueDao: SyncQueueDao
) {

    open suspend fun audit(): SyncIntegrityResult {
        val now = System.currentTimeMillis()
        val staleCutoff = now - SyncQueueRepositoryImpl.STALE_UPLOADING_THRESHOLD_MS

        // Efficient COUNT queries — no table materialization
        val scannedTransactions = transactionDao.countEligibleForAudit()
        val scannedQueueItems = syncQueueDao.countTransactionQueueItems()

        val missingQueueCount = transactionDao.countMissingQueueForAudit()
        val orphanedQueueCount = syncQueueDao.countOrphanedQueueItems()
        // Prefer extra duplicate rows (problematic records), not duplicate groups
        val duplicateQueueCount = syncQueueDao.countDuplicateExtraRows().coerceAtLeast(0)
        val invalidQueueCount = syncQueueDao.countInvalidQueueItems()
        val staleUploadingCount = syncQueueDao.countStaleUploading(staleCutoff)

        return SyncIntegrityResult(
            scannedTransactions = scannedTransactions,
            scannedQueueItems = scannedQueueItems,
            missingQueueCount = missingQueueCount,
            orphanedQueueCount = orphanedQueueCount,
            duplicateQueueCount = duplicateQueueCount,
            invalidQueueCount = invalidQueueCount,
            staleUploadingCount = staleUploadingCount,
            checkedAt = now
        )
    }
}
