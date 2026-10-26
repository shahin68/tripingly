package com.falcon.tripingly.feature.trips.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.FloatingTopBar
import com.falcon.tripingly.core.designsystem.component.LoadStateContent
import com.falcon.tripingly.core.designsystem.component.SettingsAction
import com.falcon.tripingly.core.designsystem.component.ShowMessage
import com.falcon.tripingly.core.designsystem.component.TopBarSearchField
import com.falcon.tripingly.core.designsystem.component.glassSource
import com.falcon.tripingly.core.designsystem.component.screenContentPadding
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.trips.generated.resources.Res
import com.falcon.tripingly.feature.trips.generated.resources.home_fab_add_trip
import com.falcon.tripingly.feature.trips.generated.resources.home_list_empty
import com.falcon.tripingly.feature.trips.generated.resources.home_list_no_matches
import com.falcon.tripingly.feature.trips.generated.resources.home_search_placeholder_trips
import com.falcon.tripingly.feature.trips.generated.resources.home_tab_my_trips
import com.falcon.tripingly.feature.trips.presentation.screen.HomeViewModel.Action
import com.falcon.tripingly.feature.trips.presentation.screen.HomeViewModel.State
import org.jetbrains.compose.resources.stringResource

/**
 * The Trips tab: a floating top bar with search and Settings, the trips as a timeline under it, and the Add Trip
 * button. [bottomBarPadding] is the tab shell's floating bottom bar, which the list scrolls behind.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
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
                title = stringResource(Res.string.home_tab_my_trips),
                actions = { SettingsAction(onOpenSettings) },
                search = {
                    TopBarSearchField(
                        query = state.searchQuery,
                        onQueryChange = { onAction(Action.SearchQueryChanged(it)) },
                        placeholder = stringResource(Res.string.home_search_placeholder_trips),
                    )
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(stringResource(Res.string.home_fab_add_trip)) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = { onAction(Action.Trips.Add) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.padding(bottom = bottomBarPadding.calculateBottomPadding()),
            )
        },
    ) { padding ->
        val contentPadding = screenContentPadding(padding, bottomBarPadding, extraBottom = FabClearance)
        ShowMessage(
            message = state.message?.asString(),
            onShown = { onAction(Action.DismissMessage) },
            isError = state.message !is UiText.Note,
        )
        PullToRefreshBox(
            isRefreshing = state.isRefreshing && state.hasLoaded,
            onRefresh = { onAction(Action.Trips.Refresh) },
            modifier = Modifier.fillMaxSize(),
        ) {
            LoadStateContent(
                isLoading = !state.hasLoaded,
                errorMessage = state.loadError?.asString(),
                onRetry = { onAction(Action.Trips.Refresh) },
                contentPadding = contentPadding,
            ) {
                if (state.timeline.isEmpty) {
                    EmptyTrips(hasTrips = state.hasTrips, contentPadding = contentPadding)
                } else {
                    TripTimelineList(state.timeline, onAction, contentPadding)
                }
            }
        }
    }
}

@Composable
private fun TripTimelineList(
    timeline: TripTimeline,
    onAction: (Action) -> Unit,
    contentPadding: PaddingValues,
) {
    val today = timeline.today
    LazyColumn(modifier = Modifier.fillMaxSize().glassSource(), contentPadding = contentPadding) {
        if (today != null) item(key = "today") { TodayHeader(today, Modifier.animateItem()) }
        items(timeline.upcoming, key = { it.id }) { trip ->
            TimelineTripItem(trip, today, past = false, onAction = onAction, modifier = Modifier.animateItem())
        }
        if (timeline.past.isNotEmpty()) {
            item(key = "past") { PastHeader(timeline.past.size, Modifier.animateItem()) }
            items(timeline.past, key = { it.id }) { trip ->
                TimelineTripItem(trip, today, past = true, onAction = onAction, modifier = Modifier.animateItem())
            }
        }
    }
}

/** No trips yet, or none matching the search; a list so pull-to-refresh still works. */
@Composable
private fun EmptyTrips(hasTrips: Boolean, contentPadding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Text(
                text = stringResource(if (hasTrips) Res.string.home_list_no_matches else Res.string.home_list_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(MaterialTheme.spacing.extraLarge),
            )
        }
    }
}

/** Room for the Add Trip button below the last trip. */
private val FabClearance = 72.dp

@Preview
@Composable
private fun HomeScreenPreview(@PreviewParameter(HomeScreenPreviewStates::class) state: State) {
    TripinglyTheme { HomeScreen(state = state, onAction = {}, onOpenSettings = {}) }
}

@Preview
@Composable
private fun HomeScreenDarkPreview() {
    TripinglyTheme(darkTheme = true) {
        HomeScreen(state = HomeScreenPreviewStates().values.first(), onAction = {}, onOpenSettings = {})
    }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun HomeScreenLandscapePreview() {
    TripinglyTheme(darkTheme = true) {
        Box { HomeScreen(state = HomeScreenPreviewStates().values.first(), onAction = {}, onOpenSettings = {}) }
    }
}
