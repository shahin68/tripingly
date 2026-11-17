package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.ui.UiText
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** The ring of places around a pin (Around, Tripinly Pro): [radiusMeters] around [center], [label] its size ("500 m"). */
@Immutable
data class AroundRing(val center: Coordinates, val radiusMeters: Int, val label: UiText) {

    /** The ring's east edge. */
    fun edge(): Coordinates = handle(0f)

    /**
     * Where on the ring the handle is at [angle], in radians from east, south positive (clockwise as on a north-up map).
     */
    fun handle(angle: Float): Coordinates = Coordinates(
        center.latitude - radiusMeters * sin(angle) / METERS_PER_DEGREE,
        center.longitude + radiusMeters * cos(angle) / (METERS_PER_DEGREE * cos(center.latitude * PI / 180)),
    )

    /**
     * The ring's size in metres for a finger at [finger] px, with the ring drawn at [centerPx] [radiusPx] px wide: the
     * finger's distance from the pin in the ring's own metres per pixel. Null while the ring has no size on screen.
     */
    fun radiusAt(centerPx: Offset, radiusPx: Float, finger: Offset): Int? {
        if (radiusPx <= 0f) return null
        return ((finger - centerPx).getDistance() * radiusMeters / radiusPx).roundToInt()
    }
}

private const val METERS_PER_DEGREE = 111_320.0
