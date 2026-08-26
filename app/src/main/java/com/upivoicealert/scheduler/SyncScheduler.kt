package com.upivoicealert.scheduler

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.upivoicealert.worker.TransactionSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules sync worker with NetworkType.CONNECTED constraint.
 * Uses existing Hilt WorkManager integration.
 */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager = WorkManager.getInstance(context)

    fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<TransactionSyncWorker>()
            .setConstraints(constraints)
            .addTag(TransactionSyncWorker.WORK_NAME)
            .build()
        workManager.enqueueUniqueWork(
            TransactionSyncWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun schedulePeriodicSync() {
        // Future periodic variant; currently uses one-time with network constraint
        scheduleSync()
    }

    fun cancelSync() {
        workManager.cancelUniqueWork(TransactionSyncWorker.WORK_NAME)
    }
}
