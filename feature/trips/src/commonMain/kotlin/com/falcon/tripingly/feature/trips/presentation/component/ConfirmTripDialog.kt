package com.falcon.tripingly.feature.trips.presentation.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.feature.trips.generated.resources.Res
import com.falcon.tripingly.feature.trips.generated.resources.common_cancel
import com.falcon.tripingly.feature.trips.generated.resources.delete_trip_button
import com.falcon.tripingly.feature.trips.generated.resources.delete_trip_message
import com.falcon.tripingly.feature.trips.generated.resources.delete_trip_title
import com.falcon.tripingly.feature.trips.generated.resources.leave_trip_button
import com.falcon.tripingly.feature.trips.generated.resources.leave_trip_message
import com.falcon.tripingly.feature.trips.generated.resources.leave_trip_title
import org.jetbrains.compose.resources.stringResource

enum class TripDialogKind { Delete, Leave }

/** Confirms a change no one can undo: deleting a trip, or leaving someone else's. */
@Composable
fun ConfirmTripDialog(
    kind: TripDialogKind,
    tripName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    isSaving: Boolean = false,
    error: String? = null,
) {
    val title = when (kind) {
        TripDialogKind.Delete -> Res.string.delete_trip_title
        TripDialogKind.Leave -> Res.string.leave_trip_title
    }
    val message = when (kind) {
        TripDialogKind.Delete -> Res.string.delete_trip_message
        TripDialogKind.Leave -> Res.string.leave_trip_message
    }
    val button = when (kind) {
        TripDialogKind.Delete -> Res.string.delete_trip_button
        TripDialogKind.Leave -> Res.string.leave_trip_button
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column {
                Text(stringResource(message, tripName))
                DialogError(error)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isSaving) {
                if (isSaving) {
                    SavingLabel(isSaving = true, label = "")
                } else {
                    Text(stringResource(button), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(stringResource(Res.string.common_cancel))
            }
        },
    )
}

@Preview
@Composable
private fun ConfirmTripDialogPreview() {
    TripinglyTheme {
        ConfirmTripDialog(TripDialogKind.Delete, "Summer Vacation in Paris", onDismiss = {}, onConfirm = {})
    }
}
