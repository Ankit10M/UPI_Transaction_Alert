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
        if (!filter.isPaymentCandidate(packageName, rawText)) {
            return ProcessingResult.NOT_A_PAYMENT
        }

        val classification = classifier.classify(rawText)
        if (classification.type != TransactionType.RECEIVED || classification.status != TransactionStatus.SUCCESS) {
            return ProcessingResult.IGNORED
        }

        val parser = resolver.resolve(packageName, rawText)
        if (parser == null) {
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
                val language = settingsRepository.getLanguage()
                val rate = settingsRepository.getSpeechRate()
                val fellBackToEnglish = voiceEngine.prepare(language, rate)
                if (fellBackToEnglish && !settingsRepository.ttsFallbackOccurred.first()) {
                    settingsRepository.setTtsFallbackOccurred(true)
                }
                voiceEngine.speak(announcementTemplates.build(transaction, language))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Voice announcement failed (transaction already saved)", e)
        }

        return ProcessingResult.SAVED
    }

    private companion object {
        const val TAG = "ProcessTransaction"
    }
}