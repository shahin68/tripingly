package com.falcon.tripingly.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.generated.resources.Res
import com.falcon.tripingly.core.designsystem.generated.resources.loading
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import org.jetbrains.compose.resources.stringResource

/**
 * Shows [content] as its own loading skeleton: the real composables, filled with placeholder data of a typical
 * length, where every element marked with [Modifier.skeleton] draws as a shimmering grey block of its own size and
 * shape. Everything else (cards, spacing) stays as it is, so the skeleton is true to the design and nothing jumps
 * when the real content arrives. Nothing in it can be tapped, and screen readers hear "Loading".
 */
@Composable
fun Skeleton(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val loading = stringResource(Res.string.loading)
    val paint = SkeletonPaint(
        base = MaterialTheme.colorScheme.surfaceVariant,
        shine = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = SHINE_ALPHA),
        // Previews stay still so screenshots are stable.
        sweep = if (LocalInspectionMode.current) null else shimmerProgress(),
    )
    CompositionLocalProvider(LocalSkeleton provides paint) {
        Box(
            modifier
                .clearAndSetSemantics { contentDescription = loading }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                },
        ) { content() }
    }
}

/**
 * Marks an element (a text, an image, an icon) of a component as a placeholder block inside a [Skeleton]: there it
 * draws as a grey [shape] the element's size with the shimmer sweeping across; anywhere else it draws as usual.
 */
fun Modifier.skeleton(shape: Shape = BlockShape): Modifier = this then SkeletonElement(shape, isText = false)

/**
 * [Modifier.skeleton] for a text: a rounded bar the text's width that leaves out the line's leading above and below,
 * so lines of text stay apart like real lines do.
 */
fun Modifier.skeletonText(): Modifier = this then SkeletonElement(CircleShape, isText = true)

/**
 * A grey placeholder block with a light sweeping across it, for a placeholder with no real composable behind it
 * (the map before it opens).
 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
) {
    Skeleton { Box(modifier.skeleton(shape)) }
}

/** How a [Skeleton]'s blocks are painted: their colour, the light's colour and where the light is (null: still). */
@Immutable
private class SkeletonPaint(val base: Color, val shine: Color, val sweep: State<Float>?)

/** Inside a [Skeleton]: how to paint the blocks; null outside, where marked elements draw as usual. */
private val LocalSkeleton = compositionLocalOf<SkeletonPaint?> { null }

private data class SkeletonElement(val shape: Shape, val isText: Boolean) : ModifierNodeElement<SkeletonNode>() {
    override fun create() = SkeletonNode(shape, isText)
    override fun update(node: SkeletonNode) {
        node.shape = shape
        node.isText = isText
    }
}

private class SkeletonNode(var shape: Shape, var isText: Boolean) : Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {
    override fun ContentDrawScope.draw() {
        val paint = currentValueOf(LocalSkeleton) ?: return drawContent()
        val inset = if (isText) size.height * TEXT_LEADING else 0f
        translate(top = inset) { drawBlock(paint, Size(size.width, size.height - inset * 2)) }
    }

    private fun DrawScope.drawBlock(paint: SkeletonPaint, size: Size) {
        val outline = shape.createOutline(size, layoutDirection, this)
        drawOutline(outline, paint.base)
        // Read while drawing, so the light moves without recomposing anything.
        val sweep = paint.sweep?.value ?: return
        val x = size.width * sweep
        drawOutline(
            outline,
            Brush.linearGradient(
                colors = listOf(Color.Transparent, paint.shine, Color.Transparent),
                start = Offset(x - size.width / 2, 0f),
                end = Offset(x, 0f),
            ),
        )
    }
}

/** Where the light is, from before the left edge to past the right; read only while drawing, so no recomposition. */
@Composable
private fun shimmerProgress(): State<Float> = rememberInfiniteTransition().animateFloat(
    initialValue = -1f,
    targetValue = 2f,
    animationSpec = infiniteRepeatable(tween(SHIMMER_MILLIS, easing = LinearEasing), RepeatMode.Restart),
)

private val BlockShape: Shape = RoundedCornerShape(6.dp)
private const val SHIMMER_MILLIS = 1400
/** The share of a text line's height above and below its letters. */
private const val TEXT_LEADING = 0.2f
private const val SHINE_ALPHA = 0.5f

/** A card of the app's usual shape with its marked parts shimmering, as a screen would build it. */
@Composable
private fun SkeletonPreviewContent() {
    Skeleton(Modifier.background(MaterialTheme.colorScheme.background).padding(MaterialTheme.spacing.medium)) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            Row(
                Modifier.fillMaxWidth().padding(MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AvatarCircle(name = "Alex Traveller", photo = null)
                Column {
                    Text("Alex Traveller", style = MaterialTheme.typography.titleSmall, modifier = Modifier.skeletonText())
                    Text("@traveller", style = MaterialTheme.typography.bodySmall, modifier = Modifier.skeletonText())
                }
            }
        }
    }
}

@Preview
@Composable
private fun SkeletonPreview() {
    TripinglyTheme { SkeletonPreviewContent() }
}

@Preview
@Composable
private fun SkeletonDarkPreview() {
    TripinglyTheme(darkTheme = true) { SkeletonPreviewContent() }
}
