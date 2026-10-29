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
    /** The day the timeline was made for; the "Today" row and the "In 3 days" labels count from it. */
    val today: LocalDate? = null,
    val upcoming: ImmutableList<TimelineEntry> = persistentListOf(),
    val past: ImmutableList<Trip> = persistentListOf(),
) {
    val isEmpty: Boolean get() = upcoming.isEmpty() && past.isEmpty()

    /** No trip is on today, so a "Today" row stands above what's coming; otherwise those trips carry today's marks. */
    val showsTodayRow: Boolean get() = today != null && upcoming.none { it.mark.isNow }

    /** The trip whose mark the rail starts at, as nothing stands above it; with the "Today" row it starts there. */
    val railStartTripId: String? get() = upcoming.firstOrNull()?.takeUnless { showsTodayRow }?.trip?.id
}

/** A coming trip and its mark on the timeline's rail. */
@Immutable
data class TimelineEntry(val trip: Trip, val mark: TimelineMark = TimelineMark())

/**
 * A trip's mark on the rail (Shahin, 2026-10-29): a small dot for a coming trip, a big filled dot for one that started
 * before today and is still on, today's ring for one that starts today. Trips starting on the same day share one mark
 * that stretches from the first one's dot to the last one's ([isFirst], [isLast]); only the first shows its date.
 */
@Immutable
data class TimelineMark(val kind: Kind = Kind.Coming, val isFirst: Boolean = true, val isLast: Boolean = true) {
    /** The trip is on today, so its card lights up. */
    val isNow: Boolean get() = kind != Kind.Coming

    enum class Kind { Coming, Ongoing, StartsToday }
}

internal fun List<Trip>.toTimeline(today: LocalDate): TripTimeline {
    val (past, upcoming) = partition { trip -> trip.endDate?.let { it < today } ?: false }
    return TripTimeline(
        today = today,
        upcoming = upcoming.sortedWith(compareBy(nullsLast()) { it.startDate }).marked(today).toImmutableList(),
        past = past.sortedByDescending { it.endDate }.toImmutableList(),
    )
}

/** Gives trips (sorted by start) their marks; trips starting on the same day share one. Trips without dates stand alone. */
private fun List<Trip>.marked(today: LocalDate): List<TimelineEntry> {
    val groups = mutableListOf<MutableList<Trip>>()
    for (trip in this) {
        val previous = groups.lastOrNull()?.last()
        if (trip.startDate != null && trip.startDate == previous?.startDate) groups.last() += trip else groups += mutableListOf(trip)
    }
    return groups.flatMap { group ->
        val kind = group.first().markKind(today)
        group.mapIndexed { index, trip -> TimelineEntry(trip, TimelineMark(kind, isFirst = index == 0, isLast = index == group.lastIndex)) }
    }
}

private fun Trip.markKind(today: LocalDate): TimelineMark.Kind {
    val start = startDate ?: return TimelineMark.Kind.Coming
    return when {
        start == today -> TimelineMark.Kind.StartsToday
        start < today && today <= (endDate ?: start) -> TimelineMark.Kind.Ongoing
        else -> TimelineMark.Kind.Coming
    }
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
