package com.upivoicealert.data.sync

import com.upivoicealert.data.datastore.SyncStatusStore
import com.upivoicealert.domain.sync.CloudSyncStatus
import com.upivoicealert.domain.sync.SyncState
import com.upivoicealert.domain.sync.SyncStatusRepository
import com.upivoicealert.network.NetworkMonitor
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Implementation of [SyncStatusRepository].
 *
 * Merges three reactive sources into a single [CloudSyncStatus]:
 * 1. Queue counts (pending, uploading, failed) from [SyncQueueDao]
 * 2. Network connectivity from [NetworkMonitor]
 * 3. Last successful sync timestamp from [SyncStatusStore]
 *
 * State priority: OFFLINE > SYNCING > FAILED > PENDING > SYNCED / NEVER_SYNCED
 */
@Singleton
class SyncStatusRepositoryImpl @Inject constructor(
    private val syncQueueDao: SyncQueueDao,
    private val networkMonitor: NetworkMonitor,
    private val syncStatusStore: SyncStatusStore
) : SyncStatusRepository {

    override fun observeSyncStatus(): Flow<CloudSyncStatus> {
        val pendingCountFlow = syncQueueDao.observeCountByStatus(SyncQueueEntity.STATUS_PENDING)
        val uploadingCountFlow = syncQueueDao.observeCountByStatus(SyncQueueEntity.STATUS_UPLOADING)
        val failedCountFlow = syncQueueDao.observeCountByStatus(SyncQueueEntity.STATUS_FAILED)
        val isOnlineFlow = networkMonitor.isOnline()
        val lastSyncFlow = syncStatusStore.lastSuccessfulSyncAt

        return combine(
            pendingCountFlow,
            uploadingCountFlow,
            failedCountFlow,
            isOnlineFlow,
            lastSyncFlow
        ) { pending, uploading, failed, isOnline, lastSync ->
            val state = deriveState(
                pendingCount = pending,
                uploadingCount = uploading,
                failedCount = failed,
                isOnline = isOnline,
                lastSuccessfulSyncAt = lastSync
            )
            CloudSyncStatus(
                state = state,
                pendingCount = pending,
                failedCount = failed,
                uploadingCount = uploading,
                lastSuccessfulSyncAt = lastSync
            )
        }
    }

    override suspend fun recordSuccessfulSync(timestamp: Long) {
        syncStatusStore.saveLastSuccessfulSync(timestamp)
    }

    /**
     * Determine the merchant-facing [SyncState] using the defined priority:
     * OFFLINE > SYNCING > FAILED > PENDING > SYNCED / NEVER_SYNCED
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
}
