package com.upivoicealert.domain.model

/**
 * Merchant account model (Feature 5 — account model preparation).
 *
 * Pure data holder, future-ready for Firebase OTP auth. No authentication is
 * implemented in the MVP: [merchantId] is a permanent local identity generated
 * once (format `SP-XXXXXX`, see
 * [com.upivoicealert.utils.MerchantIdGenerator]) and persisted via
 * [com.upivoicealert.domain.repository.UserRepository] into DataStore. When
 * OTP auth lands, `merchantId` maps to the Firebase user/merchant key.
 */
data class MerchantUser(
    val merchantId: String,
    val name: String,
    val shopName: String,
    val phoneNumber: String,
    val createdAt: Long
)