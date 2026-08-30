package com.upivoicealert.domain.sync

import kotlinx.coroutines.flow.Flow

/**
 * Domain repository for merchant-facing sync health.
 * Combines queue counts, network state, and last-sync timestamp into a
 * single [CloudSyncStatus] stream the UI can observe.
 */
interface SyncStatusRepository {

    /** Reactive stream of the current sync health. */
    fun observeSyncStatus(): Flow<CloudSyncStatus>

    /** Record that a complete sync operation finished successfully. */
    suspend fun recordSuccessfulSync(timestamp: Long = System.currentTimeMillis())
}
