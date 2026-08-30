package com.upivoicealert.data.datastore

interface ReconciliationStatusRecorder {
    suspend fun recordReconciliation(timestamp: Long, repairedCount: Int)
}
