package com.falcon.tripingly.core.common.util

import android.icu.text.DateIntervalFormat
import android.icu.util.DateInterval
import android.text.format.DateFormat
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

internal actual fun formatLocalized(date: LocalDate, skeleton: String): String {
    val locale = Locale.getDefault()
    return SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(date.toCalendar(locale).time)
}

internal actual fun formatLocalizedRange(start: LocalDate, end: LocalDate, skeleton: String): String {
    val locale = Locale.getDefault()
    val interval = DateInterval(start.toCalendar(locale).timeInMillis, end.toCalendar(locale).timeInMillis)
    return DateIntervalFormat.getInstance(skeleton, locale).format(interval)
}

private fun LocalDate.toCalendar(locale: Locale): Calendar = Calendar.getInstance(locale).apply {
    clear()
    set(year, month.number - 1, day)
}
