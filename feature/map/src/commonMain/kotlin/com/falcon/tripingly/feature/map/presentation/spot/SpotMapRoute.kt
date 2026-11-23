package com.falcon.tripingly.feature.map.presentation.spot

import androidx.compose.foundation.gestures.animateTo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.core.designsystem.component.rememberHaptics
import com.falcon.tripingly.core.location.permission.rememberLocationPermissionController
import com.falcon.tripingly.core.ui.ObserveAsEvents
import com.falcon.tripingly.feature.map.presentation.map.MapAction
import com.falcon.tripingly.feature.map.presentation.map.MapEvent
import com.falcon.tripingly.feature.map.presentation.map.MapSheets
import com.falcon.tripingly.feature.map.presentation.map.ui.MapDialogs
import com.falcon.tripingly.feature.map.presentation.map.ui.PlanSheetValue
import com.falcon.tripingly.feature.map.presentation.map.ui.rememberPlanSheetState
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** One spot on its own map, from Nearby or a push about its flames ([SpotMapViewModel]). */
@Composable
fun SpotMapRoute(
    placeId: String,
    onNavigateBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    /** Tripinly Pro's paywall, when a free user taps Go here or Around. */
    onOpenPaywall: () -> Unit,
    // Keyed by spot: the view model store is shared across screens, and the class alone would hand every spot the first one's.
    viewModel: SpotMapViewModel = koinViewModel(key = "spot:$placeId") { parametersOf(placeId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onMapAction = viewModel::onMapAction

    val scope = rememberCoroutineScope()
    var locationSwitches by remember { mutableIntStateOf(0) }
    val locationPermission = rememberLocationPermissionController(
        onResult = { permission -> onMapAction(MapAction.Location.PermissionResult(permission)) },
        onLocationSwitched = { locationSwitches++ },
    )
    // As on the trip map: the permission, then whether the device's location is on, on open, on every return to the
    // app and whenever the location is switched.
    LifecycleResumeEffect(locationPermission, locationSwitches) {
        onMapAction(MapAction.Location.PermissionChecked(locationPermission.status()))
        val check = scope.launch { onMapAction(MapAction.Location.LocationSwitched(locationPermission.isLocationOn())) }
        onPauseOrDispose { check.cancel() }
    }

    val planSheet = rememberPlanSheetState()
    val haptics = rememberHaptics()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            MapEvent.RequestPermission -> locationPermission.request()
            MapEvent.OpenAppSettings -> locationPermission.openSettings()
            MapEvent.TurnOnLocation -> locationPermission.turnOnLocation()
            MapEvent.NavigateBack -> onNavigateBack()
            is MapEvent.OpenProfile -> onOpenProfile(event.username)
            // A locked feature is felt as a no before the paywall.
            MapEvent.OpenPaywall -> {
                haptics.error()
                onOpenPaywall()
            }
            MapEvent.ShowWholePlan -> scope.launch { planSheet.animateTo(PlanSheetValue.Expanded) }
            MapEvent.AddedToMyTrip -> haptics.success()
            MapEvent.StopAdded, is MapEvent.ShareTrip -> Unit
        }
    }

    SpotMapScreen(
        state = state,
        cameraCommands = viewModel.cameraCommands,
        onAction = viewModel::onAction,
        onMapAction = onMapAction,
        planSheet = planSheet,
    )

    // The trip map's sheets: the spot's photos and details, the trips to add it to, a route's steps, the image viewer.
    MapSheets(state.map.sheet, onMapAction, onOpenProfile, canRoute = false)
    state.map.dialog?.let { dialog -> MapDialogs(dialog, onMapAction) }
}
