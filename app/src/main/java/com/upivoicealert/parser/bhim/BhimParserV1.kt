package com.upivoicealert.parser.bhim

import com.upivoicealert.parser.ParsedTransaction
import com.upivoicealert.parser.ParserException
import com.upivoicealert.parser.TransactionParser
import com.upivoicealert.utils.PackageNames
import javax.inject.Inject

/**
 * BHIM parser, version 1.
 * NOTE: patterns require real notification samples (CLAUDE.md Phase 2).
 */
class BhimParserV1 @Inject constructor() : TransactionParser {

    override val packageName: String = PackageNames.BHIM
    override val version: String = "BhimParserV1"

    override fun canParse(rawText: String): Boolean = false

    override fun parse(rawText: String, postTime: Long): ParsedTransaction {
        throw ParserException("BhimParserV1 pattern not defined; requires real notification samples")
    }
}