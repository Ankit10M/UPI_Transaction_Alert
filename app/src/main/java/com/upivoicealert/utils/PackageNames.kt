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

    /** Kotak 811 banking app — confirmed source of received-payment notifications. */
    const val KOTAK = "com.kotak811mobilebankingapp"

    /**
     * Sentinel package for package-agnostic parsers. The resolver falls back to
     * these when no package-specific parser matches (e.g. any bank posting the
     * generic "₹X received from Y" format).
     */
    const val GENERIC = "*"

    val ALL: Set<String> = setOf(GPAY, PHONEPE, PAYTM, BHIM, KOTAK)

    private const val LABEL_GPAY = "Google Pay"
    private const val LABEL_PHONEPE = "PhonePe"
    private const val LABEL_PAYTM = "Paytm"
    private const val LABEL_BHIM = "BHIM"
    private const val LABEL_KOTAK = "Kotak 811"

    fun labelFor(packageName: String): String = when (packageName) {
        GPAY -> LABEL_GPAY
        PHONEPE -> LABEL_PHONEPE
        PAYTM -> LABEL_PAYTM
        BHIM -> LABEL_BHIM
        KOTAK -> LABEL_KOTAK
        else -> packageName
    }
}