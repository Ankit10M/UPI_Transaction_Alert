package com.upivoicealert.data.security

/**
 * Response wrapper for GET /api/auth/devices
 * { "devices": [ DeviceDto, ... ] }
 */
data class DevicesResponseDto(
    val devices: List<DeviceDto>
)
