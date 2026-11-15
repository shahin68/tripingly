package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.theme.MapColors
import com.falcon.tripingly.core.designsystem.theme.extendedColors
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.model.route.TransitStep
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

/**
 * A line to draw: its [points] from start to end and the [arrows] pointing the way. A new value runs the streak again.
 * With [parts] (public transport) they are drawn instead of one line in [style]'s colour; [points] still carry the streak.
 */
@Immutable
data class MapLine(
    val points: List<Coordinates>,
    val arrows: List<PathArrow>,
    val style: MapLineStyle,
    val parts: List<MapLinePart> = emptyList(),
    /** Public transport: the rides' line badges and the rings where they start, change and end. */
    val marks: List<LineMark> = emptyList(),
)

/** A public transport route's ride in its line's colour, solid, or a walk, dotted (Shahin, 2026-11-15: no edge round them). */
data class MapLinePart(val points: List<Coordinates>, val color: Color, val isDotted: Boolean)

/** This line in the theme's colours: the plan, which has no colour of its own, takes the stops' colour. */
@Composable
internal fun MapLine.themed(): MapLine =
    if (style.color.isSpecified) this else copy(style = style.copy(color = MaterialTheme.extendedColors.stop))

/**
 * A mark on a public transport line (the canvas's lines board, Shahin 2026-11-15): in place of the arrows, which show the
 * way on the other routes, each ride has its line's rounded badge halfway along, and a ring where it starts and ends.
 */
@Immutable
sealed interface LineMark {
    val position: Coordinates

    /** A white circle with a [color] ring: a ride's first or last stop in its line's colour, a change in ink. */
    data class Ring(override val position: Coordinates, val color: Color) : LineMark

    /** The line's [name] ("U1", "6") on its [color], in a rounded rectangle. */
    data class Badge(override val position: Coordinates, val name: String, val color: Color, val textColor: Color) : LineMark
}

/** [bearing] in degrees clockwise from north, the way the line runs here. */
data class PathArrow(val position: Coordinates, val bearing: Float)

/** A real route along streets: one jewel colour per way of travelling. */
internal val TravelMode.lineColor: Color
    get() = when (this) {
        TravelMode.Walking -> MapColors.Walk
        TravelMode.Cycling -> MapColors.Bike
        TravelMode.Driving -> MapColors.Car
        TravelMode.Transit -> MapColors.Transit
    }

/** A route's width: a little thicker than the plan line (Shahin, 2026-11-15: "all the routing lines a bit thicker"). */
internal val RouteWidth = 7.dp

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

/**
 * A public transport route through [stops]: each ride in its line's own colour, each walk dotted in the walking colour.
 * No arrows: the lines' colours and the steps sheet show the way, and an arrow in one colour would sit on every other.
 */
internal fun transitLine(points: List<Coordinates>, steps: List<TransitStep>, stops: List<Coordinates>): MapLine {
    val parts = steps.filter { it.points.size > 1 }.map { step ->
        MapLinePart(
            points = step.points.map { Coordinates(it.lat, it.lng) },
            color = if (step.isRide) step.line?.color?.let { Color(it) } ?: MapColors.Transit else MapColors.Walk,
            isDotted = !step.isRide,
        )
    }
    // Steps without their own lines: the whole route in one colour.
    if (parts.isEmpty()) return routeLine(points, stops, TravelMode.Transit)
    return routeLine(parts.flatMap { it.points }, stops, TravelMode.Transit).copy(arrows = emptyList(), parts = parts, marks = transitMarks(steps))
}

/**
 * Each ride's badge halfway along it and a ring at each of its ends: in its line's colour where the route gets on first
 * or off last, in ink where it changes to or from another ride.
 */
private fun transitMarks(steps: List<TransitStep>): List<LineMark> {
    val rides = steps.filter { it.isRide && it.points.size > 1 }
    return rides.flatMapIndexed { index, ride ->
        val points = ride.points.map { Coordinates(it.lat, it.lng) }
        val color = ride.line?.color?.let { Color(it) } ?: MapColors.Transit
        listOfNotNull(
            LineMark.Ring(points.first(), if (index > 0) ChangeInk else color),
            LineMark.Ring(points.last(), if (index < rides.lastIndex) ChangeInk else color),
            ride.line?.let { line -> LineMark.Badge(halfway(points), line.name, color, line.textColor?.let { Color(it) } ?: Color.White) },
        )
    }
}

/** The point halfway along [points] by length. */
private fun halfway(points: List<Coordinates>): Coordinates {
    val xScale = xScale(points)
    var left = points.zipWithNext { a, b -> distance(a, b, xScale) }.sum() / 2
    for ((a, b) in points.zipWithNext()) {
        val length = distance(a, b, xScale)
        if (left <= length && length > 0) {
            val t = left / length
            return Coordinates(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t)
        }
        left -= length
    }
    return points[points.size / 2]
}

/** A change's rings: the canvas's ink, dark on the light map and clear on the dark one with its white middle. */
private val ChangeInk = Color(0xFF15171C)

/** A ring on a public transport line: white inside a [color] ring, a thin white edge round it so it shows on any map. */
internal fun drawLineRing(color: Color, density: Density, layoutDirection: LayoutDirection): ImageBitmap {
    val px = ceil(with(density) { RingSize.toPx() }).toInt()
    val ring = with(density) { RingStroke.toPx() }
    val bitmap = ImageBitmap(px, px)
    CanvasDrawScope().draw(density, layoutDirection, Canvas(bitmap), Size(px.toFloat(), px.toFloat())) {
        drawCircle(color, radius = size.minDimension / 2)
        drawCircle(Color.White, radius = size.minDimension / 2 - ring)
    }
    return bitmap
}

/** A line's badge on the map: its [name] in bold on its [color], a rounded rectangle with a thin white edge. */
internal fun drawLineBadge(
    name: String,
    color: Color,
    textColor: Color,
    textMeasurer: TextMeasurer,
    density: Density,
    layoutDirection: LayoutDirection,
): ImageBitmap {
    val text = textMeasurer.measure(name, TextStyle(color = textColor, fontSize = BadgeText, fontWeight = FontWeight.ExtraBold), density = density, layoutDirection = layoutDirection)
    val edge = with(density) { BadgeEdge.toPx() }
    val width = ceil(text.size.width + with(density) { (BadgePaddingX * 2).toPx() } + edge * 2).toInt()
    val height = ceil(text.size.height + with(density) { (BadgePaddingY * 2).toPx() } + edge * 2).toInt()
    val corner = with(density) { BadgeCorner.toPx() }
    val bitmap = ImageBitmap(width, height)
    CanvasDrawScope().draw(density, layoutDirection, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
        drawRoundRect(Color.White, cornerRadius = CornerRadius(corner + edge))
        drawRoundRect(color, topLeft = Offset(edge, edge), size = Size(size.width - edge * 2, size.height - edge * 2), cornerRadius = CornerRadius(corner))
        drawText(text, topLeft = Offset((size.width - text.size.width) / 2, (size.height - text.size.height) / 2))
    }
    return bitmap
}

private val RingSize = 16.dp
private val RingStroke = 4.dp
private val BadgeText = 13.sp
private val BadgePaddingX = 7.dp
private val BadgePaddingY = 2.dp
private val BadgeCorner = 6.dp
private val BadgeEdge = 1.5.dp

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
