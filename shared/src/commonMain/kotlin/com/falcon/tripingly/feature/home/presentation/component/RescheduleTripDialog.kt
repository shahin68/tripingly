package com.falcon.tripingly.feature.home.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.falcon.tripingly.core.util.DateUtils
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import tripingly.shared.generated.resources.Res
import tripingly.shared.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RescheduleTripDialog(
    initialStartDate: LocalDate,
    initialEndDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    
    val today = DateUtils.today()
    val dateRangePickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStartDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
        initialSelectedEndDateMillis = initialEndDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val selectableDate = Instant.fromEpochMilliseconds(utcTimeMillis).toLocalDateTime(TimeZone.UTC).date
                return selectableDate >= today
            }
        }
    )
    
    val selectedStartDate = dateRangePickerState.selectedStartDateMillis?.let {
        Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date
    }
    val selectedEndDate = dateRangePickerState.selectedEndDateMillis?.let {
        Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date
    }

    if (showDatePicker) {
        Dialog(
            onDismissRequest = { showDatePicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(600.dp),
                shape = RoundedCornerShape(28.dp),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    DateRangePicker(
                        state = dateRangePickerState,
                        modifier = Modifier.weight(1f),
                        title = {
                            Text(
                                text = stringResource(Res.string.reschedule_trip_title),
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        },
                        headline = {
                             Text(
                                text = stringResource(Res.string.reschedule_trip_picker_headline),
                                modifier = Modifier.padding(horizontal = 16.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        showModeToggle = false,
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text(stringResource(Res.string.common_cancel))
                        }
                        TextButton(onClick = { showDatePicker = false }) {
                            Text(stringResource(Res.string.common_ok))
                        }
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.reschedule_trip_title)) },
        text = {
            val dateRangeText = if (selectedStartDate != null && selectedEndDate != null) {
                "${DateUtils.formatFormal(selectedStartDate)} - ${DateUtils.formatFormal(selectedEndDate)}"
            } else ""

            OutlinedTextField(
                value = dateRangeText,
                onValueChange = {},
                label = { Text(stringResource(Res.string.reschedule_trip_dates_label)) },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                trailingIcon = {
                    TextButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = null)
                    }
                },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { 
                    if (selectedStartDate != null && selectedEndDate != null) {
                        onConfirm(selectedStartDate, selectedEndDate)
                    }
                },
                enabled = selectedStartDate != null && selectedEndDate != null && (selectedStartDate != initialStartDate || selectedEndDate != initialEndDate)
            ) {
                Text(stringResource(Res.string.reschedule_trip_button_reschedule))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.common_cancel))
            }
        }
    )
}
