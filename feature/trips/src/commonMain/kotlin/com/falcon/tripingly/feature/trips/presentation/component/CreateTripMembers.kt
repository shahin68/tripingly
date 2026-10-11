package com.falcon.tripingly.feature.trips.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.falcon.tripingly.core.designsystem.component.SectionLabel
import com.falcon.tripingly.core.designsystem.component.SuggestionItem
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.feature.trips.generated.resources.Res
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_member_placeholder
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_members_limit
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_section_members
import com.falcon.tripingly.feature.trips.presentation.screen.CreateTripForm
import com.falcon.tripingly.feature.trips.presentation.screen.HomeViewModel.Action
import org.jetbrains.compose.resources.stringResource

/** "Invite friends": a row of friends to tap, a username search, and the picked people as chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CreateTripMembers(
    form: CreateTripForm,
    isSaving: Boolean,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        SectionLabel(stringResource(Res.string.create_trip_section_members))
        if (form.friends.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)) {
                items(form.friends, key = { it.id }) { friend ->
                    val selected = form.isMember(friend)
                    AvatarChoice(
                        name = friend.displayName,
                        key = friend.username,
                        selected = selected,
                        onToggle = { onAction(Action.OnFriendToggled(friend)) },
                        enabled = !isSaving && (selected || form.canAddMembers),
                    )
                }
            }
        }
        RoundedTextField(
            value = form.memberQuery,
            onValueChange = { onAction(Action.OnMemberQueryChanged(it)) },
            placeholder = stringResource(Res.string.create_trip_member_placeholder),
            leadingIcon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
            trailingIcon = if (form.isSearchingMembers) {
                { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
            } else {
                null
            },
            enabled = !isSaving && form.canAddMembers,
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
                onClick = { onAction(Action.OnMemberAdded(user)) },
            )
        }
        if (!form.canAddMembers) {
            Text(
                text = stringResource(Res.string.create_trip_members_limit, CreateTripForm.MAX_MEMBERS),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (form.members.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            ) {
                form.members.forEach { member ->
                    PillChip(
                        label = "@${member.username}",
                        onRemove = { onAction(Action.OnMemberRemoved(member.username)) },
                        leading = { AvatarCircle(name = member.displayName, key = member.username, size = AvatarSize.Small) },
                        error = form.memberErrors[member.username]?.asString(),
                        enabled = !isSaving,
                    )
                }
            }
        }
    }
}
