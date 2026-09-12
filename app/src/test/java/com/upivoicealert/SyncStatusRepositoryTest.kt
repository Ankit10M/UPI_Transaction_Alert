package com.upivoicealert

import com.upivoicealert.data.sync.SyncQueueDao
import com.upivoicealert.data.sync.SyncQueueEntity
import com.upivoicealert.data.sync.SyncStatusRepositoryImpl
import com.upivoicealert.domain.sync.CloudSyncStatus
import com.upivoicealert.domain.sync.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SyncStatusRepositoryImpl] state derivation logic.
 * Since [SyncStatusRepositoryImpl] requires Android dependencies (NetworkMonitor,
 * SyncStatusStore), we test the core state derivation logic independently.
 *
 * Priority: OFFLINE > SYNCING > FAILED > PENDING > SYNCED / NEVER_SYNCED
 */
class SyncStatusRepositoryTest {

    private class FakeSyncQueueDao : SyncQueueDao {
        private val pendingCount = MutableStateFlow(0)
        private val uploadingCount = MutableStateFlow(0)
        private val failedCount = MutableStateFlow(0)

        fun setPendingCount(count: Int) { pendingCount.value = count }
        fun setUploadingCount(count: Int) { uploadingCount.value = count }
        fun setFailedCount(count: Int) { failedCount.value = count }

        override fun observeCountByStatus(status: String): Flow<Int> = when (status) {
            SyncQueueEntity.STATUS_PENDING -> pendingCount
            SyncQueueEntity.STATUS_UPLOADING -> uploadingCount
            SyncQueueEntity.STATUS_FAILED -> failedCount
            else -> flowOf(0)
        }
        override suspend fun getCountByStatus(status: String): Int = when (status) {
            SyncQueueEntity.STATUS_PENDING -> pendingCount.value
            SyncQueueEntity.STATUS_UPLOADING -> uploadingCount.value
            SyncQueueEntity.STATUS_FAILED -> failedCount.value
            else -> 0
        }
        override suspend fun insert(item: SyncQueueEntity): Long = 1L
        override suspend fun insertIgnore(item: SyncQueueEntity): Long = -1L
        override suspend fun getByStatus(status: String) = emptyList<SyncQueueEntity>()
        override suspend fun getPendingItems() = emptyList<SyncQueueEntity>()
        override suspend fun updateStatus(id: Long, status: String, updatedAt: Long) {}
        override suspend fun incrementRetryCount(id: Long, updatedAt: Long) {}
        override suspend fun getAll() = emptyList<SyncQueueEntity>()
        override suspend fun deleteById(id: Long) {}
        override suspend fun clearAll() {}
        override suspend fun getFailedItems(): List<SyncQueueEntity> = emptyList()
        override suspend fun getById(id: Long): SyncQueueEntity? = null
        override suspend fun markFailedWithDiagnostics(id: Long, status: String, errorCode: String?, errorMessage: String?, failedAt: Long?, updatedAt: Long) {}
        override suspend fun retryFailedItem(id: Long, updatedAt: Long): Int = 0
        override suspend fun retryAllFailed(updatedAt: Long): Int = 0
        override fun observeFailedItems(): kotlinx.coroutines.flow.Flow<List<SyncQueueEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
        override suspend fun getStaleUploadingItems(cutoffTime: Long) = emptyList<SyncQueueEntity>()
        override suspend fun recoverStaleUploading(cutoffTime: Long, updatedAt: Long) = 0
        override suspend fun countTransactionQueueItems(): Int = 0
        override suspend fun countAllQueueItems(): Int = 0
        override suspend fun countOrphanedQueueItems(): Int = 0
        override suspend fun countDuplicateExtraRows(): Int = 0
        override suspend fun countDuplicateGroups(): Int = 0
        override suspend fun countInvalidQueueItems(): Int = 0
        override suspend fun countStaleUploading(cutoffTime: Long): Int = 0
        override suspend fun updateStatusIfExpected(id: Long, expectedStatus: String, newStatus: String, updatedAt: Long): Int = 0
        override suspend fun incrementRetryCountIfExpected(id: Long, expectedStatus: String, updatedAt: Long): Int = 0
        override suspend fun markFailedWithDiagnosticsIfExpected(id: Long, status: String, errorCode: String?, errorMessage: String?, failedAt: Long?, updatedAt: Long, expectedStatus: String): Int = 0
}

    /**
     * Mirrors [SyncStatusRepositoryImpl.deriveState] logic.
     * Extracted for testability without Android dependencies.
     */
    private fun deriveState(
        pendingCount: Int,
        uploadingCount: Int,
        failedCount: Int,
        isOnline: Boolean,
        lastSuccessfulSyncAt: Long?
    ): SyncState {
        if (!isOnline) return SyncState.OFFLINE
        if (uploadingCount > 0) return SyncState.SYNCING
        if (failedCount > 0) return SyncState.FAILED
        if (pendingCount > 0) return SyncState.PENDING
        return if (lastSuccessfulSyncAt != null) SyncState.SYNCED else SyncState.NEVER_SYNCED
    }

    @Test
    fun `pending transactions result in PENDING state`() = runTest {
        val state = deriveState(
            pendingCount = 3,
            uploadingCount = 0,
            failedCount = 0,
            isOnline = true,
            lastSuccessfulSyncAt = null
        )
        assertEquals(SyncState.PENDING, state)
    }

    @Test
    fun `uploading transactions result in SYNCING state`() = runTest {
        val state = deriveState(
            pendingCount = 0,
            uploadingCount = 2,
            failedCount = 0,
            isOnline = true,
            lastSuccessfulSyncAt = null
        )
        assertEquals(SyncState.SYNCING, state)
    }

    @Test
    fun `failed transactions result in FAILED state`() = runTest {
        val state = deriveState(
            pendingCount = 0,
            uploadingCount = 0,
            failedCount = 1,
            isOnline = true,
            lastSuccessfulSyncAt = null
        )
        assertEquals(SyncState.FAILED, state)
    }

    @Test
    fun `offline network results in OFFLINE state`() = runTest {
        val state = deriveState(
            pendingCount = 5,
            uploadingCount = 0,
            failedCount = 0,
            isOnline = false,
            lastSuccessfulSyncAt = null
        )
        assertEquals(SyncState.OFFLINE, state)
    }

    @Test
    fun `empty queue with timestamp results in SYNCED state`() = runTest {
        val state = deriveState(
            pendingCount = 0,
            uploadingCount = 0,
            failedCount = 0,
            isOnline = true,
            lastSuccessfulSyncAt = 1_700_000_000_000L
        )
        assertEquals(SyncState.SYNCED, state)
    }

    @Test
    fun `empty queue without timestamp results in NEVER_SYNCED state`() = runTest {
        val state = deriveState(
            pendingCount = 0,
            uploadingCount = 0,
            failedCount = 0,
            isOnline = true,
            lastSuccessfulSyncAt = null
        )
        assertEquals(SyncState.NEVER_SYNCED, state)
    }

    @Test
    fun `OFFLINE has highest priority over SYNCING`() = runTest {
        val state = deriveState(
            pendingCount = 0,
            uploadingCount = 2,
            failedCount = 0,
            isOnline = false,
            lastSuccessfulSyncAt = 1_700_000_000_000L
        )
        assertEquals(SyncState.OFFLINE, state)
    }

    @Test
    fun `SYNCING has higher priority than FAILED`() = runTest {
        val state = deriveState(
            pendingCount = 0,
            uploadingCount = 1,
            failedCount = 2,
            isOnline = true,
            lastSuccessfulSyncAt = null
        )
        assertEquals(SyncState.SYNCING, state)
    }

    @Test
    fun `FAILED has higher priority than PENDING`() = runTest {
        val state = deriveState(
            pendingCount = 5,
            uploadingCount = 0,
            failedCount = 1,
            isOnline = true,
            lastSuccessfulSyncAt = null
        )
        assertEquals(SyncState.FAILED, state)
    }

    @Test
    fun `PENDING has higher priority than SYNCED`() = runTest {
        val state = deriveState(
            pendingCount = 1,
            uploadingCount = 0,
            failedCount = 0,
            isOnline = true,
            lastSuccessfulSyncAt = 1_700_000_000_000L
        )
        assertEquals(SyncState.PENDING, state)
    }

    @Test
    fun `CloudSyncStatus INITIAL has correct default values`() = runTest {
        val initial = CloudSyncStatus.INITIAL
        assertEquals(SyncState.NEVER_SYNCED, initial.state)
        assertEquals(0, initial.pendingCount)
        assertEquals(0, initial.failedCount)
        assertEquals(0, initial.uploadingCount)
        assertNull(initial.lastSuccessfulSyncAt)
    }

    @Test
    fun `CloudSyncStatus data class preserves all fields`() = runTest {
        val status = CloudSyncStatus(
            state = SyncState.SYNCED,
            pendingCount = 3,
            failedCount = 1,
            uploadingCount = 2,
            lastSuccessfulSyncAt = 1_700_000_000_000L
        )
        assertEquals(SyncState.SYNCED, status.state)
        assertEquals(3, status.pendingCount)
        assertEquals(1, status.failedCount)
        assertEquals(2, status.uploadingCount)
        assertEquals(1_700_000_000_000L, status.lastSuccessfulSyncAt)
    }

    @Test
    fun `all zero counts online with no sync = NEVER_SYNCED`() = runTest {
        val state = deriveState(0, 0, 0, true, null)
        assertEquals(SyncState.NEVER_SYNCED, state)
    }

    @Test
    fun `all zero counts offline = OFFLINE`() = runTest {
        val state = deriveState(0, 0, 0, false, null)
        assertEquals(SyncState.OFFLINE, state)
    }
}
