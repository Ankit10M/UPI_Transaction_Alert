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

    override fun observeReceivedSuccessSince(since: Long): Flow<List<Transaction>> =
        transactionDao.observeReceivedSuccessSince(since).map { list -> list.map { it.toDomain() } }

    override fun observeLatest(): Flow<Transaction?> =
        transactionDao.observeLatest().map { it?.toDomain() }

    override fun observeCount(): Flow<Int> = transactionDao.observeCount()

    override fun observeCountSince(since: Long): Flow<Int> = transactionDao.observeCountSince(since)

    /**
     * Hybrid deduplication (CLAUDE.md Module 4, production fixes):
     *
     * 1. UPI reference ID / UTR (primary): matched GLOBALLY, source-agnostic.
     *    A UPI reference is unique per payment, so a reference reported by GPay
     *    and by a bank notification must resolve to the SAME transaction — the
     *    previous per-app restriction caused one payment to be stored twice and
     *    the Business Summary to double-count the collection.
     * 2. Cross-source fingerprint (secondary, when the reference is unavailable
     *    on one or both sides): amount + normalized sender + transaction type
     *    within the dedup window. If the incoming carries a reference that was
     *    not found, only existing rows WITHOUT a reference are matched (an
     *    existing row with a different reference is a genuinely different
     *    payment and is never blocked).
     * 3. Exact same cleaned notification reposted within the window (tertiary,
     *    covers the "Processing -> Success" repost pattern).
     *
     * Every decision is logged under tag "UPI_DUPLICATE_DEBUG".
     */
    override suspend fun isDuplicate(transaction: Transaction): Boolean {
        val windowStart = transaction.createdAt - Constants.DEDUP_WINDOW_MS
        val windowEnd = transaction.createdAt + Constants.DEDUP_WINDOW_MS
        val fingerprint = TransactionFingerprint.compute(transaction)
        val incomingRef = transaction.transactionId?.trim()?.takeIf { it.isNotEmpty() }
        Log.i(
            DUP_TAG,
            "CHECK_START amount=${transaction.amount} sender=${transaction.sender} app=${transaction.upiApp} " +
                "package=${transaction.packageName} referenceId=${incomingRef ?: "<none>"} " +
                "generatedFingerprint=${fingerprint ?: "<none>"} createdAt=${transaction.createdAt} " +
                "windowStart=$windowStart windowEnd=$windowEnd"
        )

        // Priority 1: UPI reference ID / UTR — globally unique across all sources.
        if (incomingRef != null) {
            val existing = transactionDao.findByReferenceIdGlobal(incomingRef)
            if (existing != null) {
                Log.i(
                    DUP_TAG,
                    "DECISION=DUPLICATE matched=referenceId reason=same_reference_id " +
                        "incomingRef=$incomingRef existingId=${existing.id} existingApp=${existing.upiApp} " +
                        "existingRef=${existing.transactionId ?: "<none>"}"
                )
                return true
            }
            Log.i(
                DUP_TAG,
                "DECISION=NOT_DUPLICATE_YET reason=reference_id_not_found checkedRef=$incomingRef"
            )
        }

        // Priority 2: cross-source fingerprint within the window.
        if (fingerprint != null) {
            val existing = if (incomingRef != null) {
                transactionDao.findByFingerprintNullRef(fingerprint, windowStart, windowEnd)
            } else {
                transactionDao.findByFingerprint(fingerprint, windowStart, windowEnd)
            }
            if (existing != null) {
                Log.i(
                    DUP_TAG,
                    "DECISION=DUPLICATE matched=fingerprint reason=cross_source_fingerprint " +
                        "amount=${transaction.amount} sender=${transaction.sender} fingerprint=$fingerprint " +
                        "existingId=${existing.id} existingApp=${existing.upiApp} " +
                        "existingRef=${existing.transactionId ?: "<none>"}"
                )
                return true
            }
            Log.i(
                DUP_TAG,
                "DECISION=NOT_DUPLICATE_YET reason=fingerprint_no_match fingerprint=$fingerprint"
            )
        }

        // Priority 3 (fallback): exact same notification reposted within the window.
        val existing = transactionDao.findExactDuplicate(transaction.rawNotification, windowStart, windowEnd)
        if (existing != null) {
            Log.i(
                DUP_TAG,
                "DECISION=DUPLICATE matched=rawNotification reason=exact_notification_reposted " +
                    "windowStart=$windowStart windowEnd=$windowEnd existingId=${existing.id} " +
                    "existingCreatedAt=${existing.createdAt}"
            )
            return true
        }
        Log.i(
            DUP_TAG,
            "DECISION=NOT_DUPLICATE reason=no_match_found windowStart=$windowStart windowEnd=$windowEnd"
        )
        return false
    }

    override suspend fun insertTransactionIfNotDuplicate(transaction: Transaction): Boolean {
        if (isDuplicate(transaction)) {
            Log.i(DUP_TAG, "IGNORED id=${transaction.id} incomingRef=${transaction.transactionId ?: "<none>"} reason=duplicate")
            return false
        }
        val fingerprint = TransactionFingerprint.compute(transaction)
        val rowId = transactionDao.insert(transaction.copy(dedupFingerprint = fingerprint).toEntity())
        val inserted = rowId != -1L
        Log.i(
            DUP_TAG,
            if (inserted) "INSERTED id=${transaction.id} incomingRef=${transaction.transactionId ?: "<none>"} fingerprint=$fingerprint"
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