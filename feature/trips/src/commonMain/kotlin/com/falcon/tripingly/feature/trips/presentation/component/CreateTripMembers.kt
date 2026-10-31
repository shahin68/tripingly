package com.falcon.tripingly.feature.trips.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import com.falcon.tripingly.core.designsystem.component.FormField
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.feature.trips.generated.resources.Res
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_find_friends_hint
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_friends_label
import com.falcon.tripingly.feature.trips.generated.resources.trip_friends_only_hint
import com.falcon.tripingly.feature.trips.presentation.screen.CreateTripForm
import com.falcon.tripingly.feature.trips.presentation.screen.HomeViewModel.Action
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Friends to add (the only people an owner can add) under their label: one row of avatars, tapping checks or unchecks; without friends only a nudge to find some, no label. */
@Composable
internal fun CreateTripMembers(
    form: CreateTripForm,
    isSaving: Boolean,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        form.friends == null -> Unit
        form.friends.isEmpty() -> FriendsOnlyHint(modifier, text = Res.string.create_trip_find_friends_hint)
        else -> FormField(stringResource(Res.string.create_trip_friends_label), modifier) {
            FriendPickerRow(
                friends = form.friends,
                isPicked = form::isMember,
                onToggle = { onAction(Action.CreateTrip.FriendToggled(it)) },
                enabled = !isSaving,
            )
        }
    }
}

/** Shown where there are no friends to pick: by default who can be added and how everyone else joins. */
@Composable
internal fun FriendsOnlyHint(modifier: Modifier = Modifier, text: StringResource = Res.string.trip_friends_only_hint) {
    Text(
        text = stringResource(text),
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
                user = friend,
                selected = isPicked(friend),
                onToggle = { onToggle(friend) },
                enabled = enabled,
            )
        }
    }
}

/** Friends with one picked, and no friends yet. */
internal class CreateTripMembersPreviewStates : PreviewParameterProvider<CreateTripForm> {
    override val values = sequenceOf(createTripFormPreviewData, createTripFormPreviewData.copy(members = persistentListOf(), friends = persistentListOf()))
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
