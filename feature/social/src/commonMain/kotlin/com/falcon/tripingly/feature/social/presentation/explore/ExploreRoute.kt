package com.falcon.tripingly.feature.social.presentation.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.core.designsystem.component.FloatingTopBar
import com.falcon.tripingly.core.designsystem.component.LoadStateContent
import com.falcon.tripingly.core.designsystem.component.ReportDialog
import com.falcon.tripingly.core.designsystem.component.SettingsAction
import com.falcon.tripingly.core.designsystem.component.ShowMessage
import com.falcon.tripingly.core.designsystem.component.SkeletonBox
import com.falcon.tripingly.core.designsystem.component.glassSource
import com.falcon.tripingly.core.designsystem.component.screenContentPadding
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.location.permission.rememberLocationPermissionController
import com.falcon.tripingly.core.ui.ObserveAsEvents
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_explore
import com.falcon.tripingly.feature.social.generated.resources.social_explore_empty
import com.falcon.tripingly.feature.social.generated.resources.social_explore_empty_filtered
import com.falcon.tripingly.feature.social.presentation.component.EmptyText
import com.falcon.tripingly.feature.social.presentation.component.ExplorePreviewStates
import com.falcon.tripingly.feature.social.presentation.component.LoadMoreAtEnd
import com.falcon.tripingly.feature.social.presentation.explore.ExploreViewModel.Action
import com.falcon.tripingly.feature.social.presentation.explore.ExploreViewModel.Event
import com.falcon.tripingly.feature.social.presentation.explore.ExploreViewModel.State
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** The Explore tab. [bottomBarPadding] is the tab shell's floating bottom bar, which the grid scrolls behind. */
@Composable
fun ExploreRoute(
    onOpenTrip: (tripId: String) -> Unit,
    onOpenProfile: (username: String) -> Unit,
    onOpenSettings: () -> Unit,
    bottomBarPadding: PaddingValues = PaddingValues(),
    viewModel: ExploreViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val locationPermission = rememberLocationPermissionController { viewModel.onAction(Action.LocationPermissionResult(it)) }
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is Event.OpenTrip -> onOpenTrip(event.tripId)
            is Event.OpenProfile -> onOpenProfile(event.username)
            Event.RequestPermission -> locationPermission.request()
            Event.CheckLocation -> viewModel.onAction(Action.LocationChecked(locationPermission.status()))
        }
    }

    ExploreScreen(state = state, onAction = viewModel::onAction, onOpenSettings = onOpenSettings, bottomBarPadding = bottomBarPadding)

    state.report?.let { report ->
        ReportDialog(
            onReport = { reason, details -> viewModel.onAction(Action.SendReport(reason, details)) },
            onDismiss = { viewModel.onAction(Action.DismissReport) },
            isSending = report.isSending,
        )
    }
}

@Composable
internal fun ExploreScreen(
    state: State,
    onAction: (Action) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBarPadding: PaddingValues = PaddingValues(),
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            FloatingTopBar(
                title = stringResource(Res.string.social_explore),
                actions = {
                    ExplorePeriodMenu(state.period, state.isNearbyFilter, onAction)
                    SettingsAction(onOpenSettings)
                },
            )
        },
    ) { padding ->
        val contentPadding = screenContentPadding(padding, bottomBarPadding)
        ShowMessage(message = state.message?.asString(), onShown = { onAction(Action.DismissMessage) }, isError = state.message !is UiText.Note)
        LoadStateContent(
            isLoading = !state.hasLoaded,
            errorMessage = state.loadError?.asString(),
            onRetry = { onAction(Action.Refresh) },
            contentPadding = contentPadding,
            loading = { ExploreSkeleton(contentPadding) },
        ) {
            ExploreGrid(state, onAction, contentPadding)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExploreGrid(state: State, onAction: (Action) -> Unit, contentPadding: PaddingValues) {
    val gridState = rememberLazyGridState()
    LoadMoreAtEnd(gridState) { onAction(Action.LoadMore) }
    PullToRefreshBox(isRefreshing = state.isLoading, onRefresh = { onAction(Action.Refresh) }, modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(GridCellMinWidth),
            modifier = Modifier.fillMaxSize().glassSource(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(GridGap),
            verticalArrangement = Arrangement.spacedBy(GridGap),
        ) {
            if (state.trips.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    EmptyText(
                        stringResource(if (state.isFiltered) Res.string.social_explore_empty_filtered else Res.string.social_explore_empty),
                        Modifier.animateItem(),
                    )
                }
            }
            items(state.trips, key = { it.trip.id }) { trip ->
                ExploreTripCard(
                    trip = trip,
                    onOpen = { onAction(Action.TripClick(trip)) },
                    onOpenOwner = { onAction(Action.OwnerClick(trip.trip.owner)) },
                    onLike = { onAction(Action.LikeTrip(trip)) },
                    onToggleSave = { onAction(Action.ToggleSaveTrip(trip)) },
                    onReport = { onAction(Action.ReportTrip(trip)) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

/** Four grey cards in the grid's shape while the first page loads. */
@Composable
private fun ExploreSkeleton(contentPadding: PaddingValues) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(GridCellMinWidth),
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(GridGap),
        verticalArrangement = Arrangement.spacedBy(GridGap),
        userScrollEnabled = false,
    ) {
        items(SKELETON_CARDS) { SkeletonBox(Modifier.fillMaxWidth().height(ExploreCardHeight), shape = MaterialTheme.shapes.extraLarge) }
    }
}

private val GridCellMinWidth = 150.dp
private val GridGap = 12.dp
private const val SKELETON_CARDS = 4

@Preview
@Composable
private fun ExploreScreenPreview(@PreviewParameter(ExplorePreviewStates::class) state: State) {
    TripinglyTheme { ExploreScreen(state = state, onAction = {}, onOpenSettings = {}) }
}

@Preview
@Composable
private fun ExploreScreenDarkPreview() {
    TripinglyTheme(darkTheme = true) { ExploreScreen(state = ExplorePreviewStates().values.first(), onAction = {}, onOpenSettings = {}) }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun ExploreScreenLandscapePreview() {
    TripinglyTheme { ExploreScreen(state = ExplorePreviewStates().values.first(), onAction = {}, onOpenSettings = {}) }
}
