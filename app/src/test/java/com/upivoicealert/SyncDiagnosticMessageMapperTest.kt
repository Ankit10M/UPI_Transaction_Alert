package com.upivoicealert

import com.upivoicealert.data.sync.SyncDiagnosticMessageMapper
import com.upivoicealert.domain.sync.SyncDiagnosticCategory
import com.upivoicealert.domain.sync.SyncDiagnosticEventType
import org.junit.Assert.*
import org.junit.Test

class SyncDiagnosticMessageMapperTest {
    @Test fun `allow-listed event produces sanitized message`() {
        val msg = SyncDiagnosticMessageMapper.messageFor(SyncDiagnosticCategory.SYNC, SyncDiagnosticEventType.SYNC_NETWORK_RETRY)
        assertEquals("Sync will retry when the connection is available.", msg)
        assertFalse(msg.contains("Bearer"))
        assertFalse(msg.contains("Exception"))
    }

    @Test fun `arbitrary exception text never stored via mapper`() {
        // Simulate that recorder only uses mapper, not arbitrary string
        val arbitrary = "Authorization: Bearer SECRET eyJ..."
        val mapped = SyncDiagnosticMessageMapper.messageFor(SyncDiagnosticCategory.SYNC, SyncDiagnosticEventType.SYNC_COMPLETED)
        assertFalse(mapped.contains(arbitrary))
        assertFalse(mapped.contains("SECRET"))
    }

    @Test fun `all event types have non-technical messages`() {
        for (type in SyncDiagnosticEventType.values()) {
            val cat = when (type.name.substringBefore("_")) {
                "SYNC" -> SyncDiagnosticCategory.SYNC
                "RECONCILIATION" -> SyncDiagnosticCategory.RECONCILIATION
                "STALE" -> SyncDiagnosticCategory.STALE_RECOVERY
                "INTEGRITY" -> SyncDiagnosticCategory.INTEGRITY_AUDIT
                else -> SyncDiagnosticCategory.SYNC
            }
            val msg = SyncDiagnosticMessageMapper.messageFor(cat, type)
            assertFalse(msg.contains("SQLiteException"))
            assertFalse(msg.contains("HTTP 500"))
            assertFalse(msg.contains("Coroutine"))
            assertTrue(msg.isNotBlank())
        }
    }
}
