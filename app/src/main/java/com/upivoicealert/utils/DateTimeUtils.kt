package com.upivoicealert.utils

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateTimeUtils {

    private val timeFormatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
    private val dateTimeFormatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    private val currencyFormatter = DecimalFormat("0.##")

    fun formatTime(epochMillis: Long): String = timeFormatter.format(Date(epochMillis))

    fun formatDateTime(epochMillis: Long): String = dateTimeFormatter.format(Date(epochMillis))

    fun formatCurrency(amount: Double): String = "\u20B9" + currencyFormatter.format(amount)

    fun startOfToday(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}