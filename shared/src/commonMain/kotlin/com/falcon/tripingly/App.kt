package com.falcon.tripingly

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.falcon.tripingly.core.presentation.theme.TripinglyTheme
import com.falcon.tripingly.feature.map.presentation.di.rememberMapViewModel
import com.falcon.tripingly.feature.map.presentation.screen.MapRoute

@Composable
fun App() {
    TripinglyTheme {
        val mapViewModel = rememberMapViewModel()
        MapRoute(
            viewModel = mapViewModel,
            modifier = Modifier.fillMaxSize()
        )
    }
}