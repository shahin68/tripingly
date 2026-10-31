package com.falcon.tripingly.feature.trips.presentation.screen

import androidx.compose.runtime.Immutable
import com.falcon.tripingly.core.model.trip.Trip
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.LocalDate

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
