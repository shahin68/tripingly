package com.falcon.tripingly.feature.home.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.presentation.component.FloatingSearchBar
import com.falcon.tripingly.core.util.DateUtils
import com.falcon.tripingly.feature.home.domain.model.Trip
import com.falcon.tripingly.feature.home.presentation.screen.HomeViewModel.Action
import com.falcon.tripingly.feature.home.presentation.screen.HomeViewModel.State
import com.falcon.tripingly.feature.home.presentation.screen.HomeViewModel.Tab

@Composable
fun HomeScreen(
    state: State,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            HomeNavigationBar(
                selectedTab = state.selectedTab,
                onTabSelected = { onAction(Action.OnTabSelected(it)) }
            )
        },
        floatingActionButton = {
            if (state.selectedTab == Tab.MyTrips) {
                FloatingActionButton(onClick = { onAction(Action.OnAddTripClick) }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Trip")
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
                placeholder = if (state.selectedTab == Tab.MyTrips) "Search trips..." else "Search social..."
            )

            when (state.selectedTab) {
                Tab.MyTrips -> MyTripsContent(
                    trips = state.trips,
                    onTripClick = { onAction(Action.OnTripClick(it)) },
                    onDeleteTrip = { onAction(Action.OnDeleteTrip(it)) }
                )
                Tab.Social -> SocialContent()
            }
        }
    }
}

@Composable
private fun MyTripsContent(
    trips: List<Trip>,
    onTripClick: (String) -> Unit,
    onDeleteTrip: (String) -> Unit
) {
    if (trips.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No trips yet. Tap + to create one!")
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(trips, key = { it.id }) { trip ->
                val dismissState = rememberSwipeToDismissBoxState()

                SwipeToDismissBox(
                    state = dismissState,
                    enableDismissFromStartToEnd = false,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    onDismiss = {
                        if (it == SwipeToDismissBoxValue.EndToStart) {
                            onDeleteTrip(trip.id)
                        }
                    },
                    backgroundContent = {
                        val color = when (dismissState.targetValue) {
                            SwipeToDismissBoxValue.EndToStart -> Color.Red.copy(alpha = 0.8f)
                            else -> Color.Transparent
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(color, MaterialTheme.shapes.medium),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = Color.White,
                                modifier = Modifier.padding(end = 16.dp)
                            )
                        }
                    }
                ) {
                    TripItem(
                        trip = trip,
                        onClick = { onTripClick(trip.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SocialContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Social feature coming soon!")
    }
}

@Composable
private fun TripItem(
    trip: Trip,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick
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
            label = { Text("My Trips") }
        )
        NavigationBarItem(
            selected = selectedTab == Tab.Social,
            onClick = { onTabSelected(Tab.Social) },
            icon = { Icon(Icons.Default.People, contentDescription = null) },
            label = { Text("Social") }
        )
    }
}
