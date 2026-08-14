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

    /** Hour of day (0..23) for a timestamp — used for the business peak-hour metric. */
    fun hourOfDay(epochMillis: Long): Int =
        Calendar.getInstance().apply { timeInMillis = epochMillis }.get(Calendar.HOUR_OF_DAY)

    /** "6 PM" style label for a 0..23 hour value. */
    fun formatHour(hourOfDay: Int): String =
        SimpleDateFormat("h a", Locale.getDefault()).format(Date(hourOfDay * 3_600_000L))

    fun formatCurrency(amount: Double): String = "\u20B9" + currencyFormatter.format(amount)

    /**
     * Compact relative label for the UI: "Just now", "2 minutes ago",
     * "3 hours ago", "Yesterday", or the date (e.g. "12 Jul").
     */
    fun formatRelativeTime(epochMillis: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - epochMillis
        if (diff < 60_000L) return "Just now"
        val minutes = diff / 60_000L
        if (minutes < 60) return "$minutes min ago"
        val hours = minutes / 60
        if (hours < 24 && isSameDay(epochMillis, now)) return "$hours hr ago"
        if (hours < 48 && isSameDay(epochMillis, now - 24 * 60 * 60 * 1000L)) return "Yesterday"
        return SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(epochMillis))
    }

    private fun isSameDay(a: Long, b: Long): Boolean {
        val calA = Calendar.getInstance().apply { timeInMillis = a }
        val calB = Calendar.getInstance().apply { timeInMillis = b }
        return calA.get(Calendar.YEAR) == calB.get(Calendar.YEAR) &&
            calA.get(Calendar.DAY_OF_YEAR) == calB.get(Calendar.DAY_OF_YEAR)
    }

    fun startOfToday(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}