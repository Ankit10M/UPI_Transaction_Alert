package com.upivoicealert

import com.upivoicealert.domain.model.ParseStatus
import com.upivoicealert.domain.model.Transaction
import com.upivoicealert.domain.model.TransactionStatus
import com.upivoicealert.domain.model.TransactionType
import com.upivoicealert.domain.model.UnparsedNotification
import com.upivoicealert.domain.repository.TransactionRepository
import com.upivoicealert.domain.usecases.BusinessSummaryUseCase
import com.upivoicealert.utils.DateTimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BusinessSummaryUseCaseTest {

    private val today = DateTimeUtils.startOfToday()
    private val hour = 3_600_000L

    @Test
    fun `computes daily totals from real transactions`() = runTest {
        val transactions = listOf(
            transaction(amount = 500.0, createdAt = today + 10 * hour + 30 * 60_000L),
            transaction(amount = 200.0, createdAt = today + 10 * hour + 45 * 60_000L),
            transaction(amount = 300.0, createdAt = today + 14 * hour + 15 * 60_000L)
        )
        val useCase = BusinessSummaryUseCase(FakeTransactionRepository(transactions))

        val summary = useCase.observeTodaySummary().first()

        assertEquals(1000.0, summary.totalCollection, 0.001)
        assertEquals(3, summary.transactionCount)
        assertEquals(1000.0 / 3, summary.averageTransactionValue, 0.001)
        assertEquals(500.0, summary.largestPayment, 0.001)
        assertEquals(10, summary.peakPaymentHour) // two payments at 10am -> peak hour
    }

    @Test
    fun `empty day produces zeroed summary with no peak hour`() = runTest {
        val useCase = BusinessSummaryUseCase(FakeTransactionRepository(emptyList()))

        val summary = useCase.observeTodaySummary().first()

        assertEquals(0.0, summary.totalCollection, 0.001)
        assertEquals(0, summary.transactionCount)
        assertEquals(0.0, summary.averageTransactionValue, 0.001)
        assertEquals(0.0, summary.largestPayment, 0.001)
        assertNull(summary.peakPaymentHour)
    }

    @Test
    fun `multiple valid payments sum to total collection`() = runTest {
        // TEST CASE 4: three distinct payments of ₹10, ₹20, ₹50 -> collection ₹80.
        val transactions = listOf(
            transaction(amount = 10.0, createdAt = today + 10 * hour),
            transaction(amount = 20.0, createdAt = today + 11 * hour),
            transaction(amount = 50.0, createdAt = today + 12 * hour)
        )
        val useCase = BusinessSummaryUseCase(FakeTransactionRepository(transactions))

        val summary = useCase.observeTodaySummary().first()

        assertEquals(80.0, summary.totalCollection, 0.001)
        assertEquals(3, summary.transactionCount)
        assertEquals(80.0 / 3, summary.averageTransactionValue, 0.001)
        assertEquals(50.0, summary.largestPayment, 0.001)
    }

    private fun transaction(amount: Double, createdAt: Long) = Transaction(
        id = "t-$amount-$createdAt",
        amount = amount,
        sender = "Rahul",
        upiApp = "PhonePe",
        transactionType = TransactionType.RECEIVED,
        status = TransactionStatus.SUCCESS,
        transactionId = null,
        rawNotification = "Received ₹$amount from Rahul",
        parserVersion = "TestParserV1",
        parseStatus = ParseStatus.PARSED,
        createdAt = createdAt
    )
}

/** Minimal fake: only the flow the use case reads is implemented. */
private class FakeTransactionRepository(
    private val todayTransactions: List<Transaction>
) : TransactionRepository {

    override fun observeTransactions(): Flow<List<Transaction>> = flowOf(emptyList())
    override fun observeReceivedSuccess(): Flow<List<Transaction>> = flowOf(todayTransactions)
    override fun observeReceivedSuccessSince(since: Long): Flow<List<Transaction>> = flowOf(todayTransactions)
    override fun observeCount(): Flow<Int> = flowOf(todayTransactions.size)
    override fun observeCountSince(since: Long): Flow<Int> = flowOf(todayTransactions.size)
    override fun observeLatest(): Flow<Transaction?> = flowOf(todayTransactions.firstOrNull())
    override suspend fun insertTransactionIfNotDuplicate(transaction: Transaction): Boolean = false
    override suspend fun isDuplicate(transaction: Transaction): Boolean = false
    override suspend fun addUnparsedNotification(notification: UnparsedNotification) = Unit
    override fun observeUnparsedNotifications(): Flow<List<UnparsedNotification>> = flowOf(emptyList())
    override fun observeUnparsedCountSince(since: Long): Flow<Int> = flowOf(0)
    override suspend fun markVoiceAnnounced(id: String) = Unit
    override suspend fun getUnparsedNotifications(): List<UnparsedNotification> = emptyList()
    override suspend fun deleteUnparsedNotification(id: String) = Unit
    override suspend fun clearUnparsedNotifications() = Unit
    override suspend fun deleteUnparsedOlderThan(before: Long) = Unit
    override suspend fun clearAllData() = Unit
}
