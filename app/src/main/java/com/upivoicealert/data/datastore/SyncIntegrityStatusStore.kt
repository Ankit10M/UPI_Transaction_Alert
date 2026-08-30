package com.upivoicealert.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.upivoicealert.domain.sync.SyncIntegrityResult
import com.upivoicealert.domain.sync.SyncIntegrityStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.syncIntegrityDataStore by preferencesDataStore(name = "sync_integrity_status")

/**
 * Persists merchant-safe audit metadata. Separate namespace from SyncStatusStore and ReconciliationStatusStore.
 * Survives app restart and process death via DataStore. Safe with concurrent access (DataStore edit is atomic).
 */
@Singleton
class SyncIntegrityStatusStore @Inject constructor(
    @ApplicationContext private val context: Context
) : SyncIntegrityStatusRecorder {

    private object Keys {
        val LAST_AUDIT_AT = longPreferencesKey("last_audit_at")
        val LAST_ISSUE_COUNT = intPreferencesKey("last_issue_count")
        val LAST_IS_HEALTHY = intPreferencesKey("last_is_healthy") // 1=healthy, 0=issues, absent=never checked
        val MISSING = intPreferencesKey("missing_queue_count")
        val ORPHANED = intPreferencesKey("orphaned_queue_count")
        val DUPLICATE = intPreferencesKey("duplicate_queue_count")
        val INVALID = intPreferencesKey("invalid_queue_count")
        val STALE = intPreferencesKey("stale_uploading_count")
    }

    val status: Flow<SyncIntegrityStatus> = context.syncIntegrityDataStore.data.map { prefs ->
        val auditAt = prefs[Keys.LAST_AUDIT_AT]?.takeIf { it > 0 }
        if (auditAt == null) {
            SyncIntegrityStatus.NEVER_CHECKED
        } else {
            val isHealthyInt = prefs[Keys.LAST_IS_HEALTHY]
            SyncIntegrityStatus(
                lastAuditAt = auditAt,
                lastIssueCount = prefs[Keys.LAST_ISSUE_COUNT] ?: 0,
                isHealthy = when (isHealthyInt) {
                    1 -> true
                    0 -> false
                    else -> null
                },
                missingQueueCount = prefs[Keys.MISSING] ?: 0,
                orphanedQueueCount = prefs[Keys.ORPHANED] ?: 0,
                duplicateQueueCount = prefs[Keys.DUPLICATE] ?: 0,
                invalidQueueCount = prefs[Keys.INVALID] ?: 0,
                staleUploadingCount = prefs[Keys.STALE] ?: 0
            )
        }
    }

    override suspend fun recordAudit(result: SyncIntegrityResult) {
        context.syncIntegrityDataStore.edit { prefs ->
            prefs[Keys.LAST_AUDIT_AT] = result.checkedAt
            prefs[Keys.LAST_ISSUE_COUNT] = result.totalIssues
            prefs[Keys.LAST_IS_HEALTHY] = if (result.isHealthy) 1 else 0
            prefs[Keys.MISSING] = result.missingQueueCount
            prefs[Keys.ORPHANED] = result.orphanedQueueCount
            prefs[Keys.DUPLICATE] = result.duplicateQueueCount
            prefs[Keys.INVALID] = result.invalidQueueCount
            prefs[Keys.STALE] = result.staleUploadingCount
        }
    }
}
