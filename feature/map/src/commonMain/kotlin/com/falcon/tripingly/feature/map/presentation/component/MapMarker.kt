package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.runtime.Immutable
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.ui.UiText

@Immutable
data class MapMarker(
    val id: String,
    val position: Coordinates,
    /** The stop's name; empty while it has none (its number is in the bold "Stop 3" under it, Shahin 2026-11-13). */
    val title: UiText,
    val orderNumber: Int,
    /** Its place's kind, for its icon in the card and the public transport steps; null while unknown. */
    val category: PlaceCategory? = null,
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
    /** Something happened here I haven't seen (a comment, a like, a change): the pin carries a dot. */
    val isNew: Boolean = false,
    /** Added without a name, which the server is still finding: the title shows a small spinner meanwhile. */
    val isNaming: Boolean = false,
)
