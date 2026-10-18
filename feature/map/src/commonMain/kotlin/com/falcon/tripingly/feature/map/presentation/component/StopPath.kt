package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.location.model.Coordinates
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * While no route shows, the open day's stops are joined by one smooth dashed line whose dashes march from each
 * stop to the next, so the order of the day reads at a glance. Drawn by the app alone: no routing call.
 */

/** Playful coral, apart from the stops' and the route's blue. */
internal val StopPathColor = Color(0xFFFF7A59)

internal val StopPathWidth = 4.dp
internal val StopPathDash = 10.dp
internal val StopPathGap = 8.dp

/** The dashes move one dash and gap further in this time. */
internal const val STOP_PATH_PERIOD_MILLIS = 600

/** How far the line between just two stops bows out, as an angle off the straight line (radians). */
private const val TWO_STOP_BEND = 0.4

private const val STEPS_PER_LEG = 24

/**
 * One smooth line through the [stops] in order, with no corner at any stop, like a line drawn by hand: at
 * each stop it heads from the stop before towards the one after, and at the first and last stop it swings
 * in the way the next curve would. Two stops get a gentle arc; stops in the same spot are joined directly.
 */
internal fun curvedStopPath(stops: List<Coordinates>): List<Coordinates> {
    if (stops.size < 2) return stops
    // Longitude degrees shrink away from the equator: curve in a square view so curves look the same everywhere.
    val xScale = cos(stops.sumOf { it.latitude } / stops.size * PI / 180)
    val points = stops.map { Point(it.longitude * xScale, it.latitude) }
    val tangents = tangents(points)
    val path = mutableListOf(stops.first())
    for (i in 0 until points.lastIndex) {
        // A cubic curve from stop i to stop i + 1 leaving and arriving along their tangents.
        val start = points[i]
        val end = points[i + 1]
        // Two stops in the same spot: nothing to draw between them.
        if (start == end) {
            path += stops[i + 1]
            continue
        }
        val c1 = start + tangents[i] / 3.0
        val c2 = end - tangents[i + 1] / 3.0
        for (step in 1 until STEPS_PER_LEG) {
            val t = step.toDouble() / STEPS_PER_LEG
            val u = 1 - t
            val p = start * (u * u * u) + c1 * (3 * u * u * t) + c2 * (3 * u * t * t) + end * (t * t * t)
            path += Coordinates(latitude = p.y, longitude = p.x / xScale)
        }
        path += stops[i + 1]
    }
    return path
}

/** The direction (and pull) of the line at each stop. */
private fun tangents(points: List<Point>): List<Point> {
    if (points.size == 2) {
        val chord = points[1] - points[0]
        // Leaving to the right of the straight line and coming back to it: the arc bows right of travel.
        return listOf(chord.rotated(TWO_STOP_BEND), chord.rotated(-TWO_STOP_BEND))
    }
    val inner = List(points.size) { i ->
        if (i == 0 || i == points.lastIndex) Point(0.0, 0.0) else (points[i + 1] - points[i - 1]) / 2.0
    }.toMutableList()
    // The ends turn in the way a curve through the first (or last) three stops would.
    inner[0] = (points[1] - points[0]) * 2.0 - inner[1]
    val last = points.lastIndex
    inner[last] = (points[last] - points[last - 1]) * 2.0 - inner[last - 1]
    return inner
}

private data class Point(val x: Double, val y: Double) {
    operator fun plus(other: Point) = Point(x + other.x, y + other.y)
    operator fun minus(other: Point) = Point(x - other.x, y - other.y)
    operator fun times(factor: Double) = Point(x * factor, y * factor)
    operator fun div(factor: Double) = Point(x / factor, y / factor)

    /** Turned by [angle] radians, clockwise for a positive angle. */
    fun rotated(angle: Double) = Point(x * cos(angle) + y * sin(angle), -x * sin(angle) + y * cos(angle))
}
