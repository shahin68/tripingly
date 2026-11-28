package com.falcon.tripingly.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * The app's bloom (Shahin, 2026-11-24): a rounded box grows out of one corner, about a button's size at first, and goes
 * back into it, as the ⋮ menus do (AppDropdownMenu). Used for what appears in place on a screen: the top bar's search
 * row (out of the search button), Search's people box and its tip.
 */

/**
 * Whether a [Bloom] shows, and how far it is grown. Its box stays composed while it shrinks back ([isShown]), so a
 * list can keep the item until it's gone. Remember it with [rememberBloomState].
 */
@Stable
class BloomState internal constructor(visible: Boolean) {
    internal val progress = Animatable(if (visible) 1f else 0f)

    /** Open, or still on its way back into its corner. */
    var isShown by mutableStateOf(visible)
        private set

    internal suspend fun animateTo(visible: Boolean) {
        if (visible) {
            isShown = true
            progress.animateTo(1f, tween(BloomDefaults.OPEN_MILLIS, easing = BloomDefaults.Easing))
        } else {
            // The same ease-out back: it starts fast and slows into the corner.
            progress.animateTo(0f, tween(BloomDefaults.CLOSE_MILLIS, easing = BloomDefaults.Easing))
            isShown = false
        }
    }
}

/** A [BloomState] following [visible]: shown at once when it starts visible, blooming on every later change. */
@Composable
fun rememberBloomState(visible: Boolean): BloomState {
    val state = remember { BloomState(visible) }
    LaunchedEffect(visible) { state.animateTo(visible) }
    return state
}

/**
 * [content] blooming out of its top end corner (top start with [fromEnd] false) while [state] shows it: the box's
 * outline, [cornerRadius] round, grows from a button-sized corner to the whole box and the height it takes in its
 * layout glides with it, so what's below moves along; the content fades in and slides down a little once the box has
 * room. Closing plays it back. One [Animatable] read only in layout and drawing, so the content is measured once.
 * [padding] sits outside the box but inside the bloom, so it goes away with it.
 */
@Composable
fun Bloom(
    state: BloomState,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
    fromEnd: Boolean = true,
    padding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit,
) {
    if (!state.isShown) return
    val progress = state.progress
    val fromRight = fromEnd == (LocalLayoutDirection.current == LayoutDirection.Ltr)
    Box(
        modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val height = (placeable.height * progress.value).roundToInt()
                layout(placeable.width, height) { placeable.place(0, 0) }
            }
            .padding(padding)
            .graphicsLayer {
                shape = BloomShape(
                    progress = progress.value,
                    startWidth = BloomDefaults.Start.toPx(),
                    startHeight = 0f,
                    radius = cornerRadius.toPx(),
                    fromRight = fromRight,
                    fromBottom = false,
                )
                clip = true
            },
    ) {
        Box(
            Modifier.graphicsLayer {
                val shown = BloomDefaults.Easing.transform(((progress.value - CONTENT_START) / (1f - CONTENT_START)).coerceIn(0f, 1f))
                alpha = shown
                translationY = -(1f - shown) * BloomDefaults.ContentSlide.toPx()
            },
        ) { content() }
    }
}

/** A [Bloom] of its own, following [visible]. */
@Composable
fun Bloom(
    visible: Boolean,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
    fromEnd: Boolean = true,
    padding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit,
) = Bloom(rememberBloomState(visible), cornerRadius, modifier, fromEnd, padding, content)

/**
 * A box at [progress] of its bloom: a rounded rectangle growing from [startWidth] × [startHeight] (at most the box)
 * out of the corner [fromRight] and [fromBottom] name, to the whole box; corners [radius] round, less while it's small.
 */
data class BloomShape(
    val progress: Float,
    val startWidth: Float,
    val startHeight: Float,
    val radius: Float,
    val fromRight: Boolean,
    val fromBottom: Boolean,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val width = lerp(min(startWidth, size.width), size.width, progress)
        val height = lerp(min(startHeight, size.height), size.height, progress)
        val left = if (fromRight) size.width - width else 0f
        val top = if (fromBottom) size.height - height else 0f
        val corner = min(radius, min(width, height) / 2f)
        return Outline.Rounded(RoundRect(left, top, left + width, top + height, CornerRadius(corner)))
    }
}

object BloomDefaults {
    /** The corner it starts from: about a button's size. */
    val Start = 40.dp
    internal val ContentSlide = 12.dp
    internal const val OPEN_MILLIS = 400
    internal const val CLOSE_MILLIS = 300

    /** Material's emphasized decelerate, as the menus and the trip card. */
    val Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
}

/** The content shows from this far into the bloom, once the box has room for it. */
private const val CONTENT_START = 0.3f

@Composable
private fun BloomPreviewContent() {
    Column(Modifier.background(MaterialTheme.colorScheme.background).padding(MaterialTheme.spacing.medium)) {
        Bloom(visible = true, cornerRadius = 20.dp) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Text("Bloomed out of its top end", Modifier.padding(MaterialTheme.spacing.medium))
            }
        }
    }
}

@Preview
@Composable
private fun BloomPreview() {
    TripinglyTheme { BloomPreviewContent() }
}

@Preview
@Composable
private fun BloomDarkPreview() {
    TripinglyTheme(darkTheme = true) { BloomPreviewContent() }
}
