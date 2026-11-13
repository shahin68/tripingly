package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.falcon.tripingly.core.designsystem.component.GlassButtonDefaults
import com.falcon.tripingly.core.designsystem.component.GlassDefaults
import com.falcon.tripingly.core.designsystem.theme.extendedColors
import kotlin.math.roundToInt

/*
 * A glass panel that grows out of the header's title pill in the same window, no Dialog and no shared bounds (both
 * broke on iOS): the trip card and the route bar. One transition drives everything: the panel's shape (from the
 * pill's rectangle to the whole panel, corners kept round), the header's buttons pushed out (MapHeader's
 * cardProgress), and the rows sliding down into place. The panel is laid out once at its full size, so every frame
 * only draws again, the same on Android and iOS. Design: the "Final" board of the trip card canvas (Shahin,
 * 2026-11-08), used for the route bar too (Shahin, 2026-11-13).
 */

/** The panel's growth: 0 is the title pill, 1 the open panel. It slows down towards the end both ways. */
@Composable
internal fun Transition<Boolean>.animatePillGrowth(): State<Float> = animateFloat(
    transitionSpec = { tween(if (false isTransitioningTo true) OPEN_MILLIS else CLOSE_MILLIS, easing = Decelerate) },
    label = "pill growth",
) { open -> if (open) 1f else 0f }

/**
 * The glass panel grown out of the title pill at [pill] (its bounds in the same coordinates as this panel) by
 * [progress] of [transition], wrapping [content]. The pill's own [pillContent] fades as the panel grows over it and
 * back as it shrinks into the pill. Taps on the panel stay on it.
 */
@Composable
internal fun PillGrowthPanel(
    transition: Transition<Boolean>,
    progress: () -> Float,
    pill: Rect,
    pillContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val tint = GlassDefaults.opaqueTint
    val edge = MaterialTheme.extendedColors.glassEdge
    val pillAlpha by transition.animateFloat(
        transitionSpec = { if (false isTransitioningTo true) tween(PILL_FADE_OUT_MILLIS, easing = LinearEasing) else tween(PILL_FADE_IN_MILLIS, PILL_FADE_IN_DELAY_MILLIS, LinearEasing) },
        label = "pill",
    ) { open -> if (open) 0f else 1f }
    val pillSize = with(LocalDensity.current) { pill.width.toDp() to pill.height.toDp() }
    Box(
        modifier = modifier
            .graphicsLayer {
                shape = GrowingShape(pill, progress())
                clip = true
                shadowElevation = GlassDefaults.ShadowElevation.toPx()
                ambientShadowColor = GlassDefaults.Shadow
                spotShadowColor = GlassDefaults.Shadow
            }
            .drawBehind {
                val outline = GrowingShape(pill, progress()).createOutline(size, layoutDirection, this)
                drawOutline(outline, tint)
                drawOutline(outline, edge, style = Stroke(GlassDefaults.Edge.toPx()))
            }
            .clickable(interactionSource = null, indication = null) {},
    ) {
        // Drawn by hand rather than with GlassSurface, so it sets the glass's content color itself.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            content()
            Box(
                Modifier
                    .offset { IntOffset(pill.left.roundToInt(), pill.top.roundToInt()) }
                    .size(pillSize.first, pillSize.second)
                    .graphicsLayer { alpha = pillAlpha },
            ) { pillContent() }
        }
    }
}

/**
 * A row of the panel: opening, it fades in and slides down from above (each row from further up, so they land one
 * after another); closing, it only fades out, quickly, and goes back up once it's gone.
 */
@Composable
internal fun Transition<Boolean>.rowMotion(index: Int): Modifier {
    val alpha by animateFloat(
        transitionSpec = {
            if (false isTransitioningTo true) {
                tween(ROW_FADE_IN_MILLIS, ROW_FADE_IN_DELAY_MILLIS + index * ROW_STAGGER_MILLIS, LinearEasing)
            } else {
                tween(ROW_FADE_OUT_MILLIS, easing = LinearEasing)
            }
        },
        label = "row $index",
    ) { open -> if (open) 1f else 0f }
    val lift by animateFloat(
        transitionSpec = { if (false isTransitioningTo true) tween(ROW_SLIDE_MILLIS, easing = SlideIn) else snap(CLOSE_MILLIS) },
        label = "row $index lift",
    ) { open -> if (open) 0f else 1f }
    val distance = RowSlide * (index + 1)
    return Modifier.graphicsLayer {
        this.alpha = alpha
        translationY = -lift * distance.toPx()
    }
}

/**
 * The panel's outline at [progress]: the pill's rectangle at 0, the whole panel at 1, every edge moving at once; the
 * corners stay as round as the pill's.
 */
private class GrowingShape(private val pill: Rect, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val rect = Rect(
            left = lerp(pill.left, 0f, progress),
            top = lerp(pill.top, 0f, progress),
            right = lerp(pill.right, size.width, progress),
            bottom = lerp(pill.bottom, size.height, progress),
        )
        val radius = with(density) { PanelRadius.toPx() }.coerceAtMost(rect.height / 2)
        return Outline.Rounded(RoundRect(rect, CornerRadius(radius)))
    }
}

/** Material's emphasized decelerate: fast at first, easing into place. */
private val Decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/** Softer than the panel's, so the rows' slide is still seen once the panel is nearly open. */
private val SlideIn = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private const val OPEN_MILLIS = 400
private const val CLOSE_MILLIS = 320
private const val PILL_FADE_OUT_MILLIS = 100
private const val PILL_FADE_IN_MILLIS = 150

/** The pill's content comes back only once the rows are gone, so nothing shows through it. */
private const val PILL_FADE_IN_DELAY_MILLIS = 170
private const val ROW_FADE_IN_MILLIS = 260
private const val ROW_FADE_IN_DELAY_MILLIS = 60
private const val ROW_STAGGER_MILLIS = 30
private const val ROW_FADE_OUT_MILLIS = 90
private const val ROW_SLIDE_MILLIS = 460

/** How far above its place each row starts, times its position (24, 48, 72 dp). */
private val RowSlide = 24.dp

/** The pill's corners, kept by the panel. */
internal val PanelRadius = GlassButtonDefaults.HeaderSize / 2
