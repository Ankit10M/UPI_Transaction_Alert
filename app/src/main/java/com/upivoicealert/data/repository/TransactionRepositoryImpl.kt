package com.upivoicealert.data.repository

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

    override suspend fun isDuplicate(transaction: Transaction): Boolean {
        transaction.transactionId?.let { id ->
            if (transactionDao.findByReferenceId(id, transaction.upiApp) != null) return true
        } ?: run {
            val windowStart = transaction.createdAt - Constants.DEDUP_WINDOW_MS
            val windowEnd = transaction.createdAt + Constants.DEDUP_WINDOW_MS
            if (transactionDao.findFuzzyDuplicate(
                    amount = transaction.amount,
                    sender = transaction.sender,
                    upiApp = transaction.upiApp,
                    windowStart = windowStart,
                    windowEnd = windowEnd
                ) != null
            ) {
                return true
            }
        }
        return false
    }

    override suspend fun insertTransactionIfNotDuplicate(transaction: Transaction): Boolean {
        if (isDuplicate(transaction)) return false
        return transactionDao.insert(transaction.toEntity()) != -1L
    }

    override suspend fun addUnparsedNotification(notification: UnparsedNotification) {
        unparsedNotificationDao.insert(notification.toEntity())
    }

    override fun observeUnparsedNotifications(): Flow<List<UnparsedNotification>> =
        unparsedNotificationDao.observeAll().map { list -> list.map { it.toDomain() } }

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
}