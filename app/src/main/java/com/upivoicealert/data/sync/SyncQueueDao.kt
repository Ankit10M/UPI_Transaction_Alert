package com.upivoicealert.data.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO for sync queue. Follows existing DAO style (suspend functions, no Flow for pending).
 * Phase 7.2 extends with failure diagnostics and controlled retry.
 */
@Dao
interface SyncQueueDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SyncQueueEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(item: SyncQueueEntity): Long

    @Query("SELECT * FROM sync_queue WHERE status = :status ORDER BY createdAt ASC")
    suspend fun getByStatus(status: String): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingItems(): List<SyncQueueEntity>

    @Query("UPDATE sync_queue SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, updatedAt: Long)

    @Query("UPDATE sync_queue SET status = :newStatus, updatedAt = :updatedAt WHERE id = :id AND status = :expectedStatus")
    suspend fun updateStatusIfExpected(id: Long, expectedStatus: String, newStatus: String, updatedAt: Long): Int = 1

    @Query("UPDATE sync_queue SET retryCount = retryCount + 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun incrementRetryCount(id: Long, updatedAt: Long)

    @Query("UPDATE sync_queue SET retryCount = retryCount + 1, updatedAt = :updatedAt WHERE id = :id AND status = :expectedStatus")
    suspend fun incrementRetryCountIfExpected(id: Long, expectedStatus: String, updatedAt: Long): Int = 1

    @Query("SELECT * FROM sync_queue ORDER BY createdAt ASC")
    suspend fun getAll(): List<SyncQueueEntity>

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sync_queue")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = :status")
    fun observeCountByStatus(status: String): kotlinx.coroutines.flow.Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = :status")
    suspend fun getCountByStatus(status: String): Int

    // ─── Phase 7.2: failure diagnostics & recovery ──────────────────────────

    @Query("SELECT * FROM sync_queue WHERE status = 'FAILED' ORDER BY failedAt DESC, updatedAt DESC")
    suspend fun getFailedItems(): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SyncQueueEntity?

    /**
     * Mark a FAILED item as FAILED with diagnostics. Only updates diagnostics fields
     * when the item exists — caller ensures status transition is appropriate.
     * Updates status, errorCode, errorMessage, failedAt, updatedAt in one atomic operation.
     */
    @Query(
        "UPDATE sync_queue SET status = :status, lastErrorCode = :errorCode, " +
            "lastErrorMessage = :errorMessage, failedAt = :failedAt, updatedAt = :updatedAt " +
            "WHERE id = :id"
    )
    suspend fun markFailedWithDiagnostics(
        id: Long,
        status: String,
        errorCode: String?,
        errorMessage: String?,
        failedAt: Long?,
        updatedAt: Long
    )

    @Query(
        "UPDATE sync_queue SET status = :status, lastErrorCode = :errorCode, " +
            "lastErrorMessage = :errorMessage, failedAt = :failedAt, updatedAt = :updatedAt " +
            "WHERE id = :id AND status = :expectedStatus"
    )
    suspend fun markFailedWithDiagnosticsIfExpected(
        id: Long,
        status: String,
        errorCode: String?,
        errorMessage: String?,
        failedAt: Long?,
        updatedAt: Long,
        expectedStatus: String
    ): Int = 1

    /**
     * Controlled retry: FAILED -> PENDING. Concurrency-safe: only succeeds if current
     * status is FAILED. Clears diagnostics (errorCode/message/failedAt) and preserves retryCount.
     * Returns number of rows updated (0 = not found or invalid state, 1 = success).
     */
    @Query(
        "UPDATE sync_queue SET status = 'PENDING', lastErrorCode = NULL, " +
            "lastErrorMessage = NULL, failedAt = NULL, updatedAt = :updatedAt " +
            "WHERE id = :id AND status = 'FAILED'"
    )
    suspend fun retryFailedItem(id: Long, updatedAt: Long): Int

    /**
     * Bulk retry: all FAILED -> PENDING. Only affects rows where status='FAILED'.
     * Clears diagnostics, preserves retryCount. Returns rows affected.
     */
    @Query(
        "UPDATE sync_queue SET status = 'PENDING', lastErrorCode = NULL, " +
            "lastErrorMessage = NULL, failedAt = NULL, updatedAt = :updatedAt " +
            "WHERE status = 'FAILED'"
    )
    suspend fun retryAllFailed(updatedAt: Long): Int

    @Query("SELECT * FROM sync_queue WHERE status = 'FAILED' ORDER BY failedAt DESC")
    fun observeFailedItems(): kotlinx.coroutines.flow.Flow<List<SyncQueueEntity>>

    // ─── Phase 7.4: stale UPLOADING recovery ───────────────────────────────

    @Query("SELECT * FROM sync_queue WHERE status = 'UPLOADING' AND updatedAt < :cutoffTime ORDER BY updatedAt ASC")
    suspend fun getStaleUploadingItems(cutoffTime: Long): List<SyncQueueEntity> = emptyList()

    @Query(
        "UPDATE sync_queue SET status = 'PENDING', updatedAt = :updatedAt " +
            "WHERE status = 'UPLOADING' AND updatedAt < :cutoffTime"
    )
    suspend fun recoverStaleUploading(cutoffTime: Long, updatedAt: Long): Int = 0

    // ─── Phase 7.5: integrity audit — efficient COUNT/EXISTS queries ────────

    @Query("SELECT COUNT(*) FROM sync_queue WHERE entityType = 'TRANSACTION'")
    suspend fun countTransactionQueueItems(): Int = 0

    @Query("SELECT COUNT(*) FROM sync_queue")
    suspend fun countAllQueueItems(): Int = 0

    @Query(
        """
        SELECT COUNT(*) FROM sync_queue
        WHERE entityType = 'TRANSACTION'
        AND NOT EXISTS (
            SELECT 1 FROM transactions
            WHERE transactions.transactionUuid = sync_queue.entityId
        )
        """
    )
    suspend fun countOrphanedQueueItems(): Int = 0

    /**
     * Defensive duplicate check: extra duplicate rows beyond the first per logical key.
     * With unique index (entityType, entityId) this should be 0. Counts extra rows,
     * not duplicate groups, so a group of 3 identical keys counts as 2.
     * Uses COUNT(*) - COUNT(DISTINCT) to avoid loading groups into memory.
     */
    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM sync_queue WHERE entityType = 'TRANSACTION')
            - (SELECT COUNT(DISTINCT entityId) FROM sync_queue WHERE entityType = 'TRANSACTION')
        """
    )
    suspend fun countDuplicateExtraRows(): Int = 0

    @Query(
        """
        SELECT COUNT(*) FROM (
            SELECT entityType, entityId FROM sync_queue
            GROUP BY entityType, entityId HAVING COUNT(*) > 1
        )
        """
    )
    suspend fun countDuplicateGroups(): Int = 0

    @Query(
        """
        SELECT COUNT(*) FROM sync_queue
        WHERE TRIM(entityId) = ''
           OR entityType != 'TRANSACTION'
           OR status NOT IN ('PENDING','UPLOADING','SYNCED','FAILED')
        """
    )
    suspend fun countInvalidQueueItems(): Int = 0

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'UPLOADING' AND updatedAt < :cutoffTime")
    suspend fun countStaleUploading(cutoffTime: Long): Int = 0
}
