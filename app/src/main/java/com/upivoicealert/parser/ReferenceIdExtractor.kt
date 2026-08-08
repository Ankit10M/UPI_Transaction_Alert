package com.upivoicealert.parser

/**
 * Shared extraction helper for UPI reference IDs / UTR / transaction IDs from
 * notification text. Used by the V1 parsers so that deduplication can rely on
 * the reference ID (CLAUDE.md Module 4, priority 1) instead of a fuzzy
 * amount+sender match that wrongly blocks distinct same-amount payments.
 *
 * Recognized keyword prefixes (case-insensitive):
 *   "UPI Ref 1234...", "Ref: 1234...", "Ref No. 1234...", "Ref ID 1234...",
 *   "Reference 1234...", "UTR 1234...", "Txn ID 1234...", "Transaction ID 1234..."
 *
 * The captured token must contain at least 6 consecutive digits (optionally
 * with a few surrounding alphanumerics) — UPI refs / UTRs are digit-heavy, and
 * this guard rejects false positives such as "Refund" or "Ref Newsletter".
 */
object ReferenceIdExtractor {

    private val REFERENCE_PATTERN = Regex(
        "(?:UPI\\s+)?(?:Ref(?:erence)?\\s*(?:No\\.?|ID)?|UTR\\s*(?:No\\.?|ID)?|Txn(?:\\.?\\s*ID)?|Transaction(?:\\.?\\s*ID)?)\\s*[:#-]?\\s*([A-Z0-9]{0,4}[0-9]{6,}[A-Z0-9]{0,4})",
        RegexOption.IGNORE_CASE
    )

    fun extract(rawText: String): String? =
        REFERENCE_PATTERN.find(rawText)?.groupValues?.get(1)?.trim()
}
