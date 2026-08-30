package com.upivoicealert.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.reconciliationDataStore by preferencesDataStore(name = "reconciliation_status")

@Singleton
class ReconciliationStatusStore @Inject constructor(
    @ApplicationContext private val context: Context
) : ReconciliationStatusRecorder {
    private object Keys {
        val LAST_CHECKED_AT = longPreferencesKey("last_checked_at")
        val LAST_REPAIRED_COUNT = intPreferencesKey("last_repaired_count")
    }

    val lastCheckedAt: Flow<Long?> = context.reconciliationDataStore.data.map {
        it[Keys.LAST_CHECKED_AT]?.takeIf { v -> v > 0 }
    }

    val lastRepairedCount: Flow<Int> = context.reconciliationDataStore.data.map {
        it[Keys.LAST_REPAIRED_COUNT] ?: 0
    }

    override suspend fun recordReconciliation(timestamp: Long, repairedCount: Int) {
        context.reconciliationDataStore.edit {
            it[Keys.LAST_CHECKED_AT] = timestamp
            it[Keys.LAST_REPAIRED_COUNT] = repairedCount
        }
    }
}
