package com.upivoicealert.parser

import com.upivoicealert.utils.PackageNames
import javax.inject.Inject

sealed class ValidationResult {
    data class Valid(val transaction: ParsedTransaction) : ValidationResult()
    data class Invalid(val reason: String) : ValidationResult()
}

/**
 * Validation Layer (CLAUDE.md Module 2, Component 4). A failed parse is never
 * silently dropped — it is routed to the Unparsed Notification Queue with a reason.
 */
class TransactionValidator @Inject constructor() {

    fun validate(parsed: ParsedTransaction): ValidationResult {
        if (parsed.amount <= 0) {
            return ValidationResult.Invalid("validation failed: amount <= 0")
        }
        if (parsed.sender.isBlank()) {
            return ValidationResult.Invalid("validation failed: sender is empty")
        }
        if (parsed.upiApp !in PackageNames.LABELS) {
            return ValidationResult.Invalid("validation failed: unrecognized UPI app")
        }
        return ValidationResult.Valid(parsed)
    }
}