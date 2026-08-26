package com.upivoicealert.domain.profile

/**
 * Domain model for merchant profile. Never expose DTO directly to UI.
 * Backend is source of truth; cache is only for offline display.
 */
data class MerchantProfile(
    val merchantId: String,
    val ownerName: String?,
    val shopName: String?,
    val phoneNumber: String,
    val createdAt: String
)
