package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.rememberHaptics
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.location.model.Coordinates
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The Around ring over the map, drawn by Compose on both platforms (the map SDKs' own circles differ): a faint purple disc
 * with a dashed edge, and on it a handle to drag it bigger or smaller, with the ring's size in a tag above it. The handle
 * follows the finger around the ring and stays where it was let go; dragging ticks like a ratchet and taps sharply at the ends (Shahin, 2026-11-17).
 * [project] places a point on the map view in px and must read the camera's state, so the ring follows the camera.
 * [onRadius] gets the new size in metres while the handle is dragged, and once more as final when it is let go.
 */
@Composable
internal fun AroundRingLayer(ring: AroundRing, project: (Coordinates) -> Offset?, onRadius: (meters: Int, isFinal: Boolean) -> Unit) {
    val currentRing by rememberUpdatedState(ring)
    val currentOnRadius by rememberUpdatedState(onRadius)
    val currentProject by rememberUpdatedState(project)
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val color = AlongTheWayPinColor
    // The finger on the handle, in this layer's px, while it drags: the ring and the handle follow it at once, before the
    // new size comes back from the screen's state.
    var finger by remember { mutableStateOf<Offset?>(null) }
    // Where around the ring the handle sits, in radians from east: where the finger last left it.
    var angle by remember { mutableFloatStateOf(0f) }

    // Where the ring is on screen now, its size following the finger while it drags; null while the map can't tell yet.
    fun geometry(): Pair<Offset, Float>? {
        val center = currentProject(currentRing.center) ?: return null
        val edge = currentProject(currentRing.edge()) ?: return null
        val shown = edge.x - center.x
        val dragged = finger?.let { currentRing.radiusAt(center, shown, it) } ?: return center to shown
        return center to shown * dragged.coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS) / currentRing.radiusMeters
    }
    fun handleCenter(center: Offset, radius: Float) = center + Offset(cos(angle), sin(angle)) * radius

    Canvas(Modifier.fillMaxSize()) {
        val (center, radius) = geometry() ?: return@Canvas
        drawCircle(color.copy(alpha = DISC_ALPHA), radius, center)
        val dash = DashLength.toPx()
        drawCircle(color, radius, center, style = Stroke(EdgeWidth.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, DashGap.toPx()))))
    }
    // The handle and its tag sit on the ring where the handle is, laid out in px each frame the camera or finger moves.
    Layout(
        content = {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface, shadowElevation = 2.dp) {
                Text(
                    text = ring.label.asString(),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = MaterialTheme.spacing.small, vertical = MaterialTheme.spacing.extraSmall),
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(HandleTouchSize)
                    .pointerInput(Unit) {
                        val half = density.run { HandleTouchSize.toPx() } / 2
                        // The step the finger last felt: a light tick at every 10 m (100 m from 1 km), a sharp tap at the
                        // smallest and largest size (Shahin, 2026-11-17), worked out from the finger so the ticks keep up.
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
                                val (center, radius) = geometry() ?: return@detectDragGestures
                                // The touch, in the layer: the handle's centre plus where on it the finger landed.
                                finger = handleCenter(center, radius) + at - Offset(half, half)
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
                            val toFinger = moved - center
                            if (toFinger.getDistance() > 0f) angle = atan2(toFinger.y, toFinger.x)
                            currentRing.radiusAt(center, edge.x - center.x, moved)?.let { meters ->
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
            ) {
                Box(
                    Modifier
                        .size(HandleSize)
                        .shadow(2.dp, CircleShape)
                        // White in both themes: a dark surface colour got lost on the dark map (Shahin, 2026-11-17).
                        .background(Color.White, CircleShape)
                        .border(HandleBorder, color, CircleShape),
                )
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val tag = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0))
        val handle = measurables[1].measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(constraints.maxWidth, constraints.maxHeight) {
            val (center, radius) = geometry() ?: return@layout
            val at = handleCenter(center, radius)
            val top = at.y - handle.height / 2f
            handle.place(IntOffset((at.x - handle.width / 2f).roundToInt(), top.roundToInt()))
            tag.place(x = (at.x - tag.width / 2f).roundToInt(), y = (top - tag.height).roundToInt())
        }
    }
}

/** The server's limits for the ring's size (GET /v1/places/around), which the dragged ring keeps to as well. */
/**
 * The step a size is felt in: every 10 m, every 100 m from 1 km. The smallest and largest sizes are steps of their own,
 * so reaching 50 m from 51 to 59 m still taps the end.
 */
private fun feelStep(meters: Int) = when {
    meters <= MIN_RADIUS_METERS -> -2
    meters >= MAX_RADIUS_METERS -> -3
    meters < 1000 -> meters / 10
    else -> 100 + meters / 100
}

private const val MIN_RADIUS_METERS = 50
private const val MAX_RADIUS_METERS = 5000
/** Almost clear: a slight purple, so the map under it reads as it is. */
private const val DISC_ALPHA = 0.08f
private val EdgeWidth = 2.dp
private val DashLength = 8.dp
private val DashGap = 6.dp
private val HandleSize = 22.dp
private val HandleBorder = 3.dp
/** The handle's touch area: a finger's size. */
private val HandleTouchSize = 48.dp
