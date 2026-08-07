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
 * extraction. Priority rules (confirmed during pipeline testing):
 *
 * RECEIVED keywords: received, credited, credit, paid you, got, money received, amount credited
 * SENT keywords:     sent, debited, paid to, transferred
 *
 * RECEIVED keywords are checked first (priority), so e.g. "PRIYA paid you ₹10"
 * classifies as RECEIVED. Keywords are matched on word boundaries so short words
 * like "got" do not false-positive inside "forgot". Notifications without
 * positive received evidence are labelled SENT so they are never announced.
 * (CLAUDE.md Module 2, Component 2.)
 */
class TransactionClassifier @Inject constructor() {

    private val receivedPatterns: List<Regex> = RECEIVED_KEYWORDS.map { wordBoundaryPattern(it) }
    private val sentPatterns: List<Regex> = SENT_KEYWORDS.map { wordBoundaryPattern(it) }

    fun classify(rawText: String): ClassificationResult {
        val text = rawText.lowercase()

        val status = when {
            text.contains("failed") || text.contains("declined") -> TransactionStatus.FAILED
            text.contains("pending") || text.contains("processing") -> TransactionStatus.PENDING
            else -> TransactionStatus.SUCCESS
        }

        val type = when {
            receivedPatterns.any { it.containsMatchIn(text) } -> TransactionType.RECEIVED
            text.contains("refund") -> TransactionType.REFUND
            sentPatterns.any { it.containsMatchIn(text) } -> TransactionType.SENT
            else -> TransactionType.SENT
        }

        return ClassificationResult(type, status)
    }

    private fun wordBoundaryPattern(phrase: String): Regex =
        Regex("\\b${Regex.escape(phrase)}\\b", RegexOption.IGNORE_CASE)

    private companion object {
        val RECEIVED_KEYWORDS = listOf(
            "received", "credited", "credit", "paid you", "got", "money received", "amount credited"
        )
        val SENT_KEYWORDS = listOf(
            "sent", "debited", "paid to", "transferred"
        )
    }
}
