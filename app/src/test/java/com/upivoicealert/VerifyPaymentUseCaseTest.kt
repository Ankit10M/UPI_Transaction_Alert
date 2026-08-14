package com.upivoicealert

import com.upivoicealert.domain.model.ParseStatus
import com.upivoicealert.domain.model.Transaction
import com.upivoicealert.domain.model.TransactionStatus
import com.upivoicealert.domain.model.TransactionType
import com.upivoicealert.domain.model.VerificationResult
import com.upivoicealert.domain.repository.PaymentVerificationRepository
import com.upivoicealert.domain.usecases.VerifyPaymentUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifyPaymentUseCaseTest {

    @Test
    fun `returns verified transaction when room match exists`() = runTest {
        val match = transaction(id = "tx-1", amount = 500.0)
        val useCase = VerifyPaymentUseCase(FakeVerificationRepository(match))

        val result = useCase(500.0)

        assertTrue(result is VerificationResult.Verified)
        assertEquals(match, (result as VerificationResult.Verified).transaction)
    }

    @Test
    fun `returns not found when no room match exists`() = runTest {
        val useCase = VerifyPaymentUseCase(FakeVerificationRepository(null))

        val result = useCase(500.0)

        assertEquals(VerificationResult.NotFound, result)
    }

    @Test
    fun `queries repository with the expected amount`() = runTest {
        val repository = FakeVerificationRepository(transaction(id = "tx-2", amount = 250.0))
        val useCase = VerifyPaymentUseCase(repository)

        useCase(250.0)

        assertEquals(250.0, repository.lastAmount ?: -1.0, 0.001)
    }

    private fun transaction(id: String, amount: Double) = Transaction(
        id = id,
        amount = amount,
        sender = "Rahul",
        upiApp = "PhonePe",
        transactionType = TransactionType.RECEIVED,
        status = TransactionStatus.SUCCESS,
        transactionId = "ref-1",
        rawNotification = "Received ₹$amount from Rahul",
        parserVersion = "TestParserV1",
        parseStatus = ParseStatus.PARSED,
        createdAt = System.currentTimeMillis()
    )
}

private class FakeVerificationRepository(
    private val match: Transaction?
) : PaymentVerificationRepository {

    var lastAmount: Double? = null

    override suspend fun findRecentReceived(amount: Double, since: Long): Transaction? {
        lastAmount = amount
        return match?.takeIf { it.amount == amount }
    }
}
