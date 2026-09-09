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
        SyncDiagnosticEventType.AUTH_LOGIN_SUCCESS -> "Sign-in completed."
        SyncDiagnosticEventType.AUTH_LOGIN_FAILED -> "Sign-in failed."
        SyncDiagnosticEventType.AUTH_REFRESH_SUCCESS -> "Session refreshed."
        SyncDiagnosticEventType.AUTH_REFRESH_FAILED -> "Session refresh failed."
        SyncDiagnosticEventType.AUTH_REFRESH_REPLAY -> "Session refresh replay detected."
        SyncDiagnosticEventType.AUTH_SESSION_EXPIRED -> "Session expired."
        SyncDiagnosticEventType.AUTH_LOGOUT -> "Signed out."
        SyncDiagnosticEventType.AUTH_LOGOUT_ALL -> "All sessions signed out."
        SyncDiagnosticEventType.PIPELINE_NOTIFICATION_RECEIVED -> "Payment notification received."
        SyncDiagnosticEventType.PIPELINE_PARSE_REJECTED -> "Notification not recognized as payment."
        SyncDiagnosticEventType.PIPELINE_VALIDATION_REJECTED -> "Payment data rejected by validation."
        SyncDiagnosticEventType.PIPELINE_DUPLICATE_DETECTED -> "Duplicate payment detected."
        SyncDiagnosticEventType.PIPELINE_PERSISTED -> "Payment recorded locally."
        SyncDiagnosticEventType.PIPELINE_TTS_FAILED -> "Voice announcement failed."
        SyncDiagnosticEventType.API_SUCCESS -> "API request succeeded."
        SyncDiagnosticEventType.API_VALIDATION_ERROR -> "Request validation failed."
        SyncDiagnosticEventType.API_AUTH_ERROR -> "Authentication required."
        SyncDiagnosticEventType.API_RATE_LIMITED -> "Too many requests."
        SyncDiagnosticEventType.API_SERVER_ERROR -> "Server temporarily unavailable."
        SyncDiagnosticEventType.API_NETWORK_ERROR -> "Network unavailable."
        SyncDiagnosticEventType.APP_HEALTH_OK -> "App health OK."
        SyncDiagnosticEventType.APP_HEALTH_DEGRADED -> "App health degraded."
    }
}
