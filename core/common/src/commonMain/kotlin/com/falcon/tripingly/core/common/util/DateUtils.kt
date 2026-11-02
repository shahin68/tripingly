package com.falcon.tripingly.core.common.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.Instant

object DateUtils {
    fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

    /** Day, month name and year in the device's locale, e.g. "Oct 4, 2026", "4. Okt. 2026", "2026. okt. 4.". */
    fun formatFormal(date: LocalDate): String = formatLocalized(date, "yMMMd")

    /** The day [instant] falls on where the device is, as [formatFormal]. */
    fun formatFormal(instant: Instant): String = formatFormal(instant.toLocalDateTime(TimeZone.currentSystemDefault()).date)

    /** Day and month name in the device's locale, e.g. "Oct 4", "4. Okt.", "okt. 4.". */
    fun formatAbbreviated(date: LocalDate): String = formatLocalized(date, "MMMd")

    /** The month's short name in the device's locale, e.g. "Oct", "Okt.", "okt."; the timeline's date column. */
    fun formatMonthShort(date: LocalDate): String = formatLocalized(date, "MMM")

    /** Weekday, day and month in the device's locale, e.g. "Mon, Oct 26", "Mo., 26. Okt.", "okt. 26., h". */
    fun formatWeekday(date: LocalDate): String = formatLocalized(date, "MMMEd")

    /** "Jun 1, 2025 - Jun 15, 2025", or null for a trip without dates. */
    fun formatRange(start: LocalDate?, end: LocalDate?): String? = when {
        start == null -> null
        end == null || end == start -> formatFormal(start)
        else -> "${formatFormal(start)} - ${formatFormal(end)}"
    }
}

/** Formats [date] with the platform's best pattern for the CLDR [skeleton] in the device's locale. */
internal expect fun formatLocalized(date: LocalDate, skeleton: String): String
