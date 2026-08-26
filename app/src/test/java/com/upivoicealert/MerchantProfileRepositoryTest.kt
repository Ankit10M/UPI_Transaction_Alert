package com.upivoicealert

import com.upivoicealert.data.datastore.UserProfileStore
import com.upivoicealert.data.profile.MerchantProfileApi
import com.upivoicealert.data.profile.MerchantProfileDto
import com.upivoicealert.data.profile.MerchantProfileRepository
import com.upivoicealert.data.profile.MerchantProfileResponseDto
import com.upivoicealert.data.profile.MerchantProfileResult
import com.upivoicealert.data.profile.UpdateMerchantProfileRequest
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class MerchantProfileRepositoryTest {

    private class FakeStore : UserProfileStore {
        private val _userName = MutableStateFlow("")
        private val _shopName = MutableStateFlow("")
        private val _mobileNumber = MutableStateFlow("")
        private val _userCreatedAt = MutableStateFlow(0L)
        private val _merchantId = MutableStateFlow("")
        override val userName: Flow<String> get() = _userName
        override val shopName: Flow<String> get() = _shopName
        override val mobileNumber: Flow<String> get() = _mobileNumber
        override val userCreatedAt: Flow<Long> get() = _userCreatedAt
        override val merchantId: Flow<String> get() = _merchantId
        override suspend fun setMerchantId(id: String) { _merchantId.value = id }
        override suspend fun setUserCreatedAt(createdAt: Long) { _userCreatedAt.value = createdAt }
        override suspend fun setUserName(name: String) { _userName.value = name }
        override suspend fun setShopName(shopName: String) { _shopName.value = shopName }
        override suspend fun setMobileNumber(number: String) { _mobileNumber.value = number }
    }

    private fun dto(
        merchantId: String = "SP-123456",
        ownerName: String? = "Rahul",
        shopName: String? = "Rahul Store",
        phoneNumber: String = "+919876543210",
        createdAt: String = "2026-01-01T00:00:00.000Z"
    ) = MerchantProfileDto(merchantId, ownerName, shopName, phoneNumber, createdAt)

    @Test
    fun `successful fetch overwrites cache and returns Success`() = runTest {
        val store = FakeStore()
        var capturedPatch: UpdateMerchantProfileRequest? = null
        val api = object : MerchantProfileApi {
            override suspend fun getProfile() = MerchantProfileResponseDto(dto())
            override suspend fun updateProfile(request: UpdateMerchantProfileRequest): MerchantProfileResponseDto {
                capturedPatch = request
                return MerchantProfileResponseDto(dto())
            }
        }
        val repo = MerchantProfileRepository(api, store)
        val result = repo.fetchProfileWithCacheFallback()
        assertTrue(result is MerchantProfileResult.Success)
        assertEquals("SP-123456", (result as MerchantProfileResult.Success).profile.merchantId)
        assertEquals("SP-123456", store.merchantId.first())
        assertEquals("Rahul", store.userName.first())
        assertEquals("+919876543210", store.mobileNumber.first())
    }

    @Test
    fun `updateProfile sends only ownerName and shopName and updates cache`() = runTest {
        val store = FakeStore()
        var captured: UpdateMerchantProfileRequest? = null
        val updatedDto = dto(ownerName = "New Owner", shopName = "New Shop")
        val api = object : MerchantProfileApi {
            override suspend fun getProfile() = MerchantProfileResponseDto(dto())
            override suspend fun updateProfile(request: UpdateMerchantProfileRequest): MerchantProfileResponseDto {
                captured = request
                return MerchantProfileResponseDto(updatedDto)
            }
        }
        val repo = MerchantProfileRepository(api, store)
        val result = repo.updateProfileWithResult("New Owner", "New Shop")
        assertTrue(result is MerchantProfileResult.Success)
        assertEquals("New Owner", captured?.ownerName)
        assertEquals("New Shop", captured?.shopName)
        // Must never send merchantId/phoneNumber via PATCH body (request has only 2 fields)
        assertEquals(2, captured?.let { listOfNotNull(it.ownerName, it.shopName).size } ?: 0)
        assertEquals("New Owner", store.userName.first())
        assertEquals("New Shop", store.shopName.first())
    }

    @Test
    fun `network failure returns Offline with cached profile`() = runTest {
        val store = FakeStore()
        store.setMerchantId("SP-CACHED")
        store.setUserName("Cached Owner")
        store.setShopName("Cached Shop")
        store.setMobileNumber("+911111111111")
        store.setUserCreatedAt(1_700_000_000_000L)
        val api = object : MerchantProfileApi {
            override suspend fun getProfile(): MerchantProfileResponseDto { throw IOException("no internet") }
            override suspend fun updateProfile(request: UpdateMerchantProfileRequest) = throw IOException("no internet")
        }
        val repo = MerchantProfileRepository(api, store)
        val result = repo.fetchProfileWithCacheFallback()
        assertTrue(result is MerchantProfileResult.Offline)
        assertEquals("SP-CACHED", (result as MerchantProfileResult.Offline).profile.merchantId)
    }

    @Test
    fun `network failure without cache returns Error`() = runTest {
        val store = FakeStore()
        val api = object : MerchantProfileApi {
            override suspend fun getProfile(): MerchantProfileResponseDto { throw IOException("no internet") }
            override suspend fun updateProfile(request: UpdateMerchantProfileRequest) = throw IOException("no internet")
        }
        val repo = MerchantProfileRepository(api, store)
        val result = repo.fetchProfileWithCacheFallback()
        assertTrue(result is MerchantProfileResult.Error)
    }

    @Test
    fun `immutable field protection - repository never sends forbidden fields`() = runTest {
        val store = FakeStore()
        var captured: UpdateMerchantProfileRequest? = null
        val api = object : MerchantProfileApi {
            override suspend fun getProfile() = MerchantProfileResponseDto(dto())
            override suspend fun updateProfile(request: UpdateMerchantProfileRequest): MerchantProfileResponseDto {
                captured = request
                return MerchantProfileResponseDto(dto())
            }
        }
        val repo = MerchantProfileRepository(api, store)
        repo.updateProfile("A", "B")
        // Request class has only ownerName/shopName — compile-time guarantee
        assertTrue(captured != null)
        // Verify no hidden serialization of merchantId etc via reflection
        val fields = UpdateMerchantProfileRequest::class.java.declaredFields.map { it.name }.toSet()
        assertTrue(fields.contains("ownerName"))
        assertTrue(fields.contains("shopName"))
        assertTrue(!fields.contains("merchantId"))
        assertTrue(!fields.contains("phoneNumber"))
        assertTrue(!fields.contains("firebaseUid"))
        assertTrue(!fields.contains("createdAt"))
    }

    @Test
    fun `401 maps to SessionExpired`() = runTest {
        val store = FakeStore()
        val api = object : MerchantProfileApi {
            override suspend fun getProfile(): MerchantProfileResponseDto {
                throw HttpException(Response.error<MerchantProfileResponseDto>(401, "".toResponseBody(null)))
            }
            override suspend fun updateProfile(request: UpdateMerchantProfileRequest) =
                throw HttpException(Response.error<MerchantProfileResponseDto>(401, "".toResponseBody(null)))
        }
        val repo = MerchantProfileRepository(api, store)
        assertTrue(repo.fetchProfileWithCacheFallback() is MerchantProfileResult.SessionExpired)
        assertTrue(repo.updateProfileWithResult("A", "B") is MerchantProfileResult.SessionExpired)
    }
}
