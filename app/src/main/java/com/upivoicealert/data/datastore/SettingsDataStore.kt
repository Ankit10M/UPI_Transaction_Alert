package com.upivoicealert.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.upivoicealert.domain.model.VoiceLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: androidx.datastore.core.DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object Keys {
        val VOICE_ENABLED = booleanPreferencesKey("voice_enabled")
        val LANGUAGE = stringPreferencesKey("language")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val DEBUG_MODE = booleanPreferencesKey("debug_mode")
        val HAS_ACCEPTED = booleanPreferencesKey("has_accepted_privacy_disclosure")
        val TTS_FALLBACK = booleanPreferencesKey("tts_fallback_occurred")
        val MOBILE_NUMBER = stringPreferencesKey("mobile_number")
        val USER_NAME = stringPreferencesKey("user_name")

        /**
         * Master switch for the voice-alert service. When false the listener
         * service stops processing notifications entirely (the big START/STOP
         * control on the Home screen). Defaults to true.
         */
        val MONITORING_ENABLED = booleanPreferencesKey("monitoring_enabled")
    }

    val voiceEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.VOICE_ENABLED] ?: true }
    val language: Flow<VoiceLanguage> = context.settingsDataStore.data.map {
        runCatching { VoiceLanguage.valueOf(it[Keys.LANGUAGE] ?: VoiceLanguage.ENGLISH.name) }
            .getOrDefault(VoiceLanguage.ENGLISH)
    }
    val speechRate: Flow<Float> = context.settingsDataStore.data.map { it[Keys.SPEECH_RATE] ?: 1.0f }
    val debugModeEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.DEBUG_MODE] ?: false }
    val hasAcceptedPrivacyDisclosure: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.HAS_ACCEPTED] ?: false }
    val ttsFallbackOccurred: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.TTS_FALLBACK] ?: false }
    val mobileNumber: Flow<String> = context.settingsDataStore.data.map { it[Keys.MOBILE_NUMBER] ?: "" }
    val userName: Flow<String> = context.settingsDataStore.data.map { it[Keys.USER_NAME] ?: "" }
    val monitoringEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.MONITORING_ENABLED] ?: true }

    suspend fun setVoiceEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.VOICE_ENABLED] = enabled }
    }

    suspend fun setLanguage(language: VoiceLanguage) {
        context.settingsDataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    suspend fun setSpeechRate(rate: Float) {
        context.settingsDataStore.edit { it[Keys.SPEECH_RATE] = rate }
    }

    suspend fun setDebugModeEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.DEBUG_MODE] = enabled }
    }

    suspend fun setHasAcceptedPrivacyDisclosure(accepted: Boolean) {
        context.settingsDataStore.edit { it[Keys.HAS_ACCEPTED] = accepted }
    }

    suspend fun setTtsFallbackOccurred(occurred: Boolean) {
        context.settingsDataStore.edit { it[Keys.TTS_FALLBACK] = occurred }
    }

    suspend fun setMobileNumber(number: String) {
        context.settingsDataStore.edit { it[Keys.MOBILE_NUMBER] = number }
    }

    suspend fun setUserName(name: String) {
        context.settingsDataStore.edit { it[Keys.USER_NAME] = name }
    }

    suspend fun setMonitoringEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.MONITORING_ENABLED] = enabled }
    }
}