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
            if (state.canScrollBackward) fadeOut(atLeft = !rtl, edge)
            if (state.canScrollForward) fadeOut(atLeft = rtl, edge)
        }

/** Erases the content towards the left or right side, fully at the edge, not at all [edge] in. */
private fun ContentDrawScope.fadeOut(atLeft: Boolean, edge: Float) {
    val start = if (atLeft) 0f else size.width
    val end = if (atLeft) edge else size.width - edge
    drawRect(
        brush = Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = start, endX = end),
        topLeft = Offset(if (atLeft) 0f else size.width - edge, 0f),
        size = Size(edge, size.height),
        blendMode = BlendMode.DstOut,
    )
}

private val FadingEdgeLength = 24.dp
