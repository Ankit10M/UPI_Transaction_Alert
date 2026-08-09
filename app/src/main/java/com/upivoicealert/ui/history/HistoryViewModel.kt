package com.upivoicealert.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.upivoicealert.domain.model.Transaction
import com.upivoicealert.domain.model.VoiceLanguage
import com.upivoicealert.domain.repository.SettingsRepository
import com.upivoicealert.domain.repository.TransactionRepository
import com.upivoicealert.domain.usecases.GetTransactionHistoryUseCase
import com.upivoicealert.voice.AnnouncementTemplates
import com.upivoicealert.voice.VoiceAnnouncementEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HistoryViewModel @Inject constructor(
    getTransactionHistoryUseCase: GetTransactionHistoryUseCase,
    private val transactionRepository: TransactionRepository,
    private val settingsRepository: SettingsRepository,
    private val voiceEngine: VoiceAnnouncementEngine,
    private val announcementTemplates: AnnouncementTemplates
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    /** Filter by UPI app label; null = All apps. */
    private val _appFilter = MutableStateFlow<String?>(null)
    val appFilter: StateFlow<String?> = _appFilter

    val transactions: StateFlow<List<Transaction>> = combine(
        getTransactionHistoryUseCase.receivedSuccess(),
        _searchQuery,
        _appFilter
    ) { all, query, app ->
        all.filter { txn ->
            val matchesQuery = query.isBlank() ||
                txn.sender.contains(query, ignoreCase = true) ||
                txn.upiApp.contains(query, ignoreCase = true)
            val matchesApp = app == null || txn.upiApp.equals(app, ignoreCase = true)
            matchesQuery && matchesApp
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Distinct UPI apps present in the history, for the filter chips. */
    val availableApps: StateFlow<List<String>> =
        getTransactionHistoryUseCase.receivedSuccess()
            .combine(_appFilter) { all, _ ->
                all.map { it.upiApp }.distinct().sorted()
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onAppFilterChange(app: String?) {
        _appFilter.value = app
    }

    /**
     * Replays a stored transaction through the existing TTS engine using the
     * user's current language + speech-rate settings, and marks it announced.
     */
    fun replayVoice(transaction: Transaction) = viewModelScope.launch {
        try {
            val language = settingsRepository.getLanguage()
            val rate = settingsRepository.getSpeechRate()
            val fellBackToEnglish = voiceEngine.prepare(language, rate)
            val effectiveLanguage = if (fellBackToEnglish) VoiceLanguage.ENGLISH else language
            voiceEngine.speak(announcementTemplates.build(transaction, effectiveLanguage))
            if (!transaction.voiceAnnounced) {
                transactionRepository.markVoiceAnnounced(transaction.id)
            }
        } catch (e: Exception) {
            // TTS failures must never crash the UI (CLAUDE.md Section 8).
        }
    }
}
