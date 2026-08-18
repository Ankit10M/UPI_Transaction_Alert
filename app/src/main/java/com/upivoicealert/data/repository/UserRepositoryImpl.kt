package com.upivoicealert.data.repository

import android.util.Log
import com.upivoicealert.data.datastore.UserProfileStore
import com.upivoicealert.domain.model.MerchantUser
import com.upivoicealert.domain.repository.UserRepository
import com.upivoicealert.utils.MerchantIdGenerator
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Local DataStore-backed merchant account (Feature 5). The permanent merchant
 * identity (SP-XXXXXX) + createdAt are generated exactly once — on first
 * profile access or save — and kept stable for the lifetime of the install.
 * Existing pre-Phase-4 users get an ID automatically on their next access
 * while their name/shop/phone are preserved untouched.
 */
@Singleton
class UserRepositoryImpl @Inject constructor(
    private val profileStore: UserProfileStore
) : UserRepository {

    private val idMutex = Mutex()

    override fun observeUser(): Flow<MerchantUser?> = flow {
        val merchantId = ensureMerchantId()
        emitAll(
            combine(
                profileStore.userCreatedAt,
                profileStore.userName,
                profileStore.shopName,
                profileStore.mobileNumber
            ) { createdAt, name, shopName, phone ->
                if (name.isBlank() && shopName.isBlank() && phone.isBlank()) {
                    null
                } else {
                    MerchantUser(
                        merchantId = merchantId,
                        name = name,
                        shopName = shopName,
                        phoneNumber = phone,
                        createdAt = createdAt
                    )
                }
            }
        )
    }

    override suspend fun getUser(): MerchantUser? {
        val merchantId = ensureMerchantId()
        val createdAt = profileStore.userCreatedAt.first()
        val name = profileStore.userName.first()
        val shopName = profileStore.shopName.first()
        val phone = profileStore.mobileNumber.first()
        return if (name.isBlank() && shopName.isBlank() && phone.isBlank()) {
            null
        } else {
            MerchantUser(
                merchantId = merchantId,
                name = name,
                shopName = shopName,
                phoneNumber = phone,
                createdAt = createdAt
            )
        }
    }

    override suspend fun saveProfile(name: String, shopName: String, phoneNumber: String) {
        // The merchant identity + createdAt are generated once, on the first
        // save, and never regenerated on later edits.
        val merchantId = ensureMerchantId()
        val createdAt = profileStore.userCreatedAt.first().let { if (it <= 0L) System.currentTimeMillis() else it }

        profileStore.setMerchantId(merchantId)
        profileStore.setUserCreatedAt(createdAt)
        profileStore.setUserName(name)
        profileStore.setShopName(shopName)
        profileStore.setMobileNumber(phoneNumber)

        Log.i(TAG, "PROFILE_SAVED merchantId=$merchantId name=$name shopName=$shopName phone=$phoneNumber createdAt=$createdAt")
    }

    /**
     * Returns the persisted merchant identity, generating and persisting it
     * exactly once. A mutex guards against concurrent first-access racing two
     * different IDs (idempotent afterwards).
     */
    private suspend fun ensureMerchantId(): String {
        val existing = profileStore.merchantId.first()
        if (existing.isNotBlank()) return existing

        return idMutex.withLock {
            val rechecked = profileStore.merchantId.first()
            if (rechecked.isNotBlank()) {
                rechecked
            } else {
                val generated = MerchantIdGenerator.generate()
                profileStore.setMerchantId(generated)
                generated
            }
        }
    }

    private companion object {
        const val TAG = "SHOUTPAY_PROFILE_DEBUG"
    }
}