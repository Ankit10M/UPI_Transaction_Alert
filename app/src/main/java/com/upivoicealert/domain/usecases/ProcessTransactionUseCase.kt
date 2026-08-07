package com.upivoicealert.domain.usecases

import android.util.Log
import com.upivoicealert.domain.model.Transaction
import com.upivoicealert.domain.model.TransactionStatus
import com.upivoicealert.domain.model.TransactionType
import com.upivoicealert.domain.model.UnparsedNotification
import com.upivoicealert.domain.repository.SettingsRepository
import com.upivoicealert.domain.repository.TransactionRepository
import com.upivoicealert.filter.NotificationFilter
import com.upivoicealert.filter.TransactionClassifier
import com.upivoicealert.parser.ParserVersionResolver
import com.upivoicealert.parser.TransactionValidator
import com.upivoicealert.parser.ValidationResult
import com.upivoicealert.parser.toTransaction
import com.upivoicealert.utils.PackageNames
import com.upivoicealert.voice.AnnouncementTemplates
import com.upivoicealert.voice.VoiceAnnouncementEngine
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

enum class ProcessingResult {
    SAVED,
    DUPLICATE,
    IGNORED,
    NOT_A_PAYMENT
}

/**
 * Orchestrates the notification filter pipeline for a single raw notification
 * (filter -> classify -> resolve parser -> parse -> validate -> dedup -> save -> voice),
 * then handles dedup + persistence + voice announcement for an already-parsed transaction.
 */
@Singleton
class ProcessTransactionUseCase @Inject constructor(
    private val filter: NotificationFilter,
    private val classifier: TransactionClassifier,
    private val resolver: ParserVersionResolver,
    private val validator: TransactionValidator,
    private val transactionRepository: TransactionRepository,
    private val settingsRepository: SettingsRepository,
    private val announcementTemplates: AnnouncementTemplates,
    private val voiceEngine: VoiceAnnouncementEngine
) {

    suspend fun processNotification(packageName: String, rawText: String, postTime: Long): ProcessingResult {
        Log.i(TAG, "PROCESS_START package=$packageName postTime=$postTime text=$rawText")

        if (!filter.isPaymentCandidate(packageName, rawText)) {
            Log.i(TAG, "FILTER_FAIL package=$packageName")
            return ProcessingResult.NOT_A_PAYMENT
        }
        Log.i(TAG, "FILTER_PASS package=$packageName")

        val classification = classifier.classify(rawText)
        Log.i(TAG, "CLASSIFICATION_RESULT package=$packageName type=${classification.type} status=${classification.status}")
        if (classification.type != TransactionType.RECEIVED || classification.status != TransactionStatus.SUCCESS) {
            return ProcessingResult.IGNORED
        }

        Log.i(TAG, "PARSER_SEARCH package=$packageName")
        val parser = resolver.resolve(packageName, rawText)
        if (parser == null) {
            Log.i(TAG, "PARSER_NOT_FOUND package=$packageName")
            transactionRepository.addUnparsedNotification(
                UnparsedNotification(
                    id = UUID.randomUUID().toString(),
                    packageName = packageName,
                    rawNotification = rawText,
                    failureReason = "no parser matched",
                    createdAt = postTime
                )
            )
            return ProcessingResult.IGNORED
        }
        Log.i(TAG, "PARSER_FOUND package=$packageName parser=${parser.version}")

        val parsed = try {
            parser.parse(rawText, postTime)
        } catch (e: Exception) {
            Log.w(TAG, "Parser ${parser.version} failed", e)
            transactionRepository.addUnparsedNotification(
                UnparsedNotification(
                    id = UUID.randomUUID().toString(),
                    packageName = packageName,
                    rawNotification = rawText,
                    failureReason = "parse error: ${e.message ?: "unknown"}",
                    createdAt = postTime
                )
            )
            return ProcessingResult.IGNORED
        }

        val parsedWithAppLabel = parsed.copy(upiApp = PackageNames.labelFor(packageName))
        Log.i(TAG, "parsed amount=${parsedWithAppLabel.amount}")
        Log.i(TAG, "parsed sender=${parsedWithAppLabel.sender}")
        Log.i(TAG, "parsed app=${parsedWithAppLabel.upiApp}")
        return when (val validation = validator.validate(parsedWithAppLabel)) {
            is ValidationResult.Invalid -> {
                transactionRepository.addUnparsedNotification(
                    UnparsedNotification(
                        id = UUID.randomUUID().toString(),
                        packageName = packageName,
                        rawNotification = rawText,
                        failureReason = validation.reason,
                        createdAt = postTime
                    )
                )
                ProcessingResult.IGNORED
            }
            is ValidationResult.Valid -> {
                process(validation.transaction.toTransaction(parser.version))
            }
        }
    }

    suspend fun process(transaction: Transaction): ProcessingResult {
        val inserted = transactionRepository.insertTransactionIfNotDuplicate(transaction)
        if (!inserted) {
            return ProcessingResult.DUPLICATE
        }

        // TTS failure must never block the save pipeline (CLAUDE.md Section 8).
        try {
            if (settingsRepository.isVoiceEnabled()) {
                val configuredMobile = settingsRepository.getMobileNumber()
                if (configuredMobile.isBlank() || transaction.rawNotification.contains(configuredMobile)) {
                    val language = settingsRepository.getLanguage()
                    val rate = settingsRepository.getSpeechRate()
                    val fellBackToEnglish = voiceEngine.prepare(language, rate)
                    if (fellBackToEnglish && !settingsRepository.ttsFallbackOccurred.first()) {
                        settingsRepository.setTtsFallbackOccurred(true)
                    }
                    voiceEngine.speak(announcementTemplates.build(transaction, language))
                } else {
                    Log.d(TAG, "Skipping announcement: mobile number mismatch")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Voice announcement failed (transaction already saved)", e)
        }

        return ProcessingResult.SAVED
    }

    private companion object {
        const val TAG = "UPI_DEBUG"
    }
}