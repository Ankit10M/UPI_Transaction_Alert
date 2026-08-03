package com.upivoicealert.parser

import javax.inject.Inject

/**
 * Given a package name, tries the registered parser versions for that app
 * (most-recent-first) and returns the first one whose pattern matches the text.
 * (CLAUDE.md Module 2, Component 3.)
 */
class ParserVersionResolver @Inject constructor(
    parsers: List<@JvmSuppressWildcards TransactionParser>
) {

    private val parsersByPackage: Map<String, List<TransactionParser>> =
        parsers.groupBy { it.packageName }

    fun resolve(packageName: String, rawText: String): TransactionParser? =
        parsersByPackage[packageName]?.firstOrNull { it.canParse(rawText) }
}