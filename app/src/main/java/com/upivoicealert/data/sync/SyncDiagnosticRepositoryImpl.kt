package com.upivoicealert.data.sync

import com.upivoicealert.domain.sync.SyncDiagnosticCategory
import com.upivoicealert.domain.sync.SyncDiagnosticEvent
import com.upivoicealert.domain.sync.SyncDiagnosticEventType
import com.upivoicealert.domain.sync.SyncDiagnosticRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class SyncDiagnosticRepositoryImpl @Inject constructor(
    private val dao: SyncDiagnosticDao
) : SyncDiagnosticRepository, com.upivoicealert.domain.sync.SyncDiagnosticRecorder {

    companion object {
        const val MAX_DIAGNOSTIC_EVENTS = 200
    }

    override suspend fun record(category: SyncDiagnosticCategory, eventType: SyncDiagnosticEventType, affectedCount: Int) {
        val entity = SyncDiagnosticEventEntity(
            category = category.name,
            eventType = eventType.name,
            affectedCount = affectedCount,
            createdAt = System.currentTimeMillis(),
            message = SyncDiagnosticMessageMapper.messageFor(category, eventType)
        )
        dao.insert(entity)
        // Bounded retention: keep newest MAX, delete oldest beyond
        dao.trimToMaxCount(MAX_DIAGNOSTIC_EVENTS)
    }

    override fun observeRecent(limit: Int): Flow<List<SyncDiagnosticEvent>> {
        return dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getRecent(limit: Int): List<SyncDiagnosticEvent> {
        return dao.getRecent(limit).map { it.toDomain() }
    }

    override suspend fun clearAll() {
        dao.clearAll()
    }

    override suspend fun count(): Int = dao.count()

    private fun SyncDiagnosticEventEntity.toDomain(): SyncDiagnosticEvent {
        val cat = try { SyncDiagnosticCategory.valueOf(category) } catch (_: Exception) { SyncDiagnosticCategory.SYNC }
        val type = try { SyncDiagnosticEventType.valueOf(eventType) } catch (_: Exception) { SyncDiagnosticEventType.SYNC_COMPLETED }
        return SyncDiagnosticEvent(
            id = id,
            category = cat,
            eventType = type,
            affectedCount = affectedCount,
            createdAt = createdAt,
            message = message
        )
    }
}
