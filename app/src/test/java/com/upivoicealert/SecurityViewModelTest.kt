package com.upivoicealert

import com.upivoicealert.data.security.DeviceApi
import com.upivoicealert.data.security.DeviceDto
import com.upivoicealert.data.security.DeviceRepository
import com.upivoicealert.data.security.DevicesResponseDto
import com.upivoicealert.ui.security.SecurityUiState
import com.upivoicealert.ui.security.SecurityViewModel
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class SecurityViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val dto1 = DeviceDto(
        deviceId = "123e4567-e89b-42d3-a456-426614174001",
        deviceName = "Samsung A54",
        active = true,
        lastUsedAt = "2026-05-10T12:00:00.000Z",
        createdAt = "2026-01-01T00:00:00.000Z"
    )
    private val dto2 = DeviceDto(
        deviceId = "123e4567-e89b-42d3-a456-426614174002",
        deviceName = "Old Phone",
        active = false,
        lastUsedAt = "2026-05-09T10:00:00.000Z",
        createdAt = "2026-01-02T00:00:00.000Z"
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `Loading to Success`() = runTest {
        val api = object : DeviceApi {
            override suspend fun getDevices() = DevicesResponseDto(listOf(dto1, dto2))
            override suspend fun logoutDevice(deviceId: String) {}
            override suspend fun logoutOtherDevices() {}
        }
        val repo = DeviceRepository(api)
        val vm = SecurityViewModel(repo)
        vm.loadDevices()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is SecurityUiState.Success)
        val devices = (vm.uiState.value as SecurityUiState.Success).devices
        assertTrue(devices.size == 2)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `logout refreshes list`() = runTest {
        var getCallCount = 0
        val api = object : DeviceApi {
            override suspend fun getDevices(): DevicesResponseDto {
                getCallCount++
                // After logout, return only active device
                return if (getCallCount == 1) DevicesResponseDto(listOf(dto1, dto2))
                else DevicesResponseDto(listOf(dto1))
            }
            override suspend fun logoutDevice(deviceId: String) {}
            override suspend fun logoutOtherDevices() {}
        }
        val repo = DeviceRepository(api)
        val vm = SecurityViewModel(repo)
        vm.loadDevices()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is SecurityUiState.Success)
        vm.logoutDevice(dto2.deviceId)
        advanceUntilIdle()
        // logout triggers loadDevices again
        assertTrue(vm.uiState.value is SecurityUiState.Success)
        val devices = (vm.uiState.value as SecurityUiState.Success).devices
        assertTrue(devices.size == 1)
        assertTrue(devices[0].active)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `error state on network failure`() = runTest {
        val api = object : DeviceApi {
            override suspend fun getDevices(): DevicesResponseDto { throw IOException("offline") }
            override suspend fun logoutDevice(deviceId: String) {}
            override suspend fun logoutOtherDevices() {}
        }
        val repo = DeviceRepository(api)
        val vm = SecurityViewModel(repo)
        vm.loadDevices()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is SecurityUiState.Error)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `session expired state`() = runTest {
        val api = object : DeviceApi {
            override suspend fun getDevices(): DevicesResponseDto {
                throw HttpException(Response.error<DevicesResponseDto>(401, "".toResponseBody(null)))
            }
            override suspend fun logoutDevice(deviceId: String) {}
            override suspend fun logoutOtherDevices() {}
        }
        val repo = DeviceRepository(api)
        val vm = SecurityViewModel(repo)
        vm.loadDevices()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is SecurityUiState.SessionExpired)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `logout other devices refreshes list`() = runTest {
        val api = object : DeviceApi {
            override suspend fun getDevices() = DevicesResponseDto(listOf(dto1))
            override suspend fun logoutDevice(deviceId: String) {}
            override suspend fun logoutOtherDevices() {}
        }
        val repo = DeviceRepository(api)
        val vm = SecurityViewModel(repo)
        vm.logoutOtherDevices()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is SecurityUiState.Success)
    }
}
