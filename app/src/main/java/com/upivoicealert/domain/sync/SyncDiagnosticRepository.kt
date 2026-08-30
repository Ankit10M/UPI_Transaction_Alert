package com.upivoicealert.domain.sync

import kotlinx.coroutines.flow.Flow

interface SyncDiagnosticRepository {
    suspend fun record(category: SyncDiagnosticCategory, eventType: SyncDiagnosticEventType, affectedCount: Int = 0)
    fun observeRecent(limit: Int = 100): Flow<List<SyncDiagnosticEvent>>
    suspend fun getRecent(limit: Int = 100): List<SyncDiagnosticEvent>
    suspend fun clearAll()
    suspend fun count(): Int
}
