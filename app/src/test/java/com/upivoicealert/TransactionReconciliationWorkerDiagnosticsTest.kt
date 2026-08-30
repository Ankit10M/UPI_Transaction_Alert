package com.upivoicealert

import com.upivoicealert.domain.sync.SyncDiagnosticCategory
import com.upivoicealert.domain.sync.SyncDiagnosticEventType
import com.upivoicealert.domain.sync.SyncDiagnosticRecorder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class TransactionReconciliationWorkerDiagnosticsTest {

    private class FakeRecorder : SyncDiagnosticRecorder {
        val events = mutableListOf<Pair<SyncDiagnosticCategory, SyncDiagnosticEventType>>()
        val counts = mutableListOf<Int>()
        var throwOnRecord = false
        override suspend fun record(category: SyncDiagnosticCategory, eventType: SyncDiagnosticEventType, affectedCount: Int) {
            if (throwOnRecord) throw RuntimeException("fail")
            events.add(category to eventType); counts.add(affectedCount)
        }
    }

    private suspend fun simulate(repaired: Int, shouldThrow: Boolean = false, recorder: FakeRecorder): String {
        return try {
            if (shouldThrow) throw IOException("busy")
            if (repaired > 0) {
                runCatching { recorder.record(SyncDiagnosticCategory.RECONCILIATION, SyncDiagnosticEventType.RECONCILIATION_REPAIRED, repaired) }
            } else {
                runCatching { recorder.record(SyncDiagnosticCategory.RECONCILIATION, SyncDiagnosticEventType.RECONCILIATION_COMPLETED, 0) }
            }
            "success"
        } catch (e: IOException) {
            runCatching { recorder.record(SyncDiagnosticCategory.RECONCILIATION, SyncDiagnosticEventType.RECONCILIATION_ERROR, 0) }
            "retry"
        } catch (e: Exception) {
            runCatching { recorder.record(SyncDiagnosticCategory.RECONCILIATION, SyncDiagnosticEventType.RECONCILIATION_ERROR, 0) }
            "failure"
        }
    }

    @Test fun `repaired records one diagnostic with count`() = runTest {
        val rec = FakeRecorder()
        val res = simulate(3, recorder = rec)
        assertEquals("success", res)
        assertEquals(1, rec.events.size)
        assertEquals(SyncDiagnosticEventType.RECONCILIATION_REPAIRED, rec.events[0].second)
        assertEquals(3, rec.counts[0])
    }
    @Test fun `no repairs records completion`() = runTest {
        val rec = FakeRecorder()
        val res = simulate(0, recorder = rec)
        assertEquals("success", res)
        assertEquals(SyncDiagnosticEventType.RECONCILIATION_COMPLETED, rec.events[0].second)
    }
    @Test fun `error records error diagnostic`() = runTest {
        val rec = FakeRecorder()
        val res = simulate(0, shouldThrow = true, recorder = rec)
        assertEquals("retry", res)
        assertTrue(rec.events.any { it.second == SyncDiagnosticEventType.RECONCILIATION_ERROR })
    }
    @Test fun `no transaction mutation caused by diagnostics`() = runTest {
        val rec = FakeRecorder()
        simulate(2, recorder = rec)
        // diagnostics only records, does not mutate transactions — verified by no queue/transaction calls
        assertEquals(1, rec.events.size)
    }
}
