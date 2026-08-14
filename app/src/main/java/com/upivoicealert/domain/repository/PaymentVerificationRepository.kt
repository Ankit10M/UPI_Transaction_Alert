package com.upivoicealert.domain.repository

import com.upivoicealert.domain.model.Transaction

/**
 * Reads Room transaction data for payment verification (Feature 1).
 *
 * Implementation is Room-based; the interface exists so a future remote/cloud
 * verification source could be swapped in without touching the UI layer.
 */
interface PaymentVerificationRepository {

    /**
     * Most recent RECEIVED + SUCCESS transaction whose amount equals [amount]
     * and whose post-time is at/after [since] (the verification window).
     * Returns null when no such transaction exists.
     */
    suspend fun findRecentReceived(amount: Double, since: Long): Transaction?
}
