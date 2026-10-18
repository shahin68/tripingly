package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.location.model.Coordinates
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * While no route shows, the open day's stops are joined by one smooth line with an arrow on each stretch
 * pointing to the next stop, so the order of the day reads at a glance. When the line appears or changes, a
 * light streak runs along it once, like a progress bar, and then nothing moves: no lasting animation, so no
 * battery cost. Drawn by the app alone: no routing call.
 */

/** Playful coral, apart from the stops' and the route's blue. */
internal val StopPathColor = Color(0xFFFF7A59)

internal val StopPathWidth = 4.dp

/** The streak that runs along the line once. */
internal val StopPathSweepColor = Color.White.copy(alpha = 0.85f)

internal const val STOP_PATH_SWEEP_MILLIS = 1200

/** The streak's length, as a share of the whole line. */
private const val SWEEP_LENGTH = 0.25f

/** The arrow on each stretch, pointing up (north) before it's turned. */
private val ArrowSize = 16.dp
private val ArrowStroke = 2.dp

/** The line through the stops and an arrow halfway along each stretch, pointing the way. */
internal class StopPathShape(val points: List<Coordinates>, val arrows: List<PathArrow>)

/** [bearing] in degrees clockwise from north, the way the line runs here. */
internal data class PathArrow(val position: Coordinates, val bearing: Float)

/** How far the line between just two stops bows out, as an angle off the straight line (radians). */
private const val TWO_STOP_BEND = 0.4

private const val STEPS_PER_LEG = 24

/**
 * One smooth line through the [stops] in order, with no corner at any stop, like a line drawn by hand: at
 * each stop it heads from the stop before towards the one after, and at the first and last stop it swings
 * in the way the next curve would. Two stops get a gentle arc; stops in the same spot are joined directly
 * and get no arrow.
 */
internal fun stopPathShape(stops: List<Coordinates>): StopPathShape {
    if (stops.size < 2) return StopPathShape(stops, emptyList())
    // Longitude degrees shrink away from the equator: curve in a square view so curves look the same everywhere.
    val xScale = cos(stops.sumOf { it.latitude } / stops.size * PI / 180)
    val points = stops.map { Point(it.longitude * xScale, it.latitude) }
    val tangents = tangents(points)
    val path = mutableListOf(stops.first())
    val arrows = mutableListOf<PathArrow>()
    fun Point.toCoordinates() = Coordinates(latitude = y, longitude = x / xScale)
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
            path += (start * (u * u * u) + c1 * (3 * u * u * t) + c2 * (3 * u * t * t) + end * (t * t * t)).toCoordinates()
        }
        path += stops[i + 1]
        // Halfway, heading the way the curve does there.
        val middle = (start + c1 * 3.0 + c2 * 3.0 + end) / 8.0
        val heading = end + c2 - c1 - start
        arrows += PathArrow(middle.toCoordinates(), (atan2(heading.x, heading.y) * 180 / PI).toFloat())
    }
    return StopPathShape(path, arrows)
}

/**
 * The part of [points] the streak covers at [progress] (0 to 1): it enters at the start and leaves at the end,
 * like an indeterminate progress bar. Fewer than two points while it's off the line.
 */
internal fun <T> sweepOf(points: List<T>, progress: Float): List<T> {
    val last = points.lastIndex
    val head = (progress * (1 + SWEEP_LENGTH) * last).roundToInt()
    val tail = head - (SWEEP_LENGTH * last).roundToInt()
    return points.subList(tail.coerceIn(0, last), (head + 1).coerceIn(0, last + 1))
}

/** The arrow drawn on each stretch: a coral chevron with a white edge, pointing up. */
internal fun drawPathArrow(density: Density, layoutDirection: LayoutDirection): ImageBitmap {
    val px = ceil(with(density) { ArrowSize.toPx() }).toInt()
    val stroke = with(density) { ArrowStroke.toPx() }
    val bitmap = ImageBitmap(px, px)
    CanvasDrawScope().draw(density, layoutDirection, Canvas(bitmap), Size(px.toFloat(), px.toFloat())) {
        val chevron = Path().apply {
            moveTo(size.width / 2, stroke)
            lineTo(size.width - stroke, size.height - stroke)
            lineTo(size.width / 2, size.height * 0.7f)
            lineTo(stroke, size.height - stroke)
            close()
        }
        drawPath(chevron, Color.White, style = Stroke(width = stroke * 2, join = StrokeJoin.Round))
        drawPath(chevron, StopPathColor)
    }
    return bitmap
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
