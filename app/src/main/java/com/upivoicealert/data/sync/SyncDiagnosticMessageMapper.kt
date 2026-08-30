package com.upivoicealert.data.sync

import com.upivoicealert.domain.sync.SyncDiagnosticCategory
import com.upivoicealert.domain.sync.SyncDiagnosticEventType

object SyncDiagnosticMessageMapper {
    fun messageFor(category: SyncDiagnosticCategory, eventType: SyncDiagnosticEventType): String = when (eventType) {
        SyncDiagnosticEventType.SYNC_COMPLETED -> "Transactions were successfully synced."
        SyncDiagnosticEventType.SYNC_PARTIAL -> "Some transactions were synced. Others will be retried."
        SyncDiagnosticEventType.SYNC_NO_PENDING -> "No transactions were waiting to sync."
        SyncDiagnosticEventType.SYNC_NETWORK_RETRY -> "Sync will retry when the connection is available."
        SyncDiagnosticEventType.SYNC_SERVER_RETRY -> "The sync service is temporarily unavailable."
        SyncDiagnosticEventType.SYNC_VALIDATION_FAILED -> "Some transaction data could not be synced."
        SyncDiagnosticEventType.SYNC_SESSION_EXPIRED -> "Sign-in is required before sync can continue."
        SyncDiagnosticEventType.SYNC_OFFLINE -> "Device is offline. Sync will retry when online."
        SyncDiagnosticEventType.SYNC_STARTED -> "Sync started."
        SyncDiagnosticEventType.RECONCILIATION_COMPLETED -> "Sync check completed. No missing items found."
        SyncDiagnosticEventType.RECONCILIATION_REPAIRED -> "Missing transactions were prepared for cloud sync."
        SyncDiagnosticEventType.RECONCILIATION_ERROR -> "Sync check encountered an issue."
        SyncDiagnosticEventType.STALE_RECOVERY_COMPLETED -> "No interrupted uploads needed recovery."
        SyncDiagnosticEventType.STALE_UPLOADS_RECOVERED -> "Interrupted uploads were prepared to sync again."
        SyncDiagnosticEventType.STALE_RECOVERY_ERROR -> "Stale upload recovery encountered an issue."
        SyncDiagnosticEventType.INTEGRITY_AUDIT_HEALTHY -> "Local sync records look healthy."
        SyncDiagnosticEventType.INTEGRITY_AUDIT_ISSUES_FOUND -> "Some local sync records need attention."
        SyncDiagnosticEventType.INTEGRITY_AUDIT_ERROR -> "Sync integrity check encountered an issue."
    }
}
