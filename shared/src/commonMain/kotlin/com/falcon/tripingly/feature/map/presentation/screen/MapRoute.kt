package com.falcon.tripingly.feature.map.presentation.screen

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.feature.map.presentation.mvi.MapUiAction
import com.falcon.tripingly.feature.map.presentation.mvi.MapUiEvent
import com.falcon.tripingly.feature.map.presentation.permission.rememberLocationPermissionLauncher
import com.falcon.tripingly.feature.map.presentation.viewmodel.MapViewModel

@Composable
fun MapRoute(
    viewModel: MapViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val requestPermission = rememberLocationPermissionLauncher { isGranted ->
        viewModel.onAction(MapUiAction.OnPermissionResult(isGranted))
    }

    // Handle single-time ViewModel events
    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is MapUiEvent.RequestPermission -> {
                    requestPermission()
                }
                is MapUiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is MapUiEvent.AnimateCamera -> {
                    // Handled reactively through state.cameraTarget and state.zoomLevel in MapView
                }
            }
        }
    }

    // On first composition, trigger location request to load user location as default
    LaunchedEffect(Unit) {
        viewModel.onAction(MapUiAction.RequestLocationPermission)
    }

    MapScreen(
        state = state,
        onAction = viewModel::onAction,
        snackbarHostState = snackbarHostState,
        modifier = modifier
    )
}
