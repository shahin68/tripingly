package com.falcon.tripingly.feature.social.presentation.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.core.designsystem.component.ErrorBanner
import com.falcon.tripingly.core.designsystem.component.ReportDialog
import com.falcon.tripingly.core.designsystem.component.SegmentedToggle
import com.falcon.tripingly.core.designsystem.component.SegmentedToggleOption
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.location.permission.rememberLocationPermissionController
import com.falcon.tripingly.core.ui.ObserveAsEvents
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_explore
import com.falcon.tripingly.feature.social.generated.resources.social_explore_empty
import com.falcon.tripingly.feature.social.generated.resources.social_nearby
import com.falcon.tripingly.feature.social.generated.resources.social_people_none
import com.falcon.tripingly.feature.social.presentation.component.PersonRow
import com.falcon.tripingly.feature.social.presentation.component.PublicTripCard
import com.falcon.tripingly.feature.social.presentation.social.SocialViewModel.Action
import com.falcon.tripingly.feature.social.presentation.social.SocialViewModel.Event
import com.falcon.tripingly.feature.social.presentation.social.SocialViewModel.Section
import com.falcon.tripingly.feature.social.presentation.social.SocialViewModel.State
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The Social tab's content under Home's search bar: [query] (what's typed there) finds people; otherwise
 * Explore and Nearby.
 */
@Composable
fun SocialRoute(
    query: String,
    onOpenTrip: (tripId: String) -> Unit,
    onOpenProfile: (username: String) -> Unit,
    viewModel: SocialViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(query) { viewModel.onAction(Action.OnQueryChanged(query)) }

    val locationPermission = rememberLocationPermissionController { viewModel.onAction(Action.OnPermissionResult(it)) }
    if (state.section == Section.Nearby) {
        // When Nearby opens and on every return to the app, e.g. from Settings.
        LifecycleResumeEffect(locationPermission) {
            viewModel.onAction(Action.OnPermissionChecked(locationPermission.status()))
            onPauseOrDispose {}
        }
    }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is Event.OpenTrip -> onOpenTrip(event.tripId)
            is Event.OpenProfile -> onOpenProfile(event.username)
            Event.RequestPermission -> locationPermission.request()
            Event.OpenAppSettings -> locationPermission.openSettings()
        }
    }

    SocialScreen(state = state, onAction = viewModel::onAction)

    if (state.reporting != null) {
        ReportDialog(
            onReport = { reason, details -> viewModel.onAction(Action.OnSendReport(reason, details)) },
            onDismiss = { viewModel.onAction(Action.OnDismissReport) },
            isSending = state.isReporting,
        )
    }
    state.addPlace?.let { addPlace ->
        AddPlaceSheet(addPlace = addPlace, onAction = viewModel::onAction)
    }
}

@Composable
internal fun SocialScreen(state: State, onAction: (Action) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        state.message?.let { message ->
            ErrorBanner(
                errorMessage = message.asString(),
                onDismiss = { onAction(Action.OnDismissMessage) },
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium, vertical = 4.dp),
            )
        }
        if (state.isSearchingByName) {
            PeopleResults(state, onAction)
            return@Column
        }
        SegmentedToggle(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = MaterialTheme.spacing.small)
                .width(280.dp),
        ) {
            SegmentedToggleOption(
                label = stringResource(Res.string.social_explore),
                selected = state.section == Section.Explore,
                onClick = { onAction(Action.OnSectionSelected(Section.Explore)) },
                icon = Icons.Default.Explore,
            )
            SegmentedToggleOption(
                label = stringResource(Res.string.social_nearby),
                selected = state.section == Section.Nearby,
                onClick = { onAction(Action.OnSectionSelected(Section.Nearby)) },
                icon = Icons.Default.NearMe,
            )
        }
        when (state.section) {
            Section.Explore -> ExploreContent(state, onAction)
            Section.Nearby -> NearbyContent(state, onAction)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExploreContent(state: State, onAction: (Action) -> Unit) {
    val listState = rememberLazyListState()
    LoadMoreAtEnd(listState) { onAction(Action.OnLoadMore) }
    PullToRefreshBox(
        isRefreshing = state.isLoadingExplore && state.hasLoadedExplore,
        onRefresh = { onAction(Action.OnRefresh) },
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            !state.hasLoadedExplore && state.isLoadingExplore -> Centered { CircularProgressIndicator() }
            state.explore.isEmpty() -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                // A list, so pull-to-refresh also works when it's empty.
                item { EmptyText(stringResource(Res.string.social_explore_empty)) }
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(MaterialTheme.spacing.medium),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            ) {
                items(state.explore, key = { it.trip.id }) { trip ->
                    PublicTripCard(
                        trip = trip,
                        isCopying = state.copyingTripId == trip.trip.id,
                        onOpen = { onAction(Action.OnTripClick(trip)) },
                        onOpenOwner = { onAction(Action.OnPersonClick(trip.trip.owner)) },
                        onLike = { onAction(Action.OnLikeTrip(trip)) },
                        onCopy = { onAction(Action.OnCopyTrip(trip)) },
                        onReport = { onAction(Action.OnReportTrip(trip)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PeopleResults(state: State, onAction: (Action) -> Unit) {
    when {
        state.isSearchingPeople && state.people.isEmpty() -> Centered { CircularProgressIndicator() }
        state.people.isEmpty() -> Centered { EmptyText(stringResource(Res.string.social_people_none)) }
        else -> LazyColumn(Modifier.fillMaxSize()) {
            items(state.people, key = { it.id }) { user ->
                PersonRow(user = user, onClick = { onAction(Action.OnPersonClick(user)) })
            }
        }
    }
}

/** Asks for the next page once the list is scrolled to within a few items of its end. */
@Composable
internal fun LoadMoreAtEnd(listState: LazyListState, onLoadMore: () -> Unit) {
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - LOAD_MORE_THRESHOLD
        }.distinctUntilChanged().filter { it }.collect { onLoadMore() }
    }
}

@Composable
internal fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
internal fun EmptyText(text: String) {
    Text(
        text = text,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(32.dp),
    )
}

private const val LOAD_MORE_THRESHOLD = 3
