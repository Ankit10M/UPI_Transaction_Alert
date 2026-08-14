package com.upivoicealert.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
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
 *
 * Debug: all locale/voice decisions are logged under tag "UPI_TTS_DEBUG".
 */
@Singleton
class VoiceAnnouncementEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var tts: TextToSpeech? = null
    private val ready = AtomicBoolean(false)
    private var activeLocale: Locale? = null

    init {
        tts = TextToSpeech(context) { status ->
            ready.set(status == TextToSpeech.SUCCESS)
            val engine = tts
            val voices = engine?.voices ?: emptyList()
            val voiceTags = voices.map { it.locale.toLanguageTag() }.distinct()
            val defaultVoice = engine?.defaultVoice?.locale?.toLanguageTag() ?: "<none>"
            Log.i(TAG, "TTS_INIT status=$status engine=${engine?.defaultEngine ?: "<unknown>"} defaultVoice=$defaultVoice")
            Log.i(TAG, "TTS_AVAILABLE_LANGUAGES count=${voiceTags.size} tags=$voiceTags")
        }
    }

    /**
     * Applies speech rate and requested language. Returns true when the requested
     * language's voice pack is unavailable and the engine fell back to English.
     *
     * Locale resolution order per language (first available wins):
     *   1. exact tag (en-IN / hi-IN / mr-IN)
     *   2. bare language tag (en / hi / mr)
     *   3. any installed voice whose language code matches (covers OEM engines
     *      that only register variant tags such as "hi-IN-x-hi-network")
     */
    @Synchronized
    fun prepare(language: VoiceLanguage, speechRate: Float): Boolean {
        val engine = tts
        if (engine == null || !ready.get()) {
            Log.w(TAG, "PREPARE_SKIPPED engineReady=${ready.get()} enginePresent=${engine != null} language=${language.name}")
            return false
        }

        engine.setSpeechRate(speechRate)
        val candidates = candidateLocales(language)
        Log.i(TAG, "SELECTED_LANGUAGE=${language.name} targetTags=${candidates.map { it.toLanguageTag() }}")

        for (locale in candidates) {
            val availability = engine.isLanguageAvailable(locale)
            Log.i(TAG, "LOCALE_CHECK locale=${locale.toLanguageTag()} availability=${availabilityLabel(availability)}")
            if (availability == TextToSpeech.LANG_MISSING_DATA ||
                availability == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                continue
            }
            // Call setLanguage() directly so the result code can be logged
            // (the Kotlin property setter `engine.language = locale` discards it).
            val setResult = engine.setLanguage(locale)
            Log.i(TAG, "SET_LANGUAGE locale=${locale.toLanguageTag()} result=${availabilityLabel(setResult)}")
            if (setResult == TextToSpeech.LANG_MISSING_DATA ||
                setResult == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                // isLanguageAvailable passed but setLanguage still failed; try next candidate.
                continue
            }
            activeLocale = locale
            return false
        }

        // Last resort: English voice, so announcements stay audible.
        val fallbackLocale = Locale.forLanguageTag("en-IN")
        val fallbackResult = engine.setLanguage(fallbackLocale)
        Log.w(TAG, "LANGUAGE_UNAVAILABLE language=${language.name} candidates=${candidates.map { it.toLanguageTag() }} falling back to English")
        Log.i(TAG, "SET_LANGUAGE_FALLBACK locale=${fallbackLocale.toLanguageTag()} result=${availabilityLabel(fallbackResult)}")
        activeLocale = fallbackLocale
        return true
    }

    @Synchronized
    fun speak(text: String) {
        val engine = tts ?: return
        if (!ready.get()) {
            Log.w(TAG, "SPEAK_SKIPPED engineNotReady")
            return
        }
        val currentLanguage = activeLocale?.toLanguageTag() ?: "<unknown>"
        val result = engine.speak(text, TextToSpeech.QUEUE_ADD, null, "upi_voice_alert")
        Log.i(TAG, "SPEAK_RESULT=${if (result == TextToSpeech.SUCCESS) "SUCCESS" else "ERROR($result)"} currentLocale=$currentLanguage text=$text")
    }

    @Synchronized
    fun shutdown() {
        tts?.shutdown()
        tts = null
    }

    private fun candidateLocales(language: VoiceLanguage): List<Locale> {
        val tag = when (language) {
            VoiceLanguage.ENGLISH -> "en-IN"
            VoiceLanguage.HINDI -> "hi-IN"
            VoiceLanguage.MARATHI -> "mr-IN"
        }
        val languageCode = tag.substringBefore("-")
        val candidates = mutableListOf<Locale>()
        candidates += Locale.forLanguageTag(tag)
        candidates += Locale.forLanguageTag(languageCode)
        tts?.voices
            ?.filter { it.locale.language == languageCode }
            ?.map { it.locale }
            ?.forEach { if (it !in candidates) candidates += it }
        return candidates.distinct()
    }

    private fun availabilityLabel(code: Int): String = when (code) {
        TextToSpeech.LANG_AVAILABLE -> "LANG_AVAILABLE"
        TextToSpeech.LANG_COUNTRY_AVAILABLE -> "LANG_COUNTRY_AVAILABLE"
        TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> "LANG_COUNTRY_VAR_AVAILABLE"
        TextToSpeech.LANG_MISSING_DATA -> "LANG_MISSING_DATA"
        TextToSpeech.LANG_NOT_SUPPORTED -> "LANG_NOT_SUPPORTED"
        else -> "UNKNOWN($code)"
    }

    private companion object {
        const val TAG = "SHOUTPAY_TTS_DEBUG"
    }
}
