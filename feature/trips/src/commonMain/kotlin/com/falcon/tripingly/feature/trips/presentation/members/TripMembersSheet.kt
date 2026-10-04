package com.falcon.tripingly.feature.trips.presentation.members

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.core.common.util.DateUtils
import com.falcon.tripingly.core.common.util.ShareManager
import com.falcon.tripingly.core.designsystem.component.ErrorBanner
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.model.trip.TripInvite
import com.falcon.tripingly.core.model.trip.TripMember
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.core.ui.ObserveAsEvents
import com.falcon.tripingly.feature.trips.generated.resources.Res
import com.falcon.tripingly.feature.trips.generated.resources.members_add_button
import com.falcon.tripingly.feature.trips.generated.resources.members_add_label
import com.falcon.tripingly.feature.trips.generated.resources.members_invite_create
import com.falcon.tripingly.feature.trips.generated.resources.members_invite_expires
import com.falcon.tripingly.feature.trips.generated.resources.members_invite_hint
import com.falcon.tripingly.feature.trips.generated.resources.members_invite_revoke
import com.falcon.tripingly.feature.trips.generated.resources.members_invite_share
import com.falcon.tripingly.feature.trips.generated.resources.members_invite_share_text
import com.falcon.tripingly.feature.trips.generated.resources.members_invites_title
import com.falcon.tripingly.feature.trips.generated.resources.members_remove
import com.falcon.tripingly.feature.trips.generated.resources.members_role_editor
import com.falcon.tripingly.feature.trips.generated.resources.members_role_owner
import com.falcon.tripingly.feature.trips.generated.resources.members_title
import com.falcon.tripingly.feature.trips.presentation.members.TripMembersViewModel.Action
import com.falcon.tripingly.feature.trips.presentation.members.TripMembersViewModel.Event
import com.falcon.tripingly.feature.trips.presentation.members.TripMembersViewModel.State
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripMembersRoute(
    tripId: String,
    onDismiss: () -> Unit,
    viewModel: TripMembersViewModel = koinViewModel(key = "members:$tripId") { parametersOf(tripId) },
    shareManager: ShareManager = koinInject(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is Event.ShareInvite -> shareManager.share(getString(Res.string.members_invite_share_text, event.tripName, event.url))
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        TripMembersScreen(state = state, onAction = viewModel::onAction)
    }
}

@Composable
fun TripMembersScreen(
    state: State,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth().navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                text = stringResource(Res.string.members_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        state.message?.let { message ->
            item {
                ErrorBanner(
                    errorMessage = message.asString(),
                    onDismiss = { onAction(Action.OnDismissMessage) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        if (state.isLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
        items(state.members, key = { it.user.id }) { member ->
            MemberRow(
                member = member,
                canRemove = state.canManage && member.role != TripRole.OWNER,
                onRemove = { onAction(Action.OnRemoveMember(member.user.id)) },
            )
        }
        if (state.canManage) {
            item { AddMemberField(state, onAction) }
            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    text = stringResource(Res.string.members_invites_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Text(
                    text = stringResource(Res.string.members_invite_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            items(state.invites, key = { it.id }) { invite ->
                InviteRow(
                    invite = invite,
                    onShare = { onAction(Action.OnShareInvite(invite)) },
                    onRevoke = { onAction(Action.OnRevokeInvite(invite.id)) },
                )
            }
            item {
                OutlinedButton(
                    onClick = { onAction(Action.OnCreateInvite) },
                    enabled = !state.isCreatingInvite,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                ) {
                    if (state.isCreatingInvite) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(Res.string.members_invite_create))
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberRow(member: TripMember, canRemove: Boolean, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(member.user.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = "@${member.user.username} · " + stringResource(
                    if (member.role == TripRole.OWNER) Res.string.members_role_owner else Res.string.members_role_editor,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (canRemove) {
            TextButton(onClick = onRemove) {
                Text(stringResource(Res.string.members_remove), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun AddMemberField(state: State, onAction: (Action) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = state.username,
            onValueChange = { onAction(Action.OnUsernameChanged(it)) },
            label = { Text(stringResource(Res.string.members_add_label)) },
            singleLine = true,
            enabled = !state.isAdding,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onAction(Action.OnAddMember) }),
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { onAction(Action.OnAddMember) }, enabled = !state.isAdding && state.username.isNotBlank()) {
            if (state.isAdding) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(Res.string.members_add_button))
            }
        }
    }
}

@Composable
private fun InviteRow(invite: TripInvite, onShare: () -> Unit, onRevoke: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.members_invite_expires, invite.expiresAt.formatDate()),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onShare) { Text(stringResource(Res.string.members_invite_share)) }
        TextButton(onClick = onRevoke) {
            Text(stringResource(Res.string.members_invite_revoke), color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun Instant.formatDate(): String =
    DateUtils.formatFormal(toLocalDateTime(TimeZone.currentSystemDefault()).date)

@Preview
@Composable
private fun TripMembersScreenPreview() {
    TripinglyTheme {
        TripMembersScreen(
            state = State(
                tripName = "Summer Vacation in Paris",
                canManage = true,
                isLoading = false,
                members = persistentListOf(
                    TripMember(UserSummary("1", "shahin", "Shahin"), TripRole.OWNER),
                    TripMember(UserSummary("2", "anna", "Anna"), TripRole.EDITOR),
                ),
                invites = persistentListOf(
                    TripInvite("i1", "https://example.com/i/abc", Instant.parse("2025-06-08T10:00:00Z")),
                ),
            ),
            onAction = {},
        )
    }
}
