package com.falcon.tripingly.feature.map.presentation.component

import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.model.place.MapPlace
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/**
 * A map tile the place pins are painted into: [POINTS] a side, Web Mercator, numbered like Google
 * Maps numbers its tiles on Android and iOS. Positions are in points from the tile's top left corner.
 */
internal class PlaceTile(x: Int, y: Int, zoom: Int) {
    private val worldPoints = POINTS * (1 shl zoom).toDouble()
    private val left = x * POINTS.toDouble()
    private val top = y * POINTS.toDouble()

    /** The tile's area and [margin] points around it, so pins on a tile edge are drawn whole. */
    fun area(margin: Double) = GeoBounds(
        south = latitudeAt(top + POINTS + margin),
        west = longitudeAt(left - margin),
        north = latitudeAt(top - margin),
        east = longitudeAt(left + POINTS + margin),
    )

    fun x(longitude: Double): Double = (longitude + 180) / 360 * worldPoints - left

    fun y(latitude: Double): Double {
        val phi = latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE) * PI / 180
        return (1 - ln(tan(PI / 4 + phi / 2)) / PI) / 2 * worldPoints - top
    }

    private fun latitudeAt(y: Double): Double =
        atan(sinh(PI * (1 - 2 * y.coerceIn(0.0, worldPoints) / worldPoints))) * 180 / PI

    private fun longitudeAt(x: Double): Double = (x / worldPoints * 360 - 180).coerceIn(-180.0, 180.0)

    companion object {
        const val POINTS = 256
        private const val MAX_LATITUDE = 85.05112878

        // A cluster: at least 36 points round, a 2-point white ring, its count in bold.
        const val CLUSTER_RADIUS_POINTS = 18f
        const val CLUSTER_PADDING_POINTS = 8f
        const val CLUSTER_BORDER_POINTS = 2f
        const val CLUSTER_TEXT_POINTS = 14f
    }
}

/** Hot spots over plain places, the most liked on top. */
internal fun List<MapPlace>.inDrawingOrder(): List<MapPlace> = sortedWith(compareBy({ it.isTripinly }, { it.likeCount }))
