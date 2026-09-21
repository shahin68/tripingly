package com.falcon.tripingly.feature.home.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.datetime.LocalDate

@Immutable
data class Trip(
    val id: String,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val imageUrl: String? = null
)
