package com.upivoicealert.data.sync

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_diagnostic_events",
    indices = [
        Index(value = ["createdAt"]),
        Index(value = ["category"]),
        Index(value = ["eventType"])
    ]
)
data class SyncDiagnosticEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val eventType: String,
    val affectedCount: Int = 0,
    val createdAt: Long,
    val message: String? = null
)
