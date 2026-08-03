package com.upivoicealert.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "unparsed_notifications")
data class UnparsedNotificationEntity(
    @PrimaryKey val id: String,
    val packageName: String,
    val rawNotification: String,
    val failureReason: String,
    val createdAt: Long
)