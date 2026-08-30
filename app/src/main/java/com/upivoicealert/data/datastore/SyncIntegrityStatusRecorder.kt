package com.upivoicealert.data.datastore

import com.upivoicealert.domain.sync.SyncIntegrityResult

interface SyncIntegrityStatusRecorder {
    suspend fun recordAudit(result: SyncIntegrityResult)
}
