package com.upivoicealert.domain.usecases

import android.util.Log
import com.upivoicealert.domain.model.VerificationResult
import com.upivoicealert.domain.repository.PaymentVerificationRepository
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
        Log.i(TAG, "VERIFY_CHECK amount=$amount windowMs=${Constants.VERIFICATION_WINDOW_MS} since=$since")
        val transaction = repository.findRecentReceived(amount, since)
        return if (transaction != null) {
            Log.i(
                TAG,
                "VERIFY_MATCH amount=$amount transactionId=${transaction.id} sender=${transaction.sender} app=${transaction.upiApp} createdAt=${transaction.createdAt}"
            )
            VerificationResult.Verified(transaction)
        } else {
            Log.i(TAG, "VERIFY_NO_MATCH amount=$amount since=$since — payment not found within window")
            VerificationResult.NotFound
        }
    }

    private companion object {
        const val TAG = "SHOUTPAY_VERIFICATION_DEBUG"
    }
}
