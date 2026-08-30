package com.upivoicealert.domain.sync

/**
 * Domain model for sync queue. No business logic related to transactions.
 * Phase 7.2 adds failure diagnostics (nullable unless FAILED).
 */
data class SyncItem(
    val id: Long = 0,
    val entityType: String,
    val entityId: String,
    val status: SyncStatus,
    val retryCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val lastErrorCode: String? = null,
    val lastErrorMessage: String? = null,
    val failedAt: Long? = null
)

/**
 * Merchant-safe diagnostic for a failed sync item.
 * Never exposes raw backend internals; only sanitized merchant-friendly messages.
 */
data class SyncFailure(
    val queueId: Long,
    val entityType: String,
    val entityId: String,
    val retryCount: Int,
    val errorCode: String?,
    val errorMessage: String?,
    val failedAt: Long?
)

sealed interface RetrySyncItemResult {
    data object Success : RetrySyncItemResult
    data object NotFound : RetrySyncItemResult
    data object InvalidState : RetrySyncItemResult
}

data class StaleUploadRecoveryResult(
    val recoveredCount: Int
)

enum class SyncStatus {
    PENDING,
    UPLOADING,
    SYNCED,
    FAILED;

    companion object {
        fun fromString(value: String): SyncStatus = when (value.uppercase()) {
            "PENDING" -> PENDING
            "UPLOADING" -> UPLOADING
            "SYNCED" -> SYNCED
            "FAILED" -> FAILED
            else -> PENDING
        }
    }
}
