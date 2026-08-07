package com.upivoicealert.parser.kotak

import com.upivoicealert.domain.model.TransactionType
import com.upivoicealert.parser.AmountExtractor
import com.upivoicealert.parser.ParsedTransaction
import com.upivoicealert.parser.ParserException
import com.upivoicealert.parser.ReceivedFromFormat
import com.upivoicealert.parser.TransactionParser
import com.upivoicealert.utils.PackageNames
import javax.inject.Inject

/**
 * Kotak 811 parser, version 1. Confirmed notification format:
 *   Title: "₹10.00 received from PRIYA BRIJESH MISHRA"
 *   Text:  "Amount credited to XX3434. Check out details."
 *
 * The pipeline passes the combined title + text into the parser. Extracts:
 * amount, sender, bank/app name (Kotak 811), transaction type (RECEIVED).
 */
class KotakParserV1 @Inject constructor() : TransactionParser {

    override val packageName: String = PackageNames.KOTAK
    override val version: String = "KotakParserV1"

    override fun canParse(rawText: String): Boolean = ReceivedFromFormat.canParse(rawText)

    override fun parse(rawText: String, postTime: Long): ParsedTransaction {
        val amount = AmountExtractor.extract(rawText)
            ?: throw ParserException("amount extraction failed")
        val sender = ReceivedFromFormat.extractSender(rawText)
            ?: throw ParserException("sender extraction failed")
        return ParsedTransaction(
            amount = amount,
            sender = sender,
            upiApp = PackageNames.labelFor(PackageNames.KOTAK),
            transactionId = null,
            postTime = postTime,
            rawNotification = rawText,
            transactionType = TransactionType.RECEIVED
        )
    }
}
