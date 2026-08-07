package com.upivoicealert.parser.gpay

import com.upivoicealert.domain.model.TransactionType
import com.upivoicealert.parser.AmountExtractor
import com.upivoicealert.parser.ParsedTransaction
import com.upivoicealert.parser.ParserException
import com.upivoicealert.parser.TransactionParser
import com.upivoicealert.utils.PackageNames
import javax.inject.Inject

/**
 * Google Pay parser, version 1. Confirmed notification format:
 *   "PRIYA BRIJESH MISHRA paid you ₹10.00"
 *
 * Extracts sender and amount. Transaction type is RECEIVED (the "paid you"
 * phrasing is a payment received). NOTE: patterns are built against the format
 * confirmed during pipeline testing; if GPay changes format, add a V2 parser
 * rather than rewriting this one (CLAUDE.md Module 2, Component 3).
 */
class GPayParserV1 @Inject constructor() : TransactionParser {

    override val packageName: String = PackageNames.GPAY
    override val version: String = "GPayParserV1"

    private val paidYouPattern = Regex(
        "(.+?)\\s+paid\\s+you\\s+(?:₹|Rs\\.?|INR)",
        RegexOption.IGNORE_CASE
    )

    override fun canParse(rawText: String): Boolean = paidYouPattern.containsMatchIn(rawText)

    override fun parse(rawText: String, postTime: Long): ParsedTransaction {
        val amount = AmountExtractor.extract(rawText)
            ?: throw ParserException("amount extraction failed")
        val sender = paidYouPattern.find(rawText)?.groupValues?.get(1)?.trim()
            ?: throw ParserException("sender extraction failed")
        return ParsedTransaction(
            amount = amount,
            sender = sender,
            upiApp = PackageNames.labelFor(PackageNames.GPAY),
            transactionId = null,
            postTime = postTime,
            rawNotification = rawText,
            transactionType = TransactionType.RECEIVED
        )
    }
}
