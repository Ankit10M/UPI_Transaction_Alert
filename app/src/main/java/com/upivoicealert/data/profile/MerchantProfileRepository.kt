package com.upivoicealert.data.profile

import com.upivoicealert.data.datastore.UserProfileStore
import com.upivoicealert.domain.profile.MerchantProfile
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import retrofit2.HttpException

/**
 * Repository for merchant profile sync. Backend is source of truth.
 *
 * Flow:
 * UI -> Repository -> API -> Mapper -> Cache (DataStore)
 *
 * Rules:
 * - Cache is NOT source of truth, backend response always overwrites cache.
 * - Cache exists only for offline display.
 * - Only ownerName/shopName are sent in PATCH (immutable fields never sent).
 */
@Singleton
class MerchantProfileRepository @Inject constructor(
    private val api: MerchantProfileApi,
    private val profileStore: UserProfileStore
) {

    // ─── Cache ──────────────────────────────────────────────────────────────

    fun observeCachedProfile(): Flow<MerchantProfile?> = combine(
        profileStore.merchantId,
        profileStore.userName,
        profileStore.shopName,
        profileStore.mobileNumber,
        profileStore.userCreatedAt
    ) { merchantId, ownerName, shopName, phoneNumber, createdAtMillis ->
        if (merchantId.isBlank()) null else MerchantProfile(
            merchantId = merchantId,
            ownerName = ownerName.ifBlank { null },
            shopName = shopName.ifBlank { null },
            phoneNumber = phoneNumber,
            createdAt = if (createdAtMillis > 0) Instant.ofEpochMilli(createdAtMillis).toString() else ""
        )
    }

    suspend fun getCachedProfile(): MerchantProfile? {
        val merchantId = profileStore.merchantId.first()
        if (merchantId.isBlank()) return null
        val ownerName = profileStore.userName.first().ifBlank { null }
        val shopName = profileStore.shopName.first().ifBlank { null }
        val phoneNumber = profileStore.mobileNumber.first()
        val createdAtMillis = profileStore.userCreatedAt.first()
        val createdAt = if (createdAtMillis > 0) Instant.ofEpochMilli(createdAtMillis).toString() else ""
        return MerchantProfile(
            merchantId = merchantId,
            ownerName = ownerName,
            shopName = shopName,
            phoneNumber = phoneNumber,
            createdAt = createdAt
        )
    }

    suspend fun saveMerchantProfile(profile: MerchantProfile) {
        profileStore.setMerchantId(profile.merchantId)
        profileStore.setUserName(profile.ownerName ?: "")
        profileStore.setShopName(profile.shopName ?: "")
        profileStore.setMobileNumber(profile.phoneNumber)
        val epoch = try {
            if (profile.createdAt.isBlank()) 0L else Instant.parse(profile.createdAt).toEpochMilli()
        } catch (_: Exception) {
            0L
        }
        profileStore.setUserCreatedAt(epoch)
    }

    // ─── Backend ────────────────────────────────────────────────────────────

    /**
     * Fetch profile from backend, overwrite cache, return domain model.
     * Throws IOException for network failure, HttpException for HTTP errors.
     */
    suspend fun fetchProfile(): MerchantProfile {
        val response = api.getProfile()
        val domain = response.merchant.toDomain()
        saveMerchantProfile(domain)
        return domain
    }

    /**
     * Update profile via PATCH. Only ownerName/shopName are sent.
     * Backend response overwrites cache.
     */
    suspend fun updateProfile(ownerName: String?, shopName: String?): MerchantProfile {
        // Immutable field protection: only allowed fields in request
        val request = UpdateMerchantProfileRequest(
            ownerName = ownerName?.trim()?.ifBlank { null },
            shopName = shopName?.trim()?.ifBlank { null }
        )
        val response = api.updateProfile(request)
        val domain = response.merchant.toDomain()
        saveMerchantProfile(domain)
        return domain
    }

    /**
     * Fetch with offline fallback: if network fails, return cached profile if available.
     * Returns Result wrapper for ViewModel to map to Offline state.
     */
    suspend fun fetchProfileWithCacheFallback(): MerchantProfileResult {
        return try {
            val profile = fetchProfile()
            MerchantProfileResult.Success(profile)
        } catch (e: IOException) {
            val cached = getCachedProfile()
            if (cached != null) MerchantProfileResult.Offline(cached, e.message ?: "Offline")
            else MerchantProfileResult.Error(e.message ?: "Network error")
        } catch (e: HttpException) {
            when (e.code()) {
                401 -> MerchantProfileResult.SessionExpired
                else -> MerchantProfileResult.Error(e.message() ?: "HTTP ${e.code()}")
            }
        } catch (e: Exception) {
            MerchantProfileResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun updateProfileWithResult(ownerName: String?, shopName: String?): MerchantProfileResult {
        return try {
            val profile = updateProfile(ownerName, shopName)
            MerchantProfileResult.Success(profile)
        } catch (e: IOException) {
            MerchantProfileResult.Error(e.message ?: "Network error")
        } catch (e: HttpException) {
            when (e.code()) {
                401 -> MerchantProfileResult.SessionExpired
                400 -> MerchantProfileResult.Error("Validation error: ${e.message()}")
                else -> MerchantProfileResult.Error(e.message() ?: "HTTP ${e.code()}")
            }
        } catch (e: Exception) {
            MerchantProfileResult.Error(e.message ?: "Unknown error")
        }
    }
}

/**
 * Result wrapper for ViewModel. Keeps repository pure but allows Offline handling.
 */
sealed interface MerchantProfileResult {
    data class Success(val profile: MerchantProfile) : MerchantProfileResult
    data class Offline(val profile: MerchantProfile, val message: String = "Offline") : MerchantProfileResult
    data class Error(val message: String) : MerchantProfileResult
    data object SessionExpired : MerchantProfileResult
}
