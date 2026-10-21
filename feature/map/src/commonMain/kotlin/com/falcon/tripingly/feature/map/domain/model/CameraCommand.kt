package com.falcon.tripingly.feature.map.domain.model

import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.model.place.GeoBounds

/**
 * A move the screen asks of the map's camera. The map flies there (it jumps instead while it hasn't loaded
 * yet) and reports where it comes to rest. The map keeps out of its content padding, the part the screen's
 * header and cards cover.
 */
sealed interface CameraCommand {
    data class MoveTo(val target: Coordinates, val zoom: Float) : CameraCommand

    /**
     * Shows all of [bounds], with a margin, in the part of the map nothing covers. A new map's first fit isn't
     * [isAnimated]: the map opens right there instead of flying in from the default.
     */
    data class Fit(val bounds: GeoBounds, val isAnimated: Boolean = true) : CameraCommand

    companion object {
        /**
         * Fits [points], and at least a few streets around them, so one stop, or stops next door to each other,
         * don't zoom in to the houses.
         */
        fun fit(points: List<Coordinates>): Fit {
            val south = points.minOf { it.latitude }
            val north = points.maxOf { it.latitude }
            val west = points.minOf { it.longitude }
            val east = points.maxOf { it.longitude }
            val latPad = ((MIN_SPAN_DEGREES - (north - south)) / 2).coerceAtLeast(0.0)
            val lngPad = ((MIN_SPAN_DEGREES - (east - west)) / 2).coerceAtLeast(0.0)
            return Fit(GeoBounds(south = south - latPad, west = west - lngPad, north = north + latPad, east = east + lngPad))
        }

        /** About 500 m. */
        private const val MIN_SPAN_DEGREES = 0.005
    }
}
