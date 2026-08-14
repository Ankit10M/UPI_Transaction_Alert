package com.upivoicealert.domain.usecases

import android.util.Log
import com.upivoicealert.domain.model.NotificationSource
import com.upivoicealert.domain.model.ServiceStatus
import com.upivoicealert.domain.model.Transaction
import com.upivoicealert.domain.model.TransactionStatus
import com.upivoicealert.domain.model.TransactionType
import com.upivoicealert.domain.model.UnparsedNotification
import com.upivoicealert.domain.model.VoiceLanguage
import com.upivoicealert.domain.repository.ServiceStateRepository
import com.upivoicealert.domain.repository.SettingsRepository
import com.upivoicealert.domain.repository.TransactionRepository
import com.upivoicealert.filter.NotificationFilter
import com.upivoicealert.filter.NotificationTextCleaner
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
    private val textCleaner: NotificationTextCleaner,
    private val filter: NotificationFilter,
    private val classifier: TransactionClassifier,
    private val resolver: ParserVersionResolver,
    private val validator: TransactionValidator,
    private val transactionRepository: TransactionRepository,
    private val serviceStateRepository: ServiceStateRepository,
    private val settingsRepository: SettingsRepository,
    private val announcementTemplates: AnnouncementTemplates,
    private val voiceEngine: VoiceAnnouncementEngine
) {

    suspend fun processNotification(
        packageName: String,
        rawText: String,
        postTime: Long,
        notificationKey: String? = null
    ): ProcessingResult {
        Log.i(TAG, "PROCESS_START package=$packageName postTime=$postTime rawText=$rawText")

        // Master service switch (Home screen START/STOP): when the service is
        // stopped nothing enters the pipeline — no save, no announcement. The
        // NotificationListenerService itself stays connected to the system.
        if (serviceStateRepository.getStatus() != ServiceStatus.SERVICE_RUNNING) {
            Log.i(TAG, "MONITORING_DISABLED package=$packageName (service stopped by user)")
            return ProcessingResult.IGNORED
        }

        // Normalize once at the pipeline entry: the filter, classifier, parsers,
        // stored raw text and exact-match dedup fallback all see clean text.
        val text = textCleaner.clean(rawText)
        Log.i(TAG, "CLEANED_TEXT package=$packageName text=$text")
        if (text.isBlank()) {
            Log.i(TAG, "FILTER_FAIL package=$packageName reason=empty after cleaning")
            return ProcessingResult.NOT_A_PAYMENT
        }

        if (!filter.isPaymentCandidate(packageName, text)) {
            Log.i(TAG, "FILTER_FAIL package=$packageName")
            return ProcessingResult.NOT_A_PAYMENT
        }
        Log.i(TAG, "FILTER_PASS package=$packageName")

        val classification = classifier.classify(text)
        Log.i(TAG, "CLASSIFICATION_RESULT package=$packageName type=${classification.type} status=${classification.status}")
        if (classification.type != TransactionType.RECEIVED || classification.status != TransactionStatus.SUCCESS) {
            return ProcessingResult.IGNORED
        }

        Log.i(TAG, "PARSER_SEARCH package=$packageName")
        val parser = resolver.resolve(packageName, text)
        if (parser == null) {
            Log.i(TAG, "PARSER_NOT_FOUND package=$packageName")
            transactionRepository.addUnparsedNotification(
                UnparsedNotification(
                    id = UUID.randomUUID().toString(),
                    packageName = packageName,
                    rawNotification = text,
                    failureReason = "no parser matched",
                    createdAt = postTime
                )
            )
            return ProcessingResult.IGNORED
        }
        Log.i(TAG, "PARSER_FOUND package=$packageName parser=${parser.version}")

        val parsed = try {
            parser.parse(text, postTime)
        } catch (e: Exception) {
            Log.w(TAG, "Parser ${parser.version} failed", e)
            transactionRepository.addUnparsedNotification(
                UnparsedNotification(
                    id = UUID.randomUUID().toString(),
                    packageName = packageName,
                    rawNotification = text,
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
                        rawNotification = text,
                        failureReason = validation.reason,
                        createdAt = postTime
                    )
                )
                ProcessingResult.IGNORED
            }
            is ValidationResult.Valid -> {
                // Attach multi-source metadata (schema v2) to the parsed transaction.
                // rawNotification stays the cleaned text (legacy semantic, unchanged);
                // originalNotificationText preserves the raw capture for future
                // reprocessing / cross-source dedup (e.g. a future SMS parser).
                val enriched = validation.transaction.toTransaction(parser.version).copy(
                    sourceType = NotificationSource.forPackage(packageName),
                    packageName = packageName,
                    notificationKey = notificationKey,
                    originalNotificationText = rawText,
                    cleanedNotificationText = text
                )
                process(enriched)
            }
        }
    }

    suspend fun process(transaction: Transaction): ProcessingResult {
        // Decide whether this transaction will be announced BEFORE persisting, so
        // the stored voiceAnnounced flag is accurate (schema v3). TTS failure must
        // never block the save pipeline (CLAUDE.md Section 8), so the flag records
        // the announcement decision, and the actual speak() is best-effort.
        //
        // BUGFIX (D.5): the mobile number is profile metadata only — it is NOT a
        // notification-text filter. UPI "received" notifications carry the sender's
        // name, never the receiver's own number, so gating announcements on the
        // configured number silently disabled voice for every user who added one.
        // Announce whenever voice is enabled.
        val shouldAnnounce = runCatching { settingsRepository.isVoiceEnabled() }.getOrDefault(false)
        val transactionWithVoice = transaction.copy(voiceAnnounced = shouldAnnounce)

        val inserted = transactionRepository.insertTransactionIfNotDuplicate(transactionWithVoice)
        if (!inserted) {
            Log.i(DUP_TAG, "SKIP_TRANSACTION id=${transaction.id} amount=${transaction.amount} sender=${transaction.sender} app=${transaction.upiApp} incomingRef=${transaction.transactionId ?: "<none>"} reason=duplicate")
            return ProcessingResult.DUPLICATE
        }

        if (transactionWithVoice.voiceAnnounced) {
            try {
                val language = settingsRepository.getLanguage()
                val rate = settingsRepository.getSpeechRate()
                val fellBackToEnglish = voiceEngine.prepare(language, rate)
                if (fellBackToEnglish && !settingsRepository.ttsFallbackOccurred.first()) {
                    settingsRepository.setTtsFallbackOccurred(true)
                }
                // If the selected language's voice pack is missing, the engine
                // fell back to English — build the announcement in English too,
                // otherwise an English voice is asked to read Devanagari text.
                val effectiveLanguage = if (fellBackToEnglish) VoiceLanguage.ENGLISH else language
                voiceEngine.speak(announcementTemplates.build(transactionWithVoice, effectiveLanguage))
            } catch (e: Exception) {
                Log.w(TAG, "Voice announcement failed (transaction already saved)", e)
            }
        } else {
            Log.d(TAG, "Skipping announcement: voice disabled")
        }

        return ProcessingResult.SAVED
    }

    private companion object {
        const val TAG = "SHOUTPAY_NOTIFICATION_DEBUG"
        const val DUP_TAG = "SHOUTPAY_NOTIFICATION_DEBUG"
    }
}