package com.falcon.tripingly.feature.trips.presentation.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.common.util.DateUtils
import com.falcon.tripingly.core.designsystem.component.AppDropdownMenu
import com.falcon.tripingly.core.designsystem.component.DropdownAction
import com.falcon.tripingly.core.designsystem.component.ShowMessage
import com.falcon.tripingly.core.designsystem.component.FloatingSearchBar
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.core.model.trip.Trip
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.timeAgo
import com.falcon.tripingly.feature.trips.generated.resources.*
import com.falcon.tripingly.feature.trips.generated.resources.Res
import com.falcon.tripingly.feature.trips.presentation.screen.HomeViewModel.Action
import com.falcon.tripingly.feature.trips.presentation.screen.HomeViewModel.State
import com.falcon.tripingly.feature.trips.presentation.screen.HomeViewModel.Tab
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun HomeScreen(
    state: State,
    onAction: (Action) -> Unit,
    onOpenMyProfile: () -> Unit,
    modifier: Modifier = Modifier,
    /** The Social tab, from the social feature; it gets what's typed in the search bar there. */
    socialContent: @Composable (query: String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = state.selectedTab.ordinal,
        pageCount = { Tab.entries.size }
    )

    val currentTab = Tab.entries[pagerState.currentPage]

    LaunchedEffect(pagerState.currentPage) {
        onAction(Action.TabSelected(currentTab))
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            HomeNavigationBar(
                selectedTab = currentTab,
                onTabSelected = { tab ->
                    scope.launch {
                        pagerState.animateScrollToPage(tab.ordinal)
                    }
                }
            )
        },
        floatingActionButton = {
            val isFabVisible = currentTab == Tab.MyTrips
            val fabScale by animateFloatAsState(
                targetValue = if (isFabVisible) 1f else 0f,
                animationSpec = tween(durationMillis = 200),
                label = "fabScale"
            )

            if (fabScale > 0f) {
                FloatingActionButton(
                    onClick = { onAction(Action.Trips.Add) },
                    modifier = Modifier.scale(fabScale)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(Res.string.home_fab_add_trip)
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            FloatingSearchBar(
                query = if (currentTab == Tab.MyTrips) state.searchQuery else state.peopleQuery,
                onQueryChange = {
                    onAction(if (currentTab == Tab.MyTrips) Action.SearchQueryChanged(it) else Action.PeopleQueryChanged(it))
                },
                placeholder = if (currentTab == Tab.MyTrips) {
                    stringResource(Res.string.home_search_placeholder_trips)
                } else {
                    stringResource(Res.string.home_search_placeholder_social)
                },
                trailingIcon = {
                    IconButton(onClick = onOpenMyProfile) {
                        Icon(Icons.Default.AccountCircle, contentDescription = stringResource(Res.string.home_my_profile))
                    }
                },
            )

            ShowMessage(
                message = state.message?.asString(),
                onShown = { onAction(Action.DismissMessage) },
                isError = state.message !is UiText.Note,
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = true,
                beyondViewportPageCount = 1
            ) { page ->
                when (Tab.entries[page]) {
                    Tab.MyTrips -> MyTripsContent(
                        state = state,
                        onAction = onAction
                    )
                    Tab.Social -> socialContent(state.peopleQuery)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MyTripsContent(
    state: State,
    onAction: (Action) -> Unit
) {
    var selectedTripId by rememberSaveable { mutableStateOf<String?>(null) }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing && state.hasLoaded,
        onRefresh = { onAction(Action.Trips.Refresh) },
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            !state.hasLoaded -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.trips.isEmpty() -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // A list, so pull-to-refresh also works when it's empty.
                item {
                    Text(
                        text = stringResource(if (state.hasTrips) Res.string.home_list_no_matches else Res.string.home_list_empty),
                        modifier = Modifier.padding(32.dp),
                    )
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.trips, key = { it.id }) { trip ->
                    val isSelected = selectedTripId == trip.id
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .animateItem()
                    ) {
                        TripItem(
                            trip = trip,
                            onClick = { onAction(Action.Trips.Click(trip.id)) },
                            onLongClick = { selectedTripId = trip.id },
                            isSelected = isSelected
                        )

                        AppDropdownMenu(
                            expanded = isSelected,
                            onDismissRequest = { selectedTripId = null },
                            actions = tripActions(trip, onAction),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

/** The long-press menu; which entries a trip has is decided by [menu]. */
@Composable
private fun tripActions(trip: Trip, onAction: (Action) -> Unit): List<DropdownAction> = trip.menu().map { item ->
    when (item) {
        TripMenuItem.Rename -> DropdownAction(stringResource(Res.string.trip_action_rename)) { onAction(Action.Trips.Rename(trip.id)) }
        TripMenuItem.Reschedule -> DropdownAction(stringResource(Res.string.trip_action_reschedule)) { onAction(Action.Trips.Reschedule(trip.id)) }
        TripMenuItem.MakePrivate -> DropdownAction(stringResource(Res.string.trip_action_make_private)) { onAction(Action.Trips.ToggleVisibility(trip.id)) }
        TripMenuItem.MakePublic -> DropdownAction(stringResource(Res.string.trip_action_make_public)) { onAction(Action.Trips.ToggleVisibility(trip.id)) }
        TripMenuItem.Members -> DropdownAction(stringResource(Res.string.trip_action_members)) { onAction(Action.Trips.ManageMembers(trip.id)) }
        TripMenuItem.Share -> DropdownAction(stringResource(Res.string.trip_action_share)) { onAction(Action.Trips.Share(trip.id)) }
        TripMenuItem.RemoveSaved -> DropdownAction(stringResource(Res.string.trip_action_remove_saved), isDestructive = true) {
            onAction(Action.Trips.RemoveSaved(trip.id))
        }
        TripMenuItem.Delete -> DropdownAction(stringResource(Res.string.trip_action_delete), isDestructive = true) { onAction(Action.Trips.Delete(trip.id)) }
        TripMenuItem.Leave -> DropdownAction(stringResource(Res.string.trip_action_leave), isDestructive = true) { onAction(Action.Trips.Leave(trip.id)) }
    }
}

@Composable
private fun TripItem(
    trip: Trip,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            // Keeps the ripple inside the card's rounded corners.
            .clip(CardDefaults.shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        border = if (isSelected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = trip.name, style = MaterialTheme.typography.titleLarge)
            Text(
                text = DateUtils.formatRange(trip.startDate, trip.endDate) ?: stringResource(Res.string.trip_no_dates),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = listOf(
                    pluralStringResource(Res.plurals.trip_day_count, trip.dayCount, trip.dayCount),
                    pluralStringResource(Res.plurals.trip_stop_count, trip.markerCount, trip.markerCount),
                    stringResource(Res.string.trip_created, timeAgo(trip.createdAt)),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val details = buildList {
                if (trip.role == TripRole.EDITOR) add(stringResource(Res.string.trip_shared_by, trip.owner.username))
                if (trip.role == TripRole.VIEWER) add(stringResource(Res.string.trip_saved_by, trip.owner.username))
                if (trip.role == TripRole.OWNER && trip.editorCount > 0) {
                    add(pluralStringResource(Res.plurals.trip_shared_with, trip.editorCount, trip.editorCount))
                }
                if (trip.visibility == TripVisibility.PRIVATE) add(stringResource(Res.string.trip_private))
            }
            if (details.isNotEmpty()) {
                Text(
                    text = details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HomeNavigationBar(
    selectedTab: Tab,
    onTabSelected: (Tab) -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected = selectedTab == Tab.MyTrips,
            onClick = { onTabSelected(Tab.MyTrips) },
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = { Text(stringResource(Res.string.home_tab_my_trips)) }
        )
        NavigationBarItem(
            selected = selectedTab == Tab.Social,
            onClick = { onTabSelected(Tab.Social) },
            icon = { Icon(Icons.Default.People, contentDescription = null) },
            label = { Text(stringResource(Res.string.home_tab_social)) }
        )
    }
}

@Preview
@Composable
private fun HomeScreenPreview() {
    TripinglyTheme {
        HomeScreen(
            state = State(
                trips = tripsPreviewData,
                hasTrips = true,
                hasLoaded = true,
            ),
            onAction = {},
            onOpenMyProfile = {},
        )
    }
}



