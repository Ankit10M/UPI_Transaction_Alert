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

    /** Received + success transactions posted at/after [since] (business summary). */
    @Query("SELECT * FROM transactions WHERE transactionType = 'RECEIVED' AND status = 'SUCCESS' AND createdAt >= :since ORDER BY createdAt DESC")
    fun observeReceivedSuccessSince(since: Long): Flow<List<TransactionEntity>>

    /** Most recent received + success transaction matching [amount] at/after [since] (payment verification). */
    @Query("SELECT * FROM transactions WHERE transactionType = 'RECEIVED' AND status = 'SUCCESS' AND amount = :amount AND createdAt >= :since ORDER BY createdAt DESC LIMIT 1")
    suspend fun findRecentReceivedSuccess(amount: Double, since: Long): TransactionEntity?

    @Query("SELECT * FROM transactions ORDER BY createdAt DESC LIMIT 1")
    fun observeLatest(): Flow<TransactionEntity?>

    @Query("SELECT COUNT(*) FROM transactions")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM transactions WHERE createdAt >= :since")
    fun observeCountSince(since: Long): Flow<Int>

    /**
     * Reference-ID / UTR lookup. Source-agnostic on purpose: a UPI reference is
     * globally unique per payment, so the SAME payment reported by GPay and by a
     * bank notification must match regardless of which app label the rows carry.
     */
    @Query("SELECT * FROM transactions WHERE transactionId = :transactionId LIMIT 1")
    suspend fun findByReferenceIdGlobal(transactionId: String): TransactionEntity?

    /**
     * Cross-source fingerprint match within the dedup window. Used when the
     * incoming notification carries NO reference ID — matches any stored row
     * with the same fingerprint in the window.
     */
    @Query("SELECT * FROM transactions WHERE dedupFingerprint = :fingerprint AND createdAt BETWEEN :windowStart AND :windowEnd LIMIT 1")
    suspend fun findByFingerprint(
        fingerprint: String,
        windowStart: Long,
        windowEnd: Long
    ): TransactionEntity?

    /**
     * Fingerprint match restricted to stored rows WITHOUT a reference ID. Used
     * when the incoming notification HAS a reference ID that was not found — an
     * existing row carrying a DIFFERENT reference is a genuinely different
     * payment and must never be blocked; a row without a reference is a likely
     * same-payment report from a source that did not expose the UTR.
     */
    @Query("SELECT * FROM transactions WHERE dedupFingerprint = :fingerprint AND (transactionId IS NULL OR TRIM(transactionId) = '') AND createdAt BETWEEN :windowStart AND :windowEnd LIMIT 1")
    suspend fun findByFingerprintNullRef(
        fingerprint: String,
        windowStart: Long,
        windowEnd: Long
    ): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE TRIM(rawNotification) = TRIM(:rawNotification) AND createdAt BETWEEN :windowStart AND :windowEnd LIMIT 1")
    suspend fun findExactDuplicate(
        rawNotification: String,
        windowStart: Long,
        windowEnd: Long
    ): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: TransactionEntity): Long

    /** Marks a transaction as having been announced by the voice engine. */
    @Query("UPDATE transactions SET voiceAnnounced = 1 WHERE id = :id")
    suspend fun markVoiceAnnounced(id: String)

    @Query("DELETE FROM transactions")
    suspend fun clearAll()
}