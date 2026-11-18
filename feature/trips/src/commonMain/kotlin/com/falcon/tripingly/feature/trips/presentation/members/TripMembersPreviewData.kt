package com.falcon.tripingly.feature.trips.presentation.members

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.falcon.tripingly.core.model.trip.JoinRequest
import com.falcon.tripingly.core.model.trip.TripMember
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.feature.trips.presentation.members.TripMembersViewModel.JoinRequestsState
import com.falcon.tripingly.feature.trips.presentation.members.TripMembersViewModel.SearchState
import com.falcon.tripingly.feature.trips.presentation.members.TripMembersViewModel.State
import androidx.compose.foundation.text.input.TextFieldState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlin.time.Instant

internal val previewMembers = persistentListOf(
    TripMember(UserSummary("1", "shahin", "Shahin"), TripRole.OWNER),
    TripMember(UserSummary("2", "anna", "Anna"), TripRole.EDITOR),
)

internal val previewJoinRequest = JoinRequest("r1", "t1", UserSummary("5", "mia", "Mia"), Instant.parse("2025-06-01T10:00:00Z"))

internal val previewFriends = persistentListOf(
    UserSummary("3", "jonas.k", "Jonas"),
    UserSummary("4", "lena", "Lena"),
    UserSummary("6", "ben", "Ben"),
    UserSummary("7", "mira", "Mira"),
    UserSummary("8", "lea", "Lea"),
)

private val ownersView = State(
    tripName = "Summer Vacation in Paris",
    canManage = true,
    isLoading = false,
    members = previewMembers,
    friends = previewFriends,
    onTrip = persistentSetOf("shahin", "anna"),
    asking = persistentSetOf("mia"),
    joinRequests = JoinRequestsState(persistentListOf(previewJoinRequest)),
)

/**
 * The owner's sheet, loading, an editor's (only who is on the trip), a request being answered with no friends left to
 * add, and the Add people page with someone typed.
 */
internal class TripMembersPreviewStates : PreviewParameterProvider<State> {
    override val values = sequenceOf(
        ownersView,
        State(tripName = "Summer Vacation in Paris", canManage = true),
        ownersView.copy(canManage = false, friends = null, joinRequests = JoinRequestsState()),
        ownersView.copy(friends = persistentListOf(), joinRequests = JoinRequestsState(persistentListOf(previewJoinRequest), answeringIds = persistentSetOf("r1")), isCreatingInvite = true),
        ownersView.copy(search = SearchState(query = TextFieldState("an"), results = persistentListOf(UserSummary("9", "anna.k", "Anna K."), previewMembers[1].user))),
    )
}
