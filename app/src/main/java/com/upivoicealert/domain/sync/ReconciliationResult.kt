package com.upivoicealert.domain.sync

/**
 * Result of local integrity reconciliation between Transaction table and SyncQueue.
 * Merchant-safe, no Room entities exposed.
 */
data class ReconciliationResult(
    val scannedCount: Int,
    val alreadyQueuedCount: Int,
    val repairedCount: Int,
    val skippedCount: Int,
    val errorCount: Int
) {
    companion object {
        val EMPTY = ReconciliationResult(0, 0, 0, 0, 0)
    }
}
