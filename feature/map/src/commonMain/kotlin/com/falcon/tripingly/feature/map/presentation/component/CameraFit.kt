package com.falcon.tripingly.feature.map.presentation.component

import com.falcon.tripingly.feature.map.domain.model.Coordinates
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.tan

/** Where the camera goes so every point shows in the part of the map that nothing covers. */
data class CameraFit(val center: Coordinates, val zoom: Float)

/**
 * The center and zoom that show all [points] on a map of [mapWidth] x [mapHeight], with [top] and
 * [bottom] covered by the screen's cards and [margin] kept free around the points. All sizes in dp.
 * Web Mercator, as Google Maps and Apple Maps use: one world is 256 dp wide at zoom 0.
 */
fun fitCamera(
    points: List<Coordinates>,
    mapWidth: Float,
    mapHeight: Float,
    top: Float,
    bottom: Float,
    margin: Float,
): CameraFit? {
    if (points.isEmpty()) return null
    val west = points.minOf { it.longitude }
    val east = points.maxOf { it.longitude }
    val south = mercatorY(points.minOf { it.latitude })
    val north = mercatorY(points.maxOf { it.latitude })

    val width = max(mapWidth - 2 * margin, 1f)
    val height = max(mapHeight - top - bottom - 2 * margin, 1f)
    val zoomForWidth = if (east > west) log2(width / (WORLD * (east - west) / 360.0)) else MAX_ZOOM
    val zoomForHeight = if (north > south) log2(height / (WORLD * (north - south) / (2 * PI))) else MAX_ZOOM
    val zoom = min(min(zoomForWidth, zoomForHeight), MAX_ZOOM)

    // The free part's middle sits (bottom - top) / 2 above the map's middle, so the camera aims that much lower.
    val shift = (bottom - top) / 2 / (WORLD * 2.0.pow(zoom)) * 2 * PI
    return CameraFit(
        center = Coordinates(latitude = latitudeOf((north + south) / 2 - shift), longitude = (west + east) / 2),
        zoom = zoom.toFloat(),
    )
}

private const val WORLD = 256.0
private const val MAX_ZOOM = 15.0

private fun mercatorY(latitude: Double): Double = ln(tan(PI / 4 + latitude * PI / 360))

private fun latitudeOf(y: Double): Double = (2 * atan(exp(y)) - PI / 2) * 180 / PI
