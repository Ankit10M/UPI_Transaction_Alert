package com.upivoicealert.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.upivoicealert.utils.Constants
import java.util.concurrent.TimeUnit

object WorkScheduler {

    fun schedulePeriodic(context: Context) {
        val cleanupRequest = PeriodicWorkRequestBuilder<CleanupWorker>(
            Constants.CLEANUP_PERIOD_HOURS, TimeUnit.HOURS
        ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "cleanup_work", ExistingPeriodicWorkPolicy.KEEP, cleanupRequest
        )

        val retryRequest = PeriodicWorkRequestBuilder<RetryFailedParseWorker>(
            Constants.RETRY_PERIOD_HOURS, TimeUnit.HOURS
        ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "retry_work", ExistingPeriodicWorkPolicy.KEEP, retryRequest
        )
    }
}