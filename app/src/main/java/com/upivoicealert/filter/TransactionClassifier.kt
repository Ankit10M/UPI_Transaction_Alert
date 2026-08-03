package com.upivoicealert.filter

import com.upivoicealert.domain.model.TransactionStatus
import com.upivoicealert.domain.model.TransactionType
import javax.inject.Inject

data class ClassificationResult(
    val type: TransactionType,
    val status: TransactionStatus
)

/**
 * Determines transaction type and status from notification text before any field
 * extraction. Keyword matching is intentionally conservative: notifications without
 * positive "received" evidence are labelled SENT so they are never announced.
 * (CLAUDE.md Module 2, Component 2.)
 */
class TransactionClassifier @Inject constructor() {

    fun classify(rawText: String): ClassificationResult {
        val text = rawText.lowercase()

        val status = when {
            text.contains("failed") || text.contains("declined") -> TransactionStatus.FAILED
            text.contains("pending") || text.contains("processing") -> TransactionStatus.PENDING
            else -> TransactionStatus.SUCCESS
        }

        val type = when {
            text.contains("received") || text.contains("credited") -> TransactionType.RECEIVED
            text.contains("refund") -> TransactionType.REFUND
            text.contains("sent") || text.contains("debited") -> TransactionType.SENT
            else -> TransactionType.SENT
        }

        return ClassificationResult(type, status)
    }
}