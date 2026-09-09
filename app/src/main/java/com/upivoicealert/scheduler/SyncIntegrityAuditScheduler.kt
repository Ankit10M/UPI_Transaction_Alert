package com.upivoicealert.scheduler

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.upivoicealert.worker.SyncIntegrityAuditWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncIntegrityAuditScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager = WorkManager.getInstance(context)

    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncIntegrityAuditWorker>(24, TimeUnit.HOURS)
            .addTag(SyncIntegrityAuditWorker.WORK_NAME)
            .build()
        workManager.enqueueUniquePeriodicWork(
            SyncIntegrityAuditWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun scheduleNow() {
        val request = OneTimeWorkRequestBuilder<SyncIntegrityAuditWorker>()
            .addTag(SyncIntegrityAuditWorker.WORK_NAME)
            .build()
        workManager.enqueueUniqueWork(
            SyncIntegrityAuditWorker.WORK_NAME_IMMEDIATE,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun cancel() {
        workManager.cancelUniqueWork(SyncIntegrityAuditWorker.WORK_NAME)
        workManager.cancelUniqueWork(SyncIntegrityAuditWorker.WORK_NAME_IMMEDIATE)
    }
}
