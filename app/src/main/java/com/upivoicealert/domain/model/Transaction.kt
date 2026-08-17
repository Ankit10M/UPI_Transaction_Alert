package com.upivoicealert.domain.model

data class Transaction(
    val id: String,
    val amount: Double,
    val sender: String,
    val upiApp: String,
    val transactionType: TransactionType,
    val status: TransactionStatus,
    val transactionId: String?,
    val rawNotification: String,
    val parserVersion: String,
    val parseStatus: ParseStatus,
    val createdAt: Long,
    // Multi-source metadata (schema v2). Defaults keep legacy construction paths
    // (ParsedTransaction.toTransaction, migrated rows) compiling and behaving
    // exactly as before; the pipeline enriches these via copy().
    val sourceType: NotificationSource = NotificationSource.UNKNOWN,
    val packageName: String = "",
    val notificationKey: String? = null,
    val originalNotificationText: String = "",
    val cleanedNotificationText: String = "",
    // Voice status (schema v3): true when the payment was announced by the TTS
    // engine at capture time (or replayed later from History).
    val voiceAnnounced: Boolean = false,
    // Cross-source dedup fingerprint (schema v4): computed at insert time by the
    // data layer (TransactionFingerprint) and persisted for the duplicate check.
    // Never constructed by parsers — default null keeps all pipeline paths unchanged.
    val dedupFingerprint: String? = null
)
