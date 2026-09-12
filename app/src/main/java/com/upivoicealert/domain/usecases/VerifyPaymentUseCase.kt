package com.upivoicealert.domain.usecases

import com.upivoicealert.domain.model.VerificationResult
import com.upivoicealert.domain.repository.PaymentVerificationRepository
import com.upivoicealert.logging.AppLogger
import com.upivoicealert.utils.Constants
import javax.inject.Inject

/**
 * Feature 1 — payment verification.
 *
 * The merchant enters the expected amount; this use case searches the REAL Room
 * transaction history for a RECEIVED + SUCCESS transaction whose amount matches
 * and whose post-time falls within the last [Constants.VERIFICATION_WINDOW_MS]
 * (10 minutes). Never fabricates a transaction.
 */
class VerifyPaymentUseCase @Inject constructor(
    private val repository: PaymentVerificationRepository
) {

    suspend operator fun invoke(amount: Double): VerificationResult {
        val since = System.currentTimeMillis() - Constants.VERIFICATION_WINDOW_MS
        AppLogger.d(TAG, "VERIFY_CHECK windowMs=${Constants.VERIFICATION_WINDOW_MS} since=$since")
        val transaction = repository.findRecentReceived(amount, since)
        return if (transaction != null) {
            AppLogger.d(
                TAG,
                "VERIFY_MATCH transactionId=${transaction.id} createdAt=${transaction.createdAt}"
            )
            VerificationResult.Verified(transaction)
        } else {
            AppLogger.d(TAG, "VERIFY_NO_MATCH since=$since — payment not found within window")
            VerificationResult.NotFound
        }
    }

    private companion object {
        const val TAG = "SHOUTPAY_VERIFICATION_DEBUG"
    }
}
