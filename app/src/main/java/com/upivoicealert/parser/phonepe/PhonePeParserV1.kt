package com.upivoicealert.parser.phonepe

import com.upivoicealert.parser.ParsedTransaction
import com.upivoicealert.parser.ParserException
import com.upivoicealert.parser.TransactionParser
import com.upivoicealert.utils.PackageNames
import javax.inject.Inject

/**
 * PhonePe parser, version 1.
 * NOTE: patterns require real notification samples (CLAUDE.md Phase 2).
 */
class PhonePeParserV1 @Inject constructor() : TransactionParser {

    override val packageName: String = PackageNames.PHONEPE
    override val version: String = "PhonePeParserV1"

    override fun canParse(rawText: String): Boolean = false

    override fun parse(rawText: String, postTime: Long): ParsedTransaction {
        throw ParserException("PhonePeParserV1 pattern not defined; requires real notification samples")
    }
}