package com.upivoicealert.domain.sync

/**
 * Domain model for sync queue. No business logic related to transactions.
 */
data class SyncItem(
    val id: Long = 0,
    val entityType: String,
    val entityId: String,
    val status: SyncStatus,
    val retryCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long
)

enum class SyncStatus {
    PENDING,
    SYNCED,
    FAILED;

    companion object {
        fun fromString(value: String): SyncStatus = when (value.uppercase()) {
            "PENDING" -> PENDING
            "SYNCED" -> SYNCED
            "FAILED" -> FAILED
            else -> PENDING
        }
    }
}
