package com.falcon.tripingly.feature.map.presentation.screen

import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.domain.model.MapMarker
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.LocalDate

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

internal val mapScreenStatePreviewData = MapUiState(
    header = HeaderState(tripName = "Summer Vacation in Paris", editorMenu = EditorMenu(canDeleteDay = true)),
    dayTabs = DayTabsState(
        days = persistentListOf(
            DayTab("d1", 1, LocalDate(2025, 6, 1)),
            DayTab("d2", 2, LocalDate(2025, 6, 2)),
            DayTab("d3", 3, LocalDate(2025, 6, 3)),
        ),
    ),
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
