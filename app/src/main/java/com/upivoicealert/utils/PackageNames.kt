package com.upivoicealert.utils

/**
 * Whitelist of supported UPI app package names (CLAUDE.md Module 1).
 * NOTE: verify these against the currently installed app builds before shipping
 * (e.g. `adb shell pm list packages`). Package names occasionally change across
 * major app rebrands.
 */
object PackageNames {

    const val GPAY = "com.google.android.apps.nbu.paisa.user"
    const val PHONEPE = "com.phonepe.app"
    const val PAYTM = "net.one97.paytm"
    const val BHIM = "in.org.npci.upiapp"

    val ALL: Set<String> = setOf(GPAY, PHONEPE, PAYTM, BHIM)

    val LABELS: Set<String> = setOf(LABEL_GPAY, LABEL_PHONEPE, LABEL_PAYTM, LABEL_BHIM)

    private const val LABEL_GPAY = "Google Pay"
    private const val LABEL_PHONEPE = "PhonePe"
    private const val LABEL_PAYTM = "Paytm"
    private const val LABEL_BHIM = "BHIM"

    fun labelFor(packageName: String): String = when (packageName) {
        GPAY -> LABEL_GPAY
        PHONEPE -> LABEL_PHONEPE
        PAYTM -> LABEL_PAYTM
        BHIM -> LABEL_BHIM
        else -> packageName
    }
}