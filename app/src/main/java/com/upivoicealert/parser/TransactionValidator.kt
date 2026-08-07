package com.upivoicealert.parser

import javax.inject.Inject

sealed class ValidationResult {
    data class Valid(val transaction: ParsedTransaction) : ValidationResult()
    data class Invalid(val reason: String) : ValidationResult()
}

/**
 * Validation Layer (CLAUDE.md Module 2, Component 4). A failed parse is never
 * silently dropped — it is routed to the Unparsed Notification Queue with a reason.
 *
 * The app-name check accepts any non-blank value (not just the known UPI app
 * labels): the filter pipeline is source-agnostic (confirmed flow — payments
 * arrive from any bank app, e.g. Kotak 811), so the label is derived from the
 * notification's package name and must not gate on a whitelist.
 */
class TransactionValidator @Inject constructor() {

    fun validate(parsed: ParsedTransaction): ValidationResult {
        if (parsed.amount <= 0) {
            return ValidationResult.Invalid("validation failed: amount <= 0")
        }
        if (parsed.sender.isBlank()) {
            return ValidationResult.Invalid("validation failed: sender is empty")
        }
        if (parsed.upiApp.isBlank()) {
            return ValidationResult.Invalid("validation failed: app name is empty")
        }
        return ValidationResult.Valid(parsed)
    }
}