package com.falcon.tripingly.feature.trips.presentation.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.core.common.util.ShareManager
import com.falcon.tripingly.core.ui.ObserveAsEvents
import com.falcon.tripingly.feature.trips.generated.resources.Res
import com.falcon.tripingly.feature.trips.generated.resources.share_trip_text
import com.falcon.tripingly.feature.trips.generated.resources.share_trip_text_no_dates
import com.falcon.tripingly.feature.trips.presentation.component.ConfirmTripDialog
import com.falcon.tripingly.feature.trips.presentation.component.CreateTripDialog
import com.falcon.tripingly.feature.trips.presentation.component.RenameTripDialog
import com.falcon.tripingly.feature.trips.presentation.component.RescheduleTripDialog
import com.falcon.tripingly.feature.trips.presentation.component.TripDialogKind
import com.falcon.tripingly.feature.trips.presentation.component.formatDateRange
import com.falcon.tripingly.feature.trips.presentation.members.TripMembersRoute
import com.falcon.tripingly.feature.trips.presentation.screen.HomeViewModel.Action
import com.falcon.tripingly.feature.trips.presentation.screen.HomeViewModel.Dialog
import com.falcon.tripingly.feature.trips.presentation.screen.HomeViewModel.Event
import org.jetbrains.compose.resources.getString
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeRoute(
    onNavigateToMap: (String) -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
    shareManager: ShareManager = koinInject(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is Event.NavigateToMap -> onNavigateToMap(event.tripId)
            is Event.ShareTrip -> {
                val trip = event.trip
                val dates = formatDateRange(trip.startDate, trip.endDate)
                shareManager.share(
                    if (dates != null) {
                        getString(Res.string.share_trip_text, trip.name, dates)
                    } else {
                        getString(Res.string.share_trip_text_no_dates, trip.name)
                    },
                )
            }
        }
    }

    HomeScreen(
        state = state,
        onAction = viewModel::onAction,
    )

    val onDismiss = { viewModel.onAction(Action.OnDismissDialog) }
    val error = state.dialogError?.asString()
    when (val dialog = state.dialog) {
        null -> Unit
        Dialog.Create -> CreateTripDialog(
            isSaving = state.isSaving,
            error = error,
            onDismiss = onDismiss,
            onConfirm = { name, start, end, destination ->
                viewModel.onAction(Action.OnConfirmCreateTrip(name, start, end, destination))
            },
            onDestinationQueryChange = { viewModel.onAction(Action.OnDestinationQueryChanged(it)) },
            destinationResults = state.destinationResults,
            isSearchingDestination = state.isSearchingDestination,
        )
        is Dialog.Rename -> RenameTripDialog(
            initialName = dialog.trip.name,
            isSaving = state.isSaving,
            error = error,
            onDismiss = onDismiss,
            onConfirm = { viewModel.onAction(Action.OnConfirmRenameTrip(dialog.trip.id, it)) },
        )
        is Dialog.Reschedule -> RescheduleTripDialog(
            initialStartDate = dialog.trip.startDate,
            initialEndDate = dialog.trip.endDate,
            isSaving = state.isSaving,
            error = error,
            onDismiss = onDismiss,
            onConfirm = { start, end -> viewModel.onAction(Action.OnConfirmRescheduleTrip(dialog.trip.id, start, end)) },
        )
        is Dialog.ConfirmDelete -> ConfirmTripDialog(
            kind = TripDialogKind.Delete,
            tripName = dialog.trip.name,
            isSaving = state.isSaving,
            error = error,
            onDismiss = onDismiss,
            onConfirm = { viewModel.onAction(Action.OnConfirmDeleteTrip(dialog.trip.id)) },
        )
        is Dialog.ConfirmLeave -> ConfirmTripDialog(
            kind = TripDialogKind.Leave,
            tripName = dialog.trip.name,
            isSaving = state.isSaving,
            error = error,
            onDismiss = onDismiss,
            onConfirm = { viewModel.onAction(Action.OnConfirmLeaveTrip(dialog.trip.id)) },
        )
    }

    state.membersTripId?.let { tripId ->
        TripMembersRoute(
            tripId = tripId,
            onDismiss = { viewModel.onAction(Action.OnDismissMembers) },
        )
    }
}
