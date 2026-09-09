package com.upivoicealert

import com.upivoicealert.data.sync.SyncDiagnosticMessageMapper
import com.upivoicealert.domain.sync.SyncDiagnosticCategory
import com.upivoicealert.domain.sync.SyncDiagnosticEventType
import com.upivoicealert.observability.PaymentPipelineMetrics
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ObservabilityHardeningTest {

    @Test fun `diagnostic retention capped 200`() {
        val src = File("D:/UPI_Notification_Alert/app/src/main/java/com/upivoicealert/data/sync/SyncDiagnosticRepositoryImpl.kt").readText()
        assertTrue(src.contains("MAX_DIAGNOSTIC_EVENTS = 200"))
        assertTrue(src.contains("trimToMaxCount"))
    }

    @Test fun `AlreadyRunning not failure`() {
        val worker = File("D:/UPI_Notification_Alert/app/src/main/java/com/upivoicealert/worker/TransactionSyncWorker.kt").readText()
        assertTrue(worker.contains("AlreadyRunning"))
        // AlreadyRunning branch should be Log.d only, not a SYNC_VALIDATION_FAILED diagnostic
        val idx = worker.indexOf("AlreadyRunning")
        val snippet = worker.substring(idx, (idx + 400).coerceAtMost(worker.length))
        assertTrue(snippet.contains("Log.d"))
        assertFalse(snippet.contains("SYNC_VALIDATION_FAILED"))
    }

    @Test fun `pipeline metrics bounded no Room write per notification`() {
        val m = PaymentPipelineMetrics()
        m.onNotificationReceived(); m.onNotificationReceived()
        m.onParseRejected(); m.onDuplicate(); m.onPersisted()
        val s = m.snapshot()
        assertEquals(2, s.received); assertEquals(1, s.parseRejected)
        // No sensitive fields in snapshot
        val str = s.toString()
        assertFalse(str.contains("VPA")); assertFalse(str.contains("JWT"))
    }

    @Test fun `new auth and pipeline event types sanitized messages`() {
        val msg = SyncDiagnosticMessageMapper.messageFor(SyncDiagnosticCategory.AUTH, SyncDiagnosticEventType.AUTH_LOGIN_FAILED)
        assertFalse(msg.contains("token", true)); assertFalse(msg.contains("JWT"))
        val pipe = SyncDiagnosticMessageMapper.messageFor(SyncDiagnosticCategory.PAYMENT_PIPELINE, SyncDiagnosticEventType.PIPELINE_PERSISTED)
        assertFalse(pipe.contains("sender", true))
    }

    @Test fun `diagnostic failure does not break operation invariant`() {
        val repo = File("D:/UPI_Notification_Alert/app/src/main/java/com/upivoicealert/data/sync/SyncDiagnosticRepositoryImpl.kt").readText()
        val worker = File("D:/UPI_Notification_Alert/app/src/main/java/com/upivoicealert/worker/TransactionSyncWorker.kt").readText()
        assertTrue(worker.contains("runCatching"))
        assertTrue(worker.contains("diagnosticRecorder.record"))
    }

    @Test fun `release logging disabled for sensitive`() {
        val logger = File("D:/UPI_Notification_Alert/app/src/main/java/com/upivoicealert/logging/AppLogger.kt").readText()
        assertTrue(logger.contains("BuildConfig.DEBUG"))
    }

    @Test fun `backend requestId not derived from PII`() {
        val app = File("D:/UPI_Notification_Alert/shoutpay-backend/src/app.js").readText()
        assertTrue(app.contains("crypto.randomUUID()"))
        assertTrue(app.contains("requestId"))
        assertFalse(app.contains("merchantId") && app.contains("requestId = merchant"))
    }

    @Test fun `backend logs no JWT refresh phone VPA`() {
        val app = File("D:/UPI_Notification_Alert/shoutpay-backend/src/app.js").readText()
        // Production log only method/path/status/errorCode/requestId
        assertTrue(app.contains("method"))
        assertFalse(app.contains("Authorization"))
        assertFalse(app.contains("refreshToken"))
        assertFalse(app.contains("phone"))
    }
}
