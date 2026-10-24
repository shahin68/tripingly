package com.falcon.tripingly.feature.social.presentation.profile

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.falcon.tripingly.core.model.social.Profile
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_error_load_profile
import com.falcon.tripingly.feature.social.presentation.component.previewPeople
import com.falcon.tripingly.feature.social.presentation.component.previewTrip
import com.falcon.tripingly.feature.social.presentation.profile.ProfileViewModel.State
import kotlinx.collections.immutable.persistentListOf

internal val previewProfile = Profile(
    user = previewPeople.first(),
    followerCount = 128,
    followingCount = 1,
    isFollowing = true,
    followsMe = true,
    isFriend = true,
    publicTripCount = 2,
    trips = listOf(previewTrip),
    nextCursor = null,
)

/** A friend's profile with trips, my own without trips, loading, not found, and one that failed to load. */
internal class ProfileScreenPreviewStates : PreviewParameterProvider<State> {
    override val values = sequenceOf(
        State(isMe = false, profile = previewProfile, trips = persistentListOf(previewTrip), isLoading = false),
        State(isMe = true, profile = previewProfile.copy(followerCount = 0, followingCount = 0, publicTripCount = 0, trips = emptyList()), isLoading = false),
        State(isMe = false),
        State(isMe = false, isLoading = false, isNotFound = true),
        State(isMe = false, isLoading = false, message = UiText.Resource(Res.string.social_error_load_profile)),
    )
}

/** Not followed yet, following, and a friend whose name is very long. */
internal class ProfileHeaderPreviewStates : PreviewParameterProvider<Profile> {
    override val values = sequenceOf(
        previewProfile.copy(isFollowing = false, followsMe = false, isFriend = false),
        previewProfile.copy(followsMe = false, isFriend = false),
        previewProfile.copy(user = previewPeople.last()),
    )
}
