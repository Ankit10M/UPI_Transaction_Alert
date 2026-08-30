package com.upivoicealert.di

import com.upivoicealert.data.datastore.ReconciliationStatusRecorder
import com.upivoicealert.data.datastore.ReconciliationStatusStore
import com.upivoicealert.data.datastore.SyncIntegrityStatusRecorder
import com.upivoicealert.data.datastore.SyncIntegrityStatusStore
import com.upivoicealert.data.sync.DefaultSyncMaintenanceCoordinator
import com.upivoicealert.domain.sync.SyncMaintenanceCoordinator
import com.upivoicealert.scheduler.SyncSchedulable
import com.upivoicealert.scheduler.SyncScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SchedulerModule {
    @Binds
    abstract fun bindSyncSchedulable(impl: SyncScheduler): SyncSchedulable

    @Binds
    abstract fun bindReconciliationRecorder(impl: ReconciliationStatusStore): ReconciliationStatusRecorder

    @Binds
    abstract fun bindSyncIntegrityRecorder(impl: SyncIntegrityStatusStore): SyncIntegrityStatusRecorder

    @Binds
    abstract fun bindSyncMaintenanceCoordinator(impl: DefaultSyncMaintenanceCoordinator): SyncMaintenanceCoordinator
}
