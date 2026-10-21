package com.falcon.tripingly.feature.map.presentation.screen

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.domain.model.MapMarker
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_day_label_no_date
import com.falcon.tripingly.feature.map.generated.resources.map_error_load_trip
import kotlinx.collections.immutable.persistentListOf

private val previewStops = persistentListOf(
    MapMarker(
        id = "1",
        position = Coordinates(48.8584, 2.2945),
        title = UiText.Server("Eiffel Tower"),
        orderNumber = 1,
        canDelete = true,
    ),
    MapMarker(
        id = "2",
        position = Coordinates(48.8606, 2.3376),
        title = UiText.Server("Louvre Museum"),
        orderNumber = 2,
        canDelete = true,
    ),
)

private val previewDays = DayTabsState(
    days = persistentListOf(1, 2, 3).let { numbers ->
        persistentListOf(*numbers.map { DayTab("d$it", it, UiText.Resource(Res.string.map_day_label_no_date, it)) }.toTypedArray())
    },
)

private val withStops = MapUiState(
    header = HeaderState(tripName = "Summer Vacation in Paris", editorMenu = EditorMenu(canDeleteDay = true)),
    dayTabs = previewDays,
    stopsCard = StopsCardState(
        stops = previewStops,
        selectedStopId = null,
        canClear = true,
        isRouteOn = false,
        route = null,
        canFindBestOrder = false,
        isFindingBestOrder = false,
    ),
    layers = MapLayersState(stops = previewStops),
    camera = CameraState(isReady = true, target = Coordinates.Paris, zoom = 13f),
)

/** The map with stops, while the trip loads, a day without stops, and a trip that couldn't load. */
internal class MapScreenPreviewStates : PreviewParameterProvider<MapUiState> {
    override val values = sequenceOf(
        withStops,
        MapUiState(header = HeaderState(isBusy = true)),
        withStops.copy(stopsCard = null, layers = MapLayersState()),
        MapUiState(message = MessageState(UiText.Resource(Res.string.map_error_load_trip), canRetry = false)),
    )
}
