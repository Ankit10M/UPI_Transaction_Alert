package com.upivoicealert.domain.sync

interface SyncDiagnosticRecorder {
    suspend fun record(category: SyncDiagnosticCategory, eventType: SyncDiagnosticEventType, affectedCount: Int)
}
