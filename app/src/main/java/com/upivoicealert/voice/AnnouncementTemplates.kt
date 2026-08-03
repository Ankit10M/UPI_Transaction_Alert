package com.upivoicealert.voice

import com.upivoicealert.domain.model.Transaction
import com.upivoicealert.domain.model.VoiceLanguage
import javax.inject.Inject

/**
 * Locale-keyed announcement phrase templates (CLAUDE.md Module 3). Stored here so
 * more languages can be added without touching business logic.
 */
class AnnouncementTemplates @Inject constructor(
    private val converter: AmountToWordsConverter
) {

    fun build(transaction: Transaction, language: VoiceLanguage): String {
        val amountWords = converter.convert(transaction.amount, language)
        return when (language) {
            VoiceLanguage.ENGLISH ->
                "Received $amountWords from ${transaction.sender} via ${transaction.upiApp}"
            VoiceLanguage.HINDI ->
                "$amountWords प्राप्त हुए ${transaction.sender} से ${transaction.upiApp} के माध्यम से"
            VoiceLanguage.MARATHI ->
                "$amountWords मिळाले ${transaction.sender} कडून ${transaction.upiApp} द्वारे"
        }
    }
}