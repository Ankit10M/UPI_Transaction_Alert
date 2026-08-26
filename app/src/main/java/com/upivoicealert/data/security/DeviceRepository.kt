package com.upivoicealert.data.security

import com.upivoicealert.domain.security.Device
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.HttpException

/**
 * Repository for device management. Calls API, maps DTO → Domain, handles errors.
 * Does not duplicate authentication logic; 401 is delegated to AuthInterceptor/AuthAuthenticator.
 */
@Singleton
class DeviceRepository @Inject constructor(
    private val api: DeviceApi
) {

    sealed interface DeviceResult<out T> {
        data class Success<T>(val data: T) : DeviceResult<T>
        data class Error(val message: String) : DeviceResult<Nothing>
        data object SessionExpired : DeviceResult<Nothing>
    }

    suspend fun getDevices(): DeviceResult<List<Device>> {
        return try {
            val response = api.getDevices()
            val devices = response.devices.map { it.toDomain() }
            DeviceResult.Success(devices)
        } catch (e: IOException) {
            DeviceResult.Error(e.message ?: "Network error")
        } catch (e: HttpException) {
            if (e.code() == 401) DeviceResult.SessionExpired
            else DeviceResult.Error(e.message() ?: "HTTP ${e.code()}")
        } catch (e: Exception) {
            DeviceResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun logoutDevice(deviceId: String): DeviceResult<Unit> {
        return try {
            api.logoutDevice(deviceId)
            DeviceResult.Success(Unit)
        } catch (e: IOException) {
            DeviceResult.Error(e.message ?: "Network error")
        } catch (e: HttpException) {
            when (e.code()) {
                401 -> DeviceResult.SessionExpired
                404 -> DeviceResult.Error("Device not found")
                else -> DeviceResult.Error(e.message() ?: "HTTP ${e.code()}")
            }
        } catch (e: Exception) {
            DeviceResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun logoutOtherDevices(): DeviceResult<Unit> {
        return try {
            api.logoutOtherDevices()
            DeviceResult.Success(Unit)
        } catch (e: IOException) {
            DeviceResult.Error(e.message ?: "Network error")
        } catch (e: HttpException) {
            if (e.code() == 401) DeviceResult.SessionExpired
            else DeviceResult.Error(e.message() ?: "HTTP ${e.code()}")
        } catch (e: Exception) {
            DeviceResult.Error(e.message ?: "Unknown error")
        }
    }
}
