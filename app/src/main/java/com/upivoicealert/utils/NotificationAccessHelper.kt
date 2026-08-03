package com.upivoicealert.utils

import android.content.Context
import android.provider.Settings

object NotificationAccessHelper {

    /** True when the user has granted this app Notification Listener access. */
    fun isGranted(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return enabled.split(":")
            .any { it.startsWith("${context.packageName}/") }
    }
}