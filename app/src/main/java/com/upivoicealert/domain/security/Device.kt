package com.upivoicealert.domain.security

/**
 * Domain model for device. DTO must not reach UI.
 */
data class Device(
    val deviceId: String,
    val deviceName: String,
    val active: Boolean,
    val lastUsedAt: String?,
    val createdAt: String?
)
