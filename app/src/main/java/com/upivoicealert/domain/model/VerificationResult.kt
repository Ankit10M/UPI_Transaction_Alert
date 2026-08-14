package com.upivoicealert.domain.model

/**
 * Result of a payment-verification request (Feature 1). [Verified] carries the
 * actual Room transaction that matched — never a fabricated record.
 */
sealed class VerificationResult {
    data class Verified(val transaction: Transaction) : VerificationResult()
    data object NotFound : VerificationResult()
}
