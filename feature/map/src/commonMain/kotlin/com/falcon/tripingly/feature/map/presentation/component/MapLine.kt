package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.theme.MapColors
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.model.route.TravelMode
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt

/*
 * Every line on the map is drawn the same way: rounded ends, an arrow halfway along each stretch between two
 * stops pointing the way, and a light streak that runs along it once when it appears or changes, like a
 * progress bar. Then nothing moves: no lasting animation, so no battery cost. Lines differ only by [MapLineStyle].
 */

/** How a line looks. */
data class MapLineStyle(val color: Color, val width: Dp)

/** A line to draw: its [points] from start to end and the [arrows] pointing the way. A new value runs the streak again. */
@Immutable
data class MapLine(val points: List<Coordinates>, val arrows: List<PathArrow>, val style: MapLineStyle)

/** [bearing] in degrees clockwise from north, the way the line runs here. */
data class PathArrow(val position: Coordinates, val bearing: Float)

/** A real route along streets: one soft, pastel color per way of travelling. */
internal val TravelMode.lineColor: Color
    get() = when (this) {
        TravelMode.Walking -> MapColors.Walk
        TravelMode.Cycling -> MapColors.Bike
        TravelMode.Driving -> MapColors.Car
    }

internal val RouteWidth = 5.dp

/** The streak that runs along a line once. */
internal val MapLineSweepColor = Color.White.copy(alpha = 0.85f)

internal const val MAP_LINE_SWEEP_MILLIS = 1200

/** The streak's length, as a share of the whole line. */
private const val SWEEP_LENGTH = 0.25f

/** Points a real route is cut into at most, so its streak glides at an even speed on long straight roads too. */
private const val ROUTE_SWEEP_STEPS = 200

private val ArrowSize = 16.dp
private val ArrowStroke = 2.dp

/**
 * A real route through [stops] (the day's stops in order, or A and B). It starts and ends exactly at the first
 * and last stop, not where the street nearest them is, and has an arrow halfway between each two stops.
 */
internal fun routeLine(points: List<Coordinates>, stops: List<Coordinates>, mode: TravelMode): MapLine {
    val style = MapLineStyle(mode.lineColor, RouteWidth)
    if (points.size < 2) return MapLine(points, emptyList(), style)
    val ends = buildList {
        stops.firstOrNull()?.takeIf { it != points.first() }?.let(::add)
        addAll(points)
        stops.lastOrNull()?.takeIf { it != points.last() }?.let(::add)
    }
    val line = evenlySpaced(ends)
    val xScale = xScale(line)
    // Each stop's nearest point along the line, in order: the route passes them one after the other.
    val stopIndices = buildList {
        var from = 0
        for (stop in stops) {
            val index = (from..line.lastIndex).minByOrNull { distance(line[it], stop, xScale) } ?: break
            add(index)
            from = index
        }
    }
    val arrows = stopIndices.zipWithNext().mapNotNull { (start, end) -> arrowBetween(line, start, end, xScale) }
    return MapLine(line, arrows, style)
}

/** The point halfway along [line] from [start] to [end], pointing the way the line runs there. */
private fun arrowBetween(line: List<Coordinates>, start: Int, end: Int, xScale: Double): PathArrow? {
    if (end <= start) return null
    val lengths = (start until end).map { distance(line[it], line[it + 1], xScale) }
    var left = lengths.sum() / 2
    if (left == 0.0) return null
    for ((offset, length) in lengths.withIndex()) {
        if (left <= length) {
            val a = line[start + offset]
            val b = line[start + offset + 1]
            val t = left / length
            val position = Coordinates(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t)
            return PathArrow(position, bearing(a, b, xScale))
        }
        left -= length
    }
    return null
}

/** [points] with points added on long stretches, so no stretch is longer than a [ROUTE_SWEEP_STEPS]th of the whole. */
private fun evenlySpaced(points: List<Coordinates>): List<Coordinates> {
    val xScale = xScale(points)
    val step = points.zipWithNext { a, b -> distance(a, b, xScale) }.sum() / ROUTE_SWEEP_STEPS
    if (step == 0.0) return points
    return buildList {
        add(points.first())
        for ((a, b) in points.zipWithNext()) {
            val parts = ceil(distance(a, b, xScale) / step).toInt()
            for (part in 1 until parts) {
                val t = part.toDouble() / parts
                add(Coordinates(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t))
            }
            add(b)
        }
    }
}

/** Longitude degrees shrink away from the equator: lengths and bearings are measured in a square view. */
internal fun xScale(points: List<Coordinates>): Double = cos(points.sumOf { it.latitude } / points.size * PI / 180)

private fun distance(a: Coordinates, b: Coordinates, xScale: Double): Double =
    hypot(b.latitude - a.latitude, (b.longitude - a.longitude) * xScale)

internal fun bearing(from: Coordinates, to: Coordinates, xScale: Double): Float =
    (atan2((to.longitude - from.longitude) * xScale, to.latitude - from.latitude) * 180 / PI).toFloat()

/**
 * The part of [points] the streak covers at [progress] (0 to 1): it enters at the start and leaves at the end,
 * like an indeterminate progress bar. Fewer than two points while it's off the line.
 */
internal fun <T> sweepOf(points: List<T>, progress: Float): List<T> {
    // No line: the last stop was just removed.
    if (points.size < 2) return emptyList()
    val last = points.lastIndex
    val head = (progress * (1 + SWEEP_LENGTH) * last).roundToInt()
    val tail = head - (SWEEP_LENGTH * last).roundToInt()
    return points.subList(tail.coerceIn(0, last), (head + 1).coerceIn(0, last + 1))
}

/** The arrow drawn on each stretch: a chevron in the line's [color] with a white edge, pointing up (north). */
internal fun drawPathArrow(density: Density, layoutDirection: LayoutDirection, color: Color): ImageBitmap {
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
        drawPath(chevron, color)
    }
    return bitmap
}

/** iOS lines have square ends: a dot as wide as the line, drawn on each end, rounds them. */
internal fun drawLineCap(density: Density, layoutDirection: LayoutDirection, style: MapLineStyle): ImageBitmap {
    val px = ceil(with(density) { style.width.toPx() }).toInt().coerceAtLeast(1)
    val bitmap = ImageBitmap(px, px)
    CanvasDrawScope().draw(density, layoutDirection, Canvas(bitmap), Size(px.toFloat(), px.toFloat())) {
        drawCircle(style.color)
    }
    return bitmap
}
