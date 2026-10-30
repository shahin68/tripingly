package com.falcon.tripingly.feature.trips

import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.core.model.trip.Trip
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.feature.trips.presentation.screen.TimelineMark
import com.falcon.tripingly.core.ui.TripWhen
import com.falcon.tripingly.feature.trips.presentation.screen.toTimeline
import com.falcon.tripingly.core.ui.tripWhen
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
    fun ongoingTrips_getAFilledDot_tripsStartingToday_shareTodaysRing_andTheTodayRowHides() {
        val timeline = listOf(
            trip("Week", LocalDate(2026, 10, 24), LocalDate(2026, 10, 28)),
            trip("Weekend", LocalDate(2026, 10, 25), LocalDate(2026, 10, 26)),
            trip("Day", today),
            trip("Two days", today, LocalDate(2026, 10, 27)),
            trip("Next", LocalDate(2026, 10, 27), LocalDate(2026, 10, 29)),
        ).toTimeline(today)

        assertEquals(
            listOf(
                TimelineMark(TimelineMark.Kind.Ongoing),
                TimelineMark(TimelineMark.Kind.Ongoing),
                TimelineMark(TimelineMark.Kind.StartsToday, isFirst = true, isLast = false),
                TimelineMark(TimelineMark.Kind.StartsToday, isFirst = false, isLast = true),
                TimelineMark(),
            ),
            timeline.upcoming.map { it.mark },
        )
        assertFalse(timeline.showsTodayRow)
        assertEquals("Week", timeline.railStartTripId)
    }

    @Test
    fun onlyTripsStartingTheSameDay_shareAMark_overlappingOnesDont() {
        val timeline = listOf(
            trip("A", LocalDate(2026, 11, 1), LocalDate(2026, 11, 5)),
            trip("B", LocalDate(2026, 11, 3), LocalDate(2026, 11, 10)),
            trip("C", LocalDate(2026, 11, 3)),
            trip("Dateless", null),
            trip("Dateless 2", null),
        ).toTimeline(today)

        assertEquals(listOf(true, true, false, true, true), timeline.upcoming.map { it.mark.isFirst })
        assertEquals(listOf(true, false, true, true, true), timeline.upcoming.map { it.mark.isLast })
        assertTrue(timeline.showsTodayRow)
        assertNull(timeline.railStartTripId)
    }

    @Test
    fun when_countsInDays_thenWeeks_thenNextMonth_seasons_andYears() {
        fun on(start: LocalDate?, end: LocalDate? = start) = tripWhen(start, end, today)
        assertEquals(TripWhen.Now, on(LocalDate(2026, 10, 25), LocalDate(2026, 10, 27)))
        assertEquals(TripWhen.Today, on(today))
        assertEquals(TripWhen.Tomorrow, on(LocalDate(2026, 10, 27)))
        assertEquals(TripWhen.InDays(3), on(LocalDate(2026, 10, 29)))
        assertEquals(TripWhen.InWeeks(1), on(LocalDate(2026, 11, 2)))
        assertEquals(TripWhen.InWeeks(3), on(LocalDate(2026, 11, 16)))
        assertEquals(TripWhen.NextMonth, on(LocalDate(2026, 11, 30)))
        assertEquals(TripWhen.InSeason(TripWhen.Season.Winter, next = false), on(LocalDate(2026, 12, 20)))
        assertEquals(TripWhen.InSeason(TripWhen.Season.Spring, next = false), on(LocalDate(2027, 3, 1)))
        assertEquals(TripWhen.InSeason(TripWhen.Season.Fall, next = true), on(LocalDate(2027, 10, 1)))
        assertEquals(TripWhen.InYear(2029), on(LocalDate(2029, 1, 10)))
        assertEquals(TripWhen.Yesterday, on(LocalDate(2026, 10, 25)))
        assertEquals(TripWhen.WeeksAgo(2), on(LocalDate(2026, 10, 10)))
        assertEquals(TripWhen.MonthsAgo(3), on(LocalDate(2026, 7, 20)))
        assertEquals(TripWhen.YearsAgo(1), on(LocalDate(2025, 9, 1)))
        assertNull(on(null))
    }
}
