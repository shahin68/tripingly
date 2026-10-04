package com.falcon.tripingly.core.common.util

import android.text.format.DateFormat
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

internal actual fun formatLocalized(date: LocalDate, skeleton: String): String {
    val locale = Locale.getDefault()
    val calendar = Calendar.getInstance(locale).apply {
        clear()
        set(date.year, date.month.number - 1, date.day)
    }
    return SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(calendar.time)
}
