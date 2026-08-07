package com.upivoicealert.parser

/**
 * Shared extraction helpers for the V1 parsers, built against the notification
 * formats confirmed during pipeline testing.
 *
 * Amount formats supported: "₹10.00", "₹ 500", "Rs 500", "Rs. 500", "INR 500".
 */
object AmountExtractor {

    private val AMOUNT_PATTERN = Regex(
        "(?:₹|Rs\\.?|INR)\\s*([0-9]+(?:\\.[0-9]{1,2})?)",
        RegexOption.IGNORE_CASE
    )

    fun extract(rawText: String): Double? =
        AMOUNT_PATTERN.find(rawText)?.groupValues?.get(1)?.toDoubleOrNull()

    fun containsAmount(rawText: String): Boolean = AMOUNT_PATTERN.containsMatchIn(rawText)
}

/**
 * The generic "received from" format (confirmed sample):
 *   "₹10.00 received from PRIYA BRIJESH MISHRA Amount credited to XX3434"
 * Sender is captured from "received from" up to the next known delimiter
 * (Amount/Via/On <date>/At <time>/Ref) or end of text.
 */
internal object ReceivedFromFormat {

    private val RECEIVED_FROM_PATTERN = Regex(
        "received\\s+from\\s+([A-Za-z][A-Za-z0-9 .'\\-]*?)" +
            "(?=\\s+(?:Amount|Via|On\\s+\\d|At\\s+\\d|Ref|$))",
        RegexOption.IGNORE_CASE
    )

    fun canParse(rawText: String): Boolean =
        RECEIVED_FROM_PATTERN.containsMatchIn(rawText) && AmountExtractor.containsAmount(rawText)

    fun extractSender(rawText: String): String? =
        RECEIVED_FROM_PATTERN.find(rawText)?.groupValues?.get(1)?.trim()
}
