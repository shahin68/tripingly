package com.falcon.tripingly.core.util

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

object DateUtils {
    fun today(): LocalDate {
        val now = Clock.System.now()
        // If Instant is the same, this might work
        return Instant.fromEpochSeconds(now.epochSeconds, now.nanosecondsOfSecond)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
    }

    private val formalDateFormat = LocalDate.Format {
        monthName(MonthNames.ENGLISH_ABBREVIATED)
        char(' ')
        day()
        char(',')
        char(' ')
        year()
    }

    fun formatFormal(date: LocalDate): String {
        return date.format(formalDateFormat)
    }

    private val abbreviatedDateFormat = LocalDate.Format {
        monthName(MonthNames.ENGLISH_ABBREVIATED)
        char(' ')
        day()
    }

    fun formatAbbreviated(date: LocalDate): String {
        return date.format(abbreviatedDateFormat)
    }
}
