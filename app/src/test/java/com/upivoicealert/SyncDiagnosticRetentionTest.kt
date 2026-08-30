package com.upivoicealert

import com.upivoicealert.data.sync.SyncDiagnosticDao
import com.upivoicealert.data.sync.SyncDiagnosticEventEntity
import com.upivoicealert.data.sync.SyncDiagnosticRepositoryImpl
import com.upivoicealert.domain.sync.SyncDiagnosticCategory
import com.upivoicealert.domain.sync.SyncDiagnosticEventType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncDiagnosticRetentionTest {

    private class FakeDao : SyncDiagnosticDao {
        val rows = mutableListOf<SyncDiagnosticEventEntity>()
        var nextId = 1L
        override suspend fun insert(event: SyncDiagnosticEventEntity): Long { val id=nextId++; rows.add(event.copy(id=id)); return id }
        override suspend fun getRecent(limit: Int): List<SyncDiagnosticEventEntity> = rows.sortedWith(compareByDescending<SyncDiagnosticEventEntity> { it.createdAt }.thenByDescending { it.id }).take(limit)
        override fun observeRecent(limit: Int) = MutableStateFlow(emptyList<SyncDiagnosticEventEntity>())
        override suspend fun count(): Int = rows.size
        override suspend fun deleteOlderThan(cutoff: Long): Int = 0
        override suspend fun clearAll() { rows.clear() }
        override suspend fun trimToMaxCount(maxCount: Int): Int {
            if (rows.size <= maxCount) return 0
            val sorted = rows.sortedWith(compareByDescending<SyncDiagnosticEventEntity> { it.createdAt }.thenByDescending { it.id })
            val keepIds = sorted.take(maxCount).map { it.id }.toSet()
            val removed = rows.filter { it.id !in keepIds }
            rows.removeAll(removed.toSet())
            return removed.size
        }
    }

    @Test fun `insert 201 events retains 200 newest`() = runTest {
        val dao = FakeDao(); val repo = SyncDiagnosticRepositoryImpl(dao)
        repeat(201) { i ->
            // Use distinct createdAt to guarantee ordering
            dao.insert(SyncDiagnosticEventEntity(category="SYNC", eventType="SYNC_COMPLETED", affectedCount=i, createdAt=1000L + i, message="msg"))
            dao.trimToMaxCount(SyncDiagnosticRepositoryImpl.MAX_DIAGNOSTIC_EVENTS)
        }
        // Simulate repo's trimming after each insert
        assertEquals(200, dao.count())
        val recent = dao.getRecent(5)
        // newest should be i=200
        assertEquals(200, recent[0].affectedCount)
        // oldest retained should be i=1 (0 removed)
        val all = dao.getRecent(300)
        assertEquals(1, all.last().affectedCount)
        assertTrue(all.none { it.affectedCount == 0 })
    }

    @Test fun `100 events all remain`() = runTest {
        val dao = FakeDao(); val repo = SyncDiagnosticRepositoryImpl(dao)
        repeat(100) { repo.record(SyncDiagnosticCategory.SYNC, SyncDiagnosticEventType.SYNC_COMPLETED, 1) }
        assertEquals(100, dao.count())
    }

    @Test fun `ordering with equal timestamps keeps newest id`() = runTest {
        val dao = FakeDao(); val repo = SyncDiagnosticRepositoryImpl(dao)
        // Insert 3 with same timestamp; ids increment, newest id should be first
        val ts = 5000L
        dao.insert(SyncDiagnosticEventEntity(category="SYNC", eventType="A", affectedCount=1, createdAt=ts, message="a"))
        dao.insert(SyncDiagnosticEventEntity(category="SYNC", eventType="B", affectedCount=2, createdAt=ts, message="b"))
        dao.insert(SyncDiagnosticEventEntity(category="SYNC", eventType="C", affectedCount=3, createdAt=ts, message="c"))
        val recent = dao.getRecent(3)
        assertEquals(3, recent[0].affectedCount)
        assertEquals(2, recent[1].affectedCount)
        assertEquals(1, recent[2].affectedCount)
    }
}
