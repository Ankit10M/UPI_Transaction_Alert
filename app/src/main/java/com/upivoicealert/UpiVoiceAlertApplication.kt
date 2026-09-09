package com.upivoicealert

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.upivoicealert.scheduler.ReconciliationScheduler
import com.upivoicealert.scheduler.StaleUploadRecoveryScheduler
import com.upivoicealert.scheduler.SyncIntegrityAuditScheduler
import com.upivoicealert.work.WorkScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class UpiVoiceAlertApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var reconciliationScheduler: ReconciliationScheduler

    @Inject
    lateinit var staleUploadRecoveryScheduler: StaleUploadRecoveryScheduler

    @Inject
    lateinit var syncIntegrityAuditScheduler: SyncIntegrityAuditScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Startup scheduling must never crash the application or block payment processing.
        // Each scheduler is wrapped individually so one failure does not prevent others.
        // Payment pipeline (NotificationListenerService -> Room -> TTS) is independent of
        // these maintenance workers.
        try { WorkScheduler.schedulePeriodic(this) } catch (_: Exception) { }
        try { reconciliationScheduler.schedulePeriodic() } catch (_: Exception) { }
        try { staleUploadRecoveryScheduler.schedulePeriodic() } catch (_: Exception) { }
        try { staleUploadRecoveryScheduler.scheduleNow() } catch (_: Exception) { }
        try { syncIntegrityAuditScheduler.schedulePeriodic() } catch (_: Exception) { }
    }
}