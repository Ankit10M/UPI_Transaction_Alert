package com.upivoicealert

import com.upivoicealert.domain.sync.SyncIntegrityResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncIntegrityStatusStoreTest {

    private class FakeStatusStore {
        private var lastAt: Long? = null
        private var lastIssue: Int = 0
        private var isHealthy: Boolean? = null
        private var missing=0; private var orphaned=0; private var duplicate=0; private var invalid=0; private var stale=0

        suspend fun recordAudit(result: SyncIntegrityResult) {
            lastAt = result.checkedAt
            lastIssue = result.totalIssues
            isHealthy = result.isHealthy
            missing = result.missingQueueCount
            orphaned = result.orphanedQueueCount
            duplicate = result.duplicateQueueCount
            invalid = result.invalidQueueCount
            stale = result.staleUploadingCount
        }

        fun get(): Map<String, Any?> = mapOf(
            "at" to lastAt, "issue" to lastIssue, "healthy" to isHealthy,
            "missing" to missing, "orphaned" to orphaned, "duplicate" to duplicate, "invalid" to invalid, "stale" to stale
        )

        fun observeAt() = kotlinx.coroutines.flow.flow { emit(lastAt) }
    }

    private lateinit var store: FakeStatusStore

    @Before fun setup() { store = FakeStatusStore() }

    @Test fun `1 initial state never checked`() = runTest {
        assertNull(store.get()["at"])
        assertEquals(0, store.get()["issue"])
        assertNull(store.get()["healthy"])
    }

    @Test fun `2 audit result saved`() = runTest {
        val r = SyncIntegrityResult(2,2,0,0,0,0,0,12345L)
        store.recordAudit(r)
        assertEquals(12345L, store.get()["at"])
    }

    @Test fun `3 healthy status persisted`() = runTest {
        val r = SyncIntegrityResult(2,2,0,0,0,0,0,100L)
        store.recordAudit(r)
        assertEquals(true, store.get()["healthy"])
        assertEquals(0, store.get()["issue"])
    }

    @Test fun `4 issue count persisted`() = runTest {
        val r = SyncIntegrityResult(2,1,1,1,0,0,0,100L)
        store.recordAudit(r)
        assertEquals(2, store.get()["issue"])
        assertEquals(false, store.get()["healthy"])
        assertEquals(1, store.get()["missing"])
        assertEquals(1, store.get()["orphaned"])
    }

    @Test fun `5 timestamp survives reload`() = runTest {
        val r = SyncIntegrityResult(1,1,0,0,0,0,0,9999L)
        store.recordAudit(r)
        val at1 = store.observeAt().first()
        // Simulate reload: new store reading persisted value would still have same timestamp
        assertEquals(9999L, at1)
        // second save overwrites
        store.recordAudit(r.copy(checkedAt=10000L))
        assertEquals(10000L, store.observeAt().first())
    }
}
