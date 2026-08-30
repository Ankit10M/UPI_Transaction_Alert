package com.upivoicealert.domain.sync

/**
 * Merchant-facing sync health summary.
 * Produced by [SyncStatusRepository] from the queue, network, and DataStore.
 * Never expose Room entities to the UI — this is the single model the UI consumes.
 *
 * Named [CloudSyncStatus] to avoid conflict with the queue-level [SyncStatus] enum
 * (which represents individual queue item states: PENDING, UPLOADING, SYNCED, FAILED).
 */
data class CloudSyncStatus(
    val state: SyncState,
    val pendingCount: Int,
    val failedCount: Int,
    val uploadingCount: Int,
    val lastSuccessfulSyncAt: Long?
) {
    companion object {
        /** Initial state before any real data loads. */
        val INITIAL = CloudSyncStatus(
            state = SyncState.NEVER_SYNCED,
            pendingCount = 0,
            failedCount = 0,
            uploadingCount = 0,
            lastSuccessfulSyncAt = null
        )
    }
}
