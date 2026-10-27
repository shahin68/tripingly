package com.falcon.tripingly.feature.trips.presentation.component

import androidx.compose.foundation.text.input.TextFieldState
import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.core.model.place.PlaceSearchResult
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.trips.presentation.screen.CreateTripForm
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.datetime.LocalDate

private val anna = UserSummary("u1", "anna", "Anna")
private val jonas = UserSummary("u2", "jonas.k", "Jonas")
private val lena = UserSummary("u3", "lena", "Lena")

internal val createTripFormPreviewData = CreateTripForm(
    name = TextFieldState("Summer in Vienna"),
    startDate = LocalDate(2026, 7, 1),
    endDate = LocalDate(2026, 7, 4),
    destinationQuery = TextFieldState("Vie"),
    destinationResults = persistentListOf(PlaceSearchResult("Vienna", "Austria", GeoPoint(48.2082, 16.3738))),
    visibility = TripVisibility.PUBLIC,
    members = persistentListOf(anna),
    friends = persistentListOf(anna, jonas, lena),
)

internal val createTripFormWithErrorsPreviewData = createTripFormPreviewData.copy(
    destinationQuery = TextFieldState("Vienna"),
    destinationResults = persistentListOf(),
    visibility = TripVisibility.PRIVATE,
    members = persistentListOf(anna, UserSummary("u9", "nobody", "Nobody")),
    memberErrors = persistentMapOf("nobody" to UiText.Server("No one has this username.")),
)
