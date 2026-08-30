package com.upivoicealert.worker

import android.content.Context
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.upivoicealert.data.datastore.SyncIntegrityStatusRecorder
import com.upivoicealert.data.sync.SyncIntegrityAuditor
import com.upivoicealert.domain.sync.MaintenanceResult
import com.upivoicealert.domain.sync.SyncDiagnosticCategory
import com.upivoicealert.domain.sync.SyncDiagnosticEventType
import com.upivoicealert.domain.sync.SyncDiagnosticRecorder
import com.upivoicealert.domain.sync.SyncMaintenanceCoordinator
import com.upivoicealert.domain.sync.SyncMaintenanceOperation
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException

/**
 * Phase 7.5 audit worker — local database only, no network, read-only.
 * Never uploads, mutates queue, or repairs. Only audits and persists result.
 */
@HiltWorker
class SyncIntegrityAuditWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val auditor: SyncIntegrityAuditor,
    private val statusRecorder: SyncIntegrityStatusRecorder,
    private val diagnosticRecorder: SyncDiagnosticRecorder,
    private val maintenanceCoordinator: SyncMaintenanceCoordinator
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val coord = maintenanceCoordinator.coordinate(SyncMaintenanceOperation.INTEGRITY_AUDIT) {
                auditor.audit()
            }
            when (coord) {
                is MaintenanceResult.AlreadyRunning -> {
                    Log.d(TAG, "Integrity audit already running → skip duplicate")
                    return Result.success()
                }
                is MaintenanceResult.Executed -> {
                    val result = coord.value
                    try {
                        statusRecorder.recordAudit(result)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to persist audit result", e)
                    }
                    Log.i(TAG, "Audit complete: healthy=${result.isHealthy} issues=${result.totalIssues} $result")
                    if (result.isHealthy) {
                        runCatching { diagnosticRecorder.record(SyncDiagnosticCategory.INTEGRITY_AUDIT, SyncDiagnosticEventType.INTEGRITY_AUDIT_HEALTHY, 0) }
                    } else {
                        runCatching { diagnosticRecorder.record(SyncDiagnosticCategory.INTEGRITY_AUDIT, SyncDiagnosticEventType.INTEGRITY_AUDIT_ISSUES_FOUND, result.totalIssues) }
                    }
                    Result.success()
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Transient IO failure, retry", e)
            runCatching { diagnosticRecorder.record(SyncDiagnosticCategory.INTEGRITY_AUDIT, SyncDiagnosticEventType.INTEGRITY_AUDIT_ERROR, 0) }
            Result.retry()
        } catch (e: SQLiteException) {
            Log.w(TAG, "Transient DB failure, retry", e)
            runCatching { diagnosticRecorder.record(SyncDiagnosticCategory.INTEGRITY_AUDIT, SyncDiagnosticEventType.INTEGRITY_AUDIT_ERROR, 0) }
            Result.retry()
        } catch (e: Exception) {
            val msg = e.message?.lowercase() ?: ""
            if (msg.contains("busy") || msg.contains("locked") || msg.contains("transient")) {
                Log.w(TAG, "Transient failure, retry", e)
                runCatching { diagnosticRecorder.record(SyncDiagnosticCategory.INTEGRITY_AUDIT, SyncDiagnosticEventType.INTEGRITY_AUDIT_ERROR, 0) }
                Result.retry()
            } else {
                Log.e(TAG, "Permanent audit failure", e)
                runCatching { diagnosticRecorder.record(SyncDiagnosticCategory.INTEGRITY_AUDIT, SyncDiagnosticEventType.INTEGRITY_AUDIT_ERROR, 0) }
                Result.failure()
            }
        }
    }

    companion object {
        const val TAG = "SyncIntegrityAuditWorker"
        const val WORK_NAME = "sync_integrity_audit_work"
    }
}
