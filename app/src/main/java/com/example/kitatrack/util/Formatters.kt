package com.example.kitatrack.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Formatters {
    private val pesoFormatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-PH"))
    private val dateFormatter = SimpleDateFormat("MMM d, yyyy", Locale.US)
    private val shortDateFormatter = SimpleDateFormat("MMM d", Locale.US)

    fun peso(amountInCentavos: Long): String = pesoFormatter.format(amountInCentavos / 100.0)
    fun date(millis: Long): String = dateFormatter.format(Date(millis))
    fun shortDate(millis: Long): String = shortDateFormatter.format(Date(millis))
}

object DateRanges {
    fun currentMonth(): LongRange {
        return monthRange(Calendar.getInstance())
    }

    fun currentWeek(): LongRange = weekRange(Calendar.getInstance())

    fun weekRange(calendar: Calendar): LongRange {
        val start = (calendar.clone() as Calendar).apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = (start.clone() as Calendar).apply {
            add(Calendar.DAY_OF_MONTH, 7)
            add(Calendar.MILLISECOND, -1)
        }
        return start.timeInMillis..end.timeInMillis
    }

    fun monthRange(calendar: Calendar): LongRange {
        val start = (calendar.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = (start.clone() as Calendar).apply {
            add(Calendar.MONTH, 1)
            add(Calendar.MILLISECOND, -1)
        }
        return start.timeInMillis..end.timeInMillis
    }

    fun monthLabel(calendar: Calendar): String =
        SimpleDateFormat("MMMM yyyy", Locale.US).format(calendar.time)
}
