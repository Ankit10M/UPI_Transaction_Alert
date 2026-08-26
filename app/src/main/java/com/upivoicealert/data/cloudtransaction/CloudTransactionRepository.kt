package com.upivoicealert.data.cloudtransaction

import com.upivoicealert.domain.cloudtransaction.CloudPagination
import com.upivoicealert.domain.cloudtransaction.CloudSummary
import com.upivoicealert.domain.cloudtransaction.CloudTransaction
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.HttpException

/**
 * Repository for cloud transactions. Handles mapping and error translation.
 * Reuses AuthInterceptor/AuthAuthenticator; no manual token refresh.
 * 401 after refresh → SessionExpired.
 */
@Singleton
class CloudTransactionRepository @Inject constructor(
    private val api: CloudTransactionApi
) {

    sealed interface CloudResult<out T> {
        data class Success<T>(val data: T) : CloudResult<T>
        data class Error(val message: String) : CloudResult<Nothing>
        data object SessionExpired : CloudResult<Nothing>
    }

    suspend fun getTransactions(
        page: Int = 1,
        limit: Int = 20,
        startDate: String? = null,
        endDate: String? = null
    ): CloudResult<Pair<List<CloudTransaction>, CloudPagination>> {
        return try {
            val response = api.getTransactions(page, limit, startDate, endDate)
            val domainTransactions = response.transactions.map { it.toDomain() }
            val pagination = CloudPagination(
                page = response.pagination.page,
                limit = response.pagination.limit,
                total = response.pagination.total,
                totalPages = response.pagination.totalPages
            )
            CloudResult.Success(domainTransactions to pagination)
        } catch (e: IOException) {
            CloudResult.Error(e.message ?: "Network error")
        } catch (e: HttpException) {
            if (e.code() == 401) CloudResult.SessionExpired
            else CloudResult.Error(e.message() ?: "HTTP ${e.code()}")
        } catch (e: Exception) {
            CloudResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getSummary(): CloudResult<CloudSummary> {
        return try {
            val response = api.getSummary()
            val summary = CloudSummary(
                todayTotal = response.summary.todayTotal,
                todayCount = response.summary.todayCount,
                averageTransaction = response.summary.averageTransaction,
                largestTransaction = response.summary.largestTransaction
            )
            CloudResult.Success(summary)
        } catch (e: IOException) {
            CloudResult.Error(e.message ?: "Network error")
        } catch (e: HttpException) {
            if (e.code() == 401) CloudResult.SessionExpired
            else CloudResult.Error(e.message() ?: "HTTP ${e.code()}")
        } catch (e: Exception) {
            CloudResult.Error(e.message ?: "Unknown error")
        }
    }

    private fun CloudTransactionDto.toDomain(): CloudTransaction = CloudTransaction(
        transactionUuid = transactionUuid,
        amount = amount,
        currency = currency,
        senderName = senderName,
        senderVpa = senderVpa,
        upiReference = upiReference,
        upiApp = upiApp,
        transactionTime = transactionTime,
        status = status
    )
}
