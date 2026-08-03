package com.upivoicealert.voice

import com.upivoicealert.domain.model.VoiceLanguage
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Converts an amount (in rupees) to spoken word form for the supported languages,
 * using Indian numbering (crore / lakh / thousand). Locale-aware, kept pure for
 * unit testing (CLAUDE.md Module 3).
 */
class AmountToWordsConverter @Inject constructor() {

    fun convert(amount: Double, language: VoiceLanguage): String {
        val rupees = amount.toLong()
        val paise = ((amount - rupees) * 100).roundToInt()
        val whole = when (language) {
            VoiceLanguage.ENGLISH -> numberToWordsEnglish(rupees)
            VoiceLanguage.HINDI -> numberToWordsHindi(rupees)
            VoiceLanguage.MARATHI -> numberToWordsMarathi(rupees)
        }
        return when (language) {
            VoiceLanguage.ENGLISH ->
                if (paise > 0) "$whole rupees and $paise paise" else "$whole rupees"
            VoiceLanguage.HINDI ->
                if (paise > 0) "$whole रुपये और $paise पैसे" else "$whole रुपये"
            VoiceLanguage.MARATHI ->
                if (paise > 0) "$whole रुपये आणि $paise पैसे" else "$whole रुपये"
        }
    }

    fun numberToWordsEnglish(n: Long): String {
        if (n == 0L) return "zero"
        var num = n
        val crore = num / 10_000_000L; num %= 10_000_000L
        val lakh = num / 100_000L; num %= 100_000L
        val thousand = num / 1_000L; num %= 1_000L

        val parts = mutableListOf<String>()
        if (crore > 0) parts.add("${threeDigitsEnglish(crore.toInt())} crore")
        if (lakh > 0) parts.add("${threeDigitsEnglish(lakh.toInt())} lakh")
        if (thousand > 0) parts.add("${threeDigitsEnglish(thousand.toInt())} thousand")
        if (num > 0) parts.add(threeDigitsEnglish(num.toInt()))
        return parts.joinToString(" ")
    }

    fun numberToWordsHindi(n: Long): String {
        if (n == 0L) return "शून्य"
        var num = n
        val crore = num / 10_000_000L; num %= 10_000_000L
        val lakh = num / 100_000L; num %= 100_000L
        val thousand = num / 1_000L; num %= 1_000L

        val parts = mutableListOf<String>()
        if (crore > 0) parts.add("${threeDigitsHindi(crore.toInt())} करोड़")
        if (lakh > 0) parts.add("${threeDigitsHindi(lakh.toInt())} लाख")
        if (thousand > 0) parts.add("${threeDigitsHindi(thousand.toInt())} हज़ार")
        if (num > 0) parts.add(threeDigitsHindi(num.toInt()))
        return parts.joinToString(" ")
    }

    fun numberToWordsMarathi(n: Long): String {
        if (n == 0L) return "शून्य"
        var num = n
        val crore = num / 10_000_000L; num %= 10_000_000L
        val lakh = num / 100_000L; num %= 100_000L
        val thousand = num / 1_000L; num %= 1_000L

        val parts = mutableListOf<String>()
        if (crore > 0) parts.add("${threeDigitsMarathi(crore.toInt())} कोटी")
        if (lakh > 0) parts.add("${threeDigitsMarathi(lakh.toInt())} लाख")
        if (thousand > 0) parts.add("${threeDigitsMarathi(thousand.toInt())} हजार")
        if (num > 0) parts.add(threeDigitsMarathi(num.toInt()))
        return parts.joinToString(" ")
    }

    private fun threeDigitsEnglish(n: Int): String {
        val hundreds = n / 100
        val rest = n % 100
        val builder = StringBuilder()
        if (hundreds > 0) {
            builder.append(englishBelowTwenty[hundreds]).append(" hundred")
        }
        if (rest > 0) {
            if (builder.isNotEmpty()) builder.append(" ")
            builder.append(englishTwoDigits(rest))
        }
        return builder.toString()
    }

    private fun englishTwoDigits(n: Int): String = when {
        n < 20 -> englishBelowTwenty[n]
        n % 10 == 0 -> englishTens[n / 10]
        else -> "${englishTens[n / 10]}-${englishBelowTwenty[n % 10]}"
    }

    private fun threeDigitsHindi(n: Int): String {
        val hundreds = n / 100
        val rest = n % 100
        val builder = StringBuilder()
        if (hundreds > 0) {
            builder.append(hindiBelowTwenty[hundreds]).append(" सौ")
        }
        if (rest > 0) {
            if (builder.isNotEmpty()) builder.append(" ")
            builder.append(hindiTwoDigits(rest))
        }
        return builder.toString()
    }

    private fun hindiTwoDigits(n: Int): String = when {
        n < 20 -> hindiBelowTwenty[n]
        n % 10 == 0 -> hindiTens[n / 10]
        else -> hindiTens[n / 10] + " " + hindiBelowTwenty[n % 10]
    }

    private fun threeDigitsMarathi(n: Int): String {
        val hundreds = n / 100
        val rest = n % 100
        val builder = StringBuilder()
        if (hundreds > 0) {
            builder.append(marathiBelowTwenty[hundreds]).append("शे")
        }
        if (rest > 0) {
            if (builder.isNotEmpty()) builder.append(" ")
            builder.append(marathiTwoDigits(rest))
        }
        return builder.toString()
    }

    private fun marathiTwoDigits(n: Int): String = when {
        n < 20 -> marathiBelowTwenty[n]
        n % 10 == 0 -> marathiTens[n / 10]
        else -> marathiTens[n / 10] + " " + marathiBelowTwenty[n % 10]
    }

    private val englishBelowTwenty = arrayOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
        "seventeen", "eighteen", "nineteen"
    )

    private val englishTens = arrayOf(
        "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"
    )

    private val hindiBelowTwenty = arrayOf(
        "शून्य", "एक", "दो", "तीन", "चार", "पाँच", "छह", "सात", "आठ", "नौ",
        "दस", "ग्यारह", "बारह", "तेरह", "चौदह", "पंद्रह", "सोलह", "सत्रह", "अठारह", "उन्नीस"
    )

    private val hindiTens = arrayOf(
        "", "", "बीस", "तीस", "चालीस", "पचास", "साठ", "सत्तर", "अस्सी", "नब्बे"
    )

    private val marathiBelowTwenty = arrayOf(
        "शून्य", "एक", "दोन", "तीन", "चार", "पाच", "सहा", "सात", "आठ", "नऊ",
        "दहा", "अकरा", "बारा", "तेरा", "चौदा", "पंधरा", "सोळा", "सतरा", "अठरा", "एकोणीस"
    )

    private val marathiTens = arrayOf(
        "", "", "वीस", "तीस", "चाळीस", "पन्नास", "साठ", "सत्तर", "ऐंशी", "नव्वद"
    )
}