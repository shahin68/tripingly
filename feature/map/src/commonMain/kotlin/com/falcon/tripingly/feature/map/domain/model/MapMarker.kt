package com.falcon.tripingly.feature.map.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class MapMarker(
    val id: String,
    val position: Coordinates,
    val title: String,
    val orderNumber: Int,
    val snippet: String? = null,
    /** The cover photo, drawn inside the pin; see [coverThumbUrl]. */
    val coverPhotoId: String? = null,
    val coverThumbUrl: String? = null,
    val photoCount: Int = 0,
    val color: Long = 0xFF2196F3 // Default Material Blue
)
