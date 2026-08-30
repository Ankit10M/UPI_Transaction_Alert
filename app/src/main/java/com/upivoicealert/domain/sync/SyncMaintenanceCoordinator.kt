package com.upivoicealert.domain.sync

/**
 * Centralized coordination for non-critical sync maintenance operations.
 * Prevents same operation overlapping with itself (in-memory, process-level).
 * Different operations may execute concurrently.
 */
interface SyncMaintenanceCoordinator {
    /**
     * Attempt to execute [block] under coordination for [operation].
     * If same operation is already running, returns AlreadyRunning without executing block.
     * Different operations do not block each other.
     * Always releases lock after success/failure/exception/cancellation.
     */
    suspend fun <T> coordinate(
        operation: SyncMaintenanceOperation,
        block: suspend () -> T
    ): MaintenanceResult<T>
}

sealed interface MaintenanceResult<out T> {
    data class Executed<T>(val value: T) : MaintenanceResult<T>
    data object AlreadyRunning : MaintenanceResult<Nothing>
}
