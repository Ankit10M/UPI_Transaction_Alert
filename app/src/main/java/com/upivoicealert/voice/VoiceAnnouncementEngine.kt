package com.upivoicealert.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import com.upivoicealert.domain.model.VoiceLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline Android TextToSpeech wrapper (CLAUDE.md Module 3).
 *
 * Stream choice: STREAM_MUSIC is used so announcements ride the media volume.
 * CLAUDE.md Module 3 requires this choice to be tested and documented on real
 * devices — if STREAM_NOTIFICATION proves preferable on target devices, change
 * it here before `speak()`.
 */
@Singleton
class VoiceAnnouncementEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var tts: TextToSpeech? = null
    private val ready = AtomicBoolean(false)

    init {
        tts = TextToSpeech(context) { status ->
            ready.set(status == TextToSpeech.SUCCESS)
        }
    }

    /**
     * Applies speech rate and requested language. Returns true when the requested
     * language's voice pack is unavailable and the engine fell back to English.
     */
    @Synchronized
    fun prepare(language: VoiceLanguage, speechRate: Float): Boolean {
        val engine = tts ?: return true
        engine.setSpeechRate(speechRate)
        val locale = when (language) {
            VoiceLanguage.ENGLISH -> Locale.US
            VoiceLanguage.HINDI -> Locale("hi", "IN")
            VoiceLanguage.MARATHI -> Locale("mr", "IN")
        }
        val availability = engine.isLanguageAvailable(locale)
        if (availability == TextToSpeech.LANG_MISSING_DATA ||
            availability == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            engine.language = Locale.US
            return true
        }
        engine.language = locale
        return false
    }

    @Synchronized
    fun speak(text: String) {
        val engine = tts ?: return
        if (!ready.get()) return
        engine.speak(text, TextToSpeech.QUEUE_ADD, null, "upi_voice_alert")
    }

    @Synchronized
    fun shutdown() {
        tts?.shutdown()
        tts = null
    }
}