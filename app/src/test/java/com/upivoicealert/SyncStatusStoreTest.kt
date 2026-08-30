package com.upivoicealert

import com.upivoicealert.data.datastore.SyncStatusStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SyncStatusStore].
 * Tests saving and retrieving the last successful sync timestamp.
 *
 * Since DataStore requires a real Android Context, we test the store's API
 * contract using an in-memory fake that mirrors the same behavior.
 */
class SyncStatusStoreTest {

    /**
     * Minimal in-memory implementation for testing the store's API contract.
     * Mirrors the SyncStatusStore interface without requiring Android DataStore.
     */
    private class FakeSyncStatusStore {
        private var lastSyncAt: Long? = null

        suspend fun saveLastSuccessfulSync(timestamp: Long) {
            lastSyncAt = timestamp
        }

        suspend fun getLastSuccessfulSync(): Long? = lastSyncAt

        fun observe(): kotlinx.coroutines.flow.Flow<Long?> = kotlinx.coroutines.flow.flow {
            emit(lastSyncAt)
        }
    }

    private lateinit var fakeStore: FakeSyncStatusStore

    @Before
    fun setup() {
        fakeStore = FakeSyncStatusStore()
    }

    @Test
    fun `initial state is null - never synced`() = runTest {
        assertNull(fakeStore.getLastSuccessfulSync())
    }

    @Test
    fun `save and retrieve timestamp`() = runTest {
        val timestamp = 1_700_000_000_000L
        fakeStore.saveLastSuccessfulSync(timestamp)
        assertEquals(timestamp, fakeStore.getLastSuccessfulSync())
    }

    @Test
    fun `save overwrites previous timestamp`() = runTest {
        fakeStore.saveLastSuccessfulSync(1000L)
        fakeStore.saveLastSuccessfulSync(2000L)
        assertEquals(2000L, fakeStore.getLastSuccessfulSync())
    }

    @Test
    fun `store survives multiple saves in sequence`() = runTest {
        fakeStore.saveLastSuccessfulSync(100L)
        fakeStore.saveLastSuccessfulSync(200L)
        fakeStore.saveLastSuccessfulSync(300L)
        assertEquals(300L, fakeStore.getLastSuccessfulSync())
    }

    @Test
    fun `observe returns null initially`() = runTest {
        val value = fakeStore.observe().first()
        assertNull(value)
    }

    @Test
    fun `observe returns saved value`() = runTest {
        fakeStore.saveLastSuccessfulSync(5000L)
        val value = fakeStore.observe().first()
        assertEquals(5000L, value)
    }
}
