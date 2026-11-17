package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Fades a horizontally scrolling list out at a side where more of it is hidden, over [length]: none at a side it
 * can't scroll to. The state is read while drawing only, so scrolling never recomposes; one offscreen layer, two
 * gradient rects. Works with any [ScrollableState] (`LazyListState`, `ScrollState`), and follows right to left.
 */
fun Modifier.horizontalFadingEdge(state: ScrollableState, length: Dp = FadingEdgeLength): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val edge = length.toPx().coerceAtMost(size.width / 2)
            val rtl = layoutDirection == LayoutDirection.Rtl
            if (state.canScrollBackward) fadeOut(Side.Left.takeUnless { rtl } ?: Side.Right, edge)
            if (state.canScrollForward) fadeOut(Side.Right.takeUnless { rtl } ?: Side.Left, edge)
        }

/** The same for a vertically scrolling list: it fades out at the top or bottom while more of it is hidden there. */
fun Modifier.verticalFadingEdge(state: ScrollableState, length: Dp = FadingEdgeLength): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val edge = length.toPx().coerceAtMost(size.height / 2)
            if (state.canScrollBackward) fadeOut(Side.Top, edge)
            if (state.canScrollForward) fadeOut(Side.Bottom, edge)
        }

private enum class Side { Left, Right, Top, Bottom }

/** Erases the content towards [side], fully at the edge, not at all [edge] in. */
private fun ContentDrawScope.fadeOut(side: Side, edge: Float) {
    val fade = listOf(Color.Black, Color.Transparent)
    val (brush, topLeft) = when (side) {
        Side.Left -> Brush.horizontalGradient(fade, startX = 0f, endX = edge) to Offset.Zero
        Side.Right -> Brush.horizontalGradient(fade, startX = size.width, endX = size.width - edge) to Offset(size.width - edge, 0f)
        Side.Top -> Brush.verticalGradient(fade, startY = 0f, endY = edge) to Offset.Zero
        Side.Bottom -> Brush.verticalGradient(fade, startY = size.height, endY = size.height - edge) to Offset(0f, size.height - edge)
    }
    val isHorizontal = side == Side.Left || side == Side.Right
    drawRect(
        brush = brush,
        topLeft = topLeft,
        size = if (isHorizontal) Size(edge, size.height) else Size(size.width, edge),
        blendMode = BlendMode.DstOut,
    )
}

private val FadingEdgeLength = 24.dp
