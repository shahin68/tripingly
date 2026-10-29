package com.falcon.tripingly.feature.trips.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.falcon.tripingly.core.designsystem.component.AvatarChoice
import com.falcon.tripingly.core.designsystem.component.AvatarCircle
import com.falcon.tripingly.core.designsystem.component.AvatarSize
import com.falcon.tripingly.core.designsystem.component.PillChip
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.feature.trips.generated.resources.Res
import com.falcon.tripingly.feature.trips.generated.resources.trip_friends_only_hint
import com.falcon.tripingly.feature.trips.presentation.screen.CreateTripForm
import com.falcon.tripingly.feature.trips.presentation.screen.HomeViewModel.Action
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

/** Friends to add (the only people an owner can add): a row to tap, or a hint without friends, and the picked ones as chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CreateTripMembers(
    form: CreateTripForm,
    isSaving: Boolean,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        when {
            form.friends == null -> Unit
            form.friends.isEmpty() -> FriendsOnlyHint()
            else -> FriendPickerRow(
                friends = form.friends,
                isPicked = form::isMember,
                onToggle = { onAction(Action.CreateTrip.FriendToggled(it)) },
                enabled = !isSaving,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            form.members.forEach { member ->
                PillChip(
                    label = "@${member.username}",
                    onRemove = { onAction(Action.CreateTrip.MemberRemoved(member.username)) },
                    leading = { AvatarCircle(name = member.displayName, size = AvatarSize.Small) },
                    error = form.memberErrors[member.username]?.asString(),
                    enabled = !isSaving,
                )
            }
        }
    }
}

/** Who can be added, and how everyone else joins; shown where there are no friends to pick. */
@Composable
internal fun FriendsOnlyHint(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(Res.string.trip_friends_only_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Friends as avatars with their handle; tapping one picks or unpicks them. Hidden without friends. */
@Composable
internal fun FriendPickerRow(
    friends: ImmutableList<UserSummary>,
    isPicked: (UserSummary) -> Boolean,
    onToggle: (UserSummary) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    if (friends.isEmpty()) return
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)) {
        items(friends, key = { it.id }) { friend ->
            AvatarChoice(
                modifier = Modifier.animateItem(),
                name = friend.displayName,
                handle = friend.username,
                selected = isPicked(friend),
                onToggle = { onToggle(friend) },
                enabled = enabled,
            )
        }
    }
}

/** Friends with one picked, a picked person who's no longer a friend, and no friends yet. */
internal class CreateTripMembersPreviewStates : PreviewParameterProvider<CreateTripForm> {
    override val values = sequenceOf(createTripFormPreviewData, createTripFormWithErrorsPreviewData, createTripFormPreviewData.copy(members = persistentListOf(), friends = persistentListOf()))
}

@Preview
@Composable
private fun CreateTripMembersPreview(@PreviewParameter(CreateTripMembersPreviewStates::class) form: CreateTripForm) {
    TripinglyTheme {
        CreateTripMembers(form = form, isSaving = false, onAction = {}, modifier = Modifier.background(MaterialTheme.colorScheme.surface))
    }
}

@Preview
@Composable
private fun CreateTripMembersSavingPreview() {
    TripinglyTheme(darkTheme = true) {
        CreateTripMembers(form = createTripFormPreviewData, isSaving = true, onAction = {}, modifier = Modifier.background(MaterialTheme.colorScheme.surface))
    }
}

@Preview
@Composable
private fun FriendPickerRowPreview() {
    TripinglyTheme {
        FriendPickerRow(
            friends = friendsPreviewData,
            isPicked = { it.username == "anna" },
            onToggle = {},
            enabled = true,
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
        )
    }
}
