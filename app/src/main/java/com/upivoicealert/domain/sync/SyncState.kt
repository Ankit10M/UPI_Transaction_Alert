package com.upivoicealert.domain.sync

/**
 * Merchant-facing sync state — shown in the CloudSyncStatusCard.
 * Priority (highest to lowest): OFFLINE > SYNCING > FAILED > PENDING > SYNCED.
 * NEVER_SYNCED is shown when no transaction has ever been successfully synced.
 */
enum class SyncState {
    OFFLINE,
    SYNCING,
    FAILED,
    PENDING,
    SYNCED,
    NEVER_SYNCED
}
