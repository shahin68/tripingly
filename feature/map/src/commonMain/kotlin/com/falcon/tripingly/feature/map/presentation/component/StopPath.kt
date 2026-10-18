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

/*
 * While no route shows, the open day's stops are joined by straight lines with an arrow on each stretch
 * pointing to the next stop, so the order of the day reads at a glance. When the line appears or changes, a
 * light streak runs along it once, like a progress bar, and then nothing moves: no lasting animation, so no
 * battery cost. Drawn by the app alone: no routing call.
 */

/** Calm slate blue: a plan, quieter than the real route's bright blue. */
internal val StopPathColor = Color(0xFF3D5A80)

internal val StopPathWidth = 3.dp

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

/** Points on each stretch, so the streak glides instead of jumping from stop to stop. */
private const val STEPS_PER_LEG = 24

/** Straight lines through the [stops] in order. Stops in the same spot are joined directly and get no arrow. */
internal fun stopPathShape(stops: List<Coordinates>): StopPathShape {
    if (stops.size < 2) return StopPathShape(stops, emptyList())
    // Longitude degrees shrink away from the equator: bearings are measured in a square view.
    val xScale = cos(stops.sumOf { it.latitude } / stops.size * PI / 180)
    val path = mutableListOf(stops.first())
    val arrows = mutableListOf<PathArrow>()
    for ((start, end) in stops.zipWithNext()) {
        if (start == end) {
            path += end
            continue
        }
        val lat = end.latitude - start.latitude
        val lng = end.longitude - start.longitude
        for (step in 1 until STEPS_PER_LEG) {
            val t = step.toDouble() / STEPS_PER_LEG
            path += Coordinates(latitude = start.latitude + lat * t, longitude = start.longitude + lng * t)
        }
        path += end
        val middle = Coordinates(latitude = start.latitude + lat / 2, longitude = start.longitude + lng / 2)
        arrows += PathArrow(middle, (atan2(lng * xScale, lat) * 180 / PI).toFloat())
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

/** The arrow drawn on each stretch: a slate-blue chevron with a white edge, pointing up. */
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
