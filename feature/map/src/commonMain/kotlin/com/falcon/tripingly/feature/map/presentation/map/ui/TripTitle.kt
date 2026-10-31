package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseOutBack
import kotlin.math.roundToInt
import androidx.compose.ui.util.lerp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Rect
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.GlassButtonDefaults
import com.falcon.tripingly.core.designsystem.component.GlassSurface
import com.falcon.tripingly.core.designsystem.component.Skeleton
import com.falcon.tripingly.core.designsystem.component.skeletonText
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_stop_count
import com.falcon.tripingly.feature.map.presentation.map.HeaderState
import org.jetbrains.compose.resources.pluralStringResource

/** The trip's name, and its dates, stop count and (someone else's trip) owner under it. A tap opens [TripTitleCard]. */
@Composable
internal fun TripTitle(header: HeaderState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassSurface(modifier = modifier.height(GlassButtonDefaults.HeaderSize).clip(CircleShape).clickable(onClick = onClick), shape = CircleShape, blur = false) {
        Row(
            modifier = Modifier.fillMaxHeight().padding(horizontal = MaterialTheme.spacing.medium + MaterialTheme.spacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (header.isLoading) {
                // Placeholder lengths only: drawn as the title's shimmering bars, never as text.
                Skeleton(Modifier.weight(1f)) { TitleLines("A trip's name", "1 Jan – 7 Jan · 6 stops") }
            } else {
                TitleLines(header.tripName, subtitle(header), Modifier.weight(1f))
            }
            if (header.isBusy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
    }
}

/**
 * The whole trip name and its details, for names too long for the title pill. It morphs out of the pill ([from]) into
 * a card below the header's buttons ([to]: its left, top and right; the height is the name's) as [progress] goes to 1,
 * and back ([progress] runs linearly; the card eases it): one motion in which it first drops below the buttons and,
 * nearly there, starts to widen, both settling with a small bounce. Solid, and it takes the touches on it, so the map
 * underneath gets none. Fills the screen to place itself.
 */
@Composable
internal fun TripTitleCard(header: HeaderState, from: Rect, to: Rect, progress: () -> Float, modifier: Modifier = Modifier) {
    Layout(
        modifier = modifier.fillMaxSize(),
        content = {
            Surface(
                // Holds the touches on the card; the screen closes it on any touch.
                modifier = Modifier.pointerInput(Unit) { awaitEachGesture { awaitFirstDown() } },
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
            ) {
                // Laid out once at the card's final width and only uncovered as the card grows, so the text never
                // reflows while it moves.
                val finalWidth = with(LocalDensity.current) { to.width.toDp() }
                // The pill's padding and line spacing, so the lines start exactly where the pill's were.
                Column(
                    modifier = Modifier
                        .wrapContentWidth(Alignment.Start, unbounded = true)
                        .width(finalWidth)
                        .padding(horizontal = MaterialTheme.spacing.medium + MaterialTheme.spacing.extraSmall, vertical = MaterialTheme.spacing.small),
                ) {
                    Text(header.tripName, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle(header), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
    ) { (card), constraints ->
        // One motion, so nothing runs back to back: the card drops below the buttons in the first part, and widens
        // from when it is nearly there (so it never grows over the buttons); both overshoot a little and settle.
        val p = progress()
        val down = EaseOutBack.transform((p / DropShare).coerceIn(0f, 1f))
        val across = WidenEasing.transform(((p - WidenStart) / (1f - WidenStart)).coerceIn(0f, 1f))
        val fullHeight = card.minIntrinsicHeight(to.width.roundToInt()).toFloat().coerceAtLeast(from.height)
        val placeable = card.measure(
            Constraints.fixed(lerp(from.width, to.width, across).roundToInt(), lerp(from.height, fullHeight, down).roundToInt()),
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.place(lerp(from.left, to.left, across).roundToInt(), lerp(from.top, to.top, down).roundToInt())
        }
    }
}

/** Share of the motion in which the card drops below the buttons. */
private const val DropShare = 0.6f

/** When the card starts to widen: it has nearly reached its place below the buttons by then. */
private const val WidenStart = 0.2f

/** Ease out with a slight overshoot: a gentler bounce than [EaseOutBack], as the width travels further. */
private val WidenEasing = CubicBezierEasing(0.34f, 1.25f, 0.64f, 1f)

@Composable
private fun subtitle(header: HeaderState): String = listOfNotNull(
    header.dates,
    pluralStringResource(Res.plurals.map_stop_count, header.stopCount, header.stopCount),
    header.ownerLabel?.asString(),
).joinToString(" · ")

@Composable
private fun TitleLines(name: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.skeletonText())
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.skeletonText(),
        )
    }
}
