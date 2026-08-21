package com.upivoicealert.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import kotlinx.coroutines.flow.first

private val Context.deviceIdentityDataStore by preferencesDataStore(name = "device_identity")

/** Creates one ShoutPay-specific UUID v4 and persists it for this app install. */
class DeviceIdGenerator(private val context: Context) {
    private val deviceIdKey = stringPreferencesKey("device_id")

    suspend fun getOrCreate(): String {
        val existing = context.deviceIdentityDataStore.data.first()[deviceIdKey]
        if (!existing.isNullOrBlank()) return existing

        var generated: String? = null
        context.deviceIdentityDataStore.edit { preferences ->
            generated = preferences[deviceIdKey]
            if (generated.isNullOrBlank()) {
                generated = UUID.randomUUID().toString()
                preferences[deviceIdKey] = generated!!
            }
        }
        return requireNotNull(generated)
    }
}
