package com.falcon.tripingly.feature.trips.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.falcon.tripingly.core.common.util.DateUtils
import com.falcon.tripingly.feature.trips.generated.resources.Res
import com.falcon.tripingly.feature.trips.generated.resources.common_cancel
import com.falcon.tripingly.feature.trips.generated.resources.common_ok
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_picker_headline
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_picker_title
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus

/**
 * A trip's date range picker (New trip, Reschedule), from today on. OK hands back the picked range
 * (the end is null when only one day was tapped); Cancel keeps the dates as they were.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TripDatesPickerDialog(
    initialStartDate: LocalDate?,
    initialEndDate: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate?, LocalDate?) -> Unit,
    title: StringResource = Res.string.create_trip_picker_title,
    headline: StringResource = Res.string.create_trip_picker_headline,
) {
    val today = DateUtils.today()
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStartDate?.toEpochMillis(),
        initialSelectedEndDateMillis = initialEndDate?.toEpochMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis.toLocalDate() >= today
        },
    )

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ElevatedCard(
            // At most 600 dp tall, less in landscape: the months scroll inside.
            modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(max = 600.dp),
            shape = RoundedCornerShape(28.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                DateRangePicker(
                    state = state,
                    modifier = Modifier.weight(1f),
                    title = {
                        Text(
                            text = stringResource(title),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                    headline = {
                        Text(
                            text = stringResource(headline),
                            modifier = Modifier.padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    showModeToggle = false,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.common_cancel))
                    }
                    TextButton(
                        onClick = {
                            onConfirm(state.selectedStartDateMillis?.toLocalDate(), state.selectedEndDateMillis?.toLocalDate())
                        },
                    ) {
                        Text(stringResource(Res.string.common_ok))
                    }
                }
            }
        }
    }
}

private fun LocalDate.toEpochMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

private fun Long.toLocalDate(): LocalDate = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date

/** Nothing picked yet, and a long weekend from today. */
internal class TripDatesPreviewStates : PreviewParameterProvider<Pair<LocalDate?, LocalDate?>> {
    override val values = sequenceOf(null to null, DateUtils.today().let { it to it.plus(3, DateTimeUnit.DAY) })
}

@Preview
@Composable
private fun TripDatesPickerDialogPreview(@PreviewParameter(TripDatesPreviewStates::class) dates: Pair<LocalDate?, LocalDate?>) {
    TripinglyTheme {
        TripDatesPickerDialog(initialStartDate = dates.first, initialEndDate = dates.second, onDismiss = {}, onConfirm = { _, _ -> })
    }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun TripDatesPickerDialogLandscapePreview() {
    TripinglyTheme(darkTheme = true) {
        TripDatesPickerDialog(initialStartDate = null, initialEndDate = null, onDismiss = {}, onConfirm = { _, _ -> })
    }
}
