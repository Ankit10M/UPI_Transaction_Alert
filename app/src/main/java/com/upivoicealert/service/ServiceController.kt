package com.upivoicealert.service

import android.util.Log
import com.upivoicealert.domain.model.ServiceStatus
import com.upivoicealert.domain.repository.ServiceStateRepository
import com.upivoicealert.domain.repository.SettingsRepository
import com.upivoicealert.voice.VoiceAnnouncementEngine
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Single control surface between the UI and the notification service.
 *
 * The big START/STOP control on the Home screen is the ONLY place the service
 * state is changed: [start] / [stop] / [toggle] persist the run state via
 * [ServiceStateRepository] (DataStore), which
 * [com.upivoicealert.domain.usecases.ProcessTransactionUseCase] reads at
 * pipeline entry to gate processing. The UI only controls and observes this
 * controller — it never touches the pipeline directly.
 *
 * Every state change is logged under tag "SHOUTPAY_SERVICE_DEBUG" so the
 * expected flow can be verified in Logcat:
 *     Press START  -> SERVICE_STARTED log
 *     Press STOP   -> SERVICE_STOPPED log
 *
 * START also warms up the TTS engine so the first payment is never dropped to
 * the TTS-initialization race (the engine is created here, before any
 * transaction arrives).
 */
@Singleton
class ServiceController @Inject constructor(
    private val serviceStateRepository: ServiceStateRepository,
    private val settingsRepository: SettingsRepository,
    private val voiceEngine: VoiceAnnouncementEngine
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isRunning = MutableStateFlow(false)

    /** True when the voice-alert service is running (persisted across restarts). */
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    /** Voice announcement preference, surfaced for the Protection status card. */
    val voiceEnabled: StateFlow<Boolean> = settingsRepository.voiceEnabled
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), true)

    init {
        // Restore the persisted state so the UI reflects reality after restart.
        scope.launch {
            val persisted = serviceStateRepository.getStatus() == ServiceStatus.SERVICE_RUNNING
            _isRunning.value = persisted
            Log.i(TAG, "STATE_RESTORED isRunning=$persisted")
        }
    }

    fun start() = scope.launch {
        if (_isRunning.value) return@launch
        serviceStateRepository.setRunning(true)
        _isRunning.value = true
        Log.i(TAG, "SERVICE_STARTED monitoring=enabled — notifications will be processed and announced")

        // Warm up TTS so the first payment is announced reliably (never blocks the
        // save pipeline: failures are logged and swallowed here).
        try {
            val language = settingsRepository.getLanguage()
            val rate = settingsRepository.getSpeechRate()
            voiceEngine.prepare(language, rate)
            Log.i(TAG, "TTS_WARMUP triggered language=${language.name} rate=$rate")
        } catch (e: Exception) {
            Log.w(TAG, "TTS_WARMUP failed (announcements still attempted)", e)
        }
    }

    fun stop() = scope.launch {
        if (!_isRunning.value) return@launch
        serviceStateRepository.setRunning(false)
        _isRunning.value = false
        Log.i(TAG, "SERVICE_STOPPED monitoring=disabled — notifications ignored, battery saved")
    }

    fun toggle() = scope.launch {
        if (_isRunning.value) stop() else start()
    }

    private companion object {
        const val TAG = "SHOUTPAY_SERVICE_DEBUG"
    }
}
