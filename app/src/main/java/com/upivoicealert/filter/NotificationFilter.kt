package com.upivoicealert.filter

import com.upivoicealert.utils.PackageNames
import javax.inject.Inject

/**
 * Removes notifications that cannot be payment-success candidates: unknown packages
 * and notifications matching known promotional keywords. The keyword list is a
 * configurable resource, extendable without code changes (CLAUDE.md Module 2,
 * Component 1). Intentionally conservative: prefers false pass-through over
 * false-drop of a real payment.
 */
class NotificationFilter @Inject constructor(
    private val keywords: Set<String>
) {

    fun isPaymentCandidate(packageName: String, rawText: String): Boolean {
        if (packageName !in PackageNames.ALL) return false
        val lower = rawText.lowercase()
        return keywords.none { lower.contains(it) }
    }
}