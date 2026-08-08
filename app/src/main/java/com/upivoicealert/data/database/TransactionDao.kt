package com.upivoicealert.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE transactionType = 'RECEIVED' AND status = 'SUCCESS' ORDER BY createdAt DESC")
    fun observeReceivedSuccess(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY createdAt DESC LIMIT 1")
    fun observeLatest(): Flow<TransactionEntity?>

    @Query("SELECT COUNT(*) FROM transactions")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM transactions WHERE createdAt >= :since")
    fun observeCountSince(since: Long): Flow<Int>

    @Query("SELECT * FROM transactions WHERE transactionId = :transactionId AND upiApp = :upiApp LIMIT 1")
    suspend fun findByReferenceId(transactionId: String, upiApp: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE TRIM(rawNotification) = TRIM(:rawNotification) AND createdAt BETWEEN :windowStart AND :windowEnd LIMIT 1")
    suspend fun findExactDuplicate(
        rawNotification: String,
        windowStart: Long,
        windowEnd: Long
    ): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: TransactionEntity): Long

    @Query("DELETE FROM transactions")
    suspend fun clearAll()
}