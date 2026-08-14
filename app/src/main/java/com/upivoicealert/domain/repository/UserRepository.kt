package com.upivoicealert.domain.repository

import com.upivoicealert.domain.model.MerchantUser
import kotlinx.coroutines.flow.Flow

/**
 * Merchant account repository (Feature 5 — account model preparation).
 *
 * No authentication is implemented in the MVP. The local DataStore-backed
 * implementation persists profile fields; a future Firebase OTP implementation
 * would swap in here without touching the UI layer.
 */
interface UserRepository {

    /** Reactive profile. Null until the merchant has entered any profile data. */
    fun observeUser(): Flow<MerchantUser?>

    /** One-shot read of the current profile (null before any data is entered). */
    suspend fun getUser(): MerchantUser?

    /**
     * Persists the merchant profile. The local user id and createdAt are
     * generated once, on the first save, and kept stable afterwards.
     */
    suspend fun saveProfile(name: String, shopName: String, phoneNumber: String)
}
