package com.falcon.tripingly.feature.map.presentation.screen

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.feature.map.presentation.permission.rememberLocationPermissionLauncher
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_cancel
import com.falcon.tripingly.feature.map.generated.resources.map_clear_day_message
import com.falcon.tripingly.feature.map.generated.resources.map_clear_day_title
import com.falcon.tripingly.feature.map.generated.resources.map_confirm_delete
import com.falcon.tripingly.feature.map.generated.resources.map_delete_day_message
import com.falcon.tripingly.feature.map.generated.resources.map_delete_day_title
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel.Action
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel.Confirm
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel.Event
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun MapRoute(
    tripId: String,
    // Keyed by trip: the view model store is shared across screens, and the class alone would
    // hand every trip the first trip's view model.
    viewModel: MapViewModel = koinViewModel(key = "map:$tripId") { parametersOf(tripId) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val requestPermission = rememberLocationPermissionLauncher { isGranted: Boolean ->
        viewModel.onAction(Action.OnPermissionResult(isGranted))
    }

    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is Event.RequestPermission -> {
                    requestPermission()
                }
                is Event.AnimateCamera -> {
                    // Reactive
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
