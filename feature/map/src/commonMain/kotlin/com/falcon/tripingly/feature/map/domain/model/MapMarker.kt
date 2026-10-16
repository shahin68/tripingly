package com.falcon.tripingly.feature.map.domain.model

import androidx.compose.runtime.Immutable
import com.falcon.tripingly.core.location.model.Coordinates

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
    val likeCount: Int = 0,
    val likedByMe: Boolean = false,
    val commentCount: Int = 0,
    /** The place the stop is at; its images stand in for a missing cover. */
    val placeId: String? = null,
    /** I added it, or the trip is mine. */
    val canDelete: Boolean = false,
    val color: Long = 0xFF2196F3 // Default Material Blue
)
