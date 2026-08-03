package com.upivoicealert.parser.paytm

import com.upivoicealert.parser.ParsedTransaction
import com.upivoicealert.parser.ParserException
import com.upivoicealert.parser.TransactionParser
import com.upivoicealert.utils.PackageNames
import javax.inject.Inject

/**
 * Paytm parser, version 1.
 * NOTE: patterns require real notification samples (CLAUDE.md Phase 2).
 */
class PaytmParserV1 @Inject constructor() : TransactionParser {

    override val packageName: String = PackageNames.PAYTM
    override val version: String = "PaytmParserV1"

    override fun canParse(rawText: String): Boolean = false

    override fun parse(rawText: String, postTime: Long): ParsedTransaction {
        throw ParserException("PaytmParserV1 pattern not defined; requires real notification samples")
    }
}