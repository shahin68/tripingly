package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.location.model.Coordinates
import kotlin.math.PI
import kotlin.math.cos

/*
 * While no route shows, the open day's stops are joined by curved dashed lines whose dashes march from each
 * stop to the next, so the order of the day reads at a glance. Drawn by the app alone: no routing call.
 */

/** Playful coral, apart from the stops' and the route's blue. */
internal val StopPathColor = Color(0xFFFF7A59)

internal val StopPathWidth = 4.dp
internal val StopPathDash = 10.dp
internal val StopPathGap = 8.dp

/** The dashes move one dash and gap further in this time. */
internal const val STOP_PATH_PERIOD_MILLIS = 600

/** How far each curve bows out, as a share of the distance between its stops. */
private const val BEND = 0.2

private const val STEPS_PER_LEG = 24

/**
 * The [stops] joined in order by gentle arcs, each bowing to the right of its direction of travel. Stops in
 * the same spot are joined directly.
 */
internal fun curvedStopPath(stops: List<Coordinates>): List<Coordinates> {
    if (stops.size < 2) return stops
    val path = mutableListOf(stops.first())
    stops.zipWithNext { from, to ->
        // Longitude degrees shrink away from the equator: bend in a square view so arcs look the same everywhere.
        val xScale = cos((from.latitude + to.latitude) / 2 * PI / 180)
        val dx = (to.longitude - from.longitude) * xScale
        val dy = to.latitude - from.latitude
        if (dx == 0.0 && dy == 0.0) {
            path += to
            return@zipWithNext
        }
        // The middle, moved sideways to the right of travel (the normal (dy, -dx)).
        val controlX = (from.longitude * xScale + to.longitude * xScale) / 2 + dy * BEND
        val controlY = (from.latitude + to.latitude) / 2 - dx * BEND
        for (step in 1 until STEPS_PER_LEG) {
            val t = step.toDouble() / STEPS_PER_LEG
            val a = (1 - t) * (1 - t)
            val b = 2 * (1 - t) * t
            val c = t * t
            path += Coordinates(
                latitude = a * from.latitude + b * controlY + c * to.latitude,
                longitude = (a * from.longitude * xScale + b * controlX + c * to.longitude * xScale) / xScale,
            )
        }
        path += to
    }
    return path
}
