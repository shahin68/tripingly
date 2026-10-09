package com.falcon.tripingly.feature.trips.presentation.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.common.util.DateUtils
import com.falcon.tripingly.core.model.place.PlaceSearchResult
import com.falcon.tripingly.core.model.trip.Destination
import com.falcon.tripingly.core.model.trip.GeoPoint
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import com.falcon.tripingly.feature.trips.generated.resources.Res
import com.falcon.tripingly.feature.trips.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTripDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, LocalDate, LocalDate, Destination?) -> Unit,
    onDestinationQueryChange: (String) -> Unit,
    destinationResults: ImmutableList<PlaceSearchResult>,
    isSearchingDestination: Boolean,
    isSaving: Boolean = false,
    error: String? = null,
) {
    var name by remember { mutableStateOf("") }
    var destinationText by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf<Destination?>(null) }
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
                                text = stringResource(Res.string.create_trip_picker_title),
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        },
                        headline = {
                             Text(
                                text = stringResource(Res.string.create_trip_picker_headline),
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
        title = { Text(stringResource(Res.string.create_trip_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.create_trip_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isSaving,
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                val dateRangeText = formatDateRange(selectedStartDate, selectedEndDate).orEmpty()

                OutlinedTextField(
                    value = dateRangeText,
                    onValueChange = {},
                    label = { Text(stringResource(Res.string.create_trip_dates_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true,
                    trailingIcon = {
                        TextButton(onClick = { showDatePicker = true }, enabled = !isSaving) {
                            Icon(Icons.Default.DateRange, contentDescription = null)
                        }
                    },
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = destinationText,
                    onValueChange = {
                        destinationText = it
                        destination = null
                        onDestinationQueryChange(it)
                    },
                    label = { Text(stringResource(Res.string.create_trip_destination_label)) },
                    leadingIcon = { Icon(Icons.Default.Place, contentDescription = null) },
                    trailingIcon = {
                        when {
                            isSearchingDestination -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            destinationText.isNotEmpty() -> IconButton(
                                onClick = {
                                    destinationText = ""
                                    destination = null
                                    onDestinationQueryChange("")
                                },
                                enabled = !isSaving,
                            ) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.create_trip_destination_clear))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isSaving,
                )
                if (destination == null && destinationResults.isNotEmpty()) {
                    destinationResults.take(MAX_DESTINATION_RESULTS).forEach { result ->
                        ListItem(
                            headlineContent = { Text(result.name) },
                            supportingContent = result.address?.let { { Text(it) } },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                destination = Destination(result.name, result.location)
                                destinationText = result.name
                                onDestinationQueryChange("")
                            },
                        )
                    }
                    Text(
                        text = stringResource(Res.string.create_trip_destination_attribution),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DialogError(error)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { 
                    if (selectedStartDate != null && selectedEndDate != null) {
                        onConfirm(name, selectedStartDate, selectedEndDate, destination)
                    }
                },
                enabled = !isSaving && name.isNotBlank() && selectedStartDate != null && selectedEndDate != null,
            ) {
                SavingLabel(isSaving, stringResource(Res.string.create_trip_button_create))
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
private fun CreateTripDialogPreview() {
    TripinglyTheme {
        CreateTripDialog(
            onDismiss = {},
            onConfirm = { _, _, _, _ -> },
            onDestinationQueryChange = {},
            destinationResults = persistentListOf(
                PlaceSearchResult("Paris", "Île-de-France, France", GeoPoint(48.8566, 2.3522)),
                PlaceSearchResult("Paris", "Texas, United States", GeoPoint(33.6609, -95.5555)),
            ),
            isSearchingDestination = false,
        )
    }
}

private const val MAX_DESTINATION_RESULTS = 5

