package com.upivoicealert.data.model

import com.upivoicealert.data.database.TransactionEntity
import com.upivoicealert.data.database.UnparsedNotificationEntity
import com.upivoicealert.domain.model.ParseStatus
import com.upivoicealert.domain.model.Transaction
import com.upivoicealert.domain.model.TransactionStatus
import com.upivoicealert.domain.model.TransactionType
import com.upivoicealert.domain.model.UnparsedNotification

fun TransactionEntity.toDomain(): Transaction = Transaction(
    id = id,
    amount = amount,
    sender = sender,
    upiApp = upiApp,
    transactionType = enumValueOf<TransactionType>(transactionType),
    status = enumValueOf<TransactionStatus>(status),
    transactionId = transactionId,
    rawNotification = rawNotification,
    parserVersion = parserVersion,
    parseStatus = enumValueOf<ParseStatus>(parseStatus),
    createdAt = createdAt
)

fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
    id = id,
    amount = amount,
    sender = sender,
    upiApp = upiApp,
    transactionType = transactionType.name,
    status = status.name,
    transactionId = transactionId,
    rawNotification = rawNotification,
    parserVersion = parserVersion,
    parseStatus = parseStatus.name,
    createdAt = createdAt
)

fun UnparsedNotificationEntity.toDomain(): UnparsedNotification = UnparsedNotification(
    id = id,
    packageName = packageName,
    rawNotification = rawNotification,
    failureReason = failureReason,
    createdAt = createdAt
)

fun UnparsedNotification.toEntity(): UnparsedNotificationEntity = UnparsedNotificationEntity(
    id = id,
    packageName = packageName,
    rawNotification = rawNotification,
    failureReason = failureReason,
    createdAt = createdAt
)