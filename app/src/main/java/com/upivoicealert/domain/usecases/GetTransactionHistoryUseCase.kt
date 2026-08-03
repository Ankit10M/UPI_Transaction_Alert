package com.upivoicealert.domain.usecases

import com.upivoicealert.domain.model.Transaction
import com.upivoicealert.domain.repository.TransactionRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class GetTransactionHistoryUseCase @Inject constructor(
    private val repository: TransactionRepository
) {

    fun receivedSuccess(): Flow<List<Transaction>> = repository.observeReceivedSuccess()

    fun all(): Flow<List<Transaction>> = repository.observeTransactions()
}