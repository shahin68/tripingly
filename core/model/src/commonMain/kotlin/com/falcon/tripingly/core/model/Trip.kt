package com.falcon.tripingly.core.model

import kotlinx.datetime.LocalDate

data class Trip(
    val id: String,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val imageUrl: String? = null
)
