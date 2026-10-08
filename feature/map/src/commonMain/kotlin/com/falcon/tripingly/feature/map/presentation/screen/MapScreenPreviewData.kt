package com.falcon.tripingly.feature.map.presentation.screen

import com.falcon.tripingly.feature.map.domain.model.Coordinates
import com.falcon.tripingly.feature.map.domain.model.MapMarker
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel.DayTab
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel.State
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.LocalDate

internal val mapScreenStatePreviewData = State(
    tripName = "Summer Vacation in Paris",
    days = persistentListOf(
        DayTab("d1", 1, LocalDate(2025, 6, 1)),
        DayTab("d2", 2, LocalDate(2025, 6, 2)),
        DayTab("d3", 3, LocalDate(2025, 6, 3)),
    ),
    activeDayIndex = 0,
    canEdit = true,
    cameraTarget = Coordinates.Paris,
    zoomLevel = 13f,
    markers = persistentListOf(
        MapMarker(
            id = "1",
            position = Coordinates(48.8584, 2.2945),
            title = "Eiffel Tower",
            orderNumber = 1,
        ),
        MapMarker(
            id = "2",
            position = Coordinates(48.8606, 2.3376),
            title = "Louvre Museum",
            orderNumber = 2,
        )
    )
)
