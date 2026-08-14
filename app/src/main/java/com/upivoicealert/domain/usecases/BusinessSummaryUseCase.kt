package com.upivoicealert.domain.usecases

import android.util.Log
import com.upivoicealert.domain.model.BusinessSummary
import com.upivoicealert.domain.model.Transaction
import com.upivoicealert.domain.repository.TransactionRepository
import com.upivoicealert.utils.DateTimeUtils
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Feature 2 — business summary.
 *
 * Computes the merchant's daily performance from the REAL Room transaction
 * history (RECEIVED + SUCCESS only, from start of today):
 *   - today's total collection
 *   - number of transactions
 *   - average transaction value
 *   - largest payment
 *   - peak payment hour (hour with the most transactions today)
 */
class BusinessSummaryUseCase @Inject constructor(
    private val repository: TransactionRepository
) {

    fun observeTodaySummary(): Flow<BusinessSummary> =
        repository.observeReceivedSuccessSince(DateTimeUtils.startOfToday())
            .map { transactions ->
                computeSummary(transactions).also { summary ->
                    Log.i(
                        TAG,
                        "BUSINESS_SUMMARY total=${summary.totalCollection} count=${summary.transactionCount} " +
                            "average=${summary.averageTransactionValue} largest=${summary.largestPayment} " +
                            "peakHour=${summary.peakPaymentHour ?: "none"}"
                    )
                }
            }

    private fun computeSummary(transactions: List<Transaction>): BusinessSummary {
        if (transactions.isEmpty()) {
            return BusinessSummary()
        }
        val total = transactions.sumOf { it.amount }
        val largest = transactions.maxOf { it.amount }
        val average = total / transactions.size

        // Peak hour: hour of day (0..23) with the most transactions. Ties break
        // to the earliest hour so the result is deterministic (count * 100 - hour
        // ranks higher counts first, then earlier hours).
        val hourCounts = transactions
            .map { DateTimeUtils.hourOfDay(it.createdAt) }
            .groupingBy { it }
            .eachCount()
        val peakHour = hourCounts.maxByOrNull { (hour, count) -> count * 100 - hour }?.key

        return BusinessSummary(
            totalCollection = total,
            transactionCount = transactions.size,
            averageTransactionValue = average,
            largestPayment = largest,
            peakPaymentHour = peakHour
        )
    }

    private companion object {
        const val TAG = "SHOUTPAY_BUSINESS_DEBUG"
    }
}
