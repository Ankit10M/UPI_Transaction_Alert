package com.upivoicealert.domain.repository

import com.upivoicealert.domain.model.Transaction
import com.upivoicealert.domain.model.UnparsedNotification
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {

    fun observeTransactions(): Flow<List<Transaction>>

    fun observeReceivedSuccess(): Flow<List<Transaction>>

    fun observeCount(): Flow<Int>

    fun observeCountSince(since: Long): Flow<Int>

    fun observeLatest(): Flow<Transaction?>

    /**
     * Hybrid deduplication (CLAUDE.md Module 4): reference-ID match first (same
     * ID + app = duplicate; different ID = different payment), then exact same
     * raw notification within the short window when no reference ID exists.
     * Returns true only if the transaction was actually inserted.
     */
    suspend fun insertTransactionIfNotDuplicate(transaction: Transaction): Boolean

    suspend fun isDuplicate(transaction: Transaction): Boolean

    suspend fun addUnparsedNotification(notification: UnparsedNotification)

    fun observeUnparsedNotifications(): Flow<List<UnparsedNotification>>

    suspend fun getUnparsedNotifications(): List<UnparsedNotification>

    suspend fun deleteUnparsedNotification(id: String)

    suspend fun clearUnparsedNotifications()

    suspend fun deleteUnparsedOlderThan(before: Long)

    suspend fun clearAllData()
}