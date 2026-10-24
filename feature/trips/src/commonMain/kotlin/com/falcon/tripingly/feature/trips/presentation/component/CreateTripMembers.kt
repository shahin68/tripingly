package com.falcon.tripingly.feature.trips.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.AvatarChoice
import com.falcon.tripingly.core.designsystem.component.AvatarCircle
import com.falcon.tripingly.core.designsystem.component.AvatarSize
import com.falcon.tripingly.core.designsystem.component.PillChip
import com.falcon.tripingly.core.designsystem.component.RoundedTextField
import com.falcon.tripingly.core.designsystem.component.SuggestionItem
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.feature.trips.generated.resources.Res
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_add_by_username
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_member_placeholder
import com.falcon.tripingly.feature.trips.presentation.screen.CreateTripForm
import com.falcon.tripingly.feature.trips.presentation.screen.HomeViewModel.Action
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import androidx.compose.foundation.background

/**
 * People to add: a row of friends to tap, an "Add by username" chip that opens a username search,
 * and the picked people as chips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CreateTripMembers(
    form: CreateTripForm,
    isSaving: Boolean,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searching by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        FriendPickerRow(
            friends = form.friends,
            isPicked = form::isMember,
            onToggle = { onAction(Action.CreateTrip.FriendToggled(it)) },
            enabled = !isSaving,
        )
        if (searching || form.memberQuery.isNotEmpty()) {
            RoundedTextField(
                value = form.memberQuery,
                onValueChange = { onAction(Action.CreateTrip.MemberQueryChanged(it)) },
                placeholder = stringResource(Res.string.create_trip_member_placeholder),
                leadingIcon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                trailingIcon = if (form.isSearchingMembers) {
                    { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
                } else {
                    null
                },
                enabled = !isSaving,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            form.memberResults.filterNot(form::isMember).forEach { user ->
                SuggestionItem(
                    title = user.displayName,
                    subtitle = "@${user.username}",
                    onClick = { onAction(Action.CreateTrip.MemberAdded(user)) },
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            form.members.forEach { member ->
                PillChip(
                    label = "@${member.username}",
                    onRemove = { onAction(Action.CreateTrip.MemberRemoved(member.username)) },
                    leading = { AvatarCircle(name = member.displayName, key = member.username, size = AvatarSize.Small) },
                    error = form.memberErrors[member.username]?.asString(),
                    enabled = !isSaving,
                )
            }
            if (!searching && form.memberQuery.isEmpty()) {
                PillChip(
                    label = stringResource(Res.string.create_trip_add_by_username),
                    onClick = { searching = true },
                    leading = {
                        Icon(
                            Icons.Default.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.padding(start = MaterialTheme.spacing.small).size(18.dp),
                        )
                    },
                    enabled = !isSaving,
                )
            }
        }
    }
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
                key = friend.username,
                selected = isPicked(friend),
                onToggle = { onToggle(friend) },
                enabled = enabled,
            )
        }
    }
}

/** Friends with one picked and a person added by username, and a username that doesn't exist. */
internal class CreateTripMembersPreviewStates : PreviewParameterProvider<CreateTripForm> {
    override val values = sequenceOf(createTripFormPreviewData, createTripFormWithErrorsPreviewData)
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
            friends = createTripFormPreviewData.friends,
            isPicked = { it.username == "anna" },
            onToggle = {},
            enabled = true,
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
        )
    }
}
