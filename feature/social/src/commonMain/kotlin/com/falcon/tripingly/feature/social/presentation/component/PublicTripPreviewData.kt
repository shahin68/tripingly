package com.falcon.tripingly.feature.social.presentation.component

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.falcon.tripingly.core.model.social.PublicTrip
import com.falcon.tripingly.core.model.trip.Trip
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.core.model.trip.UserSummary
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

internal val previewTrip = PublicTrip(
    trip = Trip(
        id = "t1",
        name = "A week in Vienna",
        startDate = LocalDate(2026, 6, 1),
        endDate = LocalDate(2026, 6, 7),
        visibility = TripVisibility.PUBLIC,
        role = TripRole.VIEWER,
        owner = UserSummary("u1", "anna", "Anna"),
        dayCount = 7,
        markerCount = 12,
        createdAt = Instant.fromEpochSeconds(1_790_000_000),
        updatedAt = Instant.fromEpochSeconds(1_790_000_000),
    ),
    likeCount = 24,
    likedByMe = true,
    saveCount = 3,
)

/** Someone else's trip I liked, one I saved, and one of mine (nothing to save or report). */
internal class PublicTripCardPreviewStates : PreviewParameterProvider<PublicTrip> {
    override val values = sequenceOf(
        previewTrip,
        previewTrip.copy(trip = previewTrip.trip.copy(savedByMe = true, startDate = null, endDate = null), likedByMe = false),
        previewTrip.copy(trip = previewTrip.trip.copy(role = TripRole.OWNER), likeCount = 0, likedByMe = false, saveCount = 0),
    )
}

internal val previewPeople = listOf(
    UserSummary("u1", "anna", "Anna"),
    UserSummary("u2", "jonas.k", "Jonas Krüger"),
    UserSummary("u3", "maximilian.alexander", "Maximilian-Alexander von Habsburg-Lothringen"),
)
