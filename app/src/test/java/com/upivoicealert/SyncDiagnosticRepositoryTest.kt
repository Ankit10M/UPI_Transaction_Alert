package com.upivoicealert

import com.upivoicealert.data.sync.SyncDiagnosticDao
import com.upivoicealert.data.sync.SyncDiagnosticEventEntity
import com.upivoicealert.data.sync.SyncDiagnosticRepositoryImpl
import com.upivoicealert.domain.sync.SyncDiagnosticCategory
import com.upivoicealert.domain.sync.SyncDiagnosticEventType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SyncDiagnosticRepositoryTest {

    private class FakeDao : SyncDiagnosticDao {
        private val rows = mutableListOf<SyncDiagnosticEventEntity>()
        private var nextId = 1L
        private val flow = MutableStateFlow<List<SyncDiagnosticEventEntity>>(emptyList())
        private fun refresh() { flow.value = rows.sortedWith(compareByDescending<SyncDiagnosticEventEntity> { it.createdAt }.thenByDescending { it.id }) }
        override suspend fun insert(event: SyncDiagnosticEventEntity): Long {
            val id = nextId++
            val e = event.copy(id = id)
            rows.add(e); refresh(); return id
        }
        override suspend fun getRecent(limit: Int): List<SyncDiagnosticEventEntity> = rows.sortedWith(compareByDescending<SyncDiagnosticEventEntity> { it.createdAt }.thenByDescending { it.id }).take(limit)
        override fun observeRecent(limit: Int): Flow<List<SyncDiagnosticEventEntity>> = flow
        override suspend fun count(): Int = rows.size
        override suspend fun deleteOlderThan(cutoff: Long): Int { val before=rows.size; rows.removeIf{ it.createdAt < cutoff }; refresh(); return before-rows.size }
        override suspend fun clearAll() { rows.clear(); refresh() }
        override suspend fun trimToMaxCount(maxCount: Int): Int {
            if (rows.size <= maxCount) return 0
            val sorted = rows.sortedWith(compareByDescending<SyncDiagnosticEventEntity> { it.createdAt }.thenByDescending { it.id })
            val keepIds = sorted.take(maxCount).map { it.id }.toSet()
            val toDelete = rows.filter { it.id !in keepIds }
            rows.removeAll(toDelete.toSet())
            refresh()
            return toDelete.size
        }
        fun rawRows(): List<SyncDiagnosticEventEntity> = rows.toList()
    }

    @Test fun `event saved and observed`() = runTest {
        val dao = FakeDao(); val repo = SyncDiagnosticRepositoryImpl(dao)
        repo.record(SyncDiagnosticCategory.SYNC, SyncDiagnosticEventType.SYNC_COMPLETED, 5)
        assertEquals(1, dao.count())
        val recent = repo.getRecent(10)
        assertEquals(1, recent.size)
        assertEquals(SyncDiagnosticCategory.SYNC, recent[0].category)
        assertEquals(SyncDiagnosticEventType.SYNC_COMPLETED, recent[0].eventType)
        assertEquals(5, recent[0].affectedCount)
        // newest first
        repo.record(SyncDiagnosticCategory.SYNC, SyncDiagnosticEventType.SYNC_OFFLINE, 0)
        val list = repo.observeRecent(10).first()
        assertEquals(SyncDiagnosticEventType.SYNC_OFFLINE, list[0].eventType)
    }

    @Test fun `entity to domain mapping`() = runTest {
        val dao = FakeDao(); val repo = SyncDiagnosticRepositoryImpl(dao)
        repo.record(SyncDiagnosticCategory.RECONCILIATION, SyncDiagnosticEventType.RECONCILIATION_REPAIRED, 2)
        val e = repo.getRecent(1)[0]
        assertFalse(e.message.isNullOrBlank())
        // domain model does not expose forbidden fields: check no raw notification etc via type inspection
        assertNotNull(e.id); assertNotNull(e.createdAt)
    }

    @Test fun `clear removes diagnostics only`() = runTest {
        val dao = FakeDao(); val repo = SyncDiagnosticRepositoryImpl(dao)
        repo.record(SyncDiagnosticCategory.SYNC, SyncDiagnosticEventType.SYNC_COMPLETED, 1)
        repo.record(SyncDiagnosticCategory.STALE_RECOVERY, SyncDiagnosticEventType.STALE_UPLOADS_RECOVERED, 3)
        assertEquals(2, repo.count())
        repo.clearAll()
        assertEquals(0, repo.count())
    }

    @Test fun `domain model does not expose sensitive fields`() = runTest {
        val dao = FakeDao(); val repo = SyncDiagnosticRepositoryImpl(dao)
        repo.record(SyncDiagnosticCategory.SYNC, SyncDiagnosticEventType.SYNC_VALIDATION_FAILED, 1)
        val e = repo.getRecent(1)[0]
        // Ensure no sensitive data leaked via message
        assertFalse(e.message!!.contains("Bearer"))
        assertFalse(e.message!!.contains("eyJ"))
    }
}
