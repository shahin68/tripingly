package com.falcon.tripingly.feature.trips.presentation.members

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.core.common.util.ShareManager
import com.falcon.tripingly.core.designsystem.component.AppTextField
import com.falcon.tripingly.core.designsystem.component.AppTextFieldStyle
import com.falcon.tripingly.core.designsystem.component.LocalSheetBack
import com.falcon.tripingly.core.designsystem.component.MessageBannerHost
import com.falcon.tripingly.core.designsystem.component.SheetHeader
import com.falcon.tripingly.core.designsystem.component.ShowMessage
import com.falcon.tripingly.core.designsystem.component.Skeleton
import com.falcon.tripingly.core.designsystem.component.bottomSystemBar
import com.falcon.tripingly.core.designsystem.component.rememberHaptics
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.trip.JoinRequest
import com.falcon.tripingly.core.model.trip.TripMember
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.core.ui.ObserveAsEvents
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.timeAgo
import com.falcon.tripingly.feature.trips.generated.resources.Res
import com.falcon.tripingly.feature.trips.generated.resources.members_added
import com.falcon.tripingly.feature.trips.generated.resources.members_count
import com.falcon.tripingly.feature.trips.generated.resources.members_friends_none
import com.falcon.tripingly.feature.trips.generated.resources.members_friends_title
import com.falcon.tripingly.feature.trips.generated.resources.members_invite_share_text
import com.falcon.tripingly.feature.trips.generated.resources.members_join_requests_title
import com.falcon.tripingly.feature.trips.generated.resources.members_on_trip_title
import com.falcon.tripingly.feature.trips.generated.resources.members_search
import com.falcon.tripingly.feature.trips.generated.resources.members_search_everyone
import com.falcon.tripingly.feature.trips.generated.resources.members_search_hint
import com.falcon.tripingly.feature.trips.generated.resources.members_search_none
import com.falcon.tripingly.feature.trips.generated.resources.members_status_asking
import com.falcon.tripingly.feature.trips.generated.resources.members_status_on_trip
import com.falcon.tripingly.feature.trips.generated.resources.members_title
import com.falcon.tripingly.feature.trips.generated.resources.members_undo
import com.falcon.tripingly.feature.trips.presentation.members.TripMembersViewModel.Action
import com.falcon.tripingly.feature.trips.presentation.members.TripMembersViewModel.Event
import com.falcon.tripingly.feature.trips.presentation.members.TripMembersViewModel.SearchState
import com.falcon.tripingly.feature.trips.presentation.members.TripMembersViewModel.State
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

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

    val haptics = rememberHaptics()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is Event.ShareInvite -> shareManager.share(getString(Res.string.members_invite_share_text, event.tripName, event.url))
            Event.Accepted -> haptics.success()
        }
    }

    Box {
        TripMembersScreen(state = state, onAction = viewModel::onAction, onClose = onDismiss)
        MessageBannerHost(Modifier.align(Alignment.TopCenter))
    }
}

/**
 * Who is on the trip (design: the members sheet canvas, board "Search icon in the header", Shahin 2026-11-18). Cards:
 * people asking to join (the owner's, only while there are any), the people on the trip, and the owner's friends to
 * add with a tap; 🔍 opens the Add people page and the invite link is pinned at the bottom. The sheet is as high as
 * it can be from the start, so nothing that loads makes it jump; an editor sees only who is on the trip.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TripMembersScreen(
    state: State,
    onAction: (Action) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ShowMessage(message = state.message?.asString(), onShown = { onAction(Action.DismissMessage) }, isError = state.message !is UiText.Note)
    val added = state.added
    ShowMessage(
        message = added?.let { stringResource(Res.string.members_added, it.displayName) },
        onShown = { onAction(Action.DismissAdded) },
        isError = false,
        actionLabel = stringResource(Res.string.members_undo),
        onAction = { added?.let { onAction(Action.Undo(it)) } },
    )
    if (state.search != null) BackHandler { onAction(Action.Search.Close) }
    // The page leaving keeps its own search while it fades out.
    AnimatedContent(
        targetState = state.search,
        modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.bottomSystemBar),
        contentKey = { it != null },
        label = "membersPage",
    ) { search ->
        if (search != null) AddPeoplePage(state, search, onAction) else MembersPage(state, onAction, onClose)
    }
}

@Composable
private fun MembersPage(state: State, onAction: (Action) -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        SheetHeader(
            title = stringResource(Res.string.members_title),
            subtitle = listOfNotNull(
                pluralStringResource(Res.plurals.members_count, state.members.size, state.members.size).takeUnless { state.isLoading },
                state.tripName.takeIf { it.isNotBlank() },
            ).joinToString(" · "),
            onClose = onClose,
            actions = {
                if (state.canManage) {
                    FilledTonalIconButton(onClick = { onAction(Action.Search.Open) }) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(Res.string.members_search))
                    }
                }
            },
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = MaterialTheme.spacing.small)) {
            if (state.isLoading) skeletonCards(state.canManage) else memberCards(state, onAction)
        }
        if (state.canManage) {
            InvitePill(
                isCreating = state.isCreatingInvite,
                onShare = { onAction(Action.ShareInvite) },
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium).padding(bottom = MaterialTheme.spacing.medium),
            )
        }
    }
}

private fun LazyListScope.memberCards(state: State, onAction: (Action) -> Unit) {
    val requests = state.joinRequests.requests
    if (state.canManage && requests.isNotEmpty()) {
        card(
            key = "requests",
            title = { CardTitle("${stringResource(Res.string.members_join_requests_title)} · ${requests.size}", hasNews = true) },
            entries = requests,
            entryKey = JoinRequest::id,
        ) { request ->
            JoinRequestRow(
                request = request,
                detail = "@${request.user.username} · ${timeAgo(request.createdAt)}",
                isAnswering = request.id in state.joinRequests.answeringIds,
                onAccept = { onAction(Action.JoinRequests.Accept(request)) },
                onDecline = { onAction(Action.JoinRequests.Decline(request)) },
            )
        }
    }
    card(
        key = "members",
        title = { CardTitle("${stringResource(Res.string.members_on_trip_title)} · ${state.members.size}") },
        entries = state.members,
        entryKey = { it.user.username },
    ) { member ->
        MemberRow(
            member = member,
            canRemove = state.canManage && member.role != TripRole.OWNER,
            onRemove = { onAction(Action.RemoveMember(member.user)) },
            reserveMenu = state.canManage,
        )
    }
    val friends = state.friends
    if (state.canManage && friends != null) {
        card(
            key = "friends",
            title = { CardTitle(stringResource(Res.string.members_friends_title)) },
            entries = listOfNotNull(friends.takeIf { it.isNotEmpty() }),
            entryKey = { "faces" },
            empty = {
                Text(
                    stringResource(Res.string.members_friends_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = MaterialTheme.spacing.extraSmall, vertical = MaterialTheme.spacing.small),
                )
            },
        ) { FriendFaces(it, onAdd = { friend -> onAction(Action.AddPerson(friend)) }) }
    }
}

/** The cards as they will be, with placeholder people, while everything loads together. */
private fun LazyListScope.skeletonCards(canManage: Boolean) {
    item(key = "skeleton") {
        Skeleton {
            Column {
                repeat(if (canManage) 2 else 1) {
                    CardPart(isFirst = true, isLast = false) { CardTitle(SKELETON_TITLE) }
                    repeat(SKELETON_ROWS) { index ->
                        CardPart(isFirst = false, isLast = index == SKELETON_ROWS - 1) {
                            MemberRow(PlaceholderMember, canRemove = false, onRemove = {}, reserveMenu = canManage)
                        }
                    }
                    Spacer(Modifier.padding(top = MaterialTheme.spacing.small))
                }
                if (canManage) {
                    CardPart(isFirst = true, isLast = false) { CardTitle(SKELETON_TITLE) }
                    CardPart(isFirst = false, isLast = true) { FriendFaces(List(SKELETON_FACES) { PlaceholderMember.user.copy(id = "placeholder$it") }, onAdd = {}) }
                }
            }
        }
    }
}

/**
 * Add people: a search field for friends (matched as you type) and anyone on Tripinly by name or @username (asked
 * for once typing pauses). Each person shows Add, or that they're on the trip or asking to join already. Back (the
 * header's arrow or the system back) returns to the members.
 */
@Composable
private fun AddPeoplePage(state: State, search: SearchState, onAction: (Action) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val query = search.query.text.trim().removePrefix("@").toString()
    val friends = state.friends.orEmpty().filter { query.isEmpty() || it.username.contains(query, ignoreCase = true) || it.displayName.contains(query, ignoreCase = true) }
    val friendIds = friends.mapTo(HashSet()) { it.id }
    val everyone = search.results.filterNot { it.id in friendIds }
    val onTrip = stringResource(Res.string.members_status_on_trip)
    val asking = stringResource(Res.string.members_status_asking)
    val status = { user: UserSummary ->
        when (user.username) {
            in state.onTrip -> onTrip
            in state.asking -> asking
            else -> null
        }
    }
    Column(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalSheetBack provides { onAction(Action.Search.Close) }) {
            SheetHeader(title = stringResource(Res.string.members_search), onClose = { onAction(Action.Search.Close) })
        }
        AppTextField(
            state = search.query,
            placeholder = stringResource(Res.string.members_search_hint),
            style = AppTextFieldStyle.Search,
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium).fillMaxWidth().focusRequester(focus),
        )
        Box(Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.large).padding(vertical = MaterialTheme.spacing.extraSmall)) {
            if (search.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = MaterialTheme.spacing.medium)) {
            if (friends.isNotEmpty()) {
                card(key = "friends", title = { CardTitle(stringResource(Res.string.members_friends_title)) }, entries = friends, entryKey = UserSummary::id) { user ->
                    FoundPersonRow(user, status(user), onAdd = { onAction(Action.AddPerson(user)) })
                }
            }
            if (everyone.isNotEmpty()) {
                card(key = "everyone", title = { CardTitle(stringResource(Res.string.members_search_everyone)) }, entries = everyone, entryKey = UserSummary::id) { user ->
                    FoundPersonRow(user, status(user), onAdd = { onAction(Action.AddPerson(user)) })
                }
            }
            if (friends.isEmpty() && everyone.isEmpty() && query.isNotEmpty() && !search.isLoading) {
                item(key = "none") {
                    Text(
                        stringResource(Res.string.members_search_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.large),
                    )
                }
            }
        }
    }
}

private const val SKELETON_ROWS = 2
private const val SKELETON_FACES = 4
private const val SKELETON_TITLE = "Asking to join"
private val PlaceholderMember = TripMember(UserSummary("placeholder", "traveller", "Alex Traveller"), TripRole.EDITOR)

@Preview
@Composable
private fun TripMembersScreenPreview(@PreviewParameter(TripMembersPreviewStates::class) state: State) {
    TripinglyTheme { TripMembersScreen(state = state, onAction = {}, onClose = {}, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) }
}

@Preview
@Composable
private fun TripMembersScreenDarkPreview() {
    TripinglyTheme(darkTheme = true) {
        TripMembersScreen(state = TripMembersPreviewStates().values.first(), onAction = {}, onClose = {}, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow))
    }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun TripMembersScreenLandscapePreview() {
    TripinglyTheme(darkTheme = true) {
        TripMembersScreen(state = TripMembersPreviewStates().values.first(), onAction = {}, onClose = {}, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow))
    }
}
