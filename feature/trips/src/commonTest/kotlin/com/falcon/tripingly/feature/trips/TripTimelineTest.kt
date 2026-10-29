package com.falcon.tripingly.feature.trips

import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.core.model.trip.Trip
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.feature.trips.presentation.screen.TimelineMark
import com.falcon.tripingly.feature.trips.presentation.screen.TripWhen
import com.falcon.tripingly.feature.trips.presentation.screen.toTimeline
import com.falcon.tripingly.feature.trips.presentation.screen.whenRelativeTo
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull
import kotlin.time.Instant

class TripTimelineTest {

    private val today = LocalDate(2026, 10, 26)

    private fun trip(name: String, start: LocalDate?, end: LocalDate? = start) = Trip(
        id = name,
        name = name,
        startDate = start,
        endDate = end,
        visibility = TripVisibility.PRIVATE,
        role = TripRole.OWNER,
        owner = UserSummary("u1", "anna", "Anna"),
        dayCount = 1,
        markerCount = 0,
        createdAt = Instant.parse("2026-10-16T10:00:00Z"),
        updatedAt = Instant.parse("2026-10-16T10:00:00Z"),
    )

    @Test
    fun timeline_putsTheSoonestFirst_dateless_last_andThePastNewestFirst() {
        val timeline = listOf(
            trip("Dateless", null),
            trip("Rome", LocalDate(2026, 12, 1)),
            trip("Old", LocalDate(2025, 5, 1)),
            trip("Now", LocalDate(2026, 10, 20), LocalDate(2026, 10, 30)),
            trip("Recent", LocalDate(2026, 9, 1)),
        ).toTimeline(today)

        assertEquals(listOf("Now", "Rome", "Dateless"), timeline.upcoming.map { it.trip.name })
        assertEquals(listOf("Recent", "Old"), timeline.past.map { it.name })
    }

    @Test
    fun aTripEndingToday_isStillUpcoming() {
        val timeline = listOf(trip("Ends today", LocalDate(2026, 10, 24), today)).toTimeline(today)

        assertEquals(1, timeline.upcoming.size)
    }

    @Test
    fun tripsOnToday_shareTodaysMark_andHideTheTodayRow() {
        val timeline = listOf(
            trip("Week", LocalDate(2026, 10, 24), LocalDate(2026, 10, 28)),
            trip("Day", today),
            trip("Next", LocalDate(2026, 10, 27), LocalDate(2026, 10, 29)),
        ).toTimeline(today)

        assertEquals(
            listOf(TimelineMark(isNow = true, isFirst = true, isLast = false), TimelineMark(isNow = true, isFirst = false, isLast = true), TimelineMark()),
            timeline.upcoming.map { it.mark },
        )
        assertFalse(timeline.showsTodayRow)
    }

    @Test
    fun overlappingTrips_shareOneMark_andTouchingOnesToo_butNotLaterOnes() {
        val timeline = listOf(
            trip("A", LocalDate(2026, 11, 1), LocalDate(2026, 11, 5)),
            trip("B", LocalDate(2026, 11, 3), LocalDate(2026, 11, 10)),
            trip("C", LocalDate(2026, 11, 10)),
            trip("D", LocalDate(2026, 11, 11)),
            trip("Dateless", null),
        ).toTimeline(today)

        assertEquals(listOf(true, false, false, true, true), timeline.upcoming.map { it.mark.isFirst })
        assertEquals(listOf(false, false, true, true, true), timeline.upcoming.map { it.mark.isLast })
        assertTrue(timeline.showsTodayRow)
    }

    @Test
    fun when_countsInDays_thenWeeks_thenMonths() {
        assertEquals(TripWhen.Now, trip("a", LocalDate(2026, 10, 25), LocalDate(2026, 10, 27)).whenRelativeTo(today))
        assertEquals(TripWhen.Today, trip("a", today).whenRelativeTo(today))
        assertEquals(TripWhen.Tomorrow, trip("a", LocalDate(2026, 10, 27)).whenRelativeTo(today))
        assertEquals(TripWhen.InDays(3), trip("a", LocalDate(2026, 10, 29)).whenRelativeTo(today))
        assertEquals(TripWhen.InWeeks(3), trip("a", LocalDate(2026, 11, 16)).whenRelativeTo(today))
        assertEquals(TripWhen.InMonths(4), trip("a", LocalDate(2027, 3, 1)).whenRelativeTo(today))
        assertEquals(TripWhen.Yesterday, trip("a", LocalDate(2026, 10, 25)).whenRelativeTo(today))
        assertEquals(TripWhen.WeeksAgo(2), trip("a", LocalDate(2026, 10, 10)).whenRelativeTo(today))
        assertNull(trip("a", null).whenRelativeTo(today))
    }
}
