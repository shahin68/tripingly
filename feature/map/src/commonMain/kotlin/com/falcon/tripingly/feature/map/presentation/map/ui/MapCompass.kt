package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.falcon.tripingly.core.designsystem.component.GlassIconButton
import com.falcon.tripingly.core.designsystem.component.rememberHaptics
import com.falcon.tripingly.core.designsystem.theme.MapColors
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_compass_north
import com.falcon.tripingly.feature.map.generated.resources.map_north_up
import kotlin.math.abs
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * Our compass (Shahin, 2026-11-14, look B of the compass canvas): a glass button over the map buttons with a needle,
 * red north tip and a south tip in the map buttons' icon colour, and an N just above the tip, all turning so they point north while the map turns.
 * It shows once the map is turned and leaves [HIDE_DELAY_MILLIS] after it is north up again; its place glides open and
 * shut with it, so the buttons under it never jump. A tap turns the map north up ([onNorthUp]) with a tick.
 *
 * [bearing] is read only while drawing, so a turning map redraws the needle without recomposing anything.
 */
@Composable
internal fun MapCompass(bearing: () -> Float, onNorthUp: () -> Unit, modifier: Modifier = Modifier) {
    val isTurned by remember { derivedStateOf { abs(bearing().normalizedBearing()) > TURNED_DEGREES } }
    var isShown by remember { mutableStateOf(isTurned) }
    LaunchedEffect(isTurned) {
        if (!isTurned) delay(HIDE_DELAY_MILLIS)
        isShown = isTurned
    }
    AnimatedVisibility(
        visible = isShown,
        modifier = modifier,
        // The space opens upwards from the buttons and the button grows out of its bottom edge with it, so it never
        // pokes past its space; no clipping, so the glass shadow isn't cut on the way.
        enter = expandVertically(tween(MOVE_MILLIS, easing = Emphasized), expandFrom = Alignment.Bottom, clip = false) +
            scaleIn(tween(MOVE_MILLIS, easing = Emphasized), initialScale = 0.6f, transformOrigin = GrowFromBottom) + fadeIn(tween(FADE_MILLIS)),
        exit = shrinkVertically(tween(MOVE_MILLIS, easing = Emphasized), shrinkTowards = Alignment.Bottom, clip = false) +
            scaleOut(tween(MOVE_MILLIS, easing = Emphasized), targetScale = 0.6f, transformOrigin = GrowFromBottom) + fadeOut(tween(FADE_MILLIS)),
    ) {
        val haptics = rememberHaptics()
        val description = stringResource(Res.string.map_north_up)
        // The gap under it comes and goes with it.
        Box(Modifier.padding(bottom = MaterialTheme.spacing.small)) {
            GlassIconButton(
                onClick = {
                    haptics.tick()
                    onNorthUp()
                },
                blur = false,
                modifier = Modifier.semantics { contentDescription = description },
            ) {
                CompassNeedle(Modifier.size(NeedleBoxSize).graphicsLayer { rotationZ = -bearing() })
            }
        }
    }
}

@Composable
private fun CompassNeedle(modifier: Modifier = Modifier) {
    val north = MapColors.CompassNorth
    // The grey tip in the map buttons' own icon colour (the fit button's), from the glass it sits on.
    val south = LocalContentColor.current
    Box(modifier) {
        Text(
            stringResource(Res.string.map_compass_north),
            modifier = Modifier.align(Alignment.TopCenter),
            color = north,
            fontSize = 10.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2
            // The needle sits low enough to leave a gap under the N.
            val cy = size.height / 2 + 6.dp.toPx()
            val half = 3.5.dp.toPx()
            val tip = 9.dp.toPx()
            drawPath(Path().apply { moveTo(cx, cy - tip); lineTo(cx + half, cy); lineTo(cx - half, cy); close() }, north)
            drawPath(Path().apply { moveTo(cx, cy + tip); lineTo(cx - half, cy); lineTo(cx + half, cy); close() }, south)
        }
    }
}

/** [this] in -180..180 degrees, so 359° counts as barely turned. */
internal fun Float.normalizedBearing(): Float = ((this + 180f) % 360f + 360f) % 360f - 180f

/** Less than this is north up (a pinch can nudge the bearing a hair). */
private const val TURNED_DEGREES = 1f

/** How long the compass stays after the map is north up again. */
private const val HIDE_DELAY_MILLIS = 600L
private const val MOVE_MILLIS = 300
private const val FADE_MILLIS = 200
private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val GrowFromBottom = TransformOrigin(0.5f, 1f)
private val NeedleBoxSize = 34.dp

@Preview
@Composable
private fun MapCompassPreview() {
    TripinglyTheme { MapCompass(bearing = { 35f }, onNorthUp = {}) }
}

@Preview
@Composable
private fun MapCompassDarkPreview() {
    TripinglyTheme(darkTheme = true) { MapCompass(bearing = { -120f }, onNorthUp = {}) }
}
