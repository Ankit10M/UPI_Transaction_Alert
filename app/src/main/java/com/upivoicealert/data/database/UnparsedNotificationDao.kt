package com.upivoicealert.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UnparsedNotificationDao {

    @Query("SELECT * FROM unparsed_notifications ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<UnparsedNotificationEntity>>

    @Query("SELECT * FROM unparsed_notifications ORDER BY createdAt ASC")
    suspend fun getAll(): List<UnparsedNotificationEntity>

    @Query("SELECT COUNT(*) FROM unparsed_notifications WHERE createdAt >= :since")
    fun observeCountSince(since: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: UnparsedNotificationEntity): Long

    @Query("DELETE FROM unparsed_notifications WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM unparsed_notifications")
    suspend fun clearAll()

    @Query("DELETE FROM unparsed_notifications WHERE createdAt < :before")
    suspend fun deleteOlderThan(before: Long)
}