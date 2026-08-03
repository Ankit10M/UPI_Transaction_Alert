package com.upivoicealert.domain.usecases

import com.upivoicealert.domain.model.Transaction
import com.upivoicealert.domain.repository.TransactionRepository
import javax.inject.Inject

class CheckDuplicateUseCase @Inject constructor(
    private val repository: TransactionRepository
) {

    suspend operator fun invoke(transaction: Transaction): Boolean = repository.isDuplicate(transaction)
}