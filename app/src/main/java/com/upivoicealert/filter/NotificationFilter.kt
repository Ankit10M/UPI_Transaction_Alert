package com.upivoicealert.filter

import android.util.Log
import javax.inject.Inject
import javax.inject.Named

/**
 * Source-agnostic notification candidate filter (updated MVP design).
 *
 * Old behavior: strict package whitelist (GPay / PhonePe / Paytm / BHIM) plus
 * promotional-keyword rejection. Real testing showed valid UPI payment
 * confirmations arriving from other sources (e.g. Kotak 811 banking app), so the
 * whitelist caused valid payments to be missed.
 *
 * New behavior:
 * 1. Rejects notifications from a configurable blocklist of obvious non-financial
 *    apps (WhatsApp, Instagram, YouTube, ...).
 * 2. Rejects known promotional noise (cashback/offer/...) via the configurable
 *    keyword resource.
 * 3. Passes only notifications carrying at least one financial signal keyword
 *    (received / credited / ₹ / UPI / amount / ...).
 *
 * No strict package whitelist — any package may carry a payment notification.
 * Intentionally conservative: prefers false pass-through over false-drop of a
 * real payment (CLAUDE.md Module 2, Component 1). Every decision is logged under
 * TAG "UPI_DEBUG" as FILTER_CHECK package=... reason=...
 */
class NotificationFilter @Inject constructor(
    @Named("filter_keywords") private val keywords: Set<String>,
    @Named("financial_signals") private val financialSignals: Set<String>,
    @Named("blocked_packages") private val blockedPackages: Set<String>
) {

    fun isPaymentCandidate(packageName: String, rawText: String): Boolean {
        val lower = rawText.lowercase()

        if (packageName in blockedPackages) {
            Log.i(TAG, "FILTER_CHECK package=$packageName reason=blocked package: $packageName")
            return false
        }

        for (promo in keywords) {
            if (lower.contains(promo)) {
                Log.i(TAG, "FILTER_CHECK package=$packageName reason=promotional keyword: $promo")
                return false
            }
        }

        val signal = matchedFinancialSignal(lower)
        if (signal != null) {
            Log.i(TAG, "FILTER_CHECK package=$packageName reason=financial keyword detected: $signal")
            return true
        }

        Log.i(TAG, "FILTER_CHECK package=$packageName reason=no financial signal")
        return false
    }

    private fun matchedFinancialSignal(lower: String): String? {
        for (signal in financialSignals) {
            if (signal != "rs" && lower.contains(signal)) return signal
        }
        // "rs" is handled separately (digit-adjacency regex) to avoid false positives.
        return if ("rs" in financialSignals && RS_PATTERN.containsMatchIn(lower)) "rs" else null
    }

    private companion object {
        const val TAG = "SHOUTPAY_NOTIFICATION_DEBUG"

        /**
         * "rs" is matched only when adjacent to digits (e.g. "Rs. 500", "500 rs",
         * "rs500") so it never false-positives inside words like "transfers".
         * Text is already lowercased before matching.
         */
        val RS_PATTERN = Regex("\\brs\\.?\\s*\\d+|\\d+\\.?\\s*rs\\b")
    }
}
