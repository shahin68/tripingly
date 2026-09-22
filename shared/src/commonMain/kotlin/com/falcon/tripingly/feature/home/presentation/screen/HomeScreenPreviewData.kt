package com.falcon.tripingly.feature.home.presentation.screen

import com.falcon.tripingly.feature.home.domain.model.Trip
import kotlinx.datetime.LocalDate

internal val tripsPreviewData = listOf(
    Trip(
        id = "1",
        name = "Summer Vacation in Paris",
        startDate = LocalDate(2025, 6, 1),
        endDate = LocalDate(2025, 6, 15)
    ),
    Trip(
        id = "2",
        name = "Tokyo Adventure",
        startDate = LocalDate(2025, 10, 10),
        endDate = LocalDate(2025, 10, 20)
    )
)