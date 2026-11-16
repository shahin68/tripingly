package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.falcon.tripingly.core.designsystem.theme.MapColors
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.ui.UiText
import kotlin.math.ceil

/**
 * One end of an A→B route: a dot in the way of travelling's [color] on the place, or beside the end's stop pin
 * ([stopPinSize] round, the stop keeps its own pin), and a pill in that colour with the end's icon (a ring for the start,
 * the route bar's flag for the destination) and [name], on the side away from the other end ([isPillLeft]) so it doesn't
 * lie on the line. Drawn above every place pin; a tap centres the map on it (Shahin, 2026-11-16, E11).
 */
@Immutable
data class RouteEndMark(
    val position: GeoPoint,
    val name: UiText,
    val isStart: Boolean,
    val color: Color,
    val stopPinSize: Dp? = null,
    val isPillLeft: Boolean = false,
)

/** A route end's look and where on it the end's point is ([anchorX], [anchorY] as shares of its size). */
class RouteEndIcon(val bitmap: ImageBitmap, val anchorX: Float, val anchorY: Float)

/**
 * A tap at [touch] lands on one of [ends]' marks, drawn as [icons] holds them (by look and name) where [project] puts
 * their points (px): the end takes it, not a place pin or dot under its pill.
 */
internal fun isOnRouteEnd(touch: Offset, ends: List<RouteEndMark>, icons: Map<Pair<RouteEndMark, String>, RouteEndIcon>, project: (GeoPoint) -> Offset?): Boolean =
    ends.any { end ->
        val look = end.copy(position = NoEndPosition)
        val icon = icons.entries.firstOrNull { it.key.first == look }?.value ?: return@any false
        val at = project(end.position) ?: return@any false
        val left = at.x - icon.anchorX * icon.bitmap.width
        val top = at.y - icon.anchorY * icon.bitmap.height
        touch.x in left..left + icon.bitmap.width && touch.y in top..top + icon.bitmap.height
    }

/** Where a route end's look is cached: the look without its place. */
internal val NoEndPosition = GeoPoint(0.0, 0.0)

/** Draws [end] with its [name] (an empty name leaves only the icon in the pill). */
fun drawRouteEnd(end: RouteEndMark, name: String, textMeasurer: TextMeasurer, density: Density, layoutDirection: LayoutDirection): RouteEndIcon {
    val text = textMeasurer.measure(
        name,
        TextStyle(color = MapColors.OnRoute, fontSize = RouteEndTextSize, fontWeight = FontWeight.ExtraBold),
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        constraints = Constraints(maxWidth = with(density) { RouteEndNameMaxWidth.roundToPx() }),
        density = density,
        layoutDirection = layoutDirection,
    )
    val ring = with(density) { PIN_STROKE.toPx() }
    val pillHeight = with(density) { RouteEndPillHeight.toPx() }
    val padding = with(density) { RouteEndPillPadding.toPx() }
    val icon = with(density) { RouteEndIconSize.toPx() }
    val iconGap = with(density) { RouteEndIconGap.toPx() }
    val pillWidth = padding * 2 + icon + if (name.isEmpty()) 0f else iconGap + text.size.width
    val mark = with(density) { (end.stopPinSize ?: RouteEndDotSize).toPx() }
    val markGap = with(density) { RouteEndPillGap.toPx() }
    val width = ceil(mark + markGap + pillWidth).toInt()
    val height = ceil(maxOf(mark, pillHeight)).toInt()
    val markX = if (end.isPillLeft) width - mark / 2 else mark / 2
    val pillLeft = if (end.isPillLeft) 0f else mark + markGap
    val middle = height / 2f
    val bitmap = ImageBitmap(width, height)
    CanvasDrawScope().draw(density, layoutDirection, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
        // A stop's own pin shows there; a place's pin gives way to the dot.
        if (end.stopPinSize == null) {
            drawCircle(MapColors.PinStroke, mark / 2, Offset(markX, middle))
            drawCircle(end.color, mark / 2 - RouteEndDotRing.toPx(), Offset(markX, middle))
        }
        val top = middle - pillHeight / 2
        drawRoundRect(MapColors.PinStroke, Offset(pillLeft, top), Size(pillWidth, pillHeight), CornerRadius(pillHeight / 2))
        drawRoundRect(end.color, Offset(pillLeft + ring, top + ring), Size(pillWidth - 2 * ring, pillHeight - 2 * ring), CornerRadius(pillHeight / 2 - ring))
        val iconLeft = pillLeft + padding
        if (end.isStart) {
            val stroke = RouteEndStartRing.toPx()
            drawCircle(MapColors.OnRoute, icon / 2 - stroke / 2, Offset(iconLeft + icon / 2, middle), style = Stroke(stroke))
        } else {
            // The Material flag, 24 units in its box.
            val unit = icon / 24
            val flag = PathParser().parsePathString(ROUTE_END_FLAG).toPath()
            translate(left = iconLeft, top = middle - icon / 2) {
                scale(unit, unit, pivot = Offset.Zero) { drawPath(flag, MapColors.OnRoute) }
            }
        }
        if (name.isNotEmpty()) drawText(text, topLeft = Offset(iconLeft + icon + iconGap, middle - text.size.height / 2f))
    }
    return RouteEndIcon(bitmap, anchorX = markX / width, anchorY = middle / height)
}

/**
 * Material's filled flag (Icons.Filled.Flag), the route bar's destination icon, in its 24-unit box. Kept as path data,
 * not a `Path`: a top-level platform path would be made as the file loads, which the JVM unit tests can't.
 */
private const val ROUTE_END_FLAG = "M14.4,6L14,4H5v17h2v-7h5.6l0.4,2h7V6z"

/** A place end's dot, in a white ring, and the pill beside it. */
private val RouteEndDotSize = 16.dp
private val RouteEndDotRing = 3.dp
private val RouteEndPillGap = 4.dp
private val RouteEndPillHeight = 26.dp
private val RouteEndPillPadding = 10.dp
private val RouteEndIconSize = 12.dp
private val RouteEndIconGap = 5.dp
private val RouteEndStartRing = 2.dp
private val RouteEndTextSize = 12.sp

/** A long name ends in "…" past this. */
private val RouteEndNameMaxWidth = 160.dp
