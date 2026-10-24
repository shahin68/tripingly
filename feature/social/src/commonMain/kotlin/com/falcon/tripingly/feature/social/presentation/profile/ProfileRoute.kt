package com.falcon.tripingly.feature.social.presentation.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.core.designsystem.component.AppDropdownMenu
import com.falcon.tripingly.core.designsystem.component.AvatarCircle
import com.falcon.tripingly.core.designsystem.component.DropdownAction
import com.falcon.tripingly.core.designsystem.component.ShowMessage
import com.falcon.tripingly.core.designsystem.component.ReportDialog
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.social.PeopleList
import com.falcon.tripingly.core.model.social.Profile
import com.falcon.tripingly.core.ui.ObserveAsEvents
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_back
import com.falcon.tripingly.feature.social.generated.resources.social_block
import com.falcon.tripingly.feature.social.generated.resources.social_block_confirm
import com.falcon.tripingly.feature.social.generated.resources.social_block_message
import com.falcon.tripingly.feature.social.generated.resources.social_block_title
import com.falcon.tripingly.feature.social.generated.resources.social_cancel
import com.falcon.tripingly.feature.social.generated.resources.social_follow
import com.falcon.tripingly.feature.social.generated.resources.social_followers
import com.falcon.tripingly.feature.social.generated.resources.social_following
import com.falcon.tripingly.feature.social.generated.resources.social_following_button
import com.falcon.tripingly.feature.social.generated.resources.social_follows_you
import com.falcon.tripingly.feature.social.generated.resources.social_friends
import com.falcon.tripingly.feature.social.generated.resources.social_more
import com.falcon.tripingly.feature.social.generated.resources.social_profile_no_trips
import com.falcon.tripingly.feature.social.generated.resources.social_profile_not_found
import com.falcon.tripingly.feature.social.generated.resources.social_public_trips
import com.falcon.tripingly.feature.social.generated.resources.social_report_user
import com.falcon.tripingly.feature.social.generated.resources.social_retry
import com.falcon.tripingly.feature.social.generated.resources.social_settings
import com.falcon.tripingly.feature.social.presentation.component.PublicTripCard
import com.falcon.tripingly.feature.social.presentation.profile.ProfileViewModel.Action
import com.falcon.tripingly.feature.social.presentation.profile.ProfileViewModel.Event
import com.falcon.tripingly.feature.social.presentation.profile.ProfileViewModel.State
import com.falcon.tripingly.feature.social.presentation.social.Centered
import com.falcon.tripingly.feature.social.presentation.social.EmptyText
import com.falcon.tripingly.feature.social.presentation.social.LoadMoreAtEnd
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import androidx.compose.ui.tooling.preview.Preview
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.foundation.background

/** A person's profile; [username] null is my own. */
@Composable
fun ProfileRoute(
    username: String?,
    onBack: () -> Unit,
    onOpenTrip: (tripId: String) -> Unit,
    onOpenList: (PeopleList) -> Unit,
    onOpenSettings: () -> Unit,
    // Keyed by person, like the trip map: the class alone would hand everyone the first profile's view model.
    viewModel: ProfileViewModel = koinViewModel(key = "profile:${username.orEmpty()}") { parametersOf(username) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is Event.OpenTrip -> onOpenTrip(event.tripId)
            is Event.OpenList -> onOpenList(event.list)
            Event.Close -> onBack()
        }
    }

    ProfileScreen(state = state, onAction = viewModel::onAction, onBack = onBack, onOpenSettings = onOpenSettings)

    state.report?.let { report ->
        ReportDialog(
            onReport = { reason, details -> viewModel.onAction(Action.Report.Send(reason, details)) },
            onDismiss = { viewModel.onAction(Action.Report.Dismiss) },
            isSending = report.isSending,
        )
    }
    if (state.isConfirmingBlock) {
        val name = state.profile?.user?.username.orEmpty()
        AlertDialog(
            onDismissRequest = { viewModel.onAction(Action.Person.DismissBlock) },
            title = { Text(stringResource(Res.string.social_block_title, name)) },
            text = { Text(stringResource(Res.string.social_block_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.onAction(Action.Person.ConfirmBlock) }) {
                    Text(stringResource(Res.string.social_block_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onAction(Action.Person.DismissBlock) }) { Text(stringResource(Res.string.social_cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileScreen(state: State, onAction: (Action) -> Unit, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.profile?.user?.let { "@${it.username}" }.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.social_back))
                    }
                },
                actions = {
                    if (state.isMe) {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(Res.string.social_settings))
                        }
                    } else if (state.profile != null) {
                        ProfileMenu(onAction)
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ShowMessage(
                message = state.message?.asString(),
                onShown = { onAction(Action.DismissMessage) },
                isError = state.message !is UiText.Note,
            )
            val profile = state.profile
            when {
                profile != null -> ProfileContent(profile, state, onAction)
                state.isLoading -> Centered { CircularProgressIndicator() }
                state.isNotFound -> Centered { EmptyText(stringResource(Res.string.social_profile_not_found)) }
                else -> Centered { TextButton(onClick = { onAction(Action.Retry) }) { Text(stringResource(Res.string.social_retry)) } }
            }
        }
    }
}

@Composable
private fun ProfileMenu(onAction: (Action) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.social_more))
        }
        AppDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            actions = listOf(
                DropdownAction(stringResource(Res.string.social_report_user), isDestructive = true) { onAction(Action.Person.Report) },
                DropdownAction(stringResource(Res.string.social_block), isDestructive = true) { onAction(Action.Person.Block) },
            ),
        )
    }
}

@Composable
private fun ProfileContent(profile: Profile, state: State, onAction: (Action) -> Unit) {
    val listState = rememberLazyListState()
    LoadMoreAtEnd(listState) { onAction(Action.Trips.LoadMore) }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        item { ProfileHeader(profile, state.isMe, onAction) }
        item {
            Text(
                text = stringResource(Res.string.social_public_trips, profile.publicTripCount),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (state.trips.isEmpty()) {
            item { EmptyText(stringResource(Res.string.social_profile_no_trips)) }
        }
        items(state.trips, key = { it.trip.id }) { trip ->
            PublicTripCard(
                modifier = Modifier.animateItem(),
                trip = trip,
                onOpen = { onAction(Action.Trips.Click(trip)) },
                onOpenOwner = {},
                onLike = { onAction(Action.Trips.Like(trip)) },
                onToggleSave = { onAction(Action.Trips.ToggleSave(trip)) },
                onReport = { onAction(Action.Trips.Report(trip)) },
                showOwner = false,
            )
        }
    }
}

@Composable
private fun ProfileHeader(profile: Profile, isMe: Boolean, onAction: (Action) -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        AvatarCircle(name = profile.user.displayName, key = profile.user.username)
        Text(
            text = profile.user.displayName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = MaterialTheme.spacing.small),
        )
        if (profile.followsMe && !isMe) {
            Text(
                text = stringResource(Res.string.social_follows_you),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.padding(vertical = MaterialTheme.spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
        ) {
            // Only my own lists can be opened: the server lists them for me alone.
            Count(profile.followerCount, stringResource(Res.string.social_followers), onClick = if (isMe) ({ onAction(Action.OpenList(PeopleList.Followers)) }) else null)
            Count(profile.followingCount, stringResource(Res.string.social_following), onClick = if (isMe) ({ onAction(Action.OpenList(PeopleList.Following)) }) else null)
        }
        if (isMe) {
            OutlinedButton(onClick = { onAction(Action.OpenList(PeopleList.Friends)) }) {
                Text(stringResource(Res.string.social_friends))
            }
        } else if (profile.isFollowing) {
            OutlinedButton(onClick = { onAction(Action.Person.ToggleFollow) }) { Text(stringResource(Res.string.social_following_button)) }
        } else {
            Button(onClick = { onAction(Action.Person.ToggleFollow) }) { Text(stringResource(Res.string.social_follow)) }
        }
    }
}

@Composable
private fun Count(count: Int, label: String, onClick: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = MaterialTheme.spacing.small, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(count.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Preview
@Composable
private fun ProfileScreenPreview(@PreviewParameter(ProfileScreenPreviewStates::class) state: State) {
    TripinglyTheme { ProfileScreen(state = state, onAction = {}, onBack = {}, onOpenSettings = {}) }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun ProfileScreenLandscapePreview() {
    TripinglyTheme(darkTheme = true) {
        ProfileScreen(state = ProfileScreenPreviewStates().values.first(), onAction = {}, onBack = {}, onOpenSettings = {})
    }
}

@Preview
@Composable
private fun ProfileHeaderPreview(@PreviewParameter(ProfileHeaderPreviewStates::class) profile: Profile) {
    TripinglyTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.surface)) { ProfileHeader(profile = profile, isMe = false, onAction = {}) }
    }
}
