package com.upivoicealert.data.sync

import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import com.upivoicealert.data.database.TransactionDao
import com.upivoicealert.data.datastore.ReconciliationStatusRecorder
import com.upivoicealert.domain.sync.ReconciliationResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local integrity reconciler: ensures every eligible transaction (SUCCESS + RECEIVED)
 * has a SyncQueue entry. Only creates missing PENDING items; never modifies existing rows.
 * Idempotent, concurrency-safe via unique index (entityType, entityId) + INSERT IGNORE.
 * Never blocks payment detection/TTS — runs offline, asynchronously via WorkManager.
 */
@Singleton
open class TransactionSyncReconciler @Inject constructor(
    private val transactionDao: TransactionDao,
    private val syncQueueDao: SyncQueueDao,
    private val reconciliationStatusStore: ReconciliationStatusRecorder
) {
    companion object {
        private const val TAG = "SyncReconciler"
        const val BATCH_SIZE = 100
    }

    open suspend fun reconcile(): ReconciliationResult {
        var scanned = 0
        var repaired = 0
        var skipped = 0
        var errors = 0

        try {
            val totalEligible = try {
                transactionDao.countEligibleTransactions()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to count eligible", e)
                throw e
            }

            // Paginated scan with offset 0 loop: after repairing a batch, the missing set shrinks,
            // so next query with offset 0 returns the next page of still-missing. This avoids
            // offset-shift issues and guarantees we eventually drain all missing.
            var iterations = 0
            while (iterations < 1000) {
                iterations++
                val missingUuids = try {
                    transactionDao.getEligibleMissingQueueUuids(BATCH_SIZE, 0)
                } catch (e: Exception) {
                    Log.w(TAG, "DB transient error during scan", e)
                    throw e
                }

                if (missingUuids.isEmpty()) break

                for (uuid in missingUuids) {
                    scanned++
                    try {
                        val now = System.currentTimeMillis()
                        val entity = SyncQueueEntity(
                            entityType = SyncQueueEntity.ENTITY_TYPE_TRANSACTION,
                            entityId = uuid,
                            status = SyncQueueEntity.STATUS_PENDING,
                            retryCount = 0,
                            createdAt = now,
                            updatedAt = now,
                            lastErrorCode = null,
                            lastErrorMessage = null,
                            failedAt = null
                        )
                        val rowId = syncQueueDao.insertIgnore(entity)
                        if (rowId != -1L) {
                            repaired++
                        } else {
                            // Already exists (race) — treat as skipped
                            skipped++
                        }
                    } catch (e: SQLiteConstraintException) {
                        Log.d(TAG, "Ignore duplicate constraint for $uuid", e)
                        skipped++
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to repair $uuid", e)
                        errors++
                    }
                }
            }

            val alreadyQueued = (totalEligible - scanned).coerceAtLeast(0)

            val result = ReconciliationResult(
                scannedCount = scanned,
                alreadyQueuedCount = alreadyQueued,
                repairedCount = repaired,
                skippedCount = skipped,
                errorCount = errors
            )

            try {
                reconciliationStatusStore.recordReconciliation(System.currentTimeMillis(), repaired)
            } catch (_: Exception) { /* non-critical */ }

            Log.i(TAG, "Reconciliation done: $result")
            return result

        } catch (e: Exception) {
            Log.w(TAG, "Reconciliation failed", e)
            throw e
        }
    }
}
