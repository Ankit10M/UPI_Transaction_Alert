package com.upivoicealert.data.sync

import com.upivoicealert.domain.sync.MaintenanceResult
import com.upivoicealert.domain.sync.SyncMaintenanceCoordinator
import com.upivoicealert.domain.sync.SyncMaintenanceOperation
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex

/**
 * Application-process-level coordinator.
 * In-memory only; lock disappears on process death.
 * Per-operation Mutex ensures same-operation exclusion without global serialization.
 * Coroutine-safe: tryLock is atomic, release in finally covers success/failure/cancellation.
 */
@Singleton
class DefaultSyncMaintenanceCoordinator @Inject constructor() : SyncMaintenanceCoordinator {

    private val locks = ConcurrentHashMap<SyncMaintenanceOperation, Mutex>()

    private fun mutexFor(operation: SyncMaintenanceOperation): Mutex =
        locks.computeIfAbsent(operation) { Mutex() }

    override suspend fun <T> coordinate(
        operation: SyncMaintenanceOperation,
        block: suspend () -> T
    ): MaintenanceResult<T> {
        val mutex = mutexFor(operation)
        // tryLock is non-suspending, immediate decision; does not block different operations
        if (!mutex.tryLock()) {
            return MaintenanceResult.AlreadyRunning
        }
        try {
            val result = block()
            return MaintenanceResult.Executed(result)
        } finally {
            // Always release, even on exception/cancellation
            if (mutex.isLocked) {
                mutex.unlock()
            }
        }
    }
}
