package com.falcon.tripingly.feature.social.presentation.nearby

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.falcon.tripingly.core.common.util.DateUtils
import com.falcon.tripingly.core.designsystem.component.PickerRow
import com.falcon.tripingly.core.designsystem.component.PickerSheet
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_add_place_pick_day
import com.falcon.tripingly.feature.social.generated.resources.social_add_place_pick_trip
import com.falcon.tripingly.feature.social.generated.resources.social_day_number
import com.falcon.tripingly.feature.social.presentation.component.AddPlacePreviewStates
import com.falcon.tripingly.feature.social.presentation.nearby.NearbyViewModel.Action
import com.falcon.tripingly.feature.social.presentation.nearby.NearbyViewModel.AddPlaceState
import org.jetbrains.compose.resources.stringResource

/** Adds a Nearby place to a day: pick one of my trips, then the day. */
@Composable
internal fun AddPlaceSheet(addPlace: AddPlaceState, onAction: (Action) -> Unit, onClose: () -> Unit) {
    val trip = addPlace.trip
    if (trip == null) {
        PickerSheet(
            title = stringResource(Res.string.social_add_place_pick_trip, addPlace.place.name),
            rows = addPlace.trips.map { PickerRow(it.id, it.name, DateUtils.formatRange(it.startDate, it.endDate)) },
            onPick = { row -> addPlace.trips.firstOrNull { it.id == row.id }?.let { onAction(Action.AddToTrip.PickTrip(it)) } },
            onClose = onClose,
        )
    } else {
        val dayLabels = addPlace.days.map { stringResource(Res.string.social_day_number, it.position + 1) }
        PickerSheet(
            title = stringResource(Res.string.social_add_place_pick_day, trip.name),
            rows = if (addPlace.isLoadingDays) null else addPlace.days.mapIndexed { i, day -> PickerRow(day.id, dayLabels[i], day.date?.let(DateUtils::formatAbbreviated)) },
            onPick = { row -> addPlace.days.firstOrNull { it.id == row.id }?.let { onAction(Action.AddToTrip.PickDay(it)) } },
            onClose = onClose,
        )
    }
}

@Preview
@Composable
private fun AddPlaceSheetPreview(@PreviewParameter(AddPlacePreviewStates::class) state: AddPlaceState) {
    TripinglyTheme { AddPlaceSheet(addPlace = state, onAction = {}, onClose = {}) }
}
