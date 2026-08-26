package com.upivoicealert

import com.upivoicealert.data.datastore.UserProfileStore
import com.upivoicealert.data.profile.MerchantProfileApi
import com.upivoicealert.data.profile.MerchantProfileDto
import com.upivoicealert.data.profile.MerchantProfileRepository
import com.upivoicealert.data.profile.MerchantProfileResponseDto
import com.upivoicealert.data.profile.UpdateMerchantProfileRequest
import com.upivoicealert.domain.profile.MerchantProfile
import com.upivoicealert.ui.profile.MerchantProfileUiState
import com.upivoicealert.ui.profile.MerchantProfileViewModel
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MerchantProfileViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

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

    private fun profileDto() = MerchantProfileDto(
        merchantId = "SP-123456",
        ownerName = "Rahul",
        shopName = "Shop",
        phoneNumber = "+919876543210",
        createdAt = "2026-01-01T00:00:00.000Z"
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `loadProfile success emits Success`() = runTest {
        val store = FakeStore()
        val api = object : MerchantProfileApi {
            override suspend fun getProfile() = MerchantProfileResponseDto(profileDto())
            override suspend fun updateProfile(request: UpdateMerchantProfileRequest) = MerchantProfileResponseDto(profileDto())
        }
        val repo = MerchantProfileRepository(api, store)
        val vm = MerchantProfileViewModel(repo)
        vm.loadProfile()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is MerchantProfileUiState.Success)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `loadProfile offline emits Offline with cached`() = runTest {
        val store = FakeStore()
        store.setMerchantId("SP-OFFLINE")
        store.setUserName("Cached")
        store.setShopName("Shop")
        store.setMobileNumber("+911111111111")
        store.setUserCreatedAt(1_700_000_000_000L)
        val api = object : MerchantProfileApi {
            override suspend fun getProfile(): MerchantProfileResponseDto { throw IOException("offline") }
            override suspend fun updateProfile(request: UpdateMerchantProfileRequest) = throw IOException("offline")
        }
        val repo = MerchantProfileRepository(api, store)
        val vm = MerchantProfileViewModel(repo)
        vm.loadProfile()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is MerchantProfileUiState.Offline)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `updateProfile success emits Success`() = runTest {
        val store = FakeStore()
        val updated = profileDto().copy(ownerName = "New", shopName = "New Shop")
        val api = object : MerchantProfileApi {
            override suspend fun getProfile() = MerchantProfileResponseDto(profileDto())
            override suspend fun updateProfile(request: UpdateMerchantProfileRequest) = MerchantProfileResponseDto(updated)
        }
        val repo = MerchantProfileRepository(api, store)
        val vm = MerchantProfileViewModel(repo)
        vm.updateProfile("New", "New Shop")
        advanceUntilIdle()
        assertTrue(vm.uiState.value is MerchantProfileUiState.Success)
        val state = vm.uiState.value as MerchantProfileUiState.Success
        assertTrue(state.profile.ownerName == "New")
    }
}
