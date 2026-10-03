package com.falcon.tripingly.feature.trips.presentation.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.feature.trips.presentation.component.CreateTripDialog
import com.falcon.tripingly.feature.trips.presentation.component.RenameTripDialog
import com.falcon.tripingly.feature.trips.presentation.component.RescheduleTripDialog
import com.falcon.tripingly.feature.trips.presentation.screen.HomeViewModel.Event
import org.koin.compose.viewmodel.koinViewModel
import kotlinx.datetime.LocalDate

@Composable
fun HomeRoute(
    onNavigateToMap: (String) -> Unit,
    viewModel: HomeViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is Event.NavigateToMap -> onNavigateToMap(event.tripId)
            }
        }
    }

    HomeScreen(
        state = state,
        onAction = viewModel::onAction
    )

    if (state.isCreateDialogVisible) {
        CreateTripDialog(
            onDismiss = { viewModel.onAction(HomeViewModel.Action.OnDismissCreateDialog) },
            onConfirm = { name: String, start: LocalDate, end: LocalDate ->
                viewModel.onAction(HomeViewModel.Action.OnConfirmCreateTrip(name, start, end))
            }
        )
    }

    state.renamingTrip?.let { trip ->
        RenameTripDialog(
            initialName = trip.name,
            onDismiss = { viewModel.onAction(HomeViewModel.Action.OnDismissRenameDialog) },
            onConfirm = { newName ->
                viewModel.onAction(HomeViewModel.Action.OnConfirmRenameTrip(trip.id, newName))
            }
        )
    }

    state.reschedulingTrip?.let { trip ->
        RescheduleTripDialog(
            initialStartDate = trip.startDate,
            initialEndDate = trip.endDate,
            onDismiss = { viewModel.onAction(HomeViewModel.Action.OnDismissRescheduleDialog) },
            onConfirm = { start, end ->
                viewModel.onAction(HomeViewModel.Action.OnConfirmRescheduleTrip(trip.id, start, end))
            }
        )
    }
}
