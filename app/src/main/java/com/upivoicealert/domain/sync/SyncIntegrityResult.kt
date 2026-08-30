package com.upivoicealert.domain.sync

/**
 * Merchant-safe audit result for local-to-cloud sync integrity.
 * Category counts are counts of problematic records, not raw IDs or amounts.
 * No raw notification, sender, amount, JWT, or stack trace is exposed.
 */
data class SyncIntegrityResult(
    val scannedTransactions: Int,
    val scannedQueueItems: Int,
    val missingQueueCount: Int,
    val orphanedQueueCount: Int,
    val duplicateQueueCount: Int,
    val invalidQueueCount: Int,
    val staleUploadingCount: Int,
    val checkedAt: Long
) {
    val totalIssues: Int
        get() = missingQueueCount + orphanedQueueCount + duplicateQueueCount + invalidQueueCount + staleUploadingCount

    val isHealthy: Boolean
        get() = totalIssues == 0

    companion object {
        fun empty(checkedAt: Long = System.currentTimeMillis()) = SyncIntegrityResult(
            scannedTransactions = 0,
            scannedQueueItems = 0,
            missingQueueCount = 0,
            orphanedQueueCount = 0,
            duplicateQueueCount = 0,
            invalidQueueCount = 0,
            staleUploadingCount = 0,
            checkedAt = checkedAt
        )
    }
}
