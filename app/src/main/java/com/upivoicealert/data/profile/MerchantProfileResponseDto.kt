package com.upivoicealert.data.profile

/**
 * Response wrapper for GET/PATCH /api/merchant/profile
 * { "merchant": MerchantProfileDto }
 */
data class MerchantProfileResponseDto(
    val merchant: MerchantProfileDto
)
