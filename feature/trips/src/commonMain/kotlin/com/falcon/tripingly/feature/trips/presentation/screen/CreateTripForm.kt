package com.falcon.tripingly.feature.trips.presentation.screen

import androidx.compose.runtime.Immutable
import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.core.model.place.PlaceSearchResult
import com.falcon.tripingly.core.model.trip.Destination
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.core.ui.UiText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.datetime.LocalDate

/** What the New trip sheet shows; kept in [HomeViewModel] so it survives a rotation. */
@Immutable
data class CreateTripForm(
    val name: String = "",
    /** Required to create the trip (Shahin, 2026-10-11); with only a start date the trip is one day long. */
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    /** What is typed into the destination field; the picked place's name once [destination] is set. */
    val destinationQuery: String = "",
    val destination: Destination? = null,
    val destinationResults: ImmutableList<PlaceSearchResult> = persistentListOf(),
    val isSearchingDestination: Boolean = false,
    /** Starts at the user's `defaultTripVisibility`. */
    val visibility: TripVisibility = TripVisibility.PUBLIC,
    /** What is typed into the username field. */
    val memberQuery: String = "",
    val memberResults: ImmutableList<UserSummary> = persistentListOf(),
    val isSearchingMembers: Boolean = false,
    /** People who join as editors when the trip is created, in the order they were picked. */
    val members: ImmutableList<UserSummary> = persistentListOf(),
    /** Why the server refused a member, by username; shown on that member's chip. */
    val memberErrors: ImmutableMap<String, UiText> = persistentMapOf(),
    /** The user's friends, to pick with one tap; empty hides the row. */
    val friends: ImmutableList<UserSummary> = persistentListOf(),
) {
    val canCreate: Boolean get() = name.isNotBlank() && startDate != null

    val canAddMembers: Boolean get() = members.size < MAX_MEMBERS

    fun isMember(user: UserSummary): Boolean = members.any { it.username == user.username }

    companion object {
        /** The most people `POST /trips` takes in `memberUsernames`. */
        const val MAX_MEMBERS = 20
    }
}
