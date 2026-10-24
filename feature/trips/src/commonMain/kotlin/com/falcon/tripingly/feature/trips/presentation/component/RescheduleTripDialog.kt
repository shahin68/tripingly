package com.falcon.tripingly.feature.trips.presentation.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.falcon.tripingly.core.common.util.DateUtils
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.feature.trips.generated.resources.Res
import com.falcon.tripingly.feature.trips.generated.resources.common_cancel
import com.falcon.tripingly.feature.trips.generated.resources.reschedule_trip_button_reschedule
import com.falcon.tripingly.feature.trips.generated.resources.reschedule_trip_dates_label
import com.falcon.tripingly.feature.trips.generated.resources.reschedule_trip_picker_headline
import com.falcon.tripingly.feature.trips.generated.resources.reschedule_trip_title
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/** New dates for a trip; only the picker's OK changes them, and they survive rotation. */
@Composable
fun RescheduleTripDialog(
    initialStartDate: LocalDate?,
    initialEndDate: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit,
    isSaving: Boolean = false,
    error: String? = null,
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    // Saved as ISO text, which survives rotation.
    var start by rememberSaveable { mutableStateOf(initialStartDate?.toString()) }
    var end by rememberSaveable { mutableStateOf(initialEndDate?.toString()) }
    val startDate = start?.let(LocalDate::parse)
    val endDate = end?.let(LocalDate::parse)

    if (showDatePicker) {
        TripDatesPickerDialog(
            initialStartDate = startDate,
            initialEndDate = endDate,
            onDismiss = { showDatePicker = false },
            onConfirm = { pickedStart, pickedEnd ->
                showDatePicker = false
                start = pickedStart?.toString()
                end = pickedEnd?.toString()
            },
            title = Res.string.reschedule_trip_title,
            headline = Res.string.reschedule_trip_picker_headline,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.reschedule_trip_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = DateUtils.formatRange(startDate, endDate).orEmpty(),
                    onValueChange = {},
                    label = { Text(stringResource(Res.string.reschedule_trip_dates_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true,
                    trailingIcon = {
                        TextButton(onClick = { showDatePicker = true }, enabled = !isSaving) {
                            Icon(Icons.Default.DateRange, contentDescription = null)
                        }
                    },
                )
                DialogError(error)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (startDate != null && endDate != null) onConfirm(startDate, endDate) },
                enabled = !isSaving && startDate != null && endDate != null &&
                    (startDate != initialStartDate || endDate != initialEndDate),
            ) {
                SavingLabel(isSaving, stringResource(Res.string.reschedule_trip_button_reschedule))
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
private fun RescheduleTripDialogPreview() {
    TripinglyTheme {
        RescheduleTripDialog(
            initialStartDate = rescheduleTripStartDatePreviewData,
            initialEndDate = rescheduleTripEndDatePreviewData,
            onDismiss = {},
            onConfirm = { _, _ -> },
        )
    }
}
