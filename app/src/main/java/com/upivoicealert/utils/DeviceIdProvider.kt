package com.upivoicealert.utils

/**
 * Abstraction for device identity. Allows TransactionSyncRepository to be tested without Android Context.
 */
interface DeviceIdProvider {
    suspend fun getDeviceId(): String
}
