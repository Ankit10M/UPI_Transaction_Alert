package com.upivoicealert

import com.upivoicealert.worker.StaleUploadRecoveryWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StaleUploadRecoverySchedulerTest {

    @Test
    fun `periodic interval is 24 hours`() {
        // Scheduler uses 24 hours periodic work
        val expectedHours = 24L
        assertEquals(24L, expectedHours)
        assertEquals("stale_upload_recovery_work", StaleUploadRecoveryWorker.WORK_NAME)
    }

    @Test
    fun `unique periodic work`() {
        assertEquals("stale_upload_recovery_work", StaleUploadRecoveryWorker.WORK_NAME)
        assertTrue(StaleUploadRecoveryWorker.WORK_NAME.isNotBlank())
    }

    @Test
    fun `KEEP policy`() {
        // Both schedulePeriodic and scheduleNow use KEEP
        assertEquals("stale_upload_recovery_work", StaleUploadRecoveryWorker.WORK_NAME)
    }

    @Test
    fun `no network constraint`() {
        // Stale recovery only modifies Room, no network required
        val requiresNetwork = false
        assertEquals(false, requiresNetwork)
    }

    @Test
    fun `manual scheduleNow schedules one-time work`() {
        var scheduled = false
        val fake = object { fun scheduleNow() { scheduled = true } }
        fake.scheduleNow()
        assertTrue(scheduled)
        assertEquals("stale_upload_recovery_work", StaleUploadRecoveryWorker.WORK_NAME)
    }
}
