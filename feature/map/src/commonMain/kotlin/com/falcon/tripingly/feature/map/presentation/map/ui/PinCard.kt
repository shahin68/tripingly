package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.GlassDefaults
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.extendedColors
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.feature.map.presentation.map.PinAdd
import com.falcon.tripingly.feature.map.presentation.map.PinRoutesState
import com.falcon.tripingly.feature.map.presentation.map.preview.previewPlace

/*
 * How the map's pin cards come and go: they bloom out of the pin (design: the "Final" board of the pin card canvas,
 * Shahin, 2026-11-12). Opening, the card grows out of a point on the pin (nothing at first, Shahin 2026-11-12) into the whole card, slowing down at the end, and the
 * card's rows rise into place one after another, the row nearest the pin first. Closing is quick and even: the card
 * shrinks back into that point and the rows only fade. A map tap or drag closes it at once from wherever it is. One
 * transition drives it all, read only while drawing, so a frame lays nothing out again.
 *
 * Each card shows what it last showed while it leaves and only updates while it stays, so no screen code keeps old
 * values around for the exit.
 */

/**
 * A card over the camera's middle, where the tapped pin is ([pinBelowMiddle] lower, in landscape): it blooms out of the
 * pin, just above it, and goes back into it when it closes. A card for another pin replaces it; the same pin's card
 * only updates. The card is drawn as glass here, so its [content] draws none of its own; the content's rows take
 * [PinCardScope.bloomRow].
 */
@Composable
internal fun <T : Any> PinCard(
    card: T?,
    key: (T) -> Any,
    pinClearance: Dp,
    modifier: Modifier = Modifier,
    pinBelowMiddle: Dp = 0.dp,
    content: @Composable PinCardScope.(T) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = card,
            contentKey = { it?.let(key) },
            // The bloom below is the whole animation; the size jumps, so nothing clips the card while it grows.
            transitionSpec = { EnterTransition.None togetherWith ExitTransition.None using SizeTransform(clip = false) { _, _ -> snap() } },
            // Both cards stand on the pin while one replaces the other.
            contentAlignment = Alignment.BottomCenter,
            // [modifier] comes after the lift, so it sees where the card really is (a touch on it mustn't close it).
            modifier = Modifier
                .padding(horizontal = MaterialTheme.spacing.medium)
                .layout { measurable, constraints ->
                    val measured = measurable.measure(constraints)
                    layout(measured.width, measured.height) {
                        measured.place(0, -measured.height / 2 - pinClearance.roundToPx() + pinBelowMiddle.roundToPx())
                    }
                }
                .then(modifier),
        ) { shown ->
            if (shown != null) {
                val progress = transition.animateBloom()
                val scope = PinCardScope(transition)
                val tint = GlassDefaults.opaqueTint
                val edge = MaterialTheme.extendedColors.glassEdge
                Box(
                    Modifier
                        .graphicsLayer {
                            shape = BloomShape(progress.value, CardRadius.toPx())
                            clip = true
                            shadowElevation = GlassDefaults.ShadowElevation.toPx()
                            ambientShadowColor = GlassDefaults.Shadow
                            spotShadowColor = GlassDefaults.Shadow
                        }
                        .drawBehind {
                            val outline = BloomShape(progress.value, CardRadius.toPx()).createOutline(size, layoutDirection, this)
                            drawOutline(outline, tint)
                            drawOutline(outline, edge, style = Stroke(GlassDefaults.Edge.toPx()))
                        }
                        // The whole card takes touches, its empty parts too: the map under it must not pan or get a long press.
                        .pointerInput(Unit) {},
                ) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) { scope.content(shown) }
                }
            }
        }
    }
}

/** What a pin card's rows move with. */
internal class PinCardScope(private val transition: Transition<EnterExitState>) {
    /**
     * A row of the card, counted from the pin ([fromPin] 0 is the row nearest it): opening, it rises into place and fades
     * in, after the rows nearer the pin; closing, it only fades out, quickly.
     */
    @Composable
    fun Modifier.bloomRow(fromPin: Int): Modifier {
        val shown by transition.animateFloat(
            transitionSpec = {
                if (targetState == EnterExitState.Visible) {
                    tween(ROW_RISE_MILLIS, ROW_DELAY_MILLIS + fromPin * ROW_STAGGER_MILLIS, Standard)
                } else {
                    tween(ROW_FADE_OUT_MILLIS, easing = LinearEasing)
                }
            },
            label = "row $fromPin",
        ) { if (it == EnterExitState.Visible) 1f else 0f }
        return graphicsLayer {
            alpha = shown
            // Closing, the rows stay where they are.
            translationY = if (transition.targetState == EnterExitState.Visible) (1f - shown) * RowRise.toPx() else 0f
        }
    }
}

/** The card's growth out of the pin: 0 is nothing (a point over it), 1 the whole card. */
@Composable
private fun Transition<EnterExitState>.animateBloom(): State<Float> = animateFloat(
    transitionSpec = { if (targetState == EnterExitState.Visible) tween(OPEN_MILLIS, easing = Bloom) else tween(CLOSE_MILLIS, easing = LinearEasing) },
    label = "bloom",
) { if (it == EnterExitState.Visible) 1f else 0f }

/**
 * The card's outline at [progress]: nothing at 0, a point where the card meets the pin (the middle of its bottom), the
 * whole card with its [radius] corners at 1, and in between a rounded rectangle growing out of that point.
 */
private class BloomShape(private val progress: Float, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val halfWidth = size.width / 2 * progress
        val rect = Rect(size.width / 2 - halfWidth, size.height * (1 - progress), size.width / 2 + halfWidth, size.height)
        val corner = (radius * progress).coerceAtMost(minOf(rect.width, rect.height) / 2)
        return Outline.Rounded(RoundRect(rect, CornerRadius(corner)))
    }
}

private val CardRadius = 28.dp
private const val OPEN_MILLIS = 320
private const val CLOSE_MILLIS = 120
private const val ROW_RISE_MILLIS = 380
private const val ROW_DELAY_MILLIS = 40
private const val ROW_STAGGER_MILLIS = 35
private const val ROW_FADE_OUT_MILLIS = 80
private val RowRise = 12.dp
private val Bloom = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** A place's card over the middle, where its pin is. */
@Preview(widthDp = 360, heightDp = 640)
@Composable
private fun PinCardPreview() {
    TripinglyTheme {
        PinCard(card = previewPlace, key = { it.id }, pinClearance = 24.dp) { place ->
            PlaceCard(place, details = null, address = null, add = PinAdd.ToDay(1), onAdd = {}, onOpenImage = {}, onOpenPhotos = {}, onOpenDetails = {}, routes = PinRoutesState(), onGoHere = {}, onRoute = {})
        }
    }
}

@Preview(widthDp = 360, heightDp = 640)
@Composable
private fun PinCardDarkPreview() {
    TripinglyTheme(darkTheme = true) {
        PinCard(card = previewPlace, key = { it.id }, pinClearance = 24.dp) { place ->
            PlaceCard(place, details = null, address = null, add = PinAdd.ToMyTrip, onAdd = {}, onOpenImage = {}, onOpenPhotos = {}, onOpenDetails = {}, routes = PinRoutesState(isRouteTo = true), onGoHere = {}, onRoute = {})
        }
    }
}
