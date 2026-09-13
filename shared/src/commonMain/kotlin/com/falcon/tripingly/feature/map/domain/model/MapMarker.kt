package com.falcon.tripingly.feature.map.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class MapMarker(
    val id: String,
    val position: Coordinates,
    val title: String,
    val orderNumber: Int,
    val snippet: String? = null
)
