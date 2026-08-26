package com.upivoicealert

import com.upivoicealert.data.security.DeviceApi
import com.upivoicealert.data.security.DeviceDto
import com.upivoicealert.data.security.DeviceRepository
import com.upivoicealert.data.security.DevicesResponseDto
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class DeviceRepositoryTest {

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

    @Test
    fun `successful device fetch returns device list`() = runTest {
        val api = object : DeviceApi {
            override suspend fun getDevices() = DevicesResponseDto(listOf(dto1, dto2))
            override suspend fun logoutDevice(deviceId: String) {}
            override suspend fun logoutOtherDevices() {}
        }
        val repo = DeviceRepository(api)
        val result = repo.getDevices()
        assertTrue(result is DeviceRepository.DeviceResult.Success)
        val devices = (result as DeviceRepository.DeviceResult.Success).data
        assertEquals(2, devices.size)
        assertEquals("123e4567-e89b-42d3-a456-426614174001", devices[0].deviceId)
        assertTrue(devices[0].active)
        assertEquals("Samsung A54", devices[0].deviceName)
    }

    @Test
    fun `logout device success calls DELETE`() = runTest {
        var calledId: String? = null
        val api = object : DeviceApi {
            override suspend fun getDevices() = DevicesResponseDto(emptyList())
            override suspend fun logoutDevice(deviceId: String) { calledId = deviceId }
            override suspend fun logoutOtherDevices() {}
        }
        val repo = DeviceRepository(api)
        val result = repo.logoutDevice("123e4567-e89b-42d3-a456-426614174002")
        assertTrue(result is DeviceRepository.DeviceResult.Success)
        assertEquals("123e4567-e89b-42d3-a456-426614174002", calledId)
    }

    @Test
    fun `logout others success calls API`() = runTest {
        var called = false
        val api = object : DeviceApi {
            override suspend fun getDevices() = DevicesResponseDto(emptyList())
            override suspend fun logoutDevice(deviceId: String) {}
            override suspend fun logoutOtherDevices() { called = true }
        }
        val repo = DeviceRepository(api)
        val result = repo.logoutOtherDevices()
        assertTrue(result is DeviceRepository.DeviceResult.Success)
        assertTrue(called)
    }

    @Test
    fun `network failure returns Error state`() = runTest {
        val api = object : DeviceApi {
            override suspend fun getDevices(): DevicesResponseDto { throw IOException("no internet") }
            override suspend fun logoutDevice(deviceId: String): Unit { throw IOException("offline") }
            override suspend fun logoutOtherDevices(): Unit { throw IOException("offline") }
        }
        val repo = DeviceRepository(api)
        val result = repo.getDevices()
        assertTrue(result is DeviceRepository.DeviceResult.Error)
        assertTrue((result as DeviceRepository.DeviceResult.Error).message.isNotBlank())
        val logoutResult = repo.logoutDevice("any")
        assertTrue(logoutResult is DeviceRepository.DeviceResult.Error)
    }

    @Test
    fun `session expired returns SessionExpired state`() = runTest {
        val api = object : DeviceApi {
            override suspend fun getDevices(): DevicesResponseDto {
                throw HttpException(Response.error<DevicesResponseDto>(401, "".toResponseBody(null)))
            }
            override suspend fun logoutDevice(deviceId: String): Unit {
                throw HttpException(Response.error<Unit>(401, "".toResponseBody(null)))
            }
            override suspend fun logoutOtherDevices(): Unit {
                throw HttpException(Response.error<Unit>(401, "".toResponseBody(null)))
            }
        }
        val repo = DeviceRepository(api)
        assertTrue(repo.getDevices() is DeviceRepository.DeviceResult.SessionExpired)
        assertTrue(repo.logoutDevice("id") is DeviceRepository.DeviceResult.SessionExpired)
        assertTrue(repo.logoutOtherDevices() is DeviceRepository.DeviceResult.SessionExpired)
    }

    @Test
    fun `DTO must not contain sensitive fields - DeviceRepository never exposes token`() = runTest {
        // compile-time guarantee: DeviceApi and DTO have only deviceId/deviceName/active/lastUsedAt/createdAt
        val fields = DeviceDto::class.java.declaredFields.map { it.name }.toSet()
        assertTrue(fields.contains("deviceId"))
        assertTrue(fields.contains("deviceName"))
        assertTrue(fields.contains("active"))
        assertTrue(!fields.contains("refreshToken"))
        assertTrue(!fields.contains("tokenHash"))
        assertTrue(!fields.contains("merchantId"))
    }
}
