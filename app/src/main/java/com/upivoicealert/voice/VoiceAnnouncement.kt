package com.upivoicealert.voice

import com.upivoicealert.domain.model.VoiceLanguage

/**
 * The subset of [VoiceAnnouncementEngine] consumed by the transaction pipeline.
 *
 * Extracted so [com.upivoicealert.domain.usecases.ProcessTransactionUseCase] can
 * be unit-tested on the JVM (the real engine constructs an Android TextToSpeech
 * and needs a Context). The production binding resolves to the same singleton
 * [VoiceAnnouncementEngine]; behavior is unchanged.
 */
interface VoiceAnnouncement {

    /**
     * Applies speech rate and requested language. Returns true when the requested
     * language's voice pack is unavailable and the engine fell back to English.
     */
    fun prepare(language: VoiceLanguage, speechRate: Float): Boolean

    /** Best-effort spoken announcement. Never blocks or throws on failure. */
    fun speak(text: String)
}