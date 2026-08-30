package com.upivoicealert.data.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDiagnosticDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: SyncDiagnosticEventEntity): Long

    @Query("SELECT * FROM sync_diagnostic_events ORDER BY createdAt DESC, id DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<SyncDiagnosticEventEntity>

    @Query("SELECT * FROM sync_diagnostic_events ORDER BY createdAt DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SyncDiagnosticEventEntity>>

    @Query("SELECT COUNT(*) FROM sync_diagnostic_events")
    suspend fun count(): Int = 0

    @Query("DELETE FROM sync_diagnostic_events WHERE createdAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int = 0

    @Query("DELETE FROM sync_diagnostic_events")
    suspend fun clearAll()

    /**
     * Trim to maxCount newest events. Deletes oldest rows beyond maxCount.
     * SQL: keep newest maxCount by createdAt DESC, id DESC.
     */
    @Query(
        """
        DELETE FROM sync_diagnostic_events WHERE id IN (
            SELECT id FROM sync_diagnostic_events ORDER BY createdAt DESC, id DESC LIMIT -1 OFFSET :maxCount
        )
        """
    )
    suspend fun trimToMaxCount(maxCount: Int): Int = 0
}
