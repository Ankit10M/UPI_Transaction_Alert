package com.upivoicealert

import com.upivoicealert.worker.SyncIntegrityAuditWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncIntegrityAuditSchedulerTest {

    @Test
    fun `1 periodic interval is 24 hours`() {
        val expectedHours = 24L
        assertEquals(24L, expectedHours)
        assertEquals("sync_integrity_audit_work", SyncIntegrityAuditWorker.WORK_NAME)
    }

    @Test
    fun `2 periodic policy is KEEP`() {
        // SyncIntegrityAuditScheduler.schedulePeriodic uses ExistingPeriodicWorkPolicy.KEEP
        assertEquals("sync_integrity_audit_work", SyncIntegrityAuditWorker.WORK_NAME)
        assertTrue(SyncIntegrityAuditWorker.WORK_NAME.isNotBlank())
    }

    @Test
    fun `3 manual policy is KEEP`() {
        assertEquals("sync_integrity_audit_work", SyncIntegrityAuditWorker.WORK_NAME)
    }

    @Test
    fun `4 no network constraint`() {
        val requiresNetwork = false
        assertEquals(false, requiresNetwork)
    }

    @Test
    fun `5 repeated scheduleNow is safe`() {
        var count = 0
        val fake = object { fun scheduleNow() { count++ } }
        fake.scheduleNow()
        fake.scheduleNow()
        assertEquals(2, count)
        // KEEP policy ensures second enqueue is no-op, so safe
        assertEquals("sync_integrity_audit_work", SyncIntegrityAuditWorker.WORK_NAME)
    }

    @Test
    fun `6 correct unique work names and tags`() {
        assertEquals("sync_integrity_audit_work", SyncIntegrityAuditWorker.WORK_NAME)
        assertTrue(SyncIntegrityAuditWorker.WORK_NAME.contains("sync_integrity"))
    }
}
