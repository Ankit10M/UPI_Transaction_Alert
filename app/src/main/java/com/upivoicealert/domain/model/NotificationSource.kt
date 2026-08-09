package com.upivoicealert.domain.model

import com.upivoicealert.utils.PackageNames

/**
 * Source of a captured payment notification (multi-source architecture,
 * prepared ahead of a future SMS/alternate-source parser — none added yet).
 *
 * Classified from the notification's package name by [forPackage]. Legacy rows
 * migrated before this field existed map to [UNKNOWN].
 */
enum class NotificationSource {
    /** A UPI app notification (GPay, PhonePe, Paytm, BHIM, Kotak 811, ...). */
    UPI_APP,

    /** An SMS / messaging-app notification (e.g. a bank credit alert). */
    SMS,

    /** Unknown / unclassified source (legacy rows, unrecognized packages). */
    UNKNOWN;

    companion object {
        private val SMS_PACKAGES = setOf(
            "com.google.android.apps.messaging", // Google Messages
            "com.android.mms",
            "com.android.messaging",
            "com.samsung.android.messaging"
        )

        fun forPackage(packageName: String): NotificationSource = when {
            packageName in PackageNames.ALL -> UPI_APP
            packageName in SMS_PACKAGES -> SMS
            else -> UNKNOWN
        }
    }
}
