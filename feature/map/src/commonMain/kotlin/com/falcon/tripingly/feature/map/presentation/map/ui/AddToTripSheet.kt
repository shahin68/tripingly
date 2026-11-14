package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.falcon.tripingly.core.common.util.DateUtils
import com.falcon.tripingly.core.designsystem.component.PickerRow
import com.falcon.tripingly.core.designsystem.component.PickerSheet
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.core.model.trip.Trip
import com.falcon.tripingly.core.model.trip.TripDay
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_add_to_trip_pick_day
import com.falcon.tripingly.feature.map.generated.resources.map_add_to_trip_pick_trip
import com.falcon.tripingly.feature.map.generated.resources.map_day_label_no_date
import com.falcon.tripingly.feature.map.presentation.map.AddToTripState
import com.falcon.tripingly.feature.map.presentation.map.MapAction
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

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

/** My trips, then a trip's days loading, then its days. */
internal class AddToTripPreviewStates : PreviewParameterProvider<AddToTripState> {
    private val vienna = Trip(
        id = "t-1",
        name = "A week in Vienna",
        startDate = LocalDate(2026, 6, 1),
        endDate = LocalDate(2026, 6, 7),
        visibility = TripVisibility.PRIVATE,
        role = TripRole.OWNER,
        owner = UserSummary("u-1", "me", "Me"),
        dayCount = 7,
        markerCount = 4,
        createdAt = Instant.fromEpochSeconds(1_790_000_000),
        updatedAt = Instant.fromEpochSeconds(1_790_000_000),
    )
    private val trips = persistentListOf(vienna, vienna.copy(id = "t-2", name = "Weekend in Paris", startDate = null, endDate = null))
    private val days = persistentListOf(TripDay("d-1", 0, LocalDate(2026, 6, 1), emptyList()), TripDay("d-2", 1, LocalDate(2026, 6, 2), emptyList()))
    override val values = sequenceOf(
        AddToTripState(UiText.Server("Café Central"), trips, trip = null, days = null),
        AddToTripState(UiText.Server("Café Central"), trips, trip = trips.first(), days = null),
        AddToTripState(UiText.Server("Café Central"), trips, trip = trips.first(), days = days),
    )
}

@Composable
private fun AddToTripSheetPreviewContent(state: AddToTripState) {
    Column(Modifier.background(MaterialTheme.colorScheme.surface)) { AddToTripSheet(state, onAction = {}, onClose = {}) }
}

@Preview
@Composable
private fun AddToTripSheetPreview(@PreviewParameter(AddToTripPreviewStates::class) state: AddToTripState) {
    TripinglyTheme { AddToTripSheetPreviewContent(state) }
}

@Preview
@Composable
private fun AddToTripSheetDarkPreview(@PreviewParameter(AddToTripPreviewStates::class) state: AddToTripState) {
    TripinglyTheme(darkTheme = true) { AddToTripSheetPreviewContent(state) }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun AddToTripSheetLandscapePreview() {
    TripinglyTheme { AddToTripSheetPreviewContent(AddToTripPreviewStates().values.last()) }
}
