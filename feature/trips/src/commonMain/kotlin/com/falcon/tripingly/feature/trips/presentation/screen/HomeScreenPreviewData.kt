package com.falcon.tripingly.feature.trips.presentation.screen

import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.core.model.trip.Trip
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.core.model.trip.UserSummary
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

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
        updatedAt = Instant.parse("2025-04-01T10:00:00Z"),
    ),
)
