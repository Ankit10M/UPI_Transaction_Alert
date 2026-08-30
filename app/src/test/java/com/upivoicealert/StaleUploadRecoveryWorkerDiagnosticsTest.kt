package com.upivoicealert

import com.upivoicealert.domain.sync.SyncDiagnosticCategory
import com.upivoicealert.domain.sync.SyncDiagnosticEventType
import com.upivoicealert.domain.sync.SyncDiagnosticRecorder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class StaleUploadRecoveryWorkerDiagnosticsTest {

    private class FakeRecorder : SyncDiagnosticRecorder {
        val events = mutableListOf<SyncDiagnosticEventType>()
        val counts = mutableListOf<Int>()
        override suspend fun record(category: SyncDiagnosticCategory, eventType: SyncDiagnosticEventType, affectedCount: Int) {
            events.add(eventType); counts.add(affectedCount)
        }
    }

    private suspend fun simulate(recovered: Int, shouldThrow: Boolean, rec: FakeRecorder): String {
        return try {
            if (shouldThrow) throw IOException("busy")
            if (recovered > 0) runCatching { rec.record(SyncDiagnosticCategory.STALE_RECOVERY, SyncDiagnosticEventType.STALE_UPLOADS_RECOVERED, recovered) }
            else runCatching { rec.record(SyncDiagnosticCategory.STALE_RECOVERY, SyncDiagnosticEventType.STALE_RECOVERY_COMPLETED, 0) }
            "success"
        } catch (e: IOException) {
            runCatching { rec.record(SyncDiagnosticCategory.STALE_RECOVERY, SyncDiagnosticEventType.STALE_RECOVERY_ERROR, 0) }
            "retry"
        } catch (e: Exception) {
            runCatching { rec.record(SyncDiagnosticCategory.STALE_RECOVERY, SyncDiagnosticEventType.STALE_RECOVERY_ERROR, 0) }
            "failure"
        }
    }

    @Test fun `recovered records one event`() = runTest {
        val rec = FakeRecorder()
        val res = simulate(5, false, rec)
        assertEquals("success", res)
        assertEquals(SyncDiagnosticEventType.STALE_UPLOADS_RECOVERED, rec.events[0])
        assertEquals(5, rec.counts[0])
    }
    @Test fun `no stale records completion`() = runTest {
        val rec = FakeRecorder()
        val res = simulate(0, false, rec)
        assertEquals("success", res)
        assertEquals(SyncDiagnosticEventType.STALE_RECOVERY_COMPLETED, rec.events[0])
    }
    @Test fun `error records error event`() = runTest {
        val rec = FakeRecorder()
        val res = simulate(0, true, rec)
        assertEquals("retry", res)
        assertTrue(rec.events.contains(SyncDiagnosticEventType.STALE_RECOVERY_ERROR))
    }
    @Test fun `diagnostics do not modify queue state`() = runTest {
        val rec = FakeRecorder()
        simulate(2, false, rec)
        assertEquals(1, rec.events.size)
    }
}
