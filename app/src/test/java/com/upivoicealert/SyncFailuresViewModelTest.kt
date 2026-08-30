package com.upivoicealert

import com.upivoicealert.domain.sync.RetrySyncItemResult
import com.upivoicealert.domain.sync.SyncFailure
import com.upivoicealert.domain.sync.SyncQueueRepository
import com.upivoicealert.domain.sync.SyncStatus
import com.upivoicealert.domain.sync.SyncItem
import com.upivoicealert.scheduler.SyncSchedulable
import com.upivoicealert.ui.sync.SyncFailuresUiState
import com.upivoicealert.ui.sync.SyncFailuresViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncFailuresViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class FakeRepo : SyncQueueRepository {
        val failuresFlow = MutableStateFlow<List<SyncFailure>>(emptyList())
        var failuresSnapshot: List<SyncFailure> = emptyList()
        var retryResult: RetrySyncItemResult = RetrySyncItemResult.Success
        var retryAllCount: Int = 0
        var retryCalledId: Long? = null
        var retryAllCalled = false
        var throwOnGet = false

        override suspend fun addQueueItem(entityType: String, entityId: String): Long = 1
        override suspend fun addSyncItem(item: SyncItem): Long = 1
        override suspend fun getPendingItems(): List<SyncItem> = emptyList()
        override suspend fun updateStatus(id: Long, status: SyncStatus) {}
        override suspend fun incrementRetryCount(id: Long) {}
        override suspend fun getAll(): List<SyncItem> = emptyList()
        override suspend fun clearAll() {}
        override suspend fun getFailedItems(): List<SyncFailure> {
            if (throwOnGet) throw RuntimeException("load failed")
            return failuresSnapshot
        }
        override suspend fun getFailedItem(id: Long): SyncFailure? = failuresSnapshot.firstOrNull { it.queueId == id }
        override fun observeFailedItems(): Flow<List<SyncFailure>> = failuresFlow
        override suspend fun retryFailedItem(id: Long): RetrySyncItemResult {
            retryCalledId = id
            return retryResult
        }
        override suspend fun retryAllFailed(): Int {
            retryAllCalled = true
            return retryAllCount
        }
    }

    private class FakeScheduler : SyncSchedulable {
        var scheduleCount = 0
        override fun scheduleSync() { scheduleCount++ }
    }

    private fun createViewModel(repo: FakeRepo, scheduler: FakeScheduler): SyncFailuresViewModel {
        return SyncFailuresViewModel(repo, scheduler)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `27 failed list loads`() = runTest {
        val repo = FakeRepo()
        val failures = listOf(SyncFailure(1, "TRANSACTION", "txn-1", 1, "VALIDATION_ERROR", "msg", 1000L))
        repo.failuresSnapshot = failures
        repo.failuresFlow.value = failures
        val scheduler = FakeScheduler()
        val vm = createViewModel(repo, scheduler)
        advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(state is SyncFailuresUiState.Success)
        assertEquals(1, (state as SyncFailuresUiState.Success).failures.size)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `28 empty state`() = runTest {
        val repo = FakeRepo()
        repo.failuresSnapshot = emptyList()
        repo.failuresFlow.value = emptyList()
        val vm = createViewModel(repo, FakeScheduler())
        advanceUntilIdle()
        assertTrue(vm.uiState.value is SyncFailuresUiState.Empty)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `29 retry success queues sync`() = runTest {
        val repo = FakeRepo()
        repo.failuresSnapshot = listOf(SyncFailure(5, "TRANSACTION", "txn-5", 1, "VALIDATION_ERROR", "msg", 1000L))
        repo.failuresFlow.value = listOf(SyncFailure(5, "TRANSACTION", "txn-5", 1, "VALIDATION_ERROR", "msg", 1000L))
        repo.retryResult = RetrySyncItemResult.Success
        val scheduler = FakeScheduler()
        val vm = createViewModel(repo, scheduler)
        advanceUntilIdle()
        vm.retryFailedItem(5)
        advanceUntilIdle()
        assertEquals(5L, repo.retryCalledId)
        assertEquals(1, scheduler.scheduleCount)
        assertTrue(vm.retryEvent.value != null)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `30 retry invalid state`() = runTest {
        val repo = FakeRepo()
        repo.retryResult = RetrySyncItemResult.InvalidState
        val vm = createViewModel(repo, FakeScheduler())
        advanceUntilIdle()
        vm.retryFailedItem(99)
        advanceUntilIdle()
        assertTrue(vm.retryEvent.value is com.upivoicealert.ui.sync.RetryUiEvent.InvalidState)
        assertTrue(vm.isRetrying.value == false)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `31 retry all success`() = runTest {
        val repo = FakeRepo()
        repo.retryAllCount = 2
        val scheduler = FakeScheduler()
        val vm = createViewModel(repo, scheduler)
        advanceUntilIdle()
        vm.retryAllFailed()
        advanceUntilIdle()
        assertTrue(repo.retryAllCalled)
        assertEquals(1, scheduler.scheduleCount)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `32 offline retry queues successfully`() = runTest {
        val repo = FakeRepo()
        repo.failuresSnapshot = listOf(SyncFailure(1, "TRANSACTION", "txn-1", 0, "VALIDATION_ERROR", "msg", 1000L))
        repo.failuresFlow.value = listOf(SyncFailure(1, "TRANSACTION", "txn-1", 0, "VALIDATION_ERROR", "msg", 1000L))
        repo.retryResult = RetrySyncItemResult.Success
        val scheduler = FakeScheduler()
        val vm = createViewModel(repo, scheduler)
        advanceUntilIdle()
        vm.retryFailedItem(1)
        advanceUntilIdle()
        assertEquals(1, scheduler.scheduleCount)
        assertTrue(vm.retryEvent.value is com.upivoicealert.ui.sync.RetryUiEvent.QueuedForSync)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `33 error state when load fails via flow exception`() = runTest {
        val repo = FakeRepo()
        // Make observe flow throw by using a flow that throws
        val throwingRepo = object : SyncQueueRepository by repo {
            override fun observeFailedItems(): Flow<List<SyncFailure>> = kotlinx.coroutines.flow.flow { throw RuntimeException("load failed") }
            override suspend fun getFailedItems(): List<SyncFailure> = throw RuntimeException("load failed")
        }
        val vm = SyncFailuresViewModel(throwingRepo, FakeScheduler())
        advanceUntilIdle()
        // After exception, should be Error, not Loading
        assertTrue(vm.uiState.value is SyncFailuresUiState.Error)
    }

    @Test
    fun `retry not found`() = runTest {
        val repo = FakeRepo()
        repo.retryResult = RetrySyncItemResult.NotFound
        val vm = createViewModel(repo, FakeScheduler())
        advanceUntilIdle()
        vm.retryFailedItem(999)
        advanceUntilIdle()
        assertTrue(vm.retryEvent.value is com.upivoicealert.ui.sync.RetryUiEvent.NotFound)
    }
}
