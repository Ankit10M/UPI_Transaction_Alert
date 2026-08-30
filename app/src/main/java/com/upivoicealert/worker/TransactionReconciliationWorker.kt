package com.upivoicealert.worker

import android.content.Context
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.upivoicealert.data.sync.TransactionSyncReconciler
import com.upivoicealert.domain.sync.MaintenanceResult
import com.upivoicealert.domain.sync.SyncDiagnosticCategory
import com.upivoicealert.domain.sync.SyncDiagnosticEventType
import com.upivoicealert.domain.sync.SyncDiagnosticRecorder
import com.upivoicealert.domain.sync.SyncMaintenanceCoordinator
import com.upivoicealert.domain.sync.SyncMaintenanceOperation
import com.upivoicealert.scheduler.SyncSchedulable
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException

/**
 * Local integrity reconciliation worker. Does NOT require network.
 * Finds eligible transactions missing SyncQueue entries, repairs them,
 * and schedules sync if repairs occurred. Never modifies existing queue rows.
 */
@HiltWorker
class TransactionReconciliationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val reconciler: TransactionSyncReconciler,
    private val syncScheduler: SyncSchedulable,
    private val diagnosticRecorder: SyncDiagnosticRecorder,
    private val maintenanceCoordinator: SyncMaintenanceCoordinator
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val coordResult = maintenanceCoordinator.coordinate(SyncMaintenanceOperation.LOCAL_RECONCILIATION) {
                reconciler.reconcile()
            }
            when (coordResult) {
                is MaintenanceResult.AlreadyRunning -> {
                    Log.d(TAG, "Reconciliation already running → skip duplicate")
                    return Result.success()
                }
                is MaintenanceResult.Executed -> {
                    val result = coordResult.value
                    Log.i(TAG, "Reconciliation result: $result")

                    if (result.repairedCount > 0) {
                        try {
                            syncScheduler.scheduleSync()
                            Log.i(TAG, "Scheduled sync after repairing ${result.repairedCount} items")
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to schedule sync after reconciliation", e)
                        }
                        runCatching {
                            diagnosticRecorder.record(SyncDiagnosticCategory.RECONCILIATION, SyncDiagnosticEventType.RECONCILIATION_REPAIRED, result.repairedCount)
                        }
                    } else {
                        runCatching {
                            diagnosticRecorder.record(SyncDiagnosticCategory.RECONCILIATION, SyncDiagnosticEventType.RECONCILIATION_COMPLETED, 0)
                        }
                    }

                    if (result.errorCount > 0 && result.repairedCount == 0) {
                        Result.success()
                    } else {
                        Result.success()
                    }
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Transient DB/IO failure, retry", e)
            runCatching { diagnosticRecorder.record(SyncDiagnosticCategory.RECONCILIATION, SyncDiagnosticEventType.RECONCILIATION_ERROR, 0) }
            Result.retry()
        } catch (e: SQLiteException) {
            Log.w(TAG, "Transient DB failure, retry", e)
            runCatching { diagnosticRecorder.record(SyncDiagnosticCategory.RECONCILIATION, SyncDiagnosticEventType.RECONCILIATION_ERROR, 0) }
            Result.retry()
        } catch (e: Exception) {
            val msg = e.message?.lowercase() ?: ""
            if (msg.contains("busy") || msg.contains("locked") || msg.contains("transient")) {
                Log.w(TAG, "Transient failure, retry", e)
                runCatching { diagnosticRecorder.record(SyncDiagnosticCategory.RECONCILIATION, SyncDiagnosticEventType.RECONCILIATION_ERROR, 0) }
                Result.retry()
            } else {
                Log.e(TAG, "Permanent reconciliation failure", e)
                runCatching { diagnosticRecorder.record(SyncDiagnosticCategory.RECONCILIATION, SyncDiagnosticEventType.RECONCILIATION_ERROR, 0) }
                Result.failure()
            }
        }
    }

    companion object {
        const val TAG = "ReconciliationWorker"
        const val WORK_NAME = "transaction_reconciliation_work"
    }
}
