package com.falcon.tripingly.feature.trips.presentation.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.AppDropdownMenu
import com.falcon.tripingly.core.designsystem.component.DropdownAction
import com.falcon.tripingly.core.designsystem.component.FloatingSearchBar
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.common.util.DateUtils
import com.falcon.tripingly.core.model.Trip
import com.falcon.tripingly.feature.trips.presentation.screen.HomeViewModel.Action
import com.falcon.tripingly.feature.trips.presentation.screen.HomeViewModel.State
import com.falcon.tripingly.feature.trips.presentation.screen.HomeViewModel.Tab
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.falcon.tripingly.feature.trips.generated.resources.Res
import com.falcon.tripingly.feature.trips.generated.resources.*

@Composable
fun HomeScreen(
    state: State,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = state.selectedTab.ordinal,
        pageCount = { Tab.entries.size }
    )

    val currentTab = Tab.entries[pagerState.currentPage]

    LaunchedEffect(pagerState.currentPage) {
        onAction(Action.OnTabSelected(currentTab))
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
                    onClick = { onAction(Action.OnAddTripClick) },
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
                query = state.searchQuery,
                onQueryChange = { onAction(Action.OnSearchQueryChanged(it)) },
                placeholder = if (currentTab == Tab.MyTrips) {
                    stringResource(Res.string.home_search_placeholder_trips)
                } else {
                    stringResource(Res.string.home_search_placeholder_social)
                }
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = true,
                beyondViewportPageCount = 1
            ) { page ->
                when (Tab.entries[page]) {
                    Tab.MyTrips -> MyTripsContent(
                        trips = state.trips,
                        onAction = onAction
                    )
                    Tab.Social -> SocialContent()
                }
            }
        }
    }
}

@Composable
private fun MyTripsContent(
    trips: List<Trip>,
    onAction: (Action) -> Unit
) {
    var selectedTripId by remember { mutableStateOf<String?>(null) }

    if (trips.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(Res.string.home_list_empty))
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(trips, key = { it.id }) { trip ->
                val isSelected = selectedTripId == trip.id
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    TripItem(
                        trip = trip,
                        onClick = { onAction(Action.OnTripClick(trip.id)) },
                        onLongClick = { selectedTripId = trip.id },
                        isSelected = isSelected
                    )

                    AppDropdownMenu(
                        expanded = isSelected,
                        onDismissRequest = { selectedTripId = null },
                        actions = listOf(
                            DropdownAction(
                                label = stringResource(Res.string.trip_action_rename),
                                onClick = { onAction(Action.OnRenameTrip(trip.id)) }
                            ),
                            DropdownAction(
                                label = stringResource(Res.string.trip_action_reschedule),
                                onClick = { onAction(Action.OnRescheduleTrip(trip.id)) }
                            ),
                            DropdownAction(
                                label = stringResource(Res.string.trip_action_share),
                                onClick = { onAction(Action.OnShareTrip(trip.id)) }
                            ),
                            DropdownAction(
                                label = stringResource(Res.string.trip_action_delete),
                                isDestructive = true,
                                onClick = { onAction(Action.OnDeleteTrip(trip.id)) }
                            )
                        ),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SocialContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(Res.string.home_social_coming_soon))
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
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        border = if (isSelected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = trip.name, style = MaterialTheme.typography.titleLarge)
            val dateRangeText = "${DateUtils.formatFormal(trip.startDate)} - ${DateUtils.formatFormal(trip.endDate)}"
            Text(
                text = dateRangeText,
                style = MaterialTheme.typography.bodyMedium
            )
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
                trips = tripsPreviewData
            ),
            onAction = {}
        )
    }
}



