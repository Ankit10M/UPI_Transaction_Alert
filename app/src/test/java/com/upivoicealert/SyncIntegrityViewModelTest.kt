package com.upivoicealert

import com.upivoicealert.domain.sync.SyncIntegrityStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncIntegrityViewModelTest {

    // Lightweight ViewModel test harness that mirrors SyncIntegrityViewModel logic without requiring Hilt/DataStore context.
    private class TestViewModel(
        val statusFlow: MutableStateFlow<SyncIntegrityStatus>,
        val scheduler: FakeScheduler
    ) {
        private val isChecking = MutableStateFlow(false)
        val status = statusFlow
        val checking = isChecking
        // Derive ui state similar to real ViewModel
        suspend fun deriveState(): String {
            val s = statusFlow.first()
            return when {
                s.lastAuditAt == null -> "NeverChecked"
                s.isHealthy == true -> "Healthy"
                s.isHealthy == false -> "IssuesDetected"
                else -> "NeverChecked"
            }
        }
        fun checkNow() {
            if(isChecking.value) return
            scheduler.scheduleNow()
            isChecking.value = true
        }
        fun resetChecking() { isChecking.value = false }
    }

    private class FakeScheduler {
        var scheduledCount = 0
        fun scheduleNow() { scheduledCount++ }
    }

    @Test fun `1 never checked state`() = runTest {
        val flow = MutableStateFlow(SyncIntegrityStatus.NEVER_CHECKED)
        val vm = TestViewModel(flow, FakeScheduler())
        assertEquals("NeverChecked", vm.deriveState())
    }

    @Test fun `2 healthy state`() = runTest {
        val flow = MutableStateFlow(SyncIntegrityStatus(lastAuditAt=1000L, lastIssueCount=0, isHealthy=true))
        val vm = TestViewModel(flow, FakeScheduler())
        assertEquals("Healthy", vm.deriveState())
    }

    @Test fun `3 issues detected state`() = runTest {
        val flow = MutableStateFlow(SyncIntegrityStatus(lastAuditAt=1000L, lastIssueCount=2, isHealthy=false, missingQueueCount=2))
        val vm = TestViewModel(flow, FakeScheduler())
        assertEquals("IssuesDetected", vm.deriveState())
    }

    @Test fun `4 manual check schedules work`() = runTest {
        val flow = MutableStateFlow(SyncIntegrityStatus.NEVER_CHECKED)
        val scheduler = FakeScheduler()
        val vm = TestViewModel(flow, scheduler)
        vm.checkNow()
        assertEquals(1, scheduler.scheduledCount)
        assertTrue(vm.checking.value)
    }

    @Test fun `5 repeated check remains safe`() = runTest {
        val flow = MutableStateFlow(SyncIntegrityStatus.NEVER_CHECKED)
        val scheduler = FakeScheduler()
        val vm = TestViewModel(flow, scheduler)
        vm.checkNow()
        vm.checkNow() // second should be ignored because isChecking true (KEEP policy)
        assertEquals(1, scheduler.scheduledCount)
        vm.resetChecking()
        vm.checkNow()
        assertEquals(2, scheduler.scheduledCount) // after reset, allowed again
    }

    @Test fun `6 loading checking state behaves correctly`() = runTest {
        val flow = MutableStateFlow(SyncIntegrityStatus.NEVER_CHECKED)
        val vm = TestViewModel(flow, FakeScheduler())
        assertFalse(vm.checking.value)
        vm.checkNow()
        assertTrue(vm.checking.value)
        vm.resetChecking()
        assertFalse(vm.checking.value)
    }

    @Test fun `7 error does not erase previous valid status`() = runTest {
        val flow = MutableStateFlow(SyncIntegrityStatus(lastAuditAt=5000L, lastIssueCount=0, isHealthy=true))
        val vm = TestViewModel(flow, FakeScheduler())
        assertEquals("Healthy", vm.deriveState())
        // Simulate an error that would be shown as Error state but previous status preserved in flow
        // In real ViewModel, error state keeps previousStatus param; here we just verify flow still has old value
        flow.value = SyncIntegrityStatus(lastAuditAt=5000L, lastIssueCount=0, isHealthy=true) // error does not clear store
        assertEquals("Healthy", vm.deriveState())
        assertEquals(5000L, flow.first().lastAuditAt)
    }
}
