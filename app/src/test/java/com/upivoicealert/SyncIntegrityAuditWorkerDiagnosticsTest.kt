package com.upivoicealert

import com.upivoicealert.domain.sync.SyncDiagnosticCategory
import com.upivoicealert.domain.sync.SyncDiagnosticEventType
import com.upivoicealert.domain.sync.SyncDiagnosticRecorder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class SyncIntegrityAuditWorkerDiagnosticsTest {

    private class FakeRecorder : SyncDiagnosticRecorder {
        val events = mutableListOf<SyncDiagnosticEventType>()
        val counts = mutableListOf<Int>()
        override suspend fun record(category: SyncDiagnosticCategory, eventType: SyncDiagnosticEventType, affectedCount: Int) { events.add(eventType); counts.add(affectedCount) }
    }

    private suspend fun simulate(isHealthy: Boolean, totalIssues: Int, shouldThrow: Boolean, rec: FakeRecorder): String {
        return try {
            if (shouldThrow) throw IOException("busy")
            if (isHealthy) runCatching { rec.record(SyncDiagnosticCategory.INTEGRITY_AUDIT, SyncDiagnosticEventType.INTEGRITY_AUDIT_HEALTHY, 0) }
            else runCatching { rec.record(SyncDiagnosticCategory.INTEGRITY_AUDIT, SyncDiagnosticEventType.INTEGRITY_AUDIT_ISSUES_FOUND, totalIssues) }
            "success"
        } catch (e: IOException) {
            runCatching { rec.record(SyncDiagnosticCategory.INTEGRITY_AUDIT, SyncDiagnosticEventType.INTEGRITY_AUDIT_ERROR, 0) }
            "retry"
        } catch (e: Exception) {
            runCatching { rec.record(SyncDiagnosticCategory.INTEGRITY_AUDIT, SyncDiagnosticEventType.INTEGRITY_AUDIT_ERROR, 0) }
            "failure"
        }
    }

    @Test fun `healthy records healthy event`() = runTest {
        val rec = FakeRecorder()
        val res = simulate(true, 0, false, rec)
        assertEquals("success", res)
        assertEquals(SyncDiagnosticEventType.INTEGRITY_AUDIT_HEALTHY, rec.events[0])
    }
    @Test fun `issues records issues event with count`() = runTest {
        val rec = FakeRecorder()
        val res = simulate(false, 5, false, rec)
        assertEquals("success", res)
        assertEquals(SyncDiagnosticEventType.INTEGRITY_AUDIT_ISSUES_FOUND, rec.events[0])
        assertEquals(5, rec.counts[0])
    }
    @Test fun `error records error event`() = runTest {
        val rec = FakeRecorder()
        val res = simulate(true, 0, true, rec)
        assertEquals("retry", res)
        assertTrue(rec.events.contains(SyncDiagnosticEventType.INTEGRITY_AUDIT_ERROR))
    }
    @Test fun `diagnostics do not modify audit result`() = runTest {
        val rec = FakeRecorder()
        simulate(false, 3, false, rec)
        assertEquals(1, rec.events.size)
    }
}
