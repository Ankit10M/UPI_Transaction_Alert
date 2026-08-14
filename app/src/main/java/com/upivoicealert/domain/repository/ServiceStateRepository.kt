package com.upivoicealert.domain.repository

import com.upivoicealert.domain.model.ServiceStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Persisted service run state (Feature 6 — service control improvement).
 *
 * Stored in DataStore so the START/STOP state survives process restarts. The
 * NotificationListenerService remains connected to the system in both states —
 * only transaction processing and TTS are gated on [ServiceStatus.SERVICE_RUNNING].
 */
interface ServiceStateRepository {

    /** Reactive service state. */
    fun observeStatus(): Flow<ServiceStatus>

    suspend fun getStatus(): ServiceStatus = observeStatus().first()

    suspend fun setRunning(running: Boolean)
}
