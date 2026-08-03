package com.upivoicealert.parser

interface TransactionParser {

    /** Package this parser handles, e.g. a UPI app package name. */
    val packageName: String

    /** Stable version identifier, e.g. "PhonePeParserV1". Stored per transaction. */
    val version: String

    /** Quick pattern check used by the resolver to pick the matching parser version. */
    fun canParse(rawText: String): Boolean

    /**
     * Extracts amount, sender, UPI provider, reference ID and post time from the
     * notification text. Throws [ParserException] when extraction fails.
     */
    fun parse(rawText: String, postTime: Long): ParsedTransaction
}

class ParserException(message: String) : Exception(message)