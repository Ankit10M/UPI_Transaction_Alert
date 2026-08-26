package com.upivoicealert.data.security

import com.upivoicealert.domain.security.Device

/**
 * Mapper DTO → Domain. Keeps API models separated.
 */
fun DeviceDto.toDomain(): Device = Device(
    deviceId = deviceId,
    deviceName = deviceName ?: "Unknown Device",
    active = active,
    lastUsedAt = lastUsedAt,
    createdAt = createdAt
)
