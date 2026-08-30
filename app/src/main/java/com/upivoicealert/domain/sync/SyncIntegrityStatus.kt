package com.upivoicealert.domain.sync

/**
 * Persistent merchant-safe audit metadata, survives restart/process death.
 * Does not contain transaction contents or raw IDs.
 */
data class SyncIntegrityStatus(
    val lastAuditAt: Long?,
    val lastIssueCount: Int,
    val isHealthy: Boolean?,
    val missingQueueCount: Int = 0,
    val orphanedQueueCount: Int = 0,
    val duplicateQueueCount: Int = 0,
    val invalidQueueCount: Int = 0,
    val staleUploadingCount: Int = 0
) {
    companion object {
        val NEVER_CHECKED = SyncIntegrityStatus(
            lastAuditAt = null,
            lastIssueCount = 0,
            isHealthy = null
        )
    }
}
