package com.falcon.tripingly.feature.home.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.falcon.tripingly.core.util.DateUtils
import kotlinx.datetime.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTripDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, LocalDate, LocalDate) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    
    val today = DateUtils.today()
    val dateRangePickerState = rememberDateRangePickerState(
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
                                text = "Select Trip Dates",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        },
                        headline = {
                             Text(
                                text = "Choose the range",
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
                            Text("Cancel")
                        }
                        TextButton(onClick = { showDatePicker = false }) {
                            Text("OK")
                        }
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Trip") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Trip Name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                val dateRangeText = if (selectedStartDate != null && selectedEndDate != null) {
                    "${DateUtils.formatFormal(selectedStartDate)} - ${DateUtils.formatFormal(selectedEndDate)}"
                } else ""

                OutlinedTextField(
                    value = dateRangeText,
                    onValueChange = {},
                    label = { Text("Dates") },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true,
                    trailingIcon = {
                        TextButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.DateRange, contentDescription = null)
                        }
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { 
                    if (selectedStartDate != null && selectedEndDate != null) {
                        onConfirm(name, selectedStartDate, selectedEndDate)
                    }
                },
                enabled = (name.isNotBlank() && selectedStartDate != null && selectedEndDate != null),
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
