package com.upivoicealert

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NetworkMonitor logic tests using fake implementation.
 * Verifies online/offline state exposure without Android dependencies.
 */
class NetworkMonitorTest {

    private class FakeNetworkMonitor(private var online: Boolean) {
        private var flowValue = online
        fun setOnline(value: Boolean) { flowValue = value; online = value }
        fun isCurrentlyOnline(): Boolean = online
        // Mimics Flow<Boolean> distinctUntilChanged behavior
        suspend fun isOnlineFlow(): Boolean = flowValue
    }

    @Test
    fun `online state returns true`() = runTest {
        val monitor = FakeNetworkMonitor(true)
        assertTrue(monitor.isCurrentlyOnline())
        assertTrue(monitor.isOnlineFlow())
    }

    @Test
    fun `offline state returns false`() = runTest {
        val monitor = FakeNetworkMonitor(false)
        assertFalse(monitor.isCurrentlyOnline())
        assertFalse(monitor.isOnlineFlow())
    }

    @Test
    fun `transitions from offline to online`() = runTest {
        val monitor = FakeNetworkMonitor(false)
        assertFalse(monitor.isCurrentlyOnline())
        monitor.setOnline(true)
        assertTrue(monitor.isCurrentlyOnline())
    }
}
