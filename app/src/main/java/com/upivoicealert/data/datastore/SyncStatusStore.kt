package com.upivoicealert.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.syncStatusDataStore: androidx.datastore.core.DataStore<Preferences> by preferencesDataStore(
    name = "sync_status"
)

/**
 * Persistent store for sync metadata — survives app restart, process death,
 * and device reboot.
 *
 * Source of truth for [lastSuccessfulSyncAt]. Only updated when a complete
 * sync operation finishes successfully (all attempted batches accepted as
 * created or idempotent duplicate). NOT updated on:
 * - scheduling work
 * - network becoming available
 * - request beginning
 * - authentication failure
 * - partial batch failure
 */
@Singleton
class SyncStatusStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val LAST_SUCCESSFUL_SYNC_AT = longPreferencesKey("last_successful_sync_at")
    }

    /** Reactive stream of the last successful sync timestamp (null = never synced). */
    val lastSuccessfulSyncAt: Flow<Long?> = context.syncStatusDataStore.data.map {
        it[Keys.LAST_SUCCESSFUL_SYNC_AT]?.takeIf { t -> t > 0 }
    }

    /** One-shot read for synchronous access. */
    suspend fun getLastSuccessfulSync(): Long? {
        return try {
            val prefs = context.syncStatusDataStore.data.first()
            prefs[Keys.LAST_SUCCESSFUL_SYNC_AT]?.takeIf { it > 0 }
        } catch (_: Exception) {
            null
        }
    }

    /** Persist the timestamp of the last fully successful sync operation. */
    suspend fun saveLastSuccessfulSync(timestamp: Long) {
        context.syncStatusDataStore.edit { it[Keys.LAST_SUCCESSFUL_SYNC_AT] = timestamp }
    }
}
