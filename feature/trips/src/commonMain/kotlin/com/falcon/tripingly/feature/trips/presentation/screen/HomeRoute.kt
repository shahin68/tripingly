package com.falcon.tripingly.feature.trips.presentation.screen

import com.falcon.tripingly.core.designsystem.component.AppBottomSheet
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.core.common.util.DateUtils
import com.falcon.tripingly.core.common.util.ShareManager
import com.falcon.tripingly.core.ui.ObserveAsEvents
import com.falcon.tripingly.feature.trips.generated.resources.Res
import com.falcon.tripingly.feature.trips.generated.resources.share_trip_text
import com.falcon.tripingly.feature.trips.generated.resources.share_trip_text_no_dates
import com.falcon.tripingly.feature.trips.presentation.component.ConfirmTripDialog
import com.falcon.tripingly.feature.trips.presentation.component.CreateTripSheet
import com.falcon.tripingly.feature.trips.presentation.component.RenameTripDialog
import com.falcon.tripingly.feature.trips.presentation.component.RescheduleTripDialog
import com.falcon.tripingly.feature.trips.presentation.component.TripDialogKind
import com.falcon.tripingly.feature.trips.presentation.members.TripMembersRoute
import com.falcon.tripingly.feature.trips.presentation.permission.rememberNotificationPermissionRequest
import com.falcon.tripingly.feature.trips.presentation.screen.HomeViewModel.Action
import com.falcon.tripingly.feature.trips.presentation.screen.HomeViewModel.Dialog
import com.falcon.tripingly.feature.trips.presentation.screen.HomeViewModel.DialogState
import com.falcon.tripingly.feature.trips.presentation.screen.HomeViewModel.Event
import org.jetbrains.compose.resources.getString
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeRoute(
    onNavigateToMap: (String) -> Unit,
    /** The tab shell's floating top and bottom bars, which the list scrolls behind. */
    barsPadding: PaddingValues = PaddingValues(),
    viewModel: HomeViewModel = koinViewModel(),
    shareManager: ShareManager = koinInject(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val askForNotifications = rememberNotificationPermissionRequest()
    // Asked as Home opens, before anything is tapped; once per visit, rotation included.
    var askedForNotifications by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!askedForNotifications) {
            askedForNotifications = true
            askForNotifications()
        }
    }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is Event.NavigateToMap -> onNavigateToMap(event.tripId)
            is Event.ShareTrip -> {
                val trip = event.trip
                val dates = DateUtils.formatRange(trip.startDate, trip.endDate)
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

    HomeScreen(state = state, onAction = viewModel::onAction, barsPadding = barsPadding)

    state.dialog?.takeUnless { it.dialog is Dialog.Create }?.let { TripDialog(it, viewModel::onAction) }

    AppBottomSheet(
        value = state.dialog?.takeIf { it.dialog is Dialog.Create },
        onDismiss = { viewModel.onAction(Action.TripDialog.Dismiss) },
        dismissEnabled = state.dialog?.isSaving != true,
    ) { dialog, close ->
        val form = (dialog.dialog as Dialog.Create).form
        CreateTripSheet(form = form, isSaving = dialog.isSaving, error = dialog.error, onAction = viewModel::onAction, onClose = close)
    }

    AppBottomSheet(value = state.membersTripId, onDismiss = { viewModel.onAction(Action.Trips.DismissMembers) }) { tripId, close ->
        TripMembersRoute(tripId = tripId, onDismiss = close)
    }
}

@Composable
private fun TripDialog(state: DialogState, onAction: (Action) -> Unit) {
    val onDismiss = { onAction(Action.TripDialog.Dismiss) }
    val error = state.error?.asString()
    when (val dialog = state.dialog) {
        is Dialog.Create -> Unit
        is Dialog.Rename -> RenameTripDialog(
            name = dialog.name,
            currentName = dialog.trip.name,
            isSaving = state.isSaving,
            error = error,
            onDismiss = onDismiss,
            onConfirm = { onAction(Action.TripDialog.ConfirmRename(dialog.trip.id)) },
        )
        is Dialog.Reschedule -> RescheduleTripDialog(
            initialStartDate = dialog.trip.startDate,
            initialEndDate = dialog.trip.endDate,
            isSaving = state.isSaving,
            error = error,
            onDismiss = onDismiss,
            onConfirm = { start, end -> onAction(Action.TripDialog.ConfirmReschedule(dialog.trip.id, start, end)) },
        )
        is Dialog.ConfirmDelete -> ConfirmTripDialog(
            kind = TripDialogKind.Delete,
            tripName = dialog.trip.name,
            editorCount = dialog.trip.editorCount,
            isSaving = state.isSaving,
            error = error,
            onDismiss = onDismiss,
            onConfirm = { onAction(Action.TripDialog.ConfirmDelete(dialog.trip.id)) },
        )
        is Dialog.ConfirmLeave -> ConfirmTripDialog(
            kind = TripDialogKind.Leave,
            tripName = dialog.trip.name,
            isSaving = state.isSaving,
            error = error,
            onDismiss = onDismiss,
            onConfirm = { onAction(Action.TripDialog.ConfirmLeave(dialog.trip.id)) },
        )
    }
}
