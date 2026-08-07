package com.upivoicealert.parser.generic

import com.upivoicealert.domain.model.TransactionType
import com.upivoicealert.parser.AmountExtractor
import com.upivoicealert.parser.ParsedTransaction
import com.upivoicealert.parser.ParserException
import com.upivoicealert.parser.ReceivedFromFormat
import com.upivoicealert.parser.TransactionParser
import com.upivoicealert.utils.PackageNames
import javax.inject.Inject

/**
 * Package-agnostic parser for the generic received-payment format (confirmed
 * sample): "₹10.00 received from PRIYA BRIJESH MISHRA Amount credited to XX3434".
 *
 * Extracts: amount, sender, transaction type (RECEIVED). The app label is filled
 * in by the pipeline from the notification's package name; the parser itself is
 * not bound to a specific UPI app, so it is registered under [PackageNames.GENERIC]
 * and used by [com.upivoicealert.parser.ParserVersionResolver] as the fallback
 * when no package-specific parser matches.
 */
class GenericReceivedParserV1 @Inject constructor() : TransactionParser {

    override val packageName: String = PackageNames.GENERIC
    override val version: String = "GenericReceivedParserV1"

    override fun canParse(rawText: String): Boolean = ReceivedFromFormat.canParse(rawText)

    override fun parse(rawText: String, postTime: Long): ParsedTransaction {
        val amount = AmountExtractor.extract(rawText)
            ?: throw ParserException("amount extraction failed")
        val sender = ReceivedFromFormat.extractSender(rawText)
            ?: throw ParserException("sender extraction failed")
        return ParsedTransaction(
            amount = amount,
            sender = sender,
            upiApp = "",
            transactionId = null,
            postTime = postTime,
            rawNotification = rawText,
            transactionType = TransactionType.RECEIVED
        )
    }
}
