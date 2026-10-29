package com.falcon.tripingly.feature.trips.presentation.members

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.falcon.tripingly.core.model.trip.JoinRequest
import com.falcon.tripingly.core.model.trip.TripInvite
import com.falcon.tripingly.core.model.trip.TripMember
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.trips.generated.resources.Res
import com.falcon.tripingly.feature.trips.generated.resources.members_error_some_not_added
import com.falcon.tripingly.feature.trips.presentation.members.TripMembersViewModel.AddState
import com.falcon.tripingly.feature.trips.presentation.members.TripMembersViewModel.InvitesState
import com.falcon.tripingly.feature.trips.presentation.members.TripMembersViewModel.JoinRequestsState
import com.falcon.tripingly.feature.trips.presentation.members.TripMembersViewModel.State
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlin.time.Instant

internal val previewMembers = persistentListOf(
    TripMember(UserSummary("1", "shahin", "Shahin"), TripRole.OWNER),
    TripMember(UserSummary("2", "anna", "Anna"), TripRole.EDITOR),
)

internal val previewJoinRequest = JoinRequest("r1", "t1", UserSummary("5", "mia", "Mia"), Instant.parse("2025-06-01T10:00:00Z"))

internal val previewInvite = TripInvite("i1", "https://example.com/i/abc", Instant.parse("2025-06-08T10:00:00Z"))

private val ownersView = State(
    tripName = "Summer Vacation in Paris",
    canManage = true,
    isLoading = false,
    members = previewMembers,
    add = AddState(
        friends = persistentListOf(UserSummary("3", "jonas.k", "Jonas"), UserSummary("4", "lena", "Lena")),
        pickedFriends = persistentSetOf("lena"),
    ),
    joinRequests = JoinRequestsState(persistentListOf(previewJoinRequest)),
    invites = InvitesState(persistentListOf(previewInvite)),
)

/** The owner's sheet with requests and invites, loading, an editor's (nothing to manage), an error adding people, and an owner without friends to add. */
internal class TripMembersPreviewStates : PreviewParameterProvider<State> {
    override val values = sequenceOf(
        ownersView,
        State(tripName = "Summer Vacation in Paris"),
        ownersView.copy(canManage = false, add = AddState(), joinRequests = JoinRequestsState(), invites = InvitesState()),
        ownersView.copy(
            add = ownersView.add.copy(isAdding = true),
            joinRequests = JoinRequestsState(persistentListOf(previewJoinRequest), answeringIds = persistentSetOf("r1")),
            invites = InvitesState(isCreating = true),
            message = UiText.Resource(Res.string.members_error_some_not_added),
        ),
        ownersView.copy(add = AddState(friends = persistentListOf())),
    )
}
