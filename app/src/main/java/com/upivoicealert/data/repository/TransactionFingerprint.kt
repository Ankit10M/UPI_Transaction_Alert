package com.upivoicealert.data.repository

import com.upivoicealert.domain.model.Transaction

/**
 * Secondary cross-source dedup identity (CLAUDE.md Module 4, priority 2) used
 * when a payment's UPI reference ID / UTR is not available in a notification
 * (or is missing from the already-stored row).
 *
 * The primary identity is the reference ID (globally unique per UPI payment).
 * The fingerprint exists to catch the SAME payment arriving from two sources
 * whose notifications do not both expose the reference (e.g. a Google Pay
 * notification and a bank "received from" notification for one payment).
 *
 * It deliberately does NOT include the package/source or the notification text:
 * those differ across sources for the SAME payment, so including them would
 * defeat the purpose. It is amount + normalized sender + transaction type, and
 * the time window is applied at query time (Constants.DEDUP_WINDOW_MS) — two
 * genuine same-amount payments from the same sender that fall outside the
 * window stay insertable.
 *
 * Returns null when no reliable fingerprint can be formed (blank sender), in
 * which case the caller falls back to the exact-text repost check.
 */
object TransactionFingerprint {

    private val NON_ALNUM_RUN = Regex("[^A-Z0-9]+")

    fun compute(transaction: Transaction): String? {
        val sender = normalizeSender(transaction.sender)
        if (sender.isBlank()) return null
        val amount = formatAmount(transaction.amount) ?: return null
        return "$amount|$sender|${transaction.transactionType.name}"
    }

    /** Uppercases, trims and collapses non-alphanumeric runs so "Priya  Mishra"
     *  and "priya-mishra" normalize to the same key while distinct names differ. */
    fun normalizeSender(sender: String): String =
        sender.trim().uppercase().replace(NON_ALNUM_RUN, " ").trim()

    private fun formatAmount(amount: Double): String? {
        if (amount <= 0 || amount.isNaN() || amount.isInfinite()) return null
        // 10.0 -> "10", 10.5 -> "10.5", 10.55 -> "10.55" (stored amounts are
        // <= 2 decimals, so Double.toString is stable and canonical).
        return if (amount == amount.toLong().toDouble()) {
            amount.toLong().toString()
        } else {
            amount.toString()
        }
    }
}