package com.falcon.tripingly.feature.trips.presentation.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.falcon.tripingly.core.common.util.DateUtils
import com.falcon.tripingly.core.designsystem.component.AppDialog
import com.falcon.tripingly.core.designsystem.component.FieldButton
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.feature.trips.generated.resources.Res
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

    AppDialog(
        title = stringResource(Res.string.reschedule_trip_title),
        confirmLabel = stringResource(Res.string.reschedule_trip_button_reschedule),
        onConfirm = { if (startDate != null && endDate != null) onConfirm(startDate, endDate) },
        onDismiss = onDismiss,
        error = error,
        isBusy = isSaving,
        confirmEnabled = startDate != null && endDate != null && (startDate != initialStartDate || endDate != initialEndDate),
    ) {
        FieldButton(
            text = DateUtils.formatRange(startDate, endDate),
            placeholder = stringResource(Res.string.reschedule_trip_dates_label),
            onClick = { showDatePicker = true },
            leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
        )
    }
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

@Preview
@Composable
private fun RescheduleTripDialogDarkPreview() {
    TripinglyTheme(darkTheme = true) {
        RescheduleTripDialog(initialStartDate = null, initialEndDate = null, onDismiss = {}, onConfirm = { _, _ -> }, isSaving = true)
    }
}
