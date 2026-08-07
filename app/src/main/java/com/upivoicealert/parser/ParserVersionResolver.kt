package com.upivoicealert.parser

import com.upivoicealert.utils.PackageNames
import javax.inject.Inject

/**
 * Given a package name, tries the registered parser versions for that app
 * (most-recent-first) and returns the first one whose pattern matches the text.
 * If no package-specific parser matches, falls back to package-agnostic parsers
 * registered under [PackageNames.GENERIC] (e.g. the generic "received from"
 * format used by banking apps not in the whitelist).
 * (CLAUDE.md Module 2, Component 3.)
 */
class ParserVersionResolver @Inject constructor(
    parsers: List<@JvmSuppressWildcards TransactionParser>
) {

    private val parsersByPackage: Map<String, List<TransactionParser>> =
        parsers.groupBy { it.packageName }

    private val genericParsers: List<TransactionParser> =
        parsersByPackage[PackageNames.GENERIC] ?: emptyList()

    fun resolve(packageName: String, rawText: String): TransactionParser? {
        parsersByPackage[packageName]?.firstOrNull { it.canParse(rawText) }?.let { return it }
        // Package-agnostic fallback for the confirmed generic received format.
        return genericParsers.firstOrNull { it.canParse(rawText) }
    }
}