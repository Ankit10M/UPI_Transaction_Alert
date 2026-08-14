package com.upivoicealert.domain.model

/**
 * Daily business performance (Feature 2 — business summary), computed from the
 * real Room transaction history — never fabricated analytics.
 */
data class BusinessSummary(
    /** Sum of today's RECEIVED + SUCCESS transaction amounts. */
    val totalCollection: Double = 0.0,
    /** Number of today's RECEIVED + SUCCESS transactions. */
    val transactionCount: Int = 0,
    /** totalCollection / transactionCount (0 when no transactions). */
    val averageTransactionValue: Double = 0.0,
    /** Largest single payment today (0 when no transactions). */
    val largestPayment: Double = 0.0,
    /** Hour of day (0..23) with the most transactions; null when none today. */
    val peakPaymentHour: Int? = null
)
