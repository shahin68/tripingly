package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.rememberHaptics
import com.falcon.tripingly.core.location.model.Coordinates
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.ceil
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius

/*
 * The Around ring (Shahin, 2026-11-17). The map draws it itself, so it moves with the streets in the same frame: a faint
 * purple disc with a dashed edge (a circle), and the handle with the ring's size in a tag above it (a marker) where the
 * finger left it (Android: maps-compose's Circle and MarkerComposable; iOS: GMSCircle, a dashed polyline and a marker
 * from the Swift side). Drawn by Compose over the map instead, it trailed the map by a frame while panning, as the camera
 * reaches Compose only after the map has drawn. Compose keeps only the handle's touch area here, which a pan can't show.
 */

/**
 * The handle's touch area over the map, a finger's size where the handle is: dragging it resizes the ring, the handle
 * following the finger around it ([onAngle], radians from the ring's east, south positive). Dragging ticks like a ratchet and hits
 * the ends hard (Shahin, 2026-11-17). [project] places a point on the map view in px and must read the camera's state.
 * [onRadius] gets the new size in metres while dragged, and once more as final when let go.
 */
@Composable
internal fun AroundHandleLayer(
    ring: AroundRing,
    angle: Float,
    onAngle: (Float) -> Unit,
    project: (Coordinates) -> Offset?,
    onRadius: (meters: Int, isFinal: Boolean) -> Unit,
) {
    val currentRing by rememberUpdatedState(ring)
    val currentAngle by rememberUpdatedState(angle)
    val currentOnAngle by rememberUpdatedState(onAngle)
    val currentOnRadius by rememberUpdatedState(onRadius)
    val currentProject by rememberUpdatedState(project)
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    Layout(
        content = {
            Box(
                Modifier.size(HandleTouchSize).pointerInput(Unit) {
                    val half = density.run { HandleTouchSize.toPx() } / 2
                    // The finger, in the layer's px.
                    var finger: Offset? = null
                    // The step the finger last felt: a light tick at every 10 m (100 m from 1 km), a hard hit at the
                    // smallest and largest size, worked out from the finger so the ticks keep up.
                    var feltStep = -1
                    // The size the finger left the ring at: sent as final on letting go, the only time places load.
                    var dragged: Int? = null
                    fun letGo() {
                        finger = null
                        dragged?.let { currentOnRadius(it, true) }
                        dragged = null
                    }
                    detectDragGestures(
                        onDragStart = { at ->
                            val handle = currentProject(currentRing.handle(currentAngle)) ?: return@detectDragGestures
                            finger = handle + at - Offset(half, half)
                            feltStep = feelStep(currentRing.radiusMeters)
                            haptics.tick()
                        },
                        onDragEnd = { letGo() },
                        onDragCancel = { letGo() },
                    ) { change, drag ->
                        change.consume()
                        val moved = (finger ?: return@detectDragGestures) + drag
                        finger = moved
                        val center = currentProject(currentRing.center) ?: return@detectDragGestures
                        val edge = currentProject(currentRing.edge()) ?: return@detectDragGestures
                        // The ring's east on screen: the map may be turned, so the finger's angle is taken from it.
                        val east = edge - center
                        val toFinger = moved - center
                        if (toFinger.getDistance() > 0f) currentOnAngle(atan2(toFinger.y, toFinger.x) - atan2(east.y, east.x))
                        currentRing.radiusAt(center, east.getDistance(), moved)?.let { meters ->
                            val clamped = meters.coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS)
                            val step = feelStep(clamped)
                            if (step != feltStep) {
                                if (clamped == MIN_RADIUS_METERS || clamped == MAX_RADIUS_METERS) haptics.end() else haptics.fineTick()
                                feltStep = step
                            }
                            dragged = meters
                            currentOnRadius(meters, false)
                        }
                    }
                },
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val touch = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(constraints.maxWidth, constraints.maxHeight) {
            // Laid out where the handle is now, each time the camera or the ring moves.
            val at = currentProject(currentRing.handle(currentAngle)) ?: return@layout
            touch.place(IntOffset((at.x - touch.width / 2f).roundToInt(), (at.y - touch.height / 2f).roundToInt()))
        }
    }
}

/**
 * The handle as the map's marker draws it: the ring's size in a tag over a white circle in the purple edge. The marker
 * touches the map at the circle's centre ([AroundRingLook.HandleAnchorY]). Android draws it from here, iOS the same
 * sizes in Skia (`drawAroundHandle`).
 */
@Composable
internal fun AroundHandleMarker(label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AroundRingLook.TagGap),
        modifier = Modifier.height(AroundRingLook.MarkerHeight),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .height(AroundRingLook.TagHeight)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
                .border(AroundRingLook.TagOutline, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .padding(horizontal = AroundRingLook.TagPadding),
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.wrapContentHeight())
        }
        Box(
            Modifier
                .size(AroundRingLook.HandleSize)
                // White in both themes: a dark surface colour got lost on the dark map (Shahin, 2026-11-17).
                .background(Color.White, CircleShape)
                .border(AroundRingLook.HandleBorder, AlongTheWayPinColor, CircleShape),
        )
    }
}

/**
 * [AroundHandleMarker] drawn as an image, for iOS's marker: the tag with [label] in [textStyle] on [surface] with an
 * [outline], [AroundRingLook.TagGap] over the white handle in the purple edge, horizontally centred.
 */
internal fun drawAroundHandle(
    label: String,
    textStyle: TextStyle,
    surface: Color,
    outline: Color,
    textMeasurer: TextMeasurer,
    density: Density,
    layoutDirection: LayoutDirection,
): ImageBitmap = with(density) {
    val text = textMeasurer.measure(label, textStyle, density = density, layoutDirection = layoutDirection)
    val tagHeight = AroundRingLook.TagHeight.toPx()
    val tagWidth = text.size.width + 2 * AroundRingLook.TagPadding.toPx()
    val handle = AroundRingLook.HandleSize.toPx()
    val width = ceil(maxOf(tagWidth, handle)).toInt()
    val height = ceil(AroundRingLook.MarkerHeight.toPx()).toInt()
    val bitmap = ImageBitmap(width, height)
    CanvasDrawScope().draw(density, layoutDirection, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
        val tagLeft = (size.width - tagWidth) / 2
        val corner = CornerRadius(tagHeight / 2)
        drawRoundRect(surface, Offset(tagLeft, 0f), Size(tagWidth, tagHeight), corner)
        val line = AroundRingLook.TagOutline.toPx()
        drawRoundRect(outline, Offset(tagLeft + line / 2, line / 2), Size(tagWidth - line, tagHeight - line), CornerRadius(tagHeight / 2 - line / 2), style = Stroke(line))
        drawText(text, topLeft = Offset((size.width - text.size.width) / 2, (tagHeight - text.size.height) / 2))
        val center = Offset(size.width / 2, size.height - handle / 2)
        val border = AroundRingLook.HandleBorder.toPx()
        drawCircle(AlongTheWayPinColor, handle / 2, center)
        drawCircle(Color.White, handle / 2 - border, center)
    }
    bitmap
}

/** The ring's look, the same on both platforms. */
internal object AroundRingLook {
    /** Almost clear: a slight purple, so the map under it reads as it is. */
    const val DISC_ALPHA = 0.08f
    val EdgeWidth = 2.dp
    val DashLength = 8.dp
    val DashGap = 6.dp
    val HandleSize = 22.dp
    val HandleBorder = 3.dp
    val TagHeight = 24.dp
    val TagOutline = 0.5.dp
    val TagPadding = 8.dp
    val TagGap = 4.dp
    val MarkerHeight = TagHeight + TagGap + HandleSize
    /** The handle's centre, as a share of the marker's height from its top. */
    val HandleAnchorY = (TagHeight + TagGap + HandleSize / 2) / MarkerHeight
}

/**
 * The step a size is felt in: every 10 m, every 100 m from 1 km. The smallest and largest sizes are steps of their own,
 * so reaching 50 m from 51 to 59 m still hits the end.
 */
private fun feelStep(meters: Int) = when {
    meters <= MIN_RADIUS_METERS -> -2
    meters >= MAX_RADIUS_METERS -> -3
    meters < 1000 -> meters / 10
    else -> 100 + meters / 100
}

/** The server's limits for the ring's size (GET /v1/places/around), which the dragged ring keeps to as well. */
private const val MIN_RADIUS_METERS = 50
private const val MAX_RADIUS_METERS = 5000
/** The handle's touch area: a finger's size. */
private val HandleTouchSize = 48.dp
