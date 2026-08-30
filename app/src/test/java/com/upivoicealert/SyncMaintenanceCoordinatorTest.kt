package com.upivoicealert

import com.upivoicealert.data.sync.DefaultSyncMaintenanceCoordinator
import com.upivoicealert.domain.sync.MaintenanceResult
import com.upivoicealert.domain.sync.SyncMaintenanceOperation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SyncMaintenanceCoordinatorTest {

    @Test fun `A first execution is allowed`() = runTest {
        val c = DefaultSyncMaintenanceCoordinator()
        val r = c.coordinate(SyncMaintenanceOperation.LOCAL_RECONCILIATION) { 42 }
        assertTrue(r is MaintenanceResult.Executed)
        assertEquals(42, (r as MaintenanceResult.Executed).value)
    }

    @Test fun `B duplicate same-operation prevented`() = runTest {
        val c = DefaultSyncMaintenanceCoordinator()
        // Start long-running operation in background, keep lock held
        val job = async {
            c.coordinate(SyncMaintenanceOperation.LOCAL_RECONCILIATION) {
                delay(200)
                "A"
            }
        }
        // Give background time to acquire lock
        delay(50)
        val b = c.coordinate(SyncMaintenanceOperation.LOCAL_RECONCILIATION) { "B" }
        assertTrue(b is MaintenanceResult.AlreadyRunning)
        // Cleanup
        val a = job.await()
        assertTrue(a is MaintenanceResult.Executed)
    }

    @Test fun `C different operations do not block each other`() = runTest {
        val c = DefaultSyncMaintenanceCoordinator()
        val job = async {
            c.coordinate(SyncMaintenanceOperation.LOCAL_RECONCILIATION) {
                delay(200)
                "recon"
            }
        }
        delay(50)
        // Integrity audit should succeed while reconciliation is running
        val audit = c.coordinate(SyncMaintenanceOperation.INTEGRITY_AUDIT) { "audit" }
        assertTrue(audit is MaintenanceResult.Executed)
        assertEquals("audit", (audit as MaintenanceResult.Executed).value)
        job.await()
    }

    @Test fun `D lock released after success`() = runTest {
        val c = DefaultSyncMaintenanceCoordinator()
        val r1 = c.coordinate(SyncMaintenanceOperation.STALE_UPLOAD_RECOVERY) { "first" }
        assertTrue(r1 is MaintenanceResult.Executed)
        val r2 = c.coordinate(SyncMaintenanceOperation.STALE_UPLOAD_RECOVERY) { "second" }
        assertTrue(r2 is MaintenanceResult.Executed)
        assertEquals("second", (r2 as MaintenanceResult.Executed).value)
    }

    @Test fun `E lock released after failure`() = runTest {
        val c2 = DefaultSyncMaintenanceCoordinator()
        var threw = false
        try {
            c2.coordinate(SyncMaintenanceOperation.INTEGRITY_AUDIT) { throw IllegalStateException("fail") }
        } catch (e: IllegalStateException) { threw = true }
        assertTrue(threw)
        // Now should be able to run again
        val r2 = c2.coordinate(SyncMaintenanceOperation.INTEGRITY_AUDIT) { "ok" }
        assertTrue(r2 is MaintenanceResult.Executed)
    }

    @Test fun `F lock released after cancellation`() = runTest {
        val c = DefaultSyncMaintenanceCoordinator()
        val job = async {
            c.coordinate(SyncMaintenanceOperation.LOCAL_RECONCILIATION) {
                delay(500)
                "never"
            }
        }
        delay(50)
        job.cancel()
        // Give cancellation propagation
        try { job.await() } catch (_: CancellationException) {}
        delay(50)
        val r = c.coordinate(SyncMaintenanceOperation.LOCAL_RECONCILIATION) { "afterCancel" }
        assertTrue(r is MaintenanceResult.Executed)
    }

    @Test fun `G concurrent acquisition only one wins`() = runTest {
        val c = DefaultSyncMaintenanceCoordinator()
        // Launch 10 concurrent attempts for same operation
        val deferred = (1..10).map {
            async {
                c.coordinate(SyncMaintenanceOperation.STALE_UPLOAD_RECOVERY) {
                    delay(100)
                    it
                }
            }
        }
        val results = deferred.map { it.await() }
        val executed = results.count { it is MaintenanceResult.Executed }
        val already = results.count { it is MaintenanceResult.AlreadyRunning }
        assertEquals(1, executed)
        assertEquals(9, already)
    }

    @Test fun `H different ops concurrent both execute`() = runTest {
        val c = DefaultSyncMaintenanceCoordinator()
        val a = async { c.coordinate(SyncMaintenanceOperation.LOCAL_RECONCILIATION) { delay(100); "a" } }
        val b = async { c.coordinate(SyncMaintenanceOperation.STALE_UPLOAD_RECOVERY) { delay(100); "b" } }
        val c2 = async { c.coordinate(SyncMaintenanceOperation.INTEGRITY_AUDIT) { delay(100); "c" } }
        val ra = a.await(); val rb = b.await(); val rc = c2.await()
        assertTrue(ra is MaintenanceResult.Executed)
        assertTrue(rb is MaintenanceResult.Executed)
        assertTrue(rc is MaintenanceResult.Executed)
    }
}
