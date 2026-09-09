package com.upivoicealert.worker

import android.content.Context
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.upivoicealert.data.sync.StaleUploadRecoveryManager
import com.upivoicealert.domain.sync.MaintenanceResult
import com.upivoicealert.domain.sync.SyncDiagnosticCategory
import com.upivoicealert.domain.sync.SyncDiagnosticEventType
import com.upivoicealert.domain.sync.SyncDiagnosticRecorder
import com.upivoicealert.domain.sync.SyncMaintenanceCoordinator
import com.upivoicealert.domain.sync.SyncMaintenanceOperation
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException

@HiltWorker
class StaleUploadRecoveryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val recoveryManager: StaleUploadRecoveryManager,
    private val diagnosticRecorder: SyncDiagnosticRecorder,
    private val maintenanceCoordinator: SyncMaintenanceCoordinator
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val coord = maintenanceCoordinator.coordinate(SyncMaintenanceOperation.STALE_UPLOAD_RECOVERY) {
                recoveryManager.recover()
            }
            when (coord) {
                is MaintenanceResult.AlreadyRunning -> {
                    Log.d(TAG, "Stale recovery already running → skip duplicate")
                    return Result.success()
                }
                is MaintenanceResult.Executed -> {
                    val result = coord.value
                    Log.i(TAG, "Stale recovery done: $result")
                    if (result.recoveredCount > 0) {
                        runCatching { diagnosticRecorder.record(SyncDiagnosticCategory.STALE_RECOVERY, SyncDiagnosticEventType.STALE_UPLOADS_RECOVERED, result.recoveredCount) }
                    } else {
                        runCatching { diagnosticRecorder.record(SyncDiagnosticCategory.STALE_RECOVERY, SyncDiagnosticEventType.STALE_RECOVERY_COMPLETED, 0) }
                    }
                    Result.success()
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Transient failure, retry", e)
            runCatching { diagnosticRecorder.record(SyncDiagnosticCategory.STALE_RECOVERY, SyncDiagnosticEventType.STALE_RECOVERY_ERROR, 0) }
            Result.retry()
        } catch (e: SQLiteException) {
            Log.w(TAG, "Transient DB failure, retry", e)
            runCatching { diagnosticRecorder.record(SyncDiagnosticCategory.STALE_RECOVERY, SyncDiagnosticEventType.STALE_RECOVERY_ERROR, 0) }
            Result.retry()
        } catch (e: Exception) {
            val msg = e.message?.lowercase() ?: ""
            if (msg.contains("busy") || msg.contains("locked") || msg.contains("transient")) {
                Log.w(TAG, "Transient, retry", e)
                runCatching { diagnosticRecorder.record(SyncDiagnosticCategory.STALE_RECOVERY, SyncDiagnosticEventType.STALE_RECOVERY_ERROR, 0) }
                Result.retry()
            } else {
                Log.e(TAG, "Permanent failure", e)
                runCatching { diagnosticRecorder.record(SyncDiagnosticCategory.STALE_RECOVERY, SyncDiagnosticEventType.STALE_RECOVERY_ERROR, 0) }
                Result.failure()
            }
        }
    }

    companion object {
        const val TAG = "StaleRecoveryWorker"
        const val WORK_NAME = "stale_upload_recovery_work"
        /** One-time immediate work (startup recovery) uses distinct name to avoid collision with periodic. */
        const val WORK_NAME_IMMEDIATE = "stale_upload_recovery_work_immediate"
    }
}
