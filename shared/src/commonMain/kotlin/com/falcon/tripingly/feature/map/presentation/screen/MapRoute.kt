package com.falcon.tripingly.feature.map.presentation.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.feature.map.presentation.permission.rememberLocationPermissionLauncher
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel.Action
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel.Event

@Composable
fun MapRoute(viewModel: MapViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val requestPermission = rememberLocationPermissionLauncher { isGranted ->
        viewModel.onAction(Action.OnPermissionResult(isGranted))
    }

    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is Event.RequestPermission -> {
                    requestPermission()
                }

                is Event.AnimateCamera -> {
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.onAction(Action.RequestLocationPermission)
    }

    MapScreen(
        state = state,
        onAction = viewModel::onAction
    )
}
