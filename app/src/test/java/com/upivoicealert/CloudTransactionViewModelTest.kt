package com.upivoicealert

import com.upivoicealert.data.cloudtransaction.CloudTransactionApi
import com.upivoicealert.data.cloudtransaction.CloudTransactionDto
import com.upivoicealert.data.cloudtransaction.CloudTransactionRepository
import com.upivoicealert.data.cloudtransaction.CloudTransactionsResponseDto
import com.upivoicealert.data.cloudtransaction.PaginationDto
import com.upivoicealert.ui.cloud.CloudTransactionViewModel
import com.upivoicealert.ui.cloud.CloudUiState
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class CloudTransactionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `loading to success`() = runTest {
        val api = object : CloudTransactionApi {
            override suspend fun getTransactions(page: Int, limit: Int, startDate: String?, endDate: String?): CloudTransactionsResponseDto {
                return CloudTransactionsResponseDto(listOf(dto()), PaginationDto(1, 20, 1, 1))
            }
            override suspend fun getSummary() = throw NotImplementedError()
        }
        val repo = CloudTransactionRepository(api)
        val vm = CloudTransactionViewModel(repo)
        vm.loadTransactions()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is CloudUiState.Success)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `loading to empty`() = runTest {
        val api = object : CloudTransactionApi {
            override suspend fun getTransactions(page: Int, limit: Int, startDate: String?, endDate: String?): CloudTransactionsResponseDto {
                return CloudTransactionsResponseDto(emptyList(), PaginationDto(1, 20, 0, 0))
            }
            override suspend fun getSummary() = throw NotImplementedError()
        }
        val repo = CloudTransactionRepository(api)
        val vm = CloudTransactionViewModel(repo)
        vm.loadTransactions()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is CloudUiState.Empty)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `error state on network failure`() = runTest {
        val api = object : CloudTransactionApi {
            override suspend fun getTransactions(page: Int, limit: Int, startDate: String?, endDate: String?): CloudTransactionsResponseDto {
                throw IOException("offline")
            }
            override suspend fun getSummary() = throw NotImplementedError()
        }
        val repo = CloudTransactionRepository(api)
        val vm = CloudTransactionViewModel(repo)
        vm.loadTransactions()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is CloudUiState.Error)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `session expired state`() = runTest {
        val api = object : CloudTransactionApi {
            override suspend fun getTransactions(page: Int, limit: Int, startDate: String?, endDate: String?): CloudTransactionsResponseDto {
                throw HttpException(Response.error<CloudTransactionsResponseDto>(401, "".toResponseBody(null)))
            }
            override suspend fun getSummary() = throw NotImplementedError()
        }
        val repo = CloudTransactionRepository(api)
        val vm = CloudTransactionViewModel(repo)
        vm.loadTransactions()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is CloudUiState.SessionExpired)
    }
}
