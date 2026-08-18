package com.upivoicealert.data.datastore

import kotlinx.coroutines.flow.Flow

/**
 * Narrow contract for the merchant profile storage backing
 * [com.upivoicealert.domain.repository.UserRepository]. Implemented by
 * [SettingsDataStore]; declared as an interface so the repository can be unit
 * tested with an in-memory fake (the Android DataStore itself is not JVM-testable).
 */
interface UserProfileStore {

    val userName: Flow<String>
    val shopName: Flow<String>
    val mobileNumber: Flow<String>
    val userCreatedAt: Flow<Long>

    /** Permanent merchant identity, e.g. "SP-A82F91". Blank until generated. */
    val merchantId: Flow<String>

    suspend fun setMerchantId(id: String)

    suspend fun setUserCreatedAt(createdAt: Long)

    suspend fun setUserName(name: String)

    suspend fun setShopName(shopName: String)

    suspend fun setMobileNumber(number: String)
}