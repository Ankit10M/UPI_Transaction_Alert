package com.upivoicealert.parser.gpay

import com.upivoicealert.parser.ParsedTransaction
import com.upivoicealert.parser.ParserException
import com.upivoicealert.parser.TransactionParser
import com.upivoicealert.utils.PackageNames
import javax.inject.Inject

/**
 * Google Pay parser, version 1.
 * NOTE: patterns require real notification samples (CLAUDE.md Phase 2). Do not
 * hardcode regex against guessed formats. Shipped as a non-matching stub so
 * un-collected formats land in the Unparsed Notification Queue rather than being
 * silently mis-parsed.
 */
class GPayParserV1 @Inject constructor() : TransactionParser {

    override val packageName: String = PackageNames.GPAY
    override val version: String = "GPayParserV1"

    override fun canParse(rawText: String): Boolean = false

    override fun parse(rawText: String, postTime: Long): ParsedTransaction {
        throw ParserException("GPayParserV1 pattern not defined; requires real notification samples")
    }
}