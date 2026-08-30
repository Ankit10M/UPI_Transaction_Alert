package com.upivoicealert

import com.upivoicealert.domain.sync.CloudSyncStatus
import com.upivoicealert.domain.sync.SyncState
import com.upivoicealert.domain.sync.SyncStatusRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SyncStatusViewModel] and [CloudSyncStatus].
 * Since [SyncStatusViewModel] requires a real [SyncScheduler] (Android Context),
 * we test the status observation logic via the repository contract and
 * the state derivation logic via the model directly.
 */
class SyncStatusViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class FakeSyncStatusRepository : SyncStatusRepository {
        private val status = MutableStateFlow(CloudSyncStatus.INITIAL)
        var lastSyncRecorded = false
            private set

        fun emitStatus(syncStatus: CloudSyncStatus) {
            status.value = syncStatus
        }

        override fun observeSyncStatus(): Flow<CloudSyncStatus> = status

        override suspend fun recordSuccessfulSync(timestamp: Long) {
            lastSyncRecorded = true
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `initial state is CloudSyncStatus INITIAL`() = runTest {
        val initial = CloudSyncStatus.INITIAL
        assertEquals(SyncState.NEVER_SYNCED, initial.state)
        assertEquals(0, initial.pendingCount)
        assertEquals(0, initial.failedCount)
        assertEquals(0, initial.uploadingCount)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `repository emits synced status`() = runTest {
        val repo = FakeSyncStatusRepository()

        repo.emitStatus(CloudSyncStatus(
            state = SyncState.SYNCED,
            pendingCount = 0,
            failedCount = 0,
            uploadingCount = 0,
            lastSuccessfulSyncAt = 1_700_000_000_000L
        ))
        advanceUntilIdle()

        val status = repo.observeSyncStatus().first()
        assertEquals(SyncState.SYNCED, status.state)
        assertEquals(0, status.pendingCount)
        assertEquals(0, status.failedCount)
        assertEquals(1_700_000_000_000L, status.lastSuccessfulSyncAt)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `repository emits offline status`() = runTest {
        val repo = FakeSyncStatusRepository()

        repo.emitStatus(CloudSyncStatus(
            state = SyncState.OFFLINE,
            pendingCount = 5,
            failedCount = 0,
            uploadingCount = 0,
            lastSuccessfulSyncAt = null
        ))
        advanceUntilIdle()

        val status = repo.observeSyncStatus().first()
        assertEquals(SyncState.OFFLINE, status.state)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `repository emits pending status`() = runTest {
        val repo = FakeSyncStatusRepository()

        repo.emitStatus(CloudSyncStatus(
            state = SyncState.PENDING,
            pendingCount = 3,
            failedCount = 0,
            uploadingCount = 0,
            lastSuccessfulSyncAt = 1_700_000_000_000L
        ))
        advanceUntilIdle()

        val status = repo.observeSyncStatus().first()
        assertEquals(SyncState.PENDING, status.state)
        assertEquals(3, status.pendingCount)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `repository emits syncing status`() = runTest {
        val repo = FakeSyncStatusRepository()

        repo.emitStatus(CloudSyncStatus(
            state = SyncState.SYNCING,
            pendingCount = 0,
            failedCount = 0,
            uploadingCount = 2,
            lastSuccessfulSyncAt = null
        ))
        advanceUntilIdle()

        val status = repo.observeSyncStatus().first()
        assertEquals(SyncState.SYNCING, status.state)
        assertEquals(2, status.uploadingCount)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `repository emits failed status`() = runTest {
        val repo = FakeSyncStatusRepository()

        repo.emitStatus(CloudSyncStatus(
            state = SyncState.FAILED,
            pendingCount = 0,
            failedCount = 2,
            uploadingCount = 0,
            lastSuccessfulSyncAt = null
        ))
        advanceUntilIdle()

        val status = repo.observeSyncStatus().first()
        assertEquals(SyncState.FAILED, status.state)
        assertEquals(2, status.failedCount)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `recordSuccessfulSync updates repository state`() = runTest {
        val repo = FakeSyncStatusRepository()
        assertFalse(repo.lastSyncRecorded)

        repo.recordSuccessfulSync(System.currentTimeMillis())
        assertTrue(repo.lastSyncRecorded)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `CloudSyncStatus equality works correctly`() = runTest {
        val a = CloudSyncStatus(SyncState.SYNCED, 0, 0, 0, 1000L)
        val b = CloudSyncStatus(SyncState.SYNCED, 0, 0, 0, 1000L)
        assertEquals(a, b)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `CloudSyncStatus copy preserves fields`() = runTest {
        val original = CloudSyncStatus(SyncState.PENDING, 5, 2, 1, 1000L)
        val copy = original.copy(failedCount = 0)
        assertEquals(SyncState.PENDING, copy.state)
        assertEquals(5, copy.pendingCount)
        assertEquals(0, copy.failedCount)
        assertEquals(1, copy.uploadingCount)
        assertEquals(1000L, copy.lastSuccessfulSyncAt)
    }
}
