package com.upivoicealert.scheduler

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.upivoicealert.worker.StaleUploadRecoveryWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StaleUploadRecoveryScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager = WorkManager.getInstance(context)

    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<StaleUploadRecoveryWorker>(24, TimeUnit.HOURS)
            .addTag(StaleUploadRecoveryWorker.WORK_NAME)
            .build()
        workManager.enqueueUniquePeriodicWork(
            StaleUploadRecoveryWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun scheduleNow() {
        val request = OneTimeWorkRequestBuilder<StaleUploadRecoveryWorker>()
            .addTag(StaleUploadRecoveryWorker.WORK_NAME)
            .build()
        workManager.enqueueUniqueWork(
            StaleUploadRecoveryWorker.WORK_NAME_IMMEDIATE,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun cancel() {
        workManager.cancelUniqueWork(StaleUploadRecoveryWorker.WORK_NAME)
        workManager.cancelUniqueWork(StaleUploadRecoveryWorker.WORK_NAME_IMMEDIATE)
    }
}
