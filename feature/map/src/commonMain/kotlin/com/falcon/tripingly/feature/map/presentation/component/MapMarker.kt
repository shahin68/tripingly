package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.runtime.Immutable
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.ui.UiText

@Immutable
data class MapMarker(
    val id: String,
    val position: Coordinates,
    /** The stop's name, or "Stop 3" while it has none (one just tapped on the map waits for the server's). */
    val title: UiText,
    val orderNumber: Int,
    val snippet: String? = null,
    /** The cover photo, drawn inside the pin; see [coverThumbUrl]. */
    val coverPhotoId: String? = null,
    val coverThumbUrl: String? = null,
    val photoCount: Int = 0,
    val hotCount: Int = 0,
    val hotByMe: Boolean = false,
    /** False for a custom spot that is only in private trips: no flame. */
    val canMarkHot: Boolean = true,
    val commentCount: Int = 0,
    /** The place the stop is at; its images stand in for a missing cover. */
    val placeId: String? = null,
    /** I added it, or the trip is mine. */
    val canDelete: Boolean = false,
)
