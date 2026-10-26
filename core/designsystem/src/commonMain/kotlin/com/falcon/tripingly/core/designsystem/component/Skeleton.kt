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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.generated.resources.Res
import com.falcon.tripingly.core.designsystem.generated.resources.loading
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import org.jetbrains.compose.resources.stringResource

/**
 * A grey placeholder block with a light sweeping across it, the building block of every loading skeleton.
 * Lay blocks out like the content that's coming (a photo, two lines of text) so nothing jumps when it arrives.
 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
) {
    val shine = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = SHINE_ALPHA)
    // Previews stay still so screenshots are stable.
    val progress = if (LocalInspectionMode.current) null else shimmerProgress()
    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .drawWithContent {
                drawContent()
                val sweep = progress?.value ?: return@drawWithContent
                val x = size.width * sweep
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(Color.Transparent, shine, Color.Transparent),
                        start = Offset(x - size.width / 2, 0f),
                        end = Offset(x, 0f),
                    ),
                )
            },
    )
}

/** Where the light is, from before the left edge to past the right; read only while drawing, so no recomposition. */
@Composable
private fun shimmerProgress(): State<Float> = rememberInfiniteTransition().animateFloat(
    initialValue = -1f,
    targetValue = 2f,
    animationSpec = infiniteRepeatable(tween(SHIMMER_MILLIS, easing = LinearEasing), RepeatMode.Restart),
)

/** A line of text that's still loading: a short rounded [SkeletonBox] of the text's height. */
@Composable
fun SkeletonLine(modifier: Modifier = Modifier, height: Dp = SkeletonDefaults.LineHeight) {
    SkeletonBox(modifier.height(height), shape = CircleShape)
}

/**
 * A screen-wide loading skeleton: [count] copies of [item] in a column, under the floating bars thanks to
 * [contentPadding]. The default item is a card with a picture and three lines, like most lists in the app.
 */
@Composable
fun SkeletonList(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    count: Int = SkeletonDefaults.Count,
    item: @Composable () -> Unit = { SkeletonCard() },
) {
    val loading = stringResource(Res.string.loading)
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = MaterialTheme.spacing.medium)
            .semantics { contentDescription = loading },
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        repeat(count) { item() }
    }
}

/** A list card that's still loading: a square picture and three lines of text on a card. */
@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Row(
            modifier = Modifier.padding(MaterialTheme.spacing.small),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonBox(Modifier.size(SkeletonDefaults.Thumb), shape = MaterialTheme.shapes.medium)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                SkeletonLine(Modifier.fillMaxWidth(0.4f), height = SkeletonDefaults.SmallLineHeight)
                SkeletonLine(Modifier.fillMaxWidth(0.75f))
                SkeletonLine(Modifier.fillMaxWidth(0.6f), height = SkeletonDefaults.SmallLineHeight)
            }
        }
    }
}

object SkeletonDefaults {
    val LineHeight = 14.dp
    val SmallLineHeight = 10.dp
    val Thumb = 56.dp
    const val Count = 5
}

private const val SHIMMER_MILLIS = 1400
private const val SHINE_ALPHA = 0.5f

@Composable
private fun SkeletonPreviewContent() {
    Box(Modifier.background(MaterialTheme.colorScheme.background).padding(vertical = MaterialTheme.spacing.medium)) {
        SkeletonList(count = 3)
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
