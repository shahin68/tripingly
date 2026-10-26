package com.falcon.tripingly.feature.trips.presentation.members

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.core.common.util.ShareManager
import com.falcon.tripingly.core.designsystem.component.ButtonSize
import com.falcon.tripingly.core.designsystem.component.MessageBannerHost
import com.falcon.tripingly.core.designsystem.component.PrimaryButton
import com.falcon.tripingly.core.designsystem.component.SecondaryButton
import com.falcon.tripingly.core.designsystem.component.SectionLabel
import com.falcon.tripingly.core.designsystem.component.SheetHeader
import com.falcon.tripingly.core.designsystem.component.ShowMessage
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.core.ui.ObserveAsEvents
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.trips.generated.resources.Res
import com.falcon.tripingly.feature.trips.generated.resources.members_add_friends
import com.falcon.tripingly.feature.trips.generated.resources.members_invite_create
import com.falcon.tripingly.feature.trips.generated.resources.members_invite_hint
import com.falcon.tripingly.feature.trips.generated.resources.members_invite_share_text
import com.falcon.tripingly.feature.trips.generated.resources.members_invites_title
import com.falcon.tripingly.feature.trips.generated.resources.members_join_requests_title
import com.falcon.tripingly.feature.trips.generated.resources.members_title
import com.falcon.tripingly.feature.trips.presentation.component.FriendPickerRow
import com.falcon.tripingly.feature.trips.presentation.members.TripMembersViewModel.Action
import com.falcon.tripingly.feature.trips.presentation.members.TripMembersViewModel.Event
import com.falcon.tripingly.feature.trips.presentation.members.TripMembersViewModel.State
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

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
            TripMembersScreen(state = state, onAction = viewModel::onAction, onClose = onDismiss)
            MessageBannerHost(Modifier.align(Alignment.TopCenter))
        }
    }
}

/**
 * Who edits the trip, with what its owner manages under them: requests to join, adding friends or a username, and
 * invite links.
 */
@Composable
fun TripMembersScreen(
    state: State,
    onAction: (Action) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ShowMessage(message = state.message?.asString(), onShown = { onAction(Action.DismissMessage) }, isError = state.message !is UiText.Note)
    val rowPadding = Modifier.padding(horizontal = MaterialTheme.spacing.large)
    LazyColumn(
        modifier = modifier.fillMaxWidth().navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        item { SheetHeader(title = stringResource(Res.string.members_title), onClose = onClose) }
        if (state.isLoading) {
            items(SKELETON_ROWS, key = { "skeleton$it" }) { PersonSkeleton(rowPadding) }
        }
        if (state.joinRequests.requests.isNotEmpty()) {
            item { SectionLabel(stringResource(Res.string.members_join_requests_title), rowPadding) }
            items(state.joinRequests.requests, key = { "request:${it.id}" }) { request ->
                JoinRequestRow(
                    modifier = Modifier.animateItem(),
                    request = request,
                    isAnswering = request.id in state.joinRequests.answeringIds,
                    onAccept = { onAction(Action.JoinRequests.Accept(request)) },
                    onDecline = { onAction(Action.JoinRequests.Decline(request)) },
                )
            }
            item { HorizontalDivider(Modifier.padding(vertical = MaterialTheme.spacing.small)) }
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
                        modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium),
                    )
                }
            }
            if (state.add.pickedFriends.isNotEmpty()) {
                item {
                    PrimaryButton(
                        text = stringResource(Res.string.members_add_friends, state.add.pickedFriends.size),
                        onClick = { onAction(Action.Add.PickedFriends) },
                        isLoading = state.add.isAdding,
                        modifier = rowPadding.fillMaxWidth(),
                    )
                }
            }
            item { AddMemberField(state.add, onAction) }
            item {
                HorizontalDivider(Modifier.padding(vertical = MaterialTheme.spacing.small))
                Column(rowPadding, verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)) {
                    SectionLabel(stringResource(Res.string.members_invites_title))
                    Text(
                        text = stringResource(Res.string.members_invite_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                SecondaryButton(
                    text = stringResource(Res.string.members_invite_create),
                    onClick = { onAction(Action.Invites.Create) },
                    isLoading = state.invites.isCreating,
                    size = ButtonSize.Medium,
                    icon = Icons.Default.Link,
                    modifier = rowPadding.fillMaxWidth(),
                )
            }
        }
    }
}

private const val SKELETON_ROWS = 3

@Preview
@Composable
private fun TripMembersScreenPreview(@PreviewParameter(TripMembersPreviewStates::class) state: State) {
    TripinglyTheme { TripMembersScreen(state = state, onAction = {}, onClose = {}, modifier = Modifier.background(MaterialTheme.colorScheme.surface)) }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun TripMembersScreenLandscapePreview() {
    TripinglyTheme(darkTheme = true) {
        TripMembersScreen(state = TripMembersPreviewStates().values.first(), onAction = {}, onClose = {}, modifier = Modifier.background(MaterialTheme.colorScheme.surface))
    }
}
