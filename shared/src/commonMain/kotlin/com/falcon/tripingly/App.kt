package com.falcon.tripingly

import androidx.compose.runtime.Composable
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.NavEntry
import com.falcon.tripingly.core.navigation.NavConfig
import com.falcon.tripingly.core.navigation.Home
import com.falcon.tripingly.core.navigation.TripMap
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.feature.auth.presentation.gate.AuthGate
import com.falcon.tripingly.feature.trips.presentation.screen.HomeRoute
import com.falcon.tripingly.feature.map.presentation.screen.MapRoute

@Composable
fun App() {
    TripinglyTheme {
        AuthGate { MainNavigation() }
    }
}

@Composable
private fun MainNavigation() {
    val backStack = rememberNavBackStack(NavConfig, Home)

    NavDisplay(
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) },
    ) { route ->
        NavEntry(route) { key ->
            when (key) {
                is Home -> {
                    HomeRoute(
                        onNavigateToMap = { tripId ->
                            backStack.add(TripMap(tripId))
                        }
                    )
                }
                is TripMap -> {
                    MapRoute(tripId = key.tripId)
                }
            }
        }
    }
}
