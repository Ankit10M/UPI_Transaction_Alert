package com.upivoicealert.data.repository

import com.upivoicealert.data.datastore.SettingsDataStore
import com.upivoicealert.domain.model.VoiceLanguage
import com.upivoicealert.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: SettingsDataStore
) : SettingsRepository {

    override val voiceEnabled: Flow<Boolean> get() = dataStore.voiceEnabled

    override val language: Flow<VoiceLanguage> get() = dataStore.language

    override val speechRate: Flow<Float> get() = dataStore.speechRate

    override val debugModeEnabled: Flow<Boolean> get() = dataStore.debugModeEnabled

    override val hasAcceptedPrivacyDisclosure: Flow<Boolean> get() = dataStore.hasAcceptedPrivacyDisclosure

    override val ttsFallbackOccurred: Flow<Boolean> get() = dataStore.ttsFallbackOccurred

    override val mobileNumber: Flow<String> get() = dataStore.mobileNumber

    override val userName: Flow<String> get() = dataStore.userName

    override val monitoringEnabled: Flow<Boolean> get() = dataStore.monitoringEnabled

    override suspend fun setVoiceEnabled(enabled: Boolean) = dataStore.setVoiceEnabled(enabled)

    override suspend fun setLanguage(language: VoiceLanguage) = dataStore.setLanguage(language)

    override suspend fun setSpeechRate(rate: Float) = dataStore.setSpeechRate(rate)

    override suspend fun setDebugModeEnabled(enabled: Boolean) = dataStore.setDebugModeEnabled(enabled)

    override suspend fun setHasAcceptedPrivacyDisclosure(accepted: Boolean) =
        dataStore.setHasAcceptedPrivacyDisclosure(accepted)

    override suspend fun setTtsFallbackOccurred(occurred: Boolean) = dataStore.setTtsFallbackOccurred(occurred)

    override suspend fun setMobileNumber(number: String) = dataStore.setMobileNumber(number)

    override suspend fun setUserName(name: String) = dataStore.setUserName(name)

    override suspend fun setMonitoringEnabled(enabled: Boolean) = dataStore.setMonitoringEnabled(enabled)
}