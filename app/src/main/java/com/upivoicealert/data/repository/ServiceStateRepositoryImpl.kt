package com.upivoicealert.data.repository

import com.upivoicealert.data.datastore.SettingsDataStore
import com.upivoicealert.domain.model.ServiceStatus
import com.upivoicealert.domain.repository.ServiceStateRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed service run state (Feature 6). Reuses the existing
 * `monitoring_enabled` preference so persisted state from previous builds stays
 * compatible — START writes true, STOP writes false.
 */
@Singleton
class ServiceStateRepositoryImpl @Inject constructor(
    private val dataStore: SettingsDataStore
) : ServiceStateRepository {

    override fun observeStatus(): Flow<ServiceStatus> =
        dataStore.monitoringEnabled.map { enabled ->
            if (enabled) ServiceStatus.SERVICE_RUNNING else ServiceStatus.SERVICE_STOPPED
        }

    override suspend fun setRunning(running: Boolean) = dataStore.setMonitoringEnabled(running)
}
