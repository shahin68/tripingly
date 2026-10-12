package com.falcon.tripingly.feature.map.domain.model

import androidx.compose.runtime.Immutable
import com.falcon.tripingly.core.location.model.Coordinates

/** Stops the map should show all of. A new [id] frames them again, even when the stops are the same. */
@Immutable
data class CameraFrame(
    val points: List<Coordinates>,
    val id: Int,
)
