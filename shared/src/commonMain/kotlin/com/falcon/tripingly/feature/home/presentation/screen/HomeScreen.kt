package com.falcon.tripingly.feature.home.presentation.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                    onTripClick = { onAction(Action.OnTripClick(it)) }
                )
                Tab.Social -> SocialContent()
            }
        }
    }
}

@Composable
private fun MyTripsContent(
    trips: List<Trip>,
    onTripClick: (String) -> Unit
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
                TripItem(trip = trip, onClick = { onTripClick(trip.id) })
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
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
