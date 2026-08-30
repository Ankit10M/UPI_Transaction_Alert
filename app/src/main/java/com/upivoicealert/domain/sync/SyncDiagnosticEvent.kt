package com.upivoicealert.domain.sync

data class SyncDiagnosticEvent(
    val id: Long,
    val category: SyncDiagnosticCategory,
    val eventType: SyncDiagnosticEventType,
    val affectedCount: Int,
    val createdAt: Long,
    val message: String?
)
