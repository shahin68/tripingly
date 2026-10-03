package com.falcon.tripingly.feature.map.presentation.screen

import com.falcon.tripingly.feature.map.domain.model.Coordinates
import com.falcon.tripingly.feature.map.domain.model.MapMarker
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel.State
import kotlinx.datetime.LocalDate

internal val mapScreenStatePreviewData = State(
    tripName = "Summer Vacation in Paris",
    startDate = LocalDate(2025, 6, 1),
    endDate = LocalDate(2025, 6, 5),
    activeDayIndex = 0,
    cameraTarget = Coordinates.Paris,
    zoomLevel = 13f,
    markers = listOf(
        MapMarker(
            id = "1",
            position = Coordinates(48.8584, 2.2945),
            title = "Eiffel Tower",
            orderNumber = 1,
            snippet = "48.8584, 2.2945"
        ),
        MapMarker(
            id = "2",
            position = Coordinates(48.8606, 2.3376),
            title = "Louvre Museum",
            orderNumber = 2,
            snippet = "48.8606, 2.3376"
        )
    )
)
