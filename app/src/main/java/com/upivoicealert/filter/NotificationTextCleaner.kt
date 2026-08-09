package com.upivoicealert.filter

import javax.inject.Inject
import javax.inject.Named

/**
 * Cleans raw notification text before it enters the filter/classifier/parser
 * pipeline. The listener service concatenates title + text + bigText + textLines,
 * which produces real-world noise: duplicated bigText segments, system CTAs
 * ("Tap to view."), URLs and promotional trailing sentences.
 *
 * Cleaning is purely textual and order-independent enough to be idempotent:
 * `clean(clean(x)) == clean(x)`. It never removes currency amounts, sender names
 * or transaction evidence — the filter and classifier remain the authoritative
 * gates, and a deliberately conservative promo policy keeps this layer from ever
 * deleting a real payment (segments containing a transaction signal are kept;
 * the filter rejects them if they are still promotional).
 *
 * Applied once at the pipeline entry point ([com.upivoicealert.domain.usecases.ProcessTransactionUseCase]),
 * so the filter, classifier, parsers, stored raw text and the exact-match dedup
 * fallback all see the same normalized text.
 */
class NotificationTextCleaner @Inject constructor(
    @Named("filter_keywords") private val promoKeywords: Set<String>
) {

    fun clean(raw: String): String {
        if (raw.isBlank()) return ""

        // 1. Drop URLs (http(s), www, bare domains with common TLDs).
        var text = URL_PATTERN.replace(raw, " ")

        // 2. Collapse all whitespace runs to a single space.
        text = WHITESPACE_PATTERN.replace(text, " ").trim()

        // 3. Remove system CTA phrases ("Tap to view.", "View details", ...).
        //    Global + case-insensitive, so duplicated bigText CTAs ("Tap to view.
        //    Tap to view.") are all removed in one pass.
        for (cta in CTA_PHRASES) {
            text = ctaPattern(cta).replace(text, " ")
        }
        text = WHITESPACE_PATTERN.replace(text, " ").trim()

        // 4. Split into sentences (delimiter preserved in the segment, so
        //    "Rs. 500" and decimal amounts survive intact) and drop:
        //      - blank segments
        //      - consecutive duplicate segments (duplicated bigText content)
        //      - promotional segments that carry no transaction evidence
        val kept = mutableListOf<String>()
        var previousLower: String? = null
        for (segment in text.split(SENTENCE_SPLIT)) {
            val trimmed = segment.trim()
            if (trimmed.isBlank()) continue
            val lower = trimmed.lowercase()
            if (lower == previousLower) continue
            previousLower = lower
            if (isPromotional(trimmed)) continue
            kept.add(trimmed)
        }
        text = kept.joinToString(" ")

        // 5. Collapse immediately-repeated phrases (whole-text duplication, e.g.
        //    text == bigText with no sentence delimiters: "₹500 received ₹500 received").
        text = collapseRepeatedPhrases(text)

        // 6. Final normalization.
        text = WHITESPACE_PATTERN.replace(text, " ").trim()
        // Trailing sentence period only — never part of a decimal amount ("₹10.00"
        // ends in a digit, so only "₹10.00." loses its final ".").
        text = TRAILING_DOTS.replace(text, "")
        return text.trim()
    }

    /**
     * A segment is promotional when it contains a filter promo keyword but no
     * transaction evidence of its own. Conservative by design: a payment sentence
     * that merely mentions an offer ("You received ₹500 plus 10% reward") keeps its
     * signal and is preserved — the filter is the backstop that drops it.
     */
    private fun isPromotional(segment: String): Boolean {
        val lower = segment.lowercase()
        val hasPromoKeyword = promoKeywords.any { lower.contains(it) }
        if (!hasPromoKeyword) return false
        return TRANSACTION_SIGNALS.none { lower.contains(it) }
    }

    /**
     * Collapses runs of an immediately-repeated phrase into a single occurrence.
     * Token-based (word tokens, so currency symbols and decimals never interfere)
     * and loop-safe for 3+ copies ("X X X" -> "X").
     */
    private fun collapseRepeatedPhrases(text: String): String {
        val tokens = text.trim().split(WHITESPACE_PATTERN)
        if (tokens.size < 2) return text
        val result = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val remaining = tokens.size - i
            // Largest run length L such that the L tokens at i are immediately
            // followed by an identical run.
            var bestLen = 0
            val maxRun = remaining / 2
            for (len in maxRun downTo 1) {
                if (tokens.subList(i, i + len) == tokens.subList(i + len, i + 2 * len)) {
                    bestLen = len
                    break
                }
            }
            if (bestLen > 0) {
                val run = tokens.subList(i, i + bestLen)
                result.addAll(run)
                // Skip ALL consecutive copies of the run.
                var j = i + bestLen
                while (j + bestLen <= tokens.size && tokens.subList(j, j + bestLen) == run) {
                    j += bestLen
                }
                i = j
            } else {
                result.add(tokens[i])
                i += 1
            }
        }
        return result.joinToString(" ")
    }

    private fun ctaPattern(cta: String): Regex =
        Regex("""\b${Regex.escape(cta)}\b[.!]?\s*""", RegexOption.IGNORE_CASE)

    private companion object {
        /**
         * URLs: http(s)://..., www..., and bare domains with a common TLD that
         * carry a path ("paytm.com/offers"). A bare domain without a path ("Payment
         * of ₹500 at paytm.com") is treated as a merchant name and preserved.
         *
         * The bare-domain branch deliberately uses a single greedy character class
         * ("[A-Za-z0-9.-]+") instead of a "(?:x)+group" followed by a literal dot:
         * regex engines do not give back iterations of a +-quantified group, so the
         * grouped form can never match a single-dot domain like "paytm.com/offers".
         */
        val URL_PATTERN = Regex(
            """(?:https?://|www\.)\S+|""" +
                """\b[A-Za-z0-9.-]+\.[A-Za-z]{2,}/\S*"""
        )

        val WHITESPACE_PATTERN = Regex("""\s+""")

        /** Sentence split that keeps the ending punctuation attached to its sentence. */
        val SENTENCE_SPLIT = Regex("""(?<=[.!?])\s+""")

        val TRAILING_DOTS = Regex("""\.+$""")

        /**
         * System CTA / boilerplate phrases always stripped from the text.
         * Sentence-level "read more" style CTAs appear as trailing sentences.
         */
        val CTA_PHRASES = listOf(
            "tap to view", "tap for details", "tap for more",
            "view details", "view offers", "view all", "view more",
            "check out details", "check details", "click here", "click to view",
            "know more", "learn more", "see more", "see all", "read more",
            "explore now", "get started", "try now", "open app", "open the app",
            "download the app", "download our app", "install the app",
            "update the app", "update your app", "shop now", "order now",
            "book now", "apply now", "limited time", "today only", "hurry"
        )

        /**
         * Transaction evidence that protects a promotional-looking segment from
         * deletion (see [isPromotional]). Narrow on purpose: broad words like
         * "payment" or "upi" appear in promo copy too ("earn cashback on UPI
         * payments"), and the filter rejects those anyway — the cleaner should
         * strip them, not preserve them. "rs" is deliberately absent: substrings
         * of words ending in "rs" (offers, transfers, years) would otherwise count
         * as evidence; "₹"/"inr"/"amount" already cover currency-carrying text.
         */
        val TRANSACTION_SIGNALS = listOf(
            "received", "credited", "credit", "deposited", "paid you", "debited",
            "refund", "sent", "₹", "inr", "amount", "balance"
        )
    }
}
