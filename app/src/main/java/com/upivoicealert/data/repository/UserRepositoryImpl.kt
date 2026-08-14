package com.upivoicealert.data.repository

import android.util.Log
import com.upivoicealert.data.datastore.SettingsDataStore
import com.upivoicealert.domain.model.MerchantUser
import com.upivoicealert.domain.repository.UserRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * Local DataStore-backed merchant account (Feature 5). The user id + createdAt
 * are generated once on the first profile save and kept stable, ready for a
 * future Firebase OTP implementation to replace the id with the Firebase UID.
 */
@Singleton
class UserRepositoryImpl @Inject constructor(
    private val dataStore: SettingsDataStore
) : UserRepository {

    override fun observeUser(): Flow<MerchantUser?> = combine(
        dataStore.userId,
        dataStore.userCreatedAt,
        dataStore.userName,
        dataStore.shopName,
        dataStore.mobileNumber
    ) { id, createdAt, name, shopName, phone ->
        if (name.isBlank() && shopName.isBlank() && phone.isBlank()) {
            null
        } else {
            MerchantUser(
                id = id,
                phoneNumber = phone,
                name = name,
                shopName = shopName,
                createdAt = createdAt
            )
        }
    }

    override suspend fun getUser(): MerchantUser? {
        val id = dataStore.userId.first()
        val createdAt = dataStore.userCreatedAt.first()
        val name = dataStore.userName.first()
        val shopName = dataStore.shopName.first()
        val phone = dataStore.mobileNumber.first()
        return if (name.isBlank() && shopName.isBlank() && phone.isBlank()) {
            null
        } else {
            MerchantUser(id = id, phoneNumber = phone, name = name, shopName = shopName, createdAt = createdAt)
        }
    }

    override suspend fun saveProfile(name: String, shopName: String, phoneNumber: String) {
        // Generate id + createdAt once, on the first save.
        val currentId = dataStore.userId.first()
        val id = currentId.ifBlank { UUID.randomUUID().toString() }
        val createdAt = dataStore.userCreatedAt.first().let { if (it <= 0L) System.currentTimeMillis() else it }

        dataStore.setUserId(id)
        dataStore.setUserCreatedAt(createdAt)
        dataStore.setUserName(name)
        dataStore.setShopName(shopName)
        dataStore.setMobileNumber(phoneNumber)

        Log.i(TAG, "PROFILE_SAVED id=$id name=$name shopName=$shopName phone=$phoneNumber createdAt=$createdAt")
    }

    private companion object {
        const val TAG = "SHOUTPAY_PROFILE_DEBUG"
    }
}
