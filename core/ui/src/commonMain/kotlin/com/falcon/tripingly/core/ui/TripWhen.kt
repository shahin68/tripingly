package com.falcon.tripingly.core.ui

import androidx.compose.runtime.Composable
import com.falcon.tripingly.core.ui.generated.resources.Res
import com.falcon.tripingly.core.ui.generated.resources.trip_when_days_ago
import com.falcon.tripingly.core.ui.generated.resources.trip_when_in_days
import com.falcon.tripingly.core.ui.generated.resources.trip_when_in_weeks
import com.falcon.tripingly.core.ui.generated.resources.trip_when_in_year
import com.falcon.tripingly.core.ui.generated.resources.trip_when_months_ago
import com.falcon.tripingly.core.ui.generated.resources.trip_when_next_fall
import com.falcon.tripingly.core.ui.generated.resources.trip_when_next_month
import com.falcon.tripingly.core.ui.generated.resources.trip_when_next_spring
import com.falcon.tripingly.core.ui.generated.resources.trip_when_next_summer
import com.falcon.tripingly.core.ui.generated.resources.trip_when_next_winter
import com.falcon.tripingly.core.ui.generated.resources.trip_when_now
import com.falcon.tripingly.core.ui.generated.resources.trip_when_this_fall
import com.falcon.tripingly.core.ui.generated.resources.trip_when_this_spring
import com.falcon.tripingly.core.ui.generated.resources.trip_when_this_summer
import com.falcon.tripingly.core.ui.generated.resources.trip_when_this_winter
import com.falcon.tripingly.core.ui.generated.resources.trip_when_today
import com.falcon.tripingly.core.ui.generated.resources.trip_when_tomorrow
import com.falcon.tripingly.core.ui.generated.resources.trip_when_weeks_ago
import com.falcon.tripingly.core.ui.generated.resources.trip_when_years_ago
import com.falcon.tripingly.core.ui.generated.resources.trip_when_yesterday
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.number
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * When a trip is, said relative to today the short way people say it (My Trips and Explore cards): "Now", "Tomorrow",
 * "In 3 days", "In a week", "Next month", "This winter", "Next fall", "In 2028", "Last week", "2 months ago".
 */
sealed interface TripWhen {
    data object Now : TripWhen
    data object Today : TripWhen
    data object Tomorrow : TripWhen
    data object Yesterday : TripWhen
    data class InDays(val days: Int) : TripWhen
    data class InWeeks(val weeks: Int) : TripWhen
    data object NextMonth : TripWhen
    /** The season of the trip's start: the coming one ([next] false, "This winter") or the one a year on ("Next fall"). */
    data class InSeason(val season: Season, val next: Boolean) : TripWhen
    data class InYear(val year: Int) : TripWhen
    data class DaysAgo(val days: Int) : TripWhen
    data class WeeksAgo(val weeks: Int) : TripWhen
    data class MonthsAgo(val months: Int) : TripWhen
    data class YearsAgo(val years: Int) : TripWhen

    /** Meteorological seasons: winter is December to February. */
    enum class Season { Winter, Spring, Summer, Fall }
}

/** Null without dates; a trip with only a start date lasts that day. */
fun tripWhen(startDate: LocalDate?, endDate: LocalDate?, today: LocalDate): TripWhen? {
    val start = startDate ?: return null
    val end = endDate ?: start
    val untilStart = today.daysUntil(start)
    val sinceEnd = end.daysUntil(today)
    return when {
        untilStart < 0 && sinceEnd <= 0 -> TripWhen.Now
        untilStart == 0 -> TripWhen.Today
        untilStart == 1 -> TripWhen.Tomorrow
        untilStart in 2..<DAYS_AS_DAYS -> TripWhen.InDays(untilStart)
        untilStart in DAYS_AS_DAYS..<DAYS_AS_WEEKS -> TripWhen.InWeeks(untilStart / 7)
        untilStart > 0 -> later(start, today)
        sinceEnd == 1 -> TripWhen.Yesterday
        sinceEnd < DAYS_AS_DAYS -> TripWhen.DaysAgo(sinceEnd)
        sinceEnd < DAYS_AS_WEEKS -> TripWhen.WeeksAgo(sinceEnd / 7)
        sinceEnd < DAYS_IN_YEAR -> TripWhen.MonthsAgo(sinceEnd / 30)
        else -> TripWhen.YearsAgo(sinceEnd / DAYS_IN_YEAR)
    }
}

/** More than five weeks away: next month, else its season (the coming four are "this", the four after "next"), else its year. */
private fun later(start: LocalDate, today: LocalDate): TripWhen {
    if (start.year * 12 + start.month.number == today.year * 12 + today.month.number + 1) return TripWhen.NextMonth
    val seasonsAway = start.seasonIndex() - today.seasonIndex()
    return when {
        seasonsAway < SEASONS_IN_YEAR -> TripWhen.InSeason(start.season(), next = false)
        seasonsAway < SEASONS_IN_YEAR * 2 -> TripWhen.InSeason(start.season(), next = true)
        else -> TripWhen.InYear(start.year)
    }
}

private fun LocalDate.season(): TripWhen.Season = TripWhen.Season.entries[month.number % 12 / 3]

/** Counts seasons; December belongs to the next year's winter. */
private fun LocalDate.seasonIndex(): Int = (year + if (month.number == 12) 1 else 0) * SEASONS_IN_YEAR + season().ordinal

private const val DAYS_AS_DAYS = 7
private const val DAYS_AS_WEEKS = 35
private const val DAYS_IN_YEAR = 365
private const val SEASONS_IN_YEAR = 4

@Composable
fun TripWhen.label(): String = when (this) {
    TripWhen.Now -> stringResource(Res.string.trip_when_now)
    TripWhen.Today -> stringResource(Res.string.trip_when_today)
    TripWhen.Tomorrow -> stringResource(Res.string.trip_when_tomorrow)
    TripWhen.Yesterday -> stringResource(Res.string.trip_when_yesterday)
    is TripWhen.InDays -> pluralStringResource(Res.plurals.trip_when_in_days, days, days)
    is TripWhen.InWeeks -> pluralStringResource(Res.plurals.trip_when_in_weeks, weeks, weeks)
    TripWhen.NextMonth -> stringResource(Res.string.trip_when_next_month)
    is TripWhen.InSeason -> stringResource(
        when (season) {
            TripWhen.Season.Winter -> if (next) Res.string.trip_when_next_winter else Res.string.trip_when_this_winter
            TripWhen.Season.Spring -> if (next) Res.string.trip_when_next_spring else Res.string.trip_when_this_spring
            TripWhen.Season.Summer -> if (next) Res.string.trip_when_next_summer else Res.string.trip_when_this_summer
            TripWhen.Season.Fall -> if (next) Res.string.trip_when_next_fall else Res.string.trip_when_this_fall
        },
    )
    is TripWhen.InYear -> stringResource(Res.string.trip_when_in_year, year.toString())
    is TripWhen.DaysAgo -> pluralStringResource(Res.plurals.trip_when_days_ago, days, days)
    is TripWhen.WeeksAgo -> pluralStringResource(Res.plurals.trip_when_weeks_ago, weeks, weeks)
    is TripWhen.MonthsAgo -> pluralStringResource(Res.plurals.trip_when_months_ago, months, months)
    is TripWhen.YearsAgo -> pluralStringResource(Res.plurals.trip_when_years_ago, years, years)
}
