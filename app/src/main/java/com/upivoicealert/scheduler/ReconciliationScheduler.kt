package com.upivoicealert.scheduler

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.upivoicealert.worker.TransactionReconciliationWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReconciliationScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager = WorkManager.getInstance(context)

    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<TransactionReconciliationWorker>(24, TimeUnit.HOURS)
            .addTag(TransactionReconciliationWorker.WORK_NAME)
            .build()
        workManager.enqueueUniquePeriodicWork(
            TransactionReconciliationWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun scheduleNow() {
        val request = OneTimeWorkRequestBuilder<TransactionReconciliationWorker>()
            .addTag(TransactionReconciliationWorker.WORK_NAME)
            .build()
        workManager.enqueueUniqueWork(
            TransactionReconciliationWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun cancel() {
        workManager.cancelUniqueWork(TransactionReconciliationWorker.WORK_NAME)
    }
}
