package com.upivoicealert

import com.upivoicealert.domain.model.VoiceLanguage
import com.upivoicealert.voice.AmountToWordsConverter
import org.junit.Assert.assertEquals
import org.junit.Test

class AmountToWordsConverterTest {

    private val converter = AmountToWordsConverter()

    @Test
    fun `converts whole rupees to English words`() {
        assertEquals("five hundred rupees", converter.convert(500.0, VoiceLanguage.ENGLISH))
    }

    @Test
    fun `converts small amounts to English words`() {
        assertEquals("ten rupees", converter.convert(10.0, VoiceLanguage.ENGLISH))
        assertEquals("twenty rupees", converter.convert(20.0, VoiceLanguage.ENGLISH))
    }

    @Test
    fun `uses Indian numbering for larger amounts`() {
        assertEquals("one lakh rupees", converter.convert(100000.0, VoiceLanguage.ENGLISH))
        assertEquals("one crore rupees", converter.convert(10000000.0, VoiceLanguage.ENGLISH))
    }

    @Test
    fun `converts paise suffix when present`() {
        assertEquals("five rupees and fifty paise", converter.convert(5.5, VoiceLanguage.ENGLISH))
    }

    @Test
    fun `converts whole rupees to Hindi words`() {
        assertEquals("पाँच सौ रुपये", converter.convert(500.0, VoiceLanguage.HINDI))
    }

    @Test
    fun `converts Hindi lakh and crore`() {
        assertEquals("एक लाख रुपये", converter.convert(100000.0, VoiceLanguage.HINDI))
        assertEquals("एक करोड़ रुपये", converter.convert(10000000.0, VoiceLanguage.HINDI))
    }

    @Test
    fun `zero amount`() {
        assertEquals("zero rupees", converter.convert(0.0, VoiceLanguage.ENGLISH))
    }
}