package com.upivoicealert.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.upivoicealert.data.sync.StaleUploadRecoveryManager
import com.upivoicealert.data.sync.TransactionSyncRepository
import com.upivoicealert.domain.sync.MaintenanceResult
import com.upivoicealert.domain.sync.SyncDiagnosticCategory
import com.upivoicealert.domain.sync.SyncDiagnosticEventType
import com.upivoicealert.domain.sync.SyncDiagnosticRecorder
import com.upivoicealert.domain.sync.SyncMaintenanceCoordinator
import com.upivoicealert.domain.sync.SyncMaintenanceOperation
import com.upivoicealert.domain.sync.SyncStatusRepository
import com.upivoicealert.network.NetworkMonitor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Transaction Sync Worker — Phase 6.3: uploads pending transactions via TransactionSyncRepository.
 * Offline → retry, online → syncPendingTransactions(), maps result to WorkManager Result.
 * Records lastSuccessfulSyncAt when all batches succeed (created + duplicates = success).
 */
@HiltWorker
class TransactionSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val networkMonitor: NetworkMonitor,
    private val syncRepository: TransactionSyncRepository,
    private val syncStatusRepository: SyncStatusRepository,
    private val staleRecoveryManager: StaleUploadRecoveryManager,
    private val diagnosticRecorder: SyncDiagnosticRecorder,
    private val maintenanceCoordinator: SyncMaintenanceCoordinator
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            // Safety: recover stale UPLOADING -> PENDING before sync, coordinated to avoid duplicate with periodic recovery
            try {
                val coord = maintenanceCoordinator.coordinate(SyncMaintenanceOperation.STALE_UPLOAD_RECOVERY) {
                    staleRecoveryManager.recoverWithoutScheduling()
                }
                if (coord is MaintenanceResult.AlreadyRunning) {
                    Log.d(TAG, "Stale recovery already running, skip safety layer duplicate")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Stale recovery before sync failed, continuing", e)
            }

            val online = networkMonitor.isCurrentlyOnline()
            if (!online) {
                Log.d(TAG, "Sync worker offline → retry")
                runCatching {
                    diagnosticRecorder.record(SyncDiagnosticCategory.SYNC, SyncDiagnosticEventType.SYNC_OFFLINE, 0)
                }
                return Result.retry()
            }

            when (val result = syncRepository.syncPendingTransactions()) {
                is TransactionSyncRepository.SyncResult.Success -> {
                    Log.i(TAG, "Sync success")
                    syncStatusRepository.recordSuccessfulSync()
                    val count = syncRepository.lastSuccessCount
                    runCatching {
                        diagnosticRecorder.record(SyncDiagnosticCategory.SYNC, SyncDiagnosticEventType.SYNC_COMPLETED, count)
                    }
                    Result.success()
                }
                is TransactionSyncRepository.SyncResult.NoPending -> {
                    Log.d(TAG, "No pending items")
                    runCatching {
                        diagnosticRecorder.record(SyncDiagnosticCategory.SYNC, SyncDiagnosticEventType.SYNC_NO_PENDING, 0)
                    }
                    // NoPending -> WorkManager Result.success() but DO NOT update lastSuccessfulSyncAt.
                    Result.success()
                }
                is TransactionSyncRepository.SyncResult.PartialSuccess -> {
                    Log.i(TAG, "Partial success synced=${result.synced} failed=${result.failed}")
                    runCatching {
                        diagnosticRecorder.record(SyncDiagnosticCategory.SYNC, SyncDiagnosticEventType.SYNC_PARTIAL, result.synced)
                    }
                    // PartialSuccess -> WorkManager Result.success() but DO NOT update timestamp.
                    Result.success()
                }
                is TransactionSyncRepository.SyncResult.Retry -> {
                    Log.w(TAG, "Sync retry needed")
                    val type = if (syncRepository.lastRetryCause == TransactionSyncRepository.RetryCause.NETWORK)
                        SyncDiagnosticEventType.SYNC_NETWORK_RETRY else SyncDiagnosticEventType.SYNC_SERVER_RETRY
                    runCatching {
                        // affectedCount not known precisely; use 0 sanitized, or if network retry due to pending items we could pass pending count
                        diagnosticRecorder.record(SyncDiagnosticCategory.SYNC, type, 0)
                    }
                    Result.retry()
                }
                is TransactionSyncRepository.SyncResult.Error -> {
                    Log.w(TAG, "Sync error: ${result.message}")
                    runCatching {
                        diagnosticRecorder.record(SyncDiagnosticCategory.SYNC, SyncDiagnosticEventType.SYNC_VALIDATION_FAILED, 0)
                    }
                    Result.failure()
                }
                is TransactionSyncRepository.SyncResult.SessionExpired -> {
                    Log.w(TAG, "Session expired, failing worker")
                    runCatching {
                        diagnosticRecorder.record(SyncDiagnosticCategory.SYNC, SyncDiagnosticEventType.SYNC_SESSION_EXPIRED, 0)
                    }
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Sync worker failed, retry", e)
            Result.retry()
        }
    }

    companion object {
        const val TAG = "TransactionSyncWorker"
        const val WORK_NAME = "transaction_sync_work"
    }
}
