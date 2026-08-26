package com.upivoicealert

import com.upivoicealert.data.cloudtransaction.CloudTransactionApi
import com.upivoicealert.data.cloudtransaction.CloudTransactionDto
import com.upivoicealert.data.cloudtransaction.CloudTransactionRepository
import com.upivoicealert.data.cloudtransaction.CloudTransactionsResponseDto
import com.upivoicealert.data.cloudtransaction.PaginationDto
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class CloudTransactionRepositoryTest {

    private fun dto() = CloudTransactionDto(
        transactionUuid = "550e8400-e29b-41d4-a716-446655440001",
        amount = 500.0,
        currency = "INR",
        senderName = "Rahul",
        senderVpa = "rahul@upi",
        upiReference = "REF123",
        upiApp = "PhonePe",
        transactionTime = "2026-03-10T10:00:00.000Z",
        status = "SUCCESS"
    )

    @Test
    fun `successful fetch returns transactions`() = runTest {
        val api = object : CloudTransactionApi {
            override suspend fun getTransactions(page: Int, limit: Int, startDate: String?, endDate: String?): CloudTransactionsResponseDto {
                return CloudTransactionsResponseDto(
                    transactions = listOf(dto()),
                    pagination = PaginationDto(1, 20, 1, 1)
                )
            }
            override suspend fun getSummary() = throw NotImplementedError()
        }
        val repo = CloudTransactionRepository(api)
        val result = repo.getTransactions()
        assertTrue(result is CloudTransactionRepository.CloudResult.Success)
        val data = (result as CloudTransactionRepository.CloudResult.Success).data
        assertEquals(1, data.first.size)
        assertEquals("550e8400-e29b-41d4-a716-446655440001", data.first[0].transactionUuid)
        // Ensure DTO not leaked raw, domain has same
        assertEquals("INR", data.first[0].currency)
    }

    @Test
    fun `empty response returns empty list`() = runTest {
        val api = object : CloudTransactionApi {
            override suspend fun getTransactions(page: Int, limit: Int, startDate: String?, endDate: String?): CloudTransactionsResponseDto {
                return CloudTransactionsResponseDto(emptyList(), PaginationDto(1, 20, 0, 0))
            }
            override suspend fun getSummary() = throw NotImplementedError()
        }
        val repo = CloudTransactionRepository(api)
        val result = repo.getTransactions()
        assertTrue(result is CloudTransactionRepository.CloudResult.Success)
        val data = (result as CloudTransactionRepository.CloudResult.Success).data
        assertTrue(data.first.isEmpty())
    }

    @Test
    fun `network failure returns Error`() = runTest {
        val api = object : CloudTransactionApi {
            override suspend fun getTransactions(page: Int, limit: Int, startDate: String?, endDate: String?): CloudTransactionsResponseDto {
                throw IOException("offline")
            }
            override suspend fun getSummary() = throw NotImplementedError()
        }
        val repo = CloudTransactionRepository(api)
        val result = repo.getTransactions()
        assertTrue(result is CloudTransactionRepository.CloudResult.Error)
    }

    @Test
    fun `session expired returns SessionExpired`() = runTest {
        val api = object : CloudTransactionApi {
            override suspend fun getTransactions(page: Int, limit: Int, startDate: String?, endDate: String?): CloudTransactionsResponseDto {
                throw HttpException(Response.error<CloudTransactionsResponseDto>(401, "".toResponseBody(null)))
            }
            override suspend fun getSummary() = throw NotImplementedError()
        }
        val repo = CloudTransactionRepository(api)
        val result = repo.getTransactions()
        assertTrue(result is CloudTransactionRepository.CloudResult.SessionExpired)
    }

    @Test
    fun `internal fields never exposed via DTO`() = runTest {
        // DTO should only have allowed fields
        val fields = CloudTransactionDto::class.java.declaredFields.map { it.name }.toSet()
        assertTrue(fields.contains("transactionUuid"))
        assertTrue(fields.contains("amount"))
        assertTrue(!fields.contains("id"))
        assertTrue(!fields.contains("merchantId"))
        assertTrue(!fields.contains("firebaseUid"))
    }
}
