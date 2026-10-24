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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.core.designsystem.component.MessageBannerHost
import com.falcon.tripingly.core.designsystem.component.PrimaryButton
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.trips.generated.resources.members_add_friends
import com.falcon.tripingly.feature.trips.presentation.component.FriendPickerRow
import com.falcon.tripingly.core.common.util.DateUtils
import com.falcon.tripingly.core.common.util.ShareManager
import com.falcon.tripingly.core.designsystem.component.ShowMessage
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.model.trip.JoinRequest
import com.falcon.tripingly.core.model.trip.TripInvite
import com.falcon.tripingly.core.model.trip.TripMember
import com.falcon.tripingly.core.model.trip.TripRole
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
import com.falcon.tripingly.feature.trips.generated.resources.members_join_accept
import com.falcon.tripingly.feature.trips.generated.resources.members_join_decline
import com.falcon.tripingly.feature.trips.generated.resources.members_join_requests_title
import com.falcon.tripingly.feature.trips.generated.resources.members_remove
import com.falcon.tripingly.feature.trips.generated.resources.members_role_editor
import com.falcon.tripingly.feature.trips.generated.resources.members_role_owner
import com.falcon.tripingly.feature.trips.generated.resources.members_title
import com.falcon.tripingly.feature.trips.presentation.members.TripMembersViewModel.Action
import com.falcon.tripingly.feature.trips.presentation.members.TripMembersViewModel.AddState
import com.falcon.tripingly.feature.trips.presentation.members.TripMembersViewModel.Event
import com.falcon.tripingly.feature.trips.presentation.members.TripMembersViewModel.State
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.foundation.background

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripMembersRoute(
    tripId: String,
    onDismiss: () -> Unit,
    viewModel: TripMembersViewModel = koinViewModel(key = "members:$tripId") { parametersOf(tripId) },
    shareManager: ShareManager = koinInject(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var isOpened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!isOpened) viewModel.onAction(Action.Opened)
        isOpened = true
    }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is Event.ShareInvite -> shareManager.share(getString(Res.string.members_invite_share_text, event.tripName, event.url))
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Box {
            TripMembersScreen(state = state, onAction = viewModel::onAction)
            MessageBannerHost(Modifier.align(Alignment.TopCenter))
        }
    }
}

@Composable
fun TripMembersScreen(
    state: State,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    ShowMessage(message = state.message?.asString(), onShown = { onAction(Action.DismissMessage) }, isError = state.message !is UiText.Note)
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
        if (state.isLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
        if (state.joinRequests.requests.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(Res.string.members_join_requests_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            items(state.joinRequests.requests, key = { "request:${it.id}" }) { request ->
                JoinRequestRow(
                    modifier = Modifier.animateItem(),
                    request = request,
                    isAnswering = request.id in state.joinRequests.answeringIds,
                    onAccept = { onAction(Action.JoinRequests.Accept(request)) },
                    onDecline = { onAction(Action.JoinRequests.Decline(request)) },
                )
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        }
        items(state.members, key = { it.user.id }) { member ->
            MemberRow(
                modifier = Modifier.animateItem(),
                member = member,
                canRemove = state.canManage && member.role != TripRole.OWNER,
                onRemove = { onAction(Action.RemoveMember(member.user.id)) },
            )
        }
        if (state.canManage) {
            if (state.add.friends.isNotEmpty()) {
                item {
                    FriendPickerRow(
                        friends = state.add.friends,
                        isPicked = { it.username in state.add.pickedFriends },
                        onToggle = { onAction(Action.Add.FriendToggled(it)) },
                        enabled = !state.add.isAdding,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            if (state.add.pickedFriends.isNotEmpty()) {
                item {
                    PrimaryButton(
                        text = stringResource(Res.string.members_add_friends, state.add.pickedFriends.size),
                        onClick = { onAction(Action.Add.PickedFriends) },
                        isLoading = state.add.isAdding,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    )
                }
            }
            item { AddMemberField(state.add, onAction) }
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
            items(state.invites.invites, key = { it.id }) { invite ->
                InviteRow(
                    modifier = Modifier.animateItem(),
                    invite = invite,
                    onShare = { onAction(Action.Invites.Share(invite)) },
                    onRevoke = { onAction(Action.Invites.Revoke(invite.id)) },
                )
            }
            item {
                OutlinedButton(
                    onClick = { onAction(Action.Invites.Create) },
                    enabled = !state.invites.isCreating,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                ) {
                    if (state.invites.isCreating) {
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
private fun MemberRow(member: TripMember, canRemove: Boolean, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp),
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
private fun JoinRequestRow(request: JoinRequest, isAnswering: Boolean, onAccept: () -> Unit, onDecline: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(request.user.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = "@${request.user.username}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isAnswering) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            TextButton(onClick = onDecline) {
                Text(stringResource(Res.string.members_join_decline), color = MaterialTheme.colorScheme.error)
            }
            TextButton(onClick = onAccept) { Text(stringResource(Res.string.members_join_accept)) }
        }
    }
}

@Composable
private fun AddMemberField(state: AddState, onAction: (Action) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = state.username,
            onValueChange = { onAction(Action.Add.UsernameChanged(it)) },
            label = { Text(stringResource(Res.string.members_add_label)) },
            singleLine = true,
            enabled = !state.isAdding,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onAction(Action.Add.ByUsername) }),
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { onAction(Action.Add.ByUsername) }, enabled = !state.isAdding && state.username.isNotBlank()) {
            if (state.isAdding) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(Res.string.members_add_button))
            }
        }
    }
}

@Composable
private fun InviteRow(invite: TripInvite, onShare: () -> Unit, onRevoke: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.members_invite_expires, DateUtils.formatFormal(invite.expiresAt)),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onShare) { Text(stringResource(Res.string.members_invite_share)) }
        TextButton(onClick = onRevoke) {
            Text(stringResource(Res.string.members_invite_revoke), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Preview
@Composable
private fun TripMembersScreenPreview(@PreviewParameter(TripMembersPreviewStates::class) state: State) {
    TripinglyTheme { TripMembersScreen(state = state, onAction = {}, modifier = Modifier.background(MaterialTheme.colorScheme.surface)) }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun TripMembersScreenLandscapePreview() {
    TripinglyTheme(darkTheme = true) {
        TripMembersScreen(state = TripMembersPreviewStates().values.first(), onAction = {}, modifier = Modifier.background(MaterialTheme.colorScheme.surface))
    }
}

/** The owner, who can't be removed, and an editor the owner can remove. */
@Preview
@Composable
private fun MemberRowPreview() {
    TripinglyTheme {
        Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
            previewMembers.forEach { member -> MemberRow(member = member, canRemove = member.role != TripRole.OWNER, onRemove = {}) }
        }
    }
}

/** A request to answer, and one whose answer waits for the server. */
@Preview
@Composable
private fun JoinRequestRowPreview() {
    TripinglyTheme {
        Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
            JoinRequestRow(previewJoinRequest, isAnswering = false, onAccept = {}, onDecline = {})
            JoinRequestRow(previewJoinRequest, isAnswering = true, onAccept = {}, onDecline = {})
        }
    }
}

/** Empty, and a username typed while it's being added. */
@Preview
@Composable
private fun AddMemberFieldPreview() {
    TripinglyTheme {
        Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
            AddMemberField(state = AddState(), onAction = {})
            AddMemberField(state = AddState(username = "jonas.k", isAdding = true), onAction = {})
        }
    }
}

@Preview
@Composable
private fun InviteRowPreview() {
    TripinglyTheme {
        InviteRow(invite = previewInvite, onShare = {}, onRevoke = {})
    }
}
