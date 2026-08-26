package com.upivoicealert.data.security

/**
 * DTO for backend device. Mirrors /api/auth/devices response.
 * Never exposed to UI directly.
 */
data class DeviceDto(
    val deviceId: String,
    val deviceName: String?,
    val active: Boolean,
    val lastUsedAt: String?,
    val createdAt: String?
)
