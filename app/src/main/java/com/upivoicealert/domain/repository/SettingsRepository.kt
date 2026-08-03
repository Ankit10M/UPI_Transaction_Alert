package com.upivoicealert.domain.repository

import com.upivoicealert.domain.model.VoiceLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

interface SettingsRepository {

    val voiceEnabled: Flow<Boolean>
    val language: Flow<VoiceLanguage>
    val speechRate: Flow<Float>
    val debugModeEnabled: Flow<Boolean>
    val hasAcceptedPrivacyDisclosure: Flow<Boolean>
    val ttsFallbackOccurred: Flow<Boolean>

    suspend fun isVoiceEnabled(): Boolean = voiceEnabled.first()

    suspend fun getLanguage(): VoiceLanguage = language.first()

    suspend fun getSpeechRate(): Float = speechRate.first()

    suspend fun setVoiceEnabled(enabled: Boolean)

    suspend fun setLanguage(language: VoiceLanguage)

    suspend fun setSpeechRate(rate: Float)

    suspend fun setDebugModeEnabled(enabled: Boolean)

    suspend fun setHasAcceptedPrivacyDisclosure(accepted: Boolean)

    suspend fun setTtsFallbackOccurred(occurred: Boolean)
}