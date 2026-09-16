package com.falcon.tripingly

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.falcon.tripingly.core.presentation.theme.TripinglyTheme
import com.falcon.tripingly.feature.map.presentation.screen.MapRoute
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App() {
    TripinglyTheme {
        val mapViewModel = koinViewModel<MapViewModel>()
        MapRoute(
            viewModel = mapViewModel,
            modifier = Modifier.fillMaxSize()
        )
    }
}