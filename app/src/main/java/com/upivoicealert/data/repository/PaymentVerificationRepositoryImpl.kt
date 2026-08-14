package com.upivoicealert.data.repository

import com.upivoicealert.data.database.TransactionDao
import com.upivoicealert.data.model.toDomain
import com.upivoicealert.domain.model.Transaction
import com.upivoicealert.domain.repository.PaymentVerificationRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-based payment verification (Feature 1). Searches the REAL stored
 * transaction history — no fabricated records.
 */
@Singleton
class PaymentVerificationRepositoryImpl @Inject constructor(
    private val transactionDao: TransactionDao
) : PaymentVerificationRepository {

    override suspend fun findRecentReceived(amount: Double, since: Long): Transaction? =
        transactionDao.findRecentReceivedSuccess(amount, since)?.toDomain()
}
