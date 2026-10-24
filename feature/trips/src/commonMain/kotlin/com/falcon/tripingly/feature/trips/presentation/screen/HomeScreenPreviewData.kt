package com.falcon.tripingly.feature.trips.presentation.screen

import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.core.model.trip.Trip
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.core.model.trip.UserSummary
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.trips.generated.resources.Res
import com.falcon.tripingly.feature.trips.generated.resources.home_error_load_trips
import com.falcon.tripingly.feature.trips.presentation.screen.HomeViewModel.State

internal val tripsPreviewData = persistentListOf(
    Trip(
        id = "1",
        name = "Summer Vacation in Paris",
        startDate = LocalDate(2025, 6, 1),
        endDate = LocalDate(2025, 6, 15),
        visibility = TripVisibility.PUBLIC,
        role = TripRole.OWNER,
        owner = UserSummary("u1", "shahin", "Shahin"),
        dayCount = 15,
        markerCount = 4,
        createdAt = Instant.parse("2025-05-01T10:00:00Z"),
        updatedAt = Instant.parse("2025-05-01T10:00:00Z"),
    ),
    Trip(
        id = "2",
        name = "Tokyo Adventure",
        startDate = LocalDate(2025, 10, 10),
        endDate = LocalDate(2025, 10, 20),
        visibility = TripVisibility.PRIVATE,
        role = TripRole.EDITOR,
        owner = UserSummary("u2", "anna", "Anna"),
        dayCount = 11,
        markerCount = 0,
        createdAt = Instant.parse("2025-04-01T10:00:00Z"),
        updatedAt = Instant.parse("2025-04-01T10:00:00Z"),
    ),
    Trip(
        id = "3",
        name = "A long weekend in Budapest with the whole family and friends",
        startDate = null,
        endDate = null,
        visibility = TripVisibility.PUBLIC,
        role = TripRole.VIEWER,
        owner = UserSummary("u3", "jonas.k", "Jonas"),
        dayCount = 1,
        markerCount = 1,
        createdAt = Instant.parse("2025-03-01T10:00:00Z"),
        updatedAt = Instant.parse("2025-03-01T10:00:00Z"),
    ),
)

/** Trips, the first load, no trips yet, a search without matches, and trips that couldn't load. */
internal class HomeScreenPreviewStates : PreviewParameterProvider<State> {
    override val values = sequenceOf(
        State(trips = tripsPreviewData, hasTrips = true, hasLoaded = true),
        State(),
        State(hasLoaded = true),
        State(searchQuery = "Rome", hasTrips = true, hasLoaded = true),
        State(hasLoaded = true, message = UiText.Resource(Res.string.home_error_load_trips)),
    )
}
