package com.upivoicealert.data.profile

/**
 * DTO for backend merchant profile. Mirrors backend response:
 * { "merchant": { merchantId, ownerName, shopName, phoneNumber, createdAt } }
 * createdAt is ISO-8601 string from backend (e.g. 2026-01-01T00:00:00.000Z).
 */
data class MerchantProfileDto(
    val merchantId: String,
    val ownerName: String?,
    val shopName: String?,
    val phoneNumber: String,
    val createdAt: String
)
