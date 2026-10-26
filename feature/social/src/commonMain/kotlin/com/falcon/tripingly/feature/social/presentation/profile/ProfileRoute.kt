package com.falcon.tripingly.feature.social.presentation.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.core.designsystem.component.AppDialog
import com.falcon.tripingly.core.designsystem.component.AppDropdownMenu
import com.falcon.tripingly.core.designsystem.component.BackAction
import com.falcon.tripingly.core.designsystem.component.DropdownAction
import com.falcon.tripingly.core.designsystem.component.ErrorScreen
import com.falcon.tripingly.core.designsystem.component.FloatingTopBar
import com.falcon.tripingly.core.designsystem.component.LoadStateContent
import com.falcon.tripingly.core.designsystem.component.ReportDialog
import com.falcon.tripingly.core.designsystem.component.ShowMessage
import com.falcon.tripingly.core.designsystem.component.glassSource
import com.falcon.tripingly.core.designsystem.component.screenContentPadding
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.social.Profile
import com.falcon.tripingly.core.ui.ObserveAsEvents
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_block
import com.falcon.tripingly.feature.social.generated.resources.social_block_confirm
import com.falcon.tripingly.feature.social.generated.resources.social_block_message
import com.falcon.tripingly.feature.social.generated.resources.social_block_title
import com.falcon.tripingly.feature.social.generated.resources.social_more
import com.falcon.tripingly.feature.social.generated.resources.social_profile_no_trips
import com.falcon.tripingly.feature.social.generated.resources.social_profile_not_found
import com.falcon.tripingly.feature.social.generated.resources.social_public_trips
import com.falcon.tripingly.feature.social.generated.resources.social_report_user
import com.falcon.tripingly.feature.social.presentation.component.EmptyText
import com.falcon.tripingly.feature.social.presentation.component.LoadMoreAtEnd
import com.falcon.tripingly.feature.social.presentation.component.PublicTripCard
import com.falcon.tripingly.feature.social.presentation.profile.ProfileViewModel.Action
import com.falcon.tripingly.feature.social.presentation.profile.ProfileViewModel.Event
import com.falcon.tripingly.feature.social.presentation.profile.ProfileViewModel.State
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * A person's profile; [username] null is my own, shown as the Profile tab (with people search and my lists, see
 * [MyProfileTab]). [bottomBarPadding] is the tab shell's floating bottom bar there.
 */
@Composable
fun ProfileRoute(
    username: String?,
    onBack: () -> Unit,
    onOpenTrip: (tripId: String) -> Unit,
    onOpenProfile: (username: String) -> Unit,
    onOpenSettings: () -> Unit,
    bottomBarPadding: PaddingValues = PaddingValues(),
    // Keyed by person, like the trip map: the class alone would hand everyone the first profile's view model.
    viewModel: ProfileViewModel = koinViewModel(key = "profile:${username.orEmpty()}") { parametersOf(username) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is Event.OpenTrip -> onOpenTrip(event.tripId)
            Event.Close -> onBack()
        }
    }

    if (username == null) {
        MyProfileTab(state = state, onAction = viewModel::onAction, onOpenProfile = onOpenProfile, onOpenSettings = onOpenSettings, bottomBarPadding = bottomBarPadding)
    } else {
        PersonProfileScreen(state = state, onAction = viewModel::onAction, onBack = onBack)
    }

    state.report?.let { report ->
        ReportDialog(
            onReport = { reason, details -> viewModel.onAction(Action.Report.Send(reason, details)) },
            onDismiss = { viewModel.onAction(Action.Report.Dismiss) },
            isSending = report.isSending,
        )
    }
    if (state.isConfirmingBlock) {
        BlockDialog(state.profile?.user?.username.orEmpty(), viewModel::onAction)
    }
}

/** Someone else's profile: a floating header with back and ⋮ (report, block), then them and their public trips. */
@Composable
internal fun PersonProfileScreen(state: State, onAction: (Action) -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            FloatingTopBar(
                title = state.profile?.user?.let { "@${it.username}" }.orEmpty(),
                navigationIcon = { BackAction(onBack) },
                actions = { if (state.profile != null && !state.isMe) ProfileMenu(onAction) },
            )
        },
    ) { padding ->
        val contentPadding = screenContentPadding(padding)
        ShowMessage(message = state.message?.asString(), onShown = { onAction(Action.DismissMessage) }, isError = state.message !is UiText.Note)
        if (state.isNotFound) {
            ErrorScreen(onRetry = null, message = stringResource(Res.string.social_profile_not_found), contentPadding = contentPadding)
            return@Scaffold
        }
        LoadStateContent(
            isLoading = state.profile == null && state.isLoading,
            errorMessage = state.loadError?.asString(),
            onRetry = { onAction(Action.Retry) },
            contentPadding = contentPadding,
        ) {
            state.profile?.let { PersonProfileContent(it, state, onAction, contentPadding) }
        }
    }
}

@Composable
private fun PersonProfileContent(profile: Profile, state: State, onAction: (Action) -> Unit, contentPadding: PaddingValues) {
    val listState = rememberLazyListState()
    LoadMoreAtEnd(listState) { onAction(Action.Trips.LoadMore) }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().glassSource(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        item(key = "header") { PersonProfileHeader(profile, onToggleFollow = { onAction(Action.Person.ToggleFollow) }.takeUnless { state.isMe }, modifier = Modifier.animateItem()) }
        item(key = "title") {
            Text(
                text = stringResource(Res.string.social_public_trips, profile.publicTripCount),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.animateItem(),
            )
        }
        if (state.trips.isEmpty()) {
            item(key = "empty") { EmptyText(stringResource(Res.string.social_profile_no_trips), Modifier.animateItem()) }
        }
        items(state.trips, key = { it.trip.id }) { trip ->
            PublicTripCard(
                trip = trip,
                onOpen = { onAction(Action.Trips.Click(trip)) },
                onLike = { onAction(Action.Trips.Like(trip)) },
                onToggleSave = { onAction(Action.Trips.ToggleSave(trip)) },
                onReport = { onAction(Action.Trips.Report(trip)) },
                modifier = Modifier.animateItem(),
            )
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
                DropdownAction(stringResource(Res.string.social_report_user), isDestructive = true, icon = Icons.Outlined.Flag) { onAction(Action.Person.Report) },
                DropdownAction(stringResource(Res.string.social_block), isDestructive = true, icon = Icons.Outlined.Block) { onAction(Action.Person.Block) },
            ),
        )
    }
}

@Composable
private fun BlockDialog(username: String, onAction: (Action) -> Unit) {
    AppDialog(
        title = stringResource(Res.string.social_block_title, username),
        message = stringResource(Res.string.social_block_message),
        confirmLabel = stringResource(Res.string.social_block_confirm),
        isDestructive = true,
        onConfirm = { onAction(Action.Person.ConfirmBlock) },
        onDismiss = { onAction(Action.Person.DismissBlock) },
    )
}

@Preview
@Composable
private fun PersonProfileScreenPreview(@PreviewParameter(ProfileScreenPreviewStates::class) state: State) {
    TripinglyTheme { PersonProfileScreen(state = state.copy(isMe = false), onAction = {}, onBack = {}) }
}

@Preview
@Composable
private fun PersonProfileScreenDarkPreview() {
    TripinglyTheme(darkTheme = true) { PersonProfileScreen(state = ProfileScreenPreviewStates().values.first(), onAction = {}, onBack = {}) }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun PersonProfileScreenLandscapePreview() {
    TripinglyTheme { PersonProfileScreen(state = ProfileScreenPreviewStates().values.first(), onAction = {}, onBack = {}) }
}

@Preview
@Composable
private fun BlockDialogPreview() {
    TripinglyTheme { BlockDialog("anna", onAction = {}) }
}
