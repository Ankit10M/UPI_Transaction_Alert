package com.upivoicealert.domain.model

/**
 * Merchant account model (Feature 5 — account model preparation).
 *
 * Pure data holder, future-ready for Firebase OTP auth. No authentication is
 * implemented in the MVP: the id is generated locally and profile fields are
 * persisted via [com.upivoicealert.domain.repository.UserRepository] into
 * DataStore. When OTP auth lands, `id` becomes the Firebase UID.
 */
data class MerchantUser(
    val id: String,
    val phoneNumber: String,
    val name: String,
    val shopName: String,
    val createdAt: Long
)
