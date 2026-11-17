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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.rememberHaptics
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.location.model.Coordinates
import kotlin.math.roundToInt

/**
 * The Around ring over the map, drawn by Compose on both platforms (the map SDKs' own circles differ): a soft purple disc
 * with its edge, and on its east side a handle to drag it bigger or smaller, with the ring's size in a tag above it.
 * [project] places a point on the map view in px and must read the camera's state, so the ring follows the camera.
 * [onRadius] gets the new size in metres while the handle is dragged.
 */
@Composable
internal fun AroundRingLayer(ring: AroundRing, project: (Coordinates) -> Offset?, onRadius: (Int) -> Unit) {
    val currentRing by rememberUpdatedState(ring)
    val currentOnRadius by rememberUpdatedState(onRadius)
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val color = AlongTheWayPinColor
    // Where the ring is on screen now; null while the map can't tell yet.
    fun geometry(): Pair<Offset, Float>? {
        val center = project(currentRing.center) ?: return null
        val edge = project(currentRing.edge()) ?: return null
        return center to (edge.x - center.x)
    }
    Canvas(Modifier.fillMaxSize()) {
        val (center, radius) = geometry() ?: return@Canvas
        drawCircle(color.copy(alpha = DISC_ALPHA), radius, center)
        drawCircle(color, radius, center, style = Stroke(EdgeWidth.toPx()))
    }
    // The handle and its tag sit where the ring's east edge is, laid out in px each frame the camera moves.
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
                        detectDragGestures(onDragStart = { haptics.tick() }) { change, _ ->
                            change.consume()
                            val (center, radius) = geometry() ?: return@detectDragGestures
                            val finger = change.position + handleTopLeft(center, radius, density.run { HandleTouchSize.toPx() })
                            currentRing.radiusAt(center, radius, finger)?.let(currentOnRadius)
                        }
                    },
            ) {
                Box(
                    Modifier
                        .size(HandleSize)
                        .shadow(2.dp, CircleShape)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
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
            val handleAt = handleTopLeft(center, radius, handle.width.toFloat())
            handle.place(IntOffset(handleAt.x.roundToInt(), handleAt.y.roundToInt()))
            tag.place(
                x = (center.x + radius - tag.width / 2f).roundToInt(),
                y = (handleAt.y - tag.height).roundToInt(),
            )
        }
    }
}

/** The top left of the handle's touch area of [size] px, centered on the ring's east edge. */
private fun handleTopLeft(center: Offset, radius: Float, size: Float) = Offset(center.x + radius - size / 2, center.y - size / 2)

private const val DISC_ALPHA = 0.12f
private val EdgeWidth = 2.dp
private val HandleSize = 22.dp
private val HandleBorder = 3.dp
/** The handle's touch area: a finger's size. */
private val HandleTouchSize = 48.dp
