package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.falcon.tripingly.core.common.util.DateUtils
import com.falcon.tripingly.core.designsystem.component.PickerRow
import com.falcon.tripingly.core.designsystem.component.PickerSheet
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_add_to_trip_pick_day
import com.falcon.tripingly.feature.map.generated.resources.map_add_to_trip_pick_trip
import com.falcon.tripingly.feature.map.generated.resources.map_day_label_no_date
import com.falcon.tripingly.feature.map.presentation.map.AddToTripState
import com.falcon.tripingly.feature.map.presentation.map.MapAction
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

/** The + on a trip I only view: which of my trips, then which day (the same picker as Nearby's). */
@Composable
internal fun AddToTripSheet(picker: AddToTripState, onAction: (MapAction) -> Unit, onClose: () -> Unit) {
    val trip = picker.trip
    if (trip == null) {
        PickerSheet(
            title = stringResource(Res.string.map_add_to_trip_pick_trip, picker.name.asString()),
            rows = picker.trips.map { PickerRow(it.id, it.name, DateUtils.formatRange(it.startDate, it.endDate)) },
            onPick = { onAction(MapAction.Sheet.PickTrip(it.id)) },
            onClose = onClose,
        )
    } else {
        val days = picker.days?.map { it to stringResource(Res.string.map_day_label_no_date, it.position + 1) }
        PickerSheet(
            title = stringResource(Res.string.map_add_to_trip_pick_day, trip.name),
            rows = days?.map { (day, label) -> PickerRow(day.id, label, day.date?.let(DateUtils::formatAbbreviated)) },
            onPick = { onAction(MapAction.Sheet.PickDay(it.id)) },
            onClose = onClose,
        )
    }
}

@Preview
@Composable
private fun AddToTripSheetLoadingPreview() {
    TripinglyTheme {
        AddToTripSheet(AddToTripState(UiText.Server("Café Central"), persistentListOf(), trip = null, days = null), onAction = {}, onClose = {})
    }
}
