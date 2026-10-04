package com.falcon.tripingly.core.common.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

object DateUtils {
    fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

    /** Day, month name and year in the device's locale, e.g. "Oct 4, 2026", "4. Okt. 2026", "2026. okt. 4.". */
    fun formatFormal(date: LocalDate): String = formatLocalized(date, "yMMMd")

    /** Day and month name in the device's locale, e.g. "Oct 4", "4. Okt.", "okt. 4.". */
    fun formatAbbreviated(date: LocalDate): String = formatLocalized(date, "MMMd")
}

/** Formats [date] with the platform's best pattern for the CLDR [skeleton] in the device's locale. */
internal expect fun formatLocalized(date: LocalDate, skeleton: String): String
