package com.falcon.tripingly.feature.trips.presentation.screen

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.core.model.place.PlaceSearchResult
import com.falcon.tripingly.core.model.trip.Destination
import com.falcon.tripingly.core.model.trip.UserSummary
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.LocalDate

/**
 * What the New trip sheet shows; kept in [HomeViewModel] so it survives a rotation. The text fields' states are part
 * of the form, so a new sheet starts with empty fields.
 */
@Immutable
data class CreateTripForm(
    val name: TextFieldState = TextFieldState(),
    /** Required to create the trip (Shahin, 2026-10-11); with only a start date the trip is one day long. */
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    /** What is typed into the destination field; the picked place's name once [destination] is set. */
    val destinationQuery: TextFieldState = TextFieldState(),
    val destination: Destination? = null,
    val destinationResults: ImmutableList<PlaceSearchResult> = persistentListOf(),
    val isSearchingDestination: Boolean = false,
    /** Starts at the user's `defaultTripVisibility`. */
    val visibility: TripVisibility = TripVisibility.PUBLIC,
    /** Friends checked in the row; they join as editors when the trip is created, in the order they were picked. */
    val members: ImmutableList<UserSummary> = persistentListOf(),
    /** The user's friends, the only people they can add (Shahin, 2026-10-29); null until they've loaded. */
    val friends: ImmutableList<UserSummary>? = null,
) {
    val canCreate: Boolean get() = name.text.isNotBlank() && startDate != null

    fun isMember(user: UserSummary): Boolean = members.any { it.username == user.username }
}
