package com.upivoicealert

import com.upivoicealert.domain.model.ParseStatus
import com.upivoicealert.domain.model.ServiceStatus
import com.upivoicealert.domain.model.Transaction
import com.upivoicealert.domain.model.TransactionStatus
import com.upivoicealert.domain.model.TransactionType
import com.upivoicealert.domain.model.UnparsedNotification
import com.upivoicealert.domain.model.VoiceLanguage
import com.upivoicealert.domain.repository.ServiceStateRepository
import com.upivoicealert.domain.repository.SettingsRepository
import com.upivoicealert.domain.repository.TransactionRepository
import com.upivoicealert.domain.usecases.ProcessTransactionUseCase
import com.upivoicealert.domain.usecases.ProcessingResult
import com.upivoicealert.filter.NotificationFilter
import com.upivoicealert.filter.NotificationTextCleaner
import com.upivoicealert.filter.TransactionClassifier
import com.upivoicealert.parser.ParserVersionResolver
import com.upivoicealert.parser.TransactionValidator
import com.upivoicealert.parser.generic.GenericReceivedParserV1
import com.upivoicealert.parser.gpay.GPayParserV1
import com.upivoicealert.parser.kotak.KotakParserV1
import com.upivoicealert.utils.PackageNames
import com.upivoicealert.voice.AmountToWordsConverter
import com.upivoicealert.voice.AnnouncementTemplates
import com.upivoicealert.voice.VoiceAnnouncement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BUG #1 verification — START/STOP gating.
 *
 * The NotificationListenerService stays connected to the system in BOTH states;
 * the big STOP control only flips the persisted run state, and the pipeline
 * gate in ProcessTransactionUseCase.processNotification() is what stops new
 * payments from being saved or announced. These tests pin that contract:
 *   - SERVICE_STOPPED  -> notification IGNORED, nothing saved, nothing spoken
 *   - SERVICE_RUNNING  -> payment saved + announced (TTS)
 * (TEST CASE 3.)
 */
class ProcessTransactionUseCaseServiceGateTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `stopped service ignores payment without saving or speaking`() = runTest {
        val repo = RecordingTransactionRepository()
        val useCase = useCase(ServiceStatus.SERVICE_STOPPED, repo)

        val result = useCase.processNotification(
            packageName = PackageNames.GPAY,
            rawText = "RAHUL paid you ₹10.00 UPI Ref 123456",
            postTime = now,
            notificationKey = "k1"
        )

        assertEquals(ProcessingResult.IGNORED, result)
        assertTrue("no insert while stopped", repo.inserted.isEmpty())
        assertTrue("no unparsed entry while stopped", repo.unparsed.isEmpty())
        assertTrue("no announcement while stopped", repo.voice.speakCalls.isEmpty())
    }

    @Test
    fun `stopped service ignores even a failed parser attempt`() = runTest {
        val repo = RecordingTransactionRepository()
        val useCase = useCase(ServiceStatus.SERVICE_STOPPED, repo)

        // A financial notification that would be processed when running must be
        // ignored outright when stopped (gate runs before filter/classifier).
        val result = useCase.processNotification(
            packageName = "com.some.bank",
            rawText = "₹10.00 received from PRIYA BRIJESH MISHRA Amount credited to XX3434",
            postTime = now
        )

        assertEquals(ProcessingResult.IGNORED, result)
        assertTrue(repo.inserted.isEmpty())
        assertTrue(repo.unparsed.isEmpty())
        assertTrue(repo.voice.speakCalls.isEmpty())
    }

    @Test
    fun `running service saves and announces payment`() = runTest {
        val repo = RecordingTransactionRepository()
        val useCase = useCase(ServiceStatus.SERVICE_RUNNING, repo)

        val result = useCase.processNotification(
            packageName = PackageNames.GPAY,
            rawText = "RAHUL paid you ₹10.00 UPI Ref 123456",
            postTime = now,
            notificationKey = "k1"
        )

        assertEquals(ProcessingResult.SAVED, result)
        assertEquals(1, repo.inserted.size)
        val saved = repo.inserted.single()
        assertEquals(10.0, saved.amount, 0.001)
        assertEquals("RAHUL", saved.sender)
        assertEquals("123456", saved.transactionId)
        assertEquals(1, repo.voice.speakCalls.size)
        assertTrue(repo.voice.speakCalls[0].contains("Received ten rupees"))
    }

    @Test
    fun `running service still filters non payments`() = runTest {
        val repo = RecordingTransactionRepository()
        val useCase = useCase(ServiceStatus.SERVICE_RUNNING, repo)

        val result = useCase.processNotification(
            packageName = "com.example.news",
            rawText = "Breaking news: weather update today",
            postTime = now
        )

        assertEquals(ProcessingResult.NOT_A_PAYMENT, result)
        assertTrue(repo.inserted.isEmpty())
        assertTrue(repo.voice.speakCalls.isEmpty())
    }

    private fun useCase(
        status: ServiceStatus,
        repo: RecordingTransactionRepository
    ): ProcessTransactionUseCase {
        val cleaner = NotificationTextCleaner(
            promoKeywords = setOf(
                "cashback", "offer", "reward", "scratch card", "reminder",
                "request money", "bill due", "win a", "lucky draw", "collect"
            )
        )
        val filter = NotificationFilter(
            keywords = setOf("cashback", "offer", "reward", "scratch card", "reminder", "request money", "bill due"),
            financialSignals = setOf(
                "received", "credited", "credit", "deposited", "transaction", "upi",
                "inr", "₹", "rs", "amount", "payment", "bank", "account", "balance"
            ),
            blockedPackages = setOf("com.whatsapp", "com.instagram.android", "com.google.android.youtube")
        )
        val resolver = ParserVersionResolver(
            listOf(GPayParserV1(), KotakParserV1(), GenericReceivedParserV1())
        )
        return ProcessTransactionUseCase(
            textCleaner = cleaner,
            filter = filter,
            classifier = TransactionClassifier(),
            resolver = resolver,
            validator = TransactionValidator(),
            transactionRepository = repo,
            serviceStateRepository = MutableServiceStateRepository(status),
            settingsRepository = FakeSettingsRepository(),
            announcementTemplates = AnnouncementTemplates(AmountToWordsConverter()),
            voiceEngine = repo.voice
        )
    }
}

/** Records pipeline side-effects (inserts, unparsed entries, TTS calls). */
private class RecordingTransactionRepository : TransactionRepository {

    val inserted = mutableListOf<Transaction>()
    val unparsed = mutableListOf<UnparsedNotification>()
    val voice = RecordingVoice()

    override fun observeTransactions(): Flow<List<Transaction>> = flowOf(emptyList())
    override fun observeReceivedSuccess(): Flow<List<Transaction>> = flowOf(emptyList())
    override fun observeReceivedSuccessSince(since: Long): Flow<List<Transaction>> = flowOf(emptyList())
    override fun observeCount(): Flow<Int> = flowOf(0)
    override fun observeCountSince(since: Long): Flow<Int> = flowOf(0)
    override fun observeLatest(): Flow<Transaction?> = flowOf(null)

    override suspend fun insertTransactionIfNotDuplicate(transaction: Transaction): Boolean {
        inserted.add(transaction)
        return true
    }

    override suspend fun isDuplicate(transaction: Transaction): Boolean = false
    override suspend fun addUnparsedNotification(notification: UnparsedNotification) {
        unparsed.add(notification)
    }

    override fun observeUnparsedNotifications(): Flow<List<UnparsedNotification>> = flowOf(emptyList())
    override fun observeUnparsedCountSince(since: Long): Flow<Int> = flowOf(0)
    override suspend fun markVoiceAnnounced(id: String) = Unit
    override suspend fun getUnparsedNotifications(): List<UnparsedNotification> = emptyList()
    override suspend fun deleteUnparsedNotification(id: String) = Unit
    override suspend fun clearUnparsedNotifications() = Unit
    override suspend fun deleteUnparsedOlderThan(before: Long) = Unit
    override suspend fun clearAllData() = Unit
}

private class RecordingVoice : VoiceAnnouncement {
    val speakCalls = mutableListOf<String>()
    override fun prepare(language: VoiceLanguage, speechRate: Float): Boolean = false
    override fun speak(text: String) {
        speakCalls.add(text)
    }
}

private class MutableServiceStateRepository(initial: ServiceStatus) : ServiceStateRepository {
    private val status = MutableStateFlow(initial)
    override fun observeStatus(): Flow<ServiceStatus> = status
    override suspend fun setRunning(running: Boolean) {
        status.value = if (running) ServiceStatus.SERVICE_RUNNING else ServiceStatus.SERVICE_STOPPED
    }
}

private class FakeSettingsRepository : SettingsRepository {

    override val voiceEnabled: Flow<Boolean> = flowOf(true)
    override val language: Flow<VoiceLanguage> = flowOf(VoiceLanguage.ENGLISH)
    override val speechRate: Flow<Float> = flowOf(1.0f)
    override val debugModeEnabled: Flow<Boolean> = flowOf(false)
    override val hasAcceptedPrivacyDisclosure: Flow<Boolean> = flowOf(true)
    override val ttsFallbackOccurred: Flow<Boolean> = flowOf(false)
    override val mobileNumber: Flow<String> = flowOf("")
    override val userName: Flow<String> = flowOf("")
    override val monitoringEnabled: Flow<Boolean> = flowOf(true)

    override suspend fun setVoiceEnabled(enabled: Boolean) = Unit
    override suspend fun setLanguage(language: VoiceLanguage) = Unit
    override suspend fun setSpeechRate(rate: Float) = Unit
    override suspend fun setDebugModeEnabled(enabled: Boolean) = Unit
    override suspend fun setHasAcceptedPrivacyDisclosure(accepted: Boolean) = Unit
    override suspend fun setTtsFallbackOccurred(occurred: Boolean) = Unit
    override suspend fun setMobileNumber(number: String) = Unit
    override suspend fun setUserName(name: String) = Unit
    override suspend fun setMonitoringEnabled(enabled: Boolean) = Unit
}