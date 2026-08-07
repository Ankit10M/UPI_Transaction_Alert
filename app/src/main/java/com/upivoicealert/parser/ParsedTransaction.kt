package com.upivoicealert.parser

import com.upivoicealert.domain.model.Transaction
import com.upivoicealert.domain.model.TransactionStatus
import com.upivoicealert.domain.model.TransactionType
import com.upivoicealert.domain.model.ParseStatus
import java.util.UUID

data class ParsedTransaction(
    val amount: Double,
    val sender: String,
    val upiApp: String,
    val transactionId: String?,
    val postTime: Long,
    val rawNotification: String,
    /** Extracted by the parser; defaults to RECEIVED for received-payment parsers. */
    val transactionType: TransactionType = TransactionType.RECEIVED
)

fun ParsedTransaction.toTransaction(parserVersion: String): Transaction = Transaction(
    id = UUID.randomUUID().toString(),
    amount = amount,
    sender = sender.trim(),
    upiApp = upiApp,
    transactionType = transactionType,
    status = TransactionStatus.SUCCESS,
    transactionId = transactionId,
    rawNotification = rawNotification,
    parserVersion = parserVersion,
    parseStatus = ParseStatus.PARSED,
    createdAt = postTime
)