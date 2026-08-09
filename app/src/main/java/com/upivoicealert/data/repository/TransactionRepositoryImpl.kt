package com.upivoicealert.data.repository

import android.util.Log
import com.upivoicealert.data.database.TransactionDao
import com.upivoicealert.data.database.UnparsedNotificationDao
import com.upivoicealert.data.model.toDomain
import com.upivoicealert.data.model.toEntity
import com.upivoicealert.domain.model.Transaction
import com.upivoicealert.domain.model.UnparsedNotification
import com.upivoicealert.domain.repository.TransactionRepository
import com.upivoicealert.utils.Constants
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class TransactionRepositoryImpl @Inject constructor(
    private val transactionDao: TransactionDao,
    private val unparsedNotificationDao: UnparsedNotificationDao
) : TransactionRepository {

    override fun observeTransactions(): Flow<List<Transaction>> =
        transactionDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeReceivedSuccess(): Flow<List<Transaction>> =
        transactionDao.observeReceivedSuccess().map { list -> list.map { it.toDomain() } }

    override fun observeLatest(): Flow<Transaction?> =
        transactionDao.observeLatest().map { it?.toDomain() }

    override fun observeCount(): Flow<Int> = transactionDao.observeCount()

    override fun observeCountSince(since: Long): Flow<Int> = transactionDao.observeCountSince(since)

    /**
     * Hybrid deduplication (CLAUDE.md Module 4, priorities as fixed):
     *
     * 1. UPI reference ID / transaction ID: if the incoming transaction carries
     *    one, it is a duplicate only when the SAME ID is already stored for the
     *    same UPI app. A different ID means a different payment — never blocked.
     * 2. No reference ID available: duplicate only when the EXACT same raw
     *    notification text was stored within the short window (covers the
     *    "Processing -> Success" repost pattern). Amount + sender + app alone is
     *    NOT sufficient — distinct same-amount payments must not be blocked.
     *
     * Every decision is logged under tag "UPI_DUPLICATE_DEBUG".
     */
    override suspend fun isDuplicate(transaction: Transaction): Boolean {
        Log.i(
            DUP_TAG,
            "CHECK_START amount=${transaction.amount} sender=${transaction.sender} app=${transaction.upiApp} incomingRef=${transaction.transactionId ?: "<none>"} createdAt=${transaction.createdAt}"
        )

        // Priority 1 & 2: UPI reference ID / transaction ID.
        val incomingRef = transaction.transactionId
        if (incomingRef != null) {
            val existing = transactionDao.findByReferenceId(incomingRef, transaction.upiApp)
            if (existing != null) {
                Log.i(
                    DUP_TAG,
                    "DECISION=DUPLICATE reason=same_reference_id incomingRef=$incomingRef app=${transaction.upiApp} existingId=${existing.id} existingRef=${existing.transactionId}"
                )
                return true
            }
            Log.i(
                DUP_TAG,
                "DECISION=NOT_DUPLICATE reason=reference_id_not_found checkedRef=$incomingRef app=${transaction.upiApp} existingId=<none>"
            )
            return false
        }

        // Priority 3 (fallback): exact same notification reposted within the window.
        val windowStart = transaction.createdAt - Constants.DEDUP_WINDOW_MS
        val windowEnd = transaction.createdAt + Constants.DEDUP_WINDOW_MS
        val existing = transactionDao.findExactDuplicate(transaction.rawNotification, windowStart, windowEnd)
        if (existing != null) {
            Log.i(
                DUP_TAG,
                "DECISION=DUPLICATE reason=exact_notification_reposted windowStart=$windowStart windowEnd=$windowEnd existingId=${existing.id} existingCreatedAt=${existing.createdAt}"
            )
            return true
        }
        Log.i(
            DUP_TAG,
            "DECISION=NOT_DUPLICATE reason=no_exact_notification_in_window windowStart=$windowStart windowEnd=$windowEnd"
        )
        return false
    }

    override suspend fun insertTransactionIfNotDuplicate(transaction: Transaction): Boolean {
        if (isDuplicate(transaction)) {
            Log.i(DUP_TAG, "IGNORED id=${transaction.id} incomingRef=${transaction.transactionId ?: "<none>"} reason=duplicate")
            return false
        }
        val rowId = transactionDao.insert(transaction.toEntity())
        val inserted = rowId != -1L
        Log.i(
            DUP_TAG,
            if (inserted) "INSERTED id=${transaction.id} incomingRef=${transaction.transactionId ?: "<none>"}"
            else "INSERT_CONFLICT id=${transaction.id} incomingRef=${transaction.transactionId ?: "<none>"}"
        )
        return inserted
    }

    override suspend fun addUnparsedNotification(notification: UnparsedNotification) {
        unparsedNotificationDao.insert(notification.toEntity())
    }

    override fun observeUnparsedNotifications(): Flow<List<UnparsedNotification>> =
        unparsedNotificationDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeUnparsedCountSince(since: Long): Flow<Int> =
        unparsedNotificationDao.observeCountSince(since)

    override suspend fun markVoiceAnnounced(id: String) {
        transactionDao.markVoiceAnnounced(id)
    }

    override suspend fun getUnparsedNotifications(): List<UnparsedNotification> =
        unparsedNotificationDao.getAll().map { it.toDomain() }

    override suspend fun deleteUnparsedNotification(id: String) {
        unparsedNotificationDao.deleteById(id)
    }

    override suspend fun clearUnparsedNotifications() {
        unparsedNotificationDao.clearAll()
    }

    override suspend fun deleteUnparsedOlderThan(before: Long) {
        unparsedNotificationDao.deleteOlderThan(before)
    }

    override suspend fun clearAllData() {
        transactionDao.clearAll()
        unparsedNotificationDao.clearAll()
    }

    private companion object {
        const val DUP_TAG = "UPI_DUPLICATE_DEBUG"
    }
}