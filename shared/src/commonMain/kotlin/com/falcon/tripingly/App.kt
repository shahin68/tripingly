package com.falcon.tripingly

import androidx.compose.runtime.Composable
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.NavEntry
import com.falcon.tripingly.core.presentation.navigation.NavConfig
import com.falcon.tripingly.core.presentation.navigation.Home
import com.falcon.tripingly.core.presentation.navigation.TripMap
import com.falcon.tripingly.core.presentation.theme.TripinglyTheme
import com.falcon.tripingly.feature.home.presentation.screen.HomeRoute
import com.falcon.tripingly.feature.map.presentation.screen.MapRoute

@Composable
fun App() {
    TripinglyTheme {
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
}
