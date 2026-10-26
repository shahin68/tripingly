package com.falcon.tripingly.feature.trips.presentation.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.falcon.tripingly.core.model.trip.Trip
import com.falcon.tripingly.feature.trips.generated.resources.Res
import com.falcon.tripingly.feature.trips.generated.resources.trip_when_days_ago
import com.falcon.tripingly.feature.trips.generated.resources.trip_when_in_days
import com.falcon.tripingly.feature.trips.generated.resources.trip_when_in_months
import com.falcon.tripingly.feature.trips.generated.resources.trip_when_in_weeks
import com.falcon.tripingly.feature.trips.generated.resources.trip_when_months_ago
import com.falcon.tripingly.feature.trips.generated.resources.trip_when_now
import com.falcon.tripingly.feature.trips.generated.resources.trip_when_today
import com.falcon.tripingly.feature.trips.generated.resources.trip_when_tomorrow
import com.falcon.tripingly.feature.trips.generated.resources.trip_when_weeks_ago
import com.falcon.tripingly.feature.trips.generated.resources.trip_when_yesterday
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * My Trips as a timeline: what's coming (trips on now first, then the soonest; trips without dates last), then
 * past trips, the most recent first.
 */
@Immutable
data class TripTimeline(
    /** The day the timeline was made for; the "Today" header and the "In 3 days" labels count from it. */
    val today: LocalDate? = null,
    val upcoming: ImmutableList<Trip> = persistentListOf(),
    val past: ImmutableList<Trip> = persistentListOf(),
) {
    val isEmpty: Boolean get() = upcoming.isEmpty() && past.isEmpty()
}

internal fun List<Trip>.toTimeline(today: LocalDate): TripTimeline {
    val (past, upcoming) = partition { trip -> trip.endDate?.let { it < today } ?: false }
    return TripTimeline(
        today = today,
        upcoming = upcoming.sortedWith(compareBy(nullsLast()) { it.startDate }).toImmutableList(),
        past = past.sortedByDescending { it.endDate }.toImmutableList(),
    )
}

/** When a trip is, said relative to [today]: "Now", "Tomorrow", "In 3 weeks", "Last week". Null without dates. */
internal sealed interface TripWhen {
    data object Now : TripWhen
    data object Today : TripWhen
    data object Tomorrow : TripWhen
    data object Yesterday : TripWhen
    data class InDays(val days: Int) : TripWhen
    data class InWeeks(val weeks: Int) : TripWhen
    data class InMonths(val months: Int) : TripWhen
    data class DaysAgo(val days: Int) : TripWhen
    data class WeeksAgo(val weeks: Int) : TripWhen
    data class MonthsAgo(val months: Int) : TripWhen
}

internal fun Trip.whenRelativeTo(today: LocalDate): TripWhen? {
    val start = startDate ?: return null
    val end = endDate ?: start
    val untilStart = today.daysUntil(start)
    val sinceEnd = end.daysUntil(today)
    return when {
        untilStart < 0 && sinceEnd <= 0 -> TripWhen.Now
        untilStart == 0 -> TripWhen.Today
        untilStart == 1 -> TripWhen.Tomorrow
        untilStart > 1 -> relative(untilStart, TripWhen::InDays, TripWhen::InWeeks, TripWhen::InMonths)
        sinceEnd == 1 -> TripWhen.Yesterday
        else -> relative(sinceEnd, TripWhen::DaysAgo, TripWhen::WeeksAgo, TripWhen::MonthsAgo)
    }
}

private fun relative(days: Int, inDays: (Int) -> TripWhen, inWeeks: (Int) -> TripWhen, inMonths: (Int) -> TripWhen): TripWhen = when {
    days < DAYS_SHOWN_AS_DAYS -> inDays(days)
    days < DAYS_SHOWN_AS_WEEKS -> inWeeks(days / 7)
    else -> inMonths(days / 30)
}

private const val DAYS_SHOWN_AS_DAYS = 7
private const val DAYS_SHOWN_AS_WEEKS = 60

@Composable
internal fun TripWhen.label(): String = when (this) {
    TripWhen.Now -> stringResource(Res.string.trip_when_now)
    TripWhen.Today -> stringResource(Res.string.trip_when_today)
    TripWhen.Tomorrow -> stringResource(Res.string.trip_when_tomorrow)
    TripWhen.Yesterday -> stringResource(Res.string.trip_when_yesterday)
    is TripWhen.InDays -> pluralStringResource(Res.plurals.trip_when_in_days, days, days)
    is TripWhen.InWeeks -> pluralStringResource(Res.plurals.trip_when_in_weeks, weeks, weeks)
    is TripWhen.InMonths -> pluralStringResource(Res.plurals.trip_when_in_months, months, months)
    is TripWhen.DaysAgo -> pluralStringResource(Res.plurals.trip_when_days_ago, days, days)
    is TripWhen.WeeksAgo -> pluralStringResource(Res.plurals.trip_when_weeks_ago, weeks, weeks)
    is TripWhen.MonthsAgo -> pluralStringResource(Res.plurals.trip_when_months_ago, months, months)
}
