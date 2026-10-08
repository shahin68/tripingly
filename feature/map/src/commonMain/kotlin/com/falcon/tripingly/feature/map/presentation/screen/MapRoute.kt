package com.falcon.tripingly.feature.map.presentation.screen

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.falcon.tripingly.feature.map.presentation.permission.rememberLocationPermissionController
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_cancel
import com.falcon.tripingly.feature.map.generated.resources.map_clear_day_message
import com.falcon.tripingly.feature.map.generated.resources.map_clear_day_title
import com.falcon.tripingly.feature.map.generated.resources.map_confirm_delete
import com.falcon.tripingly.feature.map.generated.resources.map_delete_day_message
import com.falcon.tripingly.feature.map.generated.resources.map_delete_day_title
import com.falcon.tripingly.feature.map.generated.resources.map_location_continue
import com.falcon.tripingly.feature.map.generated.resources.map_location_explain_message
import com.falcon.tripingly.feature.map.generated.resources.map_location_explain_title
import com.falcon.tripingly.feature.map.generated.resources.map_location_open_settings
import com.falcon.tripingly.feature.map.generated.resources.map_location_settings_message
import com.falcon.tripingly.feature.map.generated.resources.map_location_settings_title
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel.Action
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel.Confirm
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel.Event
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel.LocationPrompt
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun MapRoute(
    tripId: String,
    onNavigateBack: () -> Unit,
    // Keyed by trip: the view model store is shared across screens, and the class alone would
    // hand every trip the first trip's view model.
    viewModel: MapViewModel = koinViewModel(key = "map:$tripId") { parametersOf(tripId) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val locationPermission = rememberLocationPermissionController { permission ->
        viewModel.onAction(Action.OnPermissionResult(permission))
    }
    // On open and on every return to the app, e.g. from Settings.
    LifecycleResumeEffect(locationPermission) {
        viewModel.onAction(Action.OnPermissionChecked(locationPermission.status()))
        onPauseOrDispose {}
    }

    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is Event.RequestPermission -> locationPermission.request()
                is Event.OpenAppSettings -> locationPermission.openSettings()
                is Event.NavigateBack -> onNavigateBack()
                is Event.AnimateCamera -> {
                    // Reactive
                }
            }
        }
    }

    MapScreen(
        state = state,
        onAction = viewModel::onAction
    )

    state.locationPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = { viewModel.onAction(Action.OnDismissLocationPrompt) },
            title = {
                Text(
                    when (prompt) {
                        LocationPrompt.Explain -> stringResource(Res.string.map_location_explain_title)
                        LocationPrompt.OpenSettings -> stringResource(Res.string.map_location_settings_title)
                    },
                )
            },
            text = {
                Text(
                    when (prompt) {
                        LocationPrompt.Explain -> stringResource(Res.string.map_location_explain_message)
                        LocationPrompt.OpenSettings -> stringResource(Res.string.map_location_settings_message)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onAction(Action.OnConfirmLocationPrompt) }) {
                    Text(
                        when (prompt) {
                            LocationPrompt.Explain -> stringResource(Res.string.map_location_continue)
                            LocationPrompt.OpenSettings -> stringResource(Res.string.map_location_open_settings)
                        },
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onAction(Action.OnDismissLocationPrompt) }) {
                    Text(stringResource(Res.string.map_cancel))
                }
            },
        )
    }

    state.confirm?.let { confirm ->
        val dayNumber = state.days.getOrNull(state.activeDayIndex)?.number ?: 1
        AlertDialog(
            onDismissRequest = { viewModel.onAction(Action.OnDismissConfirm) },
            title = {
                Text(
                    when (confirm) {
                        Confirm.ClearDay -> stringResource(Res.string.map_clear_day_title, dayNumber)
                        Confirm.DeleteDay -> stringResource(Res.string.map_delete_day_title, dayNumber)
                    },
                )
            },
            text = {
                Text(
                    when (confirm) {
                        Confirm.ClearDay -> stringResource(Res.string.map_clear_day_message, state.markers.size)
                        Confirm.DeleteDay -> stringResource(Res.string.map_delete_day_message, state.markers.size)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onAction(Action.OnConfirm) }) {
                    Text(stringResource(Res.string.map_confirm_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onAction(Action.OnDismissConfirm) }) {
                    Text(stringResource(Res.string.map_cancel))
                }
            },
        )
    }
}
